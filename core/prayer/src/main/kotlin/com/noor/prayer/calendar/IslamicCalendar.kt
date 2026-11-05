package com.noor.prayer.calendar

import com.noor.prayer.HijriDate
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit

/**
 * Days of the Islamic year that are widely observed. Dates come from the Umm al-Qura calendar (the same one the
 * app shows), shifted by the user's Hijri adjustment; the actual day can follow a local moon-sighting announcement.
 */
enum class IslamicEvent(val month: Int, val day: Int, val en: String, val ar: String, val lastDay: Int = day) {
    ISLAMIC_NEW_YEAR(1, 1, "Islamic New Year", "رأس السنة الهجرية"),
    ASHURA(1, 10, "Day of Ashura", "يوم عاشوراء"),
    RAMADAN(9, 1, "Ramadan begins", "بداية رمضان"),
    EID_AL_FITR(10, 1, "Eid al-Fitr", "عيد الفطر"),
    DAY_OF_ARAFAH(12, 9, "Day of Arafah", "يوم عرفة"),
    EID_AL_ADHA(12, 10, "Eid al-Adha", "عيد الأضحى"),
    /** 13–15 of each month, when many fast; not in Ramadan (already fasting) nor Dhu al-Hijjah (13th is a day of Tashreeq). */
    WHITE_DAYS(0, 13, "White Days", "الأيام البيض", lastDay = 15);

    val monthly: Boolean get() = month == 0
}

data class UpcomingEvent(
    val event: IslamicEvent,
    val date: LocalDate,
    val hijri: HijriDate,
    /** 0 = today. For the White Days, 0 while they are under way. */
    val daysUntil: Long,
    /** Last Gregorian day of the event (the White Days span three days). */
    val endDate: LocalDate = date,
)

object IslamicCalendar {
    /** java.time's Umm al-Qura data covers these Hijri years. */
    private const val MIN_YEAR = 1300
    private const val MAX_YEAR = 1600

    /** The Gregorian date of a Hijri day, with the user's adjustment (the inverse of [HijriDate.of]). */
    fun gregorian(year: Int, month: Int, day: Int, adjustDays: Int = 0): LocalDate? = runCatching {
        LocalDate.from(HijrahDate.of(year, month, day)).minusDays(adjustDays.toLong())
    }.getOrNull()

    /** Days in a Hijri month (29 or 30), or null outside the calendar's range. */
    fun monthLength(year: Int, month: Int): Int? = runCatching { HijrahDate.of(year, month, 1).lengthOfMonth() }.getOrNull()

    /**
     * The next occurrences of the events from [today] on (an event happening today is included), soonest first.
     * Each event appears once; the White Days appear for the next month where they apply.
     */
    fun upcoming(today: LocalDate, adjustDays: Int = 0, limit: Int = IslamicEvent.entries.size): List<UpcomingEvent> {
        val h = HijriDate.of(today, adjustDays)
        if (h.year !in MIN_YEAR..MAX_YEAR - 2) return emptyList()
        val out = ArrayList<UpcomingEvent>()
        for (e in IslamicEvent.entries) {
            val next = if (e.monthly) nextWhiteDays(today, h, adjustDays) else nextYearly(e, today, h.year, adjustDays)
            next?.let(out::add)
        }
        return out.sortedBy { it.date }.take(limit)
    }

    private fun nextYearly(e: IslamicEvent, today: LocalDate, year: Int, adjust: Int): UpcomingEvent? {
        for (y in year..year + 1) {
            val d = gregorian(y, e.month, e.day, adjust) ?: continue
            if (!d.isBefore(today)) return UpcomingEvent(e, d, HijriDate(e.day, e.month, y), ChronoUnit.DAYS.between(today, d))
        }
        return null
    }

    private fun nextWhiteDays(today: LocalDate, h: HijriDate, adjust: Int): UpcomingEvent? {
        var y = h.year; var m = h.month
        repeat(3) {
            if (m != 9 && m != 12) {
                val start = gregorian(y, m, 13, adjust)
                val end = gregorian(y, m, 15, adjust)
                if (start != null && end != null && !end.isBefore(today)) {
                    val until = if (today.isBefore(start)) ChronoUnit.DAYS.between(today, start) else 0L
                    return UpcomingEvent(IslamicEvent.WHITE_DAYS, start, HijriDate(13, m, y), until, endDate = end)
                }
            }
            m += 1; if (m > 12) { m = 1; y += 1 }
        }
        return null
    }
}
