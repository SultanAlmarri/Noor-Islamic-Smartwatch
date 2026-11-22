package com.noor.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.noor.prayer.sky.SkyPalette

/**
 * 0.25: the sky of the moment behind the watch's home screen: the phone's own sky colours ([SkyPalette]) for the
 * Sun's height, from the zenith down to the light at the horizon, darkened where needed so the white text on it
 * always reads. Pure functions on colour ints, so a test can check every sky there is (`WatchSkyTest`).
 */
internal object WatchSky {
    /** The text on the sky ([Ink]). */
    const val INK = 0xFFF7F2E7.toInt()
    /** What the sky is held to (WCAG AA asks 4.5:1 for text). */
    const val MIN_CONTRAST = 5.0

    class Stops(val top: Int, val mid: Int, val low: Int, /** 0 in daylight … 1 in full night. */ val night: Float)

    fun stops(elevationDeg: Double, evening: Boolean): Stops {
        val c = SkyPalette.colors(elevationDeg, evening)
        return Stops(readable(c.zenith), readable(c.mid), readable(SkyPalette.glow(elevationDeg, evening)), c.night)
    }

    /** [argb], or the brightest darker shade of it (same hue and saturation) on which [INK] reads at [MIN_CONTRAST]:1. */
    fun readable(argb: Int): Int {
        if (SkyPalette.contrast(INK, argb) >= MIN_CONTRAST) return argb
        var lo = 0f; var hi = 1f
        repeat(14) {
            val k = (lo + hi) / 2f
            if (SkyPalette.contrast(INK, scaled(argb, k)) >= MIN_CONTRAST) lo = k else hi = k
        }
        return scaled(argb, lo)
    }

    fun scaled(argb: Int, k: Float): Int {
        fun ch(shift: Int) = (((argb shr shift) and 0xFF) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Where the stars are (fractions of the screen), their size and brightness: always the same sky. None stands
     * where the countdown is written (a star between the figures reads as a stray point), and none below the dunes.
     */
    val stars: List<FloatArray> = run {
        var seed = 0x2545F491L
        fun next(): Float { seed = seed xor (seed shl 13); seed = seed xor (seed ushr 7); seed = seed xor (seed shl 17); return ((seed ushr 11) and 0xFFFFF).toFloat() / 0xFFFFF }
        List(90) { floatArrayOf(next(), next() * 0.66f, 0.45f + next() * 0.8f, 0.35f + next() * 0.65f) }.filterNot { inText(it[0], it[1]) }
    }

    /** The part of the first screenful the next prayer is written on. */
    fun inText(x: Float, y: Float) = x in 0.13f..0.87f && y in 0.22f..0.72f
}

/**
 * The sky drawn full-bleed: the gradient, stars as night comes, and dunes along the bottom (nature only) that end
 * in the black the rest of the page is on.
 */
@Composable
internal fun SkyBackdrop(stops: WatchSky.Stops, modifier: Modifier = Modifier) {
    val back = remember { Path() }
    val front = remember { Path() }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val top = Color(stops.top); val mid = Color(stops.mid); val low = Color(stops.low)
        drawRect(Brush.verticalGradient(0f to top, 0.46f to mid, 0.80f to low, 1f to low, startY = 0f, endY = h))
        // The time is written straight on the sky in the ring's gap: the sky is a little darker there, fading in 44 dp.
        val shade = 44.dp.toPx()
        drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent, startY = 0f, endY = shade), size = Size(w, shade))
        if (stops.night > 0.02f) {
            val unit = h / 225f
            for (s in WatchSky.stars) drawCircle(Color.White.copy(alpha = (stops.night * s[3]).coerceIn(0f, 1f)), s[2] * unit, Offset(s[0] * w, s[1] * h))
        }
        // The far dune, in the sky's own colour gone dark; the near one almost black.
        back.rewind()
        back.moveTo(0f, h * 0.800f)
        back.cubicTo(w * 0.18f, h * 0.735f, w * 0.34f, h * 0.715f, w * 0.52f, h * 0.770f)
        back.cubicTo(w * 0.68f, h * 0.815f, w * 0.84f, h * 0.800f, w, h * 0.760f)
        back.lineTo(w, h); back.lineTo(0f, h); back.close()
        drawPath(back, Color(SkyPalette.mix(stops.low, 0xFF000000.toInt(), 0.62f)))
        front.rewind()
        front.moveTo(0f, h * 0.850f)
        front.cubicTo(w * 0.22f, h * 0.870f, w * 0.44f, h * 0.800f, w * 0.68f, h * 0.775f)
        front.cubicTo(w * 0.82f, h * 0.762f, w * 0.93f, h * 0.790f, w, h * 0.815f)
        front.lineTo(w, h); front.lineTo(0f, h); front.close()
        drawPath(front, Color(SkyPalette.mix(stops.low, 0xFF000000.toInt(), 0.82f)))
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = h * 0.80f, endY = h * 0.985f), Offset(0f, h * 0.80f), Size(w, h * 0.20f))
    }
}
