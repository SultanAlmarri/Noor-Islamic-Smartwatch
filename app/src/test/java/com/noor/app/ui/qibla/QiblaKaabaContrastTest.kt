package com.noor.app.ui.qibla

import com.noor.app.ui.theme.DayColors
import com.noor.app.ui.theme.NightColors
import com.noor.app.ui.theme.Sky
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.25, second review: the Kaaba's sign on the dial stands out from its disc, by night and by day, whether the disc is
 * gold (turning) or green (facing the Qibla): at least 3:1, the measure for graphics. By day it was black on the deep
 * green (about 3.1:1, and it looked darker still); it is white now.
 */
class QiblaKaabaContrastTest {
    @Test fun theKaabasSignReadsOnItsDisc() {
        for ((name, c) in listOf("night" to NightColors, "day" to DayColors)) {
            for ((disc, fill) in listOf("gold" to c.gold, "green" to c.success)) {
                val r = Sky.contrast(kaabaGlyph(c), fill)
                assertTrue("$name: the sign on the $disc disc, $r", r >= 3.0)
            }
        }
        // By day on the green the white sign is the clearly better of the two.
        assertTrue(Sky.contrast(kaabaGlyph(DayColors), DayColors.success) > Sky.contrast(DayColors.onGold, DayColors.success))
    }
}
