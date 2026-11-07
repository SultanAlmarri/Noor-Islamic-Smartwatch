package com.noor.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/**
 * How one play ended. [played] is false only when no sound came out at all; [error] then says why (for the alert
 * history), and the caller tries something else.
 */
data class PlayResult(
    val played: Boolean, val playedMs: Long, val stoppedByUser: Boolean = false, val error: String? = null,
    /** How it ended and what happened on the way (focus, recoveries), for the alert history. */
    val report: String? = null,
)

/**
 * Plays one alert sound on the alarm stream: asks for audio focus (music pauses, calls win), rises gradually to the
 * chosen volume, stops after an optional limit with a short fade, and always releases everything when done.
 * Used by the Adhan service and by the previews in the app, so a preview sounds exactly like the real alert.
 */
class AlertPlayer(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var onEnd: ((PlayResult) -> Unit)? = null
    private var target = 1f
    private var startedAt = 0L
    private var rampMs = 0L
    private var fadingOut = false
    private var started = false
    private var source: SoundSource? = null
    private var recoveries = 0
    private val events = ArrayList<String>()

    val isPlaying: Boolean get() = player != null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * @param volumePercent percent of the alarm volume (perceptual curve).
     * @param onEnd called once on the main thread with how it ended.
     */
    fun play(source: SoundSource, volumePercent: Int, gradualSeconds: Int, maxSeconds: Int, onEnd: (PlayResult) -> Unit) {
        stop(notify = false)
        this.onEnd = onEnd
        this.source = source
        target = gain(volumePercent)
        rampMs = gradualSeconds.coerceAtLeast(0) * 1000L
        fadingOut = false
        started = false
        recoveries = 0
        events.clear()

        // Focus is a courtesy between apps, not a permission: an alarm sounds even if another app keeps focus.
        // (Calls are handled before playing: the dispatcher never starts an alert during a call.)
        events += "focus " + when (requestFocus()) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"
            else -> "refused"
        }
        open(source, seekMs = 0, maxSeconds = maxSeconds)
        main.postDelayed(callWatch, CALL_CHECK_MS)
    }

    /** Opens a player on [source] from [seekMs] and starts it when ready. */
    private fun open(source: SoundSource, seekMs: Int, maxSeconds: Int) {
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            when (source) {
                is SoundSource.Asset -> app.assets.openFd(source.path).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                is SoundSource.LocalFile -> mp.setDataSource(source.file.absolutePath)
            }
            val v = if (started) currentGain() else if (rampMs > 0) START_GAIN * target else target
            mp.setVolume(v, v)
            mp.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                main.removeCallbacks(watchdog)
                if (seekMs > 0) runCatching { it.seekTo(seekMs) }
                it.start()
                if (!started) {
                    startedAt = SystemClock.elapsedRealtime()
                    started = true
                    if (rampMs > 0) main.post(ramp)
                    if (maxSeconds > 0) main.postDelayed(limit, maxSeconds * 1000L)
                }
            }
            mp.setOnCompletionListener { done ->
                // A recording that "completes" well before its end was cut off by the system: carry on from there.
                val dur = runCatching { done.duration }.getOrDefault(-1)
                val pos = runCatching { done.currentPosition }.getOrDefault(-1)
                if (player === done && dur > 0 && pos in 0 until dur - EARLY_END_MS) recover("ended early at ${pos / 1000} of ${dur / 1000} s", pos, maxSeconds)
                else finish(null, reason = "completed")
            }
            mp.setOnErrorListener { failed, what, extra ->
                val pos = runCatching { failed.currentPosition }.getOrDefault(0)
                if (started) recover("player error $what/$extra at ${pos / 1000} s", pos, maxSeconds)
                else finish("player error $what/$extra")
                true
            }
            mp.prepareAsync()
            // A player that never becomes ready must not leave the alert hanging in silence.
            main.removeCallbacks(watchdog)
            main.postDelayed(watchdog, PREPARE_TIMEOUT_MS)
        } catch (e: Exception) {
            if (started) recover("${e.javaClass.simpleName}: ${e.message}", seekMs, maxSeconds)
            else finish("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Something interrupted a sound that had started: open it again where it stopped (a few times at most). */
    private fun recover(why: String, positionMs: Int, maxSeconds: Int) {
        events += why
        val src = source
        player?.let { mp -> runCatching { mp.release() } }
        player = null
        if (src == null || recoveries >= MAX_RECOVERIES) { finish(null, reason = "stopped: $why"); return }
        recoveries++
        events += "resumed"
        // The limit keeps counting from the first start; only what is left of it applies.
        val left = if (maxSeconds > 0) (maxSeconds - ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()).coerceAtLeast(1) else 0
        main.removeCallbacks(limit)
        if (maxSeconds > 0) main.postDelayed(limit, left * 1000L)
        open(src, positionMs, maxSeconds = 0)
    }

    /** Stops playing. [notify] = the owner is told (as stopped by the user); false = released quietly. */
    fun stop(notify: Boolean = true) {
        if (player == null) return
        if (notify) finish(null, byUser = true) else release()
    }

    /** Fades out over [ms] and then stops. */
    fun fadeOutAndStop(ms: Long = FADE_MS) {
        val mp = player ?: return
        if (fadingOut) return
        fadingOut = true
        main.removeCallbacks(ramp)
        val from = currentGain()
        val start = SystemClock.elapsedRealtime()
        main.post(object : Runnable {
            override fun run() {
                if (player !== mp) return
                val f = ((SystemClock.elapsedRealtime() - start).toFloat() / ms).coerceIn(0f, 1f)
                val v = from * (1f - f)
                runCatching { mp.setVolume(v, v) }
                if (f >= 1f) finish(null, reason = "time limit reached") else main.postDelayed(this, STEP_MS)
            }
        })
    }

    private fun currentGain(): Float {
        if (rampMs <= 0) return target
        val f = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / rampMs).coerceIn(0f, 1f)
        return target * (START_GAIN + (1 - START_GAIN) * f * f)
    }

    private val ramp = object : Runnable {
        override fun run() {
            val mp = player ?: return
            if (fadingOut) return
            val v = currentGain()
            runCatching { mp.setVolume(v, v) }
            if (SystemClock.elapsedRealtime() - startedAt < rampMs) main.postDelayed(this, STEP_MS)
        }
    }

    private val limit = Runnable { events += "time limit"; fadeOutAndStop() }
    private val watchdog = Runnable { if (!started) finish("not ready after ${PREPARE_TIMEOUT_MS / 1000} s") }

    /** A call starting (ringing, or in a call) ends the alert: the Adhan never talks over a call. */
    private val callWatch = object : Runnable {
        override fun run() {
            if (player == null) return
            if (inCall()) { finish(null, reason = "a call started"); return }
            main.postDelayed(this, CALL_CHECK_MS)
        }
    }

    private fun inCall(): Boolean = audio?.mode.let { it == AudioManager.MODE_IN_CALL || it == AudioManager.MODE_IN_COMMUNICATION || it == AudioManager.MODE_RINGTONE }

    /**
     * 0.9.7: losing audio focus no longer ends the Adhan. Focus is how apps share sound politely, and a prayer alarm,
     * like an alarm clock, keeps sounding; on Android 17 the system may also deny or withdraw focus from an app that is
     * not in front (its background-audio rules), which used to stop the Adhan a second after it began. Only a call
     * stops it (checked here and every second).
     */
    private fun requestFocus(): Int {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        events += "focus lost ($change) at ${elapsedS()} s"
                        if (inCall()) finish(null, reason = "a call started")
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> events += "focus regained at ${elapsedS()} s"
                }
            }, main)
            .build()
        focus = req
        return runCatching { audio?.requestAudioFocus(req) }.getOrNull() ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }

    private fun elapsedS(): Long = if (started) (SystemClock.elapsedRealtime() - startedAt) / 1000 else 0

    /** Ends the current play. [error] = why nothing could be heard (only meaningful before the sound started). */
    private fun finish(error: String?, byUser: Boolean = false, reason: String? = null) {
        val cb = onEnd
        onEnd = null
        val playedMs = if (started) SystemClock.elapsedRealtime() - startedAt else 0L
        // Once the player has started, sound came out, whatever ends it later.
        val heard = started
        events += reason ?: when {
            byUser -> "stopped by the user"
            fadingOut -> "time limit reached"
            error != null -> error
            else -> "ended"
        }
        val report = events.joinToString("; ")
        release()
        cb?.invoke(PlayResult(played = heard, playedMs = playedMs, stoppedByUser = byUser,
            error = if (heard) null else error ?: if (byUser) "stopped before it started" else "ended before it started", report = report))
    }

    private fun release() {
        main.removeCallbacks(ramp); main.removeCallbacks(limit); main.removeCallbacks(watchdog); main.removeCallbacks(callWatch)
        player?.let { mp -> runCatching { mp.stop() }; runCatching { mp.release() } }
        player = null
        focus?.let { f -> runCatching { audio?.abandonAudioFocusRequest(f) } }
        focus = null
    }

    companion object {
        private const val STEP_MS = 100L
        private const val FADE_MS = 1500L
        private const val START_GAIN = 0.05f
        private const val PREPARE_TIMEOUT_MS = 8_000L
        private const val CALL_CHECK_MS = 1_000L
        private const val EARLY_END_MS = 2_500
        private const val MAX_RECOVERIES = 3

        /** Percent to linear gain on a perceptual (square) curve: 50% sounds about half as loud, not barely quieter. */
        fun gain(percent: Int): Float { val p = percent.coerceIn(0, 100) / 100f; return p * p }

        /** The alarm stream volume, 0..1, for warnings such as "your alarm volume is off". */
        fun alarmVolume(context: Context): Float {
            val am = context.getSystemService(AudioManager::class.java) ?: return 1f
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
            return am.getStreamVolume(AudioManager.STREAM_ALARM).toFloat() / max
        }
    }
}
