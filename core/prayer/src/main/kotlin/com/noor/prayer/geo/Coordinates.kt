package com.noor.prayer.geo

import com.noor.prayer.GeoPoint
import kotlin.math.abs

/**
 * Coordinates typed or pasted by the user (0.9.7). Accepts what people copy from maps and GPS apps:
 * - decimal degrees: "25.2048, 55.2708", "25.2048 55.2708", "-33.8688,151.2093"
 * - with hemispheres: "25.2048° N, 55.2708° E", "N 25.2048 E 55.2708", "33.8688S 151.2093E"
 * - degrees, minutes and seconds: "25°12'17.3\"N 55°16'14.9\"E", "25 12 17.3 N 55 16 14.9 E", degrees and minutes
 * - map links: "https://maps.google.com/?q=25.2048,55.2708", ".../@25.2048,55.2708,15z", "geo:25.2048,55.2708"
 * - Arabic-Indic digits, the Arabic decimal and thousands separators and the Arabic comma.
 * Latitude comes first unless the hemispheres say otherwise. Anything ambiguous or out of range is refused (null):
 * prayer times are never computed for a position the user did not mean.
 */
object Coordinates {
    fun parse(input: String): GeoPoint? {
        val s = normalize(input)
        if (s.isBlank()) return null
        Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""").find(s)?.let { return checked(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
        Regex("""(?:[?&](?:q|query|ll|center)=|geo:)\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""").find(s)?.let {
            return checked(it.groupValues[1].toDouble(), it.groupValues[2].toDouble())
        }
        if (s.contains("://")) return null
        // Split into two halves, each a number of up to three parts (degrees, minutes, seconds) and a hemisphere.
        val tokens = Regex("""[NSEW]|-?\d+(?:\.\d+)?""").findAll(s.uppercase().replace(Regex("""[°º'′"″]"""), " ")).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        val rest = s.uppercase().replace(Regex("""[NSEW]|-?\d+(?:\.\d+)?|[°º'′"″,;\s]"""), "")
        if (rest.isNotEmpty()) return null
        val halves = split(tokens) ?: return null
        val a = value(halves.first) ?: return null
        val b = value(halves.second) ?: return null
        val (lat, lon) = when {
            a.second in EW && b.second in NS -> b.first to a.first
            a.second in NS && b.second in NS -> return null
            a.second in EW && b.second in EW -> return null
            a.second in EW || b.second in NS -> return null
            else -> a.first to b.first
        }
        return checked(lat, lon)
    }

    /** Arabic-Indic and Persian digits to ASCII; the Arabic decimal separator to '.', the Arabic comma to ','. */
    fun normalize(s: String): String {
        val b = StringBuilder(s.length)
        for (ch in s) when (ch) {
            in '٠'..'٩' -> b.append('0' + (ch - '٠'))
            in '۰'..'۹' -> b.append('0' + (ch - '۰'))
            '٫' -> b.append('.')
            '،', '٬' -> b.append(',')
            '−', '–' -> b.append('-')
            else -> b.append(ch)
        }
        return b.toString().trim()
    }

    private fun checked(lat: Double, lon: Double): GeoPoint? =
        if (abs(lat) <= 90.0 && abs(lon) <= 180.0 && !(lat == 0.0 && lon == 0.0)) GeoPoint(lat, lon) else null

    private val NS = setOf("N", "S")
    private val EW = setOf("E", "W")

    private fun isLetter(t: String) = t.length == 1 && t[0] in "NSEW"

    /** Two groups of number tokens, each with its hemisphere letter (before or after) if any. */
    private fun split(tokens: List<String>): Pair<List<String>, List<String>>? {
        val letters = tokens.indices.filter { isLetter(tokens[it]) }
        val numbers = tokens.count { !isLetter(it) }
        if (numbers !in 2..6) return null
        return when (letters.size) {
            0 -> if (numbers % 2 != 0) null else tokens.take(numbers / 2) to tokens.drop(numbers / 2)
            // "N 25 12 E 55 16" (letters first), "25 12 N 55 16 E" (letters last), or one of each.
            2 -> if (letters[0] == 0) tokens.subList(0, letters[1]) to tokens.subList(letters[1], tokens.size)
                 else tokens.subList(0, letters[0] + 1) to tokens.subList(letters[0] + 1, tokens.size)
            else -> null
        }
    }

    /** Degrees from up to three numbers (degrees, minutes, seconds), signed by the hemisphere; with that letter. */
    private fun value(group: List<String>): Pair<Double, String>? {
        val letters = group.filter { isLetter(it) }
        if (letters.size > 1 || (letters.size == 1 && group.first() != letters[0] && group.last() != letters[0])) return null
        val letter = letters.firstOrNull() ?: ""
        val nums = group.filter { !isLetter(it) }.map { it.toDouble() }
        if (nums.isEmpty() || nums.size > 3) return null
        if (nums.size > 1 && nums[0] != Math.floor(nums[0])) return null
        if (nums.size > 1 && nums.drop(1).any { it < 0 || it >= 60 }) return null
        if (nums.size > 1 && nums.dropLast(1).drop(1).any { it != Math.floor(it) }) return null
        val deg = nums[0]
        var v = abs(deg) + nums.getOrElse(1) { 0.0 } / 60.0 + nums.getOrElse(2) { 0.0 } / 3600.0
        if (deg < 0 || letter == "S" || letter == "W") v = -v
        if (deg < 0 && (letter == "S" || letter == "W")) return null
        return v to letter
    }

    /** "25.20480° N, 55.27080° E" (or Arabic letters), five decimals: about a metre. */
    fun format(p: GeoPoint, arabic: Boolean = false): String {
        val ns = if (p.latitude >= 0) (if (arabic) "ش" else "N") else (if (arabic) "ج" else "S")
        val ew = if (p.longitude >= 0) (if (arabic) "ق" else "E") else (if (arabic) "غ" else "W")
        return "%.5f° %s, %.5f° %s".format(java.util.Locale.ROOT, abs(p.latitude), ns, abs(p.longitude), ew)
    }

    /** "25.20480, 55.27080": what maps and GPS apps accept. */
    fun decimal(p: GeoPoint): String = "%.5f, %.5f".format(java.util.Locale.ROOT, p.latitude, p.longitude)
}
