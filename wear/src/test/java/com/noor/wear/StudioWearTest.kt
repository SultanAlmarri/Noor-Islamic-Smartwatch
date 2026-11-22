package com.noor.wear

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.noor.wear.ui.NoorScaffold
import com.noor.prayer.GeoPoint
import com.noor.prayer.LocalSetup
import com.noor.prayer.SetupPlace
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.FaceTemplates
import com.noor.wear.data.ConfigSource
import com.noor.wear.data.PhoneLink
import com.noor.wear.data.WatchState
import com.noor.wear.ui.AlertSettingsScreen
import com.noor.wear.ui.Dest
import com.noor.wear.ui.DisplaySettingsScreen
import com.noor.wear.ui.FacesScreen
import com.noor.wear.ui.NoorWearTheme
import com.noor.wear.ui.Progress
import com.noor.wear.ui.SettingsActions
import com.noor.wear.ui.SettingsScreen
import com.noor.wear.ui.SettingsUi
import com.noor.wear.ui.StudioUi
import com.noor.wear.ui.TimesSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.18: the watch's settings in their new shape (one page of rows that say their state, each opening its details) and
 * the watch face page with Minhad Studio: in use, not yet chosen, and on a watch that cannot run it. Software renders
 * to build/screenshots; each screen is checked for text in the wrong language.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w225dp-h225dp-round-watch-xhdpi")
class StudioWearTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var config by mutableStateOf<WatchConfig?>(TestConfigs.dubai("en"))

    private fun shot(name: String, waitFor: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(waitFor, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(3500); rule.waitForIdle()
        Shots.save(Shots.drawView(rule.activity.window.decorView), name)
        val wrong = wrongLanguageTexts(rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes(),
            config?.language == "ar")
        assertTrue("$name shows text in the wrong language: $wrong", wrong.isEmpty())
    }

    @Test fun settingsPagesAndTheStudioFacePage() {
        rule.setContent { NoorWearTheme(config) { _ -> NoorScaffold { screen?.invoke() } } }
        val opened = mutableListOf<String>()
        val place = SetupPlace(GeoPoint(25.2048, 55.2708), "Asia/Dubai", "Dubai", "دبي", "AE", "AE-DU", fromGps = false)
        var setup = LocalSetup(place, hijriAdjust = 1)
        fun watchUi() = SettingsUi(WatchState(setup.toConfig(System.currentTimeMillis(), java.time.ZoneId.of("Asia/Dubai"), false) { null }, ConfigSource.WATCH, setup, true, null),
            PhoneLink.Phone.CONNECTED, Progress.IDLE, "0.18.0")
        val actions = SettingsActions(onOpen = { opened += it }, onSetup = { f -> setup = f(setup) }, faceSummary = "Minhad Studio · In use")

        screen = { NoorWearTheme(config) { t -> SettingsScreen(watchUi(), t, actions) } }
        shot("t01_settings_hub_watch", "Watch face")
        rule.onNode(hasText("Watch face")).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Language"))
        rule.onNode(hasText("Language")).performClick()
        assertEquals(listOf(Dest.FACES, Dest.SETTINGS_DISPLAY), opened)

        screen = { NoorWearTheme(config) { t -> TimesSettingsScreen(watchUi(), t, actions) } }
        shot("t02_settings_times", "Location")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Hijri date"))
        shot("t03_settings_times_hijri", "+1 day")

        screen = { NoorWearTheme(config) { t -> DisplaySettingsScreen(watchUi(), t, actions) } }
        shot("t04_settings_display", "Numbers")
        rule.onNode(hasText("Clock")).performClick(); rule.waitForIdle()
        assertEquals(false, setup.use24h)

        val phone = SettingsUi(WatchState(TestConfigs.dubai("en"), ConfigSource.PHONE, null, true, System.currentTimeMillis() - 60_000L), PhoneLink.Phone.CONNECTED, Progress.IDLE, "0.18.0")
        screen = { NoorWearTheme(config) { t -> AlertSettingsScreen(phone, t, actions) } }
        shot("t05_settings_alerts_phone", "Set in Minhad on your phone")

        screen = { FacesScreen(config, StudioUi(supported = true, active = false, fromPhone = true), FaceTemplates.DAY, { false }, showClassic = false) }
        shot("t06_faces_studio_not_in_use", "Minhad Studio")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Show Minhad Studio"))
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Touch and hold", substring = true))
        repeat(3) { rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(1500); rule.waitForIdle() }
        shot("t07_faces_studio_hint", "Design it in Minhad")

        // 0.25: the Minhad faces' own picker (design and colour), which the page shows under the studio face where
        // Watch Face Push exists (not in this test's Android): rendered by itself.
        var picked = ""
        screen = { NoorWearTheme(config) { _ ->
            androidx.wear.compose.foundation.lazy.ScalingLazyColumn(androidx.compose.ui.Modifier.fillMaxSize()) {
                item { com.noor.wear.ui.FacePicker(com.noor.prayer.WatchFaces.DEFAULT.id) { picked = it } }
            }
        } }
        shot("t11_face_picker", "Dial")
        rule.onNode(hasText("Dial")).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle()
        assertEquals(com.noor.prayer.WatchFaces.Design.DIAL, com.noor.prayer.WatchFaces.parse(picked)?.design)

        config = TestConfigs.dubai("ar")
        screen = { NoorWearTheme(config) { _ -> FacesScreen(config, StudioUi(supported = true, active = true, fromPhone = true), FaceTemplates.KUFI, { true }, showClassic = false) } }
        shot("t08_faces_studio_in_use_ar", "استوديو منهاد")
        screen = { NoorWearTheme(config) { _ -> FacesScreen(config, StudioUi(supported = false, active = false, fromPhone = false), FaceTemplates.HORIZON, { false }, showClassic = false) } }
        shot("t09_faces_studio_unsupported_ar", "غير متاح")
        screen = { NoorWearTheme(config) { t -> SettingsScreen(phone.copy(state = phone.state.copy(config = config)), t, actions.copy(faceSummary = "")) } }
        shot("t10_settings_hub_phone_ar", "واجهة الساعة")
    }
}
