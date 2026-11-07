package com.noor.app.ui.today

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The lie of the land for a place (0.9.6): five layers, far to near, each a skyline across the width as heights 0..1.
 * Far away a mountain range (or the farthest dunes), nearer rolling hills (or bigger dunes). Fixed for a place, so home
 * always looks like home. The GPU land shader reads these tables (and adds fine detail of its own); the prayer marks
 * sit on the same skyline; the Canvas fallback draws the same shapes.
 */
internal class LandProfile private constructor(val terrain: Terrain, private val heights: Array<FloatArray>) {
    /** Height of [layer] at fraction [u] of the width, 0..1 (linear between samples). */
    fun height(layer: Int, u: Float): Float {
        val h = heights[layer]
        val x = u.coerceIn(0f, 1f) * (N - 1)
        val i = min(x.toInt(), N - 2)
        val f = x - i
        return h[i] * (1 - f) + h[i + 1] * f
    }

    /**
     * The table for the shader: N × 2 opaque pixels; row 0 holds layers 0–2 in R, G, B, row 1 layers 3–4 in R, G (alpha
     * stays opaque: GPU sampling premultiplies by it). Heights are quantised to 8 bits (the shader smooths its slopes
     * over a few dp for that reason).
     */
    fun table(): IntArray {
        val out = IntArray(N * 2)
        fun q(v: Float) = (v.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
        for (i in 0 until N) {
            out[i] = (0xFF shl 24) or (q(heights[0][i]) shl 16) or (q(heights[1][i]) shl 8) or q(heights[2][i])
            out[N + i] = (0xFF shl 24) or (q(heights[3][i]) shl 16) or (q(heights[4][i]) shl 8)
        }
        return out
    }

    /** Where each layer lies, for a header whose horizon is at [horizonY] and bottom at [h] (px), [density] px per dp. */
    class Geometry(val base: FloatArray, val amp: FloatArray, val distKm: FloatArray)

    fun geometry(horizonY: Float, h: Float, density: Float): Geometry {
        val spec = if (terrain == Terrain.DUNES) DUNES else HILLS
        val spread = (((h - horizonY) / density - 12f) / DEEPEST).coerceIn(0.55f, 1.4f)
        return Geometry(
            FloatArray(LAYERS) { horizonY + spec[it][1] * spread * density },
            FloatArray(LAYERS) { spec[it][0] * density },
            if (terrain == Terrain.DUNES) floatArrayOf(22f, 7f, 2.8f, 1.1f, 0.35f) else floatArrayOf(48f, 15f, 6f, 2.2f, 0.6f),
        )
    }

    /**
     * The top of the land at [x] (px) across a width [w]: where it meets the sky. Where the Sun rises and sets
     * ([riseX], [setX]) the land is kept below the horizon line [horizonY], as the land shader does.
     */
    fun skylineAt(x: Float, w: Float, g: Geometry, horizonY: Float = 0f, density: Float = 1f, riseX: Float = -1e5f, setX: Float = -1e5f): Float {
        val open = horizonOpening(x, riseX, setX, density)
        var m = Float.MAX_VALUE
        for (l in 0 until LAYERS) {
            val y0 = g.base[l] - g.amp[l] * height(l, x / w)
            val cap = maxOf((g.base[l] - horizonY - density) / g.amp[l], 0f)
            val y = g.base[l] - g.amp[l] * (height(l, x / w).let { it + (minOf(it, cap) - it) * open })
            m = min(m, if (open > 0f) y else y0)
        }
        return m
    }

    companion object {
        const val N = 1024
        const val LAYERS = 5
        /** (height, base below the horizon) in dp, far to near. */
        private val HILLS = arrayOf(floatArrayOf(40f, 3f), floatArrayOf(28f, 16f), floatArrayOf(24f, 32f), floatArrayOf(22f, 50f), floatArrayOf(24f, 72f))
        private val DUNES = arrayOf(floatArrayOf(16f, 3f), floatArrayOf(20f, 16f), floatArrayOf(24f, 32f), floatArrayOf(26f, 50f), floatArrayOf(26f, 72f))
        private const val DEEPEST = 72f

        private val cache = HashMap<Pair<Long, Terrain>, LandProfile>()

        fun of(seed: Long, terrain: Terrain): LandProfile = synchronized(cache) {
            cache.getOrPut(seed to terrain) { build(seed, terrain) }
        }

        /** Smooth value noise on a loop of [n] random values. */
        private class Noise(rnd: java.util.Random, private val n: Int = 64) {
            private val v = FloatArray(n) { rnd.nextFloat() }
            fun at(x: Float): Float {
                val i = floor(x).toInt(); val f = x - i
                val u = f * f * (3 - 2 * f)
                return v[Math.floorMod(i, n)] * (1 - u) + v[Math.floorMod(i + 1, n)] * u
            }
        }

        private fun normalise(a: FloatArray, lo: Float): FloatArray {
            val mn = a.min(); val mx = a.max()
            return FloatArray(a.size) { lo + (1 - lo) * (a[it] - mn) / max(mx - mn, 1e-6f) }
        }

        private fun build(seed: Long, terrain: Terrain): LandProfile {
            val rnd = java.util.Random(seed * 31 + 17)
            val layers = Array(LAYERS) { layer ->
                val a = FloatArray(N)
                when {
                    terrain == Terrain.DUNES -> {
                        // A dune: a long windward slope up to a sharp crest, a steeper slip face down; nearer dunes are
                        // bigger and further apart.
                        val f = floatArrayOf(3.4f, 2.5f, 1.8f, 1.25f, 0.85f)[layer]
                        val ph = rnd.nextFloat(); val n1 = Noise(rnd); val n2 = Noise(rnd)
                        for (i in 0 until N) {
                            val u = i / (N - 1f)
                            val x = u * f + ph + 0.18f * n1.at(u * 6)
                            val p = x - floor(x)
                            val k = 0.70f + 0.08f * (n2.at(u * 3) - 0.5f)
                            var h = if (p < k) (p / k).pow(1.15f) else 1 - ((p - k) / (1 - k)).pow(0.85f)
                            h = h * h * (3 - 2 * h)
                            a[i] = h * (0.55f + 0.45f * n2.at(u * 2.2f + 3)) + 0.08f * n1.at(u * 2 + 9)
                        }
                        normalise(a, 0f)
                    }
                    layer == 0 -> {
                        // A range: a broad massif with several summits, sharp ridgelines, saddles between.
                        val ns = Array(6) { Noise(rnd) }; val broad = Noise(rnd)
                        for (i in 0 until N) {
                            val u = i / (N - 1f)
                            var s = 0f; var amp = 0.5f; var fq = 3.1f; var w = 1f
                            for (o in 0 until 6) {
                                val v = 1 - abs(ns[o].at(u * fq) * 2 - 1)
                                s += amp * v * v * w; w = min(1f, v * 1.8f); amp *= 0.48f; fq *= 2.07f
                            }
                            a[i] = s * (0.55f + 0.45f * broad.at(u * 1.6f))
                        }
                        normalise(a, 0f)
                    }
                    else -> {
                        // Rolling hills, broader the nearer they are.
                        val f = floatArrayOf(0f, 1.7f, 1.25f, 0.95f, 0.65f)[layer]
                        val ns = Array(3) { Noise(rnd) }
                        for (i in 0 until N) {
                            val u = i / (N - 1f)
                            a[i] = 0.62f * ns[0].at(u * f * 2) + 0.28f * ns[1].at(u * f * 4.3f) + 0.10f * ns[2].at(u * f * 9.7f)
                        }
                        normalise(a, 0.05f)
                    }
                }
            }
            return LandProfile(terrain, layers)
        }
    }
}
