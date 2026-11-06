package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.noor.app.NoorApplication
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ThemeMode
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The home screen through the day, in both themes and languages, over desert and over hills, as the app frames it.
 * Software renders for design review (build/design), not device photos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class TodayDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var scrub by mutableStateOf<Double?>(null)
    private var skyTime by mutableStateOf<Float?>(null)

    private fun harness(): RenderHarness {
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
            TodayContent(u, pad, { cache.resolved(s, place, profile, it) }, {}, {}, {}, previewScrub = scrub, previewSkyTime = skyTime)
        }
        return h
    }

    /** One run (the app's data store is one per process): Dubai through the day, ambient moments, scrubbing, Istanbul. */
    @Test fun home() {
        val h = harness()
        h.start()
        h.shot("today_asr_dark_en", "Maghrib")
        h.theme = ThemeMode.LIGHT; h.shot("today_asr_light_en", "Maghrib")
        h.theme = ThemeMode.DARK
        h.clock = RenderHarness.at(5, 52); h.shot("today_dawn_en", "Shuruq")
        h.clock = RenderHarness.at(10, 30); h.theme = ThemeMode.LIGHT; h.shot("today_morning_light_en", "Dhuhr")
        h.clock = RenderHarness.at(17, 46); h.theme = ThemeMode.DARK; h.shot("today_sunset_en", "Isha")
        h.clock = RenderHarness.at(21, 30); h.shot("today_night_en", "Fajr")
        h.theme = ThemeMode.LIGHT; h.shot("today_night_light_en", "Fajr")
        h.theme = ThemeMode.DARK
        h.arabic = true; h.clock = RenderHarness.at(17, 20); h.shot("today_golden_ar", "المغرب")
        h.arabic = false; h.fontScale = 1.3f; h.clock = RenderHarness.DEFAULT_NOW; h.shot("today_large_text_en", "Maghrib")
        h.fontScale = 1f
        // Ambient moments, frozen: birds crossing at golden hour, a meteor at night.
        h.clock = RenderHarness.at(17, 20); skyTime = 0.03f; h.shot("today_birds_en", "Maghrib")
        h.clock = RenderHarness.at(22, 40); skyTime = 0.004f; h.shot("today_meteor_en", "Fajr")
        skyTime = null
        // The Sun drawn along its path: to midday (caught by no prayer in a preview), then evening.
        h.clock = RenderHarness.DEFAULT_NOW; scrub = (11 * 60 + 2) / 1440.0; h.shot("today_scrub_en", "ago")
        scrub = null
        // Over hills.
        h.settings = RenderHarness.DUBAI.copy(manualCityId = "ist")
        h.clock = istanbul(16, 40); h.shot("today_hills_golden_en", "Maghrib")
        h.clock = istanbul(7, 20); h.shot("today_hills_dawn_en", "Shuruq")
        h.clock = istanbul(11, 0); h.theme = ThemeMode.LIGHT; h.shot("today_hills_noon_light_en", "Dhuhr")
        h.clock = istanbul(23, 10); h.theme = ThemeMode.DARK; h.shot("today_hills_night_en", "Fajr")
    }

    private fun istanbul(h: Int, m: Int): Instant = ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant()
}
