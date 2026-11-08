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
    /** 0.9.7: the offline world places and time-zone map, and online place search, for choosing a location by hand. */
    val world = com.noor.app.data.WorldStore(app)
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
    override fun adhanScreen(context: Context): Intent = Intent(context, com.noor.app.adhan.AdhanAlertActivity::class.java)
    override val alarmClockAlarms: Boolean get() = true
    override val alertLanguage: String? get() = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()[0]?.language
    override fun onPrayerAlarm(context: Context) = com.noor.app.notify.StatusNotification.refreshAsync(context)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.wear.start(container.scope)
        AlertNotifications.ensureChannels(this)
        // The home sky's tables (a fraction of a second), built in the background before the first screen needs them.
        container.scope.launch(kotlinx.coroutines.Dispatchers.Default) { com.noor.app.ui.today.CloudAtlas.preload(resources); com.noor.app.ui.today.Atmosphere.warmUp() }
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
        // When Noor draws the notification's countdown itself (its digits differ from Android's), it ticks only while the
        // screen is on: rebuilt when the screen comes on, stopped when it goes off.
        runCatching {
            val screen = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.action == Intent.ACTION_SCREEN_OFF) com.noor.app.notify.StatusTicker.stop()
                    else com.noor.app.notify.StatusNotification.refreshAsync(c)
                }
            }
            registerReceiver(screen, android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        }
        // The persistent notification follows the same things, plus its own switch and the display choices.
        container.scope.launch {
            container.settings.settings
                .map { s -> Triple(s.statusNotification to s.elapsedAfterPrayer, WearSync.configFor(s, 0L, container.timetables), Triple(s.language, s.numerals, s.timeFormat)) }
                .distinctUntilChanged()
                .debounce(300)
                .collect { com.noor.app.notify.StatusNotification.refresh(this@NoorApplication) }
        }
    }
}
