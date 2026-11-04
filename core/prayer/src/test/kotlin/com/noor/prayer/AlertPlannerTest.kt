package com.noor.prayer

import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertPlanner
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.FireDecision
import com.noor.prayer.alerts.PrayerAlert
import com.noor.prayer.alerts.SoundIds
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlertPlannerTest {

    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )
    private val on = AlertSettings(enabled = true)
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: String = "Asia/Dubai") =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of(zone)).toInstant()

    @Test fun settingsRoundTripAndTolerateDamage() {
        val s = AlertSettings(enabled = true, device = AlertDevice.BOTH, gradualSeconds = 20, playInDnd = true)
            .with(Prayer.ASR, PrayerAlert(true, AlertMode.CUSTOM, "custom.5f0c9a52-1b7e-4b39-9a8e-2b8f2a4d7c11", 55, false, 90))
            .with(Prayer.ISHA, PrayerAlert(false, AlertMode.SOUND, "alt.two_ticks"))
        assertEquals(s, AlertSettings.decode(s.encode()))
        // Unknown keys, a bad mode and a bad id fall back field by field.
        val damaged = AlertSettings.decode("v=9;on=1;future=x;ASR=1,LOUDER,BAD ID!,500,1,0")
        assertTrue(damaged.enabled)
        assertEquals(AlertMode.ADHAN, damaged.alert(Prayer.ASR).mode)
        assertEquals(null, damaged.alert(Prayer.ASR).soundId)
        assertEquals(100, damaged.alert(Prayer.ASR).volumePercent)
        assertEquals(AlertSettings(), AlertSettings.decode(null))
        assertEquals(AlertSettings(), AlertSettings.decode("garbage"))
    }

    @Test fun offByDefaultAndFajrHasItsOwnAdhan() {
        val d = AlertSettings()
        assertEquals(false, d.enabled)
        assertEquals(SoundIds.DEFAULT_FAJR_ADHAN, d.alert(Prayer.FAJR).soundId)
        assertEquals(SoundIds.DEFAULT_ADHAN, d.alert(Prayer.DHUHR).soundId)
        assertEquals(false, d.alert(Prayer.SHURUQ).enabled)
        assertTrue(AlertPlanner.plan(dubai, d, AlertDevice.PHONE, at(2026, 11, 4, 3, 0)).isEmpty())
    }

    @Test fun theAdhanIsNeverCalledForSunrise() {
        val s = on.with(Prayer.SHURUQ, PrayerAlert(true, AlertMode.ADHAN, SoundIds.DEFAULT_ADHAN))
        assertEquals(AlertMode.SOUND, s.alert(Prayer.SHURUQ).mode)
        assertEquals(AlertMode.SOUND, AlertSettings.decode(s.encode().replace("SHURUQ=1,SOUND", "SHURUQ=1,ADHAN_SHORT")).alert(Prayer.SHURUQ).mode)
    }

    @Test fun plansTheNextOccurrenceOfEachEnabledPrayer() {
        val now = at(2026, 11, 4, 13, 0) // after Dhuhr, before Asr
        val plan = AlertPlanner.plan(dubai, on, AlertDevice.PHONE, now)
        assertEquals(listOf(Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA, Prayer.FAJR, Prayer.DHUHR), plan.map { it.prayer })
        assertTrue(plan.all { it.at.isAfter(now) })
        assertEquals(LocalDate.of(2026, 11, 5), plan.first { it.prayer == Prayer.FAJR }.date)
        assertEquals(LocalDate.of(2026, 11, 5), plan.first { it.prayer == Prayer.DHUHR }.date)
        // Each planned time is exactly the displayed time.
        plan.forEach { assertEquals(dubai.day(it.date).instant(it.prayer), it.at) }
    }

    @Test fun deviceChoiceDecidesWhoAlerts() {
        val now = at(2026, 11, 4, 13, 0)
        val watchOnly = on.copy(device = AlertDevice.WATCH)
        assertTrue(AlertPlanner.plan(dubai, watchOnly, AlertDevice.PHONE, now).isEmpty())
        assertEquals(5, AlertPlanner.plan(dubai, watchOnly, AlertDevice.WATCH, now).size)
        val both = on.copy(device = AlertDevice.BOTH)
        assertEquals(5, AlertPlanner.plan(dubai, both, AlertDevice.PHONE, now).size)
        assertEquals(5, AlertPlanner.plan(dubai, both, AlertDevice.WATCH, now).size)
        assertEquals(4, AlertPlanner.plan(dubai, on.with(Prayer.ASR, on.alert(Prayer.ASR).copy(enabled = false)), AlertDevice.PHONE, now).size)
    }

    @Test fun firingAlarmsAreCheckedAgainstCurrentTimes() {
        val date = LocalDate.of(2026, 11, 4)
        val asr = dubai.day(date).instant(Prayer.ASR)!!
        assertIs<FireDecision.Alert>(AlertPlanner.onFire(dubai, on, AlertDevice.PHONE, Prayer.ASR, date, asr.plusSeconds(3)))
        val late = AlertPlanner.onFire(dubai, on, AlertDevice.PHONE, Prayer.ASR, date, asr.plus(Duration.ofMinutes(25)))
        assertIs<FireDecision.Late>(late); assertEquals(Duration.ofMinutes(25), late.lateBy)
        assertEquals(FireDecision.Skip, AlertPlanner.onFire(dubai, on, AlertDevice.PHONE, Prayer.ASR, date, asr.plus(Duration.ofHours(5))))
        // The user moved Asr 5 minutes later after the alarm was set: the old alarm is stale.
        val moved = dubai.copy(offsets = mapOf(Prayer.ASR to 5))
        assertEquals(FireDecision.Skip, AlertPlanner.onFire(moved, on, AlertDevice.PHONE, Prayer.ASR, date, asr))
        // Alerts turned off, or moved to the watch, since the alarm was set.
        assertEquals(FireDecision.Skip, AlertPlanner.onFire(dubai, on.copy(enabled = false), AlertDevice.PHONE, Prayer.ASR, date, asr))
        assertEquals(FireDecision.Skip, AlertPlanner.onFire(dubai, on.copy(device = AlertDevice.WATCH), AlertDevice.PHONE, Prayer.ASR, date, asr))
    }

    @Test fun plansCorrectlyAcrossDaylightSavingChanges() {
        // London, the night the clocks go back (25 Oct 2026).
        val london = dubai.copy(point = GeoPoint(51.5074, -0.1278), zoneId = "Europe/London", profileId = Profiles.MWL.id)
        val now = at(2026, 10, 24, 22, 0, "Europe/London")
        val fajr = AlertPlanner.plan(london, on, AlertDevice.PHONE, now).first { it.prayer == Prayer.FAJR }
        assertEquals(LocalDate.of(2026, 10, 25), fajr.date)
        val local = fajr.at.atZone(ZoneId.of("Europe/London"))
        assertEquals(london.day(fajr.date).minutes[Prayer.FAJR], local.hour * 60 + local.minute)
    }

    @Test fun watchConfigCarriesAlertSettings() {
        val s = on.copy(device = AlertDevice.BOTH).with(Prayer.FAJR, PrayerAlert(true, AlertMode.ADHAN_SHORT, "adhan.istanbul_2025", 40))
        val back = WatchConfig.decode(dubai.copy(alerts = s).encode())!!
        assertEquals(s, back.alerts)
        assertEquals(null, WatchConfig.decode(dubai.encode())!!.alerts)
    }
}
