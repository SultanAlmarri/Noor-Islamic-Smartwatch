package com.noor.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.noor.prayer.face.Ambient
import com.noor.prayer.face.Background
import com.noor.prayer.face.ColorRole
import com.noor.prayer.face.Edge
import com.noor.prayer.face.Element
import com.noor.prayer.face.FaceData
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceFont
import com.noor.prayer.face.FaceGeometry
import com.noor.prayer.face.Hands
import com.noor.prayer.face.Marks
import com.noor.prayer.face.Placement
import com.noor.prayer.face.Slot
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The typefaces a face may use; each app loads them from its own bundled fonts (the same files in both). */
class FaceFonts(val light: Typeface, val regular: Typeface, val medium: Typeface, val kufi: Typeface) {
    fun main(f: FaceFont): Typeface = when (f) { FaceFont.LIGHT -> light; FaceFont.REGULAR -> regular; FaceFont.MEDIUM -> medium; FaceFont.KUFI -> kufi }
    fun body(f: FaceFont): Typeface = when (f) { FaceFont.KUFI -> kufi; FaceFont.MEDIUM -> medium; else -> regular }
    fun strong(f: FaceFont): Typeface = when (f) { FaceFont.KUFI -> kufi; else -> medium }

    companion object {
        /** The platform's sans-serif, for tests and as a last resort. */
        val SYSTEM = FaceFonts(Typeface.create("sans-serif-light", Typeface.NORMAL), Typeface.DEFAULT, Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.DEFAULT_BOLD)
    }
}

/**
 * The moment being drawn. Mutable and reused, so a frame allocates nothing: the watch fills it from its clock, the
 * phone's preview from its own.
 */
class FaceFrame {
    var hour = 10; var minute = 9; var second = 30
    var ambient = false
    /** Pixel shift on always-on (burn-in protection), in pixels. */
    var shiftX = 0f; var shiftY = 0f

    fun set(hour: Int, minute: Int, second: Int, ambient: Boolean): FaceFrame { this.hour = hour; this.minute = minute; this.second = second; this.ambient = ambient; return this }

    companion object {
        /** The pixel shift for [minute]: a slow walk round a small square, at most [max] px from the centre. */
        fun shiftFor(minute: Int, max: Float): Pair<Float, Float> {
            val step = minute % 8
            val xs = floatArrayOf(0f, 1f, 1f, 0f, -1f, -1f, -1f, 0f); val ys = floatArrayOf(-1f, -1f, 0f, 1f, 1f, 0f, -1f, 0f)
            return xs[step] * max to ys[step] * max
        }
    }
}

/**
 * Draws a Minhad Studio face ([FaceDesign]) with its data ([FaceData]) on any Canvas: the watch's face service and the
 * phone's editor call the same code, so the preview is the face. Paints, paths and shaders are made once and reused.
 */
class FaceRenderer(context: Context, private val fonts: FaceFonts) {
    private val dunes: Bitmap? = runCatching {
        BitmapFactory.decodeResource(context.resources, R.drawable.face_dunes, BitmapFactory.Options().apply { inScaled = false })
    }.getOrNull()

    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fm = Paint.FontMetrics()
    private val oval = RectF()
    private val src = Rect()
    private val dst = RectF()
    private val hourHand = Path(); private val minuteHand = Path(); private val qiblaArrow = Path(); private val moon = Path()
    private var builtFor = -1f
    private var bgShader: Shader? = null
    private var bgFor: Background? = null
    private var bgFor1 = 0; private var bgFor2 = 0; private var bgForR = 0f; private var bgForX = 0f; private var bgForY = 0f

    // Per frame (set in draw).
    private var cx = 0f; private var cy = 0f; private var r = 1f
    private var ambient = false
    private var outline = false
    private lateinit var design: FaceDesign
    private lateinit var data: FaceData

