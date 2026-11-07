package com.noor.prayer

import com.noor.prayer.qibla.CompassAssessor
import com.noor.prayer.qibla.CompassIssue
import com.noor.prayer.qibla.CompassQuality
import com.noor.prayer.qibla.CompassTracker
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 0.9.7: the accuracy shown on the Qibla screen does not flicker when a reading sits near a limit, and still follows a
 * real change at once. Readings at 50 per second (the phone's rate), with the noise of a phone magnetometer
 * (about 1 µT); the field expected here is 45 µT.
 */
class CompassTrackerTest {
    private val expected = 45.0
    private val stepNs = 20_000_000L

    /** Feeds [seconds] of a field around [meanUt] with Gaussian noise; returns every verdict. */
    private fun run(t: CompassTracker, meanUt: Double, seconds: Double, rnd: Random, clock: LongArray, noiseUt: Double = 1.0,
                    accuracy: Double? = 4.0, accNoise: Double = 0.0, tilt: Double = 3.0, tiltNoise: Double = 0.0): List<CompassQuality> =
        (0 until (seconds * 50).toInt()).map {
            clock[0] += stepNs
            t.field(meanUt + rnd.nextGaussian() * noiseUt, clock[0])
            t.assess(accuracy?.let { it + rnd.nextGaussian() * accNoise }, CompassAssessor.STATUS_HIGH, expected, tilt + rnd.nextGaussian() * tiltNoise, clock[0]).quality
        }

    private fun changes(q: List<CompassQuality>) = q.zipWithNext().count { (a, b) -> a != b }

    @Test fun aFieldSittingOnTheFairLimitDoesNotFlicker() {
        val rnd = Random(7)
        // 15 % off is exactly the limit: one moment judged alone flips between Good and Fair constantly.
        val alone = (0 until 500).map { CompassAssessor.assess(4.0, 3, 45.0 * 1.15 + rnd.nextGaussian(), expected, 3.0).quality }
        assertTrue(changes(alone) > 50, "judged alone it flickers (${changes(alone)} changes)")
        val t = CompassTracker(); val clock = longArrayOf(0)
        val q = run(t, expected * 1.15, 10.0, rnd, clock)
        assertTrue(changes(q) <= 1, "at most one change in 10 s, got ${changes(q)}")
    }

    @Test fun onThePoorLimitAndOnTheSensorsOwnEstimateLimit() {
        val rnd = Random(11)
        val t = CompassTracker(); val clock = longArrayOf(0)
        assertTrue(changes(run(t, expected * 1.25, 10.0, rnd, clock)) <= 2)
        val u = CompassTracker(); val c2 = longArrayOf(0)
        // The rotation sensor's estimate hovering around 10° (Good/Fair) and around 25° (Fair/Poor).
        assertTrue(changes(run(u, expected, 10.0, rnd, c2, accuracy = 10.0, accNoise = 0.8)) <= 1)
        assertTrue(changes(run(u, expected, 10.0, rnd, c2, accuracy = 25.0, accNoise = 1.5)) <= 1)
    }

    @Test fun holdFlatWarningDoesNotFlicker() {
        val rnd = Random(3)
        val t = CompassTracker(); val clock = longArrayOf(0)
        val flags = (0 until 500).map {
            clock[0] += stepNs; t.field(expected, clock[0])
            CompassIssue.NOT_FLAT in t.assess(4.0, 3, expected, 30.0 + rnd.nextGaussian() * 2, clock[0]).issues
        }
        assertTrue(flags.zipWithNext().count { (a, b) -> a != b } <= 1)
    }

    @Test fun realChangesShowAtOnce() {
        val rnd = Random(5)
        val t = CompassTracker(); val clock = longArrayOf(0)
        assertEquals(CompassQuality.GOOD, run(t, expected, 2.0, rnd, clock).last())
        // Metal nearby: the field jumps 40 % off. Poor within a tenth of a second (5 readings).
        val near = run(t, expected * 1.4, 1.0, rnd, clock)
        assertEquals(CompassQuality.POOR, near[5], "Poor after 0.1 s: ${near.take(8)}")
        assertTrue(near.drop(5).all { it == CompassQuality.POOR })
        assertTrue(CompassIssue.MAGNETIC_INTERFERENCE in t.assess(4.0, 3, expected, 3.0, clock[0]).issues)
        // Taken away: Good again within a tenth of a second too, with no timer holding the old level.
        val away = run(t, expected, 1.0, rnd, clock)
        assertEquals(CompassQuality.GOOD, away[6], "Good after 0.12 s: ${away.take(8)}")
        assertTrue(away.drop(6).all { it == CompassQuality.GOOD })
        // The sensor's own estimate jumping from 4° to 40° (Poor within a tenth of a second) and back to 5° (Good within a
        // quarter of a second: the averaged estimate has 35° to come down).
        val est = { v: Double, n: Int -> (0 until n).map { clock[0] += stepNs; t.assess(v, 3, expected, 3.0, clock[0]).quality } }
        assertEquals(CompassQuality.POOR, est(40.0, 5).last())
        assertEquals(CompassQuality.GOOD, est(5.0, 13).last())
        // Android's own calibration status, at once.
        assertEquals(CompassQuality.POOR, t.assess(5.0, CompassAssessor.STATUS_UNRELIABLE, expected, 3.0, clock[0]).quality)
        assertEquals(CompassQuality.FAIR, t.assess(5.0, CompassAssessor.STATUS_MEDIUM, expected, 3.0, clock[0]).quality)
    }

    @Test fun sameLimitsAsTheSingleReadingJudgement() {
        // From Good, the tracker gets worse exactly where CompassAssessor does.
        for (dev in listOf(0.0, 0.1, 0.149, 0.151, 0.2, 0.249, 0.251, 0.4)) {
            val t = CompassTracker(); t.field(expected * (1 + dev), 0)
            assertEquals(CompassAssessor.assess(4.0, 3, expected * (1 + dev), expected, 3.0).quality, t.assess(4.0, 3, expected, 3.0, 0).quality, "deviation $dev")
        }
        for (acc in listOf(3.0, 9.9, 10.1, 24.9, 25.1, 40.0)) {
            val t = CompassTracker(); t.field(expected, 0)
            assertEquals(CompassAssessor.assess(acc, 3, expected, expected, 3.0).quality, t.assess(acc, 3, expected, 3.0, 0).quality, "accuracy $acc")
        }
        // The band: worse at once, better only past the exit.
        assertEquals(1, CompassTracker.band(0, 0.16, 0.15, 0.25, 0.12, 0.21))
        assertEquals(1, CompassTracker.band(1, 0.13, 0.15, 0.25, 0.12, 0.21))
        assertEquals(0, CompassTracker.band(1, 0.11, 0.15, 0.25, 0.12, 0.21))
        assertEquals(2, CompassTracker.band(2, 0.22, 0.15, 0.25, 0.12, 0.21))
        assertEquals(1, CompassTracker.band(2, 0.20, 0.15, 0.25, 0.12, 0.21))
        assertEquals(0, CompassTracker.band(2, 0.05, 0.15, 0.25, 0.12, 0.21))
    }

    @Test fun estimateShownInSteadySteps() {
        val t = CompassTracker()
        assertEquals(10, t.shownEstimate(10.2))
        // Hovering between the 10 and 15 steps (12.5 is the rounding point): stays 10 until clearly past.
        for (v in listOf(12.6, 12.4, 13.0, 12.2, 13.6)) assertEquals(10, t.shownEstimate(v), "at $v")
        assertEquals(15, t.shownEstimate(13.9))
        for (v in listOf(12.4, 11.9, 11.3)) assertEquals(15, t.shownEstimate(v), "at $v")
        assertEquals(10, t.shownEstimate(11.0))
        assertEquals(3, t.shownEstimate(3.2))
        assertEquals(null, t.shownEstimate(null))
    }
}
