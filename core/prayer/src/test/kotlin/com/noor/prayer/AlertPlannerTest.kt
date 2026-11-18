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

    // ---- 0.16: days of the week, a reminder before and after each prayer, all prayers at once ----

    @Test fun aPrayerAlertsOnlyOnItsDaysOfTheWeek() {
        // 4 November 2026 is a Wednesday. Dhuhr on Fridays only (for instance, not on the others, or the reverse).
        val friday = 1 shl (java.time.DayOfWeek.FRIDAY.value - 1)
        val s = on.with(Prayer.DHUHR, on.alert(Prayer.DHUHR).copy(days = friday))
        val now = at(2026, 11, 4, 3, 0)
        val dhuhr = AlertPlanner.plan(dubai, s, AlertDevice.PHONE, now).first { it.prayer == Prayer.DHUHR }
        assertEquals(LocalDate.of(2026, 11, 6), dhuhr.date, "the next Friday, two days on")
        // The other prayers are unchanged.
        assertEquals(AlertPlanner.plan(dubai, on, AlertDevice.PHONE, now).filter { it.prayer != Prayer.DHUHR },
            AlertPlanner.plan(dubai, s, AlertDevice.PHONE, now).filter { it.prayer != Prayer.DHUHR })
        // A firing alarm on a day that is off does nothing (the days were changed after it was set).
        assertEquals(FireDecision.Skip, AlertPlanner.onFire(dubai, s, AlertDevice.PHONE, Prayer.DHUHR, LocalDate.of(2026, 11, 4),
            dubai.day(LocalDate.of(2026, 11, 4)).instant(Prayer.DHUHR)!!))
        // Every day off: nothing planned for it at all, within the week ahead.
        val never = on.with(Prayer.ASR, on.alert(Prayer.ASR).copy(days = 0))
        assertFalse(AlertPlanner.plan(dubai, never, AlertDevice.PHONE, now).any { it.prayer == Prayer.ASR })
    }

    @Test fun eachPrayerHasItsOwnReminderAndOldSettingsKeepTheirs() {
        val now = at(2026, 11, 4, 3, 0)
        // Before 0.16 one lead served every prayer: it still does.
        val old = AlertSettings.decode(on.copy(reminderMinutes = 15).encode())
        assertTrue(Prayer.entries.filter { old.alert(it).enabled }.all { old.beforeOf(it) == 15 })
        // One prayer's own lead, the old one cleared: only that prayer has a reminder.
        val one = on.withBefore(Prayer.ASR, 20)
        val rem = AlertPlanner.reminders(dubai, one, AlertDevice.PHONE, now)
        assertEquals(listOf(Prayer.ASR), rem.map { it.prayer })
        assertEquals(Duration.ofMinutes(20), Duration.between(rem[0].remindAt, rem[0].at))
        // Turning one prayer's reminder off while the old lead is set leaves the others theirs.
        val mixed = on.copy(reminderMinutes = 15).withBefore(Prayer.MAGHRIB, 0)
        assertEquals(0, mixed.reminderMinutes)
        assertFalse(AlertPlanner.reminders(dubai, mixed, AlertDevice.PHONE, now).any { it.prayer == Prayer.MAGHRIB })
        assertTrue(AlertPlanner.reminders(dubai, mixed, AlertDevice.PHONE, now).any { it.prayer == Prayer.ISHA })
        // For all prayers at once.
        val all = on.withBeforeForAll(10)
        assertTrue(Prayer.entries.all { all.alert(it).beforeMinutes == 10 })
        // Round trips, and an old watch still reads the shared lead.
        assertEquals(mixed, AlertSettings.decode(mixed.encode()))
        assertEquals(all, AlertSettings.decode(all.encode()))
        assertTrue(all.encode().contains("pre=10"))
        assertFalse(mixed.encode().contains("pre="), "leads that differ: no single lead for old watches")
        // Settings without the new fields encode exactly as before.
        assertFalse(on.encode().split(';').any { f -> f.startsWith("FAJR=") && f.split(',').size > 6 })
    }

    @Test fun aReminderAfterThePrayerForTheIqamah() {
        val now = at(2026, 11, 4, 3, 0)
        val s = on.with(Prayer.ASR, on.alert(Prayer.ASR).copy(afterMinutes = 20))
        val after = AlertPlanner.afterReminders(dubai, s, AlertDevice.PHONE, now)
        assertEquals(listOf(Prayer.ASR), after.map { it.prayer })
        assertTrue(after[0].after)
        assertEquals(Duration.ofMinutes(20), Duration.between(after[0].at, after[0].remindAt))
        assertEquals(s, AlertSettings.decode(s.encode()))
        // Fired on time it says how long since the prayer began; far too early or long after, nothing.
        fun fire(t: java.time.Instant) = AlertPlanner.onAfter(dubai, s, AlertDevice.PHONE, Prayer.ASR, after[0].date, t)
        assertEquals(20, fire(after[0].remindAt)!!.minutesLeft)
        assertEquals(null, fire(after[0].remindAt.minus(Duration.ofMinutes(5))))
        assertEquals(null, fire(after[0].remindAt.plus(Duration.ofMinutes(45))))
        // Off on that day, or alerts off: none.
        assertTrue(AlertPlanner.afterReminders(dubai, s.copy(enabled = false), AlertDevice.PHONE, now).isEmpty())
        // Once its moment has passed, the next is the next day's.
        val next = AlertPlanner.afterReminders(dubai, s, AlertDevice.PHONE, after[0].remindAt.plusSeconds(60)).first()
        assertEquals(after[0].date.plusDays(1), next.date)
    }

    /** 0.22: a prayer of a day already dealt with is never planned again (the clock moved back, an alarm a little early). */
    @Test fun aPrayerAlreadyHandledIsNeverPlannedTwice() {
        val date = LocalDate.of(2026, 11, 4)
        val dhuhr = dubai.day(date).instant(Prayer.DHUHR)!!
        val h: (Prayer, LocalDate) -> Boolean = { p, d -> p == Prayer.DHUHR && d == date }
        // The Adhan has been called at Dhuhr; then the phone's clock is put back five minutes (network time, by hand).
        val back = dhuhr.minus(Duration.ofMinutes(5))
        // Without the record, today's Dhuhr is armed again and called a second time.
        assertEquals(date, AlertPlanner.plan(dubai, on, AlertDevice.PHONE, back).first { it.prayer == Prayer.DHUHR }.date)
        assertEquals(date.plusDays(1), AlertPlanner.plan(dubai, on, AlertDevice.PHONE, back, handled = h).first { it.prayer == Prayer.DHUHR }.date)
        // The other prayers are untouched.
        assertEquals(AlertPlanner.plan(dubai, on, AlertDevice.PHONE, back).filter { it.prayer != Prayer.DHUHR },
            AlertPlanner.plan(dubai, on, AlertDevice.PHONE, back, handled = h).filter { it.prayer != Prayer.DHUHR })
    }

    /**
     * 0.22: an alarm armed and not yet delivered when its time has come is kept, not replaced by the next day's (which
     * lost the alert without a trace): armed again at its own time, so it is delivered at once and judged as it fires.
     */
    @Test fun anAlarmDueButNotYetDeliveredIsKept() {
        val date = LocalDate.of(2026, 11, 4)
        val dhuhr = dubai.day(date).instant(Prayer.DHUHR)!!
        val pending = mapOf(Prayer.DHUHR to com.noor.prayer.alerts.Armed(date, dhuhr))
        val now = dhuhr.plus(Duration.ofMinutes(4))
        // Without it (as up to 0.21), the plan moves on to tomorrow's Dhuhr: today's was never delivered, nor noted.
        assertEquals(date.plusDays(1), AlertPlanner.plan(dubai, on, AlertDevice.PHONE, now).first { it.prayer == Prayer.DHUHR }.date)
        val kept = AlertPlanner.plan(dubai, on, AlertDevice.PHONE, now, pending = pending).first { it.prayer == Prayer.DHUHR }
        assertEquals(date, kept.date); assertEquals(dhuhr, kept.at)
        // ...and it fires as a delivered-late alarm would: within ten minutes, the Adhan.
        assertIs<FireDecision.Alert>(AlertPlanner.onFire(dubai, on, AlertDevice.PHONE, Prayer.DHUHR, date, now))
        // Once handled, or more than three hours on, or turned off, it is not kept.
        assertEquals(date.plusDays(1), AlertPlanner.plan(dubai, on, AlertDevice.PHONE, now, { p, d -> p == Prayer.DHUHR && d == date }, pending)
            .first { it.prayer == Prayer.DHUHR }.date)
        assertEquals(date.plusDays(1), AlertPlanner.plan(dubai, on, AlertDevice.PHONE, dhuhr.plus(Duration.ofHours(4)), pending = pending)
            .first { it.prayer == Prayer.DHUHR }.date)
        val off = on.with(Prayer.DHUHR, on.alert(Prayer.DHUHR).copy(enabled = false))
        assertTrue(AlertPlanner.plan(dubai, off, AlertDevice.PHONE, now, pending = pending).none { it.prayer == Prayer.DHUHR })
        // One still to come is planned as usual.
        assertEquals(AlertPlanner.plan(dubai, on, AlertDevice.PHONE, dhuhr.minusSeconds(60)),
            AlertPlanner.plan(dubai, on, AlertDevice.PHONE, dhuhr.minusSeconds(60), pending = pending))
    }
}
