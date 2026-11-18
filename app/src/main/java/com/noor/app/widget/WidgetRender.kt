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
    /** A line's height over its text size: Arabic's system font stands taller than Latin's. */
    /**
     * A line's height over its text size, for deciding what fits: the taller script's (Arabic's), in both languages, so
     * an English and an Arabic widget of one size show the same lines (the owner: the same content in both).
     */
    val lineK: Float get() = if (s.numerals == com.noor.app.data.Numerals.ARABIC_INDIC) 1.65f else 1.5f

    /** The same words in the app's other language (to size and choose text alike in both). */
    val other: Formatter get() = Formatter(!f.arabic, s.numerals, s.timeFormat)

    /** The prayer counted to or from, in the app's language (as [prayer]). */
    fun name0(): String = prayer

    /** The prayer counted to or from, named in the other language. */
    val otherPrayer: String get() {
        val c = c ?: return ""
        val p = c.event ?: return c.prayer
        return if (o.jumuah) other.prayerName(p, c.eventDate ?: today) else other.prayerName(p)
    }

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

/** What a full update built for one widget (0.24: the countdown's scale, for the tests and the seconds wheel). */
internal class Built(val countScale: Float, val design: Design) {
    /** The countdown picture handed over with it (Android 12 and later), or null. */
    var countSpec: CountPicture.Spec? = null
}

/**
 * The widgets' builders (0.24): each widget is composed for its size ([WidgetDesigns]), drawn as pictures in IBM Plex
 * ([Sheet]) over a full-bleed background ([WidgetBack]), and handed to the home screen in one layout
 * (res/layout/widget_v3.xml): each picture placed by its padding, the live countdown (its picture and the seconds
 * wheel) by its slot's.
 */
internal object WidgetRender {
    val BLOCKS = intArrayOf(R.id.widget_block_0, R.id.widget_block_1, R.id.widget_block_2, R.id.widget_block_3, R.id.widget_block_4,
        R.id.widget_block_5, R.id.widget_block_6, R.id.widget_block_7, R.id.widget_block_8, R.id.widget_block_9, R.id.widget_block_10,
        R.id.widget_block_11, R.id.widget_block_12, R.id.widget_block_13)

    /** The design of [r]'s kind for its size. */
    fun design(r: WidgetCtx): Design {
        val sheet = Sheet(r.ctx, r.wDp.toFloat(), r.hDp.toFloat(), r.rtl, r.density)
        val d = WidgetDesigns(r, sheet)
        val wide = r.wDp >= 250
        // The size class whose composition comes out largest at this size (a taller one when it is nearly as large:
        // it shows more), by the scale min(w / base w, h / base h) of each class's base size.
        val baseW = if (wide) 360f else 176f
        fun scale(baseH: Float) = minOf(r.wDp / baseW, r.hDp / baseH)
        val rows = when {
            scale(176f) < 0.9f * scale(84f) -> 1
            scale(372f) < 0.9f * scale(176f) -> 2
            else -> 4
        }
        when (r.kind) {
            // A 1-row widget under 300 dp wide reads best as the 2×1 (the name and its time on one line, the countdown
            // under it, larger) than as a squeezed 4×1.
            WidgetKind.NEXT -> when (rows) { 1 -> if (r.wDp >= 300) d.next4x1() else d.next2x1(); 2 -> if (wide) d.next4x2() else d.next2x2(); else -> d.nextTall() }
            WidgetKind.TODAY -> when (rows) { 1 -> if (wide) d.strip4x1() else d.next2x1(); 2 -> if (wide) d.today4x2() else d.today2x2(); else -> d.today4x4() }
            WidgetKind.STRIP -> if (rows == 1 || wide) d.strip4x1() else d.today2x2()
            WidgetKind.RING -> d.ring2x2()
            WidgetKind.SKY -> if (wide && rows >= 2) d.sky4x2() else if (rows == 1) (if (wide) d.next4x1() else d.next2x1()) else d.next2x2()
            WidgetKind.DATE -> d.date2x2()
        }
        return sheet.design(WidgetBack.draw(r, r.wDp.toFloat(), r.hDp.toFloat()))
    }

    /** Builds the widget at its size; [built] receives what was drawn. */
    fun build(r: WidgetCtx, built: (Built) -> Unit = {}): RemoteViews {
        val design = design(r)
        val v = views(r, design)
        built(Built(design.count?.scale ?: 0f, design).also { it.countSpec = if (design.count != null) Widgets.lastCount else null })
        return v
    }

    fun views(r: WidgetCtx, design: Design): RemoteViews {
        val v = RemoteViews(r.app.packageName, R.layout.widget_v3)
        val d = r.density
        if (design.back != null) v.setImageViewBitmap(R.id.widget_bg, design.back) else v.setViewVisibility(R.id.widget_bg, View.GONE)
        fun px(dp: Float) = (dp * d + 0.5f).toInt()
        for ((i, id) in BLOCKS.withIndex()) {
            val b = design.blocks.getOrNull(i)
            if (b == null) { v.setViewVisibility(id, View.GONE); continue }
            v.setViewVisibility(id, View.VISIBLE)
            v.setImageViewBitmap(id, b.bitmap)
            v.setViewPadding(id, px(b.rect.left), px(b.rect.top), px(design.wDp - b.rect.right), px(design.hDp - b.rect.bottom))
        }
        val spot = design.count
        if (spot != null) {
            v.setViewVisibility(R.id.widget_count_slot, View.VISIBLE)
            v.setViewPadding(R.id.widget_count_slot, px(spot.left), px(spot.top), 0, 0)
            liveCount(v, r, spot.scale, WidgetDesigns(r, Sheet(r.ctx, 1f, 1f, r.rtl, d)).ink)
        } else v.setViewVisibility(R.id.widget_count_slot, View.GONE)
        v.setOnClickPendingIntent(android.R.id.background, Widgets.open(r.app, r.o.tap))
        describe(v, r)
        return v
    }

