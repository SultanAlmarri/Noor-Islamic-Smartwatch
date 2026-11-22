package com.noor.app.ui.timetable

import com.noor.app.data.AppSettings
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ResolvedPlace
import com.noor.app.ui.today.DateWindow
import com.noor.app.util.Formatter
import com.noor.prayer.CalculationProfile
import com.noor.prayer.HijriDate
import com.noor.prayer.HijriMonth
import com.noor.prayer.Prayer
import com.noor.shared.geo.OfficialTimetables
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The month a timetable shows: a Gregorian month, or a Hijri one (Umm al-Qura with the user's adjustment, as
 * everywhere in the app). Only the days inside [DateWindow] are listed.
 */
sealed interface TableMonth {
    data class Gregorian(val month: YearMonth) : TableMonth
    data class Hijri(val month: HijriMonth) : TableMonth

    val hijri: Boolean get() = this is Hijri

    /** The month's days, in order. [adjust] is the user's Hijri adjustment (it moves a Hijri month's days). */
    fun dates(adjust: Int): List<LocalDate> = when (this) {
        is Gregorian -> (1..month.lengthOfMonth()).map { month.atDay(it) }
        is Hijri -> month.days(adjust)
    }.filter { it in DateWindow }

    /** As the route writes it: "2026-11", "h1448-6". */
    val arg: String get() = when (this) {
        is Gregorian -> "%04d-%02d".format(java.util.Locale.ROOT, month.year, month.monthValue)
        is Hijri -> "h${month.year}-${month.month}"
    }

    companion object {
        /** The month a route's argument names; null for anything else ("now": the month of today). */
        fun parse(arg: String?): TableMonth? = runCatching {
            when {
                arg == null -> null
                arg.startsWith("h") -> arg.drop(1).split('-').let { Hijri(HijriMonth(it[0].toInt(), it[1].toInt())) }
                else -> Gregorian(YearMonth.parse(arg))
            }
        }.getOrNull()

        fun of(date: LocalDate, hijri: Boolean, adjust: Int): TableMonth =
            if (hijri) Hijri(HijriMonth.of(date, adjust)) else Gregorian(YearMonth.from(date))
    }
}

/**
 * The months the timetable can be turned to, as pages: every month with a day in [DateWindow] (2021 to 2035), by one
 * calendar or the other.
 */
class MonthPages(val hijri: Boolean, val adjust: Int) {
    private val firstG = YearMonth.from(DateWindow.FIRST)
    private val firstH = HijriMonth.of(DateWindow.FIRST, adjust)
    private val lastH = HijriMonth.of(DateWindow.LAST, adjust)

    val count: Int = if (hijri) index(lastH) - index(firstH) + 1
        else (DateWindow.LAST.year - DateWindow.FIRST.year) * 12 + DateWindow.LAST.monthValue - DateWindow.FIRST.monthValue + 1

    fun at(page: Int): TableMonth = page.coerceIn(0, count - 1).let { p ->
        if (hijri) TableMonth.Hijri(firstH.plus(p)) else TableMonth.Gregorian(firstG.plusMonths(p.toLong()))
    }

    /** The page of the month [date] falls in (the nearest one for a date outside the window). */
    fun pageOf(date: LocalDate): Int = DateWindow.clamp(date).let { d ->
        if (hijri) index(HijriMonth.of(d, adjust)) - index(firstH)
        else (d.year - firstG.year) * 12 + d.monthValue - firstG.monthValue
    }.coerceIn(0, count - 1)

    /** The page of [month] if it is one of this calendar's, otherwise the page of the month its middle falls in. */
    fun pageOf(month: TableMonth): Int = when {
        month is TableMonth.Hijri && hijri -> (index(month.month) - index(firstH)).coerceIn(0, count - 1)
        month is TableMonth.Gregorian && !hijri -> ((month.month.year - firstG.year) * 12 + month.month.monthValue - firstG.monthValue).coerceIn(0, count - 1)
        else -> pageOf(month.middle(adjust))
    }

    private fun index(m: HijriMonth) = m.year * 12 + m.month - 1
}

/** A day in the middle of the month: the day another calendar's page is chosen by, so most of the month stays on show. */
internal fun TableMonth.middle(adjust: Int): LocalDate = when (this) {
    is TableMonth.Gregorian -> month.atDay(15)
    is TableMonth.Hijri -> month.date(15, adjust)
}

/** One row: a day and its six times (minutes after midnight, as the home screen has them; null: it does not occur). */
data class TableDay(val date: LocalDate, val hijri: HijriDate, val minutes: Map<Prayer, Int?>, val official: Boolean) {
    val friday: Boolean get() = date.dayOfWeek == DayOfWeek.FRIDAY
}

/** A month's times. */
data class Table(val month: TableMonth, val days: List<TableDay>) {
    /** Whether the authority's own published times were used: for every day, for some, or for none. */
    val official: Coverage get() = when (days.count { it.official }) { 0 -> Coverage.NONE; days.size -> Coverage.ALL; else -> Coverage.SOME }
    val hasRamadan: Boolean get() = days.any { it.hijri.isRamadan }

    enum class Coverage { ALL, SOME, NONE }
}

object Timetables {
    /**
     * A month's times, each day by the home screen's own path ([PrayerProvider.resolve] with the same place, method,
     * settings and official timetables): what the table shows for a day is what the home screen shows on that day.
     */
    fun build(month: TableMonth, place: ResolvedPlace, profile: CalculationProfile, s: AppSettings, tables: OfficialTimetables?): Table =
        Table(month, month.dates(s.hijriAdjust).map { date ->
            val r = PrayerProvider.resolve(date, place, profile, s, tables)
            TableDay(date, HijriDate.of(date, s.hijriAdjust), r.day.minutes, r.official)
        })

