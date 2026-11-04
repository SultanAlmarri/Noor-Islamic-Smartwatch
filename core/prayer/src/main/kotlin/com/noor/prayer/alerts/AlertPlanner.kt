package com.noor.prayer.alerts

import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One alert to schedule: the next occurrence of [prayer] on this device. */
data class PlannedAlert(val prayer: Prayer, val date: LocalDate, val at: Instant, val alert: PrayerAlert)

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
    /** Alerts more than this late are reported quietly rather than played. */
    val MAX_LATENESS: Duration = Duration.ofMinutes(10)
    /** A firing alarm whose prayer time moved by more than this is stale. */
    val TOLERANCE: Duration = Duration.ofSeconds(90)

    /** The next occurrence of every prayer that alerts on [device], strictly after [now], soonest first. */
    fun plan(config: WatchConfig, alerts: AlertSettings, device: AlertDevice, now: Instant): List<PlannedAlert> {
        if (!alerts.enabled || !alerts.device.includes(device)) return emptyList()
        val upcoming = config.events(now, 3).filter { it.at.isAfter(now) }
        return Prayer.entries.filter { alerts.alertsOn(it, device) }.mapNotNull { p ->
            upcoming.firstOrNull { it.prayer == p }?.let { PlannedAlert(p, it.date, it.at, alerts.alert(p)) }
        }.sortedBy { it.at }
    }

    /** Re-checks a firing alarm against the current settings and times. */
    fun onFire(config: WatchConfig, alerts: AlertSettings, device: AlertDevice, prayer: Prayer, date: LocalDate, now: Instant): FireDecision {
        if (!alerts.alertsOn(prayer, device)) return FireDecision.Skip
        val at = config.day(date).instant(prayer) ?: return FireDecision.Skip
        val delta = Duration.between(at, now)
        return when {
            delta < TOLERANCE.negated() -> FireDecision.Skip           // fired far too early: the time has moved later
            delta > MAX_LATENESS -> if (delta > Duration.ofHours(3)) FireDecision.Skip else FireDecision.Late(prayer, date, at, delta)
            else -> FireDecision.Alert(prayer, date, at, alerts.alert(prayer))
        }
    }
}
