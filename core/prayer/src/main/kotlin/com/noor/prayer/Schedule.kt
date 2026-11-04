package com.noor.prayer

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One event on the timeline. */
data class TimedEvent(val prayer: Prayer, val date: LocalDate, val at: Instant)

/** What the dashboard shows at a given moment. */
data class ScheduleState(
    val today: PrayerDay,
    val current: TimedEvent?,     // most recent event that has started (yesterday's Isha before Fajr)
    val next: TimedEvent,         // next event (tomorrow's Fajr after Isha)
    val untilNext: Duration,
    /** 0..1 progress from [current] to [next], for rings and bars. */
    val progress: Float,
)

/** Builds the current/next state from yesterday, today and tomorrow, so midnight never breaks it. */
object Schedule {
    fun state(now: Instant, zone: ZoneId, compute: (LocalDate) -> PrayerDay): ScheduleState {
        val todayDate = now.atZone(zone).toLocalDate()
        val days = listOf(compute(todayDate.minusDays(1)), compute(todayDate), compute(todayDate.plusDays(1)))
        val events = days.flatMap { d -> Prayer.entries.mapNotNull { p -> d.instant(p)?.let { TimedEvent(p, d.date, it) } } }
            .sortedBy { it.at }
        val current = events.lastOrNull { !it.at.isAfter(now) }
        val next = events.first { it.at.isAfter(now) }
        val total = if (current != null) Duration.between(current.at, next.at).seconds.toFloat() else 1f
        val done = if (current != null) Duration.between(current.at, now).seconds.toFloat() else 0f
        return ScheduleState(days[1], current, next, Duration.between(now, next.at), (done / total).coerceIn(0f, 1f))
    }
}
