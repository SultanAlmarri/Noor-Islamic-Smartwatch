package com.noor.app.sky

import com.noor.app.ui.today.CloudField
import com.noor.app.ui.today.NightSky
import com.noor.app.ui.today.SkyLook
import com.noor.app.ui.today.SkyScene
import com.noor.app.ui.today.SkyView
import com.noor.app.ui.today.Weather
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The weather and the season of the real sky at one moment ("Showcase": invented, but always something the real
 * atmosphere does, and chosen to show the sky at its best). Every value 0..1 unless said otherwise.
 */
internal class SkyState(
    /** Low clouds: how much of them, and their kind (0 sheet .. 0.4 heaped .. 0.7 towers .. 1 thunderstorm). */
    val cover: Float, val kind: Float,
    /** Their base (km) and how they gather into fields. */
    val base: Float, val cluster: Float,
    /** Middle clouds (altocumulus): how much, and their form (0 flocks .. 0.5 rows .. 1 a veil). */
    val mid: Float, val midForm: Float,
    /** High ice clouds: streaks, and a veil (which rings the Sun and the moon). */
    val cirrus: Float, val veil: Float,
    val storm: Float, val showers: Float, val rain: Float,
    /** The air: 1 = clean; more = haze or dust. Mist lying on the ground; dust blowing. */
    val haze: Float, val mist: Float, val dust: Float,
    val wind: Float,
    /** The land's season as its scenery understands it (see each land_*.glsl), snow lying, the ground wet. */
    val season: Float, val snow: Float, val wet: Float,
    val aurora: Float,
    val waterRough: Float,
)

/**
 * Directs the sky (Showcase weather): a character for each day at each place from [Weather] (clear, fair, convective,
 * stormy, layered, overcast, rainy, mixed), weighted by scenery and season, evolving hour by hour, then arranged the
 * way a landscape photographer would hope to find it:
 *
 * - at sunrise and sunset there is cloud to catch the colour (middle and high cloud lit from below), and the low
 *   cloud leaves the Sun room;
 * - deep night is mostly clear, for the stars;
 * - drama (towers, storms, rain, dust) comes now and then, not every day;
 * - everything follows the season of the user's hemisphere: hazy desert summers, snow in the northern forest's
 *   winter, the savanna green after its rains, the frozen sea open in summer.
 *
 * Pure Kotlin, tested (SkyDirectorTest).
 */
internal object SkyDirector {
    //                                            clear  fair   conv   storm  layer  overc  rainy  mixed
    private val DUNES = floatArrayOf(0.30f, 0.30f, 0.08f, 0.03f, 0.13f, 0.03f, 0.02f, 0.11f)
    private val FOREST = floatArrayOf(0.08f, 0.21f, 0.10f, 0.05f, 0.18f, 0.15f, 0.10f, 0.13f)
    private val ARCTIC = floatArrayOf(0.24f, 0.22f, 0.04f, 0.02f, 0.22f, 0.11f, 0.03f, 0.12f)
    private val OCEAN = floatArrayOf(0.10f, 0.28f, 0.15f, 0.07f, 0.14f, 0.07f, 0.06f, 0.13f)
    private val SAVANNA_DRY = floatArrayOf(0.20f, 0.35f, 0.10f, 0.03f, 0.14f, 0.04f, 0.02f, 0.12f)
    private val SAVANNA_WET = floatArrayOf(0.05f, 0.22f, 0.28f, 0.14f, 0.08f, 0.06f, 0.07f, 0.10f)
    private val CANYON = floatArrayOf(0.24f, 0.28f, 0.15f, 0.06f, 0.12f, 0.03f, 0.02f, 0.10f)

    /** How far through the year a moment is, from midwinter (0) through midsummer (0.5), for the user's hemisphere. */
    fun yearPhase(at: Instant, southern: Boolean): Float {
        val day = at.atOffset(ZoneOffset.UTC).dayOfYear
        val n = ((day + 10) % 365) / 365f
        return if (southern) (n + 0.5f) % 1f else n
    }

    /** 0 in midwinter .. 1 in midsummer. */
    fun summer(year: Float): Float = (0.5f - 0.5f * cos(2 * PI * year).toFloat())

