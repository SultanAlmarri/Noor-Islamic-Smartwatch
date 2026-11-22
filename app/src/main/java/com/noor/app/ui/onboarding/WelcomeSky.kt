package com.noor.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.noor.app.ui.theme.Sky
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The welcome's own dawn (0.25): the sky of Minhad's icon, the Earth's edge at first light, as a place one stands in.
 * It is one picture for the whole welcome; each page lets in more light ([lightFor]), and the way out is the sunrise.
 *
 * What is written on it stays readable by construction: the words live above [Band], in the part of the sky that
 * never grows brighter than [Look.low]; the band of first light under them holds no words; the buttons stand on the
 * Earth. `WelcomeDesignTest` measures it on the drawn pixels, at every stage.
 */
internal object Dawn {
    /** The band of first light over the Earth's edge. Nothing is written in it. */
    val Band = 64.dp
    /** The light of the last page, and of the Sun's first edge (the way out). */
    const val LAST = 3f
    const val SUNRISE = 4f
    /** The night the welcome comes out of (its first second). */
    const val BEFORE = -1f

    /** How much light each page has: 0 the cover (deep twilight) … 3 the Adhan's page (minutes before sunrise). */
    fun lightFor(step: Int): Float = step.coerceIn(0, 3).toFloat()

    /** The Earth below its edge: night's side, a shade of the sky it turns under. */
    val EarthTop = Color(0xFF14143A)
    val EarthLow = Color(0xFF05061A)

    @Immutable
    class Look(
        val zenith: Color, val mid: Color,
        /** The sky where the words end, just above the band of first light. */
        val low: Color,
        val warm: Color, val bright: Color,
        /** 0..1: the light gathering where the Sun will rise. */
        val glow: Float,
        val stars: Float,
    )

    private val keys = listOf(
        Look(Color(0xFF040716), Color(0xFF0A1136), Color(0xFF141C52), Color(0xFF2C2A70), Color(0xFF51407F), 0.20f, 1.00f),
        Look(Color(0xFF050920), Color(0xFF0D1746), Color(0xFF1C2668), Color(0xFF4B4089), Color(0xFFA06A8C), 0.36f, 0.85f),
        Look(Color(0xFF07102E), Color(0xFF122058), Color(0xFF28307E), Color(0xFF75548F), Color(0xFFDB8E70), 0.62f, 0.62f),
        Look(Color(0xFF0A163C), Color(0xFF182968), Color(0xFF33378A), Color(0xFFA26888), Color(0xFFF8C078), 0.86f, 0.38f),
        Look(Color(0xFF10224E), Color(0xFF243A7C), Color(0xFF4A4690), Color(0xFFD98C78), Color(0xFFFFDCA0), 1.00f, 0.12f),
    )

    fun look(light: Float): Look {
        if (light <= 0f) {
            // Before the cover's own light: the same sky, deeper in the night.
            val k = keys[0]; val d = (-light).coerceIn(0f, 1f)
            fun dim(c: Color) = Sky.mix(c, Color(0xFF02030A), d * 0.7f)
            return Look(dim(k.zenith), dim(k.mid), dim(k.low), dim(k.warm), dim(k.bright), k.glow * (1f - d), 1f)
        }
        val i = floor(light).toInt().coerceIn(0, keys.size - 2)
        val t = (light - i).coerceIn(0f, 1f)
        val a = keys[i]; val b = keys[i + 1]
        return Look(Sky.mix(a.zenith, b.zenith, t), Sky.mix(a.mid, b.mid, t), Sky.mix(a.low, b.low, t), Sky.mix(a.warm, b.warm, t), Sky.mix(a.bright, b.bright, t),
            a.glow + (b.glow - a.glow) * t, a.stars + (b.stars - a.stars) * t)
    }
}

private val BandSteps = floatArrayOf(0.14f, 0.28f, 0.42f, 0.56f, 0.70f, 0.82f, 0.92f, 1f)
private val GlowCore = Color(0xFFFFEFCF)
private val GlowWarm = Color(0xFFFAAA6C)
private val GlowRose = Color(0xFFE07C8C)
private val Rim = Color(0xFFFFE9C4)
private val Air = Color(0xFF8FA8FF)

/**
 * The sky. [light] is the stage of the dawn ([Dawn.lightFor]); [crest] the height (px from the top) of the Earth's
 * edge at its highest point, 0 until the page knows where its buttons are. Both are read while drawing, so the dawn
 * and the Earth move without recomposing anything. [twinkle] 0..1 turns endlessly when ambient motion is allowed; below 0 the stars are still.
 */
