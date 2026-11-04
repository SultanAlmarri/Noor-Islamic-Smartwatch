package com.noor.prayer.astro

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * NREL Solar Position Algorithm (Reda & Andreas 2004/2008), ported from pvlib-python's
 * `pvlib.spa` so that results match the research reference implementation to well under
 * a second of time. Accuracy of the algorithm: +/-0.0003 degrees.
 */
object SolarPosition {

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun mod(x: Double, m: Double): Double { val r = x % m; return if (r < 0) r + m else r }

    /** TT - UT1 in seconds. Measured value for the present era; long-term polynomial otherwise. */
    fun deltaT(year: Int): Double = when (year) {
        in 2015..2035 -> 69.2
        else -> {
            val y = year + 0.5
            if (y in 2005.0..2050.0) 62.92 + 0.32217 * (y - 2000) + 0.005589 * (y - 2000) * (y - 2000)
            else -20 + 32 * ((y - 1820) / 100) * ((y - 1820) / 100)
        }
    }

    private fun sumTerms(t: Array<DoubleArray>, x: Double): Double {
        var s = 0.0
        for (r in t) s += r[0] * cos(r[1] + r[2] * x)
        return s
    }

    private fun series(groups: Array<Array<DoubleArray>>, jme: Double): Double {
        var s = 0.0
        var p = 1.0
        for (g in groups) { s += sumTerms(g, jme) * p; p *= jme }
        return s / 1e8
    }

    /** Geocentric apparent quantities needed by the topocentric step. */
    class Geo(
        val jd: Double,
        val alpha: Double,      // apparent right ascension (deg, 0..360)
        val delta: Double,      // apparent declination (deg)
        val nu: Double,         // apparent sidereal time at Greenwich (deg)
        val radius: Double,     // Earth-Sun distance (AU)
        val eotMinutes: Double, // equation of time (minutes)
    )

    fun geocentric(unixSeconds: Double, deltaT: Double): Geo {
        val jd = unixSeconds / 86400.0 + 2440587.5
        val jde = jd + deltaT / 86400.0
        val jc = (jd - 2451545.0) / 36525.0
        val jce = (jde - 2451545.0) / 36525.0
        val jme = jce / 10.0

        val l = mod(deg(series(SpaTables.L, jme)), 360.0)
        val b = deg(series(SpaTables.B, jme))
        val r = series(SpaTables.R, jme)
        val theta = mod(l + 180.0, 360.0)
        val beta = -b

        val x0 = 297.85036 + 445267.111480 * jce - 0.0019142 * jce * jce + jce * jce * jce / 189474.0
        val x1 = 357.52772 + 35999.050340 * jce - 0.0001603 * jce * jce - jce * jce * jce / 300000.0
        val x2 = 134.96298 + 477198.867398 * jce + 0.0086972 * jce * jce + jce * jce * jce / 56250.0
        val x3 = 93.27191 + 483202.017538 * jce - 0.0036825 * jce * jce + jce * jce * jce / 327270.0
        val x4 = 125.04452 - 1934.136261 * jce + 0.0020708 * jce * jce + jce * jce * jce / 450000.0
        var dPsi = 0.0
        var dEps = 0.0
        for (i in SpaTables.NUT_Y.indices) {
            val y = SpaTables.NUT_Y[i]
            val c = SpaTables.NUT_ABCD[i]
            val arg = rad(y[0] * x0 + y[1] * x1 + y[2] * x2 + y[3] * x3 + y[4] * x4)
            dPsi += (c[0] + c[1] * jce) * sin(arg)
            dEps += (c[2] + c[3] * jce) * cos(arg)
        }
        dPsi /= 36000000.0
        dEps /= 36000000.0

        val u = jme / 10.0
        var e0 = 84381.448
        val coef = doubleArrayOf(-4680.93, -1.55, 1999.25, -51.38, -249.67, -39.05, 7.12, 27.87, 5.79, 2.45)
        var up = u
        for (k in coef) { e0 += k * up; up *= u }
        val eps = e0 / 3600.0 + dEps
        val dTau = -20.4898 / (3600.0 * r)
        val lambda = theta + dPsi + dTau
        val v0 = mod(280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * jc * jc - jc * jc * jc / 38710000.0, 360.0)
        val nu = v0 + dPsi * cos(rad(eps))
        val alpha = mod(deg(atan2(sin(rad(lambda)) * cos(rad(eps)) - tan(rad(beta)) * sin(rad(eps)), cos(rad(lambda)))), 360.0)
        val delta = deg(asin(sin(rad(beta)) * cos(rad(eps)) + cos(rad(beta)) * sin(rad(eps)) * sin(rad(lambda))))

        val m = 280.4664567 + 360007.6982779 * jme + 0.03032028 * jme * jme + jme * jme * jme / 49931.0 -
            jme * jme * jme * jme / 15300.0 - jme * jme * jme * jme * jme / 2000000.0
        var e = mod(m - 0.0057183 - alpha + dPsi * cos(rad(eps)), 360.0) * 4.0
        if (e > 20) e -= 1440.0 else if (e < -20) e += 1440.0
        return Geo(jd, alpha, delta, nu, r, e)
    }

