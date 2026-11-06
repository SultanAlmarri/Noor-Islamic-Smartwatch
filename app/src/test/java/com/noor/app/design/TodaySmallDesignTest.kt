package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.noor.app.NoorApplication
import com.noor.app.data.PrayerProvider
import com.noor.app.ui.today.DayCache
import com.noor.app.ui.today.TodayContent
import com.noor.app.ui.today.TodayUi
import com.noor.prayer.Schedule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home screen on a common phone size (360 × 740 dp): all the day's times fit without scrolling. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class TodaySmallDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun small() {
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
        h.start()
        h.shot("today_small_en", "Maghrib")
        h.assertPrayersFit(RenderHarness.PRAYERS_EN)
        h.arabic = true; h.shot("today_small_ar", "المغرب")
        h.assertPrayersFit(RenderHarness.PRAYERS_AR)
    }
}
