package com.noor.wear

import android.app.Application
import android.content.Context
import android.content.Intent
import com.noor.alerts.AlertHost
import com.noor.alerts.AlertNotifications
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertDevice
import com.noor.wear.data.ConfigRepository
import com.noor.wear.ui.MainActivity

class NoorWearApp : Application(), AlertHost {
    val config by lazy { ConfigRepository(this) }

    override val alertDevice = AlertDevice.WATCH
    override val notificationIcon = R.drawable.ic_notification
    override suspend fun alertConfig(): WatchConfig? = config.current()
    override fun launchIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

    override fun onCreate() {
        super.onCreate()
        AlertNotifications.ensureChannels(this)
    }
}
