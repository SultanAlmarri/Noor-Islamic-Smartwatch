package com.noor.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.noor.alerts.CustomSounds
import com.noor.alerts.SoundLibrary
import com.noor.prayer.Prayer
import com.noor.prayer.WatchReport
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.SoundIds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.InputStream

/**
 * The watch carries the short Adhans and the alert sounds itself. A full Adhan or an imported sound chosen for a prayer
 * that alerts on the watch is sent from the phone over the Wear OS Data Layer (a channel per file), once; the watch
 * reports which sounds it holds, so nothing is sent twice.
 */
class WatchSoundSender(private val context: Context, private val custom: CustomSounds) {
    private val mutex = Mutex()

    /** Sound ids the watch needs and cannot have bundled. */
    fun needed(alerts: AlertSettings?): Set<String> {
        if (alerts == null || !alerts.enabled || !alerts.device.includes(AlertDevice.WATCH)) return emptySet()
        val lib = SoundLibrary.get(context)
        return Prayer.entries.filter { alerts.alertsOn(it, AlertDevice.WATCH) }.flatMap { p ->
            val a = alerts.alert(p)
            when {
                // Random from favourites: the watch picks the same one as the phone would, so it needs all of them.
                a.mode == AlertMode.ADHAN && a.soundId == SoundIds.RANDOM ->
                    alerts.favourites.mapNotNull { lib.byId(it) }.filter { it.hasFullRecording }.map { it.id }
                a.mode == AlertMode.ADHAN -> listOfNotNull((lib.byId(a.soundId) ?: lib.byId(if (p == Prayer.FAJR) SoundIds.DEFAULT_FAJR_ADHAN else SoundIds.DEFAULT_ADHAN))
                    ?.takeIf { it.hasFullRecording }?.id)
                a.mode == AlertMode.CUSTOM -> listOfNotNull(a.soundId?.takeIf { custom.get(it) != null })
                else -> emptyList()
            }
        }.toSet()
    }

    /** Sends whatever each Noor watch is missing. Safe to call often: it only transfers what a watch lacks. */
    suspend fun syncIfNeeded(alerts: AlertSettings?) = mutex.withLock {
        val want = needed(alerts)
        if (want.isEmpty()) return@withLock
        runCatching {
            val nodes = Wearable.getCapabilityClient(context)
                .getCapability(com.noor.prayer.WatchConfig.CAPABILITY_WATCH, com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
                .await().nodes
            val reports = reports()
            for (node in nodes) {
                val have = reports[node.id]?.sounds.orEmpty()
                for (id in want - have) send(node.id, id)
            }
        }.onFailure { Log.i(TAG, "Sounds not sent to the watch now: ${it.message}") }
    }

    private suspend fun reports(): Map<String, WatchReport> {
        val items = Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*" + WatchReport.PATH)).await()
        return try {
            items.mapNotNull { item ->
                val host = item.uri.host ?: return@mapNotNull null
                com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap.getString(WatchReport.KEY)
                    ?.let(WatchReport::decode)?.let { host to it }
            }.toMap()
        } finally { items.release() }
    }

    private suspend fun send(nodeId: String, id: String) {
        val (ext, open) = source(id) ?: return
        val channels = Wearable.getChannelClient(context)
        val channel = channels.openChannel(nodeId, "$PATH_PREFIX$id/$ext").await()
        try {
            // Closing the stream marks the end of the file; the watch closes the channel once it has stored it.
            withTimeout(120_000) {
                channels.getOutputStream(channel).await().use { out -> open().use { it.copyTo(out, 64 * 1024) } }
            }
            Log.i(TAG, "Sent $id to $nodeId")
        } catch (e: Exception) {
            runCatching { channels.close(channel).await() }
            throw e
        }
    }

    private fun source(id: String): Pair<String, () -> InputStream>? {
        if (SoundIds.isCustom(id)) {
            val s = custom.get(id) ?: return null
            val f = custom.file(s) ?: return null
            return s.ext to { f.inputStream() }
        }
        val e = SoundLibrary.get(context).byId(id) ?: return null
        val path = e.file ?: return null
        return "ogg" to { context.assets.open(path) }
    }

    companion object {
        /** Channel path: /noor/sound/<id>/<extension>. */
        const val PATH_PREFIX = "/noor/sound/"
        private const val TAG = "NoorWatchSounds"
    }
}
