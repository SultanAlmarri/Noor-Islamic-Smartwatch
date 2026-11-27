package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ThemeMode
import com.noor.app.ui.qibla.CalibrateNudge
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
 * (0.22.1) The Qibla page when the compass is unsure: the figure 8 drawn under the dial (the owner: "it should display
 * the figure 8 ... it should be visually appealing"), on the owner's width (360 dp), in English and Arabic, by night
 * and by day, with large text; the drawing alone at five moments of its loop; and the page once the compass is sure
 * again (the drawing gone). Fails if a page shows text in the other language. Software renders (build/design/qibla_*),
 * not photos of a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class QiblaCalibrateDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** A synthetic reading, as CompassSensor would produce it (no sensors under Robolectric). */
    private fun reading(heading: Double, accuracyDeg: Double?, magStatus: Int, fieldUt: Double, tilt: Double) = CompassReading(
        trueHeadingDeg = heading, declinationDeg = 1.95, headingAccuracyDeg = accuracyDeg, magnetometerStatus = magStatus,
        fieldMicroTesla = fieldUt, expectedFieldMicroTesla = 44.0, tiltDeg = tilt,
        assessment = CompassAssessor.assess(accuracyDeg, magStatus, fieldUt, 44.0, tilt), source = CompassReading.Source.ROTATION_VECTOR,
    )

    private var compass by mutableStateOf<CompassReading?>(null)
    private var strip by mutableStateOf(false)
    private val problems = mutableListOf<String>()

    @Test fun calibrate() {
        val h = RenderHarness(rule)
        h.tabRoute = com.noor.app.Routes.QIBLA
        h.tabFade = true
        h.screen = { pad ->
            if (strip) Column(Modifier.fillMaxWidth().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until 5) CalibrateNudge({}, phase = i / 5f)
            } else {
                val s = h.settings.copy(theme = h.theme)
                val place = PrayerProvider.place(s)
                QiblaContent(pad, QiblaPlace(s, place, place?.let { Qibla.from(it.point) }), compass, false, {})
            }
        }
        h.start()
        val poor = reading(301.4, 34.0, 1, 71.0, 4.0)
        compass = poor
        for ((theme, tag) in listOf(ThemeMode.DARK to "night", ThemeMode.LIGHT to "day")) {
            h.theme = theme
            h.arabic = false; h.shot("qibla_calibrate_${tag}_en", "figure 8"); check("qibla_calibrate_${tag}_en", false)
            h.arabic = true; h.shot("qibla_calibrate_${tag}_ar", "الرقم 8"); check("qibla_calibrate_${tag}_ar", true)
        }
        h.theme = ThemeMode.DARK; h.arabic = false; h.fontScale = 1.5f
        // (0.26) With large text the page's card says only what to do, so the compass keeps its size.
        h.shot("qibla_calibrate_large_text_en", "Calibrate the compass"); h.fontScale = 1f
        // Sure again: the drawing folds away.
        compass = reading(174.82, 7.5, 3, 44.0, 3.0)
        h.shot("qibla_calibrate_then_good_en", "Turn right")
        assertTrue("the figure 8 is still shown once the compass is sure",
            rule.onAllNodes(SemanticsMatcher("figure 8") { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { "figure 8" in it.text } }, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty())
        // The drawing alone, at five moments of its loop.
        strip = true; h.tabRoute = null
        h.shot("qibla_calibrate_loop_night_en", "figure 8")
        h.theme = ThemeMode.LIGHT; h.shot("qibla_calibrate_loop_day_en", "figure 8")
        h.theme = ThemeMode.DARK; h.arabic = true; h.shot("qibla_calibrate_loop_night_ar", "الرقم 8")
        assertTrue("Text in the wrong language:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun check(name: String, arabic: Boolean) {
        val texts = rule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { n ->
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }.distinct()
        texts.forEach { t ->
            if (arabic && Regex("[A-Za-z]").containsMatchIn(t)) problems += "$name (Arabic screen): \"$t\""
            if (!arabic && Regex("[\\u0600-\\u06FF]").containsMatchIn(t.replace("منهاد", ""))) problems += "$name (English screen): \"$t\""
        }
    }
}
