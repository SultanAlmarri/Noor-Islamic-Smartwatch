package com.noor.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.noor.prayer.Prayer
import com.noor.prayer.astro.SunPath
import com.noor.prayer.sky.SkyPalette
import kotlin.math.pow

/**
 * What is behind a widget (0.24), full-bleed: the launcher's own rounded clip is the widget's only corner (our
 * pictures have none, so no second, smaller corner shows inside Samsung's). The sky is a smooth gradient of the
 * moment's colours ([SkyPalette]: zenith, the middle, the horizon) with a soft glow of the Sun or the Moon and, at
 * night, faint stars; no land, clouds or photographs. It is darkened wherever white text would read under
 * [SKY_CONTRAST] to 1, so the quieter text (72 % and 52 % white) still reads.
 */
internal object WidgetBack {
    /** White on the sky, at least. */
    const val SKY_CONTRAST = 4.6

    /** The picture's resolution: a smooth gradient needs few pixels (the launcher smooths it as it scales). */
    private const val PX_PER_DP = 1f

    class Look(val zenith: Int, val mid: Int, val horizon: Int, val night: Float)

    /** The design's three skies (as iOS Weather's: vivid, clean): by day, at the golden hour, at night. */
    private val DAY = intArrayOf(0xFF2F7FD8.toInt(), 0xFF4B97E6.toInt(), 0xFF6FB0EF.toInt())
    private val GOLDEN = intArrayOf(0xFF3A4F9A.toInt(), 0xFF8A6AA0.toInt(), 0xFFE0896A.toInt())
    /** The morning's low light: rosier than the evening's. */
    private val DAWN = intArrayOf(0xFF34508F.toInt(), 0xFF9A7AAE.toInt(), 0xFFF0A38A.toInt())
    private val NIGHT = intArrayOf(0xFF0B1430.toInt(), 0xFF16244C.toInt(), 0xFF233564.toInt())

    /** The Sun's elevation (degrees) now at the widget's place. */
    fun elevation(r: WidgetCtx): Double = r.ws?.let { SunPath.elevation(r.now, it.place.point) } ?: 30.0

    /** The elevation the sky is drawn for: now's, or the light chosen (Dawn, Day, Golden, Night). */
    fun elevationFor(r: WidgetCtx): Double = when (r.o.skyLight) {
        SkyLight.LIVE -> elevation(r); SkyLight.DAWN, SkyLight.GOLDEN -> 3.0; SkyLight.DAY -> 35.0; SkyLight.NIGHT -> -20.0
    }

    /** Whether the low light is the morning's (before noon, or Dawn chosen). */
    fun morning(r: WidgetCtx): Boolean = when (r.o.skyLight) {
        SkyLight.DAWN -> true; SkyLight.GOLDEN -> false
        else -> r.ws?.day?.instant(Prayer.DHUHR)?.let { r.now.isBefore(it) } ?: false
    }

    /**
     * The sky's colours for the Sun at [elev] degrees: the day's sky from 10° up, the golden hour's at 2°, the night's
     * from 6° under the horizon, blended smoothly between (in OKLab, [SkyPalette.mix]).
     */
    fun skyLook(elev: Double, morning: Boolean = false): Look {
        fun mix(a: IntArray, b: IntArray, t: Float) = IntArray(3) { SkyPalette.mix(a[it], b[it], t.coerceIn(0f, 1f)) }
        val low = if (morning) DAWN else GOLDEN
        val c = when {
            elev >= 10 -> DAY
            elev >= 2 -> mix(low, DAY, ((elev - 2) / 8).toFloat())
            elev >= -6 -> mix(NIGHT, low, ((elev + 6) / 8).toFloat())
            else -> NIGHT
        }
        return Look(c[0], c[1], c[2], ((-elev - 2) / 6).toFloat().coerceIn(0f, 1f))
    }

