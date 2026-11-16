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
    /** (0.14) Fog lying on the water or the ground: the mornings that have it, from before dawn until the Sun has burnt it off. */
    val fog: Float = 0f,
)

/**
 * A set of clouds on show: a wide window of the sky (tools/scenery/skybake.py) passing across the view. [offset]:
 * how far right of the set's middle the view's middle stands, degrees; [weight]: how much of it there is (the hour's
 * thinning: by night, after sunset); [speed]: how fast it passes, degrees a second (the renderer carries it on
 * between one minute's frame and the next, so that it glides).
 */
internal class CloudLeg(
    val set: String, val offset: Float, val weight: Float, val speed: Float = 0f,
    /** (0.14) The set's picture is turned about (left for right): every set passes both ways round, so the same picture comes back half as often. */
    val turned: Boolean = false,
)

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

    /**
     * How long a set of each level (0 high, 1 middle, 2 low) takes to pass, hours. The low clouds are the nearest and
     * pass fastest: about a thirtieth of a degree a second, slow enough for a calm day and fast enough to be seen.
     */
    private val LEG_HOURS = floatArrayOf(1.9f, 1.0f, 0.55f)
    /**
     * How many degrees of sky a set takes on its way, from one set's middle to the next's (`SKY_PITCH` in common.glsl).
     * Its window is 94 degrees wide: over the 28 degrees two neighbours share, the one breaks up into patches and
     * thins out as the other gathers (layer.frag), as a sheet of cloud really ends: never along a line, which would
     * bring the edge of an overcast across the sky as a wall.
     */
    const val PITCH = 64f
    /** Half a set's window and half the view's, degrees: a set is on show while its middle is nearer than both together. */
    const val REACH = 47f + 26f

    /** How much of the view's width a set standing at [offset] lies over, 0..1. */
    fun share(offset: Float): Float = ((min(26f, PITCH / 2 - offset) - max(-26f, -PITCH / 2 - offset)) / 52f).coerceIn(0f, 1f)

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

    // ---- The day's programme (0.14) ---------------------------------------------------------------------------
    //
    // The owner, of 0.13: "the same cloud types just keep cycling over and over again, which is extremely boring...
    // It's just overcast, and then it's overcast again... I want the entire day to have a lot of variety... sometimes
    // it's overcast, sometimes it's broken, sometimes it's scattered, sometimes it's isolated clouds... That's not how
    // real life works."
    //
    // Until 0.13 a leg's set was read off the weather in the middle of that leg, and the weather of a day has one
    // character from midnight to midnight: an overcast day was forty-four overcasts in a row, each the same picture
    // passing again. Now the day is a walk through skies. The weather still says where the day leans (how closed the
    // sky wants to be at each hour: open with the odd swell on a clear day, building through a convective afternoon,
    // closed for a spell of a few hours on an overcast or rainy day and open before and after it), and each leg takes
    // the sky nearest to that which the rules allow:
    //
    // - never the set that has just passed, and rather not the one before it either;
    // - a sky closes and opens by degrees: no overcast wall to wall beside an open sky;
    // - a closed sky (overcast, or overcast with breaks) holds for three legs at most, then it breaks for a while,
    //   and of any twelve hours it takes four and a half at most;
    // - the hours of sunrise and sunset keep room for the Sun under clouds that take its colour, unless the day's
    //   closed spell falls on them;
    // - thunderstorms come in their hours (late on a convective day, now and then on a stormy one), towers with the
    //   warmth of the day.
    //
    // SkyDirectorTest.theDayIsAWalkThroughDifferentSkies locks all of it.

    /** The low sets by how closed their sky is (0 open .. 5 a full overcast). */
    private val HOW_CLOSED = mapOf(
        "lo_few" to 0, "lo_scattered" to 1, "lo_towers" to 1, "lo_front" to 2, "lo_broken" to 3, "lo_storm" to 3, "lo_overcast_breaks" to 4, "lo_overcast" to 5,
    )
    /** What a middle set amounts to, for choosing it by how much middle cloud the hour wants. */
    private val MID_AMOUNT = mapOf("mi_patches" to 0.32f, "mi_bands" to 0.50f, "mi_veil" to 0.68f, "mi_sheet" to 0.86f)

    /** The low sets with a thunderhead in them, and where its towers stand: degrees right of the middle of the set. */
    val THUNDER = mapOf("lo_storm" to floatArrayOf(14f))

    /** How closed a low set's sky is, 0..5 (sets not named: open). */
    fun closedness(set: String?): Int = HOW_CLOSED[set?.removeSuffix(TURNED)] ?: 0

    /**
     * A day's skies: for each level, the set of every leg (null: that level is empty then), from leg [FIRST_LEG] on.
     * A name that ends in [TURNED] is that set with its picture turned about, left for right.
     */
    class Programme(val sets: Array<Array<String?>>)
    const val TURNED = "~"

    /**
     * Which way round a set passes this time: the other way than the last time it passed, so that the very same
     * picture comes back only after its set has been by the other way round (the first time: by a throw of the dice).
     */
    private class Turns(val seed: Int, val salt: Int) {
        private val last = HashMap<String, Boolean>()
        fun next(name: String): String {
            val t = last[name]?.let { !it } ?: (CloudField.hash(seed, salt, name.hashCode() and 0xffff) < 0.5f)
            last[name] = t
            return if (t) name + TURNED else name
        }
    }
    const val FIRST_LEG = -3
    private fun legCount(level: Int) = (24f / LEG_HOURS[level]).toInt() + 8

    private val programmes = object : LinkedHashMap<String, Programme>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Programme>?) = size > 48
    }

    /** The programme of the day with weather [w] over [biome] at time of year [year], the Sun rising and setting at hours [rise] and [set]. */
    fun programme(w: Weather, biome: Biome, year: Float = 0.5f, rise: Float = 6f, set: Float = 18f): Programme {
        val key = "${w.seed} ${w.type} ${biome.ordinal} ${(year * 24).toInt()} ${(rise * 6).toInt()} ${(set * 6).toInt()}"
        synchronized(programmes) { programmes[key]?.let { return it } }
        val p = Programme(arrayOf(walkHigh(w, biome, year, rise, set), walkMid(w, biome, year, rise, set), walkLow(w, biome, year, rise, set)))
        synchronized(programmes) { programmes[key] = p }
        return p
    }

    /** The hour a leg of a level has its middle at. */
    private fun midHour(w: Weather, level: Int, k: Int): Float = (k + 0.5f) * LEG_HOURS[level] - CloudField.hash(w.seed, 40 + level, 0) * LEG_HOURS[level]

    /** 1 at hour [c] and for [flat] hours to either side of it, falling to 0 at [half] hours away. */
    private fun spell(h: Float, c: Float, half: Float, flat: Float = half * 0.4f): Float {
        var d = abs(h - c); if (d > 12f) d = 24f - d
        return 1f - smooth(flat, half, d)
    }

    /**
     * How closed the low sky wants to be at hour [h], 0..5: where the day leans. [inSpell] comes back as how far the
     * hour lies in the day's closed spell, 0..1.
     */
    private fun lowLean(w: Weather, biome: Biome, year: Float, h: Float, rise: Float, set: Float): FloatArray {
        val seed = w.seed
        fun day(k: Int) = CloudField.hash(seed, 7, 100 + k)
        val slow = hourNoise(seed, h, 3.7f, 81) - 0.5f
        val quick = hourNoise(seed, h, 1.4f, 82) - 0.5f
        val warm = smooth(9.5f, 14f, h) * (1f - smooth(18f, 21f, h))
        // The day's closed spell, on the days that have one: early (low cloud that the morning burns off), in the
        // afternoon and evening, or by night; two and a half to five hours of it.
        val centre = when {
            day(2) < 0.40f -> rise - 0.5f + 3.0f * day(3)
            day(2) < 0.75f -> 13.5f + 6f * day(3)
            else -> (21f + 6f * day(3)) % 24f
        }
        val half = 1.3f + 1.2f * day(4)
        var inSpell = 0f
        var c = when (w.type) {
            Weather.CLEAR -> 0.5f + 1.5f * slow + 0.6f * warm * day(1)
            Weather.FAIR -> 1.1f + 2.4f * slow + 0.7f * warm
            Weather.CONVECTIVE -> 0.8f + 1.8f * slow + 1.9f * warm
            Weather.STORMY -> 2.2f + 2.8f * slow + 0.8f * warm
            Weather.LAYERED -> 1.0f + 2.2f * slow
            Weather.OVERCAST -> { inSpell = spell(h, centre, half); 1.0f + 1.6f * slow + 3.9f * inSpell }
            Weather.RAINY -> { inSpell = spell(h, centre, half + 0.8f); 1.4f + 1.6f * slow + 3.6f * inSpell + 1.8f * spell(h, (centre + 7f + 5f * day(5)) % 24f, 1.1f) }
            else -> 1.9f + 4.4f * slow
        } + 1.0f * quick
        c = when (biome) {
            // The desert's sky is open; a closed spell there is a rare day's event.
            Biome.DUNES -> if (day(6) < 0.2f) c * 0.85f else min(c * 0.8f, 3.3f)
            Biome.RAINFOREST -> c + 0.25f
            Biome.CANYON -> c * 0.95f
            Biome.ARCTIC -> c + 0.1f
            else -> c
        }
        // The hours of colour keep room for the Sun, unless the day's closed spell falls on them.
        val colour = max(spell(h, rise, 1.3f, 0.6f), spell(h, set, 1.3f, 0.6f)) * (1f - inSpell)
        c += (min(c, 2.0f) - c) * colour
        // Deep night is mostly clear, for the stars (a storm apart).
        val night = max(1f - smooth(rise - 2.2f, rise - 0.8f, h), smooth(set + 1.0f, set + 2.4f, h))
        if (w.type != Weather.STORMY) c *= 1f - 0.45f * night * (1f - inSpell)
        return floatArrayOf(c.coerceIn(0f, 5f), inSpell, warm)
    }

    /** Whether a thunderstorm may stand in the sky at hour [h] of this day. */
    private fun stormHour(w: Weather, biome: Biome, h: Float): Boolean {
        if (biome == Biome.ARCTIC) return false
        val seed = w.seed
        val late = smooth(13.5f, 16f, h) * (1f - smooth(20f, 22.5f, h))
        val ok = when (w.type) {
            Weather.CONVECTIVE -> late * (0.55f + 0.45f * CloudField.hash(seed, 7, 110)) > 0.5f
            Weather.STORMY -> hourNoise(seed, h, 2.6f, 83) > 0.5f
            Weather.MIXED -> late * hourNoise(seed, h, 2.2f, 83) > 0.5f
            Weather.RAINY -> hourNoise(seed, h, 2.4f, 83) > 0.72f
            else -> false
        }
        // (Over the dunes only now and then: a desert thunderstorm is an event.)
        return ok && (biome != Biome.DUNES || CloudField.hash(seed, 7, 111) < 0.5f)
    }

    private fun walkLow(w: Weather, biome: Biome, year: Float, rise: Float, set: Float): Array<String?> {
        val n = legCount(2)
        val out = arrayOfNulls<String>(n)
        val names = SETS[2]
        var prev: String? = null; var prev2: String? = null
        var closedRun = 0; var mustOpen = 0; var sinceStorm = 9
        val turns = Turns(w.seed, 362)
        for (i in 0 until n) {
            val k = FIRST_LEG + i
            val h = ((midHour(w, 2, k) % 24f) + 24f) % 24f
            val lean = lowLean(w, biome, year, h, rise, set)
            val storm = stormHour(w, biome, h)
            // (Of any twelve hours, the sky is closed for four and a half at most.)
            var closedLately = 0
            for (b in max(0, i - 21) until i) if (closedness(out[b]) >= 4) closedLately++
            val towers = lean[2] > 0.25f && w.type != Weather.OVERCAST && w.type != Weather.RAINY && (w.type != Weather.CLEAR || CloudField.hash(w.seed, 7, 112) < 0.5f)
            var best: String? = null; var bestScore = 1e9f
            for ((j, name) in names.withIndex()) {
                if (name == prev) continue
                val c = closedness(name)
                if (prev != null && abs(c - closedness(prev)) > 2) continue
                if (name == "lo_storm" && (!storm || sinceStorm < 3)) continue
                if (name == "lo_towers" && !towers) continue
                if (c >= 4 && (mustOpen > 0 || closedLately >= 8)) continue
                var score = abs(c - lean[0]) + 0.9f * CloudField.hash(w.seed, 300 + j, k)
                if (name == prev2) score += 0.8f
                if (name == "lo_storm") score -= 1.4f              // (in its hour a storm shows)
                if (score < bestScore) { bestScore = score; best = name }
            }
            val pick = best ?: (if (prev == "lo_scattered") "lo_few" else "lo_scattered")
            out[i] = turns.next(pick)
            sinceStorm = if (pick == "lo_storm") 0 else sinceStorm + 1
            if (closedness(pick) >= 4) { closedRun++; if (closedRun >= 3) { mustOpen = 2; closedRun = 0 } } else { closedRun = 0; if (mustOpen > 0) mustOpen-- }
            prev2 = prev; prev = pick
        }
        return out
    }

    private fun walkMid(w: Weather, biome: Biome, year: Float, rise: Float, set: Float): Array<String?> {
        val n = legCount(1)
        val out = arrayOfNulls<String>(n)
        var prev: String? = null; var prev2: String? = null
        val turns = Turns(w.seed, 363)
        for (i in 0 until n) {
            val k = FIRST_LEG + i
            val h = ((midHour(w, 1, k) % 24f) + 24f) % 24f
            val nz = hourNoise(w.seed, h, 4.3f, 84)
            var m = when (w.type) {
                Weather.CLEAR -> 0.08f + 0.26f * nz
                Weather.FAIR -> 0.16f + 0.40f * nz
                Weather.CONVECTIVE -> 0.16f + 0.36f * nz
                Weather.STORMY -> 0.30f + 0.45f * nz
                Weather.LAYERED -> 0.50f + 0.50f * nz
                Weather.OVERCAST -> 0.35f + 0.45f * nz
                Weather.RAINY -> 0.40f + 0.40f * nz
                else -> 0.22f + 0.70f * nz
            }
            val lean = lowLean(w, biome, year, h, rise, set)
            // The hour of colour: something for the low Sun to light from beneath (not under the day's closed spell).
            val colour = max(spell(h, rise, 1.2f, 0.5f), spell(h, set, 1.2f, 0.5f)) * (1f - lean[1])
            m = max(m, colour * (0.36f + 0.30f * CloudField.hash(w.seed, 7, 120)))
            val night = max(1f - smooth(rise - 2.2f, rise - 0.8f, h), smooth(set + 1.0f, set + 2.4f, h))
            m *= 1f - 0.45f * night
            if (m < 0.20f) { out[i] = null; prev2 = prev; prev = null; continue }
            var best: String? = null; var bestScore = 1e9f
            for ((j, name) in SETS[1].withIndex()) {
                if (name == prev) continue
                // (A sheet does not lie over the hour of colour: it would take the Sun.)
                if (colour > 0.5f && (name == "mi_sheet" || name == "mi_veil")) continue
                var score = abs(MID_AMOUNT.getValue(name) - m) + 0.36f * CloudField.hash(w.seed, 320 + j, k)
                if (name == prev2) score += 0.22f
                if (score < bestScore) { bestScore = score; best = name }
            }
            out[i] = best?.let { turns.next(it) }
            prev2 = prev; prev = best
        }
        return out
    }

    private fun walkHigh(w: Weather, biome: Biome, year: Float, rise: Float, set: Float): Array<String?> {
        val n = legCount(0)
        val out = arrayOfNulls<String>(n)
        var prev: String? = null; var prev2: String? = null
        val veilDay = CloudField.hash(w.seed, 7, 1) < 0.22f
        val turns = Turns(w.seed, 364)
        for (i in 0 until n) {
            val k = FIRST_LEG + i
            val h = ((midHour(w, 0, k) % 24f) + 24f) % 24f
            val lean = lowLean(w, biome, year, h, rise, set)
            val colour = max(spell(h, rise, 1.6f, 0.8f), spell(h, set, 1.6f, 0.8f)) * (1f - lean[1])
            var amount = 0.12f + 0.75f * hourNoise(w.seed, h, 6.0f, 85)
            if (w.type == Weather.CLEAR) amount *= 0.8f
            amount = max(amount, colour * 0.5f)
            // (Nothing is seen of the high clouds through a closed low sky.)
            if (amount < 0.26f || lean[0] > 4.4f) { out[i] = null; prev2 = prev; prev = null; continue }
            var best: String? = null; var bestScore = 1e9f
            for ((j, name) in SETS[0].withIndex()) {
                if (name == prev) continue
                var score = CloudField.hash(w.seed, 340 + j, k)
                if (name == "hi_veil") score += if (veilDay) -0.35f else 0.45f
                if (name == prev2) score += 0.3f
                if (score < bestScore) { bestScore = score; best = name }
            }
            out[i] = best?.let { turns.next(it) }
            prev2 = prev; prev = best
        }
        return out
    }

    /** The set a level shows on leg [k] of the day (null: that level is empty for the leg): see [programme]. */
    fun setFor(w: Weather, level: Int, k: Int, biome: Biome, year: Float = 0.5f, rise: Float = 6f, set: Float = 18f): String? =
        programme(w, biome, year, rise, set).sets[level].getOrNull(k - FIRST_LEG)

    /**
     * The sets a level shows at hour [h] of the day, and where each stands. The sets of a level follow one another
     * across the sky like the clouds of a real day, side by side (each level at its own pace and with its own start,
     * so that no two levels change together): one passes out of the view on one side as the next comes in on the
     * other. Nothing fades in or out, nothing changes at a stroke, and nothing changes because the Sun has reached
     * some height or a prayer has begun. (0.12 let a set drift 41 degrees and then dissolved it into the next.)
     */
    fun legs(w: Weather, level: Int, h: Float, biome: Biome, year: Float = 0.5f, rise: Float = 6f, set: Float = 18f): List<CloudLeg> {
        val d = LEG_HOURS[level]
        val x = (h + CloudField.hash(w.seed, 40 + level, 0) * d) / d
        val k = kotlin.math.floor(x).toInt(); val p = x - k
        val dir = if (CloudField.hash(w.seed, 60, 0) < 0.5f) 1f else -1f
        val speed = dir * PITCH / (d * 3600f)
        val out = ArrayList<CloudLeg>(2)
        for (j in -1..1) {
            // (Set k + j stands with its middle j + 0.5 sets along; the view's middle is p sets along.)
            val offset = dir * (p - 0.5f - j) * PITCH
            if (abs(offset) >= REACH) continue
            setFor(w, level, k + j, biome, year, rise, set)?.let { out += CloudLeg(it.removeSuffix(TURNED), offset, 1f, speed, it.endsWith(TURNED)) }
        }
        return out
    }

    private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /**
     * The sky at [fraction] of the day with the Sun at [sunEl] degrees: [w] is the day's weather, [year] the season
     * (see [yearPhase]), [mistBase] the dawn mist the sky already works out from the Sun's height.
     */
    fun at(biome: Biome, w: Weather, fraction: Double, sunEl: Double, year: Float, mistBase: Float, rise: Float = 6f, set: Float = 18f): SkyState {
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
        val clouds = List(3) { level -> legs(w, level, h, biome, year, rise, set).map { CloudLeg(it.set, it.offset, it.weight * amounts[level] * midnight, it.speed, it.turned) } }
        // The Sun and the clouds. The Sun is drawn low in the picture, where far clouds crowd together, though it
        // stands high in the real sky, where gaps are wide: left to the picture it would nearly always be hidden. So
        // whether a cloud is in front of it is the weather's say: clouds pass over it now and then, the more often the
        // more of them there are, and otherwise stand apart round it; under an overcast it is gone.
        // (0.14: by the sets that are on show, not by what the day's weather would have wanted.)
        val lowSky = clouds[2].sumOf { (it.weight * share(it.offset) * closedness(it.set) / 5f).toDouble() }.toFloat().coerceIn(0f, 1f)
        val often = (0.10f + 0.85f * lowSky).coerceIn(0f, 1f)
        val covered = smooth(1f - often - 0.2f, 1f - often + 0.2f, hourNoise(seed, h, 0.75f, 71))
        // (What the sets on show close of the sky: a closed sky hides the Sun whatever the hour's weather says, and
        // the land under it is in its even light, not in sunshine.)
        fun closed(level: Int) = clouds[level].sumOf { (it.weight * share(it.offset) * (CLOSES[it.set] ?: 0f)).toDouble() }.toFloat().coerceIn(0f, 1f)
        val overcast = 1f - (1f - closed(2)) * (1f - closed(1))
        // (Nor does a thunderhead stand apart for the Sun: where it is in front of it, the Sun is behind it.)
        val full = clouds[2].sumOf { (it.weight * share(it.offset) * (if (it.set == "lo_overcast" || it.set == "lo_storm") 1f else 0f)).toDouble() }.toFloat().coerceIn(0f, 1f)
        val sunPart = ((1f - covered) * (1f - smooth(0.6f, 0.92f, overcast)) * (1f - full)).coerceIn(0f, 1f)
        val shade = max(overcast, 0.6f * (1f - sunPart))
        // (0.14) Morning fog, on the mornings that have it (most often over the forest lake and in the green hills'
        // valley, in the cold half of the year; now and then over the sea, the fjord, the red rock and, as on the
        // Gulf's winter mornings, the dunes): it gathers in the last hours of the night, is deepest about sunrise and
        // is burnt off an hour or two after it. Never on a stormy day: fog needs still air.
        val fogChance = when (biome) {
            Biome.RAINFOREST -> 0.32f + 0.22f * winter
            Biome.MEADOW -> 0.22f + 0.28f * winter
            Biome.OCEAN -> 0.20f
            Biome.ARCTIC -> 0.24f
            Biome.CANYON -> 0.06f + 0.10f * winter
            Biome.DUNES -> 0.06f + 0.18f * winter
        }
        val fog = if (day(20) < fogChance && w.type != Weather.STORMY)
            (0.55f + 0.45f * day(21)) * smooth(rise - 3.5f, rise - 1.2f, h) * (1f - smooth(rise + 0.7f, rise + 2.2f + 1.2f * day(22), h)) else 0f
        // (0.14) It rains only out of a sky that is closed, as far as it is closed: rain out of a sunny sky with a
        // few clouds in it was a grey veil over the distance that nothing in the picture accounted for.
        val falling = rain * smooth(0.12f, 0.45f, overcast)
        val halo = clouds[0].sumOf { (it.weight * share(it.offset) * (if (it.set == "hi_veil") 1f else if (it.set == "hi_mixed") 0.4f else 0f)).toDouble() }.toFloat().coerceIn(0f, 1f)
        return SkyState(
            clouds = clouds, sunPart = sunPart, halo = halo, overcast = overcast, shade = shade,
            cover = cover, kind = kind, base = 1.25f + 0.5f * day(11), cluster = s.cluster,
            mid = mid, midForm = midForm,
            cirrus = cirrus, veil = veilNow, storm = storm, showers = showers, rain = falling,
            haze = haze, mist = mist, dust = dust, wind = windy, season = season, snow = snow,
            // (Where snow lies, what falls is snow: the ground is not wet.)
            wet = smooth(0.15f, 0.5f, falling) * (1f - smooth(0.4f, 0.6f, snow)),
            aurora = aurora, waterRough = rough, fog = fog,
        )
    }
}

