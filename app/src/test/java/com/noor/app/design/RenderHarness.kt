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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.noor.app.data.AppSettings
import com.noor.app.data.LocationMode
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ThemeMode
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.theme.NoorTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Renders any screen inside the real app frame (status-bar inset, floating tab bar) with a chosen theme, language,
 * font scale and moment, and saves it under build/design. Software renders, not device photos.
 */
class RenderHarness(private val rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>) {
    var theme by mutableStateOf(ThemeMode.DARK)
    var arabic by mutableStateOf(false)
    var fontScale by mutableStateOf(1f)
    var clock by mutableStateOf(DEFAULT_NOW)
    var tabRoute by mutableStateOf<String?>(null)
    var settings by mutableStateOf(DUBAI)
    var screen by mutableStateOf<(@Composable (PaddingValues) -> Unit)?>(null)

    fun start() {
        rule.setContent {
            val base = LocalContext.current
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); this.fontScale = this@RenderHarness.fontScale; setLayoutDirection(locale) }
            val ctx = base.createConfigurationContext(config)
            CompositionLocalProvider(
                LocalAmbientMotion provides false,
                LocalContext provides ctx,
                LocalConfiguration provides config,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalActivityResultRegistryOwner provides rule.activity,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                NoorTheme(theme) {
                    NoorBackground {
                        val tab = tabRoute
                        val pad = PaddingValues(top = 28.dp, bottom = if (tab != null) 112.dp else 16.dp)
                        screen?.invoke(pad)
                        if (tab != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            com.noor.app.NoorTabBar(tab) {}
                        }
                    }
                }
            }
        }
    }

    fun shot(name: String, waitFor: String? = null) {
        if (waitFor != null) rule.waitUntil(20_000) { rule.onAllNodesWithText(waitFor, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/design").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        /** Wednesday 4 November 2026, 15:29:21 in Dubai: Asr is in progress, Maghrib is next. */
        val DEFAULT_NOW: Instant = ZonedDateTime.of(2026, 11, 4, 15, 29, 21, 0, ZoneId.of("Asia/Dubai")).toInstant()
        val DUBAI = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = "dxb")
        fun at(h: Int, m: Int): Instant = ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, ZoneId.of("Asia/Dubai")).toInstant()
    }
}
