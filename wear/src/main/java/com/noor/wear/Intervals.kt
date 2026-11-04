package com.noor.wear

import com.noor.prayer.TimedEvent
import com.noor.prayer.WatchConfig
import java.time.Duration
import java.time.Instant

/** The time between two consecutive events; while it lasts, [next] is the one counted down to. */
data class Interval(val previous: TimedEvent, val next: TimedEvent) {
    val start: Instant get() = previous.at
    val end: Instant get() = next.at
    val length: Duration get() = Duration.between(start, end)
}

object Intervals {
    /**
     * Consecutive intervals from the one containing [now] until [horizon] after it, so a complication or tile
     * can switch to the next prayer on time without the app having to wake up.
     */
    fun upcoming(config: WatchConfig, now: Instant, horizon: Duration = Duration.ofHours(48)): List<Interval> {
        val days = (horizon.toHours() / 24 + 1).toInt()
        val events = config.events(now, days)
        val end = now.plus(horizon)
        return events.zipWithNext { a, b -> Interval(a, b) }
            .filter { it.end > now && it.start < end && it.end > it.start }
    }
}
