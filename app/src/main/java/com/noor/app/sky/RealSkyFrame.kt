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
    /**
     * The clouds: for each level (0 high, 1 middle, 2 low) the baked sets on show (assets/sky2/clouds/<id>), where
     * each stands in its drift across the view and how much of it is seen.
     */
    val clouds: List<List<CloudLeg>> = listOf(emptyList(), emptyList(), emptyList()),
    /** How far the lower clouds stand apart round the Sun (0 it is covered .. 1 it is clear); how strong a halo the ice above draws. */
    val sunPart: Float = 1f, val halo: Float = 0f,
    /**
     * How overcast the sky on show is (the land's light then comes evenly from the whole grey sky), and how much of
     * the Sun's light the sky as a whole takes from the land: an overcast, or a cloud standing over the Sun just now.
     */
    val overcast: Float = 0f, val shade: Float = 0f,
)

/**
 * A set of clouds on show: a wide window of the sky (tools/scenery/skybake.py) drifting across the view. [offset]:
 * how far right of the set's middle the view's middle stands, degrees; [weight]: how much of it is seen (it comes in
 * over the set before it and gives way to the next, slowly).
 */
internal class CloudLeg(val set: String, val offset: Float, val weight: Float)

/**
 * Which of the rendered Suns of a baked picture the light of a moment lies between, and how much of each (see
 * tools/scenery/bake.py and lit.frag). [names] are five pictures (two on the left, two on the right, the high one in
 * front), [w] their weights, [glow] the weights of the two glows (left, right), [dir] where that light falls from
 * (the scene's own frame: x to the right of the unmirrored scene, y up, z into the picture).
 */
internal class LightWeights(val names: Array<String>, val w: FloatArray, val glow: FloatArray, val dir: FloatArray) {
    /** The same light for a picture that is shown mirrored (right to left): left and right change places. */
    fun mirrored(): LightWeights = LightWeights(
        Array(names.size) { val n = names[it]; if (n == "top") n else (if (n[0] == 'l') "r" else "l") + n.substring(1) },
        w, floatArrayOf(glow[1], glow[0]), dir,
    )

