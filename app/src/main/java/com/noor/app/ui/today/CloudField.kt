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
        const val FRACTUS = 6
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

/** One thing to draw in the cloud layer: a cloud (its picture) or a shaft of rain under one. */
internal class CloudDraw {
    var shaft = false
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
    // A shaft of rain: from [top] to [bottom] between [left] and [right] at the top, leaning by [slant] px at the
    // bottom; [fade] where it thins out (1: it reaches the ground; less: virga, rain that evaporates as it falls).
    var slant = 0f
    var fade = 1f
    var color = 0
}

/** A stroke of lightning: where it runs (x, y pairs), a branch, and how bright it is now. */
internal class Bolt {
    val path = ArrayList<Float>()
    val branch = ArrayList<Float>()
    var intensity = 0f
    /** Only the cloud lights up (lightning within the cloud). */
    var inCloud = false
    var cx = 0f; var cy = 0f; var radius = 0f
}

/**
 * The clouds of the home sky (0.9.9): real clouds, photographed, placed in the scene and lit by the moment. 0.9.8
 * computed every cloud for every pixel of the sky 15 times a second (too slow for many phones, and it looked
 * artificial); now the phone draws a few dozen pictures, each with one colour matrix.
 *
 * - The weather of the moment ([Weather.State]) decides how many clouds, how tall, whether isolated or in clusters and
 *   broken fields, whether towers and thunderstorms rise; every cloud has its own threshold, so as the weather changes
 *   through the day clouds fade in and out one by one instead of the sky jumping from one arrangement to another.
 * - Each cloud has a real width (km) and distance (from a few km to 100), as many in each band of distance, so rows of
 *   small far clouds crowd towards the horizon and a few larger near ones stand higher. Widths follow true perspective;
 *   heights above the horizon are raised by [ELEVATION_GAIN], as the scenery's hills are drawn taller than life, so the
 *   clouds stand above the far hills as in a photograph. Towers and storms whose base the photograph cut off rise from
 *   behind the land.
 * - Light: the photograph's light and shade is kept and recoloured with the light of the moment (the same atmosphere
 *   and tone curve as the sky): white with blue-grey shade by day, gold at sunrise and sunset, dim and moonlit at night,
 *   flat and grey under an overcast; each cloud is turned so that its lit side faces the Sun; the far ones take the
 *   colour of the air in front of them.
 * - Showers: under some clouds a shaft of rain reaches the ground, under others virga thins out on the way; in a
 *   thunderstorm, now and then lightning: the cloud flashes, often a stroke to the ground.
 * - They drift with the wind, the near ones faster across the sky.
 */
internal object CloudField {
    /** Raise of elevations above the horizon, matching the scenery's exaggerated relief. */
    const val ELEVATION_GAIN = 1.6
    private const val EARTH_R = 6360.0
    private const val EYE_H = 0.25
    /** Places for clouds: their own thresholds decide which show in a given weather. */
    const val SLOTS = 44

    class Frame(
        /** Size of the area drawn into, px. */
        val w: Float, val h: Float,
        val horizonY: Float,
        /** px per dp. */
        val dp: Float,
        val fov: Double,
        /** seconds of the ambient clock (drift, lightning). */
        val time: Float,
        val weather: Weather,
        val state: Weather.State,
        /** The Sun: azimuth in the view (radians, 0 ahead) and true elevation (degrees). */
        val sunAz: Double, val sunEl: Double,
        val light: Atmosphere.Light,
        val lut: IntArray,
        val night: Float,
        /** How much light the moon gives (0..1). */
        val moonLight: Float,
    )

    fun hash(seed: Int, i: Int, k: Int): Float {
        var x = seed * -0x61c88647 + i * -0x7a143589 + k * 0x27d4eb2f
        x = x xor (x ushr 16); x *= 0x7feb352d
        x = x xor (x ushr 15); x *= -0x7b935975
        x = x xor (x ushr 16)
        return (x ushr 8) / 16777216f
    }

