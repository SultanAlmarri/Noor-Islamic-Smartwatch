package com.noor.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.noor.prayer.Prayer
import kotlin.math.ceil

/** What one text on a widget is, for the tests: its role, words, size (dp), its ink and the box it was given (dp). */
internal class Drawn(val role: String, val text: String, val size: Float, val ink: RectF, val box: RectF, val color: Int, val block: Int)

/** One picture of a widget's content, placed at [rect] (dp, from the widget's left and top). */
internal class Block(val rect: RectF, val bitmap: Bitmap)

/** Where the live countdown (its picture and seconds wheel) stands, in dp, and its scale (of the wheel's 40 dp). */
internal class CountSpot(val left: Float, val top: Float, val scale: Float, val width: Float, val height: Float)

/** A widget as drawn for one size (0.24): its background picture, its blocks, the live countdown, and what it says. */
internal class Design(val wDp: Float, val hDp: Float, val back: Bitmap?, val blocks: List<Block>, val count: CountSpot?, val drawn: List<Drawn>)

/**
 * A widget's surface while it is composed (0.24), in dp. Compositions place things from the reading edge ([x] from
 * the start, so an Arabic widget is the English one mirrored, its digits and countdown never reversed); each
 * [block] becomes one picture, as small as its ink, drawn at the screen's density.
 */
internal class Sheet(val context: Context, val wDp: Float, val hDp: Float, val rtl: Boolean, val density: Float) {
    private class Op(val bounds: RectF, val draw: (Canvas) -> Unit)

    private val blocks = ArrayList<Block>()
    private val drawn = ArrayList<Drawn>()
    private var ops: ArrayList<Op>? = null
    var count: CountSpot? = null

    /** A start-relative box [x, x + w) in the widget's left-to-right coordinates. */
    fun absLeft(x: Float, w: Float): Float = if (rtl) wDp - x - w else x

    enum class Align { START, END, CENTER }

    fun block(fn: () -> Unit) {
        val mine = ArrayList<Op>()
        ops = mine
        val drawnFrom = drawn.size
        fn()
        ops = null
        if (mine.isEmpty()) return
        val u = RectF(mine[0].bounds)
        for (o in mine) u.union(o.bounds)
        // A little air round the ink (smoothing), and whole pixels.
        u.inset(-2f, -2f)
        u.left = maxOf(0f, kotlin.math.floor(u.left * density) / density); u.top = maxOf(0f, kotlin.math.floor(u.top * density) / density)
        u.right = minOf(wDp, ceil(u.right * density) / density); u.bottom = minOf(hDp, ceil(u.bottom * density) / density)
        if (u.width() <= 0f || u.height() <= 0f) return
        val bmp = Bitmap.createBitmap(maxOf(1, (u.width() * density + 0.5f).toInt()), maxOf(1, (u.height() * density + 0.5f).toInt()), Bitmap.Config.ARGB_8888)
        bmp.density = context.resources.displayMetrics.densityDpi
        val c = Canvas(bmp)
        c.scale(density, density)
        c.translate(-u.left, -u.top)
        for (o in mine) o.draw(c)
        for (i in drawnFrom until drawn.size) drawn[i] = drawn[i].let { Drawn(it.role, it.text, it.size, it.ink, it.box, it.color, blocks.size) }
        blocks += Block(u, bmp)
    }

    private fun op(bounds: RectF, draw: (Canvas) -> Unit) {
        val o = ops
        if (o != null) o += Op(bounds, draw) else block { op(bounds, draw) }
    }

    fun width(line: WidgetInk.Line) = WidgetInk.width(context, line)

    /**
     * [line] in the box [x, x + boxW) (from the start) on [baseline], aligned in it; returns its ink (dp, absolute).
     * The line must already fit ([fit]).
     */
    fun text(line: WidgetInk.Line, x: Float, baseline: Float, boxW: Float, align: Align, role: String): RectF {
        val lw = width(line)
        val inBox = when (align) { Align.START -> 0f; Align.END -> boxW - lw; Align.CENTER -> (boxW - lw) / 2 }
        val left = absLeft(x + inBox, lw)
        val ink = WidgetInk.ink(context, line).apply { offset(left, baseline) }
        val boxAbs = RectF(absLeft(x, boxW), baseline - WidgetInk.ascent(line.maxSize) * 1.25f, absLeft(x, boxW) + boxW, baseline + WidgetInk.descent(line.maxSize) * 1.2f)
        drawn += Drawn(role, line.text, line.runs.minOf { it.size }, RectF(ink), boxAbs, line.runs.first().color, -1)
        op(RectF(ink)) { c -> WidgetInk.draw(context, c, line, left, baseline) }
        return ink
    }

    fun icon(p: Prayer, x: Float, top: Float, size: Float, color: Int) {
        val left = absLeft(x, size)
        op(RectF(left, top, left + size, top + size)) { c -> WidgetInk.icon(c, p, left, top, size, color) }
    }

    /** A shape in a start-relative box; [draw] gets the absolute box. */
    fun shape(x: Float, top: Float, w: Float, h: Float, draw: (Canvas, RectF) -> Unit) {
        val r = RectF(absLeft(x, w), top, absLeft(x, w) + w, top + h)
        op(RectF(r)) { c -> draw(c, r) }
    }

    /** A picture (the countdown before Android 12) in a start-relative box. */
    fun picture(bmp: Bitmap, x: Float, top: Float, w: Float, h: Float, role: String, text: String, size: Float, color: Int) {
        val r = RectF(absLeft(x, w), top, absLeft(x, w) + w, top + h)
        drawn += Drawn(role, text, size, RectF(r), RectF(r), color, -1)
        op(RectF(r)) { c -> if (!WidgetInk.hideText) c.drawBitmap(bmp, null, r, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
    }

    /** The live countdown at a start-relative box. */
    fun countAt(x: Float, top: Float, scale: Float, w: Float, h: Float, text: String, size: Float, color: Int) {
        val left = absLeft(x, w)
        count = CountSpot(left, top, scale, w, h)
        drawn += Drawn("countdown", text, size, RectF(left, top, left + w, top + h), RectF(left, top, left + w, top + h), color, -1)
    }

    fun design(back: Bitmap?) = Design(wDp, hDp, back, blocks.toList(), count, drawn.toList())
}