    private fun weights(biome: Biome, summer: Float): FloatArray = when (biome) {
        Biome.DUNES -> DUNES
        Biome.RAINFOREST -> FOREST
        Biome.ARCTIC -> ARCTIC
        Biome.OCEAN -> OCEAN
        Biome.SAVANNA -> FloatArray(8) { SAVANNA_DRY[it] * (1 - summer) + SAVANNA_WET[it] * summer }
        Biome.CANYON -> CANYON
    }

    /** The day's weather for a scenery: a [Weather] of the kind drawn for this day and place. */
    fun weather(seed: Long, biome: Biome, year: Float): Weather {
        // (Scrambled first: java.util.Random gives neighbouring seeds, which neighbouring days are, nearly the same
        // first numbers, and every day of a week would get the same kind of weather.)
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        val r = java.util.Random(z xor (z ushr 31))
        val w = weights(biome, summer(year))
        var pick = r.nextFloat() * w.sum(); var type = 0
        while (type < w.size - 1 && pick > w[type]) { pick -= w[type]; type++ }
        return Weather.of(type, r)
    }

    private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /**
     * The sky at [fraction] of the day with the Sun at [sunEl] degrees: [w] is the day's weather, [year] the season
     * (see [yearPhase]), [mistBase] the dawn mist the sky already works out from the Sun's height.
     */
    fun at(biome: Biome, w: Weather, fraction: Double, sunEl: Double, year: Float, mistBase: Float): SkyState {
        val s = w.at(fraction)
        val summer = summer(year)
        val winter = 1f - summer
        val el = sunEl.toFloat()
        val seed = w.seed
        fun day(k: Int) = CloudField.hash(seed, 7, k)                 // a number for the whole day
        val low = smooth(0f, 1f, 1f - abs(el - 1.5f) / 9f)            // the Sun near the horizon: the hour of colour
        val night = 1f - smooth(-14f, -3f, el)

        var cover = (s.cumulus * 0.60f + s.overcast * 0.55f).coerceIn(0f, 0.95f)
        var kind = 0.34f + 0.62f * s.size
        kind = kind + (0.10f - kind) * (s.overcast * (1f - s.cumulus)).coerceIn(0f, 1f)
        var mid = s.layer
        var cirrus = s.cirrus
        val veil = if (day(1) < 0.22f) 0.18f + 0.35f * day(2) else 0f
        // The hour of colour: something for the low Sun to light from beneath, and room for the Sun itself (unless the
        // day is overcast; eased in, so nothing switches).
        val colour = low * (1f - smooth(0.35f, 0.6f, s.overcast))
        mid = max(mid, colour * (0.30f + 0.35f * day(3)))
        cirrus = max(cirrus, colour * (0.30f + 0.3f * day(4)))
        cover = cover + (min(cover, 0.42f) - cover) * colour
        // Once the Sun has gone, the day's heaped clouds sink and thin (the ground no longer feeds them): the afterglow
        // belongs to the middle and high clouds, which are still in sunlight.
        val after = smooth(-1f, -7f, el) * (1f - s.storm) * (1f - smooth(0.35f, 0.6f, s.overcast))
        cover *= 1f - 0.45f * after
        kind += (min(kind, 0.42f) - kind) * after
        // Deep night belongs to the stars, unless a storm is on.
        val calm = night * (1f - s.storm)
        cover *= 1f - 0.55f * calm
        mid *= 1f - 0.6f * calm
        cirrus *= 1f - 0.5f * calm

        var haze = 1f; var mist = 0f; var dust = 0f; var snow = 0f; var season = summer; var aurora = 0f
        var rain = s.rain; var showers = s.showers; var storm = s.storm
        var rough = biome.waterRough
        val windy = (0.12f + 0.5f * day(5) + 0.4f * storm).coerceIn(0f, 1f)
        when (biome) {
            Biome.DUNES -> {
                // Summer is hazy and white-skied; winter crisp. Rain is rare: a storm day raises dust instead.
                haze = 1.05f + 0.9f * summer + 0.5f * day(6) * summer
                dust = (storm * 0.7f + rain * 0.5f).coerceIn(0f, 0.8f)
                rain *= 0.25f; showers *= 0.5f
                kind = min(kind, 0.80f)
            }
            Biome.RAINFOREST -> {
                haze = 1.25f
                mist = (mistBase * 1.1f + 0.25f * s.overcast + 0.15f).coerceIn(0f, 1f)
                snow = smooth(0.62f, 0.9f, winter)
                season = year
                cover = max(cover, 0.12f)
            }
            Biome.ARCTIC -> {
                haze = 0.85f
                mist = 0.12f + 0.25f * mistBase
                snow = 1f
                aurora = night * (0.35f + 0.65f * day(8)) * (0.55f + 0.45f * winter) * (1f - 0.7f * s.overcast)
                kind = min(kind, 0.55f)
                rain = 0f
            }
            Biome.OCEAN -> {
                haze = 1.15f
                mist = 0.10f * mistBase
                rough = (0.34f + 0.3f * windy + 0.3f * winter * day(9) + 0.3f * storm).coerceIn(0.15f, 1f)
            }
            Biome.SAVANNA -> {
                // Green after the rains (summer), gold and dusty in the dry season.
                season = smooth(0.25f, 0.75f, summer)
                haze = 1.1f + 0.6f * (1f - season)
                mist = 0.22f * mistBase
                dust = 0.15f * (1f - season) * windy
            }
            Biome.CANYON -> {
                haze = 0.9f + 0.2f * summer
                snow = smooth(0.72f, 0.95f, winter) * (0.5f + 0.5f * day(10))
                // Summer afternoons build thunderstorms over the mesas.
                storm = max(storm, 0f)
            }
        }
        return SkyState(
            cover = cover.coerceIn(0f, 0.95f), kind = kind.coerceIn(0.05f, 1f), base = 1.25f + 0.5f * day(11), cluster = s.cluster,
            mid = mid.coerceIn(0f, 1f), midForm = (0.15f + 0.6f * day(12) + 0.35f * s.overcast).coerceIn(0f, 1f),
            cirrus = cirrus.coerceIn(0f, 1f), veil = veil * (1f - s.overcast), storm = storm, showers = showers, rain = rain,
            haze = haze, mist = mist, dust = dust, wind = windy, season = season, snow = snow,
            // (Where snow lies, what falls is snow: the ground is not wet.)
            wet = smooth(0.15f, 0.5f, rain) * (1f - smooth(0.4f, 0.6f, snow)),
            aurora = aurora, waterRough = rough,
        )
    }
}