    fun draw(r: WidgetCtx, wDp: Float, hDp: Float, opacity: Int = r.o.opacity): Bitmap? {
        val bg = r.pal.background
        if (bg == WidgetBackground.CLEAR) return null
        val w = maxOf(8, (wDp * PX_PER_DP).toInt()); val h = maxOf(8, (hDp * PX_PER_DP).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        fun vertical(vararg stops: Pair<Float, Int>) {
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
        }
        // (The opacity as drawn: WidgetLooks may have made it more solid, so its text reads.)
        val a = if (r.pal.background == WidgetBackground.YOU) 255 else (opacity.coerceIn(0, 100) * 255 + 50) / 100
        fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
        when (bg) {
            WidgetBackground.SKY -> {
                val l = skyLook(elevationFor(r), morning(r))
                vertical(0f to l.zenith, 0.55f to l.mid, 1f to l.horizon)
                clamp(bmp, SKY_CONTRAST)
                if (l.night > 0.3f) stars(c, w, h, l.night)
                if (a < 255) fade(bmp, a)
            }
            WidgetBackground.DARK -> c.drawColor(withAlpha(0xFF1C1C1E.toInt(), a))
            WidgetBackground.LIGHT -> c.drawColor(withAlpha(0xFFFFFFFF.toInt(), a))
            WidgetBackground.AUTO -> c.drawColor(withAlpha(if (r.pal.light) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt(), a))
            WidgetBackground.SOLID, WidgetBackground.GRADIENT -> {
                if (r.o.background == WidgetBackground.SOLID) c.drawColor(r.o.color1 or 0xFF000000.toInt())
                else vertical(0f to (r.o.color1 or 0xFF000000.toInt()), 1f to (r.o.color2 or 0xFF000000.toInt()))
                // A middle tone neither white nor dark text reads on: darkened just enough for white (WidgetLooks).
                if (WidgetLooks.needsDarkening(WidgetLooks.customColors(r.o))) clamp(bmp, SKY_CONTRAST)
                if (a < 255) fade(bmp, a)
            }
            WidgetBackground.GLASS -> {
                vertical(0f to withAlpha(0xFF2A2D36.toInt(), a), 1f to withAlpha(0xFF15171D.toInt(), a))
                // A soft light along the top, as the app's glass.
                vertical(0f to 0x14FFFFFF, 0.35f to 0x00FFFFFF)
            }
            WidgetBackground.YOU -> {
                val fill = if (r.pal.fill != 0) r.pal.fill else 0xFF232327.toInt()
                vertical(0f to fill, 1f to darker(fill, if (r.pal.light) 0.96f else 0.85f))
            }
            WidgetBackground.CLEAR -> Unit
        }
        return bmp.also { it.density = Bitmap.DENSITY_NONE }
    }

    private fun darker(c: Int, k: Float): Int {
        val r = ((c shr 16 and 255) * k).toInt(); val g = ((c shr 8 and 255) * k).toInt(); val b = ((c and 255) * k).toInt()
        return (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    private val LIN = DoubleArray(256) { v -> (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) } }
    private const val GAM_STEPS = 4096
    private val GAM = IntArray(GAM_STEPS + 1) { i -> val v = i.toDouble() / GAM_STEPS
        (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055).let { (it * 255 + 0.5).toInt().coerceIn(0, 255) } }
    private fun lin(v: Int): Double = LIN[v]
    private fun gam(v: Double): Int = GAM[(v.coerceIn(0.0, 1.0) * GAM_STEPS + 0.5).toInt()]

    /** The picture at [alpha] (0..255) of its strength. */
    private fun fade(bmp: Bitmap, alpha: Int) {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in px.indices) px[i] = (px[i] and 0x00FFFFFF) or ((((px[i] ushr 24) * alpha) / 255) shl 24)
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }

    /** One colour no brighter than white text can read on at [SKY_CONTRAST]:1 (as [clamp] does to the picture). */
    fun clampColor(c: Int): Int {
        val maxLum = 1.05 / SKY_CONTRAST - 0.05
        val r = lin(c shr 16 and 255); val g = lin(c shr 8 and 255); val b = lin(c and 255)
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        if (lum <= maxLum) return c
        val k = maxLum / lum * 0.995
        return (c and 0xFF000000.toInt()) or (gam(r * k) shl 16) or (gam(g * k) shl 8) or gam(b * k)
    }

    /** Every pixel no brighter than white text can read on at [contrast]:1 (its light scaled down, its hue kept). */
    internal fun clamp(bmp: Bitmap, contrast: Double) {
        val maxLum = 1.05 / contrast - 0.05
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in px.indices) {
            val c = px[i]
            val r = lin(c shr 16 and 255); val g = lin(c shr 8 and 255); val b = lin(c and 255)
            val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            if (lum <= maxLum) continue
            val k = maxLum / lum * 0.995
            px[i] = (c and 0xFF000000.toInt()) or (gam(r * k) shl 16) or (gam(g * k) shl 8) or gam(b * k)
        }
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }

    /** Faint stars in the upper sky, the same each night (a fixed scatter). */
    private fun stars(c: Canvas, w: Int, h: Int, night: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val n = (w * h / 900).coerceIn(12, 60)
        for (i in 0 until n) {
            fun frac(x: Double) = (kotlin.math.abs(kotlin.math.sin(x) * 43758.5453) % 1.0).toFloat()
            val x = frac(i * 12.9898) * w; val y = frac(i * 78.233) * h * 0.7f
            p.alpha = ((0.25f + ((i * 13) % 5) / 12f) * night * 255).toInt().coerceIn(0, 255)
            c.drawCircle(x, y, 0.45f + ((i * 7) % 3) * 0.2f, p)
        }
    }
}
