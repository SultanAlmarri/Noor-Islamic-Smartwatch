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
 *   left (−); for 30 minutes after it, the time since it (+); then the next prayer. The seconds are ticked by Android
 *   itself (a Chronometer, as in the notification), so the widgets cost nothing between updates; where the phone's
 *   digits differ from those chosen in Noor, Noor writes the countdown itself, to the minute.
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

    internal fun refreshFrom(receiver: BroadcastReceiver, context: Context) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        scope.launch { try { refresh(app) } catch (_: Exception) { } finally { pending.finish() } }
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { refresh(app) } }
    }

    private fun ids(app: Context, cls: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, cls)) }.getOrNull() ?: IntArray(0)

    /** Rebuilds every Noor widget on the home screen and arms the next update (or nothing, if there is none). */
    suspend fun refresh(context: Context, at: Instant? = null) = lock.withLock {
        val app = context.applicationContext as? NoorApplication ?: return@withLock
        val all = providers.associateWith { ids(app, it) }
        if (all.values.all { it.isEmpty() }) { cancelRefresh(app); return@withLock }
        val s = app.container.settings.current()
        val ctx = localized(app, s)
        val arabic = ctx.resources.configuration.locales[0].language == "ar"
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val f = Formatter(arabic, s.numerals, s.timeFormat)
        val now = at ?: Instant.now()
        val config = WearSync.configFor(s, now.toEpochMilli(), app.container.timetables)
        val place = PrayerProvider.place(s)
        val manager = AppWidgetManager.getInstance(app)
        if (config == null || place == null) {
            for ((cls, ids) in all) for (id in ids) runCatching { manager.updateAppWidget(id, noPlace(ctx, app, cls)) }
            cancelRefresh(app)
            return@withLock
        }
        val c = StatusNotification.content(config, s, now, arabic)
        val drawn = !StatusNotification.systemDigitsMatch(app, s.numerals)
        val ws = WidgetScene(s, place, config, now)
        for ((cls, ids) in all) for (id in ids) runCatching {
            val size = sizeOf(manager, id, app)
            val views = when (cls) {
                NextPrayerWidget::class.java -> nextPrayer(ctx, app, c, now, f, drawn, ws, size, rtl)
                TodayWidget::class.java -> today(ctx, app, c, now, f, drawn, ws, size, rtl)
                else -> sky(ctx, app, c, now, f, drawn, ws, size, rtl)
            }
            manager.updateAppWidget(id, views)
        }
        var next = minOf(c.refreshAt, now.plus(PICTURE_EVERY))
        if (drawn) next = minOf(next, now.plusSeconds(60 - now.atZone(config.zone).second.toLong()))
        armRefresh(app, next)
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
            setViewVisibility(R.id.widget_countdown, View.GONE)
            setViewVisibility(R.id.widget_countdown_text, View.GONE)
            setOnClickPendingIntent(R.id.widget_root, open(app))
        }
    }

    private fun nextPrayer(ctx: Context, app: Context, c: StatusNotification.Content, now: Instant, f: Formatter, drawn: Boolean, ws: WidgetScene, size: Size, rtl: Boolean) =
        RemoteViews(app.packageName, R.layout.widget_next).apply {
            setImageViewBitmap(R.id.widget_sky, WidgetSky.picture(app.resources, ws, size.w, size.h, size.density, rtl, WidgetSky.Options(horizon = 0.80f)))
            setTextViewText(R.id.widget_prayer, c.prayer)
            setTextViewText(R.id.widget_time, c.time)
            setTextViewText(R.id.widget_caption, caption(ctx, c))
            countdown(this, ctx, c, now, f, drawn)
            setOnClickPendingIntent(R.id.widget_root, open(app))
        }

    private fun today(ctx: Context, app: Context, c: StatusNotification.Content, now: Instant, f: Formatter, drawn: Boolean, ws: WidgetScene, size: Size, rtl: Boolean) =
        RemoteViews(app.packageName, R.layout.widget_today).apply {
            setImageViewBitmap(R.id.widget_sky, WidgetSky.picture(app.resources, ws, size.w, size.h, size.density, rtl, WidgetSky.Options(horizon = 0.62f)))
            setTextViewText(R.id.widget_prayer, c.prayer)
            setTextViewText(R.id.widget_time, c.time)
            setTextViewText(R.id.widget_place, listOfNotNull(c.place, c.hijri).joinToString("  ·  "))
            countdown(this, ctx, c, now, f, drawn)
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

    private fun sky(ctx: Context, app: Context, c: StatusNotification.Content, now: Instant, f: Formatter, drawn: Boolean, ws: WidgetScene, size: Size, rtl: Boolean) =
        RemoteViews(app.packageName, R.layout.widget_sky).apply {
            setImageViewBitmap(R.id.widget_sky, WidgetSky.picture(app.resources, ws, size.w, size.h, size.density, rtl, WidgetSky.Options(path = true, horizon = 0.76f, arcTop = 0.40f)))
            setTextViewText(R.id.widget_prayer, c.prayer)
            setTextViewText(R.id.widget_time, c.time)
            setTextViewText(R.id.widget_place, listOfNotNull(c.place, c.hijri).joinToString("  ·  "))
            countdown(this, ctx, c, now, f, drawn)
            setOnClickPendingIntent(R.id.widget_root, open(app))
        }

    private fun caption(ctx: Context, c: StatusNotification.Content) =
        ctx.getString(when { !c.elapsed -> R.string.next_prayer; c.sunrise -> R.string.since_sunrise; else -> R.string.since_prayer })

    /**
     * The countdown: ticked by Android (from the prayer's time: "−0:17:05" before it, "+4:12" after it), or, where the
     * phone's digits differ from Noor's, written by Noor to the minute ("−1 h 10 min").
     */
    private fun countdown(v: RemoteViews, ctx: Context, c: StatusNotification.Content, now: Instant, f: Formatter, drawn: Boolean) {
        if (drawn) {
            v.setViewVisibility(R.id.widget_countdown, View.GONE)
            v.setViewVisibility(R.id.widget_countdown_text, View.VISIBLE)
            val d = if (c.elapsed) Duration.between(c.countAt, now) else Duration.between(now, c.countAt)
            val m = d.toMinutes().coerceAtLeast(0)
            val text = if (m >= 60) ctx.getString(R.string.hours_minutes, f.number((m / 60).toInt()), f.number((m % 60).toInt()))
                else ctx.getString(R.string.minutes_only, f.number(m.toInt()))
            v.setTextViewText(R.id.widget_countdown_text, (if (c.elapsed) "+" else StatusNotification.MINUS) + text)
            return
        }
        v.setViewVisibility(R.id.widget_countdown_text, View.GONE)
        v.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
        val base = SystemClock.elapsedRealtime() + Duration.between(now, c.countAt).toMillis()
        v.setChronometer(R.id.widget_countdown, base, if (c.elapsed) "+%s" else null, true)
        v.setChronometerCountDown(R.id.widget_countdown, false)
    }

    private val NAMES = intArrayOf(R.id.widget_name_0, R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4, R.id.widget_name_5)
    private val TIMES = intArrayOf(R.id.widget_time_0, R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3, R.id.widget_time_4, R.id.widget_time_5)

    /** The language chosen in Noor (or the phone's). */
    private fun localized(app: Context, s: AppSettings): Context {
        val tag = when (s.language) { Language.ARABIC -> "ar"; Language.ENGLISH -> "en"; Language.SYSTEM -> null } ?: return app
        return app.createConfigurationContext(Configuration(app.resources.configuration).also { it.setLocale(Locale(tag)) })
    }

    private fun pending(app: Context, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(app, REQUEST, Intent(app, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH), flag or PendingIntent.FLAG_IMMUTABLE)

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
}

/** The widgets' own update time, a restart, an app update, a clock, time-zone or language change. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Widgets.refreshFrom(this, context)
}
