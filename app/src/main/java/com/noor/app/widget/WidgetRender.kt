package com.noor.app.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.noor.app.R
import com.noor.app.data.AppSettings
import com.noor.app.data.Numerals
import com.noor.app.notify.StatusNotification
import com.noor.app.util.Formatter
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.calendar.IslamicCalendar
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything one widget is built from at one moment (0.23): the app's settings, language and digits, what the home
 * screen says now ([c]), the day's times ([ws]), the widget's size in dp and its options and colours.
 */
internal class WidgetCtx(
    val kind: WidgetKind,
    val ctx: Context,
    val app: Context,
    val s: AppSettings,
    val f: Formatter,
    val rtl: Boolean,
    val c: StatusNotification.Content?,
    val now: Instant,
    val mode: Widgets.CountMode,
    val ws: WidgetScene?,
    val zone: ZoneId,
    val wDp: Int,
    val hDp: Int,
    val o: WidgetOptions,
    val pal: WidgetPalette,
) {
    val density: Float = app.resources.displayMetrics.density
    /** The text's scale: the widget's text size over the system's font size (up to 1.3; the layouts fit at 1.3). */
    val k: Float = o.textSize.scale * ctx.resources.configuration.fontScale.coerceIn(0.85f, 1.3f)
    val today: LocalDate get() = now.atZone(zone).toLocalDate()

    /** The prayer counted to or from, named with Jumu'ah on Fridays when the widget's option says so. */
    val prayer: String get() {
        val c = c ?: return ""
        val p = c.event ?: return c.prayer
        return if (o.jumuah) f.prayerName(p, c.eventDate ?: today) else f.prayerName(p)
    }

    /** The prayer counted to or from, when it is one of today's (its mark in rows and grids). */
    val marked: Prayer? get() = c?.takeIf { it.countAt.atZone(zone).toLocalDate() == today }?.event

    /** 0..1 from the last prayer to the next. */
    val progress: Float get() = ws?.state?.progress ?: 0f

    fun name(p: Prayer): String = if (o.jumuah) f.prayerName(p, today) else f.prayerName(p)
}

/** What a full update built for one widget, so the minute's update can redraw its countdown alike (0.23). */
internal class Built(val layout: Int, val countScale: Float, val ringPx: Int = 0, val barPx: Int = 0)

/**
 * The widgets' builders (0.23): one per [WidgetKind], each choosing its layout for the widget's size, and the parts
 * they share (background, countdown, cells of prayers, ring, bar). Every text is in the app's language and digits,
 * sized to fit ([Fit]); the countdown is always left to right.
 */
internal object WidgetRender {
    private const val PAD = 16f
    private const val PAD_V = 13f

    private class Layout(val normal: Int, val plain: Int)
    private val NEXT = Layout(R.layout.widget_next, R.layout.widget_next_plain)
    private val NEXT_WIDE = Layout(R.layout.widget_next_wide, R.layout.widget_next_wide_plain)
    private val NEXT_TALL = Layout(R.layout.widget_next_tall, R.layout.widget_next_tall_plain)
    private val TODAY = Layout(R.layout.widget_today, R.layout.widget_today_plain)
    private val TODAY_ROW = Layout(R.layout.widget_today_row, R.layout.widget_today_row_plain)
    private val TODAY_COLUMN = Layout(R.layout.widget_today_column, R.layout.widget_today_column_plain)
    private val TODAY_GRID = Layout(R.layout.widget_today_grid, R.layout.widget_today_grid_plain)
    private val SKY = Layout(R.layout.widget_sky, R.layout.widget_sky_plain)
    private val RING = Layout(R.layout.widget_ring, R.layout.widget_ring_plain)
    private val STRIP = Layout(R.layout.widget_strip, R.layout.widget_strip_plain)
    private val DATE = Layout(R.layout.widget_date, R.layout.widget_date_plain)
    private val DATE_WIDE = Layout(R.layout.widget_date_wide, R.layout.widget_date_wide_plain)
    private val MESSAGE = Layout(R.layout.widget_message, R.layout.widget_message_plain)

    val NAMES = intArrayOf(R.id.widget_name_0, R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4, R.id.widget_name_5)
    val TIMES = intArrayOf(R.id.widget_time_0, R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3, R.id.widget_time_4, R.id.widget_time_5)
    val CELLS = intArrayOf(R.id.widget_cell_0, R.id.widget_cell_1, R.id.widget_cell_2, R.id.widget_cell_3, R.id.widget_cell_4, R.id.widget_cell_5)
    val PILLS = intArrayOf(R.id.widget_pill_0, R.id.widget_pill_1, R.id.widget_pill_2, R.id.widget_pill_3, R.id.widget_pill_4, R.id.widget_pill_5)
    val ICONS = intArrayOf(R.id.widget_icon_0, R.id.widget_icon_1, R.id.widget_icon_2, R.id.widget_icon_3, R.id.widget_icon_4, R.id.widget_icon_5)
    private val SIGNS = intArrayOf(R.drawable.widget_icon_fajr, R.drawable.widget_icon_shuruq, R.drawable.widget_icon_dhuhr,
        R.drawable.widget_icon_asr, R.drawable.widget_icon_maghrib, R.drawable.widget_icon_isha)

