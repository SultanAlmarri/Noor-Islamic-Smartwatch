package com.noor.alerts

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.os.UserManagerCompat
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.Armed
import com.noor.prayer.alerts.PlannedAlert
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 0.22: what the prayer alarms remember between firings, in device-protected storage (readable before the phone is
 * first unlocked after a restart: Direct Boot):
 *
 * - the prayers of each day already dealt with (alerted, or reported late), so the same Adhan is never called twice;
 * - the alarm armed for each prayer, so one whose time has come but which Android has not delivered yet is never
 *   silently replaced by the next day's;
 * - a copy of the times and choices the alarms were planned from, so the alarms can be armed and fired before unlock,
 *   when the app's own settings (in credential storage) cannot be read.
 */
internal object AlertStore {
    private const val FILE = "noor_alert_state"
    private const val SNAPSHOT = "config"
    /** Days of handled prayers kept per prayer (today, and a little before it for a clock put back). */
    private const val KEEP = 4

    fun deviceContext(context: Context): Context =
        if (Build.VERSION.SDK_INT >= 24) context.applicationContext.createDeviceProtectedStorageContext() else context.applicationContext

    private fun prefs(context: Context): SharedPreferences = deviceContext(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** False before the phone's first unlock after a restart (Direct Boot): the app's own files cannot be read then. */
    fun unlocked(context: Context): Boolean = runCatching { UserManagerCompat.isUserUnlocked(context) }.getOrDefault(true)

    // ---- handled prayers ----

    private fun handledDates(context: Context, p: Prayer): List<String> =
        prefs(context).getString("h." + p.name, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    fun isHandled(context: Context, p: Prayer, date: LocalDate): Boolean = date.toString() in handledDates(context, p)

    /** [p] of [date] has been dealt with: it is never planned or called again. Only the latest few days are kept. */
    fun markHandled(context: Context, p: Prayer, date: LocalDate) = synchronized(this) {
        val dates = (handledDates(context, p) + date.toString()).distinct()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sortedDescending().take(KEEP)
        prefs(context).edit().putString("h." + p.name, dates.joinToString(",")).apply()
    }

    fun handled(context: Context): (Prayer, LocalDate) -> Boolean {
        val all = Prayer.entries.associateWith { handledDates(context, it).toSet() }
        return { p, d -> d.toString() in all[p].orEmpty() }
    }

    // ---- what is armed ----

    fun armed(context: Context): Map<Prayer, Armed> = Prayer.entries.mapNotNull { p ->
        prefs(context).getString("a." + p.name, null)?.split('|')?.let { f ->
            runCatching { p to Armed(LocalDate.parse(f[0]), Instant.ofEpochMilli(f[1].toLong())) }.getOrNull()
        }
    }.toMap()

    /** The prayer alarms armed now (one per prayer at most); every other prayer has none. */
    fun setArmed(context: Context, plan: List<PlannedAlert>) = synchronized(this) {
        val byPrayer = plan.associateBy { it.prayer }
        val e = prefs(context).edit()
        for (p in Prayer.entries) {
            val x = byPrayer[p]
            if (x == null) e.remove("a." + p.name) else e.putString("a." + p.name, x.date.toString() + "|" + x.at.toEpochMilli())
        }
        e.apply()
    }

    /** [p]'s alarm of [date] has been delivered and needs nothing more (nothing was due). */
    fun clearArmed(context: Context, p: Prayer, date: LocalDate) = synchronized(this) {
        if (armed(context)[p]?.date == date) prefs(context).edit().remove("a." + p.name).apply()
    }

    // ---- alarms being delivered now (this process) ----

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private fun key(p: Prayer, date: LocalDate) = p.name + "|" + date
    fun begin(p: Prayer, date: LocalDate) { inFlight += key(p, date) }
    fun end(p: Prayer, date: LocalDate) { inFlight -= key(p, date) }
    fun inFlight(p: Prayer, date: LocalDate): Boolean = key(p, date) in inFlight

    /** What is armed and not being delivered right now: what a reschedule must not lose. */
    fun pending(context: Context): Map<Prayer, Armed> = armed(context).filterNot { (p, a) -> inFlight(p, a.date) }

    // ---- the copy for Direct Boot ----

    fun saveSnapshot(context: Context, config: WatchConfig?) {
        val e = prefs(context).edit()
        if (config == null) e.remove(SNAPSHOT) else e.putString(SNAPSHOT, config.encode())
        e.apply()
    }

    fun snapshot(context: Context): WatchConfig? = prefs(context).getString(SNAPSHOT, null)?.let { runCatching { WatchConfig.decode(it) }.getOrNull() }

    @androidx.annotation.VisibleForTesting
    fun clearForTests(context: Context) { prefs(context).edit().clear().commit(); inFlight.clear() }
}
