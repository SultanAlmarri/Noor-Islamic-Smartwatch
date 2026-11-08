package com.noor.app.ui.today

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The clouds in res/raw/clouds.webp (res/raw/cloud_atlas.txt, written by tools/sky/photo_clouds.py): real clouds cut
 * out of public-domain photographs. For each: its kind, the size of its largest copy, where its base is, whether its
 * base was cut off (it then rises from behind the land), which side the Sun lit it from, how many clouds' width it holds
 * (a group), and its copies at decreasing sizes (x, y, w, h in the image, largest first).
 */
internal class CloudTable(val sprites: List<Sprite>) {
    class Sprite(
        val kind: Int, val w: Int, val h: Int,
        /** px above the bottom of the largest copy where the cloud's base is (0 when cut). */
        val base: Int,
        val cut: Boolean,
        /** The side the Sun lit it from in its photograph: 1 right, −1 left, 0 in front. */
        val side: Int,
        val group: Int,
        val levels: List<IntArray>,
    )

    private val byKind = Array(KINDS) { k -> sprites.indices.filter { sprites[it].kind == k }.toIntArray() }

    /** The sprites of [kind] (indices into [sprites]). */
    fun ofKind(kind: Int): IntArray = byKind[kind]

    companion object {
        const val HUMILIS = 0; const val MEDIOCRIS = 1; const val CONGESTUS = 2; const val CUMULONIMBUS = 3
        const val FRACTUS = 6; const val ROW = 7
        const val KINDS = 8

        fun parse(text: String): CloudTable {
            val sprites = ArrayList<Sprite>()
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val f = line.split(Regex("\\s+"))
                if (f[0] != "cloud") continue
                val n = f.drop(1).map { it.toInt() }
                sprites += Sprite(n[0], n[1], n[2], n[3], n[4] == 1, n[5], n[7], n.drop(8).chunked(4).map { it.toIntArray() })
            }
            return CloudTable(sprites)
        }
    }
}

/** One cloud of the frame, ready to draw: its picture, where it goes, and how to colour it. */
internal class CloudDraw {
    var sprite = 0
    /** The copy of the picture used (x, y, w, h in the image). */
    var src = IntArray(4)
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    var flip = false
    /** km, for drawing far to near. */
    var distance = 0f
    /**
     * Colour matrix (android.graphics.ColorMatrix layout: 4 × 5, translation in 0..255): the picture's brightness (its
     * red channel) becomes the cloud's colour for this moment and place; its alpha, the cloud's opacity.
     */
    val matrix = FloatArray(20)
}

/**
 * The clouds of the home sky (0.9.9): real clouds, photographed, placed in the scene and lit by the moment. 0.9.8
 * computed every cloud for every pixel of the sky 15 times a second (too slow for many phones, and it looked
 * artificial); now the phone draws a few dozen pictures, each with one colour matrix.
 *
 * - The weather of the day (regime) decides the kinds and how many: fair-weather cumulus, scattered cumulus, towering
 *   cumulus with a thunderstorm far off, distant towers, broken cloud.
 * - Each cloud has a real width (km) and distance (from a few km to 100), as many in each band of distance, so rows of
 *   small far clouds crowd towards the horizon and a few larger near ones stand higher. Widths follow true perspective;
 *   heights above the horizon are raised by [ELEVATION_GAIN], as the scenery's hills are drawn taller than life, so the
 *   clouds stand above the far hills as in a photograph. Towers and storms whose base the photograph cut off rise from
 *   behind the land.
 * - Light: the photograph's light and shade is kept and recoloured with the light of the moment (the same atmosphere
 *   and tone curve as the sky): white with blue-grey shade by day, gold at sunrise and sunset, dim and moonlit at night;
 *   each cloud is turned so that its lit side faces the Sun; the far ones take the colour of the air in front of them.
 * - They drift with the wind, the near ones faster across the sky.
 */
internal object CloudField {
    /** Raise of elevations above the horizon, matching the scenery's exaggerated relief. */
    const val ELEVATION_GAIN = 1.6
    private const val EARTH_R = 6360.0
    private const val EYE_H = 0.25

