package com.noor.app.design

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.NoorApplication
import com.noor.app.data.KnownLocation
import com.noor.app.data.SkyGraphics
import com.noor.app.data.ThemeMode
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.GeoPoint
import com.noor.prayer.Prayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * (0.21) Every page of the app whole, top to foot, on a phone of the owner's width (a Samsung's 360 dp), in English, in
 * Arabic, and in English with the largest common text size (1.5), in a window tall enough to hold the page, so that
 * what lies below the first screen is seen too (the screenshots show one screen of each page). Pages and states the
 * other renders never showed are here: the scenery page at each graphics level, the alarms, a prayer's page with its
 * alert on, Minhad Studio, the tester's page, a long place name. Fails if a page shows text in the other language.
 * Software renders (build/design/pages), not photos of a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-hdpi")
class PagesDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var theme by mutableStateOf(ThemeMode.DARK)
    private var arabic by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)
    private var page by mutableStateOf<(@Composable (PaddingValues) -> Unit)?>(null)
    /** Counts up to compose a page afresh (its own remembered state, as when it is opened). */
    private var fresh by mutableIntStateOf(0)

    @Test fun pages() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        runBlocking {
            repo.setManualCity("dxb"); repo.setOnboardingDone()
            repo.updateAlerts { it.copy(enabled = true, device = com.noor.prayer.alerts.AlertDevice.PHONE)
                .withAlarm(com.noor.prayer.alerts.PrayerAlarm(0, Prayer.DHUHR, 10, after = true, days = 0b0011111, snoozeMinutes = 5, label = "Leave the office for the mosque"))
                .withAlarm(com.noor.prayer.alerts.PrayerAlarm(0, Prayer.FAJR, 25, after = false)) }
        }
        val fajrAlarm = runBlocking { repo.current().alerts.alarms.first { it.prayer == Prayer.FAJR }.id }
        com.noor.app.ui.today.SkyCompute.synchronous = true
        rule.setContent {
            val base = LocalContext.current
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); this.fontScale = this@PagesDesignTest.fontScale; setLayoutDirection(locale) }
            CompositionLocalProvider(
                LocalAmbientMotion provides false,
                LocalContext provides base.createConfigurationContext(config),
                LocalConfiguration provides config,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalActivityResultRegistryOwner provides rule.activity,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                NoorTheme(theme) {
                    NoorBackground {
                        key(fresh) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { page?.invoke(PaddingValues(top = 28.dp, bottom = 112.dp)) } }
                    }
                }
            }
        }

        fun settle() {
            for (i in 0 until 20) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle(); Thread.sleep(10) }
            rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        }
        fun click(en: String, ar: String) { rule.onAllNodes(hasText(if (arabic) ar else en)).onFirst().performClick(); settle() }
        fun open(p: @Composable (PaddingValues) -> Unit) { page = p; fresh++; settle() }
        val variants = listOf(Triple("en", false, 1f), Triple("ar", true, 1f), Triple("en_150", false, 1.5f))
        for ((tag, ar, scale) in variants) {
            arabic = ar; fontScale = scale
            fun shot(name: String) { save("${name}_$tag"); if (scale == 1f) checkLanguage("${name}_$tag") }

            open { com.noor.app.ui.settings.SettingsScreen(it, {}, {}) }; shot("settings")
            click("Prayer times", "أوقات الصلاة"); shot("settings_times")
            open { com.noor.app.ui.settings.SettingsScreen(it, {}, {}) }
            click("Language and region", "اللغة والمنطقة"); shot("settings_language")
            for (g in SkyGraphics.entries) {
                runBlocking { repo.setSkyGraphics(g) }
                open { com.noor.app.ui.settings.SceneryScreen(it, {}) }; shot("scenery_${g.name.lowercase()}")
            }
            runBlocking { repo.setSkyGraphics(SkyGraphics.AUTO) }
            open { com.noor.app.ui.settings.AccuracyScreen(it, {}) }; shot("accuracy")
            open { com.noor.app.ui.watch.WatchScreen(it, onBack = {}) }; shot("watch")
            open { com.noor.app.ui.watch.studio.StudioScreen(it, {}) }; shot("studio")
            open { com.noor.app.ui.adhan.AdhanScreen(it, {}, {}, {}) }; shot("adhan")
            click("Devices and silent mode", "الأجهزة والوضع الصامت"); shot("adhan_more")
            open { com.noor.app.ui.adhan.AdhanScreen(it, {}, {}, {}) }
            click("Alarms", "المنبهات"); shot("adhan_alarms")
            open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.FAJR, {}, {}) }; shot("prayer_fajr")
            open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.SHURUQ, {}, {}) }; shot("prayer_shuruq")
            open { com.noor.app.ui.adhan.VoiceScreen(it, Prayer.MAGHRIB, {}) }; shot("voice_maghrib")
            open { com.noor.app.ui.adhan.AlarmScreen(it, 0, {}) }; shot("alarm_new")
            open { com.noor.app.ui.adhan.AlarmScreen(it, fajrAlarm, {}) }; shot("alarm_edit")
            open { com.noor.app.ui.adhan.SoundLibraryScreen(it, {}) }; shot("library")
            open { com.noor.app.ui.adhan.MySoundsScreen(it, {}) }; shot("my_sounds")
            open { com.noor.app.ui.adhan.AlertHistoryScreen(it, {}) }; shot("history_empty")
            open { com.noor.app.ui.location.LocationScreen(it, {}) }; shot("location")
            open { com.noor.app.ui.onboarding.OnboardingScreen(it, {}, {}) }; shot("onboarding")
            open { com.noor.app.ui.qibla.QiblaScreen(it, {}) }; shot("qibla_no_compass")
            // A long place name, where the place is named (Settings' card, the location page, the Qibla page).
            runBlocking { repo.setManualPlace(KnownLocation(GeoPoint(25.0732, 55.2297), "Asia/Dubai", "Mohammed Bin Rashid Al Maktoum City", "AE", null, 0L,
                "مدينة محمد بن راشد آل مكتوم", placeContext = "Dubai", placeContextAr = "دبي")) }
            open { com.noor.app.ui.settings.SettingsScreen(it, {}, {}) }; shot("settings_long_place")
            open { com.noor.app.ui.location.LocationScreen(it, {}) }; shot("location_long_place")
            open { com.noor.app.ui.qibla.QiblaScreen(it, {}) }; shot("qibla_long_place")
            open { com.noor.app.ui.adhan.AdhanScreen(it, {}, {}, {}) }; shot("adhan_long_place")
            runBlocking { repo.setManualCity("dxb") }
            if (!ar) {
                // The tester's page (English only, testing only: to be removed before release).
                open { com.noor.app.testing.TestingScreen(it, com.noor.app.data.Scenery.AUTO, {}, {}, {}, {}) }; save("testing_$tag")
            }
        }
        arabic = false; fontScale = 1f; theme = ThemeMode.LIGHT
        open { com.noor.app.ui.settings.SettingsScreen(it, {}, {}) }; save("settings_light_en")
        open { com.noor.app.ui.adhan.AdhanScreen(it, {}, {}, {}) }; save("adhan_light_en")
        open { com.noor.app.ui.adhan.AlarmScreen(it, fajrAlarm, {}) }; save("alarm_edit_light_en")
        open { com.noor.app.ui.settings.SceneryScreen(it, {}) }; save("scenery_light_en")
        open { com.noor.app.ui.watch.WatchScreen(it, onBack = {}) }; save("watch_light_en")
        theme = ThemeMode.DARK
        assertTrue("Text in the wrong language:\n" + languageProblems.joinToString("\n"), languageProblems.isEmpty())
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("build/design/pages").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // The language check of ScreenshotTest, for the pages it does not see.
    private val languageProblems = mutableListOf<String>()
    private val arabicLetters = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
    private val latinLetters = Regex("[A-Za-z]")
    private val allowedInEnglish = listOf(Regex("منهاد"), Regex("[٠-٩]+"))
    private val allowedInArabic = listOf(
        Regex("(Galaxy|Pixel) Watch\\S*( Pro| Classic)?( \\([0-9A-F]{4}\\))?"), Regex("Wear OS"), Regex("Galaxy Wearable"), Regex("WGS-84"),
        Regex("\\b(MP3|M4A|AAC|WAV|OGG|Opus|FLAC)\\b"), Regex("CC0|CC[ \u00A0]BY(-SA)?[ \u00A0]\\d\\.\\d"),
        Regex("\\b[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(ae|sg|org|com|gov)\\b"), Regex("GeoNames"), Regex("timezone-boundary-builder|OpenStreetMap|ODbL"),
        Regex("English"), Regex("Leave the office for the mosque"), // the language picker names English in English; a name the user gave an alarm
    ) + Regex("\"author\":\\s*\"([^\"]+)\"").findAll(File("src/main/assets/sounds/library.json").readText()).map { Regex(Regex.escape(it.groupValues[1])) }.toList()

    private fun checkLanguage(name: String) {
        val texts = rule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { n ->
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                listOfNotNull(n.config.getOrNull(SemanticsProperties.EditableText)?.text)
        }.distinct()
        texts.forEach { t ->
            if (arabic) { if (latinLetters.containsMatchIn(allowedInArabic.fold(t) { a, r -> a.replace(r, "") })) languageProblems += "$name (Arabic): \"$t\"" }
            else if (arabicLetters.containsMatchIn(allowedInEnglish.fold(t) { a, r -> a.replace(r, "") })) languageProblems += "$name (English): \"$t\""
        }
    }
}
