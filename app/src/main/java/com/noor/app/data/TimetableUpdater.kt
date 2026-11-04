package com.noor.app.data

import android.content.Context
import android.util.Log
import com.noor.prayer.authority.Authority
import com.noor.prayer.authority.Timetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Refreshes official timetables that authorities publish as open data. Currently: MUIS (Singapore) on data.gov.sg
 * (Singapore Open Data Licence), which adds each new year when MUIS releases it. Runs only when the user's authority
 * is MUIS, at most once a week, and sends nothing about the user (a plain request for the public dataset).
 */
class TimetableUpdater(private val context: Context, private val tables: OfficialTimetables) {
    private val prefs = context.getSharedPreferences("noor_timetables", Context.MODE_PRIVATE)

    suspend fun refreshIfDue(authority: Authority?) {
        val id = authority?.timetableId ?: return
        if (id != MUIS) return
        val last = prefs.getLong("checked_$id", 0L)
        val current = tables.get(id)
        // Nothing to do while the table already reaches well into the future and was checked this week.
        if (System.currentTimeMillis() - last < 7 * 86_400_000L) return
        if (current?.last != null && current.last!! > LocalDate.now().plusDays(330)) { mark(id); return }
        val fresh = withContext(Dispatchers.IO) { runCatching { fetchMuis() }.onFailure { Log.i(TAG, "MUIS refresh failed: ${it.message}") }.getOrNull() }
        if (fresh != null && fresh.size > (current?.size ?: 0)) tables.store(id, fresh)
        mark(id)
    }

    private fun mark(id: String) = prefs.edit().putLong("checked_$id", System.currentTimeMillis()).apply()

    private fun fetchMuis(): Timetable {
        val url = URL("https://data.gov.sg/api/action/datastore_search?resource_id=$MUIS_DATASET&limit=5000")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val records = JSONObject(body).getJSONObject("result").getJSONArray("records")
        val lines = (0 until records.length()).map { i ->
            val r = records.getJSONObject(i)
            listOf(r.getString("Date"), am(r.getString("Subuh")), am(r.getString("Syuruk")), noon(r.getString("Zohor")),
                pm(r.getString("Asar")), pm(r.getString("Maghrib")), pm(r.getString("Isyak"))).joinToString(",")
        }
        return Timetable.parse(MUIS, lines.joinToString("\n")).also { require(it.size > 300) { "dataset too small" } }
    }

    companion object {
        const val MUIS = "muis_singapore"
        const val MUIS_DATASET = "d_a6a206cba471fe04b62dd886ef5eaf22"
        private const val TAG = "NoorTimetables"

        // MUIS writes afternoon times both as 12-hour ("04:34") and 24-hour ("16:34"); normalise to 24 h.
        fun am(t: String) = norm(t, false)
        fun pm(t: String) = norm(t, true)
        fun noon(t: String) = norm(t, t.substringBefore(':').trim().toInt() < 11)
        private fun norm(t: String, afternoon: Boolean): String {
            val (h, m) = t.trim().split(':').map { it.toInt() }
            return "%02d:%02d".format(if (afternoon && h < 12) h + 12 else h, m)
        }
    }
}
