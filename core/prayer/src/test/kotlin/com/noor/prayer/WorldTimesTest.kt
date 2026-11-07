package com.noor.prayer

import com.noor.prayer.geo.TimeZoneMap
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 0.9.7: a place chosen anywhere (by coordinates, on the map, from the list) gets its own time zone from the offline
 * map, and its times are right in that zone's local time, daylight-saving changes included. Sunrise and sunset are
 * checked against an independent calculation (NOAA's solar equations, written here from scratch); a wrong zone, a
 * wrong daylight-saving offset or swapped coordinates would be off by an hour or more, a real error by minutes.
 */
class WorldTimesTest {
    private val tz by lazy { File("../../app/src/main/assets/tzmap.bin").inputStream().use { TimeZoneMap.read(it) } }

    private data class Spot(val name: String, val lat: Double, val lon: Double, val zone: String)

    private val spots = listOf(
        Spot("Dubai Marina", 25.0805, 55.1403, "Asia/Dubai"), Spot("Makkah", 21.4225, 39.8262, "Asia/Riyadh"),
        Spot("Cairo", 30.0444, 31.2357, "Africa/Cairo"), Spot("Casablanca", 33.5731, -7.5898, "Africa/Casablanca"),
        Spot("Istanbul", 41.0082, 28.9784, "Europe/Istanbul"), Spot("London", 51.5074, -0.1278, "Europe/London"),
        Spot("Paris", 48.8566, 2.3522, "Europe/Paris"), Spot("New York", 40.7128, -74.0060, "America/New_York"),
        Spot("Chicago", 41.8781, -87.6298, "America/Chicago"), Spot("Phoenix", 33.4484, -112.0740, "America/Phoenix"),
        Spot("Sydney", -33.8688, 151.2093, "Australia/Sydney"), Spot("Santiago", -33.4489, -70.6693, "America/Santiago"),
        Spot("Tehran", 35.6892, 51.3890, "Asia/Tehran"), Spot("Kathmandu", 27.7172, 85.3240, "Asia/Kathmandu"),
        Spot("Jakarta", -6.2088, 106.8456, "Asia/Jakarta"), Spot("Auckland", -36.8485, 174.7633, "Pacific/Auckland"),
        Spot("Honolulu", 21.3069, -157.8583, "Pacific/Honolulu"), Spot("Reykjavik", 64.1466, -21.9426, "Atlantic/Reykjavik"),
        Spot("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg"), Spot("Lahore", 31.5204, 74.3587, "Asia/Karachi"),
    )

    // Daylight-saving change days (2026) and the days around them, and a few ordinary days.
    private val dates = listOf("2026-01-15", "2026-03-07", "2026-03-08", "2026-03-09", "2026-03-28", "2026-03-29", "2026-03-30",
        "2026-04-04", "2026-04-05", "2026-04-06", "2026-06-21", "2026-09-26", "2026-09-27", "2026-10-03", "2026-10-04",
        "2026-10-24", "2026-10-25", "2026-10-26", "2026-10-31", "2026-11-01", "2026-11-02", "2026-12-21").map(LocalDate::parse)

    @Test fun sunriseAndSunsetInTheRightLocalTimeEverywhere() {
        var checked = 0
        for (s in spots) {
            val zoneId = tz.zoneAt(s.lat, s.lon)
            assertEquals(s.zone, zoneId, s.name)
            val zone = ZoneId.of(zoneId)
            val point = GeoPoint(s.lat, s.lon)
            for (d in dates) {
                val day = PrayerEngine.compute(d, point, zone, Profiles.MWL)
                for ((p, rising) in listOf(Prayer.SHURUQ to true, Prayer.MAGHRIB to false)) {
                    val ref = noaa(d, s.lat, s.lon, rising) ?: continue
                    val got = day.instant(p) ?: continue
                    val diff = kotlin.math.abs(got.epochSecond - ref.epochSecond)
                    assertTrue(diff <= 120, "${s.name} $d $p: Noor ${got.atZone(zone).toLocalTime()} vs reference ${ref.atZone(zone).toLocalTime()} (${diff}s)")
                    // And the local clock time shown is that instant in the place's own zone, with that day's offset.
                    val local = got.atZone(zone)
                    assertEquals(zone.rules.getOffset(got), local.offset)
                    checked++
                }
            }
        }
        assertTrue(checked > 800, "checked $checked")
    }

    @Test fun dhuhrMovesSmoothlyAcrossDaylightSavingChanges() {
        // Solar noon moves by seconds from one day to the next; on a change day the clock jumps an hour, the prayer does not.
        for (s in spots) {
            val zone = ZoneId.of(tz.zoneAt(s.lat, s.lon))
            val point = GeoPoint(s.lat, s.lon)
            var prev: Instant? = null
            var d = LocalDate.parse("2026-01-01")
            while (d.year == 2026) {
                val at = PrayerEngine.compute(d, point, zone, Profiles.MWL).instant(Prayer.DHUHR)!!
                prev?.let { p ->
                    val step = at.epochSecond - p.epochSecond - 86400
                    assertTrue(kotlin.math.abs(step) <= 90, "${s.name} $d: Dhuhr moved ${step}s in a day")
                }
                prev = at
                d = d.plusDays(1)
            }
        }
    }

    /** NOAA's sunrise/sunset (90.833° zenith), from the published spreadsheet's equations; null when the Sun does not rise or set. */
    private fun noaa(date: LocalDate, lat: Double, lon: Double, rising: Boolean): Instant? {
        var minutes = 720.0 - 4 * lon
        repeat(3) {
            val jd = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond() / 86400.0 + 2440587.5 + minutes / 1440.0
            val jc = (jd - 2451545.0) / 36525.0
            val l0 = (280.46646 + jc * (36000.76983 + jc * 0.0003032)).mod(360.0)
            val m = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
            val e = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
            val c = sin(rad(m)) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * jc) + sin(rad(3 * m)) * 0.000289
            val app = l0 + c - 0.00569 - 0.00478 * sin(rad(125.04 - 1934.136 * jc))
            val obl = 23 + (26 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60) / 60 + 0.00256 * cos(rad(125.04 - 1934.136 * jc))
            val decl = deg(asin(sin(rad(obl)) * sin(rad(app))))
            val y = tan(rad(obl / 2)).let { it * it }
            val eqt = 4 * deg(y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)))
            val cosH = cos(rad(90.833)) / (cos(rad(lat)) * cos(rad(decl))) - tan(rad(lat)) * tan(rad(decl))
            if (cosH < -1 || cosH > 1) return null
            val ha = deg(acos(cosH))
            val noon = 720 - 4 * lon - eqt
            minutes = if (rising) noon - 4 * ha else noon + 4 * ha
        }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().plusMillis((minutes * 60_000).toLong())
    }

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
}
