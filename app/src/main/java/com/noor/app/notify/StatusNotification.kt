package com.noor.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.noor.alerts.AlertScheduler
import com.noor.app.MainActivity
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.data.AppSettings
import com.noor.app.data.Language
import com.noor.app.data.WearSync
import com.noor.app.util.Formatter
import com.noor.prayer.Countdowns
import com.noor.prayer.HijriDate
import com.noor.prayer.WatchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * The persistent prayer notification (0.9.3): the next prayer and its time, a live countdown with seconds, the Hijri
 * date and the place, always in the notification shade while the user wants it.
 *
 * 0.9.7: the same countdown as the home screen ([Countdowns]): − time left before a prayer; once it has come, + time
 * since it began for a while, then the next prayer's countdown. Collapsed, the place and the Hijri date share a line
 * that shrinks to fit; expanded, each has its own lines and is never cut.
 *
 * - The seconds are ticked by Android itself (a Chronometer counting down inside the notification), so Noor never wakes
 *   up to update them: the notification costs nothing between updates.
 * - It is rebuilt only when what it says changes: at each prayer time, at midnight, and when the place, the times or
 *   the settings change. Those updates use non-waking alarms: they never wake a sleeping phone, and run as soon as the
 *   phone wakes, which is the only time the notification can be seen. At a prayer time the Adhan's own alarm (which
 *   does wake the phone) also refreshes it, so it is right the moment the screen lights up for the Adhan.
 * - It is an ordinary ongoing notification, not a foreground service: nothing keeps running. On Android 14 and later
 *   the user can swipe it away; it then returns at the next update (the next prayer), and can be turned off in Settings.
 * - It is separate from the prayer alerts: silent, low importance, its own notification category.
 */
object StatusNotification {
    const val CHANNEL = "noor_status"
    const val ID = 4300
    private const val ACTION_REFRESH = "com.noor.app.STATUS_REFRESH"
    private const val REQUEST = 7300
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * What the notification says at [now]. Pure, so it is tested without a device.
     * [prayer] and [time] are the prayer counted to (before it) or from (after it, while [elapsed]); [countAt] is its
     * time; [nextAt] is the next prayer's time; [refreshAt] is when the notification must be rebuilt.
     */
    data class Content(
        val prayer: String, val time: String, val line: String, val nextAt: Instant, val refreshAt: Instant,
        val place: String?, val hijri: String, val elapsed: Boolean, val countAt: Instant, val sunrise: Boolean = false,
    ) {
        /** The countdown as the home screen writes it at [now] ("−1:10:34", "+04:12"). */
        fun countdown(now: Instant, f: Formatter): String =
            (if (elapsed) "+" else MINUS) + f.timer(if (elapsed) Duration.between(countAt, now) else Duration.between(now, countAt))
    }

    /** A true minus sign, as on the home screen. */
    const val MINUS = "\u2212"

    fun content(config: WatchConfig, s: AppSettings, now: Instant, arabic: Boolean): Content {
        val f = Formatter(arabic, s.numerals, s.timeFormat)
        val st = config.state(now)
        val cd = Countdowns.of(st, now)
        val today = now.atZone(config.zone).toLocalDate()
        val place = (if (arabic) config.placeAr ?: config.placeEn else config.placeEn).ifBlank { null }
        val hijri = f.hijri(HijriDate.of(today, config.hijriAdjust))
        val midnight = today.plusDays(1).atStartOfDay(config.zone).toInstant()
        // Half a second after the change (the prayer's time, or the end of its "since" period), so the new display is
        // the one built; Android's countdown still reads 0:00 then, never a wrong sign. And just after midnight, for the
        // date.
        val refresh = minOf(cd.changesAt.plusMillis(REFRESH_AFTER_MS), midnight.plusSeconds(1))
        return Content(f.prayerName(cd.event.prayer, cd.event.date), f.timeAt(cd.event.at, config.zone), listOfNotNull(place, hijri).joinToString("  ·  "),
            st.next.at, refresh, place, hijri, cd.elapsed, cd.event.at, cd.event.prayer == com.noor.prayer.Prayer.SHURUQ)
    }

    private const val REFRESH_AFTER_MS = 500L

