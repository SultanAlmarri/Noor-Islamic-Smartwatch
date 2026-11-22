package com.noor.app

import com.noor.app.ui.components.TonalColors
import com.noor.app.ui.theme.Backdrop
import com.noor.app.ui.theme.DayColors
import com.noor.app.ui.theme.NightColors
import com.noor.app.ui.theme.Sky
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (0.25, second review) The second kind of button ([TonalColors]: the text's ink on a neutral fill) reads wherever it
 * stands: on the bare page and on the glass, at every height of the Sun, morning and evening, in both themes (WCAG AA,
 * 4.5:1). And what stands on the gold of the main button reads too.
 */
class ButtonContrastTest {
    @Test fun theSecondButtonsLabelReadsOnItsFillEverywhere() {
        var worst = 99.0
        for (c in listOf(NightColors, DayColors)) for (evening in listOf(false, true)) {
            var e = -18.0
            while (e <= 75.0) {
                val b = Backdrop.of(e, evening, c.isDark)
                for (f in floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)) for (under in listOf(b.at(f), c.glass.compositeOver(b.at(f)), c.glassRaised.compositeOver(b.at(f)))) {
                    val fill = TonalColors.fill(c).compositeOver(under)
                    val k = Sky.contrast(TonalColors.label(c), fill)
                    worst = minOf(worst, k)
                    assertTrue("${if (c.isDark) "night" else "day"} sun $e ${if (evening) "pm" else "am"} at $f: $k", k >= 4.5)
                }
                e += 1.0
            }
            assertTrue("onGold on goldFill", Sky.contrast(c.onGold, c.goldFill) >= 4.5)
        }
        println("worst tonal label contrast: $worst")
    }
}
