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
import android.os.Build
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
import com.noor.app.data.ResolvedPlace
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
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
 * The home-screen widgets (0.9.10; redrawn in 0.9.12): [NextPrayerWidget], [TodayWidget] and [SkyWidget].
 *
 * - They say what the home screen and the notification say ([StatusNotification.content]): before a prayer, the time
 *   left (−); for 30 minutes after it, the time since it (+); then the next prayer.
 * - Everything is drawn as on the home screen, in the app's own fonts and digits: the sky picture by the home screen's
 *   renderer ([WidgetSky]), the words ([TextArt]) and the countdown ([CountdownArt]: "−1:23" redrawn by Noor as each
 *   minute begins, the seconds turned by Android itself every second).
 * - A full update (pictures and words) comes at each prayer, at the end of a "+" period, at midnight, every 15 minutes
 *   for the sky (the Sun moves, the weather changes), and when the place, the times or the settings change; between
 *   them, only the countdown's minutes, as each minute begins. Both by one non-waking alarm, set 1.5 s before the minute:
 *   a sleeping phone is never woken for a widget; when it wakes, the alarm due is delivered at once.
 */
object Widgets {
    private const val TAG = "NoorWidgets"
    private const val ACTION_TICK = "com.noor.app.WIDGET_TICK"
    private const val EXTRA_AT = "at"
    private const val REQUEST = 7310
    private const val PREFS = "noor_widgets"
    private const val KEY_FULL_AT = "full_at"
    /** How often the sky picture is redrawn. */
    private val PICTURE_EVERY: Duration = Duration.ofMinutes(15)
    /** The minute's alarm comes this early, so the new minutes are ready when the minute begins. */
    private const val EARLY_MS = 1500L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private val providers = listOf(NextPrayerWidget::class.java, TodayWidget::class.java, SkyWidget::class.java)

    /** The time, for updates that do not say when they are for (a test sets its own). */
    internal var clock: () -> Instant = Instant::now
    /** How the countdown is shown; null: as this phone allows (a test sets its own). */
    internal var modeOverride: CountdownArt.Mode? = null
    /** Puts up a widget's new minutes (a test's simulated home screen applies them itself). */
    internal var partialUpdate: (AppWidgetManager, Int, RemoteViews) -> Unit = { manager, id, v -> manager.partiallyUpdateAppWidget(id, v) }

