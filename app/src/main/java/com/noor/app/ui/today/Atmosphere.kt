package com.noor.app.ui.today

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The colour of the sky, computed rather than painted (0.9.6).
 *
 * Sunlight is scattered by the air (Rayleigh: blue first), by haze (Mie: the bright glow round the Sun) and partly
 * absorbed by ozone (which keeps the twilight sky blue after sunset), over a round Earth whose shadow rises in the east
 * at dusk. Single scattering along each line of sight, with the light reaching each point taken from a precomputed
 * transmittance table, a simple allowance for light scattered more than once, a daylight white balance, an exposure
 * that follows the Sun down through twilight the way the eye adapts, and a filmic tone curve.
 *
 * The result is a small table of display colours over (column of the screen, height in the sky), which the GPU shaders
 * and the plain-Canvas fallback both stretch over the sky, plus the light of the moment (sunlight on the clouds and on
 * the land, skylight) that the shaders use. Pure Kotlin: tested on the JVM (text contrast, continuity through twilight).
 *
 * Units: km and radians. The camera stands [EYE_H] km up looking along +z; x is to the right and y up.
 */
internal object Atmosphere {
    const val NX = 48
    const val NY = 32
    /** Rows are spaced by v = (altitude / 90°)^(1/[LUT_POW]): more rows near the horizon, where the colour changes. */
    const val LUT_POW = 2.0

    private const val R = 6360.0
    private const val RA = 6460.0
    private const val HR = 8.0
    private const val HM = 1.2
    private val BR = doubleArrayOf(5.802e-3, 13.558e-3, 33.1e-3)
    private const val BMS = 3.996e-3
    private const val BME = 4.40e-3
    // Ozone, a little stronger than the standard atmosphere: the blue hour is the point of it.
    private val BO = doubleArrayOf(0.650e-3 * 2.2, 1.881e-3 * 2.2, 0.085e-3 * 2.2)
    private const val EYE_H = 0.25
    private const val G = 0.76
    private const val MS = 0.06
    private const val SAT = 1.25
    const val E_SUN = 20.0
    private const val DEG = PI / 180.0

    // Transmittance to the top of the atmosphere, T(h, mu); 0 where the ray meets the ground (the Earth's shadow).
    private const val TH = 64
    private const val TM = 256
    private val table: FloatArray by lazy { buildTable() }
    /** Sunlight at 50° elevation is white, as the eye (or a camera set to daylight) sees it. */
    private val sun: DoubleArray by lazy {
        val t = DoubleArray(3).also { trans(EYE_H, sin(50 * DEG), it) }
        val l = luma(t[0], t[1], t[2])
        doubleArrayOf(l / t[0], l / t[1], l / t[2])
    }

    /** Builds the tables now (a fraction of a second on a phone), so the first frame does not have to. */
    fun warmUp() { table; sun }

    private fun hOf(i: Int): Double { val x = i / (TH - 1.0); return x * x * (RA - R) }
    private fun iOfH(h: Double): Double = sqrt(max(0.0, h) / (RA - R)) * (TH - 1)
    private fun muOf(j: Int): Double { val x = j / (TM - 1.0) * 2 - 1; return (if (x < 0) -1 else 1) * x * x }
    private fun jOfMu(mu: Double): Double { val x = (if (mu < 0) -1 else 1) * sqrt(abs(mu)); return (x + 1) / 2 * (TM - 1) }

    private fun buildTable(): FloatArray {
        val out = FloatArray(TH * TM * 3)
        for (i in 0 until TH) {
            val h = hOf(i); val r = R + h
            for (j in 0 until TM) {
                val mu = muOf(j)
                val k = (i * TM + j) * 3
                val b = r * mu
                val discG = b * b - (r * r - R * R)
                if (mu < 0 && discG > 0 && -b - sqrt(discG) > 0) continue
                val len = -b + sqrt(max(0.0, b * b - (r * r - RA * RA)))
                val n = 60
                var tr = 0.0; var tm = 0.0; var to = 0.0
                for (s in 0 until n) {
                    val t = (s + 0.5) / n * len
                    val hh = sqrt(r * r + t * t + 2 * r * mu * t) - R
                    tr += exp(-hh / HR); tm += exp(-hh / HM); to += max(0.0, 1 - abs(hh - 25) / 15)
                }
                val dt = len / n
                for (c in 0..2) out[k + c] = exp(-(BR[c] * tr + BME * tm + BO[c] * to) * dt).toFloat()
            }
        }
        return out
    }

