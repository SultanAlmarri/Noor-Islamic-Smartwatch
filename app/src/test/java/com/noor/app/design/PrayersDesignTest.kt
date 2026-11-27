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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.NoorApplication
import com.noor.app.data.ThemeMode
import com.noor.app.ui.adhan.AdhanViewModel
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
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
 * (0.26) The prayers' part of the Adhan tab as redesigned: the tab with the prayers as the home screen draws them, what
 * every prayer plays, a prayer's page, the sound picker for every prayer, for Fajr and for sunrise, and the devices
 * page; with the prayers alike and with them differing ("Mixed"); in English, Arabic, light, and with large text (1.3),
 * on a 360 dp phone. Software renders (build/design/adhan026), for looking at; not photos of a phone. The test asserts
 * only that pages were written, and what the pages say where the design depends on it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1500dp-hdpi")
class PrayersDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var theme by mutableStateOf(ThemeMode.DARK)
    private var arabic by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)
    private var page by mutableStateOf<(@Composable (PaddingValues) -> Unit)?>(null)
    private var fresh by mutableIntStateOf(0)

    @Test fun pages() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        runBlocking { repo.setManualCity("dxb"); repo.setOnboardingDone() }
        com.noor.app.ui.today.SkyCompute.synchronous = true
        rule.setContent {
            val base = LocalContext.current
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); this.fontScale = this@PrayersDesignTest.fontScale; setLayoutDirection(locale) }
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
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[AdhanViewModel::class.java]
        fun settle() {
            for (i in 0 until 20) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle(); Thread.sleep(10) }
            rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        }
        // (Android's answers as on a phone where everything is allowed: no "Fix" row in these pictures.)
        val readiness = AdhanViewModel::class.java.getDeclaredField("_readiness").apply { isAccessible = true }.get(vm)
        fun allowAll() {
            repeat(2) {
                Thread.sleep(250); settle()
                @Suppress("UNCHECKED_CAST") (readiness as kotlinx.coroutines.flow.MutableStateFlow<com.noor.app.ui.adhan.Readiness>).value =
                    com.noor.app.ui.adhan.Readiness(notificationsAllowed = true, exactAlarms = true, alarmVolume = 0.7f, fullScreenAllowed = true)
                settle()
            }
        }
        fun open(p: @Composable (PaddingValues) -> Unit) { page = p; fresh++; settle(); allowAll() }
        fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        fun click(text: String) { rule.onAllNodes(hasText(text)).onFirst().performClick(); settle() }
        var saved = 0
        fun save(name: String) {
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(File("build/design/adhan026").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            saved++
        }
        fun each(langs: List<String>, shots: (String) -> Unit) {
            for (tag in langs) {
                arabic = tag == "ar"; fontScale = if (tag == "en_130") 1.3f else 1f; theme = if (tag == "en_light") ThemeMode.LIGHT else ThemeMode.DARK
                shots(tag)
            }
            arabic = false; fontScale = 1f; theme = ThemeMode.DARK
        }
        val tab: @Composable (PaddingValues) -> Unit = { com.noor.app.ui.adhan.AdhanScreen(it, {}, {}, {}) }
        val all = listOf("en", "ar", "en_130", "en_light")

        // 1. Off, as everyone starts.
        runBlocking { repo.updateAlerts { it.copy(enabled = false, device = AlertDevice.PHONE) } }
        each(listOf("en")) { tag -> open(tab); save("tab_off_$tag") }

        // 2. On, every prayer alike (the defaults: Makkah, Fajr on Dubai's Fajr Adhan, sunrise off).
        runBlocking { repo.updateAlerts { it.copy(enabled = true) } }
        each(all) { tag ->
            open(tab); save("tab_on_$tag")
            open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.FAJR, {}, {}) }; save("prayer_fajr_$tag")
            open { com.noor.app.ui.adhan.VoiceScreen(it, null, {}) }; save("sound_all_$tag")
        }
        open(tab)
        assertTrue("the six prayers say what they play", shown("Makkah") && shown("Dubai"))
        assertTrue("every prayer's sound, at once", shown("Every prayer".uppercase()) || shown("Every prayer"))
        assertTrue("full volume from the start, by default", shown("Full volume from the start"))
        each(listOf("en", "ar")) { tag ->
            open { com.noor.app.ui.adhan.VoiceScreen(it, Prayer.FAJR, {}) }; save("sound_fajr_$tag")
            open { com.noor.app.ui.adhan.VoiceScreen(it, Prayer.DHUHR, {}) }; save("sound_dhuhr_$tag")
            open(tab); click(if (arabic) "الأجهزة والوضع الصامت" else "Devices and silent mode"); save("devices_$tag")
        }
        open { com.noor.app.ui.adhan.VoiceScreen(it, Prayer.FAJR, {}) }
        assertTrue("a voice with a Fajr recording can be heard in it", shown("Fajr version"))
        assertTrue("a voice without one says so", shown("No Fajr version"))

        // 3. The prayers differ: Asr softer and starting softly, Maghrib the opening only, Isha a notification, sunrise
        // birds on weekdays.
        runBlocking {
            repo.updateAlerts { s ->
                s.with(Prayer.ASR, s.alert(Prayer.ASR).copy(volumePercent = 45, gradualSeconds = 10, vibrate = false))
                    .with(Prayer.MAGHRIB, s.alert(Prayer.MAGHRIB).copy(mode = AlertMode.ADHAN_SHORT))
                    .with(Prayer.ISHA, s.alert(Prayer.ISHA).copy(mode = AlertMode.NOTIFICATION))
                    .with(Prayer.SHURUQ, s.alert(Prayer.SHURUQ).copy(enabled = true, mode = AlertMode.SOUND, soundId = vm.library.sounds.firstOrNull()?.id, days = 0b0011111))
            }
        }
        each(listOf("en", "ar", "en_light")) { tag ->
            open(tab); save("tab_mixed_$tag")
            open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.ASR, {}, {}) }; save("prayer_asr_soft_$tag")
            open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.SHURUQ, {}, {}) }; save("prayer_shuruq_$tag")
            open { com.noor.app.ui.adhan.VoiceScreen(it, Prayer.SHURUQ, {}) }; save("sound_shuruq_$tag")
        }
        open(tab)
        assertTrue("prayers that differ say so", shown("Mixed") && shown("On for some prayers"))
        open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.ISHA, {}, {}) }; save("prayer_isha_notification_en")
        assertTrue("no page was rendered", saved > 0)
    }
}