    class Frame(
        /** Size of the area drawn into, px. */
        val w: Float, val h: Float,
        val horizonY: Float,
        /** px per dp. */
        val dp: Float,
        val fov: Double,
        /** seconds of the ambient clock (drift). */
        val time: Float,
        val weather: Weather,
        /** The Sun: azimuth in the view (radians, 0 ahead) and true elevation (degrees). */
        val sunAz: Double, val sunEl: Double,
        val light: Atmosphere.Light,
        val lut: IntArray,
        val night: Float,
        /** How much light the moon gives (0..1). */
        val moonLight: Float,
    )

    private fun hash(seed: Int, i: Int, k: Int): Float {
        var x = seed * -0x61c88647 + i * -0x7a143589 + k * 0x27d4eb2f
        x = x xor (x ushr 16); x *= 0x7feb352d
        x = x xor (x ushr 15); x *= -0x7b935975
        x = x xor (x ushr 16)
        return (x ushr 8) / 16777216f
    }

    fun seedOf(w: Weather): Int = floor(w.seedX * 1000f).toInt() * 31 + floor(w.seedY * 1000f).toInt()

    /** The kind of the [i]th cloud: [h] random, [far] 0 near … 1 far. */
    fun kindOf(regime: Int, h: Float, far: Float, i: Int): Int = when (regime) {
        // Fair weather: small and middling cumulus, a ragged scrap now and then, rows of them far off.
        1 -> if (far > 0.8f && h < 0.3f) CloudTable.ROW else if (h < 0.55f) CloudTable.HUMILIS else if (h < 0.93f) CloudTable.MEDIOCRIS else CloudTable.FRACTUS
        // Scattered: bigger cumulus, a few towers far off.
        2 -> if (far > 0.75f && h < 0.25f) CloudTable.ROW else if (far > 0.5f && h > 0.85f) CloudTable.CONGESTUS else if (h < 0.6f) CloudTable.MEDIOCRIS else CloudTable.HUMILIS
        // Towering: a thunderstorm (two now and then) far off, towers, cumulus.
        3 -> if (i == 0 || (i == 1 && h > 0.6f)) CloudTable.CUMULONIMBUS else if (far > 0.35f && h < 0.4f) CloudTable.CONGESTUS else if (h < 0.8f) CloudTable.MEDIOCRIS else CloudTable.HUMILIS
        // Distant towers over a clear sky.
        4 -> if (far > 0.45f && h < 0.45f) CloudTable.CONGESTUS else if (h < 0.85f) CloudTable.HUMILIS else CloudTable.FRACTUS
        // Broken cloud: groups of cumulus, rows to the horizon.
        5 -> if (far > 0.7f && h < 0.4f) CloudTable.ROW else if (h < 0.8f) CloudTable.MEDIOCRIS else CloudTable.HUMILIS
        else -> CloudTable.HUMILIS
    }

