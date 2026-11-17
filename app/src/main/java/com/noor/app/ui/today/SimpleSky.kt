package com.noor.app.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.noor.app.ui.theme.Sky
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The low graphics (0.19; Settings → Home scenery → Graphics → Low): the simplest home sky, for any phone. The sky's
 * colours of the moment in one smooth gradient, the Sun as a soft disc, the moon in its phase, a fixed set of stars at
 * night, and the land of the scenery (all six: dunes, forest, arctic, sea, the meadow's hills, canyon) as two to four
 * flat silhouettes, far to near, coloured by the light of the moment, the nearest flowing into the page. No clouds,
 * nothing that moves by itself.
 *
 * What it costs: every shape (the land, the snow, the stars, the moon's phase, the glitter on the sea, the Sun's glow) is
 * built once for a day, a size and a scenery; a frame fills one gradient rectangle, a few paths and circles, in colours
 * worked out for the moment. A frame is drawn only when the moment shown changes (once a minute, or while the Sun is
 * dragged) or the size does: no clock, no animation, no layer, no blur, no shader of its own (plain Canvas, Android 8+).
 *
 * The Sun's path and the prayers are drawn over it by [LivingSky], as on every level (its Sun is not: the Sun is drawn
 * here, behind the land). The sky lags behind when the page scrolls, as the 0.9 sky does; the land and the Sun stay with
 * the page (the path does too). In Arabic the scene is mirrored, as [SkyGeometry] mirrors the Sun's path.
 */
@Composable
internal fun SimpleSkyLayer(
    scene: SkyScene,
    layout: SceneLayout,
    display: Double,
    page: Color,
    rtl: Boolean,
    parallax: () -> Float,
    modifier: Modifier = Modifier,
) {
    // The scene changes once a minute (its "now"); what is built here depends only on the day, the place and the
    // scenery, so it is built once a day, not once a minute.
    val day = remember(scene.path, scene.events, scene.terrain, scene.landSeed, scene.hijriDay, scene.southernHemisphere) { scene }
    val light = remember(day, display) { lightAt(day.elevationAt(display), day.eveningAt(display)) }
    // Read while drawing, never in composition: a new moment draws again without building anything.
    val displayNow = rememberUpdatedState(display)
    val lightNow = rememberUpdatedState(light)
    val pageNow = rememberUpdatedState(page)
    val parallaxNow = rememberUpdatedState(parallax)

    // The sky: farthest away, so it lags most when the page scrolls.
    val sky = remember(day, layout, rtl) {
        Modifier.fillMaxSize().graphicsLayer { translationY = parallaxNow.value() * 0.5f }.drawWithCache {
            val w = size.width; val h = size.height
            val hy = h * layout.horizon
            val g = geometry(day, layout, w, h, rtl)
            val stars = SimpleStars(w, hy, density)
            val moon = SimpleMoon(day.hijriDay, day.southernHemisphere, rtl, 10.dp.toPx())
            val gradient = BrushMemo()
            onDrawBehind {
                val l = lightNow.value
                val d = displayNow.value
                drawRect(gradient.vertical(l.zenith, l.mid, l.horizon, layout.horizon, h))
                if (l.night > 0.01f) stars.draw(this, l.night)
                // The moon on the Sun's own path, as far behind it as it is old (as the 0.9 sky places it).
                val age = (((day.hijriDay - 1).coerceIn(0, 29) + 0.5) / 29.53)
                val moonF = ((d - age) % 1.0 + 1.0) % 1.0
                val moonEl = day.elevationAt(moonF)
                if (moonEl > 2.0) {
                    val visibility = (0.35f + 0.65f * l.night).coerceAtMost(1f) * ss(2f, 8f, moonEl.toFloat())
                    moon.draw(this, Offset(g.x(moonF), g.y(moonEl, moonF)), visibility, l.night)
                }
            }
        }
    }

    // The Sun and the land, with the page: the Sun is drawn first, so that it sets behind the land.
    val land = remember(day, layout, rtl) {
        Modifier.fillMaxSize().drawWithCache {
            val w = size.width; val h = size.height
            val hy = h * layout.horizon
            val g = geometry(day, layout, w, h, rtl)
            val shapes = SimpleLand.build(day.terrain, day.landSeed, day, g, w, hy, h, density, rtl)
            val sun = SimpleSun(density)
            val brushes = BrushMemo()
            val fade = BrushMemo()
            onDrawBehind {
                val l = lightNow.value
                val d = displayNow.value
                val pg = pageNow.value
                val sunEl = day.elevationAt(d)
                val up = ss(-2.5f, 0.5f, sunEl.toFloat())
                val sunAt = Offset(g.x(d), g.y(sunEl, d))
                if (up > 0f) sun.draw(this, sunAt, l, up)
                // The moon's light on the water at night.
                val age = (((day.hijriDay - 1).coerceIn(0, 29) + 0.5) / 29.53)
                val moonF = ((d - age) % 1.0 + 1.0) % 1.0
                val moonEl = day.elevationAt(moonF)
                val moonOn = if (moonEl > 2.0) l.night * ss(2f, 8f, moonEl.toFloat()) * moonLit(day.hijriDay) else 0f
                shapes.draw(this, l, pg, brushes, fade, sunAt.x, up, g.x(moonF), moonOn)
            }
        }
    }
    Box(modifier.then(sky))
    Box(modifier.then(land))
}

