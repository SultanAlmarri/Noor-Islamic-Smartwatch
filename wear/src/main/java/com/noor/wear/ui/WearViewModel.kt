package com.noor.wear.ui

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.prayer.LocalSetup
import com.noor.prayer.SetupPlace
import com.noor.prayer.dhikr.TasbihState
import com.noor.prayer.geo.GazPlace
import com.noor.wear.NoorWearApp
import com.noor.wear.data.PhoneLink
import com.noor.wear.data.WatchLocation
import com.noor.wear.data.WatchState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val android.content.Context.wearPrefs by preferencesDataStore("noor_watch_ui")

/** State and actions behind the watch app's screens. Survives screen changes; work runs in [viewModelScope]. */
class WearViewModel(app: Application) : AndroidViewModel(app) {
    private val noor = app as NoorWearApp
    private val keyVibrate = booleanPreferencesKey("tasbih_vibrate")

    val state: StateFlow<WatchState?> = noor.config.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val tasbih: StateFlow<TasbihState?> = noor.tasbih.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val tasbihVibrate: StateFlow<Boolean> = app.wearPrefs.data.map { it[keyVibrate] ?: true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _phone = MutableStateFlow<PhoneLink.Phone?>(null)
    val phone: StateFlow<PhoneLink.Phone?> = _phone.asStateFlow()
    private val _sync = MutableStateFlow(Progress.IDLE)
    val sync: StateFlow<Progress> = _sync.asStateFlow()
    private val _locate = MutableStateFlow<LocateStatus>(LocateStatus.Idle)
    val locate: StateFlow<LocateStatus> = _locate.asStateFlow()
    private val _results = MutableStateFlow<List<GazPlace>?>(null)
    val results: StateFlow<List<GazPlace>?> = _results.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    val versionName: String = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""

    init {
        viewModelScope.launch { _phone.value = noor.phone.phone() }
        viewModelScope.launch { noor.config.refreshLocal() }
    }

    fun refreshPhone() { viewModelScope.launch { _phone.value = noor.phone.phone() } }

    fun requestSync() {
        if (_sync.value == Progress.WORKING) return
        viewModelScope.launch {
            _sync.value = Progress.WORKING
            val before = noor.config.receivedAt.first()
            val sent = noor.phone.requestSync()
            // The phone answers by publishing its settings again; wait briefly for them to arrive.
            var arrived = false
            if (sent) repeat(16) { if (!arrived) { delay(500); noor.config.pullFromDataLayer(); arrived = noor.config.receivedAt.first() != before } }
            _sync.value = if (sent) Progress.DONE else Progress.FAILED
            delay(4000)
            if (_sync.value != Progress.WORKING) _sync.value = Progress.IDLE
        }
    }

    /** Finds the watch's position and, when found, makes it the place the watch calculates for. */
    fun locate(onFound: () -> Unit) {
        if (_locate.value == LocateStatus.Working) return
        viewModelScope.launch {
            _locate.value = LocateStatus.Working
            when (val r = noor.location.locate()) {
                is WatchLocation.Result.Found -> { pick(r.place); _locate.value = LocateStatus.Idle; onFound() }
                WatchLocation.Result.NoPermission -> _locate.value = LocateStatus.NoPermission
                WatchLocation.Result.Unavailable -> _locate.value = LocateStatus.Unavailable
            }
        }
    }

    fun locationDenied() { _locate.value = LocateStatus.NoPermission }

    fun hasLocationPermission(): Boolean = noor.location.hasPermission()

    fun pick(place: SetupPlace) {
        viewModelScope.launch {
            val current = noor.config.state.first().setup ?: LocalSetup()
            noor.config.saveSetup(current.copy(place = place))
        }
    }

    fun updateSetup(transform: (LocalSetup) -> LocalSetup) {
        viewModelScope.launch {
            val current = noor.config.state.first().setup ?: LocalSetup()
            noor.config.saveSetup(transform(current))
        }
    }

    fun useWatchSettings(watch: Boolean) { viewModelScope.launch { noor.config.useWatchSettings(watch) } }

    fun search(query: String) {
        val q = query.trim()
        if (q.length < 2) { _results.value = emptyList(); return }
        viewModelScope.launch {
            _searching.value = true
            _results.value = runCatching { noor.gazetteer.get().search(q, limit = 12) }.getOrDefault(emptyList())
            _searching.value = false
        }
    }

    private var lastSave: Job? = null
    /** Counting is instant on screen; each count is then written in order (DataStore queues the writes). */
    fun saveTasbih(s: TasbihState) { lastSave = viewModelScope.launch { noor.tasbih.save(s) } }

    /** Leaving the counter: the complication and tile show the new day total. */
    fun tasbihLeft() { viewModelScope.launch { lastSave?.join(); noor.tasbih.refreshSurfaces() } }

    fun setTasbihVibrate(on: Boolean) { viewModelScope.launch { getApplication<Application>().wearPrefs.edit { it[keyVibrate] = on } } }

    fun openPhoneDownload() { viewModelScope.launch { noor.phone.openDownloadOnPhone() } }
}
