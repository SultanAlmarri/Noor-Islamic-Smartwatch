package com.noor.app

import android.app.Application
import android.content.Context
import android.content.Intent
import com.noor.alerts.AlertHost
import com.noor.alerts.AlertNotifications
import com.noor.alerts.AlertScheduler
import com.noor.alerts.CustomSounds
import com.noor.shared.geo.GazetteerStore
import com.noor.app.data.LocationRepository
import com.noor.app.data.LocationWorker
import com.noor.shared.geo.OfficialTimetables
import com.noor.app.data.SettingsRepository
import com.noor.app.data.TimetableUpdater
import com.noor.app.data.WatchSetup
import com.noor.app.data.WatchSoundSender
import com.noor.app.data.WearSync
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Simple manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(private val app: Application) {
    val settings = SettingsRepository(app)
    val gazetteer = GazetteerStore(app)
    val location = LocationRepository(app, settings, gazetteer)
    val timetables = OfficialTimetables(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val wear = WearSync(app, settings, timetables)
    val timetableUpdater = TimetableUpdater(app, timetables, onUpdated = { wear.syncNow(); AlertScheduler.reschedule(app) })
    val watchSetup = WatchSetup(app)
    val customSounds = CustomSounds(app)
    val watchSounds = WatchSoundSender(app, customSounds)

    /** Times and alert choices exactly as the screens show them (and as the watch receives them). */
    suspend fun alertConfig(): WatchConfig? = WearSync.configFor(settings.current(), System.currentTimeMillis(), timetables)
}

class NoorApplication : Application(), AlertHost {
    lateinit var container: AppContainer
        private set

    override val alertDevice = AlertDevice.PHONE
    override val notificationIcon = R.drawable.ic_notification
    override suspend fun alertConfig(): WatchConfig? = container.alertConfig()
    override fun launchIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.wear.start(container.scope)
        AlertNotifications.ensureChannels(this)
        container.scope.launch {
            val s = container.settings.current()
            LocationWorker.sync(this@NoorApplication, s.backgroundUpdates && container.location.hasBackgroundPermission())
        }
        // Re-arm alerts whenever anything that changes the times or the choices changes (place, authority, offsets,
        // alert settings). Debounced so dragging a slider does not re-arm on every step.
        container.scope.launch {
            container.settings.settings
                .map { WearSync.configFor(it, 0L, container.timetables) }
                .distinctUntilChanged()
                .debounce(400)
                .collect {
                    AlertScheduler.reschedule(this@NoorApplication)
                    container.watchSounds.syncIfNeeded(it?.alerts)
                }
        }
    }
}
