package com.noor.app.widget

import android.graphics.RectF
import com.noor.app.R
import com.noor.app.notify.StatusNotification
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import java.time.Instant
import java.time.LocalDate
import com.noor.app.widget.WidgetInk.Line
import com.noor.app.widget.WidgetInk.Run
import com.noor.app.widget.WidgetInk.Weight
import com.noor.app.widget.Sheet.Align

/**
 * The widgets' compositions (0.24), one per kind and size class, as the design's mock (its dp are the spec at the
 * base sizes 2×1 176×84, 2×2 176×176, 4×1 360×84, 4×2 360×176, 4×4 360×372). Other sizes take their class's
 * composition scaled by min(w / base w, h / base h), so larger widgets get larger text. Text never goes under its
 * floor (names 10 dp, times 11 dp, the marked time 12 dp): content is dropped first, in the order each composition
 * says.
 */
internal class WidgetDesigns(val r: WidgetCtx, val sheet: Sheet) {
    companion object {
        /** The seconds wheel's largest text size (dp): its drawables are made at it (tools/widget/SecondsWheel.java). */
        const val MAX_WHEEL = 40f
    }

    private val rtl = sheet.rtl
    private val W = sheet.wDp
    private val H = sheet.hDp
    private val k = r.o.textScaleFactor * r.ctx.resources.configuration.fontScale.coerceIn(0.85f, 1.15f)
    private val o = r.o
    /** Where the text stands in the compositions that may choose (Next 2×1, 2×2 and tall, the Date 2×2). */
    private val al = if (r.o.align == WidgetAlign.CENTER) Align.CENTER else Align.START
    private val centred = r.o.align == WidgetAlign.CENTER

    // ---- Colours ----

    /** The colours chosen, kept legible ([WidgetLooks]). */
    val looks = WidgetLooks.of(r)
    private val light = looks.light
    private val clear = r.pal.background == WidgetBackground.CLEAR
    val ink: Int = looks.ink
    /** Over the sky, glass, a colour of one's own or a see-through background the quieter text is less quiet (3:1). */
    private val sky = looks.vivid
    private fun alpha(c: Int, f: Float) = (c and 0x00FFFFFF) or (((c ushr 24) * f).toInt().coerceIn(0, 255) shl 24)
    /** Secondary, tertiary and past text: 72 %, 52 %, 50 % of the ink (stronger where 4.5:1 or 3:1 asks it). */
    // Over the vivid sky (white reads at 4.6:1 at least there) the quieter text is less quiet, to keep 3:1.
    // (Each at the least strength that reads on the background: WidgetLooks.)
    val sec = alpha(ink, if (clear) 0.9f else looks.secK)
    val ter = alpha(ink, if (clear) 0.82f else looks.terK)
    private val pastK = if (o.past == PastStyle.NORMAL) 1f else if (clear) 0.8f else looks.pastK
    /** The accent: the design's warm gold (#FFD36B) on colour, or the one chosen (one's own where it reads). */
    val gold = looks.accent
    /** The prayers' signs: shown at all, and how. */
    private val iconsOn = o.icons != IconStyle.OFF && o.showIcon
    private fun iconSize(sz: Float) = if (iconsOn) sz else 0f
    private fun ig(gap: Float) = if (iconsOn) gap else 0f
    private fun icon(p: Prayer, x: Float, top: Float, size: Float, color: Int) { if (iconsOn && size > 0f) sheet.icon(p, x, top, size, color) }
    /** The prayers' signs in their own colours (deeper on light backgrounds), or the accent and the text's. */
    fun iconColor(p: Prayer, on: Boolean): Int {
        if (o.icons == IconStyle.MONO) return if (on) gold else sec
        val c = when (p) { Prayer.FAJR -> 0xFF8FD0FF; Prayer.SHURUQ -> 0xFFFFB14A; Prayer.DHUHR -> 0xFFFFD23F; Prayer.ASR -> 0xFFFFBE3B
            Prayer.MAGHRIB -> 0xFFFF8A4C; Prayer.ISHA -> 0xFFE9EDFF }.toInt()
        if (!light) return c
        return when (p) { Prayer.FAJR -> 0xFF2E86C9; Prayer.SHURUQ -> 0xFFD9801A; Prayer.DHUHR -> 0xFFC79A00; Prayer.ASR -> 0xFFD18A00
            Prayer.MAGHRIB -> 0xFFD9581C; Prayer.ISHA -> 0xFF4F5B93 }.toInt()
    }
    val track = if (light) 0x1A000000 else 0x38FFFFFF
    val divider = if (light) 0x1A000000 else 0x26FFFFFF

    // ---- Words ----

    private val c = r.c
    private val ws = r.ws
    /** The prayer counted to or from. */
    val counted: Prayer? = c?.event
    val countedAt: Instant? = c?.countAt

    fun name(p: Prayer, date: LocalDate = r.today) = if (r.o.jumuah) r.f.prayerName(p, date) else r.f.prayerName(p)
    val countedName: String get() = if (o.showName) r.prayer else ""

    fun run(text: String, size: Float, weight: Weight, color: Int, gap: Float = 0f) = Run(text, size, weight, color, gap)
    fun line(vararg runs: Run) = Line(runs.toList(), rtl)

    /** "3:14" and, in 12 hours, "PM" at 0.62 of the size in Medium. */
    fun time(at: Instant?, size: Float, weight: Weight, color: Int, suffix: Boolean = true): List<Run> {
        if (at == null) return listOf(run("—", size, weight, color))
        val z = at.atZone(r.zone)
        val (t, s0) = r.f.time(z.hour * 60 + z.minute)
        val s = s0.takeIf { o.ampm }
        // The marker a thin space (U+2009) from the digits, on the reading side (an Arabic "3:14 م" has its م to the left).
        return if (s == null || !suffix) listOf(run(t, size, weight, color))
            else listOf(run(t, size, weight, color), Run(s, size * (if (r.f.arabic) 0.72f else 0.62f), Weight.MEDIUM, color, thinSpace(size), sep = "\u2009"))
    }

    /** The counted prayer's Adhan time beside its name, or (hidden) nothing; with the Adhan time as the large number, the countdown to the minute. */
    fun countedTime(size: Float, weight: Weight, color: Int): List<Run> = when {
        o.main == MainNumber.ADHAN && o.countdown -> listOf(run(minuteWords(), size, weight, color))
        o.main == MainNumber.ADHAN || !o.showTime -> listOf(run("", size, weight, color))
        else -> time(countedAt, size, weight, color)
    }

    private fun thinSpace(size: Float) = sheet.width(Line(listOf(Run("\u2009", size, Weight.REGULAR, 0)), false)).coerceAtLeast(size * 0.16f)

    /** [l] made smaller to fit [w], never under [floor]; null when it cannot. */
    fun fit(l: Line, w: Float, floor: Float): Line? {
        val lw = sheet.width(l)
        if (lw <= w) return l
        val f = w / lw * 0.995f
        if (l.runs.maxOf { it.size } * f < floor) return null
        return Line(l.runs.map { Run(it.text, it.size * f, it.weight, it.color, it.gapBefore * f, it.sep) }, l.rtl)
    }

    // ---- Times of the day ----

