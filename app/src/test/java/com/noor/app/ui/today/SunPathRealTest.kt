package com.noor.app.ui.today

import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.astro.SunPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * (0.23) The home screen's Sun path is the Sun's real path, drawn with true proportions, anywhere in the world (the
 * owner, 18 November 2026: "i want it to be exactly like real life anywhere in the world"). Checked against an
 * independent algorithm (the Astronomical Almanac's low-precision solar coordinates, not the app's SPA): every angle on
 * the screen, divided by the one scale, is the angle in the sky, across and up; Shuruq and Maghrib on the horizon; the
 * whole of Fajr..Isha on the screen; the Sun moving the way it really moves (left to right facing south, right to left
 * facing north); dragging through the whole day; no jump and no NaN on a zenith day, in a polar day or a polar night.
 */
class SunPathRealTest {
    private data class Case(val name: String, val point: GeoPoint, val zone: String, val date: LocalDate, val north: Boolean)

    private val cases = listOf(
        Case("Dubai", GeoPoint(25.2048, 55.2708), "Asia/Dubai", LocalDate.of(2026, 11, 18), false),
        Case("Dubai", GeoPoint(25.2048, 55.2708), "Asia/Dubai", LocalDate.of(2026, 6, 21), false),
        Case("Dubai", GeoPoint(25.2048, 55.2708), "Asia/Dubai", LocalDate.of(2026, 12, 21), false),
        Case("Makkah", GeoPoint(21.4225, 39.8262), "Asia/Riyadh", LocalDate.of(2026, 5, 27), false),
        Case("Makkah", GeoPoint(21.4225, 39.8262), "Asia/Riyadh", LocalDate.of(2026, 6, 21), true),
        Case("Makkah", GeoPoint(21.4225, 39.8262), "Asia/Riyadh", LocalDate.of(2026, 7, 15), true),
        Case("Jakarta", GeoPoint(-6.2088, 106.8456), "Asia/Jakarta", LocalDate.of(2026, 6, 21), true),
        Case("Jakarta", GeoPoint(-6.2088, 106.8456), "Asia/Jakarta", LocalDate.of(2026, 12, 21), false),
        Case("Nairobi", GeoPoint(-1.2921, 36.8219), "Africa/Nairobi", LocalDate.of(2026, 3, 20), true),
        Case("Nairobi", GeoPoint(-1.2921, 36.8219), "Africa/Nairobi", LocalDate.of(2026, 6, 21), true),
        Case("Sydney", GeoPoint(-33.8688, 151.2093), "Australia/Sydney", LocalDate.of(2026, 6, 21), true),
        Case("Sydney", GeoPoint(-33.8688, 151.2093), "Australia/Sydney", LocalDate.of(2026, 12, 21), true),
        Case("Johannesburg", GeoPoint(-26.2041, 28.0473), "Africa/Johannesburg", LocalDate.of(2026, 12, 21), true),
        Case("London", GeoPoint(51.5072, -0.1276), "Europe/London", LocalDate.of(2026, 6, 21), false),
        Case("Oslo", GeoPoint(59.9139, 10.7522), "Europe/Oslo", LocalDate.of(2026, 6, 21), false),
        Case("Oslo", GeoPoint(59.9139, 10.7522), "Europe/Oslo", LocalDate.of(2026, 12, 21), false),
        Case("Tromso", GeoPoint(69.6492, 18.9553), "Europe/Oslo", LocalDate.of(2026, 6, 21), false),
        Case("Tromso", GeoPoint(69.6492, 18.9553), "Europe/Oslo", LocalDate.of(2026, 12, 21), false),
        Case("Reykjavik", GeoPoint(64.1466, -21.9426), "Atlantic/Reykjavik", LocalDate.of(2026, 6, 21), false),
    )

    /** (width, header height, horizon, arc top, density): a common phone, a taller header, a small phone. */
    private val sizes = listOf(
        floatArrayOf(412f, 400f, 318f, 237f, 2.625f), floatArrayOf(412f, 457f, 375f, 237f, 2.625f), floatArrayOf(360f, 252f, 202f, 150f, 2f),
    )

    private fun scene(c: Case): SkyScene {
        val config = WatchConfig(point = c.point, zoneId = c.zone, placeEn = c.name, placeAr = null, fromGps = false,
            profileId = Profiles.MWL.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED, offsets = emptyMap(),
            hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L)
        val day = config.day(c.date)
        val events = Prayer.entries.mapNotNull { p -> day.instant(p)?.let { p to it } }
        val path = SunPath.of(c.date, c.point, ZoneId.of(c.zone))
        return SkyScene(path, events, path.start.plusSeconds(12 * 3600), Prayer.DHUHR, 10, c.point.latitude < 0, Terrain.HILLS, 1L, 7L,
            latitude = c.point.latitude, longitude = c.point.longitude)
    }

