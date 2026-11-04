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
    private var onEnd: ((Boolean) -> Unit)? = null
    private var target = 1f
    private var startedAt = 0L
    private var rampMs = 0L
    private var fadingOut = false

    val isPlaying: Boolean get() = player != null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * @param volumePercent percent of the alarm volume (perceptual curve).
     * @param onEnd called once on the main thread: true when the sound played (to the end or until stopped).
     */
    fun play(source: SoundSource, volumePercent: Int, gradualSeconds: Int, maxSeconds: Int, onEnd: (Boolean) -> Unit) {
        stop(notify = false)
        this.onEnd = onEnd
        target = gain(volumePercent)
        rampMs = gradualSeconds.coerceAtLeast(0) * 1000L
        fadingOut = false

        if (!requestFocus()) { finish(false); return }
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            when (source) {
                is SoundSource.Asset -> app.assets.openFd(source.path).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                is SoundSource.LocalFile -> mp.setDataSource(source.file.absolutePath)
            }
            mp.setVolume(if (rampMs > 0) START_GAIN * target else target, if (rampMs > 0) START_GAIN * target else target)
            mp.setOnPreparedListener {
                startedAt = SystemClock.elapsedRealtime()
                it.start()
                if (rampMs > 0) main.post(ramp)
                if (maxSeconds > 0) main.postDelayed(limit, maxSeconds * 1000L)
            }
            mp.setOnCompletionListener { finish(true) }
            mp.setOnErrorListener { _, _, _ -> finish(false); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            finish(false)
        }
    }

    fun stop(notify: Boolean = true) {
        if (player == null) return
        if (notify) finish(true) else release()
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
                if (f >= 1f) finish(true) else main.postDelayed(this, STEP_MS)
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

    private val limit = Runnable { fadeOutAndStop() }

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener({ change ->
                // A call or another alarm takes over: stop rather than talk over it.
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) finish(true)
            }, main)
            .build()
        focus = req
        return audio?.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun finish(played: Boolean) {
        val cb = onEnd
        onEnd = null
        release()
        cb?.invoke(played)
    }

    private fun release() {
        main.removeCallbacks(ramp); main.removeCallbacks(limit)
        player?.let { mp -> runCatching { mp.stop() }; runCatching { mp.release() } }
        player = null
        focus?.let { f -> runCatching { audio?.abandonAudioFocusRequest(f) } }
        focus = null
    }

    companion object {
        private const val STEP_MS = 100L
        private const val FADE_MS = 1500L
        private const val START_GAIN = 0.05f

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
