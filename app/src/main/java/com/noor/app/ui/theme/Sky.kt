package com.noor.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.noor.prayer.sky.SkyPalette

/** The colours of the sky at one moment: top of the sky, middle, and the band at the horizon. */
@Immutable
data class SkyColors(
    val zenith: Color,
    val mid: Color,
    val horizon: Color,
    /** 0 in daylight, 1 in full night: how visible the stars and the moon are. */
    val night: Float,
)

/** Noor's sky on the phone: the real sky's colour for the Sun's elevation, and the colours drawn on it. */
object Sky {
    /** Text and marks drawn on the sky. White stays readable on the upper sky at every elevation (SkyPaletteTest). */
    val OnSky = Color(0xFFFFFFFF)
    val OnSkySecondary = Color(0xD9FFFFFF)
    val OnSkyMuted = Color(0x99FFFFFF)
    val SunCore = Color(0xFFFFEDC2)
    val SunGlow = Color(0xFFFFD58C)
    val Moon = Color(0xFFF3EEDF)
    /** Arabic display names on the sky. */
    val GoldOnSky = Color(0xFFF6D696)

    /** The shared palette (core `SkyPalette`, also used by the watch) as Compose colours. */
    fun colors(elevationDeg: Double, evening: Boolean): SkyColors {
        val p = SkyPalette.colors(elevationDeg, evening)
        return SkyColors(Color(p.zenith), Color(p.mid), Color(p.horizon), p.night)
    }

    /** Perceptual blend (OKLab). */
    fun mix(a: Color, b: Color, t: Float): Color = Color(SkyPalette.mix(a.toArgb(), b.toArgb(), t))

    /** WCAG contrast ratio. */
    fun contrast(a: Color, b: Color): Double = SkyPalette.contrast(a.toArgb(), b.toArgb())
}
