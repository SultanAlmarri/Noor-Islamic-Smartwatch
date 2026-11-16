package com.noor.prayer.alerts

import com.noor.prayer.Prayer

/** What happens at a prayer time. */
enum class AlertMode {
    /** The whole Adhan recording. */
    ADHAN,
    /** The opening of the Adhan (the first takbir phrases), about 15–30 seconds. */
    ADHAN_SHORT,
    /** One of Noor's own alert sounds (ticks, chime, bell, birds…). */
    SOUND,
    /** A sound file the user imported. */
    CUSTOM,
    /** A standard notification with the system's notification sound; follows Do Not Disturb like any notification. */
    NOTIFICATION,
    /** A notification on the lock screen and in the shade, with no sound and no vibration. */
    SILENT;

    val playsAudio: Boolean get() = this == ADHAN || this == ADHAN_SHORT || this == SOUND || this == CUSTOM
}

/** Which device alerts. The other device stays quiet, so the user never gets the same alert twice. */
enum class AlertDevice {
    PHONE, WATCH, BOTH;

    fun includes(d: AlertDevice): Boolean = this == BOTH || this == d
}

data class PrayerAlert(
    val enabled: Boolean,
    val mode: AlertMode,
    /** Library id ("adhan.makkah_2013", "alt.soft_chime") or custom id ("custom.<uuid>"); null = the default for [mode]. */
    val soundId: String? = null,
    /** Percent of the device's alarm volume. */
    val volumePercent: Int = 80,
    val vibrate: Boolean = true,
    /** Stop after this many seconds (with a short fade). 0 = play to the end. */
    val maxSeconds: Int = 0,
    /**
     * 0.16: the days of the week it alerts on, one bit a day, Monday the lowest ([ALL_DAYS] = every day). Off on a day
     * means no alert and no reminders for that prayer that day.
     */
    val days: Int = ALL_DAYS,
    /** 0.16: minutes before the prayer for a reminder notification ("Asr in 15 minutes"), 0 = none. One of [BEFORE_CHOICES]. */
    val beforeMinutes: Int = 0,
    /** 0.16: minutes after the prayer for a reminder ("Asr began 20 minutes ago": the iqamah), 0 = none. One of [AFTER_CHOICES]. */
    val afterMinutes: Int = 0,
) {
    /** Whether it alerts on [day]. */
    fun on(day: java.time.DayOfWeek): Boolean = days and (1 shl (day.value - 1)) != 0
    fun normalized(p: Prayer): PrayerAlert {
        // Sunrise is not a prayer: the Adhan is never called for it.
        val m = if (p == Prayer.SHURUQ && (mode == AlertMode.ADHAN || mode == AlertMode.ADHAN_SHORT)) AlertMode.SOUND else mode
        return copy(mode = m, volumePercent = volumePercent.coerceIn(5, 100), maxSeconds = maxSeconds.coerceIn(0, 600),
            soundId = soundId?.takeIf { SoundIds.isValid(it) }, days = days.coerceIn(0, ALL_DAYS),
            beforeMinutes = beforeMinutes.takeIf { it in BEFORE_CHOICES } ?: 0, afterMinutes = afterMinutes.takeIf { it in AFTER_CHOICES } ?: 0)
    }

    companion object {
        /** Every day of the week. */
        const val ALL_DAYS = 0b1111111
        /** The lead times offered before a prayer, in minutes (0 = off). */
        val BEFORE_CHOICES = listOf(0, 5, 10, 15, 20, 30)
        /** The times offered after a prayer (the iqamah), in minutes (0 = off). */
        val AFTER_CHOICES = listOf(0, 5, 10, 15, 20, 25, 30)
    }
}

/**
 * The user's Adhan and notification choices. Shared by phone and watch (the phone sends them inside the watch config),
 * so both devices plan exactly the same alerts.
 */
