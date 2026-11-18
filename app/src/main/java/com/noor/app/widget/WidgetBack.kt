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
    /** White on the sky, at least (so 52 % white text keeps 3:1 and white 7:1). */
    const val SKY_CONTRAST = 7.0

    /** The picture's resolution: a smooth gradient needs few pixels (the launcher smooths it as it scales). */
    private const val PX_PER_DP = 1f

    class Look(val zenith: Int, val mid: Int, val horizon: Int, val night: Float, val glow: Int, val glowAlpha: Float)

    /** The sky's colours now, where the widget's place is. */
    fun skyLook(r: WidgetCtx): Look {
        val ws = r.ws
        val (elev, evening) = if (ws != null) {
            val noon = ws.day.instant(Prayer.DHUHR)
            SunPath.elevation(r.now, ws.place.point) to (noon != null && r.now.isAfter(noon))
        } else 30.0 to false
        val c = SkyPalette.colors(elev, evening)
        val night = c.night
        // By day a warm Sun's glow, at twilight the horizon's colour, at night a cool, faint Moon's.
        val glow = when { night > 0.6f -> 0xFFC9D6FF.toInt(); elev < 8 -> c.horizon; else -> 0xFFFFD58C.toInt() }
        return Look(c.zenith, c.mid, c.horizon, night, glow, if (night > 0.6f) 0.10f else 0.30f)
    }

    fun draw(r: WidgetCtx, wDp: Float, hDp: Float): Bitmap? {
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
        val a = r.pal.alpha
        fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
        when (bg) {
            WidgetBackground.SKY -> {
                val l = skyLook(r)
                vertical(0f to l.zenith, 0.6f to l.mid, 1f to l.horizon)
                // The glow: high at the widget's far side, as the Sun or the Moon over it.
                val gx = w * 0.80f; val gy = h * 0.15f; val gr = maxOf(w, h) * 0.55f
                val g = l.glow and 0x00FFFFFF
                p.shader = RadialGradient(gx, gy, gr, intArrayOf(g or ((255 * l.glowAlpha).toInt() shl 24), g or ((255 * l.glowAlpha * 0.4f).toInt() shl 24), g), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
                clamp(bmp, SKY_CONTRAST)
                if (l.night > 0.3f) stars(c, w, h, l.night)
            }
            WidgetBackground.DARK -> vertical(0f to withAlpha(0xFF232327.toInt(), a), 1f to withAlpha(0xFF18181B.toInt(), a))
            WidgetBackground.LIGHT -> vertical(0f to withAlpha(0xFFFFFFFF.toInt(), a), 1f to withAlpha(0xFFF3F1EC.toInt(), a))
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

    private fun lin(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun gam(v: Double): Int = (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055).let { (it * 255 + 0.5).toInt().coerceIn(0, 255) }

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
