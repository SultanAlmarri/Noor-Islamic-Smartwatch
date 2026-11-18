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
import com.noor.app.data.PrayerProvider
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
 * - Where Android's Chronometer can tick it (the phone's digits are the app's, no Live Update), it is an ordinary
 *   ongoing notification: nothing keeps running. On Android 14 and later the user can swipe it away; it then returns at
 *   the next update (the next prayer), and can be turned off in Settings.
 * - (0.22.4) Where Minhad writes the countdown itself (the phone's digits differ from the app's, or a Live Update), a
 *   foreground service holds it ([StatusCountdownService]), so the countdown keeps ticking when Minhad is closed; where
 *   Android does not let it start, the countdown is written to the minute ([Ticking.seconds]), never frozen seconds.
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
        /** (0.23) The prayer counted to or from, and its day (the widgets name it with or without Jumu'ah). */
        val event: com.noor.prayer.Prayer? = null, val eventDate: java.time.LocalDate? = null,
    ) {
        /** The countdown as the home screen writes it at [now] ("−1:10:34", "+04:12"). */
        fun countdown(now: Instant, f: Formatter): String =
            (if (elapsed) "+" else MINUS) + f.timer(if (elapsed) Duration.between(countAt, now) else com.noor.prayer.Countdowns.timeLeft(now, countAt))
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
            st.next.at, refresh, place, hijri, cd.elapsed, cd.event.at, cd.event.prayer == com.noor.prayer.Prayer.SHURUQ,
            cd.event.prayer, cd.event.date)
    }

    private const val REFRESH_AFTER_MS = 500L

    /** Shows, updates or removes the notification to match the settings now, and arms the next update. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext as? NoorApplication ?: return
        // (Testing only) The tester's notification test, through the same builders, in place of the real one.
        test?.let { t -> val now = Instant.now(); show(app, t.context(app), t.content(now), t.formatter(), t.live(app), t.numerals, now, t.onShown); return }
        val s = app.container.settings.current()
        val nm = NotificationManagerCompat.from(app)
        val config = if (s.statusNotification) WearSync.configFor(s, System.currentTimeMillis(), app.container.timetables) else null
        if (config == null) {
            StatusCountdownService.release(app, remove = true)
            nm.cancel(ID)
            StatusTicker.stop()
            cancelRefresh(app)
            latest = null
            return
        }
        val ctx = localized(app, s)
        val arabic = ctx.resources.configuration.locales[0].language == "ar"
        val now = Instant.now()
        val c = content(config, s, now, arabic).let { k -> k.copy(place = withCity(k.place, PrayerProvider.place(s), arabic)) }
        // (0.22) As a chip in the status bar (Android 16's Live Updates), Android's own chronometer counts, in the phone's
        // digits, as the chip and the Now Bar show them: Minhad does not draw it then.
        // (0.22) Only while the phone really shows it there ([LivePromotion]): where it does not, Minhad's own notification,
        // its large countdown in the app's digits.
        val live = liveWanted(app, s) && !LivePromotion.refused(app)
        chipIcon = s.statusChipIcon
        systemTimer = s.statusChipSystemTimer
        show(app, ctx, c, Formatter(arabic, s.numerals, s.timeFormat), live, s.numerals, now)
    }

    /**
     * Posts the notification for [c] ([live]: as a Live Update), keeps it ticking (the service, the ticker, the minute
     * alarm) and arms its next rebuild. [shown] (the tester's page) hears what was posted.
     */
    private fun show(app: Context, ctx: Context, c: Content, f: Formatter, live: Boolean, numerals: com.noor.app.data.Numerals, now: Instant,
                     shown: ((Shown) -> Unit)? = null) {
        val nm = NotificationManagerCompat.from(app)
        ensureChannel(ctx)
        // (0.23.2) A Live Update counted by Android itself needs nothing of Minhad's running: no ticker, no service.
        val drawn = if (live) !systemTimer else !systemDigitsMatch(app, numerals)
        android.util.Log.d("NoorStatus", "countdown digits: phone ${systemLocale(app).toLanguageTag()}, Noor $numerals: ${if (drawn) "drawn by Noor" else "Android's Chronometer"}; live $live")
        val make = { seconds: Boolean -> if (live) buildLive(ctx, app, c, now, f, seconds) else build(ctx, app, c, now, if (drawn) f else null, seconds) }
        var n = make(true)
        latest = n
        // (0.22.4) Minhad writes the countdown: a foreground service keeps it ticking when the app is closed (the owner:
        // it froze once Minhad was swiped away). Android lets it start only at certain moments ([StatusCountdownService]);
        // where it does not, the countdown is written to the minute, so it is never seen frozen on a second.
        val kept = when {
            drawn && nm.areNotificationsEnabled() -> StatusCountdownService.keep(app)
            else -> { StatusCountdownService.release(app, remove = false); true }
        }
        val seconds = kept || !drawn
        if (!seconds) { n = make(false); latest = n }
        runCatching { nm.notify(ID, n) }
        if (live) LivePromotion.check(app)
        if (drawn) {
            // Noor draws the countdown itself: every second while the screen is on (every minute without the service),
            // and at least at each change of its minute otherwise (non-waking: an asleep phone is not woken; the update
            // runs when it wakes). This alarm is also the safety net if Android ends the service.
            armRefresh(app, minOf(c.refreshAt, com.noor.app.widget.Widgets.nextMinute(c, now)))
            StatusTicker.start(app, Ticking(ctx, app, c, f, live, seconds))
        } else {
            armRefresh(app, c.refreshAt)
            StatusTicker.stop()
        }
        shown?.invoke(Shown(n, live, drawn, kept && drawn, seconds, fitTitle(c.prayer, c.time, countText(c, now, f, seconds), titleFits(ctx), seconds).second))
    }

    /** What [show] posted, for the tester's page. [form]: the first line's spacing ([fitTitle]); [held]: by the service. */
    internal class Shown(val notification: android.app.Notification, val live: Boolean, val drawn: Boolean, val held: Boolean, val seconds: Boolean, val form: Int)

    /** (Testing only) What the tester's notification test shows in place of the real notification. */
    internal interface TestSource {
        val numerals: com.noor.app.data.Numerals
        val onShown: ((Shown) -> Unit)?
        fun context(app: Context): Context
        fun content(now: Instant): Content
        fun formatter(): Formatter
        fun live(app: Context): Boolean
    }

    /** (Testing only) The tester's notification test (testing/NotificationTests); null: the real notification. */
    @Volatile internal var test: TestSource? = null

    /**
     * (0.24) Whether the Live Update carries Minhad's icon (AppSettings.statusChipIcon). Off: a transparent one, so the
     * status-bar chip shows the countdown alone. (The shade's card still names Minhad in its header.)
     */
    @Volatile internal var chipIcon = false

    /** (0.23.2) Whether Android counts the Live Update's timer itself (AppSettings.statusChipSystemTimer). */
    @Volatile internal var systemTimer = false

    /** The notification last built for the bar (what the service shows as it starts). */
    @Volatile internal var latest: android.app.Notification? = null

    /** What the ticker needs to redraw the notification each second without recomputing the day. */
    internal class Ticking(val ctx: Context, val app: Context, val content: Content, val f: Formatter, val live: Boolean = false,
        /** With its seconds (ticked each second); false: to the minute (ticked as its minute changes). */
        val seconds: Boolean = true)

    /** Redraws the notification with Noor's own digits; false once its content is due for a full rebuild. */
    internal fun tick(t: Ticking, now: Instant): Boolean {
        val n = prepare(t, now) ?: return false
        post(t, n)
        return true
    }

    /** The notification as it is at [now]; null where its content is out of date (rebuild it). */
    internal fun prepare(t: Ticking, now: Instant): android.app.Notification? {
        if (!now.isBefore(t.content.refreshAt)) return null
        return if (t.live) buildLive(t.ctx, t.app, t.content, now, t.f, t.seconds) else build(t.ctx, t.app, t.content, now, t.f, t.seconds)
    }

    /** Shows [n] (built by [prepare]). */
    internal fun post(t: Ticking, n: android.app.Notification) {
        latest = n
        runCatching { NotificationManagerCompat.from(t.app).notify(ID, n) }
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
    internal fun build(ctx: Context, app: Context, c: Content, now: Instant, drawn: Formatter? = null, seconds: Boolean = true): android.app.Notification {
        val title = "${c.prayer}  ·  ${c.time}"
        // (0.22: no "Next prayer" in the header any more, the owner's word: the countdown's sign says it.)
        val small = RemoteViews(app.packageName, R.layout.notification_status)
        small.setTextViewText(R.id.status_prayer, title)
        // The date first: if a place name is too long even for the smallest size, only the place is shortened here.
        // (0.22) Collapsed, the place by its own name, the part before the first comma ("Al Barsha South" of "Al Barsha
        // South, Dubai"): the whole of it did not fit beside the date on a Galaxy's narrow row and was cut. The expanded
        // view shows it whole.
        small.setTextViewText(R.id.status_line, listOfNotNull(c.hijri, c.place?.let(::shortPlace)).joinToString(" · "))
        countdown(small, c, now, drawn, seconds)
        val big = RemoteViews(app.packageName, R.layout.notification_status_big)
        big.setTextViewText(R.id.status_prayer, title)
        big.setTextViewText(R.id.status_place, c.place ?: "")
        big.setViewVisibility(R.id.status_place, if (c.place == null) android.view.View.GONE else android.view.View.VISIBLE)
        big.setTextViewText(R.id.status_hijri, c.hijri)
        countdown(big, c, now, drawn, seconds)
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
            // (0.22.4) Shown at once when the service holds it (Android may otherwise wait up to 10 s).
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** The countdown as written by Minhad: with its seconds ("−1:10:34"), or to the minute ("−1:10", h:mm). */
    internal fun countText(c: Content, now: Instant, f: Formatter, seconds: Boolean): String =
        if (seconds) c.countdown(now, f) else com.noor.app.widget.Widgets.minuteCountdown(c, now, f)

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
    internal fun buildLive(ctx: Context, app: Context, c: Content, now: Instant, f: Formatter, seconds: Boolean = true): android.app.Notification {
        val count = countText(c, now, f, seconds)
        val prayer = "${c.prayer}  ·  ${c.time}"
        val fits = titleFits(ctx)
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(if (chipIcon) R.drawable.ic_notification else R.drawable.ic_notification_clear)
            .setColor(0xFFB4883A.toInt())
            // (0.22.2, the owner: more compact) The prayer, its time and the countdown on one line, each kept whole
            // and in its own direction: in a right-to-left shade the prayer stands on the right, the countdown on the
            // left; in a left-to-right one the other way round. Then the place and the Hijri date.
            .setContentTitle(if (systemTimer) styled("\u2068${c.prayer} · ${c.time}\u2069") else fittedTitle(c.prayer, c.time, count, fits, seconds))
            .setContentText(listOfNotNull(c.place, c.hijri).joinToString("  ·  "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(c.place, c.hijri).joinToString("\n")))
            .setShowWhen(systemTimer)
            .apply {
                // (0.23.2) Android's own timer: the chip and the card count on the phone's clock. Before the prayer it counts
                // down to it, its "when" 999 ms later so that the time left reads rounded up, as Minhad counts
                // (Countdowns.timeLeft); after it, up from it.
                if (systemTimer) setUsesChronometer(true).setChronometerCountDown(!c.elapsed)
                    .setWhen(c.countAt.toEpochMilli() + if (c.elapsed) 0L else 999L)
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setLocalOnly(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        // Asks Android to promote it, both ways Android 16 reads: the request in the extras (EXTRA_REQUEST_PROMOTED_ONGOING,
        // public since Android 16's 36.1), and the promoted flag (which Android clears again if it does not promote the
        // notification: the user's switch for Minhad in Android's settings, a phone maker's own rules). The chip's text:
        // the countdown with its sign (chipText).
        n.extras.putBoolean(EXTRA_REQUEST_PROMOTED, true)
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            n.flags = n.flags or android.app.Notification.FLAG_PROMOTED_ONGOING
            val b = android.app.Notification.Builder.recoverBuilder(app, n)
            if (!systemTimer) b.setShortCriticalText(chipText(count))
            return b.build()
                .also { it.flags = it.flags or android.app.Notification.FLAG_PROMOTED_ONGOING; it.extras.putBoolean(EXTRA_REQUEST_PROMOTED, true) }
        }
        return n
    }

    /**
     * The chip's text: the countdown with its sign, "−1:03:32" before a prayer and "+15:21" after it (0.22.2, the owner:
     * 0.22.1 left the minus out), in the app's own digits whatever the phone's language, kept left to right ([ltr]).
     */
    internal fun chipText(countdown: String): String = ltr(countdown)

    /**
     * (0.22.2) A countdown kept left to right inside right-to-left text: the owner's Galaxy, its system in Arabic, wrote
     * "2:08:17−" (the shade lays a notification out right to left, and the sign went to the far end). Isolated (LRI ..
     * PDI, with a left-to-right mark inside for text engines that skip isolates), the sign stays before the digits and
     * the line keeps the shade's own direction and alignment.
     */
    internal fun ltr(text: String): String = "\u2066\u200E$text\u2069"

    /** The Live Update's first line: the prayer and its time, a wide gap, the countdown (each isolated, [ltr]). */
    internal fun titleLine(prayer: String, countdown: String): String = "\u2068$prayer\u2069\u2003\u2003${ltr(countdown)}"

    /**
     * (0.22.3) The Live Update's first line, made to fit (the owner's Galaxy cut "Maghrib · 5:34 PM  −1:46:17" to
     * "−1:…": a notification's title is one line and Android ends it with dots). Nothing is taken out and the order stays
     * (the prayer, its time, the countdown): only the spacing tightens, step by step, until [fits] says the line fits:
     * the roomy form; one gap; no dot between the name and the time; narrow spaces. Only where none fits (a very
     * large font on a narrow phone) the seconds go, so the countdown is never cut.
     */
    internal fun fittedTitle(name: String, time: String, countdown: String, fits: (CharSequence) -> Boolean, seconds: Boolean = true): CharSequence =
        fitTitle(name, time, countdown, fits, seconds).first

    /**
     * [fittedTitle] and the form it chose: 0 the roomy form, 1 one gap, 2 no dot, 3 narrow spaces, 4 the seconds left
     * out (the tester's page reports it).
     */
    internal fun fitTitle(name: String, time: String, countdown: String, fits: (CharSequence) -> Boolean, seconds: Boolean = true): Pair<CharSequence, Int> {
        val nb = time.replace(' ', '\u202F')
        val forms = listOf(
            "\u2068$name · $time\u2069\u2003${ltr(countdown)}",
            "\u2068$name · $time\u2069\u2002${ltr(countdown)}",
            "\u2068$name $time\u2069\u2002${ltr(countdown)}",
            "\u2068$name\u2009$nb\u2069\u2009\u2009${ltr(countdown)}",
        ).map { styled(it, seconds) }
        forms.indexOfFirst(fits).takeIf { it >= 0 }?.let { return forms[it] to it }
        val noSeconds = countdown.substringBeforeLast(':').takeIf { seconds && countdown.count { it == ':' } >= 2 } ?: countdown
        return styled("\u2068$name\u2009$nb\u2069\u2009\u2009${ltr(noSeconds)}", seconds && noSeconds == countdown) to 4
    }

    /**
     * The title as the home screen writes a countdown and a time: the AM/PM mark a little smaller, the seconds a little
     * smaller and quieter; everything else as Android's title. (It also gives the line room: 0.22.3.)
     */
    private fun styled(text: String, seconds: Boolean = true): CharSequence {
        val sb = android.text.SpannableStringBuilder(text)
        // AM/PM (and the Arabic ص/م) after a time's digits.
        Regex("(?<=[\\d\\u0660-\\u0669])[\\u0020\\u202F](AM|PM|\u0635|\u0645)").findAll(text).forEach { m ->
            sb.setSpan(android.text.style.RelativeSizeSpan(0.8f), m.range.first, m.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // The countdown's seconds: the last ":ss" of the countdown (after the second colon of h:mm:ss, or of m:ss).
        val iso = text.lastIndexOf('\u2066')
        if (iso >= 0 && seconds) {
            val cd = text.substring(iso)
            val colon = cd.lastIndexOf(':')
            if (colon > 0 && cd.count { it == ':' } >= 1 && cd.length - colon >= 3) {
                val start = iso + colon
                val end = text.lastIndexOf('\u2069').takeIf { it > start } ?: text.length
                sb.setSpan(android.text.style.RelativeSizeSpan(0.86f), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    /**
     * Whether a title line fits on one line of the shade, measured as the shade draws a notification's title: its own
     * text appearance (size with the phone's font scale), in bold to be safe, across the width the card leaves it
     * (the screen less the shade's margins, the card's padding and the app's icon, as measured on the owner's Galaxy),
     * with a 3 % margin for the maker's own font.
     */
    internal fun titleFits(ctx: Context, screenWidthPx: Int = ctx.resources.displayMetrics.widthPixels): (CharSequence) -> Boolean {
        val dm = ctx.resources.displayMetrics
        val a = ctx.obtainStyledAttributes(android.R.style.TextAppearance_Material_Notification_Title, intArrayOf(android.R.attr.textSize))
        val size = a.getDimension(0, 16f * dm.scaledDensity).let { maxOf(it, 16f * dm.scaledDensity) }
        a.recycle()
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        // (Measured on the owner's Galaxy, 411 dp wide: the title had 278 dp, the screen less 133 dp.)
        val avail = (screenWidthPx - 133f * dm.density) * 0.97f
        return { text -> android.text.Layout.getDesiredWidth(text, paint) <= avail }
    }

    /**
     * (0.22.3) The place with its city, as the widgets write it: "Madinat Al Mataar, Dubai" (the owner). The city is the
     * first part of the place's context that is not the area itself, in the app's language; nothing is added where the
     * name already holds a comma or there is no context.
     */
    internal fun withCity(area: String?, place: com.noor.app.data.ResolvedPlace?, arabic: Boolean): String? {
        if (area == null || area.contains(',') || area.contains('\u060C')) return area
        val context = (if (arabic) place?.contextAr ?: place?.contextEn else place?.contextEn) ?: return area
        val city = context.split(',', '\u060C').map { it.trim() }.firstOrNull { it.isNotEmpty() && it != area } ?: return area
        return area + (if (arabic) "\u060C " else ", ") + city
    }

    /** A place's own name: the part before its first comma (Latin or Arabic), or the whole when it has none. */
    internal fun shortPlace(place: String): String = place.split(',', '\u060C').first().trim().ifEmpty { place }

    /**
     * The live countdown, ticked by Android itself on its own clock (elapsed time, which survives clock changes). It
     * always counts from the prayer's time: before it, Android writes the time left with its own minus sign ("−00:17");
     * after it, with our plus sign ("+04:12"). So if the rebuild at the prayer's time comes a moment late, the
     * countdown passes through "00:00" — never a wrong or doubled sign (a count-down with our own "−" would read
     * "−−00:01").
     */
    private fun countdown(v: RemoteViews, c: Content, now: Instant, drawn: Formatter?, seconds: Boolean = true) {
        if (drawn != null) {
            v.setViewVisibility(R.id.status_countdown, android.view.View.GONE)
            v.setViewVisibility(R.id.status_countdown_text, android.view.View.VISIBLE)
            v.setTextViewText(R.id.status_countdown_text, countText(c, now, drawn, seconds))
            return
        }
        v.setViewVisibility(R.id.status_countdown_text, android.view.View.GONE)
        v.setViewVisibility(R.id.status_countdown, android.view.View.VISIBLE)
        // (0.24) Before the prayer, Android's Chronometer drops the fraction of a second left (8:50:07.4 reads 8:50:07); a
        // base 999 ms later rounds it up, as Minhad counts (Countdowns.timeLeft), so it is the prayer's time less the clock's.
        val base = SystemClock.elapsedRealtime() + Duration.between(now, c.countAt).toMillis() + (if (c.elapsed) 0L else 999L)
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

    /** A context in the language [tag] ("ar", "en"), for the tester's page. */
    internal fun inLanguage(app: Context, tag: String): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).also { it.setLocale(Locale(tag)) })

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
    internal val looper: android.os.Looper = android.os.HandlerThread("NoorStatusTicker").apply { start() }.looper
    private val handler = android.os.Handler(looper)
    @Volatile private var current: StatusNotification.Ticking? = null
    /** How many times it has redrawn the notification (for tests). */
    @Volatile internal var ticks = 0L
    /** Whether a redraw is waiting (never while the screen is off). */
    @Volatile internal var scheduled = false
    /** The moment the next notification is to be shown (wall clock, ms): the countdown's next change. */
    @Volatile private var boundary = 0L
    /** Built ahead of [boundary], waiting to be shown at it. */
    @Volatile private var ready: android.app.Notification? = null

    /**
     * (0.24) Built ahead, shown on the instant: until 0.23 the next second's notification was built only once that second
     * had begun, so building it and handing it to the shade put the chip a little behind the true countdown (the owner:
     * "off by a few milliseconds", one second behind the widget in a screenshot). Now it is built [LEAD_MS] before the
     * countdown changes, for the instant just after the change, and handed to Android [EARLY_MS] before it, the time the
     * shade takes to draw it.
     */
    private val tick = object : Runnable {
        override fun run() {
            scheduled = false
            val t = current ?: return
            val pm = t.app.getSystemService(android.os.PowerManager::class.java)
            if (pm?.isInteractive == false) { stop(); return }
            val at = Instant.ofEpochMilli(boundary + 1)
            val n = StatusNotification.prepare(t, at)
            if (n == null) { current = null; StatusNotification.refreshAsync(t.app); return }
            ready = n
            scheduled = true
            handler.postDelayed(show, maxOf(0L, boundary - EARLY_MS - System.currentTimeMillis()))
        }
    }

    private val show = object : Runnable {
        override fun run() {
            scheduled = false
            val t = current ?: return
            val n = ready ?: return
            ready = null
            StatusNotification.post(t, n)
            ticks++
            schedule(t)
        }
    }

    /** When the countdown next changes, after [now]: as its seconds (or, to the minute, its minutes) turn. */
    internal fun nextChange(c: StatusNotification.Content, now: Long, seconds: Boolean): Long {
        val unit = if (seconds) 1000L else 60_000L
        val ms = now - c.countAt.toEpochMilli()   // negative before the prayer
        // The seconds change where (now − countAt) is a whole second, before and after the prayer. The minutes, before
        // it, one second after the time left is a whole minute (it is rounded up: −8:50:00 holds through that second, then
        // −8:49:59); after it, where the time since reaches a whole minute.
        val at = if (!seconds && !c.elapsed) 1_000L else 0L
        val wait = unit - Math.floorMod(ms - at, unit)
        return now + wait
    }

    private fun schedule(t: StatusNotification.Ticking) {
        val now = System.currentTimeMillis()
        var b = nextChange(t.content, now, t.seconds)
        // Too close to build in time: the change after it (the one now is shown as it stands).
        if (b - LEAD_MS / 4 < now) b = nextChange(t.content, b + 1, t.seconds)
        boundary = b
        scheduled = true
        handler.postDelayed(tick, maxOf(0L, b - LEAD_MS - now))
    }

    /** How long before the change the notification is built (it takes a few hundredths of a second). */
    private val LEAD_MS get() = maxOf(300L, earlyMs + 250L)
    /**
     * How long before the change it is handed to Android, so that the shade, which takes a moment to draw it, shows it on
     * the second (0.23.2: 80 ms by default; 0.23.1's 12 ms still showed it a little late on the owner's Galaxy). Fine-tuned
     * on the phone in Testing → Notification tests (kept in the "noor_status_live" preferences).
     */
    @Volatile internal var earlyMs = 80L
    @Volatile private var timingLoaded = false
    private val EARLY_MS get() = earlyMs
    internal const val EARLY_DEFAULT = 80L

    fun loadTiming(app: Context) { earlyMs = app.getSharedPreferences("noor_status_live", 0).getLong("early_ms", EARLY_DEFAULT) }
    fun saveTiming(app: Context, ms: Long) { earlyMs = ms.coerceIn(-200L, 400L); app.getSharedPreferences("noor_status_live", 0).edit().putLong("early_ms", earlyMs).apply() }

    fun start(app: Context, t: StatusNotification.Ticking) {
        if (!timingLoaded) { timingLoaded = true; loadTiming(app) }
        handler.post {
            current = t
            handler.removeCallbacks(tick); handler.removeCallbacks(show); ready = null; scheduled = false
            val pm = app.getSystemService(android.os.PowerManager::class.java)
            if (pm?.isInteractive != false) schedule(t)
        }
    }

    fun stop() { handler.post { current = null; handler.removeCallbacks(tick); handler.removeCallbacks(show); ready = null; scheduled = false } }

    val running: Boolean get() = current != null
    /** What it is ticking (for tests and the tester's page). */
    internal val ticking: StatusNotification.Ticking? get() = current
}
