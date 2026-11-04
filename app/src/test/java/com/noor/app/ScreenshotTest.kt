package com.noor.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.data.ThemeMode
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.prayers.PrayersScreen
import com.noor.app.ui.settings.AccuracyScreen
import com.noor.app.ui.settings.SettingsScreen
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.watch.WatchScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Renders the real screens with Robolectric's native graphics and saves PNGs to build/screenshots,
 * so every CI build shows what the app looks like. One test method: DataStore is a per-process singleton.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private enum class Screen { PRAYERS, SETTINGS, WATCH, ACCURACY }

    @Test fun renderScreens() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        runBlocking {
            app.container.settings.setManualCity("dxb")
            app.container.settings.setOnboardingDone()
        }
        var screen by mutableStateOf(Screen.PRAYERS)
        var theme by mutableStateOf(ThemeMode.DARK)
        var arabic by mutableStateOf(false)
        rule.setContent {
            val base = LocalContext.current
            val config = Configuration(base.resources.configuration).apply {
                setLocale(if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH)
                setLayoutDirection(if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH)
            }
            val ctx = base.createConfigurationContext(config)
            CompositionLocalProvider(
                LocalContext provides ctx,
                LocalConfiguration provides config,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NoorTheme(theme) {
                    NoorBackground {
                        val pad = PaddingValues(top = 28.dp, bottom = 16.dp)
                        when (screen) {
                            Screen.PRAYERS -> PrayersScreen(pad, {}, {})
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
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            val dir = File("build/screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shot("01_prayers_night_en", "Maghrib")
        screen = Screen.SETTINGS; shot("02_settings_night_en", "Numerals")
        screen = Screen.WATCH; shot("03_watch_night_en", "Galaxy")
        screen = Screen.ACCURACY; shot("04_accuracy_night_en", "IACAD")
        screen = Screen.PRAYERS; arabic = true; shot("05_prayers_night_ar", "المغرب")
        theme = ThemeMode.LIGHT; arabic = false; shot("06_prayers_day_en", "Maghrib")
    }
}