    /** Shows, updates or removes the notification to match the settings now, and arms the next update. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext as? NoorApplication ?: return
        val s = app.container.settings.current()
        val nm = NotificationManagerCompat.from(app)
        val config = if (s.statusNotification) WearSync.configFor(s, System.currentTimeMillis(), app.container.timetables) else null
        if (config == null) {
            nm.cancel(ID)
            cancelRefresh(app)
            return
        }
        val ctx = localized(app, s)
        val arabic = ctx.resources.configuration.locales[0].language == "ar"
        ensureChannel(ctx)
        val now = Instant.now()
        val c = content(config, s, now, arabic)
        runCatching { nm.notify(ID, build(ctx, app, c, now)) }
        armRefresh(app, c.refreshAt)
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { refresh(app) } }
    }

    internal fun build(ctx: Context, app: Context, c: Content, now: Instant): android.app.Notification {
        val title = "${c.prayer}  ·  ${c.time}"
        val caption = ctx.getString(when { !c.elapsed -> R.string.next_prayer; c.sunrise -> R.string.since_sunrise; else -> R.string.since_prayer })
        val small = RemoteViews(app.packageName, R.layout.notification_status)
        small.setTextViewText(R.id.status_prayer, title)
        // The date first: if a place name is too long even for the smallest size, only the place is shortened here.
        small.setTextViewText(R.id.status_line, listOfNotNull(c.hijri, c.place).joinToString("  ·  "))
        countdown(small, c, now)
        val big = RemoteViews(app.packageName, R.layout.notification_status_big)
        big.setTextViewText(R.id.status_prayer, title)
        big.setTextViewText(R.id.status_caption, caption)
        big.setTextViewText(R.id.status_place, c.place ?: "")
        big.setViewVisibility(R.id.status_place, if (c.place == null) android.view.View.GONE else android.view.View.VISIBLE)
        big.setTextViewText(R.id.status_hijri, c.hijri)
        countdown(big, c, now)
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB4883A.toInt())
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            // Also as plain text, for screen readers, the lock screen and watches that ignore custom views.
            .setContentTitle(title)
            .setContentText(c.line)
            .setSubText(caption)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            // The watch has its own tiles and complications; never mirror this to it.
            .setLocalOnly(true)
            .build()
    }

    /**
     * The live countdown, ticked by Android itself on its own clock (elapsed time, which survives clock changes):
     * counting down to the prayer with a minus sign, or up from it with a plus sign.
     */
    private fun countdown(v: RemoteViews, c: Content, now: Instant) {
        val base = SystemClock.elapsedRealtime() + Duration.between(now, c.countAt).toMillis()
        v.setChronometer(R.id.status_countdown, base, if (c.elapsed) "+%s" else "$MINUS%s", true)
        v.setChronometerCountDown(R.id.status_countdown, !c.elapsed)
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.status_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = ctx.getString(R.string.status_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        })
    }

    /** The language chosen in Noor (or the device's), as AlertNotifications does for the alert categories. */
    private fun localized(app: Context, s: AppSettings): Context {
        val tag = when (s.language) { Language.ARABIC -> "ar"; Language.ENGLISH -> "en"; Language.SYSTEM -> null } ?: return app
        return app.createConfigurationContext(Configuration(app.resources.configuration).also { it.setLocale(Locale(tag)) })
    }

    private fun pending(app: Context, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(app, REQUEST, Intent(app, StatusRefreshReceiver::class.java).setAction(ACTION_REFRESH), flag or PendingIntent.FLAG_IMMUTABLE)

    private fun armRefresh(app: Context, at: Instant) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // Non-waking (RTC): delivered at the time if the phone is awake, otherwise as soon as it wakes.
        runCatching {
            if (AlertScheduler.canScheduleExact(app)) am.setExact(AlarmManager.RTC, at.toEpochMilli(), pi)
            else am.set(AlarmManager.RTC, at.toEpochMilli(), pi)
        }.onFailure { am.set(AlarmManager.RTC, at.toEpochMilli(), pi) }
    }

    private fun cancelRefresh(app: Context) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        pending(app, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    /** Is the notification showing right now? (For tests and the settings screen.) */
    fun isShowing(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == ID } == true
}

/** Its own update time, a restart, an app update, a clock, time-zone or language change: rebuild the notification. */
class StatusRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { StatusNotification.refresh(app) } catch (_: Exception) { } finally { pending.finish() }
        }
    }
}
