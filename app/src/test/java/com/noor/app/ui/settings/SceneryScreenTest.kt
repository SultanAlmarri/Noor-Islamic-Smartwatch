package com.noor.app.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.noor.app.NoorApplication
import com.noor.app.data.Scenery
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.today.Terrain
import com.noor.app.ui.today.terrainFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 0.9.8: home scenery. Automatic follows the place (Dubai: dunes; Sana'a, 2,250 m: mountains; the Maldives: sea; London:
 * hills); a choice made in Settings is kept wherever the user is.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SceneryScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun chooseScenery() {
        // (0.19) The medium and low graphics draw the scenery the real sky chooses (Biome.of), for every place and choice.
        assertEquals(Terrain.DUNES, terrainFor(Scenery.AUTO, "AE", 5.0, 25.2))
        assertEquals(Terrain.DUNES, terrainFor(Scenery.AUTO, "YE", 2250.0, 15.4))
        assertEquals(Terrain.SEA, terrainFor(Scenery.AUTO, "MV", 2.0, 4.2))
        assertEquals(Terrain.FOREST, terrainFor(Scenery.AUTO, "GB", 30.0, 51.5))
        assertEquals(Terrain.HILLS, terrainFor(Scenery.AUTO, "KE", 1795.0, -1.3))
        assertEquals(Terrain.CANYON, terrainFor(Scenery.AUTO, "TR", 900.0, 39.9))
        assertEquals(Terrain.ARCTIC, terrainFor(Scenery.AUTO, "NO", 10.0, 69.6))
        assertEquals(Terrain.SEA, terrainFor(Scenery.SEA, "AE", 5.0, 25.2))
        for (sc in Scenery.entries) assertEquals(sc.name, Terrain.of(com.noor.app.sky.Biome.of(sc, "AE", 5.0, 25.2)), terrainFor(sc, "AE", 5.0, 25.2))
        // Each of the six sceneries has its own terrain.
        assertEquals(6, com.noor.app.sky.Biome.entries.map { Terrain.of(it) }.toSet().size)

        val app = RuntimeEnvironment.getApplication() as NoorApplication
        runBlocking { app.container.settings.setManualCity("dxb"); app.container.settings.setOnboardingDone() }
        rule.setContent { NoorTheme(com.noor.app.data.ThemeMode.DARK) { com.noor.app.ui.components.NoorBackground { SceneryScreen(PaddingValues(), onBack = {}) } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Automatic").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Dunes, for Dubai", substring = true).onFirst().assertExists()
        // (0.23) The Sun's path: Simple by default (the owner's word), described under the choice.
        assertEquals(com.noor.app.data.SunPathMode.SIMPLE, runBlocking { app.container.settings.current().sunPath })
        rule.onAllNodesWithText("Always the same arc", substring = true).onFirst().assertExists()
        shot("75_scenery_en")
        // 0.10: the six sceneries of the real sky.
        assertEquals(com.noor.app.sky.Biome.DUNES, com.noor.app.sky.Biome.of(Scenery.AUTO, "AE", 5.0, 25.2))
        assertEquals(com.noor.app.sky.Biome.OCEAN, com.noor.app.sky.Biome.of(Scenery.AUTO, "MV", 2.0, 4.2))
        assertEquals(com.noor.app.sky.Biome.ARCTIC, com.noor.app.sky.Biome.of(Scenery.AUTO, "NO", 10.0, 69.6))
        assertEquals(com.noor.app.sky.Biome.MEADOW, com.noor.app.sky.Biome.of(Scenery.AUTO, "KE", 1700.0, -1.3))
        assertEquals(com.noor.app.sky.Biome.RAINFOREST, com.noor.app.sky.Biome.of(Scenery.AUTO, "GB", 30.0, 51.5))
        assertEquals(com.noor.app.sky.Biome.CANYON, com.noor.app.sky.Biome.of(Scenery.AUTO, "YE".let { "AF" }, 1800.0, 34.5))
        assertEquals(com.noor.app.sky.Biome.RAINFOREST, com.noor.app.sky.Biome.of(Scenery.HILLS, "AE", 5.0, 25.2))
        rule.onAllNodesWithText("Red rock").onFirst().performScrollTo().performClick()
        // The choice is saved on the view model's scope (DataStore writes on its own thread): let the main looper run.
        for (i in 0 until 200) {
            if (runBlocking { app.container.settings.current().scenery } == Scenery.CANYON) break
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle(); Thread.sleep(25)
        }
        assertEquals(Scenery.CANYON, runBlocking { app.container.settings.current().scenery })
        shot("76_scenery_canyon_en")
        // (0.20) The graphics level is chosen at the top of the page, and the pictures show that level at once.
        rule.onAllNodesWithText("Low").onFirst().performScrollTo().performClick()
        for (i in 0 until 200) {
            if (runBlocking { app.container.settings.current().skyGraphics } == com.noor.app.data.SkyGraphics.LOW) break
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle(); Thread.sleep(25)
        }
        assertEquals(com.noor.app.data.SkyGraphics.LOW, runBlocking { app.container.settings.current().skyGraphics })
        rule.onAllNodesWithText("The simplest sky", substring = true).onFirst().assertExists()
        shot("76b_scenery_low_en")
        // (0.23) The real Sun's path, chosen and kept.
        rule.onAllNodesWithText("Real sky").onFirst().performScrollTo().performClick()
        for (i in 0 until 200) {
            if (runBlocking { app.container.settings.current().sunPath } == com.noor.app.data.SunPathMode.REAL) break
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle(); Thread.sleep(25)
        }
        assertEquals(com.noor.app.data.SunPathMode.REAL, runBlocking { app.container.settings.current().sunPath })
        rule.onAllNodesWithText("The Sun's true path for your place and date").onFirst().assertExists()
        shot("76c_scenery_sun_real_en")
    }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("build/screenshots").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
