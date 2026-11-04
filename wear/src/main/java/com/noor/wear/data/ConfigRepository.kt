package com.noor.wear.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.noor.prayer.WatchConfig
import com.noor.wear.complication.NextPrayerComplicationService
import com.noor.wear.tile.PrayerTileService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private val Context.store by preferencesDataStore("noor_watch")

/**
 * The watch keeps the last configuration it received from the phone, so it keeps working with the phone
 * switched off, out of range or uninstalled.
 */
class ConfigRepository(private val context: Context) {
    private val keyConfig = stringPreferencesKey("config")
    private val keyReceived = longPreferencesKey("received_at")

    val config: Flow<WatchConfig?> = context.store.data.map { p -> p[keyConfig]?.let(WatchConfig::decode) }
    val receivedAt: Flow<Long?> = context.store.data.map { it[keyReceived] }

    suspend fun current(): WatchConfig? = config.first()

    /** The stored configuration, or the one waiting in the Data Layer if the watch app was installed after the phone published it. */
    suspend fun currentOrPull(): WatchConfig? = current() ?: run { pullFromDataLayer(); current() }

    /** Stores a configuration from the phone. Returns true when it changed anything the watch shows. */
    suspend fun save(encoded: String): Boolean {
        val incoming = WatchConfig.decode(encoded) ?: return false
        var changed = false
        context.store.edit { p ->
            val old = p[keyConfig]?.let(WatchConfig::decode)
            changed = old?.copy(updatedAtEpochMs = 0) != incoming.copy(updatedAtEpochMs = 0)
            p[keyConfig] = encoded
            p[keyReceived] = System.currentTimeMillis()
        }
        if (changed) refreshSurfaces(context)
        return changed
    }

    /**
     * Reads the phone's latest item straight from the Data Layer. Covers the case where the listener service
     * was not running when the item arrived (first install, app update).
     */
    suspend fun pullFromDataLayer() {
        runCatching {
            val items = Wearable.getDataClient(context).dataItems.await()
            try {
                items.firstOrNull { it.uri.path == WatchConfig.PATH }?.let { item ->
                    DataMapItem.fromDataItem(item).dataMap.getString(KEY_CONFIG)?.let { save(it) }
                }
            } finally {
                items.release()
            }
        }.onFailure { Log.w(TAG, "Data Layer not available", it) }
    }

    companion object {
        const val KEY_CONFIG = "config"
        private const val TAG = "NoorConfig"

        /** Asks the complication and the tile to redraw with fresh data. */
        fun refreshSurfaces(context: Context) {
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(context, ComponentName(context, NextPrayerComplicationService::class.java))
                    .requestUpdateAll()
            }
            runCatching { TileService.getUpdater(context).requestUpdate(PrayerTileService::class.java) }
        }
    }
}
