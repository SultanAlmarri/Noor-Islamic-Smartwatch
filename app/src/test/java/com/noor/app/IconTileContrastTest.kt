package com.noor.app

import com.noor.app.ui.settings.IconTileLook
import com.noor.app.ui.settings.Tints
import com.noor.app.ui.theme.Sky
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (0.26) The rows' signs ([IconTileLook]: a small sky of the sign's colour, as the home screen's prayer discs, with the
 * sign in white) can be seen: the white sign stands out of its sky wherever the sign is drawn on it (the middle half
 * of the disc, from a quarter of the way down to three quarters), for every colour of [Tints] (WCAG 1.4.11, a
 * graphic's 3:1). The disc is its own sky, so the page behind it does not matter.
 */
class IconTileContrastTest {
    @Test fun everySignStandsOutOfItsSky() {
        val tints = listOf(Tints.Gold, Tints.Blue, Tints.Green, Tints.Orange, Tints.Red, Tints.Pink, Tints.Purple, Tints.Indigo,
            Tints.Teal, Tints.Mint, Tints.Brown, Tints.Gray, Tints.Graphite)
        var worst = 99.0
        for ((i, t) in tints.withIndex()) {
            val look = IconTileLook.of(t)
            var y = 0.25f
            while (y <= 0.75f) {
                val k = Sky.contrast(look.glyph, look.at(y))
                worst = minOf(worst, k)
                assertTrue("tint $i at $y: $k", k >= 3.0)
                y += 0.05f
            }
        }
        println("worst sign contrast: $worst")
    }
}
