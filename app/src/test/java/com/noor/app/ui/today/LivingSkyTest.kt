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
     * (0.20) The Sun's path is one even arch, the same over every scenery (the owner, 17 November 2026: part of it
     * hidden behind mountains and no consistency between the sceneries; until 0.19 it was lifted onto each scenery's
     * skyline). Its height is in proportion to the sine of the Sun's elevation and the time runs evenly between sunrise
     * and sunset, so it is symmetrical about the Sun's highest point and round at the top (in proportion to the
     * elevation it met itself at noon in a point).
     */
    @Test fun theSunsPathIsOneEvenArchOverEveryScenery() = listOf(false, true).forEach { dial ->
        // (0.23: the same with the sky dial, the arch standing on its baseline over the land.)
        val g = SkyGeometry(scene, 1080f, 900f, 700f, 1100f, rtl = false, dial = dial)
        // The same path whatever the scenery: the geometry knows nothing of the land.
        for (t in Terrain.entries) {
            val other = SkyGeometry(scene.copy(terrain = t), 1080f, 900f, 700f, 1100f, rtl = false, dial = dial)
            var f = 0.0
            while (f <= 1.0) { assertEquals(g.x(f), other.x(f), 0f); assertEquals(g.y(scene.elevationAt(f), f), other.y(scene.elevationAt(f), f), 0f); f += 0.05 }
        }
        val rise = path.fraction(at(6, 25)); val set = path.fraction(at(17, 40))
        val noon = (rise + set) / 2
        fun pt(f: Double) = g.x(f) to g.y(scene.elevationAt(f), f)
        val top = pt(noon)
        // Symmetrical about the middle of the day, in height and in width.
        var dt = 0.01
        while (noon - dt > rise) {
            val (xa, ya) = pt(noon - dt); val (xb, yb) = pt(noon + dt)
            assertEquals("height at ±$dt", ya, yb, 2.5f)
            assertEquals("width at ±$dt", top.first - xa, xb - top.first, 2.5f)
            dt += 0.02
        }
        // Round at the top: a quarter of an hour either side of noon it has come down less than 1 % of its height.
        val (_, yq) = pt(noon + 15.0 / 1440)
        assertTrue("${yq - top.second} px", yq - top.second < 0.01f * 200f)
        // Rising all morning and falling all afternoon, smoothly (no step larger than a few pixels in five minutes).
        var f = rise; var last = pt(f).second
        while (f < set) {
            val y = pt(f).second
            if (f < noon - 0.002) assertTrue("rising at $f", y <= last + 0.01f) else if (f > noon + 0.002) assertTrue("falling at $f", y >= last - 0.01f)
            assertTrue(abs(y - last) < 6f)
            last = y; f += 5.0 / 1440
        }
    }

    /** (0.20) The real sky's skyline gives the top of the land at a point of the screen, mirrored as the renderer draws it. */
    @Test fun theSkylineGivesTheTopOfTheLand() {
        val sky = FloatArray(360) { if (it < 180) 0.1f else 0.0f }   // land on the picture's first half only
        // Left to right, the picture's first strip is on the right of the screen: land on the right.
        assertEquals(500f - 0.1f * SunLift.FOCAL * 1000f, SunLift.skylineY(sky, 900f, 1000f, 500f, rtl = false), 0.01f)
        assertEquals(500f, SunLift.skylineY(sky, 100f, 1000f, 500f, rtl = false), 0.01f)
        // In Arabic it is not mirrored: land on the left.
        assertEquals(500f - 0.1f * SunLift.FOCAL * 1000f, SunLift.skylineY(sky, 100f, 1000f, 500f, rtl = true), 0.01f)
        // No skyline: the level horizon.
        assertEquals(500f, SunLift.skylineY(null, 300f, 1000f, 500f, rtl = false), 0f)
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
