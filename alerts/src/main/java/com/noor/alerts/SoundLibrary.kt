package com.noor.alerts

import android.content.Context
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.PrayerAlert
import com.noor.prayer.alerts.SoundIds
import org.json.JSONObject
import java.io.File

/** Who made a recording and under which licence; shown next to every bundled Adhan. */
data class SoundCredit(
    val author: String,
    val licence: String,
    val licenceUrl: String?,
    val sourceUrl: String?,
    val changes: String?,
)

enum class SoundKind { ADHAN, ALT }

/** One bundled sound, as described in assets/sounds/library.json. */
data class SoundEntry(
    val id: String,
    val kind: SoundKind,
    val titleEn: String,
    val titleAr: String,
    val placeEn: String?,
    val placeAr: String?,
    /** For Adhans: traditional, renowned, regional, fajr. For sounds: brief, short, long. */
    val categories: Set<String>,
    /** The recording contains "As-salatu khayrun min an-nawm" and is only for Fajr. */
    val fajr: Boolean,
    val file: String?,
    val seconds: Double,
    val shortFile: String?,
    val shortSeconds: Double?,
    val credit: SoundCredit?,
    /** Anything the listener should know about the recording (crowd noise, an excerpt, an early ending). */
    val noteEn: String? = null,
    val noteAr: String? = null,
) {
    /** False when the original recording does not contain the whole Adhan: only the short version exists. */
    val hasFullRecording: Boolean get() = file != null
    fun note(arabic: Boolean) = if (arabic) noteAr ?: noteEn else noteEn
    fun title(arabic: Boolean) = if (arabic) titleAr else titleEn
    fun place(arabic: Boolean) = if (arabic) placeAr ?: placeEn else placeEn
}

/** Where to read the audio from. */
sealed interface SoundSource {
    data class Asset(val path: String) : SoundSource
    data class LocalFile(val file: File) : SoundSource
}

/** Why the sound that plays differs from the one chosen. Always shown to the user, never silent. */
enum class Substitution { FULL_NOT_ON_DEVICE, NO_FULL_RECORDING, CUSTOM_MISSING, SOUND_MISSING, FAJR_ADHAN_OUTSIDE_FAJR }

data class ResolvedSound(val source: SoundSource, val entry: SoundEntry?, val customName: String?, val short: Boolean, val substitution: Substitution?)

/**
 * The catalogue of bundled sounds. The phone bundles every file; the watch bundles the short Adhans and the alert
 * sounds, and receives full Adhans or imported sounds from the phone into [transferDir] when the user wants them there.
 */
class SoundLibrary(val entries: List<SoundEntry>, private val exists: (String) -> Boolean, private val transferDir: File?) {

    private val byId = entries.associateBy { it.id }
    fun byId(id: String?): SoundEntry? = id?.let { byId[it] }

    val adhans: List<SoundEntry> get() = entries.filter { it.kind == SoundKind.ADHAN }
    val sounds: List<SoundEntry> get() = entries.filter { it.kind == SoundKind.ALT }.sortedBy { it.seconds }

    /** Adhans suitable for [p]: Fajr gets its own recordings first; the Fajr Adhan is never offered for other prayers. */
    fun adhansFor(p: Prayer): List<SoundEntry> =
        if (p == Prayer.FAJR) adhans.sortedByDescending { it.fajr } else adhans.filterNot { it.fajr }

    fun hasFull(e: SoundEntry) = e.file != null && (exists(e.file) || transferred(e.id) != null)
    fun hasShort(e: SoundEntry) = e.shortFile != null && exists(e.shortFile)

    /** A file the phone sent to this device (watch), in any audio format. */
    fun transferred(id: String): File? = transferDir?.listFiles()?.firstOrNull { f ->
        f.isFile && f.length() > 0 && f.name.startsWith("$id.") && !f.name.endsWith(".part") && f.name.substringBeforeLast('.') == id
    }

