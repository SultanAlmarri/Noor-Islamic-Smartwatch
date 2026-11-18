package com.noor.app.ui.today

import com.noor.prayer.Prayer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The Sun's real path across the sky for one place and one day (0.23), as a person sees it facing the Sun at noon, in
 * an equal-angle panorama: across, the Sun's azimuth measured from the noon Sun's (degrees, positive to the right of
 * the person); up, its elevation. The home screen draws it with one scale for both ([SkyGeometry]), so its shape is
 * the real one: in Dubai it leaves the horizon steeply, rounds over the south and comes down steeply; in a polar summer
 * it circles the whole sky, low.
 *
 * The person faces the side of the sky the Sun is on at noon: south in the northern mid-latitudes (the Sun moves left to
 * right), north in the southern hemisphere (right to left), and in the tropics whichever side the noon Sun is on that
 * day (north of the zenith at Makkah from late May to mid July). On the day the Sun passes through the zenith its
 * azimuth swings across the sky in minutes: within a degree of the zenith the azimuth means nothing, and there the
 * track goes evenly in time from where it was to where it will be, along the top of the picture (which is all one
 * point, the zenith); the direction of the swing is the direction the Sun moves that day.
 *
 * The scene is never mirrored, whatever the app's language (the owner, 18 November 2026: "make sure the sunpath reflects
 * real life exactly (regardless of app language setting)").
 */
internal class SunPanorama private constructor(
    /** The track: fractions of the day, true elevations (degrees), azimuths from the noon Sun's (degrees, unwrapped). */
    val fractions: DoubleArray,
    val elevations: DoubleArray,
    val across: DoubleArray,
    /** The person faces north (the noon Sun is on the north side of the zenith). */
    val facingNorth: Boolean,
    /** +1: the Sun moves from left to right across the sky that day (facing south); -1: right to left. */
    val travel: Int,
    /** The part of the day the screen shows exactly: Fajr to Isha (or sunrise and sunset an hour out, or the whole day). */
    val start: Double,
    val end: Double,
) {
    /** Azimuth from the noon Sun's at [f] (degrees; interpolated in time along the track). */
    fun acrossAt(f: Double): Double = lerpAt(across, f)
    fun elevationAt(f: Double): Double = lerpAt(elevations, f)

    private fun lerpAt(v: DoubleArray, f: Double): Double {
        val n = fractions.size
        if (f <= fractions[0]) return v[0]
        if (f >= fractions[n - 1]) return v[n - 1]
        var lo = 0; var hi = n - 1
        while (hi - lo > 1) { val m = (lo + hi) ushr 1; if (fractions[m] <= f) lo = m else hi = m }
        val t = (f - fractions[lo]) / (fractions[hi] - fractions[lo])
        return v[lo] + (v[hi] - v[lo]) * t
    }

    /** The widest the shown part of the day reaches to either side (degrees), and its highest and lowest elevations. */
    val acrossMin: Double; val acrossMax: Double; val highest: Double; val lowest: Double
    init {
        var a = min(acrossAt(start), acrossAt(end)); var b = max(acrossAt(start), acrossAt(end))
        var hi = max(elevationAt(start), elevationAt(end)); var lo = min(elevationAt(start), elevationAt(end))
        for (i in fractions.indices) if (fractions[i] in start..end) {
            a = min(a, across[i]); b = max(b, across[i]); hi = max(hi, elevations[i]); lo = min(lo, elevations[i])
        }
        acrossMin = a; acrossMax = b; highest = hi; lowest = lo
    }

    companion object {
        /** Within this of the zenith the azimuth is not used: the track goes straight on in time (see the class). */
        private const val NEAR_ZENITH = 89.0

        fun of(scene: SkyScene): SunPanorama {
            val tr = scene.path.track
            val n = tr.fractions.size
            val el = tr.elevations; val az = tr.azimuths
            val top = el.indices.maxByOrNull { el[it] } ?: 0
            // The side of the noon Sun: its azimuth at the highest point (on the meridian, 0 or 180), or, within a degree
            // of the zenith, the side its azimuth comes from an hour later (the side of the zenith it passes).
            val noonAz = if (el[top] < NEAR_ZENITH) az[top] else {
                val later = (top until n).firstOrNull { el[it] < NEAR_ZENITH - 4 } ?: (n - 1)
                val earlier = (top downTo 0).firstOrNull { el[it] < NEAR_ZENITH - 4 } ?: 0
                // East in the morning and west in the afternoon either way; the passage is north of the zenith when the
                // two lean to the north (their azimuths' cosines add up positive).
                if (cosd(az[later]) + cosd(az[earlier]) > 0) 0.0 else 180.0
            }
            val facingNorth = cosd(noonAz) > 0
            val facing = if (facingNorth) 0.0 else 180.0
            val travel = if (facingNorth) -1 else 1
            // Azimuth from the facing direction, positive to the right, unwrapped outward from noon. A step across the
            // zenith (more than 90 degrees between two samples) goes in the direction the Sun travels.
            val acr = DoubleArray(n) { Double.NaN }
            val ok = BooleanArray(n) { el[it] < NEAR_ZENITH }
            val first = (0 until n).filter { ok[it] }.minByOrNull { abs(it - top) } ?: top
            acr[first] = wrap(az[first] - facing)
            var last = first
            for (i in first + 1 until n) if (ok[i]) { acr[i] = acr[last] + step(wrap(az[i] - az[last]), travel); last = i }
            last = first
            for (i in first - 1 downTo 0) if (ok[i]) { acr[i] = acr[last] - step(wrap(az[last] - az[i]), travel); last = i }
            // Near the zenith: straight on in time between the samples either side.
            for (i in 0 until n) if (acr[i].isNaN()) {
                val a = (i downTo 0).firstOrNull { !acr[it].isNaN() }; val b = (i until n).firstOrNull { !acr[it].isNaN() }
                acr[i] = when {
                    a != null && b != null -> acr[a] + (acr[b] - acr[a]) * (tr.fractions[i] - tr.fractions[a]) / (tr.fractions[b] - tr.fractions[a])
                    a != null -> acr[a]
                    b != null -> acr[b]
                    else -> 0.0
                }
            }
            // What the screen shows exactly: Fajr to Isha; where one is missing, sunrise an hour before or sunset an hour
            // after; where those are missing too (polar day and night), the whole day.
            val f = scene.events.associate { (p, t) -> p to scene.path.fraction(t) }
            val noonF = tr.fractions[top]
            val hour = 1.0 / 24
            val s = (f[Prayer.FAJR] ?: f[Prayer.SHURUQ]?.let { it - hour })?.takeIf { it > 0.0 && it < noonF } ?: 0.0
            val e = (f[Prayer.ISHA] ?: f[Prayer.MAGHRIB]?.let { it + hour })?.takeIf { it < 1.0 && it > noonF } ?: 1.0
            return SunPanorama(tr.fractions, el, acr, facingNorth, travel, s, e)
        }

        private fun cosd(a: Double) = kotlin.math.cos(Math.toRadians(a))
        private fun wrap(a: Double): Double { var x = a % 360.0; if (x > 180) x -= 360; if (x <= -180) x += 360; return x }
        /** A step of azimuth [d] (wrapped) as the track takes it: across the zenith (> 90°), in the direction of travel. */
        private fun step(d: Double, travel: Int): Double = if (abs(d) > 90 && d * travel < 0) d + 360.0 * travel else d
    }
}
