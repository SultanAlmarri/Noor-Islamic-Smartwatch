package com.noor.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Noor palette. Two deliberately designed themes, not an inversion of each other:
 * night is a deep ink with warm gold light; day is a warm paper with ink text and a darker, readable gold.
 * Surfaces are solid (no glass, no heavy gradients); hierarchy comes from spacing, type and one accent colour.
 */
@Immutable
data class NoorColors(
    val isDark: Boolean,
    /** Screen background. */
    val background: Color,
    /** Kept for components that still draw a two-stop background; both equal [background] in this design. */
    val backgroundTop: Color,
    val backgroundBottom: Color,
    /** Cards and grouped lists. */
    val surface: Color,
    /** Highlighted card, selected row. */
    val surfaceRaised: Color,
    /** Hairline separators and card edges. */
    val separator: Color,
    val outline: Color,
    /** Accent for icons, progress, selection. */
    val gold: Color,
    /** Accent used for text (meets 4.5:1 on the surface). */
    val goldText: Color,
    val goldSoft: Color,
    val goldDeep: Color,
    /** Content drawn on a gold fill. */
    val onGold: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val pattern: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

val NightColors = NoorColors(
    isDark = true,
    background = Color(0xFF080C14),
    backgroundTop = Color(0xFF080C14),
    backgroundBottom = Color(0xFF080C14),
    surface = Color(0xFF121826),
    surfaceRaised = Color(0xFF1A2232),
    separator = Color(0x1AFFFFFF),
    outline = Color(0x24FFFFFF),
    gold = Color(0xFFE3C27E),
    goldText = Color(0xFFE8CB8E),
    goldSoft = Color(0xFFF4E2B8),
    goldDeep = Color(0xFFB98F45),
    onGold = Color(0xFF1C1407),
    textPrimary = Color(0xFFF5F2EA),
    textSecondary = Color(0xFFA9B1C1),
    textMuted = Color(0xFF6F7889),
    pattern = Color(0x0DE3C27E),
    success = Color(0xFF6FD3A6),
    warning = Color(0xFFF2C14E),
    danger = Color(0xFFFF6B6B),
)

val DayColors = NoorColors(
    isDark = false,
    background = Color(0xFFF4F1EA),
    backgroundTop = Color(0xFFF4F1EA),
    backgroundBottom = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFBF6EA),
    separator = Color(0x14000000),
    outline = Color(0x1F000000),
    gold = Color(0xFFB4883A),
    goldText = Color(0xFF8A6524),
    goldSoft = Color(0xFF7C5A1F),
    goldDeep = Color(0xFF8A6424),
    onGold = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF17191F),
    textSecondary = Color(0xFF575E6B),
    textMuted = Color(0xFF8B909A),
    pattern = Color(0x0FB4883A),
    success = Color(0xFF1F8A62),
    warning = Color(0xFFB7791F),
    danger = Color(0xFFD64545),
)

val LocalNoorColors = staticCompositionLocalOf { NightColors }