    companion object {
        /** Elevations (degrees) of the Suns the land and the clouds are rendered under: bake.SUN_ELEVATIONS. */
        val SUN_EL = doubleArrayOf(0.7, 2.5, 5.0, 9.0, 14.0, 22.0, 34.0, 50.0)
        /** ... and of the Suns under the horizon that clouds have as well: skybake.UNDER. */
        val UNDER_EL = doubleArrayOf(-1.5, -4.0)
        private const val DEG = PI / 180

        private fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

        /** Where a rendered Sun of elevation [e] stands to the side of the view's middle (degrees): bake.sun_azimuth. */
        fun sunAzimuth(e: Double): Double = 14.7 + 62.0 * smooth(8.0, 40.0, e)

        /**
         * [el]: the light's elevation (degrees); [side]: -1 light from the left (morning) .. +1 from the right;
         * [under]: the picture has Suns under the horizon (clouds); [glow]: how strong the glow round a low Sun is, 0..1.
         */
        fun of(el: Double, side: Double, under: Boolean, glow: Float): LightWeights {
            val wr = smooth(-1.0, 1.0, side); val wl = 1 - wr
            val a: String; val b: String; val f: Double; val ea: Double; val eb: Double
            var top = 0.0
            if (el >= SUN_EL[0] || !under) {
                val e = el.coerceIn(SUN_EL[0], SUN_EL[SUN_EL.size - 1])
                var i = 0
                while (i < SUN_EL.size - 2 && e > SUN_EL[i + 1]) i++
                f = (e - SUN_EL[i]) / (SUN_EL[i + 1] - SUN_EL[i])
                a = "$i"; b = "${i + 1}"; ea = SUN_EL[i]; eb = SUN_EL[i + 1]
                top = 4 * wl * wr * smooth(24.0, 46.0, e) * 0.85
            } else if (el >= UNDER_EL[0]) {
                f = (el - UNDER_EL[0]) / (SUN_EL[0] - UNDER_EL[0]); a = "u0"; b = "0"; ea = UNDER_EL[0]; eb = SUN_EL[0]
            } else {
                val e = max(el, UNDER_EL[1])
                f = (e - UNDER_EL[1]) / (UNDER_EL[0] - UNDER_EL[1]); a = "u1"; b = "u0"; ea = UNDER_EL[1]; eb = UNDER_EL[0]
            }
            val names = arrayOf("l$a", "l$b", "r$a", "r$b", "top")
            val w = doubleArrayOf(wl * (1 - top) * (1 - f), wl * (1 - top) * f, wr * (1 - top) * (1 - f), wr * (1 - top) * f, top)
            val dirs = arrayOf(
                RealSkyFrame.dirAzEl(-sunAzimuth(ea) * DEG, ea * DEG), RealSkyFrame.dirAzEl(-sunAzimuth(eb) * DEG, eb * DEG),
                RealSkyFrame.dirAzEl(sunAzimuth(ea) * DEG, ea * DEG), RealSkyFrame.dirAzEl(sunAzimuth(eb) * DEG, eb * DEG),
                RealSkyFrame.dirAzEl(0.0, 68 * DEG),
            )
            val d = DoubleArray(3)
            for (k in 0 until 5) for (c in 0 until 3) d[c] += dirs[k][c] * w[k]
            // Below the lowest rendered Sun the picture keeps that shading; the light itself sinks with the Sun.
            if (el < SUN_EL[0]) d[1] = sin(el * DEG)
            val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).coerceAtLeast(1e-6)
            return LightWeights(
                names, FloatArray(5) { w[it].toFloat() }, floatArrayOf((wl * glow).toFloat(), (wr * glow).toFloat()),
                floatArrayOf((d[0] / len).toFloat(), (d[1] / len).toFloat(), (d[2] / len).toFloat()),
            )
        }
    }
}

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
    private val MEADOW = floatArrayOf(0.14f, 0.32f, 0.15f, 0.04f, 0.14f, 0.05f, 0.04f, 0.12f)
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
        Biome.MEADOW -> MEADOW
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

    /** How long a set of each level (0 high, 1 middle, 2 low) is on its way across the view, hours. */
    private val LEG_HOURS = floatArrayOf(3.1f, 2.3f, 1.6f)
    /** How far to either side of a set's middle the view's middle travels, degrees. */
    const val DRIFT = 20.5f
    /** Every set the director can choose, by level (0 high, 1 middle, 2 low). */
    val SETS = listOf(
        listOf("hi_wisps", "hi_sprays", "hi_veil", "hi_ripples", "hi_mixed"),
        listOf("mi_patches", "mi_bands", "mi_sheet", "mi_veil"),
        listOf("lo_few", "lo_scattered", "lo_towers", "lo_broken", "lo_front", "lo_storm", "lo_overcast_breaks", "lo_overcast"),
    )

    /** How far a set closes the sky over the land (sets not named leave it open). */
    private val CLOSES = mapOf(
        "lo_overcast" to 1f, "lo_overcast_breaks" to 0.75f, "lo_storm" to 0.6f, "lo_broken" to 0.45f, "lo_front" to 0.25f,
        "mi_sheet" to 0.4f, "mi_veil" to 0.25f,
    )

    private fun hourNoise(seed: Int, h: Float, period: Float, k: Int): Float {
        val x = h / period; val i = kotlin.math.floor(x).toInt(); val f = x - i; val u = f * f * (3 - 2 * f)
        return CloudField.hash(seed, i, k) * (1 - u) + CloudField.hash(seed, i + 1, k) * u
    }

    /**
     * The set a level shows on leg [k] of the day: chosen from the weather in the middle of that leg, with a throw of
     * the dice, so that the same weather does not always wear the same sky. Null: that level is empty for the leg.
     */
    fun setFor(w: Weather, level: Int, k: Int, biome: Biome): String? {
        val d = LEG_HOURS[level]
        val mid = (((k + 0.5f) * d - CloudField.hash(w.seed, 40 + level, 0) * d) / 24f).coerceIn(0f, 1f)
        val s = w.at(mid.toDouble())
        val r = CloudField.hash(w.seed, 50 + level, k)
        val cover = (s.cumulus * 0.60f + s.overcast * 0.55f).coerceIn(0f, 0.95f)
        return when (level) {
            2 -> when {
                s.overcast > 0.78f -> "lo_overcast"
                s.overcast > 0.5f -> "lo_overcast_breaks"
                s.storm > 0.5f && biome != Biome.ARCTIC -> "lo_storm"
                cover < 0.12f -> "lo_few"
                cover < 0.28f -> if (s.size > 0.5f && r < 0.6f) "lo_towers" else if (r < 0.3f) "lo_few" else "lo_scattered"
                cover < 0.45f -> if (r < 0.34f) "lo_scattered" else if (r < 0.67f) "lo_towers" else "lo_front"
                else -> if (r < 0.6f) "lo_broken" else "lo_front"
            }
            1 -> when {
                s.overcast > 0.6f -> if (r < 0.5f) "mi_veil" else null
                s.layer < 0.10f -> if (r < 0.25f) "mi_patches" else null
                s.layer < 0.40f -> if (r < 0.7f) "mi_patches" else "mi_bands"
                s.layer < 0.65f -> if (r < 0.5f) "mi_bands" else if (r < 0.8f) "mi_patches" else "mi_sheet"
                else -> if (r < 0.5f) "mi_sheet" else if (r < 0.8f) "mi_veil" else "mi_bands"
            }
            else -> when {
                s.overcast > 0.8f -> null
                CloudField.hash(w.seed, 7, 1) < 0.22f && r < 0.7f -> "hi_veil"
                s.cirrus < 0.08f && r < 0.35f -> null
                r < 0.28f -> "hi_wisps"
                r < 0.52f -> "hi_sprays"
                r < 0.72f -> "hi_ripples"
                r < 0.90f -> "hi_mixed"
                else -> "hi_veil"
            }
        }
    }

    /**
     * The sets a level shows at hour [h] of the day, and where each stands. The day is cut into legs (of a different
     * length and start for each level, so that no two levels change together): a set comes in over the last fifth of
     * the leg before its own, drifts across the view through its leg, and gives way over its last fifth. Nothing ever
     * changes at a stroke, and nothing changes because the Sun has reached some height or a prayer has begun.
     */
    fun legs(w: Weather, level: Int, h: Float, biome: Biome): List<CloudLeg> {
        val d = LEG_HOURS[level]
        val x = (h + CloudField.hash(w.seed, 40 + level, 0) * d) / d
        val k = kotlin.math.floor(x).toInt(); val p = x - k
        val dir = if (CloudField.hash(w.seed, 60, 0) < 0.5f) 1f else -1f
        fun leg(k: Int, p: Float, weight: Float): CloudLeg? =
            if (weight <= 0f) null else setFor(w, level, k, biome)?.let { CloudLeg(it, dir * (-DRIFT + 2f * DRIFT * (p + 0.2f) / 1.2f), weight) }
        val change = smooth(0.8f, 1f, p)
        return listOfNotNull(leg(k, p, 1f - change), leg(k + 1, p - 1f, change))
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
        // A sky with nothing in it shows nothing of the day's light: even the clearest day keeps a few small heaped
        // clouds through its warm hours, and a wisp of high cloud.
        val warm = smooth(4f, 14f, el)
        cover = max(cover, warm * (0.27f + 0.07f * day(13)))
        cirrus = max(cirrus, smooth(-2f, 8f, el) * (0.20f + 0.15f * day(14)))
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
            Biome.MEADOW -> {
                // Green hills: soft air, mist in the hollows at dawn, snow only in the depth of winter.
                haze = 1.1f + 0.25f * summer
                mist = 0.35f * mistBase
                snow = smooth(0.80f, 0.97f, winter) * (0.5f + 0.5f * day(10))
                season = year
            }
            Biome.CANYON -> {
                haze = 0.9f + 0.2f * summer
                snow = smooth(0.72f, 0.95f, winter) * (0.5f + 0.5f * day(10))
                // Summer afternoons build thunderstorms over the mesas.
                storm = max(storm, 0f)
            }
        }
        cover = cover.coerceIn(0f, 0.95f); kind = kind.coerceIn(0.05f, 1f)
        mid = mid.coerceIn(0f, 1f); cirrus = cirrus.coerceIn(0f, 1f)
        val midForm = (0.15f + 0.6f * day(12) + 0.35f * s.overcast).coerceIn(0f, 1f)
        val veilNow = veil * (1f - s.overcast)
        // The clouds on show (see [legs]). By night the heaped clouds thin (unless a storm is on) and after sunset they
        // sink: smoothly, with the Sun's height. The day's sets end at midnight: the sky clears for a moment there,
        // so that nothing jumps.
        val h = (fraction * 24.0).toFloat()
        val midnight = smooth(0f, 0.4f, min(h, 24f - h))
        val amounts = floatArrayOf(1f - 0.5f * calm, 1f - 0.6f * calm, (1f - 0.55f * calm) * (1f - 0.45f * after))
        val clouds = List(3) { level -> legs(w, level, h, biome).map { CloudLeg(it.set, it.offset, it.weight * amounts[level] * midnight) } }
        // The Sun and the clouds. The Sun is drawn low in the picture, where far clouds crowd together, though it
        // stands high in the real sky, where gaps are wide: left to the picture it would nearly always be hidden. So
        // whether a cloud is in front of it is the weather's say: clouds pass over it now and then, the more often the
        // more of them there are, and otherwise stand apart round it; under an overcast it is gone.
        val often = (0.10f + 0.85f * (s.cumulus * 0.55f + s.overcast * 0.9f)).coerceIn(0f, 1f)
        val covered = smooth(1f - often - 0.2f, 1f - often + 0.2f, hourNoise(seed, h, 0.75f, 71))
        // (What the sets on show close of the sky: a closed sky hides the Sun whatever the hour's weather says, and
        // the land under it is in its even light, not in sunshine.)
        fun closed(level: Int) = clouds[level].sumOf { (it.weight * (CLOSES[it.set] ?: 0f)).toDouble() }.toFloat().coerceIn(0f, 1f)
        val overcast = 1f - (1f - closed(2)) * (1f - closed(1))
        // (Nor does a thunderhead stand apart for the Sun: where it is in front of it, the Sun is behind it.)
        val full = clouds[2].sumOf { (it.weight * (if (it.set == "lo_overcast" || it.set == "lo_storm") 1f else 0f)).toDouble() }.toFloat().coerceIn(0f, 1f)
        val sunPart = ((1f - covered) * (1f - smooth(0.5f, 0.75f, s.overcast)) * (1f - full)).coerceIn(0f, 1f)
        val shade = max(overcast, 0.6f * (1f - sunPart))
        val halo = clouds[0].sumOf { (it.weight * (if (it.set == "hi_veil") 1f else if (it.set == "hi_mixed") 0.4f else 0f)).toDouble() }.toFloat().coerceIn(0f, 1f)
        return SkyState(
            clouds = clouds, sunPart = sunPart, halo = halo, overcast = overcast, shade = shade,
            cover = cover, kind = kind, base = 1.25f + 0.5f * day(11), cluster = s.cluster,
            mid = mid, midForm = midForm,
            cirrus = cirrus, veil = veilNow, storm = storm, showers = showers, rain = rain,
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
 * The Sun is drawn where the day's path puts it on the screen ([SkyLook]). The land and the clouds are pictures
 * rendered under a set of Suns ([LightWeights]): low Suns stand where the Sun is seen, higher ones swing round to the
 * side, so the scene is modelled by light and shade. The colour of the light always comes from the Sun's true
 * elevation.
 */
internal class RealSkyFrame(
    val biome: Biome, val state: SkyState,
    val focalPx: Float, val horizon: Float, val flip: Boolean,
    val sunDir: FloatArray, val lightDir: FloatArray, val sunTrue: FloatArray, val sunEl: Float,
    /** Which rendered Suns light the land, and the clouds, now (already for the mirrored picture when [flip]). */
    val landLight: LightWeights, val cloudLight: LightWeights,
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

    fun landKey(): String = biome.id

    /** How far the wind has slid a layer of cloud (0 high, 1 middle, 2 low), as a share of the room it has (-1..1). */
    fun slide(layer: Int, time: Float, travel: Float): Float {
        val t = if (still) 0f else time + lapse
        val speed = floatArrayOf(0.55f, 0.8f, 1f)[layer] * (0.5f + state.wind)
        // (There and back, slowly: the picture is only a little wider than the view. A day's drag crosses it twice.)
        val x = hour / 24f * 2.2f * speed + t * 0.0009f * speed + travel * 0.5f + layer * 0.37f + seed * 0.13f
        val ph = x - kotlin.math.floor(x)
        return (if (ph < 0.5f) ph * 4f - 1f else 3f - ph * 4f) * 0.92f
    }

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
        u["uSunDir"] = sunDir; u["uLightDir"] = lightDir; u["uSunTrue"] = sunTrue
        u["uSunEl"] = floatArrayOf(sunEl)
        u["uMoon"] = moon; u["uMoonLit"] = moonLit
        u["uNight"] = floatArrayOf(night)
        u["uHaze"] = floatArrayOf(s.haze)
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
        // Life: by day a flock of birds crosses the sky every few minutes (not in rain, nor under a storm); by night
        // a shooting star now and then. Nothing of either when animations are off.
        val k = (t / BIRDS_EVERY).toInt()
        val into = t - k * BIRDS_EVERY
        val flock = !still && sunEl > 3f && s.rain < 0.15f && s.storm < 0.5f && into < BIRDS_CROSS && CloudField.hash(seed.toInt(), k, 57) < 0.8f
        u["uSunPart"] = floatArrayOf(state.sunPart, state.halo, state.shade, state.overcast)
        u["uBirds"] = if (flock) floatArrayOf(
            into / BIRDS_CROSS, 0.07f + 0.13f * CloudField.hash(seed.toInt(), k, 58), k * 7.31f + seed, if (CloudField.hash(seed.toInt(), k, 59) < 0.5f) 1f else -1f,
        ) else floatArrayOf(-1f, 0f, 0f, 1f)
        val mk = (t / METEOR_EVERY).toInt()
        val mInto = t - mk * METEOR_EVERY
        val star = !still && night > 0.9f && mInto < 0.85f && CloudField.hash(seed.toInt(), mk, 61) < 0.6f
        u["uMeteor"] = if (star) floatArrayOf(
            (mInto / 0.85f).coerceIn(0.001f, 1f), 0.15f + 0.7f * CloudField.hash(seed.toInt(), mk, 62), 0.05f + 0.35f * CloudField.hash(seed.toInt(), mk, 63),
            -1.1f + 2.2f * CloudField.hash(seed.toInt(), mk, 64),
        ) else floatArrayOf(0f, 0f, 0f, 0f)
        u["uText"] = floatArrayOf(textBottom, 0f, 0f, 0f)
        u["uPanel"] = floatArrayOf(panelTop, panelRadius, if (lightGlass) 0.50f else 0.50f, if (lightGlass) 1f else 0f)
        u["uPanelTint"] = tint
        u["uFade"] = floatArrayOf(fade)
        u["uCelX"] = cel[0]; u["uCelY"] = cel[1]; u["uCelZ"] = cel[2]; u["uGalN"] = cel[3]; u["uGalC"] = cel[4]
        ui["uBiome"] = biome.ordinal
    }

    companion object {
        private const val DEG = PI / 180
        /** A flock of birds sets out every so many seconds and takes this long to cross; a shooting star's chance. */
        private const val BIRDS_EVERY = 210f
        private const val BIRDS_CROSS = 75f
        private const val METEOR_EVERY = 38f

        /**
         * The direction seen at a point of the layer (px from its top-left corner). Mirrors `viewRay` in common.glsl:
         * the camera looks level, its lens shifted so that the horizon lies [horizon] px from the top.
         */
        @Suppress("UNUSED_PARAMETER")
        fun ray(px: Float, py: Float, w: Float, h: Float, focalPx: Float, horizon: Float, flip: Boolean): FloatArray {
            var x = px - 0.5f * w
            if (flip) x = -x
            val y = horizon - py
            val len = sqrt(x * x + y * y + focalPx * focalPx)
            return floatArrayOf(x / len, y / len, focalPx / len)
        }

        fun dirAzEl(az: Double, el: Double) = floatArrayOf((cos(el) * sin(az)).toFloat(), sin(el).toFloat(), (cos(el) * cos(az)).toFloat())

        /**
         * The night sky at its finest: the celestial axes (x to right ascension 0, y to 6 h, z to the north pole), the
         * Milky Way's pole and its centre, as directions of the view, turned so that the bright heart of the Milky Way
         * stands in the picture a hand's breadth over the horizon and its band climbs steeply across the sky, as in
         * the photographs people remember. It leans and drifts a little through the night ([hour], 0..24), so the
         * sky is not a still, and keeps to the side of the picture the Moon is not on ([moonBearing]: where the Moon
         * is drawn, degrees right of the middle of the screen; NaN when it is down). The stars keep their true places
         * among themselves; only the whole sky is turned.
         */
        fun showcaseSky(hour: Float, moonBearing: Float = Float.NaN): Array<FloatArray> {
            val s = (((hour + 6f) % 24f) / 12f).coerceIn(0f, 1f).toDouble()          // 18:00 -> 0, 06:00 -> 1
            val aside = if (moonBearing.isNaN()) 0.0 else -8.0 * (moonBearing / 6f).coerceIn(-1f, 1f)
            val az = (-5.0 + 10.0 * s + aside) * DEG
            val el = (11.0 + 4.0 * sin(PI * s)) * DEG
            val lean = (58.0 + 10.0 * s) * DEG
            val c = dirAzEl(az, el).map { it.toDouble() }
            val r = doubleArrayOf(cos(az), 0.0, -sin(az))                               // to the right, level
            // (Upwards at the centre: the upright with its part along the line of sight taken out.)
            val uy = doubleArrayOf(-c[1] * c[0], 1.0 - c[1] * c[1], -c[1] * c[2])
            val ul = sqrt(uy[0] * uy[0] + uy[1] * uy[1] + uy[2] * uy[2])
            val t = DoubleArray(3) { -cos(lean) * r[it] + sin(lean) * uy[it] / ul }      // the band's way: up and to the left
            val n = doubleArrayOf(c[1] * t[2] - c[2] * t[1], c[2] * t[0] - c[0] * t[2], c[0] * t[1] - c[1] * t[0])
            fun cel(ra: Double, dec: Double) = doubleArrayOf(cos(dec * DEG) * cos(ra * DEG), cos(dec * DEG) * sin(ra * DEG), sin(dec * DEG))
            val gc = cel(266.40499, -28.93617); val gn = cel(192.85948, 27.12825)
            val gm = doubleArrayOf(gn[1] * gc[2] - gn[2] * gc[1], gn[2] * gc[0] - gn[0] * gc[2], gn[0] * gc[1] - gn[1] * gc[0])
            fun axis(k: Int) = FloatArray(3) { (c[it] * gc[k] + n[it] * gn[k] + t[it] * gm[k]).toFloat() }
            return arrayOf(axis(0), axis(1), axis(2), FloatArray(3) { n[it].toFloat() }, FloatArray(3) { c[it].toFloat() })
        }

        /**
         * The frame for a scene: [view] and [look] are the home screen's own (where the Sun and the moon are drawn);
         * [w] × [h] is the layer the renderer fills (px).
         */
        fun of(
            scene: SkyScene, view: SkyView, look: SkyLook, biome: Biome, weather: Weather, year: Float, display: Double, shownAt: Instant,
            w: Float, h: Float, textBottom: Float, panelTop: Float, panelRadius: Float, lightGlass: Boolean, tint: FloatArray,
            fade: Float, busy: Boolean, still: Boolean, lapse: Float, trueStars: Boolean = false,
        ): RealSkyFrame {
            val focalPx = biome.focal * w
            val horizon = view.horizonY
            val flip = view.rtl
            val el = look.elevation
            val seen = ray(look.sunX, look.sunY, w, h, focalPx, horizon, flip)
            val az = atan2(seen[0].toDouble(), seen[2].toDouble())
            // Below the horizon the Sun is where it truly is; above it, where the path draws it.
            // (Over a scenery with a skyline of its own the path stands on the land: there the Sun is still drawn above
            // the level horizon for a while after its true height has gone under it.)
            val sunDir = if (el > 0 || seen[1] > sin(el * DEG)) seen else dirAzEl(az, el * DEG)
            val moonUp = look.moonVisibility > 0f
            val moonDir = if (moonUp) ray(look.moonX, look.moonY, w, h, focalPx, horizon, flip) else floatArrayOf(0f, -0.2f, 0.98f)
            // By day the light falls from the side of the Sun's half of the sky (where it rose in the morning, where it
            // will set in the afternoon); as the Sun nears the horizon it comes from the Sun as it is seen. Deep in the
            // night the Moon lights the scene the same way, from where it stands.
            val moonLight = el < -6.0
            val side = if (moonLight) ((look.moonX - 0.5f * w) / (0.2f * w)).toDouble().coerceIn(-1.0, 1.0)
            else ((display - scene.noonFraction) / 0.07).coerceIn(-1.0, 1.0) * (if (flip) -1 else 1)
            val moonEl = max(asin(moonDir[1].toDouble().coerceIn(-1.0, 1.0)) / DEG, 0.7)
            val glow = (((12.0 - el) / 10.0).coerceIn(0.0, 1.0)).let { it * it * (3 - 2 * it) }.toFloat()
            var lw = LightWeights.of(if (moonLight) moonEl else max(el, 0.7), side, false, glow)
            var cw = LightWeights.of(if (moonLight) moonEl else el, side, true, glow)
            // (The weights are found for the screen as it is seen; the scene's own x runs the other way when mirrored.)
            var light = if (moonLight && !moonUp) floatArrayOf(0f, 1f, 0f) else lw.dir
            if (flip) { lw = lw.mirrored(); cw = cw.mirrored(); light = floatArrayOf(-light[0], light[1], light[2]) }
            val state = SkyDirector.at(biome, weather, display, el, year, look.mist)
            // The stars are a real, unmirrored sky; the scene's own x runs the other way when it is mirrored.
            fun m(v: FloatArray) = if (flip) floatArrayOf(-v[0], v[1], v[2]) else v
            // Where the heavens stand: truly, for the place and the moment, if that is asked for in Settings; otherwise
            // as the finest night shows them (the owner's rule: by default, the most beautiful view the app has).
            val cel = if (trueStars) NightSky.frame(shownAt, scene.latitude, scene.longitude, flip).let { arrayOf(it.celX, it.celY, it.celZ, it.galN, it.galC) }
            else showcaseSky((display * 24).toFloat(), if (moonUp) (Math.toDegrees(atan2(moonDir[0].toDouble(), moonDir[2].toDouble())) * (if (flip) -1 else 1)).toFloat() else Float.NaN)
            return RealSkyFrame(
                biome, state, focalPx, horizon, flip, sunDir, light, dirAzEl(az, el * DEG), el.toFloat(), lw, cw,
                floatArrayOf(moonDir[0], moonDir[1], moonDir[2], look.moonIllumination),
                floatArrayOf(look.moonLight[0], look.moonLight[1], look.moonLight[2], look.moonVisibility),
                look.night, Array(5) { m(cel[it]) },
                hour = (display * 24).toFloat(), seed = (weather.seedX + weather.seedY * 0.37f),
                textBottom = textBottom, panelTop = panelTop, panelRadius = panelRadius, lightGlass = lightGlass, tint = tint,
                fade = fade, busy = busy, still = still, lapse = lapse,
            )
        }
    }
}
