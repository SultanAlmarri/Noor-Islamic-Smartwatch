package com.noor.wear.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.noor.prayer.GeoPoint
import com.noor.prayer.Places
import com.noor.prayer.SetupPlace
import com.noor.prayer.geo.PlaceNaming
import com.noor.shared.geo.GazetteerStore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/**
 * One position from the watch, named offline. Wear OS gives the watch's own GPS, or the phone's location while the
 * phone is connected; either way only when the user has allowed location for Noor on the watch. The position is used
 * for prayer times and the Qibla and never leaves the watch.
 */
class WatchLocation(private val context: Context, private val gazetteer: GazetteerStore) {

    sealed interface Result {
        data class Found(val place: SetupPlace) : Result
        data object NoPermission : Result
        /** No fix in time: indoors, GPS off, or no phone connected on a watch without GPS. */
        data object Unavailable : Result
    }

    fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun precise(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** True when the watch has its own GPS (most Galaxy and Pixel watches). Without it, the phone's position is used. */
    val hasGps: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    @SuppressLint("MissingPermission")
    suspend fun locate(): Result {
        if (!hasPermission()) return Result.NoPermission
        val fix = fused() ?: platform() ?: return Result.Unavailable
        return Result.Found(describe(GeoPoint(fix.latitude, fix.longitude, if (fix.hasAltitude()) fix.altitude else 0.0), precise()))
    }

    @SuppressLint("MissingPermission")
    private suspend fun fused(): Location? = runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            // City-level accuracy is plenty for prayer times (a minute is roughly 25 km east–west) and saves battery.
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(10 * 60_000L)
            .setDurationMillis(45_000L)
            .build()
        try {
            withTimeoutOrNull(50_000) { client.getCurrentLocation(request, cts.token).await() } ?: client.lastLocation.await()
        } finally { cts.cancel() }
    }.getOrNull()

    /** Fallback for watches without Google's location service. */
    @SuppressLint("MissingPermission")
    private fun platform(): Location? = runCatching {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }
            .filter { System.currentTimeMillis() - it.time < 60 * 60_000L }
            .maxByOrNull { it.time }
    }.getOrNull()

    /** Names a position with the offline gazetteer; falls back to coordinates rather than inventing a name. */
    suspend fun describe(point: GeoPoint, precise: Boolean): SetupPlace {
        val gaz = runCatching { gazetteer.get().nearest(point, 30.0, specific = precise) }.getOrNull()
        val near = Places.nearest(point)
        val en = gaz?.let { PlaceNaming.label(it, false).primary } ?: "%.3f, %.3f".format(java.util.Locale.ROOT, point.latitude, point.longitude)
        val ar = gaz?.let { PlaceNaming.label(it, true).primary }
        val elevation = if (point.elevationM != 0.0) point.elevationM else gaz?.point?.elevationM ?: 0.0
        return SetupPlace(
            point = GeoPoint(point.latitude, point.longitude, elevation),
            zoneId = ZoneId.systemDefault().id,
            nameEn = en,
            nameAr = ar,
            countryCode = gaz?.countryCode ?: near.countryCode.takeIf { Places.distanceKm(near.point, point) < 150 },
            regionCode = gaz?.region ?: near.region.takeIf { Places.distanceKm(near.point, point) < 40 },
            fromGps = true,
            fixedAtEpochMs = System.currentTimeMillis(),
        )
    }
}
