package com.noor.app.ui.location

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.noor.app.NoorApplication
import com.noor.app.ui.theme.NoorTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 0.9.7: the location screen on a small phone (360 × 640 dp): nothing cut off, the buttons on screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class LocationSmallScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun smallPhone() = renderLocation(rule, "small")
}

/** 0.9.7: on a foldable's inner screen (about 840 × 900 dp): lists and cards keep a readable width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h900dp-xhdpi")
class LocationWideScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun foldInnerScreen() = renderLocation(rule, "wide")
}

internal fun renderLocation(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, ComponentActivity>, size: String) {
    TileCache.networkAllowed = false
    val app = RuntimeEnvironment.getApplication() as NoorApplication
    runBlocking { app.container.settings.setManualCity("dxb"); app.container.settings.setOnboardingDone() }
    rule.setContent { NoorTheme(com.noor.app.data.ThemeMode.DARK) { com.noor.app.ui.components.NoorBackground { LocationScreen(PaddingValues(), onDone = {}) } } }
    fun waitText(t: String) = rule.waitUntil(30_000) { rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
    fun shot(name: String) {
        rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        File(File("build/screenshots").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    waitText("Browse by country"); shot("73_location_home_$size")
    rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("21.4225, 39.8262"); rule.waitForIdle()
    waitText("Use these coordinates")
    rule.onAllNodesWithText("Use these coordinates").onFirst().performClick(); rule.waitForIdle()
    waitText("Isha"); shot("74_location_confirm_$size")
    // Both buttons are on screen, whole.
    val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
    for (label in listOf("Use this location", "Adjust on the map")) {
        val b = rule.onAllNodesWithText(label).onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue("$label at $b in $root", b.bottom <= root.bottom + 0.5f && b.top >= root.top && b.left >= root.left && b.right <= root.right + 0.5f)
    }
}
