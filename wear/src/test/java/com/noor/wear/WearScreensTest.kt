package com.noor.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.noor.prayer.GeoPoint
import com.noor.prayer.LocalSetup
import com.noor.prayer.SetupPlace
import com.noor.prayer.WatchAlertStyle
import com.noor.prayer.WatchConfig
import com.noor.prayer.dhikr.TasbihPreset
import com.noor.prayer.dhikr.TasbihState
import com.noor.prayer.geo.GazPlace
import com.noor.prayer.qibla.CompassAssessment
import com.noor.prayer.qibla.CompassIssue
import com.noor.prayer.qibla.CompassQuality
import com.noor.prayer.qibla.Qibla
import com.noor.shared.compass.CompassReading
import com.noor.wear.complication.HijriData
import com.noor.wear.complication.QiblaComplicationService
import com.noor.wear.complication.TasbihComplicationService
import com.noor.wear.data.ConfigSource
import com.noor.wear.data.PhoneLink
import com.noor.wear.data.WatchState
import com.noor.wear.tile.PrayerTileService
import com.noor.wear.tile.ToolsTileService
import com.noor.wear.ui.AlertPrayersScreen
import com.noor.wear.ui.CalendarScreen
import com.noor.wear.ui.HomeExtras
import com.noor.wear.ui.HomeScreen
import com.noor.wear.ui.LocateStatus
import com.noor.wear.ui.LocationScreen
import com.noor.wear.ui.NoorWearTheme
import com.noor.wear.ui.Progress
import com.noor.wear.ui.QiblaContent
import com.noor.wear.ui.SettingsActions
import com.noor.wear.ui.SettingsScreen
import com.noor.wear.ui.SettingsUi
import com.noor.wear.ui.TasbihControls
import com.noor.wear.ui.TasbihOptionsScreen
import com.noor.wear.ui.TasbihScreen
import com.noor.wear.ui.WelcomeActions
import com.noor.wear.ui.WelcomeScreen
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Every watch screen rendered on a 450 px round display (Galaxy Watch 5 Pro / Pixel Watch size), with the interactions
 * that matter checked through the UI: counting, undo, a round completing, alignment wording.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w225dp-h225dp-round-watch-xhdpi")
class WearScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 5)
    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var config by mutableStateOf<WatchConfig?>(TestConfigs.dubai("en"))

    private fun start() = rule.setContent { NoorWearTheme(config) { _ -> AppScaffold { screen?.invoke() } } }

    private fun shot(name: String, waitFor: String? = null, substring: Boolean = false) {
        if (waitFor != null) rule.waitUntil(10_000) { rule.onAllNodesWithText(waitFor, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
        Shots.save(Shots.drawView(rule.activity.window.decorView), name)
        checkLanguage(name, config?.language == "ar")
    }

    private fun checkLanguage(name: String, arabic: Boolean) {
        val wrong = wrongLanguageTexts(rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes(), arabic)
        assertTrue("$name shows text in the wrong language: $wrong", wrong.isEmpty())
    }

    private fun has(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun reading(heading: Double, quality: CompassQuality = CompassQuality.GOOD, issues: Set<CompassIssue> = emptySet(), accuracy: Double? = 6.0) =
        CompassReading(heading, 1.6, accuracy, 3, 45.0, 45.0, 4.0, CompassAssessment(quality, issues), CompassReading.Source.ROTATION_VECTOR)

    @Test fun homeScrollsToTheToolsAndOpensThem() {
        val opened = mutableListOf<String>()
        screen = { NoorWearTheme(config) { t -> HomeScreen(config!!, t, { TestConfigs.afternoon }, HomeExtras(tasbihToday = 254) { opened += it }) } }
        start()
        shot("s01_home_top", "Asr")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tasbih"))
        shot("s02_home_tools", "Today 254")
        assertTrue("Qibla bearing shown", has("° from North", substring = true) || has("°\u2069 from North", substring = true))
        // The calendar chip names the next event and how far it is.
        assertTrue(has("White Days · in", substring = true))
        rule.onAllNodesWithText("Tasbih", useUnmergedTree = true).fetchSemanticsNodes()
        rule.onNode(hasText("Tasbih")).performClick()
        assertEquals(listOf("tasbih"), opened)
    }

    @Test fun qiblaCompassStates() {
        val c = TestConfigs.dubai("en")
        val bearing = Qibla.from(c.point).bearingDeg
        var r by mutableStateOf<CompassReading?>(reading(bearing - 40))
        var hasCompass by mutableStateOf(true)
        screen = { NoorWearTheme(config) { t -> QiblaContent(c, t, r, hasCompass) } }
        start()
        shot("s10_qibla_turn_right", "Turn right")
        assertTrue(has("40°", substring = true))
        r = reading(bearing + 2.0)
        shot("s11_qibla_aligned", "Facing the Qibla")
        // Hysteresis: 5° off after being aligned still counts as facing; 8° does not.
        r = reading(bearing + 5.0); rule.waitForIdle()
        assertTrue(has("Facing the Qibla"))
        r = reading(bearing - 8.0); rule.waitForIdle()
        assertTrue(has("Turn right"))
        r = reading(bearing + 30, CompassQuality.POOR, setOf(CompassIssue.MAGNETIC_INTERFERENCE), accuracy = 35.0)
        shot("s12_qibla_calibrate", "figure 8", substring = true)
        r = reading(bearing, issues = setOf(CompassIssue.NOT_FLAT))
        shot("s13_qibla_not_level", "Hold your wrist level")
        assertTrue("never claims alignment while tilted", !has("Facing the Qibla"))
        hasCompass = false
        shot("s14_qibla_no_compass", "no compass", substring = true)
        config = TestConfigs.dubai("ar", arabicDigits = true); hasCompass = true; r = reading(bearing + 25)
        shot("s15_qibla_ar", "استدر يسارًا")
    }

    @Test fun tasbihCountsUndoesAndCompletesARound() {
        var saved: TasbihState? = null
        val initial = TasbihState(preset = TasbihPreset.AFTER_PRAYER, stepIndex = 0, count = 20, day = today, today = 120, lifetime = 5000)
        screen = { NoorWearTheme(config) { t -> TasbihScreen(initial, t, TasbihControls(vibrate = true, today = { today }, onChange = { saved = it })) } }
        start()
        shot("s20_tasbih", "SubhanAllah")
        repeat(3) { rule.onRoot().performClick() }
        rule.waitForIdle()
        assertTrue(has("23")); assertEquals(23, saved?.count); assertEquals(123, saved?.todayOn(today))
        rule.onNodeWithContentDescription("Take one back", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertTrue(has("22")); assertEquals(22, saved?.count)
        // Eleven more finish SubhanAllah: the next phrase begins.
        repeat(11) { rule.onRoot().performClick() }
        rule.waitForIdle()
        assertTrue(has("Alhamdulillah")); assertEquals(1, saved?.stepIndex)
        shot("s21_tasbih_second_phrase", "Alhamdulillah")
    }

    @Test fun tasbihRoundDoneArabicAndOptions() {
        config = TestConfigs.dubai("ar", arabicDigits = true)
        val last = TasbihState(preset = TasbihPreset.AFTER_PRAYER, stepIndex = 3, count = 0, day = today, today = 99, lifetime = 99)
        var s by mutableStateOf(last)
        screen = { NoorWearTheme(config) { t -> TasbihScreen(s, t, TasbihControls(today = { today }, onChange = { s = it })) } }
        start()
        shot("s22_tasbih_last_phrase_ar", "لا إله إلا الله", substring = true)
        rule.onRoot().performClick()
        rule.mainClock.advanceTimeBy(300)
        shot("s23_tasbih_round_done_ar", "تمّ")
        assertEquals(1, s.rounds); assertEquals(0, s.stepIndex)

        config = TestConfigs.dubai("en")
        screen = { NoorWearTheme(config) { t -> TasbihOptionsScreen(s.copy(lifetime = 12_345), t, true, today, {}, {}, {}) } }
        shot("s24_tasbih_options", "After prayer")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("All time", substring = true))
        assertTrue(has("All time 12,345", substring = true))
        shot("s25_tasbih_options_end", "Vibrate on each count")
    }

    @Test fun calendarEnglishAndArabic() {
        screen = { NoorWearTheme(config) { t -> CalendarScreen(config!!, t, today) } }
        start()
        // TestConfigs uses a Hijri adjustment of -1: 24 Jumada al-Ula.
        shot("s30_calendar_en", "Jumada al-Ula 1448 AH")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Ramadan begins"))
        shot("s31_calendar_events_en", "Ramadan begins")
        config = TestConfigs.dubai("ar")
        shot("s32_calendar_ar", "بداية رمضان", substring = true)
    }

    @Test fun settingsForPhoneAndForWatchSetup() {
        val phoneState = WatchState(TestConfigs.dubai("en"), ConfigSource.PHONE, null, true, System.currentTimeMillis() - 5 * 60_000L)
        var ui by mutableStateOf(SettingsUi(phoneState, PhoneLink.Phone.CONNECTED, Progress.IDLE, "0.7.0"))
        screen = { NoorWearTheme(config) { t -> SettingsScreen(ui, t, SettingsActions(showFace = false)) } }
        start()
        shot("s40_settings_phone", "Updated 5 min ago")
        ui = ui.copy(sync = Progress.FAILED)
        shot("s41_settings_sync_failed", "Phone not reachable")

        val place = SetupPlace(GeoPoint(25.2048, 55.2708), "Asia/Dubai", "Dubai", "دبي", "AE", "AE-DU", fromGps = true)
        val setup = LocalSetup(place, alertsOn = true, alertStyle = WatchAlertStyle.ADHAN_SHORT)
        val local = setup.toConfig(System.currentTimeMillis(), java.time.ZoneId.of("Asia/Dubai"), false) { null }!!
        ui = SettingsUi(WatchState(local, ConfigSource.WATCH, setup, false, null), PhoneLink.Phone.NOT_REACHABLE, Progress.IDLE, "0.7.0")
        shot("s42_settings_watch", "Set on this watch")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Opening of the Adhan"))
        shot("s43_settings_watch_alerts", "Opening of the Adhan")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Noor for your phone"))
        shot("s44_settings_get_phone_app", "Noor for your phone")

        screen = { NoorWearTheme(config) { t -> AlertPrayersScreen(setup, t) { _, _ -> } } }
        shot("s45_alert_prayers", "Fajr")
    }

    @Test fun welcomeAndLocation() {
        config = null
        var locate by mutableStateOf<LocateStatus>(LocateStatus.Idle)
        screen = { NoorWearTheme(null) { _ -> WelcomeScreen(WelcomeActions(locate = locate)) } }
        start()
        shot("s50_welcome", "Use my location")
        locate = LocateStatus.Working
        shot("s51_welcome_locating", "Finding your location…")
        locate = LocateStatus.Unavailable
        shot("s52_welcome_unavailable", "Try outdoors", substring = true)

        val results = listOf(
            GazPlace(1, "Al Barsha", "البرشاء", GeoPoint(25.11, 55.20), "AE", "03", "Dubai", 50_000, "Asia/Dubai"),
            GazPlace(2, "Barcelona", null, GeoPoint(41.39, 2.17), "ES", "56", "Catalonia", 1_600_000, "Europe/Madrid"),
        )
        screen = { NoorWearTheme(TestConfigs.dubai("en")) { t -> LocationScreen(t, LocateStatus.Idle, results, false, {}, {}, {}) } }
        shot("s53_location", "Search for a city")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Barcelona", substring = true))
        shot("s54_location_search_results", "Barcelona", substring = true)
    }

    @Test fun tilesAndToolComplications() {
        val ctx = RuntimeEnvironment.getApplication()
        val cfg = TestConfigs.dubai("en")
        val tools = ToolsTileService.layout(ctx, Shots.device(225), cfg, today, 254)
        Shots.save(Shots.tile(rule.activity, tools, ToolsTileService.resources(), 450), "s60_tile_tools_en")
        val toolsAr = ToolsTileService.layout(ctx, Shots.device(225), TestConfigs.dubai("ar", arabicDigits = true), today, 0)
        Shots.save(Shots.tile(rule.activity, toolsAr, ToolsTileService.resources(), 450), "s61_tile_tools_ar")
        val iv = Intervals.upcoming(cfg, TestConfigs.afternoon)
        val prayers = PrayerTileService.prayerLayout(ctx, Shots.device(192), cfg, iv[0], iv.drop(1).take(2))
        Shots.save(Shots.tile(rule.activity, prayers, androidx.wear.protolayout.ResourceBuilders.Resources.Builder().setVersion("1").build(), 384), "s62_tile_prayers_small")
        // The test renderer drops the large "display" text style, so check the layout itself for the prayer name.
        assertTrue(Shots.texts(prayers).toString(), "Asr" in Shots.texts(prayers))
        assertTrue(Shots.texts(tools).toString(), Shots.texts(tools).any { "Jumada al-Ula" in it })

        // Countdown text in the tile: dynamic (kept current by the watch), with a static fallback.
        val countdown = PrayerTileService.countdown(ctx, WearText(cfg), cfg, iv[0])
        assertEquals("Next", countdown.staticValue)
        assertTrue(countdown.dynamicValue != null)
        assertTrue(PrayerTileService.countdown(ctx, WearText(cfg), cfg.copy(arabicDigits = true), iv[0]).dynamicValue == null)

        val res = ctx.resources
        val hijri = HijriData.data(ctx, cfg, ComplicationType.SHORT_TEXT, today) as ShortTextComplicationData
        assertEquals("24", hijri.text.getTextAt(res, TestConfigs.afternoon).toString())
        assertEquals("Jum I", hijri.title!!.getTextAt(res, TestConfigs.afternoon).toString())
        val hijriLong = HijriData.data(ctx, cfg, ComplicationType.LONG_TEXT, today) as LongTextComplicationData
        assertEquals("24 Jumada al-Ula 1448 AH", hijriLong.text.getTextAt(res, TestConfigs.afternoon).toString())
        val hijriAr = HijriData.data(ctx, TestConfigs.dubai("ar", arabicDigits = true), ComplicationType.SHORT_TEXT, today) as ShortTextComplicationData
        assertEquals("٢٤", hijriAr.text.getTextAt(res, TestConfigs.afternoon).toString())
        assertEquals("جمادى ١", hijriAr.title!!.getTextAt(res, TestConfigs.afternoon).toString())

        val tasbih = TasbihComplicationService.data(ctx, cfg, ComplicationType.SHORT_TEXT, 254) as ShortTextComplicationData
        assertEquals("254", tasbih.text.getTextAt(res, TestConfigs.afternoon).toString())
        assertTrue(tasbih.tapAction != null)
        val qibla = QiblaComplicationService.data(ctx, cfg, ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("%.0f°".format(Qibla.from(cfg.point).bearingDeg), qibla.text.getTextAt(res, TestConfigs.afternoon).toString())
        assertTrue(QiblaComplicationService.data(ctx, null, ComplicationType.MONOCHROMATIC_IMAGE) != null)
    }
}

/** The smallest common round watch (Galaxy Watch 40 mm, ~192 dp): nothing important may be cut off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-watch-xhdpi")
class WearSmallScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun keyScreensOnASmallWatch() {
        val c = TestConfigs.dubai("en")
        var screen by mutableStateOf<(@Composable () -> Unit)?>(null)
        rule.setContent { NoorWearTheme(c) { _ -> AppScaffold { screen?.invoke() } } }
        fun shot(name: String, wait: String) {
            rule.waitUntil(10_000) { rule.onAllNodesWithText(wait, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            rule.mainClock.advanceTimeBy(1200); rule.waitForIdle()
            Shots.save(Shots.drawView(rule.activity.window.decorView), name)
            val wrong = wrongLanguageTexts(rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes(), false)
            assertTrue("$name shows text in the wrong language: $wrong", wrong.isEmpty())
        }
        screen = { NoorWearTheme(c) { t -> HomeScreen(c, t, { TestConfigs.afternoon }, HomeExtras()) } }
        shot("m01_home_small", "Asr")
        val day = LocalDate.of(2026, 11, 5)
        screen = { NoorWearTheme(c) { t -> TasbihScreen(TasbihState(count = 32, day = day, today = 32), t, TasbihControls(today = { day })) } }
        shot("m02_tasbih_small", "SubhanAllah")
        val bearing = Qibla.from(c.point).bearingDeg
        screen = { NoorWearTheme(c) { t -> QiblaContent(c, t, CompassReading(bearing + 12, 1.6, 5.0, 3, 45.0, 45.0, 3.0, CompassAssessment(CompassQuality.GOOD, emptySet()), CompassReading.Source.ROTATION_VECTOR), true) } }
        shot("m03_qibla_small", "Turn left")
        screen = { NoorWearTheme(c) { t -> CalendarScreen(c, t, day) } }
        shot("m04_calendar_small", "1448")
    }
}

/**
 * Language separation (0.9): texts on a watch screen in the wrong language. No Arabic letters on an English screen,
 * no Latin letters on an Arabic one (Noor's Kufi logo and product names excepted).
 */
internal fun wrongLanguageTexts(nodes: List<androidx.compose.ui.semantics.SemanticsNode>, arabic: Boolean): List<String> =
    nodes.flatMap { n ->
        n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text } +
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty()
    }.distinct().filter { t ->
        if (arabic) Regex("[A-Za-z]").containsMatchIn(t.replace(Regex("Wear OS|Noor"), ""))
        else Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]").containsMatchIn(t.replace("نور", ""))
    }
