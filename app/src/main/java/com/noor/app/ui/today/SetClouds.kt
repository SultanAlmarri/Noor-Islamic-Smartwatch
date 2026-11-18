package com.noor.app.ui.today

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.noor.app.sky.CloudLeg
import com.noor.app.sky.LightWeights
import com.noor.app.sky.SkyDirector
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The medium graphics' clouds (0.19.1): the real sky's own baked sets (assets/sky2/clouds/<id>, path traced in Blender:
 * tools/scenery/skybake.py), chosen as the real sky chooses them ([SkyDirector.at]: the same sets at the same places for
 * the same place and day), lit for the moment into one picture per set and laid over the sky.
 *
 * Until 0.19.0 the medium sky drew clouds cut out of photographs, as small sprites kept below the text written on the
 * sky, and wind-combed cirrus streaks computed in its shader. The owner (17 November 2026): "Clouds kind of have
 * straight lines coming out of them, and artifacts which look very unrealistic, and the clouds don't seem to appear
 * above a certain level ... there's like a line blocking them". Now the clouds fill the whole sky, as in the real sky,
 * and the text stays readable as it does there: behind it the brightest parts of the clouds are eased down.
 *
 * Light: a set's pictures were rendered under unit lights (the Sun at a few heights on either side, the glow of a low
 * Sun, a white sky above, white ground below; layer.frag). Here they are given the colours of the medium sky's own light
 * ([Atmosphere.Light]): the Sun as it stands over each cloud (a far cloud keeps its colour after sunset), the glow of
 * the sky round a low Sun, the blue dome, the ground; thin cloud near the Sun lit through, undersides lit from below
 * once the Sun is lower than the cloud; far clouds fading into the colour of the air in front of them; the lower
 * clouds standing apart round the Sun when the weather lets it through; the glare of the Sun over them.
 *
 * Cost: the pictures are read at half their size, on a background thread, once per set; a set is lit again (a few
 * milliseconds, off the main thread) only when the light of the moment changes or it has drifted a quarter of a
 * degree; drawing is one bitmap per set. The same on every phone, GPU sky or Canvas sky.
 */
internal object CloudSets {
    /** The pictures are read at 1/SAMPLE of their size. */
    const val SAMPLE = 2
    private const val KEEP = 6

    /** One set's pictures, one channel each (0..255 as unsigned bytes). */
    class Pictures(
        val id: String, val level: Int, val w: Int, val h: Int,
        val azHalf: Float, val elMin: Float, val elMax: Float, val rMax: Float,
        val a: ByteArray, val k: ByteArray, val g: ByteArray, val f: ByteArray?,
        private val read: (String) -> ByteArray?,
    ) {
        private val suns = LinkedHashMap<String, ByteArray?>(16, 0.75f, true)

        /** The picture lit by the Sun [name] (l0 … r7, top, gl, gr, lu0 …), read when first asked for. */
        @Synchronized fun sun(name: String): ByteArray? {
            if (suns.containsKey(name)) return suns[name]
            val p = read("s_$name")
            suns[name] = p
            while (suns.size > 10) suns.remove(suns.keys.first())
            return p
        }
    }

    private val sets = LinkedHashMap<String, Pictures>(8, 0.75f, true)
    private val failed = HashSet<String>()

    /** Reads a set (blocking): its description and its pictures of opacity, distance, sky light and ground light. */
    fun load(assets: AssetManager, id: String): Pictures? {
        synchronized(sets) { sets[id]?.let { return it }; if (id in failed) return null }
        val p = runCatching {
            val json = org.json.JSONObject(assets.open("sky2/clouds/$id/sky.json").bufferedReader().use { it.readText() })
            var w = 0; var h = 0
            fun read(name: String): ByteArray? = runCatching {
                val o = BitmapFactory.Options().apply { inSampleSize = SAMPLE; inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }
                val b = assets.open("sky2/clouds/$id/$name.webp").use { BitmapFactory.decodeStream(it, null, o) } ?: return@runCatching null
                w = b.width; h = b.height
                val px = IntArray(w * h)
                b.getPixels(px, 0, w, 0, 0, w, h)
                b.recycle()
                ByteArray(w * h) { ((px[it] shr 8) and 0xFF).toByte() }
            }.getOrNull()
            val a = read("a") ?: error("no opacity")
            val ww = w; val hh = h
            val layer = json.opt("layer")
            val level = if (layer == "low") 2 else (layer as? Number)?.toInt() ?: 2
            Pictures(
                id, level, ww, hh, json.optDouble("azHalf", 47.0).toFloat(), json.optDouble("elMin", -1.0).toFloat(), json.optDouble("elMax", 40.0).toFloat(),
                json.optDouble("rMax", 4.0).toFloat(), a, read("k") ?: ByteArray(ww * hh), read("g") ?: ByteArray(ww * hh), read("f"),
            ) { name -> read(name)?.takeIf { it.size == ww * hh } }
        }.onFailure { Log.w("NoorSky", "Cloud set $id unavailable", it) }.getOrNull()
        synchronized(sets) {
            if (p == null) failed += id else { sets[id] = p; while (sets.size > KEEP) sets.remove(sets.keys.first()) }
        }
        return p
    }

