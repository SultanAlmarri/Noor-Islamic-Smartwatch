package com.noor.app.ui.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * (0.20 candidate) The medium graphics' land from the real sky's own pictures (PictureLand). For each of the six
 * sceneries: its band reads; lit at noon, at sunset and at night it covers the header from its skyline to its foot, and
 * stands where the real sky puts it (its top agrees with the real sky's own skyline, land.json); by day it is not dark,
 * by night it does not glow; where the scenery has water, there is a mirror. Prints how long one lighting takes (this
 * machine's JVM, not a phone) and the memory the band takes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PictureLandTest {
    @Test fun everySceneryIsLitWhereTheRealSkyPutsIt() {
        val assets = RuntimeEnvironment.getApplication().assets
        // A phone's header: 412 dp across at 2.625 px per dp, the horizon 345 dp down, the header 425 dp high.
        val dp = 2.625f; val w = 412 * dp; val hy = 345 * dp; val bottom = 425 * dp
        val alt: (Float) -> Double = { y -> SkyView.ALT_TOP * ((hy - y) / hy).toDouble().coerceIn(0.0, 1.0).let { Math.pow(it, SkyView.ALT_POW) } }
        for (t in listOf(Terrain.DUNES, Terrain.HILLS, Terrain.SEA, Terrain.FOREST, Terrain.ARCTIC, Terrain.CANYON)) {
            val id = LandPictures.idOf(t)!!
            val p = LandPictures.load(assets, id)
            assertNotNull("$id must read", p); p!!
            val bytes = p.alb.size + p.alpha.size + p.k.size + p.dist.size * 4 + (p.water?.size ?: 0)
            val json = org.json.JSONObject(assets.open("sky2/land/$id/land.json").bufferedReader().use { it.readText() })
            val skyline = json.optJSONArray("skyline")
            for ((name, el) in listOf("noon" to 40.0, "sunset" to -0.5, "night" to -30.0)) {
                val lut = IntArray(Atmosphere.NX * Atmosphere.NY)
                val light = Atmosphere.frame(el, 0.3, Math.toRadians(maxOf(el, 2.0)), 2.1, 60.0, lut)
                val l = LandLight(
                    el = el, side = 0.6, moonLight = el < -6, moonEl = 30.0, glow = if (el < 12) 1.0 else 0.0, moonW = if (el < -6) 1.0 else 0.0,
                    moon = doubleArrayOf(0.05, 0.062, 0.095), light = light, lut = lut, night = light.night,
                    shade = 0.0, overcast = 0.0, haze = 1.0, fog = 0.0, sunX = w * 0.6f, sunY = hy - 80 * dp, dp = dp,
                    sunDir = doubleArrayOf(0.3, 0.2, 0.93), glade = if (el > 0) 0.4 else 0.0, gladeColor = doubleArrayOf(1.0, 0.9, 0.7),
                    page = doubleArrayOf(0.03, 0.05, 0.08), bottom = bottom,
                )
                var lit = PictureLand.compose(p, l, w, hy, false, alt)
                val t0 = System.nanoTime()
                repeat(2) { lit = PictureLand.compose(p, l, w, hy, false, alt) }
                if (name == "noon") println("$id lit in ${(System.nanoTime() - t0) / 2_000_000} ms (${lit.w} × ${lit.h}; the band takes ${bytes / 1024} KB, ~${(bytes + p.w * p.h) / 1024} KB with the Sun pictures of one moment)")
                // The foot of the header (just above the fade into the page) is land or water all across.
                val footY = bottom - 30 * dp
                val row = ((footY - lit.y0) / (lit.y1 - lit.y0) * lit.h).toInt()
                assertTrue("$id $name: the band reaches the header's foot", row in 0 until lit.h)
                val covered = (0 until lit.w).count { (lit.px[row * lit.w + it] ushr 24) > 200 }
                assertTrue("$id $name: the land covers the foot of the header ($covered of ${lit.w})", covered > lit.w * 0.97)
                // By day not dark, by night not glowing (the mean of the lit pixels).
                var sum = 0.0; var n = 0
                for (q in lit.px) if ((q ushr 24) > 200) { sum += ((q shr 16 and 0xFF) + (q shr 8 and 0xFF) + (q and 0xFF)) / 3.0; n++ }
                val mean = sum / n
                when (name) {
                    "noon" -> assertTrue("$id noon: mean $mean", mean > 30)
                    "night" -> assertTrue("$id night: mean $mean", mean < 70)
                }
                // Where the real sky puts it: the land's top in the middle of the screen against land.json's skyline.
                if (skyline != null && name == "noon") {
                    val x = lit.w / 2
                    var top = -1
                    for (y in 0 until lit.h) if ((lit.px[y * lit.w + x] ushr 24) > 127) { top = y; break }
                    val topY = lit.y0 + (top + 0.5f) / lit.h * (lit.y1 - lit.y0)
                    val tan = skyline.getDouble(skyline.length() / 2)
                    val expect = hy - tan.toFloat() * 1.15f * w
                    assertTrue("$id: the land's top ${topY / dp} dp, the real sky's skyline ${expect / dp} dp", kotlin.math.abs(topY - expect) < 6 * dp)
                }
            }
            if (p.water != null) assertTrue("$id: water", p.water.count { (it.toInt() and 0xFF) > 127 } > 100)
        }
    }
}
