package com.noor.prayer

import com.noor.prayer.sky.SkyPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkyPaletteTest {
    private val white = 0xFFFFFFFF.toInt()

    /** Text on the sky is white; it must stay readable at every elevation, morning and evening. */
    @Test fun whiteTextIsReadableOnTheUpperSkyAtEveryElevation() {
        for (e in -90..90) for (evening in listOf(false, true)) {
            val c = SkyPalette.colors(e.toDouble(), evening)
            // Small text sits on the zenith band: WCAG AAA (7:1).
            assertTrue(SkyPalette.contrast(white, c.zenith) >= 7.0, "zenith at $e°: ${SkyPalette.contrast(white, c.zenith)}")
            // Large text (the countdown) may reach the middle band: WCAG large-text minimum (3:1).
            assertTrue(SkyPalette.contrast(white, c.mid) >= 3.0, "mid at $e°: ${SkyPalette.contrast(white, c.mid)}")
        }
    }

    @Test fun nightFadesInWithTwilight() {
        assertEquals(0f, SkyPalette.colors(10.0, false).night)
        assertEquals(1f, SkyPalette.colors(-20.0, true).night)
        assertTrue(SkyPalette.colors(-8.0, true).night in 0.3f..0.5f)
    }

    @Test fun eveningsAreWarmerThanMornings() {
        val dawn = SkyPalette.colors(0.0, false).horizon; val dusk = SkyPalette.colors(0.0, true).horizon
        fun red(c: Int) = (c shr 16) and 0xFF
        fun blue(c: Int) = c and 0xFF
        assertTrue(red(dusk) - blue(dusk) > red(dawn) - blue(dawn))
    }

    @Test fun mixingIsExactAtTheEnds() {
        val a = 0xFF102030.toInt(); val b = 0xFFF0E0D0.toInt()
        assertEquals(a, SkyPalette.mix(a, b, 0f)); assertEquals(b, SkyPalette.mix(a, b, 1f))
    }
}