    /** The day whose prayers are listed: today, or tomorrow once today's last is behind (counting to tomorrow's Fajr). */
    val listDate: LocalDate get() = countedAt?.atZone(r.zone)?.toLocalDate()?.takeIf { it.isAfter(r.today) } ?: r.today
    fun at(p: Prayer, date: LocalDate = listDate): Instant? = ws?.config?.day(date)?.instant(p)
    /** The prayers listed: Shuruq as chosen (where there is room, by default), the past ones unless hidden. */
    val shown: List<Prayer> get() = Prayer.entries.filter { (it != Prayer.SHURUQ || o.shuruqShown != ShuruqChoice.HIDE) && !(o.past == PastStyle.HIDE && past(it)) }
    /** Whether Shuruq may give way where the room is short (not when it is asked for). */
    val shuruqMayGo: Boolean get() = o.shuruqShown == ShuruqChoice.AUTO
    /** The prayer marked in lists (the one counted to or from, if it is on the listed day). */
    val marked: Prayer? get() = counted?.takeIf { countedAt?.atZone(r.zone)?.toLocalDate() == listDate }
    fun past(p: Prayer): Boolean {
        val m = marked ?: return false
        return p.ordinal < m.ordinal
    }
    /** A past prayer's text: the ink at the past's strength (whatever its own strength was), never fainter. */
    fun pastColor(color: Int, p: Prayer) = if (past(p)) alpha(ink, pastK) else color
    /**
     * The marked prayer's words: the accent where it reads on its capsule (dark, light, glass), the ink over the vivid
     * sky and the wallpaper (gold on a white capsule there is under 3:1; the gold stays in its sign).
     */
    val markText: Int get() = if (o.highlight != HighlightStyle.FILLED) looks.accentOnBack else if (clear) ink else looks.markOnCapsule

    /** The marked prayer's mark: its capsule, an outline, or (only its colour) nothing. */
    fun mark(cv: android.graphics.Canvas, b: RectF, radius: Float): Unit = when (o.highlight) {
        HighlightStyle.FILLED -> WidgetInk.capsule(cv, b, radius, light, gold, looks.capsule)
        HighlightStyle.OUTLINE -> WidgetInk.outline(cv, b, radius, if (light) 0x40000000 else 0x66FFFFFF)
        HighlightStyle.COLOR -> Unit
    }

    /** The two prayers after the one counted, from today into tomorrow. */
    fun later(n: Int): List<Pair<Prayer, Instant>> {
        if (!o.showLater) return emptyList()
        val from = countedAt ?: return emptyList()
        val out = ArrayList<Pair<Prayer, Instant>>()
        for (d in 0..1) for (p in Prayer.entries) {
            if (p == Prayer.SHURUQ && o.shuruqShown == ShuruqChoice.HIDE) continue
            val t = at(p, r.today.plusDays(d.toLong())) ?: continue
            if (t.isAfter(from) && out.size < n) out += p to t
        }
        return out
    }

    // ---- The countdown ----

    private val wheel = r.mode == Widgets.CountMode.WHEEL && o.countStyle == CountStyle.TIMER && o.main == MainNumber.COUNTDOWN
    /** Something stands where the countdown does: the countdown, or the Adhan's time as the large number. */
    private val bigShown get() = o.countdown || o.main == MainNumber.ADHAN
    private val seconds get() = r.o.seconds && wheel
    private fun leading(): String {
        val c = c ?: return ""
        return if (seconds) Widgets.leading(c, r.now, r.f) else Widgets.minuteCountdown(c, r.now, r.f)
    }

    /**
     * (0.24) The seconds beside hours and minutes of text size [size]: at most the wheel's own 40 dp (its drawables are
     * made at it; larger it would be a picture thousands of pixels across) and at most 0.74 of the minutes, on their
     * baseline, quieter, as the home screen writes them ("2:10" large, ":39" smaller).
     */
    fun secondsScale(size: Float) = if (!seconds) 1f else minOf(MAX_WHEEL, 0.74f * size) / size
    /** The countdown's width (dp) at text size [size]. */
    fun countWidth(size: Float) = bigLine(size)?.let { sheet.width(it) + 1f } ?: (CountPicture.widthDp(r.app, leading(), size / 40f, seconds, secondsScale = secondsScale(size)) + 1f)
    fun countHeight(size: Float) = CountPicture.heightDp(r.app, size / 40f)