@Composable
internal fun DawnSky(light: () -> Float, crest: () -> Float, modifier: Modifier = Modifier, stars: Boolean = true, twinkle: () -> Float = { -1f }) {
    val field = remember {
        // (Neighbouring seeds give java.util.Random nearly the same first numbers: one generator, run on.)
        val r = java.util.Random(0x5DEECE66DL)
        List(96) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.25f + 0.75f * r.nextFloat() * r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val l = light()
        val look = Dawn.look(l)
        val band = Dawn.Band.toPx()
        val cy = crest().takeIf { it > band * 2f } ?: (h * 0.74f)
        val wordsEnd = cy - band

        // Down to where the words end the sky is night's own blue; under it the band of first light begins softly (no
        // line where it starts) and gathers toward the Earth's edge.
        val stops = ArrayList<Pair<Float, Color>>(11)
        stops += 0f to look.zenith
        stops += (wordsEnd * 0.5f) / cy to look.mid
        stops += wordsEnd / cy to look.low
        for (u in BandSteps) {
            val v = u * u * (0.4f + 0.6f * u)
            val c = if (v < 0.5f) Sky.mix(look.low, look.warm, v / 0.5f) else Sky.mix(look.warm, look.bright, (v - 0.5f) / 0.5f)
            stops += (wordsEnd + band * u) / cy to c
        }
        drawRect(Brush.verticalGradient(*stops.toTypedArray(), startY = 0f, endY = cy))

        // The stars, thinning toward the light.
        if (stars && look.stars > 0.01f) {
            val turn = twinkle()
            field.forEach { s ->
                val y = s[1] * wordsEnd
                val low = (y / wordsEnd).let { (1f - it * kotlin.math.sqrt(it)).coerceAtLeast(0f) }
                val wink = if (turn >= 0f) 0.72f + 0.28f * sin((turn + s[3]) * 2f * PI.toFloat()) else 1f
                drawCircle(Color.White, (0.45f + 0.9f * s[2]) * density, Offset(s[0] * w, y), alpha = (look.stars * s[2] * low * wink).coerceIn(0f, 1f))
            }
        }

        // The Earth is a very large circle; its edge crosses the screen as a shallow arc.
        val earthR = w * 1.7f
        val earth = Offset(w / 2f, cy + earthR)

        // First light gathers behind the edge, where the Sun will rise.
        val reach = minOf(w, 520.dp.toPx())
        val g = look.glow
        val sunrise = (l - Dawn.LAST).coerceIn(0f, 1f)
        // (Wide and low, as light lies along a horizon: a circle of light drawn twice as wide as it is high.)
        val gr = reach * (0.30f + 0.14f * g + 0.22f * sunrise)
        val gc = Offset(w / 2f, cy + band * 0.2f)
        scale(2.1f, 1f, gc) {
            drawCircle(Brush.radialGradient(
                0f to GlowCore.copy(alpha = 0.90f * g), 0.2f to GlowWarm.copy(alpha = 0.46f * g), 0.5f to GlowRose.copy(alpha = 0.14f * g), 1f to Color.Transparent,
                center = gc, radius = gr,
            ), gr, gc)
        }
        // The Sun's own edge, on the way out.
        if (sunrise > 0f) {
            val sr = 26.dp.toPx()
            val sc = Offset(w / 2f, cy + sr * (1.05f - 1.25f * sunrise))
            drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = 0.9f * sunrise), 0.35f to GlowCore.copy(alpha = 0.55f * sunrise), 1f to Color.Transparent, center = sc, radius = sr * 4f), sr * 4f, sc)
            drawCircle(Sky.SunCore, sr, sc, alpha = sunrise)
        }

        // The air along the edge: a thin blue line of atmosphere, then the lit rim, brightest where the light gathers.
        val air = 14.dp.toPx()
        drawCircle(Brush.radialGradient(
            0f to Color.Transparent, earthR / (earthR + air) to Air.copy(alpha = 0.10f + 0.20f * g), 1f to Color.Transparent,
            center = earth, radius = earthR + air,
        ), earthR + air, earth)
        val rim = 1.5.dp.toPx()
        drawCircle(Brush.horizontalGradient(
            0f to Rim.copy(alpha = 0f), 0.5f to Rim.copy(alpha = (0.25f + 0.75f * g).coerceAtMost(1f)), 1f to Rim.copy(alpha = 0f),
            startX = w / 2f - reach * 0.46f, endX = w / 2f + reach * 0.46f,
        ), earthR + rim, earth)

        drawCircle(Brush.verticalGradient(0f to Dawn.EarthTop, 1f to Dawn.EarthLow, startY = cy, endY = cy + 200.dp.toPx()), earthR, earth)
        // (Below the arc's ends, on a very tall screen, the Earth goes on.)
        val sag = earthR - sqrt(earthR * earthR - (w / 2f) * (w / 2f))
        if (cy + sag < h) drawRect(Brush.verticalGradient(0f to Dawn.EarthTop, 1f to Dawn.EarthLow, startY = cy, endY = cy + 200.dp.toPx()), Offset(0f, cy + sag), androidx.compose.ui.geometry.Size(w, h - cy - sag))
    }
}
