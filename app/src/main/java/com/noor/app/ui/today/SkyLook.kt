package com.noor.app.ui.today

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything the sky shows at one moment of the day, for one header size (0.9.6): where the Sun and the moon are drawn,
 * the light, the weather, the mist. Pure Kotlin, shared by the GPU renderer and the Canvas fallback, and tested.
 *
 * The sky is seen through a wide lens: [FOV] across the screen, the horizon at [SceneLayout.horizon], the top of the
 * header [ALT_TOP] degrees up, more of the height given to the low sky where the colours of dawn and dusk are.
 */
internal class SkyView(scene: SkyScene, val w: Float, val h: Float, val density: Float, layout: SceneLayout, val rtl: Boolean) {
    val horizonY = h * layout.horizon
    val arcTopY = h * layout.arcTop
    val cloudTopY = h * layout.cloudTop
    val geometry = SkyGeometry(scene, w, horizonY, arcTopY, h * layout.ground, rtl, SunLift.rise * SunLift.FOCAL * w, SunLift.set * SunLift.FOCAL * w)
    val fov: Double = (w / density * 0.0051).toDouble().coerceIn(1.6, 2.6)
    /** Where the Sun rises and sets on the horizon today, px (far off screen when it does not). */
    val sunrise: Pair<Float, Float> = sunriseSunsetX(scene, geometry)

    /** Altitude (degrees) seen at height [y] (px). */
    fun altitudeAt(y: Float): Double = ALT_TOP * ((horizonY - y) / horizonY).toDouble().coerceIn(0.0, 1.0).pow(ALT_POW)
    fun azimuthAt(x: Float): Double = (x / w - 0.5) * fov

    companion object {
        const val ALT_TOP = 80.0
        const val ALT_POW = 1.25
    }
}

/**
 * The weather of a day at a place (0.9.9). Each day, at each place, has a character — clear, fair-weather cumulus,
 * convective (cumulus building into towers and afternoon thunderstorms), stormy, a layered sky, overcast, rainy, or a
 * mix — chosen at random, more often clear over the desert, more often stormy over mountains. Within the day it
 * evolves hour by hour ([at]): heaped clouds grow with the warmth of the day and melt away at night, storms come late
 * in the afternoon and linger into the evening, an overcast thins and thickens; on top of that smooth randomness, so no
 * two days and no two hours are quite alike, and nothing changes abruptly while the Sun is dragged through the day.
 */
