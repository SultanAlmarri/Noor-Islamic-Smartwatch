package com.noor.app.ui.adhan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.CustomSound
import com.noor.alerts.CustomSounds
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.SoundIds

/**
 * The Adhan's voices (0.16; 0.18 the owner's final five): Makkah, Madinah, Al-Quds, Egypt and Dubai, and Random.
 * [libraryId]: Noor's licensed recording of that voice, when the library has one; otherwise the user adds one (a
 * recording they have the right to use), kept on their phone only, under the voice's name.
 */
internal enum class ClockVoice(val label: Int, val libraryId: String, val names: List<String>) {
    MAKKAH(R.string.voice_makkah, VOICE_LIBRARY_IDS[0], listOf("Makkah", "مكة")),
    MADINAH(R.string.voice_madinah, VOICE_LIBRARY_IDS[1], listOf("Madinah", "المدينة")),
    ALQUDS(R.string.voice_alquds, VOICE_LIBRARY_IDS[2], listOf("Al-Quds", "القدس")),
    EGYPT(R.string.voice_egypt, VOICE_LIBRARY_IDS[3], listOf("Egypt", "مصر")),
    DUBAI(R.string.voice_dubai, VOICE_LIBRARY_IDS[4], listOf("Dubai", "دبي")),
}

/** The user's sound that stands for [this] voice (one they added under its name), if any. */
internal fun ClockVoice.ownSound(custom: List<CustomSound>) = custom.firstOrNull { it.name.trim() in names }

/** 0.18: the user's Fajr recording of this voice ("Dubai Fajr", "دبي الفجر"), played at Fajr instead of [ownSound]. */
internal fun ClockVoice.ownFajr(custom: List<CustomSound>) = custom.firstOrNull { c ->
    val n = c.name.trim().lowercase()
    names.any { v -> v.lowercase().let { b -> n == "$b fajr" || n == "$b (fajr)" || n == "$b الفجر" || n == "$b (الفجر)" } }
}

/** The recording a voice plays at Fajr: its own Fajr recording (Noor's or the user's) when there is one. */
internal fun fajrOf(id: String, vm: AdhanViewModel, custom: List<CustomSound>): String =
    if (SoundIds.isCustom(id)) ClockVoice.entries.firstOrNull { it.ownSound(custom)?.id == id }?.ownFajr(custom)?.id ?: id
    else vm.library.byId(id + "_fajr")?.id ?: id

/** The sound that plays for this voice: Noor's recording, or the user's own; null when there is neither yet. */
internal fun ClockVoice.soundId(vm: AdhanViewModel, custom: List<CustomSound>): String? =
    libraryId.takeIf { vm.library.byId(it) != null } ?: ownSound(custom)?.id

/** Which voice [id] is (a voice's Fajr recording counts as the voice). */
internal fun voiceOf(id: String?, vm: AdhanViewModel, custom: List<CustomSound>): ClockVoice? =
    ClockVoice.entries.firstOrNull { v -> v.soundId(vm, custom).let { it != null && (it == id || it + "_fajr" == id) } || (id != null && v.ownFajr(custom)?.id == id) }

/** A voice's name for a sound id: a voice's, a sound effect's, or one of the user's sounds'. */
@Composable
internal fun voiceName(id: String?, vm: AdhanViewModel, custom: List<CustomSound>, ar: Boolean): String? = when {
    id == null -> null
    id == SoundIds.RANDOM -> stringResource(R.string.voice_random)
    else -> voiceOf(id, vm, custom)?.let { stringResource(it.label) } ?: vm.library.byId(id)?.title(ar) ?: custom.firstOrNull { it.id == id }?.name
}

/** The voice every prayer that calls the Adhan shares (Fajr's own recording counted as its voice), or null if they differ. */
internal fun sharedVoice(a: AlertSettings, vm: AdhanViewModel, custom: List<CustomSound>): String? {
    // (Fajr on a Fajr Adhan of another voice, kept because the shared voice has none, does not make the voices "mixed".)
    val ids = Prayer.entries.filter { it != Prayer.SHURUQ && a.alert(it).mode in VOICE_MODES }
        .filterNot { p -> p == Prayer.FAJR && vm.library.byId(a.alert(p).soundId)?.fajr == true }.map { p ->
        val id = a.alert(p).soundId
        voiceOf(id, vm, custom)?.soundId(vm, custom) ?: id
    }.distinct()
    return ids.singleOrNull()
}

/**
 * One prayer's voice (0.16; 0.18: only the five voices and Random, the user's own recordings, and, for a prayer set to a
 * sound, the sound effects). [target] null: every prayer at once (0.25: the one place for that choice; the Adhan tab's
 * six tiles were the same choice a second time). A touch chooses a voice and plays its opening, as the tiles did (a
 * second touch stops it); the round button plays the whole recording. A voice Noor has no recording of says so and has
 * a plus where the others have their button: the row adds the user's own.
 */
