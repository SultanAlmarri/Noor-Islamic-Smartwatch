package com.noor.wear

import com.noor.wear.complication.TasbihComplicationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the complications show over the days after the watch last asked for their data. */
class ComplicationTimelinesTest {
    private val dubai = ZoneId.of("Asia/Dubai")

    /**
     * 0.22: the tasbih count, asked for on 18 November, reads 0 from that midnight on, not only on the 19th: the
     * complication is refreshed only when the user counts, and from the 20th it showed the 18th's count again.
     */
    @Test fun theTasbihCountStaysAtZeroAfterEveryLaterMidnight() {
        val today = LocalDate.of(2026, 11, 18)
        val reset = TasbihComplicationService.resetInterval(today, dubai)
        assertEquals(ZonedDateTime.of(2026, 11, 19, 0, 0, 0, 0, dubai).toInstant(), reset.start)
        for (d in listOf(1L, 2L, 3L, 30L, 365L)) {
            val at = today.plusDays(d).atTime(9, 0).atZone(dubai).toInstant()
            assertTrue("day +$d still reads 0", at >= reset.start && at < reset.end)
        }
        // Before midnight the day's own count shows (the default entry).
        assertTrue(today.atTime(23, 59).atZone(dubai).toInstant() < reset.start)
    }
}
