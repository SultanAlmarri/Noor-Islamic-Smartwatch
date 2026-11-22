package com.noor.wear

import com.noor.prayer.Prayer
import com.noor.wear.tile.ToolsTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 0.25: the Minhad tile names the next prayer on its top line, so its timeline now changes at each prayer as well as at
 * midnight. The stretches must cover from now to the end of tomorrow without a gap, each inside one day and one
 * interval; and the digits of a count follow the language.
 */
class ToolsTimelineTest {
    private val cfg = TestConfigs.dubai("en")
    private val zone = ZoneId.of("Asia/Dubai")
    private val now = TestConfigs.afternoon

    @Test fun theStretchesCoverTwoDaysWithoutAGap() {
        val s = ToolsTimeline.stretches(Intervals.upcoming(cfg, now), now, zone)
        assertNull("the first runs from now on", s.first().startMillis)
        assertEquals(Prayer.ASR, s.first().next!!.next.prayer)
        for (i in 1 until s.size) assertEquals("no gap before stretch $i", s[i - 1].endMillis, s[i].startMillis)
        val today = now.atZone(zone).toLocalDate()
        assertEquals("to the start of the day after tomorrow", today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli(), s.last().endMillis)
        assertEquals(today, s.first().date)
    }

    @Test fun eachStretchIsOneDayAndOnePrayer() {
        val s = ToolsTimeline.stretches(Intervals.upcoming(cfg, now), now, zone)
        var from = now.toEpochMilli()
        for (x in s) {
            val a = Instant.ofEpochMilli(x.startMillis ?: from)
            val lastMoment = Instant.ofEpochMilli(x.endMillis - 1)
            assertEquals("starts on its day", x.date, a.atZone(zone).toLocalDate())
            assertEquals("ends on its day", x.date, lastMoment.atZone(zone).toLocalDate())
            val iv = x.next!!
            assertTrue("inside its interval", !a.isBefore(iv.start.takeIf { it.isAfter(now) } ?: now) && x.endMillis <= iv.end.toEpochMilli())
            from = x.endMillis
        }
        // Midnight falls between Isha and Fajr: that interval is cut in two, the same prayer next on both sides.
        val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val before = s.single { it.endMillis == midnight }; val after = s.single { it.startMillis == midnight }
        assertEquals(Prayer.FAJR, before.next!!.next.prayer); assertEquals(Prayer.FAJR, after.next!!.next.prayer)
        assertEquals(before.date.plusDays(1), after.date)
        // Six prayers a day: about a dozen stretches, never hundreds.
        assertTrue("${s.size} stretches", s.size in 8..16)
    }

    @Test fun withNothingSetUpTheTwoDaysAlone() {
        val s = ToolsTimeline.stretches(emptyList(), now, zone)
        assertEquals(2, s.size)
        assertNull(s[0].startMillis); assertNull(s[0].next); assertNull(s[1].next)
        assertEquals(s[0].endMillis, s[1].startMillis)
        assertEquals(s[0].date.plusDays(1), s[1].date)
    }

    @Test fun aCountTakesTheThousandsMarkOfItsDigits() {
        assertEquals("1,633", WearText(cfg).count(1633))
        assertEquals("1,633", WearText(TestConfigs.dubai("ar")).count(1633))
        assertEquals("١٬٦٣٣", WearText(TestConfigs.dubai("ar", arabicDigits = true)).count(1633))
    }
}
