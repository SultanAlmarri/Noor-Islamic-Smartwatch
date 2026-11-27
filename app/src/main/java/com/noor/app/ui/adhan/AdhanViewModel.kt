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
import com.noor.app.data.DailyConfig
import com.noor.app.data.PrayerProvider
import com.noor.prayer.Prayer
import com.noor.prayer.WatchReport
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlarmPlanner
import com.noor.prayer.alerts.AlertPlanner
import com.noor.prayer.alerts.PlannedAlarm
import com.noor.prayer.alerts.PrayerAlarm
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PlannedAlert
import com.noor.prayer.alerts.PrayerAlert
import com.noor.prayer.alerts.SoundIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
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
    /** 0.18: Android 14+ lets the Adhan's screen show over the lock screen (it keeps the sound playing on Android 17). */
    val fullScreenAllowed: Boolean = true,
)

/** The watch's side, from its last report. */
data class WatchAlertState(val node: WatchNode, val report: WatchReport?)

/** 0.17: each alarm's next ring (by id), today's prayer times, and the zone they are told in. */
data class Rings(val next: Map<Int, PlannedAlarm> = emptyMap(), val today: Map<Prayer, Instant> = emptyMap(), val zone: ZoneId = ZoneId.systemDefault())

/** The next alert this phone will give. */
data class NextAlert(val planned: PlannedAlert, val zone: ZoneId)

data class PreviewKey(val id: String, val short: Boolean)

class AdhanViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    private val repo = container.settings
    val library: SoundLibrary = SoundLibrary.get(app)

    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Counts the times the screen has come back, so what depends on the clock is worked out again. */
    private val shownAgain = MutableStateFlow(0)

    /**
     * Computed from the same plan the scheduler arms, so what is shown is what will happen. Worked out again when the
     * settings change, when the screen comes back, and once the alert it names has come (0.14: it used to stay on a
     * prayer that had passed for as long as the page lived, which with the tabs' saved state can be hours).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val next: StateFlow<NextAlert?> = combine(repo.settings, shownAgain) { s, _ -> s }.transformLatest { s ->
        while (true) {
            val n = run {
                val c = DailyConfig.get(s, container.timetables) ?: return@run null
                val a = c.alerts ?: return@run null
                AlertPlanner.plan(c, a, AlertDevice.PHONE, Instant.now()).firstOrNull()?.let { NextAlert(it, c.zone) }
            }
            emit(n)
            val at = n?.planned?.at ?: break
            kotlinx.coroutines.delay((java.time.Duration.between(Instant.now(), at).toMillis() + 1_000).coerceAtLeast(1_000))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 0.17: when each alarm rings next (by id), from the same planner the scheduler arms, and today's prayer times for
     * the prayers' rows; worked out again when the settings change, the screen comes back, or an alarm has rung.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val rings: StateFlow<Rings> = combine(repo.settings, shownAgain) { s, _ -> s }.transformLatest { s ->
        while (true) {
            val c = DailyConfig.get(s, container.timetables)
            val now = Instant.now()
            val r = if (c == null) Rings() else Rings(
                (c.alerts?.alarms.orEmpty()).mapNotNull { AlarmPlanner.next(c, it, now) }.associateBy { it.alarm.id },
                c.day(java.time.LocalDate.now(c.zone)).let { d -> Prayer.entries.mapNotNull { p -> d.instant(p)?.let { p to it } }.toMap() },
                c.zone)
            emit(r)
            val soonest = (r.next.values.map { it.at } + listOfNotNull(java.time.LocalDate.now(r.zone).plusDays(1).atStartOfDay(r.zone).toInstant())).minOrNull() ?: break
            kotlinx.coroutines.delay((java.time.Duration.between(Instant.now(), soonest).toMillis() + 1_000).coerceIn(1_000, 3_600_000))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, Rings())

    /**
     * 0.17: when [alarm] would ring next if saved as it is (the editor's line "Tomorrow at 4:27 AM"). Off the main
     * thread: the configuration is worked out once for the settings and the day, then only the alarm is planned.
     */
    suspend fun nextRing(alarm: PrayerAlarm): PlannedAlarm? = withContext(Dispatchers.Default) {
        val s = settings.value ?: return@withContext null
        val c = DailyConfig.get(s, container.timetables) ?: return@withContext null
        AlarmPlanner.next(c, alarm.normalized(), Instant.now())
    }

    /**
     * The same at once, when the configuration is already worked out (the editor's first frame, so it opens with its
     * answer); null when it is not yet, and [nextRing] gives it a moment later.
     */
    fun nextRingIfReady(alarm: PrayerAlarm): PlannedAlarm? {
        val s = settings.value ?: return null
        val c = DailyConfig.peek(s, container.timetables) ?: return null
        return AlarmPlanner.next(c, alarm.normalized(), Instant.now())
    }

    /**
     * Saves [alarm] (new with id 0); a snooze of an alarm that was changed is cancelled (it would ring as it was). In
     * the app's scope, not the screen's: the editor closes at once.
     */
    fun saveAlarm(alarm: PrayerAlarm) = container.scope.launch {
        repo.updateAlerts { it.withAlarm(alarm) }
        if (alarm.id != 0) AlertScheduler.cancelSnooze(getApplication(), alarm.id)
    }

    fun setAlarmOn(id: Int, on: Boolean) = viewModelScope.launch {
        repo.updateAlerts { s -> s.alarms.firstOrNull { it.id == id }?.let { s.withAlarm(it.copy(enabled = on)) } ?: s }
        if (!on) AlertScheduler.cancelSnooze(getApplication(), id)
    }

    fun deleteAlarm(id: Int) = container.scope.launch {
        repo.updateAlerts { it.withoutAlarm(id) }
        AlertScheduler.cancelSnooze(getApplication(), id)
    }

    /** [alarm] rings in five seconds, as it will (saved or not). */
    fun tryAlarm(alarm: PrayerAlarm) {
        val ctx = getApplication<Application>()
        stopPreview()
        _message.value = if (AlertScheduler.tryAlarm(ctx, alarm, 5)) ctx.getString(com.noor.app.R.string.alarm_try_sent, digits(5))
            else ctx.getString(com.noor.app.R.string.adhan_test_failed)
    }

    /** A recording added for an alarm: [onAdded] gets its id. */
    fun importForAlarm(uri: Uri, onAdded: (String) -> Unit) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val result = withContext(Dispatchers.IO) { container.customSounds.import(ctx.contentResolver, uri, null) }
        reloadCustom()
        if (result is ImportResult.Ok) { onAdded(result.sound.id); _message.value = ctx.getString(com.noor.app.R.string.adhan_import_ok, result.sound.name) }
        else importSoundResult(result)
    }

    // (configFor is null exactly when the settings give no place: the same answer without eight weeks of times.)
    val hasLocation: StateFlow<Boolean> = repo.settings.map { PrayerProvider.place(it) != null }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // (Read from disk off the main thread, in init; until then the list as this app last read it.)
    private val _custom = MutableStateFlow(lastCustom ?: emptyList())
    val custom: StateFlow<List<CustomSound>> = _custom.asStateFlow()

    // (Asked of the system off the main thread, in refresh(); until then as it was last time this app asked.)
    private val _readiness = MutableStateFlow(lastReadiness ?: Readiness())
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
        viewModelScope.launch { reloadCustom() }
        viewModelScope.launch(Dispatchers.IO) { container.watchSounds.syncIfNeeded(repo.current().alerts) }
    }

    /** True until the screen is first shown: init has just done what being shown does. */
    private var refreshedByInit = true

    /** The screen is shown (again): [refresh], except the first time, right after init's own. */
    fun onShown() {
        if (refreshedByInit) { refreshedByInit = false; return }
        refresh()
    }

    /**
     * Re-reads permissions and device state (called when the screen is shown again). The system is asked, the
     * notification channels made and the alerts re-armed off the main thread; the answers are published as state.
     */
    fun refresh() {
        shownAgain.value++
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                AlertNotifications.ensureChannels(ctx)
                readinessOf(ctx)
            }
            _readiness.value = r
            lastReadiness = r
        }
        viewModelScope.launch {
            _history.value = withContext(Dispatchers.IO) { AlertLog.all(ctx) }
            // (As the alert receivers do, on a background thread: AlarmManager may be called from any.)
            withContext(Dispatchers.Default) { AlertScheduler.reschedule(ctx) }
            val snap = container.watchSetup.snapshot()
            _watches.value = snap.connected.filter { it.id in snap.withNoor }.map { WatchAlertState(it, snap.reports[it.id]) }
        }
    }

    private fun readinessOf(ctx: Application): Readiness {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return Readiness(
            notificationsAllowed = granted && NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            canRequestNotifications = Build.VERSION.SDK_INT >= 33 && !granted,
            adhanChannelBlocked = nm?.getNotificationChannel(AlertNotifications.CHANNEL_ADHAN)?.importance == NotificationManager.IMPORTANCE_NONE,
            exactAlarms = AlertScheduler.canScheduleExact(ctx),
            alarmVolume = AlertPlayer.alarmVolume(ctx),
            batteryRestricted = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == false,
            fullScreenAllowed = Build.VERSION.SDK_INT < 34 || nm?.canUseFullScreenIntent() != false,
        )
    }

    /** The user's own sounds, read from disk off the main thread. */
    private suspend fun reloadCustom() {
        val list = withContext(Dispatchers.IO) { container.customSounds.list() }
        _custom.value = list
        lastCustom = list
    }

    private fun update(change: (AlertSettings) -> AlertSettings) = viewModelScope.launch { repo.updateAlerts(change) }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    /** The alerts turned on from one prayer's switch (0.15): that prayer alone alerts. */
    fun enableOnly(p: Prayer) = update { s -> Prayer.entries.fold(s.copy(enabled = true)) { x, q -> x.with(q, x.alert(q).copy(enabled = q == p)) } }
    fun setDevice(d: AlertDevice) = update { it.copy(device = d) }
    fun setGradual(seconds: Int) = update { it.copy(gradualSeconds = seconds) }
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
            AlertMode.ADHAN -> a.soundId?.takeIf { it == SoundIds.RANDOM } ?: a.soundId?.let { library.byId(it) }?.takeIf { it.hasFullRecording && it in library.adhansFor(p) }?.id
                ?: if (p == Prayer.FAJR) SoundIds.DEFAULT_FAJR_ADHAN else SoundIds.DEFAULT_ADHAN
            AlertMode.ADHAN_SHORT -> a.soundId?.takeIf { it == SoundIds.RANDOM } ?: a.soundId?.let { library.byId(it) }?.takeIf { it in library.adhansFor(p) }?.id
                ?: if (p == Prayer.FAJR) SoundIds.DEFAULT_FAJR_ADHAN else SoundIds.DEFAULT_ADHAN
            AlertMode.SOUND -> a.soundId?.takeIf { library.byId(it)?.let { e -> e in library.sounds } == true } ?: SoundIds.DEFAULT_SOUND
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

    private fun play(key: PreviewKey, source: SoundSource, volumePercent: Int, maxSeconds: Int = 0) {
        if (_preview.value == key) { stopPreview(); return }
        _preview.value = key
        player.play(source, volumePercent, gradualSeconds = 0, maxSeconds = maxSeconds) { _preview.value = null }
    }

    fun stopPreview() { player.stop(); _preview.value = null }

    /** 0.18: the phone's alarm sound ([SYS_ALARM]) or ringtone ([SYS_RINGTONE]), as an alarm would play it. */
    fun previewSystem(which: String) {
        val ctx = getApplication<Application>()
        val type = when (which) {
            SYS_RINGTONE -> android.media.RingtoneManager.TYPE_RINGTONE
            SYS_NOTIFICATION -> android.media.RingtoneManager.TYPE_NOTIFICATION
            else -> android.media.RingtoneManager.TYPE_ALARM
        }
        val uri = runCatching { android.media.RingtoneManager.getActualDefaultRingtoneUri(ctx, type) }.getOrNull() ?: android.media.RingtoneManager.getDefaultUri(type) ?: return
        play(PreviewKey(which, false), SoundSource.System(uri.toString()), 90)
    }

    fun importSound(uri: Uri, replaceId: String? = null, forPrayer: Prayer? = null) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val result = withContext(Dispatchers.IO) { container.customSounds.import(ctx.contentResolver, uri, replaceId) }
        reloadCustom()
        _message.value = when (result) {
            is ImportResult.Ok -> {
                if (forPrayer != null) setPrayer(forPrayer) { it.copy(enabled = true, mode = AlertMode.CUSTOM, soundId = result.sound.id) }
                // A replaced file has to reach the watch again.
                if (replaceId != null) container.watchSounds.syncIfNeeded(repo.current().alerts, resend = setOf(replaceId))
                ctx.getString(com.noor.app.R.string.adhan_import_ok, result.sound.name)
            }
            is ImportResult.Rejected -> rejected(result.reason)
        }
    }

    // ---- 0.16: the voice for every prayer at once, days, and reminders before and after ----

    /**
     * The Adhan's voice: a library recording, one of the user's own sounds ("custom.…"), or [SoundIds.RANDOM]; for
     * [target] alone, or for every prayer that calls the Adhan (or plays one of the user's sounds) when null. Fajr
     * keeps a Fajr Adhan (with "As-salatu khayrun min an-nawm") unless the voice is one, or it is chosen for Fajr alone.
     */
    fun applyVoice(target: Prayer?, voice: String) = update { s0 ->
        var s = s0
        // ("Random": a different one of the voices each time, Fajr's own recordings at Fajr.)
        if (voice == SoundIds.RANDOM) s = s.copy(favourites = library.adhans.map { it.id }.filter { id -> VOICE_LIBRARY_IDS.any { id == it || id == it + "_fajr" } })
        val custom = SoundIds.isCustom(voice)
        for (p in Prayer.entries) {
            if (p == Prayer.SHURUQ) continue
            if (target != null && p != target) continue
            val a = s.alert(p)
            // (For every prayer: those that call the Adhan; a prayer set to a notification or a sound keeps it.)
            if (target == null && a.mode !in VOICE_MODES) continue
            // 0.18: Fajr takes the voice's own Fajr recording ("As-salatu khayrun min an-nawm") when there is one; for
            // every prayer at once, a voice without one leaves Fajr on the Fajr Adhan it has (never a plain Adhan at Fajr).
            val own = if (p == Prayer.FAJR) fajrOf(voice, this, _custom.value) else voice
            if (p == Prayer.FAJR && target == null && own == voice && library.byId(a.soundId)?.fajr == true && voice != SoundIds.RANDOM) continue
            val id = own
            val entry = library.byId(id)
            val mode = when {
                custom -> AlertMode.CUSTOM
                a.mode == AlertMode.ADHAN_SHORT -> AlertMode.ADHAN_SHORT
                entry != null && !entry.hasFullRecording -> AlertMode.ADHAN_SHORT
                else -> AlertMode.ADHAN
            }
            s = s.with(p, a.copy(enabled = a.enabled || target != null, mode = mode, soundId = id))
        }
        s
    }

    /** 0.18: the volume of every prayer's alert at once (each can still have its own, on its page). */
    fun setVolumeAll(percent: Int) = update { s -> Prayer.entries.fold(s) { x, p -> x.with(p, x.alert(p).copy(volumePercent = percent)) } }

    /**
     * Files imported as one of the voices ([name]: "Al-Quds", "Egypt", "Dubai"…), then used as [applyVoice] would. 0.18:
     * a file whose name says Fajr ("… Fajr", "… الفجر") becomes the voice's Fajr recording, played at Fajr.
     */
    fun importVoice(uris: List<Uri>, name: String, target: Prayer?) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        var main: String? = null
        for (uri in uris.take(2)) {
            val display = runCatching { ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull().orEmpty()
            val fajr = Regex("fajr|الفجر|فجر", RegexOption.IGNORE_CASE).containsMatchIn(display) && uris.size > 1
            val result = withContext(Dispatchers.IO) { container.customSounds.import(ctx.contentResolver, uri, null) }
            if (result !is ImportResult.Ok) { importSoundResult(result); continue }
            withContext(Dispatchers.IO) { container.customSounds.rename(result.sound.id, if (fajr) "$name Fajr" else name) }
            if (!fajr) main = result.sound.id
        }
        reloadCustom()
        main?.let { applyVoice(target, it); _message.value = ctx.getString(com.noor.app.R.string.adhan_import_ok, name) }
    }

    private fun importSoundResult(result: ImportResult) {
        val ctx = getApplication<Application>()
        if (result is ImportResult.Rejected) _message.value = rejected(result.reason)
    }

    /** Why a file was refused, in a sentence (the largest size in the digits the user chose). */
    private fun rejected(reason: ImportResult.Reason): String {
        val ctx = getApplication<Application>()
        return when (reason) {
            ImportResult.Reason.UNSUPPORTED_TYPE -> ctx.getString(com.noor.app.R.string.adhan_import_unsupported)
            ImportResult.Reason.TOO_LARGE -> ctx.getString(com.noor.app.R.string.adhan_import_too_large,
                digits((com.noor.alerts.CustomSounds.MAX_BYTES / (1024 * 1024)).toInt()))
            ImportResult.Reason.NOT_AUDIO -> ctx.getString(com.noor.app.R.string.adhan_import_not_audio)
            ImportResult.Reason.UNREADABLE -> ctx.getString(com.noor.app.R.string.adhan_import_unreadable)
        }
    }

    fun setDays(p: Prayer, days: Int) = setPrayer(p) { it.copy(days = days) }

    fun renameSound(id: String, name: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { container.customSounds.rename(id, name) }
        reloadCustom()
    }

    /** Prayers that use [id]; they move to the default Adhan when it is deleted. */
    fun usersOf(id: String): List<Prayer> = settings.value?.alerts?.let { a ->
        Prayer.entries.filter { a.alert(it).mode == AlertMode.CUSTOM && a.alert(it).soundId == id }
    }.orEmpty()

    fun deleteSound(id: String) = viewModelScope.launch {
        if (_preview.value?.id == id) stopPreview()
        val users = usersOf(id)
        repo.updateAlerts { s0 ->
            // (0.17: an alarm that rang with it rings with the phone's alarm sound.)
            val s = s0.alarms.filter { it.customId == id }.fold(s0) { acc, a -> acc.withAlarm(a.copy(sound = com.noor.prayer.alerts.AlarmSound.PHONE_ALARM, customId = null)) }
            users.fold(s) { acc, p ->
                acc.with(p, acc.alert(p).copy(mode = if (p == Prayer.SHURUQ) AlertMode.SOUND else AlertMode.ADHAN,
                    soundId = when (p) { Prayer.SHURUQ -> SoundIds.DEFAULT_SOUND; Prayer.FAJR -> SoundIds.DEFAULT_FAJR_ADHAN; else -> SoundIds.DEFAULT_ADHAN }))
            }
        }
        withContext(Dispatchers.IO) { container.customSounds.delete(id) }
        reloadCustom()
    }

    fun test(p: Prayer) {
        val ctx = getApplication<Application>()
        stopPreview()
        _message.value = ctx.getString(
            if (AlertScheduler.scheduleTest(ctx, p, 10)) com.noor.app.R.string.adhan_test_sent else com.noor.app.R.string.adhan_test_failed, digits(10))
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
            appendLine("Minhad ${com.noor.app.BuildConfigInfo.versionName} alert report, ${t(System.currentTimeMillis())} ($zone)")
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

    // ---- 0.26: what plays, the volume, the soft start and vibration, for one prayer or for every prayer at once ----

    /**
     * [choice] for [target], or for the five prayers at once when null (sunrise keeps its own: it has no Adhan). Each
     * prayer keeps its switch, volume and days. At Fajr a voice plays its Fajr recording when it has one ([playing]).
     */
    internal fun choose(target: Prayer?, choice: Plays) = update { s0 ->
        var s = s0
        // ("Random": a different one of the voices each time, Fajr's own recordings at Fajr.)
        if (choice is Plays.Adhan && choice.voice == SoundIds.RANDOM)
            s = s.copy(favourites = library.adhans.map { it.id }.filter { id -> VOICE_LIBRARY_IDS.any { id == it || id == it + "_fajr" } })
        val custom = _custom.value
        s.withEach(target?.let(::listOf) ?: FIVE_PRAYERS) { p, a ->
            if (p == Prayer.SHURUQ && choice is Plays.Adhan) a else a.playing(p, choice, library, custom, forAll = target == null)
        }
    }

    /** "Start softly" for [target], or for every prayer when null: a rise over [PrayerAlert.SOFT_START_SECONDS], or none. */
    fun setSoftStart(target: Prayer?, on: Boolean) = update { s ->
        s.withEach(target?.let(::listOf) ?: Prayer.entries) { _, a -> a.copy(gradualSeconds = if (on) PrayerAlert.SOFT_START_SECONDS else 0) }
    }

    /** Vibration for [target], or for every prayer when null. */
    fun setVibrate(target: Prayer?, on: Boolean) = update { s -> s.withEach(target?.let(::listOf) ?: Prayer.entries) { _, a -> a.copy(vibrate = on) } }

    /** The volume of [target] (every prayer when null), and a few seconds of what it plays at that volume. */
    fun setVolume(target: Prayer?, percent: Int) {
        if (target == null) setVolumeAll(percent) else setPrayer(target) { it.copy(volumePercent = percent) }
        sample(target, percent)
    }

    /**
     * A few seconds of what [target] plays (for every prayer: what they share, or else Dhuhr's) at [percent], so that a
     * volume is heard as it is set: the Adhan's opening, an alert sound, the user's recording. Always from its start.
     */
    fun sample(target: Prayer?, percent: Int) {
        val s = settings.value?.alerts ?: return
        val p = target ?: Prayer.DHUHR
        val plays = (if (target == null) sharedPlays(s, library, _custom.value) else null) ?: playsOf(p, s.alert(p), library, _custom.value)
        val source: Pair<String, SoundSource>? = when (plays) {
            is Plays.Adhan -> {
                val id = (if (p == Prayer.FAJR) fajrSound(plays.voice, library, _custom.value) else plays.voice)
                    .let { if (it == SoundIds.RANDOM) library.byId(SoundIds.DEFAULT_ADHAN)?.id else it }
                when {
                    id == null -> null
                    SoundIds.isCustom(id) -> container.customSounds.get(id)?.let { container.customSounds.file(it) }?.let { id to SoundSource.LocalFile(it) }
                    else -> library.byId(id)?.let { e -> (e.shortFile ?: e.file)?.let { id to SoundSource.Asset(it) } }
                }
            }
            is Plays.Effect -> library.byId(plays.id)?.let { e -> (e.file ?: e.shortFile)?.let { e.id to SoundSource.Asset(it) } }
            is Plays.Own -> container.customSounds.get(plays.id)?.let { container.customSounds.file(it) }?.let { plays.id to SoundSource.LocalFile(it) }
            Plays.Notification, Plays.Silent -> null
        }
        source ?: return
        stopPreview()
        play(PreviewKey(SAMPLE, false), source.second, percent, maxSeconds = 6)
    }

    /**
     * What a row of the sound picker plays when touched: [plays] as it will sound at [p] ([fajr]: its Fajr recording),
     * at the prayer's volume; a second touch stops it.
     */
    internal fun preview(plays: Plays, p: Prayer?, fajr: Boolean = false) {
        val volume = p?.let { settings.value?.alerts?.alert(it)?.volumePercent } ?: settings.value?.alerts?.alert(Prayer.DHUHR)?.volumePercent ?: 80
        when (plays) {
            is Plays.Adhan -> {
                val id = if (fajr) fajrSound(plays.voice, library, _custom.value) else plays.voice
                if (id == SoundIds.RANDOM) return
                if (SoundIds.isCustom(id)) previewCustom(id, volume)
                else previewLibrary(id, short = plays.opening && library.byId(id)?.shortFile != null || library.byId(id)?.hasFullRecording == false, volumePercent = volume)
            }
            is Plays.Effect -> previewLibrary(plays.id, short = library.byId(plays.id)?.file == null, volumePercent = volume)
            is Plays.Own -> previewCustom(plays.id, volume)
            Plays.Notification -> previewSystem(SYS_NOTIFICATION)
            Plays.Silent -> stopPreview()
        }
    }

    fun openNoorOnWatch(nodeId: String) = viewModelScope.launch { container.watchSetup.openNoorOnWatch(nodeId) }

    fun consumeMessage() { _message.value = null }

    /** [n] in the digits the user chose and the app's language (for messages built here). */
    private fun digits(n: Int): String {
        val s = settings.value ?: return n.toString()
        val ar = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()[0]?.language == "ar" ||
            (androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().isEmpty && java.util.Locale.getDefault().language == "ar")
        return com.noor.app.util.Formatter(ar, s.numerals, s.timeFormat).number(n)
    }

    override fun onCleared() { player.stop(notify = false) }

    private companion object {
        /** What the last page asked of the system and read from disk: a new page starts from it, not from nothing. */
        @Volatile var lastReadiness: Readiness? = null
        @Volatile var lastCustom: List<CustomSound>? = null
    }
}

/** Preview keys of the phone's own sounds. */
internal const val SYS_ALARM = "sys.alarm"
internal const val SYS_RINGTONE = "sys.ringtone"
/** 0.26: the phone's notification sound ("Notification only"). */
internal const val SYS_NOTIFICATION = "sys.notification"
/** 0.26: the few seconds heard while a volume is set. */
internal const val SAMPLE = "sample"

/** The kinds of alert a voice applies to: the Adhan, its opening, the user's own sound. */
internal val VOICE_MODES = setOf(AlertMode.ADHAN, AlertMode.ADHAN_SHORT, AlertMode.CUSTOM)

/** The library recordings behind the voices named as on the owner's clock (Makkah, Madinah). */
/** 0.18: the owner's five voices, as the library names them (a voice the library lacks is the user's own recording). */
internal val VOICE_LIBRARY_IDS = listOf("adhan.makkah", "adhan.madinah", "adhan.alquds", "adhan.egypt", "adhan.dubai")
