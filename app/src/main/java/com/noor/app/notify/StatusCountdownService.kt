package com.noor.app.notify

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * (0.22.4) Keeps the status notification's countdown ticking when Minhad is closed (the owner: "sometimes it doesn't
 * work when I close the application totally ... the countdown timer freezes").
 *
 * Where Minhad writes the countdown itself (the phone's digits differ from the app's, or a Live Update, whose title and
 * status-bar chip are Minhad's), it was rewritten each second by [StatusTicker] inside the app's process; once the
 * user swiped Minhad away, the process ended and the countdown froze until the next minute alarm. This foreground
 * service holds the notification while it is shown that way, so Android keeps the process (and the ticker) alive:
 *
 * - Android's documented way to keep a user-chosen, always-visible task running: a foreground service of the type
 *   "specialUse" (Android 14+), with its reason written in the manifest. Not sticky to the task (`stopWithTask` false),
 *   restarted by Android if it is ended ([START_STICKY]).
 * - It costs nothing while the screen is off: no wakelock, no alarm that wakes the phone; the ticker stops on
 *   SCREEN_OFF and starts again on SCREEN_ON / USER_PRESENT (received here, so without waiting for an alarm).
 * - It stops itself when the notification is turned off, or when Android's own Chronometer can tick it (the phone's
 *   digits are the app's and no Live Update): [StatusNotification.refresh] decides, [keep] and [release] do.
 *
 * When Android lets it start (Android 12 and later forbid starting a foreground service from the background, except in
 * documented cases): while Minhad is on screen; on BOOT_COMPLETED and MY_PACKAGE_REPLACED; on TIME_SET,
 * TIMEZONE_CHANGED and LOCALE_CHANGED; from a prayer alarm (an alarm-clock alarm, which needs the exact-alarm
 * permission the Adhan asks for); always where the user turned battery optimisation off for Minhad; always below
 * Android 12. Elsewhere (the minute alarm alone, after Android ended the service) the start is refused
 * ([android.app.ForegroundServiceStartNotAllowedException]): Minhad then writes the countdown to the minute, kept right
 * by the non-waking minute alarm, until the next moment Android allows the service.
 */
class StatusCountdownService : Service() {
    private var screen: BroadcastReceiver? = null
    @Volatile private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onScreen(i.action)
        }
        // System broadcasts only (no exported flag needed for them).
        runCatching {
            registerReceiver(r, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            })
            screen = r
        }
    }

    /** The screen went off: no more ticks. On, or unlocked: the notification rebuilt now and ticking again. */
    internal fun onScreen(action: String?) {
        if (action == Intent.ACTION_SCREEN_OFF) StatusTicker.stop() else StatusNotification.refreshAsync(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Restarted by Android after it ended the process (START_STICKY): nothing is known yet. The notification is
            // rebuilt, and the rebuild asks this service to hold it ([keep]), which Android may refuse.
            StatusNotification.refreshAsync(applicationContext)
            return START_STICKY
        }
        // Started with startForegroundService: Android requires startForeground now, even if no longer wanted.
        val ok = promote(StatusNotification.latest ?: placeholder())
        if (!ok) {
            // (Never a loop: the rebuild below writes the countdown to the minute and does not start this again soon.)
            brokenUntil = SystemClock.elapsedRealtime() + RETRY_AFTER_FAILURE_MS
            StatusNotification.refreshAsync(applicationContext)
            return START_NOT_STICKY
        }
        if (!wanted) { halt(remove = false); return START_NOT_STICKY }
        return START_STICKY
    }

    /** Holds [n] as a foreground notification; false (and stopped) where Android refuses. */
    internal fun promote(n: Notification): Boolean {
        if (foreground) return true
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(StatusNotification.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(StatusNotification.ID, n)
            foreground = true
            android.util.Log.i(TAG, "holding the countdown (foreground)")
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (Android 12+, from the background), or a type Android refuses.
            android.util.Log.w(TAG, "foreground refused: ${e.javaClass.simpleName}: ${e.message}")
            halt(remove = false)
            false
        }
    }

    /** Stops: the notification removed with it ([remove]: it was turned off) or left to the ordinary path. */
    internal fun halt(remove: Boolean) {
        foreground = false
        if (instance === this) instance = null
        runCatching { ServiceCompat.stopForeground(this, if (remove) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH) }
        stopSelf()
    }

    /** Swiped away from the recent apps: the countdown stays (the reason this service exists). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.i(TAG, "Minhad closed from the recent apps: the countdown keeps ticking")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        screen?.let { runCatching { unregisterReceiver(it) } }
        screen = null
        foreground = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Only if the notification was never built (it always is before the start): replaced at once by the real one. */
    private fun placeholder(): Notification {
        StatusNotification.ensureChannel(this)
        return androidx.core.app.NotificationCompat.Builder(this, StatusNotification.CHANNEL)
            .setSmallIcon(com.noor.app.R.drawable.ic_notification)
            .setContentTitle(getString(com.noor.app.R.string.app_name))
            .setOngoing(true).setSilent(true).setLocalOnly(true)
            .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "NoorStatus"
        private const val ACTION_KEEP = "com.noor.app.STATUS_KEEP"
        private const val RETRY_AFTER_FAILURE_MS = 15 * 60_000L
        private val lock = Any()

        @Volatile internal var instance: StatusCountdownService? = null
        /** Whether the notification wants the service now (set by [keep], cleared by [release]). */
        @Volatile internal var wanted = false
        /** After the service itself could not go foreground: not started again before this (elapsed realtime). */
        @Volatile internal var brokenUntil = 0L

        /** Starts the service; replaceable in tests (to stand for Android refusing it). */
        internal var starter: (Context, Intent) -> Unit = { c, i -> ContextCompat.startForegroundService(c, i) }

        /** Is the service holding the notification now? */
        val running: Boolean get() = instance?.foreground == true

        /**
         * Asks for the service to hold the notification ([StatusNotification.latest]). True when it does or is starting;
         * false where Android refuses the start now (the caller writes the countdown to the minute).
         */
        fun keep(context: Context): Boolean = synchronized(lock) {
            val app = context.applicationContext
            wanted = true
            instance?.let { s -> return s.promote(StatusNotification.latest ?: return true) }
            if (SystemClock.elapsedRealtime() < brokenUntil) return false
            return try {
                starter(app, Intent(app, StatusCountdownService::class.java).setAction(ACTION_KEEP))
                true
            } catch (e: Exception) {
                // Android 12+: ForegroundServiceStartNotAllowedException from the background (an IllegalStateException).
                android.util.Log.i(TAG, "service not allowed to start now (${e.javaClass.simpleName}): the countdown to the minute")
                false
            }
        }

        /** The service is not needed (turned off, or Android's Chronometer ticks the notification). */
        fun release(context: Context, remove: Boolean) = synchronized(lock) {
            wanted = false
            instance?.halt(remove)
        }
    }
}
