package com.noor.wear

import com.noor.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class IntervalsTest {
    private val c = TestConfigs.dubai()

    @Test fun firstIntervalContainsNowAndPointsToNextPrayer() {
        val iv = Intervals.upcoming(c, TestConfigs.afternoon)
        assertEquals(Prayer.DHUHR, iv.first().previous.prayer)
        assertEquals(Prayer.ASR, iv.first().next.prayer)
        assertTrue(iv.first().start <= TestConfigs.afternoon && TestConfigs.afternoon < iv.first().end)
    }

    @Test fun intervalsAreContiguousForTwoDays() {
        val iv = Intervals.upcoming(c, TestConfigs.afternoon)
        assertTrue(iv.zipWithNext().all { (a, b) -> a.end == b.start })
        assertTrue(Duration.between(TestConfigs.afternoon, iv.last().end) >= Duration.ofHours(47))
        assertTrue(iv.size in 12..14)
    }

    @Test fun afterIshaTheNextIsTomorrowsFajr() {
        val late = ZonedDateTime.of(2026, 11, 4, 23, 30, 0, 0, ZoneId.of("Asia/Dubai")).toInstant()
        val first = Intervals.upcoming(c, late).first()
        assertEquals(Prayer.ISHA, first.previous.prayer)
        assertEquals(Prayer.FAJR, first.next.prayer)
        assertEquals(5, first.next.date.dayOfMonth)
    }

    @Test fun exactlyAtAPrayerTheIntervalSwitches() {
        val asr = Intervals.upcoming(c, TestConfigs.afternoon).first().end
        val iv = Intervals.upcoming(c, asr).first()
        assertEquals(Prayer.ASR, iv.previous.prayer)
        assertEquals(Prayer.MAGHRIB, iv.next.prayer)
    }
}
