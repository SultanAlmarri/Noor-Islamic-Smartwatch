package com.noor.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Noor palette: night sky and warm gold, with a calm ivory day variant. */
@Immutable
data class NoorColors(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val gold: Color,
    val goldSoft: Color,
    val goldDeep: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val pattern: Color,
    val success: Color,
)

val NightColors = NoorColors(
    isDark = true,
    backgroundTop = Color(0xFF13223A),
    backgroundBottom = Color(0xFF060B15),
    surface = Color(0x1AFFFFFF),
    surfaceRaised = Color(0x26FFFFFF),
    outline = Color(0x33E3C27E),
    gold = Color(0xFFE3C27E),
    goldSoft = Color(0xFFF4E2B8),
    goldDeep = Color(0xFFB98F45),
    textPrimary = Color(0xFFF7F2E7),
    textSecondary = Color(0xFFB7C0D2),
    textMuted = Color(0xFF7D879B),
    pattern = Color(0x0FE3C27E),
    success = Color(0xFF7FD1B9),
)

val DayColors = NoorColors(
    isDark = false,
    backgroundTop = Color(0xFFFBF7EE),
    backgroundBottom = Color(0xFFEFE6D2),
    surface = Color(0xB3FFFFFF),
    surfaceRaised = Color(0xE6FFFFFF),
    outline = Color(0x4DA97C2F),
    gold = Color(0xFFA97C2F),
    goldSoft = Color(0xFF7C5A1F),
    goldDeep = Color(0xFF8A6424),
    textPrimary = Color(0xFF1B2433),
    textSecondary = Color(0xFF4F5B70),
    textMuted = Color(0xFF8590A3),
    pattern = Color(0x14A97C2F),
    success = Color(0xFF2E8B6E),
)

val LocalNoorColors = staticCompositionLocalOf { NightColors }
