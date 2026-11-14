package com.noor.app.ui.today

import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.theme.Sky
import com.noor.prayer.Prayer
import com.noor.prayer.astro.SunPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The land under the sky: the kind of country the user is in, drawn in layers that fade into the distance. */
enum class Terrain {
    /** Sand seas: long, smooth crests with a steeper slip face. */
    DUNES,
    /** Mountain ridges far away, softening into rolling hills nearby. */
    HILLS,
    /** High ranges (0.9.8): tall, sharp ridges with snow on the far peaks, forested slopes nearer. */
    MOUNTAINS,
    /** The open sea to the horizon (0.9.8), the Sun's and the moon's path on the water, a low shore in front. */
    SEA;

    companion object {
        /** Arid countries of the Arabian Peninsula, the Sahara and its edges, and the Levant's deserts. */
        private val ARID = setOf(
            "AE", "SA", "QA", "KW", "BH", "OM", "YE", "IQ", "JO", "EG", "LY", "DZ", "TN", "MA", "EH", "MR", "ML", "NE", "TD", "SD", "DJ",
        )
        /** Small island countries: the sea is never far. */
        private val ISLANDS = setOf(
            "MV", "BH", "SG", "MT", "CY", "MU", "SC", "KM", "BS", "BB", "FJ", "TO", "WS", "KI", "MH", "PW", "FM", "NR", "TV", "CV", "ST",
            "AG", "DM", "GD", "KN", "LC", "VC", "TT", "BM", "KY", "VU", "SB",
        )
        /** Countries lying mostly among high mountains. */
        private val ALPINE = setOf("NP", "BT", "CH", "AT", "AF", "KG", "TJ", "LS", "AD", "LI")

        fun of(countryCode: String?): Terrain = if (countryCode?.uppercase() in ARID) DUNES else HILLS

        /**
         * The scenery that fits a place (0.9.8): high places (1,400 m and above) and alpine countries show mountains,
         * small island countries the sea, the deserts dunes, everywhere else hills.
         */
        fun auto(countryCode: String?, elevationM: Double?): Terrain {
            val cc = countryCode?.uppercase()
            return when {
                elevationM != null && elevationM >= 1400.0 -> MOUNTAINS
                cc in ISLANDS -> SEA
                cc in ARID -> DUNES
                cc in ALPINE -> MOUNTAINS
                else -> HILLS
            }
        }
    }
}

/** Everything the scene needs for one place and one day. */
@Immutable
data class SkyScene(
    val path: SunPath,
    /** Today's events in order (Fajr … Isha); missing events (polar days) are simply absent. */
    val events: List<Pair<Prayer, Instant>>,
    /** The current minute (the scene does not need seconds). */
    val now: Instant,
    val next: Prayer?,
    /** Day of the Hijri month, for the moon's phase and where it stands. */
    val hijriDay: Int,
    val southernHemisphere: Boolean,
    val terrain: Terrain = Terrain.HILLS,
    /** Varies the clouds from day to day and place to place. */
    val seed: Long = 0L,
    /** The lie of the land: fixed for a place, so home always looks like home. */
    val landSeed: Long = 0L,
    /** Where the user is (degrees): the night sky shows the stars as they stand there. */
    val latitude: Double = if (southernHemisphere) -30.0 else 30.0,
    val longitude: Double = 0.0,
) {
    /** (fraction of the day, elevation) pairs, computed once. */
    val samples: List<Pair<Double, Double>> = path.samples
    val nowFraction: Double = path.fraction(now)
    /** Solar noon, as a fraction of the day. */
    val noonFraction: Double = samples.maxByOrNull { it.second }?.first ?: 0.5
    val sunElevation: Double = path.elevationAt(now)
    /** After the highest point of the day the light turns warm. */
    val evening: Boolean = nowFraction > noonFraction

    fun elevationAt(fraction: Double): Double = interpolate(samples, fraction.coerceIn(0.0, 1.0))
    /** The clock time at a fraction of the day. */
    fun instantAt(fraction: Double): Instant =
        path.start.plusMillis(((path.end.toEpochMilli() - path.start.toEpochMilli()) * fraction.coerceIn(0.0, 1.0)).toLong())
    fun eveningAt(fraction: Double): Boolean = fraction > noonFraction
}

/** Heights in the scene, as fractions of its height. */
@Immutable
data class SceneLayout(
    /** The clouds keep below this line, clear of the text written on the sky. */
    val cloudTop: Float,
    /** Where the noon Sun is drawn. */
    val arcTop: Float,
    /** Elevation zero. */
    val horizon: Float,
    /** Where the deepest point of the night part of the path is drawn. */
    val ground: Float,
)

/** Positions on the sky: a smooth time axis that gives daylight most of the width, and an elevation scale. */
internal class SkyGeometry(scene: SkyScene, private val w: Float, val horizonY: Float, val arcTopY: Float, val groundY: Float, private val rtl: Boolean) {
    private val maxEl = max(scene.path.maxElevation, 12.0)
    private val minEl = minOf(scene.path.minElevation, -24.0)
    private val warp: (Double) -> Double = timeWarp(scene)

    fun x(fraction: Double): Float {
        val u = (-0.04 + 1.08 * warp(fraction.coerceIn(0.0, 1.0))).toFloat() * w
        return if (rtl) w - u else u
    }

    private val maxApp = apparent(maxEl)

    /**
     * Height on screen of the Sun at true elevation [el] (degrees), as it is seen (0.9.7). Shuruq and Maghrib are the
     * moments the Sun's upper edge touches the horizon, with the air lifting it near the horizon (refraction): 50' in
     * all, by the standard definition the prayer times use. So the path is drawn as the Sun's visible upper edge: it
     * meets the horizon line exactly at Shuruq and at Maghrib, where their marks are. The lift fades out a few degrees
     * below the horizon, where nothing is seen.
     */
    fun y(el: Double): Float {
        val e = apparent(el)
        return if (e >= 0) (horizonY - (e / maxApp) * (horizonY - arcTopY)).toFloat()
        else (horizonY + (-e / -minEl) * (groundY - horizonY)).toFloat()
    }

    private fun apparent(el: Double): Double = el + horizonLift(el)

    /** The fraction of the day drawn at [px] (inverse of [x]). */
    fun fractionAt(px: Float): Double {
        var lo = 0.0; var hi = 1.0
        val target = if (rtl) w - px else px
        repeat(40) {
            val m = (lo + hi) / 2
            val u = (-0.04 + 1.08 * warp(m)).toFloat() * w
            if (u < target) lo = m else hi = m
        }
        return (lo + hi) / 2
    }

