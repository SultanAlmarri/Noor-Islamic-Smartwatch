package com.noor.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.noor.app.ui.opening.Opening
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app itself, started as a phone starts it (0.14): a first run opens on the welcome page behind the launch
 * screen's dissolving veil, and "Choose a city instead" leads to the location page and stays there. Before 0.14 the
 * navigation was rebuilt on the home screen the moment the welcome page was recorded as done, so that button could
 * never reach the list of ways to choose a place. Robolectric: the app's real code and navigation, no phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class MainFlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun theWelcomePageLeadsToTheLocationPageAndStaysThere() {
        com.noor.app.sky.RealSky.forceOff = true
        com.noor.app.ui.today.SkyCompute.synchronous = true
        Opening.playedInThisProcess = false
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(30_000) { rule.onAllNodesWithText("Choose a city instead").fetchSemanticsNodes().isNotEmpty() }
            // A first run is not the home screen's opening: the launch screen only dissolves, once for the process.
            assertTrue(Opening.playedInThisProcess)
            rule.waitForIdle()
            rule.onAllNodesWithText("Choose a city instead").onFirst().performClick()
            rule.waitUntil(30_000) { rule.onAllNodesWithText("Browse by country").fetchSemanticsNodes().isNotEmpty() }
            // The welcome page is recorded as done (on the settings' own thread): once it is, the location page is
            // still the page on show.
            for (i in 0 until 400) {
                if (runBlocking { app.container.settings.current().onboardingDone }) break
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(25)
            }
            assertTrue(runBlocking { app.container.settings.current().onboardingDone })
            repeat(20) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeBy(100); Thread.sleep(10) }
            rule.waitForIdle()
            assertTrue("The location page is still on show", rule.onAllNodesWithText("Browse by country").fetchSemanticsNodes().isNotEmpty())
            assertTrue("Not thrown back to the home screen", rule.onAllNodesWithText("Set location").fetchSemanticsNodes().isEmpty())
        }
    }
}
