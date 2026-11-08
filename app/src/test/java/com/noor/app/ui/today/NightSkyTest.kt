package com.noor.app.ui.today

import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.9.8: the night sky is the real one. Sidereal time against a published value; known stars where an almanac puts
 * them (Polaris at the latitude's height in the north; Sirius on the meridian at its culmination); the frame's axes
 * are orthonormal; the Milky Way's centre where it is (high in the south on a July night).
 */
class NightSkyTest {
    private fun altAz(v: DoubleArray, centerAz: Double): Pair<Double, Double> {
        val alt = Math.toDegrees(asin(v[1]))
        val az = (centerAz + Math.toDegrees(atan2(v[0], v[2]))).mod(360.0)
        return alt to az
    }

    @Test fun siderealTime() {
        // Meeus, Astronomical Algorithms, example 12.a: 1987 April 10, 0h UT: GMST 13h 10m 46.3668s = 197.693195°.
        assertEquals(197.693195, NightSky.gmst(Instant.parse("1987-04-10T00:00:00Z")), 0.001)
    }

    @Test fun knownStarsWhereTheyAre() {
        val dubaiLat = 25.2048; val dubaiLon = 55.2708
        val at = Instant.parse("2026-11-07T18:00:00Z") // 22:00 in Dubai
        val lst = NightSky.gmst(at) + dubaiLon
        // Polaris: always about as high as the latitude, due north.
        val (pAlt, pAz) = altAz(NightSky.view(37.95, 89.26, dubaiLat, lst, 180.0), 180.0)
        assertEquals(dubaiLat, pAlt, 1.0)
        assertTrue("Polaris north: $pAz", pAz < 2.0 || pAz > 358.0)
        // Sirius at its culmination (hour angle 0): due south, at 90° − latitude + declination.
        val (sAlt, sAz) = altAz(NightSky.view(101.287, -16.716, dubaiLat, 101.287, 180.0), 180.0)
        assertEquals(90 - dubaiLat - 16.716, sAlt, 0.01)
        assertEquals(180.0, sAz, 0.01)
        // The frame's axes are unit vectors at right angles.
        val fr = NightSky.frame(at, dubaiLat, dubaiLon)
        for (v in listOf(fr.celX, fr.celY, fr.celZ, fr.galN, fr.galC)) assertEquals(1.0, NightSky.length(v).toDouble(), 1e-5)
        fun dot(a: FloatArray, b: FloatArray) = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).toDouble()
        assertEquals(0.0, dot(fr.celX, fr.celY), 1e-5); assertEquals(0.0, dot(fr.celX, fr.celZ), 1e-5); assertEquals(0.0, dot(fr.galN, fr.galC), 1e-3)
        // The celestial pole's height is the latitude.
        assertEquals(dubaiLat, Math.toDegrees(asin(fr.celZ[1].toDouble())), 1e-3)
        // The galactic centre (Sagittarius) high in the south at midnight in July, from Dubai.
        val july = NightSky.frame(Instant.parse("2026-07-15T20:00:00Z"), dubaiLat, dubaiLon)
        val (gAlt, gAz) = altAz(doubleArrayOf(july.galC[0].toDouble(), july.galC[1].toDouble(), july.galC[2].toDouble()), 180.0)
        assertTrue("galactic centre $gAlt° at $gAz°", gAlt > 30 && gAz in 150.0..230.0)
        // A colour for every temperature: blue-white hot stars, orange cool ones.
        val hot = NightSky.colour(25_000); val cool = NightSky.colour(3_500)
        assertTrue((hot and 0xFF) > ((hot shr 16) and 0xFF)); assertTrue(((cool shr 16) and 0xFF) > (cool and 0xFF))
        assertTrue(sqrt(1.0) == 1.0)
    }
}
