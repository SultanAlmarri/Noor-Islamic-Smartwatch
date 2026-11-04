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
fun NoorTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
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
    CompositionLocalProvider(LocalNoorColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = NoorTypography, content = content)
    }
}
