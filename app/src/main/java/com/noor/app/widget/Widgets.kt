package com.noor.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.noor.alerts.AlertScheduler
import com.noor.app.MainActivity
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.data.AppSettings
import com.noor.app.data.Language
import com.noor.app.data.PrayerProvider
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Every Minhad widget is kept up to date by [Widgets]; a widget's own update or resize just asks for a refresh. */
open class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshFrom(this, context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = Widgets.refreshFrom(this, context)
    override fun onDisabled(context: Context) = Widgets.refreshFrom(this, context)
    /** (0.23) A widget removed: its options go with it. */
    override fun onDeleted(context: Context, ids: IntArray) { WidgetOptionsStore.delete(context, ids); Widgets.forget(ids) }
    /** (0.23) Restored from a backup with new ids: the options follow. */
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) = WidgetOptionsStore.move(context, oldIds, newIds)
}

/**
 * The home-screen widgets (0.9.10; 0.23 six kinds with options): see [WidgetKind].
 *
 * - They say what the home screen and the notification say ([StatusNotification.content]): before a prayer, the time
 *   left (−); for 30 minutes after it, the time since it (+); then the next prayer. The countdown's seconds turn by
 *   themselves, in Minhad's digits (Android 12 and later; [WidgetRender.countdown]); its hours and minutes, the ring
 *   and the bar are redrawn as each minute turns, a partial update that leaves the rest (the sky picture) alone.
 * - They are rebuilt when what they show changes: at each prayer, at the end of a "+" period, at midnight, every 15
 *   minutes for the sky picture (the Sun moves, the weather changes), and when the place, the times, the settings or
 *   a widget's own options or size change. Those updates use non-waking alarms: a sleeping phone is never woken for
 *   them; they run as soon as it wakes, the only time a widget can be seen.
 * - Each widget has its own options ([WidgetOptionsStore], chosen in [WidgetConfigActivity]) and chooses its layout for
 *   its size on the screen. (0.24) On Android 12 and later the home screen gets a layout for each size it may give
 *   ([viewsFor]) and shows the one made for the size it really gives; every update is a full one, the minute's included
 *   (a partial update would not reach into such a map), built ahead and handed over on the change ([armNext]).
 */
object Widgets {
    private const val ACTION_REFRESH = "com.noor.app.WIDGET_REFRESH"
    private const val REQUEST = 7310
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    /** The time, for updates that do not say when they are for (a test sets its own). */
    internal var clock: () -> Instant = Instant::now

