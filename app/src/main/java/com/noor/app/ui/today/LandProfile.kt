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
        val spec = when (terrain) {
            Terrain.DUNES -> DUNES; Terrain.HILLS -> HILLS; Terrain.MOUNTAINS -> MOUNTAINS; Terrain.SEA -> SEA
            Terrain.FOREST -> FOREST; Terrain.ARCTIC -> ARCTIC; Terrain.CANYON -> CANYON
        }
        val spread = (((h - horizonY) / density - 12f) / DEEPEST).coerceIn(0.55f, 1.4f)
        return Geometry(
            FloatArray(LAYERS) { horizonY + spec[it][1] * spread * density },
            FloatArray(LAYERS) { spec[it][0] * density },
            when (terrain) {
                Terrain.DUNES -> floatArrayOf(22f, 7f, 2.8f, 1.1f, 0.35f)
                Terrain.HILLS -> floatArrayOf(40f, 12f, 4f, 1.2f, 0.4f)
                Terrain.MOUNTAINS -> floatArrayOf(34f, 14f, 6f, 2.2f, 0.6f)
                Terrain.SEA -> floatArrayOf(40f, 10f, 3f, 0.8f, 0.3f)
                Terrain.FOREST -> floatArrayOf(22f, 8f, 3f, 0.9f, 0.3f)
                Terrain.ARCTIC -> floatArrayOf(10f, 5f, 3f, 0.8f, 0.3f)
                Terrain.CANYON -> floatArrayOf(20f, 6f, 3f, 1.2f, 0.4f)
            },
        )
    }

    /** True where layer [layer] is open water (the sea's far layers, away from a headland; the forest's lake, the fjord). */
    fun water(layer: Int, u: Float): Boolean =
        (terrain == Terrain.SEA && layer <= 2 && height(layer, u) < 0.02f) || (layer == LAKE && hasLake(terrain))

    /**
     * (0.19.1) The top of the land at [x] (px) across a width [w], as the land shader draws it (px from the top): the
     * highest of the layers' ridges there ([Geometry.base] less [Geometry.amp] times the ridge's height). The fine
     * relief the shader adds along a skyline (crags, scrub, the treeline's single trees: a few dp) is left out.
     * Every skyline is natural: the land no longer opens onto the true horizon where the Sun rises and sets.
     */
    fun skylineY(x: Float, w: Float, g: Geometry): Float {
        var m = Float.MAX_VALUE
        val u = x / w
        for (l in 0 until LAYERS) m = min(m, g.base[l] - g.amp[l] * height(l, u))
        return m
    }

    companion object {
        const val N = 1024
        const val LAYERS = 5
        /** (height, base below the horizon) in dp, far to near. */
        /** (0.21) The green hills: far soft hills, rounded hills over a reservoir (layer [LAKE]), a near shore, a bank. */
        private val HILLS = arrayOf(floatArrayOf(26f, 3f), floatArrayOf(34f, 13f), floatArrayOf(1f, 24f), floatArrayOf(30f, 52f), floatArrayOf(18f, 74f))
        private val DUNES = arrayOf(floatArrayOf(16f, 3f), floatArrayOf(20f, 16f), floatArrayOf(24f, 32f), floatArrayOf(26f, 50f), floatArrayOf(26f, 72f))
        /** Tall far ranges, then forested ridges, then foothills (0.9.8). */
        private val MOUNTAINS = arrayOf(floatArrayOf(58f, 2f), floatArrayOf(40f, 14f), floatArrayOf(30f, 30f), floatArrayOf(24f, 50f), floatArrayOf(24f, 72f))
        /**
         * The sea (0.9.8): the water reaches the true horizon (base 0), where a distant headland may rise at one side;
         * the next two layers are water too; then a low shore and the beach in front.
         */
        private val SEA = arrayOf(floatArrayOf(16f, 0f), floatArrayOf(1f, 8f), floatArrayOf(1f, 20f), floatArrayOf(12f, 54f), floatArrayOf(18f, 74f))
        /**
         * The forest lake (0.19): a far range with snow on its summits, a wooded ridge across the lake, the lake (layer
         * [LAKE], flat), then the near shore, its tall conifers standing at one side or both, and a low bank in front.
         */
        private val FOREST = arrayOf(floatArrayOf(40f, 2f), floatArrayOf(18f, 12f), floatArrayOf(1f, 24f), floatArrayOf(30f, 54f), floatArrayOf(16f, 74f))
        /** The far north (0.19): two snow-covered ranges rising out of a fjord's still water, a shore of snow and rocks. */
        private val ARCTIC = arrayOf(floatArrayOf(52f, 3f), floatArrayOf(36f, 14f), floatArrayOf(1f, 22f), floatArrayOf(16f, 56f), floatArrayOf(14f, 74f))
        /** Red rock (0.19): a far rim, buttes and mesas with sheer walls over their slopes of scree, ledges, the floor. */
        private val CANYON = arrayOf(floatArrayOf(16f, 3f), floatArrayOf(60f, 12f), floatArrayOf(10f, 30f), floatArrayOf(18f, 52f), floatArrayOf(14f, 74f))
        private const val DEEPEST = 72f
        /** The layer that is the lake (forest) or the fjord (arctic): flat water. */
        const val LAKE = 2

        /** The sceneries with still water in their middle distance (0.19). */
        fun hasLake(t: Terrain) = t == Terrain.FOREST || t == Terrain.ARCTIC || t == Terrain.HILLS

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
            return FloatArray(a.size) { (lo + (1 - lo) * (a[it] - mn) / max(mx - mn, 1e-6f)).coerceIn(0f, 1f) }
        }

        private fun build(seed: Long, terrain: Terrain): LandProfile {
            val rnd = java.util.Random(seed * 31 + 17)
            val layers = Array(LAYERS) { layer ->
                val a = FloatArray(N)
                when {
                    terrain == Terrain.SEA && layer <= 2 -> {
                        // Open water; on the farthest layer, sometimes a headland at one side, sloping into the sea.
                        if (layer == 0 && rnd.nextFloat() < 0.7f) {
                            val left = rnd.nextBoolean(); val width = 0.18f + 0.16f * rnd.nextFloat(); val n1 = Noise(rnd)
                            for (i in 0 until N) {
                                val u = i / (N - 1f)
                                val t = (if (left) u else 1 - u) / width
                                // Its tip sinks smoothly into the water (0.9.9: it ended in a cliff, cut straight).
                                val s = 1 - t; val tip = (s / 0.4f).coerceAtMost(1f).let { it * it * (3 - 2 * it) }
                                a[i] = if (t >= 1f) 0f else (s.pow(0.7f) * tip * (0.75f + 0.25f * n1.at(u * 14f))).coerceIn(0f, 1f)
                            }
                        }
                        a
                    }
                    terrain == Terrain.SEA -> {
                        // The shore: low dunes and rocks along the water, gentle in front.
                        val f = floatArrayOf(0f, 0f, 0f, 1.6f, 0.9f)[layer]
                        val ns = Array(3) { Noise(rnd) }
                        for (i in 0 until N) {
                            val u = i / (N - 1f)
                            a[i] = 0.6f * ns[0].at(u * f * 2) + 0.3f * ns[1].at(u * f * 5.1f) + 0.1f * ns[2].at(u * f * 13f)
                        }
                        normalise(a, 0.1f)
                    }
                    terrain == Terrain.FOREST -> forest(rnd, layer)
                    terrain == Terrain.HILLS -> meadow(rnd, layer)
                    terrain == Terrain.ARCTIC -> arctic(rnd, layer)
                    terrain == Terrain.CANYON -> canyon(rnd, layer)
                    terrain == Terrain.MOUNTAINS && layer <= 1 -> {
                        // High ranges: sharp ridged peaks and deep notches, the farthest the tallest and most jagged.
                        val ns = Array(6) { Noise(rnd) }; val broad = Noise(rnd)
                        val sharp = if (layer == 0) 1.6f else 1.25f
                        for (i in 0 until N) {
                            val u = i / (N - 1f)
                            var sum = 0f; var amp = 0.55f; var fq = if (layer == 0) 2.6f else 3.4f; var w = 1f
                            for (o in 0 until 6) {
                                val v = (1 - abs(ns[o].at(u * fq) * 2 - 1)).pow(sharp)
                                sum += amp * v * w; w = min(1f, v * 2.0f); amp *= 0.5f; fq *= 2.1f
                            }
                            a[i] = sum * (0.45f + 0.55f * broad.at(u * 1.3f))
                        }
                        normalise(a, 0f)
                    }
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

        /** Sharp ridged peaks (the mountains' generator): [sharp] above 1 makes them more jagged. */
        private fun range(rnd: java.util.Random, fq0: Float, sharp: Float, broadFq: Float, broadMin: Float): FloatArray {
            val ns = Array(6) { Noise(rnd) }; val broad = Noise(rnd)
            val a = FloatArray(N)
            for (i in 0 until N) {
                val u = i / (N - 1f)
                var sum = 0f; var amp = 0.55f; var fq = fq0; var w = 1f
                for (o in 0 until 6) {
                    val v = (1 - abs(ns[o].at(u * fq) * 2 - 1)).pow(sharp)
                    sum += amp * v * w; w = min(1f, v * 2.0f); amp *= 0.5f; fq *= 2.1f
                }
                a[i] = sum * (broadMin + (1 - broadMin) * broad.at(u * broadFq))
            }
            return normalise(a, 0f)
        }

        /**
         * (0.19) [count] massifs spread across the width, each a broad rounded mass [wMin]..[wMax] wide (half-widths)
         * whose crest is broken into summits, notches and ridges by ridged noise ([fq] across the width); [low] is the
         * least of their heights.
         */
        private fun massifs(rnd: java.util.Random, count: Int, wMin: Float, wMax: Float, fq: Float, low: Float): FloatArray {
            val detail = range(rnd, fq, 1.2f, 1f, 1f)
            val m = Array(count) { k -> floatArrayOf((k + 0.5f + (rnd.nextFloat() - 0.5f) * 0.6f) / count, wMin + (wMax - wMin) * rnd.nextFloat(), low + (1 - low) * rnd.nextFloat()) }
            val a = FloatArray(N) { i ->
                val u = i / (N - 1f)
                var env = 0f
                // (0.19.1: pointed summits over long slopes, not rounded domes.)
                for (k in m) { val t = (u - k[0]) / k[1]; env = max(env, k[2] * (1f - abs(t)).coerceAtLeast(0f).pow(1.25f)) }
                env * (0.72f + 0.28f * detail[i]) + 0.05f * detail[i]
            }
            return normalise(a, 0f)
        }

        /** Rolling ground: three octaves of smooth noise at [f] bumps across the width. */
        private fun rolling(rnd: java.util.Random, f: Float, lo: Float): FloatArray {
            val ns = Array(3) { Noise(rnd) }
            return normalise(FloatArray(N) { val u = it / (N - 1f); 0.62f * ns[0].at(u * f * 2) + 0.28f * ns[1].at(u * f * 4.3f) + 0.10f * ns[2].at(u * f * 9.7f) }, lo)
        }

        private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

        /**
         * The forest lake (0.19). The treeline itself (the pointed tops of the conifers) is drawn by the land shader along
         * these crests; here only the ground under it.
         */
        private fun forest(rnd: java.util.Random, layer: Int): FloatArray = when (layer) {
            0 -> volcano(rnd)
            1 -> rolling(rnd, 1.5f, 0.12f)
            LAKE -> FloatArray(N)
            3 -> {
                // The near shore rises into wooded knolls at one side of the lake or both; between them the water.
                val sides = rnd.nextInt(3)   // 0 left, 1 right, 2 both
                val wl = 0.16f + 0.2f * rnd.nextFloat(); val wr = 0.16f + 0.2f * rnd.nextFloat()
                val n1 = Noise(rnd); val n2 = Noise(rnd)
                FloatArray(N) { i ->
                    val u = i / (N - 1f)
                    val l = if (sides != 1) 1f - smooth(wl * 0.55f, wl, u) else 0f
                    val r = if (sides != 0) smooth(1f - wr, 1f - wr * 0.55f, u) else 0f
                    val knoll = max(l, r) * (0.7f + 0.3f * n1.at(u * 9f))
                    (0.04f + 0.05f * n2.at(u * 23f) + 0.9f * knoll).coerceIn(0f, 1f)
                }
            }
            else -> {
                // The bank in front: low, rising at the edges of the view.
                val n1 = Noise(rnd); val n2 = Noise(rnd)
                FloatArray(N) { i ->
                    val u = i / (N - 1f)
                    val edge = max(1f - smooth(0f, 0.12f, u), smooth(0.88f, 1f, u))
                    (0.12f + 0.25f * n1.at(u * 3.1f) + 0.08f * n2.at(u * 17f) + 0.55f * edge).coerceIn(0f, 1f)
                }
            }
        }

        /**
         * (0.21) The forest's volcano, as the real sky's snow-capped one: one broad cone with a blunt summit, rising over
         * a low range.
         */
        private fun volcano(rnd: java.util.Random): FloatArray {
            val c = 0.25f + 0.5f * rnd.nextFloat(); val hw = 0.22f + 0.08f * rnd.nextFloat()
            val low = range(rnd, 2.4f, 1.3f, 1.2f, 0.4f)
            return normalise(FloatArray(N) { i ->
                val t = abs(i / (N - 1f) - c) / hw
                var v = (1f - t).coerceAtLeast(0f).pow(1.35f)
                v -= 0.06f * smooth(0.85f, 1f, v)
                max(v, 0.32f * low[i])
            }, 0f)
        }

        /** (0.21) The green hills (the real sky's meadow): soft far hills, rounded hills, the reservoir, its near shore, a bank. */
        private fun meadow(rnd: java.util.Random, layer: Int): FloatArray = when (layer) {
            0 -> rolling(rnd, 1.1f, 0.15f)
            1 -> rolling(rnd, 1.5f, 0.08f)
            LAKE -> FloatArray(N)
            3 -> banks(rnd, 0.05f)
            else -> rolling(rnd, 0.9f, 0.1f)
        }

        /** (0.21) A shore rising at one side of the view or both, the water between (the shader draws it under [low]). */
        private fun banks(rnd: java.util.Random, low: Float): FloatArray {
            val sides = rnd.nextInt(3)
            val wl = 0.2f + 0.2f * rnd.nextFloat(); val wr = 0.2f + 0.2f * rnd.nextFloat()
            val n1 = Noise(rnd)
            return FloatArray(N) { i ->
                val u = i / (N - 1f)
                val l = if (sides != 1) 1f - smooth(wl * 0.4f, wl, u) else 0f
                val r = if (sides != 0) smooth(1f - wr, 1f - wr * 0.4f, u) else 0f
                (low + (0.85f + 0.15f * n1.at(u * 6f) - low) * max(l, r)).coerceIn(0f, 1f)
            }
        }

        /**
         * (0.21) Red rock's great butte, as the real sky's Cathedral Rock: a massive base on its skirt of talus, crowned by
         * spires of different heights; a smaller butte elsewhere.
         */
        private fun cathedral(rnd: java.util.Random): FloatArray {
            val c = 0.35f + 0.3f * rnd.nextFloat(); val hw = 0.15f + 0.05f * rnd.nextFloat()
            val c2 = if (c < 0.5f) 0.78f + 0.12f * rnd.nextFloat() else 0.1f + 0.12f * rnd.nextFloat()
            val hw2 = 0.05f + 0.04f * rnd.nextFloat(); val top2 = 0.32f + 0.12f * rnd.nextFloat()
            val spires = Array(5) { floatArrayOf(c + (rnd.nextFloat() * 2f - 1f) * hw * 0.75f, 0.008f + 0.012f * rnd.nextFloat(), 0.6f + 0.4f * rnd.nextFloat()) }
            val n1 = Noise(rnd)
            fun butte(u: Float, cc: Float, w: Float, top: Float): Float {
                val d = abs(u - cc) - w
                return when { d <= 0f -> top * (1f - 0.25f * ((u - cc) / w) * ((u - cc) / w)); d < 0.05f -> top * 0.75f * (1f - d / 0.05f).pow(1.8f); else -> 0f }
            }
            return normalise(FloatArray(N) { i ->
                val u = i / (N - 1f)
                var v = max(0.03f + 0.03f * n1.at(u * 9f), max(butte(u, c, hw, 0.42f), butte(u, c2, hw2, top2)))
                for (s in spires) {
                    val d = abs(u - s[0]) / s[1]
                    if (d < 1.25f) v = max(v, if (d < 1f) s[2] * (1f - 0.12f * d.pow(6)) else s[2] * 0.88f * (1f - (d - 1f) / 0.25f) + 0.42f * ((d - 1f) / 0.25f))
                }
                v
            }, 0f)
        }

        /** The far north (0.19): high ranges, the fjord, a shore of snow and rocks. */
        private fun arctic(rnd: java.util.Random, layer: Int): FloatArray = when (layer) {
            // Broad massifs across the width, each with several jagged summits; the nearer ones with gaps between
            // them, so the far ones show.
            0 -> massifs(rnd, 3, 0.17f, 0.27f, 3.4f, 0.8f)
            1 -> massifs(rnd, 2, 0.09f, 0.17f, 4.2f, 0.55f)
            LAKE -> FloatArray(N)
            3 -> {
                // (0.19.1) Rocks standing in the water (the shader draws water under their waterline).
                val n1 = Noise(rnd); val n2 = Noise(rnd); val a = FloatArray(N) { 0.02f * n1.at(it / (N - 1f) * 5f) }
                repeat(12) {
                    val c = rnd.nextFloat(); val hw = 0.008f + 0.025f * rnd.nextFloat(); val hgt = 0.55f + 0.45f * rnd.nextFloat()
                    for (i in 0 until N) {
                        val d = (i / (N - 1f) - c) / hw
                        // (0.19.1: broken blocks, not domes.)
                        if (abs(d) < 1f) a[i] = max(a[i], hgt * (1f - abs(d)).pow(0.35f) * (0.72f + 0.28f * n2.at(i / (N - 1f) * 90f)))
                    }
                }
                a
            }
            else -> rolling(rnd, 0.9f, 0.1f)
        }

        /**
         * Red rock (0.19): mesas and buttes, each a flat top on sheer walls standing on a skirt of scree; a spire now and
         * then. The shader reads the parts by height (the walls above the scree, a pale cap rock at the top).
         */
        private fun canyon(rnd: java.util.Random, layer: Int): FloatArray {
            // (0.21) The great butte; the floor with its trees; the pool's banks.
            when (layer) {
                1 -> return cathedral(rnd)
                2 -> return rolling(rnd, 1.4f, 0.1f)
                3 -> return banks(rnd, 0.06f)
            }
            val a = FloatArray(N)
            val n1 = Noise(rnd); val n2 = Noise(rnd)
            if (layer >= 3) {
                // (0.19.1) The desert floor: low swells of red sand (until 0.19.0 ledges in steps, which read as terraces).
                for (i in 0 until N) {
                    val u = i / (N - 1f)
                    a[i] = 0.7f * n1.at(u * (if (layer == 3) 2.6f else 1.6f)) + 0.3f * n2.at(u * 7f)
                }
                return normalise(a, 0.1f)
            }
            for (i in 0 until N) a[i] = 0.03f + 0.04f * n1.at(i / (N - 1f) * 11f)
            val count = intArrayOf(6, 5, 3)[layer]
            val wMin = floatArrayOf(0.06f, 0.015f, 0.03f)[layer]; val wMax = floatArrayOf(0.2f, 0.08f, 0.09f)[layer]
            val wall = floatArrayOf(0.003f, 0.006f, 0.008f)[layer]
            for (k in 0..count) {
                val spire = k == count
                if (spire && rnd.nextFloat() < 0.4f) continue
                val c = rnd.nextFloat()
                val hw = if (spire) 0.004f + 0.006f * rnd.nextFloat() else wMin + (wMax - wMin) * rnd.nextFloat()
                val top = if (spire) 0.8f + 0.2f * rnd.nextFloat() else 0.5f + 0.5f * rnd.nextFloat()
                val scree = (if (spire) 0.5f else 0.28f + 0.14f * rnd.nextFloat()) * top
                val slope = (if (layer == 0) 0.02f else 0.035f) + 0.04f * rnd.nextFloat()
                // Some mesas have a second, narrower tier on top.
                val tier = !spire && rnd.nextFloat() < 0.4f
                val tierW = hw * (0.3f + 0.4f * rnd.nextFloat()); val tierC = c + (hw - tierW) * (rnd.nextFloat() * 2 - 1)
                for (i in 0 until N) {
                    val u = i / (N - 1f)
                    val d = abs(u - c) - hw
                    var v = when {
                        d <= 0f && spire -> top * (1f - 0.35f * ((u - c) / hw).let { abs(it) * kotlin.math.sqrt(abs(it)) })
                        d <= 0f -> top * (0.97f + 0.03f * n2.at(u * 40f))
                        d < wall -> top - (top - scree) * (d / wall)
                        d < wall + slope -> scree * (1f - (d - wall) / slope).pow(1.7f)
                        else -> 0f
                    }
                    if (tier) {
                        val dt = abs(u - tierC) - tierW
                        if (dt < wall) v = max(v, top + 0.22f * (if (dt <= 0f) 1f else 1f - dt / wall))
                    }
                    a[i] = max(a[i], v)
                }
            }
            return normalise(a, 0f)
        }
    }
}
