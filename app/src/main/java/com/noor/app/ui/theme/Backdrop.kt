package com.noor.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.max

/**
 * The sky behind every page (0.15). The owner, of 0.14's interface: "I see absolutely nothing changed with the user
 * interface. I want you to totally revolutionize the user interface." Until 0.14 every page but the home screen stood
 * on one flat colour (ink at night, paper by day). Since 0.15 the whole app lives in the sky of the moment at the
 * user's place, as Apple's Weather app lives in its weather: deep blue pages at midday, amber and violet at sunset,
 * indigo with a few stars at night, the colours of [Sky.colors] for the Sun's height there. Lists and cards are glass
 * over it ([NoorColors.glass]).
 *
 * In the dark theme the sky is deepened so that the text on the glass stays readable; in the light theme it is
 * washed pale, as a bright morning seen through a window. `ThemeContrastTest` checks every text colour on the glass
 * at every height of the Sun, in both themes.
 */
@Immutable
data class Backdrop(
    /** The top of the page (the bars are drawn in it). */
    val top: Color,
    val middle: Color,
    /** The foot of the page: the horizon's colour. */
    val bottom: Color,
    /** The glow at the foot of the page round sunrise and sunset (transparent otherwise). */
    val glow: Color,
    /** 0 by day .. 1 in full night: how many stars there are. */
    val night: Float,
) {
    /** The colour the glass is laid on, at a fraction of the page's height (0 top, 1 foot). */
    fun at(f: Float): Color {
        val sky = if (f < 0.55f) Sky.mix(top, middle, f / 0.55f) else Sky.mix(middle, bottom, (f - 0.55f) / 0.45f)
        // (The glow, as it lies down the middle of the page: nothing above its middle, all of it at the foot.)
        val g = ((f - 0.45f) / 0.55f).coerceIn(0f, 1f)
        return glow.copy(alpha = glow.alpha * g * g).compositeOver(sky)
    }

    companion object {
        private val Ink = Color(0xFF060A13)
        private val Paper = Color(0xFFFFFFFF)
        private val Cream = Color(0xFFFFF7EA)

        /** The backdrop for the Sun at [elevationDeg] ([evening]: after noon), in the dark or the light theme. */
        fun of(elevationDeg: Double, evening: Boolean, dark: Boolean): Backdrop {
            val s = Sky.colors(elevationDeg, evening)
            val twilight = (1.0 - kotlin.math.abs(elevationDeg + 2.0) / 10.0).coerceIn(0.0, 1.0).toFloat()
            val day = ((elevationDeg - 4.0) / 16.0).coerceIn(0.0, 1.0).toFloat()
            return if (dark) Backdrop(
                // (By day the page is a clearer blue: the deep sky of noon, not the night's.)
                top = Sky.mix(s.zenith, Ink, 0.62f - 0.12f * day),
                middle = Sky.mix(s.mid, Ink, 0.72f - 0.04f * day),
                bottom = Sky.mix(s.horizon, Ink, 0.84f),
                glow = s.horizon.copy(alpha = 0.10f * twilight),
                night = s.night,
            ) else Backdrop(
                top = Sky.mix(s.zenith, Paper, 0.80f),
                middle = Sky.mix(s.mid, Paper, 0.84f),
                bottom = Sky.mix(s.horizon, Cream, 0.70f),
                glow = s.horizon.copy(alpha = 0.18f * twilight),
                night = 0f,
            )
        }

        /** Before the place is known (the welcome page): the blue hour. */
        fun unknown(dark: Boolean) = of(-5.0, true, dark)
    }
}

val LocalBackdrop = staticCompositionLocalOf { Backdrop.unknown(true) }

/** The page's sky: the backdrop's colours from top to foot, the horizon's glow, and the stars at night. */
@Composable
fun SkyBackdrop(modifier: Modifier = Modifier, backdrop: Backdrop = LocalBackdrop.current, content: @Composable BoxScope.() -> Unit = {}) {
    val brush = remember(backdrop) { Brush.verticalGradient(0f to backdrop.top, 0.55f to backdrop.middle, 1f to backdrop.bottom) }
    Box(modifier.fillMaxSize().background(brush)) {
        Canvas(Modifier.fillMaxSize()) {
            if (backdrop.glow.alpha > 0.01f) drawRect(
                Brush.radialGradient(listOf(backdrop.glow, Color.Transparent), center = Offset(size.width * 0.5f, size.height * 1.02f), radius = max(size.width, size.height) * 0.62f),
            )
            if (backdrop.night > 0.05f) for ((x, y, b) in Stars) {
                val a = backdrop.night * b * (1f - y * 0.9f)
                drawCircle(Color.White.copy(alpha = a), radius = (0.6f + b) * density, center = Offset(x * size.width, y * size.height * 0.62f))
            }
        }
        content()
    }
}

/** A fixed scatter of faint stars (x, y as fractions; brightness), the same on every page so they never jump. */
private val Stars: List<Triple<Float, Float, Float>> = run {
    var seed = 0x2545F491L
    fun next(): Float { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) and 0xFFFFFF).toFloat() / 0xFFFFFF }
    List(70) { Triple(next(), next(), 0.18f + 0.55f * next() * next()) }
}

/** What the glass looks like over the backdrop at [f] of the page's height (for tests and for opaque copies). */
fun NoorColors.glassOver(b: Backdrop, f: Float): Color = glass.compositeOver(b.at(f))
