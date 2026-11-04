package com.noor.prayer.sky

import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Noor's sky colours for a solar elevation, shared by the phone and the watch (colours as ARGB ints, no Android).
 * Hand-picked anchors for night, the three twilights, golden hour and day, blended in OKLab so transitions stay
 * smooth. Mornings are cooler (rose), evenings warmer (amber, crimson), as they are outside.
 */
object SkyPalette {
    class Colors(val zenith: Int, val mid: Int, val horizon: Int, /** 0 in daylight … 1 in full night. */ val night: Float)

    private class Anchor(val elevation: Double, val zenith: Long, val mid: Long, val dawn: Long, val dusk: Long)

    private val anchors = listOf(
        Anchor(-90.0, 0xFF03050C, 0xFF060A16, 0xFF0A1128, 0xFF0A1128),
        Anchor(-18.0, 0xFF04060F, 0xFF080D1E, 0xFF111A36, 0xFF111A36),
        Anchor(-12.0, 0xFF060B1E, 0xFF111839, 0xFF2A2F5C, 0xFF2C2B57),
        Anchor(-6.0, 0xFF0C1537, 0xFF262C60, 0xFF5E4C82, 0xFF6E3F6C),
        Anchor(-2.0, 0xFF162350, 0xFF414381, 0xFFC07C86, 0xFFD9715C),
        Anchor(0.5, 0xFF203466, 0xFF5D5E94, 0xFFEAA27E, 0xFFF28A52),
        Anchor(4.0, 0xFF28497F, 0xFF7486B0, 0xFFF3C595, 0xFFF6B370),
        Anchor(10.0, 0xFF24568F, 0xFF6893C4, 0xFFCFDDE6, 0xFFE9D3B4),
        Anchor(25.0, 0xFF1B568F, 0xFF4A86C0, 0xFFB4D2EA, 0xFFC7D6E2),
        Anchor(60.0, 0xFF16518C, 0xFF3B7EC0, 0xFFA3CBEC, 0xFFA3CBEC),
    )

    fun colors(elevationDeg: Double, evening: Boolean): Colors {
        val e = elevationDeg.coerceIn(-90.0, 90.0)
        val i = anchors.indexOfLast { it.elevation <= e }.coerceIn(0, anchors.size - 2)
        val a = anchors[i]; val b = anchors[i + 1]
        val t = ((e - a.elevation) / (b.elevation - a.elevation)).coerceIn(0.0, 1.0).toFloat()
        return Colors(
            zenith = mix(a.zenith.toInt(), b.zenith.toInt(), t),
            mid = mix(a.mid.toInt(), b.mid.toInt(), t),
            horizon = mix((if (evening) a.dusk else a.dawn).toInt(), (if (evening) b.dusk else b.dawn).toInt(), t),
            night = ((-e - 4.0) / 10.0).coerceIn(0.0, 1.0).toFloat(),
        )
    }

    /**
     * One colour for "the light of the moment" on a black screen: the warm horizon around sunrise and sunset,
     * otherwise the colour of the open sky (blue by day, near black at night).
     */
    fun glow(elevationDeg: Double, evening: Boolean): Int {
        val c = colors(elevationDeg, evening)
        val twilight = (1.0 - kotlin.math.abs(elevationDeg) / 9.0).coerceIn(0.0, 1.0).toFloat()
        return mix(c.mid, c.horizon, twilight)
    }

    /** Perceptual blend of two ARGB colours (OKLab). */
    fun mix(a: Int, b: Int, t: Float): Int {
        val la = toLab(a); val lb = toLab(b)
        val alpha = ((a ushr 24) + (((b ushr 24) - (a ushr 24)) * t)).toInt().coerceIn(0, 255)
        return fromLab(FloatArray(3) { la[it] + (lb[it] - la[it]) * t }, alpha)
    }

    /** WCAG contrast ratio between two opaque colours. */
    fun contrast(a: Int, b: Int): Double {
        fun lum(c: Int) = 0.2126 * lin(ch(c, 16)) + 0.7152 * lin(ch(c, 8)) + 0.0722 * lin(ch(c, 0))
        val x = lum(a); val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun ch(c: Int, shift: Int) = ((c shr shift) and 0xFF) / 255f
    private fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    private fun gam(c: Float): Float { val x = c.coerceIn(0f, 1f); return if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - 0.055f }

    private fun toLab(c: Int): FloatArray {
        val r = lin(ch(c, 16)); val g = lin(ch(c, 8)); val b = lin(ch(c, 0))
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        return floatArrayOf(
            0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
        )
    }

    private fun fromLab(lab: FloatArray, alpha: Int): Int {
        val l = (lab[0] + 0.3963377774f * lab[1] + 0.2158037573f * lab[2]).pow(3)
        val m = (lab[0] - 0.1055613458f * lab[1] - 0.0638541728f * lab[2]).pow(3)
        val s = (lab[0] - 0.0894841775f * lab[1] - 1.2914855480f * lab[2]).pow(3)
        fun byte(v: Float) = (gam(v) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = byte(4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s)
        val g = byte(-1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s)
        val b = byte(-0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s)
        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }
}
