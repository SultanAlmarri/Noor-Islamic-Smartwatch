package com.noor.app.ui.today

import com.noor.prayer.Prayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Sun's path when Settings → Home scenery → Sun's path is Simple (0.23, the default; the owner, 18 November 2026:
 * "the default sun path, which is consistent, it should have some realism ... like where the sun rises and sets,
 * depending on your location. But the shape should not change").
 *
 * - **The shape never changes**: a half sine, `horizon − (horizon − arcTop)·sin(π·u)`, `u` the day's progress from sunrise
 *   (0) to sunset (1). Its top is always at [arcTopY], at the middle of the day; it is round there, never flat, never a loop.
 * - **Its ends are real**: sunrise and sunset sit on the horizon where the Sun really rises and sets: their azimuths from
 *   the noon Sun's, the observer facing the noon Sun as in the real path ([SunPanorama]: the southern hemisphere's Sun
 *   rises on the right), on one fixed scale for every place and day: half the width is [HALF_SPAN] degrees. Anything
 *   wider is held [MARGIN] of the width from the edge. So the arch is only stretched across between the two real points:
 *   narrow in Dubai in December (sunrise about 66° from the south), wide in June (about 117°).
 * - The Sun moves along it evenly in time from sunrise to sunset.
 * - Under the horizon, a fixed shallow dip: Fajr and Isha at the x of their real azimuths (on the same scale, at least
 *   [GAP] of the width outside sunrise and sunset), the depth growing smoothly to [dip] at the edges of the screen, where
 *   deep night is carried, as in the real path.
 * - **Polar day** (no sunrise or sunset, the Sun up): the arch takes the whole day, its ends at the margins at midnight
 *   (the low midnight Sun on the horizon). **Polar night** (no sunrise or sunset, the Sun down): no arch; the whole day
 *   in the dip, highest (a third of the dip down) at noon in the middle. One of the two missing (a day the Sun rises but
 *   does not set before midnight): the other mirrored about noon.
 */
internal class SimpleArch(scene: SkyScene, private val w: Float, private val horizonY: Float, arcTopY: Float, groundY: Float) {
    private val p = scene.panorama
    val travel = p.travel
    private val height = max(horizonY - arcTopY, 0f)
    /** The deepest the dip goes (px under the horizon), at the edges of the screen. */
    val dip: Float = if (groundY - horizonY > 1f) 0.5f * (groundY - horizonY) else 0.3f * height
    private val kA = 0.5f * w / HALF_SPAN.toFloat()
    /** Screen x of an azimuth [a] from the noon Sun's (degrees), on the fixed scale, held within the margins. */
    fun xOfAcross(a: Double): Float = (w / 2 + kA * a).toFloat().coerceIn(MARGIN * w, (1 - MARGIN) * w)

    private val f: Map<Prayer, Double> = scene.events.associate { (pr, t) -> pr to scene.path.fraction(t) }
    private val noon = scene.noonFraction
    private val up = scene.path.maxElevation > -0.8333
    /** Sunrise and sunset (fractions of the day; outside 0..1 in a polar day), and whether there is an arch at all. */
    val rise: Double; val set: Double; val arch: Boolean
    /** The Sun up all day (no sunrise or sunset): outside the arch's day it stays on the horizon. */
    private val polarDay = up && f[Prayer.SHURUQ] == null && f[Prayer.MAGHRIB] == null && scene.path.minElevation > -0.8333
    private val xRise: Float; private val xSet: Float
    init {
        val r = f[Prayer.SHURUQ]?.takeIf { it < noon }; val s = f[Prayer.MAGHRIB]?.takeIf { it > noon }
        val polar = up && r == null && s == null
        val start = if (travel > 0) MARGIN * w else (1 - MARGIN) * w
        val end = if (travel > 0) (1 - MARGIN) * w else MARGIN * w
        when {
            r != null && s != null -> { rise = r; set = s; arch = true; xRise = xOfAcross(p.acrossAt(r)); xSet = xOfAcross(p.acrossAt(s)) }
            r != null -> { rise = r; set = 2 * noon - r; arch = true; xRise = xOfAcross(p.acrossAt(r)); xSet = w - xRise }
            s != null -> { rise = 2 * noon - s; set = s; arch = true; xSet = xOfAcross(p.acrossAt(s)); xRise = w - xSet }
            polar -> { rise = noon - 0.5; set = noon + 0.5; arch = true; xRise = start; xSet = end }
            else -> { rise = noon; set = noon; arch = false; xRise = w / 2; xSet = w / 2 }
        }
    }
    private val edgeFrom = if (travel > 0) -EDGE * w else (1 + EDGE) * w
    private val edgeTo = if (travel > 0) (1 + EDGE) * w else -EDGE * w
    /** Fajr's and Isha's x: their real azimuths, at least [GAP] outside sunrise and sunset (the way the Sun goes). */
    private fun outside(x: Float, from: Float, dir: Int): Float {
        val gap = GAP * w
        val held = if ((x - from) * dir < gap) from + dir * gap else x
        return held.coerceIn(0.01f * w, 0.99f * w)
    }
    // The night's parts: monotone curves (in the direction of travel) through midnight at the edge, Fajr, sunrise.
    private val morning: ((Double) -> Double)?
    private val evening: ((Double) -> Double)?
    init {
        fun curve(pts: List<Pair<Double, Float>>): (Double) -> Double {
            val xs = pts.map { it.first }.toDoubleArray(); val ys = pts.map { travel * it.second.toDouble() }.toDoubleArray()
            val c = monotoneCubic(xs, ys)
            return { t -> travel * c(t) }
        }
        val fajr = f[Prayer.FAJR]?.takeIf { it < rise - 1e-4 }
        val isha = f[Prayer.ISHA]?.takeIf { it > set + 1e-4 }
        val xFajr = fajr?.let { outside(xOfAcross(p.acrossAt(it.coerceIn(0.0, 1.0))), xRise, -travel) }
        val xIsha = isha?.let { outside(xOfAcross(p.acrossAt(it.coerceIn(0.0, 1.0))), xSet, travel) }
        // (Fajr before midnight or Isha after it, in a high summer: the night's part ends at its mark, not at the edge.)
        morning = if (rise > 1e-4) curve(
            if (fajr != null && fajr <= 1e-4) listOf(0.0 to xFajr!!, rise to xRise)
            else listOfNotNull(0.0 to edgeFrom, fajr?.let { it to xFajr!! }, rise to xRise)) else null
        evening = if (set < 1 - 1e-4) curve(
            if (isha != null && isha >= 1 - 1e-4) listOf(set to xSet, 1.0 to xIsha!!)
            else listOfNotNull(set to xSet, isha?.let { it to xIsha!! }, 1.0 to edgeTo)) else null
    }