    fun cached(id: String): Pictures? = synchronized(sets) { sets[id] }
}

/** The light of the moment for the clouds (everything in the coordinates of the sky view, px). */
internal class CloudLight(
    /** The Sun's true elevation (degrees); where it is seen, as an angle right of the view's middle (radians) and over the horizon (degrees). */
    val el: Double, val sunAz: Double, val sunElSeen: Double,
    /** Which side the light comes from (-1 left … 1 right, as the screen is seen). */
    val side: Double,
    /** Deep in the night the moon lights the clouds: its height (degrees) and its light (exposed, linear). */
    val moonLight: Boolean, val moonEl: Double, val moonW: Double, val moon: DoubleArray,
    /** How strong the glow round a low Sun is (0..1). */
    val glow: Double,
    val light: Atmosphere.Light, val lut: IntArray,
    /** The air: 1 clean, more hazy (SkyState.haze). How far the lower clouds stand apart round the Sun (SkyState.sunPart). */
    val haze: Double, val sunPart: Double,
    val sunX: Float, val sunY: Float, val dp: Float,
    /** The Sun's glare over the clouds (0..1), and its colour (display). */
    val glare: Double, val glareColor: DoubleArray,
    /** The text written on the sky: the middle of the shade behind it (y, px), its height (px), the brightest a cloud may be there (linear luminance). */
    val textY: Float, val textR: Float, val textCap: Double,
)

/** The sky view the clouds are laid out in: its width, its horizon (px), the lens (px per radian), mirrored or not. */
internal class CloudView(val w: Float, val hy: Float, val focal: Float, val rtl: Boolean, val altitudeAt: (Float) -> Double)

/** A set lit for a moment: its picture (ARGB, not premultiplied) and where it stands (px of the view, before its drift). */
internal class LitCloudSet(val px: IntArray, val w: Int, val h: Int, val x0: Float, val x1: Float, val y0: Float, val y1: Float, val offset: Float)

internal object SetClouds {
    private const val DEG = Math.PI / 180
    /** The lens of the real sky (focal length in screen widths): the clouds are seen as there. */
    const val FOCAL = 1.15f
    /** Skylight on the clouds, against the Sun's (the medium sky's light is exposed for the land). */
    const val AMBIENT = 3.0
    /** The brightest a cloud may be behind the text (linear luminance), and the glare's strength. */
    const val TEXT_CAP = 0.34
    const val GLARE = 0.8

    private fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

