package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.noor.app.NoorApplication
import com.noor.app.data.PrayerProvider
import com.noor.app.ui.today.DayCache
import com.noor.app.ui.today.TodayContent
import com.noor.app.ui.today.TodayUi
import com.noor.prayer.Schedule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The opening (0.9.6): frames through its two seconds. The scene fades up from the page while the sky runs from the
 * start of the current prayer's time to now and the path draws itself; the countdown and the prayer are the real ones
 * from the very first frame. Software renders of single frames (the Canvas renderer: no GPU here), not a measure of
 * smoothness on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class TodayOpeningDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun opening() {
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
        com.noor.app.ui.today.OpeningSky.played = false
        rule.mainClock.autoAdvance = false
        h.start()
        rule.mainClock.advanceTimeByFrame()
        h.frame("today_opening_0000ms")
        // The real prayer of the countdown and its time are there from the first frame. (0.9.7: at 15:29, 15 minutes after
        // Asr, the countdown is "+" time since Asr; the countdown draws each digit on its own so they can roll, so the
        // prayer, its time and the sign are checked.)
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("Asr", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("3:14 PM", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("+", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.mainClock.advanceTimeBy(400); h.frame("today_opening_0400ms")
        rule.mainClock.advanceTimeBy(500); h.frame("today_opening_0900ms")
        rule.mainClock.advanceTimeBy(600); h.frame("today_opening_1500ms")
        rule.mainClock.advanceTimeBy(800); h.frame("today_opening_2300ms")
        org.junit.Assert.assertTrue(com.noor.app.ui.today.OpeningSky.played)
    }
}
