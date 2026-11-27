package com.noor.app.ui.adhan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.noor.alerts.CustomSound
import com.noor.alerts.SoundLibrary
import com.noor.app.R
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlert
import com.noor.prayer.alerts.SoundIds

/**
 * 0.26: what a prayer plays, as a person thinks of it (the owner, 27 November 2026: "I don't like the way you
 * organized it, like Adhan and then short Adhan and then sound, notification, silent"). Four kinds: the Adhan in one of
 * the voices, whole or only its opening; a short alert sound; one of the user's own recordings; a notification only,
 * with the phone's sound or none. Underneath, the stored [AlertMode]s are unchanged (ADHAN, ADHAN_SHORT, SOUND,
 * CUSTOM, NOTIFICATION, SILENT): [playsOf] reads them, [PrayerAlert.playing] writes them, so settings saved by any
 * earlier version keep working and show as one of these.
 */
internal sealed interface Plays {
    /**
     * The Adhan. [voice]: the voice's own sound (a library recording, the user's recording standing for a voice, or
     * [SoundIds.RANDOM]); at Fajr the voice's Fajr recording plays when it has one. [opening]: only its opening, "Allahu
     * Akbar, Allahu Akbar".
     */
    data class Adhan(val voice: String, val opening: Boolean) : Plays
    /** One of Minhad's short alert sounds (beeps, birds…). */
    data class Effect(val id: String) : Plays
    /** One of the user's own recordings that does not stand for a voice. */
    data class Own(val id: String) : Plays
    /** A notification with the phone's notification sound. */
    data object Notification : Plays
    /** A notification with no sound. */
    data object Silent : Plays
}

/** The voice a recording belongs to: a voice's Fajr recording ("adhan.dubai_fajr") counts as the voice ("adhan.dubai"). */
internal fun voiceSound(id: String?, lib: SoundLibrary, custom: List<CustomSound>): String? {
    if (id == null) return null
    if (id == SoundIds.RANDOM) return id
    ClockVoice.entries.firstOrNull { v -> v.ownFajr(custom)?.id == id }?.let { v -> return v.ownSound(custom)?.id ?: id }
    if (lib.byId(id)?.fajr == true) id.removeSuffix("_fajr").takeIf { it != id && lib.byId(it) != null }?.let { return it }
    return id
}

/** What [a] plays at [p]. */
internal fun playsOf(p: Prayer, a: PrayerAlert, lib: SoundLibrary, custom: List<CustomSound>): Plays = when (a.mode) {
    AlertMode.ADHAN, AlertMode.ADHAN_SHORT ->
        if (p == Prayer.SHURUQ) Plays.Effect(a.soundId ?: SoundIds.DEFAULT_SOUND)
        else Plays.Adhan(voiceSound(a.soundId ?: if (p == Prayer.FAJR) SoundIds.DEFAULT_FAJR_ADHAN else SoundIds.DEFAULT_ADHAN, lib, custom)!!, a.mode == AlertMode.ADHAN_SHORT)
    AlertMode.CUSTOM -> {
        val id = a.soundId
        // (The user's recording that stands for a voice is that voice's Adhan; any other is a recording of theirs.)
        if (p != Prayer.SHURUQ && id != null && ClockVoice.entries.any { v -> v.ownSound(custom)?.id == id || v.ownFajr(custom)?.id == id })
            Plays.Adhan(voiceSound(id, lib, custom)!!, false)
        else Plays.Own(id ?: "")
    }
    AlertMode.SOUND -> Plays.Effect(a.soundId ?: SoundIds.DEFAULT_SOUND)
    AlertMode.NOTIFICATION -> Plays.Notification
    AlertMode.SILENT -> Plays.Silent
}

/** The recording a voice plays at Fajr (its Fajr recording, or itself when it has none). */
internal fun fajrSound(voice: String, lib: SoundLibrary, custom: List<CustomSound>): String =
    if (SoundIds.isCustom(voice)) ClockVoice.entries.firstOrNull { it.ownSound(custom)?.id == voice }?.ownFajr(custom)?.id ?: voice
    else lib.byId(voice + "_fajr")?.id ?: voice

/** Whether [voice] has a Fajr recording of its own (with "As-salatu khayrun min an-nawm"). */
internal fun hasFajrVersion(voice: String, lib: SoundLibrary, custom: List<CustomSound>): Boolean =
    voice != SoundIds.RANDOM && fajrSound(voice, lib, custom) != voice

/**
 * [this] alert set to play [choice] at [p], keeping everything else (its switch, volume, days…). [forAll]: chosen for
 * every prayer at once, where a voice without a Fajr recording leaves Fajr on the Fajr Adhan it has (never a plain Adhan
 * at Fajr by a choice made for all).
 */
