package com.noor.app.ui.adhan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.CustomSound
import com.noor.alerts.CustomSounds
import com.noor.app.R
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.components.withTick
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
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
internal fun fajrOf(id: String, vm: AdhanViewModel, custom: List<CustomSound>): String = fajrSound(id, vm.library, custom)

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
 * What a prayer plays, chosen in one place (0.26; the owner: "I don't like the way you organized it, like Adhan and then
 * short Adhan and then sound, notification, silent ... there should be a better way"). [target]: that prayer; null:
 * every prayer at once (sunrise keeps its own). The page is a list in four parts, each a kind of thing a person wants:
 * - **Adhan**: Whole Adhan | Opening only (one control for every voice, the opening being "Allahu Akbar, Allahu Akbar"),
 *   then the voices, each heard with its round button, and where it matters (every prayer, Fajr) its Fajr version too,
 *   with "As-salatu khayrun min an-nawm" (the owner: "why can't I hear the Fajr version as well?"); a voice without one
 *   says so. Then Random.
 * - **Alert sounds**: Minhad's short sounds, read from the library (beeps, birds).
 * - **Notification only**: the phone's notification sound, or a silent notice.
 * - **My sounds**: the user's recordings, a voice to add a recording of, and importing.
 * A touch on a row chooses it and plays it as it will sound (a second touch stops it); the chosen one has a gold check.
 * Sunrise, which has no Adhan, has no Adhan part.
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
    // (0.26 review: a voice chosen for every prayer over prayers that differed says so, with Undo.)
    if (target == null) EveryPrayerUndo(vm, snackbar)
    StopPreviewOnLeave(vm)

    val current: Plays? = if (target != null) playsOf(target, alerts.alert(target), lib, custom) else sharedPlays(alerts, lib, custom)
    val adhan = target != Prayer.SHURUQ
    // (Whole or opening: as chosen, or, where nothing plays the Adhan yet, the whole Adhan for the next voice chosen.)
    var opening by rememberSaveable(current) { mutableStateOf((current as? Plays.Adhan)?.opening ?: false) }
    // (At Fajr, and for every prayer, the Fajr version is heard too.)
    val fajrMatters = target == null || target == Prayer.FAJR
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    // (A voice may take two files at once: the Adhan, and one named "… Fajr" for Fajr.)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val name = adding
        if (uris.isNotEmpty() && name != null) { if (name.isEmpty()) uris.forEach { vm.importSound(it, forPrayer = target) } else vm.importVoice(uris, name, target) }
        adding = null
    }
    val title = if (target == null) stringResource(R.string.a026_sound_for_all) else stringResource(R.string.a026_sound_for, f.prayerName(target))

    /** The id a row plays when touched, to show it as playing. */
    fun heard(p: Plays, fajr: Boolean = false): String? = when (p) {
        is Plays.Adhan -> if (fajr || target == Prayer.FAJR) fajrSound(p.voice, lib, custom) else p.voice
        is Plays.Effect -> p.id
        is Plays.Own -> p.id
        Plays.Notification -> SYS_NOTIFICATION
        Plays.Silent -> null
    }
    fun pick(p: Plays) {
        if (p != current) vm.choose(target, p)
        // (Playing already, the touch stops it; otherwise it plays as it will sound.)
        vm.preview(p, target, fajr = target == Prayer.FAJR)
    }

    NoorPage(title, contentPadding, onBack = onBack,
        floating = { NoorSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        if (adhan) SettingsSection(stringResource(R.string.adhan_mode_adhan),
            footer = when (target) { null -> stringResource(R.string.r026_fajr_note_all); Prayer.FAJR -> stringResource(R.string.r026_fajr_note_fajr); else -> null }) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)) {
                Segmented(listOf(false to stringResource(R.string.a026_whole), true to stringResource(R.string.a026_opening)), opening, { o ->
                    opening = o
                    (current as? Plays.Adhan)?.let { vm.choose(target, it.copy(opening = o)) }
                    vm.stopPreview()
                })
                Text(stringResource(if (opening) R.string.a026_opening_note else R.string.a026_whole_note), style = FootnoteStyle, color = NoorTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
            }
            // (Second review of 0.25: only the voices there is a recording of. One Minhad has no licensed recording of
            // (Al-Quds) waits under "My sounds" for the user's own, and is a voice like the others once there is one.)
            // (0.26 review: on Fajr's page the voices with a Fajr version come first; the order is otherwise the owner's.)
            ClockVoice.entries.mapNotNull { v -> v.soundId(vm, custom)?.let { v to it } }
                .sortedBy { (_, id) -> if (target == Prayer.FAJR && hasFajrVersion(id, lib, custom)) 0 else 1 }.forEach { (v, id) ->
                SettingsDivider()
                val choice = Plays.Adhan(id, opening && !SoundIds.isCustom(id))
                val fajrVersion = hasFajrVersion(id, lib, custom)
                VoiceRow(stringResource(v.label), chosen = (current as? Plays.Adhan)?.voice == id, playing = preview?.id == heard(choice),
                    previewLabel = previewLabel, onSelect = { pick(choice) }, onPreview = { vm.preview(choice, target, fajr = target == Prayer.FAJR) }) {
                    // (0.26 review: the gold chip alone says which voices have a Fajr version; "No Fajr version" under
                    // each of the others said it again and again.)
                    if (fajrMatters && fajrVersion) FajrChip(preview?.id == fajrSound(id, lib, custom)) { vm.preview(choice, target, fajr = true) }
                }
            }
            SettingsDivider()
            // (As tall as the voices, with a sign where they have their button: it plays one of them each time.)
            SoundRow(stringResource(R.string.voice_random), stringResource(R.string.voice_random_detail), (current as? Plays.Adhan)?.voice == SoundIds.RANDOM, false, previewLabel,
                onSelect = { vm.stopPreview(); vm.choose(target, Plays.Adhan(SoundIds.RANDOM, opening)) }, onPreview = null, sign = Icons.Rounded.Shuffle)
        }

        // Minhad's short alert sounds, as the library lists them.
        if (lib.sounds.isNotEmpty()) SettingsSection(stringResource(R.string.adhan_cat_sounds), footer = stringResource(if (adhan) R.string.a026_alert_sounds_note else R.string.adhan_sunrise_note)) {
            lib.sounds.forEachIndexed { i, e ->
                if (i > 0) SettingsDivider()
                val choice = Plays.Effect(e.id)
                // (0.26 review: no length under each, "30 s" told nothing a person chooses by; touching one plays it.)
                SoundRow(e.title(ar), null, current == choice, preview?.id == e.id, previewLabel,
                    onSelect = { pick(choice) }, onPreview = { vm.preview(choice, target) })
            }
        }

        // A notification only: with the phone's own sound, or none.
        SettingsSection(stringResource(R.string.a026_notification_only)) {
            SoundRow(stringResource(R.string.a026_notification_sound), stringResource(R.string.adhan_hint_notification), current == Plays.Notification,
                preview?.id == SYS_NOTIFICATION, previewLabel, onSelect = { pick(Plays.Notification) }, onPreview = { vm.preview(Plays.Notification, target) })
            SettingsDivider()
            SoundRow(stringResource(R.string.a026_silent), stringResource(R.string.a026_silent_detail), current == Plays.Silent, false, previewLabel,
                onSelect = { pick(Plays.Silent) }, onPreview = null)
        }

        // The user's own sounds (those not standing for a voice), a voice of the owner's to add a recording of, and
        // adding any other sound.
        val voiceOwn = ClockVoice.entries.flatMap { listOfNotNull(it.ownSound(custom)?.id, it.ownFajr(custom)?.id) }.toSet()
        val mine = custom.filter { it.id !in voiceOwn }
        val missing = if (!adhan) emptyList() else ClockVoice.entries.filter { it.soundId(vm, custom) == null }
        SettingsSection(stringResource(R.string.adhan_my_sounds)) {
            missing.forEach { v ->
                SettingsItem(Icons.Rounded.Add, stringResource(R.string.voice_add_named, stringResource(v.label)), stringResource(R.string.voice_add_named_detail),
                    onClick = { adding = v.names[0]; picker.launch(CustomSounds.PICKER_TYPES) })
                SettingsDivider()
            }
            mine.forEachIndexed { i, cs ->
                if (i > 0) SettingsDivider()
                val choice = Plays.Own(cs.id)
                SoundRow(cs.name, f.duration(cs.seconds), current == choice, preview?.id == cs.id, previewLabel,
                    onSelect = { pick(choice) }, onPreview = { vm.preview(choice, target) })
            }
            if (mine.isNotEmpty()) SettingsDivider()
            SettingsItem(Icons.Rounded.Add, stringResource(R.string.adhan_import), null, onClick = { adding = ""; picker.launch(CustomSounds.PICKER_TYPES) })
        }
    }
}

