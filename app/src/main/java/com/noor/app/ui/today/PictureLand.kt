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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import com.noor.app.sky.LightWeights
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The medium graphics' land drawn from the real sky's own pictures (0.20 candidate). The owner wants Medium
 * "absolutely breathtaking"; next to the real sky's sceneries (rendered in Blender from real elevation data and scans)
 * the 0.19 layered land drawn by the shader was plainly a painting. So, as the clouds already are ([SetClouds]): the
 * scenery's baked pictures (assets/sky2/land/<id>: its colours, its light from a white sky, its distance, its shading
 * under a set of unit Suns, the mask of its water) are read at half their size on a background thread, only the band the
 * medium header shows (from the skyline down to [ROWS_BELOW] px of the real sky's frame under its horizon), lit for the
 * moment on the CPU with the medium sky's own light (the real sky's `landAt`: sunlight and the glow round a low Sun on
 * its colours, skylight on its sky picture, the haze and fog of the air between), and drawn as one bitmap where the real
 * sky puts it: its horizon on the medium sky's horizon, its width the screen's.
 *
 * Water (the lake, the fjord, the pool: the mask's red; the open sea: where the ground the picture shows lies under the
 * sea's level) is a still mirror of the sky and of the land above it, darker and bluer than what it mirrors, more so where
 * it is seen from above (a soft Fresnel), with the Sun's light on it; no live waves. Wet sand mirrors a little.
 *
 * Light: works on every phone (GPU sky or Canvas sky; no OpenGL ES 3); lit again off the main thread only when the light
 * of the moment changes; a new picture cross-fades over the last. Where a scenery's pictures cannot be read the 0.19
 * layered land is drawn instead.
 */
internal object LandPictures {
    /** The pictures are read at 1/SAMPLE of their size. */
    const val SAMPLE = 2
    /** The real sky's frame: its width and its horizon row (land.json's `top` and `horizon` are in it). */
    const val FRAME_W = 1080
    const val FRAME_H0 = 1056
    /** How far under the horizon (frame px) the medium header can reach: 0.41 of the width. */
    const val ROWS_BELOW = 440

    class Pictures(
        val id: String, val w: Int, val h: Int,
        /** The band's first row in the frame, and its rows (frame px). */
        val top: Int, val rows: Int,
        val eye: Double, val sMax: Double, val waterLevel: Double,
        /** Colours under white light (sRGB bytes, RGB), how much of each pixel is land, the land lit by a white sky (sRGB, RGB). */
        val alb: ByteArray, val alpha: ByteArray, val k: ByteArray,
        /** How far each pixel is (m). */
        val dist: FloatArray,
        /** Under water: 0..255 (null: no water). */
        val water: ByteArray?,
        private val read: (String) -> ByteArray?,
    ) {
        private val suns = LinkedHashMap<String, ByteArray?>(16, 0.75f, true)

        /** The picture lit by the Sun [name] (l0 … r7, top, gl, gr), read when first asked for. */
        @Synchronized fun sun(name: String): ByteArray? {
            if (suns.containsKey(name)) return suns[name]
            val p = read("s_$name")
            suns[name] = p
            while (suns.size > 10) suns.remove(suns.keys.first())
            return p
        }
    }

    private val sets = LinkedHashMap<String, Pictures>(4, 0.75f, true)
    private val failed = HashSet<String>()

    fun hasFailed(id: String): Boolean = synchronized(sets) { id in failed }

    /** The real sky's land pictures for a medium terrain (null where there are none: the 0.9 mountains). */
    fun idOf(t: Terrain): String? = when (t) {
        Terrain.DUNES -> "dunes"; Terrain.HILLS -> "meadow"; Terrain.SEA -> "ocean"
        Terrain.FOREST -> "pnw"; Terrain.ARCTIC -> "arctic"; Terrain.CANYON -> "canyon"; Terrain.MOUNTAINS -> null
    }

    /** Reads a scenery's band (blocking). */
    fun load(assets: AssetManager, id: String): Pictures? {
        synchronized(sets) { sets[id]?.let { return it }; if (id in failed) return null }
        val p = runCatching { read(assets, id) }.onFailure { Log.w("NoorSky", "Land pictures $id unavailable", it) }.getOrNull()
        synchronized(sets) { if (p == null) failed += id else { sets[id] = p; while (sets.size > 2) sets.remove(sets.keys.first()) } }
        return p
    }

    private fun read(assets: AssetManager, id: String): Pictures {
        val json = org.json.JSONObject(assets.open("sky2/land/$id/land.json").bufferedReader().use { it.readText() })
        val width = json.getInt("width"); val height = json.getInt("height"); val top = json.getInt("top")
        val rows = min(height, FRAME_H0 + ROWS_BELOW - top)
        val w = (width + SAMPLE - 1) / SAMPLE; val h = (rows + SAMPLE - 1) / SAMPLE
        val eye = json.optJSONArray("eye")?.optDouble(2, 2.0) ?: 2.0
        val waterLevel = if (json.isNull("water")) 0.0 else json.optDouble("water", 0.0)
        // The band of one picture at w × h, ARGB, not premultiplied. Every picture covers the whole land picture whatever
        // its own size (the real sky samples them all by the same coordinates): it is read at the sample that brings it
        // nearest w across, and scaled the rest of the way if need be ([filter] off for the distance's two bytes).
        fun band(name: String, filter: Boolean = true): IntArray? {
            val path = listOf("sky2/land/$id/$name.webp", "sky2/land/$id/$name.png").firstOrNull { p -> runCatching { assets.open(p).close() }.isSuccess } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            val s = max(1, Integer.highestOneBit(max(1, bounds.outWidth / w)))
            val o = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false; inPremultiplied = false }
            var b = assets.open(path).use { BitmapFactory.decodeStream(it, null, o) } ?: return null
            val bh = ((rows.toLong() * b.height + height - 1) / height).toInt().coerceIn(1, b.height)
            if (b.width != w || bh != h) {
                val cut = Bitmap.createBitmap(b, 0, 0, b.width, bh)
                val scaled = Bitmap.createScaledBitmap(cut, w, h, filter)
                if (cut !== b) b.recycle(); if (scaled !== cut) cut.recycle()
                b = scaled
                val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h); b.recycle(); return px
            }
            val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h); b.recycle(); return px
        }
        val a = band("a") ?: error("no colours")
        val alb = ByteArray(w * h * 3); val alpha = ByteArray(w * h)
        for (i in 0 until w * h) {
            val c = a[i]
            alb[i * 3] = (c shr 16).toByte(); alb[i * 3 + 1] = (c shr 8).toByte(); alb[i * 3 + 2] = c.toByte(); alpha[i] = (c ushr 24).toByte()
        }
        val kp = band("k") ?: error("no sky light")
        val k = ByteArray(w * h * 3)
        for (i in 0 until w * h) { val c = kp[i]; k[i * 3] = (c shr 16).toByte(); k[i * 3 + 1] = (c shr 8).toByte(); k[i * 3 + 2] = c.toByte() }
        // Distance (m): 16 bits, logarithmic (scene.frag, landDist).
        val gp = band("g", filter = false) ?: error("no distance")
        val dist = FloatArray(w * h) { val c = gp[it]; 2.0.pow((((c shr 16) and 0xFF) * 256 + ((c shr 8) and 0xFF)) / 65535.0 * 18.0).toFloat() }
        // Water: the mask's red where the scenery has a mask; on the open sea (no mask), where the ground the picture
        // shows lies under the sea's level, as the real sky's beach works it out.
        val water: ByteArray? = when {
            json.optBoolean("mask", false) -> band("m")?.let { m -> ByteArray(w * h) { ((m[it] shr 16) and 0xFF).toByte() } }
            waterLevel > 0 -> {
                val f = 1.15 * width
                ByteArray(w * h) { i ->
                    val x = i % w; val y = i / w
                    val rx = (x + 0.5) * SAMPLE - width / 2.0; val ry = FRAME_H0 - (top + (y + 0.5) * SAMPLE)
                    val zb = waterLevel + ry / sqrt(rx * rx + ry * ry + f * f) * dist[i]
                    (255 * smooth(0.12, -0.05, zb)).toInt().toByte()
                }
            }
            else -> null
        }
        return Pictures(id, w, h, top, rows, eye, json.optDouble("sMax", 2.0), waterLevel, alb, alpha, k, dist, water) { name ->
            band(name)?.let { s -> ByteArray(w * h) { ((s[it] shr 8) and 0xFF).toByte() } }
        }
    }

    internal fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
}