data class AlertSettings(
    /** Master switch. Off until the user turns alerts on (and grants the notification permission). */
    val enabled: Boolean = false,
    val device: AlertDevice = AlertDevice.PHONE,
    val perPrayer: Map<Prayer, PrayerAlert> = DEFAULTS,
    /** Seconds to rise from quiet to the chosen volume. 0 = start at full volume. */
    val gradualSeconds: Int = 10,
    /**
     * False (the default): the Adhan plays like an alarm clock. The phone being on vibrate or silent does not stop it;
     * under Do Not Disturb the system's own rule for alarms decides. True: while the device is on vibrate, silent or in
     * Do Not Disturb, the alert becomes a silent notification instead.
     */
    val quietWhenSilent: Boolean = false,
    /** Favourite Adhan recordings (library ids), in the order chosen. "Random from favourites" rotates through them. */
    val favourites: List<String> = emptyList(),
    /**
     * 0.9.9: minutes before each prayer for a reminder notification ("Asr in 15 minutes"), 0 = none. One of
     * [REMINDER_CHOICES]. A reminder comes for every prayer that alerts on the device, on that device only.
     */
    val reminderMinutes: Int = 0,
    /**
     * 0.17: alarms a number of minutes before or after a prayer ("25 minutes before Fajr"), each with its own switch.
     * They ring on the phone, whether or not the Adhan is on. Ordered as the user added them.
     */
    val alarms: List<PrayerAlarm> = emptyList(),
) {
    /** [a] added (with the first free id when its id is 0) or put in place of the alarm with its id. */
    fun withAlarm(a: PrayerAlarm): AlertSettings {
        val id = if (a.id in 1..PrayerAlarm.MAX) a.id else nextAlarmId() ?: return this
        val x = a.copy(id = id).normalized()
        return copy(alarms = if (alarms.any { it.id == id }) alarms.map { if (it.id == id) x else it } else alarms + x)
    }

    fun withoutAlarm(id: Int): AlertSettings = copy(alarms = alarms.filterNot { it.id == id })

    /** The id a new alarm gets, or null when there are [PrayerAlarm.MAX] already. */
    fun nextAlarmId(): Int? = (1..PrayerAlarm.MAX).firstOrNull { id -> alarms.none { it.id == id } }

    /**
     * 0.17: the reminders before and after each prayer (0.9.9 and 0.16) become alarms that post a notification, as
     * they did, so nothing the user set is lost and everything of the kind is in one place: the alarms. Their
     * fields are cleared. Settings without any reminder come back unchanged.
     */
    fun remindersAsAlarms(): AlertSettings {
        if (reminderMinutes <= 0 && Prayer.entries.none { alert(it).beforeMinutes > 0 || alert(it).afterMinutes > 0 }) return this
        var s = this
        for (after in listOf(false, true)) for (p in Prayer.entries) {
            val a = alert(p)
            val m = if (after) a.afterMinutes else beforeOf(p)
            // (A prayer whose alert is off never reminded: it gets no alarm.)
            if (m <= 0 || !a.enabled) continue
            s = s.withAlarm(PrayerAlarm(0, p, m, after, enabled = enabled, days = a.days, sound = AlarmSound.NOTIFICATION,
                vibrate = false, snoozeMinutes = 0))
        }
        return Prayer.entries.fold(s.copy(reminderMinutes = 0)) { x, p -> x.with(p, x.alert(p).copy(beforeMinutes = 0, afterMinutes = 0)) }
    }

    fun alert(p: Prayer): PrayerAlert = (perPrayer[p] ?: DEFAULTS.getValue(p)).normalized(p)

    /** True when [p] should produce any alert on [onDevice]. */
    fun alertsOn(p: Prayer, onDevice: AlertDevice): Boolean = enabled && device.includes(onDevice) && alert(p).enabled

    /**
     * The minutes before [p] for its reminder (0.16): its own, or else the one lead set for every prayer before 0.16.
     */
    fun beforeOf(p: Prayer): Int = alert(p).beforeMinutes.takeIf { it > 0 } ?: reminderMinutes

    /** [p]'s reminder lead set to [minutes] (0 = none); the old single lead, if any, becomes each prayer's own first. */
    fun withBefore(p: Prayer, minutes: Int): AlertSettings = materialized().let { s -> s.with(p, s.alert(p).copy(beforeMinutes = minutes)) }

    /** The same reminder lead for every prayer at once (0.16: "for all prayers"). */
    fun withBeforeForAll(minutes: Int): AlertSettings = Prayer.entries.fold(copy(reminderMinutes = 0)) { s, p -> s.with(p, s.alert(p).copy(beforeMinutes = minutes)) }

    private fun materialized(): AlertSettings = if (reminderMinutes <= 0) this
        else Prayer.entries.fold(copy(reminderMinutes = 0)) { s, p -> s.with(p, s.alert(p).copy(beforeMinutes = beforeOf(p))) }

    /** True when [p] should alert on [onDevice] on [date] (its days of the week, 0.16). */
    fun alertsOn(p: Prayer, onDevice: AlertDevice, date: java.time.LocalDate): Boolean = alertsOn(p, onDevice) && alert(p).on(date.dayOfWeek)

    fun with(p: Prayer, a: PrayerAlert) = copy(perPrayer = perPrayer + (p to a.normalized(p)))

    /** Compact single-line form, used in DataStore and inside the watch config. */
    fun encode(): String = buildList {
        add("v=$VERSION"); add("on=${if (enabled) 1 else 0}"); add("dev=${device.name}")
        // "dnd" is the 0.5–0.8 field ("play in Do Not Disturb"), still written so watches on those versions behave the same.
        add("grad=$gradualSeconds"); add("dnd=${if (quietWhenSilent) 0 else 1}"); add("quiet=${if (quietWhenSilent) 1 else 0}")
        if (favourites.isNotEmpty()) add("fav=" + favourites.joinToString(","))
        // Written only when set, so settings without a reminder encode exactly as before (watches up to 0.9.8 ignore it).
        // 0.16: reminders are per prayer; when only those are set, "pre" carries the lead they share, for watches up to
        // 0.9.12 (none if they differ).
        val shared = if (reminderMinutes > 0) reminderMinutes
            else Prayer.entries.filter { alert(it).enabled }.map { alert(it).beforeMinutes }.distinct().singleOrNull() ?: 0
        if (shared > 0) add("pre=$shared")
        Prayer.entries.forEach { p ->
            val a = alert(p)
            // (Days and the reminders before and after are new fields at the end: older readers stop before them.)
            val extra = if (a.days != PrayerAlert.ALL_DAYS || a.beforeMinutes > 0 || a.afterMinutes > 0) ",${a.days},${a.beforeMinutes},${a.afterMinutes}" else ""
            add("${p.name}=${if (a.enabled) 1 else 0},${a.mode.name},${a.soundId ?: ""},${a.volumePercent},${if (a.vibrate) 1 else 0},${a.maxSeconds}$extra")
        }
        // 0.17: a new field, written only when there are alarms (older readers ignore it).
        if (alarms.isNotEmpty()) add("alarms=" + alarms.joinToString("|") { it.encode() })
    }.joinToString(";")

    companion object {
        const val VERSION = 1

        /** The reminder lead times offered, in minutes (0 = off). Short enough to end before the next prayer anywhere. */
        val REMINDER_CHOICES = listOf(0, 5, 10, 15, 20, 30)

        val DEFAULTS: Map<Prayer, PrayerAlert> = Prayer.entries.associateWith { p ->
            when (p) {
                Prayer.FAJR -> PrayerAlert(true, AlertMode.ADHAN, SoundIds.DEFAULT_FAJR_ADHAN)
                Prayer.SHURUQ -> PrayerAlert(false, AlertMode.NOTIFICATION, null, vibrate = false)
                else -> PrayerAlert(true, AlertMode.ADHAN, SoundIds.DEFAULT_ADHAN)
            }
        }

        /** Unknown or damaged fields fall back to defaults field by field; never throws. */
        fun decode(text: String?): AlertSettings {
            if (text.isNullOrBlank()) return AlertSettings()
            val m = text.split(';').mapNotNull { f -> f.indexOf('=').takeIf { it > 0 }?.let { f.substring(0, it) to f.substring(it + 1) } }.toMap()
            // (Before 0.16 one reminder lead served every prayer: it becomes each prayer's own.)
            val pre = m["pre"]?.toIntOrNull()?.takeIf { it in REMINDER_CHOICES } ?: 0
            val per = Prayer.entries.associateWith { p ->
                val d = DEFAULTS.getValue(p)
                val f = m[p.name]?.split(',') ?: return@associateWith d
                PrayerAlert(
                    enabled = f.getOrNull(0)?.let { it == "1" } ?: d.enabled,
                    mode = f.getOrNull(1)?.let { n -> AlertMode.entries.firstOrNull { it.name == n } } ?: d.mode,
                    soundId = f.getOrNull(2)?.takeIf { it.isNotEmpty() },
                    volumePercent = f.getOrNull(3)?.toIntOrNull() ?: d.volumePercent,
                    vibrate = f.getOrNull(4)?.let { it == "1" } ?: d.vibrate,
                    maxSeconds = f.getOrNull(5)?.toIntOrNull() ?: d.maxSeconds,
                    days = f.getOrNull(6)?.toIntOrNull() ?: PrayerAlert.ALL_DAYS,
                    beforeMinutes = f.getOrNull(7)?.toIntOrNull() ?: 0,
                    afterMinutes = f.getOrNull(8)?.toIntOrNull() ?: 0,
                ).normalized(p)
            }
            return AlertSettings(
                enabled = m["on"] == "1",
                device = m["dev"]?.let { n -> AlertDevice.entries.firstOrNull { it.name == n } } ?: AlertDevice.PHONE,
                perPrayer = per,
                gradualSeconds = (m["grad"]?.toIntOrNull() ?: 10).coerceIn(0, 60),
                // Versions before 0.9 had only "dnd", off by default, which silenced the Adhan on vibrate: that default was
                // the main reason Adhans did not sound, so it is not carried over. Only an explicit "quiet" choice is kept.
                quietWhenSilent = m["quiet"] == "1",
                favourites = m["fav"]?.split(',')?.filter { SoundIds.isValid(it) && !SoundIds.isCustom(it) }?.distinct().orEmpty(),
                // ("pre" alone, as before 0.16: the lead for every prayer. Written beside per-prayer leads only for old
                // watches: then the prayers' own leads are what counts.)
                reminderMinutes = if (Prayer.entries.any { (m[it.name]?.split(',')?.size ?: 0) > 6 }) 0 else pre,
                alarms = m["alarms"]?.split('|')?.mapNotNull { PrayerAlarm.decode(it) }?.distinctBy { it.id }?.take(PrayerAlarm.MAX).orEmpty(),
            )
        }
    }
}

