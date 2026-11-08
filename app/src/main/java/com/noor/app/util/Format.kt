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

    /**
     * The digits chosen in Noor, whatever produced [s]: String.format and friends write the process's default
     * locale's digits, which can be the phone's (Arabic-Indic or Persian on a phone in Arabic or Persian) even with
     * Noor in English; an Android 15 emulator set to Arabic showed the notification's countdown that way (0.9.8).
     */
    fun digits(s: String): String = buildString(s.length) {
        for (ch in s) {
            val d = when (ch) { in '0'..'9' -> ch - '0'; in '\u0660'..'\u0669' -> ch - '\u0660'; in '\u06F0'..'\u06F9' -> ch - '\u06F0'; else -> -1 }
            append(if (d < 0) ch else if (numerals == Numerals.ARABIC_INDIC) '\u0660' + d else '0' + d)
        }
    }

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

    /**
     * The prayer countdown on the home screen and in the notification (0.9.7): h:mm:ss, or mm:ss under an hour, exactly
     * as Android's own Chronometer writes it in the notification, so the two always read alike.
     */
    fun timer(d: Duration): String {
        val total = maxOf(0L, d.seconds)
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return digits(if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s))
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


    val locale: Locale get() = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH

    fun number(n: Int): String = digits(n.toString())

    /** Length of a sound: "0.5 s", "22 s", "3:05". */
    fun duration(seconds: Double): String {
        val unit = if (arabic) " ث" else " s"
        if (seconds < 1.0) return digits("%.1f".format(Locale.ENGLISH, seconds)).replace('.', if (arabic) '٫' else '.') + unit
        val t = kotlin.math.round(seconds).toLong().coerceAtLeast(1)
        return if (t < 60) digits(t.toString()) + unit else digits("%d:%02d".format(t / 60, t % 60))
    }

    /** Clock time of an instant in [zone], in the user's format. */
    fun timeAt(at: java.time.Instant, zone: java.time.ZoneId): String {
        val z = at.atZone(zone)
        return timeText(z.hour * 60 + z.minute)
    }

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
