package com.noor.prayer

import com.noor.prayer.qibla.Qibla
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reference values from GeographicLib 2.x (Karney's algorithm, WGS-84) and the spherical formula, computed independently. */
class QiblaTest {
    private data class Ref(val name: String, val lat: Double, val lon: Double, val gc: Double, val geo: Double, val km: Double)

    private val refs = listOf(
        Ref("Dubai", 25.2048, 55.2708, 258.2311, 258.3118, 1633.169),
        Ref("London", 51.5074, -0.1278, 118.9872, 118.8684, 4794.757),
        Ref("New York", 40.7128, -74.0060, 58.4817, 58.3960, 10323.917),
        Ref("Jakarta", -6.2088, 106.8456, 295.1517, 295.0247, 7922.241),
        Ref("Sydney", -33.8688, 151.2093, 277.4996, 277.3188, 13236.947),
        Ref("Cape Town", -33.9249, 18.4241, 23.3526, 23.4673, 6532.038),
        Ref("Anchorage", 61.2181, -149.9003, 350.8831, 350.8993, 10802.830),
        Ref("Medina", 24.4672, 39.6111, 176.2352, 176.2140, 337.903),
        Ref("Baden-Baden", 48.7606, 8.2398, 126.0979, 125.9740, 4126.526),
        Ref("Surfers Paradise", -28.0023, 153.4145, 279.8145, 279.6553, 13349.658),
        Ref("Tokyo", 35.6762, 139.6503, 292.9987, 293.0720, 9487.883),
        Ref("Reykjavik", 64.1466, -21.9426, 106.1176, 106.0275, 6522.784),
        Ref("Santiago", -33.4489, -70.6693, 81.8363, 82.0150, 13149.152),
    )

    @Test fun greatCircleMatchesReference() {
        for (r in refs) assertEquals(r.gc, Qibla.greatCircleBearing(GeoPoint(r.lat, r.lon), Qibla.KAABA), 1e-3, r.name)
    }

    @Test fun ellipsoidalBearingAndDistanceMatchGeographicLib() {
        for (r in refs) {
            val q = Qibla.from(GeoPoint(r.lat, r.lon))
            assertEquals(r.geo, q.bearingDeg, 1e-3, r.name)
            assertEquals(r.km, q.distanceKm, 0.01, r.name)
            assertFalse(q.nearAntipode, r.name); assertFalse(q.veryClose, r.name)
        }
    }

    @Test fun edgeCases() {
        val antipode = Qibla.from(GeoPoint(-21.40, -140.10))
        assertTrue(antipode.nearAntipode)
        assertTrue(antipode.bearingDeg in 0.0..360.0)
        val haram = Qibla.from(GeoPoint(21.4189, 39.8262)) // Makkah clock tower, ~400 m away
        assertTrue(haram.veryClose)
        assertEquals(0.09, haram.bearingDeg, 0.01)
        assertEquals(0.0, Qibla.from(Qibla.KAABA).distanceKm, 1e-9)
    }

    @Test fun turnIsTheShortestWay() {
        assertEquals(10.0, Qibla.turn(350.0, 0.0), 1e-9)
        assertEquals(-10.0, Qibla.turn(0.0, 350.0), 1e-9)
        assertEquals(180.0, Qibla.turn(0.0, 180.0), 1e-9)
        assertEquals(-84.47, Qibla.turn(343.86, 259.39), 1e-9)
    }
}
