package com.noor.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.noor.app.R

/** IBM Plex Sans Arabic carries both Latin and Arabic with one consistent voice. */
val Plex = FontFamily(
    Font(R.font.plex_arabic_light, FontWeight.Light),
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

/** Reem Kufi for Arabic display names. */
val Kufi = FontFamily(Font(R.font.reem_kufi, FontWeight.Normal))

val NoorTypography = Typography(
    displayLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 46.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 34.sp),
    headlineLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.4.sp),
    bodyLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)
