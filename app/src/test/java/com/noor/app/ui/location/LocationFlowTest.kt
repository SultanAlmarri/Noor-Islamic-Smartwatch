package com.noor.app.ui.location

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.noor.app.NoorApplication
import com.noor.app.data.LocationMode
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.GeoPoint
import com.noor.prayer.geo.Gazetteer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 0.9.7: choosing a location through the real screens, as a user does it: coordinates typed in the search box, a
 * search by name, and country → city → area. Each ends on the confirmation (exact point, time zone, today's times)
 * and saves exactly that point and zone. Software renders (no network here: the map shows itself offline) are saved
 * for review. One test method: DataStore is a per-process singleton.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class LocationFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun chooseByCoordinatesSearchAndBrowsing() {
        TileCache.networkAllowed = false
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        runBlocking { app.container.settings.setManualCity("dxb"); app.container.settings.setOnboardingDone() }
        var arabic by mutableStateOf(false)
        var done = 0
        var key by mutableIntStateOf(0)
        rule.setContent {
            val cfg = android.content.res.Configuration(LocalConfiguration.current).apply { setLocale(java.util.Locale(if (arabic) "ar" else "en")) }
            val ctx = androidx.compose.ui.platform.LocalContext.current.createConfigurationContext(cfg)
            CompositionLocalProvider(LocalConfiguration provides cfg, androidx.compose.ui.platform.LocalContext provides ctx,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides rule.activity,
                androidx.lifecycle.compose.LocalLifecycleOwner provides rule.activity,
                // A new screen each time, as the app's navigation gives it (its own view model).
                androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides androidx.compose.runtime.remember(key) {
                    object : androidx.lifecycle.ViewModelStoreOwner, androidx.lifecycle.HasDefaultViewModelProviderFactory {
                        override val viewModelStore = androidx.lifecycle.ViewModelStore()
                        override val defaultViewModelProviderFactory get() = rule.activity.defaultViewModelProviderFactory
                        override val defaultViewModelCreationExtras get() = rule.activity.defaultViewModelCreationExtras
                    } }) {
                NoorTheme(com.noor.app.data.ThemeMode.DARK) {
                    androidx.compose.runtime.key(key) {
                        com.noor.app.ui.components.NoorBackground { LocationScreen(PaddingValues(), onDone = { done++ }) }
                    }
                }
            }
        }
        fun waitText(t: String, ms: Long = 30_000) = rule.waitUntil(ms) { rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
        fun click(t: String) { waitText(t); rule.onAllNodesWithText(t, substring = true).onFirst().performClick(); rule.waitForIdle() }
        fun type(t: String) { rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(t); rule.waitForIdle() }
        fun shot(name: String) {
            rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(File("build/screenshots").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun saved() = runBlocking { app.container.settings.current() }
        // The save runs on the view model's scope (DataStore writes on its own thread): let the main looper run it.
        fun waitDone(n: Int) {
            for (i in 0 until 400) {
                if (done >= n) return
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle(); Thread.sleep(25)
            }
            throw AssertionError("not saved ($done of $n)")
        }

        // 1 · Home.
        waitText("Browse by country"); shot("60_location_home_en")

        // 2 · Coordinates typed in the search box (Makkah): the exact point and Makkah's zone are saved.
        type("21.42250, 39.82620")
        waitText("Use these coordinates"); shot("61_location_coordinates_en")
        click("Use these coordinates")
        waitText("Asia/Riyadh"); waitText("Fajr"); shot("62_location_confirm_makkah_en")
        click("Use this location")
        waitDone(1)
        saved().let { s ->
            assertEquals(LocationMode.MANUAL, s.locationMode)
            assertEquals(21.4225, s.manualPlace!!.point.latitude, 1e-9)
            assertEquals(39.8262, s.manualPlace!!.point.longitude, 1e-9)
            assertEquals("Asia/Riyadh", s.manualPlace!!.zoneId)
            assertEquals("SA", s.manualPlace!!.countryCode)
        }

        // 3 · Search: a district of Dubai, by name.
        key++
        waitText("Browse by country")
        type("Dubai Marina")
        waitText("District"); shot("63_location_search_en")
        click("District, Dubai") // the result row (the first "Dubai Marina" is the search box itself)
        waitText("Asia/Dubai"); waitText("Maghrib"); shot("64_location_confirm_marina_en")
        click("Use this location")
        waitDone(2)
        saved().manualPlace!!.let { m ->
            assertEquals("Dubai Marina", m.placeName)
            assertEquals("Asia/Dubai", m.zoneId)
            assertEquals("AE-DU", m.regionCode)
            assertTrue(Gazetteer.distanceKm(m.point, GeoPoint(25.08, 55.14)) < 3)
        }

        // 4 · Browse: country → city → area.
        key++
        click("Browse by country")
        waitText("United Arab Emirates"); shot("65_location_countries_en")
        click("United Arab Emirates")
        // 0.9.8: the country first offers its largest cities and its emirates.
        waitText("EMIRATES"); waitText("Largest: Ras Al Khaimah"); shot("66_location_cities_ae_en")
        click("Largest: Ras Al Khaimah")
        waitText("Find a place in Ras Al Khaimah"); shot("66b_location_region_rak_en")
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitText("Largest: Ras Al Khaimah") // back on the emirates
        click("Dubai")
        waitText("Areas of Dubai"); waitText("Dubai, centre"); shot("67_location_areas_dubai_en")
        // The list is long (lists draw only what is on screen): find the area by name, as a user would.
        type("karama")
        waitText("Al Karama"); click("Al Karama")
        waitText("Asia/Dubai"); shot("68_location_confirm_karama_en")
        click("Use this location")
        waitDone(3)
        assertEquals("Al Karama", saved().manualPlace!!.placeName)

        // 5 · The map (offline here: it says so; the pin and the coordinates are still there).
        key++
        click("Choose on the map")
        waitText("Use this location"); shot("69_location_map_offline_en")
        waitText("needs an internet connection")

        // 6 · Arabic: home, a search in Arabic, and the confirmation.
        arabic = true; key++
        waitText("التصفح حسب الدولة"); shot("70_location_home_ar")
        type("الإسكندرية")
        waitText("مصر"); shot("71_location_search_ar")
        rule.onAllNodesWithText("مصر", substring = true).onFirst().performClick(); rule.waitForIdle()
        waitText("الفجر"); shot("72_location_confirm_ar")
        // Arabic only: no Latin letters on the Arabic confirmation (coordinates and time zone included).
        val texts = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }.orEmpty().map { it.text } }
        val latin = texts.filter { Regex("[A-Za-z]").containsMatchIn(it) }
        assertTrue("Latin text on the Arabic confirmation: $latin", latin.isEmpty())
        assertTrue(texts.any { it.contains("ش") && it.contains("ق") && it.contains("31.2") })
    }
}
