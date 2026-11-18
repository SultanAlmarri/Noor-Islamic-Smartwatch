package com.noor.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.noor.app.R
import com.noor.prayer.Prayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The widgets' ink (0.24): IBM Plex in Minhad's own weights, drawn into pictures, because a home screen draws a
 * widget's TextViews in the phone's font whatever the app packs (the owner's Galaxy showed Samsung's font beside our
 * countdown). Lines of text in runs of their own size and weight, the six prayers' signs, and the capsule, bar and
 * ring, all in dp on a canvas already scaled to the screen's density.
 */
internal object WidgetInk {
    enum class Weight(val res: Int) { REGULAR(R.font.plex_arabic_regular), MEDIUM(R.font.plex_arabic_medium), SEMIBOLD(R.font.plex_arabic_semibold) }

    private val faces = HashMap<Weight, Typeface>()

    @Synchronized fun face(context: Context, w: Weight): Typeface =
        faces.getOrPut(w) { runCatching { ResourcesCompat.getFont(context, w.res) }.getOrNull() ?: Typeface.DEFAULT }

    /** A test may draw every widget without its text, to see what is under it. */
    @Volatile internal var hideText = false

    /** One stretch of text in one size, weight and colour. [gapBefore] in dp. */
    class Run(val text: String, val size: Float, val weight: Weight, val color: Int, val gapBefore: Float = 0f, val sep: String = " ")

    /**
     * A line: runs in reading order, each drawn left to right in itself (digits and signs never reversed), the runs
     * placed right to left when [rtl] (an Arabic "3:14 م" reads with its م to the left, as the app writes it).
     */
    class Line(val runs: List<Run>, val rtl: Boolean) {
        val text: String get() = runs.joinToString("") { (if (it.gapBefore > 0f) it.sep else "") + it.text }
        val maxSize: Float get() = runs.maxOfOrNull { it.size } ?: 0f
    }

