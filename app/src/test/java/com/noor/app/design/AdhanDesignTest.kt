package com.noor.app.design

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.alerts.AdhanService
import com.noor.alerts.AlertLog
import com.noor.alerts.AlertOutcome
import com.noor.alerts.AlertRecord
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.data.Numerals
import com.noor.app.data.ThemeMode
import com.noor.app.data.TimeFormat
import com.noor.app.ui.adhan.AdhanViewModel
import com.noor.app.ui.adhan.FixList
import com.noor.app.ui.adhan.Issue
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.theme.LocalBackdrop
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
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
import java.time.ZoneId
import java.util.Locale

/**
 * (0.25) The Adhan and alarms area, each page whole on a phone of the owner's width (360 dp), in the three states a
 * person meets it in: the alerts off (as everyone starts), on with something in the way (four things, then one), and on
 * with nothing in the way; in English and Arabic, dark and light, and with large text (1.3). Also the list of things to
 * fix as its sheet shows it, and the ringing screen at a phone's height for an alarm and for the Adhan.
 * Software renders (build/design/adhan), for looking at; not photos of a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-hdpi")
class AdhanDesignTest {
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
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); this.fontScale = this@AdhanDesignTest.fontScale; setLayoutDirection(locale) }
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
        /** What Android is taken to have answered (null: Robolectric's own answers, those of a new install on Android 14). */
        var answers: com.noor.app.ui.adhan.Readiness? = null
        val readiness = AdhanViewModel::class.java.getDeclaredField("_readiness").apply { isAccessible = true }.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<*>
        fun answer() {
            val a = answers ?: return
            // (Twice, a moment apart: a page that opens asks the system again, off the main thread, and may be answered late.)
            repeat(2) { Thread.sleep(250); settle(); @Suppress("UNCHECKED_CAST") (readiness as kotlinx.coroutines.flow.MutableStateFlow<com.noor.app.ui.adhan.Readiness>).value = a; settle() }
        }
        fun open(p: @Composable (PaddingValues) -> Unit) { page = p; fresh++; settle(); answer() }
        fun click(en: String, ar: String) { rule.onAllNodes(hasText(if (arabic) ar else en)).onFirst().performClick(); settle() }
        var saved = 0
        fun save(name: String) {
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(File("build/design/adhan").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            saved++
        }
        /**
         * What Android answered when asked whether an alert can sound, put straight into the page's state (this
         * Robolectric has no way to say that the Adhan's screen may show over the lock screen).
         */
        fun allow(notifications: Boolean, lockScreen: Boolean, exact: Boolean, volume: Float) {
            answers = com.noor.app.ui.adhan.Readiness(notificationsAllowed = notifications, canRequestNotifications = !notifications, exactAlarms = exact,
                alarmVolume = volume, fullScreenAllowed = lockScreen)
            vm.refresh(); settle(); answer()
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

        // 1. Off, as everyone starts: no alerts, no alarms.
        runBlocking { repo.updateAlerts { it.copy(enabled = false, device = AlertDevice.PHONE) } }
        allow(notifications = false, lockScreen = false, exact = false, volume = 0f)
        each(all) { tag ->
            open(adhan); save("off_adhan_$tag")
            if (tag == "en" || tag == "ar") {
                click("Alarms", "المنبهات"); save("off_alarms_$tag")
                open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.FAJR, {}, {}) }; save("off_prayer_fajr_$tag")
            }
        }

        // 2. On, with four things in the way (a new install on Android 14), and two alarms.
        runBlocking {
            repo.updateAlerts { it.copy(enabled = true, device = AlertDevice.PHONE)
                .withAlarm(PrayerAlarm(0, Prayer.DHUHR, 10, after = true, days = 0b0011111, snoozeMinutes = 5, label = "Leave the office for the mosque"))
                .withAlarm(PrayerAlarm(0, Prayer.FAJR, 25, after = false)) }
        }
        val fajrAlarm = runBlocking { repo.current().alerts.alarms.first { it.prayer == Prayer.FAJR }.id }
        allow(notifications = false, lockScreen = false, exact = false, volume = 0f)
        each(all) { tag ->
            open(adhan); save("blocked_adhan_$tag")
            click("Alarms", "المنبهات"); save("blocked_alarms_$tag")
        }
        // The things to fix, as the sheet lists them (a sheet is a window of its own, which these renders do not see).
        each(listOf("en", "ar", "en_light")) { tag ->
            open { pad ->
                Box(Modifier.fillMaxSize().padding(top = 400.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(NoorTheme.colors.glassRaised.compositeOver(LocalBackdrop.current.at(0.3f))).padding(top = 22.dp, bottom = pad.calculateBottomPadding())) {
                        val f = Formatter(arabic, Numerals.WESTERN, TimeFormat.H12)
                        FixList(listOf(
                            Issue(stringResource(R.string.adhan_warn_notifications), stringResource(R.string.adhan_action_notifications), {}, blocking = true),
                            Issue(stringResource(R.string.adhan_warn_volume), stringResource(R.string.adhan_action_volume), {}, Icons.AutoMirrored.Rounded.VolumeUp, blocking = true),
                            Issue(stringResource(R.string.adhan_warn_fullscreen), stringResource(R.string.adhan_action_allow), {}),
                            Issue(stringResource(R.string.adhan_warn_exact), stringResource(R.string.adhan_action_exact), {}),
                        ), pluralStringResource(R.plurals.adhan_fix_count, 4, f.number(4)))
                    }
                }
            }
            save("fix_list_$tag")
        }

        // 3. One thing in the way: the alarm volume at zero. Then one that only delays: exact alarms.
        allow(notifications = true, lockScreen = true, exact = true, volume = 0f)
        each(listOf("en", "ar")) { tag -> open(adhan); save("one_adhan_$tag"); click("Alarms", "المنبهات"); save("one_alarms_$tag") }
        allow(notifications = true, lockScreen = true, exact = false, volume = 0.7f)
        each(listOf("en")) { tag -> open(adhan); save("late_adhan_$tag") }

        // 4. On and nothing in the way: every page.
        run {
            val day = java.time.ZonedDateTime.now(ZoneId.systemDefault()).withHour(12).withMinute(0).withSecond(0).withNano(0)
            fun rec(i: Long, p: Prayer, h: Int, m: Int, o: AlertOutcome, played: Long = 0, delay: Long = 2_000, test: Boolean = false, stopped: Boolean = false, exact: Boolean = true) {
                val due = day.withHour(h).withMinute(m).toInstant().toEpochMilli()
                AlertLog.add(app, AlertRecord(i, p, test, due, due + delay, o, "adhan.makkah", played, stopped, exact, 80, 1))
            }
            AlertLog.clear(app)
            rec(1, Prayer.FAJR, 5, 9, AlertOutcome.BACKUP_SOUND, delay = 180_000, exact = false)
            rec(2, Prayer.DHUHR, 12, 5, AlertOutcome.PLAYED, played = 176_000)
            rec(3, Prayer.ASR, 15, 14, AlertOutcome.PLAYED, played = 41_000, stopped = true)
            rec(4, Prayer.MAGHRIB, 17, 34, AlertOutcome.DND_BLOCKS_ALARMS)
            rec(5, Prayer.ISHA, 18, 50, AlertOutcome.PLAYED_FALLBACK, played = 32_000, test = true)
        }
        allow(notifications = true, lockScreen = true, exact = true, volume = 0.7f)
        each(all) { tag ->
            open(adhan); save("on_adhan_$tag")
            click("Alarms", "المنبهات"); save("on_alarms_$tag")
            open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.FAJR, {}, {}) }; save("on_prayer_fajr_$tag")
            open { com.noor.app.ui.adhan.AlarmScreen(it, fajrAlarm, {}) }; save("on_alarm_edit_$tag")
            open { com.noor.app.ui.adhan.VoiceScreen(it, null, {}) }; save("on_voice_all_$tag")
            if (tag == "en" || tag == "ar") {
                open(adhan); click("Devices and silent mode", "الأجهزة والوضع الصامت"); save("on_options_$tag")
                open { com.noor.app.ui.adhan.PrayerAlertScreen(it, Prayer.SHURUQ, {}, {}) }; save("on_prayer_shuruq_off_$tag")
                open { com.noor.app.ui.adhan.AlarmScreen(it, 0, {}) }; save("on_alarm_new_$tag")
                click("Sound", "الصوت"); save("on_alarm_sound_$tag")
                open { com.noor.app.ui.adhan.AlertHistoryScreen(it, {}) }; save("on_history_$tag")
                open { com.noor.app.ui.adhan.VoiceScreen(it, Prayer.FAJR, {}) }; save("on_voice_fajr_$tag")
            }
            if (tag != "ar") { open { com.noor.app.ui.settings.SceneryScreen(it, {}) }; save("on_scenery_$tag") }
            // The ringing screen, at a phone's height: one of the user's alarms, and the Adhan.
            if (tag != "en_light") {
                val f = Formatter(arabic, Numerals.WESTERN, TimeFormat.H12)
                for (alarm in listOf(true, false)) {
                    open {
                        Box(Modifier.fillMaxWidth().height(780.dp)) {
                            com.noor.app.adhan.AdhanScreen(
                                if (alarm) AdhanService.Playing(if (arabic) "25 دقيقة قبل الفجر" else "25 minutes before Fajr", if (arabic) "الفجر 5:12 ص · دبي" else "Fajr 5:12 AM · Dubai", 0L, 2,
                                    if (arabic) "غفوة 10 د" else "Snooze 10 min")
                                else AdhanService.Playing(if (arabic) "المغرب" else "Maghrib", if (arabic) "5:33 م · دبي" else "5:33 PM · Dubai", 0L),
                                {}, {}, {}, clock = f.time(if (alarm) 4 * 60 + 47 else 17 * 60 + 33))
                        }
                    }
                    save("ring_${if (alarm) "alarm" else "adhan"}_$tag")
                }
            }
        }
        assertTrue("no page was rendered", saved > 0)
    }
}