    private fun hash2(x: Int, y: Int): Double {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h ushr 8) / 16777216.0
    }

    /** Smooth value noise, for the ragged ends of a set. */
    fun vnoise(x: Double, y: Double): Double {
        val ix = floor(x).toInt(); val iy = floor(y).toInt(); val fx = x - ix; val fy = y - iy
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        val a = hash2(ix, iy); val b = hash2(ix + 1, iy); val c = hash2(ix, iy + 1); val d = hash2(ix + 1, iy + 1)
        return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy
    }

    private fun hg(mu: Double, g: Double): Double { val g2 = g * g; val x = max(1 + g2 - 2 * g * mu, 1e-4); return (1 - g2) / (4 * Math.PI * x * sqrt(x)) }

    private fun thinning(x: Double) = if (x < 1e-3) 1.0 else (1 - exp(-x)) / x
    private fun ch(x: Double) = (x.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()

    /** km to a cloud from its distance picture (skybake: far = 1 - exp(-km / 30)), by the picture's 256 values. */
    private val KM = DoubleArray(256) { -30 * ln(max(1 - it / 255.0, 1e-3)) }

    /** The sRGB curves by table (the lighting runs for every cloud pixel of a set). */
    private val SRGB = DoubleArray(4097) { Atmosphere.srgb(it / 4096.0) }
    private val LINEAR = DoubleArray(1025) { Atmosphere.linear(it / 1024.0) }
    fun srgbFast(x: Double): Double { val f = x.coerceIn(0.0, 1.0) * 4096; val i = min(f.toInt(), 4095); return SRGB[i] + (SRGB[i + 1] - SRGB[i]) * (f - i) }
    fun linearFast(x: Double): Double { val f = x.coerceIn(0.0, 1.0) * 1024; val i = min(f.toInt(), 1023); return LINEAR[i] + (LINEAR[i + 1] - LINEAR[i]) * (f - i) }
    private fun aces(x: Double): Double = ((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14)).coerceIn(0.0, 1.0)

    /** [Atmosphere.tone] with the sRGB curve by table. */
    fun toneFast(c: DoubleArray, out: DoubleArray) {
        val l = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
        val s = if (l > 1e-6) aces(l) / l else 0.0
        for (k in 0..2) { val v = max(c[k], 0.0); out[k] = min(v * s, 1.0) * 0.7 + aces(v) * 0.3 }
        val y = 0.2126 * out[0] + 0.7152 * out[1] + 0.0722 * out[2]
        for (k in 0..2) out[k] = srgbFast((y + (out[k] - y) * Atmosphere.SATURATION).coerceIn(0.0, 1.0))
    }

    /**
     * Lights set [s] for the moment: [leg] says where it stands ([CloudLeg.offset]: how far right of its middle the view's
     * middle is, degrees) and whether it is turned about; [offset] is that, carried on with the wind.
     */
    fun compose(s: CloudSets.Pictures, l: CloudLight, leg: CloudLeg, offset: Float, v: CloudView): LitCloudSet {
        val wc = s.w; val hc = s.h
        val level = s.level
        val azHalf = s.azHalf.toDouble(); val elMin = s.elMin.toDouble(); val elMax = s.elMax.toDouble(); val rMax = s.rMax.toDouble()
        val m = if (v.rtl) -1 else 1; val t = if (leg.turned) -1 else 1
        val lw = LightWeights.of(if (l.moonLight) l.moonEl else l.el, l.side * m * t, true, l.glow.toFloat())
        val pics = ArrayList<ByteArray>(5); val picW = ArrayList<Double>(5)
        for (i in 0 until 5) if (lw.w[i] > 0.002f) s.sun(lw.names[i])?.let { pics += it; picW += lw.w[i] * rMax }
        val glows = ArrayList<ByteArray>(2); val glowW = ArrayList<Double>(2)
        if (lw.glow[0] > 0.002f) s.sun("gl")?.let { glows += it; glowW += lw.glow[0] * rMax }
        if (lw.glow[1] > 0.002f) s.sun("gr")?.let { glows += it; glowW += lw.glow[1] * rMax }
        val np = pics.size; val ng = glows.size
        val tanTop = tan(elMax * DEG); val tanBot = tan(elMin * DEG)
        // Rows in equal steps on the screen (the pictures' rows are equal steps of angle).
        val rowOf = IntArray(hc); val rowF = FloatArray(hc); val elOf = DoubleArray(hc)
        for (oy in 0 until hc) {
            val te = tanTop - (oy + 0.5) / hc * (tanTop - tanBot)
            val e = atan(te) / DEG; elOf[oy] = e
            val r = ((elMax - e) / (elMax - elMin) * hc - 0.5).coerceIn(0.0, hc - 1.001)
            rowOf[oy] = floor(r).toInt(); rowF[oy] = (r - floor(r)).toFloat()
        }
        // The Sun's light on a cloud, by the Sun's height over it and the cloud's own height: a small table.
        val hs = doubleArrayOf(0.6, 1.5, 3.0, 6.0, 12.0)
        val e0 = l.el - 3; val en = 48; val es = 0.25
        val direct = DoubleArray(hs.size * en * 3)
        val tmp = DoubleArray(3)
        for (hi in hs.indices) for (k in 0 until en) {
            Atmosphere.sunlight(hs[hi], e0 + k * es, l.light.exposure, tmp)
            for (c in 0..2) direct[(hi * en + k) * 3 + c] = tmp[c] * (1 - l.moonW) + l.moon[c] * l.moonW
        }
        val lt = l.light
        // (The skylight is raised by day only: the night's own faint light is not, or the clouds glow blue at night.)
        val ambient = 1 + (AMBIENT - 1) * (1 - lt.night)
        val dome = DoubleArray(3) { (lt.ambLow[it] + (lt.ambTop[it] - lt.ambLow[it]) * 0.6) * ambient }
        // In the evening a cloud's shaded side is lit by the whole blue dome, not by the orange band at the Sun's horizon.
        val ev = 0.65 * smooth(7.0, -2.0, l.el)
        val dl = 0.2126 * dome[0] + 0.7152 * dome[1] + 0.0722 * dome[2]
        val tint = doubleArrayOf(0.78, 0.95, 1.38)
        for (c in 0..2) dome[c] += (dl * tint[c] - dome[c]) * ev
        val sinEl = max(sin(l.el * DEG), 0.0)
        val ground = DoubleArray(3) { 0.30 * (lt.sunGround[it] * sinEl + dome[it]) }
        val glowK = 1 + (0.55 - 1) * smooth(2.0, -3.0, l.el)
        val lowSun = smooth(26.0, 6.0, l.el)
        val thinKind = if (level == 2) 0.45 else 1.0
        val hazeK = when (level) { 2 -> 0.92; 1 -> 0.80; else -> 0.68 }
        val share = when (level) { 2 -> 1.0; 1 -> 0.6; else -> 0.0 }
        val sd = doubleArrayOf(cos(l.sunElSeen * DEG) * sin(l.sunAz), sin(l.sunElSeen * DEG), cos(l.sunElSeen * DEG) * cos(l.sunAz))
        val out = IntArray(wc * hc)
        val a8 = s.a; val k8 = s.k; val g8 = s.g; val f8 = s.f
        val disp = DoubleArray(3); val lin = DoubleArray(3)
        val pitch = SkyDirector.PITCH.toDouble()
        val off = offset.toDouble()
        val fogBase = 0.55 * l.haze + 0.25
        // (Worked out once per column: where the column is in the set and on the screen, and its bearing.)
        val sazOf = DoubleArray(wc); val sxOf = IntArray(wc); val sinAz = DoubleArray(wc); val cosAz = DoubleArray(wc)
        val towardSun = DoubleArray(wc); val xPxOf = FloatArray(wc)
        for (ox in 0 until wc) {
            val u = (ox + 0.5) / wc
            val saz = m * (u - 0.5) * 2 * azHalf
            sazOf[ox] = saz
            sxOf[ox] = ((0.5 + t * saz / (2 * azHalf)) * wc).toInt().coerceIn(0, wc - 1)
            val azScr = ((u - 0.5) * 2 * azHalf - m * off) * DEG
            sinAz[ox] = sin(azScr); cosAz[ox] = cos(azScr); towardSun[ox] = cos(azScr - l.sunAz)
            xPxOf[ox] = (v.w / 2 + v.focal * azScr).toFloat()
        }
        // The sky behind a row, across the table's columns (display colours).
        val nx = Atmosphere.NX
        val skyRowC = DoubleArray(nx * 3)
        for (oy in 0 until hc) {
            val e = elOf[oy]; val sy = rowOf[oy]; val fy = rowF[oy].toDouble(); val fy0 = 1 - fy
            val te = tan(e * DEG); val ce = cos(e * DEG); val se = sin(e * DEG)
            val yPx = (v.hy - v.focal * te).toFloat()
            val skyRow = Atmosphere.rowOf(v.altitudeAt(max(yPx, 0f)))
            var skyReady = false
            val up0 = max(te, 0.0) / sqrt(1 + te * te)
            val qy = if (l.textR > 0) (yPx - l.textY) / l.textR else 0f
            val gy = (yPx - l.sunY).toDouble()
            for (ox in 0 until wc) {
                val i0 = sy * wc + sxOf[ox]; val i1 = i0 + wc
                fun rd(p: ByteArray): Double = ((p[i0].toInt() and 0xFF) * fy0 + (p[i1].toInt() and 0xFF) * fy) * (1 / 255.0)
                val a0 = rd(a8)
                if (a0 < 0.004) continue
                var a = a0
                val saz = sazOf[ox]
                // Where this set gives way to its neighbour: in patches, as a sheet of cloud really ends (layer.frag), along
                // a line that wanders far to either side (never straight down the sky: an overcast's end was a wall).
                val wander = (vnoise(e * 0.11 + 3.0 * level, saz * 0.035 + 5.0) - 0.5) * 22 + (vnoise(e * 0.37 + 7.0, saz * 0.1) - 0.5) * 7
                val gone = smooth(0.5 * pitch - 14, 0.5 * pitch + 14, abs(saz) + sign(saz) * 0.2 * (e - 18) + wander)
                if (gone > 0) {
                    if (gone >= 1) continue
                    val bx = saz * 0.021; val by = e * 0.050 + 0.37 * level
                    val lobes = (vnoise(bx * 2.48 + 1.7, by * 2.48 + 1.7) - 0.5) * 3.2
                    val cells = vnoise(bx * 7.2, by * 7.2 + 40) * 0.7 + vnoise(bx * 21.6 + 3, by * 21.6 + 80) * 0.3
                    val patches = (0.5 + lobes * 0.55 + (cells - 0.45) * 1.5).coerceIn(0.0, 1.0)
                    a *= smooth(-0.20, 0.16, patches - 1.4 * gone + 0.16)
                    if (a < 0.004) continue
                }
                val vx = ce * sinAz[ox]; val vz = ce * cosAz[ox]
                val mu = vx * sd[0] + se * sd[1] + vz * sd[2]
                // Round the Sun the lower clouds stand apart when the weather lets it through (scene.frag, parted()).
                if (share > 0 && l.sunPart > 0 && mu > 0.5) {
                    val om = 1 - mu
                    val th2 = 2 * om * (1 + om / 3)                                  // (the angle from the Sun, squared)
                    val thinA = 0.85 * share * l.sunPart * exp(-th2 / (0.21 * 0.21))
                    a = ((a - thinA) / (1 - thinA)).coerceIn(0.0, 1.0) * (1 - share * l.sunPart * exp(-th2 / (0.075 * 0.075)))
                }
                a *= leg.weight
                if (a < 0.004) continue
                // Lit by the Sun (the pictures the moment lies between), by the glow round a low Sun, by the sky, by the ground.
                var sSun = 0.0
                for (q in 0 until np) { val x = rd(pics[q]); sSun += x * x * picW[q] }
                var sGlow = 0.0
                for (q in 0 until ng) { val x = rd(glows[q]); sGlow += x * x * glowW[q] }
                val kv = rd(k8); val gv = rd(g8)
                val kSky = kv * kv * rMax; val kGround = gv * gv * rMax
                val km = if (f8 != null) KM[f8[i0].toInt() and 0xFF] * fy0 + KM[f8[i1].toInt() and 0xFF] * fy else 10.7
                val elThere = l.el + km * towardSun[ox] / 111.2
                val high = when (level) { 2 -> max(1.2, km * max(se, 0.0) + km * km / 12742).coerceIn(0.2, 14.0); 1 -> 5.5; else -> 9.5 }
                var hi = 0
                while (hi < hs.size - 2 && high > hs[hi + 1]) hi++
                val hf = ((high - hs[hi]) / (hs[hi + 1] - hs[hi])).coerceIn(0.0, 1.0)
                val ek = ((elThere - e0) / es).coerceIn(0.0, en - 1.001); val ei = ek.toInt(); val efr = ek - ei
                val through = 0.7 * hg(mu, 0.80) + 0.3 * hg(mu, 0.45)
                val under = smooth(3.5, -0.5, elThere)
                val inv = 1 / a0
                for (c in 0..2) {
                    val j0 = (hi * en + ei) * 3 + c; val j1 = ((hi + 1) * en + ei) * 3 + c
                    val d0 = direct[j0] + (direct[j0 + 3] - direct[j0]) * efr
                    val d1 = direct[j1] + (direct[j1 + 3] - direct[j1]) * efr
                    val dc = d0 + (d1 - d0) * hf
                    var x = (sSun * dc + sGlow * lt.glow[c] * glowK) * 1.15 + kSky * dome[c] * 0.9 + kGround * ground[c] * 0.8
                    x += dc * kSky * through * 0.15 * thinKind * (0.06 + 0.94 * lowSun)
                    x += dc * (0.09 * kGround + 2.6 * sSun) * thinKind * under
                    tmp[c] = x * inv
                }
                toneFast(tmp, disp)
                // Far clouds fade into the air in front of them, which has the colour of the sky behind them.
                val xPx = xPxOf[ox]
                val upKm = km * up0
                val fog = (1 - exp(-fogBase * km / 30 * (0.25 * thinning(upKm / 8) + 0.75 * thinning(upKm / 1.6)))) * hazeK
                if (!skyReady) {
                    for (i in 0 until nx) {
                        val c = Atmosphere.sample(l.lut, i / (nx - 1.0), skyRow)
                        skyRowC[i * 3] = ((c shr 16) and 0xFF) / 255.0; skyRowC[i * 3 + 1] = ((c shr 8) and 0xFF) / 255.0; skyRowC[i * 3 + 2] = (c and 0xFF) / 255.0
                    }
                    skyReady = true
                }
                val sxF = ((xPx / v.w).toDouble().coerceIn(0.0, 1.0)) * (nx - 1)
                val si = min(sxF.toInt(), nx - 2); val sf = sxF - si
                for (c in 0..2) {
                    val sc = skyRowC[si * 3 + c] * (1 - sf) + skyRowC[si * 3 + 3 + c] * sf
                    disp[c] += (sc - disp[c]) * fog
                }
                // The glare round the Sun lies over the clouds too.
                if (l.glare > 0) {
                    val gx = (xPx - l.sunX).toDouble()
                    val r = sqrt(gx * gx + gy * gy) / l.dp
                    if (r < 260) {
                        val gk = (0.55 * exp(-max(r - 7.5, 0.0) / 9) + 0.22 * exp(-r / 55)) * l.glare
                        for (c in 0..2) disp[c] = 1 - (1 - disp[c]) * (1 - l.glareColor[c] * gk)
                    }
                }
                // The text written on the sky stays readable: behind it the brightest parts of the clouds are eased down.
                if (l.textR > 0) {
                    val qx = (xPx - v.w / 2) / (0.44f * v.w)
                    val q2 = (qx * qx + qy * qy).toDouble()
                    if (q2 < 1.70 * 1.70) {
                        val lm = 1 - (1 - l.textCap) * smooth(1.70, 0.25, sqrt(q2))
                        for (c in 0..2) lin[c] = linearFast(disp[c])
                        val lum = 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
                        if (lum > lm * 0.5) {
                            val x = (lum - lm * 0.5) / (lm * 0.5)
                            val y = lm * 0.5 + lm * 0.5 * (1 - exp(-x))
                            for (c in 0..2) disp[c] = srgbFast(lin[c] * y / lum)
                        }
                    }
                }
                out[oy * wc + ox] = (ch(a) shl 24) or (ch(disp[0]) shl 16) or (ch(disp[1]) shl 8) or ch(disp[2])
            }
        }
        val cx = v.w / 2 - m * v.focal * (off * DEG).toFloat()
        val half = v.focal * (azHalf * DEG).toFloat()
        return LitCloudSet(out, wc, hc, cx - half, cx + half, (v.hy - v.focal * tanTop).toFloat(), (v.hy - v.focal * tanBot).toFloat(), offset)
    }

    /** The light of the moment for the clouds, from what the medium sky already knows. */
    fun lightOf(i: GpuSkyInputs, sky: com.noor.app.sky.SkyState, display: Double): CloudLight {
        val v = i.view; val look = i.look
        val focal = FOCAL * v.w
        val el = look.elevation
        val moonLight = el < -6.0
        val night = look.night.toDouble()
        val illum = look.moonIllumination.toDouble()
        val mk = 0.9 * illum * look.moonVisibility
        val glareCol = DoubleArray(3).also { Atmosphere.tone(i.light.light.sunGlow.copyOf(), it) }
        return CloudLight(
            el = el,
            sunAz = ((look.sunX - v.w / 2) / focal).toDouble(),
            sunElSeen = if (el > 0) Math.toDegrees(atan(((v.horizonY - look.sunY) / focal).toDouble())) else el,
            side = if (moonLight) ((look.moonX - v.w / 2) / (0.2f * v.w)).toDouble().coerceIn(-1.0, 1.0)
            else ((display - i.scene.noonFraction) / 0.07).coerceIn(-1.0, 1.0) * v.geometry.travel * (if (v.rtl) -1 else 1),
            moonLight = moonLight, moonEl = max(0.7, Math.toDegrees(atan(((v.horizonY - look.moonY) / focal).toDouble()))),
            moonW = smooth(-6.0, -10.0, el), moon = doubleArrayOf(0.050 * mk, 0.062 * mk, 0.095 * mk),
            glow = smooth(0.0, 1.0, (12 - el) / 10),
            light = i.light.light, lut = i.light.lut,
            haze = sky.haze.toDouble(), sunPart = sky.sunPart.toDouble(),
            sunX = look.sunX, sunY = look.sunY, dp = v.density,
            glare = GLARE * smooth(-5.0, 0.5, el) * (1 - night), glareColor = glareCol,
            textY = i.textBottom - 0.085f * v.h, textR = 0.125f * v.h, textCap = TEXT_CAP,
        )
    }
}