    /** Screen x of the Sun at [fraction] of the day. */
    fun x(fraction: Double): Float {
        val t = fraction.coerceIn(0.0, 1.0)
        return when {
            t < rise -> morning?.invoke(t)?.toFloat() ?: xRise
            t > set -> evening?.invoke(t)?.toFloat() ?: xSet
            set - rise < 1e-9 -> xRise
            else -> xRise + (xSet - xRise) * ((t - rise) / (set - rise)).toFloat()
        }
    }

    /** Screen y of the Sun at [fraction] of the day: the arch by day, the dip by night. */
    fun y(fraction: Double): Float {
        val t = fraction.coerceIn(0.0, 1.0)
        if (!arch) {
            // Polar night: the whole day in the dip, highest at noon in the middle.
            val d = min(1f, abs(x(t) - w / 2) / (0.5f * w))
            return horizonY + dip * (1f / 3 + 2f / 3 * sin(PI / 2 * d).toFloat())
        }
        if (t in rise..set) return horizonY - height * sin(PI * (t - rise) / (set - rise)).toFloat()
        if (polarDay) return horizonY
        val (from, edge) = if (t < rise) xRise to edgeFrom else xSet to edgeTo
        val d = (abs(x(t) - from) / max(abs(edge - from), 1f)).coerceIn(0f, 1f)
        return horizonY + dip * sin(PI / 2 * d).toFloat()
    }

    companion object {
        /** Degrees of azimuth across half the screen's width: the widest ordinary day fits (Dubai's June, ±117°). */
        const val HALF_SPAN = 135.0
        const val MARGIN = 0.03f
        const val EDGE = 0.04f
        const val GAP = 0.03f

        /** Monotone cubic (Fritsch–Carlson) through ([x], [y]), [x] and [y] increasing. */
        fun monotoneCubic(x: DoubleArray, y: DoubleArray): (Double) -> Double {
            val n = x.size
            if (n < 2) return { y.firstOrNull() ?: 0.0 }
            val d = DoubleArray(n - 1) { (y[it + 1] - y[it]) / (x[it + 1] - x[it]) }
            val m = DoubleArray(n) { i -> when (i) { 0 -> d[0]; n - 1 -> d[n - 2]; else -> if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2 } }
            for (i in 0 until n - 1) {
                if (d[i] == 0.0) { m[i] = 0.0; m[i + 1] = 0.0; continue }
                val a = m[i] / d[i]; val b = m[i + 1] / d[i]; val s = a * a + b * b
                if (s > 9) { val k = 3 / kotlin.math.sqrt(s); m[i] = k * a * d[i]; m[i + 1] = k * b * d[i] }
            }
            return { v ->
                var i = 0
                while (i < n - 2 && x[i + 1] <= v) i++
                val h = x[i + 1] - x[i]; val t = ((v - x[i]) / h).coerceIn(0.0, 1.0)
                val t2 = t * t; val t3 = t2 * t
                (2 * t3 - 3 * t2 + 1) * y[i] + (t3 - 2 * t2 + t) * h * m[i] + (-2 * t3 + 3 * t2) * y[i + 1] + (t3 - t2) * h * m[i + 1]
            }
        }
    }
}
