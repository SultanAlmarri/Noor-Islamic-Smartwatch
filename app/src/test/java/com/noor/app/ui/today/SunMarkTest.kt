package com.noor.app.ui.today

import com.noor.prayer.GeoPoint
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.astro.SunPath
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The prayer marks on the sun path (0.9.7) are where the Sun really is at each prayer: Shuruq and Maghrib on the
 * horizon, with the land open there so the Sun meets the horizon at that very point; Fajr and Isha below the horizon.
 * Checked for places from the equator to 64° N, both seasons, a large and a small phone, both renderers' land.
 */
class SunMarkTest {
    private data class Place(val name: String, val point: GeoPoint, val zone: String, val terrain: Terrain)
    private val places = listOf(
        Place("Dubai", GeoPoint(25.2048, 55.2708), "Asia/Dubai", Terrain.DUNES),
        Place("Istanbul", GeoPoint(41.0082, 28.9784), "Europe/Istanbul", Terrain.HILLS),
        Place("London", GeoPoint(51.5072, -0.1276), "Europe/London", Terrain.HILLS),
        Place("Jakarta", GeoPoint(-6.2088, 106.8456), "Asia/Jakarta", Terrain.HILLS),
        Place("Reykjavik", GeoPoint(64.1466, -21.9426), "Atlantic/Reykjavik", Terrain.HILLS),
    )
    private val dates = listOf(LocalDate.of(2026, 12, 21), LocalDate.of(2027, 3, 20), LocalDate.of(2027, 6, 21))
    /** (width, header height, horizon, arc top) in dp, at a density. */
    private val sizes = listOf(floatArrayOf(412f, 400f, 318f, 237f, 2.625f), floatArrayOf(360f, 252f, 202f, 150f, 2f))

    @Test fun marksSitWhereTheSunIs() {
        var checked = 0
        for (pl in places) for (date in dates) for (sz in sizes) {
            val zone = ZoneId.of(pl.zone)
            val config = WatchConfig(point = pl.point, zoneId = pl.zone, placeEn = pl.name, placeAr = null, fromGps = false,
                profileId = Profiles.MWL.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED, offsets = emptyMap(),
                hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L)
            val day = config.day(date)
            val events = Prayer.entries.mapNotNull { p -> day.instant(p)?.let { p to it } }
            val path = SunPath.of(date, pl.point, zone)
            val scene = SkyScene(path, events, events.first().second, Prayer.DHUHR, 10, pl.point.latitude < 0, pl.terrain, 1L, 77L)
            val d = sz[4]
            val w = sz[0] * d; val h = sz[1] * d; val hy = sz[2] * d; val arc = sz[3] * d
            val g = SkyGeometry(scene, w, hy, arc, h - 6 * d, rtl = false)
            val (riseX, setX) = sunriseSunsetX(scene, g)
            val land = LandProfile.of(scene.landSeed, scene.terrain)
            val geo = land.geometry(hy, h, d)
            val label = "${pl.name} $date ${sz[0]}dp"
            for ((p, t) in events) {
                val m = prayerMark(scene, g, path.fraction(t))
                when (p) {
                    Prayer.SHURUQ, Prayer.MAGHRIB -> {
                        assertTrue("$label $p mark ${(m.y - hy) / d} dp from the horizon", abs(m.y - hy) <= 0.6f * d)
                        // The land does not stand above the horizon where the Sun meets it.
                        val top = land.skylineAt(m.x, w, geo, hy, d, riseX, setX)
                        assertTrue("$label $p land ${(hy - top) / d} dp above the horizon", top >= hy - 0.5f * d)
                        // The path itself crosses the horizon at the mark (within the mark's own size).
                        var f = path.fraction(t); val step = 1.0 / 1440 / 4
                        val dir = if (p == Prayer.SHURUQ) 1 else -1
                        while (g.y(scene.elevationAt(f)) > hy) f += dir * step
                        assertTrue("$label $p path meets the horizon ${(g.x(f) - m.x) / d} dp from the mark", abs(g.x(f) - m.x) <= 1.2f * d)
                    }
                    // Below the horizon, at the Sun's actual depth (a few degrees in a high-latitude summer, where Fajr
                    // and Isha come from the high-latitude rule; 15–20° elsewhere).
                    Prayer.FAJR, Prayer.ISHA -> {
                        // (Near midsummer far north, the high-latitude rule can put Isha minutes after sunset, with the
                        // Sun's top still on the horizon: the mark is then on the horizon, where the Sun is.)
                        val el = scene.elevationAt(path.fraction(t))
                        if (el < -1.5) assertTrue("$label $p mark ${(m.y - hy) / d} dp below the horizon", m.y >= hy + 1f * d)
                        if (el < -12.0) assertTrue("$label $p mark ${(m.y - hy) / d} dp below the horizon", m.y >= hy + 8f * d)
                        assertTrue("$label $p mark at the Sun", abs(m.y - g.y(el)) < 0.01f)
                    }
                    else -> assertTrue("$label $p mark in the sky", m.y < hy)
                }
                checked++
            }
        }
        assertTrue(checked > 100)
    }
}
