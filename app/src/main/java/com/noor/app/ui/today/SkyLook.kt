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
    val geometry = SkyGeometry(scene, w, horizonY, arcTopY, h * layout.ground, rtl)
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
 * The weather of a day at a place (0.9.9): which clouds, how many, how much high cirrus, and a seed for their shapes.
 * [regime]: 0 clear, 1 fair-weather cumulus, 2 scattered cumulus, 3 towering cumulus with a thunderstorm far off,
 * 4 altocumulus, 5 stratocumulus, 6 high cloud only. Chosen from the day and the place, more often clear or high over
 * the desert, more cumulus and storms over hills and mountains. [cumulus] is kept for the land's cloud shadows.
 */
internal class Weather(
    val cumulus: Float, val cirrus: Float, val seedX: Float, val seedY: Float,
    val regime: Int = 1, val clouds: Int = 14,
) {
    companion object {
        private val WEIGHTS = mapOf(
            //                 clear fair  scat  tower alto  strato high
            Terrain.DUNES to floatArrayOf(0.20f, 0.26f, 0.10f, 0.05f, 0.14f, 0.03f, 0.22f),
            Terrain.HILLS to floatArrayOf(0.06f, 0.24f, 0.24f, 0.12f, 0.10f, 0.14f, 0.10f),
            Terrain.MOUNTAINS to floatArrayOf(0.05f, 0.20f, 0.24f, 0.20f, 0.10f, 0.11f, 0.10f),
            Terrain.SEA to floatArrayOf(0.05f, 0.30f, 0.24f, 0.10f, 0.08f, 0.15f, 0.08f),
        )

        fun of(seed: Long, terrain: Terrain): Weather {
            val r = java.util.Random(seed xor 0x2545F4914F6CDD1DL)
            val w = WEIGHTS.getValue(terrain)
            var pick = r.nextFloat() * w.sum(); var regime = 0
            while (regime < w.size - 1 && pick > w[regime]) { pick -= w[regime]; regime++ }
            return of(regime, r)
        }

        fun of(regime: Int, r: java.util.Random = java.util.Random(7)): Weather {
            val n = when (regime) { 0, 6 -> 0; 1 -> 28 + r.nextInt(9); 2 -> 22 + r.nextInt(9); 3 -> 18 + r.nextInt(8); 4 -> 14 + r.nextInt(6); else -> 18 + r.nextInt(8) }
            val cover = when (regime) { 0, 6 -> 0f; 1 -> 0.28f; 2 -> 0.45f; 3 -> 0.5f; 4 -> 0.15f; else -> 0.55f }
            val ci = when (regime) { 6 -> 0.75f + 0.25f * r.nextFloat(); 0 -> 0.15f * r.nextFloat(); 4 -> 0.3f; else -> if (r.nextFloat() < 0.35f) 0f else 0.15f + 0.5f * r.nextFloat() }
            return Weather(cover, ci, r.nextFloat() * 40f, r.nextFloat() * 40f, regime, n)
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
            val sunX = g.x(display); val sunY = g.y(el)
            val drawAlt = if (el > 0) view.altitudeAt(sunY) else el
            val night = Atmosphere.night(el).toFloat()
            // The moon rides the Sun's own path, as far behind it as it is old.
            val age = ((scene.hijriDay - 1).coerceIn(0, 29) + 0.5) / 29.53
            val moonF = ((display - age) % 1.0 + 1.0) % 1.0
            val moonEl = scene.elevationAt(moonF)
            val moonVis = if (moonEl > 2.0) ((0.35f + 0.65f * night).coerceAtMost(1f) * smooth(2f, 8f, moonEl.toFloat())) else 0f
            val mx = g.x(moonF); val my = g.y(moonEl)
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

