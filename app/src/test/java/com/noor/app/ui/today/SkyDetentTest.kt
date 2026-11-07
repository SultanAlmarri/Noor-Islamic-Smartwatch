package com.noor.app.ui.today

import com.noor.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/** Dragging the Sun past the prayers (0.9.6): it settles on each one, but never jumps. */
class SkyDetentTest {
    private fun mark(p: Prayer, x: Float) = (p to Instant.ofEpochSecond(x.toLong())) to x
    // Fajr and Shuruq close together (twilight is narrow on screen), Dhuhr far away.
    private val marks = listOf(mark(Prayer.FAJR, 100f), mark(Prayer.SHURUQ, 130f), mark(Prayer.DHUHR, 500f))

    @Test fun mappingIsContinuousAndNeverRunsBackwards() {
        var prev = SkyDetent.of(marks, 0f, 60f).x
        var x = 0f
        while (x < 700f) {
            x += 0.05f
            val now = SkyDetent.of(marks, x, 60f).x
            assertTrue("backwards at $x", now >= prev - 1e-3f)
            assertTrue("jump at $x: ${now - prev}", now - prev < 1.0f)
            prev = now
        }
    }

    @Test fun theSunRestsExactlyOnAPrayerNearItsMark() {
        val d = SkyDetent.of(marks, 503f, 60f)
        assertEquals(500f, d.x, 0f)
        assertEquals(Prayer.DHUHR, d.caught?.first)
        assertNull(SkyDetent.of(marks, 560.5f, 60f).caught)
        assertEquals(560.5f, SkyDetent.of(marks, 560.5f, 60f).x, 0f)
    }

    /** Where two marks are close, each detent shrinks to half the gap, so they meet without a step between them. */
    @Test fun closeMarksShareTheSpaceBetweenThem() {
        val mid = SkyDetent.of(marks, 115f, 60f).x
        assertTrue(abs(mid - 115f) < 0.01f)
        assertEquals(Prayer.FAJR, SkyDetent.of(marks, 102f, 60f).caught?.first)
        assertEquals(Prayer.SHURUQ, SkyDetent.of(marks, 128f, 60f).caught?.first)
    }
}
