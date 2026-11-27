package com.noor.app

import androidx.compose.ui.graphics.compositeOver
import com.noor.app.ui.settings.IconTileLook
import com.noor.app.ui.settings.Tints
import com.noor.app.ui.theme.Backdrop
import com.noor.app.ui.theme.DayColors
import com.noor.app.ui.theme.NightColors
import com.noor.app.ui.theme.Sky
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (0.26) The rows' signs ([IconTileLook]: a faint disc of the sign's colour, the sign drawn in the colour itself) can
 * be seen wherever a list stands: on the glass over the page's sky at every height of the Sun, morning and evening,
 * in both themes, for every colour of [Tints] (WCAG 1.4.11, a graphic's 3:1 against what is round it).
 */
class IconTileContrastTest {
    @Test fun everySignStandsOutOfItsDiscEverywhere() {
        val tints = listOf(Tints.Gold, Tints.Blue, Tints.Green, Tints.Orange, Tints.Red, Tints.Pink, Tints.Purple, Tints.Indigo,
            Tints.Teal, Tints.Mint, Tints.Brown, Tints.Gray, Tints.Graphite)
        var worst = 99.0
        for (c in listOf(NightColors, DayColors)) for (evening in listOf(false, true)) {
            var e = -18.0
            while (e <= 75.0) {
                val b = Backdrop.of(e, evening, c.isDark)
                for (f in floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)) for (under in listOf(c.glass.compositeOver(b.at(f)), c.glassRaised.compositeOver(b.at(f)))) {
                    for ((i, t) in tints.withIndex()) {
                        val look = IconTileLook.of(t, c.isDark)
                        val k = Sky.contrast(look.glyph, look.fill.compositeOver(under))
                        worst = minOf(worst, k)
                        assertTrue("${if (c.isDark) "night" else "day"} tint $i sun $e ${if (evening) "pm" else "am"} at $f: $k", k >= 3.0)
                    }
                }
                e += 1.0
            }
        }
        println("worst sign contrast: $worst")
    }
}
