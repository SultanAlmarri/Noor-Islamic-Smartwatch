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

/** The next prayer: its name and time, and a live countdown (0.9.10). */
class NextPrayerWidget : WidgetProvider()

/** Today's prayers: all six times, the next one marked, the place, the Hijri date and the countdown (0.9.10). */
class TodayWidget : WidgetProvider()

/** The home sky in small: the sky, weather and scenery of the moment, the Sun's path and the next prayer (0.9.10). */
class SkyWidget : WidgetProvider()

/** Every Noor widget is kept up to date by [Widgets]; a widget's own update or resize just asks for a refresh. */
open class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refreshFrom(this, context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = Widgets.refreshFrom(this, context)
    override fun onDisabled(context: Context) = Widgets.refreshFrom(this, context)
}

/**
 * The home-screen widgets (0.9.10): [NextPrayerWidget], [TodayWidget] and [SkyWidget].
 *
 * - They say what the home screen and the notification say ([StatusNotification.content]): before a prayer, the time
 *   left (−); for 30 minutes after it, the time since it (+); then the next prayer. Where the phone writes Noor's digits,
 *   the countdown's seconds are ticked by Android itself ("−1:23:45", as on the home screen), so the widgets cost
 *   nothing between updates; otherwise Noor writes it in its own digits, to the minute ("−1:23"), and updates it each
 *   minute while the phone is awake (0.9.13; see [countdown]).
 * - They are rebuilt when what they show changes: at each prayer, at the end of a "+" period, at midnight, every 15
 *   minutes for the sky picture (the Sun moves, the weather changes), and when the place, the times or the settings
 *   change. Those updates use non-waking alarms: a sleeping phone is never woken for them; they run as soon as it
 *   wakes, the only time a widget can be seen.
 * - Their backgrounds are pictures of the home sky of the moment ([WidgetSky]), drawn in the background.
 */
object Widgets {
    private const val ACTION_REFRESH = "com.noor.app.WIDGET_REFRESH"
    private const val REQUEST = 7310
    /** How often the sky picture is redrawn. */
    private val PICTURE_EVERY: Duration = Duration.ofMinutes(15)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private val providers = listOf(NextPrayerWidget::class.java, TodayWidget::class.java, SkyWidget::class.java)

    /** The time, for updates that do not say when they are for (a test sets its own). */
    internal var clock: () -> Instant = Instant::now

