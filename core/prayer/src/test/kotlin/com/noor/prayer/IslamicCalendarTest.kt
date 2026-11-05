package com.noor.prayer

import com.noor.prayer.calendar.IslamicCalendar
import com.noor.prayer.calendar.IslamicEvent
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IslamicCalendarTest {

    /** Umm al-Qura dates for 1447 AH as published (Ramadan began on 18 February 2026, Eid al-Adha on 27 May 2026). */
    @Test fun knownUmmAlQuraDates() {
        assertEquals(LocalDate.of(2026, 2, 18), IslamicCalendar.gregorian(1447, 9, 1))
        assertEquals(LocalDate.of(2026, 3, 20), IslamicCalendar.gregorian(1447, 10, 1))
        assertEquals(LocalDate.of(2026, 5, 26), IslamicCalendar.gregorian(1447, 12, 9))
        assertEquals(LocalDate.of(2026, 5, 27), IslamicCalendar.gregorian(1447, 12, 10))
        assertEquals(LocalDate.of(2026, 6, 16), IslamicCalendar.gregorian(1448, 1, 1))
    }

    @Test fun theAdjustmentIsTheInverseOfTheHijriDateAdjustment() {
        for (adj in -2..2) {
            val d = IslamicCalendar.gregorian(1448, 9, 1, adj)!!
            assertEquals(HijriDate(1, 9, 1448), HijriDate.of(d, adj))
        }
    }

    @Test fun upcomingEventsAreInOrderAndEachAppearsOnce() {
        val today = LocalDate.of(2026, 11, 5) // 25 Jumada al-Ula 1448
        val list = IslamicCalendar.upcoming(today)
        assertEquals(IslamicEvent.entries.size, list.size)
        assertEquals(list.sortedBy { it.date }, list)
        assertEquals(IslamicEvent.entries.toSet(), list.map { it.event }.toSet())
        // White Days of Jumada al-Akhirah come first, then Ramadan 1448.
        assertEquals(IslamicEvent.WHITE_DAYS, list[0].event)
        assertEquals(HijriDate(13, 6, 1448), list[0].hijri)
        assertEquals(LocalDate.of(2027, 2, 8), list.first { it.event == IslamicEvent.RAMADAN }.date)
        assertEquals(95L, list.first { it.event == IslamicEvent.RAMADAN }.daysUntil)
        for (e in list) assertEquals(e.hijri, HijriDate.of(e.date), e.event.name)
    }

    @Test fun anEventTodayCountsAsToday() {
        val eid = LocalDate.of(2026, 5, 27)
        val e = IslamicCalendar.upcoming(eid).first { it.event == IslamicEvent.EID_AL_ADHA }
        assertEquals(eid, e.date); assertEquals(0L, e.daysUntil)
        // The day after, the next Eid al-Adha is a year away.
        val next = IslamicCalendar.upcoming(eid.plusDays(1)).first { it.event == IslamicEvent.EID_AL_ADHA }
        assertEquals(LocalDate.of(2027, 5, 16), next.date)
    }

    @Test fun whiteDaysSkipRamadanAndDhuAlHijjahAndShowWhileUnderWay() {
        // 14 Ramadan 1447: the next White Days are in Shawwal.
        val inRamadan = IslamicCalendar.gregorian(1447, 9, 14)!!
        val w = IslamicCalendar.upcoming(inRamadan).first { it.event == IslamicEvent.WHITE_DAYS }
        assertEquals(10, w.hijri.month)
        // 14 Dhu al-Hijjah 1447: next White Days are in Muharram.
        val dh = IslamicCalendar.gregorian(1447, 12, 12)!!
        assertEquals(1, IslamicCalendar.upcoming(dh).first { it.event == IslamicEvent.WHITE_DAYS }.hijri.month)
        // 14 Rajab: under way, so 0 days, ending on the 15th.
        val mid = IslamicCalendar.gregorian(1448, 7, 14)!!
        val under = IslamicCalendar.upcoming(mid).first { it.event == IslamicEvent.WHITE_DAYS }
        assertEquals(0L, under.daysUntil)
        assertEquals(IslamicCalendar.gregorian(1448, 7, 15), under.endDate)
        assertTrue(under.date.isBefore(mid))
    }
}