@Composable
fun VoiceScreen(contentPadding: PaddingValues, target: Prayer?, onBack: () -> Unit, vm: AdhanViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val alerts = s.alerts
    val preview by vm.preview.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val lib = vm.library
    val snackbar = remember { SnackbarHostState() }
    val previewLabel = stringResource(R.string.adhan_preview)
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    StopPreviewOnLeave(vm)

    // (A prayer set to a sound, and sunrise, which has no Adhan: the sound effects instead of the voices.)
    val effects = target != null && (alerts.alert(target).mode == AlertMode.SOUND || target == Prayer.SHURUQ)
    val current: String? = if (target != null) alerts.alert(target).soundId else sharedVoice(alerts, vm, custom)
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    // (A voice may take two files at once: the Adhan, and one named "… Fajr" for Fajr.)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val name = adding
        if (uris.isNotEmpty() && name != null) { if (name.isEmpty()) uris.forEach { vm.importSound(it, forPrayer = target) } else vm.importVoice(uris, name, target) }
        adding = null
    }
    val volume = target?.let { alerts.alert(it).volumePercent } ?: 80
    val title = when {
        effects -> stringResource(R.string.adhan_sound_title)
        target == null -> stringResource(R.string.adhan_voice)
        else -> stringResource(R.string.voice_for, f.prayerName(target))
    }

    // The opening of [id] (the whole of it when it has no opening of its own, or is the user's file); a second touch stops it.
    fun hear(id: String) {
        if (preview?.id == id) vm.stopPreview()
        else if (SoundIds.isCustom(id)) vm.previewCustom(id, volume)
        else vm.previewLibrary(id, short = lib.byId(id)?.shortFile != null, volumePercent = volume)
    }
    // The whole recording, from the round button (which stops whatever of it is playing).
    fun whole(id: String) {
        if (preview?.id == id) vm.stopPreview()
        else if (SoundIds.isCustom(id)) vm.previewCustom(id, volume)
        else vm.previewLibrary(id, false, volume)
    }

    NoorPage(title, contentPadding, onBack = onBack,
        floating = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        if (effects) SettingsSection("") {
            lib.sounds.forEachIndexed { i, e ->
                if (i > 0) SettingsDivider()
                SoundRow(e.title(ar), f.duration(e.seconds), current == e.id, preview?.id == e.id, previewLabel,
                    onSelect = { if (current != e.id) vm.setPrayer(target!!) { it.copy(mode = AlertMode.SOUND, soundId = e.id) }; hear(e.id) }, onPreview = { whole(e.id) })
            }
        } else SettingsSection(if (target == null) stringResource(R.string.adhan_all_prayers) else "") {
            ClockVoice.entries.forEachIndexed { i, v ->
                if (i > 0) SettingsDivider()
                val id = v.soundId(vm, custom)
                // (At Fajr, the voice's own Fajr recording when it has one.)
                val play = if (target == Prayer.FAJR) id?.let { fajrOf(it, vm, custom) } else id
                val name = stringResource(v.label)
                val chosen = id != null && (current == id || current == play)
                if (id == null || play == null) SoundRow(name, stringResource(R.string.voice_add), false, false, previewLabel,
                    onSelect = { adding = v.names[0]; picker.launch(CustomSounds.PICKER_TYPES) }, onPreview = null, add = true)
                else SoundRow(name, null, selected = chosen, playing = preview?.id == play, previewLabel = previewLabel,
                    onSelect = { if (!chosen) vm.applyVoice(target, id); hear(play) }, onPreview = { whole(play) })
            }
            SettingsDivider()
            SoundRow(stringResource(R.string.voice_random), stringResource(R.string.voice_random_detail), current == SoundIds.RANDOM, false, previewLabel,
                onSelect = { vm.stopPreview(); vm.applyVoice(target, SoundIds.RANDOM) }, onPreview = null)
        }

        // The user's own sounds (those not standing for a voice), and adding one.
        val voiceOwn = ClockVoice.entries.flatMap { listOfNotNull(it.ownSound(custom)?.id, it.ownFajr(custom)?.id) }.toSet()
        val mine = custom.filter { it.id !in voiceOwn }
        SettingsSection(stringResource(R.string.adhan_my_sounds)) {
            mine.forEachIndexed { i, cs ->
                if (i > 0) SettingsDivider()
                SoundRow(cs.name, f.duration(cs.seconds), current == cs.id, preview == PreviewKey(cs.id, false), previewLabel,
                    onSelect = {
                        if (current != cs.id) { if (target == Prayer.SHURUQ) vm.setPrayer(target) { it.copy(mode = AlertMode.CUSTOM, soundId = cs.id) } else vm.applyVoice(target, cs.id) }
                        hear(cs.id)
                    },
                    onPreview = { vm.previewCustom(cs.id, volume) })
            }
            if (mine.isNotEmpty()) SettingsDivider()
            SettingsItem(Icons.Rounded.Add, stringResource(R.string.adhan_import), null, onClick = { adding = ""; picker.launch(CustomSounds.PICKER_TYPES) })
        }
    }
}
