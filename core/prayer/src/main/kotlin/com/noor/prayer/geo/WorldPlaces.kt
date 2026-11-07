package com.noor.prayer.geo

import com.noor.prayer.GeoPoint
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.pow

/**
 * The phone's offline world places (0.9.7, GeoNames, CC BY 4.0, built by tools/geo/build_world.py): every town and city
 * of 500+ people or that is the seat of an administrative division, every district, neighbourhood and quarter GeoNames
 * lists, and every populated place of the countries GeoNames lists fewer than 12,000 of (about 840,000 places).
 *
 * Kept compact (names as UTF-8 bytes, numbers in flat arrays: about 35 MB), and loaded only while the location screens
 * are open. Browse: [countries] → [cities] of a country → [areas] of a city. Search: [search], in English (accents
 * and case ignored) or Arabic (diacritics and letter forms ignored). Each place carries its time zone.
 */
class WorldPlaces private constructor(
    private val zones: Array<String>,
    private val adminKeys: Array<String>,
    private val adminNames: Array<String>,
    private val adminNamesAr: Array<String>,
    private val names: ByteArray, private val nameStart: IntArray,
    private val namesAr: ByteArray, private val nameArStart: IntArray,
    private val lat: IntArray, private val lon: IntArray,
    private val admin: ShortArray, private val pop: IntArray, private val zone: ShortArray, private val elev: ShortArray,
    private val kind: ByteArray,
) {
    val size: Int get() = lat.size

    /** A place, by its row. */
    data class Place(
        val row: Int, val name: String, val nameAr: String?, val point: GeoPoint, val countryCode: String,
        val admin1Code: String, val regionName: String, val regionNameAr: String?, val population: Int, val zoneId: String,
        val neighbourhood: Boolean,
    ) {
        /** "AE-DU"-style region code where Noor needs one (Dubai emirate for IACAD), as [GazPlace.region]. */
        val region: String? get() = if (countryCode == "AE" && admin1Code == "03") "AE-DU" else if (countryCode == "AE") "AE-OTHER" else null
    }

    fun place(row: Int): Place {
        val a = admin[row].toInt()
        val key = adminKeys[a]
        val dot = key.indexOf('.')
        // The Arabic name shown is the first; the others (after '|') are other spellings, for search.
        return Place(row, text(names, nameStart, row), text(namesAr, nameArStart, row).substringBefore('|').ifEmpty { null },
            GeoPoint(lat[row] / Q, lon[row] / Q, elev[row].toDouble()), key.substring(0, dot), key.substring(dot + 1),
            adminNames[a], adminNamesAr[a].ifEmpty { null }, pop[row], zones[zone[row].toInt()], kind[row].toInt() == 1)
    }

    private fun text(b: ByteArray, st: IntArray, row: Int) = String(b, st[row], st[row + 1] - 1 - st[row], Charsets.UTF_8)

    private fun cc(row: Int): String = adminKeys[admin[row].toInt()].substring(0, 2)

    private val countryRanges: Map<String, IntRange> by lazy {
        val m = LinkedHashMap<String, IntRange>()
        var start = 0
        for (i in 1..size) if (i == size || admin[i] != admin[start] && cc(i) != cc(start)) { m[cc(start)] = start until i; start = i }
        m
    }

    /** Every country with places, by ISO code. */
    fun countries(): Set<String> = countryRanges.keys

    /** How many places a country has. */
    fun count(countryCode: String): Int = countryRanges[countryCode]?.let { it.last - it.first + 1 } ?: 0

    /** A region of a country (state, province, emirate, governorate: GeoNames' first-level divisions). */
    data class Region(
        val index: Int, val countryCode: String, val code: String, val name: String, val nameAr: String?,
        /** Towns, cities and villages in it. */
        val places: Int,
        /** Its largest town or city. */
        val largest: Place?,
    )

    private val adminRanges: Map<Int, IntRange> by lazy {
        val m = HashMap<Int, IntRange>()
        var start = 0
        for (i in 1..size) if (i == size || admin[i] != admin[start]) { m[admin[start].toInt()] = start until i; start = i }
        m
    }

    /**
     * The regions of a country, in alphabetical order (of the names in [arabic] or English), each with its number of
     * places and its largest town. Places GeoNames gives no region come last, under an empty name.
     */
    fun regions(countryCode: String, arabic: Boolean = false): List<Region> {
        val r = countryRanges[countryCode] ?: return emptyList()
        val seen = LinkedHashSet<Int>()
        for (i in r) seen += admin[i].toInt()
        return seen.map { a ->
            val ar = adminRanges.getValue(a)
            val towns = ar.filter { kind[it].toInt() == 0 }
            val key = adminKeys[a]
            Region(a, countryCode, key.substring(key.indexOf('.') + 1), adminNames[a], adminNamesAr[a].ifEmpty { null }, towns.size,
                towns.maxByOrNull { pop[it] }?.let { place(it) })
        }.filter { it.places > 0 }.sortedWith(compareBy<Region> { it.name.isBlank() || it.code == "00" }.thenBy { sortKey(if (arabic) it.nameAr ?: it.name else it.name) })
    }

    /** Towns, cities and villages of one region: largest first, then by name. */
    fun cities(region: Region): List<Place> {
        val r = adminRanges[region.index] ?: return emptyList()
        return r.filter { kind[it].toInt() == 0 }.map { place(it) }.sortedWith(compareByDescending<Place> { it.population }.thenBy { fold(it.name) })
    }

    /** The [n] largest towns and cities of a country. */
    fun largest(countryCode: String, n: Int): List<Place> {
        val r = countryRanges[countryCode] ?: return emptyList()
        return r.filter { kind[it].toInt() == 0 }.sortedByDescending { pop[it] }.take(n).map { place(it) }
    }

    /** Towns, cities and villages of a country: largest first, then by name. */
    fun cities(countryCode: String): List<Place> {
        val r = countryRanges[countryCode] ?: return emptyList()
        return r.filter { kind[it].toInt() == 0 }.map { place(it) }.sortedWith(compareByDescending<Place> { it.population }.thenBy { fold(it.name) })
    }

    /** How far a city's districts reach from its centre: about 7 km for 100,000 people, 26 km for 3 million. */
    fun reachKm(population: Int): Double = (5.0 * (population.coerceAtLeast(1) / 50_000.0).pow(0.4)).coerceIn(3.0, 45.0)

    /**
     * The districts and neighbourhoods of [city] (within its reach, same country), and the small places of its own
     * region inside that reach (suburbs and villages GeoNames lists as places), alphabetically.
     */
    fun areas(city: Place): List<Place> {
        val r = countryRanges[city.countryCode] ?: return emptyList()
        val reach = reachKm(city.population)
        val cityAdmin = admin[city.row]
        val out = ArrayList<Place>()
        val win = (reach / 111.0 * Q).toInt() + 1
        val la = lat[city.row]
        for (i in r) {
            if (i == city.row || lat[i] < la - win || lat[i] > la + win) continue
            val isArea = kind[i].toInt() == 1 || (admin[i] == cityAdmin && pop[i] < minOf(city.population, 50_000))
            if (!isArea) continue
            if (Gazetteer.distanceKm(GeoPoint(lat[i] / Q, lon[i] / Q), city.point) <= reach) out += place(i)
        }
        return out.sortedBy { sortKey(it.name) }
    }

    /**
     * The city [p] belongs to: of the towns and cities of [countryCode] whose reach ([reachKm]) covers [p], the largest
     * (Dubai Marina belongs to Dubai, not to the smaller Palm Jumeirah next to it); else the nearest town within 30 km.
     */
    fun cityOf(p: GeoPoint, countryCode: String): Place? {
        val r = countryRanges[countryCode] ?: return null
        var best = -1; var nearest = -1; var nearestD = 30.0
        val la = (p.latitude * Q).toInt(); val win = (45.0 / 111.0 * Q).toInt() + 1
        for (i in r) {
            if (kind[i].toInt() != 0 || lat[i] < la - win || lat[i] > la + win) continue
            val d = Gazetteer.distanceKm(p, GeoPoint(lat[i] / Q, lon[i] / Q))
            if (pop[i] >= 20_000 && d <= reachKm(pop[i]) && (best < 0 || pop[i] > pop[best])) best = i
            if (d < nearestD) { nearestD = d; nearest = i }
        }
        return (if (best >= 0) best else nearest).takeIf { it >= 0 }?.let { place(it) }
    }

    /** The named place nearest [p] (any kind), within [maxKm]; for naming a point picked on the map. */
    fun nearest(p: GeoPoint, maxKm: Double = 25.0, neighbourhoods: Boolean = true): Place? {
        var best = -1; var bestD = maxKm
        val la = (p.latitude * Q).toInt()
        val win = (maxKm / 111.0 * Q).toInt() + 1
        for (i in 0 until size) {
            if (lat[i] < la - win || lat[i] > la + win || (!neighbourhoods && kind[i].toInt() == 1)) continue
            val d = Gazetteer.distanceKm(p, GeoPoint(lat[i] / Q, lon[i] / Q))
            if (d < bestD) { bestD = d; best = i }
        }
        return if (best >= 0) place(best) else null
    }

    /**
     * Places whose name matches [query]: names that start with it first, then names with a word that starts with it,
     * then names that contain it; within each, towns and cities by size, then neighbourhoods. Latin queries ignore case
     * and accents; Arabic queries ignore diacritics and the forms of alef, ta marbuta and alef maqsura.
     */
    fun search(query: String, limit: Int = 60, countryCode: String? = null): List<Place> {
        val needle = fold(query.trim())
        if (needle.length < 2) return emptyList()
        val arabic = needle.any { it in '؀'..'ۿ' }
        val bytes = if (arabic) namesAr else names
        val starts = if (arabic) nameArStart else nameStart
        val range = countryCode?.let { countryRanges[it] } ?: (0 until size)
        val buf = CharArray(256)
        val t = table
        val n0 = needle[0]
        val hits = ArrayList<Long>()
        for (row in range) {
            // Fold the row's name (UTF-8) into buf, then look for the needle in it.
            var p = starts[row]; val end = starts[row + 1] - 1
            if (end - p < needle.length) continue
            var len = 0
            while (p < end && len < buf.size) {
                val b0 = bytes[p].toInt() and 0xff
                val cp: Int
                if (b0 < 0x80) { cp = b0; p += 1 }
                else if (b0 < 0xE0) { cp = ((b0 and 0x1f) shl 6) or (bytes[p + 1].toInt() and 0x3f); p += 2 }
                else if (b0 < 0xF0) { cp = ((b0 and 0x0f) shl 12) or ((bytes[p + 1].toInt() and 0x3f) shl 6) or (bytes[p + 2].toInt() and 0x3f); p += 3 }
                else { cp = 0xFFFD; p += 4 }
                val f = t[cp]
                if (f != DROP) buf[len++] = f
            }
            var tier = 3
            var i = 0
            while (i <= len - needle.length && tier > 0) {
                if (buf[i] == n0) {
                    var k = 1
                    while (k < needle.length && buf[i + k] == needle[k]) k++
                    if (k == needle.length) {
                        val tt = if (i == 0 || buf[i - 1] == '|') 0 else if (buf[i - 1] == ' ' || buf[i - 1] == '-' || buf[i - 1] == '\'') 1 else 2
                        if (tt < tier) tier = tt
                    }
                }
                i++
            }
            if (tier < 3) hits += (tier.toLong() shl 60) or (kind[row].toLong() shl 56) or ((Int.MAX_VALUE - pop[row]).toLong() shl 24) or row.toLong()
        }
        hits.sort()
        return hits.take(limit).map { place((it and 0xFFFFFF).toInt()) }
    }

    companion object {
        private const val Q = 1e4
        private const val DROP = '\u0000'

        /** Alphabetical order that ignores accents, case and leading marks such as ‘ in ‘Ayāl. */
        fun sortKey(name: String): String = fold(name).trimStart('\'', ' ', '-', '(')

        /**
         * Text folded for search, one character at a time: lower case, accents and Arabic diacritics and tatweel
         * removed; the forms of alef become alef, ta marbuta ha, alef maqsura ya, and the hamza seats their letter.
         */
        fun fold(s: String): String {
            val t = table
            val b = StringBuilder(s.length)
            for (ch in s) { val f = t[ch.code]; if (f != DROP) b.append(f) }
            return b.toString()
        }

        private fun isMark(ch: Char): Boolean = Character.getType(ch).let {
            it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt()
        }

        private val table: CharArray by lazy {
            val t = CharArray(65536)
            for (c in 0 until 65536) {
                val ch = c.toChar()
                t[c] = when {
                    c == 0 -> DROP
                    c < 128 -> ch.lowercaseChar()
                    ch == 'ـ' || isMark(ch) -> DROP
                    ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> 'ا'
                    ch == 'ة' -> 'ه'
                    ch == 'ى' || ch == 'ئ' -> 'ي'
                    ch == 'ؤ' -> 'و'
                    ch == '’' || ch == '‘' || ch == 'ʼ' || ch == 'ʻ' -> '\''
                    Character.isSurrogate(ch) -> ch
                    else -> {
                        val d = java.text.Normalizer.normalize(ch.toString(), java.text.Normalizer.Form.NFD)
                        (if (d.length > 1 && d.drop(1).all { isMark(it) }) d[0] else ch).lowercaseChar()
                    }
                }
            }
            t
        }

        /** Reads world.bin (gzip), as written by tools/geo/build_world.py. */
        fun read(input: InputStream): WorldPlaces {
            val d = DataInputStream(GZIPInputStream(input, 1 shl 16).buffered(1 shl 16))
            val magic = ByteArray(4).also { d.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == "NWP1") { "not Noor world places" }
            fun block(): ByteArray = ByteArray(TimeZoneMap.varint(d)).also { d.readFully(it) }
            fun list(): Array<String> = block().toString(Charsets.UTF_8).split('\n').toTypedArray()
            val zones = list(); val adminKeys = list(); val adminNames = list(); val adminNamesAr = list()
            val n = TimeZoneMap.varint(d)
            fun withEnd(b: ByteArray): ByteArray = b.copyOf(b.size + 1).also { it[b.size] = '\n'.code.toByte() }
            val names = withEnd(block()); val namesAr = withEnd(block())
            fun starts(b: ByteArray): IntArray {
                val st = IntArray(n + 1); var k = 1
                for (i in b.indices) if (b[i] == '\n'.code.toByte() && k <= n) st[k++] = i + 1
                return st
            }
            fun ints(delta: Boolean, signed: Boolean): IntArray {
                val a = IntArray(n); var prev = 0
                for (i in 0 until n) {
                    val raw = TimeZoneMap.varint(d)
                    val v = if (delta || signed) TimeZoneMap.unzig(raw) else raw
                    a[i] = if (delta) { prev += v; prev } else v
                }
                return a
            }
            fun IntArray.short() = ShortArray(size) { this[it].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
            val lat = ints(true, true); val lon = ints(true, true)
            val admin = ints(true, true).short(); val pop = ints(false, false); val zone = ints(false, false).short(); val elev = ints(false, true).short()
            val kind = ByteArray(n) { TimeZoneMap.varint(d).toByte() }
            return WorldPlaces(zones, adminKeys, adminNames, adminNamesAr, names, starts(names), namesAr, starts(namesAr), lat, lon, admin, pop, zone, elev, kind)
        }
    }
}
