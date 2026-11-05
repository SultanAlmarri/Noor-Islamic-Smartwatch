package com.noor.wear.data

import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.noor.prayer.dhikr.TasbihState
import com.noor.wear.complication.TasbihComplicationService
import com.noor.wear.tile.ToolsTileService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tasbihStore by preferencesDataStore("noor_tasbih")

/** The dhikr counter, kept on the watch only (it never leaves the device). */
class TasbihRepository(private val context: Context) {
    private val key = stringPreferencesKey("state")

    val state: Flow<TasbihState> = context.tasbihStore.data.map { TasbihState.decode(it[key]) }

    suspend fun current(): TasbihState = state.first()

    suspend fun save(s: TasbihState) {
        context.tasbihStore.edit { it[key] = s.encode() }
    }

    /** Tells the tasbih complication and the tools tile to show the new day total (called when leaving the counter). */
    fun refreshSurfaces() {
        runCatching {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, TasbihComplicationService::class.java)).requestUpdateAll()
        }
        runCatching { TileService.getUpdater(context).requestUpdate(ToolsTileService::class.java) }
    }
}