private fun geometry(scene: SkyScene, layout: SceneLayout, w: Float, h: Float, rtl: Boolean) =
    SkyGeometry(scene, w, h * layout.horizon, h * layout.arcTop, h * layout.ground, rtl, SunLift.rise * SunLift.FOCAL * w, SunLift.set * SunLift.FOCAL * w)

private fun ss(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun moonLit(hijriDay: Int): Float {
    val age = ((hijriDay - 1).coerceIn(0, 29) + 1.0) / 29.53
    return ((1 - cos(2 * PI * age)) / 2).toFloat()
}

/**
 * The last gradient made, kept while its colours stay the same (a still moment draws again with no new object); a new
 * one only when the light has changed.
 */
private class BrushMemo {
    private var a = Color.Unspecified; private var b = Color.Unspecified; private var c = Color.Unspecified
    private var p0 = Float.NaN; private var p1 = Float.NaN
    private var brush: Brush? = null

    private fun same(a: Color, b: Color, c: Color, p0: Float, p1: Float) =
        brush != null && a == this.a && b == this.b && c == this.c && p0 == this.p0 && p1 == this.p1

    private fun keep(a: Color, b: Color, c: Color, p0: Float, p1: Float, made: Brush): Brush {
        this.a = a; this.b = b; this.c = c; this.p0 = p0; this.p1 = p1; brush = made
        return made
    }

    /** The sky: zenith at the top, [mid] a little over half-way down to the horizon, [horizon] at [horizonAt] (a fraction) and below. */
    fun vertical(zenith: Color, mid: Color, horizon: Color, horizonAt: Float, h: Float): Brush =
        if (same(zenith, mid, horizon, horizonAt, h)) brush!!
        else keep(zenith, mid, horizon, horizonAt, h, Brush.verticalGradient(0f to zenith, horizonAt * 0.58f to mid, horizonAt to horizon, 1f to horizon, startY = 0f, endY = h))

    /** Two colours from [top] to [bottom] (px). */
    fun twoTone(top: Color, bottom: Color, y0: Float, y1: Float): Brush =
        if (same(top, bottom, Color.Unspecified, y0, y1)) brush!!
        else keep(top, bottom, Color.Unspecified, y0, y1, Brush.verticalGradient(0f to top, 1f to bottom, startY = y0, endY = y1))
}

// ---------------------------------------------------------------------------------------------------------------------
// Stars, the moon, the Sun

/** A modest, fixed set of stars (the same every night, no twinkling), fewer and fainter towards the horizon. */
private class SimpleStars(w: Float, horizonY: Float, density: Float) {
    private val groups: List<List<Offset>>
    private val widths = floatArrayOf(1.1f * density, 1.7f * density, 2.6f * density)
    private val bright = floatArrayOf(0.45f, 0.7f, 0.95f)

    init {
        val rnd = java.util.Random(0x5DEECE66DL xor 1448L)
        val g = List(3) { ArrayList<Offset>() }
        repeat(90) {
            val u = rnd.nextFloat()
            // More stars high up than low down, none in the haze over the horizon.
            val v = rnd.nextFloat().pow(1.35f) * 0.86f
            val r = rnd.nextFloat()
            val k = when { r < 0.08f -> 2; r < 0.35f -> 1; else -> 0 }
            // The faint ones keep to the upper sky.
            if (k == 0 && v > 0.7f) return@repeat
            g[k] += Offset(u * w, v * horizonY)
        }
        groups = g
    }

    fun draw(s: DrawScope, night: Float) {
        for (k in groups.indices) {
            s.drawPoints(groups[k], PointMode.Points, Color.White.copy(alpha = night * bright[k]), widths[k], StrokeCap.Round)
        }
    }
}

/** The moon as it looks on this day of the Hijri month, lit on the side towards the Sun; its shape made once. */
private class SimpleMoon(hijriDay: Int, south: Boolean, rtl: Boolean, private val r: Float) {
    private val lit = moonLit(hijriDay)
    private val shape: Path?
    private val glow = Brush.radialGradient(listOf(Sky.Moon.copy(alpha = 0.26f), Color.Transparent), center = Offset.Zero, radius = r * 5)

    init {
        val age = ((hijriDay - 1).coerceIn(0, 29) + 1.0) / 29.53
        val waxing = age < 0.5
        // (The scene is mirrored in Arabic: the Sun is then on the other side of the moon.)
        val litOnRight = (waxing != south) != rtl
        shape = if (lit < 0.02f) null else Path().apply {
            val k = 1 - 2 * lit
            val side = if (litOnRight) 1f else -1f
            val steps = 32
            for (i in 0..steps) {
                val a = -PI / 2 + PI * i / steps
                val x = side * r * cos(a).toFloat(); val y = r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (i in steps downTo 0) {
                val a = -PI / 2 + PI * i / steps
                lineTo(side * k * r * cos(a).toFloat(), r * sin(a).toFloat())
            }
            close()
        }
    }

    fun draw(s: DrawScope, c: Offset, visibility: Float, night: Float) {
        val shape = shape ?: return
        if (visibility <= 0f) return
        s.translate(c.x, c.y) {
            if (night > 0.05f) drawCircle(glow, r * 5, Offset.Zero, alpha = visibility * lit * night)
            drawCircle(Sky.Moon.copy(alpha = 0.10f * visibility * night), r, Offset.Zero)
            drawPath(shape, Sky.Moon.copy(alpha = 0.96f * visibility))
        }
    }
}

/** The Sun: a soft glow, warmer when it is low, and the disc (as the 0.9 sky's Canvas Sun, without its rays). */
private class SimpleSun(private val density: Float) {
    private val glowR = 54f * density
    private val dayGlow = Brush.radialGradient(0f to Sky.SunGlow.copy(alpha = 0.60f), 0.35f to Sky.SunGlow.copy(alpha = 0.16f), 1f to Color.Transparent, center = Offset.Zero, radius = glowR)
    private val lowGlow = Brush.radialGradient(0f to Color(0xFFFF9A62).copy(alpha = 0.70f), 0.4f to Color(0xFFFF9A62).copy(alpha = 0.20f), 1f to Color.Transparent, center = Offset.Zero, radius = glowR)

    fun draw(s: DrawScope, at: Offset, l: Light, up: Float) {
        s.translate(at.x, at.y) {
            val warm = l.golden.coerceIn(0f, 1f)
            if (warm < 0.99f) drawCircle(dayGlow, glowR, Offset.Zero, alpha = up * (1f - warm))
            if (warm > 0.01f) drawCircle(lowGlow, glowR, Offset.Zero, alpha = up * warm)
            drawCircle(l.sunCore.copy(alpha = up), (7f + 1.5f * l.golden) * density, Offset.Zero)
            drawCircle(Color.White.copy(alpha = 0.92f * up), 3.2f * density, Offset.Zero)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// The land

/** What a layer of land is: plain ground, rock under snow, the surf's foam line. */
private enum class Kind { GROUND, SNOW, FOAM }

/**
 * One layer: its outline (made once), how far away it is ([haze]: how much of the air's colour it takes), and the ground's
 * own colour in plain daylight.
 */
private class SimpleLayer(val fill: Path, val snow: Path?, val top: Float, val base: Float, val soil: Color, val haze: Float, val kind: Kind)

/** The plan of one layer, in dp and in fractions of the width. */
private class Spec(
    val heightDp: Float, val baseDp: Float, val soil: Color, val haze: Float,
    /** The outline, 0 (the layer's base) to 1 (its highest), across the unmirrored width (0..1). */
    val shape: (Float) -> Float,
    val kind: Kind = Kind.GROUND,
    /** Conifers along the crest: spacing and height (dp), or none. */
    val treeDp: Float = 0f, val treeHeightDp: Float = 0f,
    /** Where the layer stops, in dp below its base (it stands in water there); NaN: it goes down to the page. */
    val footDp: Float = Float.NaN,
    /** Exact corners of the outline (fractions of the width), sampled as well as the even steps: cliffs stay sharp. */
    val corners: FloatArray? = null,
)

private class SimpleLand(
    private val layers: List<SimpleLayer>,
    /** Water from the horizon down, under the land (the sea, the arctic fjord), or none. */
    private val water: Boolean,
    private val waterSoil: Color,
    private val horizonY: Float,
    private val h: Float,
    /** Sun glitter on the water: offsets from the Sun's x, heights and half-lengths (px), made once. */
    private val glitter: FloatArray,
    private val density: Float,
) {
    fun draw(s: DrawScope, l: Light, page: Color, brushes: BrushMemo, fade: BrushMemo, sunX: Float, sunUp: Float, moonX: Float, moonOn: Float) = with(s) {
        val dark = page.luminance() < 0.3f
        val wN = l.night.coerceIn(0f, 1f)
        val wG = (l.golden * (1f - wN)).coerceIn(0f, 1f)
        val wD = (1f - wN - wG).coerceAtLeast(0f)
        val air = Sky.mix(l.horizon, l.mid, 0.28f)

        // The ground as the light of this moment shows it: plain by day, warmer and darker with the Sun low, dim and blue at night.
        fun lit(soil: Color): Color {
            val dayC = Sky.mix(soil, l.horizon, 0.12f)
            val goldC = Sky.mix(Sky.mix(soil, Color(0xFFE0855A), 0.38f), Color(0xFF2A1830), 0.30f)
            val nightC = Sky.mix(Sky.mix(soil, l.zenith, 0.78f), Color.Black, 0.32f)
            val t = wN + wG + wD
            return Color((nightC.red * wN + goldC.red * wG + dayC.red * wD) / t, (nightC.green * wN + goldC.green * wG + dayC.green * wD) / t,
                (nightC.blue * wN + goldC.blue * wG + dayC.blue * wD) / t)
        }
        // Snow: white by day, rose under a low Sun, moonlit blue-grey at night.
        fun snowLit(): Color {
            val dayC = Sky.mix(Color.White, l.horizon, 0.10f)
            val goldC = Sky.mix(Color(0xFFFFC9AE), l.horizon, 0.15f)
            val nightC = Sky.mix(Sky.mix(l.zenith, Color.White, 0.30f), Color.Black, 0.15f)
            val t = wN + wG + wD
            return Color((nightC.red * wN + goldC.red * wG + dayC.red * wD) / t, (nightC.green * wN + goldC.green * wG + dayC.green * wD) / t,
                (nightC.blue * wN + goldC.blue * wG + dayC.blue * wD) / t)
        }

        if (water) {
            // The water mirrors the sky: the horizon's light far off, the colour of the upper sky nearer, over the water's own colour.
            val far = Sky.mix(Sky.mix(l.horizon, lit(waterSoil), 0.30f), l.mid, 0.10f)
            val near = Sky.mix(Sky.mix(l.zenith, l.mid, 0.4f), lit(waterSoil), 0.50f)
            drawRect(brushes.twoTone(far, near, horizonY, h), topLeft = Offset(0f, horizonY), size = androidx.compose.ui.geometry.Size(size.width, h - horizonY))
            // The Sun's (or the moon's) path on the water: a few short flat strokes under it, wider nearer.
            val sunA = sunUp * (0.30f + 0.45f * l.golden) * (1f - 0.5f * l.night)
            if (sunA > 0.02f) glints(this, sunX, l.sunCore.copy(alpha = sunA))
            if (moonOn > 0.02f) glints(this, moonX, Sky.Moon.copy(alpha = 0.40f * moonOn))
        }

        val n = layers.size
        val toPage = if (dark) floatArrayOf(0.0f, 0.18f, 0.40f, 0.55f) else floatArrayOf(0.0f, 0.12f, 0.30f, 0.45f)
        layers.forEachIndexed { i, layer ->
            val nearest = i == n - 1
            val pageShare = if (nearest) toPage[3] else toPage[min(i, 2)]
            val ground = when (layer.kind) {
                Kind.FOAM -> Sky.mix(snowLit(), lit(waterSoil), 0.25f)
                else -> lit(layer.soil)
            }
            val crest = Sky.mix(Sky.mix(ground, air, layer.haze), page, pageShare)
            drawPath(layer.fill, crest)
            layer.snow?.let { drawPath(it, Sky.mix(Sky.mix(snowLit(), air, layer.haze * 0.8f), page, pageShare)) }
            // The nearest land flows into the page, where the day's times are written.
            if (nearest) drawPath(layer.fill, fade.twoTone(page.copy(alpha = 0f), page, layer.top, layer.base + 16f * density))
        }
    }

    private fun glints(s: DrawScope, x: Float, c: Color) {
        var i = 0
        while (i < glitter.size) {
            val dx = glitter[i]; val y = glitter[i + 1]; val half = glitter[i + 2]
            s.drawLine(c, Offset(x + dx - half, y), Offset(x + dx + half, y), strokeWidth = glitter[i + 3])
            i += 4
        }
    }

    companion object {
        fun build(terrain: Terrain, seed: Long, scene: SkyScene, g: SkyGeometry, w: Float, horizonY: Float, h: Float, density: Float, rtl: Boolean): SimpleLand {
            val (riseX, setX) = sunriseSunsetX(scene, g)
            // Scrambled, as java.util.Random gives neighbouring seeds nearly the same first numbers.
            var z = seed * -0x61c8864680b583ebL + terrain.ordinal * 0x2545F4914F6CDD1DL
            z = (z xor (z ushr 31)) * -0x4b47d5b1b2b3a7c3L
            val rnd = java.util.Random(z xor (z ushr 29))
            val specs = specsOf(terrain, rnd)
            // The layers spread over the depth the header gives the land (as the 0.9 sky's do).
            val deepest = specs.maxOf { it.baseDp }.coerceAtLeast(1f)
            val spread = (((h - horizonY) / density - 14f) / deepest).coerceIn(0.5f, 1.4f)
            // Where the Sun rises and sets, in the unmirrored picture.
            val rx = if (rtl) w - riseX else riseX
            val sx = if (rtl) w - setX else setX
            // How high the land may stand at x (px over the horizon): a little under the Sun's path wherever the path
            // is in the sky, so that the land never hides the Sun or crosses its path (the path is drawn over the
            // land); beyond sunrise and sunset, rising from those points as the path does.
            val margin = 10f * density
            val room = { x: Float ->
                val f = g.fractionAt(if (rtl) w - x else x)
                val pathY = g.y(scene.elevationAt(f), f)
                val under = if (pathY < horizonY) horizonY - pathY - margin else Float.MAX_VALUE
                min(under, 0.8f * min(abs(x - rx), abs(x - sx)) - margin)
            }
            val layers = specs.map { layer(it, rnd, w, horizonY, h, density, spread, rtl, rx, sx, room) }
            val water = terrain == Terrain.SEA || terrain == Terrain.ARCTIC
            val waterTop = horizonY + 2f * density
            val waterBottom = layers.firstOrNull { it.kind == Kind.FOAM }?.top ?: layers.last().top
            return SimpleLand(layers, water, if (terrain == Terrain.ARCTIC) Color(0xFF3F6A80) else Color(0xFF3F7591), horizonY, h,
                if (water) glitter(rnd, waterTop, waterBottom, density) else FloatArray(0), density)
        }

        /** Short strokes in rows down the water, further apart and longer nearer (perspective). */
        private fun glitter(rnd: java.util.Random, top: Float, bottom: Float, density: Float): FloatArray {
            val out = ArrayList<Float>()
            var t = 0.02f
            while (t < 1f) {
                val y = top + (bottom - top) * t
                if (y > bottom - 2f * density) break
                val spreadPx = (2f + 16f * t) * density
                repeat(if (t < 0.15f) 1 else 2) {
                    out += (rnd.nextFloat() * 2f - 1f) * spreadPx
                    out += y
                    out += (2f + 7f * t) * density * (0.5f + rnd.nextFloat())
                    out += (0.7f + 1.3f * t) * density
                }
                t += 0.035f + 0.11f * t
            }
            return out.toFloatArray()
        }

        private fun layer(spec: Spec, rnd: java.util.Random, w: Float, horizonY: Float, h: Float, density: Float, spread: Float, rtl: Boolean, rx: Float, sx: Float,
                          room: (Float) -> Float): SimpleLayer {
            val base = horizonY + spec.baseDp * spread * density
            val amp = spec.heightDp * density
            // Whatever stands above the horizon is pressed, smoothly, under the room the Sun's path leaves it ([room]),
            // so the land comes down towards where the Sun rises and sets; there the last few dp are opened as in the
            // 0.9 sky ([horizonOpening]), so that the Sun meets the horizon at Shuruq and Maghrib, where their marks are.
            // (Trees on the crest, [lift] px, come down with the land.)
            val ground = { x: Float, lift: Float ->
                val y0 = base - amp * spec.shape(x / w) - lift
                val above = horizonY - y0
                val y1 = if (above <= 0f) y0 else {
                    val r = room(x)
                    horizonY - (if (r <= 0f) 0f else r * kotlin.math.tanh(above / r))
                }
                y1 + (horizonY + density - y1).coerceAtLeast(0f) * horizonOpening(x, rx, sx, density)
            }
            // The x positions of the outline: even steps, the shape's own corners, and the trees' tips and notches.
            val xs = ArrayList<Float>(); val lift = ArrayList<Float>()
            if (spec.treeDp > 0f) {
                val s = spec.treeDp * density; val th = spec.treeHeightDp * density
                var x = -s
                while (x <= w + s) {
                    val wid = s * (0.7f + 0.6f * rnd.nextFloat())
                    val tall = th * (0.55f + 0.45f * rnd.nextFloat())
                    // A notch between trees, then the next tip.
                    xs += x; lift += tall * 0.18f * rnd.nextFloat()
                    xs += x + wid * 0.5f; lift += tall
                    x += wid * 0.85f
                }
            } else {
                val step = 2f * density
                var x = -step
                while (x <= w + step) { xs += x; lift += 0f; x += step }
                spec.corners?.forEach { u -> xs += u * w; lift += 0f }
                val order = xs.indices.sortedBy { xs[it] }
                val sx2 = order.map { xs[it] }; xs.clear(); xs.addAll(sx2)
                lift.clear(); repeat(xs.size) { lift += 0f }
            }
            val ys = FloatArray(xs.size)
            var top = Float.MAX_VALUE
            for (i in xs.indices) {
                val x = xs[i]
                val y = ground(x, lift[i])
                ys[i] = y
                if (y < top) top = y
            }
            fun X(x: Float) = if (rtl) w - x else x
            val foot = if (spec.footDp.isNaN()) h + 2f else base + spec.footDp * density
            val fill = Path().apply {
                moveTo(X(xs.first()), foot)
                for (i in xs.indices) lineTo(X(xs[i]), ys[i])
                lineTo(X(xs.last()), foot)
                close()
            }
            // Snow on the high ground: down from the crest, deeper the higher the crest stands, ragged at its edge.
            val snow = if (spec.kind != Kind.SNOW) null else Path().apply {
                // (Over the upper three quarters of the layer's highest ground.)
                val line = top + (base - top) * 0.75f
                val p1 = rnd.nextFloat() * 6.283f; val p2 = rnd.nextFloat() * 6.283f
                fun depth(i: Int): Float {
                    val u = xs[i] / w
                    val rag = 0.62f + 0.25f * sin(97f * u + p1) + 0.13f * sin(233f * u + p2)
                    return (line - ys[i]).coerceAtLeast(0f) * rag
                }
                moveTo(X(xs.first()), ys.first())
                for (i in xs.indices) lineTo(X(xs[i]), ys[i])
                for (i in xs.indices.reversed()) lineTo(X(xs[i]), ys[i] + depth(i))
                close()
            }
            return SimpleLayer(fill, snow, top, base, spec.soil, spec.haze, spec.kind)
        }

        private fun specsOf(terrain: Terrain, rnd: java.util.Random): List<Spec> {
            fun ph() = rnd.nextFloat() * 6.283f
            val tau = 2 * PI.toFloat()
            fun rolling(freq: Float, p1: Float, p2: Float): (Float) -> Float = { u ->
                val v = 0.62f * sin(tau * freq * u + p1) + 0.38f * sin(tau * freq * 2.3f * u + p2)
                ((v + 1f) / 2f).coerceIn(0f, 1f)
            }
            fun peaks(k: Float, p1: Float, p2: Float, p3: Float): (Float) -> Float = { u ->
                val a = 1f - abs(sin(2.1f * PI.toFloat() * u * k + p1))
                val b = 1f - abs(sin(4.7f * PI.toFloat() * u * k + p2))
                val c = 1f - abs(sin(10.9f * PI.toFloat() * u * k + p3))
                (0.55f * a * a * a + 0.32f * b * b + 0.13f * c).coerceIn(0f, 1f)
            }
            fun ridged(p1: Float, p2: Float, p3: Float): (Float) -> Float = { u ->
                val a = 1f - abs(sin(2.3f * PI.toFloat() * u + p1))
                val b = 1f - abs(sin(5.1f * PI.toFloat() * u + p2))
                val c = 1f - abs(sin(11.7f * PI.toFloat() * u + p3))
                (0.58f * a * a + 0.30f * b * b + 0.12f * c).coerceIn(0f, 1f)
            }
            return when (terrain) {
                Terrain.DUNES -> {
                    // A dune: a long windward slope rising to a sharp brink, then the steep slip face; the crests vary in height.
                    fun dune(freq: Float, p: Float, p2: Float): (Float) -> Float = { u ->
                        val t = ((freq * u + p / 6.283f) % 1f + 1f) % 1f
                        val k = 0.72f
                        val v = if (t < k) { val x = t / k; x * (2f - x) } else { val x = (t - k) / (1f - k); (1f - x).pow(1.7f) }
                        val size = 0.62f + 0.38f * sin(tau * freq * 0.37f * u + p2)
                        (v * size).coerceIn(0f, 1f)
                    }
                    val sand = Color(0xFFD9B07A)
                    listOf(
                        Spec(14f, 2f, sand, 0.42f, dune(3.1f, ph(), ph())),
                        Spec(22f, 26f, Color(0xFFD7A66C), 0.18f, dune(1.9f, ph(), ph())),
                        Spec(28f, 62f, Color(0xFFD9A468), 0.06f, dune(1.15f, ph(), ph())),
                    )
                }
                Terrain.FOREST -> {
                    // Far blue hills; nearer, wooded hills under a dark, jagged line of conifers.
                    listOf(
                        Spec(34f, 2f, Color(0xFF5E7A78), 0.50f, ridged(ph(), ph(), ph())),
                        Spec(16f, 24f, Color(0xFF3E5E44), 0.28f, rolling(1.3f, ph(), ph()), treeDp = 5f, treeHeightDp = 9f),
                        Spec(10f, 58f, Color(0xFF26402C), 0.06f, rolling(0.9f, ph(), ph()), treeDp = 8f, treeHeightDp = 26f),
                    )
                }
                Terrain.ARCTIC -> {
                    // Sharp snowy peaks over the still water of a fjord; a lower snowy shore; dark rocks in front.
                    listOf(
                        Spec(80f, 3f, Color(0xFF55606E), 0.38f, peaks(1f, ph(), ph(), ph()), kind = Kind.SNOW, footDp = 3f),
                        Spec(22f, 20f, Color(0xFF5C6672), 0.20f, peaks(1.6f, ph(), ph(), ph()), kind = Kind.SNOW, footDp = 3f),
                        Spec(12f, 60f, Color(0xFF353C44), 0.04f, rolling(1.1f, ph(), ph())),
                    )
                }
                Terrain.SEA -> {
                    // Open water to the horizon, a line of surf, a pale beach in front.
                    fun shore(freq: Float, p1: Float): (Float) -> Float = { u -> ((0.55f * sin(tau * freq * u + p1) + 0.2f * sin(tau * freq * 2.7f * u) + 1f) / 2f).coerceIn(0f, 1f) }
                    val p = ph()
                    listOf(
                        Spec(5f, 47f, Color.White, 0.0f, shore(0.9f, p), kind = Kind.FOAM),
                        Spec(8f, 58f, Color(0xFFE2CDA4), 0.05f, shore(0.9f, p)),
                    )
                }
                Terrain.CANYON -> {
                    listOf(
                        mesas(rnd, 44f, 3f, Color(0xFFBC6440), 0.36f, tall = true),
                        mesas(rnd, 26f, 26f, Color(0xFFB4532E), 0.14f, tall = false),
                        Spec(10f, 62f, Color(0xFFA45A34), 0.04f, rolling(1.4f, ph(), ph())),
                    )
                }
                else -> {
                    // The meadow's soft green hills (and, for the 0.9 names, the same).
                    listOf(
                        Spec(26f, 3f, Color(0xFF6F8E7C), 0.55f, rolling(0.9f, ph(), ph())),
                        Spec(24f, 26f, Color(0xFF6E9A5C), 0.24f, rolling(1.25f, ph(), ph())),
                        Spec(22f, 62f, Color(0xFF5E8A4A), 0.05f, rolling(0.8f, ph(), ph())),
                    )
                }
            }
        }

        /**
         * Flat-topped mesas and narrow buttes with sheer walls standing on slopes of fallen rock, across a nearly level
         * floor: a piecewise straight outline, its corners kept exactly.
         */
        private fun mesas(rnd: java.util.Random, heightDp: Float, baseDp: Float, soil: Color, haze: Float, tall: Boolean): Spec {
            val us = ArrayList<Float>(); val vs = ArrayList<Float>()
            fun at(u: Float, v: Float) { us += u; vs += v }
            var u = -0.06f
            at(u, 0.05f)
            while (u < 1.06f) {
                // The floor between.
                u += 0.05f + 0.13f * rnd.nextFloat()
                val floor = 0.04f + 0.08f * rnd.nextFloat()
                at(u, floor)
                val butte = rnd.nextFloat() < 0.3f
                val width = if (butte) 0.025f + 0.025f * rnd.nextFloat() else 0.06f + 0.16f * rnd.nextFloat()
                val top = if (butte) (if (tall) 0.8f else 0.7f) + 0.2f * rnd.nextFloat() else 0.5f + 0.4f * rnd.nextFloat()
                val talus = top * (0.28f + 0.12f * rnd.nextFloat())
                // Up the fallen rock, then the wall, the flat top (a little uneven), the wall, the rock.
                u += 0.018f; at(u, talus)
                u += 0.004f; at(u, top)
                if (!butte && rnd.nextFloat() < 0.4f) {
                    // A step in the top.
                    u += width * 0.55f; at(u, top); u += 0.003f; at(u, top * 0.82f); u += width * 0.45f; at(u, top * 0.82f)
                    u += 0.004f; at(u, talus * 0.9f)
                } else {
                    u += width; at(u, top * (0.97f + 0.03f * rnd.nextFloat()))
                    u += 0.004f; at(u, talus)
                }
                u += 0.02f; at(u, floor)
            }
            val ua = us.toFloatArray(); val va = vs.toFloatArray()
            val shape: (Float) -> Float = { x ->
                var lo = 0; var hi = ua.size - 1
                while (lo < hi - 1) { val m = (lo + hi) / 2; if (ua[m] <= x) lo = m else hi = m }
                if (x <= ua[0]) va[0] else if (x >= ua[ua.size - 1]) va[va.size - 1]
                else { val t = (x - ua[lo]) / (ua[hi] - ua[lo]); va[lo] + (va[hi] - va[lo]) * t }
            }
            return Spec(heightDp, baseDp, soil, haze, shape, corners = ua)
        }
    }
}
