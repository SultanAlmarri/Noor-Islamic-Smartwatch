package com.noor.prayer.authority

import com.noor.prayer.Prayer
import com.noor.prayer.PrayerDay
import com.noor.prayer.UserAdjustments
import java.time.LocalDate
import java.time.ZoneId

/**
 * An authority's own published times (minutes after local midnight for Fajr, Shuruq, Dhuhr, Asr, Maghrib, Isha),
 * used as published for the dates it covers.
 * Text format (UTF-8): `date,fajr,shuruq,dhuhr,asr,maghrib,isha` per line, 24-hour `HH:MM`; `#` lines are comments.
 */
class Timetable(val id: String, private val days: Map<LocalDate, IntArray>) {
    val first: LocalDate? = days.keys.minOrNull()
    val last: LocalDate? = days.keys.maxOrNull()
    val size: Int get() = days.size

    operator fun contains(date: LocalDate) = date in days

    fun minutes(date: LocalDate): IntArray? = days[date]

    /**
     * 0.22: equal when they hold the same times (the arrays compared by content). Until then a timetable was equal only
     * to itself, so a [com.noor.prayer.WatchConfig] carrying one never equalled the same settings worked out again: the
     * phone re-sent the watch its settings, re-armed its alerts and redrew its notification and widgets at every change
     * of any setting, and the watch took every arrival as new and redrew all its tiles and complications.
     */
    override fun equals(other: Any?): Boolean = other is Timetable && other.id == id && other.days.size == days.size &&
        days.all { (d, m) -> other.days[d]?.contentEquals(m) == true }

    override fun hashCode(): Int = 31 * id.hashCode() + days.entries.sumOf { (d, m) -> d.hashCode() xor m.contentHashCode() }

    /** The published day as a [PrayerDay], with the user's personal minute offsets on top. */
    fun day(date: LocalDate, zone: ZoneId, profileId: String, user: UserAdjustments = UserAdjustments()): PrayerDay? {
        val m = days[date] ?: return null
        val minutes = LinkedHashMap<Prayer, Int?>()
        val seconds = LinkedHashMap<Prayer, Double>()
        Prayer.entries.forEachIndexed { i, p ->
            minutes[p] = m[i] + (user.minuteOffsets[p] ?: 0)
            seconds[p] = m[i] * 60.0
        }
        return PrayerDay(date, zone, profileId, seconds, minutes)
    }

    /** A copy holding only the dates in [from]..[to] (for sending a short window to the watch). */
    fun window(from: LocalDate, to: LocalDate) = Timetable(id, days.filterKeys { it in from..to })

    fun encode(): String = days.toSortedMap().entries.joinToString("\n") { (d, m) ->
        d.toString() + "," + m.joinToString(",") { "%02d:%02d".format(it / 60, it % 60) }
    }

    companion object {
        fun parse(id: String, text: String): Timetable = Timetable(id, text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it[0].isDigit() }
            .mapNotNull { line ->
                runCatching {
                    val c = line.split(',')
                    val values = c.subList(1, 7).map { t -> t.trim().split(':').let { it[0].toInt() * 60 + it[1].toInt() } }
                    require(values.zipWithNext().all { (a, b) -> a < b }) { "times out of order" }
                    LocalDate.parse(c[0].trim()) to values.toIntArray()
                }.getOrNull()
            }.toMap())
    }
}
