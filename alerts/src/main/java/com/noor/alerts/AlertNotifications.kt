package com.noor.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import java.time.Instant
import java.time.LocalDate

/**
 * Notification channels and the notifications Noor posts. Each channel matches one kind of alert, so the user can
 * adjust them individually in system settings, and Noor never changes a channel's sound behind the user's back.
 */
object AlertNotifications {
    /** Shown while the Adhan or a sound plays (the sound itself comes from the player, not the channel). */
    const val CHANNEL_ADHAN = "noor_adhan"
    /** "Notification only": the system notification sound and vibration; follows Do Not Disturb. */
    const val CHANNEL_NOTIFY = "noor_prayer"
    /** "Silent": on the lock screen and in the shade, no sound, no vibration. */
    const val CHANNEL_SILENT = "noor_prayer_silent"

    const val ID_PLAYING = 4100
    private const val ID_PRAYER_BASE = 4200

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val t = context.applicationContext
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ADHAN, t.getString(R.string.noor_channel_adhan), NotificationManager.IMPORTANCE_HIGH).apply {
            description = t.getString(R.string.noor_channel_adhan_desc)
            setSound(null, null); enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(false)
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_NOTIFY, t.getString(R.string.noor_channel_notify), NotificationManager.IMPORTANCE_HIGH).apply {
            description = t.getString(R.string.noor_channel_notify_desc)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SILENT, t.getString(R.string.noor_channel_silent), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = t.getString(R.string.noor_channel_silent_desc)
            setSound(null, null); enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    /** True when Noor may show notifications on [channel] (app-level permission and the channel itself). */
    fun canPost(context: Context, channel: String): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val ch = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channel) ?: return true
        return ch.importance != NotificationManager.IMPORTANCE_NONE
    }

    internal fun contentIntent(context: Context): PendingIntent? {
        val intent = context.alertHost?.launchIntent(context) ?: context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun base(context: Context, channel: String, title: String, body: String, at: Instant): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(context.alertHost?.notificationIcon ?: android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setWhen(at.toEpochMilli()).setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent(context))
            // The phone and the watch alert independently (the user chooses which); never mirror one to the other.
            .setLocalOnly(true)

    /** The ongoing notification while a sound plays, with Stop. */
    internal fun playing(context: Context, title: String, body: String, at: Instant, detail: String?, stopLabel: String): Notification {
        val stop = PendingIntent.getService(context, 1, Intent(context, AdhanService::class.java).setAction(AdhanService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return base(context, CHANNEL_ADHAN, title, body, at)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setSilent(true)
            .apply { if (detail != null) setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$detail")) }
            .addAction(0, stopLabel, stop)
            .setDeleteIntent(stop)
            .build()
    }

    /** The prayer-time notification for notification-only and silent alerts, and after a sound has finished. */
    internal fun post(context: Context, id: Int, channel: String, title: String, body: String, at: Instant, note: String? = null) {
        ensureChannels(context)
        if (!canPost(context, channel)) return
        val n = base(context, channel, title, body, at)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (channel == CHANNEL_NOTIFY) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(90 * 60_000L)
            .apply { if (note != null) setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$note")) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    internal fun idFor(p: Prayer) = ID_PRAYER_BASE + p.ordinal

    fun post(context: Context, config: WatchConfig, p: Prayer, date: LocalDate, at: Instant, channel: String, note: String? = null) {
        val text = AlertText(context, config)
        post(context, idFor(p), channel, text.title(p, date), text.body(p, at), at, note)
    }

    /** A quiet note that a prayer time passed while the device could not alert (switched off or in deep sleep). */
    fun postLate(context: Context, config: WatchConfig, p: Prayer, date: LocalDate, at: Instant) {
        val text = AlertText(context, config)
        post(context, config, p, date, at, CHANNEL_SILENT, text.string(R.string.noor_alert_late, text.time(at)))
    }
}
