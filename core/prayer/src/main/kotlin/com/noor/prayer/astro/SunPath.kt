package com.noor.prayer.astro

import com.noor.prayer.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Sun's elevation through one civil day at one place, sampled for drawing (the Today screen's sky and sun path).
 * Elevations are topocentric, without refraction, from the same NREL SPA code the prayer engine uses.
 */
class SunPath private constructor(
    val date: LocalDate,
    /** Local midnight at the start and end of [date] (23 or 25 hours apart on daylight-saving change days). */
    val start: Instant,
    val end: Instant,
    private val point: GeoPoint,
    private val sampleSeconds: DoubleArray,
    private val elevations: DoubleArray,
) {
    val maxElevation: Double = elevations.max()
    val minElevation: Double = elevations.min()

    /** Samples as (fraction of the day 0..1, elevation in degrees). */
    val samples: List<Pair<Double, Double>> get() = sampleSeconds.indices.map { fractionOfSeconds(sampleSeconds[it]) to elevations[it] }

    /** Position of [t] within the day, 0 at the first midnight and 1 at the next. */
    fun fraction(t: Instant): Double = fractionOfSeconds(t.epochSecond.toDouble() + t.nano / 1e9)

    private fun fractionOfSeconds(s: Double): Double {
        val a = start.epochSecond.toDouble(); val b = end.epochSecond.toDouble()
        return (s - a) / (b - a)
    }

    /** Elevation at [t], computed exactly (not interpolated). */
    fun elevationAt(t: Instant): Double = elevation(t, point)

    companion object {
        /** Samples every [stepMinutes] from local midnight to the next. */
        fun of(date: LocalDate, point: GeoPoint, zone: ZoneId, stepMinutes: Int = 6): SunPath {
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val total = Duration.between(start, end).seconds
            val n = (total / (stepMinutes * 60)).toInt()
            val secs = DoubleArray(n + 1) { i -> start.epochSecond + minOf(total, i.toLong() * stepMinutes * 60).toDouble() }
            val dT = SolarPosition.deltaT(date.year)
            val els = DoubleArray(secs.size) { SolarPosition.elevation(secs[it], point.latitude, point.longitude, point.elevationM, dT) }
            return SunPath(date, start, end, point, secs, els)
        }

        fun elevation(t: Instant, point: GeoPoint): Double {
            val s = t.epochSecond + t.nano / 1e9
            val year = t.atZone(ZoneId.of("UTC")).year
            return SolarPosition.elevation(s, point.latitude, point.longitude, point.elevationM, SolarPosition.deltaT(year))
        }
    }
}
