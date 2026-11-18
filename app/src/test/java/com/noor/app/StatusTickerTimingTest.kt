package com.noor.app

import com.noor.app.notify.StatusNotification
import com.noor.app.notify.StatusTicker
import com.noor.app.util.Formatter
import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * (0.24) The countdown in the notification and the status-bar chip changes on the instant the true countdown does (the
 * owner: "off by a few milliseconds. I want it to be exact"): [StatusTicker.nextChange] is the moment it changes, and the
 * notification shown at that moment is built for the instant just after it, so it reads what the true countdown reads
 * for the whole second (or minute) that follows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatusTickerTimingTest {
    private fun content(countAt: Instant, elapsed: Boolean) = StatusNotification.Content(
        prayer = "Fajr", time = "5:18 AM", line = "", nextAt = countAt, refreshAt = countAt.plusSeconds(86_400),
        place = null, hijri = "", elapsed = elapsed, countAt = countAt,
    )

    @Test fun theCountdownIsShownOnTheInstantItChanges() {
        val f = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        val fajr = Instant.parse("2026-11-19T01:18:00Z")
        val before = content(fajr, elapsed = false)
        val t = Instant.parse("2026-11-18T16:27:51.300Z").toEpochMilli()
        // Before the prayer: the next change is at the next whole second of (now − countAt).
        val b = StatusTicker.nextChange(before, t, seconds = true)
        assertEquals(Instant.parse("2026-11-18T16:27:52Z").toEpochMilli(), b)
        // Just before it the countdown reads −8:50:08; built for just after it, −8:50:07, which is right until the next one.
        assertEquals("−8:50:08", before.countdown(Instant.ofEpochMilli(b - 1), f))
        assertEquals("−8:50:07", before.countdown(Instant.ofEpochMilli(b + 1), f))
        assertEquals("−8:50:07", before.countdown(Instant.ofEpochMilli(StatusTicker.nextChange(before, b + 1, true) - 1), f))
        // Exactly on a change, the next one is a whole second later (never the same instant again).
        assertEquals(b + 1000, StatusTicker.nextChange(before, b, seconds = true))
        // To the minute: where the time left falls below a whole minute.
        assertEquals(Instant.parse("2026-11-18T16:28:00Z").toEpochMilli(), StatusTicker.nextChange(before, t, seconds = false))
        // A prayer time with a fraction of a second (not whole minutes) is followed exactly too.
        val odd = content(fajr.plusMillis(250), elapsed = false)
        assertEquals(Instant.parse("2026-11-18T16:27:52.250Z").toEpochMilli(), StatusTicker.nextChange(odd, t, true))
        // After the prayer, counting up: the change is where the time since reaches a whole second.
        val after = content(Instant.parse("2026-11-18T16:00:00Z"), elapsed = true)
        val a = StatusTicker.nextChange(after, t, seconds = true)
        assertEquals(Instant.parse("2026-11-18T16:27:52Z").toEpochMilli(), a)
        assertEquals("+27:51", after.countdown(Instant.ofEpochMilli(a - 1), f))
        assertEquals("+27:52", after.countdown(Instant.ofEpochMilli(a + 1), f))
    }
}
