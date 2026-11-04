package com.noor.app

import androidx.compose.ui.graphics.compositeOver
import com.noor.app.ui.theme.DayColors
import com.noor.app.ui.theme.NightColors
import com.noor.app.ui.theme.NoorColors
import com.noor.app.ui.theme.Sky
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text colour must stay readable (WCAG AA) on every surface it is used on, in both themes. */
class ThemeContrastTest {
    private fun check(name: String, c: NoorColors) {
        val backgrounds = mapOf("background" to c.background, "surface" to c.surface, "surfaceRaised" to c.surfaceRaised)
        val text = mapOf("textPrimary" to c.textPrimary, "textSecondary" to c.textSecondary, "textMuted" to c.textMuted,
            "goldText" to c.goldText, "success" to c.success, "danger" to c.danger)
        for ((bn, bg) in backgrounds) for ((tn, fg) in text) {
            val ratio = Sky.contrast(fg.compositeOver(bg), bg)
            assertTrue("$name $tn on $bn: $ratio", ratio >= 4.5)
        }
        // Accent graphics (icons, rings, switches) need 3:1; text on a gold fill needs 4.5:1.
        assertTrue("$name gold on background", Sky.contrast(c.gold, c.background) >= 3.0)
        assertTrue("$name onGold on gold", Sky.contrast(c.onGold, c.gold) >= 4.5)
    }

    @Test fun night() = check("night", NightColors)
    @Test fun day() = check("day", DayColors)

    @Test fun textOnTheSkyIsAlwaysReadable() {
        for (e in -90..90) for (evening in listOf(false, true)) {
            val sky = Sky.colors(e.toDouble(), evening)
            assertTrue("zenith $e", Sky.contrast(Sky.OnSky, sky.zenith) >= 7.0)
            assertTrue("secondary on zenith $e", Sky.contrast(Sky.OnSkySecondary.compositeOver(sky.zenith), sky.zenith) >= 4.5)
            assertTrue("countdown on mid $e", Sky.contrast(Sky.OnSky, sky.mid) >= 3.0)
        }
    }

    @Test fun goldOnSkyReadsOnTheUpperSky() {
        for (e in -90..90) {
            val z = Sky.colors(e.toDouble(), true).zenith
            assertTrue("gold on zenith $e: ${Sky.contrast(Sky.GoldOnSky, z)}", Sky.contrast(Sky.GoldOnSky, z) >= 4.5)
        }
    }

}