    private fun trans(h: Double, mu: Double, out: DoubleArray) {
        val tb = table
        val fi = min(TH - 1.001, iOfH(h)); val fj = min(TM - 1.001, max(0.0, jOfMu(mu)))
        val i = fi.toInt(); val j = fj.toInt(); val a = fi - i; val b = fj - j
        for (c in 0..2) {
            val v00 = tb[(i * TM + j) * 3 + c]; val v01 = tb[(i * TM + j + 1) * 3 + c]
            val v10 = tb[((i + 1) * TM + j) * 3 + c]; val v11 = tb[((i + 1) * TM + j + 1) * 3 + c]
            out[c] = (v00 * (1 - b) + v01 * b) * (1 - a) + (v10 * (1 - b) + v11 * b) * a
        }
    }

    private fun phaseR(mu: Double) = 3 / (16 * PI) * (1 + mu * mu)
    private fun phaseM(mu: Double): Double { val g2 = G * G; return 3 / (8 * PI) * (1 - g2) * (1 + mu * mu) / ((2 + g2) * (1 + g2 - 2 * G * mu).pow(1.5)) }
    private fun luma(r: Double, g: Double, b: Double) = 0.2126 * r + 0.7152 * g + 0.0722 * b

    /** Scratch space for one thread. */
    private class Work {
        val t = DoubleArray(3); val tms = DoubleArray(3); val out = DoubleArray(3); val tone = DoubleArray(3)
    }

    /**
     * Light arriving from direction (vx, vy, vz): transmittance towards the Sun uses its true direction [sl]; the phase
     * (where the glow sits) uses the direction it is drawn in, [sp].
     */
    private fun radiance(vx: Double, vy: Double, vz: Double, sl: DoubleArray, sp: DoubleArray, w: Work, out: DoubleArray) {
        val oy = R + EYE_H
        val b = oy * vy
        val len = -b + sqrt(b * b - (oy * oy - RA * RA))
        val mu = vx * sp[0] + vy * sp[1] + vz * sp[2]
        val pr = phaseR(mu); val pm = phaseM(mu)
        val n = 12
        var odR = 0.0; var odM = 0.0; var odO = 0.0
        var l0 = 0.0; var l1 = 0.0; var l2 = 0.0
        val tt = w.t; val tms = w.tms
        for (s in 0 until n) {
            val u0 = s.toDouble() / n; val u1 = (s + 1.0) / n
            val t0 = u0 * u0 * len; val t1 = u1 * u1 * len
            val t = 0.5 * (t0 + t1); val dt = t1 - t0
            val px = vx * t; val py = oy + vy * t; val pz = vz * t
            val r = sqrt(px * px + py * py + pz * pz)
            val h = r - R
            val dR = exp(-h / HR); val dM = exp(-h / HM); val dO = max(0.0, 1 - abs(h - 25) / 15)
            odR += dR * dt; odM += dM * dt; odO += dO * dt
            val muS = (px * sl[0] + py * sl[1] + pz * sl[2]) / r
            trans(h, muS, tt)
            trans(h, muS + 0.10, tms)
            for (k in 0..2) {
                val tv = exp(-(BR[k] * odR + BME * odM + BO[k] * odO))
                val sc = ((BR[k] * dR * pr + BMS * dM * pm) * tt[k] + (BR[k] * dR + BMS * dM) * MS * tms[k]) * tv * dt
                when (k) { 0 -> l0 += sc; 1 -> l1 += sc; else -> l2 += sc }
            }
        }
        val s = sun
        out[0] = l0 * s[0] * E_SUN; out[1] = l1 * s[1] * E_SUN; out[2] = l2 * s[2] * E_SUN
    }

