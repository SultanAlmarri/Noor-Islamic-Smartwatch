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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.noor.prayer.WatchConfig
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.localizedFor

internal val Gold = Color(0xFFE3C27E)
internal val GoldSoft = Color(0xFFF4E2B8)
internal val GoldDeep = Color(0xFFB98F45)
/** Text on gold (the next prayer's row, the main button). */
internal val OnGold = Color(0xFF241A08)
internal val Ink = Color(0xFFF7F2E7)
internal val Mist = Color(0xFFB7C0D2)
/** 0.25: quiet labels and secondary lines (7:1 on black, 5.8:1 on a platter). */
internal val Quiet = Color(0xFF9A9AA2)
/** 0.25: the platter every row and card sits on, and its raised form (the prayer of the moment). */
internal val Platter = Color(0xFF1C1C1F)
internal val PlatterHigh = Color(0xFF2E2E33)
internal val Success = Color(0xFF34D17B)

/**
 * 0.25: the colours of the round signs beside settings rows and on the home screen's tools, one meaning each
 * (to make every sign gold instead, point these at [Gold]).
 */
internal object Tint {
    val Gold = Color(0xFFE3C27E)
    val Blue = Color(0xFF2E8BFF)
    val Indigo = Color(0xFF6E6BF2)
    val Green = Color(0xFF34C96B)
    val Orange = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF5A4D)
    val Teal = Color(0xFF35BFD6)
    val Gray = Color(0xFF5A5A62)
}

internal val Kufi = FontFamily(Font(R.font.reem_kufi))
/** IBM Plex Sans Arabic, the phone app's typeface, so both read as one product. */
internal val Plex = FontFamily(
    Font(R.font.plex_arabic_light, FontWeight.Light),
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_semibold, FontWeight.Bold),
)
private val NoorWearTypography = androidx.wear.compose.material3.Typography(defaultFontFamily = Plex)

/**
 * 0.25: the watch's own type scale, in Plex with real SemiBold: confident titles, large tabular numerals, and
 * quiet tracked captions. Every screen takes its styles from here so the app reads as one family.
 */
internal object Type {
    /**
     * Plex Arabic's own line is 1.6 times its size (room for Arabic's marks). Every style says its line height, which
     * sets the distance between the lines of a text; above the first line and below the last the font keeps its own
     * room (as rendered in the tests), which is why large figures go through [Numeral].
     */
    private val base = TextStyle(
        fontFamily = Plex,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val title = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp)
    val row = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val body = base.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp)
    val detail = base.copy(fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp)
    val note = base.copy(fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 15.5.sp)
    /** Small tracked capitals for labels (Arabic has no capitals: the same style without the tracking). */
    val caption = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.9.sp)
    /** Large numerals: SemiBold, every digit the same width so a running count does not jitter. */
    fun numeral(size: TextUnit, weight: FontWeight = FontWeight.SemiBold) = base.copy(
        fontWeight = weight, fontSize = size, lineHeight = size * 1.12f, fontFeatureSettings = "tnum", letterSpacing = (-0.02).em,
    )
    /** Arabic display names (the prayer, the month) are in Reem Kufi. */
    fun kufi(size: TextUnit) = base.copy(fontFamily = Kufi, fontSize = size, lineHeight = size * 1.35f)
}

/** A label in small capitals: upper case in English, as written in Arabic. */
internal fun WearText.caps(s: String): String = if (arabic) s else s.uppercase()
/** [Type.caption] for the language: Arabic letters join, so they are never tracked apart. */
internal fun WearText.captionStyle(): TextStyle = if (arabic) Type.caption.copy(letterSpacing = 0.sp, fontSize = 12.sp) else Type.caption

internal val NoorWearColors = ColorScheme(
    primary = Gold,
    primaryDim = GoldDeep,
    primaryContainer = Color(0xFF3A2E17),
    onPrimary = OnGold,
    onPrimaryContainer = GoldSoft,
    secondary = Mist,
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Platter,
    surfaceContainerHigh = PlatterHigh,
    onSurface = Ink,
    onSurfaceVariant = Quiet,
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
    @Composable fun height(): Dp = LocalConfiguration.current.screenHeightDp.dp
    /** 0 on the smallest round watches, 1 on the largest. */
    @Composable fun scale(): Float = ((LocalConfiguration.current.screenWidthDp - 192) / 36f).coerceIn(0f, 1f)
    @Composable fun sp(small: Float, large: Float): TextUnit = androidx.compose.ui.unit.TextUnit(small + (large - small) * scale(), androidx.compose.ui.unit.TextUnitType.Sp)
    @Composable fun dp(small: Float, large: Float): Dp = (small + (large - small) * scale()).dp
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
