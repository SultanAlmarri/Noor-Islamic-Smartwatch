package com.noor.wear

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.noor.prayer.GeoPoint
import com.noor.prayer.LocalSetup
import com.noor.prayer.SetupPlace
import com.noor.wear.ui.Dest
import com.noor.wear.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real app, end to end on Robolectric: set up on the watch, home, into the tasbih and back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w225dp-h225dp-round-watch-xhdpi")
class MainActivityNavTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, substring: Boolean = false) =
        rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun welcomeThenHomeAndTasbih() {
        waitFor("Use my location")
        val app = RuntimeEnvironment.getApplication() as NoorWearApp
        val dubai = SetupPlace(GeoPoint(25.2048, 55.2708, 5.0), "Asia/Dubai", "Dubai", "دبي", "AE", "AE-DU", fromGps = false)
        runBlocking { app.config.saveSetup(LocalSetup(dubai)) }

        waitFor("Fajr")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tasbih"))
        rule.onNode(hasText("Tasbih")).performClick()
        waitFor("SubhanAllah")
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        repeat(3) { rule.onRoot().performClick(); rule.mainClock.advanceTimeBy(200) }
        rule.waitForIdle()
        Shots.save(Shots.drawView(rule.activity.window.decorView), "n01_app_tasbih")
        waitFor("3")

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        // Back on the home screen where it was left (scrolled to the tools), with the new day total on the chip.
        waitFor("Today 3")
        // The count was stored on the way.
        runBlocking { assertEquals(3, app.tasbih.current().count) }

    }
}

/** A tile or complication shortcut opens its screen directly (Robolectric has no compass, so the Qibla shows its no-compass form). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w225dp-h225dp-round-watch-xhdpi")
class ShortcutLaunchTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    @Test fun qiblaShortcut() {
        val app = RuntimeEnvironment.getApplication() as NoorWearApp
        val dubai = SetupPlace(GeoPoint(25.2048, 55.2708, 5.0), "Asia/Dubai", "Dubai", "دبي", "AE", "AE-DU", fromGps = false)
        runBlocking { app.config.saveSetup(LocalSetup(dubai)) }
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(MainActivity.intent(app, Dest.QIBLA)).use { scenario ->
            rule.waitUntil(15_000) { rule.onAllNodesWithText("from true North", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { Shots.save(Shots.drawView(it.window.decorView), "n02_app_qibla_shortcut") }
        }
    }
}
