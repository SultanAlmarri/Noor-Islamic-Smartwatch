package com.noor.app

import androidx.compose.ui.graphics.compositeOver
import com.noor.app.ui.adhan.SegmentedColors
import com.noor.app.ui.theme.Backdrop
import com.noor.app.ui.theme.DayColors
import com.noor.app.ui.theme.NightColors
import com.noor.app.ui.theme.Sky
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The segmented control (0.25, one for the whole app): the chosen segment's label on the thumb and the other labels on
 * the track must be readable (WCAG AA, 4.5:1) wherever the control stands (on the page's sky, on a card's glass, on a
 * sheet), at every height of the Sun, morning and evening, in both themes. And at night the thumb must stand out from
 * the track: until 0.24 the two had the same luminance, and only the label's weight said which segment was chosen.
 */
class SegmentedContrastTest {
    @Test fun theLabelsAreReadableOnTheirFillsAndTheChosenSegmentStandsOut() {
        val worst = mutableMapOf<String, Double>()
        val where = mutableMapOf<String, String>()
        fun note(key: String, r: Double, at: String) { if (r < (worst[key] ?: 99.0)) { worst[key] = r; where[key] = at } }
        for ((name, c) in listOf("night" to NightColors, "day" to DayColors)) {
            for (e in -90..90) for (evening in listOf(false, true)) {
                val b = Backdrop.of(e.toDouble(), evening, c.isDark)
                for (k in 0..10) {
                    val sky = b.at(k / 10f)
                    // What the control stands on: the page itself, a card of glass, a sheet (the glass made solid).
                    val grounds = mapOf("page" to sky, "glass" to c.glass.compositeOver(sky), "sheet" to c.glassRaised.compositeOver(b.at(0.3f)))
                    for ((gn, ground) in grounds) {
                        val track = SegmentedColors.track(c).compositeOver(ground)
                        val thumb = SegmentedColors.thumb(c).compositeOver(track)
                        val at = "e=$e evening=$evening at=${k / 10f} on=$gn"
                        note("$name label on track", Sky.contrast(SegmentedColors.label(c).compositeOver(track), track), at)
                        note("$name chosen label on thumb", Sky.contrast(SegmentedColors.selectedLabel(c).compositeOver(thumb), thumb), at)
                        note("$name thumb against track", Sky.contrast(thumb, track), at)
                    }
                }
            }
        }
        val unreadable = worst.filter { "label" in it.key && it.value < 4.5 }
        assertTrue("below 4.5:1 somewhere: $unreadable (where: ${where.filterKeys { it in unreadable }}; all: $worst)", unreadable.isEmpty())
        // (By day the thumb is white on a pale track and its shadow lifts it; at night only its fill tells it apart.)
        assertTrue("at night the chosen segment hardly differs from the track: $worst", worst.getValue("night thumb against track") >= 1.3)
        println("SegmentedContrastTest worst ratios: $worst")
    }
}