    /** What decides a month's times: when any of it changes, the months worked out before are no longer good. */
    fun basis(place: ResolvedPlace, profile: CalculationProfile, s: AppSettings, tablesVersion: Int?): Any =
        listOf(place, profile.id, s.asrSchool, s.highLatitudeRule, s.offsets, s.hijriAdjust, s.profileId, tablesVersion)
}

/** The words a month is headed with: its name, and under it the months of the other calendar it runs through. */
object MonthWords {
    private const val DASH = " – "

    fun title(m: TableMonth, f: Formatter): String = when (m) {
        is TableMonth.Gregorian -> f.monthYear(m.month)
        is TableMonth.Hijri -> hijri(m.month, f)
    }

    fun hijri(m: HijriMonth, f: Formatter): String = "${if (f.arabic) m.nameAr else m.nameEn} ${f.number(m.year)} ${era(f)}"

    fun era(f: Formatter): String = if (f.arabic) "هـ" else "AH"

    /**
     * "Jumada al-Ula – Jumada al-Akhirah 1448 AH" under a Gregorian month; "18 February – 19 March 2026" under a Hijri
     * one. The year is said once where both ends share it.
     */
    fun span(m: TableMonth, adjust: Int, f: Formatter): String {
        val dates = m.dates(adjust)
        if (dates.isEmpty()) return ""
        val a = dates.first(); val b = dates.last()
        return when (m) {
            is TableMonth.Gregorian -> {
                val ha = HijriMonth.of(a, adjust); val hb = HijriMonth.of(b, adjust)
                fun name(h: HijriMonth) = if (f.arabic) h.nameAr else h.nameEn
                when {
                    ha == hb -> hijri(ha, f)
                    ha.year == hb.year -> name(ha) + DASH + hijri(hb, f)
                    else -> "${name(ha)} ${f.number(ha.year)}" + DASH + hijri(hb, f)
                }
            }
            is TableMonth.Hijri -> {
                fun dayMonth(d: LocalDate) = "${f.number(d.dayOfMonth)} ${f.monthName(d.month)}"
                if (a.year == b.year) dayMonth(a) + DASH + f.gregorianDate(b) else f.gregorianDate(a) + DASH + f.gregorianDate(b)
            }
        }
    }

    /**
     * The line that stands in the table where a month of the other calendar begins: "Jumada al-Akhirah 1448 AH" over
     * its first day in a Gregorian month, "December 2026" in a Hijri one. Null on every other day.
     */
    fun section(m: TableMonth, day: TableDay, f: Formatter): String? = when (m) {
        is TableMonth.Gregorian -> if (day.hijri.day == 1) hijri(HijriMonth(day.hijri.year, day.hijri.month), f) else null
        is TableMonth.Hijri -> if (day.date.dayOfMonth == 1) f.monthYear(YearMonth.from(day.date)) else null
    }
}

/** The month as plain text, to share (no file, no permission): the place, what the times follow, a line for each day. */
object TimetableText {
    /** Words the text needs from the app's resources, in its language. */
    data class Words(val title: String, val day: String, val app: String)

    private const val FIGURE_SPACE = '\u2007'
    private const val RLM = "\u200F"

    /** A time as the table writes it: "5:13", "17:38" (the column says which prayer; 12-hour times carry no AM or PM). */
    fun time(minutes: Int?, f: Formatter): String = minutes?.let { f.time(it).first } ?: "—"

    /**
     * One day's line: its number in the month on show, then the six times in the order of the day; "Jumu’ah" closes a
     * Friday's. Numbers are padded with figure spaces so the columns line up wherever digits share a width.
     */
    fun row(table: Table, index: Int, f: Formatter, widths: List<Int> = widths(table, f)): String {
        val d = table.days[index]
        val number = if (table.month.hijri) d.hijri.day else d.date.dayOfMonth
        val cells = Prayer.entries.mapIndexed { i, p -> time(d.minutes[p], f).padStart(widths[i], FIGURE_SPACE) }
        val line = f.number(number).padStart(2, FIGURE_SPACE) + "  " + cells.joinToString("  ") + if (d.friday) "  " + f.prayerName(Prayer.DHUHR, d.date) else ""
        // In Arabic every line reads from the right, as the table does: the day first, then Fajr, on to Isha.
        return if (f.arabic) RLM + line else line
    }

    private fun widths(table: Table, f: Formatter): List<Int> = Prayer.entries.map { p -> table.days.maxOfOrNull { time(it.minutes[p], f).length } ?: 0 }

    fun of(table: Table, place: String, method: String, adjust: Int, f: Formatter, words: Words): String = buildString {
        val m = table.month
        appendLine("${words.title} · ${MonthWords.title(m, f)}")
        MonthWords.span(m, adjust, f).takeIf { it.isNotEmpty() }?.let(::appendLine)
        appendLine("$place · $method")
        appendLine()
        appendLine((listOf(words.day) + Prayer.entries.map(f::prayerName)).joinToString(" · "))
        val widths = widths(table, f)
        table.days.forEachIndexed { i, d ->
            // The other calendar's date over the first day, and where one of its months begins.
            val mark = when {
                m is TableMonth.Gregorian && (i == 0 || d.hijri.day == 1) -> f.hijri(d.hijri)
                m is TableMonth.Hijri && (i == 0 || d.date.dayOfMonth == 1) -> f.gregorianDate(d.date)
                else -> null
            }
            if (mark != null) appendLine("— $mark —")
            appendLine(row(table, i, f, widths))
        }
        appendLine()
        append(words.app)
    }
}
