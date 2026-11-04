package com.noor.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.noor.app.data.compass.CompassReading
import com.noor.app.ui.qibla.CalibrationGuide
import com.noor.app.ui.qibla.QiblaContent
import com.noor.app.ui.qibla.QiblaPlace
import com.noor.prayer.qibla.CompassAssessor
import com.noor.prayer.qibla.Qibla
import com.noor.app.util.Formatter
import com.noor.prayer.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
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

    private enum class Screen { TODAY, TODAY_OTHER_DATE, CALENDAR, SETTINGS, WATCH, ACCURACY, QIBLA, CALIBRATION }

    /** Wednesday 4 November 2026, 15:29:21 in Dubai: Asr is in progress, Maghrib is next. */
    private val now = ZonedDateTime.of(2026, 11, 4, 15, 29, 21, 0, ZoneId.of("Asia/Dubai")).toInstant()

    private fun todayUi(s: AppSettings): TodayUi {
        val place = PrayerProvider.place(s)!!
        val profile = PrayerProvider.profile(s, place)
        val cache = DayCache()
        val state = Schedule.state(now, place.zone) { cache.get(s, place, profile, it) }
        return TodayUi(s, place, profile, now, state, now.atZone(place.zone).toLocalDate(), refreshing = false, lastRefresh = null)
    }

    /** A synthetic reading, as CompassSensor would produce it (no sensors under Robolectric). */
    private fun reading(heading: Double, accuracyDeg: Double?, magStatus: Int, fieldUt: Double, tilt: Double) = CompassReading(
        trueHeadingDeg = heading, declinationDeg = 1.95, headingAccuracyDeg = accuracyDeg, magnetometerStatus = magStatus,
        fieldMicroTesla = fieldUt, expectedFieldMicroTesla = 44.0, tiltDeg = tilt,
        assessment = CompassAssessor.assess(accuracyDeg, magStatus, fieldUt, 44.0, tilt), source = CompassReading.Source.ROTATION_VECTOR,
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
        rule.setContent {
            val ctxBase = LocalContext.current
            val config = Configuration(ctxBase.resources.configuration).apply {
                setLocale(if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH)
                setLayoutDirection(if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH)
            }
            val ctx = ctxBase.createConfigurationContext(config)
            CompositionLocalProvider(
                LocalContext provides ctx,
                LocalConfiguration provides config,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalActivityResultRegistryOwner provides rule.activity,
            ) {
                val s = base.copy(theme = theme)
                val u = todayUi(s)
                val cache = DayCache()
                NoorTheme(theme) {
                    NoorBackground {
                        val pad = PaddingValues(top = 28.dp, bottom = 16.dp)
                        when (screen) {
                            Screen.TODAY -> TodayContent(u, pad, { cache.get(s, u.place!!, u.profile!!, it) }, {}, {}, {})
                            Screen.TODAY_OTHER_DATE -> TodayContent(u, pad, { cache.get(s, u.place!!, u.profile!!, it) }, {}, {}, {},
                                initialDate = LocalDate.of(2026, 12, 18))
                            Screen.CALENDAR -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(NoorTheme.colors.surface)) {
                                    CalendarPicker(LocalDate.of(2026, 12, 18), u.today!!, s.hijriAdjust,
                                        Formatter(arabic, Numerals.WESTERN, TimeFormat.H12), {}, Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
                                }
                            }
                            Screen.QIBLA -> {
                                val place = PrayerProvider.place(s)
                                QiblaContent(pad, QiblaPlace(s, place, place?.let { Qibla.from(it.point) }), reading, false, {})
                            }
                            Screen.CALIBRATION -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(NoorTheme.colors.surface).padding(top = 24.dp)) {
                                    CalibrationGuide(reading)
                                }
                            }
                            Screen.SETTINGS -> SettingsScreen(pad, {}, {})
                            Screen.WATCH -> WatchScreen(pad)
                            Screen.ACCURACY -> AccuracyScreen(pad, {})
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
        }
        shot("01_today_night_en", "Maghrib")
        screen = Screen.TODAY_OTHER_DATE; shot("02_other_date_night_en", "18 December 2026")
        screen = Screen.CALENDAR; shot("03_calendar_night_en", "December 2026")
        screen = Screen.TODAY; arabic = true; shot("04_today_night_ar", "المغرب")
        screen = Screen.CALENDAR; shot("05_calendar_night_ar", "ديسمبر")
        screen = Screen.TODAY; theme = ThemeMode.LIGHT; arabic = false; shot("06_today_day_en", "Maghrib")
        screen = Screen.TODAY_OTHER_DATE; shot("07_other_date_day_en", "18 December 2026")
        theme = ThemeMode.DARK
        val qibla = Qibla.from(PrayerProvider.place(base)!!.point).bearingDeg
        reading = reading(174.82, 7.5, 3, 44.0, 3.0)
        screen = Screen.QIBLA; shot("11_qibla_turn_night_en", "Turn right")
        reading = reading(qibla - 1.2, 6.0, 3, 44.0, 2.0)
        shot("12_qibla_aligned_night_en", "Facing the Qibla")
        reading = reading(301.4, 34.0, 1, 71.0, 4.0)
        shot("13_qibla_poor_night_en", "Poor")
        reading = reading(250.0, 8.0, 3, 44.0, 62.0)
        shot("14_qibla_not_flat_night_en", "Hold your phone flat")
        arabic = true; reading = reading(174.82, 7.5, 3, 44.0, 3.0)
        shot("15_qibla_turn_night_ar", "القبلة")
        arabic = false; theme = ThemeMode.LIGHT; reading = reading(qibla - 1.2, 6.0, 3, 44.0, 2.0)
        shot("16_qibla_aligned_day_en", "Facing the Qibla")
        theme = ThemeMode.DARK; reading = reading(301.4, 34.0, 1, 71.0, 4.0)
        screen = Screen.CALIBRATION; shot("17_calibration_night_en", "Calibrate")
        screen = Screen.SETTINGS; shot("08_settings_night_en", "Numerals")
        screen = Screen.WATCH; shot("09_watch_night_en", "Galaxy")
        screen = Screen.ACCURACY; shot("10_accuracy_night_en", "IACAD")
    }
}