/**
 * Lights and draws the sets of the moment. Lighting runs on one background thread (the last request wins); until a set
 * is lit for the new moment, its last picture is drawn where the set now stands, so dragging the Sun never waits for it.
 */
internal class SetCloudPainter(private val assets: AssetManager) {
    /** Changes when a newly lit picture is ready: read while drawing, so the layer redraws then. */
    val version = mutableIntStateOf(0)
    private class Slot(var lit: LitCloudSet? = null, var bitmap: Bitmap? = null, var key: Any? = null, var busy: Boolean = false, var shownAt: Long = 0L)
    private val slots = HashMap<String, Slot>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect(); private val dst = RectF()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private data class Key(val light: Any, val offsetStep: Int, val weight: Int, val sunPart: Int, val w: Int, val h: Int, val rtl: Boolean)

    /** Draws the sets [legs] (high first) with their drift carried on by [glide] seconds; px of the view scaled by [scale]. */
    fun draw(canvas: android.graphics.Canvas, i: GpuSkyInputs, sky: com.noor.app.sky.SkyState, display: Double, glide: Float, scale: Float, synchronous: Boolean) {
        val v = i.view
        val focal = SetClouds.FOCAL * v.w
        val view = CloudView(v.w, v.horizonY, focal, v.rtl) { v.altitudeAt(it) }
        val now = android.os.SystemClock.uptimeMillis()
        val seen = HashSet<String>()
        var light: CloudLight? = null
        for (level in 0..2) for (leg in sky.clouds.getOrNull(level).orEmpty()) {
            val name = "${leg.set}|${leg.turned}"
            seen += name
            val offset = leg.offset + leg.speed * glide
            val slot = slots.getOrPut(name) { Slot() }
            val key = Key(i.light, Math.round(offset * 4), (leg.weight * 50).toInt(), (sky.sunPart * 50).toInt(), v.w.toInt(), v.h.toInt(), v.rtl)
            if (slot.key != key && !slot.busy) {
                val l = light ?: SetClouds.lightOf(i, sky, display).also { light = it }
                slot.busy = true
                val job = Runnable {
                    val pics = CloudSets.load(assets, leg.set)
                    val lit = pics?.let { runCatching { SetClouds.compose(it, l, leg, offset, view) }.onFailure { e -> Log.w("NoorSky", "Clouds not lit", e) }.getOrNull() }
                    val done = Runnable {
                        slot.busy = false; slot.key = key
                        if (lit != null) {
                            if (slot.lit == null) slot.shownAt = android.os.SystemClock.uptimeMillis()
                            slot.lit = lit
                            slot.bitmap = Bitmap.createBitmap(lit.px, lit.w, lit.h, Bitmap.Config.ARGB_8888)
                        }
                        version.intValue++
                    }
                    if (synchronous) done.run() else main.post(done)
                }
                if (synchronous) job.run() else LIGHT.execute(job)
            }
            val lit = slot.lit ?: continue
            val bmp = slot.bitmap ?: continue
            // Where the set stands now (its picture was lit a little way back along its drift).
            val shift = -(if (v.rtl) -1 else 1) * focal * Math.toRadians((offset - lit.offset).toDouble()).toFloat()
            val fadeIn = if (synchronous) 1f else ((now - slot.shownAt) / 600f).coerceIn(0f, 1f)
            if (fadeIn < 1f) version.intValue.let { main.postDelayed({ version.intValue++ }, 32) }
            paint.alpha = (255 * fadeIn * fadeIn * (3 - 2 * fadeIn)).toInt()
            src.set(0, 0, lit.w, lit.h)
            dst.set((lit.x0 + shift) * scale, lit.y0 * scale, (lit.x1 + shift) * scale, lit.y1 * scale)
            if (dst.right < 0f || dst.left > canvas.width) continue
            canvas.drawBitmap(bmp, src, dst, paint)
        }
        slots.keys.retainAll(seen)
    }