    /** Before a place is chosen: an invitation to open Minhad. */
    fun message(r: WidgetCtx): RemoteViews {
        val sheet = Sheet(r.ctx, r.wDp.toFloat(), r.hDp.toFloat(), r.rtl, r.density)
        val d = WidgetDesigns(r, sheet)
        val text = r.ctx.getString(R.string.widget_choose_place)
        val w = r.wDp - 32f
        val l = d.fit(d.line(d.run(text, 15f, WidgetInk.Weight.MEDIUM, d.ink)), w, 10f) ?: d.line(d.run(text, 10f, WidgetInk.Weight.MEDIUM, d.ink))
        sheet.block { sheet.text(l, 16f, r.hDp / 2f + l.maxSize * 0.36f, w, Sheet.Align.CENTER, "message") }
        val v = views(r, sheet.design(WidgetBack.draw(r, r.wDp.toFloat(), r.hDp.toFloat())))
        v.setOnClickPendingIntent(android.R.id.background, Widgets.open(r.app, WidgetTap.HOME))
        return v
    }

    /** What a screen reader says for the widget: the prayer, its time and the time left or since. */
    private fun describe(v: RemoteViews, r: WidgetCtx) {
        val c = r.c ?: return
        val d = if (c.elapsed) Duration.between(c.countAt, r.now) else Duration.between(r.now, c.countAt)
        val (h, m) = (maxOf(0L, d.seconds) / 60).let { it / 60 to it % 60 }
        val span = if (h > 0) r.ctx.getString(R.string.hours_minutes, r.f.number(h.toInt()), r.f.number(m.toInt()))
            else r.ctx.getString(R.string.minutes_only, r.f.number(m.toInt()))
        v.setContentDescription(android.R.id.background, r.ctx.getString(if (c.elapsed) R.string.widget_cd_after else R.string.widget_cd_before, r.prayer, c.time, span))
    }

    /** "−1:23" / "+13", the countdown before its seconds; with the seconds off, "−1:23" / "+0:13". */
    internal fun leading(r: WidgetCtx): String {
        val c = r.c ?: return ""
        return if (r.o.seconds && r.mode == Widgets.CountMode.WHEEL) Widgets.leading(c, r.now, r.f) else Widgets.minuteCountdown(c, r.now, r.f)
    }

    /**
     * The live countdown (0.9.14; 0.24 in Plex Medium, the seconds at 62 %): "−1:23" a picture drawn for each minute,
     * the seconds a wheel of Minhad's digits that Android's AnalogClock turns as its second hand, seen through a window
     * ([Widgets.wheelFor]); the clock made smaller or larger to draw the wheel at [scale]. Android 12 and later.
     */
    fun liveCount(v: RemoteViews, r: WidgetCtx, scale: Float, ink: Int) {
        val c = r.c ?: return
        val quiet = (ink and 0x00FFFFFF) or ((((ink ushr 24) * 0.62f).toInt()) shl 24)
        val seconds = r.o.seconds
        val spec = CountPicture.Spec(leading(r), scale, ink, quiet, shadow = false, colon = seconds)
        Widgets.lastCount = spec
        v.setImageViewBitmap(R.id.widget_count_main, CountPicture.draw(r.app, spec))
        v.setViewVisibility(R.id.widget_count_window, if (seconds) View.VISIBLE else View.GONE)
        if (!seconds) return
        val res = r.app.resources; val d = r.density
        val w = Widgets.wheelFor(c, r.s.numerals == Numerals.ARABIC_INDIC)
        v.setIcon(R.id.widget_count_wheel, "setSecondHand", Icon.createWithResource(r.app, w.drawable))
        v.setString(R.id.widget_count_wheel, "setTimeZone", w.zone)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            v.setViewLayoutWidth(R.id.widget_count_window, res.getDimension(if (r.s.numerals == Numerals.ARABIC_INDIC) R.dimen.noor_count_window_arabic else R.dimen.noor_count_window_latin) * scale / d, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutHeight(R.id.widget_count_window, res.getDimension(R.dimen.noor_count_height) * scale / d, TypedValue.COMPLEX_UNIT_DIP)
            val wheel = res.getDimension(R.dimen.noor_count_wheel) * scale / d
            v.setViewLayoutWidth(R.id.widget_count_wheel, wheel, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutHeight(R.id.widget_count_wheel, wheel, TypedValue.COMPLEX_UNIT_DIP)
            v.setColorStateList(R.id.widget_count_wheel, "setSecondHandTintList", ColorStateList.valueOf(quiet))
        }
    }

    /**
     * Where the times are for, as the app names the place: the area (a district or neighbourhood, or the chosen city)
     * and the city or region it is in ("Madinat Al Mataar" and "Dubai"), in the app's language only (no English city
     * beside an Arabic area). The country is left out; where the app writes one it is under its rule (Israel as
     * Palestine).
     */
    internal fun placeParts(r: WidgetCtx, arabic: Boolean = r.f.arabic): Pair<String?, String?> {
        if (!r.o.place) return null to null
        val p = r.ws?.place
        val area = (if (arabic == r.f.arabic) r.c?.place else if (arabic) p?.nameAr ?: p?.nameEn else p?.nameEn)?.let { StatusNotification.shortPlace(it) }
        val context = if (arabic) p?.contextAr else p?.contextEn
        val city = context?.split(',', '\u060C')?.map { it.trim() }?.firstOrNull { it.isNotEmpty() && it != area }
        return area to city
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

}
