package com.noor.prayer

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** Hijri date from the Umm al-Qura calendar built into java.time, with a user day adjustment. */
data class HijriDate(val day: Int, val month: Int, val year: Int) {
    val monthNameEn: String get() = MONTHS_EN[month - 1]
    val monthNameAr: String get() = MONTHS_AR[month - 1]
    val isRamadan: Boolean get() = month == 9

    companion object {
        val MONTHS_EN = listOf(
            "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
        )
        val MONTHS_AR = listOf(
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
        )

        /** [adjustDays] shifts the result (-2..+2) to follow local moon-sighting announcements. */
        fun of(date: LocalDate, adjustDays: Int = 0): HijriDate {
            val h = HijrahDate.from(date.plusDays(adjustDays.toLong()))
            return HijriDate(h.get(ChronoField.DAY_OF_MONTH), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.YEAR))
        }
    }
}

/**
 * One Hijri month (Umm al-Qura), for browsing the calendar by Hijri month (0.9.4). With the user's day adjustment it
 * maps each of its days to the Gregorian date on which [HijriDate.of] gives that day, so both directions agree.
 */
data class HijriMonth(val year: Int, val month: Int) : Comparable<HijriMonth> {
    init { require(month in 1..12) }

    val length: Int get() = HijrahDate.of(year, month, 1).lengthOfMonth()
    val nameEn: String get() = HijriDate.MONTHS_EN[month - 1]
    val nameAr: String get() = HijriDate.MONTHS_AR[month - 1]

    /** The Gregorian date of [day] in this month, as seen with [adjustDays]. */
    fun date(day: Int, adjustDays: Int = 0): LocalDate =
        LocalDate.from(HijrahDate.of(year, month, day.coerceIn(1, length))).minusDays(adjustDays.toLong())

    fun days(adjustDays: Int = 0): List<LocalDate> = (1..length).map { date(it, adjustDays) }

    fun plus(months: Int): HijriMonth {
        val i = year * 12 + (month - 1) + months
        return HijriMonth(Math.floorDiv(i, 12), Math.floorMod(i, 12) + 1)
    }

    override fun compareTo(other: HijriMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })

    companion object {
        fun of(date: LocalDate, adjustDays: Int = 0): HijriMonth = HijriDate.of(date, adjustDays).let { HijriMonth(it.year, it.month) }
    }
}

