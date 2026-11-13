package com.noor.app.ui.adhan

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.alerts.AlertLog
import com.noor.alerts.AlertNotifications
import com.noor.alerts.AlertRecord
import com.noor.alerts.AlertPlayer
import com.noor.alerts.AlertScheduler
import com.noor.alerts.CustomSound
import com.noor.alerts.ImportResult
import com.noor.alerts.SoundLibrary
import com.noor.alerts.SoundSource
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.WatchNode
import com.noor.app.data.WearSync
import com.noor.prayer.Prayer
import com.noor.prayer.WatchReport
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertPlanner
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PlannedAlert
import com.noor.prayer.alerts.PrayerAlert
import com.noor.prayer.alerts.SoundIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** What stands between the user's choices and an alert that actually sounds, on this phone. */
data class Readiness(
    val notificationsAllowed: Boolean = true,
    /** Android 13+: the permission can be requested from the app. */
    val canRequestNotifications: Boolean = false,
    val adhanChannelBlocked: Boolean = false,
    val exactAlarms: Boolean = true,
    val alarmVolume: Float = 1f,
    val batteryRestricted: Boolean = false,
)

/** The watch's side, from its last report. */
data class WatchAlertState(val node: WatchNode, val report: WatchReport?)

/** The next alert this phone will give. */
data class NextAlert(val planned: PlannedAlert, val zone: ZoneId)

data class PreviewKey(val id: String, val short: Boolean)

class AdhanViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    private val repo = container.settings
    val library: SoundLibrary = SoundLibrary.get(app)

    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Computed from the same plan the scheduler arms, so what is shown is what will happen. */
    val next: StateFlow<NextAlert?> = repo.settings.map { s ->
        val c = WearSync.configFor(s, System.currentTimeMillis(), container.timetables) ?: return@map null
        val a = c.alerts ?: return@map null
        AlertPlanner.plan(c, a, AlertDevice.PHONE, Instant.now()).firstOrNull()?.let { NextAlert(it, c.zone) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasLocation: StateFlow<Boolean> = repo.settings.map { WearSync.configFor(it, 0L, container.timetables) != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _custom = MutableStateFlow(container.customSounds.list())
    val custom: StateFlow<List<CustomSound>> = _custom.asStateFlow()

    private val _readiness = MutableStateFlow(Readiness())
    val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    private val _watches = MutableStateFlow<List<WatchAlertState>>(emptyList())
    val watches: StateFlow<List<WatchAlertState>> = _watches.asStateFlow()

    /** What happened at the last alerts on this phone, newest first. */
    private val _history = MutableStateFlow<List<AlertRecord>>(emptyList())
    val history: StateFlow<List<AlertRecord>> = _history.asStateFlow()

    private val _preview = MutableStateFlow<PreviewKey?>(null)
    val preview: StateFlow<PreviewKey?> = _preview.asStateFlow()

    /** One-off messages (import results, "test in 10 seconds"). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val player = AlertPlayer(app)

    init {
        refresh()
        viewModelScope.launch { container.watchSounds.syncIfNeeded(repo.current().alerts) }
    }

    /** Re-reads permissions and device state (called when the screen is shown again). */
    fun refresh() {
        val ctx = getApplication<Application>()
        AlertNotifications.ensureChannels(ctx)
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val nm = ctx.getSystemService(NotificationManager::class.java)
        _readiness.value = Readiness(
            notificationsAllowed = granted && NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            canRequestNotifications = Build.VERSION.SDK_INT >= 33 && !granted,
            adhanChannelBlocked = nm?.getNotificationChannel(AlertNotifications.CHANNEL_ADHAN)?.importance == NotificationManager.IMPORTANCE_NONE,
            exactAlarms = AlertScheduler.canScheduleExact(ctx),
            alarmVolume = AlertPlayer.alarmVolume(ctx),
            batteryRestricted = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == false,
        )
        viewModelScope.launch {
            _history.value = withContext(Dispatchers.IO) { AlertLog.all(ctx) }
            AlertScheduler.reschedule(ctx)
            val snap = container.watchSetup.snapshot()
            _watches.value = snap.connected.filter { it.id in snap.withNoor }.map { WatchAlertState(it, snap.reports[it.id]) }
        }
    }

    private fun update(change: (AlertSettings) -> AlertSettings) = viewModelScope.launch { repo.updateAlerts(change) }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    fun setDevice(d: AlertDevice) = update { it.copy(device = d) }
    fun setGradual(seconds: Int) = update { it.copy(gradualSeconds = seconds) }
    fun setReminder(minutes: Int) = update { it.copy(reminderMinutes = minutes.takeIf { m -> m in AlertSettings.REMINDER_CHOICES } ?: 0) }
    /** The switch reads "Play in silent mode and Do Not Disturb": on = play like an alarm (the default). */
    fun setPlayInSilentMode(on: Boolean) = update { it.copy(quietWhenSilent = !on) }
    fun setPrayer(p: Prayer, change: (PrayerAlert) -> PrayerAlert) = update { s -> s.with(p, change(s.alert(p))) }

    /** Adds or removes a library Adhan from the favourites ("Random from favourites" rotates through them). */
    fun toggleFavourite(id: String) = update { s ->
        val favs = if (id in s.favourites) s.favourites - id else s.favourites + id
        // A prayer set to "Random" keeps working when favourites run out: it falls back to the default Adhan by itself.
        s.copy(favourites = favs)
    }

    /** Picking a mode keeps a sensible sound for it (a Fajr Adhan for Fajr, the chime for sounds). */
    fun setMode(p: Prayer, mode: AlertMode) = setPrayer(p) { a ->
        val keep = when (mode) {
            AlertMode.ADHAN -> a.soundId?.let { library.byId(it) }?.takeIf { it.hasFullRecording && it in library.adhansFor(p) }?.id
                ?: if (p == Prayer.FAJR) SoundIds.DEFAULT_FAJR_ADHAN else SoundIds.DEFAULT_ADHAN
            AlertMode.ADHAN_SHORT -> a.soundId?.let { library.byId(it) }?.takeIf { it in library.adhansFor(p) }?.id
                ?: if (p == Prayer.FAJR) SoundIds.DEFAULT_FAJR_ADHAN else SoundIds.DEFAULT_ADHAN
            AlertMode.SOUND -> a.soundId?.takeIf { it.startsWith("alt.") } ?: SoundIds.DEFAULT_SOUND
            AlertMode.CUSTOM -> a.soundId?.takeIf(SoundIds::isCustom) ?: _custom.value.firstOrNull()?.id
            AlertMode.NOTIFICATION, AlertMode.SILENT -> a.soundId
        }
        a.copy(enabled = true, mode = mode, soundId = keep)
    }

    fun previewLibrary(id: String, short: Boolean, volumePercent: Int = 80) {
        val e = library.byId(id) ?: return
        val path = (if (short) e.shortFile else e.file) ?: return
        play(PreviewKey(id, short), SoundSource.Asset(path), volumePercent)
    }

    fun previewCustom(id: String, volumePercent: Int = 80) {
        val s = container.customSounds.get(id) ?: return
        val f = container.customSounds.file(s) ?: return
        play(PreviewKey(id, false), SoundSource.LocalFile(f), volumePercent)
    }

    private fun play(key: PreviewKey, source: SoundSource, volumePercent: Int) {
        if (_preview.value == key) { stopPreview(); return }
        _preview.value = key
        player.play(source, volumePercent, gradualSeconds = 0, maxSeconds = 0) { _preview.value = null }
    }

    fun stopPreview() { player.stop(); _preview.value = null }

    fun importSound(uri: Uri, replaceId: String? = null, forPrayer: Prayer? = null) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val result = withContext(Dispatchers.IO) { container.customSounds.import(ctx.contentResolver, uri, replaceId) }
        _custom.value = container.customSounds.list()
        _message.value = when (result) {
            is ImportResult.Ok -> {
                if (forPrayer != null) setPrayer(forPrayer) { it.copy(enabled = true, mode = AlertMode.CUSTOM, soundId = result.sound.id) }
                // A replaced file has to reach the watch again.
                if (replaceId != null) container.watchSounds.syncIfNeeded(repo.current().alerts)
                ctx.getString(com.noor.app.R.string.adhan_import_ok, result.sound.name)
            }
            is ImportResult.Rejected -> ctx.getString(when (result.reason) {
                ImportResult.Reason.UNSUPPORTED_TYPE -> com.noor.app.R.string.adhan_import_unsupported
                ImportResult.Reason.TOO_LARGE -> com.noor.app.R.string.adhan_import_too_large
                ImportResult.Reason.NOT_AUDIO -> com.noor.app.R.string.adhan_import_not_audio
                ImportResult.Reason.UNREADABLE -> com.noor.app.R.string.adhan_import_unreadable
            })
        }
    }

    fun renameSound(id: String, name: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { container.customSounds.rename(id, name) }
        _custom.value = container.customSounds.list()
    }

    /** Prayers that use [id]; they move to the default Adhan when it is deleted. */
    fun usersOf(id: String): List<Prayer> = settings.value?.alerts?.let { a ->
        Prayer.entries.filter { a.alert(it).mode == AlertMode.CUSTOM && a.alert(it).soundId == id }
    }.orEmpty()

    fun deleteSound(id: String) = viewModelScope.launch {
        if (_preview.value?.id == id) stopPreview()
        val users = usersOf(id)
        repo.updateAlerts { s ->
            users.fold(s) { acc, p ->
                acc.with(p, acc.alert(p).copy(mode = if (p == Prayer.SHURUQ) AlertMode.SOUND else AlertMode.ADHAN,
                    soundId = when (p) { Prayer.SHURUQ -> SoundIds.DEFAULT_SOUND; Prayer.FAJR -> SoundIds.DEFAULT_FAJR_ADHAN; else -> SoundIds.DEFAULT_ADHAN }))
            }
        }
        withContext(Dispatchers.IO) { container.customSounds.delete(id) }
        _custom.value = container.customSounds.list()
    }

    fun test(p: Prayer) {
        val ctx = getApplication<Application>()
        stopPreview()
        _message.value = ctx.getString(
            if (AlertScheduler.scheduleTest(ctx, p, 10)) com.noor.app.R.string.adhan_test_sent else com.noor.app.R.string.adhan_test_failed)
    }

    /**
     * A real alert through the whole path in ten seconds, for the next prayer that alerts (its own sound and choices),
     * so the user can lock the phone and hear exactly what will happen at prayer time.
     */
    fun testNext() {
        val ctx = getApplication<Application>()
        val p = next.value?.planned?.prayer
            ?: settings.value?.alerts?.let { a -> Prayer.entries.firstOrNull { a.alert(it).enabled && a.alert(it).mode.playsAudio } }
            ?: Prayer.DHUHR
        test(p)
        viewModelScope.launch { kotlinx.coroutines.delay(25_000); refresh() }
    }

    /** A plain-text report of this phone's alert set-up and history, to share when an alert did not sound. */
    fun report(): String {
        val ctx = getApplication<Application>()
        val r = _readiness.value
        val a = settings.value?.alerts
        val am = ctx.getSystemService(android.media.AudioManager::class.java)
        val zone = ZoneId.systemDefault()
        fun t(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().withNano(0).toString()
        return buildString {
            appendLine("Noor ${com.noor.app.BuildConfigInfo.versionName} alert report, ${t(System.currentTimeMillis())} ($zone)")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Notifications allowed: ${r.notificationsAllowed}; Adhan category blocked: ${r.adhanChannelBlocked}")
            appendLine("Exact alarms: ${r.exactAlarms}; alarm volume: ${Math.round(r.alarmVolume * 100)}%; ringer mode: ${am?.ringerMode}")
            appendLine("Battery optimisation exempt: ${!r.batteryRestricted}; background restricted: ${
                runCatching { if (Build.VERSION.SDK_INT >= 28) ctx.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted else false }.getOrNull()}")
            appendLine("Alerts: ${a?.encode()}")
            appendLine("Next: ${next.value?.let { "${it.planned.prayer} ${it.planned.at}" } ?: "none"}")
            appendLine()
            val list = AlertLog.all(ctx)
            if (list.isEmpty()) appendLine("No alerts recorded yet.")
            list.forEach { e ->
                appendLine("${t(e.dueAtMs)} ${e.prayer ?: "?"}${if (e.test) " TEST" else ""}: ${e.outcome}, played ${e.playedMs / 1000} s" +
                    "${if (e.stoppedByUser) " (stopped by user)" else ""}, ${(e.delayMs / 1000)} s after due, sound ${e.sound}," +
                    " exact ${e.exactAlarms}, volume ${e.alarmVolumePercent}%, ringer ${e.ringerMode}${e.detail?.let { ", $it" } ?: ""}")
            }
        }
    }

    fun openNoorOnWatch(nodeId: String) = viewModelScope.launch { container.watchSetup.openNoorOnWatch(nodeId) }

    fun consumeMessage() { _message.value = null }

    override fun onCleared() { player.stop(notify = false) }
}
