package com.noor.prayer

import com.noor.prayer.authority.Authorities
import com.noor.prayer.authority.SourceKind
import com.noor.prayer.authority.Timetable
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimetableTest {
    private val muis = Timetable.parse("muis_singapore", javaClass.classLoader.getResource("muis_singapore_2024_2026.csv")!!.readText())

    @Test fun parsesTheOfficialMuisTable() {
        assertEquals(1096, muis.size)
        assertEquals(LocalDate.of(2024, 1, 1), muis.first); assertEquals(LocalDate.of(2026, 12, 31), muis.last)
        val d = muis.day(LocalDate.of(2026, 1, 1), ZoneId.of("Asia/Singapore"), "sgp")!!
        assertEquals(listOf(5 * 60 + 44, 7 * 60 + 8, 13 * 60 + 10, 16 * 60 + 34, 19 * 60 + 11, 20 * 60 + 25), Prayer.entries.map { d.minutes[it] })
        assertNull(muis.day(LocalDate.of(2027, 1, 1), ZoneId.of("Asia/Singapore"), "sgp"))
    }

    @Test fun userOffsetsApplyOnTopOfOfficialTimes() {
        val d = muis.day(LocalDate.of(2026, 1, 1), ZoneId.of("Asia/Singapore"), "sgp", UserAdjustments(minuteOffsets = mapOf(Prayer.FAJR to 2)))!!
        assertEquals(5 * 60 + 46, d.minutes[Prayer.FAJR])
    }

    @Test fun malformedLinesAreSkipped() {
        val t = Timetable.parse("x", "# comment\ndate,fajr\n2026-01-01,05:00,06:00,12:00,15:00,18:00,19:00\n2026-01-02,bad\n2026-01-03,05:00,04:00,12:00,15:00,18:00,19:00\n")
        assertEquals(1, t.size)
        assertNotNull(t.minutes(LocalDate.of(2026, 1, 1)))
    }

    @Test fun roundTrip() {
        val w = muis.window(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        assertEquals(31, w.size)
        assertEquals(w.encode(), Timetable.parse("x", w.encode()).encode())
    }

    @Test fun authorityChoice() {
        assertEquals(Authorities.IACAD, Authorities.forPlace("AE", Authorities.DUBAI_REGION))
        assertEquals(Authorities.AWQAF, Authorities.forPlace("ae", "AE-OTHER"))
        assertEquals(Authorities.MUIS, Authorities.forPlace("SG", null))
        assertEquals(Authorities.JAKIM, Authorities.forPlace("MY", null))
        assertEquals(Authorities.UMM_AL_QURA, Authorities.forPlace("SA", null))
        assertEquals(Authorities.MWL, Authorities.forPlace("DE", null))
        assertEquals(SourceKind.RECOGNISED_METHOD, Authorities.forPlace(null, null).kind)
        assertTrue(Authorities.ALL.all { it.sourceUrl.startsWith("https://") })
    }
}
