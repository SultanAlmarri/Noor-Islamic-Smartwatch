package com.noor.prayer

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The −/+ countdown (0.9.7; longer "since" periods in 0.9.8): time left before a prayer, time since it for a while after. */
class CountdownTest {
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = null, fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )
    private val zone = ZoneId.of("Asia/Dubai")
    private val date = LocalDate.of(2026, 11, 7)
    private fun at(p: Prayer, d: LocalDate = date) = dubai.day(d).instant(p)!!

    @Test fun beforeAPrayerItCountsDownAndAfterItCountsUpThenMovesOn() {
        val maghrib = at(Prayer.MAGHRIB)
        // A minute before Maghrib: − 1:00 to Maghrib.
        var c = Countdowns.of(dubai.state(maghrib.minusSeconds(60)), maghrib.minusSeconds(60))
        assertEquals(Prayer.MAGHRIB, c.event.prayer); assertFalse(c.elapsed); assertEquals(Duration.ofSeconds(60), c.duration)
        assertEquals(maghrib, c.changesAt)
        // At Maghrib: + 0:00 since Maghrib (not yet Isha's countdown).
        c = Countdowns.of(dubai.state(maghrib), maghrib)
        assertEquals(Prayer.MAGHRIB, c.event.prayer); assertTrue(c.elapsed); assertEquals(Duration.ZERO, c.duration)
        // 7 minutes later: + 7:00.
        c = Countdowns.of(dubai.state(maghrib.plusSeconds(420)), maghrib.plusSeconds(420))
        assertTrue(c.elapsed); assertEquals(Duration.ofMinutes(7), c.duration); assertEquals(maghrib.plus(Duration.ofMinutes(15)), c.changesAt)
        // After its 15 minutes: − to Isha.
        val t = maghrib.plus(Duration.ofMinutes(15))
        c = Countdowns.of(dubai.state(t), t)
        assertEquals(Prayer.ISHA, c.event.prayer); assertFalse(c.elapsed); assertEquals(Duration.between(t, at(Prayer.ISHA)), c.duration)
    }

    @Test fun eachPrayerHasItsOwnSincePeriod() {
        for (p in Prayer.entries) {
            val a = at(p)
            val inside = a.plus(Countdowns.sinceWindow(p)).minusSeconds(1)
            val c = Countdowns.of(dubai.state(inside), inside)
            assertTrue(c.elapsed, "$p just before the end of its period"); assertEquals(p, c.event.prayer)
            val after = a.plus(Countdowns.sinceWindow(p))
            assertFalse(Countdowns.of(dubai.state(after), after).elapsed, "$p after its period")
        }
    }

    @Test fun acrossMidnight() {
        // Isha + 5 min, a little before midnight on a late night? Use 23:59 and 00:01: always − to tomorrow's Fajr.
        val before = ZonedDateTime.of(date.atTime(23, 59), zone).toInstant()
        val after = ZonedDateTime.of(date.plusDays(1).atTime(0, 1), zone).toInstant()
        val a = Countdowns.of(dubai.state(before), before)
        val b = Countdowns.of(dubai.state(after), after)
        assertEquals(Prayer.FAJR, a.event.prayer); assertEquals(Prayer.FAJR, b.event.prayer)
        assertEquals(date.plusDays(1), a.event.date); assertEquals(date.plusDays(1), b.event.date)
        assertEquals(a.duration.minus(b.duration), Duration.ofMinutes(2))
        // And Fajr itself starts the + period on the new day.
        val fajr = at(Prayer.FAJR, date.plusDays(1))
        val c = Countdowns.of(dubai.state(fajr.plusSeconds(90)), fajr.plusSeconds(90))
        assertTrue(c.elapsed); assertEquals(Prayer.FAJR, c.event.prayer); assertEquals(Duration.ofSeconds(90), c.duration)
    }

    @Test fun theUserCanAlwaysCountDown() {
        // Settings → "After the prayer time: count down to the next prayer": never "+".
        val maghrib = at(Prayer.MAGHRIB)
        val c = Countdowns.of(dubai.state(maghrib.plusSeconds(120)), maghrib.plusSeconds(120), showElapsed = false)
        assertFalse(c.elapsed); assertEquals(Prayer.ISHA, c.event.prayer); assertEquals(at(Prayer.ISHA), c.changesAt)
    }

    @Test fun periodsCoverTheIqamaAndThePrayer() {
        assertEquals(listOf(30L, 20L, 30L, 30L, 15L, 30L), Prayer.entries.map { Countdowns.sinceWindow(it).toMinutes() })
    }

    @Test fun aNextPrayerArrivingEarlyEndsThePeriod() {
        // A synthetic day where Isha comes 6 minutes after Maghrib (high latitudes): the + period of Maghrib ends then.
        val maghrib = at(Prayer.MAGHRIB)
        val state = ScheduleState(dubai.day(date), TimedEvent(Prayer.MAGHRIB, date, maghrib), TimedEvent(Prayer.ISHA, date, maghrib.plusSeconds(360)),
            Duration.ofSeconds(240), 0.3f)
        val c = Countdowns.of(state, maghrib.plusSeconds(120))
        assertTrue(c.elapsed); assertEquals(maghrib.plusSeconds(360), c.changesAt)
    }
}