    /** Picks the file to play for [alert] at [prayer], falling back openly when the chosen file is unavailable. */
    fun resolve(prayer: Prayer, alert: PrayerAlert, custom: CustomSounds?): ResolvedSound? {
        fun fallbackSound(sub: Substitution): ResolvedSound? =
            byId(SoundIds.DEFAULT_SOUND)?.let { e -> e.file?.takeIf(exists)?.let { ResolvedSound(SoundSource.Asset(it), e, null, false, sub) } }

        fun adhan(short: Boolean): ResolvedSound? {
            var sub: Substitution? = null
            var e = byId(alert.soundId) ?: byId(if (prayer == Prayer.FAJR) SoundIds.DEFAULT_FAJR_ADHAN else SoundIds.DEFAULT_ADHAN) ?: adhansFor(prayer).firstOrNull()
            if (e != null && e.fajr && prayer != Prayer.FAJR) {
                e = byId(SoundIds.DEFAULT_ADHAN) ?: adhansFor(prayer).firstOrNull(); sub = Substitution.FAJR_ADHAN_OUTSIDE_FAJR
            }
            e ?: return fallbackSound(Substitution.SOUND_MISSING)
            if (!short) {
                e.file?.takeIf(exists)?.let { return ResolvedSound(SoundSource.Asset(it), e, null, false, sub) }
                transferred(e.id)?.let { return ResolvedSound(SoundSource.LocalFile(it), e, null, false, sub) }
                sub = sub ?: if (e.hasFullRecording) Substitution.FULL_NOT_ON_DEVICE else Substitution.NO_FULL_RECORDING
            }
            e.shortFile?.takeIf(exists)?.let { return ResolvedSound(SoundSource.Asset(it), e, null, true, sub) }
            return fallbackSound(sub ?: Substitution.SOUND_MISSING)
        }

        return when (alert.mode) {
            AlertMode.ADHAN -> adhan(short = false)
            AlertMode.ADHAN_SHORT -> adhan(short = true)
            AlertMode.SOUND -> {
                val e = byId(alert.soundId)?.takeIf { it.kind == SoundKind.ALT } ?: byId(SoundIds.DEFAULT_SOUND)
                e?.file?.takeIf(exists)?.let { ResolvedSound(SoundSource.Asset(it), e, null, false, null) } ?: fallbackSound(Substitution.SOUND_MISSING)
            }
            AlertMode.CUSTOM -> {
                val c = alert.soundId?.let { custom?.get(it) }
                val f = c?.let { custom?.file(it) }
                val sent = alert.soundId?.let { transferred(it) }
                when {
                    c != null && f != null -> ResolvedSound(SoundSource.LocalFile(f), null, c.name, false, null)
                    sent != null -> ResolvedSound(SoundSource.LocalFile(sent), null, null, false, null)
                    else -> fallbackSound(Substitution.CUSTOM_MISSING)
                }
            }
            AlertMode.NOTIFICATION, AlertMode.SILENT -> null
        }
    }

    companion object {
        const val ASSET_PATH = "sounds/library.json"
        @Volatile private var cached: SoundLibrary? = null

        fun get(context: Context): SoundLibrary = cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }

        private fun load(context: Context): SoundLibrary {
            val app = context.applicationContext
            val text = runCatching { app.assets.open(ASSET_PATH).bufferedReader().use { it.readText() } }.getOrNull()
            val present = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
            val exists: (String) -> Boolean = { path ->
                present.getOrPut(path) { runCatching { app.assets.open(path).close(); true }.getOrDefault(false) }
            }
            return SoundLibrary(text?.let(::parse).orEmpty(), exists, File(app.filesDir, "sounds/received"))
        }

        fun parse(text: String): List<SoundEntry> {
            val arr = JSONObject(text).getJSONArray("sounds")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val cats = o.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
                val short = o.optJSONObject("short")
                val credit = o.optJSONObject("credit")?.let { c ->
                    SoundCredit(c.getString("author"), c.getString("licence"), c.optString("licence_url").ifEmpty { null },
                        c.optString("source").ifEmpty { null }, c.optString("changes").ifEmpty { null })
                }
                SoundEntry(
                    id = o.getString("id"),
                    kind = if (o.getString("kind") == "adhan") SoundKind.ADHAN else SoundKind.ALT,
                    titleEn = o.getString("title_en"), titleAr = o.getString("title_ar"),
                    placeEn = o.optString("place_en").ifEmpty { null }, placeAr = o.optString("place_ar").ifEmpty { null },
                    categories = cats, fajr = o.optBoolean("fajr"),
                    file = o.optString("file").ifEmpty { null }, seconds = o.optDouble("seconds", 0.0),
                    shortFile = short?.optString("file")?.ifEmpty { null }, shortSeconds = short?.optDouble("seconds"),
                    credit = credit,
                    noteEn = o.optString("note_en").ifEmpty { null }, noteAr = o.optString("note_ar").ifEmpty { null },
                )
            }
        }
    }
}