    /** km, by kind: width of one cloud (min, max), height of its base (min, max), nearest, farthest. For the clouds
     *  whose base is hidden, the height of their top instead of their base (their size follows from it). */
    private val SPEC = arrayOf(
        floatArrayOf(1.6f, 3.0f, 1.0f, 1.4f, 6f, 55f),      // humilis
        floatArrayOf(3.0f, 5.5f, 1.0f, 1.4f, 7f, 55f),      // mediocris
        floatArrayOf(0f, 0f, 4.5f, 7.5f, 22f, 75f),         // congestus: top
        floatArrayOf(0f, 0f, 10f, 13f, 55f, 110f),          // cumulonimbus: top
        floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f),
        floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f),
        floatArrayOf(0.9f, 1.8f, 0.8f, 1.2f, 6f, 40f),      // fractus
        floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f),
    )

    /** The kind of cloud slot [i] holds: the first three are thunderstorms, the rest by chance. */
    fun kindOf(i: Int, h: Float): Int = when {
        i < 3 -> CloudTable.CUMULONIMBUS
        h < 0.08f -> CloudTable.FRACTUS
        h < 0.47f -> CloudTable.HUMILIS
        h < 0.82f -> CloudTable.MEDIOCRIS
        else -> CloudTable.CONGESTUS
    }

    /** How much of slot [i] shows in [s] (0..1): enough cloud for its threshold, and the right size of cloud. */
    fun presence(i: Int, kind: Int, s: Weather.State, h1: Float): Float {
        if (kind == CloudTable.CUMULONIMBUS) return smooth(0.3f + 0.22f * i, 0.42f + 0.22f * i, s.storm)
        val enough = smooth(h1 * 0.97f - 0.03f, h1 * 0.97f + 0.05f, s.cumulus)
        val fits = when (kind) {
            CloudTable.HUMILIS -> 1 - smooth(0.55f, 0.85f, s.size)
            CloudTable.MEDIOCRIS -> smooth(0.04f, 0.22f, s.size)
            CloudTable.CONGESTUS -> smooth(0.38f, 0.62f, s.size)
            else -> 1f
        }
        return enough * fits
    }

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

    /** Where slot [i] would stand on its own: its distance (km) and direction (radians, before the wind). */
    private fun place(seed: Int, i: Int, kind: Int, fov: Double): DoubleArray {
        val sp = SPEC[kind]
        val h2 = hash(seed, i, 2); val h4 = hash(seed, i, 4); val t = hash(seed, i, 9)
        val big = kind == CloudTable.CUMULONIMBUS || kind == CloudTable.CONGESTUS
        val d = if (big) sp[4] + (sp[5] - sp[4]) * t else sp[4] * (sp[5] / sp[4]).pow(t) * (0.92f + 0.16f * h2)
        val window = fov + 0.6
        val az = if (kind == CloudTable.CUMULONIMBUS) (h4 - 0.5) * (fov - 0.6) else ((i * 0.618034 + h4 * 0.5) % 1.0 - 0.5) * window
        return doubleArrayOf(d.toDouble(), az)
    }

    /**
     * The clouds (and shafts of rain) of [f], far to near, into [out] (its items are reused); returns how many. Only
     * those at least partly in view are listed. A stroke of lightning, if one is flashing now, goes into [bolt].
     */
    fun layout(table: CloudTable, f: Frame, out: ArrayList<CloudDraw>, bolt: Bolt? = null): Int {
        bolt?.let { it.intensity = 0f; it.path.clear(); it.branch.clear() }
        if (table.sprites.isEmpty()) return 0
        val st = f.state
        val seed = f.weather.seed
        val pxPerRad = f.w / f.fov
        val window = f.fov + 0.6
        val el = Math.toRadians(f.sunEl)
        val sunL = doubleArrayOf(cos(el) * sin(f.sunAz), sin(el), cos(el) * cos(f.sunAz))
        val sunX = f.w * (0.5 + f.sunAz / f.fov)
        val l = f.light
        val dLit = DoubleArray(3); val dAmb = DoubleArray(3); val tmp = DoubleArray(3); val amb = DoubleArray(3); val sky = DoubleArray(3)
        // Under an overcast the Sun barely reaches the clouds below it: their light is the deck's grey.
        val sunK = 1.0 - 0.85 * st.overcast
        for (j in 0..2) amb[j] = (l.ambTop[j] * 0.85 + l.ambLow[j] * 0.4) * 1.3 * (1 + 0.15 * st.overcast)
        System.arraycopy(amb, 0, tmp, 0, 3)
        Atmosphere.tone(tmp, dAmb)
        val night = f.night.toDouble()
        val moonB = f.moonLight.toDouble()
        // Lightning: now and then, in a thunderstorm.
        val flash = lightning(f)
        var flashTarget = -1; var flashBest = -1f
        var n = 0
        for (i in 0 until SLOTS) {
            val h1 = hash(seed, i, 1); val h2 = hash(seed, i, 2); val h3 = hash(seed, i, 3)
            val h5 = hash(seed, i, 5); val h6 = hash(seed, i, 6); val h7 = hash(seed, i, 7)
            val kind0 = kindOf(i, h3)
            val vis = presence(i, kind0, st, h1)
            if (vis < 0.01f) continue
            var kind = kind0
            if (table.ofKind(kind).isEmpty()) kind = CloudTable.HUMILIS
            val kinds = table.ofKind(kind)
            if (kinds.isEmpty()) continue
            val sp = SPEC[kind]
            // Each cloud a little wider or taller than its photograph, so that one seen twice is not a copy.
            val stretch = 0.86f + 0.28f * hash(seed, i, 8)
            val si = kinds[min((h6 * kinds.size).toInt(), kinds.size - 1)]
            val s = table.sprites[si]
            var (d, az0) = place(seed, i, kind, f.fov).let { it[0] to it[1] }
            // In clusters: a cloud joins the first of its group of four, beside it and at about its distance.
            if (i >= 3 && hash(seed, i, 10) < st.cluster && (i - 3) % 4 != 0) {
                val p = 3 + (i - 3) / 4 * 4
                val pk = kindOf(p, hash(seed, p, 3)).let { if (it == CloudTable.CONGESTUS || it == CloudTable.CUMULONIMBUS) CloudTable.MEDIOCRIS else it }
                val pp = place(seed, p, pk, f.fov)
                d = pp[0] * (0.9 + 0.2 * hash(seed, i, 11))
                val spread = SPEC[CloudTable.MEDIOCRIS][1] / pp[0]
                az0 = pp[1] + (hash(seed, i, 12) - 0.5) * 3.2 * spread
                if (kind == CloudTable.CONGESTUS) d = max(d, sp[4].toDouble())
            }
            val heightKm = (sp[2] + (sp[3] - sp[2]) * h5).toDouble()
            // Drifting with the wind, the nearer the faster (seconds of the ambient clock, and the hours of the day, so a
            // cloud seen in the morning has moved on by noon); a thunderstorm stays in view (it would take hours).
            az0 += if (kind == CloudTable.CUMULONIMBUS) 0.0 else (f.time * 0.007 + st.hour * 4.0) / d
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

            // The colour of its lit and of its shaded parts, in display values.
            val altM = Math.toRadians(altitudeAt(f, (yb + top) / 2) / ELEVATION_GAIN)
            val fx = cos(altM) * sin(az); val fy = sin(altM); val fz = cos(altM) * cos(az)
            val fwd = min(hg(fx * sunL[0] + fy * sunL[1] + fz * sunL[2], 0.6) * 4 * PI, 5.0)
            for (j in 0..2) tmp[j] = l.sunCloud[j] * 2.6 * (0.85 + 0.15 * fwd) * sunK + amb[j]
            Atmosphere.tone(tmp, dLit)
            // The air in front of it: the sky's own colour there.
            val skyArgb = Atmosphere.sample(f.lut, (cx / f.w).toDouble(), Atmosphere.rowOf(altitudeAt(f, (yb + top) / 2)))
            sky[0] = ((skyArgb shr 16) and 0xFF) / 255.0; sky[1] = ((skyArgb shr 8) and 0xFF) / 255.0; sky[2] = (skyArgb and 0xFF) / 255.0
            // Towers and storms far off stay bright: they stand high above the haze of the lower air.
            val haze = (if (s.cut) 0.03 + 0.32 * (1 - exp(-d / 80.0)) else 0.03 + 0.55 * (1 - exp(-d / 45.0))) + 0.25 * st.rain
            val alpha = ((1 - 0.15 * night) * smooth(2f * f.dp, 6f * f.dp, wPx) * vis).toFloat()

            // A shaft of rain beneath it (drawn first, behind it), or virga.
            val showery = when (kind) { CloudTable.CUMULONIMBUS -> 1.0f; CloudTable.CONGESTUS -> 0.75f; CloudTable.MEDIOCRIS -> 0.35f; else -> 0f }
            if (hash(seed, i, 21) < st.showers * showery) {
                val sh = if (n < out.size) out[n] else CloudDraw().also { out += it }
                n++
                sh.shaft = true
                sh.distance = d.toFloat() + 0.01f
                val virga = kind != CloudTable.CUMULONIMBUS && hash(seed, i, 22) < 0.45f
                val sw = wPx * (0.35f + 0.3f * hash(seed, i, 23))
                val sx = cx + (hash(seed, i, 24) - 0.5f) * wPx * 0.35f
                sh.top = if (s.cut) top + (yb - top) * 0.55f else yb - (yb - top) * 0.12f
                sh.bottom = f.horizonY + 3f * f.dp
                sh.left = sx - sw / 2; sh.right = sx + sw / 2
                sh.slant = (hash(seed, i, 25) - 0.5f) * (sh.bottom - sh.top) * 0.45f
                sh.fade = if (virga) 0.3f + 0.4f * hash(seed, i, 26) else 1f
                // Grey-blue: the cloud's shade seen through the air.
                var a = (0.42 * (1 - 0.5 * haze) * alpha).toFloat()
                if (kind == CloudTable.CUMULONIMBUS) a *= 1.3f
                val cr = ((dAmb[0] * 0.55 + dLit[0] * 0.2) * (1 - haze) + sky[0] * haze) * (1 - night) + sky[0] * 0.6 * night
                val cg = ((dAmb[1] * 0.55 + dLit[1] * 0.2) * (1 - haze) + sky[1] * haze) * (1 - night) + sky[1] * 0.6 * night
                val cb = ((dAmb[2] * 0.55 + dLit[2] * 0.2) * (1 - haze) + sky[2] * haze) * (1 - night) + sky[2] * 0.6 * night
                sh.color = argb(a.coerceIn(0f, 1f), cr, cg, cb)
            }

            val c = if (n < out.size) out[n] else CloudDraw().also { out += it }
            n++
            c.shaft = false
            c.sprite = si
            c.distance = d.toFloat()
            c.left = cx - wPx / 2; c.right = cx + wPx / 2; c.top = top; c.bottom = yb
            // Its lit side towards the Sun (when the Sun is low enough to light a side).
            c.flip = if (s.side != 0 && f.sunEl > -2 && f.sunEl < 60) (s.side > 0) != (sunX > cx) else h7 > 0.5f
            var lv = s.levels[0]
            for (q in s.levels) if (q[2] >= wPx * 0.8f) lv = q
            c.src = lv
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
            m[15] = 0f; m[16] = 0f; m[17] = 0f; m[18] = alpha; m[19] = 0f
            // The thunderstorm that flashes: the biggest storm cloud in view.
            if (flash > 0f && (kind == CloudTable.CUMULONIMBUS || kind == CloudTable.CONGESTUS) && vis > 0.5f) {
                val score = (c.right - c.left) * (if (kind == CloudTable.CUMULONIMBUS) 2f else 1f) * (0.5f + hash(seed, i, 30))
                if (score > flashBest) { flashBest = score; flashTarget = n - 1 }
            }
        }
        if (flashTarget >= 0) {
            val c = out[flashTarget]
            // The cloud lights up from within, violet-white.
            for (j in 0..2) {
                c.matrix[j * 5] += (FLASH[j] * 0.7 * flash).toFloat()
                c.matrix[j * 5 + 4] += (FLASH[j] * 0.35 * flash * 255).toFloat()
            }
            if (bolt != null) strike(f, c, flash, bolt)
        }
        // Far to near.
        out.subList(0, n).sortByDescending { it.distance }
        return n
    }

    /** How bright lightning is now (0 most of the time): a flash every several seconds in a storm, flickering. */
    fun lightning(f: Frame): Float {
        val storm = f.state.storm
        if (storm < 0.45f) return 0f
        val period = 9f - 5f * storm
        val k = floor(f.time / period).toInt()
        val seed = f.weather.seed
        if (hash(seed, k, 32) > storm * 1.1f - 0.35f) return 0f
        val dt = f.time - k * period - hash(seed, k, 31) * (period - 1.2f)
        if (dt < 0f || dt > 0.9f) return 0f
        // One to three strokes in quick succession, each bright at once and fading.
        val strokes = 1 + (hash(seed, k, 35) * 3f).toInt()
        var v = 0f
        for (q in 0 until strokes) {
            val t0 = q * (0.12f + 0.1f * hash(seed, k, 36 + q))
            val x = dt - t0
            if (x >= 0f) v = max(v, exp(-x / 0.09f))
        }
        return v
    }

    /** The stroke of the current flash, from [cloud] to the ground (or within it). */
    private fun strike(f: Frame, cloud: CloudDraw, flash: Float, bolt: Bolt) {
        val period = 9f - 5f * f.state.storm
        val k = floor(f.time / period).toInt()
        val seed = f.weather.seed
        bolt.intensity = flash
        val w = cloud.right - cloud.left
        bolt.cx = (cloud.left + cloud.right) / 2; bolt.cy = cloud.top + (cloud.bottom - cloud.top) * 0.5f
        bolt.radius = w * 0.6f
        bolt.inCloud = hash(seed, k, 34) > 0.65f
        if (bolt.inCloud) return
        val x0 = cloud.left + w * (0.3f + 0.4f * hash(seed, k, 37))
        // From low in the cloud (a storm rising from behind the land: from halfway up, so the stroke shows).
        val y0 = cloud.top + (cloud.bottom - cloud.top) * (if (cloud.bottom > f.horizonY) 0.42f else 0.62f)
        val x1 = x0 + (hash(seed, k, 38) - 0.5f) * w * 0.3f
        val y1 = f.horizonY + 2f * f.dp
        // A jagged path: each step down, a little to one side or the other.
        val steps = 22
        var x = x0
        for (q in 0..steps) {
            val t = q / steps.toFloat()
            val y = y0 + (y1 - y0) * t
            if (q > 0) x += (hash(seed, k * 64 + q, 39) - 0.5f) * (y1 - y0) / steps * 1.6f + (x1 - x0) / steps
            bolt.path += x; bolt.path += y
        }
        // A branch from a third of the way down.
        val b0 = (steps * (0.25f + 0.2f * hash(seed, k, 40))).toInt()
        var bx = bolt.path[b0 * 2]; var by = bolt.path[b0 * 2 + 1]
        val dir = if (hash(seed, k, 41) > 0.5f) 1f else -1f
        bolt.branch += bx; bolt.branch += by
        for (q in 1..7) {
            by += (y1 - y0) / steps * 0.8f
            bx += dir * (y1 - y0) / steps * (0.5f + hash(seed, k * 64 + q, 42))
            bolt.branch += bx; bolt.branch += by
        }
    }

    private fun argb(a: Float, r: Double, g: Double, b: Double): Int {
        fun c(x: Double) = (x.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
        return ((a * 255 + 0.5f).toInt() shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /** How much skylight fills a cloud's shade. */
    private const val SHADE_FILL = 0.35
    private val MOONLIT = doubleArrayOf(0.42, 0.46, 0.56)
    private val NIGHT_FLOOR = doubleArrayOf(0.006, 0.008, 0.014)
    private val FLASH = doubleArrayOf(0.85, 0.82, 1.0)
}
