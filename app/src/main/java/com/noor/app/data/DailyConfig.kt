package com.noor.app.data

import com.noor.prayer.WatchConfig
import com.noor.shared.geo.OfficialTimetables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * [WearSync.configFor] (with `now` 0) worked out once and kept, off the main thread. It calculates eight weeks of
 * prayer times, and the pages that show what the watch or the alarms will do ask for it often: every second (the
 * Watch page's preview), at every turn of a wheel (the alarm editor), on every page of the Adhan tab.
 *
 * The result is exactly what configFor would return now: everything configFor depends on is in the key — the
 * settings, the place's time zone (in automatic mode the phone's own, which changes when travelling), the date at the
 * place (the eight weeks start from it) and the timetables with their [OfficialTimetables.version]. Only
 * `updatedAtEpochMs` is 0, which nothing that plans or shows times reads.
 */
object DailyConfig {
    private data class Key(val settings: AppSettings, val tablesVersion: Int, val zone: ZoneId?, val date: LocalDate?)
    private class Kept(val key: Key, val config: WatchConfig?)

    private val lock = Mutex()
    /** The last result for each set of timetables (the app's own, or none). */
    @Volatile private var kept: Map<OfficialTimetables?, Kept> = emptyMap()

    private fun key(s: AppSettings, tables: OfficialTimetables?): Key {
        val zone = PrayerProvider.place(s)?.zone
        return Key(s, tables?.version ?: 0, zone, zone?.let { LocalDate.now(it) })
    }

    /** The configuration for [s] as configFor(s, 0L, tables) gives it now; calculated on [Dispatchers.Default] when not kept. */
    suspend fun get(s: AppSettings, tables: OfficialTimetables?): WatchConfig? {
        kept[tables]?.takeIf { it.key == key(s, tables) }?.let { return it.config }
        return lock.withLock {
            withContext(Dispatchers.Default) {
                val k = key(s, tables)
                val had = kept[tables]?.takeIf { it.key == k }
                if (had != null) had.config else WearSync.configFor(s, 0L, tables).also { kept = kept + (tables to Kept(k, it)) }
            }
        }
    }

    /** The configuration if it is already worked out for [s] as it is now, else null; cheap (for a first frame). */
    fun peek(s: AppSettings, tables: OfficialTimetables?): WatchConfig? =
        kept[tables]?.takeIf { it.key == key(s, tables) }?.config

    /**
     * The configuration for each settings value, worked out again when the date or the time zone at the place changes
     * (checked once a second, which costs next to nothing); everything on [Dispatchers.Default].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun flow(settings: Flow<AppSettings>, tables: OfficialTimetables?): Flow<WatchConfig?> = settings
        .flatMapLatest { s -> flow { while (true) { emit(key(s, tables)); delay(1_000) } } }
        .distinctUntilChanged()
        .map { get(it.settings, tables) }
        .flowOn(Dispatchers.Default)
}
