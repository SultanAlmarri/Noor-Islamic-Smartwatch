package com.noor.app.ui.today

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 0.9.8: the baked cumulus (tools/sky/build_clouds.py). The two images match in size and layout; every cloud keeps
 * clear of its sprite's edges (no cut sides, nothing bleeding into the next sprite); and the heights the sky shader
 * assumes (cloudTop()) cover each cloud's real height, so that a cloud placed under the text is never cut by it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CloudAtlasTest {
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw")).first { it.isDirectory }

    @Test fun atlasMatchesTheShader() {
        fun load(name: String) = File(raw, name).readBytes().let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val a = load("clouds_a.png"); val b = load("clouds_b.png")
        assertEquals(768, a.width); assertEquals(960, a.height); assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        val src = File(raw, "sky_layer.agsl").readText()
        val tops = Regex("""float cloudTop\(float si\) \{[^}]*?return ([^;]+);""").find(src)!!.groupValues[1]
            .let { Regex("""\? ([0-9.]+)|: ([0-9.]+)$""").findAll(it.trim()).map { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toDouble() }.toList() }
        assertEquals(8, tops.size)
        val nx = 384; val ny = 192; val base = 7
        for (i in 0 until 8) {
            val x0 = (i % 2) * nx; val y0 = (i / 2) * ny
            var minRow = ny; var minCol = nx; var maxCol = 0
            for (y in 0 until ny) for (x in 0 until nx) {
                if ((a.getPixel(x0 + x, y0 + y) ushr 24) > 8) { minRow = minOf(minRow, y); minCol = minOf(minCol, x); maxCol = maxOf(maxCol, x) }
            }
            assertTrue("cloud $i touches its sprite's sides ($minCol..$maxCol)", minCol >= 6 && maxCol <= nx - 7)
            val reach = (ny - 1 - base - minRow).toDouble() / ny
            assertTrue("cloud $i reaches $reach of its sprite; the shader assumes ${tops[i]}", tops[i] >= reach)
        }
    }
}