    fun draw(canvas: Canvas, bounds: Rect, design: FaceDesign, data: FaceData, frame: FaceFrame) {
        this.design = design; this.data = data
        cx = bounds.exactCenterX(); cy = bounds.exactCenterY(); r = min(bounds.width(), bounds.height()) / 2f
        ambient = frame.ambient
        outline = ambient && (design.ambientOutline || design.ambient == Ambient.MINIMAL)
        if (builtFor != r) buildPaths()

        canvas.drawColor(Color.BLACK)
        if (!ambient) background(canvas, bounds)
        canvas.save()
        if (ambient && design.pixelShift) canvas.translate(frame.shiftX, frame.shiftY)
        if (!ambient || design.ambient == Ambient.DIMMED) edge(canvas)
        if (!ambient || design.ambient == Ambient.DIMMED) marks(canvas)
        var hasTime = false
        for (s in SLOTS) {
            val p = design.at(s) ?: continue
            if (p.element == Element.TIME) hasTime = true
            if (ambient && !keptOnAmbient(s, p)) continue
            element(canvas, s, p, frame)
        }
        // Always-on keeps the time, even when the design has none of its own.
        if (ambient && design.hands == Hands.NONE && !hasTime) {
            element(canvas, Slot.CENTER, AMBIENT_TIME, frame)
        }
        if (design.hands != Hands.NONE) hands(canvas, frame)
        canvas.restore()
    }

    /** What stays on always-on, per the design's choice. */
    fun keptOnAmbient(s: Slot, p: Placement): Boolean = when (design.ambient) {
        Ambient.DIMMED -> true
        Ambient.MINIMAL -> p.element == Element.TIME || p.element == Element.NEXT_PRAYER
        Ambient.TIME_ONLY -> p.element == Element.TIME
        Ambient.CUSTOM -> p.element == Element.TIME || s in design.ambientSlots
    }

    // ---- colours ----

    private fun colorOf(p: Placement): Int = when (p.color) {
        ColorRole.ACCENT -> design.theme.accent
        ColorRole.TEXT -> design.theme.text
        ColorRole.SOFT -> design.theme.soft
        ColorRole.CUSTOM -> p.custom ?: design.theme.accent
    }

    /** Always-on draws darker (fewer lit pixels on an OLED screen, and calmer at night). */
    private fun lit(c: Int, alpha: Float = 1f): Int {
        val k = if (ambient) 0.72f else 1f
        val a = (Color.alpha(c) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, (Color.red(c) * k).toInt(), (Color.green(c) * k).toInt(), (Color.blue(c) * k).toInt())
    }

    private fun textPaint(tf: Typeface, color: Int): Paint {
        text.typeface = tf; text.color = lit(color)
        text.style = if (outline) Paint.Style.STROKE else Paint.Style.FILL
        text.strokeWidth = r * 0.006f
        text.textAlign = Paint.Align.CENTER
        return text
    }

    /** Sets the size so [s] fits [maxW]; returns its width. */
    private fun fit(s: String, size: Float, maxW: Float): Float {
        text.textSize = size
        val w = text.measureText(s)
        if (w > maxW && w > 0f) { text.textSize = size * maxW / w; return maxW }
        return w
    }