    /** Topocentric elevation of the Sun's centre, without atmospheric refraction (deg). */
    fun elevation(unixSeconds: Double, lat: Double, lon: Double, elevationM: Double, deltaT: Double): Double {
        val g = geocentric(unixSeconds, deltaT)
        val h = mod(g.nu + lon - g.alpha, 360.0)
        val xi = 8.794 / (3600.0 * g.radius)
        val uu = atan(0.99664719 * tan(rad(lat)))
        val x = cos(uu) + elevationM / 6378140.0 * cos(rad(lat))
        val y = 0.99664719 * sin(uu) + elevationM / 6378140.0 * sin(rad(lat))
        val dAlpha = deg(atan2(-x * sin(rad(xi)) * sin(rad(h)), cos(rad(g.delta)) - x * sin(rad(xi)) * cos(rad(h))))
        val deltaP = deg(atan2((sin(rad(g.delta)) - y * sin(rad(xi))) * cos(rad(dAlpha)), cos(rad(g.delta)) - x * sin(rad(xi)) * cos(rad(h))))
        val hP = h - dAlpha
        return deg(asin(sin(rad(lat)) * sin(rad(deltaP)) + cos(rad(lat)) * cos(rad(deltaP)) * cos(rad(hP))))
    }

    /**
     * Meridian transit (local apparent noon) for the UT day starting at [utDayStart] (unix seconds,
     * a multiple of 86400), following SPA appendix A.2 exactly as pvlib implements it.
     * Returns unix seconds.
     */
    fun transit(utDayStart: Double, lon: Double, deltaT: Double): Double {
        val v = geocentric(utDayStart, deltaT).nu
        val tt0 = utDayStart - deltaT
        val g0 = geocentric(tt0, deltaT)
        val gm = geocentric(tt0 - 86400.0, deltaT)
        val gp = geocentric(tt0 + 86400.0, deltaT)
        val m0 = mod((g0.alpha - lon - v) / 360.0, 1.0)
        val vs = v + 360.985647 * m0
        val n = m0 + deltaT / 86400.0
        var a = g0.alpha - gm.alpha
        if (kotlin.math.abs(a) > 2) a = mod(a, 1.0)
        var b = gp.alpha - g0.alpha
        if (kotlin.math.abs(b) > 2) b = mod(b, 1.0)
        val c = b - a
        val alphaP = g0.alpha + n * (a + b + c * n) / 2.0
        var hp = mod(vs + lon - alphaP, 360.0)
        if (hp >= 180) hp -= 360.0
        return utDayStart + (m0 - hp / 360.0) * 86400.0
    }

    /** Apparent solar semidiameter (deg) from the Earth-Sun distance, 959.63"/r. */
    fun semidiameter(unixSeconds: Double, deltaT: Double): Double = 959.63 / 3600.0 / geocentric(unixSeconds, deltaT).radius

    @Suppress("unused")
    internal fun floorDiv(x: Double, m: Double) = floor(x / m) * m
}
