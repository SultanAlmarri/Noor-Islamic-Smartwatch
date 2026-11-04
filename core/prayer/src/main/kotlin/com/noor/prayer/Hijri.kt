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