    /** The Astronomical Almanac's low-precision Sun (about 0.01° from 1950 to 2050): [elevation, azimuth from north]. */
    private fun almanac(t: Instant, lat: Double, lon: Double): DoubleArray {
        val r = Math.PI / 180
        val n = t.epochSecond / 86400.0 + 2440587.5 - 2451545.0
        val g = (357.528 + 0.9856003 * n) * r
        val lam = (280.460 + 0.9856474 * n + 1.915 * kotlin.math.sin(g) + 0.020 * kotlin.math.sin(2 * g)) * r
        val eps = (23.439 - 0.0000004 * n) * r
        val ra = atan2(kotlin.math.cos(eps) * kotlin.math.sin(lam), kotlin.math.cos(lam))
        val dec = kotlin.math.asin(kotlin.math.sin(eps) * kotlin.math.sin(lam))
        val h = ((18.697374558 + 24.06570982441908 * n) * 15.0 + lon) * r - ra
        val phi = lat * r
        val el = kotlin.math.asin(kotlin.math.sin(phi) * kotlin.math.sin(dec) + kotlin.math.cos(phi) * kotlin.math.cos(dec) * kotlin.math.cos(h))
        val az = atan2(kotlin.math.sin(h), kotlin.math.cos(h) * kotlin.math.sin(phi) - kotlin.math.tan(dec) * kotlin.math.cos(phi)) / r + 180.0
        return doubleArrayOf(el / r, ((az % 360) + 360) % 360)
    }

    private fun wrap(a: Double): Double { var x = a % 360.0; if (x > 180) x -= 360; if (x <= -180) x += 360; return x }

    @Test fun thePathIsTheRealPathWithTrueProportions() {
        val report = StringBuilder()
        for (c in cases) for (sz in sizes) {
            val s = scene(c)
            val d = sz[4]
            val w = sz[0] * d; val h = sz[1] * d; val hy = sz[2] * d; val arc = sz[3] * d; val ground = h - 6 * d
            val g = SkyGeometry(s, w, hy, arc, ground)
            val p = s.panorama
            val label = "${c.name} ${c.date} ${sz[0]}x${sz[1]}dp"
            assertEquals("$label faces north", c.north, g.facingNorth)
            assertEquals("$label travel", if (c.north) -1 else 1, g.travel)
            assertTrue("$label scale ${g.k}", g.k.isFinite() && g.k > 0f)
            val facing = if (g.facingNorth) 0.0 else 180.0
            // The point of reference: the track's sample nearest noon that is not within 1° of the zenith.
            val tr = s.path.track
            val ref = tr.fractions.indices.filter { tr.fractions[it] in p.start..p.end && tr.elevations[it] < 85 }
                .minByOrNull { abs(tr.fractions[it] - s.noonFraction) }!!
            fun inst(f: Double) = s.path.start.plusMillis(((s.path.end.toEpochMilli() - s.path.start.toEpochMilli()) * f).toLong())
            val r0 = almanac(inst(tr.fractions[ref]), c.point.latitude, c.point.longitude)
            val x0 = g.x(tr.fractions[ref])
            var checked = 0
            for (i in tr.fractions.indices) {
                val f = tr.fractions[i]
                if (f < p.start || f > p.end || tr.elevations[i] >= 85) continue
                val a = almanac(inst(f), c.point.latitude, c.point.longitude)
                // Across: the screen's angle from the reference point is the sky's (modulo a turn, in a polar day).
                val drawn = (g.x(f) - x0) / g.k
                val sky = wrap(a[1] - facing) - wrap(r0[1] - facing)
                assertEquals("$label across at ${f * 24}h", 0.0, wrap(drawn - sky), 0.12)
                // Up: the height over the horizon is the apparent elevation, on the same scale.
                val up = (hy - g.y(s.elevationAt(f), f)) / g.k
                assertEquals("$label up at ${f * 24}h", SkyGeometry.apparent(a[0]), up.toDouble(), 0.12)
                checked++
            }
            assertTrue("$label checked $checked", checked > 20)
            // The marks: Shuruq and Maghrib on the horizon, all of them on the screen, Fajr and Isha above its foot.
            for ((pr, t) in s.events) {
                val m = prayerMark(s, g, s.path.fraction(t))
                assertTrue("$label $pr x ${m.x / d}dp", m.x >= 0f && m.x <= w)
                if (pr == Prayer.SHURUQ || pr == Prayer.MAGHRIB) assertTrue("$label $pr ${(m.y - hy) / d}dp off the horizon", abs(m.y - hy) <= 0.6f * d)
                if (pr == Prayer.FAJR || pr == Prayer.ISHA) assertTrue("$label $pr below the foot", m.y <= ground + 0.5f)
                if (pr == Prayer.DHUHR) assertTrue("$label Dhuhr over the arch's top ${(arc - m.y) / d}dp", m.y >= arc - 1f)
            }
            // Dragging runs one way through the whole day, the way the Sun moves, and finds the moment under the finger;
            // nothing jumps (a minute's step stays under a fifth of the width, even across the zenith) and nothing is NaN.
            var lastX = g.dragX(0.0); var lastSun = g.x(0.0)
            var f = 1.0 / 1440
            while (f <= 1.0) {
                val dx = g.dragX(f); val sx = g.x(f); val sy = g.y(s.elevationAt(f), f)
                assertTrue("$label NaN at $f", dx.isFinite() && sx.isFinite() && sy.isFinite())
                assertTrue("$label drag runs the Sun's way at ${f * 24}h", (dx - lastX) * g.travel > 0f)
                assertTrue("$label jump at ${f * 24}h: ${abs(sx - lastSun) / w}", abs(sx - lastSun) < 0.2f * w)
                assertEquals("$label drag inverse at ${f * 24}h", f, g.fractionAt(dx), 2e-4)
                lastX = dx; lastSun = sx; f += 1.0 / 1440
            }
            if (sz[1] == 400f) {
                val rise = s.events.firstOrNull { it.first == Prayer.SHURUQ }?.second
                val set = s.events.firstOrNull { it.first == Prayer.MAGHRIB }?.second
                report.append(String.format("%-12s %s faces %s k=%.2f dp/deg span %.0f..%.0f deg noon %.1f deg rise az %s set az %s window %.2f-%.2fh\n",
                    c.name, c.date, if (g.facingNorth) "N" else "S", g.k / d, p.acrossMin, p.acrossMax, p.highest,
                    rise?.let { String.format("%.1f", s.path.azimuthAt(it)) } ?: "-", set?.let { String.format("%.1f", s.path.azimuthAt(it)) } ?: "-",
                    p.start * 24, p.end * 24))
            }
        }
        println(report)
    }