    fun paint(context: Context, r: Run): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face(context, r.weight); textSize = r.size; color = r.color
        // Figures of one width, so the times line up and a countdown does not shift as it changes.
        fontFeatureSettings = "'tnum' 1, 'lnum' 1"
        if (r.weight == Weight.SEMIBOLD) letterSpacing = -0.01f
    }

    fun width(context: Context, line: Line): Float = line.runs.sumOf { (paint(context, it).measureText(it.text) + it.gapBefore).toDouble() }.toFloat()

    /** Above the baseline: the Latin capitals' and the Arabic letters' height (Plex), for placing lines by their ink. */
    fun ascent(size: Float) = size * 0.74f
    fun descent(size: Float) = size * 0.26f

    /** Where each run starts (from the line's left edge), in visual order. */
    private fun placed(context: Context, line: Line): List<Pair<Run, Float>> {
        val widths = line.runs.map { paint(context, it).measureText(it.text) }
        val out = ArrayList<Pair<Run, Float>>()
        if (!line.rtl) {
            var x = 0f
            line.runs.forEachIndexed { i, r -> x += r.gapBefore; out += r to x; x += widths[i] }
        } else {
            val total = widths.sum() + line.runs.sumOf { it.gapBefore.toDouble() }.toFloat()
            var x = total
            line.runs.forEachIndexed { i, r -> x -= r.gapBefore; x -= widths[i]; out += r to x }
        }
        return out
    }

    /** The line's ink, relative to its left edge and baseline. */
    fun ink(context: Context, line: Line): RectF {
        val out = RectF(); var first = true
        val b = android.graphics.Rect()
        for ((run, x) in placed(context, line)) {
            if (run.text.isBlank()) continue
            val p = paint(context, run)
            p.getTextBounds(run.text, 0, run.text.length, b)
            val adv = p.measureText(run.text)
            val rr = RectF(x + minOf(0f, b.left.toFloat()), b.top.toFloat(), x + maxOf(adv, b.right.toFloat()), b.bottom.toFloat())
            if (first) { out.set(rr); first = false } else out.union(rr)
        }
        return out
    }

    fun draw(context: Context, c: Canvas, line: Line, left: Float, baseline: Float) {
        if (hideText) return
        for ((run, x) in placed(context, line)) c.drawText(run.text, left + x, baseline, paint(context, run))
    }

    // ---- Shapes ----

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    /** The next prayer's capsule: plain white at 18 % (on light: #F2F2F7, as iOS's grouped fill), or [color]. */
    fun capsule(c: Canvas, r: RectF, radius: Float, light: Boolean, accent: Int, color: Int = if (light) 0xFFF2F2F7.toInt() else 0x2EFFFFFF) {
        c.drawRoundRect(r, radius, radius, fill(color))
    }

    /** The marked prayer in an outline, 1.5 dp. */
    fun outline(c: Canvas, r: RectF, radius: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; this.color = color }
        val i = RectF(r).apply { inset(0.75f, 0.75f) }
        c.drawRoundRect(i, radius - 0.75f, radius - 0.75f, p)
    }

    /** A progress bar, [h] high, filled from the reading side. */
    fun bar(c: Canvas, r: RectF, progress: Float, rtl: Boolean, track: Int, accent: Int) {
        val rad = r.height() / 2
        c.drawRoundRect(r, rad, rad, fill(track))
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return
        val w = maxOf(r.height(), r.width() * p)
        val f = if (rtl) RectF(r.right - w, r.top, r.right, r.bottom) else RectF(r.left, r.top, r.left + w, r.bottom)
        c.drawRoundRect(f, rad, rad, fill(accent))
    }

    /** A ring from the top, clockwise, [progress] of the way round. */
    fun ring(c: Canvas, cx: Float, cy: Float, radius: Float, stroke: Float, progress: Float, track: Int, accent: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = track }
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        c.drawOval(oval, p)
        p.color = accent; p.strokeCap = Paint.Cap.ROUND
        c.drawArc(oval, -90f, 360f * progress.coerceIn(0.005f, 1f), false, p)
    }

    // ---- The prayers' signs (24-unit grid, 1.8 stroke, as the design's mock) ----

    fun icon(c: Canvas, p: Prayer, x: Float, y: Float, size: Float, color: Int) {
        if (hideText) return
        val k = size / 24f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.8f * k; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color
        }
        val half = Paint(stroke).apply { alpha = (android.graphics.Color.alpha(color) * 0.5f).toInt() }
        c.save(); c.translate(x, y); c.scale(k, k)
        stroke.strokeWidth = 1.8f; half.strokeWidth = 1.8f
        fun line(a: Float, b: Float, cc: Float, d: Float, pp: Paint = stroke) = c.drawLine(a, b, cc, d, pp)
        fun rays(cx: Float, cy: Float, r1: Float, r2: Float, n: Int, a0: Float, a1: Float) {
            for (i in 0 until n) {
                val a = ((a0 + (a1 - a0) * i / (n - 1)) * PI / 180).toFloat()
                line(cx + r1 * cos(a), cy - r1 * sin(a), cx + r2 * cos(a), cy - r2 * sin(a))
            }
        }
        fun upperHalf(cx: Float, cy: Float, r: Float) = c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false, stroke)
        when (p) {
            Prayer.FAJR -> { line(3f, 17f, 21f, 17f); upperHalf(12f, 17f, 4.5f); rays(12f, 17f, 7.2f, 9f, 3, 60f, 120f); line(5f, 21f, 19f, 21f, half) }
            Prayer.SHURUQ -> { line(3f, 18f, 21f, 18f); upperHalf(12f, 18f, 5f); line(12f, 3f, 12f, 8f)
                c.drawPath(Path().apply { moveTo(9.5f, 5.5f); lineTo(12f, 3f); lineTo(14.5f, 5.5f) }, stroke) }
            Prayer.DHUHR -> { c.drawCircle(12f, 12f, 4.2f, stroke); rays(12f, 12f, 7f, 9.3f, 8, 0f, 315f) }
            Prayer.ASR -> { c.drawCircle(12f, 13f, 4f, stroke); rays(12f, 13f, 6.6f, 8.6f, 5, 0f, 180f); line(3f, 20f, 21f, 20f, half) }
            Prayer.MAGHRIB -> { line(3f, 18f, 21f, 18f); upperHalf(12f, 18f, 5f); line(12f, 8f, 12f, 3f)
                c.drawPath(Path().apply { moveTo(9.5f, 5.5f); lineTo(12f, 8f); lineTo(14.5f, 5.5f) }, stroke) }
            Prayer.ISHA -> {
                val path = Path().apply { moveTo(19f, 14.5f); svgArc(this, 19f, 14.5f, 7.5f, true, true, 10.5f, 5f); svgArc(this, 10.5f, 5f, 6f, false, false, 19f, 14.5f); close() }
                c.drawPath(path, stroke)
                val small = Paint(stroke).apply { strokeWidth = 1.5f }
                line(17f, 4.5f, 17f, 7.5f, small); line(15.5f, 6f, 18.5f, 6f, small)
            }
        }
        c.restore()
    }

    /** An SVG elliptical (circular) arc from (x0, y0) to (x1, y1), as `A r r 0 large sweep x1 y1`. */
    private fun svgArc(path: Path, x0: Float, y0: Float, r: Float, large: Boolean, sweep: Boolean, x1: Float, y1: Float) {
        val dx = (x0 - x1) / 2; val dy = (y0 - y1) / 2
        val d2 = dx * dx + dy * dy
        val rr = maxOf(r, sqrt(d2))
        val f = sqrt(maxOf(0f, (rr * rr - d2) / d2)) * (if (large == sweep) -1 else 1)
        val cx = (x0 + x1) / 2 + f * dy
        val cy = (y0 + y1) / 2 - f * dx
        val a0 = Math.toDegrees(atan2((y0 - cy).toDouble(), (x0 - cx).toDouble())).toFloat()
        val a1 = Math.toDegrees(atan2((y1 - cy).toDouble(), (x1 - cx).toDouble())).toFloat()
        var sweepDeg = a1 - a0
        if (sweep && sweepDeg < 0) sweepDeg += 360f
        if (!sweep && sweepDeg > 0) sweepDeg -= 360f
        if (abs(sweepDeg) < 0.01f) return
        path.arcTo(RectF(cx - rr, cy - rr, cx + rr, cy + rr), a0, sweepDeg, false)
    }
}