    private fun aces(x: Double): Double = ((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14)).coerceIn(0.0, 1.0)
    private fun srgb(x: Double): Double = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
    fun linear(s: Double): Double = if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)

    /** The filmic curve the shaders also use (sky_common.agsl `tone`): linear light in, display values (0..1) out. */
    fun tone(c: DoubleArray, out: DoubleArray) {
        val l = luma(c[0], c[1], c[2])
        val s = if (l > 1e-6) aces(l) / l else 0.0
        for (k in 0..2) {
            val v = max(c[k], 0.0)
            out[k] = min(v * s, 1.0) * 0.7 + aces(v) * 0.3
        }
        val y = luma(out[0], out[1], out[2])
        for (k in 0..2) out[k] = srgb((y + (out[k] - y) * SAT).coerceIn(0.0, 1.0))
    }

    private fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

    /** How bright the zenith is shown, by the Sun's elevation: full daylight, a gentle fall through twilight. */
    internal fun targetLuminance(el: Double): Double {
        val pts = TARGET
        if (el <= pts[0][0]) return pts[0][1]
        for (i in 1 until pts.size) if (el <= pts[i][0]) {
            val (x0, y0) = pts[i - 1].let { it[0] to it[1] }; val (x1, y1) = pts[i].let { it[0] to it[1] }
            val t = (el - x0) / (x1 - x0); val u = t * t * (3 - 2 * t)
            return exp(kotlin.math.ln(y0) + (kotlin.math.ln(y1) - kotlin.math.ln(y0)) * u)
        }
        return pts.last()[1]
    }
    private val TARGET = arrayOf(
        doubleArrayOf(-16.0, 0.0006), doubleArrayOf(-12.0, 0.0035), doubleArrayOf(-9.0, 0.011), doubleArrayOf(-6.0, 0.026),
        doubleArrayOf(-3.0, 0.042), doubleArrayOf(0.0, 0.056), doubleArrayOf(4.0, 0.068), doubleArrayOf(10.0, 0.080), doubleArrayOf(90.0, 0.092),
    )

    /** How far night has come: 0 by day, 1 when the last light of twilight has gone. */
    fun night(el: Double): Double = 1 - smooth(-14.0, -1.0, el)

    // The designed night: a deep blue, a little lighter towards the horizon (airglow, distant light).
    private val NIGHT_TOP = doubleArrayOf(0.010, 0.017, 0.045)
    private val NIGHT_LOW = doubleArrayOf(0.028, 0.044, 0.085)
    /** Small text on the sky: relative luminance at most this (white ≥ 4.5:1). */
    const val TEXT_LUMINANCE = 0.18
    /** The large countdown: at most this (white ≥ 3:1, WCAG's large-text minimum). */
    const val LARGE_TEXT_LUMINANCE = 0.30

    /**
     * How bright the sky may be at [altDeg] when the text ends at [capAlt]: the small text higher up gets 4.5:1, the
     * band just above the text's bottom (the large countdown) 3:1, and below the text the limit lifts gradually over
     * 15°, so it reads as the natural darkening of the upper sky rather than a band.
     */
    fun textLimit(altDeg: Double, capAlt: Double): Double = if (altDeg >= capAlt)
        LARGE_TEXT_LUMINANCE + (TEXT_LUMINANCE - LARGE_TEXT_LUMINANCE) * smooth(capAlt, capAlt + 12, altDeg)
    else LARGE_TEXT_LUMINANCE + (1 - LARGE_TEXT_LUMINANCE) * (1 - smooth(capAlt - 15, capAlt, altDeg))

    /** The light of one moment, for the shaders: all linear, already exposed. */
    class Light {
        var exposure = 1.0
        var night = 0.0
        val sunGround = DoubleArray(3); val sunCloud = DoubleArray(3); val sunHigh = DoubleArray(3)
        val ambTop = DoubleArray(3); val ambLow = DoubleArray(3)
    }

    /**
     * Fills [lut] (NX × NY ARGB, row 0 at the horizon) with the sky for the Sun at [sunEl] (true elevation, degrees),
     * drawn at azimuth [sunAz] and altitude [drawAlt] (radians), over a field of view [fov]; text rows from [capAlt]
     * (degrees) up are kept dark enough for white text. Returns the light of the moment.
     */
    fun frame(sunEl: Double, sunAz: Double, drawAlt: Double, fov: Double, capAlt: Double, lut: IntArray, light: Light = Light()): Light {
        val w = Work()
        val e = sunEl * DEG
        val sl = doubleArrayOf(cos(e) * sin(sunAz), sin(e), cos(e) * cos(sunAz))
        val sp = doubleArrayOf(cos(drawAlt) * sin(sunAz), sin(drawAlt), cos(drawAlt) * cos(sunAz))
        val z = DoubleArray(3); val hz = DoubleArray(3)
        radiance(cos(89 * DEG) * sin(0.0), sin(89 * DEG), cos(89 * DEG), sl, sp, w, z)
        val side = sunAz + PI / 2
        radiance(cos(4 * DEG) * sin(side), sin(4 * DEG), cos(4 * DEG) * cos(side), sl, sp, w, hz)
        val exposure = min(600.0, targetLuminance(sunEl) / max(luma(z[0], z[1], z[2]), 1e-9))
        val night = night(sunEl)
        val c = w.out; val tm = w.tone
        // First the sky as the eye would see it (display values, kept linear for the next step)...
        val lin = DoubleArray(NX * NY * 3)
        for (j in 0 until NY) {
            val v = j / (NY - 1.0)
            val altDeg = v.pow(LUT_POW) * 90
            val alt = max(altDeg, 0.2) * DEG
            val ca = cos(alt); val sa = sin(alt)
            val wN = (1 - v).pow(2.5)
            for (i in 0 until NX) {
                val az = (i / (NX - 1.0) - 0.5) * fov
                radiance(ca * sin(az), sa, ca * cos(az), sl, sp, w, c)
                for (k in 0..2) c[k] = c[k] * exposure + night * (NIGHT_TOP[k] + (NIGHT_LOW[k] - NIGHT_TOP[k]) * wN)
                tone(c, tm)
                for (k in 0..2) lin[(j * NX + i) * 3 + k] = linear(tm[k])
            }
        }
        // ...then, where text is written on it, a graduated filter as a photographer would use: one smooth darkening
        // from just above the horizon up to the text, as strong as this moment needs for the text to stay readable
        // (decided on the typical column; the few brightest, near the Sun, are held by the limit below).
        val ratios = ArrayList<Double>()
        for (j in 0 until NY) {
            val altDeg = (j / (NY - 1.0)).pow(LUT_POW) * 90
            if (altDeg < capAlt) continue
            val lim = textLimit(altDeg, capAlt)
            for (i in 0 until NX) ratios += luma(lin[(j * NX + i) * 3], lin[(j * NX + i) * 3 + 1], lin[(j * NX + i) * 3 + 2]) / lim
        }
        ratios.sort()
        val typical = if (ratios.isEmpty()) 0.0 else ratios[(ratios.size * 0.7).toInt().coerceAtMost(ratios.size - 1)]
        val darken = if (typical > 1) 1 / typical else 1.0
        for (j in 0 until NY) {
            val altDeg = (j / (NY - 1.0)).pow(LUT_POW) * 90
            val k = 1 - (1 - darken) * smooth(2.0, capAlt + 4, altDeg)
            for (i in 0 until NX) {
                val o = (j * NX + i) * 3
                for (ch in 0..2) tm[ch] = srgb(lin[o + ch] * k)
                capLuminance(tm, textLimit(altDeg, capAlt))
                lut[j * NX + i] = (0xFF shl 24) or (channel(tm[0]) shl 16) or (channel(tm[1]) shl 8) or channel(tm[2])
            }
        }
        light.exposure = exposure; light.night = night
        val muSun = sin(e)
        fun sunAt(h: Double, out: DoubleArray) {
            trans(h, muSun, w.t)
            for (k in 0..2) out[k] = w.t[k] * sun[k] * E_SUN * exposure / PI
        }
        sunAt(0.3, light.sunGround); sunAt(2.0, light.sunCloud); sunAt(8.0, light.sunHigh)
        for (k in 0..2) {
            light.ambTop[k] = z[k] * exposure + NIGHT_TOP[k] * night * 1.6
            light.ambLow[k] = hz[k] * exposure + NIGHT_TOP[k] * night * 2.4
        }
        return light
    }

    /** Keeps a display colour's relative luminance at most [max], darkening it without changing its hue. */
    fun capLuminance(c: DoubleArray, max: Double) {
        val r = linear(c[0]); val g = linear(c[1]); val b = linear(c[2])
        val l = luma(r, g, b)
        if (l <= max) return
        val k = max / l
        c[0] = srgb(r * k); c[1] = srgb(g * k); c[2] = srgb(b * k)
    }

    private fun channel(x: Double): Int = (x.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()

    /** The sky's display colour (ARGB) in a table at fraction [u] across and [v] up (both 0..1), bilinear. */
    fun sample(lut: IntArray, u: Double, v: Double): Int {
        val x = (u.coerceIn(0.0, 1.0) * (NX - 1)); val y = (v.coerceIn(0.0, 1.0) * (NY - 1))
        val i = min(x.toInt(), NX - 2); val j = min(y.toInt(), NY - 2)
        val a = x - i; val b = y - j
        var out = 0xFF shl 24
        for (sh in intArrayOf(16, 8, 0)) {
            fun ch(ii: Int, jj: Int) = (lut[jj * NX + ii] shr sh) and 0xFF
            val v0 = ch(i, j) * (1 - a) + ch(i + 1, j) * a
            val v1 = ch(i, j + 1) * (1 - a) + ch(i + 1, j + 1) * a
            out = out or (((v0 * (1 - b) + v1 * b) + 0.5).toInt().coerceIn(0, 255) shl sh)
        }
        return out
    }

    /** The table row coordinate v for an altitude in degrees. */
    fun rowOf(altDeg: Double): Double = (max(altDeg, 0.0) / 90.0).coerceAtMost(1.0).pow(1 / LUT_POW)

}
