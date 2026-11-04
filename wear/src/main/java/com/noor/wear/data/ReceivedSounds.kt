package com.noor.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.SoundIds
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Sounds the phone sends to the watch (full Adhans, imported sounds), kept in private storage.
 * Files the current settings no longer use are removed, so the watch's storage stays small.
 */
object ReceivedSounds {
    const val PATH_PREFIX = "/noor/sound/"
    private const val MAX_BYTES = 45L * 1024 * 1024
    private val allowedExt = setOf("ogg", "mp3", "m4a", "aac", "wav", "oga", "opus", "flac")

    fun dir(context: Context) = File(context.filesDir, "sounds/received")

    /** Ids present on the watch. */
    fun ids(context: Context): Set<String> =
        dir(context).listFiles().orEmpty().filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }
            .map { it.name.substringBeforeLast('.') }.filter(SoundIds::isValid).toSet()

    /** Called on the listener's background thread when the phone opens a channel. Blocks until the file is stored. */
    fun receive(context: Context, channel: ChannelClient.Channel): Boolean {
        val rest = channel.path.removePrefix(PATH_PREFIX)
        val id = rest.substringBefore('/')
        val ext = rest.substringAfter('/', "").lowercase()
        val client = Wearable.getChannelClient(context)
        if (!SoundIds.isValid(id) || ext !in allowedExt) { client.close(channel); return false }
        val d = dir(context).apply { mkdirs() }
        val part = File(d, "$id.$ext.part")
        return try {
            val input = Tasks.await(client.getInputStream(channel), 30, TimeUnit.SECONDS)
            var total = 0L
            input.use { inp ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw java.io.IOException("too large")
                        out.write(buf, 0, n)
                    }
                }
            }
            if (total == 0L) throw java.io.IOException("empty")
            d.listFiles().orEmpty().filter { it.name.startsWith("$id.") && it != part }.forEach { it.delete() }
            part.renameTo(File(d, "$id.$ext"))
        } catch (e: Exception) {
            Log.w("NoorSounds", "Receiving $id failed", e)
            part.delete(); false
        } finally {
            runCatching { client.close(channel) }
        }
    }

    /** Removes received sounds that no prayer alerting on this watch uses any more. */
    fun prune(context: Context, config: WatchConfig?) {
        val a = config?.alerts
        val keep = if (a == null || !a.enabled || !a.device.includes(AlertDevice.WATCH)) emptySet() else
            Prayer.entries.filter { a.alertsOn(it, AlertDevice.WATCH) }.mapNotNull { p -> a.alert(p).takeIf { it.mode == AlertMode.ADHAN || it.mode == AlertMode.CUSTOM }?.soundId }.toSet() +
                setOf(SoundIds.DEFAULT_ADHAN, SoundIds.DEFAULT_FAJR_ADHAN)
        dir(context).listFiles().orEmpty().filter { it.name.substringBeforeLast('.') !in keep || it.name.endsWith(".part") }.forEach { it.delete() }
    }
}