    /**
     * km, by kind: width of one cloud (min, max), height of its base (min, max), nearest, farthest. For the clouds
     * whose base is hidden, the height of their top instead of their base (their size follows from it).
     */
    private val SPEC = arrayOf(
        floatArrayOf(1.6f, 3.0f, 1.0f, 1.4f, 6f, 55f),      // humilis
        floatArrayOf(3.0f, 5.5f, 1.0f, 1.4f, 7f, 55f),      // mediocris
        floatArrayOf(0f, 0f, 4.5f, 7.0f, 25f, 75f),         // congestus: top
        floatArrayOf(0f, 0f, 10f, 13f, 60f, 110f),          // cumulonimbus: top
        floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f),
        floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f),
        floatArrayOf(0.9f, 1.8f, 0.8f, 1.2f, 6f, 40f),      // fractus
        floatArrayOf(0f, 0f, 2.2f, 3.2f, 40f, 70f),         // a row of distant cumulus: top
    )

    private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    private fun hg(mu: Double, g: Double): Double {
        val g2 = g * g
        return (1 - g2) / (4 * PI * max(1 + g2 - 2 * g * mu, 1e-4).pow(1.5))
    }

    /** y (px) of an altitude (degrees) on the sky's own scale (SkyView.altitudeAt inverted). */
    fun yOfAltitude(f: Frame, altDeg: Double): Float = if (altDeg >= 0) {
        (f.horizonY - f.horizonY * min(altDeg / SkyView.ALT_TOP, 1.0).pow(1 / SkyView.ALT_POW)).toFloat()
    } else (f.horizonY - altDeg * 6.0 * f.dp).toFloat()

    private fun altitudeAt(f: Frame, y: Float): Double = SkyView.ALT_TOP * ((f.horizonY - y) / f.horizonY).toDouble().coerceIn(0.0, 1.0).pow(SkyView.ALT_POW)

    /**
     * The clouds of [f], far to near, into [out] (its items are reused); returns how many. Only those at least partly
     * in view are listed.
     */
    fun layout(table: CloudTable, f: Frame, out: ArrayList<CloudDraw>): Int {
        val regime = f.weather.regime
        val count = f.weather.clouds.coerceIn(0, 40)
        if (count == 0 || table.sprites.isEmpty()) return 0
        val seed = seedOf(f.weather)
        val pxPerRad = f.w / f.fov
        val window = f.fov + 0.6
        val el = Math.toRadians(f.sunEl)
        val sunL = doubleArrayOf(cos(el) * sin(f.sunAz), sin(el), cos(el) * cos(f.sunAz))
        val sunX = f.w * (0.5 + f.sunAz / f.fov)
        val l = f.light
        val dLit = DoubleArray(3); val dAmb = DoubleArray(3); val tmp = DoubleArray(3); val amb = DoubleArray(3); val sky = DoubleArray(3)
        for (j in 0..2) amb[j] = (l.ambTop[j] * 0.85 + l.ambLow[j] * 0.4) * 1.3
        System.arraycopy(amb, 0, tmp, 0, 3)
        Atmosphere.tone(tmp, dAmb)
        val night = f.night.toDouble()
        val moonB = f.moonLight.toDouble()
        var n = 0
        for (i in 0 until count) {
            val h1 = hash(seed, i, 1); val h2 = hash(seed, i, 2); val h3 = hash(seed, i, 3)
            val h4 = hash(seed, i, 4); val h5 = hash(seed, i, 5); val h6 = hash(seed, i, 6); val h7 = hash(seed, i, 7)
            // Each cloud a little wider or taller than its photograph, so that one seen twice is not a copy.
            val stretch = 0.86f + 0.28f * hash(seed, i, 8)
            // Far to near: as many clouds in each band of distance (log-uniform).
            val t = (i + 0.5f * h1) / count
            var kind = kindOf(regime, h3, 1f - t, i)
            if (table.ofKind(kind).isEmpty()) kind = CloudTable.HUMILIS
            val kinds = table.ofKind(kind)
            if (kinds.isEmpty()) continue
            val sp = SPEC[kind]
            val big = kind == CloudTable.CUMULONIMBUS || kind == CloudTable.CONGESTUS || kind == CloudTable.ROW
            val d = (if (big) sp[4] + (sp[5] - sp[4]) * (if (kind == CloudTable.CUMULONIMBUS) h1 else 1f - t)
                     else sp[4] * (sp[5] / sp[4]).pow(1f - t) * (0.92f + 0.16f * h2)).toDouble()
            val si = kinds[min((h6 * kinds.size).toInt(), kinds.size - 1)]
            val s = table.sprites[si]
            val heightKm = (sp[2] + (sp[3] - sp[2]) * h5).toDouble()
            // Across the view, spread out, drifting with the wind, the nearer the faster; a thunderstorm stays in
            // view (it would take hours to cross it).
            val az0 = if (kind == CloudTable.CUMULONIMBUS) (h4 - 0.5) * (f.fov - 0.6)
            else ((i * 0.618034 + h4 * 0.5) % 1.0 - 0.5) * window + f.time * 0.007 / d
            val az = az0 - window * floor(az0 / window + 0.5)
            val cx = (f.w * (0.5 + az / f.fov)).toFloat()
            val drop = d * d / (2 * EARTH_R)
            val wPx: Float; val k: Float; val yb: Float
            if (s.cut) {
                // Its base cut off in its photograph: it rises from just below the horizon, behind the land, to its top.
                yb = f.horizonY + 4f * f.dp
                val yTop = yOfAltitude(f, Math.toDegrees(atan((heightKm - EYE_H - drop) / d)) * ELEVATION_GAIN)
                k = (yb - yTop) / s.h
                wPx = s.w * k * stretch
            } else {
                // Its real width in perspective; its base at its real height.
                val wKm = (sp[0] + (sp[1] - sp[0]) * h2).toDouble() * s.group.toDouble().pow(0.8)
                wPx = (wKm / d * pxPerRad).toFloat()
                k = wPx / s.w / stretch
                yb = yOfAltitude(f, Math.toDegrees(atan((heightKm - EYE_H - drop) / d)) * ELEVATION_GAIN) + s.base * k
            }
            if (wPx < 2f * f.dp) continue
            if (cx + wPx / 2 < 0f || cx - wPx / 2 > f.w) continue
            val top = yb - s.h * k
            if (top > f.h) continue
            val c = if (n < out.size) out[n] else CloudDraw().also { out += it }
            n++
            c.sprite = si
            c.distance = d.toFloat()
            c.left = cx - wPx / 2; c.right = cx + wPx / 2; c.top = top; c.bottom = yb
            // Its lit side towards the Sun (when the Sun is low enough to light a side).
            c.flip = if (s.side != 0 && f.sunEl > -2 && f.sunEl < 60) (s.side > 0) != (sunX > cx) else h7 > 0.5f
            var lv = s.levels[0]
            for (q in s.levels) if (q[2] >= wPx * 0.8f) lv = q
            c.src = lv

            // The colour of its lit and of its shaded parts, in display values.
            val altM = Math.toRadians(altitudeAt(f, (yb + top) / 2) / ELEVATION_GAIN)
            val fx = cos(altM) * sin(az); val fy = sin(altM); val fz = cos(altM) * cos(az)
            val fwd = min(hg(fx * sunL[0] + fy * sunL[1] + fz * sunL[2], 0.6) * 4 * PI, 5.0)
            for (j in 0..2) tmp[j] = l.sunCloud[j] * 2.6 * (0.85 + 0.15 * fwd) + amb[j]
            Atmosphere.tone(tmp, dLit)
            // The air in front of it: the sky's own colour there.
            val skyArgb = Atmosphere.sample(f.lut, (cx / f.w).toDouble(), Atmosphere.rowOf(altitudeAt(f, (yb + top) / 2)))
            sky[0] = ((skyArgb shr 16) and 0xFF) / 255.0; sky[1] = ((skyArgb shr 8) and 0xFF) / 255.0; sky[2] = (skyArgb and 0xFF) / 255.0
            val haze = 0.03 + 0.55 * (1 - exp(-d / 45.0))
            val m = c.matrix
            for (j in 0..2) {
                // Day: brightness × the sunlit colour, the shade filled with skylight; then the haze.
                val slope = (dLit[j] - SHADE_FILL * dAmb[j]) * (1 - haze)
                val off = SHADE_FILL * dAmb[j] * (1 - haze) + haze * sky[j]
                // Night: barely lit by the moon, a little paler than the sky behind.
                val nSlope = MOONLIT[j] * moonB
                val nOff = sky[j] * 0.45 + NIGHT_FLOOR[j] + MOONLIT[j] * 0.25 * moonB
                m[j * 5] = (slope * (1 - night) + nSlope * night).toFloat()
                m[j * 5 + 1] = 0f; m[j * 5 + 2] = 0f; m[j * 5 + 3] = 0f
                m[j * 5 + 4] = ((off * (1 - night) + nOff * night) * 255).toFloat()
            }
            // Small far clouds fade in rather than appear.
            m[15] = 0f; m[16] = 0f; m[17] = 0f; m[19] = 0f
            m[18] = ((1 - 0.15 * night) * smooth(2f * f.dp, 6f * f.dp, wPx)).toFloat()
        }
        // Far to near.
        out.subList(0, n).sortByDescending { it.distance }
        return n
    }

    /** How much skylight fills a cloud's shade. */
    private const val SHADE_FILL = 0.35
    private val MOONLIT = doubleArrayOf(0.42, 0.46, 0.56)
    private val NIGHT_FLOOR = doubleArrayOf(0.006, 0.008, 0.014)
}
