package com.noor.app.ui.adhan

import androidx.compose.ui.graphics.compositeOver
import com.noor.app.ui.theme.Backdrop
import com.noor.app.ui.theme.DayColors
import com.noor.app.ui.theme.NightColors
import com.noor.app.ui.theme.Sky
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (0.26 review) The one day selector ([WeekDays], [WeekColors]) reads in both themes, wherever its card stands on the
 * page's sky, at every height of the Sun, morning and evening: a day that is on, its dark letter on the gold disc
 * (4.5:1); a day that is off, its bare letter on the glass (4.5:1); and a day that is on stands apart from the glass
 * around it (3:1, WCAG's for the parts of a control: the gold disc at night, its deeper rim by day, where the gold
 * itself is light beside the white glass).
 */
class DayLettersContrastTest {
    @Test fun theDaysReadInBothThemes() {
        val worst = mutableMapOf<String, Double>()
        for (c in listOf(NightColors, DayColors)) {
            val theme = if (c.isDark) "night" else "day"
            fun note(what: String, k: Double) { val key = "$theme $what"; worst[key] = minOf(worst[key] ?: 99.0, k) }
            note("letter on the gold", Sky.contrast(WeekColors.onFill(c), WeekColors.fill(c)))
            for (evening in listOf(false, true)) for (e in -18..75) {
                val b = Backdrop.of(e.toDouble(), evening, c.isDark)
                for (k in 0..10) for (glass in listOf(c.glass, c.glassRaised)) {
                    val card = glass.compositeOver(b.at(k / 10f))
                    note("bare letter on the glass", Sky.contrast(WeekColors.letter(c).compositeOver(card), card))
                    note("a day that is on, beside the glass", Sky.contrast((WeekColors.rim(c) ?: WeekColors.fill(c)).compositeOver(card), card))
                }
            }
        }
        println("day selector, worst contrast: $worst")
        for ((what, k) in worst) assertTrue("$what: $k", k >= if ("letter" in what) 4.5 else 3.0)
    }
}
