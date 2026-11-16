package com.noor.prayer.alerts

import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One alert to schedule: the next occurrence of [prayer] on this device. */
data class PlannedAlert(val prayer: Prayer, val date: LocalDate, val at: Instant, val alert: PrayerAlert)

/**
 * A reminder before [prayer] (due at [at]), to show at [remindAt]; or, with [after] (0.16), a reminder after it began
 * (the iqamah).
 */
data class PlannedReminder(val prayer: Prayer, val date: LocalDate, val at: Instant, val remindAt: Instant, val after: Boolean = false)

/**
 * A reminder that is due now: [minutesLeft] until [prayer] begins at [at]; or, with [after], [minutesLeft] is the
 * minutes since it began.
 */
data class DueReminder(val prayer: Prayer, val date: LocalDate, val at: Instant, val minutesLeft: Int, val after: Boolean = false)

/** What to do when a scheduled alarm fires. */
sealed interface FireDecision {
    /** On time: alert as configured. */
    data class Alert(val prayer: Prayer, val date: LocalDate, val at: Instant, val alert: PrayerAlert) : FireDecision
    /** The alarm was badly delayed (device off, deep sleep): tell the user quietly instead of calling the Adhan late. */
    data class Late(val prayer: Prayer, val date: LocalDate, val at: Instant, val lateBy: Duration) : FireDecision
    /** The time no longer matches (location, authority or offsets changed) or alerts were turned off: do nothing. */
    data object Skip : FireDecision
}

/**
 * Pure planning logic shared by phone and watch: which alarms to set, and whether a firing alarm is still valid.
 * Times always come from [WatchConfig], the same source the screens, tile and complication use.
 */
object AlertPlanner {
    /** How many days ahead alarms are looked for: a week and a day, for prayers that alert on some days only. */
    const val DAYS_AHEAD = 8

    /**
     * 0.16: the next reminder after every prayer that alerts on [device] and has one ([PrayerAlert.afterMinutes], the
     * iqamah), strictly after [now], soonest first. One that would come after the next prayer has begun is left out.
     */
    fun afterReminders(config: WatchConfig, alerts: AlertSettings, device: AlertDevice, now: Instant): List<PlannedReminder> {
        if (!alerts.enabled || !alerts.device.includes(device)) return emptyList()
        val events = config.events(now.minus(Duration.ofHours(1)), DAYS_AHEAD)
        return Prayer.entries.filter { alerts.alertsOn(it, device) && alerts.alert(it).afterMinutes > 0 }.mapNotNull { p ->
            val lag = Duration.ofMinutes(alerts.alert(p).afterMinutes.toLong())
            events.withIndex().firstOrNull { (_, e) -> e.prayer == p && alerts.alert(p).on(e.date.dayOfWeek) && e.at.plus(lag).isAfter(now) }?.let { (i, e) ->
                val remindAt = e.at.plus(lag)
                val next = events.getOrNull(i + 1)?.at
                if (next != null && !remindAt.isBefore(next)) null else PlannedReminder(p, e.date, e.at, remindAt, after = true)
            }
        }.sortedBy { it.remindAt }
    }

    /** Re-checks a firing after-reminder: null when it no longer applies, otherwise the minutes since the prayer began. */
    fun onAfter(config: WatchConfig, alerts: AlertSettings, device: AlertDevice, prayer: Prayer, date: LocalDate, now: Instant): DueReminder? {
        val lag = alerts.alert(prayer).afterMinutes
        if (lag <= 0 || !alerts.alertsOn(prayer, device, date)) return null
        val at = config.day(date).instant(prayer) ?: return null
        val since = Duration.between(at, now)
        // Not before its time (the times moved later), and not long after it (more than half an hour late: no use).
        if (since < Duration.ofMinutes(lag.toLong()).minus(TOLERANCE) || since > Duration.ofMinutes(lag.toLong() + 30L)) return null
        return DueReminder(prayer, date, at, ((since.seconds + 30) / 60).toInt(), after = true)
    }