/**
 * The seasons of a scenery's land (0.14). A scenery's pictures are of one season (land.json, `season`); for each
 * other season that looks different there it may hold the land's colours again (`a_<season>.webp`, `k_<season>.webp`:
 * the leaves turned or fallen, the grass dried, snow lying), listed in `seasons`. The light pictures are shared. The
 * renderer shows the two seasons a day of the year lies between, the one passing into the other over some weeks.
 */
internal object Seasons {
    val NAMES = arrayOf("winter", "spring", "summer", "autumn")
    /**
     * When each season is at its fullest, as a phase of the year ([SkyDirector.yearPhase]: 0 = midwinter). Later than
     * the calendar's: the land follows the Sun by a month and more. Bare trees and snow late in January, new leaves
     * late in April, high summer late in July, the leaves at their brightest at the start of November.
     */
    val FULL = floatArrayOf(0.085f, 0.335f, 0.585f, 0.865f)

    /** A season's turn: which two seasons (indices of [NAMES]) and how far from the first to the second, 0..1. */
    class Turn(val from: Int, val to: Int, val mix: Float)

    /** The two seasons [year] lies between. Each holds for a while; the change takes the middle of the time between. */
    fun between(year: Float): Turn {
        var y = year - kotlin.math.floor(year)
        var i = 3
        if (y < FULL[0]) y += 1f else { i = 0; while (i < 3 && y >= FULL[i + 1]) i++ }
        val next = (i + 1) % 4
        val end = if (next == 0) FULL[0] + 1f else FULL[next]
        val t = ((y - FULL[i]) / (end - FULL[i])).coerceIn(0f, 1f)
        val u = ((t - 0.3f) / 0.4f).coerceIn(0f, 1f)
        return Turn(i, next, u * u * (3 - 2 * u))
    }

