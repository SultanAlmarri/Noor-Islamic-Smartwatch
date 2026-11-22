package com.noor.app

import com.noor.app.data.AppSettings
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.PrayerProvider
import com.noor.app.data.TimeFormat
import com.noor.app.ui.timetable.MonthPages
import com.noor.app.ui.timetable.MonthStore
import com.noor.app.ui.timetable.MonthWords
import com.noor.app.ui.timetable.Table
import com.noor.app.ui.timetable.TableMonth
import com.noor.app.ui.timetable.TimetableText
import com.noor.app.ui.timetable.Timetables
import com.noor.app.ui.today.DateWindow
import com.noor.app.ui.today.DayCache
import com.noor.app.util.Formatter
import com.noor.prayer.AsrSchool
import com.noor.prayer.HijriDate
import com.noor.prayer.HijriMonth
import com.noor.prayer.Prayer
import com.noor.prayer.UserAdjustments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The month's timetable shows, for every day, exactly the times the home screen shows on that day: each row is
 * compared with the home screen's own path (its cache of days, [DayCache], which is what `TodayViewModel.day` reads),
 * for Dubai (where IACAD's published table applies), a Friday, a month with a clock change, Ramadan by Hijri month,
 * and with the user's own adjustments. The text that is shared carries the same times.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableTest {
    private val tables get() = (RuntimeEnvironment.getApplication() as NoorApplication).container.timetables
    private fun settings(city: String) = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = city)
    private val en = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
    private val words = TimetableText.Words("Prayer times", "Day", "Minhad")

    private fun table(s: AppSettings, month: TableMonth): Table {
        val place = PrayerProvider.place(s)!!
        return Timetables.build(month, place, PrayerProvider.profile(s, place), s, tables)
    }

    /** A day's times as the home screen has them: a fresh copy of its own cache of days, nothing of the timetable's. */
    private fun home(s: AppSettings, date: LocalDate) = PrayerProvider.place(s)!!.let { place -> DayCache(tables).resolved(s, place, PrayerProvider.profile(s, place), date) }

    /** Every row of [month] against the home screen; returns how many days were compared. */
    private fun assertIsTheHomeScreens(s: AppSettings, month: TableMonth): Int {
        val t = table(s, month)
        assertEquals(month.dates(s.hijriAdjust), t.days.map { it.date })
        for (d in t.days) {
            val h = home(s, d.date)
            assertEquals("${s.manualCityId} ${d.date}", h.day.minutes, d.minutes)
            assertEquals("${s.manualCityId} ${d.date} official", h.official, d.official)
            assertEquals(HijriDate.of(d.date, s.hijriAdjust), d.hijri)
            assertEquals(6, d.minutes.size)
        }
        return t.days.size
    }

    @Test fun dubaisNovemberIsTheHomeScreensAndIacadsOwn() {
        val s = settings("dxb")
        val month = TableMonth.Gregorian(YearMonth.of(2026, 11))
        assertEquals(30, assertIsTheHomeScreens(s, month))
        val t = table(s, month)
        // November 2026 is carried as IACAD published it: every row is the authority's own time, as on the home screen.
        assertEquals(Table.Coverage.ALL, t.official)
        val place = PrayerProvider.place(s)!!
        val profile = PrayerProvider.profile(s, place)
        val published = tables.get("iacad_dubai")!!
        for (day in listOf(1, 4, 6, 13, 15, 27, 30)) {
            val date = LocalDate.of(2026, 11, day)
            val own = published.day(date, place.zone, profile.id, UserAdjustments(s.asrSchool, s.highLatitudeRule, s.offsets))!!
            assertEquals("IACAD $date", own.minutes, t.days[day - 1].minutes)
        }
        // A Friday: the same times as the home screen's Friday (whose noon prayer it names Jumu'ah).
        val friday = t.days[5]
        assertEquals(DayOfWeek.FRIDAY, friday.date.dayOfWeek)
        assertTrue(friday.friday)
        assertEquals(home(s, friday.date).day.minutes, friday.minutes)
        assertEquals("Jumu’ah", en.prayerName(Prayer.DHUHR, friday.date))
    }

    @Test fun everyRowOfOtherMonthsIsTheHomeScreens() {
        var days = 0
        // Calculated months, a month whose clocks change (London, March), an official table elsewhere (Singapore).
        for (city in listOf("dxb", "auh", "mak", "lon", "sin")) for (ym in listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 3), YearMonth.of(2021, 1), YearMonth.of(2035, 12))) {
            days += assertIsTheHomeScreens(settings(city), TableMonth.Gregorian(ym))
        }
        // Ramadan by Hijri month (Makkah's Isha is later in Ramadan: the table must follow, as the home screen does).
        days += assertIsTheHomeScreens(settings("mak"), TableMonth.Hijri(HijriMonth(1448, 9)))
        days += assertIsTheHomeScreens(settings("dxb"), TableMonth.Hijri(HijriMonth(1448, 9)))
        // The user's own choices: the Hanafi Asr, minutes added and taken away, the Hijri date moved a day, a method chosen.
        val own = settings("dxb").copy(asrSchool = AsrSchool.HANAFI, offsets = mapOf(Prayer.FAJR to 2, Prayer.ISHA to -3), hijriAdjust = 1)
        days += assertIsTheHomeScreens(own, TableMonth.Gregorian(YearMonth.of(2026, 11)))
        days += assertIsTheHomeScreens(own, TableMonth.Hijri(HijriMonth(1448, 6)))
        days += assertIsTheHomeScreens(settings("lon").copy(profileId = "mwl"), TableMonth.Gregorian(YearMonth.of(2027, 6)))
        assertEquals(767, days)
        // The adjustments really reach the table (and so the comparison above is not of two unadjusted days).
        val plain = table(settings("dxb"), TableMonth.Gregorian(YearMonth.of(2026, 12)))
        val moved = table(settings("dxb").copy(offsets = mapOf(Prayer.FAJR to 2)), TableMonth.Gregorian(YearMonth.of(2026, 12)))
        assertEquals(plain.days[9].minutes[Prayer.FAJR]!! + 2, moved.days[9].minutes[Prayer.FAJR])
        // Makkah in Ramadan: every row is a Ramadan day.
        assertTrue(table(settings("mak"), TableMonth.Hijri(HijriMonth(1448, 9))).days.all { it.hijri.isRamadan })
    }

    @Test fun theSharedTextCarriesThePlaceTheMethodAndEveryTime() {
        val s = settings("dxb")
        val month = TableMonth.Gregorian(YearMonth.of(2026, 11))
        val t = table(s, month)
        val text = TimetableText.of(t, "Dubai", "IACAD — Dubai (Dulook DXB)", s.hijriAdjust, en, words)
        val lines = text.lines()
        assertEquals("Prayer times · November 2026", lines[0])
        assertEquals("Jumada al-Ula – Jumada al-Akhirah 1448 AH", lines[1])
        assertEquals("Dubai · IACAD — Dubai (Dulook DXB)", lines[2])
        assertTrue(text, "Day · Fajr · Shuruq · Dhuhr · Asr · Maghrib · Isha" in lines)
        assertEquals("Minhad", lines.last())
        // Every day's line holds that day's six times as the home screen has them, in the order of the day.
        for ((i, d) in t.days.withIndex()) {
            val row = TimetableText.row(t, i, en)
            assertTrue("day ${i + 1} is in the text", row in lines)
            val number = (i + 1).toString().padStart(2, '\u2007')
            assertTrue(row, row.startsWith("$number  "))
            var at = number.length
            for (p in Prayer.entries) {
                val time = en.time(home(s, d.date).day.minutes[p]!!).first
                at = row.indexOf(time, at).also { assertTrue("$time of $p on ${d.date} in \"$row\"", it >= 0) } + time.length
            }
            assertEquals(d.friday, row.endsWith("Jumu’ah"))
        }
        assertEquals(4, lines.count { it.endsWith("Jumu’ah") })
        // The Hijri date over the first day, and where the next Hijri month begins.
        assertEquals("— " + en.hijri(t.days[0].hijri) + " —", lines[lines.indexOf(TimetableText.row(t, 0, en)) - 1])
        val firstOfMonth = t.days.indexOfFirst { it.hijri.day == 1 }
        assertTrue(firstOfMonth > 0)
        assertEquals("— 1 Jumada al-Akhirah 1448 AH —", lines[lines.indexOf(TimetableText.row(t, firstOfMonth, en)) - 1])

        // In Arabic, with Arabic-Indic numerals and the 24-hour clock: the same times, each line read from the right.
        val ar = Formatter(true, Numerals.ARABIC_INDIC, TimeFormat.H24)
        val arabic = TimetableText.of(t, "دبي", "دائرة الشؤون الإسلامية", s.hijriAdjust, ar, TimetableText.Words("أوقات الصلاة", "اليوم", "منهاد")).lines()
        assertEquals("أوقات الصلاة · نوفمبر ٢٠٢٦", arabic[0])
        for ((i, d) in t.days.withIndex()) {
            val row = TimetableText.row(t, i, ar)
            assertTrue(row in arabic)
            assertTrue(row.startsWith("\u200F"))
            for (p in Prayer.entries) assertTrue(ar.time(home(s, d.date).day.minutes[p]!!).first in row)
            assertTrue(row.none { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' })
        }

        // By Hijri month: the rows are numbered by its days, the Gregorian date stands over the first.
        val ramadan = table(settings("mak"), TableMonth.Hijri(HijriMonth(1448, 9)))
        val shared = TimetableText.of(ramadan, "Makkah", "Umm al-Qura", 0, en, words).lines()
        assertEquals("Prayer times · Ramadan 1448 AH", shared[0])
        assertEquals("— " + en.gregorianDate(ramadan.days[0].date) + " —", shared[shared.indexOf(TimetableText.row(ramadan, 0, en)) - 1])
        assertTrue(TimetableText.row(ramadan, 0, en).startsWith("\u20071  "))
    }

    @Test fun theMonthsThatCanBeTurnedTo() {
        val g = MonthPages(hijri = false, adjust = 0)
        assertEquals(180, g.count)
        assertEquals(TableMonth.Gregorian(YearMonth.of(2021, 1)), g.at(0))
        assertEquals(TableMonth.Gregorian(YearMonth.of(2035, 12)), g.at(179))
        assertEquals(TableMonth.Gregorian(YearMonth.of(2035, 12)), g.at(500))
        for (p in 0 until g.count) assertEquals(p, g.pageOf(g.at(p)))
        assertEquals(TableMonth.Gregorian(YearMonth.of(2026, 11)), g.at(g.pageOf(LocalDate.of(2026, 11, 4))))
        // By Hijri month, with each adjustment: the pages hold every day of the fifteen years once, in order.
        for (adjust in -2..2) {
            val h = MonthPages(hijri = true, adjust = adjust)
            val all = (0 until h.count).flatMap { h.at(it).dates(adjust) }
            assertEquals(DateWindow.pageCount, all.size)
            assertEquals(DateWindow.FIRST, all.first()); assertEquals(DateWindow.LAST, all.last())
            assertTrue(all.zipWithNext().all { (a, b) -> b == a.plusDays(1) })
            for (p in 0 until h.count) assertEquals(p, h.pageOf(h.at(p)))
            for (p in 0 until h.count) assertTrue(h.at(p).dates(adjust).all { HijriMonth.of(it, adjust) == (h.at(p) as TableMonth.Hijri).month })
        }
        // From one calendar to the other: the month that holds the middle of the one on show.
        val h = MonthPages(hijri = true, adjust = 0)
        assertEquals(TableMonth.Hijri(HijriMonth.of(LocalDate.of(2026, 11, 15))), h.at(h.pageOf(TableMonth.Gregorian(YearMonth.of(2026, 11)))))
        assertEquals(TableMonth.Gregorian(YearMonth.from(HijriMonth(1448, 9).date(15))), g.at(g.pageOf(TableMonth.Hijri(HijriMonth(1448, 9)))))
    }

    @Test fun routesNameAMonth() {
        for (m in listOf(TableMonth.Gregorian(YearMonth.of(2026, 11)), TableMonth.Gregorian(YearMonth.of(2031, 2)), TableMonth.Hijri(HijriMonth(1448, 9)), TableMonth.Hijri(HijriMonth(1450, 12)))) {
            assertEquals(m, TableMonth.parse(m.arg))
        }
        assertEquals("2026-11", TableMonth.Gregorian(YearMonth.of(2026, 11)).arg)
        assertEquals("h1448-9", TableMonth.Hijri(HijriMonth(1448, 9)).arg)
        assertEquals("timetable/2026-11", Routes.timetable(TableMonth.Gregorian(YearMonth.of(2026, 11))))
        assertEquals("timetable/now", Routes.timetable())
        for (bad in listOf(null, "now", "", "h", "h1448-13", "2026-13", "x")) assertNull(bad, TableMonth.parse(bad))
    }

    @Test fun theWordsOverAMonth() {
        val nov = TableMonth.Gregorian(YearMonth.of(2026, 11))
        assertEquals("November 2026", MonthWords.title(nov, en))
        assertEquals("Jumada al-Ula – Jumada al-Akhirah 1448 AH", MonthWords.span(nov, 0, en))
        // A Gregorian month that runs over the Hijri new year names both years.
        val newYear = (0 until 180).map { MonthPages(false, 0).at(it) }.first { m -> m.dates(0).let { HijriDate.of(it.first()).year != HijriDate.of(it.last()).year } }
        val span = MonthWords.span(newYear, 0, en)
        assertTrue(span, Regex("Dhu al-Hijjah 14\\d\\d – Muharram 14\\d\\d AH").matches(span))
        val ramadan = TableMonth.Hijri(HijriMonth(1448, 9))
        assertEquals("Ramadan 1448 AH", MonthWords.title(ramadan, en))
        val days = ramadan.dates(0)
        assertEquals("${days.first().dayOfMonth} ${en.monthName(days.first().month)} – ${en.gregorianDate(days.last())}", MonthWords.span(ramadan, 0, en))
        val ar = Formatter(true, Numerals.WESTERN, TimeFormat.H12)
        assertEquals("رمضان 1448 هـ", MonthWords.title(ramadan, ar))
        assertEquals("نوفمبر 2026", MonthWords.title(nov, ar))
    }

    @Test fun aMonthIsWorkedOutOnceAndKept() {
        val s = settings("dxb")
        val place = PrayerProvider.place(s)!!
        val profile = PrayerProvider.profile(s, place)
        val store = MonthStore(place, profile, s, tables, capacity = 3)
        val nov = TableMonth.Gregorian(YearMonth.of(2026, 11))
        assertNull(store.cached(nov))
        val first = runBlocking { store.load(nov) }
        assertEquals(table(s, nov), first)
        assertSame(first, store.cached(nov))
        assertSame(first, runBlocking { store.load(nov) })
        // Only the months lately seen are kept.
        runBlocking { for (m in 1..3) store.load(TableMonth.Gregorian(YearMonth.of(2027, m))) }
        assertNull(store.cached(nov))
        // Anything the times depend on makes a different basis: the page then asks again.
        assertEquals(store.basis, MonthStore(place, profile, s, tables).basis)
        val moved = s.copy(offsets = mapOf(Prayer.ASR to 1))
        assertNotEquals(store.basis, MonthStore(place, profile, moved, tables).basis)
        assertNotEquals(store.basis, MonthStore(PrayerProvider.place(settings("auh"))!!, profile, s, tables).basis)
    }
}