/**
 * Everything one picture of the real sky needs, worked out on the main thread from the home screen's scene and handed
 * to the renderer: where the camera looks, where the Sun and the moon are drawn and where their light comes from, the
 * weather, the land's season, where the text and the glass are.
 *
 * The Sun is drawn where the day's path puts it on the screen ([SkyLook]); the direction that corresponds to through
 * the camera lights the scene, so shadows and the silver linings of clouds agree with the Sun that is seen. The
 * colour of its light always comes from its true elevation.
 */
internal class RealSkyFrame(
    val biome: Biome, val state: SkyState,
    val focalPx: Float, val horizon: Float, val flip: Boolean,
    val sunDir: FloatArray, val lightDir: FloatArray, val sunTrue: FloatArray, val sunEl: Float,
    val moon: FloatArray, val moonLit: FloatArray, val night: Float,
    val cel: Array<FloatArray>,
    val hour: Float, val seed: Float,
    val textBottom: Float, val panelTop: Float, val panelRadius: Float, val lightGlass: Boolean, val tint: FloatArray,
    /** 0..1 while the scene comes in (the opening); 1 afterwards. */
    val fade: Float,
    /** The Sun is being dragged or the opening is running: every frame counts. */
    val busy: Boolean,
    /** Ambient motion is off ("Remove animations"): the clouds stand still. */
    val still: Boolean,
    /** Seconds added to the clock (the opening runs the clouds fast). */
    val lapse: Float,
) {
    val haze: Float get() = state.haze

    fun lightKey(): String = "${(sunEl * 40).toInt()} ${(sunDir[0] * 2000).toInt()} ${(sunDir[1] * 2000).toInt()} ${(moon[0] * 500).toInt()} ${(moon[1] * 500).toInt()} " +
        "${(moon[3] * 50).toInt()} ${(night * 100).toInt()} ${(state.haze * 50).toInt()} ${focalPx.toInt()} ${horizon.toInt()} $flip"

    fun landKey(): String = "${biome.id} ${(state.season * 12).toInt()} ${(state.snow * 6).toInt()} ${focalPx.toInt()} ${horizon.toInt()} $flip"

    fun cloudKey(): String = "${biome.id} ${seed.toInt()}"

    /**
     * Writes the uniforms every pass reads; [time] is the renderer's clock, seconds. [gust] (0..1) is a breath of wind
     * from a touch, fading; [travel] how far (km) such breaths have carried the clouds so far.
     */
    fun fill(u: HashMap<String, FloatArray>, ui: HashMap<String, Int>, w: Float, h: Float, time: Float = 0f, gust: Float = 0f, travel: Float = 0f) {
        val t = if (still) 100f else time + lapse
        val s = state
        u["uView"] = floatArrayOf(w, h)
        u["uTime"] = floatArrayOf(t)
        u["uCam"] = floatArrayOf(focalPx, horizon, biome.eye, if (flip) 1f else 0f)
        // Where the Sun rises and sets on the horizon line, as an angle from the middle of the view (the land leaves
        // a gap there): 30.24 % of the width from the middle, through this camera.
        u["uGapAz"] = floatArrayOf(gapAzimuth(w, h, focalPx, horizon))
        u["uSunDir"] = sunDir; u["uLightDir"] = lightDir; u["uSunTrue"] = sunTrue
        u["uSunEl"] = floatArrayOf(sunEl)
        u["uMoon"] = moon; u["uMoonLit"] = moonLit
        u["uNight"] = floatArrayOf(night)
        u["uHaze"] = floatArrayOf(s.haze)
        u["uCloudA"] = floatArrayOf(s.cover, s.kind, s.base, 30f)
        u["uCloudB"] = floatArrayOf(s.mid, s.midForm, s.cluster, s.storm)
        u["uCloudC"] = floatArrayOf(seed, s.cirrus, s.veil, s.showers)
        val wl = 0.006f + 0.016f * s.wind; val wm = 0.012f + 0.02f * s.wind
        u["uWind"] = floatArrayOf(t * wl + hour * 9f + travel, t * wl * 0.25f + travel * 0.2f, t * wm + hour * 14f + travel * 1.4f, t * wm * 0.2f)
        // Lightning: a flash or two every so often while a storm is on.
        val flash = if (s.storm > 0.3f && !still) {
            val k = (t * 0.9f).toInt()
            val ph = t * 0.9f - k
            if (CloudField.hash(seed.toInt(), k, 91) < 0.22f * s.storm) (if (ph < 0.08f || (ph > 0.16f && ph < 0.22f)) 1f else 0f) else 0f
        } else 0f
        u["uSky"] = floatArrayOf(s.aurora, 0f, flash, 0f)
        u["uLand"] = floatArrayOf(biome.ordinal.toFloat(), s.season, s.snow, s.wet)
        u["uAir"] = floatArrayOf(s.mist, s.rain, (s.wind + 0.8f * gust).coerceAtMost(1f), s.dust)
        u["uWater"] = floatArrayOf((s.waterRough + 0.22f * gust).coerceAtMost(1f), biome.waterClear, 0f, 0f)
        u["uTouch"] = floatArrayOf(0f, 0f, 1000f, 0f)
        u["uText"] = floatArrayOf(textBottom, 0f, 0f, 0f)
        u["uPanel"] = floatArrayOf(panelTop, panelRadius, if (lightGlass) 0.50f else 0.50f, if (lightGlass) 1f else 0f)
        u["uPanelTint"] = tint
        u["uFade"] = floatArrayOf(fade)
        u["uCelX"] = cel[0]; u["uCelY"] = cel[1]; u["uCelZ"] = cel[2]; u["uGalN"] = cel[3]; u["uGalC"] = cel[4]
        ui["uBiome"] = biome.ordinal
    }

    companion object {
        private const val DEG = PI / 180

        /** The direction seen at a point of the layer (px from its top-left corner). Mirrors `viewRay` in common.glsl. */
        fun ray(px: Float, py: Float, w: Float, h: Float, focalPx: Float, horizon: Float, flip: Boolean): FloatArray {
            var x = px - 0.5f * w
            if (flip) x = -x
            val y = 0.5f * h - py
            val len = sqrt(x * x + y * y + focalPx * focalPx)
            val dx = x / len; val dy = y / len; val dz = focalPx / len
            val pitch = atan((horizon - 0.5f * h) / focalPx)
            val c = cos(pitch); val s = sin(pitch)
            return floatArrayOf(dx, dy * c + dz * s, dz * c - dy * s)
        }

        /** The angle from straight ahead at which the Sun rises and sets (see `sunGap` in land.glsl). */
        fun gapAzimuth(w: Float, h: Float, focalPx: Float, horizon: Float): Float =
            atan(0.3024f * w / sqrt(focalPx * focalPx + (horizon - 0.5f * h) * (horizon - 0.5f * h)))

        fun dirAzEl(az: Double, el: Double) = floatArrayOf((cos(el) * sin(az)).toFloat(), sin(el).toFloat(), (cos(el) * cos(az)).toFloat())

        /**
         * The frame for a scene: [view] and [look] are the home screen's own (where the Sun and the moon are drawn);
         * [w] × [h] is the layer the renderer fills (px).
         */
        fun of(
            scene: SkyScene, view: SkyView, look: SkyLook, biome: Biome, weather: Weather, year: Float, display: Double, shownAt: Instant,
            w: Float, h: Float, textBottom: Float, panelTop: Float, panelRadius: Float, lightGlass: Boolean, tint: FloatArray,
            fade: Float, busy: Boolean, still: Boolean, lapse: Float,
        ): RealSkyFrame {
            val focalPx = biome.focal * w
            val horizon = view.horizonY
            val flip = view.rtl
            val el = look.elevation
            val seen = ray(look.sunX, look.sunY, w, h, focalPx, horizon, flip)
            val az = atan2(seen[0].toDouble(), seen[2].toDouble())
            val seenEl = asin(seen[1].toDouble().coerceIn(-1.0, 1.0)) / DEG
            // Below the horizon the Sun is where it truly is; above it, where the path draws it.
            val sunDir = if (el > 0) seen else dirAzEl(az, el * DEG)
            // Its light falls from higher than it is drawn at midday (the path is drawn low so that it fits the sky).
            val lightEl = if (el <= 0) el else max(seenEl, min(el, 50.0))
            val moonUp = look.moonVisibility > 0f
            val moonDir = if (moonUp) ray(look.moonX, look.moonY, w, h, focalPx, horizon, flip) else floatArrayOf(0f, -0.2f, 0.98f)
            // By day it falls from the side of the Sun's half of the sky (where it rose in the morning, where it will set
            // in the afternoon), so the land and the clouds are modelled by it; as the Sun nears the horizon the light
            // comes from the Sun as it is seen.
            val half = ((display - scene.noonFraction) / 0.07).coerceIn(-1.0, 1.0) * (if (flip) -1 else 1)
            val high = ((el - 8.0) / 32.0).coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
            val lightAz = az + half * 62 * DEG * high
            val light = if (el >= -6.0) dirAzEl(lightAz, lightEl * DEG) else if (moonUp) moonDir else floatArrayOf(0f, 1f, 0f)
            val state = SkyDirector.at(biome, weather, display, el, year, look.mist)
            val fr = NightSky.frame(shownAt, scene.latitude, scene.longitude, flip)
            // The stars are a real, unmirrored sky; the scene's own x runs the other way when it is mirrored.
            fun m(v: FloatArray) = if (flip) floatArrayOf(-v[0], v[1], v[2]) else v
            return RealSkyFrame(
                biome, state, focalPx, horizon, flip, sunDir, light, dirAzEl(az, el * DEG), el.toFloat(),
                floatArrayOf(moonDir[0], moonDir[1], moonDir[2], look.moonIllumination),
                floatArrayOf(look.moonLight[0], look.moonLight[1], look.moonLight[2], look.moonVisibility),
                look.night, arrayOf(m(fr.celX), m(fr.celY), m(fr.celZ), m(fr.galN), m(fr.galC)),
                hour = (display * 24).toFloat(), seed = (weather.seedX + weather.seedY * 0.37f),
                textBottom = textBottom, panelTop = panelTop, panelRadius = panelRadius, lightGlass = lightGlass, tint = tint,
                fade = fade, busy = busy, still = still, lapse = lapse,
            )
        }
    }
}