/** The light of the moment for the land (px of the sky view). */
internal class LandLight(
    val el: Double, val side: Double, val moonLight: Boolean, val moonEl: Double, val glow: Double, val moonW: Double, val moon: DoubleArray,
    val light: Atmosphere.Light, val lut: IntArray, val night: Double,
    /** SkyState: how much of the Sun's light the sky takes from the land, how overcast, how hazy, how much fog. */
    val shade: Double, val overcast: Double, val haze: Double, val fog: Double,
    val sunX: Float, val sunY: Float, val dp: Float,
    /** Where the Sun is seen (a unit direction, the camera's frame: x right, y up, z ahead). */
    val sunDir: DoubleArray,
    /** The Sun's light on the water (0..1) and its colour (display). */
    val glade: Double, val gladeColor: DoubleArray,
    /** The page under the header (display), the header's height (px). */
    val page: DoubleArray, val bottom: Float,
)

/** The land lit for a moment: its picture (ARGB, not premultiplied) and where it stands (px of the view). */
internal class LitLand(val px: IntArray, val w: Int, val h: Int, val x0: Float, val x1: Float, val y0: Float, val y1: Float)

internal object PictureLand {
    private val LIN8 = DoubleArray(256) { (it / 255.0).pow(2.2) }

    /** Lights [p] for the moment, for a view [vw] px wide with its horizon at [hy] (mirrored when [rtl]). */
    fun compose(p: LandPictures.Pictures, l: LandLight, vw: Float, hy: Float, rtl: Boolean, altitudeAt: (Float) -> Double): LitLand {
        val w = p.w; val h = p.h
        val s = LandPictures.SAMPLE
        val k = vw / LandPictures.FRAME_W
        val f = 1.15 * vw
        val m = if (rtl) -1 else 1
        val lt = l.light
        val lw = LightWeights.of(if (l.moonLight) l.moonEl else max(l.el, 0.7), l.side * m, false, l.glow.toFloat())
        val pics = ArrayList<ByteArray>(5); val picW = ArrayList<Double>(5)
        for (i in 0 until 5) if (lw.w[i] > 0.002f) p.sun(lw.names[i])?.let { pics += it; picW += lw.w[i] * p.sMax }
        val glows = ArrayList<ByteArray>(2); val glowW = ArrayList<Double>(2)
        if (lw.glow[0] > 0.002f) p.sun("gl")?.let { glows += it; glowW += lw.glow[0] * p.sMax }
        if (lw.glow[1] > 0.002f) p.sun("gr")?.let { glows += it; glowW += lw.glow[1] * p.sMax }
        val np = pics.size; val ng = glows.size
        val sunK = 0.70 + 0.14 * LandPictures.smooth(6.0, 28.0, l.el)
        val light = DoubleArray(3) { lt.sunGround[it] * sunK * (1 - l.moonW) + l.moon[it] * l.moonW }
        val nightBlue = doubleArrayOf(0.034, 0.052, 0.105)
        val amb = DoubleArray(3) { lt.ambLow[it] + (lt.ambTop[it] - lt.ambLow[it]) * 0.6 + nightBlue[it] * l.night * 0.35 }
        val la = 0.2126 * amb[0] + 0.7152 * amb[1] + 0.0722 * amb[2]
        for (c in 0..2) amb[c] += (la * 2.3 - amb[c]) * l.overcast * 0.6
        val sh = 1 - 0.85 * l.shade
        val shadeK = 0.78 + 0.22 * max(l.night, LandPictures.smooth(10.0, 0.0, l.el))
        val hour = LandPictures.smooth(10.0, 2.5, l.el) * LandPictures.smooth(-8.0, -2.5, l.el)
        val lift = 1 + 0.3 * hour
        val ya = p.eye * 0.001
        val hh = 1.3; val hm = 0.07
        // The air's colour at the horizon in each column (linear): the horizon's and a few degrees up, as the real sky.
        val hazeAt = DoubleArray(w * 3)
        for (ox in 0 until w) {
            val xs = ((ox + 0.5) * s * k).toFloat().let { if (rtl) vw - it else it }
            val a = Atmosphere.sample(l.lut, (xs / vw).toDouble(), Atmosphere.rowOf(0.8)); val b = Atmosphere.sample(l.lut, (xs / vw).toDouble(), Atmosphere.rowOf(4.5))
            for (c in 0..2) {
                val sh8 = 16 - 8 * c
                hazeAt[ox * 3 + c] = Atmosphere.linear(((a shr sh8) and 0xFF) / 255.0) * 0.4 + Atmosphere.linear(((b shr sh8) and 0xFF) / 255.0) * 0.6
            }
        }
        val lit = FloatArray(w * h * 3); val kind = ByteArray(w * h)
        val out = IntArray(w * h)
        val tmp = DoubleArray(3); val disp = DoubleArray(3)
        val sd = l.sunDir
        var lastRow = h
        for (oy in 0 until h) {
            val sy = hy + ((p.top + (oy + 0.5) * s) - LandPictures.FRAME_H0) * k
            if (sy > l.bottom + 2) { lastRow = oy; break }
            val ry = (hy - sy).toDouble()
            for (ox in 0 until w) {
                val i = oy * w + ox
                val al = (p.alpha[i].toInt() and 0xFF) / 255.0
                if (al < 0.004) continue
                val rx = (ox + 0.5) * s * k - vw / 2.0
                val rl = sqrt(rx * rx + ry * ry + f * f)
                val rdx = rx / rl; val rdy = ry / rl; val rdz = f / rl
                var sS = 0.0
                for (q in 0 until np) { val v = (pics[q][i].toInt() and 0xFF) / 255.0; sS += v * v * picW[q] }
                var sG = 0.0
                for (q in 0 until ng) { val v = (glows[q][i].toInt() and 0xFF) / 255.0; sG += v * v * glowW[q] }
                // The air between: far things fade into the colour of the air at the horizon (scene.frag, landAt).
                val t = p.dist[i].toDouble()
                val yb = max(p.eye + rdy * t, 0.0) * 0.001
                val dy = yb - ya
                val layer = if (kotlin.math.abs(dy) > 1e-3) hh / dy * (exp(-ya / hh) - exp(-yb / hh)) else exp(-ya / hh)
                val low = if (kotlin.math.abs(dy) > 1e-3) hm / dy * (exp(-ya / hm) - exp(-yb / hm)) else exp(-ya / hm)
                val fog = min(1 - exp(-t * 0.001 * (0.006 + (0.022 + 0.024 * (l.haze - 1)) * layer + 0.5 * l.fog * low)), 0.94) * 0.95
                var gs = max(0.0, rdx * m * sd[0] + rdy * sd[1] + rdz * sd[2]); gs *= gs; gs *= gs; gs *= gs
                val hx = if (rtl) w - 1 - ox else ox
                for (c in 0..2) {
                    val alb = LIN8[p.alb[i * 3 + c].toInt() and 0xFF]; val kk = LIN8[p.k[i * 3 + c].toInt() and 0xFF]
                    val v = (alb * (sS * light[c] * sh + sG * lt.glow[c]) + kk * amb[c] * shadeK) * lift
                    val air = (hazeAt[hx * 3 + c] * (1 + 0.35 * gs) * 0.74 + amb[c] * 0.26) * 0.93
                    tmp[c] = v + (air - v) * fog
                }
                SetClouds.toneFast(tmp, disp)
                val j = oy * w + (if (rtl) w - 1 - ox else ox)
                lit[j * 3] = disp[0].toFloat(); lit[j * 3 + 1] = disp[1].toFloat(); lit[j * 3 + 2] = disp[2].toFloat()
                kind[j] = if (p.water != null && (p.water[i].toInt() and 0xFF) > 20) 2 else 1
                out[j] = ((al * 255 + 0.5).toInt() shl 24)
            }
        }
        // Still water: the sky and the land beyond it upside down, about the water's far edge in each column; darker and
        // bluer than what it mirrors, more so where it is seen from above (a soft Fresnel); the Sun's light on it.
        if (p.water != null) {
            val deepIn = DoubleArray(3) { lt.ambTop[it] * 0.10 + lt.sunGround[it] * 0.012 }
            val deep = DoubleArray(3); Atmosphere.tone(deepIn, deep)
            val blue = doubleArrayOf(0.55, 0.85, 0.95); val keep = doubleArrayOf(0.86, 0.93, 0.97)
            for (c in 0..2) deep[c] *= blue[c]
            val refl = DoubleArray(3)
            for (ox in 0 until w) {
                val src = if (rtl) w - 1 - ox else ox
                var edge = -1
                for (oy in 0 until lastRow) {
                    val j = oy * w + ox
                    if (kind[j].toInt() != 2) continue
                    val wet = (p.water[oy * w + src].toInt() and 0xFF) / 255.0
                    if (edge < 0 && wet > 0.5) edge = oy
                    if (edge < 0) continue
                    val sy = hy + ((p.top + (oy + 0.5) * s) - LandPictures.FRAME_H0) * k
                    val my = 2 * edge - oy - 1
                    val xs = ((ox + 0.5) * s * k).toFloat()
                    if (my >= 0 && kind[my * w + ox].toInt() == 1 && (out[my * w + ox] ushr 24) > 127) {
                        for (c in 0..2) refl[c] = lit[(my * w + ox) * 3 + c].toDouble()
                    } else {
                        val yEdge = hy + (p.top + edge * s - LandPictures.FRAME_H0) * k
                        val c = Atmosphere.sample(l.lut, (xs / vw).toDouble(), Atmosphere.rowOf(altitudeAt(min((2 * yEdge - sy).toFloat(), hy - 0.5f))))
                        refl[0] = ((c shr 16) and 0xFF) / 255.0; refl[1] = ((c shr 8) and 0xFF) / 255.0; refl[2] = (c and 0xFF) / 255.0
                    }
                    val dep = kotlin.math.atan((sy - hy) / f)
                    val fres = 0.02 + 0.98 * (1 - sin(max(dep, 0.002))).pow(5)
                    val mixK = (0.30 + 0.70 * fres).coerceIn(0.0, 1.0) * 0.82
                    val sea = LandPictures.smooth(0.80, 0.98, wet); val film = 0.55 * LandPictures.smooth(0.08, 0.8, wet)
                    val q = max(sea, film)
                    val lane = if (l.glade > 0) {
                        val width = (5 + (sy - hy) / l.dp * 0.6) * l.dp
                        val d = (xs - l.sunX) / width
                        exp(-d * d) * l.glade
                    } else 0.0
                    for (c in 0..2) {
                        var v = deep[c] + (refl[c] * keep[c] - deep[c]) * mixK
                        if (lane > 0) v = 1 - (1 - v) * (1 - l.gladeColor[c] * lane)
                        val j3 = j * 3 + c
                        lit[j3] = (lit[j3] + (v - lit[j3]) * q).toFloat()
                    }
                    out[j] = out[j] or (0xFF shl 24)
                }
            }
        }
        // The nearest land fades into the page at the foot of the header (in linear light).
        val pageLin = DoubleArray(3) { Atmosphere.linear(l.page[it]) }
        for (oy in 0 until lastRow) {
            val sy = hy + ((p.top + (oy + 0.5) * s) - LandPictures.FRAME_H0) * k
            val fd = LandPictures.smooth((l.bottom - 22 * l.dp).toDouble(), (l.bottom - 1 * l.dp).toDouble(), sy.toDouble())
            for (ox in 0 until w) {
                val j = oy * w + ox
                val a = out[j] ushr 24
                if (a == 0) continue
                var argb = a shl 24
                for (c in 0..2) {
                    var v = lit[j * 3 + c].toDouble()
                    if (fd > 0) v = SetClouds.srgbFast(SetClouds.linearFast(v) * (1 - fd) + pageLin[c] * fd)
                    argb = argb or ((v.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl (16 - 8 * c))
                }
                out[j] = argb
            }
        }
        return LitLand(out, w, h, 0f, vw, hy + (p.top - LandPictures.FRAME_H0) * k, hy + (p.top + h * s - LandPictures.FRAME_H0) * k)
    }

    /** The light of the moment for the land, from what the medium sky already knows. */
    fun lightOf(i: GpuSkyInputs, sky: com.noor.app.sky.SkyState, display: Double, bottom: Float): LandLight {
        val v = i.view; val look = i.look
        val focal = 1.15f * v.w
        val el = look.elevation
        val moonLight = el < -6.0
        val elSeen = if (el > 0) kotlin.math.atan(((v.horizonY - look.sunY) / focal).toDouble()) else Math.toRadians(el)
        val az = ((look.sunX - v.w / 2) / focal).toDouble()
        val mk = 1.6 * look.moonIllumination * look.moonVisibility
        val gladeIn = DoubleArray(3) { i.light.light.sunGround[it] * 1.2 }
        val glade = DoubleArray(3).also { Atmosphere.tone(gladeIn, it) }
        for (c in 0..2) glade[c] = 0.7 * glade[c] + 0.3
        return LandLight(
            el = el,
            side = if (moonLight) ((look.moonX - v.w / 2) / (0.2f * v.w)).toDouble().coerceIn(-1.0, 1.0)
            else ((display - i.scene.noonFraction) / 0.07).coerceIn(-1.0, 1.0) * (if (v.rtl) -1 else 1),
            moonLight = moonLight, moonEl = max(0.7, Math.toDegrees(kotlin.math.atan(((v.horizonY - look.moonY) / focal).toDouble()))),
            glow = LandPictures.smooth(0.0, 1.0, (12 - el) / 10), moonW = LandPictures.smooth(-6.0, -10.0, el),
            moon = doubleArrayOf(0.050 * mk, 0.062 * mk, 0.095 * mk),
            light = i.light.light, lut = i.light.lut, night = i.light.light.night,
            shade = sky.shade.toDouble(), overcast = sky.overcast.toDouble(), haze = sky.haze.toDouble(), fog = sky.fog.toDouble(),
            sunX = look.sunX, sunY = look.sunY, dp = v.density,
            sunDir = doubleArrayOf(kotlin.math.cos(elSeen) * kotlin.math.sin(az), kotlin.math.sin(elSeen), kotlin.math.cos(elSeen) * kotlin.math.cos(az)),
            glade = LandPictures.smooth(0.0, 3.0, el) * (1 - 0.6 * LandPictures.smooth(20.0, 60.0, el)) * (1 - sky.overcast) * 0.5,
            gladeColor = glade,
            page = doubleArrayOf(i.page.red.toDouble(), i.page.green.toDouble(), i.page.blue.toDouble()), bottom = bottom,
        )
    }
}

/**
 * Lights and draws the land of the moment ([PictureLand]). Lighting runs on one background thread (the last request
 * wins); a newly lit picture cross-fades over the last. [solid] turns true once the first picture is fully shown (until
 * then the 0.19 layered land is drawn under it); [failed] if the scenery's pictures cannot be read.
 */
internal class PictureLandPainter(private val assets: AssetManager, val id: String) {
    val version = mutableIntStateOf(0)
    val solid = mutableStateOf(false)
    val failed = mutableStateOf(LandPictures.hasFailed(id))
    private var current: Bitmap? = null; private var currentLit: LitLand? = null
    private var previous: Bitmap? = null; private var previousLit: LitLand? = null
    private var shownAt = 0L
    private var key: Any? = null
    private var busy = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect(); private val dst = RectF()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private data class Key(val light: Any, val shade: Int, val overcast: Int, val fog: Int, val w: Int, val h: Int, val rtl: Boolean, val page: Long)