/** Ids of the bundled sounds the defaults refer to, and the id format shared with custom sounds. */
object SoundIds {
    const val DEFAULT_ADHAN = "adhan.makkah_maghrib_2012"
    const val DEFAULT_FAJR_ADHAN = "adhan.malmo_fajr_2012"
    const val DEFAULT_SOUND = "alt.soft_chime"
    const val CUSTOM_PREFIX = "custom."
    /** Not a recording: "a different favourite each time" (see [RandomAdhan]). */
    const val RANDOM = "random.favourites"

    private val pattern = Regex("^[a-z0-9_]+\\.[a-z0-9_-]{1,64}$")
    fun isValid(id: String): Boolean = pattern.matches(id)
    fun isCustom(id: String?): Boolean = id != null && id.startsWith(CUSTOM_PREFIX)
}

/**
 * "Random from favourites" (0.9.5): which favourite Adhan plays for a prayer on a date. Deterministic, so the phone and
 * the watch (and the alarm and the notification) always agree without talking to each other, and fair: every eligible
 * favourite plays once before any plays again, and a round never starts with the one that ended the previous round.
 *
 * Fajr has its own rotation over the favourites recorded for Fajr (with "As-salatu khayrun min an-nawm"), or over the
 * others when none of the favourites is a Fajr recording; the other four prayers rotate through the favourites that are
 * not Fajr recordings (a Fajr Adhan is never played at another prayer).
 */
