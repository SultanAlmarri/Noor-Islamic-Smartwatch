package com.noor.wear.tile

import com.noor.wear.Interval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 0.25: the Minhad tile's timeline. Its top line names the next prayer, so the tile changes at each prayer as well as
 * at midnight (when its Hijri date and the day's tasbih count change). A stretch is a time during which neither does.
 */
object ToolsTimeline {
    /** From [startMillis] (null: from now on) to [endMillis], the tile shows [date] and [next] (null: nothing set up). */
    class Stretch(val startMillis: Long?, val endMillis: Long, val date: LocalDate, val next: Interval?)

    /**
     * Stretches from [now] to the start of the day after tomorrow at the place: [intervals] (consecutive, the first
     * containing [now]) cut at each midnight. Without intervals, the two days alone, as before 0.25.
     */
    fun stretches(intervals: List<Interval>, now: Instant, zone: ZoneId): List<Stretch> {
        val today = now.atZone(zone).toLocalDate()
        val horizon = today.plusDays(2).atStartOfDay(zone).toInstant()
        val out = ArrayList<Stretch>()
        var from = now
        for (iv in intervals) {
            if (!iv.end.isAfter(from)) continue
            if (!from.isBefore(horizon)) break
            val end = if (iv.end.isBefore(horizon)) iv.end else horizon
            while (from.isBefore(end)) {
                val date = from.atZone(zone).toLocalDate()
                val midnight = date.plusDays(1).atStartOfDay(zone).toInstant()
                val to = if (midnight.isBefore(end)) midnight else end
                out += Stretch(if (out.isEmpty()) null else from.toEpochMilli(), to.toEpochMilli(), date, iv)
                from = to
            }
        }
        if (out.isEmpty()) {
            val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return listOf(Stretch(null, midnight, today, null), Stretch(midnight, midnight + 86_400_000L, today.plusDays(1), null))
        }
        return out
    }
}
