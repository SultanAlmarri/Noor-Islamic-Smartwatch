package com.noor.app.design

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.noor.app.NoorApplication
import com.noor.app.data.Language
import com.noor.app.data.ThemeMode
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.widget.WidgetBackground
import com.noor.app.widget.WidgetKind
import com.noor.app.widget.WidgetOptions
import com.noor.app.widget.WidgetSettings
import com.noor.app.widget.Widgets
import kotlinx.coroutines.runBlocking
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
 * (0.23) A widget's settings page, whole, with its live preview at the top (the widget itself, built as the home screen
 * gets it), in English and Arabic, for a few kinds and options, into build/widgets/settings_*.png. Software renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1700dp-xxhdpi")
class WidgetSettingsDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var arabic by mutableStateOf(false)
    private var page by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var fresh by mutableIntStateOf(0)

    @Test fun settingsPages() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        runBlocking { repo.setManualCity("dxb"); repo.setOnboardingDone() }
        val at = RenderHarness.at(14, 10)
        rule.setContent {
            val base = LocalContext.current
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
            CompositionLocalProvider(
                LocalAmbientMotion provides false,
                LocalContext provides base.createConfigurationContext(config),
                LocalConfiguration provides config,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalActivityResultRegistryOwner provides rule.activity,
            ) {
                NoorTheme(ThemeMode.DARK) {
                    NoorBackground { key(fresh) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { page?.invoke() } } }
                }
            }
        }
        val built = java.util.concurrent.atomic.AtomicInteger(0)
        fun shot(name: String, previews: Int) {
            // The preview is built off the main thread (the sky picture takes a moment here): wait for it, then settle.
            val until = System.currentTimeMillis() + 30_000
            while (built.get() < previews && System.currentTimeMillis() < until) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50) }
            // The preview's picture is made on the main thread after it is built, on Compose's clock: let that clock run.
            for (i in 0 until 40) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.mainClock.advanceTimeBy(100); rule.waitForIdle(); Thread.sleep(25) }
            rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(File("build/widgets").apply { mkdirs() }, "settings_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val cases = listOf(
            Triple(WidgetKind.TODAY, WidgetKind.TODAY.defaults, 340 to 190),
            Triple(WidgetKind.NEXT, WidgetOptions(background = WidgetBackground.GLASS), 180 to 96),
            Triple(WidgetKind.RING, WidgetKind.RING.defaults, 170 to 170),
        )
        for (lang in listOf("en", "ar")) {
            arabic = lang == "ar"
            runBlocking { repo.setLanguage(if (arabic) Language.ARABIC else Language.ENGLISH) }
            for ((kind, o, size) in cases) {
                val before = built.get()
                page = { WidgetSettings(kind, o, size, {}, {}, preview = { _, opts -> Widgets.preview(app, kind, opts, size.first, size.second, at).also { built.incrementAndGet() } }) }
                fresh++
                shot("${kind.name.lowercase()}_$lang", before + 1)
            }
        }
    }
}