object RandomAdhan {
    fun pick(favourites: List<String>, prayer: com.noor.prayer.Prayer, date: java.time.LocalDate, isFajrRecording: (String) -> Boolean): String? {
        val fajr = favourites.filter(isFajrRecording)
        val general = favourites.filterNot(isFajrRecording)
        val (pool, n) = when (prayer) {
            com.noor.prayer.Prayer.FAJR -> (fajr.ifEmpty { general }) to date.toEpochDay()
            com.noor.prayer.Prayer.SHURUQ -> return null
            else -> general to date.toEpochDay() * 4 + listOf(com.noor.prayer.Prayer.DHUHR, com.noor.prayer.Prayer.ASR,
                com.noor.prayer.Prayer.MAGHRIB, com.noor.prayer.Prayer.ISHA).indexOf(prayer)
        }
        if (pool.isEmpty()) return null
        val k = pool.size.toLong()
        val round = Math.floorDiv(n, k)
        val pos = Math.floorMod(n, k).toInt()
        return order(pool, round)[pos]
    }

    /** The shuffled order of one round; it never begins with the previous round's last pick. */
    private fun order(pool: List<String>, round: Long): List<String> {
        val sorted = pool.distinct().sorted()
        fun shuffled(r: Long) = sorted.shuffled(java.util.Random(r * 0x9E3779B97F4A7C15uL.toLong() + 17))
        val cur = shuffled(round).toMutableList()
        if (cur.size > 1 && cur.first() == shuffled(round - 1).last()) { val t = cur[0]; cur[0] = cur[1]; cur[1] = t }
        return cur
    }
}

