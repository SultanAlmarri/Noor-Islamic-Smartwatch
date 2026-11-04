package com.noor.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.noor.prayer.GeoPoint
import com.noor.prayer.Places
import com.noor.prayer.authority.Authorities
import com.noor.prayer.geo.AddressParts
import com.noor.prayer.geo.Gazetteer
import com.noor.prayer.geo.PlaceNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Battery-conscious location: one fix on demand (no continuous tracking), the device's last fix as a fallback.
 *
 * Precision follows what the user granted: precise location → high-accuracy fix and the most specific name the
 * geocoder knows (neighbourhood/district); approximate → balanced-power fix and the town or city only.
 *
 * Names: Android's Geocoder (the platform service; no third-party account, nothing sent by Noor itself), in English
 * and Arabic, cached while the user stays within a few hundred metres (precise) or kilometres (approximate);
 * without network, the offline GeoNames gazetteer names the place. Nothing is invented: if neither knows a name,
 * coordinates are shown.
 */
class LocationRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val gazetteer: GazetteerStore,
) {

    sealed interface Result {
        data class Updated(val location: KnownLocation) : Result
        data object NoPermission : Result
        data object Unavailable : Result
    }

    fun hasForegroundPermission(): Boolean = hasPrecise() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasPrecise(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun refresh(): Result {
        if (!hasForegroundPermission()) return Result.NoPermission
        val precise = hasPrecise()
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(2 * 60_000L)
            .setDurationMillis(20_000L)
            .build()
        val fix = withTimeoutOrNull(25_000) {
            runCatching { client.getCurrentLocation(request, cts.token).await() }.getOrNull()
        } ?: runCatching { client.lastLocation.await() }.getOrNull()
        cts.cancel()
        if (fix == null) return Result.Unavailable

        val point = GeoPoint(fix.latitude, fix.longitude, if (fix.hasAltitude()) fix.altitude else 0.0)
        val known = describe(point, precise, settings.current().lastLocation)
        settings.saveLocation(known)
        return Result.Updated(known)
    }

    /** Names a position, reusing the previous name when the user has not moved meaningfully. */
    suspend fun describe(point: GeoPoint, precise: Boolean, previous: KnownLocation?): KnownLocation {
        val now = System.currentTimeMillis()
        val reuseKm = if (precise) 0.3 else 3.0
        if (previous != null && previous.placeName != null && previous.precise == precise &&
            Gazetteer.distanceKm(previous.point, point) <= reuseKm
        ) return previous.copy(point = point, zoneId = ZoneId.systemDefault().id, fixedAtEpochMs = now)

        val en = reverseGeocode(point, Locale.ENGLISH)
        val ar = reverseGeocode(point, Locale.forLanguageTag("ar"))
        var label = en?.let { PlaceNaming.label(it.parts(), precise) }
        var labelAr = ar?.let { PlaceNaming.label(it.parts(), precise) }
        val gaz = runCatching { gazetteer.get().nearest(point, 30.0, specific = precise) }.getOrNull()
        if (label == null && gaz != null) { label = PlaceNaming.label(gaz, false); labelAr = PlaceNaming.label(gaz, true) }

        val near = Places.nearest(point)
        val country = en?.countryCode ?: gaz?.countryCode ?: near.countryCode.takeIf { Places.distanceKm(near.point, point) < 150 }
        val region = regionCode(en) ?: gaz?.region ?: near.region.takeIf { Places.distanceKm(near.point, point) < 40 }
        return KnownLocation(
            point = point, zoneId = ZoneId.systemDefault().id,
            placeName = label?.primary, countryCode = country, regionCode = region, fixedAtEpochMs = now,
            placeNameAr = labelAr?.primary, placeContext = label?.context, placeContextAr = labelAr?.context, precise = precise,
        )
    }

    private fun Address.parts() = AddressParts(subLocality, locality, subAdminArea, adminArea, countryCode, countryName)

    /** Dubai emirate is IACAD's jurisdiction; the English geocoder names its admin area "Dubai". */
    private fun regionCode(a: Address?): String? {
        val admin = a?.adminArea ?: return null
        return when {
            a.countryCode == "AE" && (admin.contains("Dubai", true) || admin.contains("دبي")) -> Authorities.DUBAI_REGION
            a.countryCode == "AE" -> "AE-OTHER"
            else -> null
        }
    }

    private suspend fun reverseGeocode(p: GeoPoint, locale: Locale): Address? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, locale)
        withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(p.latitude, p.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(p.latitude, p.longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }
}
