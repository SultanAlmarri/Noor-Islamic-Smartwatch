package com.noor.prayer

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchConfigTest {

    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = mapOf(Prayer.ISHA to 2, Prayer.FAJR to -1), hijriAdjust = -1, language = "ar", arabicDigits = true, use24h = false,
        updatedAtEpochMs = 1_793_000_000_000L,
    )

    @Test fun roundTripKeepsEveryField() {
        assertEquals(dubai, WatchConfig.decode(dubai.encode()))
    }

    @Test fun officialTimesTravelToTheWatchAndWin() {
        val table = com.noor.prayer.authority.Timetable.parse("iacad_dubai", "2026-11-04,05:09,06:25,12:05,15:14,17:40,18:55\n2026-11-05,05:10,06:25,12:05,15:14,17:39,18:55")
        val c = dubai.copy(countryCode = "AE", official = table, offsets = emptyMap())
        val back = WatchConfig.decode(c.encode())!!
        assertEquals("AE", back.countryCode)
        assertEquals(2, back.official!!.size)
        assertEquals(17 * 60 + 39, back.day(LocalDate.of(2026, 11, 5)).minutes[Prayer.MAGHRIB])
        // Dates outside the official window are calculated.
        assertNotNull(back.day(LocalDate.of(2026, 12, 1)).minutes[Prayer.MAGHRIB])
    }

    @Test fun watchCalculatesExactlyWhatThePhoneDoes() {
        val date = LocalDate.of(2026, 11, 4)
        val decoded = WatchConfig.decode(dubai.encode())!!
        val phone = PrayerEngine.compute(date, dubai.point, ZoneId.of("Asia/Dubai"), Profiles.DUBAI_IACAD,
            UserAdjustments(null, HighLatitudeRule.ANGLE_BASED, dubai.offsets), isRamadan = false)
        assertEquals(phone.minutes, decoded.day(date).minutes)
    }

    @Test fun toleratesUnknownAndMissingKeys() {
        val c = WatchConfig.decode("lat=21.4225\nlon=39.8262\nfuture=thing\nprofile=nope\nzone=Not/AZone\n")
        assertNotNull(c)
        assertEquals(Profiles.MWL.id, c.profileId)
        assertEquals("UTC", c.zoneId)
        assertEquals(HighLatitudeRule.ANGLE_BASED, c.highLatitudeRule)
        assertNull(WatchConfig.decode("garbage"))
        assertNull(WatchConfig.decode("lat=95\nlon=0\n"))
    }

    @Test fun clampsOutOfRangeAdjustments() {
        val c = WatchConfig.decode(dubai.encode().replace("offsets=-1,0,0,0,0,2", "offsets=-99,0,0,0,0,99").replace("hijri=-1", "hijri=7"))!!
        assertEquals(-30, c.offsets[Prayer.FAJR]); assertEquals(30, c.offsets[Prayer.ISHA]); assertEquals(2, c.hijriAdjust)
    }

    @Test fun eventsAreOrderedAndCoverTheRequestedSpan() {
        val now = ZonedDateTime.of(2026, 11, 4, 22, 0, 0, 0, ZoneId.of("Asia/Dubai")).toInstant()
        val ev = dubai.events(now, 2)
        assertTrue(ev.zipWithNext().all { (a, b) -> a.at < b.at })
        assertTrue(ev.first().at < now, "starts before now so the current interval is known")
        assertTrue(ev.count { it.at > now } >= 12, "at least two full days ahead")
    }
}

class DaylightSavingTest {
    private val london = GeoPoint(51.5074, -0.1278, 11.0)
    private val zone = java.time.ZoneId.of("Europe/London")

    /** On 29 March 2026 the UK clocks go forward: solar noon moves from about 12:04 GMT to about 13:04 BST. */
    @Test fun clocksGoingForwardShiftLocalTimesByAnHour() {
        val before = PrayerEngine.compute(LocalDate.of(2026, 3, 28), london, zone, Profiles.MWL)
        val after = PrayerEngine.compute(LocalDate.of(2026, 3, 29), london, zone, Profiles.MWL)
        val d = after.minutes[Prayer.DHUHR]!! - before.minutes[Prayer.DHUHR]!!
        assertTrue(d in 59..60, "Dhuhr moved $d minutes")
        assertTrue(after.instant(Prayer.DHUHR)!! > before.instant(Prayer.DHUHR)!!.plusSeconds(86_000))
    }

    /** On 25 October 2026 they go back; the instants stay a solar day apart while the clock times drop an hour. */
    @Test fun clocksGoingBack() {
        val before = PrayerEngine.compute(LocalDate.of(2026, 10, 24), london, zone, Profiles.MWL)
        val after = PrayerEngine.compute(LocalDate.of(2026, 10, 25), london, zone, Profiles.MWL)
        val d = after.minutes[Prayer.MAGHRIB]!! - before.minutes[Prayer.MAGHRIB]!!
        assertTrue(d in -63..-60, "Maghrib moved $d minutes")
        val gap = java.time.Duration.between(before.instant(Prayer.DHUHR), after.instant(Prayer.DHUHR)).toMinutes()
        assertTrue(gap in 1438..1441, "solar days stay ~24 h apart: $gap")
    }
}

class WatchReportTest {
    @Test fun roundTripAndTolerance() {
        val r = WatchReport(2, "0.2.0", 36, true, 1, null, false, 1_793_000_000_000, 1_793_000_100_000)
        assertEquals(r, WatchReport.decode(r.encode()))
        val installed = r.copy(faceInstalledVersion = 1, faceActive = true)
        assertEquals(installed, WatchReport.decode(installed.encode()))
        assertNull(WatchReport.decode("nonsense"))
        assertEquals(34, WatchReport.decode("code=1\nsdk=34\nunknown=x\n")!!.sdkInt)
    }
}
