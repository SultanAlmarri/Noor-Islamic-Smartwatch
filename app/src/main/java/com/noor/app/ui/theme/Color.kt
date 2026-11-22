package com.noor.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Noor palette. Two deliberately designed themes, not an inversion of each other:
 * night is a deep ink with warm gold light; day is a warm paper with ink text and a darker, readable gold.
 * Since 0.15 the pages stand on the sky of the moment ([Backdrop]) and their lists and cards are glass over it
 * ([glass], [glassRaised], with a lit edge [glassEdge]); [surface] stays solid for what rises over everything
 * (sheets, dialogs). Every text colour meets WCAG AA (4.5:1) on what it is used on, in both themes and at every
 * height of the Sun (ThemeContrastTest).
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
    /** Lists and cards (0.15): glass over the page's sky, and a lighter glass for what is chosen or raised. */
    val glass: Color,
    val glassRaised: Color,
    /** The lit upper edge of a pane of glass, fading to [glassEdgeLow] at its foot. */
    val glassEdge: Color,
    val glassEdgeLow: Color,
    /**
     * The floating tab bar: the surface's colour, all but solid (0.25, after the second review: at 78% the page's
     * figures and names were read through the bar and ran into the tabs' own names; Apple's bars blur what is behind
     * them, and a blur cannot be checked without a phone, so the bar hides what is behind it and the page fades out
     * before it reaches the bar: `TabBarFade`).
     */
    val chrome: Color,
    /** Hairline separators and card edges. */
    val separator: Color,
    val outline: Color,
    /** Accent for icons, progress, selection. */
    val gold: Color,
    /**
     * (0.25) The accent as a fill: switches that are on, the main button, the chosen day or chip. By night it is
     * [gold]; by day a true gold (until 0.24 the light theme filled these with [gold]'s darker tone, chosen to be
     * read as a line on white, and they looked mustard brown). What stands on it is [onGold].
     */
    val goldFill: Color,
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
    glass = Color(0x17FFFFFF),
    glassRaised = Color(0x21FFFFFF),
    glassEdge = Color(0x40FFFFFF),
    glassEdgeLow = Color(0x0DFFFFFF),
    chrome = Color(0xF7101623),
    separator = Color(0x1AFFFFFF),
    outline = Color(0x24FFFFFF),
    gold = Color(0xFFE3C27E),
    goldFill = Color(0xFFE3C27E),
    goldText = Color(0xFFE8CB8E),
    goldSoft = Color(0xFFF4E2B8),
    goldDeep = Color(0xFFB98F45),
    onGold = Color(0xFF1C1407),
    textPrimary = Color(0xFFF5F2EA),
    textSecondary = Color(0xFFC6CCD8),
    textMuted = Color(0xFFB0B8C6),
    pattern = Color(0x0DE3C27E),
    success = Color(0xFF6FD3A6),
    warning = Color(0xFFF2C14E),
    danger = Color(0xFFFF9B9B),
)

val DayColors = NoorColors(
    isDark = false,
    background = Color(0xFFF4F1EA),
    backgroundTop = Color(0xFFF4F1EA),
    backgroundBottom = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFBF6EA),
    glass = Color(0xB3FFFFFF),
    glassRaised = Color(0xE6FFFFFF),
    glassEdge = Color(0xF2FFFFFF),
    glassEdgeLow = Color(0x14000000),
    chrome = Color(0xF7FFFFFF),
    separator = Color(0x14000000),
    outline = Color(0x1F000000),
    gold = Color(0xFFA8792C),
    goldFill = Color(0xFFDDAE4E),
    goldText = Color(0xFF7A5A1E),
    goldSoft = Color(0xFF7C5A1F),
    goldDeep = Color(0xFF8A6424),
    onGold = Color(0xFF1A1206),
    textPrimary = Color(0xFF17191F),
    textSecondary = Color(0xFF575E6B),
    textMuted = Color(0xFF5B616C),
    pattern = Color(0x0FB4883A),
    success = Color(0xFF17724F),
    warning = Color(0xFF935F12),
    danger = Color(0xFFB83333),
)

val LocalNoorColors = staticCompositionLocalOf { NightColors }
