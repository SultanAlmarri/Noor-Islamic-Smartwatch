package com.noor.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 0.9.9: the field of photographed clouds (CloudField). Every weather but a clear sky has a sky full of clouds, spread
 * across the view; fair-weather cumulus stay a sensible size beside the land (0.9.8's were bigger than the mountains);
 * clouds with a base stand above the horizon, those whose base was cut off hide it below; they are drawn far to near
 * and the same every time; and they are lit by the moment: near-white in the midday Sun, dark on a moonless night.
 */
class CloudFieldTest {
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw")).first { it.isDirectory }
    private val table = CloudTable.parse(File(raw, "cloud_atlas.txt").readText())
    private val dp = 2.625f
    private val w = 412 * dp; private val h = 400 * dp; private val horizon = 318 * dp

    private fun frame(regime: Int, sunEl: Double, time: Float = 120f, night: Float = 0f, seed: Long = 7): CloudField.Frame {
        val lut = IntArray(Atmosphere.NX * Atmosphere.NY)
        val light = Atmosphere.frame(sunEl, 0.3, Math.toRadians(sunEl.coerceAtLeast(0.0)), 2.1, 40.0, lut)
        val weather = Weather.of(regime, java.util.Random(seed))
        return CloudField.Frame(w, h, horizon, dp, 2.1, time, weather, 0.3, sunEl, light, lut, night, 0f)
    }

    private fun layout(f: CloudField.Frame) = ArrayList<CloudDraw>().let { out -> val n = CloudField.layout(table, f, out); out.take(n) }

    @Test fun everyWeatherButClearHasASkyOfClouds() {
        assertEquals(0, layout(frame(0, 40.0)).size)
        for (regime in 1..5) {
            val c = layout(frame(regime, 40.0))
            assertTrue("regime $regime: ${c.size} clouds", c.size in 8..40)
            // Spread across the view, not bunched on one side.
            val left = c.count { (it.left + it.right) / 2 < w / 2 }
            assertTrue("regime $regime: $left of ${c.size} on the left", left in c.size / 5..c.size * 4 / 5)
        }
    }

    @Test fun cloudsKeepTheirPlaceAndSize() {
        for (regime in 1..5) for (seed in 1L..6L) {
            val f = frame(regime, 35.0, seed = seed)
            val c = layout(f)
            c.zipWithNext().forEach { (a, b) -> assertTrue("drawn far to near", a.distance >= b.distance) }
            for (d in c) {
                val s = table.sprites[d.sprite]
                val width = (d.right - d.left) / w
                if (s.kind == CloudTable.HUMILIS || s.kind == CloudTable.FRACTUS) assertTrue("a small cumulus ${width * 100}% of the view wide", width < 0.30f)
                if (s.kind == CloudTable.MEDIOCRIS) assertTrue("a cumulus ${width * 100}% of the view wide", width < 0.55f)
                if (s.cut) assertTrue("a base cut off in its photograph hides below the horizon", d.bottom > horizon)
                else assertTrue("a cloud's base stands above the horizon", d.bottom - s.base * (d.bottom - d.top) / s.h <= horizon + 1f)
            }
        }
    }

    @Test fun theSameEveryTimeAndDriftingSlowly() {
        val a = layout(frame(2, 30.0, time = 500f)); val b = layout(frame(2, 30.0, time = 500f))
        assertEquals(a.map { it.left }, b.map { it.left })
        val later = layout(frame(2, 30.0, time = 501f))
        // In one second no cloud moves more than a few pixels.
        a.zip(later).filter { it.first.sprite == it.second.sprite && it.first.distance == it.second.distance }
            .forEach { (x, y) -> assertTrue(kotlin.math.abs(x.left - y.left) < 3f * dp) }
    }

    @Test fun litByTheMoment() {
        // The brightest part of a near cloud (brightness 1 in its photograph): near white at midday.
        val noon = layout(frame(1, 60.0)).filter { it.distance < 20f }
        assertTrue(noon.isNotEmpty())
        for (c in noon) for (j in 0..2) assertTrue("midday cloud channel $j = ${c.matrix[j * 5] + c.matrix[j * 5 + 4] / 255}", c.matrix[j * 5] + c.matrix[j * 5 + 4] / 255 > 0.72f)
        // A moonless night: dark.
        val night = layout(frame(1, -30.0, night = 1f))
        for (c in night) for (j in 0..2) assertTrue(c.matrix[j * 5] + c.matrix[j * 5 + 4] / 255 < 0.25f)
    }
}
