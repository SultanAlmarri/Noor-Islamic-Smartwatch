package com.noor.wear.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.noor.prayer.WatchConfig
import androidx.concurrent.futures.await
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** The watch's side of the conversation with the Noor phone app. Everything here is optional: the watch works without it. */
class PhoneLink(private val context: Context) {

    enum class Phone {
        /** A phone with Noor is connected now. */
        CONNECTED,
        /** Paired, but no phone with Noor is reachable right now (out of range, or Noor not installed on it). */
        NOT_REACHABLE,
        /** Wear OS's phone connection is not available on this watch at all. */
        UNKNOWN,
    }

    suspend fun phone(): Phone = withTimeoutOrNull(5_000) {
        runCatching {
            val nodes = Wearable.getCapabilityClient(context).getCapability(WatchConfig.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE).await().nodes
            if (nodes.isNotEmpty()) Phone.CONNECTED else Phone.NOT_REACHABLE
        }.getOrElse { Phone.UNKNOWN }
    } ?: Phone.UNKNOWN

    /** Asks the phone to send its settings again. Returns false when no phone with Noor could be reached. */
    suspend fun requestSync(): Boolean = withTimeoutOrNull(8_000) {
        runCatching {
            val nodes = Wearable.getCapabilityClient(context).getCapability(WatchConfig.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE).await().nodes
            if (nodes.isEmpty()) return@runCatching false
            val messages = Wearable.getMessageClient(context)
            nodes.map { runCatching { messages.sendMessage(it.id, WatchConfig.PATH_SYNC_REQUEST, ByteArray(0)).await(); true }.getOrDefault(false) }.any { it }
        }.onFailure { Log.w(TAG, "sync request failed", it) }.getOrDefault(false)
    } ?: false

    /** Opens the page where the Noor phone app can be downloaded, on the paired phone's browser. */
    suspend fun openDownloadOnPhone(): Boolean = withTimeoutOrNull(10_000) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_PAGE)).addCategory(Intent.CATEGORY_BROWSABLE)
            RemoteActivityHelper(context).startRemoteActivity(intent, null).await()
            true
        }.onFailure { Log.w(TAG, "could not open on phone", it) }.getOrDefault(false)
    } ?: false

    companion object {
        private const val TAG = "NoorPhoneLink"
        const val DOWNLOAD_PAGE = "https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch/releases/tag/dev-latest"
    }
}
