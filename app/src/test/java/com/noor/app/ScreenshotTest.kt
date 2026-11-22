package com.noor.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.data.AppSettings
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ThemeMode
import com.noor.app.data.TimeFormat
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.settings.AccuracyScreen
import com.noor.app.ui.settings.SettingsScreen
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.today.CalendarPicker
import com.noor.app.ui.today.DayCache
import com.noor.app.ui.today.TodayContent
import com.noor.app.ui.today.TodayUi
import com.noor.app.ui.watch.WatchScreen
import com.noor.app.ui.watch.WatchContent
import com.noor.app.ui.watch.WatchActions
import com.noor.app.ui.watch.Pending
import com.noor.app.data.WatchNode
import com.noor.app.data.WatchSetupState
import com.noor.prayer.WatchReport
import com.noor.shared.compass.CompassReading
import com.noor.app.ui.qibla.CalibrationGuide
import com.noor.app.ui.qibla.QiblaContent
import com.noor.app.ui.qibla.QiblaPlace
import com.noor.prayer.qibla.CompassAssessor
import com.noor.prayer.qibla.Qibla
import com.noor.app.util.Formatter
import com.noor.prayer.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Renders the real screens with Robolectric's native graphics and saves PNGs to build/screenshots, so every build
 * shows what the app looks like. Software renders on a simulated 412 dp phone, not photos of a device.
 * One test method: DataStore is a per-process singleton.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private enum class Screen { TODAY, TODAY_OTHER_DATE, CALENDAR, SETTINGS, WATCH, ACCURACY, QIBLA, CALIBRATION, WATCH_STATE, ADHAN, ADHAN_FAJR, ADHAN_SHURUQ, ADHAN_LIBRARY, ADHAN_SOUNDS, ONBOARDING, CITIES, SHELL, ADHAN_HISTORY, SCENERY, ADHAN_VOICE, ALARM_EDIT, ALARM_RING }

    /** Wednesday 4 November 2026, 15:29:21 in Dubai: Asr is in progress, Maghrib is next. */
    private val now = ZonedDateTime.of(2026, 11, 4, 15, 29, 21, 0, ZoneId.of("Asia/Dubai")).toInstant()

    private fun todayUi(s: AppSettings, tables: com.noor.shared.geo.OfficialTimetables? = null, now: java.time.Instant = this.now): TodayUi {
        val place = PrayerProvider.place(s)!!
        val profile = PrayerProvider.profile(s, place)
        val cache = DayCache(tables)
        val state = Schedule.state(now, place.zone) { cache.get(s, place, profile, it) }
        return TodayUi(s, place, profile, PrayerProvider.authority(s, place), now, state, now.atZone(place.zone).toLocalDate(), refreshing = false, lastRefresh = null)
    }

    /** A synthetic reading, as CompassSensor would produce it (no sensors under Robolectric). */
    private fun reading(heading: Double, accuracyDeg: Double?, magStatus: Int, fieldUt: Double, tilt: Double) = CompassReading(
        trueHeadingDeg = heading, declinationDeg = 1.95, headingAccuracyDeg = accuracyDeg, magnetometerStatus = magStatus,
        fieldMicroTesla = fieldUt, expectedFieldMicroTesla = 44.0, tiltDeg = tilt,
        assessment = CompassAssessor.assess(accuracyDeg, magStatus, fieldUt, 44.0, tilt), source = CompassReading.Source.ROTATION_VECTOR,
    )

    private data class WatchScene(
        val state: WatchSetupState, val fromPlay: Boolean = true, val lastSent: Long? = null,
        val installs: Map<String, Pending> = emptyMap(), val faces: Map<String, Pending> = emptyMap(), val now: Long = 1_793_801_000_000L,
    )

    @Test fun renderScreens() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        runBlocking {
            app.container.settings.setManualCity("dxb")
            app.container.settings.setOnboardingDone()
        }
        val base = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = "dxb")
        var screen by mutableStateOf(Screen.TODAY)
        var theme by mutableStateOf(ThemeMode.DARK)
        var arabic by mutableStateOf(false)
        var reading by mutableStateOf<CompassReading?>(null)
        var watch by mutableStateOf<WatchScene?>(null)
        var singapore by mutableStateOf(false)
        var clock by mutableStateOf(now)
        var fontScale by mutableStateOf(1f)
        var hijriCalendar by mutableStateOf(false)
        var alarmEdit by mutableStateOf(0)
        val sgBase = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualPlace = com.noor.app.data.KnownLocation(
            com.noor.prayer.GeoPoint(1.28967, 103.85007), "Asia/Singapore", "Singapore", "SG", null, 0L, "سنغافورة"))
        com.noor.app.ui.today.SkyCompute.synchronous = true
        rule.setContent {
            val ctxBase = LocalContext.current
            val config = Configuration(ctxBase.resources.configuration).apply {
                setLocale(if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH)
                this.fontScale = fontScale
                setLayoutDirection(if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH)
            }
            val ctx = ctxBase.createConfigurationContext(config)
            CompositionLocalProvider(
                com.noor.app.ui.components.LocalAmbientMotion provides false,
                LocalContext provides ctx,
                LocalConfiguration provides config,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalActivityResultRegistryOwner provides rule.activity,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, fontScale),
            ) {
                val s = (if (singapore) sgBase else base).copy(theme = theme)
                val tables = app.container.timetables
                val u = todayUi(s, tables, clock)
                val cache = DayCache(tables)
                // (0.15: the pages stand on the sky of the moment at the place, as in the app.)
                val sun = PrayerProvider.place(s)?.let { p ->
                    val e = com.noor.prayer.astro.SunPath.elevation(clock, p.point)
                    e to (com.noor.prayer.astro.SunPath.elevation(clock.plusSeconds(600), p.point) < e)
                }
                NoorTheme(theme, sun = sun) {
                    NoorBackground {
                        val pad = PaddingValues(top = 28.dp, bottom = 16.dp)
                        when (screen) {
                            Screen.TODAY -> TodayContent(u, pad, { cache.resolved(s, u.place!!, u.profile!!, it) }, {}, {}, {})
                            Screen.TODAY_OTHER_DATE -> TodayContent(u, pad, { cache.resolved(s, u.place!!, u.profile!!, it) }, {}, {}, {},
                                initialDate = LocalDate.of(2026, 12, 18))
                            Screen.CALENDAR -> androidx.compose.runtime.key(hijriCalendar) { Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(NoorTheme.colors.surface)) {
                                    CalendarPicker(LocalDate.of(2026, 12, 18), u.today!!, s.hijriAdjust,
                                        Formatter(arabic, Numerals.WESTERN, TimeFormat.H12), {}, Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                                        startHijri = hijriCalendar)
                                }
                            } }
                            Screen.QIBLA -> {
                                val place = PrayerProvider.place(s)
                                QiblaContent(pad, QiblaPlace(s, place, place?.let { Qibla.from(it.point) }), reading, false, {})
                            }
                            // (The sheet opens in a window of its own, which this capture cannot see: its content is shown
                            // on a stand-in with the sheet's corners and grabber, 36 by 5 dp, as NoorSheet draws them.)
                            Screen.CALIBRATION -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.BottomCenter) {
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(NoorTheme.colors.surface),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 5.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(NoorTheme.colors.textMuted.copy(alpha = 0.35f)))
                                    CalibrationGuide(reading)
                                }
                            }
                            // (As the app opens it: from Settings, with the way back; at its top the user's watch face,
                            // here Minhad Studio's first design, drawn from the configuration the phone sends the watch.)
                            Screen.WATCH_STATE -> watch?.let { w ->
                                WatchContent(pad, w.state, true, w.lastSent, w.installs, w.faces, false, false, w.fromPlay,
                                    Formatter(arabic, Numerals.WESTERN, TimeFormat.H12),
                                    WatchActions({}, {}, {}, {}, {}, studioDesign = com.noor.prayer.face.FaceTemplates.DEFAULT,
                                        studioConfig = remember(s) { com.noor.app.data.WearSync.configFor(s, clock.toEpochMilli()) }),
                                    now = w.now, onBack = {})
                            }
                            Screen.SETTINGS -> SettingsScreen(pad, {}, {})
                            Screen.WATCH -> WatchScreen(pad, onBack = {})
                            Screen.ACCURACY -> AccuracyScreen(pad, {})
                            Screen.ADHAN -> com.noor.app.ui.adhan.AdhanScreen(pad, {}, {}, {})
                            Screen.ADHAN_FAJR -> com.noor.app.ui.adhan.PrayerAlertScreen(pad, com.noor.prayer.Prayer.FAJR, {}, {})
                            Screen.ADHAN_SHURUQ -> com.noor.app.ui.adhan.PrayerAlertScreen(pad, com.noor.prayer.Prayer.SHURUQ, {}, {})
                            Screen.ADHAN_LIBRARY -> com.noor.app.ui.adhan.SoundLibraryScreen(pad, {})
                            Screen.ADHAN_SOUNDS -> com.noor.app.ui.adhan.MySoundsScreen(pad, {})
                            Screen.ADHAN_HISTORY -> com.noor.app.ui.adhan.AlertHistoryScreen(pad, {})
                            Screen.ADHAN_VOICE -> com.noor.app.ui.adhan.VoiceScreen(pad, null, {})
                            Screen.ALARM_EDIT -> androidx.compose.runtime.key(alarmEdit) { com.noor.app.ui.adhan.AlarmScreen(pad, alarmEdit, {}) }
                            // (0.25: the texts as the app writes them with its digits as first set, Western in both languages:
                            // until 0.24 the Arabic ones here were typed with Arabic-Indic digits, which the app shows only
                            // when they are chosen. The time at the top is 25 minutes before that Fajr.)
                            Screen.ALARM_RING -> com.noor.app.adhan.AdhanScreen(com.noor.alerts.AdhanService.Playing(
                                if (arabic) "25 دقيقة قبل الفجر" else "25 minutes before Fajr", if (arabic) "الفجر 5:12 ص · دبي" else "Fajr 5:12 AM · Dubai",
                                clock.toEpochMilli(), 2, if (arabic) "غفوة 10 د" else "Snooze 10 min"), {}, {}, {},
                                clock = Formatter(arabic, Numerals.WESTERN, TimeFormat.H12).time(4 * 60 + 47))
                            Screen.ONBOARDING -> com.noor.app.ui.onboarding.OnboardingScreen(pad, {}, {})
                            Screen.CITIES -> com.noor.app.ui.location.LocationScreen(pad, {})
                            Screen.SCENERY -> com.noor.app.ui.settings.SceneryScreen(pad, {})
                            Screen.SHELL -> Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f)) { TodayContent(u, PaddingValues(top = 28.dp), { cache.resolved(s, u.place!!, u.profile!!, it) }, {}, {}, {}) }
                                NoorTabBar(Routes.PRAYERS, {}, fade = false)
                            }
                        }
                    }
                }
            }
        }
        fun shot(name: String, waitFor: String?) {
            if (waitFor != null) rule.waitUntil(20_000) { rule.onAllNodesWithText(waitFor, substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.mainClock.advanceTimeBy(1200)
            rule.waitForIdle()
            // captureToImage() waits on a PixelCopy callback posted to the main looper, which Robolectric runs on the
            // test thread, so it deadlocks. Drawing the window's view tree into a bitmap is the same software render.
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            val dir = File("build/screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            checkLanguage(name, arabic)
        }
        shot("01_today_night_en", "Maghrib")
        screen = Screen.TODAY_OTHER_DATE; shot("02_other_date_night_en", "18 December 2026")
        screen = Screen.CALENDAR; shot("03_calendar_night_en", "December 2026")
        screen = Screen.TODAY; arabic = true; shot("04_today_night_ar", "المغرب")
        screen = Screen.CALENDAR; shot("05_calendar_night_ar", "ديسمبر")
        // Browsing by Hijri month (0.9.4).
        hijriCalendar = true; shot("57_calendar_hijri_night_ar", "رجب")
        arabic = false; shot("56_calendar_hijri_night_en", "Rajab"); hijriCalendar = false; arabic = true
        screen = Screen.TODAY; theme = ThemeMode.LIGHT; arabic = false; shot("06_today_day_en", "Maghrib")
        screen = Screen.TODAY_OTHER_DATE; shot("07_other_date_day_en", "18 December 2026")
        theme = ThemeMode.DARK
        val qibla = Qibla.from(PrayerProvider.place(base)!!.point).bearingDeg
        reading = reading(174.82, 7.5, 3, 44.0, 3.0)
        fun shown(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
        screen = Screen.QIBLA; shot("11_qibla_turn_night_en", "Turn right")
        // 0.25: whole degrees where the phone's compass speaks (the turn, the two readings); the Qibla's own bearing,
        // which is calculated, to a tenth, once.
        assertTrue("the turn, the Qibla and the heading are in whole degrees", shown("83°") == 1 && shown("258°") == 1 && shown("175°") == 1)
        assertTrue("hundredths of a degree are shown", shown("83.49") == 0 && shown("258.31") == 0 && shown("174.82") == 0)
        assertTrue("the Qibla's calculated bearing is given once", shown("258.3°") == 1)
        reading = reading(qibla - 1.2, 6.0, 3, 44.0, 2.0)
        shot("12_qibla_aligned_night_en", "Facing the Qibla")
        reading = reading(301.4, 34.0, 1, 71.0, 4.0)
        shot("13_qibla_poor_night_en", "Poor")
        reading = reading(250.0, 8.0, 3, 44.0, 62.0)
        shot("14_qibla_not_flat_night_en", "Hold your phone flat")
        assertTrue("\"Hold your phone flat\" is said once, in the dial", shown("Hold your phone flat") == 1)
        arabic = true; reading = reading(174.82, 7.5, 3, 44.0, 3.0)
        shot("15_qibla_turn_night_ar", "القبلة")
        arabic = false; theme = ThemeMode.LIGHT; reading = reading(qibla - 1.2, 6.0, 3, 44.0, 2.0)
        shot("16_qibla_aligned_day_en", "Facing the Qibla")
        // 0.22.1: the unsure compass shows the figure 8 drawn (by day, and in Arabic).
        reading = reading(301.4, 34.0, 1, 71.0, 4.0); shot("94_qibla_poor_day_en", "figure 8")
        theme = ThemeMode.DARK; arabic = true; shot("95_qibla_poor_night_ar", "الرقم 8"); arabic = false
        theme = ThemeMode.DARK; reading = reading(301.4, 34.0, 1, 71.0, 4.0)
        screen = Screen.CALIBRATION; shot("17_calibration_night_en", "Calibrate")
        // Watch setup, every state from nothing to done (as the phone would see it).
        val gw = WatchNode("node1", "Galaxy Watch5 Pro (4E2A)")
        val t0 = 1_793_801_000_000L
        val doneReport = WatchReport(2, "0.3.0", 36, true, 1, 1, true, t0 - 60_000, t0)
        screen = Screen.WATCH_STATE
        watch = WatchScene(WatchSetupState(false, emptyList(), emptySet(), emptyMap())); shot("18_watch_none_night_en", "Your watch is not connected yet")
        watch = WatchScene(WatchSetupState(false, listOf(gw), emptySet(), emptyMap()), fromPlay = false); shot("19_watch_dev_build_night_en", "not installed from the Play Store")
        // (A copy that did come from the Play Store never says so: it offers to install the watch app from there.)
        watch = WatchScene(WatchSetupState(false, listOf(gw), emptySet(), emptyMap())); rule.waitForIdle()
        assertTrue("a Play Store install is told it is not one", shown("not installed from the Play Store") == 0 && shown("Install on watch") == 1)
        watch = WatchScene(WatchSetupState(false, listOf(gw), emptySet(), emptyMap()), installs = mapOf("node1" to Pending(t0 - 30_000, true)), now = t0)
        shot("20_watch_installing_night_en", "Tap Install there")
        watch = WatchScene(WatchSetupState(false, listOf(gw), setOf("node1"), mapOf("node1" to doneReport.copy(faceInstalledVersion = 1, faceActive = false))), now = t0)
        shot("21_watch_face_ready_night_en", "Show on my watch")
        watch = WatchScene(WatchSetupState(false, listOf(gw), setOf("node1"), mapOf("node1" to doneReport)), now = t0); shot("22_watch_done_night_en", "In use")
        watch = WatchScene(WatchSetupState(false, listOf(gw.copy(name = "Galaxy Watch4")), setOf("node1"), mapOf("node1" to doneReport.copy(sdkInt = 34, facePushSupported = false, faceActive = false, faceInstalledVersion = null))), now = t0)
        shot("23_watch_wearos5_night_en", "Needs Wear OS 6")
        arabic = true; watch = WatchScene(WatchSetupState(false, listOf(gw), setOf("node1"), mapOf("node1" to doneReport)), now = t0); shot("24_watch_done_night_ar", "قيد الاستخدام")
        arabic = false
        // (Dialogs open in their own window, which this window capture cannot see; they are checked on a device.)
        screen = Screen.CITIES; shot("44_cities_night_en", "Browse by country")
        screen = Screen.SHELL; shot("46_app_shell_night_en", "Maghrib")
        theme = ThemeMode.LIGHT; shot("47_app_shell_day_en", "Maghrib")
        arabic = true; shot("48_app_shell_day_ar", "المغرب"); arabic = false; theme = ThemeMode.DARK
        screen = Screen.TODAY; fontScale = 1.3f; shot("45_today_large_text_en", "Maghrib"); fontScale = 1f
        // Singapore: MUIS's own published times for the date (official timetable), not a calculation.
        singapore = true; screen = Screen.TODAY; shot("25_today_singapore_official_en", "Singapore")
        singapore = false
        screen = Screen.SETTINGS; shot("08_settings_night_en", "Language and region")
        // Settings search (0.9.4): typing finds the setting, here the calculation method by a word it is known by
        // (0.15: it lives on the Prayer times page; the result says so, and works from here).
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("dulook")
        shot("58_settings_search_en", "Calculation method")
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextClearance()
        // 0.15: the two pages Settings opens: how the times are worked out, and language and region.
        rule.onAllNodes(androidx.compose.ui.test.hasText("Prayer times")).onFirst().performClick()
        shot("80_settings_times_en", "Calculation method")
        screen = Screen.WATCH; rule.waitForIdle(); screen = Screen.SETTINGS
        rule.onAllNodes(androidx.compose.ui.test.hasText("Language and region")).onFirst().performClick()
        shot("81_settings_language_en", "Numerals")
        // 0.25: the notification bar's page and About (Minhad's mark, the version, the accuracy, the recordings).
        screen = Screen.WATCH; rule.waitForIdle(); screen = Screen.SETTINGS
        rule.onAllNodes(androidx.compose.ui.test.hasText("Countdown notification")).onFirst().performClick()
        shot("96_settings_status_en", "one swipe away")
        screen = Screen.WATCH; rule.waitForIdle(); screen = Screen.SETTINGS
        rule.onAllNodes(androidx.compose.ui.test.hasText("About Minhad")).onFirst().performClick()
        shot("97_settings_about_en", "Version")
        rule.onAllNodes(androidx.compose.ui.test.hasText("Acknowledgements")).onFirst().performClick()
        shot("99_settings_credits_en", "IBM Plex Sans Arabic")
        // (The preview's configuration is worked out off the main thread: wait for it, as the eye would.)
        screen = Screen.WATCH; rule.waitForIdle()
        rule.waitUntil(20_000) { androidx.lifecycle.ViewModelProvider(rule.activity)[com.noor.app.ui.watch.WatchViewModel::class.java].config.value != null }
        shot("09_watch_night_en", "Galaxy")
        screen = Screen.ACCURACY; shot("10_accuracy_night_en", "IACAD")
        // Arabic versions of the text-heavy pages, so the language check covers them (0.9).
        arabic = true
        screen = Screen.SETTINGS; shot("51_settings_night_ar", "الإعدادات")
        rule.onAllNodes(androidx.compose.ui.test.hasText("أوقات الصلاة")).onFirst().performClick()
        shot("82_settings_times_ar", "طريقة الحساب")
        screen = Screen.WATCH; rule.waitForIdle(); screen = Screen.SETTINGS
        rule.onAllNodes(androidx.compose.ui.test.hasText("عن منهاد")).onFirst().performClick()
        shot("98_settings_about_ar", "الإصدار")
        rule.onAllNodes(androidx.compose.ui.test.hasText("شكر وإسناد")).onFirst().performClick()
        shot("100_settings_credits_ar", "الخطوط")
        screen = Screen.ACCURACY; shot("52_accuracy_night_ar", "دبي")
        screen = Screen.CITIES; shot("53_cities_night_ar", "التصفح حسب الدولة")
        // 0.14: the scenery page in Arabic (the language check had not seen it), Settings by day, and Settings with
        // large text, where a row's choice moves under its title instead of being squeezed beside it.
        screen = Screen.SCENERY; shot("77_scenery_night_ar", "الفصول")
        arabic = false
        theme = ThemeMode.LIGHT; screen = Screen.SETTINGS; shot("78_settings_day_en", "Language and region")
        theme = ThemeMode.DARK; fontScale = 1.3f; shot("79_settings_large_text_en", "Language and region"); fontScale = 1f
        // The sky through the day (Dubai, 4 November 2026): dawn, morning, sunset glow, night with the moon.
        theme = ThemeMode.DARK; arabic = false; screen = Screen.TODAY
        fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, ZoneId.of("Asia/Dubai")).toInstant()
        clock = at(5, 52); shot("36_sky_dawn_en", "Shuruq")
        clock = at(10, 30); shot("37_sky_morning_en", "Dhuhr")
        clock = at(17, 46); shot("38_sky_sunset_en", "Isha")
        clock = at(21, 30); shot("39_sky_night_en", "Fajr")
        arabic = true; clock = at(17, 20); shot("40_sky_golden_ar", "المغرب")
        arabic = false; theme = ThemeMode.LIGHT; clock = at(12, 40); shot("41_sky_noon_day_en", "Asr")
        theme = ThemeMode.DARK; clock = now
        // Adhan & alerts: off (first run), then on for phone and watch, the Fajr editor, sunrise, library and imports.
        // (0.25: the switch has one name, on or off; what hangs on it is dimmed and not read out while it is off.)
        screen = Screen.ADHAN; shot("26_adhan_off_night_en", "Prayer alerts")
        // 0.14: a prayer's page (reached from the home screen's rows too) says that all alerts are off, and turns them on.
        screen = Screen.ADHAN_FAJR; shot("81_adhan_fajr_alerts_off_en", "Turn on prayer alerts")
        arabic = true; shot("82_adhan_fajr_alerts_off_ar", "تشغيل تنبيهات الصلاة"); arabic = false
        screen = Screen.ADHAN
        runBlocking { app.container.settings.updateAlerts { it.copy(enabled = true, device = com.noor.prayer.alerts.AlertDevice.BOTH) } }
        // (0.25: one voice for every prayer is a row that opens the voices; the page of what is set once is named for
        // what it holds.)
        shot("27_adhan_on_night_en", "Adhan voice")
        // 0.15.2: what is set once (phone or watch, the sound's behaviour, the library) is on a page of its own.
        rule.onAllNodes(androidx.compose.ui.test.hasText("Sound and devices")).onFirst().performScrollTo().performClick()
        shot("83_adhan_options_night_en", "Gradual volume")
        screen = Screen.WATCH; rule.waitForIdle()
        screen = Screen.ADHAN_FAJR; shot("28_adhan_fajr_night_en", "Dubai")
        screen = Screen.ADHAN_SHURUQ; runBlocking {
            // (0.16: no alert sounds any more: sunrise with a notification, a reminder after it and some days only. 0.17:
            // the reminder becomes an alarm, "15 min after Sunrise", which posts a notification as the reminder did.)
            app.container.settings.updateAlerts { it.with(com.noor.prayer.Prayer.SHURUQ, it.alert(com.noor.prayer.Prayer.SHURUQ).copy(enabled = true,
                mode = com.noor.prayer.alerts.AlertMode.NOTIFICATION, afterMinutes = 15, days = 0b0011111)) }
        }
        shot("29_adhan_sunrise_night_en", "Mon–Fri")
        // 0.17: alarms before and after the prayers, in the Adhan tab's list and in their editor; and ringing.
        runBlocking { app.container.settings.updateAlerts { it.withAlarm(com.noor.prayer.alerts.PrayerAlarm(0, com.noor.prayer.Prayer.FAJR, 25, after = false))
            .withAlarm(com.noor.prayer.alerts.PrayerAlarm(0, com.noor.prayer.Prayer.DHUHR, 10, after = true, days = 0b0011111, snoozeMinutes = 5)) } }
        val fajrAlarm = runBlocking { app.container.settings.current().alerts.alarms.first { it.prayer == com.noor.prayer.Prayer.FAJR }.id }
        // (0.17.1: the alarms are a page of their own under the Adhan tab's switch.)
        screen = Screen.ADHAN; rule.waitUntil(20_000) { rule.onAllNodesWithText("Alarms").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(androidx.compose.ui.test.hasText("Alarms")).onFirst().performClick()
        shot("87_adhan_alarms_night_en", "25 min before Fajr")
        arabic = true; shot("88_adhan_alarms_night_ar", "قبل الفجر")
        rule.onAllNodes(androidx.compose.ui.test.hasText("الصلوات")).onFirst().performClick(); rule.waitForIdle(); arabic = false
        // (When it rings is worked out off the main thread: wait for it in place of the "–" shown until then.)
        fun ringShown() { rule.waitForIdle(); rule.waitUntil(20_000) { rule.onAllNodesWithText("–").fetchSemanticsNodes().isEmpty() } }
        alarmEdit = 0; screen = Screen.ALARM_EDIT; ringShown(); shot("89_alarm_new_night_en", "New alarm")
        alarmEdit = fajrAlarm; arabic = true; ringShown(); shot("90_alarm_edit_night_ar", "تعديل المنبه"); arabic = false
        theme = ThemeMode.LIGHT; shot("91_alarm_edit_day_en", "Edit alarm"); theme = ThemeMode.DARK
        screen = Screen.ALARM_RING; shot("92_alarm_ringing_night_en", "Snooze 10 min")
        arabic = true; shot("93_alarm_ringing_night_ar", "غفوة"); arabic = false
        // 0.16: the voices, named as on the owner's clock.
        screen = Screen.ADHAN_VOICE; shot("85_adhan_voice_night_en", "Al-Quds")
        arabic = true; shot("86_adhan_voice_night_ar", "القدس"); arabic = false
        screen = Screen.ADHAN_LIBRARY; shot("30_adhan_library_night_en", "Madinah")
        screen = Screen.ADHAN_SOUNDS; shot("31_adhan_my_sounds_night_en", "Import a sound")
        arabic = true; screen = Screen.ADHAN_LIBRARY; shot("54_adhan_library_night_ar", "المدينة"); arabic = false
        screen = Screen.ADHAN; arabic = true; shot("32_adhan_on_night_ar", "صوت الأذان")
        rule.onAllNodes(androidx.compose.ui.test.hasText("الصوت والأجهزة")).onFirst().performScrollTo().performClick()
        shot("84_adhan_options_night_ar", "التنبيه على")
        screen = Screen.ADHAN_FAJR; shot("33_adhan_fajr_night_ar", "دبي")
        arabic = false; theme = ThemeMode.LIGHT; screen = Screen.ADHAN; shot("34_adhan_on_day_en", "Sound and devices")
        screen = Screen.ADHAN_FAJR; shot("35_adhan_fajr_day_en", "Dubai")
        theme = ThemeMode.DARK
        // Alert history (0.9): what really happened at each alert, with examples of each kind of outcome.
        run {
            val day = java.time.ZonedDateTime.now(ZoneId.systemDefault()).withHour(12).withMinute(0).withSecond(0).withNano(0)
            fun rec(i: Long, p: com.noor.prayer.Prayer, h: Int, m: Int, o: com.noor.alerts.AlertOutcome, played: Long = 0, delay: Long = 2_000,
                    test: Boolean = false, stopped: Boolean = false, exact: Boolean = true) {
                val due = day.withHour(h).withMinute(m).toInstant().toEpochMilli()
                com.noor.alerts.AlertLog.add(app, com.noor.alerts.AlertRecord(i, p, test, due, due + delay, o, "adhan.makkah",
                    played, stopped, exact, 80, 1))
            }
            com.noor.alerts.AlertLog.clear(app)
            rec(1, com.noor.prayer.Prayer.FAJR, 5, 9, com.noor.alerts.AlertOutcome.BACKUP_SOUND, delay = 180_000, exact = false)
            rec(2, com.noor.prayer.Prayer.DHUHR, 12, 5, com.noor.alerts.AlertOutcome.PLAYED, played = 176_000)
            rec(3, com.noor.prayer.Prayer.ASR, 15, 14, com.noor.alerts.AlertOutcome.PLAYED, played = 41_000, stopped = true)
            rec(4, com.noor.prayer.Prayer.MAGHRIB, 17, 34, com.noor.alerts.AlertOutcome.DND_BLOCKS_ALARMS)
            rec(5, com.noor.prayer.Prayer.ISHA, 18, 50, com.noor.alerts.AlertOutcome.PLAYED_FALLBACK, played = 32_000, test = true)
        }
        // (The history is read from disk off the main thread: wait for its entries, not only the page's title.)
        screen = Screen.ADHAN_HISTORY; rule.waitForIdle(); rule.waitUntil(20_000) { rule.onAllNodesWithText("Isha · Test").fetchSemanticsNodes().isNotEmpty() }
        shot("49_adhan_history_night_en", "Alert history")
        arabic = true; shot("50_adhan_history_night_ar", "سجل التنبيهات")
        arabic = false
        screen = Screen.ONBOARDING; shot("42_onboarding_en", "Prayer times from the sky above you")
        arabic = true; shot("43_onboarding_ar", "منهاد")
        arabic = false
        assertTrue("Text in the wrong language:\n" + languageProblems.joinToString("\n"), languageProblems.isEmpty())
    }

    /**
     * Language separation (0.9): an English screen shows no Arabic letters and an Arabic screen no Latin letters, apart
     * from what is not language: Noor's Kufi logo, the names of devices and licences, and the people credited.
     */
    private val languageProblems = mutableListOf<String>()
    private val arabicLetters = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
    private val latinLetters = Regex("[A-Za-z]")
    private val allowedInEnglish = listOf("منهاد")
    private val allowedInArabic = listOf(
        // (0.25) The acknowledgements: the names of those credited and of their licences, written as they write them,
        // and the two notices their licences ask to have shown word for word (Kartverket, Copernicus). (First: they hold
        // shorter names the rules below would take out of them.)
        Regex("IBM Plex Sans Arabic|IBM Corp\\.|Reem Kufi|The Reem Kufi Project Authors|SIL Open Font License 1\\.1|Esri|Photon|photon\\.komoot\\.io|Google"),
        Regex("Singapore Open Data Licence|Solar Position Algorithm|I\\. Reda|A\\. Andreas|NREL|Poly Haven|ambientCG|USGS 3DEP|CGHEVEN"),
        Regex("NASA/Goddard Space Flight Center Scientific Visualization Studio|Material Symbols|Android Jetpack|Kotlin|Apache License 2\\.0"),
        Regex("Contains data from Kartverket \\(the Norwegian Mapping Authority\\), Nasjonal høydemodell, CC BY 4\\.0\\."),
        Regex("Contains modified Copernicus DEM data: © DLR e\\.V\\. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018, provided under COPERNICUS by the European Union and ESA; all rights reserved\\."),
        Regex("(Galaxy|Pixel) Watch\\S*( Pro| Classic)?( \\([0-9A-F]{4}\\))?"), // a device's own name, as Android reports it
        Regex("Wear OS"), Regex("Galaxy Wearable"), Regex("WGS-84"), Regex("\\b(MP3|M4A|AAC|WAV|OGG|Opus|FLAC)\\b"), // product, standard and format names
        Regex("CC0|CC[ \u00A0]BY(-SA)?[ \u00A0]\\d\\.\\d"), // licence names
        Regex("\\b[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(ae|sg|org|com|gov)\\b"), Regex("GeoNames"), // web addresses and the gazetteer's name
        Regex("timezone-boundary-builder|OpenStreetMap|ODbL"), // the time-zone map's source and licence (0.9.7)
    ) + creditedPeople()

    /** The recordists credited in the sound library: their names are written as credited, in every language. */
    private fun creditedPeople(): List<Regex> = Regex("\"author\":\\s*\"([^\"]+)\"").findAll(File("src/main/assets/sounds/library.json").readText())
        .map { Regex(Regex.escape(it.groupValues[1])) }.toList()

    private fun checkLanguage(name: String, arabic: Boolean) {
        val texts = rule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { n ->
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                listOfNotNull(n.config.getOrNull(SemanticsProperties.EditableText)?.text)
        }.distinct()
        texts.forEach { t ->
            if (arabic) {
                val cleaned = allowedInArabic.fold(t) { acc, r -> acc.replace(r, "") }
                if (latinLetters.containsMatchIn(cleaned)) languageProblems += "$name (Arabic screen): \"$t\""
            } else {
                val cleaned = allowedInEnglish.fold(t) { acc, w -> acc.replace(w, "") }
                if (arabicLetters.containsMatchIn(cleaned)) languageProblems += "$name (English screen): \"$t\""
            }
        }
    }
}
