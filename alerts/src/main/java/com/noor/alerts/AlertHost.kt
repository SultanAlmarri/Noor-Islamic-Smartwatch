package com.noor.alerts

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertDevice

/**
 * Implemented by each app's Application class. The alert components (alarm receiver, Adhan service) are started by
 * the system without any UI, so they ask the app for the current times and choices through this interface.
 */
interface AlertHost {
    /** Which device this app runs on. */
    val alertDevice: AlertDevice

    /** Small monochrome icon for notifications. */
    val notificationIcon: Int

    /**
     * The current place, times and alert choices (in [WatchConfig.alerts]), or null when there is no location yet.
     * Called off the main thread.
     */
    suspend fun alertConfig(): WatchConfig?

    /** Opens the app; used when the notification is tapped. */
    fun launchIntent(context: Context): Intent

    /**
     * Last chance to add to the notification shown while a sound plays, before it is posted. The watch uses this to
     * make it an Ongoing Activity (an icon on the watch face and in Recents that leads back to the Stop button).
     */
    fun decoratePlaying(context: Context, notificationId: Int, builder: NotificationCompat.Builder, title: String) {}
}

internal val Context.alertHost: AlertHost? get() = applicationContext as? AlertHost
