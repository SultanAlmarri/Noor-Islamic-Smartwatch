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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

/**
 * (0.23) The default Sun's path ([SimpleArch]): the same shape everywhere and every day (a half sine from sunrise to
 * sunset, its top at the arch's top at the middle of the day), its ends where the Sun really rises and sets on one fixed
 * scale (half the width = 135° of azimuth from the noon Sun's; checked against the Astronomical Almanac, not the app's
 * SPA), the Sun moving evenly in time, the way it really moves (right to left facing north); dragging through the whole
 * day; polar day and polar night without NaN.
 */
class SunPathSimpleTest {
    private data class Case(val name: String, val point: GeoPoint, val zone: String, val date: LocalDate, val north: Boolean)

    private val cases = listOf(
        Case("Dubai", GeoPoint(25.2048, 55.2708), "Asia/Dubai", LocalDate.of(2026, 11, 18), false),
        Case("Dubai", GeoPoint(25.2048, 55.2708), "Asia/Dubai", LocalDate.of(2026, 6, 21), false),
        Case("Dubai", GeoPoint(25.2048, 55.2708), "Asia/Dubai", LocalDate.of(2026, 12, 21), false),
        Case("Makkah", GeoPoint(21.4225, 39.8262), "Asia/Riyadh", LocalDate.of(2026, 5, 27), false),
        Case("Makkah", GeoPoint(21.4225, 39.8262), "Asia/Riyadh", LocalDate.of(2026, 6, 21), true),
        Case("Sydney", GeoPoint(-33.8688, 151.2093), "Australia/Sydney", LocalDate.of(2026, 6, 21), true),
        Case("Sydney", GeoPoint(-33.8688, 151.2093), "Australia/Sydney", LocalDate.of(2026, 12, 21), true),
        Case("Oslo", GeoPoint(59.9139, 10.7522), "Europe/Oslo", LocalDate.of(2026, 6, 21), false),
        Case("Oslo", GeoPoint(59.9139, 10.7522), "Europe/Oslo", LocalDate.of(2026, 12, 21), false),
        Case("Tromso", GeoPoint(69.6492, 18.9553), "Europe/Oslo", LocalDate.of(2026, 6, 21), false),
        Case("Tromso", GeoPoint(69.6492, 18.9553), "Europe/Oslo", LocalDate.of(2026, 12, 21), false),
    )
    private val sizes = listOf(floatArrayOf(412f, 400f, 318f, 237f, 2.625f), floatArrayOf(360f, 252f, 202f, 150f, 2f))

    private fun scene(c: Case): SkyScene {
        val config = WatchConfig(point = c.point, zoneId = c.zone, placeEn = c.name, placeAr = null, fromGps = false,
            profileId = Profiles.MWL.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED, offsets = emptyMap(),
            hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L)
        val day = config.day(c.date)
        val events = Prayer.entries.mapNotNull { p -> day.instant(p)?.let { p to it } }
        val path = SunPath.of(c.date, c.point, ZoneId.of(c.zone))
        // (The default: realPath false.)
        return SkyScene(path, events, path.start.plusSeconds(12 * 3600), Prayer.DHUHR, 10, c.point.latitude < 0, Terrain.HILLS, 1L, 7L,
            latitude = c.point.latitude, longitude = c.point.longitude)
    }

    private fun almanacAz(t: Instant, lat: Double, lon: Double): Double {
        val r = PI / 180
        val n = t.epochSecond / 86400.0 + 2440587.5 - 2451545.0
        val g = (357.528 + 0.9856003 * n) * r
        val lam = (280.460 + 0.9856474 * n + 1.915 * sin(g) + 0.020 * sin(2 * g)) * r
        val eps = (23.439 - 0.0000004 * n) * r
        val ra = atan2(kotlin.math.cos(eps) * sin(lam), kotlin.math.cos(lam))
        val dec = kotlin.math.asin(sin(eps) * sin(lam))
        val h = ((18.697374558 + 24.06570982441908 * n) * 15.0 + lon) * r - ra
        val phi = lat * r
        val az = atan2(sin(h), kotlin.math.cos(h) * sin(phi) - kotlin.math.tan(dec) * kotlin.math.cos(phi)) / r + 180.0
        return ((az % 360) + 360) % 360
    }
    private fun wrap(a: Double): Double { var x = a % 360.0; if (x > 180) x -= 360; if (x <= -180) x += 360; return x }

