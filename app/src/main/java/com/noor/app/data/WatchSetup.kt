package com.noor.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.concurrent.futures.await
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.noor.prayer.WatchConfig
import com.noor.prayer.WatchReport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** A watch paired with this phone. */
data class WatchNode(val id: String, val name: String)

/** Everything the phone knows about the watch side, refreshed live while the Watch tab is open. */
data class WatchSetupState(
    /** Wear OS services (Galaxy Wearable / Wear OS app) are missing on this phone. */
    val wearUnavailable: Boolean,
    val connected: List<WatchNode>,
    /** Node ids of watches with the Noor watch app installed (connected or not). */
    val withNoor: Set<String>,
    /** The latest report each watch published (node id → report). */
    val reports: Map<String, WatchReport>,
)

/**
 * Installation and setup of the watch side, within what Android allows: a phone app cannot install apps on a watch.
 * It can (1) see which watches have Noor, (2) open Noor's Play Store page on a watch so the user taps Install there,
 * and (3) open a screen of the Noor watch app (to make the Noor face active), all through Google's Wear OS APIs.
 */
class WatchSetup(private val context: Context) {
    private val remote by lazy { RemoteActivityHelper(context) }

    /** True when this phone app was installed by Google Play; false for development builds installed by hand. */
    val installedFromPlay: Boolean by lazy {
        runCatching {
            val installer = if (Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(context.packageName)
            installer == "com.android.vending"
        }.getOrDefault(false)
    }

    /** Live state: re-read on capability or report changes, and every 15 s for connection changes. */
    fun states(): Flow<WatchSetupState> = channelFlow {
        val trigger = Channel<Unit>(Channel.CONFLATED)
        val capability = Wearable.getCapabilityClient(context)
        val data = Wearable.getDataClient(context)
        val capListener = CapabilityClient.OnCapabilityChangedListener { trigger.trySend(Unit) }
        val dataListener = DataClient.OnDataChangedListener { events ->
            if (events.any { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchReport.PATH }) trigger.trySend(Unit)
        }
        runCatching { capability.addListener(capListener, WatchConfig.CAPABILITY_WATCH); data.addListener(dataListener) }
        launch { while (true) { trigger.send(Unit); delay(15_000) } }
        launch { for (t in trigger) send(snapshot()) }
        awaitClose {
            runCatching { capability.removeListener(capListener); data.removeListener(dataListener) }
        }
    }

    suspend fun snapshot(): WatchSetupState = runCatching {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await().map { WatchNode(it.id, it.displayName) }
        val noor = Wearable.getCapabilityClient(context)
            .getCapability(WatchConfig.CAPABILITY_WATCH, CapabilityClient.FILTER_ALL).await().nodes.map { it.id }.toSet()
        val items = Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*" + WatchReport.PATH)).await()
        val reports = try {
            items.mapNotNull { item ->
                val host = item.uri.host ?: return@mapNotNull null
                DataMapItem.fromDataItem(item).dataMap.getString(WatchReport.KEY)?.let(WatchReport::decode)?.let { host to it }
            }.toMap()
        } finally { items.release() }
        WatchSetupState(false, nodes, noor, reports)
    }.getOrElse {
        Log.w(TAG, "Wear OS services unavailable", it)
        WatchSetupState(true, emptyList(), emptySet(), emptyMap())
    }

    /** Opens Noor's Play Store page on the watch; the user confirms the install there. */
    suspend fun openPlayStoreOnWatch(nodeId: String): Boolean = startOnWatch("market://details?id=${context.packageName}", nodeId)

    /** Opens the Noor watch app on its watch-face setup screen. */
    suspend fun openFaceSetupOnWatch(nodeId: String): Boolean = startOnWatch(WatchReport.LINK_ACTIVATE_FACE, nodeId)

    suspend fun openNoorOnWatch(nodeId: String): Boolean = startOnWatch(WatchReport.LINK_OPEN, nodeId)

    private suspend fun startOnWatch(uri: String, nodeId: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(Uri.parse(uri))
        // Completes once the watch has received the request; a watch out of reach never answers.
        withTimeoutOrNull(20_000) { remote.startRemoteActivity(intent, nodeId).await(); true } ?: false
    }.onFailure { Log.w(TAG, "Could not open $uri on $nodeId", it) }.getOrDefault(false)

    companion object { private const val TAG = "NoorWatchSetup" }
}
