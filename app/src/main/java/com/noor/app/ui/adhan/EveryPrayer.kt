package com.noor.app.ui.adhan

import com.noor.alerts.CustomSound
import com.noor.alerts.SoundLibrary
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertSettings

/**
 * 0.26 review: what the "Every prayer" rows show when the prayers differ, and the Undo of a change made there.
 *
 * The review of the 0.26 renders: "Volume says Mixed but the slider shows a position; Start softly / Vibrate say On for
 * some prayers while the switch looks off; one touch overwrites six settings silently." So a row that sets several
 * prayers knows three states, not two ([Shared]); its control shows the third as its own (a switch with its thumb in
 * the middle, a slider with no thumb); and a change that overwrites prayers that differed says so once, with Undo,
 * which gives every prayer back what it had ([undo]).
 *
 * Kept free of Compose so that the unit tests read it (`EveryPrayerTest`).
 */
internal enum class Shared { ON, OFF, MIXED }

/** What a row "for every prayer" sets. */
internal enum class EverySetting { SOUND, VOLUME, SOFT_START, VIBRATE }

/** The prayers a change of [what] for every prayer sets: the sound the five prayers (sunrise has no Adhan); the rest all six. */
internal fun everyTargets(what: EverySetting): List<Prayer> = if (what == EverySetting.SOUND) FIVE_PRAYERS else Prayer.entries

/**
 * The prayers whose volume, rise and vibration the rows for every prayer show: those that play a sound and will sound
 * (switched on, with the alerts on); when none will, those that would. A prayer on a notification has no volume of
 * Minhad's, so it does not make the volume "Mixed".
 */
internal fun AlertSettings.shown(target: Prayer? = null): List<Prayer> {
    val audible = (target?.let(::listOf) ?: Prayer.entries).filter { alert(it).mode.playsAudio }
    return audible.filter { enabled && alert(it).enabled }.ifEmpty { audible }
}

/** The volume the prayers share, or null when they differ (or none plays a sound). */
internal fun AlertSettings.sharedVolume(target: Prayer? = null): Int? = shown(target).map { alert(it).volumePercent }.distinct().singleOrNull()

/** Whether the prayers start softly: all of them, none, or some ([Shared.MIXED]). */
internal fun AlertSettings.sharedSoftStart(target: Prayer? = null): Shared = tri(shown(target).map { alert(it).gradualSeconds > 0 })

/** Whether the prayers vibrate: all of them, none, or some. */
internal fun AlertSettings.sharedVibrate(target: Prayer? = null): Shared = tri(shown(target).map { alert(it).vibrate })

private fun tri(values: List<Boolean>): Shared = when {
    values.isEmpty() || values.none { it } -> Shared.OFF
    values.all { it } -> Shared.ON
    else -> Shared.MIXED
}

/**
 * Whether the rows for every prayer show [what] as differing now, so that a change made there overwrites prayers that
 * had their own. (The sound: when the five prayers do not share what they play, as the Sound row's "Mixed" says.)
 */
internal fun AlertSettings.differIn(what: EverySetting, lib: SoundLibrary, custom: List<CustomSound>): Boolean = when (what) {
    EverySetting.SOUND -> sharedPlays(this, lib, custom) == null
    EverySetting.VOLUME -> shown().map { alert(it).volumePercent }.distinct().size > 1
    EverySetting.SOFT_START -> sharedSoftStart() == Shared.MIXED
    EverySetting.VIBRATE -> sharedVibrate() == Shared.MIXED
}

/** A change for every prayer that overwrote prayers that differed: what it set, and the settings just before it. */
internal data class EveryUndo(val what: EverySetting, val before: AlertSettings)

/**
 * [this] with what [u] changed given back to every prayer it set, as it was before, and nothing else touched (a prayer
 * switched on or off since, an alarm, the device: kept as they are now).
 */
internal fun AlertSettings.undo(u: EveryUndo): AlertSettings {
    val back = withEach(everyTargets(u.what)) { p, now ->
        val old = u.before.alert(p)
        when (u.what) {
            EverySetting.SOUND -> now.copy(mode = old.mode, soundId = old.soundId)
            EverySetting.VOLUME -> now.copy(volumePercent = old.volumePercent)
            EverySetting.SOFT_START -> now.copy(gradualSeconds = old.gradualSeconds)
            EverySetting.VIBRATE -> now.copy(vibrate = old.vibrate)
        }
    }
    // (Random sets the voices it chooses from: given back too.)
    return if (u.what == EverySetting.SOUND) back.copy(favourites = u.before.favourites) else back
}
