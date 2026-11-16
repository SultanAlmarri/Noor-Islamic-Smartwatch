package com.noor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.noor.app.data.ThemeMode

object NoorTheme {
    val colors: NoorColors
        @Composable get() = LocalNoorColors.current
}

@Composable
/**
 * [daylight]: whether the sun is up at the user's place now (for [ThemeMode.SUN]); null when unknown (no location yet).
 * [sun]: the Sun's height there now (degrees) and whether it is after noon, for the pages' sky ([Backdrop]); null
 * before the place is known.
 */
fun NoorTheme(mode: ThemeMode, daylight: Boolean? = null, sun: Pair<Double, Boolean>? = null, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SUN -> daylight?.not() ?: isSystemInDarkTheme()
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val c = if (dark) NightColors else DayColors
    val scheme = if (dark) darkColorScheme(
        primary = c.gold, onPrimary = c.onGold, secondary = c.goldSoft,
        background = c.background, surface = c.surface, surfaceVariant = c.surfaceRaised,
        onBackground = c.textPrimary, onSurface = c.textPrimary, onSurfaceVariant = c.textSecondary,
        outline = c.outline, outlineVariant = c.separator,
        surfaceContainerLowest = c.background, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceRaised,
    ) else lightColorScheme(
        primary = c.gold, onPrimary = c.onGold, secondary = c.goldDeep,
        background = c.background, surface = c.surface, surfaceVariant = c.surfaceRaised,
        onBackground = c.textPrimary, onSurface = c.textPrimary, onSurfaceVariant = c.textSecondary,
        outline = c.outline, outlineVariant = c.separator,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceRaised,
    )
    val backdrop = androidx.compose.runtime.remember(dark, sun) { if (sun == null) Backdrop.unknown(dark) else Backdrop.of(sun.first, sun.second, dark) }
    CompositionLocalProvider(LocalNoorColors provides c, LocalBackdrop provides backdrop) {
        MaterialTheme(colorScheme = scheme, typography = NoorTypography, content = content)
    }
}
