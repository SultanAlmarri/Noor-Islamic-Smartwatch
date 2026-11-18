package com.noor.prayer

import com.noor.prayer.astro.SunPath
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SunPathTest {
    private val dubai = GeoPoint(25.2048, 55.2708, 5.0)
    private val zone = ZoneId.of("Asia/Dubai")

    @Test fun noonHeightMatchesGeometry() {
        // 4 Nov: declination about -15.4°, so the noon sun stands about 90 - 25.2 - 15.4 = 49.4° high.
        val p = SunPath.of(LocalDate.of(2026, 11, 4), dubai, zone)
        assertTrue(abs(p.maxElevation - 49.4) < 0.6, "max ${p.maxElevation}")
        assertTrue(p.minElevation < -60)
    }

    @Test fun prayerTimesSitWhereTheirDefinitionsSay() {
        val date = LocalDate.of(2026, 11, 4)
        val day = PrayerEngine.compute(date, dubai, zone, Profiles.DUBAI_IACAD)
        val p = SunPath.of(date, dubai, zone)
        // Sunrise and sunset: centre about 0.8° below the horizon (refraction + semidiameter), within a minute's motion.
        assertTrue(abs(p.elevationAt(day.instant(Prayer.SHURUQ)!!) + 0.8) < 0.9)
        // Fajr: the dawn angle (about 18°) below the horizon.
        assertTrue(abs(p.elevationAt(day.instant(Prayer.FAJR)!!) + 18.2) < 1.0, "fajr ${p.elevationAt(day.instant(Prayer.FAJR)!!)}")
        // Dhuhr: just after the highest point.
        assertTrue(p.maxElevation - p.elevationAt(day.instant(Prayer.DHUHR)!!) < 0.5)
    }

    @Test fun fractionsSpanTheCivilDayIncludingDaylightSavingDays() {
        val london = SunPath.of(LocalDate.of(2026, 10, 25), GeoPoint(51.5074, -0.1278), ZoneId.of("Europe/London"))
        assertEquals(25 * 3600L, london.end.epochSecond - london.start.epochSecond)
        assertEquals(0.0, london.samples.first().first, 1e-9)
        assertEquals(1.0, london.samples.last().first, 1e-9)
        assertEquals(0.5, london.fraction(london.start.plusSeconds(25 * 1800L)), 1e-9)
    }
}

/**
 * (0.23) The Sun's azimuth (for the home screen's true path) against an independent algorithm: the Astronomical
 * Almanac's low-precision solar coordinates (about 0.01° from 1950 to 2050), not SPA.
 */
class SunAzimuthTest {
    private fun almanac(t: java.time.Instant, lat: Double, lon: Double): DoubleArray {
        val r = Math.PI / 180
        val jd = t.epochSecond / 86400.0 + 2440587.5
        val n = jd - 2451545.0
        val l = 280.460 + 0.9856474 * n; val g = (357.528 + 0.9856003 * n) * r
        val lam = (l + 1.915 * kotlin.math.sin(g) + 0.020 * kotlin.math.sin(2 * g)) * r
        val eps = (23.439 - 0.0000004 * n) * r
        val ra = kotlin.math.atan2(kotlin.math.cos(eps) * kotlin.math.sin(lam), kotlin.math.cos(lam))
        val dec = kotlin.math.asin(kotlin.math.sin(eps) * kotlin.math.sin(lam))
        val gmst = (18.697374558 + 24.06570982441908 * n) * 15.0
        val h = (gmst + lon) * r - ra
        val phi = lat * r
        val el = kotlin.math.asin(kotlin.math.sin(phi) * kotlin.math.sin(dec) + kotlin.math.cos(phi) * kotlin.math.cos(dec) * kotlin.math.cos(h))
        val az = kotlin.math.atan2(kotlin.math.sin(h), kotlin.math.cos(h) * kotlin.math.sin(phi) - kotlin.math.tan(dec) * kotlin.math.cos(phi)) / r + 180.0
        return doubleArrayOf(el / r, ((az % 360) + 360) % 360)
    }

    @Test fun azimuthAndElevationAgreeWithTheAlmanac() {
        val places = listOf(
            Triple(GeoPoint(25.2048, 55.2708), "Asia/Dubai", LocalDate.of(2026, 11, 18)),
            Triple(GeoPoint(21.4225, 39.8262), "Asia/Riyadh", LocalDate.of(2026, 5, 27)),
            Triple(GeoPoint(-33.8688, 151.2093), "Australia/Sydney", LocalDate.of(2026, 12, 21)),
            Triple(GeoPoint(69.6492, 18.9553), "Europe/Oslo", LocalDate.of(2026, 6, 21)),
        )
        var n = 0
        for ((pt, zone, date) in places) {
            val path = SunPath.of(date, pt, ZoneId.of(zone))
            val tr = path.track
            for (i in tr.fractions.indices step 7) {
                val t = path.start.plusMillis(((path.end.toEpochMilli() - path.start.toEpochMilli()) * tr.fractions[i]).toLong())
                val ref = almanac(t, pt.latitude, pt.longitude)
                assertTrue(abs(tr.elevations[i] - ref[0]) < 0.05, "$zone $date el ${tr.elevations[i]} vs ${ref[0]}")
                if (tr.elevations[i] < 85) {
                    var d = tr.azimuths[i] - ref[1]; d -= 360 * Math.round(d / 360.0)
                    assertTrue(abs(d) < 0.08, "$zone $date az ${tr.azimuths[i]} vs ${ref[1]} at el ${tr.elevations[i]}")
                }
                // The track's elevation is the prayer engine's own.
                assertEquals(path.elevationAt(t), tr.elevations[i], 1e-4)   // (t to the millisecond)
                n++
            }
        }
        assertTrue(n > 100)
    }

    @Test fun dubaiSunriseAndNoon() {
        val zone = ZoneId.of("Asia/Dubai")
        val date = LocalDate.of(2026, 11, 18)
        val p = SunPath.of(date, GeoPoint(25.2048, 55.2708), zone)
        val noon = p.track.elevations.indices.maxByOrNull { p.track.elevations[it] }!!
        assertEquals(180.0, p.track.azimuths[noon], 1.5)          // due south at the highest point (a 6-minute sample)
        // At sunrise (centre at -0.833°) the Sun is a little south of east: about 112° (cos A = sin δ / cos φ, δ ≈ -19.2°).
        val rise = (0 until p.track.fractions.size - 1).first { p.track.elevations[it] < -0.833 && p.track.elevations[it + 1] >= -0.833 }
        assertEquals(111.9, p.track.azimuths[rise], 1.5)
    }
}
