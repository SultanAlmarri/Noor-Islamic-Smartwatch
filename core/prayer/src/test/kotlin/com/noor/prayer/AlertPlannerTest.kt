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
import kotlin.test.assertFalse
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
        val s = AlertSettings(enabled = true, device = AlertDevice.BOTH, gradualSeconds = 20, quietWhenSilent = true)
            .with(Prayer.ASR, PrayerAlert(true, AlertMode.CUSTOM, "custom.5f0c9a52-1b7e-4b39-9a8e-2b8f2a4d7c11", 55, false, 90))
            .with(Prayer.ISHA, PrayerAlert(false, AlertMode.SOUND, "alt.two_ticks"))
        assertEquals(s, AlertSettings.decode(s.encode()))
        assertEquals(AlertSettings(enabled = true), AlertSettings.decode(AlertSettings(enabled = true).encode()))
        // Settings saved by 0.5–0.8 ("dnd=0" was their default) now play like an alarm; only an explicit "quiet=1" stays quiet.
        assertFalse(AlertSettings.decode("v=1;on=1;dev=PHONE;grad=10;dnd=0").quietWhenSilent)
        assertTrue(AlertSettings.decode("v=1;on=1;dnd=0;quiet=1").quietWhenSilent)
        // Watches on 0.5–0.8 read only "dnd": playing like an alarm is written as dnd=1 for them.
        assertTrue(AlertSettings(enabled = true).encode().contains("dnd=1"))
        assertTrue(AlertSettings(enabled = true, quietWhenSilent = true).encode().contains("dnd=0"))
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

    // ---- 0.9.9: reminders before prayers ----

    @Test fun reminderSettingRoundTripsAndOldSettingsAreUnchanged() {
        assertEquals(0, on.reminderMinutes)
        assertFalse(on.encode().contains("pre="), "settings without a reminder encode exactly as before")
        val s = on.copy(reminderMinutes = 15)
        assertEquals(s, AlertSettings.decode(s.encode()))
        assertEquals(s, WatchConfig.decode(dubai.copy(alerts = s).encode())!!.alerts)
        // Only the offered lead times are accepted; anything else is no reminder.
        assertEquals(0, AlertSettings.decode("v=1;on=1;pre=7").reminderMinutes)
        assertEquals(0, AlertSettings.decode("v=1;on=1;pre=-5").reminderMinutes)
        assertEquals(0, AlertSettings.decode("v=1;on=1;pre=x").reminderMinutes)
        assertEquals(30, AlertSettings.decode("v=1;on=1;pre=30").reminderMinutes)
    }

    @Test fun remindersComeBeforeEachAlertingPrayerOnTheAlertingDevice() {
        val now = at(2026, 11, 4, 3, 0)
        assertTrue(AlertPlanner.reminders(dubai, on, AlertDevice.PHONE, now).isEmpty(), "off by default")
        val s = on.copy(reminderMinutes = 15)
        val plan = AlertPlanner.plan(dubai, s, AlertDevice.PHONE, now)
        val rem = AlertPlanner.reminders(dubai, s, AlertDevice.PHONE, now)
        // The same prayers, at the same times, as the alerts themselves; each 15 minutes ahead.
        assertEquals(plan.map { it.prayer to it.at }, rem.map { it.prayer to it.at })
        assertTrue(rem.all { Duration.between(it.remindAt, it.at) == Duration.ofMinutes(15) })
        assertEquals(5, rem.size) // sunrise is off by default
        // Only on the device that alerts.
        assertTrue(AlertPlanner.reminders(dubai, s.copy(device = AlertDevice.WATCH), AlertDevice.PHONE, now).isEmpty())
        assertEquals(rem, AlertPlanner.reminders(dubai, s.copy(device = AlertDevice.BOTH), AlertDevice.WATCH, now))
        assertTrue(AlertPlanner.reminders(dubai, s.copy(enabled = false), AlertDevice.PHONE, now).isEmpty())
        // A prayer whose alert is off has no reminder either.
        val noAsr = s.with(Prayer.ASR, s.alert(Prayer.ASR).copy(enabled = false))
        assertFalse(AlertPlanner.reminders(dubai, noAsr, AlertDevice.PHONE, now).any { it.prayer == Prayer.ASR })
        // Once a reminder's moment has passed, that prayer's next reminder is tomorrow's.
        val asr = rem.first { it.prayer == Prayer.ASR }
        val next = AlertPlanner.reminders(dubai, s, AlertDevice.PHONE, asr.remindAt.plusSeconds(60)).first { it.prayer == Prayer.ASR }
        assertEquals(asr.date.plusDays(1), next.date)
    }

    @Test fun aReminderNeverComesBeforeThePreviousPrayer() {
        // Maghrib 30 minutes later and Isha 30 earlier (the largest personal offsets): about 20 minutes apart.
        val squeezed = dubai.copy(profileId = Profiles.MWL.id, offsets = mapOf(Prayer.MAGHRIB to 30, Prayer.ISHA to -30))
        val day = squeezed.day(LocalDate.of(2026, 11, 4))
        val gap = Duration.between(day.instant(Prayer.MAGHRIB)!!, day.instant(Prayer.ISHA)!!)
        assertTrue(gap < Duration.ofMinutes(30), "test set-up: the gap is $gap")
        val now = at(2026, 11, 4, 12, 0)
        val r30 = AlertPlanner.reminders(squeezed, on.copy(reminderMinutes = 30), AlertDevice.PHONE, now)
        assertFalse(r30.any { it.prayer == Prayer.ISHA && it.date == day.date }, "Isha's reminder would come before Maghrib")
        assertTrue(r30.any { it.prayer == Prayer.MAGHRIB })
        // With a lead shorter than the gap it is there.
        assertTrue(AlertPlanner.reminders(squeezed, on.copy(reminderMinutes = 10), AlertDevice.PHONE, now).any { it.prayer == Prayer.ISHA && it.date == day.date })
    }

    @Test fun aFiringReminderIsRecheckedAndStatesTheMinutesLeft() {
        val s = on.copy(reminderMinutes = 10)
        val r = AlertPlanner.reminders(dubai, s, AlertDevice.PHONE, at(2026, 11, 4, 3, 0)).first { it.prayer == Prayer.ASR }
        fun fire(now: java.time.Instant, settings: AlertSettings = s) = AlertPlanner.onRemind(dubai, settings, AlertDevice.PHONE, Prayer.ASR, r.date, now)
        assertEquals(10, fire(r.remindAt)!!.minutesLeft)
        assertEquals(r.at, fire(r.remindAt)!!.at)
        // A little late (deep sleep): it says how long is really left.
        assertEquals(7, fire(r.remindAt.plusSeconds(180))!!.minutesLeft)
        // Under a minute before the prayer: the Adhan itself is about to come, so no reminder.
        assertEquals(null, fire(r.at.minusSeconds(30)))
        // Fired well before its moment (the times moved later), turned off, or alerts off: nothing.
        assertEquals(null, fire(r.remindAt.minus(Duration.ofMinutes(5))))
        assertEquals(null, fire(r.remindAt, s.copy(reminderMinutes = 0)))
        assertEquals(null, fire(r.remindAt, s.copy(enabled = false)))
        assertEquals(null, fire(r.remindAt, s.with(Prayer.ASR, s.alert(Prayer.ASR).copy(enabled = false))))
    }
}
