package com.noor.wear.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.noor.prayer.WatchReport
import com.noor.wear.NoorWearApp
import com.noor.wear.facepush.FacePush
import com.noor.wear.facepush.FaceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Tells the phone what is on this watch (app version, Wear OS level, Noor face), through the Data Layer. */
object StatusPublisher {
    suspend fun publish(context: Context) {
        // Never hold up the caller (alarms, set-up) on the phone connection: give up after a few seconds.
        withTimeoutOrNull(8_000) { send(context) }
    }

    private suspend fun send(context: Context) {
        runCatching {
            val app = context.applicationContext as NoorWearApp
            val face = FacePush(context).state()
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val report = WatchReport(
                appVersionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
                appVersionName = info.versionName ?: "",
                sdkInt = Build.VERSION.SDK_INT,
                facePushSupported = face !is FaceState.Unsupported,
                faceBundledVersion = (face as? FaceState.Ready)?.bundledBuild ?: 0L,
                faceInstalledVersion = (face as? FaceState.Ready)?.installedBuild,
                faceActive = (face as? FaceState.Ready)?.active == true,
                configReceivedAtMs = app.config.receivedAt.first(),
                updatedAtMs = System.currentTimeMillis(),
                notificationsAllowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarms = com.noor.alerts.AlertScheduler.canScheduleExact(context),
                alertsArmed = app.config.current()?.let { c ->
                    c.alerts?.let { com.noor.prayer.alerts.AlertPlanner.plan(c, it, com.noor.prayer.alerts.AlertDevice.WATCH, java.time.Instant.now()).size }
                },
                sounds = ReceivedSounds.ids(context),
                usingWatchSettings = app.config.state.first().source == ConfigSource.WATCH,
                faceVariant = (face as? FaceState.Ready)?.installedVariant,
                faceChosen = (face as? FaceState.Ready)?.chosen,
                faceNeedsPick = (face as? FaceState.Ready)?.needsPick,
            )
            val req = PutDataMapRequest.create(WatchReport.PATH).apply { dataMap.putString(WatchReport.KEY, report.encode()) }
                .asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req).await()
        }.onFailure { Log.w("NoorStatus", "could not publish status", it) }
    }
}
