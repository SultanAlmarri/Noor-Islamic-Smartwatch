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
 *   its size on the screen (the portrait size the launcher reports; a resize rebuilds it). Android 12's map of layouts
 *   by size is not used: a home screen merges a partial update into the widget's views without looking into such a map
 *   (AOSP RemoteViews.mergeRemoteViews), so the countdown's minute would never change.
 */
object Widgets {
    private const val ACTION_REFRESH = "com.noor.app.WIDGET_REFRESH"
    private const val REQUEST = 7310
    /** How often the sky picture is redrawn. */
    private val PICTURE_EVERY: Duration = Duration.ofMinutes(15)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    /** The time, for updates that do not say when they are for (a test sets its own). */
    internal var clock: () -> Instant = Instant::now

    internal fun refreshFrom(receiver: BroadcastReceiver, context: Context, minuteOnly: Boolean = false) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        scope.launch { try { refresh(app, minuteOnly = minuteOnly) } catch (_: Exception) { } finally { pending.finish() } }
    }

    /** How the countdown is shown (see [WidgetRender.countdown]). */
    internal enum class CountMode { WHEEL, TICK, MINUTES }

    /** Android 12 and later: the seconds wheel, in Minhad's digits whatever the phone's. Earlier: Android's or Minhad's. */
    internal var wheelSupported: () -> Boolean = { android.os.Build.VERSION.SDK_INT >= 31 }

    /** A minute's update: only what it sets changes on the home screen (a test applies it to its own simulated widget). */
    internal var partialUpdate: (AppWidgetManager, Int, RemoteViews) -> Unit = { m, id, v -> m.partiallyUpdateAppWidget(id, v) }

    /** When the sky pictures are next due (set by a full update; a minute's update leaves the pictures alone). */
    private var pictureDue: Instant? = null

    /**
     * What the widgets were last built to say (set by a full update), less the moments it changes at. A minute's update
     * changes the countdown alone, so it is only right while the rest is still this: the same prayer, time, sign, date
     * and place.
     */
    private var shown: StatusNotification.Content? = null

    /** What each widget was last built with (its layout, the countdown's scale), for its minute's updates. */
    private val built = java.util.concurrent.ConcurrentHashMap<Int, Built>()

    /** The countdown's picture last drawn (for the tests). */
    @Volatile internal var lastCount: CountPicture.Spec? = null

    private fun said(c: StatusNotification.Content) = c.copy(line = "", nextAt = Instant.EPOCH, refreshAt = Instant.EPOCH)

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
        val mode = when {
            wheelSupported() -> CountMode.WHEEL
            // Android writes the phone's digits; where they are not Minhad's, Minhad writes the countdown itself.
            StatusNotification.systemDigitsMatch(app, s.numerals) -> CountMode.TICK
            else -> CountMode.MINUTES
        }
        if (config == null || place == null) return Frame(app, ctx, s, f, rtl, now, null, null, mode)
        return Frame(app, ctx, s, f, rtl, now, StatusNotification.content(config, s, now, arabic), WidgetScene(s, place, config, now), mode)
    }

    internal fun ctxFor(fr: Frame, kind: WidgetKind, o: WidgetOptions, wDp: Int, hDp: Int): WidgetCtx {
        // The sky widget is always over the sky. On the smallest sizes a launcher allows (a 2x1 resized to 110x40 dp) the
        // countdown keeps its hours and minutes and lets the seconds go, so the prayer stays beside it uncut.
        val tiny = wDp < 200 && hDp < 58
        val opts = (if (kind == WidgetKind.SKY) o.copy(background = WidgetBackground.SKY) else o).let { if (tiny) it.copy(seconds = false) else it }
        return WidgetCtx(kind, fr.ctx, fr.app, fr.s, fr.f, fr.rtl, fr.c, fr.now, fr.mode, fr.ws, fr.zone, wDp, hDp, opts, WidgetPalette.of(fr.ctx, opts))
    }

    /** One widget, built (for a placed widget, its settings page's preview and the tests). */
    internal fun build(fr: Frame, kind: WidgetKind, o: WidgetOptions, wDp: Int, hDp: Int, built: (Built) -> Unit = {}): RemoteViews {
        val r = ctxFor(fr, kind, o, wDp, hDp)
        return if (kind.prayers && fr.c == null) WidgetRender.message(r) else WidgetRender.build(r, built)
    }

    /** A widget of [kind] with options [o] at [wDp]×[hDp], as it is at [at] (the settings page's preview, the tests). */
    suspend fun preview(context: Context, kind: WidgetKind, o: WidgetOptions, wDp: Int, hDp: Int, at: Instant? = null): RemoteViews? {
        val app = context.applicationContext as? NoorApplication ?: return null
        return build(frame(app, at ?: clock()), kind, o, wDp, hDp)
    }

    /**
     * Rebuilds every Minhad widget on the home screen and arms the next update (or nothing, if there is none).
     * [minuteOnly]: only the countdown's hours and minutes (and the rings and bars) changed; the pictures are left as
     * they are.
     */
    suspend fun refresh(context: Context, at: Instant? = null, minuteOnly: Boolean = false) = lock.withLock {
        val app = context.applicationContext as? NoorApplication ?: return@withLock
        val all = WidgetKind.entries.associateWith { ids(app, it.provider) }
        if (all.values.all { it.isEmpty() }) { cancelRefresh(app); return@withLock }
        val now = at ?: clock()
        val fr = frame(app, now)
        val manager = AppWidgetManager.getInstance(app)
        val c = fr.c
        val options = all.mapValues { (kind, ids) -> ids.associateWith { WidgetOptionsStore.load(app, it, kind) } }
        if (c == null) {
            for ((kind, ids) in all) for (id in ids) runCatching {
                val (w, h) = sizeDp(manager, id, kind)
                manager.updateAppWidget(id, build(fr, kind, options[kind]!![id]!!, w, h))
            }
            shown = null
            built.clear()
            // The date widgets still change at midnight.
            if (all[WidgetKind.DATE]?.isNotEmpty() == true) {
                val midnight = now.atZone(fr.zone).toLocalDate().plusDays(1).atStartOfDay(fr.zone).toInstant().plusSeconds(1)
                armRefresh(app, midnight)
            } else cancelRefresh(app)
            return@withLock
        }
        val pictures = pictureDue
        // (0.22) The minute's update armed for the moment the display changes (the end of a "+" period, a prayer with
        // the "+" turned off, midnight) comes a few hundredths of a second after it, before the full update armed for
        // it: it must then rebuild everything. Until 0.22 it changed the countdown alone, and the widgets went on naming
        // the old prayer while counting to the next, until the next picture, up to 15 minutes later.
        val everyIdBuilt = all.values.all { ids -> ids.all { built.containsKey(it) } }
        if (minuteOnly && pictures != null && now.isBefore(pictures) && now.isBefore(c.refreshAt) && shown == said(c) && everyIdBuilt) {
            // A new minute: only the countdown's picture or text, the rings and the bars change (the seconds turn by themselves).
            for ((kind, ids) in all) for (id in ids) runCatching {
                val b = built[id] ?: return@runCatching
                val o = options[kind]!![id]!!
                if (!needsMinute(kind, o, fr.mode, b)) return@runCatching
                val (w, h) = sizeDp(manager, id, kind)
                partialUpdate(manager, id, WidgetRender.minute(ctxFor(fr, kind, o, w, h), b))
            }
            armNext(app, c, now, pictures, minuteWanted(all, options, fr.mode))
            return@withLock
        }
        for ((kind, ids) in all) for (id in ids) runCatching {
            val (w, h) = sizeDp(manager, id, kind)
            manager.updateAppWidget(id, build(fr, kind, options[kind]!![id]!!, w, h) { built[id] = it })
        }
        pictureDue = now.plus(PICTURE_EVERY)
        shown = said(c)
        armNext(app, c, now, now.plus(PICTURE_EVERY), minuteWanted(all, options, fr.mode))
    }

    /** Whether a widget changes as each minute turns: a countdown not ticked by Android, a ring or a bar. */
    private fun needsMinute(kind: WidgetKind, o: WidgetOptions, mode: CountMode, b: Built?): Boolean {
        if (!kind.prayers) return false
        val count = o.countdown && (mode != CountMode.TICK || !o.seconds) && (b == null || b.countScale > 0f)
        val art = if (b != null) b.ringPx > 0 || b.barPx > 0 || b.minuteCell >= 0 else kind == WidgetKind.RING || kind == WidgetKind.TODAY || kind == WidgetKind.STRIP
        return count || art
    }

    private fun minuteWanted(all: Map<WidgetKind, IntArray>, options: Map<WidgetKind, Map<Int, WidgetOptions>>, mode: CountMode) =
        all.any { (kind, ids) -> ids.any { needsMinute(kind, options[kind]!![it]!!, mode, built[it]) } }

    /** The next update: the next prayer or change of display, the next picture, and each minute if a widget needs it. */
    private fun armNext(app: Context, c: StatusNotification.Content, now: Instant, pictures: Instant, minuteWanted: Boolean) {
        val full = minOf(c.refreshAt, pictures)
        val minute = if (!minuteWanted) null else nextMinute(c, now)
        if (minute != null && minute.isBefore(full)) armRefresh(app, minute, minuteOnly = true) else armRefresh(app, full, minuteOnly = false)
    }

    /** The widget's size on the screen, in dp (portrait width and height), or its kind's usual size. */
    internal fun sizeDp(manager: AppWidgetManager, id: Int, kind: WidgetKind): Pair<Int, Int> {
        val o = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 } ?: kind.previewDp.first
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 } ?: kind.previewDp.second
        return w to h
    }

    /** The widget's size in px for its sky picture (width, height), with the picture's density. */
    internal class Size(val w: Int, val h: Int, val density: Float)

    /** Never larger than needed for a sharp picture (widgets' pictures travel to the launcher in memory). */
    internal fun pictureSize(app: Context, wDp: Int, hDp: Int): Size {
        val d = app.resources.displayMetrics.density
        // (0.23) At the widget's real size, so the sky is sharp (up to 1440 px a side: a phone's screen width).
        val k = minOf(1f, 1440f / (wDp * d), 1440f / (hDp * d))
        return Size((wDp * d * k).toInt().coerceAtLeast(40), (hDp * d * k).toInt().coerceAtLeast(30), d * k)
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

    /** On the update alarm: true when only the countdown's minute changes. */
    internal const val EXTRA_MINUTE = "minute"

    private fun pending(app: Context, flag: Int, minuteOnly: Boolean = false): PendingIntent? =
        PendingIntent.getBroadcast(app, REQUEST, Intent(app, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH).putExtra(EXTRA_MINUTE, minuteOnly),
            flag or PendingIntent.FLAG_IMMUTABLE)

    private fun armRefresh(app: Context, at: Instant, minuteOnly: Boolean = false) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(app, PendingIntent.FLAG_UPDATE_CURRENT, minuteOnly) ?: return
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
        Widgets.refreshFrom(this, context, minuteOnly = intent.getBooleanExtra(Widgets.EXTRA_MINUTE, false))
}
