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

/**
 * What the countdown shows at a moment (0.9.7). Before a prayer: the time left, with a minus sign. Once its time has
 * come: the time since it began, with a plus sign, for the usual wait between the Adhan and the iqama, then the next
 * prayer's countdown again. Shared by the home screen and the persistent notification, so they always agree.
 */
data class Countdown(
    /** The prayer counted to (before it) or from (after it). */
    val event: TimedEvent,
    /** True after the prayer's time (+ time since); false before it (− time left). */
    val elapsed: Boolean,
    /** Time left, or time since. Never negative. */
    val duration: Duration,
    /** When this display changes next (the prayer's time, or the end of its "since" period). */
    val changesAt: Instant,
)

object Countdowns {
    /**
     * How long the time since a prayer is shown: about the wait between the Adhan and the iqama in most mosques
     * (around 20 minutes; Fajr longer, Maghrib short), and for Shuruq the quarter of an hour after sunrise before
     * prayer is again allowed. Ends earlier if the next prayer comes first.
     */
    fun sinceWindow(p: Prayer): Duration = Duration.ofMinutes(when (p) {
        Prayer.FAJR -> 25
        Prayer.SHURUQ -> 15
        Prayer.DHUHR -> 20
        Prayer.ASR -> 20
        Prayer.MAGHRIB -> 10
        Prayer.ISHA -> 20
    })

    fun of(state: ScheduleState, now: Instant): Countdown {
        val cur = state.current
        if (cur != null) {
            val end = minOf(cur.at.plus(sinceWindow(cur.prayer)), state.next.at)
            if (now.isBefore(end)) return Countdown(cur, elapsed = true, Duration.between(cur.at, now).coerceAtLeast(Duration.ZERO), end)
        }
        return Countdown(state.next, elapsed = false, Duration.between(now, state.next.at).coerceAtLeast(Duration.ZERO), state.next.at)
    }

    private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this
}
