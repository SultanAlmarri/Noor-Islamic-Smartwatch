package com.noor.prayer.alerts

import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * What an alarm rings with (0.17; 0.18 adds the phone's ringtone and Noor's own sounds: the Adhan voices and the sound
 * effects, each full or short, as the owner asked).
 */
enum class AlarmSound {
    /** The phone's own alarm sound, as the Clock app's alarms ring with (chosen in the phone's sound settings). */
    PHONE_ALARM,
    /** A notification with the phone's notification sound, and nothing to stop: a reminder. */
    NOTIFICATION,
    /** A recording the user added ([PrayerAlarm.customId]). */
    CUSTOM,
    /** 0.18: the phone's ringtone. */
    RINGTONE,
    /** 0.18: one of Noor's sounds ([PrayerAlarm.customId] "adhan.…" or "fx.…"), in full or short ([PrayerAlarm.short]). */
    LIBRARY,
}

/**
 * An alarm set a number of minutes before or after a prayer (0.17), like an alarm in the Clock app, but tied to the
 * prayer's time each day: "25 minutes before Fajr", "10 minutes after Dhuhr". It rings on the phone like an alarm,
 * with Stop and Snooze, on the days chosen, and has its own switch: it does not depend on the Adhan being on.
 */
data class PrayerAlarm(
    /** 1..[MAX], unique among the alarms; the alarm's own slot with Android's alarm service. */
    val id: Int,
    val prayer: Prayer,
    /** 1..[MAX_MINUTES]. */
    val minutes: Int,
    /** False: before the prayer begins; true: after. */
    val after: Boolean,
    val enabled: Boolean = true,
    /** The days it rings on (the prayer's day), one bit a day, Monday the lowest. */
    val days: Int = PrayerAlert.ALL_DAYS,
    /** The user's name for it ("Wake up"), or blank. */
    val label: String = "",
    val sound: AlarmSound = AlarmSound.PHONE_ALARM,
    /** With [AlarmSound.CUSTOM]: the user's recording ("custom.…"); with [AlarmSound.LIBRARY], Noor's sound ("fx.chickadee"). */
    val customId: String? = null,
    val vibrate: Boolean = true,
    /** Minutes Snooze puts it off by; 0 = no Snooze. One of [SNOOZE_CHOICES]. */
    val snoozeMinutes: Int = 10,
    /** 0.18: with [AlarmSound.LIBRARY], the sound's short version (the Adhan's opening, a bird's chirp or two). */
    val short: Boolean = false,
) {
    /** From the prayer's time to the alarm's: negative before it. */
    val offset: Duration get() = Duration.ofMinutes(if (after) minutes.toLong() else -minutes.toLong())

    fun on(day: DayOfWeek): Boolean = days and (1 shl (day.value - 1)) != 0

    fun normalized(): PrayerAlarm = copy(
        minutes = minutes.coerceIn(1, MAX_MINUTES), days = days.coerceIn(0, PrayerAlert.ALL_DAYS),
        // (Not trimmed here: the editor keeps its draft in this form, and a space typed at the end must stay.)
        label = label.replace(Regex("[\\r\\n\\t]"), " ").take(MAX_LABEL),
        customId = customId?.takeIf { SoundIds.isValid(it) && SoundIds.isCustom(it) == (sound == AlarmSound.CUSTOM) && sound.hasId },
        sound = if (sound.hasId && customId?.let { SoundIds.isValid(it) && SoundIds.isCustom(it) == (sound == AlarmSound.CUSTOM) } != true) AlarmSound.PHONE_ALARM else sound,
        short = short && sound == AlarmSound.LIBRARY,
        snoozeMinutes = snoozeMinutes.takeIf { it in SNOOZE_CHOICES } ?: 0,
    )

    /** One entry of the "alarms" field: commas between fields; the label percent-encoded (no , ; | = left in it). */
    fun encode(): String = listOf(
        id, if (enabled) 1 else 0, prayer.name, if (after) "A" else "B", minutes, days, sound.name, customId ?: "",
        if (vibrate) 1 else 0, snoozeMinutes, URLEncoder.encode(label, "UTF-8"), if (short) 1 else 0,
    ).joinToString(",")

    companion object {
        /** At most this many alarms (each holds one of Android's alarms, and another while snoozed). */
        const val MAX = 20
        /** At most 3 hours 59 minutes before or after a prayer (the editor's wheels: hours 0–3, minutes 0–59). */
        const val MAX_MINUTES = 239
        const val MAX_LABEL = 40
        val SNOOZE_CHOICES = listOf(0, 5, 10, 15)

        /** Null for an entry that cannot be read (it is dropped, not guessed at). */
        fun decode(text: String): PrayerAlarm? = runCatching {
            val f = text.split(',')
            PrayerAlarm(
                id = f[0].toInt().takeIf { it in 1..MAX } ?: return null,
                enabled = f[1] == "1",
                prayer = Prayer.entries.firstOrNull { it.name == f[2] } ?: return null,
                after = f[3] == "A",
                minutes = f[4].toInt(),
                days = f[5].toInt(),
                sound = AlarmSound.entries.firstOrNull { it.name == f[6] } ?: AlarmSound.PHONE_ALARM,
                customId = SoundIds.current(f[7].ifEmpty { null }),
                vibrate = f[8] == "1",
                snoozeMinutes = f[9].toInt(),
                label = f.getOrNull(10)?.let { URLDecoder.decode(it, "UTF-8") } ?: "",
                short = f.getOrNull(11) == "1",
            ).normalized()
        }.getOrNull()
    }
}

