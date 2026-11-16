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
    val ids = Prayer.entries.filter { it != Prayer.SHURUQ && a.alert(it).mode in VOICE_MODES }.map { p ->
        val id = a.alert(p).soundId
        voiceOf(id, vm, custom)?.soundId(vm, custom) ?: id
    }.distinct()
    return ids.singleOrNull()
}

/**
 * 0.18: the Adhan's voice for every prayer at once, first on the Adhan page (the owner: "Why do I have to go to every
 * individual prayer time and select the same Adhan?"): six tiles, the chosen one lit; a touch chooses it for every
 * prayer and plays its opening (a second touch stops it). A voice Noor has no recording of asks for the user's own.
 * Under them, the volume of every prayer's Adhan, as one slider.
 */
@Composable
internal fun VoiceCard(a: AlertSettings, vm: AdhanViewModel, custom: List<CustomSound>, f: Formatter, onAdd: (String) -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    val preview by vm.preview.collectAsStateWithLifecycle()
    val shared = sharedVoice(a, vm, custom)
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp, corner = 26.dp) {
        Text(stringResource(R.string.voice_all_title), style = MaterialTheme.typography.titleSmall, color = c.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        val tiles: List<Pair<ClockVoice?, String?>> = ClockVoice.entries.map { it to it.soundId(vm, custom) } + (null to SoundIds.RANDOM)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (v, id) ->
                        val on = id != null && id == shared
                        val shape = RoundedCornerShape(16.dp)
                        val title = v?.let { stringResource(it.label) } ?: stringResource(R.string.voice_random)
                        Column(
                            Modifier.weight(1f).heightIn(min = 64.dp).clip(shape)
                                .background(if (on) c.gold.copy(alpha = if (c.isDark) 0.18f else 0.14f) else c.glass)
                                .border(if (on) 1.5.dp else 0.75.dp, if (on) c.gold else c.glassEdge.copy(alpha = c.glassEdge.alpha * 0.6f), shape)
                                .clickable(role = Role.RadioButton) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when {
                                        v != null && id == null -> onAdd(stringResource0(v))
                                        id == SoundIds.RANDOM -> vm.applyVoice(null, id)
                                        id != null -> {
                                            if (!on) vm.applyVoice(null, id)
                                            if (SoundIds.isCustom(id)) vm.previewCustom(id, 80) else vm.previewLibrary(id, short = true, volumePercent = 80)
                                        }
                                    }
                                }
                                .semantics { selected = on }.padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    v == null -> Icon(Icons.Rounded.Shuffle, null, tint = if (on) c.gold else c.textSecondary, modifier = Modifier.size(15.dp).padding(end = 2.dp))
                                    id == null -> Icon(Icons.Rounded.Add, null, tint = c.textSecondary, modifier = Modifier.size(15.dp).padding(end = 2.dp))
                                    preview?.id == id -> Icon(Icons.Rounded.GraphicEq, null, tint = c.gold, modifier = Modifier.size(15.dp).padding(end = 2.dp))
                                }
                                Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                                    color = if (on) c.goldText else if (id == null) c.textSecondary else c.textPrimary, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        // One volume for every prayer's Adhan (the prayers' own volumes all move with it).
        val volumes = Prayer.entries.filter { a.alert(it).enabled && a.alert(it).mode.playsAudio }.map { a.alert(it).volumePercent }
        var vol by remember(volumes) { mutableFloatStateOf((volumes.takeIf { it.isNotEmpty() }?.average() ?: 80.0).toFloat()) }
        val label = stringResource(R.string.adhan_volume_all)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Slider(value = vol, valueRange = 5f..100f, modifier = Modifier.weight(1f).semantics { contentDescription = label },
                onValueChange = { v -> if ((v / 10).toInt() != (vol / 10).toInt()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vol = v },
                onValueChangeFinished = { vm.setVolumeAll(vol.toInt()) },
                colors = SliderDefaults.colors(thumbColor = c.gold, activeTrackColor = c.gold, inactiveTrackColor = c.textMuted.copy(alpha = 0.25f)))
            Spacer(Modifier.width(10.dp))
            Text(f.number(vol.toInt()) + if (f.arabic) "٪" else "%", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
        }
    }
}

// (A voice's English name, for the file the user adds for it; the names in [ClockVoice.names] match it either way.)
private fun stringResource0(v: ClockVoice): String = v.names[0]

/**
 * One prayer's voice (0.16; 0.18: only the five voices and Random, the user's own recordings, and, for a prayer set to a
 * sound, the sound effects), each heard before it is chosen. [target] null: every prayer at once.
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

    NoorPage(title, contentPadding, onBack = onBack,
        floating = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        if (effects) SettingsSection("") {
            lib.sounds.forEachIndexed { i, e ->
                if (i > 0) SettingsDivider()
                SoundRow(e.title(ar), f.duration(e.seconds), current == e.id, preview == PreviewKey(e.id, false), previewLabel,
                    onSelect = { vm.setPrayer(target!!) { it.copy(mode = AlertMode.SOUND, soundId = e.id) } }, onPreview = { vm.previewLibrary(e.id, false, volume) })
            }
        } else SettingsSection("") {
            ClockVoice.entries.forEachIndexed { i, v ->
                if (i > 0) SettingsDivider()
                val id = v.soundId(vm, custom)
                // (At Fajr, the voice's own Fajr recording when it has one.)
                val play = if (target == Prayer.FAJR) id?.let { fajrOf(it, vm, custom) } else id
                val name = stringResource(v.label)
                SoundRow(name, if (id == null) stringResource(R.string.voice_add) else null,
                    selected = id != null && (current == id || current == play), playing = play != null && preview?.id == play, previewLabel = previewLabel,
                    onSelect = { if (id != null) vm.applyVoice(target, id) else { adding = v.names[0]; picker.launch(CustomSounds.PICKER_TYPES) } },
                    onPreview = play?.let { p -> { if (SoundIds.isCustom(p)) vm.previewCustom(p, volume) else vm.previewLibrary(p, false, volume) } })
            }
            SettingsDivider()
            SoundRow(stringResource(R.string.voice_random), null, current == SoundIds.RANDOM, false, previewLabel,
                onSelect = { vm.applyVoice(target, SoundIds.RANDOM) }, onPreview = null)
        }

        // The user's own sounds (those not standing for a voice), and adding one.
        val voiceOwn = ClockVoice.entries.flatMap { listOfNotNull(it.ownSound(custom)?.id, it.ownFajr(custom)?.id) }.toSet()
        val mine = custom.filter { it.id !in voiceOwn }
        SettingsSection(stringResource(R.string.adhan_my_sounds)) {
            mine.forEachIndexed { i, cs ->
                if (i > 0) SettingsDivider()
                SoundRow(cs.name, f.duration(cs.seconds), current == cs.id, preview == PreviewKey(cs.id, false), previewLabel,
                    onSelect = { if (target == Prayer.SHURUQ) vm.setPrayer(target) { it.copy(mode = AlertMode.CUSTOM, soundId = cs.id) } else vm.applyVoice(target, cs.id) },
                    onPreview = { vm.previewCustom(cs.id, volume) })
            }
            if (mine.isNotEmpty()) SettingsDivider()
            SettingsItem(Icons.Rounded.Add, stringResource(R.string.adhan_import), null, onClick = { adding = ""; picker.launch(CustomSounds.PICKER_TYPES) })
        }
    }
}
