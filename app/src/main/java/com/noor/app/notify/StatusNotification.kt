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
    // (0.18: 4300 was Fajr's reminder's id too: the Fajr Adhan took the bar's notification away for a moment.)
    const val ID = 4600
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
        val cd = Countdowns.of(st, now, s.elapsedAfterPrayer)
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
            StatusTicker.stop()
            cancelRefresh(app)
            return
        }
        val ctx = localized(app, s)
        val arabic = ctx.resources.configuration.locales[0].language == "ar"
        ensureChannel(ctx)
        val now = Instant.now()
        val c = content(config, s, now, arabic)
        // (0.22) As a chip in the status bar (Android 16's Live Updates), Android's own chronometer counts, in the phone's
        // digits, as the chip and the Now Bar show them: Minhad does not draw it then.
        // (0.22) Only while the phone really shows it there ([LivePromotion]): where it does not, Minhad's own notification,
        // its large countdown in the app's digits.
        val live = liveWanted(app, s) && !LivePromotion.refused(app)
        val drawn = live || !systemDigitsMatch(app, s.numerals)
        android.util.Log.d("NoorStatus", "countdown digits: phone ${systemLocale(app).toLanguageTag()}, Noor ${s.numerals}: ${if (drawn) "drawn by Noor" else "Android's Chronometer"}; live $live")
        val f = Formatter(arabic, s.numerals, s.timeFormat)
        runCatching { nm.notify(ID, if (live) buildLive(ctx, app, c, now, f) else build(ctx, app, c, now, if (drawn) f else null)) }
        if (live) LivePromotion.check(app)
        if (drawn) {
            // Noor draws the seconds itself: every second while the screen is on, and at least every minute otherwise
            // (non-waking: an asleep phone is not woken; the update runs when it wakes).
            armRefresh(app, minOf(c.refreshAt, now.plusSeconds(60)))
            StatusTicker.start(app, Ticking(ctx, app, c, f, live))
        } else {
            armRefresh(app, c.refreshAt)
            StatusTicker.stop()
        }
    }

    /** What the ticker needs to redraw the notification each second without recomputing the day. */
    internal class Ticking(val ctx: Context, val app: Context, val content: Content, val f: Formatter, val live: Boolean = false)

    /** Redraws the notification with Noor's own digits; false once its content is due for a full rebuild. */
    internal fun tick(t: Ticking, now: Instant): Boolean {
        if (!now.isBefore(t.content.refreshAt)) return false
        runCatching { NotificationManagerCompat.from(t.app).notify(ID, if (t.live) buildLive(t.ctx, t.app, t.content, now, t.f) else build(t.ctx, t.app, t.content, now, t.f)) }
        return true
    }

    /**
     * The digits Android writes in its own countdown are those of the phone's language (Arabic-Indic for Arabic),
     * whatever Noor's own setting. When they differ from the digits chosen in Noor, Noor draws the countdown itself.
     */
    fun systemDigitsMatch(context: Context, numerals: com.noor.app.data.Numerals): Boolean {
        val want = if (numerals == com.noor.app.data.Numerals.ARABIC_INDIC) '\u0660' else '0'
        return systemZeroDigit(context) == want
    }

    /** The zero Android's Chronometer would write (in the phone's own language); replaceable in tests. */
    internal var systemZeroDigit: (Context) -> Char = { context -> String.format(systemLocale(context), "%d", 0).firstOrNull() ?: '0' }

    /**
     * The phone's own language, which the notification shade (SystemUI) writes in. Not this process's configuration:
     * on Android 13 and later the language chosen in Noor (a per-app language) is applied to this whole process,
     * Resources.getSystem() included, so Noor in Arabic on an English phone would look like an Arabic phone (an
     * Android 15 emulator showed the countdown in the phone's Western digits that way). Android is asked instead.
     */
    fun systemLocale(context: Context): java.util.Locale {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            runCatching { context.getSystemService(android.app.LocaleManager::class.java)?.systemLocales }.getOrNull()
                ?.takeIf { !it.isEmpty }?.let { return it[0] }
        }
        return android.content.res.Resources.getSystem().configuration.locales[0]
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { refresh(app) } }
    }

    /**
     * The notification. [drawn]: Noor writes the countdown itself with these digits (see [systemDigitsMatch]);
     * null: Android's Chronometer ticks it.
     */
    internal fun build(ctx: Context, app: Context, c: Content, now: Instant, drawn: Formatter? = null): android.app.Notification {
        val title = "${c.prayer}  ·  ${c.time}"
        // (0.22: no "Next prayer" in the header any more, the owner's word: the countdown's sign says it.)
        val small = RemoteViews(app.packageName, R.layout.notification_status)
        small.setTextViewText(R.id.status_prayer, title)
        // The date first: if a place name is too long even for the smallest size, only the place is shortened here.
        // (0.22) Collapsed, the place by its own name, the part before the first comma ("Al Barsha South" of "Al Barsha
        // South, Dubai"): the whole of it did not fit beside the date on a Galaxy's narrow row and was cut. The expanded
        // view shows it whole.
        small.setTextViewText(R.id.status_line, listOfNotNull(c.hijri, c.place?.let(::shortPlace)).joinToString(" · "))
        countdown(small, c, now, drawn)
        val big = RemoteViews(app.packageName, R.layout.notification_status_big)
        big.setTextViewText(R.id.status_prayer, title)
        big.setTextViewText(R.id.status_place, c.place ?: "")
        big.setViewVisibility(R.id.status_place, if (c.place == null) android.view.View.GONE else android.view.View.VISIBLE)
        big.setTextViewText(R.id.status_hijri, c.hijri)
        countdown(big, c, now, drawn)
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
     * (0.22) The countdown as a chip in the status bar, as Samsung's Clock shows its timer: wanted in Settings, and
     * Android 16 or later (its Live Updates). Whether Android shows it is Android's (and the user's) decision: it can be
     * turned off for Minhad in Android's settings ([promotedAllowed]).
     */
    fun liveWanted(context: Context, s: AppSettings): Boolean = s.statusBarChip && android.os.Build.VERSION.SDK_INT >= 36

    /** Whether Android lets Minhad's notifications be promoted to the status bar (always false before Android 16). */
    fun promotedAllowed(context: Context): Boolean = android.os.Build.VERSION.SDK_INT >= 36 &&
        runCatching { context.getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() == true }.getOrDefault(false)

    /** The key Android 16 reads for a notification that asks to be promoted (Notification.EXTRA_REQUEST_PROMOTED_ONGOING). */
    internal const val EXTRA_REQUEST_PROMOTED = "android.requestPromotedOngoing"

    /**
     * (0.22) The notification as a Live Update: Android's own template (Android promotes no custom view). Its title is
     * the countdown, written by Minhad in the app's own digits (Android's chronometer writes the phone's: Arabic-Indic
     * on the owner's Arabic Galaxy with Western digits chosen in Minhad, and small, in the card's header); the prayer
     * and its time below, then the place and the Hijri date. The chip in the status bar shows the same countdown
     * ([Notification.Builder.setShortCriticalText]); the ticker writes it again every second while the screen is on.
     */
    internal fun buildLive(ctx: Context, app: Context, c: Content, now: Instant, f: Formatter): android.app.Notification {
        val count = c.countdown(now, f)
        val prayer = "${c.prayer}  ·  ${c.time}"
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB4883A.toInt())
            // (0.22.2, the owner: more compact) The prayer, its time and the countdown on one line, each kept whole
            // and in its own direction: in a right-to-left shade the prayer stands on the right, the countdown on the
            // left; in a left-to-right one the other way round. Then the place and the Hijri date.
            .setContentTitle(titleLine(prayer, count))
            .setContentText(listOfNotNull(c.place, c.hijri).joinToString("  ·  "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(c.place, c.hijri).joinToString("\n")))
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setLocalOnly(true)
            .build()
        // Asks Android to promote it, both ways Android 16 reads: the request in the extras (EXTRA_REQUEST_PROMOTED_ONGOING,
        // public since Android 16's 36.1), and the promoted flag (which Android clears again if it does not promote the
        // notification: the user's switch for Minhad in Android's settings, a phone maker's own rules). The chip's text:
        // the countdown without its minus sign (the chip is about what is coming), with its plus sign after a prayer.
        n.extras.putBoolean(EXTRA_REQUEST_PROMOTED, true)
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            n.flags = n.flags or android.app.Notification.FLAG_PROMOTED_ONGOING
            return android.app.Notification.Builder.recoverBuilder(app, n).setShortCriticalText(chipText(count)).build()
                .also { it.flags = it.flags or android.app.Notification.FLAG_PROMOTED_ONGOING; it.extras.putBoolean(EXTRA_REQUEST_PROMOTED, true) }
        }
        return n
    }

    /** The chip's text: the countdown without its minus sign ("1:03:32"), or with its plus after a prayer ("+15:21"). */
    internal fun chipText(countdown: String): String = ltr(countdown.removePrefix(MINUS))

    /**
     * (0.22.2) A countdown kept left to right inside right-to-left text: the owner's Galaxy, its system in Arabic, wrote
     * "2:08:17−" (the shade lays a notification out right to left, and the sign went to the far end). Isolated (LRI ..
     * PDI, with a left-to-right mark inside for text engines that skip isolates), the sign stays before the digits and
     * the line keeps the shade's own direction and alignment.
     */
    internal fun ltr(text: String): String = "\u2066\u200E$text\u2069"

    /** The Live Update's first line: the prayer and its time, a wide gap, the countdown (each isolated, [ltr]). */
    internal fun titleLine(prayer: String, countdown: String): String = "\u2068$prayer\u2069\u2003\u2003${ltr(countdown)}"

    /** A place's own name: the part before its first comma (Latin or Arabic), or the whole when it has none. */
    internal fun shortPlace(place: String): String = place.split(',', '\u060C').first().trim().ifEmpty { place }

    /**
     * The live countdown, ticked by Android itself on its own clock (elapsed time, which survives clock changes). It
     * always counts from the prayer's time: before it, Android writes the time left with its own minus sign ("−00:17");
     * after it, with our plus sign ("+04:12"). So if the rebuild at the prayer's time comes a moment late, the
     * countdown passes through "00:00" — never a wrong or doubled sign (a count-down with our own "−" would read
     * "−−00:01").
     */
    private fun countdown(v: RemoteViews, c: Content, now: Instant, drawn: Formatter?) {
        if (drawn != null) {
            v.setViewVisibility(R.id.status_countdown, android.view.View.GONE)
            v.setViewVisibility(R.id.status_countdown_text, android.view.View.VISIBLE)
            v.setTextViewText(R.id.status_countdown_text, c.countdown(now, drawn))
            return
        }
        v.setViewVisibility(R.id.status_countdown_text, android.view.View.GONE)
        v.setViewVisibility(R.id.status_countdown, android.view.View.VISIBLE)
        val base = SystemClock.elapsedRealtime() + Duration.between(now, c.countAt).toMillis()
        v.setChronometer(R.id.status_countdown, base, if (c.elapsed) "+%s" else null, true)
        v.setChronometerCountDown(R.id.status_countdown, false)
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

/**
 * Ticks the notification each second while the screen is on, when Noor draws the countdown itself (0.9.8). Stops when
 * the screen goes off, when the notification is turned off, or when Android's own digits match Noor's again. At each
 * prayer, midnight or the end of a "+" period it asks for a full rebuild. It runs on a thread of its own (up to 0.18.1
 * it ran on the main thread, where building and posting the notification each second competed with the screen).
 */
/**
 * (0.22) Whether the phone really shows the countdown in the status bar. Android decides (the user can turn it off for
 * Minhad; a phone maker may add rules of its own: on the owner's Galaxy the first 0.22 showed no chip at all), and says
 * so in the notification it keeps: [android.app.Notification.FLAG_PROMOTED_ONGOING] stays only on a promoted one. A
 * moment after each Live Update is posted, Minhad looks; where it was not promoted, Minhad's own notification comes
 * back (its large countdown) and Live Updates are not tried again for a day, or until the user's switch, Android's
 * permission for Minhad or the app's version changes. [state] tells Settings what happened.
 */
internal object LivePromotion {
    enum class State { PROMOTED, NOT_ALLOWED, REFUSED }

    /** What the last look found (null: not looked yet in this run of the app). */
    val state = androidx.compose.runtime.mutableStateOf<State?>(null)
    private const val PREFS = "noor_status_live"
    private const val K_AT = "refused_at"
    private const val K_KEY = "refused_key"
    private const val RETRY_MS = 24 * 3600_000L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** What a refusal is remembered against: a change of any of these tries again. */
    private fun key(app: Context): String = "${StatusNotification.promotedAllowed(app)}|" + runCatching {
        @Suppress("DEPRECATION") app.packageManager.getPackageInfo(app.packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
    }.getOrDefault(0L)

    fun refused(app: Context): Boolean {
        val p = app.getSharedPreferences(PREFS, 0)
        val at = p.getLong(K_AT, 0L)
        val r = at > 0 && System.currentTimeMillis() - at < RETRY_MS && p.getString(K_KEY, null) == key(app)
        if (r && state.value == null) state.value = if (StatusNotification.promotedAllowed(app)) State.REFUSED else State.NOT_ALLOWED
        return r
    }

    /** Forgets a refusal (the user turned the switch on again): the next update tries a Live Update again. */
    fun retry(app: Context) { app.getSharedPreferences(PREFS, 0).edit().clear().apply(); state.value = null }

    /** A moment after a Live Update was posted: was it promoted? If not, Minhad's own notification comes back. */
    fun check(app: Context) {
        if (android.os.Build.VERSION.SDK_INT < 36) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            val nm = app.getSystemService(android.app.NotificationManager::class.java) ?: return@postDelayed
            val n = runCatching { nm.activeNotifications.firstOrNull { it.id == StatusNotification.ID } }.getOrNull()?.notification ?: return@postDelayed
            val promoted = n.flags and android.app.Notification.FLAG_PROMOTED_ONGOING != 0
            android.util.Log.i("NoorStatus", "Live Update promoted: $promoted (allowed for Minhad: ${StatusNotification.promotedAllowed(app)}, " +
                "promotable: ${runCatching { n.hasPromotableCharacteristics() }.getOrNull()})")
            if (promoted) { state.value = State.PROMOTED; app.getSharedPreferences(PREFS, 0).edit().clear().apply(); return@postDelayed }
            state.value = if (StatusNotification.promotedAllowed(app)) State.REFUSED else State.NOT_ALLOWED
            app.getSharedPreferences(PREFS, 0).edit().putLong(K_AT, System.currentTimeMillis()).putString(K_KEY, key(app)).apply()
            StatusNotification.refreshAsync(app)
        }, 1500L)
    }
}

internal object StatusTicker {
    private val handler = android.os.Handler(android.os.HandlerThread("NoorStatusTicker").apply { start() }.looper)
    @Volatile private var current: StatusNotification.Ticking? = null
    private val tick = object : Runnable {
        override fun run() {
            val t = current ?: return
            val pm = t.app.getSystemService(android.os.PowerManager::class.java)
            if (pm?.isInteractive == false) { stop(); return }
            if (!StatusNotification.tick(t, Instant.now())) { current = null; StatusNotification.refreshAsync(t.app); return }
            // On the next whole second, so the seconds change in step with the clock.
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L + 5L)
        }
    }

    fun start(app: Context, t: StatusNotification.Ticking) {
        handler.post {
            current = t
            handler.removeCallbacks(tick)
            val pm = app.getSystemService(android.os.PowerManager::class.java)
            if (pm?.isInteractive != false) handler.postDelayed(tick, 1000L - System.currentTimeMillis() % 1000L + 5L)
        }
    }

    fun stop() { handler.post { current = null; handler.removeCallbacks(tick) } }

    val running: Boolean get() = current != null
}