    companion object {
        /**
         * Monotone cubic (Fritsch–Carlson) through Fajr−30 min, sunrise, sunset and Isha+30 min. 0.9.6: twilight gets
         * more of the width (8–22 % and 78–92 %, was 10–18 % and 82–90 %): the sky changes fastest then, and dragging
         * the Sun through dawn and dusk changed the colours up to nine times faster per pixel than at midday; now about
         * a third less, with the four prayers of twilight further apart.
         */
        fun timeWarp(scene: SkyScene): (Double) -> Double {
            val f = scene.events.associate { (p, t) -> p to scene.path.fraction(t) }
            val fajr = f[Prayer.FAJR]; val rise = f[Prayer.SHURUQ]; val set = f[Prayer.MAGHRIB]; val isha = f[Prayer.ISHA]
            if (rise == null || set == null) return { it }
            val half = 30.0 / (24 * 60)
            val xs = mutableListOf(0.0); val ys = mutableListOf(0.0)
            fun add(x: Double, y: Double) { if (x > xs.last() + 1e-4 && x < 1 - 1e-4 && y > ys.last()) { xs += x; ys += y } }
            fajr?.let { add(it - half, 0.08) }
            add(rise, 0.22); add(set, 0.78)
            isha?.let { add(it + half, 0.92) }
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
 * The light of one moment, from the Sun's height: every colour in the scene is taken from it, so sky, clouds, land and
 * mist always agree with each other and with the live sky palette the watch also uses.
 */
@Immutable
internal data class Light(
    val zenith: Color, val mid: Color, val horizon: Color,
    /** 0 in daylight, 1 in full night: stars, moon, how dark the clouds are. */
    val night: Float,
    /** 1 with the Sun low (just above or just below the horizon): warm undersides, rim light, long rays. */
    val golden: Float,
    /** 1 in full daylight. */
    val day: Float,
    /** Morning mist in the valleys. */
    val mist: Float,
    val cloudTop: Color, val cloudBase: Color, val cloudGlow: Color,
    val sunCore: Color, val sunGlow: Color,
)

internal fun lightAt(elevation: Double, evening: Boolean): Light {
    val s = Sky.colors(elevation, evening)
    val e = elevation.toFloat()
    val day = smoothstep(-4f, 12f, e)
    val golden = smoothstep(0f, 1f, 1f - abs(e - 1f) / 10f)
    val night = s.night
    val mist = if (!evening) 0.85f * smoothstep(0f, 1f, 1f - abs(e + 1f) / 13f) + 0.18f * night else 0.22f * night + 0.12f * golden
    val white = Color.White
    val wN = night.coerceIn(0f, 1f)
    val wG = (golden * (1f - wN)).coerceIn(0f, 1f)
    val wD = (1f - wN - wG).coerceAtLeast(0f)
    fun blend(n: Color, g: Color, d: Color): Color {
        val t = wN + wG + wD
        return Color(
            (n.red * wN + g.red * wG + d.red * wD) / t, (n.green * wN + g.green * wG + d.green * wD) / t,
            (n.blue * wN + g.blue * wG + d.blue * wD) / t, 1f,
        )
    }
    val warmGlow = Color(0xFFFF9A62)
    return Light(
        zenith = s.zenith, mid = s.mid, horizon = s.horizon, night = night, golden = golden, day = day, mist = mist,
        // Day: white tops, blue-grey bases. Low sun: cool lavender tops, undersides lit coral and gold. Night: dark, faintly lit.
        // The lit side of each billow: white by day, peach and gold from a low Sun, a faint moonlit grey at night.
        cloudTop = blend(Sky.mix(s.horizon, white, 0.30f), Color(0xFFFFC9A0), Color(0xFFFFFFFF)),
        // The shaded body: blue-grey by day, dusky mauve at sunset, a little darker than the sky at night.
        // At night the vapour is a little paler than the sky around it (moonlit), so clouds never read as dark blocks.
        cloudBase = blend(Sky.mix(s.horizon, white, 0.12f), Sky.mix(Sky.mix(s.zenith, s.mid, 0.55f), Color(0xFF3A2847), 0.22f), Sky.mix(white, s.mid, 0.46f)),
        cloudGlow = Sky.mix(Sky.SunGlow, warmGlow, golden * 0.8f),
        sunCore = Sky.mix(Sky.SunCore, Color(0xFFFFC27A), golden * 0.75f),
        sunGlow = Sky.mix(Sky.SunGlow, warmGlow, golden * 0.6f),
    )
}

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * The home screen's world: the sky of this moment over a natural landscape, with the day's sun path drawn across it and
 * the prayers placed on the path. Clouds drift, mist lies in the valleys at dawn, the moon rides the Sun's own arc
 * (later each day by its age), stars come out after Isha and birds cross at dawn and dusk. Nothing man-made.
 *
 * [display] is the moment shown, as a fraction of the day: now, or wherever the user has drawn the Sun to.
 * [parallax] (the page's scroll, in px) is read while drawing, never in composition, so scrolling does not recompose:
 * the sky and its clouds lag behind as the page moves, while the land, the path and the Sun stay with the page.
 * [stillTime] fixes the ambient clock when animations are off (previews and tests), so the scene can be shown at any
 * moment of its cycle.
 */
@Composable
internal fun LivingSky(
    scene: SkyScene,
    layout: SceneLayout,
    display: Double,
    scrubbing: Boolean,
    page: Color,
    description: String,
    modifier: Modifier = Modifier,
    parallax: () -> Float = { 0f },
    stillTime: Float? = null,
    /** The opening's progress, 0 → 1 (1 when there is none): the scene fades in from the page, the land rises a
     *  little, the clouds run fast and settle to their real drift, and the path and its prayers draw themselves. */
    opening: () -> Float = { 1f },
    /** The real sky (0.10) draws the scene itself, behind the whole screen: here only the path and the prayers are
     *  drawn, over it. */
    real: com.noor.app.sky.RealSkySetup? = null,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ambient = LocalAmbientMotion.current
    val light = lightAt(scene.elevationAt(display), scene.eveningAt(display))
    val spec = if (scrubbing || !ambient) snap<Color>() else tween(1200)
    val zenith by animateColorAsState(light.zenith, spec, label = "zenith")
    val mid by animateColorAsState(light.mid, spec, label = "mid")
    val horizonColor by animateColorAsState(light.horizon, spec, label = "horizon")

    // The path, its prayers and the land come in with the opening.
    val pathIn: () -> Float = { smoothstep(0f, 0.45f, opening()) }
    val marksIn: () -> Float = { smoothstep(0.25f, 0.75f, opening()) }
    val landLift: () -> Float = { val o = 1f - opening(); o * o * 14f * densityOf() }

    // One slow cycle (four minutes) drives everything ambient: drifting clouds, twinkling stars, the turning of the
    // Sun's rays, birds and meteors. It ticks about 30 times a second rather than every frame: the motion is slow, and
    // the phone has better things to do with its battery. It stops with the screen (the frame clock pauses).
    val clock = remember { mutableFloatStateOf(stillTime ?: 0.13f) }
    // Height of the scene, kept outside the snapshot system: it only tells the clock whether the sky is still on screen.
    val heightPx = remember { floatArrayOf(Float.MAX_VALUE) }
    if (ambient) LaunchedEffect(Unit) {
        val origin = withFrameNanos { it }
        val offset = clock.floatValue
        while (true) {
            withFrameNanos { t ->
                // Scrolled out of sight, the sky rests.
                if (parallax() < heightPx[0]) clock.floatValue = ((offset + (t - origin) / 1e9 / CYCLE_SECONDS) % 1.0).toFloat()
            }
            delay(24)
        }
    }
    val live = ambient || stillTime != null
    val now: () -> Float = {
        val t = (if (ambient) clock.floatValue else stillTime ?: clock.floatValue) + OpeningSky.cloudLapse(opening()) / CYCLE_SECONDS.toFloat()
        (t % 1f + 1f) % 1f
    }
    val stars = remember { starField(150) }
    val grainBrush = remember { grain() }
    val dust = remember { dustField(220) }
    val land = remember(scene.landSeed, scene.terrain) { landField(scene.landSeed, scene.terrain) }
    val soil = soilOf(scene.terrain)

    // 0.9.6: the sky is computed (Atmosphere) for this moment and this header, off the main thread; on Android 13 and
    // later the GPU draws it with clouds and land (ShaderSky); elsewhere the Canvas layers below draw over the same sky.
    val androidView = LocalView.current
    val resources = LocalContext.current.resources
    // Held as Any: the shader class exists only on Android 13 and later.
    val context = LocalContext.current
    val quality = remember { SkyQuality.load(context) }.let { SkyQuality.level.intValue.coerceAtLeast(0) }
    val built: Any? = remember(real != null) { if (real == null && Build.VERSION.SDK_INT >= 33 && androidView.isHardwareAccelerated && !SkyShaders.failed) SkyShaders.create(resources) else null }
    val shaders: Any? = built
    // Frames are watched while the Sun is dragged (not during the opening): if the GPU sky keeps the phone under about
    // 22 frames a second for three seconds, it is drawn at a lower resolution (SkyQuality). The first second of a drag
    // is not judged (a first frame can be slow for reasons of its own).
    if (shaders != null && ambient && quality == 0) LaunchedEffect(scrubbing) {
        if (!scrubbing) return@LaunchedEffect
        // Not during the opening (read here, not in composition, so the opening does not recompose the screen).
        androidx.compose.runtime.snapshotFlow { opening() >= 1f }.first { it }
        var last = withFrameNanos { it }
        val start = last
        var ema = 16.7f
        var slowSince = -1L
        while (true) {
            val t = withFrameNanos { it }
            val ms = (t - last) / 1e6f
            last = t
            ema = ema * 0.92f + ms.coerceAtMost(200f) * 0.08f
            if (t - start < 1_000_000_000L) continue
            if (ema > 45f) {
                if (slowSince < 0) slowSince = t
                if (t - slowSince > 3_000_000_000L) { SkyQuality.stepDown(context); break }
            } else slowSince = -1L
        }
    }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val densityValue = LocalDensity.current.density
    densityHolder[0] = densityValue
    val skyView = remember(scene, layout, sizePx, rtl, densityValue) {
        if (sizePx.width == 0 || sizePx.height == 0) null else SkyView(scene, sizePx.width.toFloat(), sizePx.height.toFloat(), densityValue, layout, rtl)
    }
    val look = skyView?.let { SkyLook.at(scene, it, display) }
    val capAlt = skyView?.let { it.altitudeAt(it.cloudTopY - 8 * densityValue) } ?: 90.0
    val skyLight = if (real != null) null else rememberSkyLight(look, skyView, capAlt)
    val weather = remember(scene.seed, scene.terrain) { Weather.of(scene.seed, scene.terrain) }
    // The weather at the moment shown: it changes through the day, and as the Sun is dragged through it.
    val weatherNow = weather.at(display)
    val landProfile = remember(scene.landSeed, scene.terrain) { LandProfile.of(scene.landSeed, scene.terrain) }
    // The layers' own clocks (seconds), each layer redrawn only when its clock moves: the clouds' about 15 times a
    // second (they move less than a fifth of a dp per tick, so it looks continuous); the GPU sky's as often at night
    // (twinkling stars) but once a second by day (only the high cirrus drifts, far too slowly to see a step: 0.9.8
    // redrew the whole sky 15 times a second), 4 times under an overcast or a layer of cloud; the land's 4 times
    // (cloud shadows, mist). So the phone works a few percent of the time while the screen is simply looked at.
    val origin = remember(scene.seed) { (((scene.seed % 997) + 997) % 997).toFloat() * 3.7f }
    val cloudSeconds = remember { mutableFloatStateOf(origin + (stillTime ?: 0.13f) * CYCLE_SECONDS.toFloat()) }
    val skySeconds = remember { mutableFloatStateOf(cloudSeconds.floatValue) }
    val landSeconds = remember { mutableFloatStateOf(cloudSeconds.floatValue) }
    val starry = rememberUpdatedState(light.night > 0f)
    // An overcast or a layer of cloud drifts overhead, where it moves fastest: then the sky ticks 4 times a second.
    val decked = rememberUpdatedState(weatherNow.overcast > 0.03f || weatherNow.layer > 0.08f)
    if (ambient) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        val base = cloudSeconds.floatValue
        while (true) {
            withFrameNanos { t ->
                if (parallax() < heightPx[0]) {
                    val sec = base + (t - start) / 1e9f
                    cloudSeconds.floatValue = sec
                    if (starry.value || sec - skySeconds.floatValue >= (if (decked.value) 0.25f else 1f)) skySeconds.floatValue = sec
                    if (sec - landSeconds.floatValue >= 0.25f) landSeconds.floatValue = sec
                }
            }
            delay(60)
        }
    }
    val gpuInputs: () -> GpuSkyInputs? = {
        if (skyView != null && look != null && skyLight != null) GpuSkyInputs(scene, skyView, look, skyLight, weather, landProfile, page, skyView.cloudTopY - 8 * densityValue, weatherNow, scene.instantAt(display)) else null
    }
    val skyImage = remember(skyLight, skyView) { if (shaders == null && skyLight != null && skyView != null) screenSky(skyLight, skyView) else null }

    if (real != null) {
        // The real sky: hand the renderer everything this moment's picture needs (it is told only when that changes).
        // (The time of year: the real one where the seasons are followed, otherwise the one each scenery looks best in.)
        val year = remember(scene.now, scene.southernHemisphere, real.seasons, real.biome) {
            if (real.seasons) com.noor.app.sky.SkyDirector.yearPhase(scene.now, scene.southernHemisphere) else real.biome.signatureYear
        }
        val wx = remember(scene.seed, real.biome, (year * 24).toInt()) { com.noor.app.sky.SkyDirector.weather(scene.seed, real.biome, year) }
        val frame: () -> com.noor.app.sky.RealSkyFrame? = {
            val v = skyView; val l = look
            if (v == null || l == null || real.w <= 0f) null else {
                val o = opening()
                com.noor.app.sky.RealSkyFrame.of(
                    scene, v, l, real.biome, wx, year, display, scene.instantAt(display), real.w, real.h,
                    textBottom = v.cloudTopY - 8 * densityValue, panelTop = real.panelTop, panelRadius = real.radius, lightGlass = real.lightGlass, tint = real.tint,
                    fade = smoothstep(0f, 0.38f, o), busy = scrubbing || o < 1f, still = !ambient, lapse = OpeningSky.cloudLapse(o),
                )
            }
        }
        androidx.compose.runtime.SideEffect { real.bridge.frame.value = frame }
    }
    Box(modifier.clipToBounds().onSizeChanged { heightPx[0] = it.height.toFloat(); sizePx = it }.semantics { contentDescription = description }
        .graphicsLayer { alpha = smoothstep(0f, 0.38f, opening()) }) {
        if (real == null && shaders != null && Build.VERSION.SDK_INT >= 33) GpuSkyLayer(shaders as SkyShaders, gpuInputs, { skySeconds.floatValue + OpeningSky.cloudLapse(opening()) }, parallax, quality)
        // 1 · Sky, warm air round a low Sun, stars, the moon. Farthest away, so it lags most when the page scrolls.
        if (real == null && shaders == null) Canvas(Modifier.fillMaxSize().graphicsLayer { translationY = parallax() * 0.5f }) {
            val w = size.width; val h = size.height
            val g = SkyGeometry(scene, w, h * layout.horizon, h * layout.arcTop, h * layout.ground, rtl)
            // The clock is read only when there is something to move, so a daytime sky is not redrawn for nothing.
            val time = if (light.night > 0f && live) now() else 0.13f
            val hy = h * layout.horizon
            // The sky of this moment, as the atmosphere model computed it (the same table the GPU renderer uses).
            if (skyImage != null) drawImage(skyImage, dstSize = IntSize(w.toInt() + 1, h.toInt() + 1), filterQuality = FilterQuality.Low)
            else drawRect(Brush.verticalGradient(0f to zenith, layout.horizon * 0.58f to mid, layout.horizon to horizonColor, 1f to horizonColor))
            if (light.night > 0f) {
                // Far from city lights the Milky Way shows: a faint river of light with fine dust along it.
                val dark = smoothstep(0.55f, 1f, light.night)
                if (dark > 0f) {
                    val pivot = Offset(w * 0.5f, hy * 0.42f)
                    withTransform({ rotate(MILKY_WAY_TILT, pivot); scale(3.6f, 1f, pivot) }) {
                        val r = hy * 0.30f
                        drawCircle(Brush.radialGradient(0f to Color(0xFFC9D2FF).copy(alpha = 0.14f * dark), 0.5f to Color(0xFFB8C4F0).copy(alpha = 0.07f * dark), 1f to Color.Transparent,
                            center = pivot, radius = r), r, pivot)
                    }
                    val cosT = cos(MILKY_WAY_TILT * PI.toFloat() / 180f); val sinT = sin(MILKY_WAY_TILT * PI.toFloat() / 180f)
                    dust.forEach { d ->
                        val along = d.x * w * 0.62f; val across = d.y * hy * 0.10f
                        val px = pivot.x + along * cosT - across * sinT; val py = pivot.y + along * sinT + across * cosT
                        if (py < hy && py > 0f) drawCircle(Color.White.copy(alpha = dark * d.brightness * 0.55f * extinction(py / hy)), d.r * density * 0.7f, Offset(px, py))
                    }
                }
                // Stars over the whole sky, thinning towards the horizon as the air thickens (no line where they stop),
                // and twinkling more down there.
                stars.forEach { s ->
                    val y = s.y * hy
                    val low = s.y.coerceIn(0f, 1f)
                    val tw = if (live) 1f - (0.30f + 0.25f * low) * (0.5f + 0.5f * sin(2 * PI * (time * 70f + s.phase)).toFloat()) else 0.85f
                    drawCircle(Color.White.copy(alpha = light.night * s.brightness * tw * extinction(low)), radius = s.r * density, center = Offset(s.x * w, y))
                }
                if (live) shootingStar(time, light.night, w, h * layout.cloudTop)
            }
            // A fine grain over the sky, so slow gradients (deep night blues above all) do not show steps.
            drawRect(grainBrush, alpha = 0.05f, blendMode = BlendMode.Overlay)
            // The moon on the Sun's own path, as far behind it as it is old.
            val age = (((scene.hijriDay - 1).coerceIn(0, 29) + 0.5) / 29.53)
            val moonF = ((display - age) % 1.0 + 1.0) % 1.0
            val moonEl = scene.elevationAt(moonF)
            if (moonEl > 2.0) {
                val visibility = (0.35f + 0.65f * light.night).coerceAtMost(1f) * smoothstep(2f, 8f, moonEl.toFloat())
                moon(scene.hijriDay, Offset(g.x(moonF), g.y(moonEl)), 10.dp.toPx(), visibility, scene.southernHemisphere, light.night)
            }
        }

        // 2 · Clouds (0.9.9): the same on every phone, drifting with the wind (still when animations are off).
        if (real == null) CloudLayer(gpuInputs, { cloudSeconds.floatValue + OpeningSky.cloudLapse(opening()) }, parallax)

        // 3 · The Sun's rays and halo, the sun path above the horizon, and the Sun. The path is built once per size.
        Box(Modifier.fillMaxSize().drawWithCache {
            val w = size.width; val h = size.height
            val hy = h * layout.horizon
            val g = SkyGeometry(scene, w, hy, h * layout.arcTop, h * layout.ground, rtl)
            val path = sunPath(scene, g)
            val stroke = 1.6.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
            onDrawBehind {
                val shownF = display
                val sunEl = scene.elevationAt(shownF)
                val pathAlpha = pathIn()
                val sun = Offset(g.x(shownF), g.y(sunEl))
                // The Sun fades as it sinks below the horizon (no sudden switch at sunrise or sunset). On the GPU the
                // shader draws the Sun itself.
                val up = if (shaders != null || real != null) 0f else smoothstep(-2.5f, 0.5f, sunEl.toFloat())
                if (up > 0f) clipRect(0f, 0f, w, hy + 2.dp.toPx()) {
                    // Long, faint rays, turning slowly; longer and warmer when the Sun is low.
                    val rays = 14
                    val len = w * (0.20f + 0.24f * light.golden)
                    val turn = now() * 2 * PI.toFloat() * 3f
                    for (i in 0 until rays) {
                        val a = turn + i * 2 * PI.toFloat() / rays
                        val l = len * (if (i % 2 == 0) 1f else 0.62f)
                        val end = Offset(sun.x + l * cos(a), sun.y + l * sin(a))
                        drawLine(Brush.linearGradient(listOf(light.sunGlow.copy(alpha = (0.13f + 0.10f * light.golden) * up), Color.Transparent), sun, end),
                            sun, end, strokeWidth = (if (i % 2 == 0) 3.dp else 1.5.dp).toPx(), cap = StrokeCap.Round)
                    }
                    val halo = (52 + 18 * light.golden).dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(light.sunGlow.copy(alpha = 0.62f * up), light.sunGlow.copy(alpha = 0.14f * up), Color.Transparent), center = sun, radius = halo), halo, sun)
                }
                clipRect(0f, 0f, w, hy) {
                    drawPath(path, Color.White.copy(alpha = 0.5f * pathAlpha), style = Stroke(stroke * 0.8f, cap = StrokeCap.Round, pathEffect = dash))
                    val left = if (rtl) sun.x else 0f; val right = if (rtl) w else sun.x
                    clipRect(left, 0f, right, hy) {
                        drawPath(path, light.sunGlow.copy(alpha = 0.24f), style = Stroke(stroke * 4f, cap = StrokeCap.Round))
                        drawPath(path, light.sunCore.copy(alpha = 0.95f), style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                if (up > 0f) {
                    drawCircle(light.sunCore.copy(alpha = up), (7f + 1.5f * light.golden).dp.toPx(), sun)
                    drawCircle(Color.White.copy(alpha = 0.92f * up), 3.2.dp.toPx(), sun)
                }
            }
        })

        // 4 · The land: far ridges pale with distance, near ones darker, the nearest one the page itself.
        if (real != null) Unit
        else if (shaders != null && Build.VERSION.SDK_INT >= 33) GpuLandLayer(shaders as SkyShaders, gpuInputs, landLift, quality) { landSeconds.floatValue + OpeningSky.cloudLapse(opening()) }
        else Box(Modifier.fillMaxSize().graphicsLayer { translationY = landLift() }.drawWithCache {
            val w = size.width; val h = size.height
            val (riseX, setX) = sunriseSunsetX(scene, SkyGeometry(scene, w, h * layout.horizon, h * layout.arcTop, h * layout.ground, rtl))
            val shapes = land.paths(w, h * layout.horizon, h, density, riseX, setX)

            onDrawBehind {
                val g = SkyGeometry(scene, w, h * layout.horizon, h * layout.arcTop, h * layout.ground, rtl)
                drawLand(shapes, light, horizonColor, mid, zenith, page, soil, g.x(display), w)
            }
        })

        // Rain falling here (0.9.9), in front of the land.
        if (real == null) RainLayer(gpuInputs, ambient)

        // 5 · Over the land: the night part of the path, the prayers on it, and the birds.
        Box(Modifier.fillMaxSize().drawWithCache {
            val w = size.width; val h = size.height
            val hy = h * layout.horizon
            val g = SkyGeometry(scene, w, hy, h * layout.arcTop, h * layout.ground, rtl)
            // Each prayer at the Sun's real position at its moment: in the sky on the path, on the horizon at Shuruq
            // and Maghrib, below it (in the land) at Fajr and Isha.
            val marks = scene.events.map { (p, t) ->
                val f = scene.path.fraction(t)
                Triple(p, f, prayerMark(scene, g, f))
            }
            onDrawBehind {
                val shownF = display
                val markAlpha = marksIn()
                // A mark fills as the Sun passes it, over a few minutes of the day, rather than switching.
                val soft = 4.0 / (24 * 60)
                marks.forEach { (p, f, pt) ->
                    val passed = smoothstep(-1f, 1f, ((shownF - f) / soft).toFloat())
                    val dot = Color.White
                    // Below the horizon a mark sits on the land: a soft shadow keeps it readable there.
                    if (pt.y > hy + 2.dp.toPx()) drawCircle(Color.Black.copy(alpha = 0.28f * markAlpha), 7.dp.toPx(), pt)
                    when {
                        p == scene.next -> {
                            // The next prayer's ring breathes, slowly, while it waits.
                            val breath = if (ambient) 0.5f + 0.5f * sin(2 * PI * now() * 40).toFloat() else 0.5f
                            drawCircle(Sky.GoldOnSky.copy(alpha = (0.16f + 0.16f * breath) * markAlpha), (9f + 3f * breath).dp.toPx(), pt)
                            drawCircle(Sky.GoldOnSky.copy(alpha = markAlpha), 5.5.dp.toPx(), pt, style = Stroke(1.8.dp.toPx()))
                        }
                        else -> {
                            if (passed < 1f) {
                                drawCircle(zenith.copy(alpha = 0.55f * (1f - passed) * markAlpha), 3.4.dp.toPx(), pt)
                                drawCircle(dot.copy(alpha = 0.8f * (1f - passed) * markAlpha), 3.4.dp.toPx(), pt, style = Stroke(1.2.dp.toPx()))
                            }
                            if (passed > 0f) drawCircle(dot.copy(alpha = 0.95f * passed * markAlpha), (3.4f - 0.4f * passed).dp.toPx(), pt)
                        }
                    }
                }
                if (live && birdsFly(scene, display)) birds(now(), scene, display, light, w, h * layout.cloudTop, hy, rtl)
            }
        })
    }
}

private const val CYCLE_SECONDS = 240.0

/**
 * Where the Sun rises and sets, the land opens onto the true horizon (0.9.7): within about 34 dp of those points no
 * layer of land stands above the horizon line, so the Sun meets the horizon exactly at the moment of Shuruq and of
 * Maghrib, where their marks are. Returns 1 at the opening, 0 away from it. Shared by both renderers (the land shader
 * has the same rule, `openAt`) and the tests.
 */
/**
 * How much higher the Sun's visible upper edge stands than its centre's true elevation [el] (degrees): its radius
 * (16') plus refraction, scaled so that at the standard sunrise and sunset elevation (−50') the edge is exactly on the
 * horizon, and fading out between there and 4° below, where nothing is seen.
 */
internal fun horizonLift(el: Double): Double {
    val r0 = Atmosphere.refraction(-0.8333)
    if (el >= -0.8333) return Atmosphere.refraction(el) * (0.5667 / r0) + 0.2667
    val t = ((el + 4.0) / (4.0 - 0.8333)).coerceIn(0.0, 1.0)
    return 0.8333 * t * t * (3 - 2 * t)
}

internal fun horizonOpening(x: Float, riseX: Float, setX: Float, density: Float): Float {
    fun one(c: Float): Float { val d = abs(x - c) / density; return 1f - smoothstep(12f, 34f, d) }
    return max(one(riseX), one(setX))
}

/** Where on screen the Sun rises and sets today (far off screen when it does not, as in polar day or night). */
internal fun sunriseSunsetX(scene: SkyScene, g: SkyGeometry): Pair<Float, Float> {
    val rise = scene.events.firstOrNull { it.first == Prayer.SHURUQ }?.let { g.x(scene.path.fraction(it.second)) } ?: -1e5f
    val set = scene.events.firstOrNull { it.first == Prayer.MAGHRIB }?.let { g.x(scene.path.fraction(it.second)) } ?: -1e5f
    return rise to set
}

/**
 * Where a prayer's mark sits (0.9.7): at the Sun's real position at that moment, on the same scale as the path. So
 * Shuruq and Maghrib (the Sun's upper edge on the horizon, 0.83° below it) sit on the horizon itself, which the land
 * leaves open there, and Fajr and Isha (the Sun 15–20° down) sit below the horizon, in the land, where the Sun is.
 */
internal fun prayerMark(scene: SkyScene, g: SkyGeometry, f: Double): Offset = Offset(g.x(f), g.y(scene.elevationAt(f)))

private val densityHolder = floatArrayOf(2.625f)
private fun densityOf() = densityHolder[0]

/** Tests render without a GPU and need each frame's sky at once; the app computes it off the main thread. */
internal object SkyCompute {
    @Volatile var synchronous = false
}

/**
 * The atmosphere table for the moment shown: the first one at once (so the first frame is right), then each new one
 * computed off the main thread, the latest request winning, while the previous table stays on screen.
 */
@Composable
private fun rememberSkyLight(look: SkyLook?, view: SkyView?, capAlt: Double): SkyLight? {
    val key = if (look != null && view != null) SkyLight.key(look, view, capAlt) else null
    if (SkyCompute.synchronous) return remember(key) { key?.let { SkyLight.compute(it) } }
    val first = remember(key == null) { key?.let { SkyLight.compute(it) } }
    var latest by remember { mutableStateOf<SkyLight?>(null) }
    val keyState = rememberUpdatedState(key)
    LaunchedEffect(Unit) {
        snapshotFlow { keyState.value }.filterNotNull().distinctUntilChanged().conflate().collect { k ->
            if (latest?.key != k) latest = withContext(Dispatchers.Default) { SkyLight.compute(k) }
        }
    }
    return latest ?: first
}

/** The computed sky laid out on the screen (for the Canvas renderer): a small image stretched over the header. */
private fun screenSky(light: SkyLight, view: SkyView): ImageBitmap {
    val cols = Atmosphere.NX; val rows = 64
    val px = IntArray(cols * rows)
    for (y in 0 until rows) {
        val alt = view.altitudeAt((y + 0.5f) / rows * view.h)
        val v = Atmosphere.rowOf(alt)
        for (x in 0 until cols) px[y * cols + x] = Atmosphere.sample(light.lut, x / (cols - 1.0), v)
    }
    return android.graphics.Bitmap.createBitmap(px, cols, rows, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

internal fun interpolate(samples: List<Pair<Double, Double>>, f: Double): Double {
    // Samples are in time order: find the last one at or before f.
    var lo = 0; var hi = samples.size - 1
    while (lo < hi) { val m = (lo + hi + 1) / 2; if (samples[m].first <= f) lo = m else hi = m - 1 }
    val i = lo.coerceIn(0, samples.size - 2)
    val (f0, e0) = samples[i]; val (f1, e1) = samples[i + 1]
    return if (f1 == f0) e0 else e0 + (e1 - e0) * ((f - f0) / (f1 - f0))
}

private fun sunPath(scene: SkyScene, g: SkyGeometry): Path = Path().apply {
    scene.samples.forEachIndexed { i, (f, el) -> if (i == 0) moveTo(g.x(f), g.y(el)) else lineTo(g.x(f), g.y(el)) }
}

// ---------------------------------------------------------------------------------------------------------------------
// Stars, moon, shooting stars

private class Star(val x: Float, val y: Float, val r: Float, val brightness: Float, val phase: Float)

/** A fixed field, so the sky does not reshuffle between frames or screens. */
private fun starField(n: Int): List<Star> {
    val rnd = java.util.Random(1448)
    return List(n) {
        val big = rnd.nextFloat() < 0.10f
        Star(rnd.nextFloat(), rnd.nextFloat(), if (big) 1.3f else 0.5f + rnd.nextFloat() * 0.5f,
            if (big) 0.95f else 0.3f + rnd.nextFloat() * 0.5f, rnd.nextFloat())
    }
}

private const val MILKY_WAY_TILT = -24f

/** How much of a star's light reaches us at a height in the sky (0 = top of the scene, 1 = horizon). */
private fun extinction(low: Float): Float = 1f - smoothstep(0.45f, 1f, low)

/** A tile of fine random grain, repeated over the sky. Made once. */
private fun grain(): ShaderBrush {
    val n = 64
    val rnd = java.util.Random(7)
    val px = IntArray(n * n) { val v = 96 + rnd.nextInt(64); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
    val bmp = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}

/** Fine stars gathered along the Milky Way: x along the band (−1…1), y across it (roughly −1…1, most near the middle). */
private fun dustField(n: Int): List<Star> {
    val rnd = java.util.Random(2718)
    return List(n) {
        val across = (rnd.nextGaussian() * 0.45).toFloat().coerceIn(-1.2f, 1.2f)
        Star(rnd.nextFloat() * 2f - 1f, across, 0.35f + rnd.nextFloat() * 0.4f, 0.25f + rnd.nextFloat() * 0.6f, rnd.nextFloat())
    }
}

/** Now and then, on a dark night, a meteor: a short bright streak high in the sky. */
private fun DrawScope.shootingStar(time: Float, night: Float, w: Float, maxY: Float) {
    if (night < 0.8f) return
    // Rare, as they are: one in every other minute of the cycle (about every two minutes), lasting about a second.
    val period = 1f / 4f
    val k = (time / period).toInt()
    if (k % 2 == 1) return
    val local = (time - k * period) / period
    if (local > 0.02f) return
    val rnd = java.util.Random(k * 7919L + 3)
    val p = local / 0.02f
    // High up and off to one side, clear of the countdown, falling across the corner of the sky.
    val start = Offset(w * (0.70f + rnd.nextFloat() * 0.25f), maxY * (0.06f + rnd.nextFloat() * 0.22f))
    val dir = Offset(-0.85f, 0.52f)
    val len = w * 0.22f
    val head = start + dir * (len * p)
    val tail = start + dir * (len * max(0f, p - 0.35f))
    val fade = sin(PI * p).toFloat()
    drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * fade)), tail, head), tail, head, 1.6.dp.toPx(), StrokeCap.Round)
}

/**
 * The moon as it looks on this day of the Hijri month: a thin crescent at the start, full near the 14th, waning after.
 * Lit on the side towards the Sun (right while waxing, mirrored in the southern hemisphere). Paler by day.
 */
private fun DrawScope.moon(hijriDay: Int, c: Offset, r: Float, visibility: Float, south: Boolean, night: Float) {
    val age = ((hijriDay - 1).coerceIn(0, 29) + 1.0) / 29.53
    val phase = 2 * PI * age
    val lit = ((1 - cos(phase)) / 2).toFloat()
    if (lit < 0.02f || visibility <= 0f) return
    val waxing = age < 0.5
    val litOnRight = waxing != south
    val moonColor = Sky.Moon
    drawCircle(Brush.radialGradient(listOf(moonColor.copy(alpha = 0.26f * visibility * lit * night), Color.Transparent), center = c, radius = r * 5), r * 5, c)
    drawCircle(moonColor.copy(alpha = 0.10f * visibility * night), r, c)
    val k = (1 - 2 * lit)
    val p = Path()
    val side = if (litOnRight) 1f else -1f
    val steps = 48
    for (i in 0..steps) {
        val a = -PI / 2 + PI * i / steps
        val pt = Offset(c.x + side * r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    for (i in steps downTo 0) {
        val a = -PI / 2 + PI * i / steps
        p.lineTo(c.x + side * k * r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
    }
    p.close()
    drawPath(p, moonColor.copy(alpha = 0.96f * visibility))
}

// ---------------------------------------------------------------------------------------------------------------------
// Land

/** One layer of land: its top edge as a function of position, in [0, 1] (1 = highest). */
private class Ridge(val heightDp: Float, val baseDp: Float, val shape: (Float) -> Float)

/** The layers of land for a place, far to near, with crests that differ from place to place. */
private class LandField(private val ridges: List<Ridge>) {
    class Shapes(val fills: List<Path>, val edges: List<Path>, val bases: List<Float>, private val skyline: FloatArray, private val step: Float) {
        /** The top of the land at [x]: where it meets the sky. */
        fun skylineAt(x: Float): Float = skyline[(x / step).toInt().coerceIn(0, skyline.size - 1)]
    }

    fun paths(w: Float, horizonY: Float, h: Float, density: Float, riseX: Float = -1e5f, setX: Float = -1e5f): Shapes {
        val fills = ArrayList<Path>(); val edges = ArrayList<Path>(); val bases = ArrayList<Float>()
        val step = 3f * density
        val skyline = FloatArray((w / step).toInt() + 3) { Float.MAX_VALUE }
        // The layers spread over the depth the header gives the land, so the nearest one always fades into the page.
        val deepest = ridges.maxOf { it.baseDp }
        val spread = ((h - horizonY) / density - 12f) / deepest
        ridges.forEach { r ->
            val base = horizonY + r.baseDp * spread.coerceIn(0.55f, 1.4f) * density
            val amp = r.heightDp * density
            val fill = Path(); val edge = Path()
            fill.moveTo(0f, h + 2f)
            var x = 0f
            var first = true
            while (x <= w + step) {
                // Kept below the horizon where the Sun rises and sets (see horizonOpening).
                val y0 = base - amp * r.shape(x / w)
                val y = y0 + (horizonY + density - y0).coerceAtLeast(0f) * horizonOpening(x, riseX, setX, density)
                fill.lineTo(x, y)
                val k = (x / step).toInt().coerceIn(0, skyline.size - 1)
                if (y < skyline[k]) skyline[k] = y
                if (first) { edge.moveTo(x, y); first = false } else edge.lineTo(x, y)
                x += step
            }
            fill.lineTo(w + step, h + 2f); fill.close()
            fills += fill; edges += edge; bases += base
        }
        return Shapes(fills, edges, bases, skyline, step)
    }
}

private fun landField(seed: Long, terrain: Terrain): LandField {
    val rnd = java.util.Random(seed * 31 + 17)
    fun ph() = rnd.nextFloat() * 6.283f
    return when (terrain) {
        Terrain.DUNES -> {
            // A dune: a long gentle windward slope and a steeper slip face; nearer dunes are bigger and wider apart.
            fun dune(freq: Float, p: Float, p2: Float): (Float) -> Float = { u ->
                val t = 2 * PI.toFloat() * freq * u + p
                val v = sin(t) + 0.36f * sin(2 * t + 0.9f) + 0.12f * sin(5.3f * freq * u + p2)
                ((v + 1.3f) / 2.6f).coerceIn(0f, 1f).pow(1.25f)
            }
            LandField(listOf(
                Ridge(16f, 4f, dune(2.6f, ph(), ph())),
                Ridge(20f, 20f, dune(1.9f, ph(), ph())),
                Ridge(24f, 40f, dune(1.35f, ph(), ph())),
                Ridge(26f, 66f, dune(0.95f, ph(), ph())),
            ))
        }
        Terrain.HILLS -> {
            // Far: a mountain range with sharp ridgelines. Nearer: rounder and rounder hills.
            fun ridged(p1: Float, p2: Float, p3: Float): (Float) -> Float = { u ->
                val a = 1f - abs(sin(2.3f * PI.toFloat() * u + p1))
                val b = 1f - abs(sin(5.1f * PI.toFloat() * u + p2))
                val c = 1f - abs(sin(11.7f * PI.toFloat() * u + p3))
                (0.58f * a * a + 0.30f * b * b + 0.12f * c).coerceIn(0f, 1f)
            }
            fun rolling(freq: Float, p1: Float, p2: Float): (Float) -> Float = { u ->
                val v = 0.62f * sin(2 * PI.toFloat() * freq * u + p1) + 0.38f * sin(2 * PI.toFloat() * freq * 2.3f * u + p2)
                ((v + 1f) / 2f).coerceIn(0f, 1f)
            }
            LandField(listOf(
                Ridge(34f, 6f, ridged(ph(), ph(), ph())),
                Ridge(24f, 22f, rolling(1.6f, ph(), ph())),
                Ridge(22f, 42f, rolling(1.15f, ph(), ph())),
                Ridge(22f, 68f, rolling(0.8f, ph(), ph())),
            ))
        }
        Terrain.MOUNTAINS -> {
            // Tall, sharp ranges far away; forested ridges and foothills nearer.
            fun peaks(p1: Float, p2: Float, p3: Float, k: Float): (Float) -> Float = { u ->
                val a = 1f - abs(sin(2.1f * PI.toFloat() * u * k + p1))
                val b = 1f - abs(sin(4.7f * PI.toFloat() * u * k + p2))
                val c = 1f - abs(sin(10.9f * PI.toFloat() * u * k + p3))
                (0.55f * a * a * a + 0.32f * b * b + 0.13f * c).coerceIn(0f, 1f)
            }
            fun rolling(freq: Float, p1: Float, p2: Float): (Float) -> Float = { u ->
                val v = 0.62f * sin(2 * PI.toFloat() * freq * u + p1) + 0.38f * sin(2 * PI.toFloat() * freq * 2.3f * u + p2)
                ((v + 1f) / 2f).coerceIn(0f, 1f)
            }
            LandField(listOf(
                Ridge(56f, 4f, peaks(ph(), ph(), ph(), 1f)),
                Ridge(34f, 20f, peaks(ph(), ph(), ph(), 1.4f)),
                Ridge(22f, 42f, rolling(1.15f, ph(), ph())),
                Ridge(22f, 68f, rolling(0.8f, ph(), ph())),
            ))
        }
        Terrain.SEA -> {
            // Flat water to the horizon, a low shore in front.
            fun shore(freq: Float, p1: Float): (Float) -> Float = { u -> ((0.6f * sin(2 * PI.toFloat() * freq * u + p1) + 1f) / 2f).coerceIn(0f, 1f) }
            LandField(listOf(
                Ridge(0.5f, 0f, { 0f }),
                Ridge(0.5f, 20f, { 0f }),
                Ridge(12f, 50f, shore(1.4f, ph())),
                Ridge(16f, 70f, shore(0.8f, ph())),
            ))
        }
    }
}

/** The ground's own colour in plain daylight: warm sand, the sage of grass and scrub, grey-green mountains, the sea. */
private fun soilOf(terrain: Terrain): Color = when (terrain) {
    Terrain.DUNES -> Color(0xFFD9B07A)
    Terrain.HILLS -> Color(0xFF7F9A78)
    Terrain.MOUNTAINS -> Color(0xFF7D8A86)
    Terrain.SEA -> Color(0xFF4F7F96)
}

/**
 * Atmospheric perspective: the farthest land is nearly the colour of the air at the horizon, each nearer layer shows
 * more of the ground's own colour (as the light of the moment colours it) and is closer to the page colour, and the
 * nearest fades into the page itself, so the land flows into the day's times beneath it. Mist lies in the valleys at
 * dawn; the crests facing a low Sun catch its light.
 */
private fun DrawScope.drawLand(shapes: LandField.Shapes, light: Light, horizon: Color, mid: Color, zenith: Color, page: Color, soil: Color, sunX: Float, w: Float) {
    val n = shapes.fills.size
    val dark = page.luminance() < 0.3f
    // The ground as the light of this moment shows it.
    val wN = light.night.coerceIn(0f, 1f)
    val wG = (light.golden * (1f - wN)).coerceIn(0f, 1f)
    val wD = (1f - wN - wG).coerceAtLeast(0f)
    val dayGround = Sky.mix(soil, horizon, 0.12f)
    val goldGround = Sky.mix(Sky.mix(soil, Color(0xFFE0855A), 0.38f), Color(0xFF2A1830), 0.28f)
    val nightGround = Sky.mix(Sky.mix(soil, zenith, 0.78f), Color.Black, 0.30f)
    val t = wN + wG + wD
    val ground = Color(
        (nightGround.red * wN + goldGround.red * wG + dayGround.red * wD) / t,
        (nightGround.green * wN + goldGround.green * wG + dayGround.green * wD) / t,
        (nightGround.blue * wN + goldGround.blue * wG + dayGround.blue * wD) / t,
    )
    val air = Sky.mix(horizon, mid, 0.28f)
    val haze = floatArrayOf(0.58f, 0.32f, 0.14f, 0.04f)
    val toPage = if (dark) floatArrayOf(0.02f, 0.24f, 0.52f, 0.8f) else floatArrayOf(0.0f, 0.14f, 0.36f, 0.62f)
    val mistColor = Sky.mix(horizon, Color.White, 0.5f)
    shapes.fills.forEachIndexed { i, fill ->
        val crest = Sky.mix(Sky.mix(ground, air, haze.getOrElse(i) { 0f }), page, toPage.getOrElse(i) { 1f })
        val nearest = i == n - 1
        val base = shapes.bases[i]
        val top = base - 40.dp.toPx()
        val foot = if (nearest) page else Sky.mix(crest, page, 0.45f)
        drawPath(fill, Brush.verticalGradient(listOf(crest, foot), startY = top, endY = base + (if (nearest) 14.dp else 56.dp).toPx()))
        // Light along the crest: warm towards a low Sun, a whisper by day, faint moonlight at night.
        val rimAlpha = (0.9f * light.golden + 0.14f * light.day + 0.12f * light.night) * (1f - 0.2f * i)
        if (rimAlpha > 0.02f) {
            val rimColor = if (light.golden > 0.15f) light.sunGlow else Color.White
            drawPath(shapes.edges[i], Brush.radialGradient(listOf(rimColor.copy(alpha = rimAlpha), rimColor.copy(alpha = rimAlpha * 0.12f)),
                center = Offset(sunX, base), radius = w * 0.75f), style = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Mist settling in the valley in front of this layer.
        if (i < n - 1 && light.mist > 0.03f) {
            val y = shapes.bases[i + 1] - 8.dp.toPx()
            val a = light.mist * (0.7f - 0.16f * i)
            val hgt = 34.dp.toPx()
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.7f to mistColor.copy(alpha = a), 1f to Color.Transparent, startY = y - hgt, endY = y + 8.dp.toPx()),
                topLeft = Offset(0f, y - hgt), size = Size(w, hgt + 8.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Birds

/** Birds are about in the hours after sunrise and in the late afternoon until just after sunset. */
private fun birdsFly(scene: SkyScene, display: Double): Boolean {
    val el = scene.elevationAt(display)
    return if (scene.eveningAt(display)) el in -3.0..24.0 else el in -3.0..18.0
}

/**
 * Now and then, a small flock crossing the sky: a few strokes, wings beating, drifting in a loose V. Never at night,
 * never in the text above.
 */
private fun DrawScope.birds(time: Float, scene: SkyScene, display: Double, light: Light, w: Float, top: Float, horizonY: Float, rtl: Boolean) {
    val period = 1f / 6f // six flights a cycle (about every 40 s), each crossing in a third of it
    val k = (time / period).toInt()
    val local = (time - k * period) / period
    if (local > 0.34f) return
    val rnd = java.util.Random(k * 104729L + scene.seed)
    val p = local / 0.34f
    val y0 = top + (horizonY - top) * (0.25f + rnd.nextFloat() * 0.35f)
    val dir = if (rtl) -1f else 1f
    val startX = if (rtl) w * 1.1f else -w * 0.1f
    val lead = Offset(startX + dir * w * 1.2f * p, y0 - 18.dp.toPx() * p + 4.dp.toPx() * sin(p * 9f))
    val color = Sky.mix(light.zenith, Color.Black, 0.6f).copy(alpha = 0.42f + 0.2f * light.golden)
    val count = 3 + rnd.nextInt(3)
    for (i in 0 until count) {
        val row = (i + 1) / 2
        val sideSign = if (i % 2 == 0) 1f else -1f
        val pos = lead + Offset(-dir * row * 14.dp.toPx(), sideSign * row * 7.dp.toPx() + (if (i == 0) 0f else 0f))
        val flap = sin((time * 2400f + i * 1.7f).toDouble()).toFloat()
        val span = (4.2f + rnd.nextFloat() * 1.2f).dp.toPx()
        val lift = span * (0.25f + 0.35f * flap)
        val b = Path().apply {
            moveTo(pos.x - span, pos.y - lift)
            quadraticTo(pos.x - span * 0.4f, pos.y - lift * 0.2f, pos.x, pos.y)
            quadraticTo(pos.x + span * 0.4f, pos.y - lift * 0.2f, pos.x + span, pos.y - lift)
        }
        drawPath(b, color, style = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private operator fun Offset.times(f: Float) = Offset(x * f, y * f)
