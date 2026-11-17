package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.noor.app.NoorApplication
import com.noor.app.data.PrayerProvider
import com.noor.app.data.Scenery
import com.noor.app.data.SkyGraphics
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
 * The home screen under the low graphics (0.19, Settings → Home scenery → Graphics → Low): the simplest sky
 * (SimpleSkyLayer) for each of the six sceneries, at dawn, midday, golden hour and night, in Dubai, as the app frames
 * it; a full moon over the sea; and two in Arabic (mirrored). Software renders for design review (build/design/low_*),
 * not device photos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class TodayLowDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

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
            TodayContent(u, pad, { cache.resolved(s, place, profile, it) }, {}, {}, {})
        }
        return h
    }

    /** One run (the app's data store is one per process). */
    @Test fun low() {
        val h = harness()
        h.settings = low(Scenery.DUNES)
        h.start()
        val sceneries = listOf(Scenery.DUNES, Scenery.RAINFOREST, Scenery.ARCTIC, Scenery.SEA, Scenery.SAVANNA, Scenery.CANYON)
        val moments = listOf("dawn" to at(6, 31), "midday" to at(12, 0), "golden" to at(17, 18), "night" to at(21, 30))
        for (s in sceneries) {
            h.settings = low(s)
            for ((name, t) in moments) {
                h.theme = if (name == "midday") ThemeMode.LIGHT else ThemeMode.DARK
                h.clock = t
                h.shot("low_${s.name.lowercase()}_$name", "Fajr")
            }
        }
        // A full moon over the sea (the middle of the Hijri month), and the scene mirrored in Arabic.
        h.theme = ThemeMode.DARK
        h.settings = low(Scenery.SEA)
        h.clock = ZonedDateTime.of(2026, 11, 24, 22, 30, 0, 0, zone).toInstant(); h.shot("low_sea_fullmoon", "Fajr")
        h.arabic = true
        h.settings = low(Scenery.CANYON)
        h.clock = at(17, 18); h.shot("low_canyon_golden_ar", "الفجر")
        h.settings = low(Scenery.ARCTIC)
        h.clock = at(6, 31); h.shot("low_arctic_dawn_ar", "الفجر")
    }

    private fun low(s: Scenery) = RenderHarness.DUBAI.copy(skyGraphics = SkyGraphics.LOW, scenery = s)
    private val zone = ZoneId.of("Asia/Dubai")
    private fun at(h: Int, m: Int): Instant = ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, zone).toInstant()
}