    /** The picture of [letter] ("a" or "k") a scenery shows in season [s]: its own for that season, or the base one. */
    fun picture(letter: String, s: Int, base: String, others: Set<String>): String =
        if (NAMES[s] != base && NAMES[s] in others) "${letter}_${NAMES[s]}" else letter
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
    /** The moment shown, as the clock has it (ms): the renderer carries the clouds on from there while it is now. */
    val shownAtMs: Long = 0L,
    /** The time of year the land is shown in (0 = midwinter .. 0.5 = midsummer): see [Seasons]. */
    val year: Float = 0.5f,
    /** Testing only (Tester): the fog to show whatever the morning; null: the director's. */
    val fogShown: Float? = null,
) {
    val haze: Float get() = state.haze

    fun lightKey(): String = "${(sunEl * 40).toInt()} ${(sunDir[0] * 2000).toInt()} ${(sunDir[1] * 2000).toInt()} ${(moon[0] * 500).toInt()} ${(moon[1] * 500).toInt()} " +
        "${(moon[3] * 50).toInt()} ${(night * 100).toInt()} ${(state.haze * 50).toInt()} ${focalPx.toInt()} ${horizon.toInt()} $flip"

    fun landKey(): String = biome.id

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
        // Lightning: every so often while a thunderhead stands in the view, a stroke inside the cloud lights it from
        // within, and flickers out (two or three pulses in a third of a second). It strikes where the set's towers
        // stand (SkyDirector.THUNDER: degrees right of the middle of the set). (0.13: until then the flash was worked
        // out and never drawn. 0.14: any set with a thunderhead in it, not one; and now and then the stroke is one to
        // the ground, a crooked line of light under the cloud's base.)
        val thunder = s.clouds[2].firstOrNull { it.set in SkyDirector.THUNDER && it.weight * SkyDirector.share(it.offset) > 0.25f }
        var flash = 0f
        var strike = floatArrayOf(0f, 0.16f, 0.987f)
        var bolt = floatArrayOf(0f, 4f, 0f, 0f)
        if (thunder != null && !still) {
            val k = (t * 0.9f).toInt()
            val ph = t * 0.9f - k
            if (CloudField.hash(seed.toInt(), k, 91) < 0.30f) {
                flash = if (ph < 0.07f) 1f else if (ph > 0.13f && ph < 0.19f) 0.7f else if (ph > 0.24f && ph < 0.27f) 0.35f else 0f
                // (A tower stands so many degrees right of its set's middle; left of it when the set passes turned about.)
                val towers = SkyDirector.THUNDER.getValue(thunder.set)
                val tower = towers[(CloudField.hash(seed.toInt(), k, 95) * towers.size).toInt().coerceIn(0, towers.size - 1)]
                val az = (if (thunder.turned) -tower else tower) - thunder.offset + (CloudField.hash(seed.toInt(), k, 92) - 0.5f) * 12f
                strike = dirAzEl(az * DEG, (6f + 9f * CloudField.hash(seed.toInt(), k, 93)) * DEG)
                // (Two strokes in five reach the ground, if the place they fall is in the view.)
                if (CloudField.hash(seed.toInt(), k, 94) < 0.40f && abs(az) < 26f)
                    bolt = floatArrayOf(az, 3.2f + 2.2f * CloudField.hash(seed.toInt(), k, 96), flash, 1f + 9f * CloudField.hash(seed.toInt(), k, 97))
            }
        }
        u["uBolt"] = bolt
        // (While a thunderhead is in the view every frame counts: a stroke lasts a few of them.)
        u["uThunder"] = floatArrayOf(if (thunder != null && !still) 1f else 0f)
        u["uSky"] = floatArrayOf(s.aurora, 0f, flash, 0f)
        u["uFlash"] = floatArrayOf(strike[0], strike[1], strike[2], flash)
        u["uLand"] = floatArrayOf(biome.ordinal.toFloat(), s.season, s.snow, s.wet)
        u["uAir"] = floatArrayOf(s.mist, s.rain, (s.wind + 0.8f * gust).coerceAtMost(1f), s.dust)
        u["uFog"] = floatArrayOf(fogShown ?: s.fog, 0f, 0f, 0f)
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
            fade: Float, busy: Boolean, still: Boolean, lapse: Float, trueStars: Boolean = false, fogShown: Float? = null,
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
            // (The hours the Sun rises and sets at, for the day's programme of skies: SkyDirector.programme.)
            var rise = 6f; var set = 18f
            val sm = scene.samples
            for (i in 1 until sm.size) {
                val a = sm[i - 1]; val b = sm[i]
                if ((a.second < -0.83) != (b.second < -0.83)) {
                    val at = (a.first + (b.first - a.first) * ((-0.83 - a.second) / (b.second - a.second))).toFloat() * 24f
                    if (b.second > a.second) rise = at else set = at
                }
            }
            val state = SkyDirector.at(biome, weather, display, el, year, look.mist, rise, set)
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
                fade = fade, busy = busy, still = still, lapse = lapse, shownAtMs = shownAt.toEpochMilli(), year = year, fogShown = fogShown,
            )
        }
    }
}