    /** "in 1 h 4 min" / "1 h 4 min ago": the countdown in words, to the minute. */
    fun minuteWords(): String {
        val c = c ?: return ""
        if (o.countStyle == CountStyle.TIMER) return Widgets.minuteCountdown(c, r.now, r.f)
        val total = Widgets.minuteCountdown(c, r.now, com.noor.app.util.Formatter(false, com.noor.app.data.Numerals.WESTERN, com.noor.app.data.TimeFormat.H24))
            .filter { it.isDigit() || it == ':' }.split(':').let { (it[0].toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        val h = total / 60; val m = total % 60
        val span = if (h > 0) r.ctx.getString(R.string.hours_minutes, r.f.number(h), r.f.number(m)) else r.ctx.getString(R.string.minutes_only, r.f.number(m))
        return r.ctx.getString(if (c.elapsed) R.string.widget_words_ago else R.string.widget_words_in, span)
    }

    /** The large number when it is not the live timer: the Adhan's time, or the countdown in words; null for the timer. */
    private fun bigLine(size: Float): Line? = when {
        o.main == MainNumber.ADHAN -> line(*time(countedAt, size * 0.92f, Weight.SEMIBOLD, ink).toTypedArray())
        o.countStyle == CountStyle.WORDS -> line(run(minuteWords(), maxOf(12f, size * 0.6f), Weight.SEMIBOLD, ink))
        else -> null
    }
    /** The countdown's text size that fits [w], at most [want]. */
    fun countFit(want0: Float, w: Float): Float {
        if (!o.countdown && o.main != MainNumber.ADHAN) return want0
        // (The seconds beside it are at most the wheel's own size: secondsScale.)
        // (Not proportional: the seconds stop growing at the wheel's size. Shrunk until it fits.)
        var size = want0
        repeat(6) { val cw = countWidth(size); if (cw <= w) return size; size *= w / cw * 0.99f }
        return size
    }

    /** The countdown with its text's top (cap height) at [top]... placed by its picture's top, in a start-relative box. */
    fun countdown(x: Float, top: Float, size: Float, boxW: Float, align: Align) {
        val c = c ?: return
        val w = countWidth(size); val h = countHeight(size)
        val inBox = when (align) { Align.START -> 0f; Align.END -> boxW - w; Align.CENTER -> (boxW - w) / 2 }
        bigLine(size)?.let { l ->
            // The Adhan's time or the words, standing on the countdown's baseline.
            sheet.block { sheet.text(l, x + inBox, top + countAscent(size), w, Align.START, "countdown") }
            return
        }
        // The countdown reads left to right: its box is mirrored, never its digits.
        if (wheel) sheet.countAt(x + inBox, top, size / 40f, w, h, leading(), size, ink, secondsScale(size))
        else {
            val bmp = CountPicture.draw(r.app, CountPicture.Spec(leading(), size / 40f, ink, alpha(ink, 0.62f), shadow = false, colon = false))
            val d = r.density
            sheet.picture(bmp, x + inBox, top, bmp.width / d, bmp.height / d, "countdown", leading(), size, ink)
        }
    }

    /** The countdown's baseline below its picture's top (its digits stand on it). */
    fun countAscent(size: Float) = size * 0.773f

    fun bar(x: Float, top: Float, w: Float, h: Float) {
        if (!o.showBar) return
        val p = r.progress
        sheet.shape(x, top, w, h) { cv, b -> WidgetInk.bar(cv, b, p, rtl, track, if (light) gold else gold) }
    }

    // =====================================================================================================
    // NEXT PRAYER
    // =====================================================================================================

    /** Next 2×2 (176×176): the sign and the name; the Adhan time; the bar; the countdown large. */
    fun next2x2() {
        val s = minOf(W / 176f, H / 176f); val t = s * k
        val p = 16f * s
        val inner = W - 2 * p
        val cp = counted
        sheet.block {
            var y = p
            val iconSz = iconSize(20f * s)
            val nameL = fit(line(run(countedName, 22f * t, Weight.SEMIBOLD, ink)), inner - iconSz - 8f * s, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            val nameSize = nameL.maxSize
            val base = y + nameSize * 0.86f
            // Centred, the sign and the name stand together in the middle.
            val off = if (centred) ((inner - iconSz - ig(8f * s) - sheet.width(nameL)) / 2).coerceAtLeast(0f) else 0f
            if (cp != null) icon(cp, p + off, base - nameSize * 0.36f - iconSz / 2, iconSz, iconColor(cp, true))
            sheet.text(nameL, p + off + iconSz + ig(8f * s), base, inner - iconSz - 8f * s - off, Align.START, "name")
            y += nameSize * 1.05f + 4f * s
            fit(line(*countedTime(22f * t, Weight.REGULAR, sec).toTypedArray()), inner, 11f)?.let { tl ->
                sheet.text(tl, p, y + tl.maxSize * 0.86f, inner, al, "time")
            }
        }
        if (bigShown) {
            val size = countFit(37f * t, inner)
            val top = H - p - countHeight(size)
            bar(p, top - 12f * s - 6f * s, inner, 6f * s)
            countdown(p, top, size, inner, al)
        } else bar(p, H - p - 6f * s, inner, 6f * s)
    }

    /** Next 4×2 (360×176): the name and time large, the place, the countdown large; "Later today" with the next two. */
    fun next4x2() {
        // Where the list after it would come out small (a 4×1.5, 300×130), it goes, and the rest is drawn larger.
        val withList = minOf(W / 360f, H / 176f) * k >= 0.9f
        val s = if (withList) minOf(W / 360f, H / 176f) else minOf(W / 330f, H / 150f); val t = s * k
        val p = 18f * s
        val inner = W - 2 * p
        val gap = 18f * s
        val rightW = if (withList) (inner - gap) / 2.45f else 0f
        val leftW = if (withList) inner - gap - rightW else inner
        val cp = counted
        sheet.block {
            val iconSz = iconSize(26f * s)
            val nameRun = run(countedName, 30f * t, Weight.SEMIBOLD, ink)
            val timeRuns = countedTime(24f * t, Weight.REGULAR, sec).let { listOf(Run(it[0].text, it[0].size, it[0].weight, it[0].color, 10f * s)) + it.drop(1) }
            // The name and its time on one line beside the sign; the time drops before the name goes under its floor.
            val avail = leftW - iconSz - 10f * s
            val nameL = fit(Line(listOf(nameRun) + timeRuns, rtl), avail, 12f) ?: fit(line(nameRun), avail, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            val ns = nameL.runs[0].size
            val base = p + ns * 0.86f
            if (cp != null) icon(cp, p, base - ns * 0.36f - iconSz / 2, iconSz, iconColor(cp, true))
            sheet.text(nameL, p + iconSz + ig(10f * s), base, avail, Align.START, "name")
            if (infoOn && 14f * t >= 11f) placeLine(p, base + ns * 0.22f + 6f * s + 14f * t * 0.86f, leftW, 14f * t, ter)
        }
        if (bigShown) {
            val size = countFit(54f * t, if (withList) leftW + gap * 0.5f else inner)
            countdown(p, H - p - countHeight(size), size, leftW, Align.START)
        }
        // Later today, at the foot of the other column.
        val later = if (withList) later(2) else emptyList()
        if (later.isNotEmpty()) sheet.block {
            val rowH = 33f * s
            val x0 = p + leftW + gap
            var y = H - p - rowH * later.size
            val head = fit(line(run(r.ctx.getString(R.string.widget_later_today).let { if (r.f.arabic) it else it.uppercase() }, maxOf(11f, 12f * t), Weight.SEMIBOLD, ter)), rightW, 11f)
            if (head != null) sheet.text(head, x0, y - 4f * s, rightW, Align.START, "later_head")
            for ((lp, lt) in later) {
                sheet.shape(x0, y, rightW, 1f) { cv, b -> cv.drawRect(b, android.graphics.Paint().apply { color = divider }) }
                val base = y + 7f * s + 18f * t * 0.86f
                val tl = line(*time(lt, 17f * t, Weight.SEMIBOLD, ink, suffix = false).toTypedArray())
                val tw = sheet.width(tl)
                val nl = fit(line(run(name(lp, lt.atZone(r.zone).toLocalDate()), 17f * t, Weight.REGULAR, sec)), rightW - tw - 6f * s, 11f)
                if (nl != null) sheet.text(nl, x0, base, rightW - tw - 6f * s, Align.START, "later_name")
                sheet.text(tl, x0, base, rightW, Align.END, "later_time")
                y += rowH
            }
        }
    }

    /** Next 4×1 (360×84): the sign, the name over its time, the countdown large at the end. */
    fun next4x1() {
        val s = minOf(W / 360f, H / 84f); val t = s * k
        val px = 18f * s
        val inner = W - 2 * px
        val cp = counted
        val iconSz = iconSize(30f * s)
        val countSize = if (bigShown) countFit(42f * t, inner * 0.6f) else 0f
        val countW = if (bigShown) countWidth(countSize) else 0f
        val textW = inner - iconSz - 14f * s - (if (bigShown) countW + 14f * s else 0f)
        sheet.block {
            val nl = fit(line(run(countedName, 24f * t, Weight.SEMIBOLD, ink)), textW, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            val tl = fit(line(*countedTime(17f * t, Weight.REGULAR, sec).toTypedArray()), textW, 11f)
            val ns = nl.maxSize; val ts = tl?.maxSize ?: 0f
            val blockH = ns * 1.05f + if (tl != null) 2f * s + ts * 1.05f else 0f
            val top = (H - blockH) / 2
            if (cp != null) icon(cp, px, H / 2 - iconSz / 2, iconSz, iconColor(cp, true))
            val x = px + iconSz + ig(14f * s)
            sheet.text(nl, x, top + ns * 0.86f, textW, Align.START, "name")
            if (tl != null) sheet.text(tl, x, top + ns * 1.05f + 2f * s + ts * 0.86f, textW, Align.START, "time")
        }
        if (bigShown) countdown(px, (H - countHeight(countSize)) / 2, countSize, inner, Align.END)
    }

    /** Next 2×1 (176×84): the name and its time on one line, the countdown under it. Under 64 dp high: one line. */
    fun next2x1() {
        val s = minOf(W / 176f, H / 84f); val t = s * k
        val px = 16f * s
        val inner = W - 2 * px
        if (H < 64f) {
            // The smallest 2×1 (a launcher's 110×40): the name at the start, the countdown to the minute at the end.
            val nameAt12 = sheet.width(line(run(countedName, 12f, Weight.SEMIBOLD, ink)))
            val countSize = if (bigShown) countFit(minOf(26f, (H - 10f) / 0.85f) * k, minOf(inner * 0.58f, inner - nameAt12 - 8f)) else 0f
            val cw = if (bigShown) countWidth(countSize) + 6f else 0f
            sheet.block {
                val nl = fit(line(run(countedName, minOf(17f * k, 15f), Weight.SEMIBOLD, ink)), inner - cw, 12f)
                    ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
                sheet.text(nl, px, H / 2 + nl.maxSize * 0.36f, inner - cw, al, "name")
            }
            if (bigShown) countdown(px, (H - countHeight(countSize)) / 2, countSize, inner, Align.END)
            return
        }
        val nameL = run(countedName, 17f * t, Weight.SEMIBOLD, ink)
        val tr = countedTime(15f * t, Weight.REGULAR, sec).let { listOf(Run(it[0].text, it[0].size, it[0].weight, it[0].color, 6f * s)) + it.drop(1) }
        val l = fit(Line(listOf(nameL) + tr, rtl), inner, 11f) ?: fit(line(nameL), inner, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
        val countSize = if (bigShown) countFit(33f * t, inner) else 0f
        val ch = if (bigShown) countHeight(countSize) + 4f * s else 0f
        val ns = l.runs[0].size
        val top = (H - (ns * 1.05f + ch)) / 2
        sheet.block { sheet.text(l, px, top + ns * 0.86f, inner, al, "name") }
        if (bigShown) countdown(px, top + ns * 1.05f + 4f * s, countSize, inner, al)
    }

    /** Next, tall (2×4, 4×4): the name, its time and the place; the countdown and the bar; the prayers after it, spread. */
    fun nextTall() {
        val wide = W >= 250f
        val s = minOf(W / (if (wide) 360f else 176f), H / 372f); val t = s * k
        val p = 20f * s
        val inner = W - 2 * p
        val cp = counted
        var y = p
        sheet.block {
            val iconSz = iconSize((if (wide) 30f else 24f) * s)
            val nl = fit(line(run(countedName, (if (wide) 34f else 28f) * t, Weight.SEMIBOLD, ink)), inner - iconSz - 10f * s, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            val ns = nl.maxSize
            val base = y + ns * 0.86f
            val off = if (centred) ((inner - iconSz - ig(10f * s) - sheet.width(nl)) / 2).coerceAtLeast(0f) else 0f
            if (cp != null) icon(cp, p + off, base - ns * 0.36f - iconSz / 2, iconSz, iconColor(cp, true))
            sheet.text(nl, p + off + iconSz + ig(10f * s), base, inner - iconSz - 10f * s - off, Align.START, "name")
            y += ns * 1.05f + 4f * s
            fit(line(*countedTime((if (wide) 26f else 22f) * t, Weight.REGULAR, sec).toTypedArray()), inner, 11f)?.let { tl ->
                sheet.text(tl, p, y + tl.maxSize * 0.86f, inner, al, "time"); y += tl.maxSize * 1.05f
            }
            if (infoOn) { y += 6f * s; placeLine(p, y + 15f * t * 0.86f, inner, maxOf(11f, 15f * t), ter, al); y += 15f * t * 1.05f }
        }
        y += 16f * s
        if (bigShown) {
            val size = countFit((if (wide) 54f else 44f) * t, inner)
            countdown(p, y, size, inner, al); y += countHeight(size) + 16f * s
        }
        bar(p, y, inner, 6f * s); y += 6f * s + 12f * s
        // The prayers after it: name and time the same size, each row in an equal share of what is left.
        val ts = (if (wide) 19f else 16f) * t
        val rowH = maxOf(30f * s, ts * 1.9f)
        val later = later(4)
        val n = minOf(later.size, ((H - p - y) / rowH).toInt())
        if (n > 0) sheet.block {
            val step = (H - p - y) / n
            for ((i, pair) in later.take(n).withIndex()) {
                val (lp, lt) = pair
                val ry = y + step * i + (step - rowH) / 2
                if (i > 0) sheet.shape(p, y + step * i, inner, 1f) { cv, b -> cv.drawRect(b, android.graphics.Paint().apply { color = divider }) }
                val iconSz = iconSize((if (wide) 22f else 20f) * s)
                icon(lp, p, ry + (rowH - iconSz) / 2, iconSz, iconColor(lp, false))
                val base = ry + rowH / 2 + ts * 0.36f
                val tl = line(*time(lt, ts, Weight.SEMIBOLD, ink, suffix = wide).toTypedArray())
                val tw = sheet.width(tl)
                val room = inner - iconSz - 12f * s - tw - 8f * s
                val nl = fit(line(run(name(lp, lt.atZone(r.zone).toLocalDate()), ts, Weight.REGULAR, sec)), room, 11f)
                if (nl != null) sheet.text(nl, p + iconSz + ig(12f * s), base, room, al, "later_name")
                sheet.text(tl, p, base, inner, Align.END, "later_time")
            }
        }
    }

    /** Whether the widget has an information line to show: the place, the Hijri date or the Gregorian date. */
    private val infoOn: Boolean get() = o.placeShown != PlaceMode.OFF || o.hijri || o.gregorian

    /**
     * The information line: the place ("Madinat Al Mataar, Dubai", or the area alone as chosen), the Hijri date, the
     * Gregorian date, as the options ask, joined by " · ". The longest that fits in both languages is shown: the
     * Gregorian date goes first, then the Hijri date, then the city (never a word cut short).
     */
    fun placeLine(x: Float, baseline: Float, w: Float, size: Float, color: Int, align: Align = Align.START) {
        val today = r.today
        fun parts(arabic: Boolean): List<List<String>> {
            val (area, city) = WidgetRender.placeParts(r, arabic)
            val fmt = if (arabic == r.f.arabic) r.f else r.other
            val full = if (area != null && city != null && o.placeShown == PlaceMode.FULL) area + (if (arabic) "، " else ", ") + city else area
            val place = if (o.placeShown == PlaceMode.OFF) null else full
            val areaOnly = if (o.placeShown == PlaceMode.OFF) null else area
            val h = HijriDate.of(today, r.s.hijriAdjust)
            val hijri = if (o.hijri) "${fmt.number(h.day)} ${if (arabic) h.monthNameAr else h.monthNameEn}" else null
            val greg = if (o.gregorian) "${fmt.number(today.dayOfMonth)} ${fmt.monthShort(today.month)}" else null
            return listOf(listOfNotNull(place, hijri, greg), listOfNotNull(place, hijri), listOfNotNull(place), listOfNotNull(areaOnly), listOfNotNull(hijri)).distinct().filter { it.isNotEmpty() }
        }
        val mine = parts(r.f.arabic); val theirs = parts(!r.f.arabic)
        for ((i, a) in mine.withIndex()) {
            val text = a.joinToString("  ·  ")
            val b = theirs.getOrNull(i)?.joinToString("  ·  ")
            val l = line(run(text, size, Weight.REGULAR, color))
            val fits = sheet.width(l) <= w && (b == null || Formatter2.width(sheet, b, size, rtl) <= w)
            if (fits) { sheet.text(l, x, baseline, w, align, "place"); return }
        }
    }

    // =====================================================================================================
    // TODAY
    // =====================================================================================================

    /** Today 2×2 (176×176): six rows, the next in a capsule, the past at half strength. */
    fun today2x2(baseH: Float = 176f) {
        val s = minOf(W / 176f, H / baseH); val t = s * k
        val pv = 14f * s; val ph = 16f * s
        val inner = W - 2 * ph
        val list = shown
        val rowH = 22f * s
        val step = if (list.size > 1) (H - 2 * pv - rowH) / (list.size - 1) else 0f
        // One size for every row's name and time, so they line up: as large as the widest allows, never under the floor.
        val nameSize0 = 14.5f * t; val timeSize0 = 15f * t
        val widest = list.maxOf { p -> sheet.width(line(run(name(p, listDate), nameSize0, Weight.SEMIBOLD, ink))) + 8f + sheet.width(line(*time(at(p), timeSize0, Weight.SEMIBOLD, ink).toTypedArray())) }
        val f = minOf(1f, (inner) / widest)
        val suffix = f * timeSize0 >= 11f || true
        sheet.block {
            list.forEachIndexed { i, p ->
                val top = pv + step * i
                val on = p == marked
                if (on) sheet.shape(ph - 9f * s, top, inner + 18f * s, rowH) { cv, b -> mark(cv, b, 9f * s) }
                val nc = if (on) markText else pastColor(ink, p)
                val base = top + rowH / 2 + nameSize0 * f * 0.36f
                val tl = line(*time(at(p), maxOf(timeSize0 * f, if (on) 12f else 11f), Weight.SEMIBOLD, nc, suffix).toTypedArray())
                val nl = line(run(name(p, listDate), maxOf(nameSize0 * f, 10f), if (on) Weight.SEMIBOLD else Weight.REGULAR, nc))
                sheet.text(nl, ph, base, inner - sheet.width(tl) - 6f, Align.START, if (on) "row_name_marked" else "row_name")
                sheet.text(tl, ph, base, inner, Align.END, if (on) "row_time_marked" else "row_time")
            }
        }
    }

    /** Today 4×2 (360×176): a header (name, time, countdown); six columns of sign, name and time; the next in a capsule. */
    fun today4x2() {
        val s = minOf(W / 360f, H / 176f); val t = s * k
        val p = 16f * s
        val inner = W - 2 * p
        header(p, p, inner, 22f * t, 18f * t, 30f * t, s)
        columns(p - 6f * s, inner + 12f * s, H - p, s, t, icons = true)
    }

    /** The header of Today and the sky: the name and its time at the start, the countdown at the end, on one line. */
    fun header(x: Float, top: Float, w: Float, nameSize: Float, timeSize: Float, countSize0: Float, s: Float) {
        val cs = if (bigShown) countFit(countSize0, w * 0.55f) else 0f
        val cw = if (bigShown) countWidth(cs) + 10f * s else 0f
        val baseline = top + (if (bigShown) countAscent(cs) else nameSize * 0.86f)
        sheet.block {
            val nr = run(countedName, nameSize, Weight.SEMIBOLD, ink)
            val tr = countedTime(timeSize, Weight.REGULAR, sec).let { listOf(Run(it[0].text, it[0].size, it[0].weight, it[0].color, 7f * s)) + it.drop(1) }
            val l = fit(Line(listOf(nr) + tr, rtl), w - cw, 11f) ?: fit(line(nr), w - cw, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            sheet.text(l, x, baseline, w - cw, Align.START, "name")
        }
        if (bigShown) countdown(x, baseline - countAscent(cs), cs, w, Align.END)
    }

    /** Six (or five) columns standing on [bottom], each with its sign (when [icons]), name and time. */
    fun columns(x: Float, w: Float, bottom: Float, s: Float, t: Float, icons: Boolean, nameSize0: Float = 12.5f, timeSize0: Float = 17f, padV: Float = 8f) {
        val list = shown
        val gap = 2f * s
        val cw = (w - gap * (list.size - 1)) / list.size
        // One size for all columns: what the widest name or time allows, never under the floor.
        var ns = nameSize0 * t; var ts = timeSize0 * t
        val nf = list.minOf { p -> minOf(1f, (cw - 4f) / sheet.width(line(run(name(p, listDate), ns, Weight.SEMIBOLD, ink)))) }
        val tf = list.minOf { p -> minOf(1f, (cw - 4f) / sheet.width(line(*time(at(p), ts, Weight.SEMIBOLD, ink, suffix = false).toTypedArray()))) }
        ns = maxOf(11f, ns * nf); ts = maxOf(11f, ts * tf)
        val iconSz = iconSize(26f * s)
        val cellH = padV * s * 2 + (if (icons) iconSz + ig(4f * s) else 0f) + ns * 1.1f + 4f * s + ts * 1.05f
        val top = bottom - cellH
        sheet.mirror = o.rowOrder == RowOrder.LANGUAGE
        sheet.block {
            list.forEachIndexed { i, p ->
                val cx = x + i * (cw + gap)
                val on = p == marked
                if (on) sheet.shape(cx, top, cw, cellH) { cv, b -> mark(cv, b, 14f * s) }
                var y = top + padV * s
                val tone = if (on) markText else pastColor(sec, p)
                if (icons) { icon(p, cx + (cw - iconSz) / 2, y, iconSz, iconColor(p, on)); y += iconSz + ig(4f * s) }
                val nl = line(run(name(p, listDate), ns, if (on) Weight.SEMIBOLD else Weight.REGULAR, tone))
                sheet.text(nl, cx, y + ns * 0.86f, cw, Align.CENTER, if (on) "col_name_marked" else "col_name"); y += ns * 1.1f + 4f * s
                val tl = line(*time(at(p), if (on) maxOf(ts, 12f) else ts, Weight.SEMIBOLD, if (on) ink else pastColor(ink, p), suffix = false).toTypedArray())
                sheet.text(fit(tl, cw - 2f, 11f) ?: tl, cx, y + ts * 0.86f, cw, Align.CENTER, if (on) "col_time_marked" else "col_time")
            }
        }
        sheet.mirror = true
    }

    /** Today 4×4 (360×372) and 2×4 (176×372): header, countdown, bar, six rows with signs. */
    fun today4x4() {
        val narrow = W < 250f
        val s = minOf(W / (if (narrow) 176f else 360f), H / 372f); val t = s * k
        val p = 20f * s
        val inner = W - 2 * p
        var y = p
        sheet.block {
            val nr = run(countedName, (if (narrow) 22f else 28f) * t, Weight.SEMIBOLD, ink)
            val tr = countedTime((if (narrow) 18f else 22f) * t, Weight.REGULAR, sec).let { listOf(Run(it[0].text, it[0].size, it[0].weight, it[0].color, 9f * s)) + it.drop(1) }
            val l = fit(Line(listOf(nr) + tr, rtl), inner, 11f) ?: fit(line(nr), inner, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            sheet.text(l, p, y + l.runs[0].size * 0.86f, inner, Align.START, "name")
            y += l.runs[0].size * 1.05f
        }
        if (bigShown) {
            val size = countFit((if (narrow) 40f else 54f) * t, inner)
            y += 4f * s
            countdown(p, y, size, inner, Align.START); y += countHeight(size)
        }
        y += 12f * s
        bar(p, y, inner, 6f * s); y += 6f * s + 8f * s
        val list = shown
        val rowH = 34f * s
        val step = if (list.size > 1) (H - p - y - rowH) / (list.size - 1) else 0f
        val iconSz = iconSize(22f * s)
        sheet.block {
            val ns0 = (if (narrow) 16f else 19f) * t; val ts0 = (if (narrow) 16f else 20f) * t
            val textW = inner + 0f - iconSz - 14f * s
            val widest = list.maxOf { q -> sheet.width(line(run(name(q, listDate), ns0, Weight.SEMIBOLD, ink))) + 10f + sheet.width(line(*time(at(q), ts0, Weight.SEMIBOLD, ink).toTypedArray())) }
            val f = minOf(1f, textW / widest)
            list.forEachIndexed { i, q ->
                val top = y + step * i
                val on = q == marked
                if (on) sheet.shape(p - 12f * s, top, inner + 24f * s, rowH) { cv, b -> mark(cv, b, 12f * s) }
                val tone = if (on) gold else pastColor(sec, q)
                icon(q, p, top + (rowH - iconSz) / 2, iconSz, iconColor(q, on))
                val base = top + rowH / 2 + ns0 * f * 0.36f
                val tl = line(*time(at(q), maxOf(ts0 * f, if (on) 12f else 11f), Weight.SEMIBOLD, if (on) ink else pastColor(ink, q)).toTypedArray())
                val nl = line(run(name(q, listDate), maxOf(ns0 * f, 10f), if (on) Weight.SEMIBOLD else Weight.REGULAR, if (on) markText else pastColor(ink, q)))
                sheet.text(nl, p + iconSz + ig(14f * s), base, textW - sheet.width(tl) - 8f, Align.START, if (on) "row_name_marked" else "row_name")
                sheet.text(tl, p, base, inner, Align.END, if (on) "row_time_marked" else "row_time")
            }
        }
    }

    // =====================================================================================================
    // STRIP
    // =====================================================================================================

    /**
     * Strip 4×1 (360×84): five columns, the name over a large time, the next in a capsule. Narrow, Shuruq goes first
     * (five columns); short, the names go (the times alone, larger); tall, each column gets its sign above the name.
     */
    fun strip4x1() {
        val s = minOf(W / 360f, H / 84f); val t = s * k
        // Short strips keep less margin above and below, so the names fit over the times.
        val p = if (H < 64f) minOf(10f * s, 4f) else 10f * s
        val gap = 4f * s
        val inner = W - 2 * p
        var list = shown
        if (Prayer.SHURUQ in list && shuruqMayGo && (inner - gap * 5) / 6 < 50f) list = list - Prayer.SHURUQ
        val cw = (inner - gap * (list.size - 1)) / list.size
        val cellH = H - 2 * p
        fun widest(size: Float, names: Boolean) = list.maxOf { q -> sheet.width(if (names) line(run(name(q, listDate), size, Weight.SEMIBOLD, ink)) else line(*time(at(q), size, Weight.SEMIBOLD, ink, suffix = false).toTypedArray())) }
        // The times as large as the width allows (up to the design's 20, never under 12).
        var ts = minOf(20f * t, 20f * (cw - 6f) / widest(20f, false))
        var ns = minOf(13f * t, 13f * (cw - 4f) / widest(13f, true), ts * 0.72f)
        // The names over the times wherever the height allows them at 11 dp (bare numbers in a right-to-left row read
        // as a puzzling falling sequence): the times come down to 13 dp to make the room.
        if (ns < 11f && 11f * (cw - 4f) / widest(11f, true) >= 1f) ns = 11f
        if (ns >= 11f && ns * 1.1f + 2f * s + ts * 1.05f > cellH - 4f) ts = minOf(ts, maxOf(13f, (cellH - 4f - ns * 1.1f - 2f * s) / 1.05f))
        var namesToo = ns >= 11f && ns * 1.1f + 2f * s + ts * 1.05f <= cellH - 4f
        if (!namesToo) ts = minOf(maxOf(ts, minOf(16f * k, 16f * (cw - 6f) / widest(16f, false))), (cellH - 6f) / 1.05f)
        ts = maxOf(12f, ts)
        if (namesToo) ns = maxOf(11f, ns)
        val iconSz = iconSize(minOf(26f * s, cw * 0.5f))
        val icons = namesToo && cellH >= ns * 1.1f + 2f * s + ts * 1.05f + iconSz + ig(6f * s) + 16f
        sheet.mirror = o.rowOrder == RowOrder.LANGUAGE
        sheet.block {
            list.forEachIndexed { i, q ->
                val cx = p + i * (cw + gap)
                val on = q == marked
                if (on) sheet.shape(cx, p, cw, cellH) { cv, b -> mark(cv, b, 14f * s) }
                val blockH = (if (icons) iconSz + ig(6f * s) else 0f) + (if (namesToo) ns * 1.1f + 2f * s else 0f) + ts * 1.05f
                var y = p + (cellH - blockH) / 2
                if (icons) { icon(q, cx + (cw - iconSz) / 2, y, iconSz, iconColor(q, on)); y += iconSz + ig(6f * s) }
                if (namesToo) {
                    val nl = line(run(name(q, listDate), ns, if (on) Weight.SEMIBOLD else Weight.REGULAR, if (on) markText else pastColor(sec, q)))
                    sheet.text(nl, cx, y + ns * 0.86f, cw, Align.CENTER, if (on) "col_name_marked" else "col_name"); y += ns * 1.1f + 2f * s
                }
                val tl = line(*time(at(q), if (on) maxOf(ts, 12f) else ts, Weight.SEMIBOLD, if (on) ink else pastColor(ink, q), suffix = false).toTypedArray())
                sheet.text(tl, cx, y + ts * 0.86f, cw, Align.CENTER, if (on) "col_time_marked" else "col_time")
            }
        }
        sheet.mirror = true
    }

    // =====================================================================================================
    // RING, SKY, DATE
    // =====================================================================================================

    /** Ring 2×2: the ring from the last prayer to the next; inside, the name in gold, the countdown, the time. */
    fun ring2x2() {
        val s = minOf(W / 176f, H / 176f); val t = s * k
        val d = 150f * s; val sw = 9f * s
        val cx = W / 2; val cy = H / 2
        val rad = (d - sw) / 2
        sheet.block { sheet.shape(cx - d / 2, cy - d / 2, d, d) { cv, b -> WidgetInk.ring(cv, b.centerX(), b.centerY(), rad, sw, r.progress, if (light) 0x1A000000 else 0x2BFFFFFF, gold) } }
        val innerW = rad * 2 - sw - 16f * s
        val cs = if (bigShown) countFit(29f * t, innerW) else 0f
        val ch = if (bigShown) countHeight(cs) else 0f
        val ns = maxOf(12f, 17f * t); val ts = 14f * t
        val total = ns * 1.05f + 3f * s + ch + 3f * s + ts * 1.05f
        var y = cy - total / 2
        sheet.block {
            val nl = fit(line(run(countedName, ns, Weight.SEMIBOLD, markText)), innerW, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, markText))
            sheet.text(nl, (W - innerW) / 2, y + ns * 0.86f, innerW, Align.CENTER, "name")
            val tl = if (ts < 11f) null else fit(line(*countedTime(ts, Weight.REGULAR, sec).toTypedArray()), innerW, 11f)
            if (tl != null) sheet.text(tl, (W - innerW) / 2, y + ns * 1.05f + 3f * s + ch + 3f * s + ts * 0.86f, innerW, Align.CENTER, "time")
        }
        y += ns * 1.05f + 3f * s
        if (bigShown) countdown((W - innerW) / 2, y, cs, innerW, Align.CENTER)
    }

    /** Sky 4×2: the gradient and the Sun's arc, solid to the Sun, dotted after; sunrise and sunset at its ends. */
    fun sky4x2() {
        val s = minOf(W / 360f, H / 176f); val t = s * k
        val p = 16f * s
        header(p + 2f * s, p, W - 2 * (p + 2f * s), 22f * t, 18f * t, 30f * t, s)
        val ws = ws ?: return
        // By day the Sun's path today, solid gold as far as the Sun has come. Before sunrise today's path, after
        // sunset tomorrow's, dotted and dim, with no Sun on it.
        val todayRise = ws.day.instant(Prayer.SHURUQ); val todaySet = ws.day.instant(Prayer.MAGHRIB)
        val afterSunset = todaySet != null && r.now.isAfter(todaySet)
        val rise = if (afterSunset) at(Prayer.SHURUQ, r.today.plusDays(1)) else todayRise
        val set = if (afterSunset) at(Prayer.MAGHRIB, r.today.plusDays(1)) else todaySet
        val daytime = todayRise != null && todaySet != null && !r.now.isBefore(todayRise) && !afterSunset
        val x0 = 34f * s; val x1 = W - 34f * s; val yH = H - 40f * s; val top = 58f * s
        val u = if (daytime) ((r.now.epochSecond - todayRise!!.epochSecond).toFloat() / (todaySet!!.epochSecond - todayRise.epochSecond)).coerceIn(0f, 1f) else -1f
        val arc = if (light) r.pal.accent else 0xFFFFD36B.toInt()
        // The sky is never mirrored in Arabic (the owner, 18 November 2026): sunrise on the left, sunset on the right.
        sheet.mirror = false
        sheet.block {
            sheet.shape(0f, 0f, W, H) { cv, _ ->
                val pt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND }
                pt.color = if (light) 0x40000000 else 0x40FFFFFF; pt.strokeWidth = 1f
                cv.drawLine(14f * s, yH, W - 14f * s, yH, pt)
                fun xy(f: Float): Pair<Float, Float> { val xx = x0 + (x1 - x0) * f; return xx to (yH - (yH - top) * kotlin.math.sin(Math.PI * f).toFloat()) }
                val solid = android.graphics.Path(); val dotted = android.graphics.Path()
                for (i in 0..60) { val f = i / 60f; val (xx, yy) = xy(f); val path = if (f <= u) solid else dotted
                    if (path.isEmpty || (f > u && i > 0 && (i - 1) / 60f <= u)) path.moveTo(xx, yy) else path.lineTo(xx, yy) }
                pt.color = if (light) 0x59000000 else 0x59FFFFFF; pt.strokeWidth = 2f * s
                pt.pathEffect = android.graphics.DashPathEffect(floatArrayOf(0.1f, 5f * s), 0f)
                cv.drawPath(dotted, pt); pt.pathEffect = null
                pt.color = arc; pt.strokeWidth = 2.4f * s; if (!solid.isEmpty) cv.drawPath(solid, pt)
                if (daytime) {
                    val (sx, sy) = xy(u)
                    val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.RadialGradient(sx, sy, 40f * s, intArrayOf(0x55FFD58C, 0x22FFD58C, 0x00FFD58C), floatArrayOf(0f, 0.4f, 1f), android.graphics.Shader.TileMode.CLAMP) }
                    cv.drawCircle(sx, sy, 40f * s, glow)
                    cv.drawCircle(sx, sy, 8f * s, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = if (light) 0xFFE8A93A.toInt() else 0xFFFFF4DC.toInt() })
                }
            }
            val ts = maxOf(11f, 13f * t)
            val iconSz = 14f * s
            val base = H - 12f * s - ts * 0.2f
            sheet.icon(Prayer.SHURUQ, 14f * s, base - iconSz + 2f, iconSz, sec)
            sheet.text(line(*time(rise, ts, Weight.REGULAR, sec, suffix = false).toTypedArray()), 14f * s + iconSz + ig(5f * s), base, 80f * s, Align.START, "rise")
            sheet.icon(Prayer.MAGHRIB, W - 14f * s - iconSz, base - iconSz + 2f, iconSz, sec)
            sheet.text(line(*time(set, ts, Weight.REGULAR, sec, suffix = false).toTypedArray()), W - 14f * s - iconSz - 5f * s - 80f * s, base, 80f * s, Align.END, "set")
        }
        sheet.mirror = true
    }

    /** The date's words: the weekday, the Hijri day, month and year, the Gregorian date, the next occasion. */
    private inner class DateWords {
        val f = r.f
        val today = r.today
        val h = HijriDate.of(today, r.s.hijriAdjust)
        val day = f.number(h.day)
        val month = if (f.arabic) h.monthNameAr else h.monthNameEn
        /** The month's short form (English: "Jum I"; Arabic's names are short already). */
        val monthShort = if (f.arabic) h.monthNameAr else listOf("Muh", "Saf", "Rab I", "Rab II", "Jum I", "Jum II", "Raj", "Sha", "Ram", "Shaw", "Dhu Q", "Dhu H")[h.month - 1]
        val year = f.digits(h.year.toString()) + if (f.arabic) " هـ" else " AH"
        val weekday = f.weekday(today).let { if (f.arabic) it else it.uppercase() }
        /** "1448 AH · 4 Nov" / "٤ نوفمبر ١٤٤٨ هـ". */
        val sub = if (f.arabic) "${f.number(today.dayOfMonth)} ${f.monthName(today.month)} $year" else "$year · ${f.number(today.dayOfMonth)} ${f.monthShort(today.month)}"
        val gregorian = "${f.number(today.dayOfMonth)} ${f.monthShort(today.month)}"
        val next = WidgetRender.nextOccasion(today, r.s.hijriAdjust)
        val occasion = next?.let { e -> if (f.arabic) e.event.ar else e.event.en }
        val days = next?.let { e -> if (e.daysUntil == 0L) r.ctx.getString(R.string.widget_occasion_today)
            else r.ctx.resources.getQuantityString(R.plurals.widget_in_days, e.daysUntil.toInt(), f.number(e.daysUntil.toInt())) }
        /** "Ramadan in 96 days". */
        val occasionLine = if (occasion == null || days == null) null else if (next!!.daysUntil == 0L) "$occasion · $days"
            else r.ctx.getString(R.string.widget_occasion_in, occasion, if (f.arabic) days else days.replaceFirstChar { it.lowercase() })
    }

    /** Date 2×2: the weekday in gold capitals, the day's number large, the month, year and Gregorian date, the occasion. */
    fun date2x2() {
        val s = minOf(W / 176f, H / 176f); val t = s * k
        val p = 16f * s
        val inner = W - 2 * p
        val d = DateWords()
        sheet.block {
            var y = p
            val ws = maxOf(11f, 13f * t)
            fit(line(run(d.weekday, ws, Weight.SEMIBOLD, looks.accentOnBack)), inner, 11f)?.let { sheet.text(it, p, y + ws * 0.86f, inner, al, "weekday") }
            y += ws * 1.05f + 2f * s
            val num = line(run(d.day, 64f * t, Weight.SEMIBOLD, ink))
            sheet.text(num, p, y + 64f * t * 0.76f, inner, al, "day"); y += 64f * t * 0.92f + 2f * s
            val ms = maxOf(12f, 18f * t)
            fit(line(run(d.month, ms, Weight.SEMIBOLD, ink)), inner, 12f)?.let { sheet.text(it, p, y + ms * 0.86f, inner, al, "month") }
            y += ms * 1.05f + 2f * s
            val gs = maxOf(11f, 14f * t)
            fit(line(run(d.sub, gs, Weight.REGULAR, sec)), inner, 11f)?.let { sheet.text(it, p, y + gs * 0.86f, inner, al, "gregorian") }
            y += gs * 1.05f
            val os = maxOf(11f, 13f * t)
            // The occasion at the foot, where there is room under the rest.
            if (d.occasionLine != null && H - p - y >= os * 1.3f)
                fit(line(run(d.occasionLine, os, Weight.REGULAR, ter)), inner, 11f)?.let { sheet.text(it, p, H - p - os * 0.2f, inner, al, "occasion") }
        }
    }

    /**
     * Date 4×1 (360×84), and 4×2 the same enlarged: the day's number large at the start, the month and year over the
     * Gregorian date beside it, the occasion at the end; centred in the height.
     */
    fun date4x1() {
        val s = minOf(W / 360f, H / 96f); val t = s * k
        val px = 18f * s
        val inner = W - 2 * px
        val d = DateWords()
        sheet.block {
            val numSize = minOf(50f * t, (H - 16f) / 0.95f)
            val num = line(run(d.day, numSize, Weight.SEMIBOLD, ink))
            val numW = sheet.width(num)
            val ms = maxOf(12f, 18f * t); val gs = maxOf(11f, 14f * t)
            val occN = maxOf(11f, 12f * t); val occD = maxOf(12f, 16f * t)
            val occW = if (d.occasion != null) minOf(inner * 0.32f, maxOf(sheet.width(line(run(d.occasion, occN, Weight.REGULAR, ter))), sheet.width(line(run(d.days!!, occD, Weight.SEMIBOLD, looks.accentOnBack))))) else 0f
            val midX = px + numW + 14f * s
            val midW = inner - numW - 14f * s - (if (occW > 0f) occW + 14f * s else 0f)
            sheet.text(num, px, H / 2 + numSize * 0.36f, numW, Align.START, "day")
            val ml = fit(line(run("${d.month} ${d.year}", ms, Weight.SEMIBOLD, ink)), midW, 12f) ?: fit(line(run(d.month, ms, Weight.SEMIBOLD, ink)), midW, 12f)
            val gl = fit(line(run(d.weekday.lowercase().replaceFirstChar { it.uppercase() } + (if (r.f.arabic) "، " else ", ") + d.gregorian, gs, Weight.REGULAR, sec)), midW, 11f)
                ?: fit(line(run(d.gregorian, gs, Weight.REGULAR, sec)), midW, 11f)
            val blockH = (ml?.maxSize?.times(1.1f) ?: 0f) + (gl?.maxSize?.times(1.1f) ?: 0f)
            var y = (H - blockH) / 2
            if (ml != null) { sheet.text(ml, midX, y + ml.maxSize * 0.86f, midW, Align.START, "month"); y += ml.maxSize * 1.1f }
            if (gl != null) sheet.text(gl, midX, y + gl.maxSize * 0.86f, midW, Align.START, "gregorian")
            if (occW > 0f) {
                val nl = fit(line(run(d.occasion!!, occN, Weight.REGULAR, ter)), occW, 11f)
                val dl = fit(line(run(d.days!!, occD, Weight.SEMIBOLD, looks.accentOnBack)), occW, 12f)
                val oh = (nl?.maxSize?.times(1.1f) ?: 0f) + (dl?.maxSize?.times(1.1f) ?: 0f)
                var oy = (H - oh) / 2
                if (nl != null) { sheet.text(nl, px + inner - occW, oy + nl.maxSize * 0.86f, occW, Align.END, "occasion"); oy += nl.maxSize * 1.1f }
                if (dl != null) sheet.text(dl, px + inner - occW, oy + dl.maxSize * 0.86f, occW, Align.END, "occasion_days")
            }
        }
    }

    /** Date 2×1 (176×84) and smaller: the day's number beside the month over the year; under 60 dp, one line. */
    fun date2x1() {
        val s = minOf(W / 176f, H / 84f); val t = s * k
        val px = 16f * s
        val inner = W - 2 * px
        val d = DateWords()
        sheet.block {
            if (H < 60f) {
                // The smallest date: the day and the month at 11 dp or more, the short month only where that does not fit.
                val size = minOf(17f, (H - 10f) / 1.2f)
                val room = W - 20f
                val l = fit(line(run("${d.day} ${d.month}", size, Weight.MEDIUM, ink)), room, 11f)
                    ?: fit(line(run("${d.day} ${d.monthShort}", size, Weight.MEDIUM, ink)), room, 11f)
                    ?: line(run(d.day, 14f, Weight.MEDIUM, ink))
                sheet.text(l, 10f, H / 2 + l.maxSize * 0.36f, room, Align.START, "month")
                return@block
            }
            val numSize = minOf(40f * t, (H - 14f) / 0.95f)
            val num = line(run(d.day, numSize, Weight.SEMIBOLD, ink))
            val numW = sheet.width(num)
            val midW = inner - numW - 12f * s
            sheet.text(num, px, H / 2 + numSize * 0.36f, numW, Align.START, "day")
            val ml = fit(line(run(d.month, maxOf(12f, 17f * t), Weight.SEMIBOLD, ink)), midW, 12f)
            val gl = fit(line(run(d.sub, maxOf(11f, 13f * t), Weight.REGULAR, sec)), midW, 11f) ?: fit(line(run(d.year, maxOf(11f, 13f * t), Weight.REGULAR, sec)), midW, 11f)
            val blockH = (ml?.maxSize?.times(1.1f) ?: 0f) + (gl?.maxSize?.times(1.1f) ?: 0f)
            var y = (H - blockH) / 2
            if (ml != null) { sheet.text(ml, px + numW + 12f * s, y + ml.maxSize * 0.86f, midW, Align.START, "month"); y += ml.maxSize * 1.1f }
            if (gl != null) sheet.text(gl, px + numW + 12f * s, y + gl.maxSize * 0.86f, midW, Align.START, "gregorian")
        }
    }
}

/** Measuring text in the other language, to choose the same lines in both. */
internal object Formatter2 {
    fun width(sheet: Sheet, text: String?, size: Float, rtl: Boolean): Float =
        if (text == null) 0f else sheet.width(Line(listOf(Run(text, size, Weight.REGULAR, 0)), rtl))
}
