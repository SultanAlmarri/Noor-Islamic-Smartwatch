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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.noor.wear.ui.NoorScaffold
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

    private fun start() = rule.setContent { NoorWearTheme(config) { _ -> NoorScaffold { screen?.invoke() } } }

    /** [settle]: how long after the last change the picture is taken. 3.5 s is the page at rest (the scroll bar gone). */
    private fun shot(name: String, waitFor: String? = null, substring: Boolean = false, settle: Long = 3500) {
        if (waitFor != null) rule.waitUntil(10_000) { rule.onAllNodesWithText(waitFor, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(settle); rule.waitForIdle()
        Shots.save(Shots.drawView(rule.activity.window.decorView), name)
        checkLanguage(name, config?.language == "ar")
    }

    private fun checkLanguage(name: String, arabic: Boolean) {
        val wrong = wrongLanguageTexts(rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes(), arabic)
        assertTrue("$name shows text in the wrong language: $wrong", wrong.isEmpty())
    }

    private fun has(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** To the true end of a list: its last item as high as it can rise. */
    private fun toEnd() {
        repeat(3) { rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(1500); rule.waitForIdle() }
    }

    /** Something on screen says [text] to a screen reader. */
    private fun described(text: String) =
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun reading(heading: Double, quality: CompassQuality = CompassQuality.GOOD, issues: Set<CompassIssue> = emptySet(), accuracy: Double? = 6.0) =
        CompassReading(heading, 1.6, accuracy, 3, 45.0, 45.0, 4.0, CompassAssessment(quality, issues), CompassReading.Source.ROTATION_VECTOR)

    @Test fun homeScrollsToTheToolsAndOpensThem() {
        val opened = mutableListOf<String>()
        screen = { NoorWearTheme(config) { t -> HomeScreen(config!!, t, { TestConfigs.afternoon }, HomeExtras(tasbihToday = 254) { opened += it }) } }
        start()
        shot("s01_home_top", "Asr")
        // 0.25: the first screenful is the moment alone (the countdown in the owner's format, the time, the place and date).
        assertTrue(has("23:00")); assertTrue(has("at 3:14 PM")); assertTrue(has("Dubai", substring = true)); assertTrue(has("23 Jumada al-Ula 1448"))
        // Then the day's six times, each by name.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Dhuhr"))
        shot("s03_home_times", "Fajr")
        for ((name, time) in listOf("Fajr" to "5:09 AM", "Shuruq" to "6:25 AM", "Dhuhr" to "12:05 PM", "Asr" to "3:14 PM", "Maghrib" to "5:40 PM", "Isha" to "6:55 PM")) {
            // (By the time: the next prayer's name is also in the first screenful.)
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(time))
            assertTrue(name, has(name)); assertTrue("$name at $time", has(time))
            // One label, on the prayer we are in (Dhuhr at 14:51); the next one is the tinted row.
            if (name == "Dhuhr") assertTrue("the current prayer says Now", has("Now"))
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tasbih"))
        shot("s02_home_tools", "254")
        // 0.25: the two tools are tiles that show their figure (the bearing, today's count) and say the rest aloud.
        val bearing = "%.0f°".format(Qibla.from(config!!.point).bearingDeg)
        assertTrue("Qibla bearing shown", has(bearing, substring = true))
        assertTrue("Qibla bearing spoken", described("from North"))
        assertTrue("today's count spoken", described("Today 254"))
        // The calendar row names the next event and how far it is.
        assertTrue(has("White Days · in", substring = true))
        assertTrue("Settings is a round button with a spoken name", described("Settings"))
        rule.onAllNodesWithText("Tasbih", useUnmergedTree = true).fetchSemanticsNodes()
        rule.onNode(hasText("Tasbih")).performClick()
        assertEquals(listOf("tasbih"), opened)
    }

    /** 0.25: the first screenful under each sky of the day (the colours' contrast is locked by WatchSkyTest). */
    @Test fun homeThroughTheDay() {
        var at by mutableStateOf(TestConfigs.afternoon)
        fun dubai(h: Int, m: Int) = java.time.ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, java.time.ZoneId.of("Asia/Dubai")).toInstant()
        screen = { NoorWearTheme(config) { t -> androidx.compose.runtime.key(at) { HomeScreen(config!!, t, { at }, HomeExtras()) } } }
        start()
        // Before dawn (night, stars), first light, just after sunrise, sunset, dusk, night: the next prayer each time.
        for ((name, h, m, next) in listOf(Shot("h01_home_night", 4, 10, "Fajr"), Shot("h02_home_dawn", 5, 50, "Shuruq"), Shot("h03_home_sunrise", 6, 32, "Dhuhr"),
            Shot("h04_home_sunset", 17, 36, "Maghrib"), Shot("h05_home_dusk", 18, 5, "Isha"), Shot("h06_home_late", 22, 30, "Fajr"))) {
            at = dubai(h, m)
            shot(name, next)
        }
        // Past Isha the countdown runs to tomorrow's Fajr with its hours, in the owner's format (no leading zero).
        val toFajr = WearText(config).countdown(java.time.Duration.between(at, config!!.state(at).next.at))
        assertTrue(toFajr, Regex("6:[0-9][0-9]:00").matches(toFajr)); assertTrue(toFajr, has(toFajr))
        config = TestConfigs.dubai("ar", arabicDigits = true)
        at = dubai(17, 36)
        shot("h07_home_sunset_ar", "المغرب")
        // Right to left: the day's times and the tools in Arabic, with Arabic-Indic digits.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("٦:٢٥ ص"))
        shot("h08_home_times_ar", "الفجر")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("التسبيح"))
        shot("h09_home_tools_ar", "التسبيح")
        assertTrue(described("الإعدادات"))
    }

    private data class Shot(val name: String, val h: Int, val m: Int, val next: String)

    @Test fun qiblaCompassStates() {
        val c = TestConfigs.dubai("en")
        val bearing = Qibla.from(c.point).bearingDeg
        var r by mutableStateOf<CompassReading?>(reading(bearing - 40))
        var hasCompass by mutableStateOf(true)
        screen = { NoorWearTheme(config) { t -> QiblaContent(c, t, r, hasCompass) } }
        start()
        shot("s10_qibla_turn_right", "Turn right")
        assertTrue(has("40°", substring = true))
        // The second figure says what it is: the Qibla's bearing, with the distance under it.
        assertTrue(has("Qibla", substring = true)); assertTrue(has("1,633 km"))
        r = reading(bearing + 2.0)
        shot("s11_qibla_aligned", "Facing the Qibla")
        // Hysteresis: 5° off after being aligned still counts as facing; 8° does not.
        r = reading(bearing + 5.0); rule.waitForIdle()
        assertTrue(has("Facing the Qibla"))
        r = reading(bearing - 8.0); rule.waitForIdle()
        assertTrue(has("Turn right"))
        r = reading(bearing + 30, CompassQuality.POOR, setOf(CompassIssue.MAGNETIC_INTERFERENCE), accuracy = 35.0)
        shot("s12_qibla_calibrate", "figure 8", substring = true)
        // One order at a time: no figure and no "Turn" while the compass cannot be trusted.
        assertTrue(!has("Turn left") && !has("Turn right") && !has("30°", substring = true))
        r = reading(bearing, issues = setOf(CompassIssue.NOT_FLAT))
        shot("s13_qibla_not_level", "Hold your wrist level")
        assertTrue("never claims alignment while tilted", !has("Facing the Qibla"))
        hasCompass = false
        shot("s14_qibla_no_compass", "No compass", substring = true)
        assertTrue(has("from north"))
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
        // At nought (a lone dot in these digits) the screen says what to do.
        assertTrue(has("اضغط للعدّ"))
        rule.onRoot().performClick()
        rule.mainClock.advanceTimeBy(300)
        shot("s23_tasbih_round_done_ar", "تمّ", settle = 500)
        assertEquals(1, s.rounds); assertEquals(0, s.stepIndex)
        // The overlay hides the counter completely: where the two side buttons and the count are, the picture is black.
        val over = Shots.drawView(rule.activity.window.decorView)
        for ((x, y) in listOf(66 to 225, 384 to 225, 225 to 400, 120 to 120)) {
            assertEquals("pixel $x,$y under the round-done overlay", 0xFF000000.toInt(), over.getPixel(x, y))
        }

        config = TestConfigs.dubai("en")
        screen = { NoorWearTheme(config) { t -> TasbihOptionsScreen(s.copy(lifetime = 12_345), t, true, today, {}, {}, {}) } }
        shot("s24_tasbih_options", "After prayer")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("All time", substring = true))
        assertTrue(has("All time 12,345", substring = true))
        assertTrue(has("Vibrate"))
        toEnd()
        shot("s25_tasbih_options_end", "Counts stay on this watch", substring = true)
    }

    /** 0.25: counts of three and four digits, and the counter at nought, on the large and the small watch's width. */
    @Test fun tasbihLargeCounts() {
        val free = TasbihPreset.entries.first { it.roundTotal == 0 }
        var state by mutableStateOf(TasbihState(preset = free, count = 128, day = today, today = 328, lifetime = 9000))
        screen = { NoorWearTheme(config) { t -> androidx.compose.runtime.key(state) { TasbihScreen(state, t, TasbihControls(today = { today })) } } }
        start()
        shot("s26_tasbih_128", "128")
        state = TasbihState(preset = free, count = 1254, day = today, today = 1254, lifetime = 9000)
        shot("s27_tasbih_1254", "1254")
        assertTrue(has("Today 1,254"))
        state = TasbihState(preset = TasbihPreset.AFTER_PRAYER, stepIndex = 2, count = 0, day = today, today = 66, lifetime = 9000)
        shot("s28_tasbih_zero", "Tap to count")
        assertTrue(described("of 33 · 3/4"))
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
        shot("s40_settings_phone", "From phone · 5 min ago")
        assertTrue("the row is named for the thing", has("Prayer times"))
        ui = ui.copy(sync = Progress.FAILED)
        shot("s41_settings_sync_failed", "Can't reach phone")
        // What a list looks like a moment after it moved (the scroll bar showing), for comparison with the pictures at rest.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Prayer alerts"))
        shot("x01_scrollbar_just_scrolled", "Prayer alerts", settle = 150)
        shot("x02_scrollbar_at_rest", "Prayer alerts")
        repeat(2) { rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }; rule.mainClock.advanceTimeBy(1500); rule.waitForIdle() }

        val place = SetupPlace(GeoPoint(25.2048, 55.2708), "Asia/Dubai", "Dubai", "دبي", "AE", "AE-DU", fromGps = true)
        val setup = LocalSetup(place, alertsOn = true, alertStyle = WatchAlertStyle.ADHAN_SHORT)
        val local = setup.toConfig(System.currentTimeMillis(), java.time.ZoneId.of("Asia/Dubai"), false) { null }!!
        ui = SettingsUi(WatchState(local, ConfigSource.WATCH, setup, false, null), PhoneLink.Phone.NOT_REACHABLE, Progress.IDLE, "0.7.0")
        shot("s42_settings_watch", "This watch", substring = true)
        assertTrue(has("Prayer times"))
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Opening of the Adhan"))
        shot("s43_settings_watch_alerts", "Opening of the Adhan")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Get the phone app"))
        assertTrue(has("Opens on your phone"))
        toEnd()
        shot("s44_settings_get_phone_app", "Version 0.7.0", substring = true)

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
        val iv = Intervals.upcoming(cfg, TestConfigs.afternoon)
        val tools = ToolsTileService.layout(ctx, Shots.device(225), cfg, today, 254, iv[0])
        Shots.save(Shots.tile(rule.activity, tools, ToolsTileService.resources(), 450), "s60_tile_tools_en")
        val toolsAr = ToolsTileService.layout(ctx, Shots.device(225), TestConfigs.dubai("ar", arabicDigits = true), today, 0, iv[0])
        Shots.save(Shots.tile(rule.activity, toolsAr, ToolsTileService.resources(), 450), "s61_tile_tools_ar")
        val toolsSmall = ToolsTileService.layout(ctx, Shots.device(192), cfg, today, 1254, iv[0])
        Shots.save(Shots.tile(rule.activity, toolsSmall, ToolsTileService.resources(), 384), "s63_tile_tools_small")
        // 0.25: the next prayer on the top line, a name under each of the three buttons in both languages (the Tasbih's
        // with today's count), and the same words on both tiles' bottom button.
        assertTrue(Shots.texts(tools).toString(), Shots.texts(tools).containsAll(listOf("Asr · 3:14 PM", "Qibla  ·  Tasbih 254  ·  Calendar", "All times")))
        assertTrue(Shots.texts(toolsAr).toString(), Shots.texts(toolsAr).containsAll(listOf("القبلة  ·  التسبيح  ·  التقويم", "كل المواقيت")))
        assertTrue(Shots.texts(toolsSmall).toString(), "Qibla  ·  1,254  ·  Calendar" in Shots.texts(toolsSmall))
        assertTrue("before set-up the top line is the app's name", "Minhad" in Shots.texts(ToolsTileService.layout(ctx, Shots.device(225), null, today, 0)))
        val prayers = PrayerTileService.prayerLayout(ctx, Shots.device(192), cfg, iv[0], iv.drop(1).take(2))
        Shots.save(Shots.tile(rule.activity, prayers, androidx.wear.protolayout.ResourceBuilders.Resources.Builder().setVersion("1").build(), 384), "s62_tile_prayers_small")
        // The test renderer drops the large "display" text style, so check the layout itself for the prayer name.
        assertTrue(Shots.texts(prayers).toString(), "Asr" in Shots.texts(prayers) && "All times" in Shots.texts(prayers))
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
        rule.setContent { NoorWearTheme(c) { _ -> NoorScaffold { screen?.invoke() } } }
        fun shot(name: String, wait: String) {
            rule.waitUntil(10_000) { rule.onAllNodesWithText(wait, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            rule.mainClock.advanceTimeBy(3500); rule.waitForIdle()
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
        val free = TasbihPreset.entries.first { it.roundTotal == 0 }
        screen = { NoorWearTheme(c) { t -> TasbihScreen(TasbihState(preset = free, count = 1254, day = day, today = 1254), t, TasbihControls(today = { day })) } }
        shot("m05_tasbih_1254_small", "1254")
        screen = { NoorWearTheme(c) { t -> com.noor.wear.ui.SettingsScreen(SettingsUi(WatchState(c, ConfigSource.PHONE, null, true, System.currentTimeMillis() - 60_000L), PhoneLink.Phone.CONNECTED, Progress.IDLE, "0.7.0"), t, SettingsActions()) } }
        shot("m06_settings_small", "Prayer times")
        screen = { NoorWearTheme(null) { _ -> WelcomeScreen(WelcomeActions()) } }
        shot("m07_welcome_small", "Use my location")
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
        if (arabic) Regex("[A-Za-z]").containsMatchIn(t.replace(Regex("Wear OS|Minhad"), ""))
        else Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]").containsMatchIn(t.replace("منهاد", ""))
    }
