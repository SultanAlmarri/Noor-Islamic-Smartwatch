package com.noor.prayer

import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.authority.Timetable
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSetupTest {
    private val dubaiCity = SetupPlace(GeoPoint(25.2048, 55.2708, 5.0), "Asia/Dubai", "Dubai", "دبي", "AE", "AE-DU", fromGps = false)
    private val utc = ZoneId.of("UTC")

    @Test fun roundTrip() {
        val s = LocalSetup(dubaiCity, AsrSchool.HANAFI, "ar", true, true, -1, true, WatchAlertStyle.CHIME, setOf(Prayer.FAJR, Prayer.MAGHRIB))
        assertEquals(s, LocalSetup.decode(s.encode()))
        val empty = LocalSetup(alertPrayers = emptySet())
        assertEquals(empty, LocalSetup.decode(empty.encode()))
        assertNull(LocalSetup.decode(""))
    }

    @Test fun aCityUsesItsOwnZoneAndAuthorityLikeThePhone() {
        val table = Timetable.parse("iacad_dubai", "2026-11-04,05:09,06:25,12:05,15:14,17:40,18:55\n2026-11-05,05:10,06:25,12:05,15:14,17:39,18:55")
        val now = java.time.ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("Asia/Dubai")).toInstant().toEpochMilli()
        val c = LocalSetup(dubaiCity).toConfig(now, utc, device24h = false) { id -> if (id == "iacad_dubai") table else null }!!
        assertEquals("Asia/Dubai", c.zoneId)
        assertEquals(Profiles.DUBAI_IACAD.id, c.profileId)
        assertEquals("AE", c.countryCode)
        // Same result as the phone would send for the same city.
        assertEquals(2, c.official?.size)
        assertEquals(c.encode(), WatchConfig.decode(c.encode())!!.encode())
        // Far from the table's dates nothing official is attached; the times are calculated.
        assertNull(LocalSetup(dubaiCity).toConfig(0L, utc, false) { table }!!.official)
    }

    @Test fun ownLocationFollowsTheWatchZoneAndClockSetting() {
        val here = dubaiCity.copy(fromGps = true, zoneId = "Asia/Dubai")
        val c = LocalSetup(here).toConfig(0L, ZoneId.of("Asia/Muscat"), device24h = true) { null }!!
        assertEquals("Asia/Muscat", c.zoneId)
        assertTrue(c.use24h)
        assertEquals(false, LocalSetup(here, use24h = false).toConfig(0L, utc, true) { null }!!.use24h)
    }

    @Test fun noPlaceNoConfig() {
        assertNull(LocalSetup().toConfig(0L, utc, false) { null })
    }

    @Test fun alertChoicesMapToTheSharedAlertSettings() {
        val a = LocalSetup(dubaiCity, alertsOn = true, alertStyle = WatchAlertStyle.ADHAN_SHORT).alertSettings()
        assertTrue(a.enabled); assertEquals(AlertDevice.WATCH, a.device)
        assertEquals(AlertMode.ADHAN_SHORT, a.alert(Prayer.FAJR).mode)
        assertEquals(com.noor.prayer.alerts.SoundIds.DEFAULT_FAJR_ADHAN, a.alert(Prayer.FAJR).soundId)
        // Sunrise is off by default and never gets the Adhan.
        assertEquals(false, a.alert(Prayer.SHURUQ).enabled)
        assertEquals(AlertMode.SOUND, a.alert(Prayer.SHURUQ).mode)
        val n = LocalSetup(dubaiCity, alertsOn = true, alertStyle = WatchAlertStyle.NOTIFICATION, alertPrayers = setOf(Prayer.ISHA)).alertSettings()
        assertEquals(listOf(Prayer.ISHA), Prayer.entries.filter { n.alertsOn(it, AlertDevice.WATCH) })
        assertEquals(false, n.alertsOn(Prayer.ISHA, AlertDevice.PHONE))
    }
}
