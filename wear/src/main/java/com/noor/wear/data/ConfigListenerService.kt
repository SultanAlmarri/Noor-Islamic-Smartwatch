package com.noor.wear.data

import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.noor.prayer.WatchConfig
import com.noor.wear.NoorWearApp
import kotlinx.coroutines.runBlocking

/** Woken by the system when the phone publishes a new configuration. */
class ConfigListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val repo = (application as NoorWearApp).config
        // Freeze the events before the buffer is released; the work is tiny (a few hundred bytes to DataStore).
        val payloads = events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchConfig.PATH }
            .mapNotNull { DataMapItem.fromDataItem(it.dataItem).dataMap.getString(ConfigRepository.KEY_CONFIG) }
        if (payloads.isEmpty()) return
        runBlocking { repo.save(payloads.last()) }
    }

    /** The phone sends a full Adhan or an imported sound for a prayer that alerts on the watch. */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (!channel.path.startsWith(ReceivedSounds.PATH_PREFIX)) return
        if (ReceivedSounds.receive(this, channel)) runBlocking { StatusPublisher.publish(this@ConfigListenerService) }
    }
}
