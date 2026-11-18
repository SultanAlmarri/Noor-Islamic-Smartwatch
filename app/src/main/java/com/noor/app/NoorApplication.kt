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
    val location = LocationRepository(app, settings, gazetteer, world)
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
    @Volatile private var started: AppContainer? = null

    /**
     * The app's parts, made on first use once the phone is unlocked (0.22: before the first unlock after a restart only
     * the prayer alarms run, from their own copy of the times: the app's settings cannot be read then, and reading them
     * would give the defaults, which must never be armed or sent to the watch).
     */
    val container: AppContainer get() = started ?: start()

    override val alertDevice = AlertDevice.PHONE
    override val notificationIcon = R.drawable.ic_notification
    override suspend fun alertConfig(): WatchConfig? = container.alertConfig()
    override fun launchIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    override fun adhanScreen(context: Context): Intent = Intent(context, com.noor.app.adhan.AdhanAlertActivity::class.java)
    override val alarmClockAlarms: Boolean get() = true
    override val ringsAlarms: Boolean get() = true
    override val alertLanguage: String? get() = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()[0]?.language
    override fun onPrayerAlarm(context: Context) {
        // (Before the first unlock nothing but the alarm itself may run: the bar, the widgets and the watch wait.)
        if (!androidx.core.os.UserManagerCompat.isUserUnlocked(this)) return
        com.noor.app.notify.StatusNotification.refreshAsync(context)
        com.noor.app.widget.Widgets.refreshAsync(context)
        // 0.18: once a day, the watch gets the settings again, so the weeks of official times it carries never run out
        // while nothing changes on the phone.
        val today = java.time.LocalDate.now().toEpochDay()
        val prefs = getSharedPreferences("noor_watch_push", MODE_PRIVATE)
        if (prefs.getLong("day", 0L) != today) {
            prefs.edit().putLong("day", today).apply()
            container.scope.launch { runCatching { container.wear.syncNow() } }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // (0.18: the sky's pooled picture buffers; the pictures on the GPU stay.)
        if (level >= TRIM_MEMORY_UI_HIDDEN) com.noor.app.sky.RealSky.trimMemory()
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        if (androidx.core.os.UserManagerCompat.isUserUnlocked(this)) { start(); return }
        // 0.22, Direct Boot: started by a prayer alarm (or the boot) before the first unlock. Everything else waits for it.
        val unlocked = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { runCatching { unregisterReceiver(this) }; start() }
        }
        // (If this cannot be registered, the first use after the unlock starts it: BOOT_COMPLETED re-arms the alarms.)
        runCatching { registerReceiver(unlocked, android.content.IntentFilter(Intent.ACTION_USER_UNLOCKED)) }
    }

    /** Makes the app's parts and starts what follows the settings (once, after unlock). */
    @OptIn(FlowPreview::class)
    private fun start(): AppContainer = synchronized(this) {
        started?.let { return it }
        check(androidx.core.os.UserManagerCompat.isUserUnlocked(this)) { "Minhad's settings cannot be read before the first unlock" }
        val container = AppContainer(this)
        started = container
        container.wear.start(container.scope)
        // (0.18.2) Off the main thread: every alert makes sure of its channels itself before it posts.
        container.scope.launch { AlertNotifications.ensureChannels(this@NoorApplication) }
        // (0.18.2) The small preference files the first screen reads, loaded in the background now, so that the first
        // frame does not wait for the disk (StrictMode found the home sky's quality read there).
        container.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (n in arrayOf("noor_ui", "minhad_permissions")) runCatching { getSharedPreferences(n, MODE_PRIVATE).all }
        }
        // The home sky's tables (a fraction of a second), built in the background before the first screen needs them.
        container.scope.launch(kotlinx.coroutines.Dispatchers.Default) { com.noor.app.ui.today.CloudAtlas.preload(resources); com.noor.app.ui.today.Atmosphere.warmUp() }
        // (Testing only) A tester's demo the app was closed during gives the user's own scenery back.
        container.scope.launch { runCatching { com.noor.app.testing.Tester.restoreAfterDemo(this@NoorApplication) { container.settings.setScenery(it) } } }
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
                    // (0.22.4) While the countdown's service runs, it answers the screen itself.
                    if (com.noor.app.notify.StatusCountdownService.running) return
                    if (i.action == Intent.ACTION_SCREEN_OFF) com.noor.app.notify.StatusTicker.stop()
                    else com.noor.app.notify.StatusNotification.refreshAsync(c)
                }
            }
            registerReceiver(screen, android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        }
        // The persistent notification follows the same things, plus its own switch and the display choices.
        container.scope.launch {
            container.settings.settings
                .map { s -> Triple(Triple(s.statusNotification, s.elapsedAfterPrayer, s.statusBarChip to (s.statusChipIcon to s.statusChipSystemTimer)), WearSync.configFor(s, 0L, container.timetables), Triple(s.language, s.numerals, s.timeFormat)) }
                .distinctUntilChanged()
                .debounce(300)
                .collect { com.noor.app.notify.StatusNotification.refresh(this@NoorApplication) }
        }
        // The home-screen widgets follow the place, the times and the display choices, and the scenery.
        container.scope.launch {
            container.settings.settings
                .map { s -> Triple(s.elapsedAfterPrayer to s.scenery, WearSync.configFor(s, 0L, container.timetables), Triple(s.language, s.numerals, s.timeFormat)) }
                .distinctUntilChanged()
                .debounce(500)
                .collect { com.noor.app.widget.Widgets.refresh(this@NoorApplication) }
        }
        container
    }
}
