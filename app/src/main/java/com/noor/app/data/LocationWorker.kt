package com.noor.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.noor.app.NoorApplication
import java.util.concurrent.TimeUnit

/**
 * Low-frequency location refresh. Runs about every 6 hours (Android may batch it further),
 * takes one balanced-power fix and stops. No continuous tracking, no foreground service.
 */
class LocationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NoorApplication
        val s = app.container.settings.current()
        if (s.locationMode != LocationMode.AUTO) return Result.success()
        val periodic = tags.contains(TAG_PERIODIC)
        if (periodic && (!s.backgroundUpdates || !app.container.location.hasBackgroundPermission())) return Result.success()
        app.container.location.refresh()
        return Result.success()
    }

    companion object {
        private const val TAG_PERIODIC = "noor-location-periodic"
        private const val PERIODIC = "noor-location-refresh"
        private const val ONCE = "noor-location-once"

        /** Schedules or cancels the periodic refresh to match the user's choice. */
        fun sync(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(PERIODIC); return }
            val req = PeriodicWorkRequestBuilder<LocationWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .addTag(TAG_PERIODIC)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** One refresh soon, e.g. after a time-zone change. */
        fun once(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<LocationWorker>().build())
        }
    }
}
