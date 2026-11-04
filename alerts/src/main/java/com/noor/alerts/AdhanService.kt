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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
) {
    fun toIntent(context: Context): Intent = Intent(context, AdhanService::class.java).setAction(AdhanService.ACTION_PLAY)
        .putExtra("id", notificationId).putExtra("title", title).putExtra("body", body).putExtra("at", atMs)
        .putExtra("detail", detail).putExtra("stop", stopLabel)
        .putExtra("asset", (source as? SoundSource.Asset)?.path).putExtra("file", (source as? SoundSource.LocalFile)?.file?.absolutePath)
        .putExtra("volume", volumePercent).putExtra("gradual", gradualSeconds).putExtra("max", maxSeconds)
        .putExtra("vibrate", vibrate).putExtra("keep", keepNotification)

    companion object {
        fun from(i: Intent): PlayRequest? {
            val source = i.getStringExtra("asset")?.let { SoundSource.Asset(it) }
                ?: i.getStringExtra("file")?.let { SoundSource.LocalFile(File(it)) } ?: return null
            return PlayRequest(i.getIntExtra("id", AlertNotifications.ID_PLAYING), i.getStringExtra("title") ?: return null,
                i.getStringExtra("body") ?: "", i.getLongExtra("at", System.currentTimeMillis()), i.getStringExtra("detail"),
                i.getStringExtra("stop") ?: "Stop", source, i.getIntExtra("volume", 80), i.getIntExtra("gradual", 0),
                i.getIntExtra("max", 0), i.getBooleanExtra("vibrate", false), i.getBooleanExtra("keep", true))
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
                val n = AlertNotifications.playing(this, req.title, req.body, Instant.ofEpochMilli(req.atMs), req.detail, req.stopLabel)
                ServiceCompat.startForeground(this, AlertNotifications.ID_PLAYING, n,
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
                current?.let { finishNotification(it) }
                current = req
                if (req.vibrate) vibrate(this)
                player.play(req.source, req.volumePercent, req.gradualSeconds, req.maxSeconds) { done(req) }
            }
            ACTION_STOP -> {
                if (player.isPlaying) player.stop() else { stopVibration(this); shutdown() }
            }
            else -> shutdown()
        }
        return START_NOT_STICKY
    }

    private fun done(req: PlayRequest) {
        if (current !== req) return
        stopVibration(this)
        finishNotification(req)
        current = null
        shutdown()
    }

    private fun finishNotification(req: PlayRequest) {
        if (req.keepNotification) AlertNotifications.post(this, req.notificationId, AlertNotifications.CHANNEL_SILENT, req.title, req.body,
            Instant.ofEpochMilli(req.atMs), req.detail)
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player.stop(notify = false)
        stopVibration(this)
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.noor.alerts.PLAY"
        const val ACTION_STOP = "com.noor.alerts.STOP"

        internal fun start(context: Context, req: PlayRequest) =
            ContextCompat.startForegroundService(context, req.toIntent(context))

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, AdhanService::class.java).setAction(ACTION_STOP)) }
        }

        private val PATTERN = longArrayOf(0, 500, 250, 500, 250, 900)

        internal fun vibrate(context: Context) {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(PATTERN, -1)
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            else @Suppress("DEPRECATION") v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }

        internal fun stopVibration(context: Context) { runCatching { vibrator(context)?.cancel() } }

        private fun vibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }
}
