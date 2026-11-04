package com.noor.app.ui.watch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.app.NoorApplication
import com.noor.app.data.WatchSetupState
import com.noor.app.data.WearSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An action the user started on the phone that finishes on the watch. */
data class Pending(val startedAtMs: Long, val reached: Boolean)

class WatchViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    val setup = container.watchSetup

    /** Watches already nudged; each gets at most one automatic re-send per visit. */
    private val nudged = mutableSetOf<String>()

    val state: StateFlow<WatchSetupState?> = setup.states()
        .onEach { st ->
            // A watch that has Noor but has not confirmed receiving settings (new install, or installed while the
            // phone app was closed) gets them re-sent now; a re-sent item always reaches its listener.
            val waiting = st.withNoor.filter { st.reports[it]?.configReceivedAtMs == null && it !in nudged }
            if (waiting.isNotEmpty()) { nudged += waiting; syncNow() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings = container.settings.settings
    val lastSent = container.wear.lastSent

    private val _installs = MutableStateFlow<Map<String, Pending>>(emptyMap())
    val installs: StateFlow<Map<String, Pending>> = _installs.asStateFlow()
    private val _faces = MutableStateFlow<Map<String, Pending>>(emptyMap())
    val faces: StateFlow<Map<String, Pending>> = _faces.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _syncFailed = MutableStateFlow(false)
    val syncFailed: StateFlow<Boolean> = _syncFailed.asStateFlow()

    fun installOnWatch(nodeId: String) = viewModelScope.launch {
        val reached = setup.openPlayStoreOnWatch(nodeId)
        _installs.update { it + (nodeId to Pending(System.currentTimeMillis(), reached)) }
    }

    fun useNoorFace(nodeId: String) = viewModelScope.launch {
        val reached = setup.openFaceSetupOnWatch(nodeId)
        _faces.update { it + (nodeId to Pending(System.currentTimeMillis(), reached)) }
    }

    fun openOnWatch(nodeId: String) = viewModelScope.launch { setup.openNoorOnWatch(nodeId) }

    fun syncNow() = viewModelScope.launch {
        _syncing.value = true
        _syncFailed.value = !container.wear.syncNow()
        _syncing.value = false
    }

    fun configAvailable(s: com.noor.app.data.AppSettings?) = s?.let { WearSync.configFor(it, 0L) } != null

    companion object {
        /** After this long without the watch reporting back, a pending action is shown as not finished. */
        const val PENDING_TIMEOUT_MS = 5 * 60_000L

        fun wearOsName(sdk: Int): String = when (sdk) {
            30 -> "3"; 33 -> "4"; 34 -> "5"; 35 -> "5.1"; 36 -> "6"
            else -> if (sdk > 36) "6+" else "API $sdk"
        }
    }
}