    private fun pick(l: Layout, r: WidgetCtx) = if (r.pal.plain) l.plain else l.normal

    /** Builds the widget; [built] receives what the minute's update needs. */
    fun build(r: WidgetCtx, built: (Built) -> Unit = {}): RemoteViews = when (r.kind) {
        WidgetKind.NEXT -> next(r, built)
        WidgetKind.TODAY -> today(r, built)
        WidgetKind.SKY -> sky(r, built)
        WidgetKind.RING -> ring(r, built)
        WidgetKind.STRIP -> strip(r, built)
        WidgetKind.DATE -> date(r, built)
    }

    /** The minute's update: the countdown's hours and minutes, and the ring or the bar; nothing else changes. */
    fun minute(r: WidgetCtx, b: Built): RemoteViews = RemoteViews(r.app.packageName, b.layout).apply {
        if (r.o.countdown) countdown(this, r, b.countScale)
        if (b.ringPx > 0) setImageViewBitmap(R.id.widget_ring, WidgetArt.ring(b.ringPx, r.density, r.progress, r.pal.accent, r.pal.track, ringStroke(b.ringPx / r.density)))
        if (b.barPx > 0) setImageViewBitmap(R.id.widget_progress, bar(r, b.barPx))
    }

    /** Before a place is chosen: an invitation to open Minhad. */
    fun message(r: WidgetCtx): RemoteViews = RemoteViews(r.app.packageName, pick(MESSAGE, r)).apply {
        background(this, r, null)
        text(this, R.id.widget_message, r.ctx.getString(R.string.widget_choose_place), 13f * r.k, r.pal.text)
        direction(this, r)
        setOnClickPendingIntent(R.id.widget_root, Widgets.open(r.app, WidgetTap.HOME))
    }

    // ---- The parts ----