/**
 * A voice (0.26): a check when chosen, its name, under it what [under] puts there (its Fajr version, or that it has
 * none), and its round play button. A touch on the row chooses it and plays it.
 */
@Composable
private fun VoiceRow(
    title: String, chosen: Boolean, playing: Boolean, previewLabel: String, onSelect: () -> Unit, onPreview: () -> Unit,
    under: @Composable () -> Unit,
) {
    val c = NoorTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.RadioButton, onClick = withTick(onSelect)).semantics { selected = chosen }
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (chosen) Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (chosen) c.goldText else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            under()
        }
        Spacer(Modifier.width(8.dp))
        PreviewButton(playing, previewLabel, onPreview)
    }
}

/** "▶ Fajr version": plays the voice's Fajr recording (with "As-salatu khayrun min an-nawm"); ■ while it plays. */
@Composable
private fun FajrChip(playing: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Row(
        Modifier.padding(top = 6.dp).heightIn(min = 32.dp).clip(RoundedCornerShape(50))
            .background(if (playing) c.goldFill else c.gold.copy(alpha = if (c.isDark) 0.14f else 0.12f))
            .clickable(role = Role.Button, onClick = withTick(onClick)).padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (playing) c.onGold else c.goldText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.a026_fajr_version), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            color = if (playing) c.onGold else c.goldText, maxLines = 1)
    }
}
