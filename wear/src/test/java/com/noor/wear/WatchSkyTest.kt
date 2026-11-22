package com.noor.wear

import com.noor.prayer.sky.SkyPalette
import com.noor.wear.ui.WatchSky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.25: the sky behind the watch's home screen. Whatever the Sun's height, morning or evening, the white text of the
 * next prayer must read on every colour the sky is drawn with, and the sky must still be the sky: blue by day, warm
 * at the horizon around sunrise and sunset, near black at night.
 */
class WatchSkyTest {
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test fun theTextReadsOnEverySky() {
        var checked = 0
        for (evening in listOf(false, true)) {
            var e = -90.0
            while (e <= 90.0) {
                val s = WatchSky.stops(e, evening)
                for ((name, c) in listOf("top" to s.top, "mid" to s.mid, "low" to s.low)) {
                    val ratio = SkyPalette.contrast(WatchSky.INK, c)
                    assertTrue("$name at $e° (evening $evening): ${"%.2f".format(ratio)}:1", ratio >= 4.5)
                    checked++
                }
                e += 0.25
            }
        }
        assertEquals(2 * 721 * 3, checked)
    }

    @Test fun aSkyThatAlreadyReadsIsLeftAlone() {
        // Night and the deep blue of the zenith are the phone's own colours, untouched.
        val night = SkyPalette.colors(-40.0, evening = true)
        assertEquals(night.zenith, WatchSky.stops(-40.0, true).top)
        assertEquals(night.mid, WatchSky.stops(-40.0, true).mid)
        val day = SkyPalette.colors(50.0, evening = false)
        assertEquals(day.zenith, WatchSky.stops(50.0, false).top)
    }

    @Test fun darkeningKeepsTheHue() {
        // The bright evening horizon is too light for white text: it is darkened, and stays as warm as it was.
        val raw = SkyPalette.glow(0.5, evening = true)
        val low = WatchSky.stops(0.5, evening = true).low
        assertTrue("was too bright", SkyPalette.contrast(WatchSky.INK, raw) < 4.5)
        assertTrue("warm: red over blue", r(low) > b(low) * 2)
        assertEquals(r(raw).toFloat() / b(raw), r(low).toFloat() / b(low), 0.12f)
        // As bright as the rule allows, not darker than it needs to be.
        assertTrue(SkyPalette.contrast(WatchSky.INK, low) < WatchSky.MIN_CONTRAST + 0.25)
    }

    @Test fun theSkyIsStillTheSky() {
        val noon = WatchSky.stops(55.0, evening = false)
        assertTrue("blue by day", b(noon.mid) > r(noon.mid) + 40 && b(noon.top) > r(noon.top) + 40)
        assertEquals(0f, noon.night, 0f)
        val midnight = WatchSky.stops(-45.0, evening = true)
        assertTrue("near black at night", r(midnight.top) < 16 && g(midnight.top) < 16 && b(midnight.top) < 32)
        assertEquals(1f, midnight.night, 0f)
        // Mornings are cooler than evenings at the horizon, as on the phone.
        val dawn = WatchSky.stops(0.5, evening = false).low
        val dusk = WatchSky.stops(0.5, evening = true).low
        assertTrue(b(dawn).toFloat() / r(dawn) > b(dusk).toFloat() / r(dusk))
        // The stars keep to the sky: none below the dunes' line.
        assertTrue(WatchSky.stars.all { it[0] in 0f..1f && it[1] in 0f..0.66f })
    }
}
