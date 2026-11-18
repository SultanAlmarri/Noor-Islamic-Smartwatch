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
    private val rtl = sheet.rtl
    private val W = sheet.wDp
    private val H = sheet.hDp
    private val k = r.o.textSize.scale * r.ctx.resources.configuration.fontScale.coerceIn(0.85f, 1.15f)

    // ---- Colours ----

    private val light = r.pal.light
    private val clear = r.pal.background == WidgetBackground.CLEAR
    val ink: Int = if (light) (if (r.pal.background == WidgetBackground.YOU) r.pal.text else 0xFF1C1A17.toInt()) else r.pal.text
    private fun alpha(c: Int, f: Float) = (c and 0x00FFFFFF) or (((c ushr 24) * f).toInt().coerceIn(0, 255) shl 24)
    /** Secondary, tertiary and past text: 72 %, 52 %, 50 % of the ink (stronger where 4.5:1 or 3:1 asks it). */
    val sec = alpha(ink, when { clear -> 0.9f; light -> 0.78f; else -> 0.72f })
    val ter = alpha(ink, when { clear -> 0.82f; light -> 0.66f; else -> 0.52f })
    private val pastK = when { clear -> 0.8f; light -> 0.64f; else -> 0.5f }
    val gold = r.pal.accent
    val track = if (light) 0x1A000000 else 0x33FFFFFF
    val divider = if (light) 0x1A000000 else 0x26FFFFFF

    // ---- Words ----

    private val c = r.c
    private val ws = r.ws
    /** The prayer counted to or from. */
    val counted: Prayer? = c?.event
    val countedAt: Instant? = c?.countAt

    fun name(p: Prayer, date: LocalDate = r.today) = if (r.o.jumuah) r.f.prayerName(p, date) else r.f.prayerName(p)
    val countedName: String get() = r.prayer

    fun run(text: String, size: Float, weight: Weight, color: Int, gap: Float = 0f) = Run(text, size, weight, color, gap)
    fun line(vararg runs: Run) = Line(runs.toList(), rtl)

    /** "3:14" and, in 12 hours, "PM" at 0.62 of the size in Medium. */
    fun time(at: Instant?, size: Float, weight: Weight, color: Int, suffix: Boolean = true): List<Run> {
        if (at == null) return listOf(run("—", size, weight, color))
        val z = at.atZone(r.zone)
        val (t, s) = r.f.time(z.hour * 60 + z.minute)
        return if (s == null || !suffix) listOf(run(t, size, weight, color)) else listOf(run(t, size, weight, color), run(s, size * 0.62f, Weight.MEDIUM, color, gap = size * 0.12f))
    }

    /** [l] made smaller to fit [w], never under [floor]; null when it cannot. */
    fun fit(l: Line, w: Float, floor: Float): Line? {
        val lw = sheet.width(l)
        if (lw <= w) return l
        val f = w / lw * 0.995f
        if (l.runs.maxOf { it.size } * f < floor) return null
        return Line(l.runs.map { Run(it.text, it.size * f, it.weight, it.color, it.gapBefore * f) }, l.rtl)
    }

    // ---- Times of the day ----

    /** The day whose prayers are listed: today, or tomorrow once today's last is behind (counting to tomorrow's Fajr). */
    val listDate: LocalDate get() = countedAt?.atZone(r.zone)?.toLocalDate()?.takeIf { it.isAfter(r.today) } ?: r.today
    fun at(p: Prayer, date: LocalDate = listDate): Instant? = ws?.config?.day(date)?.instant(p)
    val shown: List<Prayer> get() = Prayer.entries.filter { it != Prayer.SHURUQ || r.o.shuruq }
    /** The prayer marked in lists (the one counted to or from, if it is on the listed day). */
    val marked: Prayer? get() = counted?.takeIf { countedAt?.atZone(r.zone)?.toLocalDate() == listDate }
    fun past(p: Prayer): Boolean {
        val m = marked ?: return false
        return p.ordinal < m.ordinal
    }
    fun pastColor(color: Int, p: Prayer) = if (past(p)) alpha(color, pastK) else color

    /** The two prayers after the one counted, from today into tomorrow. */
    fun later(n: Int): List<Pair<Prayer, Instant>> {
        val from = countedAt ?: return emptyList()
        val out = ArrayList<Pair<Prayer, Instant>>()
        for (d in 0..1) for (p in shown) {
            val t = at(p, r.today.plusDays(d.toLong())) ?: continue
            if (t.isAfter(from) && out.size < n) out += p to t
        }
        return out
    }

    // ---- The countdown ----

    private val wheel = r.mode == Widgets.CountMode.WHEEL
    private val seconds get() = r.o.seconds && wheel
    private fun leading(): String {
        val c = c ?: return ""
        return if (seconds) Widgets.leading(c, r.now, r.f) else Widgets.minuteCountdown(c, r.now, r.f)
    }

    /** The countdown's width (dp) at text size [size]. */
    fun countWidth(size: Float) = CountPicture.widthDp(r.app, leading(), size / 40f, seconds) + 1f
    fun countHeight(size: Float) = CountPicture.heightDp(r.app, size / 40f)
    /** The countdown's text size that fits [w], at most [want]. */
    fun countFit(want: Float, w: Float): Float { val cw = countWidth(want); return if (cw <= w) want else want * w / cw * 0.99f }

    /** The countdown with its text's top (cap height) at [top]... placed by its picture's top, in a start-relative box. */
    fun countdown(x: Float, top: Float, size: Float, boxW: Float, align: Align) {
        val c = c ?: return
        val w = countWidth(size); val h = countHeight(size)
        val inBox = when (align) { Align.START -> 0f; Align.END -> boxW - w; Align.CENTER -> (boxW - w) / 2 }
        // The countdown reads left to right: its box is mirrored, never its digits.
        if (wheel) sheet.countAt(x + inBox, top, size / 40f, w, h, leading(), size, ink)
        else {
            val bmp = CountPicture.draw(r.app, CountPicture.Spec(leading(), size / 40f, ink, alpha(ink, 0.62f), shadow = false, colon = false))
            val d = r.density
            sheet.picture(bmp, x + inBox, top, bmp.width / d, bmp.height / d, "countdown", leading(), size, ink)
        }
    }

    /** The countdown's baseline below its picture's top (its digits stand on it). */
    fun countAscent(size: Float) = size * 0.773f

    fun bar(x: Float, top: Float, w: Float, h: Float) {
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
            val iconSz = 20f * s
            val nameL = fit(line(run(countedName, 22f * t, Weight.SEMIBOLD, ink)), inner - iconSz - 8f * s, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            val nameSize = nameL.maxSize
            val base = y + nameSize * 0.86f
            if (cp != null) sheet.icon(cp, p, base - nameSize * 0.36f - iconSz / 2, iconSz, gold)
            sheet.text(nameL, p + iconSz + 8f * s, base, inner - iconSz - 8f * s, Align.START, "name")
            y += nameSize * 1.05f + 4f * s
            fit(line(*time(countedAt, 22f * t, Weight.REGULAR, sec).toTypedArray()), inner, 11f)?.let { tl ->
                sheet.text(tl, p, y + tl.maxSize * 0.86f, inner, Align.START, "time")
            }
        }
        if (r.o.countdown) {
            val size = countFit(37f * t, inner)
            val top = H - p - countHeight(size)
            bar(p, top - 12f * s - 4f * s, inner, 4f * s)
            countdown(p, top, size, inner, Align.START)
        } else bar(p, H - p - 4f * s, inner, 4f * s)
    }

    /** Next 4×2 (360×176): the name and time large, the place, the countdown large; "Later today" with the next two. */
    fun next4x2() {
        val s = minOf(W / 360f, H / 176f); val t = s * k
        val p = 18f * s
        val inner = W - 2 * p
        val gap = 18f * s
        val rightW = (inner - gap) / 2.45f
        val leftW = inner - gap - rightW
        val cp = counted
        sheet.block {
            val iconSz = 26f * s
            val nameRun = run(countedName, 30f * t, Weight.SEMIBOLD, ink)
            val timeRuns = time(countedAt, 24f * t, Weight.REGULAR, sec).let { listOf(Run(it[0].text, it[0].size, it[0].weight, it[0].color, 10f * s)) + it.drop(1) }
            // The name and its time on one line beside the sign; the time drops before the name goes under its floor.
            val avail = leftW - iconSz - 10f * s
            val nameL = fit(Line(listOf(nameRun) + timeRuns, rtl), avail, 12f) ?: fit(line(nameRun), avail, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            val ns = nameL.runs[0].size
            val base = p + ns * 0.86f
            if (cp != null) sheet.icon(cp, p, base - ns * 0.36f - iconSz / 2, iconSz, gold)
            sheet.text(nameL, p + iconSz + 10f * s, base, avail, Align.START, "name")
            if (r.o.place) placeLine(p, base + ns * 0.22f + 6f * s + 14f * t * 0.86f, leftW, 14f * t, ter)
        }
        if (r.o.countdown) {
            val size = countFit(54f * t, leftW + gap * 0.5f)
            countdown(p, H - p - countHeight(size), size, leftW, Align.START)
        }
        // Later today, at the foot of the other column.
        val later = later(2)
        if (later.isNotEmpty()) sheet.block {
            val rowH = 33f * s
            val x0 = p + leftW + gap
            var y = H - p - rowH * later.size
            val head = fit(line(run(r.ctx.getString(R.string.widget_later_today).let { if (r.f.arabic) it else it.uppercase() }, 12f * t, Weight.SEMIBOLD, ter)), rightW, 9f)
            if (head != null) sheet.text(head, x0, y - 4f * s, rightW, Align.START, "later_head")
            for ((lp, lt) in later) {
                sheet.shape(x0, y, rightW, 1f) { cv, b -> cv.drawRect(b, android.graphics.Paint().apply { color = divider }) }
                val base = y + 7f * s + 18f * t * 0.86f
                val tl = line(*time(lt, 18f * t, Weight.SEMIBOLD, ink, suffix = false).toTypedArray())
                val tw = sheet.width(tl)
                val nl = fit(line(run(name(lp, lt.atZone(r.zone).toLocalDate()), 16f * t, Weight.REGULAR, sec)), rightW - tw - 6f * s, 10f)
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
        val iconSz = 30f * s
        val countSize = if (r.o.countdown) countFit(42f * t, inner * 0.6f) else 0f
        val countW = if (r.o.countdown) countWidth(countSize) else 0f
        val textW = inner - iconSz - 14f * s - (if (r.o.countdown) countW + 14f * s else 0f)
        sheet.block {
            val nl = fit(line(run(countedName, 24f * t, Weight.SEMIBOLD, ink)), textW, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            val tl = fit(line(*time(countedAt, 17f * t, Weight.REGULAR, sec).toTypedArray()), textW, 11f)
            val ns = nl.maxSize; val ts = tl?.maxSize ?: 0f
            val blockH = ns * 1.05f + if (tl != null) 2f * s + ts * 1.05f else 0f
            val top = (H - blockH) / 2
            if (cp != null) sheet.icon(cp, px, H / 2 - iconSz / 2, iconSz, gold)
            val x = px + iconSz + 14f * s
            sheet.text(nl, x, top + ns * 0.86f, textW, Align.START, "name")
            if (tl != null) sheet.text(tl, x, top + ns * 1.05f + 2f * s + ts * 0.86f, textW, Align.START, "time")
        }
        if (r.o.countdown) countdown(px, (H - countHeight(countSize)) / 2, countSize, inner, Align.END)
    }

    /** Next 2×1 (176×84): the name and its time on one line, the countdown under it. Under 64 dp high: one line. */
    fun next2x1() {
        val s = minOf(W / 176f, H / 84f); val t = s * k
        val px = 16f * s
        val inner = W - 2 * px
        if (H < 64f) {
            // The smallest 2×1 (a launcher's 110×40): the name at the start, the countdown to the minute at the end.
            val countSize = if (r.o.countdown) countFit(minOf(26f, (H - 10f) / 0.85f) * k, inner * 0.58f) else 0f
            val cw = if (r.o.countdown) countWidth(countSize) + 6f else 0f
            sheet.block {
                val nl = fit(line(run(countedName, minOf(17f * k, 15f), Weight.SEMIBOLD, ink)), inner - cw, 12f)
                    ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
                sheet.text(nl, px, H / 2 + nl.maxSize * 0.36f, inner - cw, Align.START, "name")
            }
            if (r.o.countdown) countdown(px, (H - countHeight(countSize)) / 2, countSize, inner, Align.END)
            return
        }
        val nameL = run(countedName, 17f * t, Weight.SEMIBOLD, ink)
        val tr = time(countedAt, 15f * t, Weight.REGULAR, sec).let { listOf(Run(it[0].text, it[0].size, it[0].weight, it[0].color, 6f * s)) + it.drop(1) }
        val l = fit(Line(listOf(nameL) + tr, rtl), inner, 11f) ?: fit(line(nameL), inner, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
        val countSize = if (r.o.countdown) countFit(33f * t, inner) else 0f
        val ch = if (r.o.countdown) countHeight(countSize) + 4f * s else 0f
        val ns = l.runs[0].size
        val top = (H - (ns * 1.05f + ch)) / 2
        sheet.block { sheet.text(l, px, top + ns * 0.86f, inner, Align.START, "name") }
        if (r.o.countdown) countdown(px, top + ns * 1.05f + 4f * s, countSize, inner, Align.START)
    }

    /** Next, tall (2×4, 4×4): the 2×2 above, with the place and the prayers after it below the countdown. */
    fun nextTall() {
        val baseW = if (W >= 250f) 360f else 176f
        val s = minOf(W / baseW, H / 372f) * (if (baseW > 300f) 1.25f else 1f); val t = s * k
        val p = 20f * s
        val inner = W - 2 * p
        val cp = counted
        var y = p
        sheet.block {
            val iconSz = 26f * s
            val nl = fit(line(run(countedName, 30f * t, Weight.SEMIBOLD, ink)), inner - iconSz - 10f * s, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            val ns = nl.maxSize
            val base = y + ns * 0.86f
            if (cp != null) sheet.icon(cp, p, base - ns * 0.36f - iconSz / 2, iconSz, gold)
            sheet.text(nl, p + iconSz + 10f * s, base, inner - iconSz - 10f * s, Align.START, "name")
            y += ns * 1.05f + 4f * s
            fit(line(*time(countedAt, 24f * t, Weight.REGULAR, sec).toTypedArray()), inner, 11f)?.let { tl ->
                sheet.text(tl, p, y + tl.maxSize * 0.86f, inner, Align.START, "time"); y += tl.maxSize * 1.05f
            }
            if (r.o.place) { y += 6f * s; placeLine(p, y + 14f * t * 0.86f, inner, 14f * t, ter); y += 14f * t * 1.05f }
        }
        y += 12f * s
        if (r.o.countdown) {
            val size = countFit((if (baseW > 300f) 54f else 40f) * t, inner)
            countdown(p, y, size, inner, Align.START); y += countHeight(size) + 14f * s
        }
        bar(p, y, inner, 4f * s); y += 4f * s + 10f * s
        val later = later(3)
        val rowH = 34f * s
        val rows = later.take(((H - p - y) / rowH).toInt().coerceAtLeast(0))
        if (rows.isNotEmpty()) sheet.block {
            var ry = H - p - rowH * rows.size
            for ((lp, lt) in rows) {
                sheet.shape(p, ry, inner, 1f) { cv, b -> cv.drawRect(b, android.graphics.Paint().apply { color = divider }) }
                val base = ry + rowH / 2 + 17f * t * 0.36f
                val iconSz = 20f * s
                sheet.icon(lp, p, ry + (rowH - iconSz) / 2, iconSz, sec)
                val tl = line(*time(lt, 18f * t, Weight.SEMIBOLD, ink).toTypedArray())
                val tw = sheet.width(tl)
                fit(line(run(name(lp, lt.atZone(r.zone).toLocalDate()), 17f * t, Weight.REGULAR, sec)), inner - iconSz - 12f * s - tw - 8f * s, 10f)?.let {
                    sheet.text(it, p + iconSz + 12f * s, base, inner - iconSz - 12f * s - tw - 8f * s, Align.START, "later_name")
                }
                sheet.text(tl, p, base, inner, Align.END, "later_time")
                ry += rowH
            }
        }
    }

    /** "Madinat Al Mataar, Dubai", or the area alone where that does not fit (the same in both languages). */
    fun placeLine(x: Float, baseline: Float, w: Float, size: Float, color: Int) {
        fun both(arabic: Boolean): Pair<String?, String?> {
            val (area, city) = WidgetRender.placeParts(r, arabic)
            return (if (area != null && city != null) area + (if (arabic) "، " else ", ") + city else area) to area
        }
        val (mine, mineArea) = both(r.f.arabic); val (theirs, theirArea) = both(!r.f.arabic)
        val other = Formatter2.width(sheet, theirs, size, rtl)
        for ((a, b) in listOf(mine to theirs, mineArea to theirArea)) {
            a ?: continue
            val l = line(run(a, size, Weight.REGULAR, color))
            val fits = sheet.width(l) <= w && (b == null || Formatter2.width(sheet, b, size, rtl) <= w)
            if (fits) { sheet.text(l, x, baseline, w, Align.START, "place"); return }
        }
        other.let { }
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
                if (on) sheet.shape(ph - 9f * s, top, inner + 18f * s, rowH) { cv, b -> WidgetInk.capsule(cv, b, 9f * s, light, gold) }
                val nc = if (on) gold else pastColor(ink, p)
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
        val cs = if (r.o.countdown) countFit(countSize0, w * 0.55f) else 0f
        val cw = if (r.o.countdown) countWidth(cs) + 10f * s else 0f
        val baseline = top + (if (r.o.countdown) countAscent(cs) else nameSize * 0.86f)
        sheet.block {
            val nr = run(countedName, nameSize, Weight.SEMIBOLD, ink)
            val tr = time(countedAt, timeSize, Weight.REGULAR, sec).let { listOf(Run(it[0].text, it[0].size, it[0].weight, it[0].color, 7f * s)) + it.drop(1) }
            val l = fit(Line(listOf(nr) + tr, rtl), w - cw, 11f) ?: fit(line(nr), w - cw, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            sheet.text(l, x, baseline, w - cw, Align.START, "name")
        }
        if (r.o.countdown) countdown(x, baseline - countAscent(cs), cs, w, Align.END)
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
        ns = maxOf(10f, ns * nf); ts = maxOf(11f, ts * tf)
        val iconSz = 22f * s
        val cellH = padV * s * 2 + (if (icons) iconSz + 4f * s else 0f) + ns * 1.1f + 4f * s + ts * 1.05f
        val top = bottom - cellH
        sheet.block {
            list.forEachIndexed { i, p ->
                val cx = x + i * (cw + gap)
                val on = p == marked
                if (on) sheet.shape(cx, top, cw, cellH) { cv, b -> WidgetInk.capsule(cv, b, 14f * s, light, gold) }
                var y = top + padV * s
                val tone = if (on) gold else pastColor(sec, p)
                if (icons) { sheet.icon(p, cx + (cw - iconSz) / 2, y, iconSz, tone); y += iconSz + 4f * s }
                val nl = line(run(name(p, listDate), ns, if (on) Weight.SEMIBOLD else Weight.REGULAR, tone))
                sheet.text(nl, cx, y + ns * 0.86f, cw, Align.CENTER, if (on) "col_name_marked" else "col_name"); y += ns * 1.1f + 4f * s
                val tl = line(*time(at(p), if (on) maxOf(ts, 12f) else ts, Weight.SEMIBOLD, if (on) ink else pastColor(ink, p), suffix = false).toTypedArray())
                sheet.text(fit(tl, cw - 2f, 11f) ?: tl, cx, y + ts * 0.86f, cw, Align.CENTER, if (on) "col_time_marked" else "col_time")
            }
        }
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
            val tr = time(countedAt, (if (narrow) 18f else 22f) * t, Weight.REGULAR, sec).let { listOf(Run(it[0].text, it[0].size, it[0].weight, it[0].color, 9f * s)) + it.drop(1) }
            val l = fit(Line(listOf(nr) + tr, rtl), inner, 11f) ?: fit(line(nr), inner, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, ink))
            sheet.text(l, p, y + l.runs[0].size * 0.86f, inner, Align.START, "name")
            y += l.runs[0].size * 1.05f
        }
        if (r.o.countdown) {
            val size = countFit((if (narrow) 40f else 54f) * t, inner)
            y += 4f * s
            countdown(p, y, size, inner, Align.START); y += countHeight(size)
        }
        y += 12f * s
        bar(p, y, inner, 4f * s); y += 4f * s + 8f * s
        val list = shown
        val rowH = 34f * s
        val step = if (list.size > 1) (H - p - y - rowH) / (list.size - 1) else 0f
        val iconSz = 22f * s
        sheet.block {
            val ns0 = (if (narrow) 16f else 19f) * t; val ts0 = (if (narrow) 16f else 20f) * t
            val textW = inner + 0f - iconSz - 14f * s
            val widest = list.maxOf { q -> sheet.width(line(run(name(q, listDate), ns0, Weight.SEMIBOLD, ink))) + 10f + sheet.width(line(*time(at(q), ts0, Weight.SEMIBOLD, ink).toTypedArray())) }
            val f = minOf(1f, textW / widest)
            list.forEachIndexed { i, q ->
                val top = y + step * i
                val on = q == marked
                if (on) sheet.shape(p - 12f * s, top, inner + 24f * s, rowH) { cv, b -> WidgetInk.capsule(cv, b, 12f * s, light, gold) }
                val tone = if (on) gold else pastColor(sec, q)
                sheet.icon(q, p, top + (rowH - iconSz) / 2, iconSz, tone)
                val base = top + rowH / 2 + ns0 * f * 0.36f
                val tl = line(*time(at(q), maxOf(ts0 * f, if (on) 12f else 11f), Weight.SEMIBOLD, if (on) ink else pastColor(ink, q)).toTypedArray())
                val nl = line(run(name(q, listDate), maxOf(ns0 * f, 10f), if (on) Weight.SEMIBOLD else Weight.REGULAR, if (on) gold else pastColor(ink, q)))
                sheet.text(nl, p + iconSz + 14f * s, base, textW - sheet.width(tl) - 8f, Align.START, if (on) "row_name_marked" else "row_name")
                sheet.text(tl, p, base, inner, Align.END, if (on) "row_time_marked" else "row_time")
            }
        }
    }

    // =====================================================================================================
    // STRIP
    // =====================================================================================================

    /** Strip 4×1 (360×84): five columns, the name over a large time, the next in a capsule. */
    fun strip4x1() {
        val s = minOf(W / 360f, H / 84f); val t = s * k
        val p = 10f * s
        val list = shown
        val gap = 4f * s
        val inner = W - 2 * p
        val cw = (inner - gap * (list.size - 1)) / list.size
        val cellH = H - 2 * p
        var ns = 13f * t; var ts = 20f * t
        // Short strips (a Samsung 4×1 is shorter than it says): the name and time come down together to fit the height.
        val need = ns * 1.1f + 2f * s + ts * 1.05f
        if (need > cellH - 6f) { val f = (cellH - 6f) / need; ns *= f; ts *= f }
        val nf = list.minOf { q -> minOf(1f, (cw - 4f) / sheet.width(line(run(name(q, listDate), ns, Weight.SEMIBOLD, ink)))) }
        val tf = list.minOf { q -> minOf(1f, (cw - 4f) / sheet.width(line(*time(at(q), ts, Weight.SEMIBOLD, ink, suffix = false).toTypedArray()))) }
        ns = maxOf(10f, ns * nf); ts = maxOf(12f, ts * tf)
        val namesToo = ns * 1.1f + 2f * s + ts * 1.05f <= cellH - 2f
        sheet.block {
            list.forEachIndexed { i, q ->
                val cx = p + i * (cw + gap)
                val on = q == marked
                if (on) sheet.shape(cx, p, cw, cellH) { cv, b -> WidgetInk.capsule(cv, b, 14f * s, light, gold) }
                val blockH = (if (namesToo) ns * 1.1f + 2f * s else 0f) + ts * 1.05f
                var y = p + (cellH - blockH) / 2
                if (namesToo) {
                    val nl = line(run(name(q, listDate), ns, if (on) Weight.SEMIBOLD else Weight.REGULAR, if (on) gold else pastColor(sec, q)))
                    sheet.text(nl, cx, y + ns * 0.86f, cw, Align.CENTER, if (on) "col_name_marked" else "col_name"); y += ns * 1.1f + 2f * s
                }
                val tl = line(*time(at(q), ts, Weight.SEMIBOLD, if (on) ink else pastColor(ink, q), suffix = false).toTypedArray())
                sheet.text(tl, cx, y + ts * 0.86f, cw, Align.CENTER, if (on) "col_time_marked" else "col_time")
            }
        }
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
        val cs = if (r.o.countdown) countFit(29f * t, innerW) else 0f
        val ch = if (r.o.countdown) countHeight(cs) else 0f
        val ns = 17f * t; val ts = 14f * t
        val total = ns * 1.05f + 3f * s + ch + 3f * s + ts * 1.05f
        var y = cy - total / 2
        sheet.block {
            val nl = fit(line(run(countedName, ns, Weight.SEMIBOLD, gold)), innerW, 12f) ?: line(run(countedName, 12f, Weight.SEMIBOLD, gold))
            sheet.text(nl, (W - innerW) / 2, y + ns * 0.86f, innerW, Align.CENTER, "name")
            val tl = fit(line(*time(countedAt, ts, Weight.REGULAR, sec).toTypedArray()), innerW, 11f)
            if (tl != null) sheet.text(tl, (W - innerW) / 2, y + ns * 1.05f + 3f * s + ch + 3f * s + ts * 0.86f, innerW, Align.CENTER, "time")
        }
        y += ns * 1.05f + 3f * s
        if (r.o.countdown) countdown((W - innerW) / 2, y, cs, innerW, Align.CENTER)
    }

    /** Sky 4×2: the gradient and the Sun's arc, solid to the Sun, dotted after; sunrise and sunset at its ends. */
    fun sky4x2() {
        val s = minOf(W / 360f, H / 176f); val t = s * k
        val p = 16f * s
        header(p + 2f * s, p, W - 2 * (p + 2f * s), 22f * t, 18f * t, 30f * t, s)
        val ws = ws ?: return
        val rise = ws.day.instant(Prayer.SHURUQ); val set = ws.day.instant(Prayer.MAGHRIB)
        val x0 = 34f * s; val x1 = W - 34f * s; val yH = H - 40f * s; val top = 58f * s
        val u = if (rise != null && set != null) ((r.now.epochSecond - rise.epochSecond).toFloat() / (set.epochSecond - rise.epochSecond)).coerceIn(-0.05f, 1.05f) else 0.5f
        sheet.block {
            sheet.shape(0f, 0f, W, H) { cv, _ ->
                val pt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND }
                pt.color = if (light) 0x40000000 else 0x40FFFFFF; pt.strokeWidth = 1f
                cv.drawLine(14f * s, yH, W - 14f * s, yH, pt)
                fun xy(f: Float): Pair<Float, Float> { val xx = x0 + (x1 - x0) * f; return (if (rtl) W - xx else xx) to (yH - (yH - top) * kotlin.math.sin(Math.PI * f).toFloat()) }
                val solid = android.graphics.Path(); val dotted = android.graphics.Path()
                val uc = u.coerceIn(0f, 1f)
                for (i in 0..60) { val f = i / 60f; val (xx, yy) = xy(f); val path = if (f <= uc) solid else dotted
                    if (path.isEmpty || (f > uc && i > 0 && (i - 1) / 60f <= uc)) path.moveTo(xx, yy) else path.lineTo(xx, yy) }
                pt.color = if (light) 0x55000000 else 0x55FFFFFF; pt.strokeWidth = 2f * s
                pt.pathEffect = android.graphics.DashPathEffect(floatArrayOf(0.1f, 5f * s), 0f)
                cv.drawPath(dotted, pt); pt.pathEffect = null
                pt.color = gold; pt.strokeWidth = 2.4f * s; cv.drawPath(solid, pt)
                if (u in 0f..1f) {
                    val (sx, sy) = xy(u)
                    val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.RadialGradient(sx, sy, 40f * s, intArrayOf(0x55FFD58C, 0x22FFD58C, 0x00FFD58C), floatArrayOf(0f, 0.4f, 1f), android.graphics.Shader.TileMode.CLAMP) }
                    cv.drawCircle(sx, sy, 40f * s, glow)
                    cv.drawCircle(sx, sy, 8f * s, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF4DC.toInt() })
                }
            }
            val ts = 13f * t
            val iconSz = 14f * s
            val base = H - 12f * s - ts * 0.2f
            sheet.icon(Prayer.SHURUQ, 14f * s, base - iconSz + 2f, iconSz, sec)
            sheet.text(line(*time(rise, ts, Weight.REGULAR, sec, suffix = false).toTypedArray()), 14f * s + iconSz + 5f * s, base, 80f * s, Align.START, "rise")
            sheet.icon(Prayer.MAGHRIB, W - 14f * s - iconSz, base - iconSz + 2f, iconSz, sec)
            sheet.text(line(*time(set, ts, Weight.REGULAR, sec, suffix = false).toTypedArray()), W - 14f * s - iconSz - 5f * s - 80f * s, base, 80f * s, Align.END, "set")
        }
    }

    /** Date 2×2: the weekday in gold capitals, the day's number large, the month, year and Gregorian date, the occasion. */
    fun date2x2() {
        val s = minOf(W / 176f, H / 176f); val t = s * k
        val p = 16f * s
        val inner = W - 2 * p
        val f = r.f
        val today = r.today
        val h = HijriDate.of(today, r.s.hijriAdjust)
        val month = if (f.arabic) h.monthNameAr else h.monthNameEn
        val wd = f.weekday(today).let { if (f.arabic) it else it.uppercase() }
        val sub = if (f.arabic) "${f.number(today.dayOfMonth)} ${f.monthName(today.month)} ${f.digits(h.year.toString())} هـ"
            else "${f.digits(h.year.toString())} AH · ${f.number(today.dayOfMonth)} ${f.monthShort(today.month)}"
        val next = WidgetRender.nextOccasion(today, r.s.hijriAdjust)
        val occ = next?.let { e -> val n = if (f.arabic) e.event.ar else e.event.en
            if (e.daysUntil == 0L) "$n · " + r.ctx.getString(R.string.widget_occasion_today)
            else r.ctx.getString(R.string.widget_occasion_in, n, r.ctx.resources.getQuantityString(R.plurals.widget_in_days, e.daysUntil.toInt(), f.number(e.daysUntil.toInt()))) }
        sheet.block {
            var y = p
            fit(line(run(wd, 13f * t, Weight.SEMIBOLD, gold)), inner, 10f)?.let { sheet.text(it, p, y + 13f * t * 0.86f, inner, Align.START, "weekday") }
            y += 13f * t * 1.05f + 2f * s
            val num = line(run(f.number(h.day), 64f * t, Weight.MEDIUM, ink))
            sheet.text(num, p, y + 64f * t * 0.76f, inner, Align.START, "day"); y += 64f * t * 0.92f + 2f * s
            fit(line(run(month, 18f * t, Weight.SEMIBOLD, ink)), inner, 12f)?.let { sheet.text(it, p, y + 18f * t * 0.86f, inner, Align.START, "month") }
            y += 18f * t * 1.05f + 2f * s
            fit(line(run(sub, 14f * t, Weight.REGULAR, sec)), inner, 11f)?.let { sheet.text(it, p, y + 14f * t * 0.86f, inner, Align.START, "gregorian") }
            if (occ != null) fit(line(run(occ, 13f * t, Weight.REGULAR, ter)), inner, 10f)?.let { sheet.text(it, p, H - p - 13f * t * 0.2f, inner, Align.START, "occasion") }
        }
    }
}

/** Measuring text in the other language, to choose the same lines in both. */
internal object Formatter2 {
    fun width(sheet: Sheet, text: String?, size: Float, rtl: Boolean): Float =
        if (text == null) 0f else sheet.width(Line(listOf(Run(text, size, Weight.REGULAR, 0)), rtl))
}
