package com.noor.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.noor.prayer.GeoPoint
import com.noor.prayer.Places
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Battery-conscious location: one balanced-power fix on demand (no continuous tracking),
 * the device's last fix as a fallback, and a reverse-geocode only when a network is available.
 */
class LocationRepository(private val context: Context, private val settings: SettingsRepository) {

    sealed interface Result {
        data class Updated(val location: KnownLocation) : Result
        data object NoPermission : Result
        data object Unavailable : Result
    }

    fun hasForegroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun refresh(): Result {
        if (!hasForegroundPermission()) return Result.NoPermission
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        val fix = withTimeoutOrNull(20_000) {
            runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await() }.getOrNull()
        } ?: runCatching { client.lastLocation.await() }.getOrNull()
        cts.cancel()
        if (fix == null) return Result.Unavailable

        val point = GeoPoint(fix.latitude, fix.longitude, if (fix.hasAltitude()) fix.altitude else 0.0)
        val address = reverseGeocode(point)
        val near = Places.nearest(point)
        val country = address?.countryCode ?: near.countryCode.takeIf { Places.distanceKm(near.point, point) < 150 }
        val region = regionCode(address) ?: near.region.takeIf { Places.distanceKm(near.point, point) < 40 }
        val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea
            ?: near.nameEn.takeIf { Places.distanceKm(near.point, point) < 40 }
        val known = KnownLocation(point, ZoneId.systemDefault().id, name, country, region, System.currentTimeMillis())
        settings.saveLocation(known)
        return Result.Updated(known)
    }

    /** Dubai emirate is IACAD's jurisdiction; the geocoder names it "Dubai" (or "دبي"). */
    private fun regionCode(a: Address?): String? {
        val admin = a?.adminArea ?: return null
        return when {
            a.countryCode == "AE" && (admin.contains("Dubai", true) || admin.contains("دبي")) -> Places.DUBAI
            a.countryCode == "AE" -> "AE-OTHER"
            else -> null
        }
    }

    private suspend fun reverseGeocode(p: GeoPoint): Address? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.ENGLISH)
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
