package com.noor.app.ui.today

import com.noor.prayer.GeoPoint
import com.noor.prayer.Prayer
import com.noor.prayer.astro.SunPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/** The home screen's scene logic: which land a place gets, and the geometry the drag-the-sun gesture relies on. */
class LivingSkyTest {
    private val dubai = ZoneId.of("Asia/Dubai")
    private val date = LocalDate.of(2026, 11, 4)
    private val path = SunPath.of(date, GeoPoint(25.2048, 55.2708), dubai)

    private fun at(h: Int, m: Int) = ZonedDateTime.of(date.atTime(h, m), dubai).toInstant()

    private val scene = SkyScene(
        path,
        events = listOf(Prayer.FAJR to at(5, 9), Prayer.SHURUQ to at(6, 25), Prayer.DHUHR to at(12, 5), Prayer.ASR to at(15, 14), Prayer.MAGHRIB to at(17, 40), Prayer.ISHA to at(18, 55)),
        now = at(15, 29), next = Prayer.MAGHRIB, hijriDay = 24, southernHemisphere = false, terrain = Terrain.DUNES,
    )

    @Test fun aridCountriesGetDunesAndOthersHills() {
        listOf("AE", "ae", "SA", "QA", "OM", "EG", "MA").forEach { assertEquals(it, Terrain.DUNES, Terrain.of(it)) }
        listOf("TR", "GB", "MY", "SG", "ID", "PS", "LB").forEach { assertEquals(it, Terrain.HILLS, Terrain.of(it)) }
        assertEquals(Terrain.HILLS, Terrain.of(null))
    }

    /** Dragging finds the moment under the finger: the inverse of the time axis, in both reading directions. */
    @Test fun fractionAtInvertsTheTimeAxis() {
        for (rtl in listOf(false, true)) {
            val g = SkyGeometry(scene, 1080f, 900f, 700f, 1100f, rtl)
            var f = 0.02
            while (f < 0.98) {
                assertEquals("rtl=$rtl f=$f", f, g.fractionAt(g.x(f)), 1e-4)
                f += 0.01
            }
        }
    }

    @Test fun timeAxisRunsInTheReadingDirection() {
        val ltr = SkyGeometry(scene, 1080f, 900f, 700f, 1100f, rtl = false)
        val rtl = SkyGeometry(scene, 1080f, 900f, 700f, 1100f, rtl = true)
        assertTrue(ltr.x(0.3) < ltr.x(0.7))
        assertTrue(rtl.x(0.3) > rtl.x(0.7))
    }

    /**
     * Over a scenery with a skyline of its own (0.11) the Sun's path stands on the land: the Sun meets the skyline, not
     * the level horizon, exactly at Shuruq and at Maghrib, so no scenery has to be cut open for it and the times stay
     * exact to the eye. Noon stays where it was; without a skyline nothing changes.
     */
    @Test fun theSunsPathStandsOnTheSkylineOfAScenery() {
        val flat = SkyGeometry(scene, 1080f, 900f, 700f, 1100f, rtl = false)
        val g = SkyGeometry(scene, 1080f, 900f, 700f, 1100f, rtl = false, riseLift = 60f, setLift = 25f)
        val rise = path.fraction(at(6, 25)); val set = path.fraction(at(17, 40))
        assertEquals(60f, g.lift(rise), 1.5f); assertEquals(25f, g.lift(set), 1.5f)
        // The Sun is on the skyline at Shuruq and at Maghrib (as it was on the horizon line before).
        assertEquals(flat.y(scene.elevationAt(rise)) - 60f, g.y(scene.elevationAt(rise), rise), 2f)
        assertEquals(flat.y(scene.elevationAt(set)) - 25f, g.y(scene.elevationAt(set), set), 2f)
        // Before Shuruq and after Maghrib it is under the skyline; between them over it, all day.
        assertTrue(g.y(scene.elevationAt(rise - 0.01), rise - 0.01) > 900f - g.lift(rise - 0.01))
        assertTrue(g.y(scene.elevationAt(set + 0.01), set + 0.01) > 900f - g.lift(set + 0.01))
        var f = rise + 0.004
        while (f < set - 0.004) {
            assertTrue("above the land at $f", g.y(scene.elevationAt(f), f) < 900f - g.lift(f) + 1f)
            f += 0.004
        }
        // The top of the path does not move, and the path passes smoothly from one skyline to the other.
        assertEquals(700f, g.y(path.maxElevation.coerceAtLeast(12.0), scene.noonFraction), 0.5f)
        var last = g.lift(0.0); f = 0.0
        while (f <= 1.0) { assertTrue(abs(g.lift(f) - last) < 2.5f); last = g.lift(f); f += 0.002 }
        // No skyline: the path is the one it always was.
        f = 0.0
        while (f <= 1.0) { assertEquals(flat.y(scene.elevationAt(f)), flat.y(scene.elevationAt(f), f), 0f); f += 0.05 }
        // A skyline never leaves less than a quarter of the arch.
        assertTrue(SkyGeometry(scene, 1080f, 900f, 700f, 1100f, false, 500f, 500f).lift(rise) <= 200f * 0.72f + 0.01f)
    }

    /** Sunrise and sunset sit on the horizon line; noon is the highest point drawn. */
    @Test fun elevationScalePutsTheHorizonWhereTheSunRisesAndSets() {
        val g = SkyGeometry(scene, 1080f, 900f, 700f, 1100f, rtl = false)
        val rise = path.fraction(at(6, 25))
        assertTrue(abs(g.y(scene.elevationAt(rise)) - 900f) < 12f)
        val noon = scene.noonFraction
        assertTrue(g.y(scene.elevationAt(noon)) <= g.y(scene.elevationAt(0.4)))
        assertEquals(700f, g.y(path.maxElevation.coerceAtLeast(12.0)), 0.5f)
    }

    /** The binary search used while drawing gives the same curve as reading the samples one by one. */
    @Test fun interpolationMatchesTheSamples() {
        val samples = path.samples
        samples.forEach { (f, el) -> assertEquals(el, interpolate(samples, f), 1e-9) }
        val i = samples.size / 3
        val mid = (samples[i].first + samples[i + 1].first) / 2
        assertEquals((samples[i].second + samples[i + 1].second) / 2, interpolate(samples, mid), 1e-9)
    }

    /** The scrubbed moment reads back as a clock time on the right day. */
    @Test fun instantAtMapsFractionsToClockTimes() {
        val noonish = scene.instantAt(path.fraction(at(12, 5)))
        assertTrue(abs(noonish.epochSecond - at(12, 5).epochSecond) <= 1)
        assertEquals(date, scene.instantAt(0.0).atZone(dubai).toLocalDate())
    }

    @Test fun lightFollowsTheSun() {
        val night = lightAt(-40.0, evening = true)
        val golden = lightAt(1.0, evening = true)
        val noon = lightAt(60.0, evening = false)
        assertEquals(1f, night.night, 1e-3f)
        assertTrue(golden.golden > 0.9f)
        assertTrue(noon.day > 0.99f && noon.golden < 0.01f && noon.night < 0.01f)
        assertTrue("dawn mist", lightAt(-1.0, evening = false).mist > lightAt(-1.0, evening = true).mist)
    }
}