    fun draw(canvas: android.graphics.Canvas, i: GpuSkyInputs, sky: com.noor.app.sky.SkyState, display: Double, scale: Float, synchronous: Boolean) {
        val v = i.view
        val now = android.os.SystemClock.uptimeMillis()
        val k = Key(i.light, (sky.shade * 40).toInt(), (sky.overcast * 40).toInt(), (sky.fog * 40).toInt(), v.w.toInt(), v.h.toInt(), v.rtl, i.page.value.toLong())
        if (k != key && !busy && !failed.value) {
            busy = true
            val l = PictureLand.lightOf(i, sky, display, v.h)
            val job = Runnable {
                val pics = LandPictures.load(assets, id)
                val lit = pics?.let { runCatching { PictureLand.compose(it, l, v.w, v.horizonY, v.rtl) { y -> v.altitudeAt(y) } }.onFailure { e -> Log.w("NoorSky", "Land not lit", e) }.getOrNull() }
                val done = Runnable {
                    busy = false; key = k
                    if (pics == null) failed.value = true
                    if (lit != null) {
                        previous = current; previousLit = currentLit
                        currentLit = lit
                        current = Bitmap.createBitmap(lit.px, lit.w, lit.h, Bitmap.Config.ARGB_8888)
                        shownAt = android.os.SystemClock.uptimeMillis()
                    }
                    version.intValue++
                }
                if (synchronous) done.run() else main.post(done)
            }
            if (synchronous) job.run() else LIGHT.execute(job)
        }
        val lit = currentLit ?: return
        val bmp = current ?: return
        // A new picture cross-fades over the last (the first one fades in over the layered land).
        val fade = if (synchronous) 1f else ((now - shownAt) / (if (previous == null) 600f else 350f)).coerceIn(0f, 1f)
        if (fade < 1f) main.postDelayed({ version.intValue++ }, 32)
        else if (!solid.value) { solid.value = true; previous = null; previousLit = null }
        val prev = previous; val prevLit = previousLit
        if (fade < 1f && prev != null && prevLit != null) {
            paint.alpha = 255
            src.set(0, 0, prevLit.w, prevLit.h)
            dst.set(prevLit.x0 * scale, prevLit.y0 * scale, prevLit.x1 * scale, prevLit.y1 * scale)
            canvas.drawBitmap(prev, src, dst, paint)
        }
        paint.alpha = (255 * fade * fade * (3 - 2 * fade)).toInt()
        src.set(0, 0, lit.w, lit.h)
        dst.set(lit.x0 * scale, lit.y0 * scale, lit.x1 * scale, lit.y1 * scale)
        canvas.drawBitmap(bmp, src, dst, paint)
        if (fade >= 1f && prev != null) { previous = null; previousLit = null }
    }

    private companion object { val LIGHT: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "NoorLand").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } } }
}

/** (0.20 candidate) The medium graphics' land from the real sky's pictures: see [PictureLand]. */
@Composable
internal fun PictureLandLayer(painter: PictureLandPainter, inputs: () -> GpuSkyInputs?, sky: () -> com.noor.app.sky.SkyState?, display: () -> Double, lift: () -> Float) {
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = lift() }.drawBehind {
        painter.version.intValue
        val i = inputs() ?: return@drawBehind
        val s = sky() ?: return@drawBehind
        drawIntoCanvas { painter.draw(it.nativeCanvas, i, s, display(), size.width / i.view.w, SkyCompute.synchronous) }
    })
}
