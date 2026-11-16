package com.noor.app.ui.adhan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.rounded.Add
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.CustomSounds
import com.noor.app.R
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.SoundIds

/**
 * The voices named as on the owner's Al-Fajr clock (0.16): its dial reads Makkah, Madinah, Al-Quds, Egypt, Riyadh and
 * Random. [libraryId]: the licensed recording Noor has from that place; null where it has none yet, and the user adds
 * one (a recording of their own clock, say), kept on their phone only.
 */
internal enum class ClockVoice(val label: Int, val libraryId: String?, val names: List<String>) {
    MAKKAH(R.string.voice_makkah, VOICE_LIBRARY_IDS[0], listOf("Makkah", "مكة")),
    MADINAH(R.string.voice_madinah, VOICE_LIBRARY_IDS[1], listOf("Madinah", "المدينة")),
    ALQUDS(R.string.voice_alquds, null, listOf("Al-Quds", "القدس")),
    EGYPT(R.string.voice_egypt, null, listOf("Egypt", "مصر")),
    RIYADH(R.string.voice_riyadh, null, listOf("Riyadh", "الرياض")),
}

/** The user's sound that stands for [v] (one they added under its name), if any. */
internal fun ClockVoice.ownSound(custom: List<com.noor.alerts.CustomSound>) = custom.firstOrNull { it.name.trim() in names }

/** A voice's name for a sound id: a clock voice's, a library recording's, or one of the user's sounds'. */
@Composable
internal fun voiceName(id: String?, vm: AdhanViewModel, custom: List<com.noor.alerts.CustomSound>, ar: Boolean): String? = when {
    id == null -> null
    id == SoundIds.RANDOM -> stringResource(R.string.voice_random)
    else -> ClockVoice.entries.firstOrNull { it.libraryId == id || it.ownSound(custom)?.id == id }?.let { stringResource(it.label) }
        ?: vm.library.byId(id)?.title(ar) ?: custom.firstOrNull { it.id == id }?.name
}

/**
 * Choosing the Adhan's voice (0.16), for every prayer at once ([target] null) or for one. The clock's six first, then
 * the other recordings, then the user's own sounds; each can be heard before it is chosen.
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

    // What is chosen now: the target's voice, or the one every Adhan prayer but Fajr shares.
    val current: String? = if (target != null) alerts.alert(target).soundId
        else Prayer.entries.filter { it != Prayer.FAJR && it != Prayer.SHURUQ && alerts.alert(it).mode in VOICE_MODES }
            .map { alerts.alert(it).soundId }.distinct().singleOrNull()
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val name = adding
        // (A clock voice's file takes its name; any other keeps the file's own.)
        if (uri != null && name != null) { if (name.isEmpty()) vm.importSound(uri, forPrayer = target) else vm.importVoice(uri, name, target) }
        adding = null
    }
    val volume = target?.let { alerts.alert(it).volumePercent } ?: 80

    NoorPage(if (target == null) stringResource(R.string.adhan_voice) else stringResource(R.string.voice_for, f.prayerName(target)), contentPadding, onBack = onBack,
        floating = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        SettingsSection("", footer = if (target == null || target == Prayer.FAJR) stringResource(R.string.voice_fajr_note) else null) {
            ClockVoice.entries.forEachIndexed { i, v ->
                if (i > 0) SettingsDivider()
                val own = v.ownSound(custom)
                val id = v.libraryId ?: own?.id
                val title = stringResource(v.label)
                SoundRow(
                    title = title,
                    detail = when {
                        v.libraryId != null -> lib.byId(v.libraryId)?.let { e -> e.place(ar)?.let(f::digits) }
                        own != null -> stringResource(R.string.voice_yours)
                        else -> stringResource(R.string.voice_add)
                    },
                    selected = id != null && current == id, playing = id != null && preview == PreviewKey(id, false), previewLabel = previewLabel,
                    onSelect = { if (id != null) vm.applyVoice(target, id) else { adding = title; picker.launch(CustomSounds.PICKER_TYPES) } },
                    onPreview = when {
                        v.libraryId != null -> ({ vm.previewLibrary(v.libraryId, false, volume) })
                        own != null -> ({ vm.previewCustom(own.id, volume) })
                        else -> null
                    },
                )
            }
            SettingsDivider()
            SoundRow(stringResource(R.string.voice_random), stringResource(R.string.voice_random_detail), current == SoundIds.RANDOM, false, previewLabel,
                onSelect = { vm.applyVoice(target, SoundIds.RANDOM) }, onPreview = null)
        }

        // The other recordings in the library, from elsewhere in the world.
        val others = lib.adhans.filter { e -> e.id !in VOICE_LIBRARY_IDS && (target == null || e in lib.adhansFor(target)) }
        if (others.isNotEmpty()) SettingsSection(stringResource(R.string.voice_more)) {
            others.forEachIndexed { i, e ->
                if (i > 0) SettingsDivider()
                SoundRow(e.title(ar), adhanDetail(e, !e.hasFullRecording, f, ar), current == e.id, preview == PreviewKey(e.id, !e.hasFullRecording), previewLabel,
                    onSelect = { vm.applyVoice(target, e.id) }, onPreview = { vm.previewLibrary(e.id, !e.hasFullRecording, volume) },
                    badge = if (e.fajr) stringResource(R.string.adhan_badge_fajr) else null)
            }
        }

        // The user's own sounds (the ones not standing for a clock voice).
        val clockOwn = ClockVoice.entries.mapNotNull { it.ownSound(custom)?.id }.toSet()
        val mine = custom.filter { it.id !in clockOwn }
        SettingsSection(stringResource(R.string.adhan_my_sounds)) {
            mine.forEachIndexed { i, cs ->
                if (i > 0) SettingsDivider()
                SoundRow(cs.name, f.duration(cs.seconds), current == cs.id, preview == PreviewKey(cs.id, false), previewLabel,
                    onSelect = { vm.applyVoice(target, cs.id) }, onPreview = { vm.previewCustom(cs.id, volume) })
            }
            if (mine.isNotEmpty()) SettingsDivider()
            val importLabel = stringResource(R.string.adhan_import)
            com.noor.app.ui.settings.SettingsItem(androidx.compose.material.icons.Icons.Rounded.Add, importLabel, null,
                onClick = { adding = ""; picker.launch(CustomSounds.PICKER_TYPES) })
        }
    }
}
