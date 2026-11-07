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

    /**
     * The language the user chose inside the app ("ar", "en"), or null to follow the device. Notification category
     * names shown in Android's settings use it, so they match the app's language.
     */
    val alertLanguage: String? get() = null

    /**
     * Called after a prayer alarm has been handled (the alarm wakes the device at the prayer time). The phone uses it to
     * bring its persistent notification up to date at that same moment.
     */
    fun onPrayerAlarm(context: Context) {}

    /** Opens the app; used when the notification is tapped. */
    fun launchIntent(context: Context): Intent

    /**
     * A full-screen Adhan screen to show while the Adhan plays with the phone locked (like an alarm clock's), or null
     * for none (the watch). Being visible also lets the sound play under Android 17's background-audio rules.
     */
    fun adhanScreen(context: Context): Intent? = null

    /** Arm prayer alarms as alarm-clock alarms (the phone, for Android 17's background audio rules); false keeps exact alarms. */
    val alarmClockAlarms: Boolean get() = false

    /**
     * Last chance to add to the notification shown while a sound plays, before it is posted. The watch uses this to
     * make it an Ongoing Activity (an icon on the watch face and in Recents that leads back to the Stop button).
     */
    fun decoratePlaying(context: Context, notificationId: Int, builder: NotificationCompat.Builder, title: String) {}
}

internal val Context.alertHost: AlertHost? get() = applicationContext as? AlertHost
