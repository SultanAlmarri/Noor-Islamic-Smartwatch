package com.noor.app.ui.opening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of the opening (0.14): when it plays, how long it may take, that nothing in it runs backwards or late. */
class OpeningTest {

    @Test fun aColdStartOpensInFullOnceForTheProcess() {
        Opening.playedInThisProcess = false
        assertEquals(OpeningMode.FULL, Opening.modeAtStart(animations = true, home = true))
        // The activity built again in the same process (language, theme, rotation): nothing replays.
        assertEquals(OpeningMode.NONE, Opening.modeAtStart(animations = true, home = true))

        // The welcome page has its own sunrise: there the launch screen only dissolves.
        Opening.playedInThisProcess = false
        assertEquals(OpeningMode.BRIEF, Opening.modeAtStart(animations = true, home = false))

        // "Remove animations": nothing moves, now or later in this process.
        Opening.playedInThisProcess = false
        assertEquals(OpeningMode.NONE, Opening.modeAtStart(animations = false, home = true))
        assertEquals(OpeningMode.NONE, Opening.modeAtStart(animations = true, home = true))
    }

    @Test fun comingBackReplaysOnlyAfterALongWhileAndOnlyOnTheHomeScreen() {
        assertFalse(Opening.replaysOnReturn(awayMs = 0, animations = true, onHome = true))
        assertFalse(Opening.replaysOnReturn(awayMs = Opening.RETURN_AFTER_MS - 1, animations = true, onHome = true))
        assertTrue(Opening.replaysOnReturn(awayMs = Opening.RETURN_AFTER_MS, animations = true, onHome = true))
        assertFalse(Opening.replaysOnReturn(awayMs = Opening.RETURN_AFTER_MS * 4, animations = true, onHome = false))
        assertFalse(Opening.replaysOnReturn(awayMs = Opening.RETURN_AFTER_MS * 4, animations = false, onHome = true))
    }

    @Test fun itIsShort() {
        // About two seconds; never more than two and a half, however far the sky has to run.
        assertTrue(Opening.FULL_MS in 1500..2500)
        assertTrue(Opening.SKY_AT + Opening.skyMs(0.2f, 0.9f) <= Opening.FULL_MS)
        assertTrue(Opening.skyMs(0.5f, 0.52f) >= Opening.SKY_MIN_MS)
        // The veil has gone, and touches reach the home screen, well before the end.
        assertTrue(Opening.VEIL_END <= 1300)
        // The prayer and the countdown have settled by a second and a half; the last of the day's times soon after.
        assertTrue(Opening.HEADER_AT + 4 * Opening.STEP_MS + Opening.RISE_MS <= 1500)
        assertTrue(Opening.PANEL_AT + 6 * Opening.STEP_MS + Opening.RISE_MS <= Opening.FULL_MS)
        assertTrue(Opening.BRIEF_MS <= 600)
        // On a return (no veil) the day's times have settled within about a second.
        assertTrue(Opening.PANEL_AT - Opening.NO_VEIL_LEAD_MS + 6 * Opening.STEP_MS + Opening.RISE_MS <= 1150)
    }

    @Test fun everyPartComesInOnceAndNeverGoesBack() {
        val o = OpeningState().apply { begin(OpeningMode.FULL, now = true) }
        val parts = listOf(0 to Opening.GLOW_MS, Opening.IRIS_AT to Opening.IRIS_MS, Opening.HEADER_AT to Opening.RISE_MS,
            Opening.PANEL_AT + 6 * Opening.STEP_MS to Opening.RISE_MS, Opening.TAB_AT to Opening.RISE_MS)
        for ((start, length) in parts) {
            var last = -1f
            var t = 0f
            while (t <= Opening.FULL_MS) {
                o.seek(t)
                val p = o.part(start, length)
                assertTrue("part $start+$length at $t ms: $p", p in 0f..1f && p >= last - 1e-6f)
                if (t < start) assertEquals(0f, p, 0f)
                last = p
                t += 8f
            }
            o.seek(Opening.FULL_MS.toFloat())
            assertEquals(1f, o.part(start, length), 1e-4f)
        }
    }

    @Test fun theVeilLeavesAndTheOpeningEnds() {
        val o = OpeningState().apply { begin(OpeningMode.FULL, now = true) }
        assertTrue(o.veiled); assertTrue(o.arriving); assertTrue(o.active)
        o.seek(Opening.VEIL_END.toFloat())
        assertFalse(o.veiled); assertTrue(o.arriving)
        o.seek(Opening.FULL_MS.toFloat())
        assertFalse(o.arriving); assertFalse(o.active)

        // A return: the home screen comes in again, with no veil over it.
        o.begin(OpeningMode.FULL, veil = false, now = true)
        assertFalse(o.veiled); assertTrue(o.arriving)

        // The launch screen only dissolving: nothing of the home screen is held back.
        o.begin(OpeningMode.BRIEF, now = true)
        assertTrue(o.veiled); assertFalse(o.arriving)
        assertEquals(1f, o.part(Opening.PANEL_AT, Opening.RISE_MS), 0f)
        o.seek(Opening.BRIEF_MS.toFloat())
        assertFalse(o.veiled); assertFalse(o.active)
    }

    @Test fun withNoOpeningEverythingIsInItsPlace() {
        val o = OpeningState.None
        assertFalse(o.active); assertFalse(o.veiled); assertFalse(o.arriving)
        assertEquals(1f, o.part(Opening.PANEL_AT, Opening.RISE_MS), 0f)
        o.skip()
        assertFalse(o.skipped)
        assertFalse(o.claimSky())
    }

    @Test fun aTouchAsksForTheEndAndTheSkyRunsOncePerOpening() {
        val o = OpeningState().apply { begin(OpeningMode.FULL, now = true) }
        assertTrue(o.claimSky())
        assertFalse("the home screen left and shown again during the same opening", o.claimSky())
        o.skip()
        assertTrue(o.skipped)
        o.begin(OpeningMode.FULL, veil = false, now = true)
        assertFalse(o.skipped)
        assertTrue(o.claimSky())
    }
}