    @Test fun theSameShapeEverywhereWithRealEnds() {
        val report = StringBuilder()
        for (c in cases) for (sz in sizes) {
            val s = scene(c)
            val d = sz[4]
            val w = sz[0] * d; val hy = sz[2] * d; val arc = sz[3] * d; val ground = sz[1] * d - 6 * d
            val g = SkyGeometry(s, w, hy, arc, ground)
            val label = "${c.name} ${c.date} ${sz[0]}dp"
            assertEquals("$label travel", if (c.north) -1 else 1, g.travel)
            val rise = s.events.firstOrNull { it.first == Prayer.SHURUQ }?.second
            val set = s.events.firstOrNull { it.first == Prayer.MAGHRIB }?.second
            fun yAt(f: Double) = g.y(s.elevationAt(f), f)
            if (rise != null && set != null) {
                val r = s.path.fraction(rise); val e = s.path.fraction(set)
                // The shape: a half sine of the day's progress, top at the arch's top, ends on the horizon.
                var u = 0.0
                while (u <= 1.0001) {
                    val f = r + u * (e - r)
                    assertEquals("$label shape at u=$u", sin(PI * u), ((hy - yAt(f)) / (hy - arc)).toDouble(), 1e-4)
                    // Evenly in time between the ends.
                    assertEquals("$label even at u=$u", g.x(r) + (g.x(e) - g.x(r)) * u.toFloat(), g.x(f), 0.05f)
                    u += 0.05
                }
                assertEquals(hy, yAt(r), 0.01f); assertEquals(hy, yAt(e), 0.01f)
                // The ends at the real azimuths, on the fixed scale (unless held at the margins).
                val facing = if (c.north) 0.0 else 180.0
                val kA = 0.5f * w / SimpleArch.HALF_SPAN.toFloat()
                for ((t, x) in listOf(rise to g.x(r), set to g.x(e))) {
                    val sky = wrap(almanacAz(t, c.point.latitude, c.point.longitude) - facing)
                    val want = w / 2 + kA * sky
                    if (want > SimpleArch.MARGIN * w && want < (1 - SimpleArch.MARGIN) * w) assertEquals("$label end at ${sky}°", sky, ((x - w / 2) / kA).toDouble(), 0.2)
                    else assertTrue("$label held at the margin", x <= SimpleArch.MARGIN * w + 0.5f || x >= (1 - SimpleArch.MARGIN) * w - 0.5f)
                }
                assertTrue("$label the Sun's way", (g.x(e) - g.x(r)) * g.travel > 0f)
                if (sz[1] == 400f) report.append(String.format("%-7s %s rise %.1f° set %.1f° from %s, width %.0f dp\n", c.name, c.date,
                    (g.x(r) - w / 2) / kA, (g.x(e) - w / 2) / kA, if (c.north) "north" else "south", abs(g.x(e) - g.x(r)) / d))
            }
            // The marks: Shuruq and Maghrib on the horizon, Fajr and Isha under it, on screen; Dhuhr near the top.
            for ((pr, t) in s.events) {
                val m = prayerMark(s, g, s.path.fraction(t))
                assertTrue("$label $pr on screen", m.x >= 0f && m.x <= w && m.y.isFinite())
                when (pr) {
                    Prayer.SHURUQ, Prayer.MAGHRIB -> assertEquals("$label $pr", hy, m.y, 0.01f)
                    Prayer.FAJR, Prayer.ISHA -> if (rise != null) assertTrue("$label $pr under the horizon", m.y > hy + 2 * d)
                    Prayer.DHUHR -> if (rise != null && set != null) assertTrue("$label Dhuhr near the top ${(m.y - arc) / d}dp", m.y - arc < 0.02f * (hy - arc))
                    else -> {}
                }
            }
            // Polar night: under the horizon all day; polar day: never under it.
            if (rise == null && set == null) {
                val upAllDay = s.path.minElevation > -0.8333
                var f = 0.0
                while (f <= 1.0) {
                    if (upAllDay) assertTrue("$label polar day at $f", yAt(f) <= hy + 0.01f)
                    else assertTrue("$label polar night at $f", yAt(f) > hy)
                    f += 0.01
                }
            }
            // Dragging the Sun's way through the whole day, minute by minute; no NaN, no jump.
            var lastX = g.dragX(0.0); var lastSun = g.x(0.0)
            var f = 1.0 / 1440
            while (f <= 1.0) {
                val dx = g.dragX(f); val sx = g.x(f); val sy = yAt(f)
                assertTrue("$label NaN at $f", dx.isFinite() && sx.isFinite() && sy.isFinite())
                assertTrue("$label drag runs the Sun's way at ${f * 24}h", (dx - lastX) * g.travel > 0f)
                assertTrue("$label jump at ${f * 24}h", abs(sx - lastSun) < 0.05f * w)
                assertEquals("$label drag inverse at ${f * 24}h", f, g.fractionAt(dx), 2e-4)
                lastX = dx; lastSun = sx; f += 1.0 / 1440
            }
        }
        println(report)
    }
}
