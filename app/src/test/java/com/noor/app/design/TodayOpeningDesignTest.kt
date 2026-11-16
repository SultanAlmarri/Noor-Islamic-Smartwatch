package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.data.PrayerProvider
import com.noor.app.ui.opening.Dusk
import com.noor.app.ui.opening.Opening
import com.noor.app.ui.opening.OpeningMode
import com.noor.app.ui.opening.OpeningState
import com.noor.app.ui.today.DayCache
import com.noor.app.ui.today.TodayContent
import com.noor.app.ui.today.TodayUi
import com.noor.prayer.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The opening (0.14, on the sky's sweep of 0.9.6): frames through its two seconds, as the app frames them (veil, home
 * screen, tab bar). The launch screen's dusk and mark; light gathering round the mark; the dusk opening onto the home
 * screen while the sky runs from the start of the current prayer's time to now; the place, the prayer, the countdown
 * and the day's times settling in; the tab bar rising. The prayer, its time and the countdown are the real ones from
 * the very first frame (they are in the page before they can be seen). Then the same opening cut short by a touch,
 * and the home screen coming in again without the veil, as when Noor returns after a long while.
 * Software renders of single frames (the Canvas renderer: no GPU here), not a measure of smoothness on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class TodayOpeningDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun opening() {
        // The launch screen (a colour in the app's theme) and the veil (drawn by the app) are the same dusk as the
        // sky's own waiting colour, or the hand-over would show.
        assertEquals(RuntimeEnvironment.getApplication().getColor(R.color.noor_dusk), Dusk.toArgb())

        val h = RenderHarness(rule)
        val tables = (RuntimeEnvironment.getApplication() as NoorApplication).container.timetables
        h.tabRoute = com.noor.app.Routes.PRAYERS
        h.screen = { pad ->
            val s = h.settings.copy(theme = h.theme)
            val place = PrayerProvider.place(s)!!
            val profile = PrayerProvider.profile(s, place)
            val cache = DayCache(tables)
            val state = Schedule.state(h.clock, place.zone) { cache.get(s, place, profile, it) }
            val u = TodayUi(s, place, profile, PrayerProvider.authority(s, place), h.clock, state, h.clock.atZone(place.zone).toLocalDate(), refreshing = false, lastRefresh = null)
            TodayContent(u, pad, { cache.resolved(s, place, profile, it) }, {}, {}, {})
        }
        h.ambient = true
        val opening = OpeningState().apply { begin(OpeningMode.FULL, veil = true, now = true) }
        h.opening = opening
        rule.mainClock.autoAdvance = false
        h.start()
        rule.mainClock.advanceTimeByFrame()
        h.frame("today_opening_0000ms")
        assertTrue(opening.veiled)
        // The real prayer of the countdown and its time are there from the first frame. (0.9.7: at 15:29, 15 minutes after
        // Asr, the countdown is "+" time since Asr; the countdown draws each digit on its own so they can roll, so the
        // prayer, its time and the countdown as a screen reader has it are checked.)
        assertTrue(rule.onAllNodesWithText("Asr", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("3:14 PM", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("+15:21"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        var at = 0
        for (ms in listOf(250, 450, 650, 850, 1050, 1300, 1700)) {
            rule.mainClock.advanceTimeBy((ms - at).toLong()); at = ms
            h.frame("today_opening_%04dms".format(ms))
        }
        // By the time the veil has gone the prayer and its countdown are there to read; the times follow at once.
        assertFalse(opening.veiled)
        rule.mainClock.advanceTimeBy((Opening.FULL_MS + 200 - at).toLong())
        h.frame("today_opening_end")
        assertFalse("The opening is over within its time", opening.active)
        h.assertPrayersFit(RenderHarness.PRAYERS_EN)

        // A touch finishes it: the same opening, touched a quarter of a second in, is over well within half a second more.
        opening.begin(OpeningMode.FULL, veil = true, now = true)
        rule.mainClock.advanceTimeBy(250)
        assertTrue(opening.veiled)
        opening.skip()
        rule.mainClock.advanceTimeBy(120); h.frame("today_opening_skipped_120ms")
        rule.mainClock.advanceTimeBy(330)
        assertFalse("A touch ends the opening", opening.active)
        h.frame("today_opening_skipped_end")

        // Back after a long while: no veil; the Sun runs to now and the place, the countdown and the times settle again.
        opening.begin(OpeningMode.FULL, veil = false, now = true)
        rule.mainClock.advanceTimeByFrame()
        assertFalse(opening.veiled)
        rule.mainClock.advanceTimeBy(300); h.frame("today_return_0300ms")
        rule.mainClock.advanceTimeBy(400); h.frame("today_return_0700ms")
        rule.mainClock.advanceTimeBy((Opening.FULL_MS - 600).toLong())
        assertFalse(opening.active)
        h.frame("today_return_end")
        h.assertPrayersFit(RenderHarness.PRAYERS_EN)
    }
}
