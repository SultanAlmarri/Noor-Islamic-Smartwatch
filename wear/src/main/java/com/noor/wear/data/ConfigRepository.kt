package com.noor.wear.data

import android.content.ComponentName
import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.noor.prayer.LocalSetup
import com.noor.prayer.WatchConfig
import com.noor.shared.geo.OfficialTimetables
import com.noor.wear.complication.HijriComplicationService
import com.noor.wear.complication.NextPrayerComplicationService
import com.noor.wear.complication.TasbihComplicationService
import com.noor.wear.tile.PrayerTileService
import com.noor.wear.tile.ToolsTileService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.ZoneId

private val Context.store by preferencesDataStore("noor_watch")

/** Where the times on the watch come from. */
enum class ConfigSource {
    /** The Noor phone app's settings (the default whenever the phone has sent them). */
    PHONE,
    /** Set up on the watch itself: its own location or a city, chosen on the wrist. */
    WATCH,
    /** Nothing yet. */
    NONE,
}

/** What the watch shows, and where it came from. */
data class WatchState(
    val config: WatchConfig?,
    val source: ConfigSource,
    val setup: LocalSetup?,
    val hasPhoneConfig: Boolean,
    val phoneReceivedAt: Long?,
)

/**
 * The watch keeps the last configuration it received from the phone, so it keeps working with the phone switched
 * off, out of range or uninstalled. It can also be set up on its own ([LocalSetup]); then it calculates exactly as the
 * phone would for that place. The phone's settings are used whenever it has sent some, unless the user chose the
 * watch's own settings on the watch.
 */
class ConfigRepository(private val context: Context, private val timetables: OfficialTimetables = OfficialTimetables(context)) {
    private val keyConfig = stringPreferencesKey("config")
    private val keyReceived = longPreferencesKey("received_at")
    private val keySetup = stringPreferencesKey("setup")
    private val keyLocal = stringPreferencesKey("local_config")
    private val keyPreferWatch = booleanPreferencesKey("prefer_watch")

    val state: Flow<WatchState> = context.store.data.map { p ->
        val phone = p[keyConfig]?.let(WatchConfig::decode)
        val local = p[keyLocal]?.let(WatchConfig::decode)
        val preferWatch = p[keyPreferWatch] == true
        val (config, source) = when {
            phone != null && (!preferWatch || local == null) -> phone to ConfigSource.PHONE
            local != null -> local to ConfigSource.WATCH
            else -> null to ConfigSource.NONE
        }
        WatchState(config, source, LocalSetup.decode(p[keySetup]), phone != null, p[keyReceived])
    }

    val config: Flow<WatchConfig?> = state.map { it.config }
    val receivedAt: Flow<Long?> = context.store.data.map { it[keyReceived] }

    suspend fun current(): WatchConfig? = config.first()

    /** The stored configuration, or the one waiting in the Data Layer if the watch app was installed after the phone published it. */
    suspend fun currentOrPull(): WatchConfig? = current() ?: run { pullFromDataLayer(); current() }

    /** Stores a configuration from the phone. Returns true when it changed anything the watch shows. */
    suspend fun save(encoded: String): Boolean {
        val incoming = WatchConfig.decode(encoded) ?: return false
        val before = current()
        context.store.edit { p ->
            p[keyConfig] = encoded
            p[keyReceived] = System.currentTimeMillis()
        }
        val after = current()
        val changed = before?.copy(updatedAtEpochMs = 0) != after?.copy(updatedAtEpochMs = 0)
        if (changed) refreshSurfaces(context)
        // Prayer alerts on the watch follow the phone's choices (when the user chose "Watch" or "Both").
        ReceivedSounds.prune(context, incoming)
        afterChange()
        return changed
    }

    /** Saves the watch's own settings and recalculates from them. */
    suspend fun saveSetup(setup: LocalSetup, useNow: Boolean = true) {
        val built = build(setup)
        context.store.edit { p ->
            p[keySetup] = setup.encode()
            if (built != null) p[keyLocal] = built.encode() else p.remove(keyLocal)
            if (useNow) p[keyPreferWatch] = true
        }
        refreshSurfaces(context)
        afterChange()
    }

    /** Switches between the phone's settings and the watch's own (when both exist). */
    suspend fun useWatchSettings(watch: Boolean) {
        context.store.edit { it[keyPreferWatch] = watch }
        refreshSurfaces(context)
        afterChange()
    }

    /**
     * Recalculates the watch's own configuration when something it depends on moved: the watch's time zone (own
     * location follows it), its 12/24-hour setting, or the window of official times running short.
     */
    suspend fun refreshLocal(force: Boolean = false) {
        val s = state.first()
        val setup = s.setup ?: return
        val old = context.store.data.first()[keyLocal]?.let(WatchConfig::decode)
        val ageDays = old?.let { (System.currentTimeMillis() - it.updatedAtEpochMs) / 86_400_000L } ?: Long.MAX_VALUE
        val zoneMoved = old != null && setup.place?.fromGps == true && old.zoneId != ZoneId.systemDefault().id
        val clockMoved = old != null && setup.use24h == null && old.use24h != DateFormat.is24HourFormat(context)
        if (!force && old != null && ageDays < 7 && !zoneMoved && !clockMoved) return
        val built = build(setup) ?: return
        context.store.edit { it[keyLocal] = built.encode() }
        if (s.source == ConfigSource.WATCH) { refreshSurfaces(context); afterChange() }
    }

    private fun build(setup: LocalSetup): WatchConfig? =
        setup.toConfig(System.currentTimeMillis(), ZoneId.systemDefault(), DateFormat.is24HourFormat(context)) { timetables.get(it) }

    private suspend fun afterChange() {
        com.noor.alerts.AlertScheduler.reschedule(context)
        StatusPublisher.publish(context)
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

        /** Asks the complications and the tiles to redraw with fresh data. */
        fun refreshSurfaces(context: Context) {
            for (c in listOf(NextPrayerComplicationService::class.java, HijriComplicationService::class.java, TasbihComplicationService::class.java)) {
                runCatching { ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, c)).requestUpdateAll() }
            }
            runCatching { TileService.getUpdater(context).requestUpdate(PrayerTileService::class.java) }
            runCatching { TileService.getUpdater(context).requestUpdate(ToolsTileService::class.java) }
        }
    }
}
