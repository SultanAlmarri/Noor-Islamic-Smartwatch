package com.noor.alerts

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A sound file the user imported. Stored in the app's private files, so it survives restarts and app updates. */
data class CustomSound(val id: String, val name: String, val seconds: Double, val ext: String, val bytes: Long, val addedAtMs: Long)

sealed interface ImportResult {
    data class Ok(val sound: CustomSound) : ImportResult
    data class Rejected(val reason: Reason) : ImportResult
    enum class Reason { UNSUPPORTED_TYPE, TOO_LARGE, NOT_AUDIO, UNREADABLE }
}

/**
 * Imported sounds: copied into private storage (the original file can be moved or deleted afterwards), checked to be
 * playable audio, and listed in a small JSON index. Sounds can be renamed, replaced (same id, so every prayer using
 * it keeps working) and deleted.
 */
class CustomSounds(
    private val dir: File,
    /** Returns the duration in seconds when [file] is playable audio, otherwise null. Replaceable in tests. */
    private val probe: (File) -> Double? = ::probeAudio,
) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "sounds/custom"))

    private val index = File(dir, "index.json")
    private val lock = Any()

    fun list(): List<CustomSound> = synchronized(lock) { read() }.sortedBy { it.addedAtMs }
    fun get(id: String): CustomSound? = list().firstOrNull { it.id == id }
    fun file(s: CustomSound): File? = File(dir, s.id.removePrefix(PREFIX) + "." + s.ext).takeIf { it.isFile && it.length() > 0 }

    /** Imports [uri]. With [replaceId], the new audio replaces that sound's file and keeps its id and name. */
    fun import(resolver: ContentResolver, uri: Uri, replaceId: String? = null): ImportResult {
        val (name, size) = describe(resolver, uri)
        val mime = resolver.getType(uri)
        val ext = extensionFor(mime, name) ?: return ImportResult.Rejected(ImportResult.Reason.UNSUPPORTED_TYPE)
        if (size != null && size > MAX_BYTES) return ImportResult.Rejected(ImportResult.Reason.TOO_LARGE)
        val input = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return ImportResult.Rejected(ImportResult.Reason.UNREADABLE)
        return input.use { importStream(it, name ?: "Sound", ext, replaceId) }
    }

    fun importStream(input: java.io.InputStream, displayName: String, ext: String, replaceId: String? = null): ImportResult {
        dir.mkdirs()
        val tmp = File(dir, "import-${UUID.randomUUID()}.tmp")
        try {
            var total = 0L
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return ImportResult.Rejected(ImportResult.Reason.TOO_LARGE)
                    out.write(buf, 0, n)
                }
            }
            val seconds = probe(tmp)?.takeIf { it > 0.2 } ?: return ImportResult.Rejected(ImportResult.Reason.NOT_AUDIO)
            synchronized(lock) {
                val all = read().toMutableList()
                val old = replaceId?.let { id -> all.firstOrNull { it.id == id } }
                val id = old?.id ?: (PREFIX + UUID.randomUUID().toString())
                val sound = CustomSound(id, old?.name ?: cleanName(displayName), seconds, ext, total, old?.addedAtMs ?: System.currentTimeMillis())
                old?.let { o -> file(o)?.delete() }
                val target = File(dir, id.removePrefix(PREFIX) + "." + ext)
                if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true) }
                all.removeAll { it.id == id }; all += sound
                write(all)
                return ImportResult.Ok(sound)
            }
        } catch (e: java.io.IOException) {
            return ImportResult.Rejected(ImportResult.Reason.UNREADABLE)
        } finally {
            tmp.delete()
        }
    }

    fun rename(id: String, name: String) = synchronized(lock) {
        write(read().map { if (it.id == id) it.copy(name = cleanName(name)) else it })
    }

    fun delete(id: String) = synchronized(lock) {
        val all = read()
        all.firstOrNull { it.id == id }?.let { s -> file(s)?.delete() }
        write(all.filterNot { it.id == id })
    }

    private fun read(): List<CustomSound> = runCatching {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            CustomSound(o.getString("id"), o.getString("name"), o.getDouble("seconds"), o.getString("ext"), o.getLong("bytes"), o.getLong("added"))
        }
    }.getOrDefault(emptyList())

    private fun write(all: List<CustomSound>) {
        dir.mkdirs()
        val a = JSONArray()
        all.forEach { s -> a.put(JSONObject().put("id", s.id).put("name", s.name).put("seconds", s.seconds).put("ext", s.ext).put("bytes", s.bytes).put("added", s.addedAtMs)) }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(index)) { index.writeText(a.toString()); tmp.delete() }
    }

    companion object {
        const val PREFIX = "custom."
        const val MAX_BYTES = 40L * 1024 * 1024

        /** MIME types offered in the file picker. */
        val PICKER_TYPES = arrayOf("audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/aac", "audio/x-aac", "audio/wav", "audio/x-wav", "audio/wave", "audio/ogg", "audio/opus", "audio/flac", "audio/*")

        private val byMime = mapOf(
            "audio/mpeg" to "mp3", "audio/mp3" to "mp3", "audio/mp4" to "m4a", "audio/x-m4a" to "m4a", "audio/m4a" to "m4a",
            "audio/aac" to "aac", "audio/x-aac" to "aac", "audio/aacp" to "aac", "audio/wav" to "wav", "audio/x-wav" to "wav",
            "audio/wave" to "wav", "audio/vnd.wave" to "wav", "audio/ogg" to "ogg", "application/ogg" to "ogg", "audio/opus" to "opus",
            "audio/flac" to "flac", "audio/x-flac" to "flac",
        )
        private val allowedExt = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac")

        fun extensionFor(mime: String?, name: String?): String? =
            mime?.lowercase()?.let { byMime[it] } ?: name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in allowedExt }

        fun cleanName(name: String): String =
            name.substringBeforeLast('.').replace(Regex("[\\p{Cntrl}]"), " ").replace('_', ' ').trim().take(60).ifEmpty { "Sound" }

        private fun describe(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null to null
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }
                val s = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
                n to s
            } ?: (null to null)
        }.getOrDefault(null to null)

        fun probeAudio(file: File): Double? = runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(file.absolutePath)
                    if (extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") null
                    else extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000.0)
                } finally { release() }
            }
        }.getOrNull()
    }
}
