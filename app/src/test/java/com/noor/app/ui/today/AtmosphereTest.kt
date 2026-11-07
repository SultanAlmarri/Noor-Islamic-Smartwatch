package com.noor.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/** The computed sky (0.9.6): readable text at every height of the Sun, no jumps through twilight, believable colours. */
class AtmosphereTest {
    private val nx = Atmosphere.NX
    private val ny = Atmosphere.NY

    private fun lut(el: Double, az: Double = 0.6, cap: Double = 16.0): IntArray =
        IntArray(nx * ny).also { Atmosphere.frame(el, az, el.coerceAtLeast(0.0) * 0.4 * PI / 180, 2.1, cap, it) }

    private fun lum(argb: Int): Double {
        fun ch(s: Int) = Atmosphere.linear(((argb shr s) and 0xFF) / 255.0)
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    /**
     * White text on the sky keeps its contrast whatever the Sun is doing: at least 4.5:1 where the small text is, at
     * least 3:1 (large text) just above the text's bottom edge where the countdown is.
     */
    @Test fun whiteTextStaysReadableAtEveryElevation() {
        val cap = 16.0
        for (e in -40..90 step 2) for (az in listOf(-0.9, 0.0, 0.9)) {
            val t = lut(e.toDouble(), az, cap)
            for (j in 0 until ny) {
                val alt = Math.pow(j / (ny - 1.0), Atmosphere.LUT_POW) * 90
                val need = when { alt >= cap + 12 -> 4.45; alt >= cap -> 2.95; else -> continue }
                for (i in 0 until nx) {
                    val c = (1.05) / (lum(t[j * nx + i]) + 0.05)
                    assertTrue("el $e az $az row $j (alt $alt): contrast $c", c >= need)
                }
            }
        }
    }

    /**
     * Through sunrise and sunset, a small step of the Sun is a small change of the sky: no sudden switches. (Deep in
     * twilight the last glow over the horizon changes quickly by nature; a switch would show as a far larger step.)
     */
    @Test fun twilightChangesSmoothly() {
        var prev = lut(-22.0)
        var worst = 0
        var e = -22.0
        while (e < 14.0) {
            e += 0.1
            val cur = lut(e)
            for (k in cur.indices) for (s in intArrayOf(16, 8, 0)) worst = max(worst, abs(((cur[k] shr s) and 0xFF) - ((prev[k] shr s) and 0xFF)))
            prev = cur
        }
        assertTrue("largest step between 0.1° of Sun: $worst/255", worst <= 20)
    }

    @Test fun daytimeIsBlueAndNightIsDark() {
        val noon = lut(60.0)
        val top = noon[(ny - 4) * nx + nx / 2]
        assertTrue("blue zenith", (top and 0xFF) > ((top shr 16) and 0xFF) + 40)
        val night = lut(-35.0)
        night.forEach { assertTrue("night luminance ${lum(it)}", lum(it) < 0.05) }
        // At sunset the horizon towards the Sun is warm: more red than blue.
        val dusk = lut(-1.0, az = 0.6)
        val sunColumn = ((0.6 / 2.1 + 0.5) * (nx - 1)).toInt()
        val h = dusk[sunColumn]
        assertTrue("warm horizon", ((h shr 16) and 0xFF) > (h and 0xFF) + 30)
        assertEquals(1.0, Atmosphere.night(-30.0), 1e-9)
        assertEquals(0.0, Atmosphere.night(5.0), 1e-9)
    }

    /** A table is computed for every frame while the Sun is being dragged, off the main thread; it must stay quick. */
    @Test fun aTableIsQuickToCompute() {
        Atmosphere.warmUp()
        repeat(5) { lut(10.0 + it) }
        val t0 = System.nanoTime()
        repeat(20) { lut(-5.0 + it) }
        val ms = (System.nanoTime() - t0) / 1e6 / 20
        println("Atmosphere table: %.1f ms on this machine".format(ms))
        assertTrue("$ms ms", ms < 40)
    }

    @Test fun landIsFixedForAPlaceAndOpaqueForTheGpu() {
        val a = LandProfile.of(1234L, Terrain.HILLS)
        val b = LandProfile.of(1234L, Terrain.HILLS)
        assertEquals(a.height(0, 0.37f), b.height(0, 0.37f), 0f)
        for (l in 0 until LandProfile.LAYERS) for (i in 0..100) assertTrue(a.height(l, i / 100f) in 0f..1f)
        a.table().forEach { assertEquals(0xFF, (it ushr 24) and 0xFF) }
        LandProfile.of(99L, Terrain.DUNES).table().forEach { assertEquals(0xFF, (it ushr 24) and 0xFF) }
    }
}