    internal fun refreshFrom(receiver: BroadcastReceiver, context: Context, minuteOnly: Boolean = false) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        scope.launch { try { refresh(app, minuteOnly = minuteOnly) } catch (_: Exception) { } finally { pending.finish() } }
    }

    /** How the countdown is shown (see [countdown]). */
    internal enum class CountMode { WHEEL, TICK, MINUTES }

    /** Android 12 and later: the seconds wheel, in Noor's digits whatever the phone's. Earlier: Android's or Noor's. */
    internal var wheelSupported: () -> Boolean = { android.os.Build.VERSION.SDK_INT >= 31 }

    /** A minute's update: only what it sets changes on the home screen (a test applies it to its own simulated widget). */
    internal var partialUpdate: (AppWidgetManager, Int, RemoteViews) -> Unit = { m, id, v -> m.partiallyUpdateAppWidget(id, v) }

    /** When the sky pictures are next due (set by a full update; a minute's update leaves the pictures alone). */
    private var pictureDue: Instant? = null

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { refresh(app) } }
    }

    private fun ids(app: Context, cls: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, cls)) }.getOrNull() ?: IntArray(0)

    /**
     * Rebuilds every Noor widget on the home screen and arms the next update (or nothing, if there is none).
     * [minuteOnly]: only the countdown's hours and minutes changed; the widgets' pictures are left as they are.
     */
    suspend fun refresh(context: Context, at: Instant? = null, minuteOnly: Boolean = false) = lock.withLock {
        val app = context.applicationContext as? NoorApplication ?: return@withLock
        val all = providers.associateWith { ids(app, it) }
        if (all.values.all { it.isEmpty() }) { cancelRefresh(app); return@withLock }
        val s = app.container.settings.current()
        val ctx = localized(app, s)
        val arabic = ctx.resources.configuration.locales[0].language == "ar"
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val f = Formatter(arabic, s.numerals, s.timeFormat)
        val now = at ?: clock()
        val config = WearSync.configFor(s, now.toEpochMilli(), app.container.timetables)
        val place = PrayerProvider.place(s)
        val manager = AppWidgetManager.getInstance(app)
        if (config == null || place == null) {
            for ((cls, ids) in all) for (id in ids) runCatching { manager.updateAppWidget(id, noPlace(ctx, app, cls)) }
            cancelRefresh(app)
            return@withLock
        }
        val c = StatusNotification.content(config, s, now, arabic)
        val mode = when {
            wheelSupported() -> CountMode.WHEEL
            // Android writes the phone's digits; where they are not Noor's, Noor writes the countdown itself.
            StatusNotification.systemDigitsMatch(app, s.numerals) -> CountMode.TICK
            else -> CountMode.MINUTES
        }
        val pictures = pictureDue
        if (minuteOnly && pictures != null && now.isBefore(pictures) && now.isBefore(c.refreshAt)) {
            // A new minute: only the countdown's picture or text changes (the seconds turn by themselves).
            for ((cls, ids) in all) for (id in ids) runCatching {
                partialUpdate(manager, id, RemoteViews(app.packageName, layoutOf(cls)).apply { countdown(this, app, c, now, f, mode, s.numerals) })
            }
            armNext(app, c, now, pictures, mode)
            return@withLock
        }
        val ws = WidgetScene(s, place, config, now)
        for ((cls, ids) in all) for (id in ids) runCatching {
            val size = sizeOf(manager, id, app)
            val views = when (cls) {
                NextPrayerWidget::class.java -> nextPrayer(ctx, app, c, now, f, mode, s.numerals, ws, size, rtl)
                TodayWidget::class.java -> today(ctx, app, c, now, f, mode, s.numerals, ws, size, rtl)
                else -> sky(ctx, app, c, now, f, mode, s.numerals, ws, size, rtl)
            }
            manager.updateAppWidget(id, views)
        }
        pictureDue = now.plus(PICTURE_EVERY)
        armNext(app, c, now, now.plus(PICTURE_EVERY), mode)
    }

    /** The next update: the next prayer or change of display, the next picture, and each minute if the countdown needs it. */
    private fun armNext(app: Context, c: StatusNotification.Content, now: Instant, pictures: Instant, mode: CountMode) {
        val full = minOf(c.refreshAt, pictures)
        val minute = if (mode == CountMode.TICK) null else nextMinute(c, now)
        if (minute != null && minute.isBefore(full)) armRefresh(app, minute, minuteOnly = true) else armRefresh(app, full, minuteOnly = false)
    }

    private fun layoutOf(cls: Class<*>) = when (cls) {
        NextPrayerWidget::class.java -> R.layout.widget_next; TodayWidget::class.java -> R.layout.widget_today; else -> R.layout.widget_sky
    }

    /** The widget's size on the screen, in px (portrait width and height), with the screen's density. */
    internal class Size(val w: Int, val h: Int, val density: Float)

    private fun sizeOf(manager: AppWidgetManager, id: Int, app: Context): Size {
        val o = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
        val d = app.resources.displayMetrics.density
        val wDp = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 } ?: 250
        val hDp = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 } ?: 110
        // Never larger than needed for a sharp picture (widgets' pictures travel to the launcher in memory).
        val k = minOf(1f, 900f / (wDp * d), 600f / (hDp * d))
        return Size((wDp * d * k).toInt().coerceAtLeast(40), (hDp * d * k).toInt().coerceAtLeast(30), d * k)
    }

    private fun open(app: Context): PendingIntent = PendingIntent.getActivity(app, 0,
        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Before a place is chosen: an invitation to open Noor. */
    private fun noPlace(ctx: Context, app: Context, cls: Class<*>): RemoteViews {
        val layout = when (cls) { NextPrayerWidget::class.java -> R.layout.widget_next; TodayWidget::class.java -> R.layout.widget_today; else -> R.layout.widget_sky }
        return RemoteViews(app.packageName, layout).apply {
            setTextViewText(R.id.widget_prayer, ctx.getString(R.string.widget_choose_place))
            setViewVisibility(R.id.widget_time, View.GONE)
            setViewVisibility(R.id.widget_countdown_box, View.GONE)
            setOnClickPendingIntent(R.id.widget_root, open(app))
        }
    }

    private fun nextPrayer(ctx: Context, app: Context, c: StatusNotification.Content, now: Instant, f: Formatter, mode: CountMode, numerals: com.noor.app.data.Numerals, ws: WidgetScene, size: Size, rtl: Boolean) =
        RemoteViews(app.packageName, R.layout.widget_next).apply {
            setImageViewBitmap(R.id.widget_sky, WidgetSky.picture(app.resources, ws, size.w, size.h, size.density, rtl, WidgetSky.Options(horizon = 0.80f)))
            setTextViewText(R.id.widget_prayer, c.prayer)
            setTextViewText(R.id.widget_time, c.time)
            setTextViewText(R.id.widget_caption, caption(ctx, c))
            countdown(this, app, c, now, f, mode, numerals)
            setOnClickPendingIntent(R.id.widget_root, open(app))
        }

    private fun today(ctx: Context, app: Context, c: StatusNotification.Content, now: Instant, f: Formatter, mode: CountMode, numerals: com.noor.app.data.Numerals, ws: WidgetScene, size: Size, rtl: Boolean) =
        RemoteViews(app.packageName, R.layout.widget_today).apply {
            setImageViewBitmap(R.id.widget_sky, WidgetSky.picture(app.resources, ws, size.w, size.h, size.density, rtl, WidgetSky.Options(horizon = 0.62f)))
            setTextViewText(R.id.widget_prayer, c.prayer)
            setTextViewText(R.id.widget_time, c.time)
            setTextViewText(R.id.widget_place, listOfNotNull(c.place, c.hijri).joinToString("  ·  "))
            countdown(this, app, c, now, f, mode, numerals)
            // The six times; the one counted to or from is marked.
            val marked = ws.state.let { st -> if (c.elapsed) st.current?.prayer else st.next.prayer }?.takeIf { c.countAt.atZone(ws.zone).toLocalDate() == ws.today }
            for ((i, p) in Prayer.entries.withIndex()) {
                val at = ws.day.instant(p)
                setTextViewText(NAMES[i], f.prayerName(p, ws.today))
                setTextViewText(TIMES[i], at?.let { f.timeAt(it, ws.zone) } ?: "—")
                val on = p == marked
                val color = if (on) 0xFFF6D696.toInt() else 0xFFFFFFFF.toInt()
                setTextColor(NAMES[i], if (on) color else 0xD9FFFFFF.toInt())
                setTextColor(TIMES[i], color)
            }
            setOnClickPendingIntent(R.id.widget_root, open(app))
        }

    private fun sky(ctx: Context, app: Context, c: StatusNotification.Content, now: Instant, f: Formatter, mode: CountMode, numerals: com.noor.app.data.Numerals, ws: WidgetScene, size: Size, rtl: Boolean) =
        RemoteViews(app.packageName, R.layout.widget_sky).apply {
            setImageViewBitmap(R.id.widget_sky, WidgetSky.picture(app.resources, ws, size.w, size.h, size.density, rtl, WidgetSky.Options(path = true, horizon = 0.76f, arcTop = 0.40f)))
            setTextViewText(R.id.widget_prayer, c.prayer)
            setTextViewText(R.id.widget_time, c.time)
            setTextViewText(R.id.widget_place, listOfNotNull(c.place, c.hijri).joinToString("  ·  "))
            countdown(this, app, c, now, f, mode, numerals)
            setOnClickPendingIntent(R.id.widget_root, open(app))
        }

    private fun caption(ctx: Context, c: StatusNotification.Content) =
        ctx.getString(when { !c.elapsed -> R.string.next_prayer; c.sunrise -> R.string.since_sunrise; else -> R.string.since_prayer })

    /**
     * The countdown, as on the home screen: "−1:23:45" before a prayer, "+13:05" after it, "−1:23" bright and ":45"
     * quieter, in Noor's digits and font, whatever the phone's language ([CountMode.WHEEL], Android 12 and later).
     *
     * On a home screen only Android can change a widget every second without Noor running, and its digital clocks write
     * the phone's digits (fonts packed in an app are ignored there: 0.9.11 tried, and a Samsung phone in Arabic showed
     * its own digits). So Noor draws the hours and minutes itself, as a picture, each minute; and the seconds are a
     * wheel of Noor's digits that Android's AnalogClock turns as its second hand, seen through a window at its top
     * ([wheelFor]). Before Android 12: Android's ticking countdown where the phone writes Noor's digits ([CountMode.TICK]),
     * otherwise Noor's, to the minute ([CountMode.MINUTES]).
     */
    private fun countdown(v: RemoteViews, app: Context, c: StatusNotification.Content, now: Instant, f: Formatter, mode: CountMode,
                          numerals: com.noor.app.data.Numerals) {
        v.setViewVisibility(R.id.widget_count_row, if (mode == CountMode.WHEEL) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_countdown, if (mode == CountMode.TICK) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_countdown_text, if (mode == CountMode.MINUTES) View.VISIBLE else View.GONE)
        when (mode) {
            CountMode.WHEEL -> {
                v.setImageViewBitmap(R.id.widget_count_main, CountPicture.draw(app, leading(c, now, f)))
                val w = wheelFor(c, numerals == com.noor.app.data.Numerals.ARABIC_INDIC)
                v.setIcon(R.id.widget_count_wheel, "setSecondHand", android.graphics.drawable.Icon.createWithResource(app, w.drawable))
                v.setString(R.id.widget_count_wheel, "setTimeZone", w.zone)
            }
            CountMode.TICK -> {
                val base = SystemClock.elapsedRealtime() + Duration.between(now, c.countAt).toMillis()
                v.setChronometer(R.id.widget_countdown, base, if (c.elapsed) "+%s" else null, true)
                v.setChronometerCountDown(R.id.widget_countdown, false)
            }
            CountMode.MINUTES -> v.setTextViewText(R.id.widget_countdown_text, minuteCountdown(c, now, f))
        }
    }

    /** Whole seconds of the countdown (or of the time since), as the home screen counts them (rounded down). */
    private fun elapsedSeconds(c: StatusNotification.Content, now: Instant): Long =
        maxOf(0L, (if (c.elapsed) Duration.between(c.countAt, now) else Duration.between(now, c.countAt)).seconds)

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
     * whole seconds so that this is the countdown's own seconds, rounded down as the home screen counts: before a prayer
     * at second p of its minute, at real second t + ε the countdown has p − t − 1 seconds (mod 60): shift 1 − p; after
     * it, t − p: shift −p.
     */
    internal fun wheelFor(c: StatusNotification.Content, arabicDigits: Boolean): Wheel {
        val p = Math.floorMod(c.countAt.epochSecond, 60L).toInt()
        val shift = if (c.elapsed) -p else 1 - p
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
        // Before a prayer the minutes drop as soon as the countdown falls below a whole minute (at −1:27:00 exactly, the
        // next instant is −1:26:59); after it they rise when it reaches one.
        val wait = if (c.elapsed) 60_000L - Math.floorMod(ms, 60_000L) else Math.floorMod(-ms, 60_000L)
        return now.plusMillis(wait + 30)
    }

    private val NAMES = intArrayOf(R.id.widget_name_0, R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4, R.id.widget_name_5)
    private val TIMES = intArrayOf(R.id.widget_time_0, R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3, R.id.widget_time_4, R.id.widget_time_5)

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
