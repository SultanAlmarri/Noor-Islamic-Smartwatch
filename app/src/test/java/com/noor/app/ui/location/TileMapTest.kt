package com.noor.app.ui.location

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.noor.prayer.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.9.8: the map never goes dark while tiles load. With only some tiles in memory, zooming in shows the coarser tiles
 * enlarged and zooming out shows the finer ones already loaded; the tiles in the middle of the screen are fetched
 * first, and tiles that went off screen are no longer fetched.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TileMapTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val template = MapLayer.STREETS.urls.single()
    private fun url(z: Int, x: Int, y: Int) = template.replace("{z}", "$z").replace("{x}", "$x").replace("{y}", "$y")

    @Test fun neverDarkWhileTilesLoad() {
        TileCache.networkAllowed = true
        TileCache.paused = true
        val cache = TileCache.get(RuntimeEnvironment.getApplication())
        cache.release()
        val dubai = GeoPoint(25.2048, 55.2708)
        val state = MapState(dubai, 12.0)
        // Every level-12 tile around Dubai, as if loaded (a plain light colour), and nothing else.
        val n = 1 shl 12
        val cx = (Mercator.x(dubai.longitude) * n).toInt(); val cy = (Mercator.y(dubai.latitude) * n).toInt()
        val light = Bitmap.createBitmap(256, 256, Bitmap.Config.RGB_565).apply { eraseColor(0xFFE0D8C0.toInt()) }.asImageBitmap()
        for (y in cy - 6..cy + 6) for (x in cx - 6..cx + 6) cache.put(url(12, x, y), light)
        rule.setContent { TileMap(state, MapLayer.STREETS, cache, Modifier.fillMaxSize()) }

        fun darkShare(): Double {
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            var dark = 0; var all = 0
            for (y in 0 until bmp.height step 8) for (x in 0 until bmp.width step 8) { all++; if (bmp.getPixel(x, y) == 0xFF0B1A2A.toInt()) dark++ }
            return dark.toDouble() / all
        }
        assertEquals("level 12, all loaded", 0.0, darkShare(), 0.0)
        // Zoom in to 14.6: level-14/15 tiles are not loaded; the level-12 ones fill the screen, enlarged.
        rule.runOnIdle { state.zoom = 14.6 }
        assertEquals("zoomed in", 0.0, darkShare(), 0.0)
        // The tiles wanted first are those in the middle of the screen, at the level drawn.
        val wanted = cache.wantedNow()
        val level = state.level()
        val mx = (Mercator.x(dubai.longitude) * (1 shl level)).toInt(); val my = (Mercator.y(dubai.latitude) * (1 shl level)).toInt()
        assertTrue(wanted.take(4).toString(), wanted.take(4).any { it == url(level, mx, my) })
        // Zoom out to 11.2 (level 11 not loaded): the finer level-12 tiles fill the screen.
        rule.runOnIdle { state.zoom = 11.2 }
        assertTrue("zoomed out", darkShare() < 0.01)
        // Move far away: what was wanted around Dubai is no longer wanted.
        rule.runOnIdle { state.jumpTo(GeoPoint(51.5, -0.12), 11.2) }
        rule.waitForIdle()
        assertTrue(cache.wantedNow().none { it.contains("/tile/14/") || it.contains("/tile/15/") })
        TileCache.networkAllowed = false
        TileCache.paused = false
    }
}
