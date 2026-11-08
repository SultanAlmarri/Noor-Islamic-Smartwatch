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
 * 0.9.9: the clouds cut out of photographs (tools/sky/photo_clouds.py). The image decodes as Android decodes it; the
 * table names every cloud inside the image, each kind the sky needs is there, each copy is smaller than the one before;
 * and no cloud touches the sides or top of its picture (it would show a straight edge in the sky — 0.9.9's first cuts
 * did), nor its bottom unless its base is meant to hide behind the land.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CloudAtlasTest {
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw")).first { it.isDirectory }

    @Test fun everyCloudIsWholeAndWhereTheTableSays() {
        val bytes = File(raw, "clouds.webp").readBytes()
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue("clouds.webp must decode", image != null)
        assertTrue("small enough for any phone's GPU", image.width <= 2048 && image.height <= 2048)
        val table = CloudTable.parse(File(raw, "cloud_atlas.txt").readText())
        assertTrue(table.sprites.size >= 12)
        for (kind in listOf(CloudTable.HUMILIS, CloudTable.MEDIOCRIS, CloudTable.CONGESTUS, CloudTable.CUMULONIMBUS)) {
            assertTrue("no cloud of kind $kind", table.ofKind(kind).isNotEmpty())
        }
        table.sprites.forEachIndexed { i, s ->
            assertTrue(s.levels.isNotEmpty())
            assertEquals(s.w, s.levels[0][2]); assertEquals(s.h, s.levels[0][3])
            s.levels.zipWithNext().forEach { (a, b) -> assertTrue("cloud $i: copies get smaller", b[2] < a[2]) }
            for (l in s.levels) assertTrue("cloud $i outside the image", l[0] >= 0 && l[1] >= 0 && l[0] + l[2] <= image.width && l[1] + l[3] <= image.height)
            if (s.cut) assertEquals("a cut cloud's base is its bottom", 0, s.base)
            // The largest copy: opaque nowhere on its sides or top (nor its bottom unless cut).
            val (x0, y0, w, h) = s.levels[0].toList()
            fun a(x: Int, y: Int) = image.getPixel(x0 + x, y0 + y) ushr 24
            val edge = ArrayList<Int>()
            for (y in 0 until h) { edge += a(0, y); edge += a(w - 1, y) }
            for (x in 0 until w) { edge += a(x, 0); if (!s.cut) edge += a(x, h - 1) }
            val worst = edge.max()
            assertTrue("cloud $i is cut by its picture (alpha $worst at an edge)", worst < 40)
        }
    }
}