internal class Weather private constructor(
    val type: Int,
    /** For the shapes and places of everything in the sky. */
    val seed: Int,
    val seedX: Float, val seedY: Float,
    private val cirrusBase: Float,
) {
    /** The weather at one moment; every value 0..1. */
    class State(
        /** How many heaped clouds (cumulus and towers). */
        val cumulus: Float,
        /** How tall they grow: small fair-weather cumulus at 0, towers by 0.5, thunderstorms towards 1. */
        val size: Float,
        /** Isolated clouds at 0, large clusters and broken fields towards 1. */
        val cluster: Float,
        /** A layer of stratocumulus or altocumulus. */
        val layer: Float,
        /** A grey deck over the whole sky. */
        val overcast: Float,
        /** Thunderstorms: towers with anvils, lightning. */
        val storm: Float,
        /** Rain falling here. */
        val rain: Float,
        /** Rain shafts and virga under distant clouds. */
        val showers: Float,
        val cirrus: Float,
        /** The hour of the day (0…24): over the hours the clouds travel with the wind. */
        val hour: Float = 12f,
    )

    /** The weather at [fraction] of the day (0 midnight … 1 midnight). */
    fun at(fraction: Double): State {
        val h = (fraction * 24.0).toFloat()
        fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
        // Smooth randomness over the hours.
        fun noise(period: Float, k: Int): Float {
            val x = h / period; val i = kotlin.math.floor(x).toInt(); val f = x - i
            val u = f * f * (3 - 2 * f)
            return CloudField.hash(seed, i, k) * (1 - u) + CloudField.hash(seed, i + 1, k) * u
        }
        val n1 = noise(3f, 101); val n2 = noise(2.2f, 102); val n3 = noise(4f, 103)
        // The warmth of the day that builds heaped clouds; storms come later.
        val conv = smooth(9f, 13.5f, h) * (1 - smooth(18.5f, 22f, h))
        val late = smooth(13f, 16.5f, h) * (1 - smooth(20.5f, 23.8f, h))
        val day = smooth(5.5f, 8.5f, h) * (1 - smooth(19f, 22f, h))
        var cu = 0f; var size = 0.15f; var cluster = 0.2f; var layer = 0f; var overcast = 0f; var storm = 0f; var rain = 0f; var showers = 0f
        when (type) {
            CLEAR -> { cu = 0.04f + 0.14f * conv * n1; size = 0.1f; cluster = 0.1f }
            FAIR -> { cu = 0.2f + 0.4f * conv + 0.18f * (n1 - 0.5f); size = 0.1f + 0.3f * conv; cluster = 0.1f + 0.55f * n2; layer = 0.15f * smooth(0.6f, 0.9f, n3) }
            CONVECTIVE -> {
                cu = 0.3f + 0.45f * conv + 0.12f * (n1 - 0.5f)
                size = 0.18f + 0.82f * kotlin.math.max(conv * 0.55f, late) * (0.75f + 0.25f * n2)
                cluster = 0.35f + 0.45f * n2
                storm = smooth(0.5f, 0.95f, size) * (0.55f + 0.45f * n3)
                showers = 0.1f + 0.75f * storm + 0.15f * conv * n1
                rain = storm * smooth(0.6f, 0.98f, n1) * 0.7f
                overcast = 0.12f * storm; layer = 0.12f * n3
            }
            STORMY -> {
                cu = 0.5f + 0.3f * conv; size = 0.62f + 0.38f * n2; cluster = 0.6f
                storm = 0.5f + 0.5f * n1; overcast = 0.2f + 0.4f * n3; layer = 0.25f * n2
                rain = smooth(0.3f, 0.9f, n2) * 0.9f; showers = 0.85f
            }
            LAYERED -> {
                layer = 0.5f + 0.4f * n1 * (1 - 0.5f * conv); cu = 0.12f + 0.35f * conv; size = 0.18f + 0.22f * conv
                cluster = 0.5f; overcast = 0.12f * smooth(0.5f, 0.9f, n3); showers = 0.12f * n2
            }
            OVERCAST -> {
                overcast = 0.6f + 0.4f * n1; layer = 0.3f + 0.35f * n2; cu = 0.12f + 0.1f * conv; size = 0.3f
                rain = 0.35f * smooth(0.6f, 0.9f, n3); showers = 0.25f
            }
            RAINY -> {
                overcast = 0.78f + 0.22f * n1; layer = 0.4f; cu = 0.22f; size = 0.45f; cluster = 0.5f
                rain = smooth(0.2f, 0.8f, n2); showers = 0.65f; storm = 0.3f * smooth(0.7f, 0.95f, n3)
            }
            else -> { // MIXED
                cu = 0.4f + 0.3f * conv + 0.15f * (n1 - 0.5f); layer = 0.3f + 0.35f * n2; cluster = 0.55f + 0.35f * n3
                size = 0.28f + 0.35f * conv; showers = 0.1f + 0.35f * conv * n2; overcast = 0.08f
            }
        }
        // By night heaped clouds melt away (storms linger into the evening).
        cu *= 0.35f + 0.65f * kotlin.math.max(day, storm)
        val ci = (cirrusBase * (0.55f + 0.9f * n3) * (1 - overcast)).coerceIn(0f, 1f)
        fun c(x: Float) = x.coerceIn(0f, 1f)
        return State(c(cu), c(size), c(cluster), c(layer), c(overcast), c(storm), c(rain), c(showers), ci, h)
    }

    companion object {
        const val CLEAR = 0; const val FAIR = 1; const val CONVECTIVE = 2; const val STORMY = 3
        const val LAYERED = 4; const val OVERCAST = 5; const val RAINY = 6; const val MIXED = 7

        private val WEIGHTS = mapOf(
            //                            clear  fair   conv   storm  layer  overc  rainy  mixed
            Terrain.DUNES to floatArrayOf(0.30f, 0.28f, 0.08f, 0.03f, 0.10f, 0.08f, 0.03f, 0.10f),
            Terrain.HILLS to floatArrayOf(0.08f, 0.24f, 0.16f, 0.06f, 0.12f, 0.12f, 0.08f, 0.14f),
            Terrain.MOUNTAINS to floatArrayOf(0.07f, 0.20f, 0.22f, 0.09f, 0.11f, 0.10f, 0.08f, 0.13f),
            Terrain.SEA to floatArrayOf(0.10f, 0.28f, 0.12f, 0.05f, 0.13f, 0.10f, 0.08f, 0.14f),
        )

        /** The weather of the day with [seed] (a day at a place) over [terrain]. */
        fun of(seed: Long, terrain: Terrain): Weather {
            val r = java.util.Random(seed xor 0x2545F4914F6CDD1DL)
            val w = WEIGHTS.getValue(terrain)
            var pick = r.nextFloat() * w.sum(); var type = 0
            while (type < w.size - 1 && pick > w[type]) { pick -= w[type]; type++ }
            return of(type, r)
        }

        /** A day of the given character (tests, the design bench). */
        fun of(type: Int, r: java.util.Random = java.util.Random(7)): Weather {
            val sx = r.nextFloat() * 40f; val sy = r.nextFloat() * 40f
            val ci = if (r.nextFloat() < 0.3f) 0.05f else 0.15f + 0.55f * r.nextFloat()
            return Weather(type, (sx * 1000f).toInt() * 31 + (sy * 1000f).toInt(), sx, sy, if (type == CLEAR) ci * 0.6f else ci)
        }
    }
}

