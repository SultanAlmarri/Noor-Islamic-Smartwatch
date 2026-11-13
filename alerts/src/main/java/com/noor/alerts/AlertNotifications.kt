package com.noor.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
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
    /**
     * Backup Adhan: a short Adhan is the channel's own sound, played by Android itself on the alarm stream. Used only
     * when Android refuses to let Noor start its player (no exact-alarm permission), so the prayer is still heard.
     */
    const val CHANNEL_BACKUP = "noor_adhan_backup"
    /** 0.9.9: reminders before a prayer. The system notification sound; follows Do Not Disturb like any notification. */
    const val CHANNEL_REMIND = "noor_reminder"
    private const val ID_REMIND_BASE = 4300

    const val ID_PLAYING = 4100
    /** Noor's gold, readable on light and dark notification shades. */
    private const val NOOR_GOLD = 0xFFB4883A.toInt()
    private const val ID_PRAYER_BASE = 4200

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Category names follow the language chosen in the app (renaming a channel is allowed; its sound is not touched).
        val t = context.alertHost?.alertLanguage?.takeIf { it == "ar" || it == "en" }?.let { lang ->
            context.applicationContext.createConfigurationContext(
                android.content.res.Configuration(context.resources.configuration).also { it.setLocale(java.util.Locale(lang)) })
        } ?: context.applicationContext
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
        nm.createNotificationChannel(NotificationChannel(CHANNEL_BACKUP, t.getString(R.string.noor_channel_backup), NotificationManager.IMPORTANCE_HIGH).apply {
            description = t.getString(R.string.noor_channel_backup_desc)
            setSound(backupSound(t), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMIND, t.getString(R.string.noor_channel_remind), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = t.getString(R.string.noor_channel_remind_desc)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    internal fun reminderId(p: Prayer) = ID_REMIND_BASE + p.ordinal

    /** 0.9.9: "Asr in 15 minutes", with the prayer's time and the place; it disappears by itself when the prayer begins. */
    fun postReminder(context: Context, config: WatchConfig, due: com.noor.prayer.alerts.DueReminder) {
        ensureChannels(context)
        if (!canPost(context, CHANNEL_REMIND)) return
        val text = AlertText(context, config)
        val untilPrayer = (due.at.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(60_000L)
        val n = base(context, CHANNEL_REMIND, text.reminderTitle(due.prayer, due.date, due.minutesLeft), text.body(due.prayer, due.at), due.at)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(untilPrayer)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(reminderId(due.prayer), n) }
    }

    internal fun cancelReminder(context: Context, p: Prayer) {
        runCatching { NotificationManagerCompat.from(context).cancel(reminderId(p)) }
    }

    /** The short Adhan bundled as a resource, so that Android's notification system can play it itself. */
    fun backupSound(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.noor_backup_adhan}")

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
            .setColor(NOOR_GOLD)
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
            // Not setSilent(): that makes it a "silent" group child, which Android never lets interrupt, so the
            // full-screen Adhan would never open. The channel itself has no sound or vibration (the player plays the
            // Adhan), and updates do not alert again.
            .setOnlyAlertOnce(true)
            .apply { if (detail != null) setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$detail")) }
            .addAction(0, stopLabel, stop)
            .setDeleteIntent(stop)
            .apply { fullScreen(context)?.let { setFullScreenIntent(it, true) } }
            .also { b -> runCatching { context.alertHost?.decoratePlaying(context, ID_PLAYING, b, title) } }
            .build()
    }

    /** The Adhan screen, shown over the lock screen while the Adhan plays (Android shows a heads-up instead if in use). */
    private fun fullScreen(context: Context): PendingIntent? {
        val intent = context.alertHost?.adhanScreen(context) ?: return null
        if (android.os.Build.VERSION.SDK_INT >= 34 && context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != true) return null
        return PendingIntent.getActivity(context, 2, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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

    /** The backup alert: Android plays the short Adhan of [CHANNEL_BACKUP] with it. Alarm category, so DND treats it as an alarm. */
    internal fun postBackup(context: Context, id: Int, title: String, body: String, at: Instant, note: String?) {
        ensureChannels(context)
        if (!canPost(context, CHANNEL_BACKUP)) { post(context, id, CHANNEL_NOTIFY, title, body, at, note); return }
        val n = base(context, CHANNEL_BACKUP, title, body, at)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(90 * 60_000L)
            .apply { if (note != null) setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$note")) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

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
