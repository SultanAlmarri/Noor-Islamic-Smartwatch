package com.noor.prayer.geo

import com.noor.prayer.GeoPoint
import java.text.Normalizer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A populated place from the offline gazetteer (GeoNames, CC BY 4.0). */
data class GazPlace(
    val id: Int,
    val name: String,
    val nameAr: String?,
    val point: GeoPoint,
    val countryCode: String,
    val admin1Code: String,
    val admin1Name: String,
    val population: Int,
    val zoneId: String,
) {
    /** "AE-DU"-style region code where Noor needs one (Dubai emirate for IACAD). */
    val region: String? get() = if (countryCode == "AE" && admin1Code == "03") "AE-DU" else if (countryCode == "AE") "AE-OTHER" else null
}

/**
 * Offline place names for the whole world: every place with 15,000+ inhabitants and many city districts
 * (about 34,000 entries). Used to name a position without network, and to search cities in manual mode.
 */
class Gazetteer(val places: List<GazPlace>) {
    private val folded: List<String> by lazy { places.map { fold(it.name) } }

    /**
     * The place that names [p]. Specific (precise location): the nearest place, and among places about as near
     * (within 0.5 km) the smallest, i.e. the district rather than the city whose centre happens to be there.
     * Not specific (approximate location): the largest place within [maxKm].
     */
    fun nearest(p: GeoPoint, maxKm: Double = 30.0, specific: Boolean = true): GazPlace? {
        val near = ArrayList<Pair<GazPlace, Double>>()
        for (g in places) {
            val dLat = g.point.latitude - p.latitude
            if (dLat > 1.0 || dLat < -1.0) continue // quick reject (~110 km)
            val d = distanceKm(p, g.point)
            if (d <= maxKm) near += g to d
        }
        if (near.isEmpty()) return null
        if (!specific) return near.maxBy { it.first.population }.first
        val dMin = near.minOf { it.second }
        return near.filter { it.second <= dMin + 0.5 }.minBy { it.first.population }.first
    }

    /** Places whose name starts with (then contains) [query], accent- and case-insensitive; Arabic names too. */
    fun search(query: String, limit: Int = 30): List<GazPlace> {
        val q = fold(query.trim())
        if (q.length < 2) return emptyList()
        val starts = ArrayList<GazPlace>(); val contains = ArrayList<GazPlace>()
        for (i in places.indices) {
            val g = places[i]; val f = folded[i]
            val ar = g.nameAr
            when {
                f.startsWith(q) || (ar != null && ar.startsWith(query.trim())) -> starts += g
                f.contains(q) || (ar != null && ar.contains(query.trim())) -> contains += g
            }
        }
        return (starts.sortedByDescending { it.population } + contains.sortedByDescending { it.population }).take(limit)
    }

    fun byId(id: Int): GazPlace? = places.firstOrNull { it.id == id }

    companion object {
        /** Parses the gzip-decoded TSV built by tools/geo/build_gazetteer.py. */
        fun parse(lines: Sequence<String>): Gazetteer = Gazetteer(lines.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 11) return@mapNotNull null
            runCatching {
                GazPlace(
                    id = f[0].toInt(), name = f[1], nameAr = f[2].ifBlank { null },
                    point = GeoPoint(f[3].toInt() / 1e5, f[4].toInt() / 1e5, f[10].toDouble()),
                    countryCode = f[5], admin1Code = f[6], admin1Name = f[7], population = f[8].toInt(), zoneId = f[9],
                )
            }.getOrNull()
        }.toList())

        fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase().replace('’', '\'').replace('‘', '\'')

        fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
            val p1 = Math.toRadians(a.latitude); val p2 = Math.toRadians(b.latitude)
            val dp = p2 - p1; val dl = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * 6371.0088 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}

/** Address parts as a reverse geocoder returns them (Android's Geocoder fills these). */
data class AddressParts(
    val subLocality: String?,
    val locality: String?,
    val subAdminArea: String?,
    val adminArea: String?,
    val countryCode: String?,
    val countryName: String?,
)

/** A display name: the most specific reliable area first, then where it is. */
data class PlaceLabel(val primary: String, val context: String?)

/**
 * Picks the name to show for a position. Precise location: neighbourhood or district when the geocoder knows one
 * ("Za'abeel", context "Dubai"); approximate location: the town or city only. Never invents a name: codes, numbers
 * and empty values are skipped, and when nothing usable is left the caller falls back to the offline gazetteer.
 */
object PlaceNaming {
    private val junk = Regex("^[0-9A-Z]{4,}\\+[0-9A-Z]{2,}.*|^[0-9\\s\\-]+$") // plus codes, pure numbers

    private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && !junk.matches(it) }

    fun label(a: AddressParts, precise: Boolean): PlaceLabel? {
        val sub = clean(a.subLocality)
        val city = clean(a.locality) ?: clean(a.subAdminArea)
        val admin = clean(a.adminArea)
        val country = clean(a.countryName)
        if (precise && sub != null && !sub.equals(city, true)) {
            return PlaceLabel(sub, city ?: admin ?: country)
        }
        val primary = city ?: admin ?: return null
        val context = listOf(admin, country).firstOrNull { it != null && !it.equals(primary, true) }
        return PlaceLabel(primary, context)
    }

    /** Label from the offline gazetteer (district or town, then its province/emirate). */
    fun label(g: GazPlace, arabic: Boolean): PlaceLabel {
        val name = if (arabic) g.nameAr ?: g.name else g.name
        return PlaceLabel(name, g.admin1Name.takeIf { it.isNotBlank() && !it.equals(g.name, true) })
    }
}
