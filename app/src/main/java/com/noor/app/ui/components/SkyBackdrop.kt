package com.noor.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.ui.theme.Sky
import com.noor.prayer.Prayer
import com.noor.prayer.astro.SunPath
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Everything the sky needs for one moment at one place. */
@Immutable
data class SkyScene(
    val path: SunPath,
    /** Today's events in order (Fajr … Isha); missing events (polar days) are simply absent. */
    val events: List<Pair<Prayer, Instant>>,
    val now: Instant,
    val next: Prayer?,
    /** Day of the Hijri month, for the moon's phase. */
    val hijriDay: Int,
    val southernHemisphere: Boolean,
) {
    val sunElevation: Double = path.elevationAt(now)
    /** After the highest point of the day the light turns warm. */
    val evening: Boolean = run {
        val noon = path.samples.maxByOrNull { it.second }?.first ?: 0.5
        path.fraction(now) > noon
    }
}

/** Positions on the sky: a smooth time axis that gives daylight most of the width, and an elevation scale. */
internal class SkyGeometry(scene: SkyScene, private val w: Float, val horizonY: Float, val arcTopY: Float, val groundY: Float, private val rtl: Boolean) {
    private val maxEl = max(scene.path.maxElevation, 12.0)
    private val minEl = minOf(scene.path.minElevation, -24.0)
    private val warp: (Double) -> Double = timeWarp(scene)

    fun x(fraction: Double): Float {
        val u = (-0.04 + 1.08 * warp(fraction.coerceIn(0.0, 1.0))).toFloat() * w
        return if (rtl) w - u else u
    }

    fun y(el: Double): Float = if (el >= 0) (horizonY - (el / maxEl) * (horizonY - arcTopY)).toFloat()
        else (horizonY + (-el / -minEl) * (groundY - horizonY)).toFloat()

