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
