package com.noor.app.util

import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Text formatting with numerals chosen independently of the language (as on the watch face). */
class Formatter(val arabic: Boolean, private val numerals: Numerals, private val timeFormat: TimeFormat) {

    fun digits(s: String): String = if (numerals == Numerals.ARABIC_INDIC) buildString {
        for (ch in s) append(if (ch in '0'..'9') ('٠' + (ch - '0')) else ch)
    } else s

    /** Minutes after midnight -> "5:09" + suffix, or "05:09". */
    fun time(minutes: Int): Pair<String, String?> {
        val m = ((minutes % 1440) + 1440) % 1440
        val h = m / 60; val mm = m % 60
        return if (timeFormat == TimeFormat.H24) digits("%02d:%02d".format(h, mm)) to null
        else {
            val h12 = if (h % 12 == 0) 12 else h % 12
            val suffix = if (arabic) (if (h < 12) "ص" else "م") else (if (h < 12) "AM" else "PM")
            digits("%d:%02d".format(h12, mm)) to suffix
        }
    }

    fun timeText(minutes: Int): String = time(minutes).let { (t, s) -> if (s == null) t else "$t $s" }

    /** Live countdown h:mm:ss (or m:ss under an hour). */
    fun countdown(d: Duration): String {
        val total = maxOf(0L, d.seconds)
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return digits(if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s))
    }

    fun hoursMinutes(d: Duration): Pair<Long, Long> {
        val total = maxOf(0L, d.seconds + 59) / 60
        return total / 60 to total % 60
    }

    fun gregorian(date: LocalDate): String {
        val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
        val day = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        // Format the numbers ourselves so the numeral choice is respected in both languages.
        val month = date.month.getDisplayName(TextStyle.FULL, locale)
        return "$day ${digits(date.dayOfMonth.toString())} $month ${digits(date.year.toString())}"
    }

    fun hijri(h: HijriDate): String =
        "${digits(h.day.toString())} ${if (arabic) h.monthNameAr else h.monthNameEn} ${digits(h.year.toString())}" + if (arabic) " هـ" else " AH"

    fun prayerName(p: Prayer): String = if (arabic) p.ar else p.en

    /** Dhuhr is shown as Jumu'ah on Fridays. */
    fun prayerName(p: Prayer, date: LocalDate): String =
        if (p == Prayer.DHUHR && date.dayOfWeek == DayOfWeek.FRIDAY) (if (arabic) "الجمعة" else "Jumu’ah") else prayerName(p)

    fun prayerNameOther(p: Prayer, date: LocalDate): String =
        if (p == Prayer.DHUHR && date.dayOfWeek == DayOfWeek.FRIDAY) (if (arabic) "Jumu’ah" else "الجمعة") else (if (arabic) p.en else p.ar)

    val locale: Locale get() = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH

    fun number(n: Int): String = digits(n.toString())

    /** Degrees with exactly two decimals, e.g. "259.39°" (Arabic-Indic numerals use the Arabic decimal separator). */
    fun degrees(d: Double): String {
        val s = String.format(Locale.US, "%.2f", d)
        // Left-to-right marks keep "°" and any sign beside the number inside Arabic sentences.
        return LRM + (if (numerals == Numerals.ARABIC_INDIC) digits(s).replace('.', '٫') else s) + "°" + LRM
    }

    /** Signed degrees: "+83.49°" / "−43.09°". */
    fun signedDegrees(d: Double): String = LRM + (if (d > 0) "+" else if (d < 0) "−" else "") + degrees(kotlin.math.abs(d))

    /** Whole kilometres with grouping, e.g. "1,633 km". */
    fun kilometres(km: Double): String {
        val s = String.format(Locale.US, "%,d", Math.round(km))
        return (if (numerals == Numerals.ARABIC_INDIC) digits(s).replace(',', '٬') else s) + if (arabic) " كم" else " km"
    }

    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    fun weekdayShort(d: DayOfWeek): String =
        if (arabic) d.getDisplayName(TextStyle.FULL, locale) else d.getDisplayName(TextStyle.SHORT, locale).take(3)

    fun monthName(m: Month): String = m.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .let { if (it.firstOrNull()?.isDigit() == true) m.getDisplayName(TextStyle.FULL, locale) else it }

    fun monthShort(m: Month): String = if (arabic) monthName(m) else m.getDisplayName(TextStyle.SHORT, locale)

    /** "4 November 2026" (numbers in the chosen numerals). */
    fun gregorianDate(date: LocalDate): String =
        "${digits(date.dayOfMonth.toString())} ${monthName(date.month)} ${digits(date.year.toString())}"

    fun monthYear(ym: YearMonth): String = "${monthName(ym.month)} ${digits(ym.year.toString())}"

    private companion object { const val LRM = "\u200E" }
}