    companion object {
        /** Monotone cubic (Fritsch–Carlson) through Fajr−30 min, sunrise, sunset and Isha+30 min. */
        fun timeWarp(scene: SkyScene): (Double) -> Double {
            val f = scene.events.associate { (p, t) -> p to scene.path.fraction(t) }
            val fajr = f[Prayer.FAJR]; val rise = f[Prayer.SHURUQ]; val set = f[Prayer.MAGHRIB]; val isha = f[Prayer.ISHA]
            if (rise == null || set == null) return { it }
            val half = 30.0 / (24 * 60)
            val xs = mutableListOf(0.0); val ys = mutableListOf(0.0)
            fun add(x: Double, y: Double) { if (x > xs.last() + 1e-4 && x < 1 - 1e-4 && y > ys.last()) { xs += x; ys += y } }
            fajr?.let { add(it - half, 0.10) }
            add(rise, 0.18); add(set, 0.82)
            isha?.let { add(it + half, 0.90) }
            xs += 1.0; ys += 1.0
            return monotoneCubic(xs.toDoubleArray(), ys.toDoubleArray())
        }

        fun monotoneCubic(x: DoubleArray, y: DoubleArray): (Double) -> Double {
            val n = x.size
            val d = DoubleArray(n - 1) { (y[it + 1] - y[it]) / (x[it + 1] - x[it]) }
            val m = DoubleArray(n) { i ->
                when (i) { 0 -> d[0]; n - 1 -> d[n - 2]; else -> if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2 }
            }
            for (i in 0 until n - 1) {
                if (d[i] == 0.0) { m[i] = 0.0; m[i + 1] = 0.0; continue }
                val a = m[i] / d[i]; val b = m[i + 1] / d[i]
                val s = a * a + b * b
                if (s > 9) { val t = 3 / kotlin.math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
            }
            return { v ->
                val i = (x.indexOfLast { it <= v }).coerceIn(0, n - 2)
                val h = x[i + 1] - x[i]; val t = ((v - x[i]) / h).coerceIn(0.0, 1.0)
                val t2 = t * t; val t3 = t2 * t
                (2 * t3 - 3 * t2 + 1) * y[i] + (t3 - 2 * t2 + t) * h * m[i] + (-2 * t3 + 3 * t2) * y[i + 1] + (t3 - t2) * h * m[i + 1]
            }
        }
    }
}

/**
 * The living sky behind the Today screen's header: the colour of the sky now, stars and the moon at night, and the day's
 * sun path with the prayers placed on it — the times are, after all, positions of the sun.
 *
 * Heights are fractions of the canvas: [arcTop] is where the noon sun sits, [horizon] the horizon line.
 */
@Composable
fun SkyBackdrop(
    scene: SkyScene,
    modifier: Modifier = Modifier,
    horizon: Float = 0.83f,
    arcTop: Float = 0.62f,
    /** Where the deepest point of the night part of the path is drawn. */
    ground: Float = 0.92f,
    description: String,
) {
    val sky = Sky.colors(scene.sunElevation, scene.evening)
    val zenith by animateColorAsState(sky.zenith, tween(1200), label = "zenith")
    val mid by animateColorAsState(sky.mid, tween(1200), label = "mid")
    val horizonColor by animateColorAsState(sky.horizon, tween(1200), label = "horizon")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ambient = LocalAmbientMotion.current

    // The day so far draws itself once, from midnight to now, when the screen appears.
    val reveal = remember { Animatable(if (ambient) 0f else 1f) }
    LaunchedEffect(Unit) { if (ambient) reveal.animateTo(1f, tween(NoorMotion.Entrance + 300, easing = NoorMotion.Ease)) }
    val twinkle = if (ambient && sky.night > 0.05f) {
        val t = rememberInfiniteTransition(label = "twinkle")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    } else 0.25f
    val stars = remember { starField(90) }

    Canvas(modifier.semantics { contentDescription = description }) {
        val w = size.width; val h = size.height
        val hy = h * horizon
        val g = SkyGeometry(scene, w, hy, h * arcTop, h * ground, rtl)
        val groundColor = Sky.mix(zenith, Color.Black, 0.55f)

        drawRect(Brush.verticalGradient(0f to zenith, horizon * 0.58f to mid, horizon to horizonColor, 1f to groundColor))
        // Warm haze around where the sun is, near the horizon (sunrise/sunset glow).
        val sunFrac = scene.path.fraction(scene.now)
        val sunX = g.x(sunFrac)
        val haze = (1f - (abs(scene.sunElevation.toFloat()) / 14f)).coerceIn(0f, 1f)
        if (haze > 0f) drawRect(
            Brush.radialGradient(listOf(Sky.SunGlow.copy(alpha = 0.32f * haze), Color.Transparent), center = Offset(sunX, hy), radius = w * 0.75f),
            topLeft = Offset(0f, 0f), size = size,
        )

        if (sky.night > 0f) {
            stars.forEach { s ->
                if (s.y > 0.82f * horizon) return@forEach
                val tw = 0.55f + 0.45f * sin(2 * PI * (twinkle + s.phase)).toFloat()
                drawCircle(Color.White.copy(alpha = sky.night * s.brightness * tw), radius = s.r * density, center = Offset(s.x * w, s.y * h))
            }
            moon(scene.hijriDay, Offset(if (rtl) w * 0.17f else w * 0.83f, h * 0.30f), 11.dp.toPx(), sky.night, scene.southernHemisphere)
        }

        // Horizon: a hairline that fades at both ends.
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent)),
            topLeft = Offset(0f, hy - 0.5.dp.toPx()), size = androidx.compose.ui.geometry.Size(w, 1.dp.toPx()))

        val path = Path()
        scene.path.samples.forEachIndexed { i, (f, el) -> if (i == 0) path.moveTo(g.x(f), g.y(el)) else path.lineTo(g.x(f), g.y(el)) }
        val stroke = 1.6.dp.toPx()
        val past = Rect(if (rtl) lerp(w, sunX, reveal.value) else 0f, 0f, if (rtl) w else lerp(0f, sunX, reveal.value), h)
        val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
        // Future: dotted. Above the horizon brighter than below.
        clipRect(0f, 0f, w, hy) { drawPath(path, Color.White.copy(alpha = 0.42f), style = Stroke(stroke * 0.8f, cap = StrokeCap.Round, pathEffect = dash)) }
        clipRect(0f, hy, w, h) { drawPath(path, Color.White.copy(alpha = 0.20f), style = Stroke(stroke * 0.8f, cap = StrokeCap.Round, pathEffect = dash)) }
        // The day so far: solid, with a soft glow above the horizon.
        clipRect(past.left, 0f, past.right, hy) {
            drawPath(path, Sky.SunGlow.copy(alpha = 0.18f), style = Stroke(stroke * 4f, cap = StrokeCap.Round))
            drawPath(path, Sky.SunCore.copy(alpha = 0.92f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        clipRect(past.left, hy, past.right, h) { drawPath(path, Color.White.copy(alpha = 0.42f), style = Stroke(stroke, cap = StrokeCap.Round)) }

        // Prayer moments on the path.
        scene.events.forEach { (p, t) ->
            val f = scene.path.fraction(t)
            val pt = Offset(g.x(f), g.y(scene.path.elevationAt(t)))
            val passed = !t.isAfter(scene.now) && reveal.value > 0.98f
            when {
                p == scene.next -> {
                    drawCircle(Sky.GoldOnSky.copy(alpha = 0.22f), 9.dp.toPx(), pt)
                    drawCircle(Sky.GoldOnSky, 5.5.dp.toPx(), pt, style = Stroke(1.6.dp.toPx()))
                }
                passed -> drawCircle(Color.White.copy(alpha = 0.92f), 3.dp.toPx(), pt)
                else -> {
                    drawCircle(zenith.copy(alpha = 0.6f), 3.4.dp.toPx(), pt)
                    drawCircle(Color.White.copy(alpha = 0.7f), 3.4.dp.toPx(), pt, style = Stroke(1.2.dp.toPx()))
                }
            }
        }

        // The sun, travelling to where it is now.
        val shown = lerp(0f, sunFrac.toFloat(), reveal.value).toDouble()
        val sun = Offset(g.x(shown), g.y(scene.path.samples.let { s -> interpolate(s, shown) }))
        val up = scene.sunElevation > -1.0
        // In full night the sun has no place on screen; the moon and stars take over.
        if (scene.sunElevation < -18.0) return@Canvas
        drawCircle(Brush.radialGradient(listOf(Sky.SunGlow.copy(alpha = if (up) 0.55f else 0.18f), Color.Transparent), center = sun, radius = if (up) 34.dp.toPx() else 18.dp.toPx()),
            if (up) 34.dp.toPx() else 18.dp.toPx(), sun)
        drawCircle(if (up) Sky.SunCore else Sky.SunCore.copy(alpha = 0.45f), 6.5.dp.toPx(), sun)
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun interpolate(samples: List<Pair<Double, Double>>, f: Double): Double {
    val i = samples.indexOfLast { it.first <= f }.coerceIn(0, samples.size - 2)
    val (f0, e0) = samples[i]; val (f1, e1) = samples[i + 1]
    return if (f1 == f0) e0 else e0 + (e1 - e0) * ((f - f0) / (f1 - f0))
}

private class Star(val x: Float, val y: Float, val r: Float, val brightness: Float, val phase: Float)

/** A fixed, hand-tuned-looking field: deterministic so the sky does not reshuffle between frames or screens. */
private fun starField(n: Int): List<Star> {
    val rnd = java.util.Random(1448)
    return List(n) {
        val big = rnd.nextFloat() < 0.12f
        Star(rnd.nextFloat(), rnd.nextFloat() * 0.8f, if (big) 1.3f else 0.55f + rnd.nextFloat() * 0.5f,
            if (big) 0.95f else 0.35f + rnd.nextFloat() * 0.45f, rnd.nextFloat())
    }
}

/**
 * The moon as it looks on this day of the Hijri month: a thin crescent at the start, full near the 14th, waning after.
 * Lit on the right while waxing (left in the southern hemisphere).
 */
private fun DrawScope.moon(hijriDay: Int, c: Offset, r: Float, night: Float, south: Boolean) {
    val age = ((hijriDay - 1).coerceIn(0, 29) + 1.0) / 29.53          // 0..1 through the lunation
    val phase = 2 * PI * age
    val lit = ((1 - cos(phase)) / 2).toFloat()                          // illuminated fraction
    if (lit < 0.02f) return
    val waxing = age < 0.5
    val litOnRight = waxing != south
    drawCircle(Brush.radialGradient(listOf(Sky.Moon.copy(alpha = 0.22f * night * lit), Color.Transparent), center = c, radius = r * 4), r * 4, c)
    drawCircle(Sky.Moon.copy(alpha = 0.10f * night), r, c)             // earthshine
    // Lit region: a half disc plus or minus a half ellipse whose width follows the phase.
    val k = (1 - 2 * lit)                                               // +1 new … -1 full
    val p = Path()
    val side = if (litOnRight) 1f else -1f
    val steps = 48
    for (i in 0..steps) {                                               // outer limb (half circle)
        val a = -PI / 2 + PI * i / steps
        val pt = Offset(c.x + side * r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    for (i in steps downTo 0) {                                         // terminator (half ellipse)
        val a = -PI / 2 + PI * i / steps
        p.lineTo(c.x + side * k * r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
    }
    p.close()
    drawPath(p, Sky.Moon.copy(alpha = 0.95f * night))
}
