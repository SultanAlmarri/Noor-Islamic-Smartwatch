package com.noor.prayer

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Browsing by Hijri month (0.9.4): published Umm al-Qura dates, the user's adjustment, and the date window. */
class HijriMonthTest {
    @Test fun monthsMapToThePublishedGregorianDates() {
        val ramadan = HijriMonth(1447, 9)
        assertEquals(LocalDate.of(2026, 2, 18), ramadan.date(1))
        assertEquals(LocalDate.of(2026, 3, 20), HijriMonth(1447, 10).date(1))
        assertEquals(LocalDate.of(2026, 6, 16), HijriMonth(1448, 1).date(1))
        assertTrue(ramadan.length == 29 || ramadan.length == 30)
        assertEquals(ramadan.length, ramadan.days().size)
        // Consecutive days, and the next month starts the day after.
        val d = ramadan.days()
        assertTrue(d.zipWithNext().all { (a, b) -> b == a.plusDays(1) })
        assertEquals(d.last().plusDays(1), ramadan.plus(1).date(1))
    }

    @Test fun bothDirectionsAgreeWithTheUsersAdjustment() {
        for (adjust in -2..2) {
            var m = HijriMonth.of(LocalDate.of(2021, 1, 1), adjust)
            val end = HijriMonth.of(LocalDate.of(2035, 12, 31), adjust)
            while (m <= end) {
                m.days(adjust).forEachIndexed { i, date ->
                    assertEquals(HijriDate(i + 1, m.month, m.year), HijriDate.of(date, adjust), "$m day ${i + 1}, adjust $adjust")
                }
                m = m.plus(1)
            }
        }
        // 4 November 2026: 24 Jumada al-Ula 1448 (Umm al-Qura), so the 25th with +1.
        assertEquals(HijriMonth(1448, 5), HijriMonth.of(LocalDate.of(2026, 11, 4)))
        assertEquals(LocalDate.of(2026, 11, 4), HijriMonth(1448, 5).date(24))
        assertEquals(LocalDate.of(2026, 11, 3), HijriMonth(1448, 5).date(24, adjustDays = 1))
    }

    @Test fun monthArithmeticCrossesYears() {
        assertEquals(HijriMonth(1448, 1), HijriMonth(1447, 12).plus(1))
        assertEquals(HijriMonth(1446, 12), HijriMonth(1447, 1).plus(-1))
        assertEquals(HijriMonth(1449, 3), HijriMonth(1447, 3).plus(24))
    }
}