    internal fun refreshFrom(receiver: BroadcastReceiver, context: Context, intent: Intent? = null) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        val tickAt = intent?.takeIf { it.action == ACTION_TICK }?.getLongExtra(EXTRA_AT, 0L)?.takeIf { it > 0L }
        scope.launch {
            try { if (tickAt != null) tick(app, Instant.ofEpochMilli(tickAt)) else refresh(app) } catch (_: Exception) { } finally { pending.finish() }
        }
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { refresh(app) } }
    }

    private fun ids(app: Context, cls: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, cls)) }.getOrNull() ?: IntArray(0)

    /** What every widget shows at [now]: the settings, the place, the prayer counted to or from. */
    private class Moment(
        val app: NoorApplication, val s: AppSettings, val ctx: Context, val arabic: Boolean, val rtl: Boolean, val f: Formatter,
        val now: Instant, val config: WatchConfig, val place: ResolvedPlace, val c: StatusNotification.Content,
    ) {
        val arabicDigits = s.numerals == com.noor.app.data.Numerals.ARABIC_INDIC
        val mode = modeOverride ?: when {
            Build.VERSION.SDK_INT >= 31 && !AlertScheduler.canScheduleExact(app) -> CountdownArt.Mode.SYSTEM
            Build.VERSION.SDK_INT >= 31 -> CountdownArt.Mode.WHEEL
            else -> CountdownArt.Mode.MINUTES
        }
    }

    private suspend fun moment(app: NoorApplication, now: Instant): Moment? {
        val s = app.container.settings.current()
        val ctx = localized(app, s)
        val arabic = ctx.resources.configuration.locales[0].language == "ar"
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val config = WearSync.configFor(s, now.toEpochMilli(), app.container.timetables) ?: return null
        val place = PrayerProvider.place(s) ?: return null
        return Moment(app, s, ctx, arabic, rtl, Formatter(arabic, s.numerals, s.timeFormat), now, config, place, StatusNotification.content(config, s, now, arabic))
    }

    /** Rebuilds every Noor widget on the home screen and arms the next update (or nothing, if there is none). */
    suspend fun refresh(context: Context, at: Instant? = null) = lock.withLock { refreshLocked(context, at) }

    private suspend fun refreshLocked(context: Context, at: Instant?) {
        val app = context.applicationContext as? NoorApplication ?: return
        val all = providers.associateWith { ids(app, it) }
        if (all.values.all { it.isEmpty() }) { cancelRefresh(app); return }
        val now = at ?: clock()
        val manager = AppWidgetManager.getInstance(app)
        val m = moment(app, now)
        if (m == null) {
            val ctx = localized(app, app.container.settings.current())
            for ((cls, ids) in all) for (id in ids) runCatching { manager.updateAppWidget(id, noPlace(ctx, app, cls, sizeOf(manager, id, app))) }
            cancelRefresh(app)
            return
        }
        val ws = WidgetScene(m.s, m.place, m.config, now)
        for ((cls, ids) in all) for (id in ids) runCatching {
            manager.updateAppWidget(id, build(m, cls, sizeOf(manager, id, app), ws))
        }
        val fullAt = minOf(m.c.refreshAt, now.plus(PICTURE_EVERY))
        app.getSharedPreferences(PREFS, 0).edit().putLong(KEY_FULL_AT, fullAt.toEpochMilli()).apply()
        // The countdown's minutes change as each minute begins; Android's own countdown (SYSTEM) needs no minutes.
        armTick(app, if (m.mode == CountdownArt.Mode.SYSTEM) fullAt else nextMinute(now))
        android.util.Log.i(TAG, "Full update at $now: ${m.c.prayer} ${m.c.countdown(now, m.f)} mode=${m.mode} widgets=${all.values.sumOf { it.size }} next full $fullAt")
    }

    /**
     * The minute's alarm, due at [boundary] (the start of a minute): the countdown's new minutes, put up as the minute
     * begins; or, when a full update is due, that.
     */
    internal suspend fun tick(context: Context, boundary: Instant) = lock.withLock {
        val app = context.applicationContext as? NoorApplication ?: return@withLock
        val all = providers.associateWith { ids(app, it) }
        if (all.values.all { it.isEmpty() }) { cancelRefresh(app); return@withLock }
        val early = clock()
        // On time (the phone awake): the minute about to begin; late (it was asleep): now.
        val target = if (boundary.isAfter(early) && Duration.between(early, boundary).toMillis() <= EARLY_MS + 1000) boundary else early
        val m = moment(app, target.plusMillis(500))
        if (m == null) { refreshLocked(app, null); return@withLock }
        val manager = AppWidgetManager.getInstance(app)
        val views = ArrayList<Pair<Int, RemoteViews>>()
        if (m.mode != CountdownArt.Mode.SYSTEM) for ((cls, ids) in all) for (id in ids) runCatching {
            val size = sizeOf(manager, id, app)
            views += id to RemoteViews(app.packageName, layoutOf(cls, size)).apply { countdown(this, m, cls, size) }
        }
        val wait = Duration.between(clock(), target).toMillis()
        if (wait > 0) delay(wait + 15)
        for ((id, v) in views) runCatching { partialUpdate(manager, id, v) }
        android.util.Log.i(TAG, "Minute $target (asked for $boundary, woke ${Duration.between(early, target).toMillis()} ms early): ${m.c.countdown(m.now, m.f)}")
        val fullAt = app.getSharedPreferences(PREFS, 0).getLong(KEY_FULL_AT, 0L)
        if (target.toEpochMilli() + 1000 >= fullAt) refreshLocked(app, maxOf(clock(), target))
        else armTick(app, nextMinute(target.plusMillis(500)))
    }

    /** The start of the minute after [t]. */
    private fun nextMinute(t: Instant): Instant = t.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)

    /** The widget's size: its picture's px (portrait width and height) and density, and its size in dp. */
    internal class Size(val w: Int, val h: Int, val density: Float, val wDp: Float, val hDp: Float)

    private fun sizeOf(manager: AppWidgetManager, id: Int, app: Context): Size {
        val o = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
        val d = app.resources.displayMetrics.density
        val wDp = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 } ?: 250
        val hDp = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 } ?: 110
        // Never larger than needed for a sharp picture (widgets' pictures travel to the launcher in memory).
        val k = minOf(1f, 900f / (wDp * d), 600f / (hDp * d))
        return Size((wDp * d * k).toInt().coerceAtLeast(40), (hDp * d * k).toInt().coerceAtLeast(30), d * k, wDp.toFloat(), hDp.toFloat())
    }

    /** The sky widget is laid out as the home screen's header: side by side when short, stacked when tall. */
    private fun tall(size: Size) = size.hDp >= 200f && size.wDp >= 200f

    private fun layoutOf(cls: Class<*>, size: Size): Int = when (cls) {
        NextPrayerWidget::class.java -> R.layout.widget_next
        TodayWidget::class.java -> R.layout.widget_today
        else -> if (tall(size)) R.layout.widget_sky_tall else R.layout.widget_sky
    }

    /** The countdown's font size (dp) in each widget: as large as the widget allows, as on the home screen. */
    internal fun countdownDp(cls: Class<*>, size: Size): Float {
        // "−0:00:00" is about 4.1 em wide with its margins.
        val (byHeight, byWidth, max) = when (cls) {
            NextPrayerWidget::class.java -> Triple(size.hDp * 0.42f, (size.wDp - 20f) / 4.1f, 52f)
            TodayWidget::class.java -> Triple(size.hDp * 0.23f, (size.wDp - 20f) * 0.5f / 4.1f, 48f)
            else -> if (tall(size)) Triple(size.hDp * 0.20f, (size.wDp - 24f) * 0.9f / 4.1f, CountdownWheelSpec.E_DP)
                else Triple(size.hDp * 0.26f, (size.wDp - 20f) * 0.52f / 4.1f, 52f)
        }
        return minOf(byHeight, byWidth, max).coerceAtLeast(20f)
    }

    private fun titleDp(cls: Class<*>, size: Size): Float = when (cls) {
        NextPrayerWidget::class.java -> if (size.hDp < 90f) 15f else 17f
        TodayWidget::class.java -> 19f
        else -> if (tall(size)) 24f else 20f
    }

    private fun countdown(v: RemoteViews, m: Moment, cls: Class<*>, size: Size) {
        val d = m.app.resources.displayMetrics.density
        val em = countdownDp(cls, size) * d
        val text = m.c.countdown(m.now, m.f)
        val base = SystemClock.elapsedRealtime() + Duration.between(m.now, m.c.countAt).toMillis()
        CountdownArt.show(v, m.app, text, em, m.mode, m.c.elapsed, m.arabicDigits, base, listOf(m.c.prayer, text).joinToString(", "))
    }

    private fun open(app: Context): PendingIntent = PendingIntent.getActivity(app, 0,
        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Before a place is chosen: an invitation to open Noor. */
    private fun noPlace(ctx: Context, app: Context, cls: Class<*>, size: Size): RemoteViews {
        val d = app.resources.displayMetrics.density
        val arabic = ctx.resources.configuration.locales[0].language == "ar"
        val words = ctx.getString(R.string.widget_choose_place)
        return RemoteViews(app.packageName, layoutOf(cls, size)).apply {
            setInt(R.id.widget_root, "setLayoutDirection", if (arabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
            setImageViewBitmap(R.id.widget_title, TextArt.small(app, words, 14f, d, TextArt.WHITE, arabic))
            setContentDescription(R.id.widget_title, words)
            setViewVisibility(R.id.widget_countdown_box, View.GONE)
            setOnClickPendingIntent(R.id.widget_root, open(app))
        }
    }

    /** One widget, whole. */
    private fun build(m: Moment, cls: Class<*>, size: Size, ws: WidgetScene): RemoteViews {
        val app = m.app
        val d = app.resources.displayMetrics.density
        val v = RemoteViews(app.packageName, layoutOf(cls, size))
        // Laid out in Noor's language (a widget otherwise follows the phone's: right to left on an Arabic phone even
        // with Noor in English); the countdown reads left to right in both, as on the home screen.
        v.setInt(R.id.widget_root, "setLayoutDirection", if (m.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
        val title = TextArt.title(app, m.c.prayer, m.c.time, m.arabic, titleDp(cls, size), d)
        v.setImageViewBitmap(R.id.widget_title, title)
        v.setContentDescription(R.id.widget_title, listOf(m.c.prayer, m.c.time).joinToString(", "))
        lastTitle[cls] = m.c.prayer to m.c.time
        val placeLine = listOfNotNull(m.c.place, m.c.hijri).joinToString("  ·  ")
        if (cls != NextPrayerWidget::class.java) {
            val small = if (cls == SkyWidget::class.java && tall(size)) 13f else 12f
            v.setImageViewBitmap(R.id.widget_place, TextArt.small(app, placeLine, small, d, rtl = m.arabic))
            v.setContentDescription(R.id.widget_place, placeLine)
        }
        countdown(v, m, cls, size)
        // The sky, laid out as the home screen lays out its header: the text on top, the Sun's arc below it, the land.
        val em = countdownDp(cls, size)
        // As on the home screen, clouds keep below the words (they fade out above the words' bottom).
        val options = when (cls) {
            NextPrayerWidget::class.java -> WidgetSky.Options(horizon = 0.82f, cloudTop = 0.86f, textBottomDp = size.hDp * 0.86f)
            TodayWidget::class.java -> {
                val textDp = 6f + maxOf(titleDp(cls, size) * 1.5f + 22f, em * 0.97f)
                WidgetSky.Options(horizon = 0.60f, cloudTop = textDp / size.hDp, textBottomDp = textDp)
            }
            else -> {
                val textDp = if (tall(size)) 10f + 22f + titleDp(cls, size) * 1.5f + em * 0.97f else 6f + maxOf(titleDp(cls, size) * 1.5f + 20f, em * 0.97f)
                val land = if (tall(size)) maxOf(38f, size.hDp * 0.20f) else maxOf(26f, size.hDp * 0.20f)
                val horizon = (size.hDp - land) / size.hDp
                val arcTop = minOf(textDp + 12f, size.hDp - land - 22f) / size.hDp
                WidgetSky.Options(horizon = horizon, arcTop = arcTop, cloudTop = (arcTop - 8f / size.hDp).coerceAtLeast(0f), ground = (size.hDp - 4f) / size.hDp,
                    path = true, textBottomDp = textDp)
            }
        }
        v.setImageViewBitmap(R.id.widget_sky, WidgetSky.picture(app.resources, ws, size.w, size.h, size.density, m.rtl, options))
        if (cls == TodayWidget::class.java) {
            // The six times; the one counted to or from is marked.
            val marked = ws.state.let { st -> if (m.c.elapsed) st.current?.prayer else st.next.prayer }?.takeIf { m.c.countAt.atZone(ws.zone).toLocalDate() == ws.today }
            val names = Prayer.entries.map { m.f.prayerName(it, ws.today) }
            val times = Prayer.entries.map { p -> ws.day.instant(p)?.let { m.f.timeAt(it, ws.zone) } ?: "—" }
            val index = if (marked == null) -1 else Prayer.entries.indexOf(marked)
            v.setImageViewBitmap(R.id.widget_grid, TextArt.grid(app, names, times, index, ((size.wDp - 20f) * d).toInt(), d, m.arabic))
            v.setContentDescription(R.id.widget_grid, names.indices.joinToString(", ") { "${names[it]} ${times[it]}" })
            lastToday = TodayText(names, times, index)
        }
        v.setOnClickPendingIntent(R.id.widget_root, open(app))
        return v
    }

    /** What the widgets last wrote, for tests (their words are pictures; the same words are their descriptions). */
    internal class TodayText(val names: List<String>, val times: List<String>, val marked: Int)
    @Volatile internal var lastToday: TodayText? = null
    internal val lastTitle = java.util.concurrent.ConcurrentHashMap<Class<*>, Pair<String, String>>()

    /** The language chosen in Noor (or the phone's). */
    private fun localized(app: Context, s: AppSettings): Context {
        val tag = when (s.language) { Language.ARABIC -> "ar"; Language.ENGLISH -> "en"; Language.SYSTEM -> null } ?: return app
        return app.createConfigurationContext(Configuration(app.resources.configuration).also { it.setLocale(Locale(tag)) })
    }

    private fun pending(app: Context, flag: Int, at: Long = 0L): PendingIntent? =
        PendingIntent.getBroadcast(app, REQUEST, Intent(app, WidgetRefreshReceiver::class.java).setAction(ACTION_TICK).putExtra(EXTRA_AT, at), flag or PendingIntent.FLAG_IMMUTABLE)

    /** One alarm for everything: due [EARLY_MS] before [boundary], or at once if that has passed. */
    private fun armTick(app: Context, boundary: Instant) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(app, PendingIntent.FLAG_UPDATE_CURRENT, boundary.toEpochMilli()) ?: return
        val at = boundary.toEpochMilli() - EARLY_MS
        // Non-waking (RTC): delivered at the time if the phone is awake, otherwise as soon as it wakes.
        runCatching {
            if (AlertScheduler.canScheduleExact(app)) am.setExact(AlarmManager.RTC, at, pi)
            else am.set(AlarmManager.RTC, at, pi)
        }.onFailure { am.set(AlarmManager.RTC, at, pi) }
    }

    private fun cancelRefresh(app: Context) {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        pending(app, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }
}

/** The widgets' own alarm (the minute, or a full update), a restart, an app update, a clock, time-zone or language change. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Widgets.refreshFrom(this, context, intent)
}
