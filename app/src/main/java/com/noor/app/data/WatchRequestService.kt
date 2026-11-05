package com.noor.app.data

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.noor.app.NoorApplication
import com.noor.prayer.WatchConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Woken by the system when the watch asks for the phone's settings ("Sync with phone" on the watch). Publishes them
 * again; the Data Layer delivers them to the watch. Nothing else is accepted from the watch.
 */
class WatchRequestService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchConfig.PATH_SYNC_REQUEST) return
        val app = application as? NoorApplication ?: return
        runBlocking { withTimeoutOrNull(10_000) { app.container.wear.syncNow() } }
    }
}
