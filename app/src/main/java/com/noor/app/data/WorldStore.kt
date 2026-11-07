package com.noor.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.noor.prayer.GeoPoint
import com.noor.prayer.authority.Authorities
import com.noor.prayer.geo.AddressParts
import com.noor.prayer.geo.Coordinates
import com.noor.prayer.geo.Gazetteer
import com.noor.prayer.geo.PlaceNaming
import com.noor.prayer.geo.TimeZoneMap
import com.noor.prayer.geo.WorldPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.lang.ref.SoftReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Where a place chosen by hand is, in full (0.9.7): its exact point, its time zone, and its names. Prayer times are
 * computed for [point] itself, in [zoneId] (with that zone's daylight-saving rules), never for a nearby city's centre.
 */
data class PickedPlace(
    val point: GeoPoint,
    val zoneId: String,
    val nameEn: String,
    val nameAr: String?,
    val contextEn: String?,
    val contextAr: String?,
    val countryCode: String?,
    val regionCode: String?,
) {
    fun toKnown(): KnownLocation = KnownLocation(point, zoneId, nameEn, countryCode, regionCode, 0L, nameAr, contextEn, contextAr, precise = true)
}

/** One search result: offline (GeoNames) or online (the phone's geocoder). */
data class FoundPlace(
    val point: GeoPoint, val title: String, val subtitle: String?, val countryCode: String?, val online: Boolean,
    val offline: WorldPlaces.Place? = null,
)

/**
 * The phone's location data (0.9.7): the offline world places and time-zone map (loaded on first use, about a second,
 * and let go when memory is needed), and the phone's own geocoder for online search and for naming a point on the map.
 *
 * Online search uses Android's Geocoder (the platform service, Google's on most phones: no account or key, nothing
 * sent by Noor itself); on phones without one, Photon (photon.komoot.io, OpenStreetMap data). Only the words typed
 * are sent, never the user's position.
 */
class WorldStore(private val context: Context) {
    private val lock = Mutex()
    @Volatile private var world: SoftReference<WorldPlaces>? = null
    @Volatile private var zones: SoftReference<TimeZoneMap>? = null

    suspend fun places(): WorldPlaces = world?.get() ?: lock.withLock {
        world?.get() ?: withContext(Dispatchers.IO) { context.assets.open("world.bin").use { WorldPlaces.read(it) } }.also { world = SoftReference(it) }
    }

    suspend fun timeZones(): TimeZoneMap = zones?.get() ?: lock.withLock {
        zones?.get() ?: withContext(Dispatchers.IO) { context.assets.open("tzmap.bin").use { TimeZoneMap.read(it) } }.also { zones = SoftReference(it) }
    }

    /**
     * The time zone at [p]: the offline map's, if this phone's Android knows that zone (a zone created after the phone's
     * last time-zone update is replaced by the nearest place's zone, which keeps the same clock).
     */
    suspend fun zoneAt(p: GeoPoint): String = withContext(Dispatchers.Default) {
        val z = timeZones().zoneAt(p.latitude, p.longitude)
        if (runCatching { ZoneId.of(z) }.isSuccess) z
        else places().nearest(p, 100.0)?.zoneId?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: ZoneId.systemDefault().id
    }

    /** A place from the offline list, chosen in the browser or the search. */
    suspend fun picked(p: WorldPlaces.Place): PickedPlace {
        // The list's zone is GeoNames'; the map's is the boundary's. They agree almost everywhere; the map decides.
        val zone = zoneAt(p.point)
        val context = listOfNotNull(p.regionName.takeIf { it.isNotBlank() && it != p.name }, countryName(p.countryCode, false)).distinct().joinToString(", ")
        val contextAr = listOfNotNull(p.regionNameAr, countryName(p.countryCode, true)).distinct().joinToString("، ")
        val place = places()
        // A neighbourhood is named with its city: "Al Karama", "Dubai, United Arab Emirates".
        val city = if (p.neighbourhood) place.cityOf(p.point, p.countryCode)?.takeIf { it.row != p.row } else null
        return PickedPlace(p.point, zone, p.name, p.nameAr,
            city?.let { listOf(it.name, countryName(p.countryCode, false)).joinToString(", ") } ?: context.ifBlank { null },
            city?.let { listOf(it.nameAr ?: it.name, countryName(p.countryCode, true)).joinToString("، ") } ?: contextAr.ifBlank { null },
            p.countryCode, p.region)
    }

    /**
     * A point picked on the map, typed as coordinates or found online: named by the phone's geocoder (the
     * neighbourhood when it knows one), else by the nearest place in the offline list, else by its coordinates.
     */
    suspend fun pinned(p: GeoPoint, title: String? = null): PickedPlace = withContext(Dispatchers.Default) {
        val zone = zoneAt(p)
        val en = reverse(p, Locale.ENGLISH)
        val ar = reverse(p, Locale.forLanguageTag("ar"))
        val label = en?.let { PlaceNaming.label(it.parts(), precise = true) }
        val labelAr = ar?.let { PlaceNaming.label(it.parts(), precise = true) }
        val near = places().nearest(p, 25.0)
        val nearKm = near?.let { Gazetteer.distanceKm(it.point, p) }
        val cc = en?.countryCode ?: near?.countryCode?.takeIf { nearKm!! < 25 }
        val region = regionCode(en) ?: near?.region?.takeIf { nearKm!! < 5 }
        val coords = Coordinates.format(p)
        PickedPlace(
            point = p, zoneId = zone,
            nameEn = title ?: label?.primary ?: near?.takeIf { nearKm!! < 3 }?.name ?: coords,
            nameAr = labelAr?.primary ?: near?.takeIf { nearKm!! < 3 }?.let { it.nameAr ?: it.name },
            contextEn = label?.context ?: near?.let { listOfNotNull(it.regionName.ifBlank { null }, countryName(it.countryCode, false)).joinToString(", ") },
            contextAr = labelAr?.context ?: near?.let { listOfNotNull(it.regionNameAr, countryName(it.countryCode, true)).joinToString("، ") },
            countryCode = cc, regionCode = region,
        )
    }

    /** Online search: the phone's geocoder (or Photon without one). Empty when offline or nothing is found. */
    suspend fun searchOnline(query: String, arabic: Boolean): List<FoundPlace> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 3) return@withContext emptyList()
        val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
        val found = if (Geocoder.isPresent()) geocode(q, locale)?.mapNotNull { a ->
            if (!a.hasLatitude() || !a.hasLongitude()) return@mapNotNull null
            val label = PlaceNaming.label(a.parts(), precise = true)
            val title = a.featureName?.takeIf { it.isNotBlank() && !it.matches(Regex("^[0-9\\s\\-]+$")) && it != label?.primary } ?: label?.primary ?: return@mapNotNull null
            val sub = listOfNotNull(label?.primary?.takeIf { it != title }, a.locality?.takeIf { it != title && it != label?.primary }, a.adminArea?.takeIf { it != title }, a.countryName)
                .distinct().joinToString(if (arabic) "، " else ", ")
            FoundPlace(GeoPoint(a.latitude, a.longitude), title, sub.ifBlank { null }, a.countryCode, online = true)
        } else photon(q, arabic)
        found.orEmpty()
    }

    private fun Address.parts() = AddressParts(subLocality, locality, subAdminArea, adminArea, countryCode, countryName)

    private fun regionCode(a: Address?): String? {
        val admin = a?.adminArea ?: return null
        return when {
            a.countryCode == "AE" && (admin.contains("Dubai", true) || admin.contains("دبي")) -> Authorities.DUBAI_REGION
            a.countryCode == "AE" -> "AE-OTHER"
            else -> null
        }
    }

    private suspend fun reverse(p: GeoPoint, locale: Locale): Address? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val g = Geocoder(context, locale)
        withTimeoutOrNull(6_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) suspendCancellableCoroutine { cont ->
                g.getFromLocation(p.latitude, p.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                })
            } else @Suppress("DEPRECATION") runCatching { g.getFromLocation(p.latitude, p.longitude, 1)?.firstOrNull() }.getOrNull()
        }
    }

    private suspend fun geocode(q: String, locale: Locale): List<Address>? {
        val g = Geocoder(context, locale)
        return withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) suspendCancellableCoroutine { cont ->
                g.getFromLocationName(q, 12, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                })
            } else @Suppress("DEPRECATION") runCatching { g.getFromLocationName(q, 12) }.getOrNull()
        }
    }

    /** Photon (komoot, OpenStreetMap data): only on phones without a geocoder. */
    private fun photon(q: String, arabic: Boolean): List<FoundPlace>? = runCatching {
        val url = URL("https://photon.komoot.io/api/?limit=12&q=" + URLEncoder.encode(q, "UTF-8") + if (arabic) "" else "&lang=en")
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000; readTimeout = 8_000; setRequestProperty("User-Agent", USER_AGENT)
        }
        val body = c.inputStream.bufferedReader().use { it.readText() }
        val features = JSONObject(body).getJSONArray("features")
        (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val xy = f.getJSONObject("geometry").getJSONArray("coordinates")
            val pr = f.getJSONObject("properties")
            val name = pr.optString("name").ifBlank { return@mapNotNull null }
            val sub = listOf("district", "city", "state", "country").mapNotNull { k -> pr.optString(k).ifBlank { null }?.takeIf { it != name } }.distinct()
                .joinToString(if (arabic) "، " else ", ")
            FoundPlace(GeoPoint(xy.getDouble(1), xy.getDouble(0)), name, sub.ifBlank { null }, pr.optString("countrycode").ifBlank { null }?.uppercase(), online = true)
        }
    }.onFailure { Log.i("NoorPlaces", "photon: ${it.message}") }.getOrNull()

    companion object {
        const val USER_AGENT = "Noor/0.9.7 (Android; prayer times; https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch)"

        /** The country's name in English or Arabic, from Android's own locale data. */
        fun countryName(code: String, arabic: Boolean): String =
            Locale("", code).getDisplayCountry(if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH).ifBlank { code }
    }
}