    /** Dubai, 18 November 2026: the path leaves the horizon steeply, as the real Sun does (about 90° − latitude). */
    @Test fun inDubaiThePathLeavesTheHorizonSteeply() {
        val s = scene(cases[0])
        val g = SkyGeometry(s, 412f * 2.625f, 318f * 2.625f, 237f * 2.625f, 394f * 2.625f)
        val rise = s.path.fraction(s.events.first { it.first == Prayer.SHURUQ }.second)
        val a = rise + 10.0 / 1440
        val angle = Math.toDegrees(atan2((g.y(s.elevationAt(rise)) - g.y(s.elevationAt(a))).toDouble(), (g.x(a) - g.x(rise)).toDouble()))
        assertTrue("angle to the horizon $angle", angle in 58.0..68.0)
        // June: nearly overhead at noon (about 88°).
        val june = scene(cases[1])
        assertTrue("June noon ${june.panorama.highest}", june.panorama.highest in 87.5..89.0)
        // The scale: the noon Sun reaches the top of the arch; across, the day fits the width.
        assertEquals(237f * 2.625f, g.y(s.path.maxElevation), 1f)
    }

    /** The land hides the Sun exactly where its skyline is higher than the Sun, softly at the edge, and nowhere else. */
    @Test fun theLandHidesTheSunOnlyWhereItIsHigher() {
        val disc = 10f
        assertEquals(0f, sunHidden(sunY = 400f, landTop = 450f, disc = disc, el = 20.0), 0f)      // the land well below the Sun
        assertEquals(1f, sunHidden(sunY = 400f, landTop = 380f, disc = disc, el = 20.0), 0f)      // the land well above it
        val edge = sunHidden(sunY = 400f, landTop = 400f, disc = disc, el = 20.0)
        assertTrue("half hidden at the edge: $edge", edge in 0.2f..0.8f)
        // Softly: no step larger than a tenth over a tenth of the disc.
        var last = sunHidden(400f, 420f, disc, 20.0); var top = 420f
        while (top > 380f) { top -= 1f; val v = sunHidden(400f, top, disc, 20.0); assertTrue(v - last < 0.1f); assertTrue(v >= last); last = v }
        // Under the horizon there is nothing to show.
        assertEquals(0f, sunHidden(400f, 300f, disc, -2.0), 0f)
    }
}
