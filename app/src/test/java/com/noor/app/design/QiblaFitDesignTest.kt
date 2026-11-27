package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ThemeMode
import com.noor.app.ui.qibla.QiblaContent
import com.noor.app.ui.qibla.QiblaPlace
import com.noor.prayer.qibla.CompassAssessor
import com.noor.prayer.qibla.Qibla
import com.noor.shared.compass.CompassReading
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * (0.26) The Qibla page on one screen of a small phone (360 by 740 dp, the owner's width on a shorter screen): the
 * readings with the accuracy stand above the tab bar (82 dp) and a gesture bar (24 dp) without
 * scrolling, when the compass is good, when the phone faces the Qibla, and when the compass is unsure (the figure 8
 * under the dial), in English and Arabic, by night and by day. With text at 1.3 the page may scroll; it is rendered
 * to be looked at. Software renders (build/design/qibla_fit_*), not photos of a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi")
class QiblaFitDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun reading(heading: Double, accuracyDeg: Double?, magStatus: Int, fieldUt: Double, tilt: Double) = CompassReading(
        trueHeadingDeg = heading, declinationDeg = 1.95, headingAccuracyDeg = accuracyDeg, magnetometerStatus = magStatus,
        fieldMicroTesla = fieldUt, expectedFieldMicroTesla = 44.0, tiltDeg = tilt,
        assessment = CompassAssessor.assess(accuracyDeg, magStatus, fieldUt, 44.0, tilt), source = CompassReading.Source.ROTATION_VECTOR,
    )

    private var compass by mutableStateOf<CompassReading?>(null)

    @Test fun fits() {
        val h = RenderHarness(rule)
        h.tabRoute = com.noor.app.Routes.QIBLA
        h.tabFade = true
        h.screen = { pad ->
            val s = h.settings.copy(theme = h.theme)
            val place = PrayerProvider.place(s)
            QiblaContent(pad, QiblaPlace(s, place, place?.let { Qibla.from(it.point) }), compass, false, {})
        }
        h.start()
        fun fitsOneScreen(name: String) {
            val limit = (740 - 82 - 24) * rule.density.density
            val accuracy = rule.onAllNodesWithText(if (h.arabic) "الدقة" else "Accuracy").onFirst().fetchSemanticsNode().boundsInRoot
            // ...and the dial is not squeezed to make room (the owner: "don't make everything look bad and squished").
            val dial = rule.onNode(androidx.compose.ui.test.hasTestTag("qiblaDial"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            println("$name: accuracy ends at ${accuracy.bottom / rule.density.density} dp, dial ${dial.width / rule.density.density} dp")
            assertTrue("$name: the dial is ${dial.width / rule.density.density} dp", dial.width >= 280 * rule.density.density)
            assertTrue("$name: the accuracy ends at ${accuracy.bottom / rule.density.density} dp", accuracy.bottom <= limit)
        }
        val qibla = Qibla.from(PrayerProvider.place(h.settings)!!.point).bearingDeg
        for ((theme, tag) in listOf(ThemeMode.DARK to "night", ThemeMode.LIGHT to "day")) {
            h.theme = theme
            compass = reading(174.82, 7.5, 3, 44.0, 3.0)
            h.arabic = false; h.shot("qibla_fit_turn_${tag}_en", "Turn right"); fitsOneScreen("turn $tag en")
            h.arabic = true; h.shot("qibla_fit_turn_${tag}_ar", "استدر"); fitsOneScreen("turn $tag ar")
            compass = reading(qibla - 1.2, 6.0, 3, 44.0, 2.0)
            h.arabic = false; h.shot("qibla_fit_aligned_${tag}_en", "Facing the Qibla"); fitsOneScreen("aligned $tag en")
            compass = reading(301.4, 34.0, 1, 71.0, 4.0)
            h.shot("qibla_fit_poor_${tag}_en", "figure 8"); fitsOneScreen("poor $tag en")
            h.arabic = true; h.shot("qibla_fit_poor_${tag}_ar", "الرقم 8"); fitsOneScreen("poor $tag ar")
        }
        h.theme = ThemeMode.DARK; h.arabic = false; h.fontScale = 1.3f
        compass = reading(174.82, 7.5, 3, 44.0, 3.0)
        h.shot("qibla_fit_large_text_en", "Turn right")
        compass = reading(301.4, 34.0, 1, 71.0, 4.0)
        h.shot("qibla_fit_large_text_poor_en", "Calibrate the compass")
    }
}
