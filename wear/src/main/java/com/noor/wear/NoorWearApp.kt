package com.noor.wear

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.noor.alerts.AlertHost
import com.noor.alerts.AlertNotifications
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertDevice
import com.noor.shared.geo.GazetteerStore
import com.noor.shared.geo.OfficialTimetables
import com.noor.wear.data.ConfigRepository
import com.noor.wear.data.PhoneLink
import com.noor.wear.data.TasbihRepository
import com.noor.wear.data.WatchLocation
import com.noor.wear.ui.MainActivity

class NoorWearApp : Application(), AlertHost {
    val timetables by lazy { OfficialTimetables(this) }
    val gazetteer by lazy { GazetteerStore(this) }
    val config by lazy { ConfigRepository(this, timetables) }
    val tasbih by lazy { TasbihRepository(this) }
    val location by lazy { WatchLocation(this, gazetteer) }
    val phone by lazy { PhoneLink(this) }

    override val alertDevice = AlertDevice.WATCH
    override val notificationIcon = R.drawable.ic_notification
    override suspend fun alertConfig(): WatchConfig? = config.current()
    override fun launchIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

    /** While the Adhan plays, an Ongoing Activity puts Noor's icon on the watch face and in Recents, one tap from Stop. */
    override fun decoratePlaying(context: Context, notificationId: Int, builder: NotificationCompat.Builder, title: String) {
        val touch = PendingIntent.getActivity(context, 0, launchIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        OngoingActivity.Builder(context, notificationId, builder)
            .setStaticIcon(R.drawable.ic_notification)
            .setTouchIntent(touch)
            .setStatus(Status.Builder().addTemplate(title).build())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
            .apply(context)
    }

    override fun onCreate() {
        super.onCreate()
        AlertNotifications.ensureChannels(this)
    }
}
