package com.noor.prayer.geo

import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Which time zone a point on Earth is in, offline (0.9.7): the boundaries of every IANA time zone, from
 * timezone-boundary-builder (OpenStreetMap data, ODbL), built by tools/geo/build_timezones.py. Borders keep their shape
 * to within about 100 m. Land zones reach out over their territorial waters; beyond them, the open sea keeps nautical
 * time (15-degree bands of longitude, Etc/GMT±n).
 *
 * A point exactly on a border (within the 100 m) belongs to the zone it is deepest inside; a point in a sliver between
 * two simplified borders belongs to the nearest zone.
 */
class TimeZoneMap private constructor(
    val zones: Array<String>,
    private val polyZone: IntArray,
    /** minX, minY, maxX, maxY per polygon (1e-4 degree). */
    private val bbox: IntArray,
    /** Per polygon, its rings: x, y interleaved (1e-4 degree). The first ring is the outer one. */
    private val rings: Array<Array<IntArray>>,
) {
    val polygonCount: Int get() = polyZone.size

    /** The IANA zone id at this latitude and longitude (degrees). */
    fun zoneAt(lat: Double, lon: Double): String {
        val x = (lon * Q).roundToInt(); val y = (lat * Q).roundToInt()
        var best = -1; var bestDepth = -1.0
        for (i in polyZone.indices) {
            val b = i * 4
            if (x < bbox[b] || y < bbox[b + 1] || x > bbox[b + 2] || y > bbox[b + 3]) continue
            if (!inside(rings[i], x, y)) continue
            // Usually one; two only within the simplification of a shared border: the one it is deeper inside.
            val depth = if (best < 0) Double.MAX_VALUE else edgeDistance(rings[i], x, y, lat)
            if (best < 0) { best = i; bestDepth = Double.MAX_VALUE; continue }
            if (bestDepth == Double.MAX_VALUE) bestDepth = edgeDistance(rings[best], x, y, lat)
            if (depth > bestDepth) { best = i; bestDepth = depth }
        }
        if (best >= 0) return zones[polyZone[best]]
        // In no zone: a sliver between two borders, or a coast's edge: the nearest zone within 2 km; else open sea.
        var near = -1; var nearD = NEAR
        for (i in polyZone.indices) {
            val b = i * 4
            if (x < bbox[b] - NEAR || y < bbox[b + 1] - NEAR || x > bbox[b + 2] + NEAR || y > bbox[b + 3] + NEAR) continue
            val d = edgeDistance(rings[i], x, y, lat)
            if (d < nearD) { nearD = d; near = i }
        }
        return if (near >= 0) zones[polyZone[near]] else oceanZone(lon)
    }

    companion object {
        private const val Q = 1e4
        /** 0.02 degree (about 2 km) in map units. */
        private const val NEAR = 200.0

        /** Nautical time at sea: Etc/GMT-n east of Greenwich (the sign is POSIX's, the reverse of the offset). */
        fun oceanZone(lon: Double): String {
            val n = floor((lon + 7.5) / 15.0).toInt().coerceIn(-12, 12)
            return when { n == 0 -> "Etc/GMT"; n > 0 -> "Etc/GMT-$n"; else -> "Etc/GMT+${-n}" }
        }

        /** Reads tzmap.bin (gzip), as written by tools/geo/build_timezones.py. */
        fun read(input: InputStream): TimeZoneMap {
            val d = DataInputStream(GZIPInputStream(input, 1 shl 16).buffered(1 shl 16))
            val magic = ByteArray(4).also { d.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == "NTZ1") { "not a Noor time-zone map" }
            val zones = Array(varint(d)) { ByteArray(varint(d)).also { b -> d.readFully(b) }.toString(Charsets.UTF_8) }
            val n = varint(d)
            val polyZone = IntArray(n); val bbox = IntArray(n * 4)
            val rings = arrayOfNulls<Array<IntArray>>(n)
            for (i in 0 until n) {
                polyZone[i] = varint(d)
                for (k in 0..3) bbox[i * 4 + k] = unzig(varint(d))
                rings[i] = Array(varint(d)) {
                    val count = varint(d)
                    val r = IntArray(count * 2)
                    var px = 0; var py = 0
                    for (v in 0 until count) {
                        px += unzig(varint(d)); py += unzig(varint(d))
                        r[v * 2] = px; r[v * 2 + 1] = py
                    }
                    r
                }
            }
            @Suppress("UNCHECKED_CAST")
            return TimeZoneMap(zones, polyZone, bbox, rings as Array<Array<IntArray>>)
        }

        internal fun varint(d: DataInputStream): Int {
            var shift = 0; var v = 0L
            while (true) {
                val b = d.readUnsignedByte()
                v = v or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return v.toInt()
                shift += 7
            }
        }

        internal fun unzig(v: Int): Int = (v ushr 1) xor -(v and 1)

        /** Even-odd rule over all the rings (holes included). */
        private fun inside(rs: Array<IntArray>, x: Int, y: Int): Boolean {
            var c = false
            for (r in rs) {
                val n = r.size / 2
                var j = n - 1
                for (i in 0 until n) {
                    val xi = r[i * 2]; val yi = r[i * 2 + 1]; val xj = r[j * 2]; val yj = r[j * 2 + 1]
                    if ((yi > y) != (yj > y) && x < (xj - xi).toDouble() * (y - yi) / (yj - yi) + xi) c = !c
                    j = i
                }
            }
            return c
        }

        /** Distance from the point to the nearest edge, in map units with longitude scaled to ground distance. */
        private fun edgeDistance(rs: Array<IntArray>, x: Int, y: Int, lat: Double): Double {
            val k = cos(Math.toRadians(lat)).coerceAtLeast(0.01)
            var best = Double.MAX_VALUE
            for (r in rs) {
                val n = r.size / 2
                var j = n - 1
                for (i in 0 until n) {
                    val ax = (r[j * 2] - x) * k; val ay = (r[j * 2 + 1] - y).toDouble()
                    val bx = (r[i * 2] - x) * k; val by = (r[i * 2 + 1] - y).toDouble()
                    val dx = bx - ax; val dy = by - ay
                    val len = dx * dx + dy * dy
                    val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
                    val px = ax + t * dx; val py = ay + t * dy
                    val dd = px * px + py * py
                    if (dd < best) best = dd
                    j = i
                }
            }
            return sqrt(best)
        }
    }
}