internal class SkyLook(
    /** The Sun's true elevation (degrees) and where it is drawn (px). */
    val elevation: Double, val sunX: Float, val sunY: Float,
    /** Direction of the drawn Sun, for where its glow sits. */
    val sunAzimuth: Double, val drawAltitude: Double,
    val night: Float, val milkyWay: Float, val mist: Float,
    val moonX: Float, val moonY: Float, val moonVisibility: Float,
    /** Direction of sunlight on the moon in its own frame (x right, y down, z towards us): its phase. */
    val moonLight: FloatArray,
    /** How much of the moon is lit, 0 (new) … 1 (full): how much light it gives at night (0.9.8). */
    val moonIllumination: Float = 0f,
) {
    companion object {
        fun at(scene: SkyScene, view: SkyView, display: Double): SkyLook {
            val g = view.geometry
            val el = scene.elevationAt(display)
            val sunX = g.x(display); val sunY = g.y(el, display)
            val drawAlt = if (el > 0) view.altitudeAt(sunY) else el
            val night = Atmosphere.night(el).toFloat()
            // The moon rides the Sun's own path, as far behind it as it is old.
            val age = ((scene.hijriDay - 1).coerceIn(0, 29) + 0.5) / 29.53
            val moonF = ((display - age) % 1.0 + 1.0) % 1.0
            val moonEl = scene.elevationAt(moonF)
            val moonVis = if (moonEl > 2.0) ((0.35f + 0.65f * night).coerceAtMost(1f) * smooth(2f, 8f, moonEl.toFloat())) else 0f
            val mx = g.x(moonF); val my = g.y(moonEl, moonF)
            val phase = 2 * PI * (((scene.hijriDay - 1).coerceIn(0, 29) + 1.0) / 29.53)
            val pa = PI - phase
            var lx = sunX - mx; var ly = sunY - my
            val len = sqrt(lx * lx + ly * ly).coerceAtLeast(1f); lx /= len; ly /= len
            val moonLight = floatArrayOf((lx * sin(pa)).toFloat(), (ly * sin(pa)).toFloat(), cos(pa).toFloat())
            val evening = scene.eveningAt(display)
            val e = el.toFloat()
            val mist = if (!evening) 0.85f * smooth(0f, 1f, 1f - abs(e + 1f) / 13f) + 0.18f * night else 0.22f * night + 0.12f * smooth(0f, 1f, 1f - abs(e - 1f) / 10f)
            val illum = ((1 - cos(phase)) / 2).toFloat()
            return SkyLook(el, sunX, sunY, view.azimuthAt(sunX), drawAlt * PI / 180, night, smooth(0.55f, 1f, night), mist, mx, my, moonVis, moonLight, illum)
        }

        fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    }
}

/** The atmosphere table and light for a moment, computed off the main thread and handed to the renderers. */
internal class SkyLight(val lut: IntArray, val light: Atmosphere.Light, val key: SkyLight.Key) {
    data class Key(val elevation: Int, val azimuth: Int, val drawAltitude: Int, val fov: Int, val capAltitude: Int)

    companion object {
        /** Quantised so that the table is recomputed only when the sky would visibly change. */
        fun key(look: SkyLook, view: SkyView, capAlt: Double) = Key(
            (look.elevation * 50).toInt(), (look.sunAzimuth * 500).toInt(), (look.drawAltitude * 500).toInt(), (view.fov * 100).toInt(), capAlt.toInt(),
        )

        /** The last one computed: coming back to the home tab shows it at once. */
        @Volatile private var last: SkyLight? = null

        fun compute(k: Key): SkyLight {
            last?.let { if (it.key == k) return it }
            val lut = IntArray(Atmosphere.NX * Atmosphere.NY)
            val light = Atmosphere.frame(k.elevation / 50.0, k.azimuth / 500.0, k.drawAltitude / 500.0, k.fov / 100.0, k.capAltitude.toDouble(), lut)
            return SkyLight(lut, light, k).also { last = it }
        }
    }
}

