package com.noor.app.design

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.KnownLocation
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ThemeMode
import com.noor.app.data.TimeFormat
import com.noor.app.testing.Tester
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.today.DayCache
import com.noor.app.ui.today.TodayContent
import com.noor.app.ui.today.TodayUi
import com.noor.prayer.GeoPoint
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
import java.util.Locale

/**
 * (0.21) The home screen in the app's own frame (`AppFrame`: the page, the tester's banner and the floating tab bar),
 * on a phone of the owner's size (a Samsung's 360 dp), as the owner sees it: with the tester's banner on (his own
 * screenshot showed it covering Isha's row), a long place name in both languages, Arabic-Indic digits and the 24-hour
 * clock, and large text. Fails if a prayer's row goes under the banner or the tab bar. Software renders (build/design).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class HomeFrameDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var theme by mutableStateOf(ThemeMode.LIGHT)
    private var arabic by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)
    private var settings by mutableStateOf(RenderHarness.DUBAI)

    private val longPlace = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualPlace = KnownLocation(
        GeoPoint(25.0732, 55.2297), "Asia/Dubai", "Mohammed Bin Rashid Al Maktoum City", "AE", null, 0L,
        "مدينة محمد بن راشد آل مكتوم", placeContext = "Dubai", placeContextAr = "دبي"))

    @Test fun home() {
        val tables = (RuntimeEnvironment.getApplication() as NoorApplication).container.timetables
        com.noor.app.ui.today.SkyCompute.synchronous = true
        rule.setContent {
            val base = LocalContext.current
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); this.fontScale = this@HomeFrameDesignTest.fontScale; setLayoutDirection(locale) }
            CompositionLocalProvider(
                LocalAmbientMotion provides false,
                LocalContext provides base.createConfigurationContext(config),
                LocalConfiguration provides config,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                val s = settings.copy(theme = theme)
                NoorTheme(theme) {
                    NoorBackground {
                        com.noor.app.AppFrame(topLevel = true, tabBar = { com.noor.app.NoorTabBar(com.noor.app.Routes.PRAYERS) {} }) { frame ->
                            // (No status bar under Robolectric: 28 dp stands for it, as in the other renders.)
                            val pad = PaddingValues(top = frame.calculateTopPadding() + 28.dp, bottom = frame.calculateBottomPadding())
                            val clock = RenderHarness.DEFAULT_NOW
                            val place = PrayerProvider.place(s)!!
                            val profile = PrayerProvider.profile(s, place)
                            val cache = DayCache(tables)
                            val state = Schedule.state(clock, place.zone) { cache.get(s, place, profile, it) }
                            val u = TodayUi(s, place, profile, PrayerProvider.authority(s, place), clock, state, clock.atZone(place.zone).toLocalDate(), refreshing = false, lastRefresh = null)
                            TodayContent(u, pad, { cache.resolved(s, place, profile, it) }, {}, {}, {})
                        }
                    }
                }
            }
        }
        val isha = { if (arabic) "العشاء" else "Isha" }
        shot("frame_home_light_en", "Maghrib"); assertClear(isha())
        // The owner's own case: the tester's demo running, its banner over the foot of the page.
        Tester.on = true; Tester.demoLabel = "Demo 1/9: Dunes, spring, fair"
        shot("frame_home_tester_light_en", "Testing"); assertClear(isha())
        arabic = true; shot("frame_home_tester_light_ar", "Testing"); assertClear(isha())
        fontScale = 1.3f; shot("frame_home_tester_large_ar", "Testing"); assertClear(isha())
        arabic = false; theme = ThemeMode.DARK; shot("frame_home_tester_large_dark_en", "Testing"); assertClear(isha())
        Tester.reset(); fontScale = 1f
        // A long place name, in both languages; Arabic-Indic digits and the 24-hour clock.
        settings = longPlace; shot("frame_home_long_place_en", "Maghrib"); assertClear(isha())
        arabic = true; settings = longPlace.copy(numerals = Numerals.ARABIC_INDIC, timeFormat = TimeFormat.H24)
        shot("frame_home_long_place_digits_24h_ar", "المغرب"); assertClear(isha())
        // Large text: 1.3 and 1.5 (a Samsung's larger font sizes).
        arabic = false; settings = RenderHarness.DUBAI.copy(timeFormat = TimeFormat.H24)
        fontScale = 1.3f; shot("frame_home_large_text_24h_en", "Maghrib"); assertClear(isha())
        fontScale = 1.5f; shot("frame_home_larger_text_en", "Maghrib")
        arabic = true; shot("frame_home_larger_text_ar", "المغرب")
        fontScale = 1f; arabic = false
    }

    /** The day's last row stands above whatever lies over the foot of the page (the tester's banner, the tab bar). */
    private fun assertClear(isha: String) {
        val root = rule.activity.window.decorView
        val rows = rule.onAllNodesWithText(isha, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$isha is not on the screen", rows.isNotEmpty())
        val lowest = rows.maxOf { it.boundsInRoot.bottom }
        val banner = rule.onAllNodesWithText("Testing", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
        val tabs = rule.onAllNodesWithText(if (arabic) "اليوم" else "Today", useUnmergedTree = true).fetchSemanticsNodes()
        val top = (banner.map { it.boundsInRoot.top } + tabs.map { it.boundsInRoot.top - 40.dp.value * root.resources.displayMetrics.density } + root.height.toFloat()).min()
        assertTrue("$isha is hidden: its row ends at ${lowest.toInt()} px, what lies over the page starts at ${top.toInt()} px", lowest <= top)
    }

    private fun shot(name: String, waitFor: String) {
        rule.waitUntil(20_000) { rule.onAllNodesWithText(waitFor, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("build/design").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