    /** Alerts more than this late are reported quietly rather than played. */
    val MAX_LATENESS: Duration = Duration.ofMinutes(10)
    /** A firing alarm whose prayer time moved by more than this is stale. */
    val TOLERANCE: Duration = Duration.ofSeconds(90)

    /** The next occurrence of every prayer that alerts on [device], strictly after [now], soonest first. */
    fun plan(config: WatchConfig, alerts: AlertSettings, device: AlertDevice, now: Instant): List<PlannedAlert> {
        if (!alerts.enabled || !alerts.device.includes(device)) return emptyList()
        // (A week and a day ahead: a prayer on one day of the week only still finds its next day.)
        val upcoming = config.events(now, DAYS_AHEAD).filter { it.at.isAfter(now) }
        return Prayer.entries.filter { alerts.alertsOn(it, device) }.mapNotNull { p ->
            upcoming.firstOrNull { it.prayer == p && alerts.alert(p).on(it.date.dayOfWeek) }?.let { PlannedAlert(p, it.date, it.at, alerts.alert(p)) }
        }.sortedBy { it.at }
    }

    /**
     * 0.9.9: the next reminder before every prayer that alerts on [device], strictly after [now], soonest first. A
     * reminder that would come before the previous prayer has begun (short gaps at high latitudes) is left out, so a
     * reminder never interrupts the prayer before it.
     */
    fun reminders(config: WatchConfig, alerts: AlertSettings, device: AlertDevice, now: Instant): List<PlannedReminder> {
        if (!alerts.enabled || !alerts.device.includes(device)) return emptyList()
        val events = config.events(now, DAYS_AHEAD)
        // (0.16: each prayer has its own lead, and its own days.)
        return Prayer.entries.filter { alerts.alertsOn(it, device) && alerts.beforeOf(it) > 0 }.mapNotNull { p ->
            val lead = Duration.ofMinutes(alerts.beforeOf(p).toLong())
            events.withIndex().firstOrNull { (_, e) -> e.prayer == p && alerts.alert(p).on(e.date.dayOfWeek) && e.at.minus(lead).isAfter(now) }?.let { (i, e) ->
                val remindAt = e.at.minus(lead)
                val previous = events.getOrNull(i - 1)?.at
                if (previous != null && remindAt.isBefore(previous)) null else PlannedReminder(p, e.date, e.at, remindAt)
            }
        }.sortedBy { it.remindAt }
    }

    /**
     * Re-checks a firing reminder: null when it no longer applies (turned off, times moved later, or it is so late the
     * prayer itself is about to be called), otherwise the minutes actually left, which the notification states.
     */
    fun onRemind(config: WatchConfig, alerts: AlertSettings, device: AlertDevice, prayer: Prayer, date: LocalDate, now: Instant): DueReminder? {
        val lead = alerts.beforeOf(prayer)
        if (lead <= 0 || !alerts.alertsOn(prayer, device, date)) return null
        val at = config.day(date).instant(prayer) ?: return null
        val remindAt = at.minus(Duration.ofMinutes(lead.toLong()))
        if (now.isBefore(remindAt.minus(TOLERANCE))) return null
        val left = Duration.between(now, at)
        if (left < Duration.ofMinutes(1)) return null
        return DueReminder(prayer, date, at, ((left.seconds + 30) / 60).toInt())
    }

    /** Re-checks a firing alarm against the current settings and times. */
    fun onFire(config: WatchConfig, alerts: AlertSettings, device: AlertDevice, prayer: Prayer, date: LocalDate, now: Instant): FireDecision {
        if (!alerts.alertsOn(prayer, device, date)) return FireDecision.Skip
        val at = config.day(date).instant(prayer) ?: return FireDecision.Skip
        val delta = Duration.between(at, now)
        return when {
            delta < TOLERANCE.negated() -> FireDecision.Skip           // fired far too early: the time has moved later
            delta > MAX_LATENESS -> if (delta > Duration.ofHours(3)) FireDecision.Skip else FireDecision.Late(prayer, date, at, delta)
            else -> FireDecision.Alert(prayer, date, at, alerts.alert(prayer))
        }
    }
}
