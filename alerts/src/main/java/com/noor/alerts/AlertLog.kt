package com.noor.alerts

import android.content.Context
import com.noor.prayer.Prayer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What actually happened when an alert was due. Every path through the alert code ends in exactly one of these. */
enum class AlertOutcome {
    /** The sound is starting (replaced by the final outcome when it ends; stays if the process was killed meanwhile). */
    STARTING,
    /** The chosen sound played (to the end, until the user stopped it, or until the chosen limit). */
    PLAYED,
    /** The chosen sound could not be played; Noor's default Adhan played instead. */
    PLAYED_FALLBACK,
    /** Android did not let Noor start its player; the backup notification sounded a short Adhan instead. */
    BACKUP_SOUND,
    /** Nothing could be played; the notification sound was used. */
    FAILED,
    /** The user chose a notification for this prayer. */
    NOTIFICATION,
    /** The user chose a silent notification for this prayer. */
    SILENT,
    /** The user chose to stay quiet while the phone is silent, on vibrate or in Do Not Disturb, and it was. */
    QUIET_BY_CHOICE,
    /** Do Not Disturb is set to block alarms too. */
    DND_BLOCKS_ALARMS,
    /** A call was in progress. */
    IN_CALL,
    /** Notifications are turned off for Noor, so nothing could be shown or played. */
    NO_NOTIFICATIONS,
    /** The device was off or asleep and the alarm came more than ten minutes after the prayer time. */
    LATE,
    /** There was no location or no settings, so Noor could not tell which prayer was due. */
    NO_SETTINGS,
}

/**
 * One alert as it happened. [dueAtMs] is the prayer time, [firedAtMs] when Android delivered the alarm, so the
 * difference shows whether alarms arrive on time on this device.
 */
data class AlertRecord(
    val id: Long,
    val prayer: Prayer?,
    val test: Boolean,
    val dueAtMs: Long,
    val firedAtMs: Long,
    val outcome: AlertOutcome,
    /** What was chosen ("adhan.makkah_maghrib_2012", "custom.…", or the mode for notifications). */
    val sound: String? = null,
    /** How long sound actually came out, in milliseconds. */
    val playedMs: Long = 0,
    /** True when the user pressed Stop (or swiped the notification away). */
    val stoppedByUser: Boolean = false,
    /** Whether exact alarms were allowed when the alert was due. */
    val exactAlarms: Boolean = true,
    /** Alarm volume, 0–100, when the alert was due. */
    val alarmVolumePercent: Int = -1,
    /** AudioManager ringer mode when the alert was due (0 silent, 1 vibrate, 2 normal), or -1. */
    val ringerMode: Int = -1,
    /** A technical detail for the report (the player's error, the exception), never shown as the main text. */
    val detail: String? = null,
) {
    val delayMs: Long get() = firedAtMs - dueAtMs

    internal fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("prayer", prayer?.name ?: ""); put("test", test); put("due", dueAtMs); put("fired", firedAtMs)
        put("outcome", outcome.name); put("sound", sound ?: ""); put("played", playedMs); put("stopped", stoppedByUser)
        put("exact", exactAlarms); put("vol", alarmVolumePercent); put("ringer", ringerMode); put("detail", detail ?: "")
    }

    companion object {
        internal fun fromJson(o: JSONObject): AlertRecord? = runCatching {
            AlertRecord(
                id = o.getLong("id"),
                prayer = o.optString("prayer").let { n -> Prayer.entries.firstOrNull { it.name == n } },
                test = o.optBoolean("test"),
                dueAtMs = o.getLong("due"),
                firedAtMs = o.getLong("fired"),
                outcome = AlertOutcome.valueOf(o.getString("outcome")),
                sound = o.optString("sound").ifEmpty { null },
                playedMs = o.optLong("played"),
                stoppedByUser = o.optBoolean("stopped"),
                exactAlarms = o.optBoolean("exact", true),
                alarmVolumePercent = o.optInt("vol", -1),
                ringerMode = o.optInt("ringer", -1),
                detail = o.optString("detail").ifEmpty { null },
            )
        }.getOrNull()
    }
}

/**
 * The last [MAX] alerts on this device, newest first, kept in a small private file. The alert components write to it
 * from the alarm receiver and the player service; the app shows it as "Alert history" and can share it as a report.
 */
object AlertLog {
    const val MAX = 40
    private val lock = Any()
    private const val FILE = "alert_history.json"

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, FILE)

    fun all(context: Context): List<AlertRecord> = synchronized(lock) { read(file(context)) }

    fun add(context: Context, record: AlertRecord) = synchronized(lock) {
        val f = file(context)
        write(f, (listOf(record) + read(f).filter { it.id != record.id }).take(MAX))
    }

    /** Replaces the record [id] with [change] applied; does nothing if it is gone. */
    fun update(context: Context, id: Long, change: (AlertRecord) -> AlertRecord) = synchronized(lock) {
        val f = file(context)
        val list = read(f)
        if (list.none { it.id == id }) return@synchronized
        write(f, list.map { if (it.id == id) change(it) else it })
    }

    fun clear(context: Context) = synchronized(lock) { file(context).delete(); Unit }

    private fun read(f: File): List<AlertRecord> {
        if (!f.exists()) return emptyList()
        return runCatching {
            val a = JSONArray(f.readText())
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(AlertRecord::fromJson) }
        }.getOrDefault(emptyList())
    }

    private fun write(f: File, list: List<AlertRecord>) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}