    private companion object { val LIGHT: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "NoorClouds").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } } }
}

/**
 * The clouds of the medium graphics (0.19.1), over the sky and under the land, on every phone (GPU or Canvas sky).
 * [sky] is the real sky's director's state for the moment shown; [display] the moment (fraction of the day); [time]
 * the ambient clock (seconds), which carries the clouds on with the wind between one minute's state and the next.
 */
@Composable
internal fun SetCloudLayer(
    inputs: () -> GpuSkyInputs?, sky: () -> com.noor.app.sky.SkyState?, display: () -> Double, time: () -> Float, parallax: () -> Float,
    /** How many seconds of the minute shown have passed when it is first shown (0 while the Sun is dragged): the
     *  director places the sets for the minute's start, and the next minute's places must follow on without a jump. */
    intoMinute: () -> Float = { 0f },
) {
    val assets = LocalContext.current.assets
    val painter = remember { SetCloudPainter(assets) }
    // The clock when the moment shown last changed: from there the sets glide on with their own speed.
    val anchor = remember { floatArrayOf(0f, Float.NaN) }
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = parallax() * 0.5f }.drawBehind {
        painter.version.intValue
        val i = inputs() ?: return@drawBehind
        val s = sky() ?: return@drawBehind
        val d = display()
        val t = time()
        if (anchor[1] != d.toFloat()) { anchor[1] = d.toFloat(); anchor[0] = t - intoMinute() }
        val glide = (t - anchor[0]).coerceIn(0f, 120f)
        drawIntoCanvas { painter.draw(it.nativeCanvas, i, s, d, glide, size.width / i.view.w, SkyCompute.synchronous) }
    })
}

