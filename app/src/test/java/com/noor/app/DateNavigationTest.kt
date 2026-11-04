package com.noor.app

import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.ui.today.DateWindow
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DateNavigationTest {
    @Test fun windowCoversFifteenFullYears() {
        assertEquals(LocalDate.of(2021, 1, 1), DateWindow.dateOf(0))
        assertEquals(LocalDate.of(2035, 12, 31), DateWindow.dateOf(DateWindow.pageCount - 1))
        assertEquals(5478, DateWindow.pageCount) // 2021-2035 inclusive, with leap days 2024, 2028, 2032
    }

    @Test fun everyPageMapsToItsDateAndBack() {
        var d = DateWindow.FIRST
        var page = 0
        while (d <= DateWindow.LAST) {
            assertEquals(page, DateWindow.pageOf(d))
            assertEquals(d, DateWindow.dateOf(page))
            d = d.plusDays(1); page++
        }
    }

    @Test fun datesOutsideAreClamped() {
        assertEquals(0, DateWindow.pageOf(LocalDate.of(1999, 5, 5)))
        assertEquals(DateWindow.pageCount - 1, DateWindow.pageOf(LocalDate.of(2099, 1, 1)))
        assertFalse(LocalDate.of(2020, 12, 31) in DateWindow)
        assertTrue(LocalDate.of(2028, 2, 29) in DateWindow)
    }

    @Test fun fridayDhuhrIsJumuah() {
        val en = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        val ar = Formatter(true, Numerals.WESTERN, TimeFormat.H12)
        val friday = LocalDate.of(2026, 11, 13); val thursday = friday.minusDays(1)
        assertEquals("Jumu’ah", en.prayerName(Prayer.DHUHR, friday))
        assertEquals("الجمعة", ar.prayerName(Prayer.DHUHR, friday))
        assertEquals("Dhuhr", en.prayerName(Prayer.DHUHR, thursday))
        assertEquals("Asr", en.prayerName(Prayer.ASR, friday))
        assertEquals("الجمعة", en.prayerNameOther(Prayer.DHUHR, friday))
    }

    @Test fun dateTexts() {
        val en = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        assertEquals("4 November 2026", en.gregorianDate(LocalDate.of(2026, 11, 4)))
        assertEquals("Wednesday", en.weekday(LocalDate.of(2026, 11, 4)))
        assertEquals("November 2026", en.monthYear(YearMonth.of(2026, 11)))
        val ar = Formatter(true, Numerals.ARABIC_INDIC, TimeFormat.H12)
        assertEquals("٤ نوفمبر ٢٠٢٦", ar.gregorianDate(LocalDate.of(2026, 11, 4)))
        assertEquals("الأربعاء", ar.weekday(LocalDate.of(2026, 11, 4)))
    }
}
