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
