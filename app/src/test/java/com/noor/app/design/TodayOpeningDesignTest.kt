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
 * The opening time-lapse (0.9.2): frames at 0, 0.3, 0.6 and 1 s. The sky moves; the countdown and the prayer are the real
 * ones from the very first frame. Software renders of single frames, not a measure of smoothness on a phone.
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
        // The real next prayer and its countdown are there from the first frame.
        // (The countdown draws each digit on its own so they can roll, so the prayer and its time are checked.)
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("Maghrib", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("5:40 PM", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.mainClock.advanceTimeBy(300); h.frame("today_opening_0300ms")
        rule.mainClock.advanceTimeBy(300); h.frame("today_opening_0600ms")
        rule.mainClock.advanceTimeBy(400); h.frame("today_opening_1000ms")
        org.junit.Assert.assertTrue(com.noor.app.ui.today.OpeningSky.played)
    }
}