    internal fun refreshFrom(receiver: BroadcastReceiver, context: Context, aheadOf: Long = 0L) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        scope.launch { try { if (aheadOf > 0L) ahead(app, aheadOf) else refresh(app) } catch (_: Exception) { } finally { pending.finish() } }
    }

    /** How the countdown is shown (see [WidgetRender.countdown]). */
    internal enum class CountMode { WHEEL, MINUTES }

    /** Android 12 and later: the seconds wheel, in Minhad's digits whatever the phone's. Earlier: Android's or Minhad's. */
    internal var wheelSupported: () -> Boolean = { android.os.Build.VERSION.SDK_INT >= 31 }

    /** What each widget was last built with (its design and the countdown's scale, for the tests). */
    private val built = java.util.concurrent.ConcurrentHashMap<Int, Built>()

    /** The countdown's picture last drawn (for the tests). */
    @Volatile internal var lastCount: CountPicture.Spec? = null


    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { refresh(app) } }
    }

    internal fun forget(ids: IntArray) { for (id in ids) built.remove(id) }

    /** What the widget [id] was last built with (for the tests). */
    internal fun builtFor(id: Int): Built? = built[id]

    private fun ids(app: Context, cls: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, cls)) }.getOrNull() ?: IntArray(0)

    /** What every widget is built from at [now]: the settings, language, digits, the times and what the home screen says. */
    internal class Frame(val app: Context, val ctx: Context, val s: AppSettings, val f: Formatter, val rtl: Boolean, val now: Instant,
                         val c: StatusNotification.Content?, val ws: WidgetScene?, val mode: CountMode) {
        val zone: java.time.ZoneId get() = ws?.zone ?: java.time.ZoneId.systemDefault()
    }

    internal suspend fun frame(app: NoorApplication, now: Instant): Frame {
        val s = app.container.settings.current()
        val ctx = localized(app, s)
        val arabic = ctx.resources.configuration.locales[0].language == "ar"
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val f = Formatter(arabic, s.numerals, s.timeFormat)
        val config = WearSync.configFor(s, now.toEpochMilli(), app.container.timetables)
        val place = PrayerProvider.place(s)
        // (0.24) Before Android 12, Minhad's countdown to the minute (Android's own ticking one is in the phone's font).
        val mode = if (wheelSupported()) CountMode.WHEEL else CountMode.MINUTES
        if (config == null || place == null) return Frame(app, ctx, s, f, rtl, now, null, null, mode)
        return Frame(app, ctx, s, f, rtl, now, StatusNotification.content(config, s, now, arabic), WidgetScene(s, place, config, now), mode)
    }

    internal fun ctxFor(fr: Frame, kind: WidgetKind, o: WidgetOptions, wDp: Int, hDp: Int): WidgetCtx {
        // On the smallest sizes a launcher allows (a 2x1 resized to 110x40 dp) the countdown keeps its hours and minutes
        // and lets the seconds go, so the prayer stays beside it uncut. (0.24: the sky widget follows the background
        // chosen, as the others: its arc and Sun over dark, light and the rest.)
        val tiny = wDp < 200 && hDp < 58
        val opts = if (tiny) o.copy(seconds = false) else o
        return WidgetCtx(kind, fr.ctx, fr.app, fr.s, fr.f, fr.rtl, fr.c, fr.now, fr.mode, fr.ws, fr.zone, wDp, hDp, opts, WidgetPalette.of(fr.ctx, opts))
    }

    /** One widget, built (for a placed widget, its settings page's preview and the tests). */
    internal fun build(fr: Frame, kind: WidgetKind, o: WidgetOptions, wDp: Int, hDp: Int, built: (Built) -> Unit = {}): RemoteViews {
        val r = ctxFor(fr, kind, o, wDp, hDp)
        return if (kind.prayers && fr.c == null) WidgetRender.message(r) else WidgetRender.build(r, built)
    }

    /** A widget of [kind] with options [o] at [wDp]×[hDp], as it is at [at] (the settings page's preview, the tests). */
    suspend fun preview(context: Context, kind: WidgetKind, o: WidgetOptions, wDp: Int, hDp: Int, at: Instant? = null): RemoteViews? =
        previewBuilt(context, kind, o, wDp, hDp, at)

    /** As [preview], and what was drawn (the tests). */
    internal suspend fun previewBuilt(context: Context, kind: WidgetKind, o: WidgetOptions, wDp: Int, hDp: Int, at: Instant? = null, built: (Built) -> Unit = {}): RemoteViews? {
        val app = context.applicationContext as? NoorApplication ?: return null
        return build(frame(app, at ?: clock()), kind, o, wDp, hDp, built)
    }

    /** The views a home screen gets for a widget reported at [sizes] (the tests: a map of layouts by size). */
    internal suspend fun previewSized(context: Context, kind: WidgetKind, o: WidgetOptions, sizes: List<Pair<Int, Int>>, at: Instant? = null): RemoteViews? {
        val app = context.applicationContext as? NoorApplication ?: return null
        return viewsFor(frame(app, at ?: clock()), kind, o, sizes)
    }

    /**
     * Rebuilds every Minhad widget on the home screen now and arms the next update (or nothing, if there is none).
     * (0.24) Every update is a full one: each widget's layouts for every size its home screen may give it.
     */
    suspend fun refresh(context: Context, at: Instant? = null) = lock.withLock {
        val app = context.applicationContext as? NoorApplication ?: return@withLock
        val now = at ?: clock()
        val prepared = prepare(app, now) ?: return@withLock
        push(app, prepared)
        armNext(app, prepared)
    }

    /** What an update will hand the home screen: each widget's views, built for [now]. */
    internal class Prepared(val now: Instant, val content: StatusNotification.Content?, val views: Map<Int, RemoteViews>,
                            val built: Map<Int, Built>, val prayers: Boolean, val dates: Boolean, val zone: java.time.ZoneId)

    private suspend fun prepare(app: NoorApplication, now: Instant): Prepared? {
        val all = WidgetKind.entries.associateWith { ids(app, it.provider) }
        if (all.values.all { it.isEmpty() }) { cancelRefresh(app); return null }
        val t0 = System.nanoTime()
        val fr = frame(app, now)
        val t1 = System.nanoTime()
        val manager = AppWidgetManager.getInstance(app)
        val views = HashMap<Int, RemoteViews>(); val builtNow = HashMap<Int, Built>()
        for ((kind, ids) in all) for (id in ids) runCatching {
            val o = WidgetOptionsStore.load(app, id, kind)
            views[id] = viewsFor(fr, kind, o, sizesDp(manager, id, kind)) { builtNow[id] = it }
        }
        lastTimings = "frame ${(t1 - t0) / 1_000_000} ms, views ${(System.nanoTime() - t1) / 1_000_000} ms"
        android.util.Log.i("NoorWidgets", "built: $lastTimings")
        val prayers = all.any { (k, ids) -> k.prayers && ids.isNotEmpty() }
        return Prepared(now, fr.c, views, builtNow, prayers, all[WidgetKind.DATE]?.isNotEmpty() == true, fr.zone)
    }

    private fun push(app: Context, p: Prepared) {
        pushedAtMs = System.currentTimeMillis()
        val manager = AppWidgetManager.getInstance(app)
        for ((id, v) in p.views) runCatching { manager.updateAppWidget(id, v) }
        built.clear(); built.putAll(p.built)
        pushed = p.now
    }

    /**
     * A widget's views for each of its [sizes] (Android 12 and later: the home screen shows the one made for the size it
     * really gives the widget, and each is also made a little shorter, for home screens that give less than they say:
     * Samsung's 4×1 cut its bottom row), or for the first size alone before Android 12. [built] gets the first's design.
     */
    internal fun viewsFor(fr: Frame, kind: WidgetKind, o: WidgetOptions, sizes: List<Pair<Int, Int>>, built: (Built) -> Unit = {}): RemoteViews {
        val first = sizes.first()
        if (android.os.Build.VERSION.SDK_INT < 31 || !sizeMaps()) return build(fr, kind, o, first.first, first.second, built)
        val all = LinkedHashSet<Pair<Int, Int>>()
        for (s in sizes) { all += s; all += s.first to (s.second * 0.84f).toInt() }
        val map = LinkedHashMap<android.util.SizeF, RemoteViews>()
        for ((i, s) in all.take(16).withIndex()) map[android.util.SizeF(s.first.toFloat(), s.second.toFloat())] = build(fr, kind, o, s.first, s.second, if (i == 0) built else { _ -> })
        return if (map.size == 1) map.values.first() else RemoteViews(map)
    }

    /** Whether to hand home screens a map of layouts by size (a test may apply one size's layout itself). */
    internal var sizeMaps: () -> Boolean = { true }

    /** When the widgets were last handed to the home screen, built for that moment. */
    @Volatile private var pushed: Instant? = null

    /**
     * (0.24) The next change, made on its instant: the widgets' minute (the countdown's hours and minutes, the bar, the
     * ring) changes where the countdown does ([StatusTicker.nextChange]), the prayer marked at the change of display
     * ([StatusNotification.Content.refreshAt]), the date at midnight. The views for the instant just after it are built
     * [AHEAD_MS] before, and handed to the home screen [EARLY_MS] before it (the time a home screen takes to draw them),
     * so the minute shown turns with the seconds wheel, which Android turns on the clock's own second. While Minhad's
     * process lives this is timed in it; an alarm (exact where the app may set one, never waking the phone) is the
     * backstop that brings the process back for it.
     */
    private fun armNext(app: Context, p: Prepared) {
        val change = nextChange(p) ?: run { cancelRefresh(app); return }
        val at = change.toEpochMilli()
        armRefresh(app, Instant.ofEpochMilli(at - ALARM_AHEAD_MS), at)
        timer?.cancel()
        if (!inProcess) return
        val waitMs = maxOf(0L, at - AHEAD_MS - clock().toEpochMilli())
        timer = scope.launch {
            kotlinx.coroutines.delay(waitMs)
            try { ahead(app, at) } catch (e: Exception) { timerError = e.toString(); android.util.Log.w("NoorWidgets", "timed update", e) }
        }
    }

    private var timer: kotlinx.coroutines.Job? = null
    /** Whether the process times the next change itself (a test may leave it to the alarm, to step through changes). */
    internal var inProcess = true
    /** The in-process timer's last failure (for the tests and the log). */
    @Volatile internal var timerError: String? = null
    /** How long the last update took to build (for the tests and the log). */
    @Volatile internal var lastTimings = ""
    /** When the last update began to be handed to the home screen (the real clock, ms; for the tests). */
    @Volatile internal var pushedAtMs = 0L

    /** The next moment a widget's look changes after [p] was built, or null. */
    internal fun nextChange(p: Prepared): Instant? {
        val c = p.content
        val nowMs = p.now.toEpochMilli()
        val midnight = p.now.atZone(p.zone).toLocalDate().plusDays(1).atStartOfDay(p.zone).toInstant()
        val candidates = ArrayList<Instant>()
        if (p.dates || p.prayers) candidates += midnight
        if (p.prayers && c != null) {
            candidates += Instant.ofEpochMilli(com.noor.app.notify.StatusTicker.nextChange(c, nowMs, false))
            if (c.refreshAt.isAfter(p.now)) candidates += c.refreshAt
        }
        return candidates.minOrNull()
    }

    /**
     * The update for the change at [at] (wall clock, ms): built now for the instant just after it, handed over just
     * before it. Done once per change (the process's timer and the alarm may both bring it).
     */
    internal suspend fun ahead(context: Context, at: Long) {
        val app = context.applicationContext as? NoorApplication ?: return
        lock.withLock {
            if ((pushed?.toEpochMilli() ?: 0L) >= at) return@withLock
            val p = prepare(app, Instant.ofEpochMilli(at + JUST_AFTER_MS)) ?: return@withLock
            val wait = at - EARLY_MS - clock().toEpochMilli()
            if (wait > 0) kotlinx.coroutines.delay(wait)
            push(app, p)
            armNext(app, p)
        }
    }

    /** How long before a change its views are built (every widget at every size: a few hundred ms on a slow phone). */
    private const val AHEAD_MS = 800L
    /** The alarm's lead: a little more, as an alarm is delivered late by some milliseconds. */
    internal const val ALARM_AHEAD_MS = 900L
    /** How long before a change its views are handed over: about the time a home screen takes to draw them. */
    private const val EARLY_MS = 50L
    /** The views of a change are for this long after it (the countdown's new minute, well clear of the old). */
    private const val JUST_AFTER_MS = 20L

    /** The sizes the widget may be shown at (dp), the launcher's own list on Android 12 and later, portrait first. */
    internal fun sizesDp(manager: AppWidgetManager, id: Int, kind: WidgetKind): List<Pair<Int, Int>> {
        val o = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
        if (o != null && android.os.Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val list = runCatching { o.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) }.getOrNull()
            if (!list.isNullOrEmpty()) return list.map { it.width.toInt() to it.height.toInt() }.filter { it.first > 0 && it.second > 0 }.distinct()
        }
        val minW = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0; val maxH = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val maxW = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0; val minH = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val out = ArrayList<Pair<Int, Int>>()
        if (minW > 0 && maxH > 0) out += minW to maxH
        if (maxW > 0 && minH > 0) out += maxW to minH
        if (out.isEmpty()) out += kind.previewDp
        return out.distinct()
    }

    /** The widget's size on the screen, in dp (portrait width and height), or its kind's usual size. */
    internal fun sizeDp(manager: AppWidgetManager, id: Int, kind: WidgetKind): Pair<Int, Int> {
        val o = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 } ?: kind.previewDp.first
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 } ?: kind.previewDp.second
        return w to h
    }

    /** The route a widget's tap opens in the app (MainActivity reads it): a [WidgetTap]'s name. */
    const val EXTRA_OPEN = "com.noor.app.widget.OPEN"

    internal fun open(app: Context, tap: WidgetTap): PendingIntent = PendingIntent.getActivity(app, 100 + tap.ordinal,
        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_OPEN, tap.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /**
     * Whole seconds of the countdown (or of the time since), as the home screen counts them: the time left rounded up, so
     * it is the prayer's time less the clock's (0.24, Countdowns.timeLeft), the time since rounded down.
     */
    private fun elapsedSeconds(c: StatusNotification.Content, now: Instant): Long =
        maxOf(0L, if (c.elapsed) Duration.between(c.countAt, now).seconds else com.noor.prayer.Countdowns.timeLeft(now, c.countAt).seconds)

    /** "−1:23" / "+13", the part before the seconds, as the home screen writes it (hours only when there are any). */
    internal fun leading(c: StatusNotification.Content, now: Instant, f: Formatter): String {
        val total = elapsedSeconds(c, now)
        val h = total / 3600; val m = (total % 3600) / 60
        return (if (c.elapsed) "+" else StatusNotification.MINUS) + f.digits(if (h > 0) "%d:%02d".format(h, m) else "%02d".format(m))
    }

    /** "−1:23" / "+0:13": hours and whole minutes (before Android 12, where the phone's digits are not Noor's). */
    internal fun minuteCountdown(c: StatusNotification.Content, now: Instant, f: Formatter): String {
        val minutes = elapsedSeconds(c, now) / 60
        return (if (c.elapsed) "+" else StatusNotification.MINUS) + f.digits("%d:%02d".format(minutes / 60, minutes % 60))
    }

    /** The seconds wheel to show and the AnalogClock's time zone, an offset of whole seconds that sets it on the prayer's second. */
    internal class Wheel(val drawable: Int, val zone: String)

    /**
     * The clock turns the wheel to its own second s (it rounds to whole seconds, ticking just after each one). A "down"
     * wheel shows (60 − s) mod 60 and an "up" wheel shows s (tools/widget/SecondsWheel.java). The clock is shifted by
     * whole seconds so that this is the countdown's own seconds, as the home screen counts them: before a prayer at second
     * p of its minute, at real second t + ε the countdown has p − t seconds (mod 60; the time left is rounded up, 0.24):
     * shift −p; after it, t − p: shift −p.
     */
    internal fun wheelFor(c: StatusNotification.Content, arabicDigits: Boolean): Wheel {
        val p = Math.floorMod(c.countAt.epochSecond, 60L).toInt()
        val shift = -p
        val drawable = when {
            arabicDigits && c.elapsed -> R.drawable.noor_wheel_arabic_up
            arabicDigits -> R.drawable.noor_wheel_arabic_down
            c.elapsed -> R.drawable.noor_wheel_latin_up
            else -> R.drawable.noor_wheel_latin_down
        }
        return Wheel(drawable, java.time.ZoneOffset.ofTotalSeconds(shift).id)
    }

    /**
     * When the hours and minutes next change: as the countdown passes a whole minute (a few hundredths of a second after,
     * so the minute built is the new one, while the wheel shows its first second of it).
     */
    internal fun nextMinute(c: StatusNotification.Content, now: Instant): Instant {
        val ms = Duration.between(c.countAt, now).toMillis()   // negative before the prayer
        // Before a prayer the minutes drop one second after the time left is a whole minute (the time left is rounded up,
        // 0.24: −1:27:00 holds through that second, then −1:26:59); after it they rise when the time since reaches one.
        val wait = if (c.elapsed) 60_000L - Math.floorMod(ms, 60_000L) else Math.floorMod(1_000L - ms, 60_000L)
        return now.plusMillis(wait + 30)
    }

    /** The language chosen in Noor (or the phone's). */
    private fun localized(app: Context, s: AppSettings): Context {
        val tag = when (s.language) { Language.ARABIC -> "ar"; Language.ENGLISH -> "en"; Language.SYSTEM -> null } ?: return app
        return app.createConfigurationContext(Configuration(app.resources.configuration).also { it.setLocale(Locale(tag)) })
    }

    /** On the update alarm: the change (wall clock, ms) whose views it brings, built ahead and handed over on time. */
    internal const val EXTRA_AT = "at"

    private fun pending(app: Context, flag: Int, change: Long = 0L): PendingIntent? =
        PendingIntent.getBroadcast(app, REQUEST, Intent(app, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH).putExtra(EXTRA_AT, change),
            flag or PendingIntent.FLAG_IMMUTABLE)

    private fun armRefresh(app: Context, at: Instant, change: Long = 0L) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(app, PendingIntent.FLAG_UPDATE_CURRENT, change) ?: return
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
}

/** The widgets' own update time, a restart, an app update, a clock, time-zone or language change. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        Widgets.refreshFrom(this, context, aheadOf = intent.getLongExtra(Widgets.EXTRA_AT, 0L))
}
