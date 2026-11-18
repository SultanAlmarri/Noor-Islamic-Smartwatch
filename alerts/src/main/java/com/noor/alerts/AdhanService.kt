package com.noor.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.app.ActivityManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant

/** Everything the service needs, prepared by the dispatcher so the service can show its notification at once. */
internal data class PlayRequest(
    val notificationId: Int,
    val title: String,
    val body: String,
    val atMs: Long,
    val detail: String?,
    val stopLabel: String,
    val source: SoundSource,
    val volumePercent: Int,
    val gradualSeconds: Int,
    val maxSeconds: Int,
    val vibrate: Boolean,
    /** Post a regular prayer notification when the sound ends, so the time stays visible. */
    val keepNotification: Boolean,
    /** The alert-history entry to complete when the sound ends (0 = previews and tests outside the history). */
    val logId: Long = 0,
    /** Played if [source] cannot be played (Noor's default short Adhan or chime). */
    val fallback: SoundSource? = null,
    val fallbackNote: String? = null,
    val failedNote: String? = null,
    /** 0.17: an alarm ([PrayerAlarm][com.noor.prayer.alerts.PrayerAlarm] id), 0 for the Adhan and its tests. */
    val alarmId: Int = 0,
    /** With an alarm: the minutes Snooze puts it off by (0 = no Snooze). */
    val snoozeMinutes: Int = 0,
    /** Played again and again until stopped (alarms), within [maxSeconds]. */
    val loop: Boolean = false,
    /** An alarm's prayer date, for Snooze. */
    val alarmDate: String? = null,
    /** The Snooze button's words ("Snooze 10 min"), in the user's language and digits; null for none. */
    val snoozeLabel: String? = null,
    /** A tried alarm (not saved, or not due): the alarm itself, so its Snooze rings the same again. */
    val alarmText: String? = null,
    /** 0.22: when the next prayer begins (0 = unknown): the prayer's notification left after the sound goes then. */
    val nextPrayerAtMs: Long = 0L,
) {
    fun toIntent(context: Context): Intent = Intent(context, AdhanService::class.java).setAction(AdhanService.ACTION_PLAY)
        .putExtra("id", notificationId).putExtra("title", title).putExtra("body", body).putExtra("at", atMs)
        .putExtra("detail", detail).putExtra("stop", stopLabel)
        .putExtra("asset", (source as? SoundSource.Asset)?.path).putExtra("file", (source as? SoundSource.LocalFile)?.file?.absolutePath)
        .putExtra("uri", (source as? SoundSource.System)?.uri).putExtra("fbUri", (fallback as? SoundSource.System)?.uri)
        .putExtra("alarm", alarmId).putExtra("snooze", snoozeMinutes).putExtra("loop", loop).putExtra("alarmDate", alarmDate).putExtra("snoozeLabel", snoozeLabel).putExtra("alarmText", alarmText)
        .putExtra("volume", volumePercent).putExtra("gradual", gradualSeconds).putExtra("max", maxSeconds)
        .putExtra("vibrate", vibrate).putExtra("keep", keepNotification)
        .putExtra("log", logId)
        .putExtra("fbAsset", (fallback as? SoundSource.Asset)?.path).putExtra("fbFile", (fallback as? SoundSource.LocalFile)?.file?.absolutePath)
        .putExtra("fbNote", fallbackNote).putExtra("failNote", failedNote).putExtra("nextAt", nextPrayerAtMs)

    companion object {
        fun from(i: Intent): PlayRequest? {
            val source = i.getStringExtra("asset")?.let { SoundSource.Asset(it) }
                ?: i.getStringExtra("file")?.let { SoundSource.LocalFile(File(it)) }
                ?: i.getStringExtra("uri")?.let { SoundSource.System(it) } ?: return null
            return PlayRequest(i.getIntExtra("id", AlertNotifications.ID_PLAYING), i.getStringExtra("title") ?: return null,
                i.getStringExtra("body") ?: "", i.getLongExtra("at", System.currentTimeMillis()), i.getStringExtra("detail"),
                i.getStringExtra("stop") ?: "Stop", source, i.getIntExtra("volume", 80), i.getIntExtra("gradual", 0),
                i.getIntExtra("max", 0), i.getBooleanExtra("vibrate", false), i.getBooleanExtra("keep", true),
                logId = i.getLongExtra("log", 0L),
                fallback = i.getStringExtra("fbAsset")?.let { SoundSource.Asset(it) } ?: i.getStringExtra("fbFile")?.let { SoundSource.LocalFile(File(it)) }
                    ?: i.getStringExtra("fbUri")?.let { SoundSource.System(it) },
                fallbackNote = i.getStringExtra("fbNote"), failedNote = i.getStringExtra("failNote"),
                alarmId = i.getIntExtra("alarm", 0), snoozeMinutes = i.getIntExtra("snooze", 0), loop = i.getBooleanExtra("loop", false),
                alarmDate = i.getStringExtra("alarmDate"), snoozeLabel = i.getStringExtra("snoozeLabel"),
                alarmText = i.getStringExtra("alarmText"), nextPrayerAtMs = i.getLongExtra("nextAt", 0L))
        }
    }
}

