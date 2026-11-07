package com.noor.prayer

import com.noor.prayer.geo.Coordinates
import com.noor.prayer.geo.TimeZoneMap
import com.noor.prayer.geo.WorldPlaces
import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 0.9.7 location data: the offline time-zone map (every IANA zone's boundaries), the offline world places, and
 * coordinates as people type and paste them. The data files are the ones the phone app ships.
 */
class LocationDataTest {
    private val assets = File("../../app/src/main/assets")
    private val tz by lazy { File(assets, "tzmap.bin").inputStream().use { TimeZoneMap.read(it) } }
    private val world by lazy { File(assets, "world.bin").inputStream().use { WorldPlaces.read(it) } }

    @Test fun timeZonesAcrossTheWorld() {
        val cases = listOf(
            Triple(25.2048, 55.2708, "Asia/Dubai"), Triple(24.7136, 46.6753, "Asia/Riyadh"), Triple(21.4225, 39.8262, "Asia/Riyadh"),
            Triple(29.3759, 47.9774, "Asia/Kuwait"), Triple(25.2854, 51.5310, "Asia/Qatar"), Triple(30.0444, 31.2357, "Africa/Cairo"),
            Triple(33.5731, -7.5898, "Africa/Casablanca"), Triple(41.0082, 28.9784, "Europe/Istanbul"), Triple(51.5074, -0.1278, "Europe/London"),
            Triple(40.7128, -74.0060, "America/New_York"), Triple(41.8781, -87.6298, "America/Chicago"), Triple(34.0522, -118.2437, "America/Los_Angeles"),
            Triple(33.4484, -112.0740, "America/Phoenix"), Triple(-33.8688, 151.2093, "Australia/Sydney"), Triple(-27.4698, 153.0251, "Australia/Brisbane"),
            Triple(-6.2088, 106.8456, "Asia/Jakarta"), Triple(-8.6705, 115.2126, "Asia/Makassar"), Triple(3.1390, 101.6869, "Asia/Kuala_Lumpur"),
            Triple(1.3521, 103.8198, "Asia/Singapore"), Triple(35.6892, 51.3890, "Asia/Tehran"), Triple(28.6139, 77.2090, "Asia/Kolkata"),
            Triple(27.7172, 85.3240, "Asia/Kathmandu"), Triple(64.1466, -21.9426, "Atlantic/Reykjavik"), Triple(55.7558, 37.6173, "Europe/Moscow"),
            Triple(43.2389, 76.8897, "Asia/Almaty"), Triple(-23.5505, -46.6333, "America/Sao_Paulo"), Triple(19.4326, -99.1332, "America/Mexico_City"),
            Triple(43.6532, -79.3832, "America/Toronto"), Triple(31.7683, 35.2137, "Asia/Jerusalem"), Triple(31.9038, 35.2034, "Asia/Hebron"),
            Triple(33.8938, 35.5018, "Asia/Beirut"), Triple(15.3694, 44.1910, "Asia/Aden"), Triple(23.5880, 58.3829, "Asia/Muscat"),
            Triple(26.2285, 50.5860, "Asia/Bahrain"), Triple(12.9716, 77.5946, "Asia/Kolkata"), Triple(24.8607, 67.0011, "Asia/Karachi"),
            Triple(23.8103, 90.4125, "Asia/Dhaka"), Triple(9.0765, 7.3986, "Africa/Lagos"), Triple(-1.2921, 36.8219, "Africa/Nairobi"),
            // Near borders: Dubai–Sharjah (same zone), El Paso (Mountain) and Ciudad Juárez, the Indiana–Illinois line.
            Triple(25.3070, 55.3810, "Asia/Dubai"), Triple(31.7619, -106.4850, "America/Denver"), Triple(41.5868, -87.5250, "America/Chicago"),
        )
        for ((lat, lon, zone) in cases) assertEquals(zone, tz.zoneAt(lat, lon), "$lat, $lon")
        // Open sea: nautical time by longitude.
        assertEquals("Etc/GMT+2", tz.zoneAt(30.0, -35.0))
        assertEquals("Etc/GMT-5", tz.zoneAt(-20.0, 75.0))
        assertEquals("Etc/GMT", TimeZoneMap.oceanZone(0.0))
        assertEquals("Etc/GMT-12", TimeZoneMap.oceanZone(179.9))
        assertEquals("Etc/GMT+12", TimeZoneMap.oceanZone(-179.9))
        // Every zone in the map is one Java knows (so java.time applies its daylight saving rules).
        val unknown = tz.zones.filter { runCatching { ZoneId.of(it) }.isFailure }
        assertTrue(unknown.isEmpty(), "unknown zones: $unknown")
    }

    @Test fun everyPlacesZoneAgreesWithTheMap() {
        // Sampled across the world: the zone GeoNames gives a place and the zone of its point on the map agree
        // (they may differ only where two zones keep the same clock, e.g. a link, or right on a border).
        var differ = 0; var checked = 0
        val samples = (0 until world.size step 97)
        for (i in samples) {
            val p = world.place(i)
            val z = tz.zoneAt(p.point.latitude, p.point.longitude)
            checked++
            if (z != p.zoneId) {
                val a = ZoneId.of(z).rules; val b = ZoneId.of(p.zoneId).rules
                val now = java.time.Instant.parse("2026-01-15T12:00:00Z"); val summer = java.time.Instant.parse("2026-07-15T12:00:00Z")
                if (a.getOffset(now) != b.getOffset(now) || a.getOffset(summer) != b.getOffset(summer)) differ++
            }
        }
        assertTrue(differ * 1000 < checked * 3, "$differ of $checked places would keep a different clock")
    }

    @Test fun browseCountryCityArea() {
        assertTrue(world.size > 350_000, "size ${world.size}")
        assertTrue(world.countries().size > 240, "countries ${world.countries().size}")
        val ae = world.cities("AE")
        assertEquals("Dubai", ae.first().name)
        val dubai = ae.first()
        assertEquals("Asia/Dubai", dubai.zoneId)
        assertEquals("AE-DU", dubai.region)
        val areas = world.areas(dubai).map { it.name }
        for (n in listOf("Jumayrā", "Al Karama", "Dubai Marina", "Mirdif")) assertTrue(n in areas, "$n in Dubai's areas")
        assertTrue("Sharjah" !in areas, "Sharjah is not in Dubai")
        // Small towns too: a few hundred people.
        assertTrue(world.cities("SA").size > 500, "SA ${world.cities("SA").size}")
        assertTrue(world.cities("GB").any { it.population in 500..2000 }, "GB small towns")
    }

    @Test fun searchInEnglishAndArabic() {
        assertEquals("Dubai", world.search("dubai").first().name)
        assertEquals("Istanbul", world.search("istanbul").first().name)
        // Accents ignored both ways.
        assertTrue(world.search("sao paulo").any { it.name == "São Paulo" })
        assertTrue(world.search("São Paulo").any { it.name == "São Paulo" })
        // A neighbourhood.
        assertTrue(world.search("al karama").any { it.countryCode == "AE" && it.neighbourhood })
        // Arabic, with and without hamza and diacritics.
        val dubaiAr = world.search("دبي")
        assertTrue(dubaiAr.any { it.name == "Dubai" && it.countryCode == "AE" }, dubaiAr.take(5).toString())
        // Egypt's Alexandria first, shown with its standard Arabic name, found by any common spelling.
        for (q in listOf("الإسكندرية", "الاسكندرية", "اسكندرية", "الإسكندريه")) {
            val first = world.search(q).first()
            assertEquals("EG" to "Alexandria", first.countryCode to first.name, q)
        }
        assertEquals("الإسكندرية", world.search("alexandria").first { it.countryCode == "EG" }.nameAr)
        assertEquals("دبي", world.cities("AE").first().nameAr)
        assertEquals("القاهرة", world.search("cairo").first().nameAr)
        assertTrue(world.search("مكة").any { it.countryCode == "SA" })
        // Within a country.
        assertTrue(world.search("al", countryCode = "AE").all { it.countryCode == "AE" })
        assertTrue(world.search("x").isEmpty())
        // A district's city is the large city around it, not a smaller neighbour.
        val marina = world.search("dubai marina").first { it.countryCode == "AE" }
        assertEquals("Dubai", world.cityOf(marina.point, "AE")!!.name)
        // Nearest named place to a point (for a pin on the map).
        assertEquals("AE", world.nearest(GeoPoint(25.2048, 55.2708))!!.countryCode)
    }

    @Test fun coordinatesAsPeopleWriteThem() {
        fun ok(s: String, lat: Double, lon: Double) {
            val p = assertNotNull(Coordinates.parse(s), s)
            assertEquals(lat, p.latitude, 1e-6, s); assertEquals(lon, p.longitude, 1e-6, s)
        }
        ok("25.2048, 55.2708", 25.2048, 55.2708)
        ok("25.2048 55.2708", 25.2048, 55.2708)
        ok("-33.8688,151.2093", -33.8688, 151.2093)
        ok("25.2048° N, 55.2708° E", 25.2048, 55.2708)
        ok("33.8688S 151.2093E", -33.8688, 151.2093)
        ok("N 25.2048 E 55.2708", 25.2048, 55.2708)
        ok("40.7128° N, 74.0060° W", 40.7128, -74.0060)
        ok("55.2708 E, 25.2048 N", 25.2048, 55.2708)
        ok("25°12'17.3\"N 55°16'14.9\"E", 25 + 12 / 60.0 + 17.3 / 3600, 55 + 16 / 60.0 + 14.9 / 3600)
        ok("25 12 17.3 N 55 16 14.9 E", 25 + 12 / 60.0 + 17.3 / 3600, 55 + 16 / 60.0 + 14.9 / 3600)
        ok("51°30.5'N 0°7.5'W", 51 + 30.5 / 60, -(7.5 / 60))
        ok("https://maps.google.com/?q=25.2048,55.2708", 25.2048, 55.2708)
        ok("https://www.google.com/maps/place/Dubai/@25.2048,55.2708,12z", 25.2048, 55.2708)
        ok("geo:21.4225,39.8262", 21.4225, 39.8262)
        ok("٢٥٫٢٠٤٨، ٥٥٫٢٧٠٨", 25.2048, 55.2708)
        ok("−33.8688, 151.2093", -33.8688, 151.2093)
        for (bad in listOf("", "dubai", "25.2048", "95, 55", "25, 190", "25 N 55 N", "25.5 30 N 55 E", "25 61 N 55 10 E", "0, 0", "1, 2, 3", "https://example.com/x"))
            assertNull(Coordinates.parse(bad), bad)
        assertEquals("25.20480° N, 55.27080° E", Coordinates.format(GeoPoint(25.2048, 55.2708)))
        assertEquals("33.86880° S, 151.20930° E", Coordinates.format(GeoPoint(-33.8688, 151.2093)))
    }
}
