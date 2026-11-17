package com.noor.shared.geo

import android.content.Context
import android.util.Log
import com.noor.prayer.authority.Timetable
import com.noor.prayer.geo.Gazetteer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

/** The offline world gazetteer (GeoNames, CC BY 4.0), loaded once from the app's assets on first use (~100 ms). */
class GazetteerStore(private val context: Context) {
    private val lock = Mutex()
    @Volatile private var loaded: Gazetteer? = null

    suspend fun get(): Gazetteer = loaded ?: lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            GZIPInputStream(context.assets.open("gazetteer.bin")).bufferedReader().useLines { Gazetteer.parse(it.toList().asSequence()) }
        }.also { loaded = it }
    }
}

/**
 * Authorities' own published timetables. Bundled with the app (assets/timetables) and, where the authority offers an
 * open-data feed, refreshed into the app's private storage; the larger of the two is used.
 */
class OfficialTimetables(private val context: Context) {
    private val cache = HashMap<String, Timetable?>()

    @Synchronized
    fun get(id: String): Timetable? = cache.getOrPut(id) {
        val bundled = runCatching { context.assets.open("timetables/$id.csv").bufferedReader().readText() }.getOrNull()?.let { Timetable.parse(id, it) }
        val downloaded = file(id).takeIf { it.exists() }?.let { f -> runCatching { Timetable.parse(id, f.readText()) }.getOrNull() }
        listOfNotNull(bundled, downloaded).maxByOrNull { it.size }
    }

    @Synchronized
    fun store(id: String, table: Timetable) {
        runCatching {
            file(id).parentFile?.mkdirs()
            file(id).writeText(table.encode() + "\n")
            cache.remove(id)
        }.onFailure { Log.w("NoorTimetables", "could not store $id", it) }
    }

    private fun file(id: String) = File(context.filesDir, "timetables/$id.csv")
}
