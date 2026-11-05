package com.noor.wear.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.noor.prayer.WatchConfig
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.localizedFor

internal val Gold = Color(0xFFE3C27E)
internal val GoldSoft = Color(0xFFF4E2B8)
internal val GoldDeep = Color(0xFFB98F45)
internal val Ink = Color(0xFFF7F2E7)
internal val Mist = Color(0xFFB7C0D2)
internal val Night = Color(0xFF16233A)
internal val NightDeep = Color(0xFF0D1626)
internal val Success = Color(0xFF7FD3A8)
internal val Kufi = FontFamily(Font(R.font.reem_kufi))
/** IBM Plex Sans Arabic, the phone app's typeface, so both read as one product. */
internal val Plex = FontFamily(
    Font(R.font.plex_arabic_light, FontWeight.Light),
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_medium, FontWeight.SemiBold),
)
private val NoorWearTypography = androidx.wear.compose.material3.Typography(defaultFontFamily = Plex)

internal val NoorWearColors = ColorScheme(
    primary = Gold,
    primaryDim = GoldDeep,
    primaryContainer = Color(0xFF3A2E17),
    onPrimary = Color(0xFF241A08),
    onPrimaryContainer = GoldSoft,
    secondary = Mist,
    surfaceContainerLow = NightDeep,
    surfaceContainer = Night,
    surfaceContainerHigh = Color(0xFF213150),
    onSurface = Ink,
    onSurfaceVariant = Mist,
    outline = Color(0x66E3C27E),
    outlineVariant = Color(0x33E3C27E),
    background = Color.Black,
    onBackground = Ink,
)

/**
 * Noor's look on the wrist, in the language chosen on the phone (or the watch's own). Every screen goes through here
 * so Arabic is right-to-left everywhere and the fonts match the phone.
 */
@Composable
fun NoorWearTheme(config: WatchConfig?, content: @Composable (WearText) -> Unit) {
    val t = remember(config?.language, config?.arabicDigits, config?.use24h) { WearText(config) }
    val base = LocalContext.current
    val ctx = remember(base, config?.language) { base.localizedFor(config) }
    // Keep the activity's result registry reachable after LocalContext is swapped for the localized one.
    val registry = LocalActivityResultRegistryOwner.current
    MaterialTheme(colorScheme = NoorWearColors, typography = NoorWearTypography) {
        CompositionLocalProvider(
            *listOfNotNull(registry?.let { LocalActivityResultRegistryOwner provides it }).toTypedArray(),
            LocalContext provides ctx,
            LocalConfiguration provides ctx.resources.configuration,
            LocalLayoutDirection provides if (t.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) { content(t) }
    }
}

/** Sizes that follow the screen: Galaxy Watch 40 mm (~192 dp) to Pixel Watch 45 mm (~228 dp). */
internal object Screen {
    @Composable fun width(): Dp = LocalConfiguration.current.screenWidthDp.dp
    /** 0 on the smallest round watches, 1 on the largest. */
    @Composable fun scale(): Float = ((LocalConfiguration.current.screenWidthDp - 192) / 36f).coerceIn(0f, 1f)
    @Composable fun sp(small: Float, large: Float): TextUnit = androidx.compose.ui.unit.TextUnit(small + (large - small) * scale(), androidx.compose.ui.unit.TextUnitType.Sp)
}

/**
 * Haptics with a clear grammar: a light tick for a count, a firm click at a milestone, a short double pulse when a
 * phrase is done and a longer one when a round is complete or the Qibla is found. Uses the watch's own effects, so it
 * follows the strength the user set for the watch.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = runCatching {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    }.getOrNull()?.takeIf { it.hasVibrator() }

    private fun play(e: VibrationEffect) { runCatching { vibrator?.vibrate(e) } }

    fun tick() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    fun click() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    fun heavy() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    fun double() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    fun success() = play(VibrationEffect.createWaveform(longArrayOf(0, 60, 90, 60, 90, 180), intArrayOf(0, 200, 0, 200, 0, 255), -1))
}