    private fun text(v: RemoteViews, id: Int, t: CharSequence, sizeDp: Float, color: Int) {
        v.setTextViewText(id, t)
        v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, sizeDp)
        v.setTextColor(id, color)
    }

    private fun gone(v: RemoteViews, vararg ids: Int) { for (id in ids) v.setViewVisibility(id, View.GONE) }

    /** The background the options chose; [sky] the picture's framing when it is the sky (null: a plain ink instead). */
    private fun background(v: RemoteViews, r: WidgetCtx, sky: WidgetSky.Options?, scrim: Int = R.drawable.widget_scrim_top) {
        val p = r.pal
        when (p.background) {
            WidgetBackground.SKY -> {
                val ws = r.ws
                if (ws != null && sky != null) {
                    val size = Widgets.pictureSize(r.app, r.wDp, r.hDp)
                    v.setImageViewBitmap(R.id.widget_bg, WidgetSky.picture(r.app.resources, ws, size.w, size.h, size.density, false, sky))
                    v.setImageViewResource(R.id.widget_scrim, scrim)
                    v.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                } else {
                    v.setImageViewResource(R.id.widget_bg, R.drawable.widget_glass)
                    v.setInt(R.id.widget_bg, "setImageAlpha", 235)
                }
            }
            WidgetBackground.GLASS -> {
                v.setImageViewResource(R.id.widget_bg, R.drawable.widget_glass)
                v.setInt(R.id.widget_bg, "setImageAlpha", p.alpha)
            }
            WidgetBackground.DARK, WidgetBackground.LIGHT, WidgetBackground.YOU -> {
                v.setImageViewResource(R.id.widget_bg, R.drawable.widget_shape)
                v.setInt(R.id.widget_bg, "setColorFilter", p.fill)
                v.setInt(R.id.widget_bg, "setImageAlpha", p.alpha)
            }
            WidgetBackground.CLEAR -> v.setViewVisibility(R.id.widget_bg, View.GONE)
        }
    }

    /** The sky picture's framing, with the system's corner. */
    private fun skyOptions(r: WidgetCtx, horizon: Float, path: Boolean = false, arcTop: Float = 0.30f) =
        WidgetSky.Options(path = path, horizon = horizon, arcTop = arcTop,
            radius = r.app.resources.getDimension(R.dimen.widget_radius) / r.density, bodies = path)

    /**
     * The widget reads in the app's language's direction, whatever the phone's: a home screen lays a widget out in the
     * phone's own direction (an Arabic phone mirrors a widget in English).
     */
    private fun direction(v: RemoteViews, r: WidgetCtx) =
        v.setInt(R.id.widget_root, "setLayoutDirection", if (r.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)

    private fun common(v: RemoteViews, r: WidgetCtx) {
        direction(v, r)
        v.setOnClickPendingIntent(R.id.widget_root, Widgets.open(r.app, r.o.tap))
        describe(v, r)
    }

    /** What a screen reader says for the widget: the prayer, its time and the time left or since. */
    private fun describe(v: RemoteViews, r: WidgetCtx) {
        val c = r.c ?: return
        val d = if (c.elapsed) Duration.between(c.countAt, r.now) else Duration.between(r.now, c.countAt)
        val (h, m) = (maxOf(0L, d.seconds) / 60).let { it / 60 to it % 60 }
        val span = if (h > 0) r.ctx.getString(R.string.hours_minutes, r.f.number(h.toInt()), r.f.number(m.toInt()))
            else r.ctx.getString(R.string.minutes_only, r.f.number(m.toInt()))
        v.setContentDescription(R.id.widget_root, r.ctx.getString(if (c.elapsed) R.string.widget_cd_after else R.string.widget_cd_before, r.prayer, c.time, span))
    }

    /** "3:37 PM" with "PM" smaller; "15:37" in 24 hours. */
    private fun time(r: WidgetCtx, at: Instant?): CharSequence {
        if (at == null) return "—"
        val z = at.atZone(r.zone)
        val (t, suffix) = r.f.time(z.hour * 60 + z.minute)
        if (suffix == null) return t
        return SpannableString("$t $suffix").apply { setSpan(RelativeSizeSpan(0.72f), t.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    /** The width of a time written by [time] at [size]. */
    private fun timeWidth(t: CharSequence, size: Float, family: String = Fit.MEDIUM): Float {
        val s = t.toString(); val sp = s.indexOf(' ')
        return if (sp < 0) Fit.width(s, family, size) else Fit.width(s.substring(0, sp), family, size) + Fit.width(s.substring(sp), family, size * 0.72f)
    }

    /** The size at which every one of the [times] fits [avail]. */
    private fun timeSize(times: List<CharSequence>, want: Float, avail: Float, family: String = Fit.MEDIUM): Float {
        var s = want
        for (t in times) { val w = timeWidth(t, want, family); if (w > avail * 0.92f) s = minOf(s, want * avail * 0.92f / w) }
        return s.coerceAtLeast(7f)
    }

    /**
     * The place and the Hijri date, as the options ask ("Istanbul, Türkiye  ·  15 Jumada I 1448 AH"), in [R.id.widget_place]:
     * the longest of these that fits [avail] at [min] or more: the whole place and the date, the city and the date, the
     * city alone, the date alone; otherwise nothing (never a line cut short).
     */
    private fun place(v: RemoteViews, r: WidgetCtx, avail: Float, want: Float, min: Float, color: Int, hijri: Boolean = true) {
        val c = r.c
        val full = c?.place?.takeIf { r.o.place }
        val city = full?.let { StatusNotification.shortPlace(it) }
        val date = c?.hijri?.takeIf { r.o.hijri && hijri }
        val sep = "  ·  "
        val options = listOfNotNull(
            if (full != null && date != null) full + sep + date else null,
            if (city != null && date != null && city != full) city + sep + date else null,
            full, city.takeIf { it != full }, date,
        )
        for (t in options) {
            val size = Fit.size(listOf(t), Fit.REGULAR, want, avail, 0f)
            if (size >= min) { text(v, R.id.widget_place, t, size, color); return }
        }
        gone(v, R.id.widget_place)
    }

    /** "Next prayer", or since the Adhan / sunrise during the "+" period. */
    private fun caption(r: WidgetCtx): String {
        val c = r.c ?: return ""
        return r.ctx.getString(when { !c.elapsed -> R.string.next_prayer; c.sunrise -> R.string.widget_since_sunrise; else -> R.string.widget_since_adhan })
    }

    /** "−1:23" / "+13", the countdown before its seconds; with the seconds off, "−1:23" / "+0:13". */
    private fun leading(r: WidgetCtx): String {
        val c = r.c ?: return ""
        return if (r.o.seconds) Widgets.leading(c, r.now, r.f) else Widgets.minuteCountdown(c, r.now, r.f)
    }

    /**
     * The scale (of the seconds wheel's 40 dp) at which the countdown is [wantDp] high in text size and fits [availDp]
     * across; never above 1.
     */
    private fun countScale(r: WidgetCtx, wantDp: Float, availDp: Float): Float {
        var s = (wantDp / 40f).coerceAtMost(1f)
        val text = leading(r)
        val w = CountPicture.widthDp(r.app, text, s, r.o.seconds && r.mode == Widgets.CountMode.WHEEL) * 1.06f
        if (w > availDp) s *= availDp / w
        return s.coerceIn(0.3f, 1f)
    }

    /**
     * The countdown (0.9.14; 0.23 in the widget's colours and size), as on the home screen: "−1:23:45" before a prayer,
     * "+13:05" after it, "−1:23" bright and ":45" quieter, in Minhad's digits and font whatever the phone's language
     * ([Widgets.CountMode.WHEEL], Android 12 and later): the hours and minutes a picture drawn each minute, the seconds
     * a wheel of Minhad's digits that Android's AnalogClock turns as its second hand, seen through a window at its top
     * ([Widgets.wheelFor]); the clock is made smaller to draw the wheel at [scale]. Before Android 12: Android's ticking
     * countdown where the phone writes Minhad's digits ([Widgets.CountMode.TICK]), otherwise Minhad's, to the minute.
     */
    fun countdown(v: RemoteViews, r: WidgetCtx, scale: Float) {
        val c = r.c ?: return
        val mode = if (!r.o.seconds && r.mode == Widgets.CountMode.TICK) Widgets.CountMode.MINUTES else r.mode
        v.setViewVisibility(R.id.widget_countdown_box, View.VISIBLE)
        v.setViewVisibility(R.id.widget_count_row, if (mode == Widgets.CountMode.WHEEL) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_countdown, if (mode == Widgets.CountMode.TICK) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_countdown_text, if (mode == Widgets.CountMode.MINUTES) View.VISIBLE else View.GONE)
        val textDp = 40f * scale
        when (mode) {
            Widgets.CountMode.WHEEL -> {
                val spec = CountPicture.Spec(leading(r), scale, r.pal.text, r.pal.quiet, shadow = !r.pal.plain, colon = r.o.seconds)
                Widgets.lastCount = spec
                v.setImageViewBitmap(R.id.widget_count_main, CountPicture.draw(r.app, spec))
                v.setViewVisibility(R.id.widget_count_window, if (r.o.seconds) View.VISIBLE else View.GONE)
                if (r.o.seconds) {
                    val res = r.app.resources; val d = r.density
                    val w = Widgets.wheelFor(c, r.s.numerals == Numerals.ARABIC_INDIC)
                    v.setIcon(R.id.widget_count_wheel, "setSecondHand", Icon.createWithResource(r.app, w.drawable))
                    v.setString(R.id.widget_count_wheel, "setTimeZone", w.zone)
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        v.setViewLayoutWidth(R.id.widget_count_window, res.getDimension(R.dimen.noor_count_window) * scale / d, TypedValue.COMPLEX_UNIT_DIP)
                        v.setViewLayoutHeight(R.id.widget_count_window, res.getDimension(R.dimen.noor_count_height) * scale / d, TypedValue.COMPLEX_UNIT_DIP)
                        val wheel = res.getDimension(R.dimen.noor_count_wheel) * scale / d
                        v.setViewLayoutWidth(R.id.widget_count_wheel, wheel, TypedValue.COMPLEX_UNIT_DIP)
                        v.setViewLayoutHeight(R.id.widget_count_wheel, wheel, TypedValue.COMPLEX_UNIT_DIP)
                        v.setColorStateList(R.id.widget_count_wheel, "setSecondHandTintList", ColorStateList.valueOf(r.pal.text))
                    }
                }
            }
            Widgets.CountMode.TICK -> {
                val base = android.os.SystemClock.elapsedRealtime() + Duration.between(r.now, c.countAt).toMillis()
                v.setChronometer(R.id.widget_countdown, base, if (c.elapsed) "+%s" else null, true)
                v.setChronometerCountDown(R.id.widget_countdown, false)
                v.setTextViewTextSize(R.id.widget_countdown, TypedValue.COMPLEX_UNIT_DIP, textDp)
                v.setTextColor(R.id.widget_countdown, r.pal.text)
            }
            Widgets.CountMode.MINUTES -> text(v, R.id.widget_countdown_text, Widgets.minuteCountdown(c, r.now, r.f), textDp, r.pal.text)
        }
    }

    private fun bar(r: WidgetCtx, px: Int): android.graphics.Bitmap =
        WidgetArt.bar(px, (3f * r.density).toInt().coerceAtLeast(2), r.progress, r.pal.accent, r.pal.track, r.rtl)

    /**
     * The cells of the day's prayers: each prayer's name and time, the one counted to or from on a pill of the accent,
     * those already past quieter. [nameSize] and [timeSize] are common to all, so they line up.
     */
    private fun cells(v: RemoteViews, r: WidgetCtx, shown: List<Prayer>, nameSize: Float, timeSizeDp: Float, signs: Boolean = false) {
        val ws = r.ws ?: return
        val marked = r.marked
        for ((i, p) in Prayer.entries.withIndex()) {
            if (p !in shown) { v.setViewVisibility(CELLS[i], View.GONE); continue }
            val at = ws.day.instant(p)
            val on = p == marked
            val past = !on && at != null && !at.isAfter(r.now)
            val nameColor = when { on -> r.pal.accent; past -> r.pal.muted; else -> r.pal.secondary }
            val timeColor = when { on -> r.pal.accent; past -> r.pal.muted; else -> r.pal.text }
            text(v, NAMES[i], r.name(p), nameSize, nameColor)
            text(v, TIMES[i], time(r, at), timeSizeDp, timeColor)
            v.setViewVisibility(PILLS[i], if (on) View.VISIBLE else View.GONE)
            if (on) { v.setInt(PILLS[i], "setColorFilter", r.pal.accent or 0xFF000000.toInt()); v.setInt(PILLS[i], "setImageAlpha", r.pal.pill ushr 24) }
            if (signs) {
                v.setImageViewResource(ICONS[i], SIGNS[i])
                v.setInt(ICONS[i], "setColorFilter", if (on) r.pal.accent else if (past) r.pal.muted else r.pal.secondary)
            }
        }
    }

    /** The ring's line: 5 dp on a 2x2, a little bolder on larger rings. */
    private fun ringStroke(ringDp: Float) = 5f * (ringDp / 150f).coerceIn(1f, 1.3f)

    private fun shownPrayers(r: WidgetCtx): List<Prayer> = Prayer.entries.filter { it != Prayer.SHURUQ || r.o.shuruq }

    /** The prayer and its time on one line ([R.id.widget_prayer] beside [R.id.widget_time]), fitted to [availDp]. */
    private fun titleRow(v: RemoteViews, r: WidgetCtx, availDp: Float, want: Float, timeWant: Float, prayerColor: Int) {
        val c = r.c ?: return
        val t = time(r, c.countAt)
        val timeDp = minOf(timeWant, timeSize(listOf(t), timeWant, availDp * 0.45f, Fit.REGULAR))
        val tw = timeWidth(t, timeDp, Fit.REGULAR) + 8f
        val name = r.prayer
        text(v, R.id.widget_prayer, name, Fit.size(listOf(name), Fit.MEDIUM, want, availDp - tw), prayerColor)
        text(v, R.id.widget_time, t, timeDp, r.pal.secondary)
    }

    // ---- The kinds ----

    /** Next prayer: 2×1 compact (the prayer and time on a line, the countdown under), 2×2 and larger a calm column. */
    private fun next(r: WidgetCtx, built: (Built) -> Unit): RemoteViews {
        val tall = r.hDp >= 118
        val wide = !tall && r.wDp >= 230
        val layout = pick(if (tall) NEXT_TALL else if (wide) NEXT_WIDE else NEXT, r)
        val v = RemoteViews(r.app.packageName, layout)
        background(v, r, skyOptions(r, horizon = if (tall) 0.90f else 0.95f), if (tall) R.drawable.widget_scrim_full else R.drawable.widget_scrim_even)
        val c = r.c!!
        val avail = r.wDp - 2 * PAD
        val inner = r.hDp - 2 * PAD_V
        val k = r.k
        val scale: Float
        if (tall) {
            text(v, R.id.widget_caption, caption(r), 12f * k, r.pal.accent)
            val name = r.prayer
            val big = minOf(30f, 22f + (r.hDp - 118) / 12f) * k
            text(v, R.id.widget_prayer, name, Fit.size(listOf(name), Fit.MEDIUM, big, avail), r.pal.text)
            val t = time(r, c.countAt)
            text(v, R.id.widget_time, t, timeSize(listOf(t), 15f * k, avail, Fit.REGULAR), r.pal.secondary)
            val want = minOf(40f, 30f + (r.wDp - 150) / 10f + (r.hDp - 130) / 14f) * k
            scale = countScale(r, want, avail)
            if (r.o.place && r.hDp >= 140) place(v, r, avail, 12f * k, 10f * k, r.pal.muted, hijri = false) else gone(v, R.id.widget_place)
        } else if (wide) {
            // The prayer, its time (and the caption and place, when there is room) on the reading side; the countdown,
            // as large as the height allows, on the other.
            val countWant = minOf(36f * k, (inner - 4f) / 0.842f)
            scale = countScale(r, countWant, avail * 0.56f)
            val countW = if (r.o.countdown) CountPicture.widthDp(r.app, leading(r), scale, r.o.seconds && r.mode == Widgets.CountMode.WHEEL) + 12f else 0f
            val side = avail - countW
            val name = r.prayer
            text(v, R.id.widget_prayer, name, Fit.size(listOf(name), Fit.MEDIUM, 17f * k, side), r.pal.text)
            val t = time(r, c.countAt)
            text(v, R.id.widget_time, t, timeSize(listOf(t), 13f * k, side, Fit.REGULAR), r.pal.secondary)
            var room = inner - 17f * k * 1.25f - 13f * k * 1.3f
            val cap = 11f * k * 1.3f + 1f
            if (room >= cap) { text(v, R.id.widget_caption, caption(r), 11f * k, r.pal.accent); room -= cap } else gone(v, R.id.widget_caption)
            if (r.o.place && room >= cap + 2f) place(v, r, side, 11f * k, 9.5f, r.pal.muted, hijri = false) else gone(v, R.id.widget_place)
        } else {
            // From the top: the caption (when there is room), the prayer and its time, the countdown, the place.
            val title = 16f * k * 1.25f
            val cap = 11f * k * 1.3f
            var room = inner - title
            val showCap = room - cap - 26f >= 0f
            if (showCap) { room -= cap; text(v, R.id.widget_caption, caption(r), 11f * k, r.pal.accent) } else gone(v, R.id.widget_caption)
            titleRow(v, r, avail, 16f * k, 13f * k, r.pal.text)
            val want = minOf(30f * k, (room - 4f) / 0.842f)
            scale = countScale(r, want, avail)
            room -= CountPicture.heightDp(r.app, scale) + 2f
            if (r.o.place && room >= 11f * k * 1.3f + 2f) place(v, r, avail, 11f * k, 9.5f, r.pal.muted, hijri = false) else gone(v, R.id.widget_place)
        }
        if (r.o.countdown) countdown(v, r, scale) else gone(v, R.id.widget_countdown_box)
        common(v, r)
        built(Built(layout, scale))
        return v
    }

    /** Today's prayers: 4×1 one row, 4×2 the next prayer with the six times under, 2×3+ a column, 4×3+ a grid. */
    private fun today(r: WidgetCtx, built: (Built) -> Unit): RemoteViews {
        val narrow = r.wDp < 250
        if (narrow && r.hDp < 190) return next(r, built)
        val shown = shownPrayers(r)
        val n = shown.size
        val k = r.k
        val avail = r.wDp - 2 * PAD
        val l = when {
            r.hDp < 115 -> TODAY_ROW
            narrow -> TODAY_COLUMN
            r.hDp >= 250 -> TODAY_GRID
            else -> TODAY
        }
        val layout = pick(l, r)
        val v = RemoteViews(r.app.packageName, layout)
        val ws = r.ws!!
        val c = r.c!!
        val times = shown.map { time(r, ws.day.instant(it)) }
        val names = shown.map { r.name(it) }
        var scale = 0.6f
        var barPx = 0
        when (l) {
            TODAY_ROW -> {
                background(v, r, skyOptions(r, horizon = 0.62f), R.drawable.widget_scrim_dense)
                val cell = avail / n - 6f
                cells(v, r, shown, Fit.size(names, Fit.REGULAR, 12f * k, cell), timeSize(times, 15f * k, cell), signs = false)
            }
            TODAY_COLUMN -> {
                background(v, r, skyOptions(r, horizon = 0.55f), R.drawable.widget_scrim_dense)
                val name = r.prayer
                text(v, R.id.widget_prayer, name, Fit.size(listOf(name), Fit.MEDIUM, 18f * k, avail), r.pal.text)
                text(v, R.id.widget_time, time(r, c.countAt), 13f * k, r.pal.secondary)
                scale = countScale(r, 30f * k, avail)
                barPx = (avail * r.density).toInt()
                v.setImageViewBitmap(R.id.widget_progress, bar(r, barPx))
                val rows = r.hDp - 2 * PAD_V - (18f + 13f) * k * 1.25f - CountPicture.heightDp(r.app, scale) - 22f - 18f * k
                val rowSize = minOf(14f * k, rows / n / 1.45f)
                val nameAvail = avail - 16f - timeWidth(times.maxByOrNull { it.length } ?: "", rowSize) - 8f
                cells(v, r, shown, Fit.size(names, Fit.REGULAR, rowSize, nameAvail), rowSize, signs = false)
                for (id in NAMES) v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, Fit.size(names, Fit.REGULAR, rowSize, nameAvail))
                place(v, r, avail, 11f * k, 9.5f, r.pal.muted)
            }
            else -> {
                val grid = l == TODAY_GRID
                background(v, r, skyOptions(r, horizon = if (grid) 0.50f else 0.62f), R.drawable.widget_scrim_dense)
                scale = countScale(r, (if (grid) 34f else 28f) * k, avail * 0.46f)
                val countW = if (r.o.countdown) CountPicture.widthDp(r.app, leading(r), scale, r.o.seconds && r.mode == Widgets.CountMode.WHEEL) + 10f else 0f
                titleRow(v, r, avail - countW, (if (grid) 19f else 17f) * k, 13f * k, r.pal.text)
                place(v, r, avail - countW, 11.5f * k, 10f, r.pal.muted)
                barPx = (avail * r.density).toInt()
                v.setImageViewBitmap(R.id.widget_progress, bar(r, barPx))
                if (grid) {
                    val cell = avail / 3f - 8f
                    cells(v, r, shown, Fit.size(names, Fit.REGULAR, 13f * k, cell), timeSize(times, 17f * k, cell), signs = true)
                } else {
                    // The cells take what the header leaves, up to a calm 60 dp, the text growing with them.
                    val free = r.hDp - 2 * PAD_V - 17f * k * 1.25f - 11.5f * k * 1.3f - 13f
                    val cellH = (free * 0.62f).coerceIn(44f, 60f)
                    if (android.os.Build.VERSION.SDK_INT >= 31) for (id in CELLS) v.setViewLayoutHeight(id, cellH, TypedValue.COMPLEX_UNIT_DIP)
                    val grow = if (android.os.Build.VERSION.SDK_INT >= 31) cellH / 44f else 1f
                    val cell = avail / n - 6f
                    cells(v, r, shown, Fit.size(names, Fit.REGULAR, minOf(11.5f * grow, 13f) * k, cell), timeSize(times, minOf(14.5f * grow, 17.5f) * k, cell))
                }
            }
        }
        if (l != TODAY_ROW) { if (r.o.countdown) countdown(v, r, scale) else gone(v, R.id.widget_countdown_box) }
        common(v, r)
        built(Built(layout, scale, barPx = barPx))
        return v
    }

    /** Minhad sky: the home sky in small with the Sun's path, the next prayer and the countdown at the top. */
    private fun sky(r: WidgetCtx, built: (Built) -> Unit): RemoteViews {
        val layout = pick(SKY, r)
        val v = RemoteViews(r.app.packageName, layout)
        val ws = r.ws!!
        val size = Widgets.pictureSize(r.app, r.wDp, r.hDp)
        val big = r.hDp >= 240
        val o = skyOptions(r, horizon = if (big) 0.66f else 0.76f, path = true, arcTop = if (big) 0.30f else 0.40f)
        v.setImageViewBitmap(R.id.widget_bg, WidgetSky.picture(r.app.resources, ws, size.w, size.h, size.density, false, o))
        v.setImageViewResource(R.id.widget_scrim, if (big) R.drawable.widget_scrim_full else R.drawable.widget_scrim_top)
        v.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
        val k = r.k
        val avail = r.wDp - 2 * PAD
        val scale = countScale(r, 28f * k, avail * 0.45f)
        val countW = if (r.o.countdown) CountPicture.widthDp(r.app, leading(r), scale, r.o.seconds && r.mode == Widgets.CountMode.WHEEL) + 10f else 0f
        titleRow(v, r, avail - countW, 18f * k, 13f * k, r.pal.text)
        place(v, r, avail - countW, 11.5f * k, 10f, r.pal.secondary)
        // 4x3 and larger: the day's times along the foot, over the land.
        if (r.hDp >= 240) {
            v.setViewVisibility(R.id.widget_cells, View.VISIBLE)
            val shown = shownPrayers(r)
            val cell = avail / shown.size - 6f
            cells(v, r, shown, Fit.size(shown.map { r.name(it) }, Fit.REGULAR, 12f * k, cell), timeSize(shown.map { time(r, ws.day.instant(it)) }, 15f * k, cell))
        }
        if (r.o.countdown) countdown(v, r, scale) else gone(v, R.id.widget_countdown_box)
        common(v, r)
        built(Built(layout, scale))
        return v
    }

    /** Countdown ring: the time from the last prayer to the next as a ring; inside, the prayer, the countdown, its time. */
    private fun ring(r: WidgetCtx, built: (Built) -> Unit): RemoteViews {
        val layout = pick(RING, r)
        val v = RemoteViews(r.app.packageName, layout)
        background(v, r, skyOptions(r, horizon = 0.86f), R.drawable.widget_scrim_full)
        val c = r.c!!
        val k = r.k
        val ringDp = minOf(r.wDp, r.hDp) - 2 * 8f
        val ringPx = (ringDp * r.density).toInt()
        v.setImageViewBitmap(R.id.widget_ring, WidgetArt.ring(ringPx, r.density, r.progress, r.pal.accent, r.pal.track, ringStroke(ringDp)))
        // Inside the ring: the width of its inner circle a little above and below the middle.
        val inner = (ringDp - 2 * 5f) * 0.72f
        val name = r.prayer
        // Larger rings, larger text inside (a 2x2 ring is about 150 dp across).
        val grow = (ringDp / 150f).coerceIn(1f, 1.5f)
        text(v, R.id.widget_prayer, name, Fit.size(listOf(name), Fit.MEDIUM, 15f * grow * k, inner * 0.86f), r.pal.accent)
        val t = time(r, c.countAt)
        text(v, R.id.widget_time, t, timeSize(listOf(t), 12f * grow * k, inner * 0.8f, Fit.REGULAR), r.pal.secondary)
        val scale = countScale(r, minOf(40f, ringDp / 5.0f) * k, inner)
        if (r.o.countdown) countdown(v, r, scale) else gone(v, R.id.widget_countdown_box)
        common(v, r)
        built(Built(layout, scale, ringPx = ringPx))
        return v
    }

    /** Prayer strip: the next prayer and the countdown on a line, the day's prayers in a row under it. */
    private fun strip(r: WidgetCtx, built: (Built) -> Unit): RemoteViews {
        val layout = pick(STRIP, r)
        val v = RemoteViews(r.app.packageName, layout)
        background(v, r, skyOptions(r, horizon = 0.70f), R.drawable.widget_scrim_dense)
        val ws = r.ws!!
        val k = r.k
        val avail = r.wDp - 2 * PAD
        val shown = Prayer.entries.filter { it != Prayer.SHURUQ || r.o.shuruq }
        val n = shown.size
        val inner = r.hDp - 2 * 8f
        val head = minOf(14f * k, inner * 0.24f)
        val scale = countScale(r, minOf(22f * k, inner * 0.36f), avail * 0.45f)
        val countW = if (r.o.countdown) CountPicture.widthDp(r.app, leading(r), scale, r.o.seconds && r.mode == Widgets.CountMode.WHEEL) + 10f else 0f
        val c = r.c!!
        val t = time(r, c.countAt)
        val timeDp = minOf(12f * k, head * 0.86f)
        val name = r.prayer
        text(v, R.id.widget_prayer, name, Fit.size(listOf(name), Fit.MEDIUM, head, avail - countW - timeWidth(t, timeDp, Fit.REGULAR) - 10f), r.pal.accent)
        text(v, R.id.widget_time, t, timeDp, r.pal.secondary)
        val cell = avail / n - 6f
        val rowRoom = inner - CountPicture.heightDp(r.app, scale) - 2f
        val times = shown.map { time(r, ws.day.instant(it)) }
        val names = shown.map { r.name(it) }
        val timeWant = minOf(14f * k, rowRoom * 0.42f)
        cells(v, r, shown, Fit.size(names, Fit.REGULAR, minOf(11f * k, rowRoom * 0.33f), cell), timeSize(times, timeWant, cell))
        if (r.o.countdown) countdown(v, r, scale) else gone(v, R.id.widget_countdown_box)
        common(v, r)
        built(Built(layout, scale))
        return v
    }

    /**
     * The occasion to count down to: the next of the year's (Ramadan, the Eids, Arafah, Ashura, the new year), or the
     * White Days when they are under way or two days away at most (they come every month, and would otherwise always
     * hide the others).
     */
    internal fun nextOccasion(today: LocalDate, adjust: Int): com.noor.prayer.calendar.UpcomingEvent? {
        val all = IslamicCalendar.upcoming(today, adjust)
        val white = all.firstOrNull { it.event == com.noor.prayer.calendar.IslamicEvent.WHITE_DAYS }?.takeIf { it.daysUntil <= 2 }
        return white ?: all.firstOrNull { it.event != com.noor.prayer.calendar.IslamicEvent.WHITE_DAYS }
    }

    /** Date: the Hijri date large, the Gregorian date and weekday, and the next Islamic occasion in days. */
    private fun date(r: WidgetCtx, built: (Built) -> Unit): RemoteViews {
        // Wide and short (4x1): the day's number large beside the month; taller: the lines stacked, larger.
        val wide = r.wDp >= 250 && r.hDp < 140
        val layout = pick(if (wide) DATE_WIDE else DATE, r)
        val v = RemoteViews(r.app.packageName, layout)
        background(v, r, if (r.ws != null) skyOptions(r, horizon = 0.80f) else null, R.drawable.widget_scrim_full)
        val k = r.k
        val f = r.f
        val today = r.today
        val h = HijriDate.of(today, r.s.hijriAdjust)
        val month = if (f.arabic) h.monthNameAr else h.monthNameEn
        val year = f.digits(h.year.toString()) + if (f.arabic) " هـ" else " AH"
        val greg = "${f.weekday(today)}${if (f.arabic) "، " else ", "}${f.gregorianDate(today)}"
        val next = nextOccasion(today, r.s.hijriAdjust)
        val occasionName = next?.let { if (f.arabic) it.event.ar else it.event.en }
        val days = next?.let { e -> if (e.daysUntil == 0L) r.ctx.getString(R.string.widget_occasion_today)
            else r.ctx.resources.getQuantityString(R.plurals.widget_in_days, e.daysUntil.toInt(), f.number(e.daysUntil.toInt())) }
        val avail = r.wDp - 2 * PAD
        if (wide) {
            val num = f.number(h.day)
            val tallness = ((r.hDp - 84f) / 96f).coerceIn(0f, 1f)
            val numDp = minOf((44f + 12f * tallness) * k, (r.hDp - 2 * PAD_V) * 0.9f)
            val g = 1f + 0.12f * tallness
            text(v, R.id.widget_hijri_number, num, numDp, r.pal.accent)
            val numW = Fit.width(num, Fit.LIGHT, numDp) + 12f
            val occW = if (next != null) maxOf(Fit.width(occasionName!!, Fit.REGULAR, 11f * g * k), Fit.width(days!!, Fit.MEDIUM, 13f * g * k)).coerceAtMost(avail * 0.30f) + 12f else 0f
            val mid = avail - numW - occW - 10f
            val line = "$month $year"
            text(v, R.id.widget_hijri_day, line, Fit.size(listOf(line), Fit.MEDIUM, 16f * g * k, mid), r.pal.text)
            text(v, R.id.widget_gregorian, greg, Fit.size(listOf(greg), Fit.REGULAR, 12f * g * k, mid), r.pal.secondary)
            if (next != null) {
                text(v, R.id.widget_occasion_name, occasionName!!, Fit.size(listOf(occasionName), Fit.REGULAR, 11f * g * k, avail * 0.30f), r.pal.muted)
                text(v, R.id.widget_occasion, days!!, Fit.size(listOf(days), Fit.MEDIUM, 13f * g * k, avail * 0.30f), r.pal.accent)
            } else gone(v, R.id.widget_occasion_box)
        } else {
            // From the top: the Hijri day and month, its year, the Gregorian date, the next occasion, as many as fit.
            val inner = r.hDp - 2 * PAD_V
            val g = minOf(r.wDp / 170f, r.hDp / 110f).coerceIn(1f, 1.45f)
            val small = 11.5f * g * k
            val lineH = small * 1.3f + 1f
            val daySize = minOf(21f * g * k, (inner - lineH) / 1.22f).coerceAtLeast(12f)
            var room = inner - daySize * 1.22f
            val line = "${f.number(h.day)} $month"
            text(v, R.id.widget_hijri_day, line, Fit.size(listOf(line), Fit.MEDIUM, daySize, avail), r.pal.text)
            val gregSize = Fit.size(listOf(greg), Fit.REGULAR, small, avail)
            if (room >= lineH) { text(v, R.id.widget_gregorian, greg, gregSize, r.pal.secondary); room -= lineH } else gone(v, R.id.widget_gregorian)
            if (room >= lineH) { text(v, R.id.widget_hijri_year, year, 11f * g * k, r.pal.accent); room -= lineH } else gone(v, R.id.widget_hijri_year)
            if (next != null && room >= lineH + 4f) {
                val occ = "$occasionName · $days"
                text(v, R.id.widget_occasion, occ, Fit.size(listOf(occ), Fit.REGULAR, small, avail, 8.5f), r.pal.muted)
            } else gone(v, R.id.widget_occasion)
        }
        direction(v, r)
        v.setOnClickPendingIntent(R.id.widget_root, Widgets.open(r.app, r.o.tap))
        v.setContentDescription(R.id.widget_root, listOfNotNull(f.hijri(h), greg, occasionName?.let { "$it, $days" }).joinToString(". "))
        built(Built(layout, 0f))
        return v
    }
}
