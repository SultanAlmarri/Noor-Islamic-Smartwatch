package com.noor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
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
        primary = c.gold, onPrimary = Color(0xFF1A1206), secondary = c.goldSoft,
        background = c.backgroundBottom, surface = Color(0xFF111B2D), surfaceVariant = Color(0xFF1A2740),
        onBackground = c.textPrimary, onSurface = c.textPrimary, onSurfaceVariant = c.textSecondary,
        outline = c.outline, surfaceContainerHigh = Color(0xFF16233A), surfaceContainer = Color(0xFF131F33),
    ) else lightColorScheme(
        primary = c.gold, onPrimary = Color.White, secondary = c.goldDeep,
        background = c.backgroundTop, surface = Color(0xFFFFFCF5), surfaceVariant = Color(0xFFF3ECDC),
        onBackground = c.textPrimary, onSurface = c.textPrimary, onSurfaceVariant = c.textSecondary,
        outline = c.outline, surfaceContainerHigh = Color(0xFFF7F0E1), surfaceContainer = Color(0xFFFAF4E8),
    )
    CompositionLocalProvider(LocalNoorColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = NoorTypography, content = content)
    }
}