    /** Draws [s] centred on ([x], [y]), with the current paint. */
    private fun centred(canvas: Canvas, s: String, x: Float, y: Float) {
        text.getFontMetrics(fm)
        canvas.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, text)
    }

    // ---- background, edge, marks, hands ----

    private fun background(canvas: Canvas, bounds: Rect) {
        val t = design.theme
        when (t.background) {
            Background.BLACK -> Unit
            Background.SOLID -> { fill.shader = null; fill.color = t.bg1; canvas.drawCircle(cx, cy, r, fill) }
            Background.GRADIENT, Background.DUNES -> {
                if (bgFor != t.background || bgFor1 != t.bg1 || bgFor2 != t.bg2 || bgForR != r || bgForX != cx || bgForY != cy) {
                    bgFor = t.background; bgFor1 = t.bg1; bgFor2 = t.bg2; bgForR = r; bgForX = cx; bgForY = cy
                    bgShader = if (t.background == Background.DUNES)
                        RadialGradient(cx, cy - r * 0.57f, r * 1.78f, intArrayOf(0xFF16294A.toInt(), 0xFF0B1526.toInt(), 0xFF03060C.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    else RadialGradient(cx, cy - r * 0.55f, r * 1.7f, intArrayOf(t.bg1, t.bg2), null, Shader.TileMode.CLAMP)
                }
                fill.shader = bgShader; fill.color = Color.BLACK
                canvas.drawCircle(cx, cy, r, fill)
                fill.shader = null
                if (t.background == Background.DUNES && dunes != null) {
                    src.set(0, 0, dunes.width, dunes.height)
                    dst.set(cx - r, cy + r * (318f / 225f - 1f), cx + r, cy + r)
                    canvas.drawBitmap(dunes, src, dst, bmp)
                }
            }
        }
    }

    private fun edge(canvas: Canvas) {
        val w = r * 0.028f
        val rr = r - w * 1.1f
        oval.set(cx - rr, cy - rr, cx + rr, cy + rr)
        stroke.strokeWidth = w; stroke.shader = null
        when (design.edge) {
            Edge.NONE -> Unit
            Edge.PRAYER_RING -> {
                val next = data.next
                stroke.color = lit(design.theme.accent, if (ambient) 0.25f else 0.18f)
                if (ambient) stroke.strokeWidth = w * 0.35f
                canvas.drawCircle(cx, cy, rr, stroke)
                if (next != null && !ambient) {
                    stroke.color = design.theme.accent; stroke.strokeWidth = w
                    canvas.drawArc(oval, -90f, (next.progress * 360f).coerceAtLeast(0.5f), false, stroke)
                }
                if (next != null) dot(canvas, -90f + next.progress * 360f, rr, w * 0.75f, design.theme.text)
            }
            Edge.DAY_DIAL -> {
                val sun = data.sun
                stroke.color = lit(design.theme.soft, 0.16f); stroke.strokeWidth = w * 0.45f
                canvas.drawCircle(cx, cy, rr, stroke)
                if (sun == null) return
                // Daylight, Shuruq to Maghrib, as a lighter band (midnight at the bottom, noon at the top).
                val rise = sun.sunrise
                val set = sun.sunset
                if (rise != null && set != null && !ambient) {
                    stroke.color = lit(design.theme.accent, 0.6f); stroke.strokeWidth = w
                    canvas.drawArc(oval, 90f + rise * 360f, (set - rise) * 360f, false, stroke)
                }
                for (i in sun.marks.indices) {
                    val p = sun.marks[i].first; val f = sun.marks[i].second
                    val a = Math.toRadians((90f + f * 360f).toDouble())
                    val isNext = p == sun.nextPrayer
                    stroke.color = lit(if (isNext) design.theme.accent else design.theme.text, if (isNext) 1f else 0.75f)
                    stroke.strokeWidth = if (isNext) w * 0.55f else w * 0.3f
                    val r0 = rr - w * (if (isNext) 1.7f else 1.2f); val r1 = rr + w * 0.5f
                    canvas.drawLine(cx + r0 * cos(a).toFloat(), cy + r0 * sin(a).toFloat(), cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(), stroke)
                }
                dot(canvas, 90f + sun.now * 360f, rr, w * 0.95f, if (sun.dayFraction != null) design.theme.accent else design.theme.soft)
            }
        }
    }

    private fun dot(canvas: Canvas, deg: Float, radius: Float, size: Float, color: Int) {
        val a = Math.toRadians(deg.toDouble())
        fill.shader = null; fill.color = lit(color)
        canvas.drawCircle(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat(), size, fill)
    }

    /** How far in from the edge the dial's marks start: inside the ring when there is one. */
    private val markOuter: Float get() = if (design.edge == Edge.NONE) r * 0.95f else r * 0.89f

    private fun marks(canvas: Canvas) {
        val outer = markOuter
        when (design.marks) {
            Marks.NONE -> Unit
            Marks.DOTS -> for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90.0)
                fill.shader = null; fill.color = lit(if (i % 3 == 0) design.theme.accent else design.theme.soft, if (i % 3 == 0) 1f else 0.7f)
                canvas.drawCircle(cx + (outer - r * 0.02f) * cos(a).toFloat(), cy + (outer - r * 0.02f) * sin(a).toFloat(), r * (if (i % 3 == 0) 0.02f else 0.012f), fill)
            }
            Marks.MINUTES -> for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90.0)
                val hour = i % 5 == 0
                stroke.color = lit(if (hour) design.theme.text else design.theme.soft, if (hour) 0.95f else 0.45f)
                stroke.strokeWidth = r * (if (hour) 0.016f else 0.007f)
                val inner = outer - r * (if (hour) 0.075f else 0.035f)
                canvas.drawLine(cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat(), cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat(), stroke)
            }
            Marks.NUMERALS -> {
                val nr = outer - r * 0.08f
                textPaint(fonts.main(design.theme.font), design.theme.text).textSize = r * 0.11f
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0 - 60.0)
                    text.color = lit(if ((i + 1) % 3 == 0) design.theme.accent else design.theme.text)
                    centred(canvas, data.text.numerals[i], cx + nr * cos(a).toFloat(), cy + nr * sin(a).toFloat())
                }
            }
        }
    }

    private fun buildPaths() {
        builtFor = r
        hourHand.reset()
        hourHand.moveTo(0f, r * 0.08f); hourHand.lineTo(-r * 0.028f, 0f); hourHand.lineTo(-r * 0.016f, -r * 0.48f)
        hourHand.lineTo(0f, -r * 0.52f); hourHand.lineTo(r * 0.016f, -r * 0.48f); hourHand.lineTo(r * 0.028f, 0f); hourHand.close()
        minuteHand.reset()
        minuteHand.moveTo(0f, r * 0.1f); minuteHand.lineTo(-r * 0.02f, 0f); minuteHand.lineTo(-r * 0.01f, -r * 0.76f)
        minuteHand.lineTo(0f, -r * 0.8f); minuteHand.lineTo(r * 0.01f, -r * 0.76f); minuteHand.lineTo(r * 0.02f, 0f); minuteHand.close()
        qiblaArrow.reset()
        qiblaArrow.moveTo(0f, -1f); qiblaArrow.lineTo(0.55f, 0.75f); qiblaArrow.lineTo(0f, 0.4f); qiblaArrow.lineTo(-0.55f, 0.75f); qiblaArrow.close()
        moon.reset()
        moon.addCircle(0f, 0f, 1f, Path.Direction.CW)
        val cut = Path().apply { addCircle(0.45f, -0.25f, 0.85f, Path.Direction.CW) }
        moon.op(cut, Path.Op.DIFFERENCE)
    }

    private fun hands(canvas: Canvas, f: FaceFrame) {
        val hourDeg = (f.hour % 12) * 30f + f.minute * 0.5f
        val minDeg = f.minute * 6f + f.second * 0.1f
        val handColor = design.theme.text
        when (design.hands) {
            Hands.NONE -> return
            Hands.CLASSIC -> {
                fill.shader = null
                fill.style = if (ambient) Paint.Style.STROKE else Paint.Style.FILL; fill.strokeWidth = r * 0.008f
                fill.color = lit(handColor)
                canvas.save(); canvas.rotate(hourDeg, cx, cy); canvas.translate(cx, cy); canvas.drawPath(hourHand, fill); canvas.restore()
                canvas.save(); canvas.rotate(minDeg, cx, cy); canvas.translate(cx, cy); canvas.drawPath(minuteHand, fill); canvas.restore()
                fill.style = Paint.Style.FILL
            }
            Hands.SLIM, Hands.BOLD -> {
                val bold = design.hands == Hands.BOLD
                stroke.shader = null; stroke.color = lit(handColor)
                stroke.strokeWidth = r * (if (bold) 0.05f else 0.018f) * (if (ambient && bold) 0.5f else 1f)
                line(canvas, hourDeg, -r * 0.06f, r * 0.5f)
                stroke.strokeWidth = r * (if (bold) 0.036f else 0.012f) * (if (ambient && bold) 0.5f else 1f)
                line(canvas, minDeg, -r * 0.06f, r * 0.78f)
                if (bold && !ambient) {
                    stroke.color = design.theme.accent; stroke.strokeWidth = r * 0.012f
                    line(canvas, hourDeg, r * 0.12f, r * 0.46f); line(canvas, minDeg, r * 0.12f, r * 0.74f)
                }
            }
        }
        if (design.seconds && !ambient) {
            stroke.color = design.theme.accent; stroke.strokeWidth = r * 0.008f
            line(canvas, f.second * 6f, -r * 0.16f, r * 0.86f)
        }
        fill.shader = null; fill.style = Paint.Style.FILL
        fill.color = lit(if (design.seconds && !ambient) design.theme.accent else handColor)
        canvas.drawCircle(cx, cy, r * 0.032f, fill)
        fill.color = Color.BLACK
        canvas.drawCircle(cx, cy, r * 0.012f, fill)
    }

    private fun line(canvas: Canvas, deg: Float, from: Float, to: Float) {
        val a = Math.toRadians(deg - 90.0)
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        canvas.drawLine(cx + from * c, cy + from * s, cx + to * c, cy + to * s, stroke)
    }

    // ---- elements ----

    private fun element(canvas: Canvas, slot: Slot, p: Placement, f: FaceFrame) {
        val b = FaceGeometry.box(slot, design)
        val x = cx + b.cx * r; val y = cy + b.cy * r
        val w = b.hw * 2f * r; val h = b.hh * 2f * r
        val k = p.size.scale
        val compact = slot.small || (slot == Slot.CENTER && b.hw < 0.5f)
        val color = colorOf(p)
        val rtl = data.text.arabic
        val next = data.next
        when (p.element) {
            Element.NONE -> Unit
            Element.TIME -> time(canvas, x, y, w, h, k, color, compact, slot, f)
            Element.DATE -> single(canvas, data.date, x, y, w, h, k, color)
            Element.WEEKDAY -> single(canvas, data.weekday, x, y, w, h, k, color)
            Element.HIJRI -> single(canvas, data.hijri, x, y, w, h, k, color)
            Element.LABEL -> single(canvas, p.text.ifEmpty { data.appName }, x, y, w, h, k, color, fonts.strong(design.theme.font))
            Element.NEXT_PRAYER -> when {
                next == null -> single(canvas, data.setupHint, x, y, w, h, k * 0.8f, design.theme.soft)
                compact -> twoLines(canvas, next.name, color, next.time, design.theme.text, x, y, w, h, k)
                slot == Slot.CENTER -> twoLines(canvas, next.name, color, next.detail, design.theme.soft, x, y, w, h, k)
                else -> pair(canvas, next.name, color, next.time, design.theme.text, x, y, w, h, k, rtl)
            }
            Element.COUNTDOWN -> when {
                next == null -> single(canvas, data.setupHint, x, y, w, h, k * 0.8f, design.theme.soft)
                compact -> twoLines(canvas, next.name, design.theme.soft, next.countdownShort, color, x, y, w, h, k)
                else -> single(canvas, next.countdown, x, y, w, h, k, color)
            }
            Element.PRAYER_TIMES -> times(canvas, slot, b, x, y, w, h, k, color, compact)
            Element.PRAYER_GAUGE -> gauge(canvas, x, y, w, h, k, color, compact, rtl)
            Element.SUN_ARC -> sunArc(canvas, x, y, w, h, k, color)
            Element.QIBLA -> qibla(canvas, x, y, w, h, k, color, compact)
            Element.BATTERY -> battery(canvas, x, y, w, h, k, color, compact)
        }
    }

    private fun single(canvas: Canvas, s: String, x: Float, y: Float, w: Float, h: Float, k: Float, color: Int, tf: Typeface = fonts.body(design.theme.font)) {
        textPaint(tf, color)
        fit(s, h * 0.62f * k, w)
        centred(canvas, s, x, y)
    }

    /** Two pieces on one line ("Asr  3:14 PM"), the first leading in the reading direction. */
    private fun pair(canvas: Canvas, a: String, ca: Int, b: String, cb: Int, x: Float, y: Float, w: Float, h: Float, k: Float, rtl: Boolean) {
        val size = h * 0.6f * k
        textPaint(fonts.strong(design.theme.font), ca); text.textSize = size
        val wa = text.measureText(a)
        text.typeface = fonts.body(design.theme.font)
        val wb = text.measureText(b)
        val gap = size * 0.45f
        val total = wa + gap + wb
        val s = if (total > w) w / total else 1f
        val left = x - total * s / 2f
        val xa = if (rtl) left + (wb + gap) * s + wa * s / 2f else left + wa * s / 2f
        val xb = if (rtl) left + wb * s / 2f else left + (wa + gap) * s + wb * s / 2f
        text.textSize = size * s; text.typeface = fonts.strong(design.theme.font); text.color = lit(ca)
        centred(canvas, a, xa, y)
        text.typeface = fonts.body(design.theme.font); text.color = lit(cb)
        centred(canvas, b, xb, y)
    }

    private fun twoLines(canvas: Canvas, a: String, ca: Int, b: String, cb: Int, x: Float, y: Float, w: Float, h: Float, k: Float) {
        textPaint(fonts.strong(design.theme.font), ca)
        fit(a, h * 0.36f * k, w)
        centred(canvas, a, x, y - h * 0.2f)
        textPaint(fonts.body(design.theme.font), cb)
        fit(b, h * 0.3f * k, w)
        centred(canvas, b, x, y + h * 0.22f)
    }

    private fun time(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, k: Float, color: Int, compact: Boolean, slot: Slot, f: FaceFrame) {
        val main = data.time
        val showSec = design.seconds && !ambient && design.hands == Hands.NONE
        val small = data.ampm
        textPaint(fonts.main(design.theme.font), color)
        val size = h * (if (slot == Slot.CENTER) 0.98f else 0.8f) * k
        text.textSize = size
        val wMain = text.measureText(main)
        val sizeSmall = size * 0.3f
        text.textSize = sizeSmall; text.typeface = fonts.body(design.theme.font)
        val wSec = if (showSec) text.measureText(data.text.twoDigits(f.second)) else 0f
        val wAm = if (small != null) text.measureText(small) else 0f
        val wSide = maxOf(wSec, wAm)
        val gap = if (wSide > 0f) size * 0.08f else 0f
        val total = wMain + gap + wSide
        val s = if (total > w) w / total else 1f
        val left = x - total * s / 2f
        val rtl = data.text.arabic
        // Arabic puts the "ص/م" before the time (its left); the seconds always follow the minutes.
        val sideLeft = rtl && !showSec
        val mainX = if (sideLeft) left + (wSide + gap) * s + wMain * s / 2f else left + wMain * s / 2f
        val sideX = if (sideLeft) left + wSide * s / 2f else left + (wMain + gap) * s + wSide * s / 2f
        text.typeface = fonts.main(design.theme.font); text.textSize = size * s; text.color = lit(color)
        text.getFontMetrics(fm)
        val baseline = y - (fm.ascent + fm.descent) / 2f
        canvas.drawText(main, mainX, baseline, text)
        if (wSide > 0f) {
            text.typeface = fonts.body(design.theme.font); text.textSize = sizeSmall * s
            if (showSec && small != null) {
                text.color = lit(design.theme.soft); canvas.drawText(small, sideX, baseline - size * s * 0.42f, text)
                text.color = lit(design.theme.accent); canvas.drawText(data.text.twoDigits(f.second), sideX, baseline, text)
            } else if (showSec) {
                text.color = lit(design.theme.accent); canvas.drawText(data.text.twoDigits(f.second), sideX, baseline, text)
            } else if (small != null) {
                text.color = lit(design.theme.soft); canvas.drawText(small, sideX, baseline, text)
            }
        }
    }

    private fun times(canvas: Canvas, slot: Slot, b: FaceGeometry.Box, x: Float, y: Float, w: Float, h0: Float, k: Float, color: Int, compact: Boolean) {
        val lines = data.prayers
        if (lines.isEmpty()) { single(canvas, data.setupHint, x, y, w, h0, k * 0.8f, design.theme.soft); return }
        val rtl = data.text.arabic
        if (slot == Slot.CENTER && !compact) {
            // The whole day: one row per time; it takes the space above and below when those are free.
            val tall = design.at(Slot.TOP) == null && design.at(Slot.BOTTOM) == null
            val h = if (tall) r * 0.92f else h0
            val rowH = h / lines.size
            val nameX = if (rtl) x + w * 0.36f else x - w * 0.36f
            val timeX = if (rtl) x - w * 0.36f else x + w * 0.36f
            for (i in lines.indices) {
                val l = lines[i]
                val ry = y - h / 2f + rowH * (i + 0.5f)
                val hl = l.isNext
                if (hl && !ambient) {
                    fill.shader = null; fill.color = lit(design.theme.accent, 0.16f)
                    oval.set(x - w * 0.48f, ry - rowH * 0.46f, x + w * 0.48f, ry + rowH * 0.46f)
                    canvas.drawRoundRect(oval, rowH * 0.46f, rowH * 0.46f, fill)
                }
                textPaint(if (hl) fonts.strong(design.theme.font) else fonts.body(design.theme.font), if (hl) design.theme.accent else design.theme.text)
                text.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
                text.textSize = rowH * 0.56f * k.coerceAtMost(1.1f)
                text.getFontMetrics(fm)
                val base = ry - (fm.ascent + fm.descent) / 2f
                if (!hl) text.color = lit(color)
                canvas.drawText(l.name, nameX, base, text)
                text.textAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
                if (!hl) text.color = lit(design.theme.text)
                canvas.drawText(l.time, timeX, base, text)
                text.textAlign = Paint.Align.CENTER
            }
            return
        }
        // Elsewhere: the next one, two or three, side by side (in reading order).
        val count = when { compact -> 1; slot == Slot.UPPER || slot == Slot.LOWER -> 2; else -> 3 }
        val start = lines.indexOfFirst { it.isNext }.let { if (it < 0) 0 else it }
        val colW = w / count
        for (i in 0 until count) {
            val l = lines[(start + i) % lines.size]
            val col = if (rtl) count - 1 - i else i
            val lx = x - w / 2f + colW * (col + 0.5f)
            textPaint(fonts.body(design.theme.font), if (i == 0) design.theme.accent else color)
            fit(l.name, h0 * 0.34f * k, colW * 0.94f)
            centred(canvas, l.name, lx, y - h0 * 0.2f)
            textPaint(fonts.strong(design.theme.font), if (i == 0) design.theme.text else design.theme.soft)
            fit(l.time, h0 * 0.36f * k, colW * 0.94f)
            centred(canvas, l.time, lx, y + h0 * 0.22f)
        }
    }

    private fun gauge(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, k: Float, color: Int, compact: Boolean, rtl: Boolean) {
        val next = data.next
        if (compact) {
            val rr = min(w, h) / 2f * 0.86f * k.coerceAtMost(1.1f)
            oval.set(x - rr, y - rr, x + rr, y + rr)
            stroke.shader = null; stroke.strokeWidth = rr * 0.12f
            stroke.color = lit(color, 0.22f); canvas.drawCircle(x, y, rr, stroke)
            if (next != null && !ambient) { stroke.color = lit(color); canvas.drawArc(oval, -90f, (next.progress * 360f).coerceAtLeast(1f), false, stroke) }
            if (next == null) return
            textPaint(fonts.strong(design.theme.font), design.theme.text)
            fit(next.name, rr * 0.5f, rr * 1.5f); centred(canvas, next.name, x, y - rr * 0.2f)
            textPaint(fonts.body(design.theme.font), design.theme.soft)
            fit(next.countdownShort, rr * 0.42f, rr * 1.45f); centred(canvas, next.countdownShort, x, y + rr * 0.3f)
            return
        }
        if (next == null) { single(canvas, data.setupHint, x, y, w, h, k * 0.8f, design.theme.soft); return }
        // A bar from the last time to the next, with both names at its ends.
        val barY = y + h * 0.18f
        val half = w * 0.42f
        stroke.shader = null; stroke.strokeWidth = h * 0.12f * k
        stroke.color = lit(color, 0.22f); canvas.drawLine(x - half, barY, x + half, barY, stroke)
        val p = if (ambient) 0f else next.progress
        stroke.color = lit(color)
        if (p > 0f) { if (rtl) canvas.drawLine(x + half, barY, x + half - 2 * half * p, barY, stroke) else canvas.drawLine(x - half, barY, x - half + 2 * half * p, barY, stroke) }
        textPaint(fonts.body(design.theme.font), design.theme.soft)
        val cur = next.currentName ?: ""
        text.textSize = h * 0.32f * k
        text.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        text.getFontMetrics(fm)
        val base = y - h * 0.2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(cur, if (rtl) x + half else x - half, base, text)
        text.textAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
        text.color = lit(design.theme.text); text.typeface = fonts.strong(design.theme.font)
        canvas.drawText(next.nameAndShort, if (rtl) x - half else x + half, base, text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun sunArc(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, k: Float, color: Int) {
        val sun = data.sun
        val half = min(w * 0.42f, h * 1.1f) * k.coerceAtMost(1.15f)
        val horizon = y + h * 0.36f
        val rise = sun?.sunriseTime
        val set = sun?.sunsetTime
        // The Sun's path: half an ellipse from sunrise (left) to sunset (right), above the horizon line.
        val arcH = h * 0.78f
        oval.set(x - half, horizon - arcH, x + half, horizon + arcH)
        stroke.shader = null; stroke.strokeWidth = r * 0.008f
        stroke.color = lit(design.theme.soft, 0.4f)
        canvas.drawLine(x - half * 1.15f, horizon, x + half * 1.15f, horizon, stroke)
        stroke.color = lit(color, 0.45f)
        canvas.drawArc(oval, 180f, 180f, false, stroke)
        if (sun?.dayFraction != null) {
            val a = Math.toRadians(180.0 + sun.dayFraction!! * 180.0)
            fill.shader = null; fill.color = lit(color)
            canvas.drawCircle(x + half * cos(a).toFloat(), horizon + arcH * sin(a).toFloat(), r * 0.032f, fill)
        } else if (sun?.nightFraction != null) {
            val a = Math.toRadians(180.0 + sun.nightFraction!! * 180.0)
            fill.shader = null; fill.color = lit(design.theme.text, 0.9f)
            canvas.save(); canvas.translate(x + half * cos(a).toFloat(), horizon + arcH * sin(a).toFloat()); canvas.scale(r * 0.03f, r * 0.03f)
            canvas.drawPath(moon, fill); canvas.restore()
        }
        if (rise != null && set != null && !ambient) {
            textPaint(fonts.body(design.theme.font), design.theme.soft)
            text.textSize = h * 0.24f * k.coerceAtMost(1.1f)
            text.getFontMetrics(fm)
            val base = horizon + h * 0.08f - fm.ascent
            canvas.drawText(rise, x - half * 0.75f, base, text)
            canvas.drawText(set, x + half * 0.75f, base, text)
        }
    }

    private fun qibla(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, k: Float, color: Int, compact: Boolean) {
        val deg = data.qiblaDeg
        if (deg == null) { single(canvas, data.text.let { if (it.arabic) "القبلة" else "Qibla" }, x, y, w, h, k, color); return }
        if (compact) {
            val a = min(w, h) * 0.2f * k
            fill.shader = null; fill.color = lit(color)
            canvas.save(); canvas.translate(x, y - h * 0.16f); canvas.rotate(deg); canvas.scale(a, a); canvas.drawPath(qiblaArrow, fill); canvas.restore()
            stroke.shader = null; stroke.strokeWidth = r * 0.006f; stroke.color = lit(design.theme.soft, 0.45f)
            canvas.drawCircle(x, y - h * 0.16f, a * 1.35f, stroke)
            textPaint(fonts.body(design.theme.font), design.theme.text)
            val s = data.text.qiblaShort(deg)
            fit(s, h * 0.24f * k, w * 0.9f); centred(canvas, s, x, y + h * 0.3f)
            return
        }
        val s = data.qibla ?: return
        textPaint(fonts.body(design.theme.font), color)
        val tw = fit(s, h * 0.58f * k, w * 0.82f)
        val a = text.textSize * 0.42f
        val rtl = data.text.arabic
        val ax = if (rtl) x + tw / 2f + a * 1.6f else x - tw / 2f - a * 1.6f
        centred(canvas, s, x + (if (rtl) -a * 0.8f else a * 0.8f), y)
        fill.shader = null; fill.color = lit(design.theme.accent)
        canvas.save(); canvas.translate(ax + (if (rtl) -a * 0.8f else a * 0.8f), y); canvas.rotate(deg); canvas.scale(a, a); canvas.drawPath(qiblaArrow, fill); canvas.restore()
    }

    private fun battery(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, k: Float, color: Int, compact: Boolean) {
        val level = data.batteryLevel
        val s = data.battery ?: "—"
        if (compact) {
            val rr = min(w, h) / 2f * 0.86f * k.coerceAtMost(1.1f)
            oval.set(x - rr, y - rr, x + rr, y + rr)
            stroke.shader = null; stroke.strokeWidth = rr * 0.12f
            stroke.color = lit(color, 0.22f); canvas.drawCircle(x, y, rr, stroke)
            if (level != null && !ambient) { stroke.color = lit(if (level < 0.15f) 0xFFFF6B6B.toInt() else color); canvas.drawArc(oval, -90f, level * 360f, false, stroke) }
            textPaint(fonts.strong(design.theme.font), design.theme.text)
            fit(s, rr * 0.55f, rr * 1.5f); centred(canvas, s, x, y)
            return
        }
        // A small battery and the level.
        textPaint(fonts.body(design.theme.font), color)
        val tw = fit(s, h * 0.58f * k, w * 0.7f)
        val bh = text.textSize * 0.55f; val bw = bh * 1.9f
        val bx = x - (tw + bw * 1.3f) / 2f
        stroke.shader = null; stroke.strokeWidth = r * 0.006f; stroke.color = lit(color, 0.8f)
        oval.set(bx, y - bh / 2f, bx + bw, y + bh / 2f)
        canvas.drawRoundRect(oval, bh * 0.2f, bh * 0.2f, stroke)
        if (level != null) {
            fill.shader = null; fill.color = lit(color)
            oval.set(bx + bh * 0.15f, y - bh * 0.32f, bx + bh * 0.15f + (bw - bh * 0.3f) * level, y + bh * 0.32f)
            canvas.drawRect(oval, fill)
        }
        centred(canvas, s, bx + bw * 1.3f + tw / 2f, y)
    }

    private companion object {
        val SLOTS = Slot.entries.toTypedArray()
        val AMBIENT_TIME = Placement(Element.TIME, color = ColorRole.TEXT)
    }
}
