package com.noor.app.ui.watch

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.watch.studio.StudioActions
import com.noor.app.ui.watch.studio.StudioCard
import com.noor.app.ui.watch.studio.StudioContent
import com.noor.app.ui.watch.studio.StudioPanel
import com.noor.app.ui.watch.studio.StudioState
import com.noor.app.ui.watch.studio.StudioStatus
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.Element
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceTemplates
import com.noor.prayer.face.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Minhad Studio's editor (0.18), in English and Arabic: choosing what goes in a place, moving through the panels,
 * undo, and the states of "Set on my watch". Renders go to build/screenshots (software renders, for review); every
 * text and label on an Arabic screen is checked for Latin letters (product names such as Wear OS allowed, as in
 * ScreenshotTest), every English one for Arabic letters.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class StudioEditorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "", arabicDigits = false, use24h = false, updatedAtEpochMs = 1L,
    )
    private val problems = mutableListOf<String>()

    @Test fun editInBothLanguages() {
        var arabic by mutableStateOf(false)
        var s by mutableStateOf(StudioState(FaceTemplates.HORIZON))
        var status by mutableStateOf(StudioStatus.NONE)
        var card by mutableStateOf(false)
        val actions = StudioActions(
            edit = { f -> val n = f(s.design); if (n != s.design) s = s.copy(design = n, undo = s.undo + s.design, redo = emptyList()) },
            undo = { s.undo.lastOrNull()?.let { p -> s = s.copy(design = p, undo = s.undo.dropLast(1), redo = s.redo + s.design) } },
            select = { slot -> s = s.copy(selected = if (s.selected == slot) null else slot) },
            panel = { p -> s = s.copy(panel = p) },
            ambient = { on -> s = s.copy(ambient = on) },
            use = { d -> s = s.copy(design = d, undo = s.undo + s.design) },
        )
        rule.setContent {
            val base = LocalContext.current
            val conf = Configuration(base.resources.configuration).apply {
                val l = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
                setLocale(l); setLayoutDirection(l)
            }
            val ctx = base.createConfigurationContext(conf)
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides conf,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                NoorTheme(com.noor.app.data.ThemeMode.DARK) {
                    NoorBackground {
                        if (card) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
                            StudioCard(s.design, dubai, status == StudioStatus.SHOWING) {}
                        } else StudioContent(PaddingValues(top = 24.dp, bottom = 0.dp), s, dubai, status, actions) {}
                    }
                }
            }
        }

        shot("90_studio_en", "Minhad Studio", arabic)
        // Tap a place (its chip), and choose what goes there.
        rule.onNodeWithText("Below the centre").performClick(); rule.waitForIdle()
        assertEquals(Slot.BOTTOM, s.selected)
        rule.onNodeWithText("What goes here").assertExists()
        rule.onNodeWithText("Prayer gauge").performClick(); rule.waitForIdle()
        assertEquals(Element.PRAYER_GAUGE, s.design.at(Slot.BOTTOM)!!.element)
        shot("91_studio_inspector_en", "Prayer gauge", arabic)
        actions.undo(); rule.waitForIdle()
        assertEquals(Element.NEXT_PRAYER, s.design.at(Slot.BOTTOM)!!.element)

        s = s.copy(selected = null, panel = StudioPanel.STYLE); shot("92_studio_style_en", "Night dunes", arabic)
        s = s.copy(panel = StudioPanel.DIAL, design = FaceTemplates.DIAL); shot("93_studio_dial_en", "Prayer ring", arabic)
        s = s.copy(panel = StudioPanel.AMBIENT); shot("94_studio_aod_en", "Pixel shift", arabic)
        s = s.copy(panel = StudioPanel.OPTIONS, design = FaceTemplates.DAY); status = StudioStatus.PICK
        shot("95_studio_options_sent_en", "touch and hold your watch face", arabic)

        arabic = true; status = StudioStatus.NONE
        s = StudioState(FaceTemplates.KUFI, selected = Slot.LOWER)
        shot("96_studio_ar", "استوديو منهاد", arabic)
        s = s.copy(selected = null, panel = StudioPanel.DESIGNS, design = FaceTemplates.SUN, saved = listOf(FaceTemplates.TIMETABLE.copy(id = "my_1", name = "واجهتي")))
        shot("97_studio_designs_ar", "تصاميمي", arabic)
        s = s.copy(panel = StudioPanel.AMBIENT, design = FaceTemplates.TIMETABLE); status = StudioStatus.UNSUPPORTED
        shot("98_studio_aod_unsupported_ar", "الصيغة الحديثة", arabic)
        card = true; status = StudioStatus.SHOWING; arabic = false; s = StudioState(FaceTemplates.HORIZON)
        shot("99_studio_card_en", "Edit your design", arabic)

        assertTrue("Text in the wrong language:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    private fun shot(name: String, waitFor: String, arabic: Boolean) {
        rule.waitUntil(20_000) { rule.onAllNodesWithTextSub(waitFor).isNotEmpty() }
        rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("build/screenshots").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val texts = rule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { n ->
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                listOfNotNull(n.config.getOrNull(SemanticsProperties.EditableText)?.text)
        }.distinct()
        for (t in texts) {
            if (arabic && Regex("[A-Za-z]").containsMatchIn(t.replace("Wear OS", ""))) problems += "$name (Arabic): \"$t\""
            if (!arabic && Regex("[\\u0600-\\u06FF]").containsMatchIn(t)) problems += "$name (English): \"$t\""
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextSub(t: String) =
        onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
}
