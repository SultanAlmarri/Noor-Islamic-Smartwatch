package com.noor.app.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.noor.prayer.WatchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What the phone knows about the watch side. */
data class WatchStatus(
    /** Watches currently connected to this phone (Bluetooth or cloud), by display name. */
    val connected: List<String>,
    /** Watches (connected or not) that have the Noor watch app installed. */
    val withNoor: List<String>,
    /** Play services' Wear OS layer is unavailable on this phone (no Wear OS app / companion). */
    val unavailable: Boolean = false,
)

/**
 * Publishes the phone's resolved settings to the Wear OS Data Layer. The system delivers the item to the watch
 * whenever it connects, also when Noor is closed on both devices, and keeps it for watches paired later.
 */
class WearSync(private val context: Context, private val settings: SettingsRepository) {

    private val _lastSent = MutableStateFlow<Long?>(null)
    val lastSent: StateFlow<Long?> = _lastSent.asStateFlow()

    fun start(scope: CoroutineScope) = scope.launch {
        settings.settings
            .map { configFor(it, System.currentTimeMillis()) }
            .distinctUntilChanged { a, b -> a?.copy(updatedAtEpochMs = 0) == b?.copy(updatedAtEpochMs = 0) }
            .collect { c -> if (c != null) push(c) }
    }

    /** Sends the current settings now (the "Sync now" button). */
    suspend fun syncNow(): Boolean = configFor(settings.current(), System.currentTimeMillis())?.let { push(it) } ?: false

    private suspend fun push(c: WatchConfig): Boolean = runCatching {
        val req = PutDataMapRequest.create(WatchConfig.PATH).apply {
            dataMap.putString(KEY_CONFIG, c.encode())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(req).await()
        _lastSent.value = System.currentTimeMillis()
        true
    }.getOrElse { Log.w(TAG, "Could not publish to the Data Layer", it); false }

    suspend fun status(): WatchStatus = runCatching {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        val noor = Wearable.getCapabilityClient(context)
            .getCapability(WatchConfig.CAPABILITY_WATCH, CapabilityClient.FILTER_ALL).await().nodes
        WatchStatus(nodes.map { it.displayName }, noor.map { it.displayName })
    }.getOrElse { WatchStatus(emptyList(), emptyList(), unavailable = true) }

    companion object {
        const val KEY_CONFIG = "config"
        private const val TAG = "NoorWearSync"

        /** Resolves every automatic choice on the phone so the watch calculates exactly the same times. */
        fun configFor(s: AppSettings, now: Long): WatchConfig? {
            val place = PrayerProvider.place(s) ?: return null
            val profile = PrayerProvider.profile(s, place)
            return WatchConfig(
                point = place.point,
                zoneId = place.zone.id,
                placeEn = place.nameEn,
                placeAr = place.nameAr,
                fromGps = place.fromGps,
                profileId = profile.id,
                asrSchool = s.asrSchool,
                highLatitudeRule = s.highLatitudeRule,
                offsets = s.offsets,
                hijriAdjust = s.hijriAdjust,
                language = s.language.tag,
                arabicDigits = s.numerals == Numerals.ARABIC_INDIC,
                use24h = s.timeFormat == TimeFormat.H24,
                updatedAtEpochMs = now,
            )
        }
    }
}
