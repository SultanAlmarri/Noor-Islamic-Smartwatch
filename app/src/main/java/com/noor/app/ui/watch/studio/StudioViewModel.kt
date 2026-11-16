package com.noor.app.ui.watch.studio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.app.NoorApplication
import com.noor.app.data.WatchSetupState
import com.noor.app.data.WearSync
import com.noor.prayer.WatchConfig
import com.noor.prayer.WatchReport
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.Slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The editor's panels. */
enum class StudioPanel { DESIGNS, STYLE, DIAL, AMBIENT, OPTIONS }

/** Everything the editor shows; plain data, so every state can be rendered in tests. */
data class StudioState(
    val design: FaceDesign,
    val selected: Slot? = null,
    val undo: List<FaceDesign> = emptyList(),
    val redo: List<FaceDesign> = emptyList(),
    val saved: List<FaceDesign> = emptyList(),
    val ambient: Boolean = false,
    val panel: StudioPanel = StudioPanel.DESIGNS,
    val sending: Boolean = false,
    val sendFailed: Boolean = false,
    /** When this phone last sent a design, and which. */
    val sentAt: Long? = null,
    val sentDesign: FaceDesign? = null,
)

/** Where the design stands with the watch, from the watch's own report. */
enum class StudioStatus { NONE, SENT, SHOWING, PICK, UNSUPPORTED, UPDATE, FAILED }

fun studioStatus(s: StudioState, watches: WatchSetupState?): StudioStatus {
    if (s.sendFailed) return StudioStatus.FAILED
    val reports: Collection<WatchReport> = watches?.reports?.values.orEmpty()
    if (reports.any { it.studioSupported == false }) return StudioStatus.UNSUPPORTED
    val at = s.sentAt ?: return if (reports.any { it.studioActive == true }) StudioStatus.SHOWING else StudioStatus.NONE
    val got = reports.filter { it.studioStamp == at }
    return when {
        got.any { it.studioActive == true } -> StudioStatus.SHOWING
        got.isNotEmpty() -> StudioStatus.PICK
        reports.isNotEmpty() && reports.all { it.studioSupported == null } -> StudioStatus.UPDATE
        watches?.connected.isNullOrEmpty() -> StudioStatus.NONE
        else -> StudioStatus.SENT
    }
}

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    private val repo = StudioRepository(app)

    private val _state = MutableStateFlow(StudioState(repo.draft, saved = repo.saved(), sentAt = repo.lastSentAt))
    val state: StateFlow<StudioState> = _state.asStateFlow()

    /** The configuration the phone sends the watch: the face's prayer data comes from exactly this. */
    val config: StateFlow<WatchConfig?> = container.settings.settings
        .map { WearSync.configFor(it, 0L, container.timetables) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val watches: StateFlow<WatchSetupState?> = container.watchSetup.states()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every change goes through here: one undo step, and the draft is kept for next time. */
    fun edit(change: (FaceDesign) -> FaceDesign) = _state.update { s ->
        val next = change(s.design)
        if (next == s.design) s else {
            repo.draft = next
            s.copy(design = next, undo = (s.undo + s.design).takeLast(MAX_UNDO), redo = emptyList(), sendFailed = false)
        }
    }

    fun undo() = _state.update { s ->
        val prev = s.undo.lastOrNull() ?: return@update s
        repo.draft = prev
        s.copy(design = prev, undo = s.undo.dropLast(1), redo = s.redo + s.design)
    }

    fun redo() = _state.update { s ->
        val next = s.redo.lastOrNull() ?: return@update s
        repo.draft = next
        s.copy(design = next, redo = s.redo.dropLast(1), undo = s.undo + s.design)
    }

    fun select(slot: Slot?) = _state.update { it.copy(selected = if (it.selected == slot) null else slot) }
    fun panel(p: StudioPanel) = _state.update { it.copy(panel = p) }
    fun ambient(on: Boolean) = _state.update { it.copy(ambient = on) }

    /** Starts from a template or a saved design (an undo step, so it is never a loss). */
    fun use(d: FaceDesign) { edit { d }; _state.update { it.copy(selected = null) } }

    fun save(name: String) {
        val out = repo.save(_state.value.design, name)
        repo.draft = out
        _state.update { it.copy(design = out, saved = repo.saved()) }
    }

    fun delete(id: String) {
        repo.delete(id)
        _state.update { it.copy(saved = repo.saved()) }
    }

    /**
     * "Set on my watch": sends the design; if a watch in reach has the studio face but is not showing it, opens the
     * watch app's face page there, whose one button asks the system to show it (the user confirms on the watch).
     */
    fun send() = viewModelScope.launch {
        val d = _state.value.design
        _state.update { it.copy(sending = true, sendFailed = false) }
        val at = repo.send(d)
        _state.update { it.copy(sending = false, sendFailed = at == null, sentAt = at ?: it.sentAt, sentDesign = if (at != null) d else it.sentDesign) }
        val w = watches.value ?: return@launch
        for (node in w.connected) {
            val r = w.reports[node.id] ?: continue
            if (r.studioSupported == true && r.studioActive != true) container.watchSetup.openStudioOnWatch(node.id)
        }
    }

    private companion object { const val MAX_UNDO = 60 }
}
