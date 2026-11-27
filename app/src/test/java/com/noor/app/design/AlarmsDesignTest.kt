package com.noor.app.design

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import com.noor.prayer.alerts.AlarmSound
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.PrayerAlarm
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
 * (0.26) The alarms in Samsung's Clock's manner, on a phone of the owner's width (360 dp): the list empty, with five
 * alarms on and off (named and not, every day and some days), all off, and while choosing some to delete; the editor
 * for a new alarm and for ones set, whole. In English, Arabic, the light theme and large text (1.3). Software renders
 * (build/design/alarms), for looking at; not photos of a phone. [AlarmsPhoneDesignTest]: the same at a phone's height.
 */
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-hdpi")
class AlarmsDesignTest : AlarmsRenders(phoneHeight = false)

/**
 * (0.26) The alarms' list and editor at the height of the owner's phone (360 × 780 dp), as they are first seen: the
 * editor's Cancel and Save at its foot, over what scrolls under them (build/design/alarms/phone_*).
 */
@Config(sdk = [34], qualifiers = "w360dp-h780dp-hdpi")
class AlarmsPhoneDesignTest : AlarmsRenders(phoneHeight = true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class AlarmsRenders(private val phoneHeight: Boolean) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var theme by mutableStateOf(ThemeMode.DARK)
    private var arabic by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)
    private var page by mutableStateOf<(@Composable (PaddingValues) -> Unit)?>(null)
    private var fresh by mutableIntStateOf(0)
    /** The page's foot: 112 dp with the tab bar; the editor, shown without it, the navigation bar's 24 dp and 16 dp more. */
    private var foot by mutableIntStateOf(112)

    @Test fun alarms() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        runBlocking { repo.setManualCity("dxb"); repo.setOnboardingDone() }
        com.noor.app.ui.today.SkyCompute.synchronous = true
        rule.setContent {
            val base = LocalContext.current
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); this.fontScale = this@AlarmsRenders.fontScale; setLayoutDirection(locale) }
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
                        key(fresh) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                page?.invoke(PaddingValues(top = 28.dp, bottom = foot.dp))
                            }
                        }
                    }
                }
            }
        }
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[AdhanViewModel::class.java]
        fun settle() {
            for (i in 0 until 20) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle(); Thread.sleep(10) }
            rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        }
        // Android's answers as on a phone where nothing is in the way (this Robolectric cannot say that the alarm's
        // screen may show over the lock screen), so the renders show the alarms alone.
        @Suppress("UNCHECKED_CAST")
        val readiness = AdhanViewModel::class.java.getDeclaredField("_readiness").apply { isAccessible = true }.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<com.noor.app.ui.adhan.Readiness>
        val clear = com.noor.app.ui.adhan.Readiness(notificationsAllowed = true, canRequestNotifications = false, exactAlarms = true, alarmVolume = 0.7f, fullScreenAllowed = true)
        fun answer() { repeat(2) { Thread.sleep(250); settle(); readiness.value = clear; settle() } }
        fun open(p: @Composable (PaddingValues) -> Unit) { page = p; fresh++; settle(); answer() }
        fun click(en: String, ar: String) { rule.onAllNodes(hasText(if (arabic) ar else en)).onFirst().performClick(); settle() }
        var saved = 0
        val shots = mutableListOf<String>()
        fun save(name: String) {
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(File("build/design/alarms").apply { mkdirs() }, "${if (phoneHeight) "phone_" else ""}$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            saved++; shots += name
        }
        val adhan: @Composable (PaddingValues) -> Unit = { com.noor.app.ui.adhan.AdhanScreen(it, {}, {}, {}) }
        fun each(langs: List<String>, shots: (String) -> Unit) {
            for (tag in langs) {
                arabic = tag == "ar"; fontScale = if (tag == "en_130") 1.3f else 1f; theme = if (tag == "en_light") ThemeMode.LIGHT else ThemeMode.DARK
                shots(tag)
            }
            arabic = false; fontScale = 1f; theme = ThemeMode.DARK
        }
        val all = listOf("en", "ar", "en_130", "en_light")
        fun alarmsTab() { open(adhan); click("Alarms", "المنبهات") }

        // 1. No alarms yet.
        runBlocking { repo.updateAlerts { it.copy(enabled = true, device = AlertDevice.PHONE, alarms = emptyList()) } }
        if (!phoneHeight) each(listOf("en", "ar")) { tag -> alarmsTab(); save("list_empty_$tag") }

        // 2. Five alarms, on and off, named and not, every day and some days.
        runBlocking {
            repo.updateAlerts { it
                .withAlarm(PrayerAlarm(0, Prayer.FAJR, 25, after = false, label = "Wake up for Fajr"))
                .withAlarm(PrayerAlarm(0, Prayer.DHUHR, 10, after = true, days = 0b0011111, snoozeMinutes = 5, label = "Leave the office for the mosque"))
                .withAlarm(PrayerAlarm(0, Prayer.ASR, 90, after = true, enabled = false, days = 0b1100000))
                .withAlarm(PrayerAlarm(0, Prayer.MAGHRIB, 5, after = false, enabled = false, sound = AlarmSound.NOTIFICATION))
                .withAlarm(PrayerAlarm(0, Prayer.ISHA, 30, after = true, days = 0b0010000, vibrate = false))
            }
        }
        val alarms = runBlocking { repo.current().alerts.alarms }
        val fajr = alarms.first { it.prayer == Prayer.FAJR }.id
        val dhuhr = alarms.first { it.prayer == Prayer.DHUHR }.id
        val asr = alarms.first { it.prayer == Prayer.ASR }.id
        each(if (phoneHeight) listOf("en") else all) { tag -> alarmsTab(); save("list_$tag") }
        // Choosing alarms to delete: a long press on the first, then a touch on another.
        each(if (phoneHeight) listOf("en") else listOf("en", "ar")) { tag ->
            alarmsTab()
            // (The names are the user's own words, in English in both languages here; the first alarm's name is also at
            // the top of the page, so its card is found by what it is.)
            rule.onAllNodes(hasText(if (arabic) "25 دقيقة قبل الفجر" else "25 min before Fajr")).onFirst().performTouchInput { longClick() }; settle()
            rule.onAllNodes(hasText("Leave the office for the mosque")).onFirst().performClick(); settle(); save("list_selecting_$tag")
        }

        // 3. The editor: a new alarm and ones set.
        foot = 40
        each(all) { tag ->
            open { com.noor.app.ui.adhan.AlarmScreen(it, 0, {}) }; save("edit_new_$tag")
            open { com.noor.app.ui.adhan.AlarmScreen(it, dhuhr, {}) }; save("edit_dhuhr_$tag")
            if (tag == "en" && !phoneHeight) {
                open { com.noor.app.ui.adhan.AlarmScreen(it, fajr, {}) }; save("edit_fajr_$tag")
                open { com.noor.app.ui.adhan.AlarmScreen(it, asr, {}) }; save("edit_asr_$tag")
            }
        }

        foot = 112
        // 4. Every alarm off.
        runBlocking { repo.updateAlerts { s -> s.copy(alarms = s.alarms.map { it.copy(enabled = false) }) } }
        if (!phoneHeight) each(listOf("en", "ar")) { tag -> alarmsTab(); save("list_all_off_$tag") }

        println("${javaClass.simpleName}: ${shots.size} pictures: ${shots.joinToString()}")
        assertTrue("no page was rendered", saved > 0)
    }
}
