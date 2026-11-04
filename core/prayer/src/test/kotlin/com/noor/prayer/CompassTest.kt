package com.noor.prayer

import com.noor.prayer.qibla.CompassAssessor
import com.noor.prayer.qibla.CompassIssue
import com.noor.prayer.qibla.CompassMath
import com.noor.prayer.qibla.CompassQuality
import com.noor.prayer.qibla.HeadingFilter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompassTest {
    /** Rotation matrix of a phone lying flat, top edge pointing [az] degrees east of north (Android convention). */
    private fun flat(az: Double): FloatArray {
        val a = Math.toRadians(az)
        // device→world for a rotation about the vertical axis by -az (world X=east, Y=north, Z=up)
        return floatArrayOf(cos(a).toFloat(), sin(a).toFloat(), 0f, (-sin(a)).toFloat(), cos(a).toFloat(), 0f, 0f, 0f, 1f)
    }

    @Test fun attitudeOfAFlatPhone() {
        for (az in listOf(0.0, 45.0, 90.0, 182.36, 259.39, 359.5)) {
            val att = CompassMath.attitude(flat(az))
            assertEquals(az, att.azimuthDeg, 1e-3)
            assertEquals(0.0, att.tiltDeg, 1e-3)
        }
    }

    @Test fun tiltOfAPhoneHeldUpright() {
        // Upright, screen facing the user: device Z points horizontally → tilt 90°.
        val r = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        assertEquals(90.0, CompassMath.attitude(r).tiltDeg, 1e-3)
    }

    @Test fun filterWrapsAroundNorthWithoutSwinging() {
        val f = HeadingFilter(100.0)
        var t = 0L
        f.update(350.0, t)
        var last = 0.0
        repeat(50) { t += 20_000_000; last = f.update(10.0, t) }
        assertTrue(last < 11 || last > 359, "settles near 10°, got $last")
        // It never passes through 180° on the way.
        val g = HeadingFilter(100.0); t = 0; g.update(350.0, t)
        repeat(20) { t += 20_000_000; val h = g.update(10.0, t); assertTrue(h > 340 || h < 20, "went the long way: $h") }
    }

    @Test fun assessment() {
        assertEquals(CompassQuality.GOOD, CompassAssessor.assess(6.0, 3, 45.0, 44.0, 5.0).quality)
        assertEquals(CompassQuality.FAIR, CompassAssessor.assess(15.0, 3, 45.0, 44.0, 5.0).quality)
        assertEquals(CompassQuality.POOR, CompassAssessor.assess(40.0, 3, 45.0, 44.0, 5.0).quality)
        assertEquals(CompassQuality.FAIR, CompassAssessor.assess(null, 2, null, null, null).quality)
        assertEquals(CompassQuality.POOR, CompassAssessor.assess(null, 0, null, null, null).quality)
        val magnet = CompassAssessor.assess(5.0, 3, 80.0, 44.0, 5.0)
        assertEquals(CompassQuality.POOR, magnet.quality)
        assertTrue(CompassIssue.MAGNETIC_INTERFERENCE in magnet.issues)
        assertTrue(CompassIssue.NOT_FLAT in CompassAssessor.assess(5.0, 3, 44.0, 44.0, 60.0).issues)
    }
}