/**
 * Plays the Adhan (or another alert sound) as a foreground service, so it keeps playing with the screen off and the
 * user always has a visible Stop button. The service exists only while a sound plays.
 */
class AdhanService : Service() {
    private lateinit var player: AlertPlayer
    private var current: PlayRequest? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        player = AlertPlayer(this)
        AlertNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val req = intent.let(PlayRequest::from)
                if (req == null) { stopSelf(); return START_NOT_STICKY }
                val n = AlertNotifications.playing(this, req.title, req.body, Instant.ofEpochMilli(req.atMs), req.detail, req.stopLabel, snooze(req))
                ServiceCompat.startForeground(this, AlertNotifications.ID_PLAYING, n,
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
                current?.let { old ->
                    finishNotification(old, null)
                    // (0.17: an alarm cut short by the Adhan, or by another alarm, stays in the shade, so it is not lost.)
                    if (old.alarmId != 0) AlertNotifications.post(this, old.notificationId, AlertNotifications.CHANNEL_SILENT, old.title, old.body, Instant.ofEpochMilli(old.atMs),
                        timeoutMs = AlertNotifications.prayerNoteStays(old.nextPrayerAtMs))
                }
                // (An alarm's vibration repeats until stopped: it never runs on into what replaces it.)
                stopVibration(this)
                current = req
                playing.value = Playing(req.title, req.body, req.atMs, req.alarmId, snooze(req))
                // (An alarm vibrates until it is stopped, as an alarm clock does.)
                if (req.vibrate) vibrate(this, repeat = req.loop)
                // Claim audio only once Android counts this service as running in the foreground (normally at once;
                // at most a second): on Android 17 a request made a moment too early is refused as from the background.
                whenForeground(SystemClock.elapsedRealtime()) { if (current === req) start(req, req.source, usingFallback = false) }
            }
            ACTION_STOP -> {
                if (player.isPlaying) player.stop() else { stopVibration(this); shutdown() }
            }
            // 0.17: an alarm put off for its snooze minutes: armed again, and a note says until when.
            ACTION_SNOOZE -> {
                val req = current
                if (req != null && req.alarmId != 0 && req.snoozeMinutes > 0) {
                    current = null
                    player.stop(notify = false)
                    stopVibration(this)
                    AlertScheduler.snooze(this, req)
                }
                shutdown()
            }
            else -> shutdown()
        }
        return START_NOT_STICKY
    }

    private fun start(req: PlayRequest, source: SoundSource, usingFallback: Boolean) {
        player.play(source, req.volumePercent, req.gradualSeconds, req.maxSeconds, req.loop) { result -> ended(req, result, usingFallback) }
    }

    private fun whenForeground(since: Long, action: () -> Unit) {
        val info = ActivityManager.RunningAppProcessInfo()
        runCatching { ActivityManager.getMyMemoryState(info) }
        if (info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE ||
            SystemClock.elapsedRealtime() - since >= FOREGROUND_WAIT_MS) action()
        else main.postDelayed({ whenForeground(since, action) }, 50)
    }

    /** One play ended: record it, or, if nothing was heard, try the next way of making the prayer heard. */
    private fun ended(req: PlayRequest, result: PlayResult, usingFallback: Boolean) {
        if (current !== req) return
        if (result.played) {
            log(req) { it.copy(outcome = if (usingFallback) AlertOutcome.PLAYED_FALLBACK else AlertOutcome.PLAYED,
                playedMs = result.playedMs, stoppedByUser = result.stoppedByUser,
                detail = listOfNotNull(it.detail, result.report).joinToString("; ").ifEmpty { null }) }
            // (0.17: an alarm nobody stopped, silenced after its ten minutes, stays in the shade, as a missed alarm does.)
            if (req.alarmId != 0 && !result.stoppedByUser) AlertNotifications.post(this, req.notificationId, AlertNotifications.CHANNEL_SILENT, req.title, req.body,
                Instant.ofEpochMilli(req.atMs), timeoutMs = AlertNotifications.prayerNoteStays(req.nextPrayerAtMs))
            done(req, note = if (usingFallback) req.fallbackNote else null)
            return
        }
        val fb = req.fallback
        if (!usingFallback && fb != null && !result.stoppedByUser) {
            log(req) { it.copy(detail = "chosen sound: ${result.error}") }
            val n = AlertNotifications.playing(this, req.title, req.body, Instant.ofEpochMilli(req.atMs),
                listOfNotNull(req.detail, req.fallbackNote).joinToString("\n"), req.stopLabel, snooze(req))
            runCatching { androidx.core.app.NotificationManagerCompat.from(this).notify(AlertNotifications.ID_PLAYING, n) }
            start(req, fb, usingFallback = true)
            return
        }
        if (result.stoppedByUser) {
            log(req) { it.copy(outcome = AlertOutcome.PLAYED, playedMs = 0, stoppedByUser = true,
                detail = listOfNotNull(it.detail, result.report).joinToString("; ").ifEmpty { null }) }
            done(req, note = null); return
        }
        // Noor's own player could not play anything on this device: Android's notification system plays the backup Adhan.
        log(req) { it.copy(outcome = AlertOutcome.BACKUP_SOUND, detail = listOfNotNull(it.detail, "player: ${result.error}", result.report).joinToString("; ")) }
        stopVibration(this)
        current = null
        // (An alarm never falls back to the backup Adhan: an Adhan before or after its time would mislead. A plain
        // notification, with the phone's notification sound, says the alarm's time instead.)
        if (req.alarmId != 0) AlertNotifications.post(this, req.notificationId, AlertNotifications.CHANNEL_NOTIFY, req.title, req.body,
            Instant.ofEpochMilli(req.atMs), req.failedNote, AlertNotifications.prayerNoteStays(req.nextPrayerAtMs))
        else AlertNotifications.postBackup(this, req.notificationId, req.title, req.body, Instant.ofEpochMilli(req.atMs),
            listOfNotNull(req.detail, req.failedNote).joinToString("\n").ifEmpty { null }, AlertNotifications.prayerNoteStays(req.nextPrayerAtMs))
        shutdown()
    }

    private fun log(req: PlayRequest, change: (AlertRecord) -> AlertRecord) {
        if (req.logId != 0L) runCatching { AlertLog.update(this, req.logId, change) }
    }

    private fun done(req: PlayRequest, note: String?) {
        if (current !== req) return
        stopVibration(this)
        finishNotification(req, note)
        current = null
        shutdown()
    }

    /** What is playing now, for the Adhan screen. */
    data class Playing(val title: String, val body: String, val atMs: Long, val alarmId: Int = 0, val snoozeLabel: String? = null)

    /** The Snooze button's label for [req] ("Snooze 10 min"), or null for none. */
    private fun snooze(req: PlayRequest): String? = if (req.alarmId != 0 && req.snoozeMinutes > 0) req.snoozeLabel else null

    private fun finishNotification(req: PlayRequest, note: String?) {
        if (req.keepNotification) AlertNotifications.post(this, req.notificationId, AlertNotifications.CHANNEL_SILENT, req.title, req.body,
            Instant.ofEpochMilli(req.atMs), listOfNotNull(req.detail, note).joinToString("\n").ifEmpty { null },
            AlertNotifications.prayerNoteStays(req.nextPrayerAtMs))
    }

    private fun shutdown() {
        current = null
        playing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        player.stop(notify = false)
        stopVibration(this)
        playing.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.noor.alerts.PLAY"
        const val ACTION_STOP = "com.noor.alerts.STOP"
        const val ACTION_SNOOZE = "com.noor.alerts.SNOOZE"
        private const val FOREGROUND_WAIT_MS = 1_000L

        private val playing = MutableStateFlow<Playing?>(null)
        /** The alert sounding now (null when none), for the Adhan screen. */
        val nowPlaying: StateFlow<Playing?> get() = playing

        internal fun start(context: Context, req: PlayRequest) =
            ContextCompat.startForegroundService(context, req.toIntent(context))

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, AdhanService::class.java).setAction(ACTION_STOP)) }
        }

        /** 0.17: puts the alarm ringing now off by its snooze minutes. */
        fun snooze(context: Context) {
            runCatching { context.startService(Intent(context, AdhanService::class.java).setAction(ACTION_SNOOZE)) }
        }


        private val PATTERN = longArrayOf(0, 500, 250, 500, 250, 900)
        /** An alarm's: repeated until it stops. */
        private val ALARM_PATTERN = longArrayOf(0, 700, 600)

        internal fun vibrate(context: Context, repeat: Boolean = false) {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return
            val effect = if (repeat) VibrationEffect.createWaveform(ALARM_PATTERN, 0) else VibrationEffect.createWaveform(PATTERN, -1)
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            else @Suppress("DEPRECATION") v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }

        internal fun stopVibration(context: Context) { runCatching { vibrator(context)?.cancel() } }

        private fun vibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }
}