internal fun PrayerAlert.playing(p: Prayer, choice: Plays, lib: SoundLibrary, custom: List<CustomSound>, forAll: Boolean): PrayerAlert = when (choice) {
    is Plays.Adhan -> {
        val own = if (p == Prayer.FAJR) fajrSound(choice.voice, lib, custom) else choice.voice
        val keepFajr = p == Prayer.FAJR && forAll && own == choice.voice && choice.voice != SoundIds.RANDOM &&
            (lib.byId(soundId)?.fajr == true || ClockVoice.entries.any { it.ownFajr(custom)?.id == soundId })
        val id = if (keepFajr) soundId!! else own
        val entry = lib.byId(id)
        val mode = when {
            SoundIds.isCustom(id) -> AlertMode.CUSTOM
            choice.opening -> AlertMode.ADHAN_SHORT
            entry != null && !entry.hasFullRecording -> AlertMode.ADHAN_SHORT
            else -> AlertMode.ADHAN
        }
        copy(mode = mode, soundId = id)
    }
    is Plays.Effect -> copy(mode = AlertMode.SOUND, soundId = choice.id)
    is Plays.Own -> copy(mode = AlertMode.CUSTOM, soundId = choice.id)
    Plays.Notification -> copy(mode = AlertMode.NOTIFICATION)
    Plays.Silent -> copy(mode = AlertMode.SILENT)
}

/** The prayers a choice "for every prayer" sets: the five prayers (sunrise has no Adhan, and keeps its own). */
internal val FIVE_PRAYERS = Prayer.entries.filter { it != Prayer.SHURUQ }

/**
 * What the five prayers share, or null when they differ. Fajr counts as sharing a voice when it plays that voice's Fajr
 * recording, or, for a voice without one, when it is on a Fajr Adhan (which a choice for all leaves it on).
 */
internal fun sharedPlays(a: AlertSettings, lib: SoundLibrary, custom: List<CustomSound>): Plays? {
    val others = FIVE_PRAYERS.filter { it != Prayer.FAJR }.map { playsOf(it, a.alert(it), lib, custom) }.distinct().singleOrNull() ?: return null
    val fajr = playsOf(Prayer.FAJR, a.alert(Prayer.FAJR), lib, custom)
    if (fajr == others) return others
    if (others is Plays.Adhan && fajr is Plays.Adhan && fajr.opening == others.opening && !hasFajrVersion(others.voice, lib, custom) &&
        a.alert(Prayer.FAJR).soundId.let { id -> lib.byId(id)?.fajr == true || ClockVoice.entries.any { it.ownFajr(custom)?.id == id } }) return others
    return null
}

/** The name of a voice's sound ("Makkah", "Random", the user's recording's name). */
@Composable
internal fun voiceLabel(voice: String, lib: SoundLibrary, custom: List<CustomSound>, ar: Boolean): String = when {
    voice == SoundIds.RANDOM -> stringResource(R.string.voice_random)
    else -> ClockVoice.entries.firstOrNull { v -> v.libraryId == voice && lib.byId(voice) != null || v.ownSound(custom)?.id == voice }?.let { stringResource(it.label) }
        ?: lib.byId(voice)?.title(ar) ?: custom.firstOrNull { it.id == voice }?.name ?: voice
}

/**
 * [plays] in a few words, as facts for a line ("Adhan", "Makkah"; "Adhan opening", "Dubai"; "Birds"; "Notification";
 * "Silent notice"). [p]: the prayer it is for, so that Fajr's line names the recording it really plays.
 */
@Composable
internal fun playsFacts(plays: Plays, lib: SoundLibrary, custom: List<CustomSound>, ar: Boolean): List<String> = when (plays) {
    is Plays.Adhan -> listOf(stringResource(if (plays.opening) R.string.adhan_mode_short else R.string.adhan_mode_adhan), voiceLabel(plays.voice, lib, custom, ar))
    is Plays.Effect -> listOf(lib.byId(plays.id)?.title(ar) ?: stringResource(R.string.adhan_cat_sounds))
    is Plays.Own -> listOf(custom.firstOrNull { it.id == plays.id }?.name ?: stringResource(R.string.adhan_mode_custom))
    Plays.Notification -> listOf(stringResource(R.string.adhan_mode_notification))
    Plays.Silent -> listOf(stringResource(R.string.a026_silent))
}

/**
 * What [a] plays at [p], as facts for a line. At Fajr a Fajr recording is named so ("Fajr Adhan · Dubai"): beside the
 * other prayers' "Adhan · Makkah" it says why Fajr's voice differs (Makkah has no Fajr recording).
 */
@Composable
internal fun prayerFacts(p: Prayer, a: PrayerAlert, lib: SoundLibrary, custom: List<CustomSound>, ar: Boolean): List<String> {
    val plays = playsOf(p, a, lib, custom)
    val facts = playsFacts(plays, lib, custom, ar)
    val fajrRecording = lib.byId(a.soundId)?.fajr == true || ClockVoice.entries.any { it.ownFajr(custom)?.id == a.soundId }
    return if (p == Prayer.FAJR && plays is Plays.Adhan && !plays.opening && fajrRecording) listOf(stringResource(R.string.a026_fajr_adhan)) + facts.drop(1) else facts
}