/** (0.19.1) The real sky's scenery whose sky the medium graphics show over a terrain (the 0.9 mountains: the green hills'). */
internal fun skyBiomeOf(t: Terrain): com.noor.app.sky.Biome = when (t) {
    Terrain.DUNES -> com.noor.app.sky.Biome.DUNES
    Terrain.HILLS, Terrain.MOUNTAINS -> com.noor.app.sky.Biome.MEADOW
    Terrain.SEA -> com.noor.app.sky.Biome.OCEAN
    Terrain.FOREST -> com.noor.app.sky.Biome.RAINFOREST
    Terrain.ARCTIC -> com.noor.app.sky.Biome.ARCTIC
    Terrain.CANYON -> com.noor.app.sky.Biome.CANYON
}

/**
 * (0.19.1) The sky of the moment for the medium graphics: the real sky's director ([SkyDirector.at]) with the day's
 * weather [weather] over [biome], in the time of year [year]. The clouds, the overcast, the rain.
 */
internal fun mediumSkyState(scene: SkyScene, display: Double, mist: Float, biome: com.noor.app.sky.Biome, weather: Weather, year: Float): com.noor.app.sky.SkyState {
    // The hours the Sun rises and sets at, for the day's programme of skies (as RealSkyFrame.of).
    var rise = 6f; var set = 18f
    val sm = scene.samples
    for (i in 1 until sm.size) {
        val a = sm[i - 1]; val b = sm[i]
        if ((a.second < -0.83) != (b.second < -0.83)) {
            val at = (a.first + (b.first - a.first) * ((-0.83 - a.second) / (b.second - a.second))).toFloat() * 24f
            if (b.second > a.second) rise = at else set = at
        }
    }
    return SkyDirector.at(biome, weather, display, scene.elevationAt(display), year, mist, rise, set)
}
