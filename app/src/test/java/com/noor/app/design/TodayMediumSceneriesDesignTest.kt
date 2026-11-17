package com.noor.app.design

import android.graphics.BitmapFactory
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * (0.19) The medium graphics (the 0.9 sky) for each of the six sceneries, as drawn on phones below Android 13 (the
 * Canvas renderer: there is no GPU here, so this is the path Robolectric draws). Software renders for design review
 * (build/design/medium_*), not device photos. Fails if two sceneries draw the same land.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class TodayMediumSceneriesDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun everySceneryHasItsOwnLand() {
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
        val sceneries = listOf(Scenery.DUNES to "dunes", Scenery.SAVANNA to "meadow", Scenery.RAINFOREST to "forest", Scenery.ARCTIC to "arctic", Scenery.SEA to "sea", Scenery.CANYON to "canyon")
        val moments = listOf(Triple(9, 0, "morning"), Triple(16, 40, "golden"), Triple(21, 30, "night"))
        h.settings = RenderHarness.DUBAI.copy(manualCityId = "ist", skyGraphics = SkyGraphics.MEDIUM, scenery = sceneries.first().first)
        h.start()
        for ((scenery, name) in sceneries) {
            h.settings = h.settings.copy(scenery = scenery)
            for ((hh, mm, moment) in moments) {
                h.clock = istanbul(hh, mm)
                h.theme = if (moment == "morning") ThemeMode.LIGHT else ThemeMode.DARK
                h.shot("medium_${name}_$moment", "Isha")
            }
        }
        // Each scenery's land is its own: the golden-hour pictures differ pairwise where the land is.
        val crops = sceneries.map { (_, name) ->
            val b = BitmapFactory.decodeFile(File("build/design/medium_${name}_golden.png").path)
            val y0 = (b.height * 0.33).toInt(); val y1 = (b.height * 0.45).toInt()
            name to IntArray(b.width * (y1 - y0)).also { b.getPixels(it, 0, b.width, 0, y0, b.width, y1 - y0) }
        }
        for (i in crops.indices) for (j in i + 1 until crops.size) {
            val (a, pa) = crops[i]; val (b, pb) = crops[j]
            val differ = pa.indices.count { k -> Math.abs((pa[k] shr 8 and 0xFF) - (pb[k] shr 8 and 0xFF)) > 24 }
            assertTrue("$a and $b draw nearly the same land ($differ pixels differ)", differ > pa.size / 50)
        }
    }

    private fun istanbul(h: Int, m: Int): Instant = ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant()
}
