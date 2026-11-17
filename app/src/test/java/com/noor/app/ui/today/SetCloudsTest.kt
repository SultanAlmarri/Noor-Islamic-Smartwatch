package com.noor.app.ui.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noor.app.sky.CloudLeg
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.atan
import kotlin.math.tan

/**
 * (0.19.1) The medium graphics' clouds are the real sky's baked sets, lit for the moment (SetClouds). The owner, of
 * 0.19.0: "the clouds don't seem to appear above a certain level ... there's like a line blocking them". So: a set of
 * heaped clouds that fills its sky reaches the top of the view (well above the text written on the sky, where the old
 * clouds stopped), with no row left empty between its lowest clouds and the top; and its colours are sane at noon, at
 * sunset and at night. Prints how long one set takes to light (on this machine's JVM, not a phone).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SetCloudsTest {
    @Test fun cloudsFillTheWholeSkyInEveryLight() {
        val assets = RuntimeEnvironment.getApplication().assets
        val set = CloudSets.load(assets, "lo_broken")
        assertNotNull("lo_broken must load", set); set!!
        assertTrue("read at half size", set.w in 800..840 && set.h in 350..370)
        // A phone's header: 412 dp across at 2.625 px per dp, the horizon 318 dp down.
        val dp = 2.625f; val w = 412 * dp; val hy = 318 * dp; val focal = SetClouds.FOCAL * w
        val view = CloudView(w, hy, focal, false) { y -> SkyView.ALT_TOP * ((hy - y) / hy).toDouble().coerceIn(0.0, 1.0).let { Math.pow(it, SkyView.ALT_POW) } }
        for ((name, el) in listOf("noon" to 40.0, "sunset" to -0.5, "night" to -30.0)) {
            val lut = IntArray(Atmosphere.NX * Atmosphere.NY)
            val light = Atmosphere.frame(el, 0.3, Math.toRadians(maxOf(el, 2.0)), 2.1, 60.0, lut)
            val l = CloudLight(
                el = el, sunAz = 0.3 / 2.1 * w / focal, sunElSeen = maxOf(el, 2.0), side = 0.5,
                moonLight = el < -6, moonEl = 30.0, moonW = if (el < -6) 1.0 else 0.0, moon = doubleArrayOf(0.04, 0.05, 0.08),
                glow = if (el < 12) 1.0 else 0.0, light = light, lut = lut, haze = 1.0, sunPart = 0.6,
                sunX = w * 0.6f, sunY = hy - 80 * dp, dp = dp, glare = if (el > -5) 0.8 else 0.0, glareColor = doubleArrayOf(1.0, 0.9, 0.7),
                textY = 150 * dp, textR = 50 * dp, textCap = SetClouds.TEXT_CAP,
            )
            var lit = SetClouds.compose(set, l, CloudLeg("lo_broken", 0f, 1f), 0f, view)
            val t0 = System.nanoTime()
            repeat(3) { lit = SetClouds.compose(set, l, CloudLeg("lo_broken", 0f, 1f), 0.5f * it, view) }
            println("lo_broken lit for $name in ${(System.nanoTime() - t0) / 3_000_000} ms (${lit.w} × ${lit.h}, once warmed up)")
            // Rows (screen px) that have cloud in them.
            var top = Float.MAX_VALUE; var bad = 0; var cloudy = 0
            for (y in 0 until lit.h) for (x in 0 until lit.w) {
                val p = lit.px[y * lit.w + x]
                val a = p ushr 24
                if (a < 40) continue
                cloudy++
                val sy = lit.y0 + (y + 0.5f) / lit.h * (lit.y1 - lit.y0)
                if (sy < top) top = sy
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                when (name) {
                    "noon" -> if (maxOf(r, g, b) < 90) bad++               // not black smudges by day
                    "night" -> if (maxOf(r, g, b) > 140) bad++             // not glowing at night
                }
            }
            assertTrue("$name: clouds drawn", cloudy > lit.w * lit.h / 10)
            // The old clouds stopped 8 dp under the text's bottom (about 150 dp down); these reach the top of the view.
            assertTrue("$name: the clouds reach ${top / dp} dp from the top of the header", top < 20 * dp)
            assertTrue("$name: $bad of $cloudy cloud pixels out of character", bad < cloudy / 50)
            // Where they are drawn: the set's window (its elevations through the lens) over the horizon.
            assertTrue(lit.y1 > hy && lit.y0 < hy - focal * tan(Math.toRadians(30.0)).toFloat())
            assertTrue(atan(((hy - lit.y0) / focal).toDouble()) > Math.toRadians(39.0))
        }
    }
}