/** Whether the sound names a file ([PrayerAlarm.customId]). */
val AlarmSound.hasId: Boolean get() = this == AlarmSound.CUSTOM || this == AlarmSound.LIBRARY

/** The next time [alarm] rings: [minutes][PrayerAlarm.minutes] from [prayer][PrayerAlarm.prayer] at [prayerAt] on [date]. */
data class PlannedAlarm(val alarm: PrayerAlarm, val date: LocalDate, val prayerAt: Instant, val at: Instant)

/**
 * When the alarms ring (0.17). Times come from [WatchConfig], as the Adhan's do, so an alarm always keeps its distance
 * from the prayer as the prayer moves through the year.
 */
object AlarmPlanner {
    /** An alarm more than this late (phone off, deep sleep) does not ring: it would wake someone for nothing. */
    val MAX_LATENESS: Duration = Duration.ofMinutes(10)

    /** The next ring of [alarm] strictly after [now] (whether or not it is on), or null when it has none in a week. */
    fun next(config: WatchConfig, alarm: PrayerAlarm, now: Instant): PlannedAlarm? {
        if (alarm.days == 0) return null
        // (From a day back: "30 minutes after Isha" can ring after midnight, on the day after Isha's.)
        return config.events(now, AlertPlanner.DAYS_AHEAD).asSequence()
            .filter { it.prayer == alarm.prayer && alarm.on(it.date.dayOfWeek) }
            .map { PlannedAlarm(alarm, it.date, it.at, it.at.plus(alarm.offset)) }
            .firstOrNull { it.at.isAfter(now) }
    }

    /** The next ring of every alarm that is on, soonest first. */
    fun plan(config: WatchConfig, alerts: AlertSettings, now: Instant): List<PlannedAlarm> =
        alerts.alarms.filter { it.enabled }.mapNotNull { next(config, it, now) }.sortedBy { it.at }

    /**
     * Re-checks an alarm as it fires: null when it was turned off or deleted, its prayer moved (it is armed again at
     * the new time), or it is too late to be of use; otherwise when it is due.
     */
    fun onFire(config: WatchConfig, alerts: AlertSettings, id: Int, date: LocalDate, now: Instant): PlannedAlarm? {
        val alarm = alerts.alarms.firstOrNull { it.id == id && it.enabled } ?: return null
        if (!alarm.on(date.dayOfWeek)) return null
        val prayerAt = config.day(date).instant(alarm.prayer) ?: return null
        val at = prayerAt.plus(alarm.offset)
        val late = Duration.between(at, now)
        if (late < AlertPlanner.TOLERANCE.negated() || late > MAX_LATENESS) return null
        return PlannedAlarm(alarm, date, prayerAt, at)
    }
}
