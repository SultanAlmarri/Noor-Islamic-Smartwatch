package com.noor.prayer

import com.noor.prayer.alerts.AlarmPlanner
import com.noor.prayer.alerts.AlarmSound
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlarm
import com.noor.prayer.alerts.PrayerAlert
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 0.17: alarms set a number of minutes before or after a prayer. */
class PrayerAlarmTest {
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of("Asia/Dubai")).toInstant()

    private val beforeFajr = PrayerAlarm(0, Prayer.FAJR, 25, after = false, label = "Wake up, tahajjud; then Fajr = 100%|ok")
    private val afterDhuhr = PrayerAlarm(0, Prayer.DHUHR, 10, after = true, days = 0b0011111, sound = AlarmSound.NOTIFICATION, snoozeMinutes = 0)

    @Test fun alarmsRoundTripAndOldSettingsHaveNone() {
        val s = AlertSettings(enabled = true).withAlarm(beforeFajr).withAlarm(afterDhuhr)
        assertEquals(listOf(1, 2), s.alarms.map { it.id })
        assertEquals(s, AlertSettings.decode(s.encode()))
        // The label survives any character the format itself uses.
        assertEquals(beforeFajr.label, AlertSettings.decode(s.encode()).alarms[0].label)
        // Settings without alarms encode exactly as before (no new field), and old text has no alarms.
        assertFalse(AlertSettings(enabled = true).encode().contains("alarms="))
        assertTrue(AlertSettings.decode("v=1;on=1;dev=PHONE").alarms.isEmpty())
        // A damaged entry is dropped, the others kept; ids out of range and repeats too.
        val d = AlertSettings.decode("v=1;on=1;alarms=1,1,FAJR,B,25,127,PHONE_ALARM,,1,10,|x,y|2,1,NOPE,A,5,127,PHONE_ALARM,,1,10,|99,1,ASR,A,5,127,PHONE_ALARM,,1,10,|1,0,ASR,A,5,127,PHONE_ALARM,,1,10,")
        assertEquals(listOf(1), d.alarms.map { it.id })
        // Out-of-range values are brought into range; a custom sound without a file falls back to the phone's alarm.
        val n = PrayerAlarm(3, Prayer.ASR, 999, true, days = 999, sound = AlarmSound.CUSTOM, snoozeMinutes = 7).normalized()
        assertEquals(PrayerAlarm.MAX_MINUTES, n.minutes)
        assertEquals(PrayerAlert.ALL_DAYS, n.days)
        assertEquals(AlarmSound.PHONE_ALARM, n.sound)
        assertEquals(0, n.snoozeMinutes)
    }

    @Test fun idsAreReusedAndLimited() {
        var s = AlertSettings()
        repeat(PrayerAlarm.MAX) { s = s.withAlarm(beforeFajr) }
        assertEquals(PrayerAlarm.MAX, s.alarms.size)
        assertNull(s.nextAlarmId())
        assertEquals(s, s.withAlarm(beforeFajr), "no more than the maximum")
        s = s.withoutAlarm(7)
        assertEquals(7, s.nextAlarmId())
        // Editing keeps its place in the list.
        val edited = s.withAlarm(s.alarms[2].copy(minutes = 40))
        assertEquals(40, edited.alarms[2].minutes)
        assertEquals(s.alarms.map { it.id }, edited.alarms.map { it.id })
    }

    @Test fun ringsAtThePrayersTimePlusOrMinusItsMinutes() {
        val now = at(2026, 11, 16, 0, 30)
        val alarm = AlertSettings().withAlarm(beforeFajr).alarms[0]
        val p = AlarmPlanner.next(dubai, alarm, now)!!
        val fajr = dubai.day(p.date).instant(Prayer.FAJR)!!
        assertEquals(fajr, p.prayerAt)
        assertEquals(fajr.minus(Duration.ofMinutes(25)), p.at)
        assertEquals(java.time.LocalDate.of(2026, 11, 16), p.date)
        // Once it has rung, the next is the next day's, at that day's Fajr (which moves through the year).
        val q = AlarmPlanner.next(dubai, alarm, p.at)!!
        assertEquals(p.date.plusDays(1), q.date)
        assertEquals(dubai.day(q.date).instant(Prayer.FAJR)!!.minus(Duration.ofMinutes(25)), q.at)
        // After a prayer: the prayer's time plus the minutes.
        val a = AlarmPlanner.next(dubai, AlertSettings().withAlarm(afterDhuhr.copy(days = PrayerAlert.ALL_DAYS)).alarms[0], now)!!
        assertEquals(dubai.day(a.date).instant(Prayer.DHUHR)!!.plus(Duration.ofMinutes(10)), a.at)
    }

    @Test fun onlyOnItsDaysAndOnlyWhenOn() {
        // Monday to Friday only: from a Friday evening, the next is Monday's.
        val friday = at(2026, 11, 20, 20, 0)
        assertEquals(DayOfWeek.FRIDAY, friday.atZone(dubai.zone).dayOfWeek)
        val s = AlertSettings().withAlarm(afterDhuhr)
        assertEquals(DayOfWeek.MONDAY, AlarmPlanner.next(dubai, s.alarms[0], friday)!!.date.dayOfWeek)
        assertTrue(AlarmPlanner.plan(dubai, s.withAlarm(s.alarms[0].copy(enabled = false)), friday).isEmpty())
        assertNull(AlarmPlanner.next(dubai, s.alarms[0].copy(days = 0), friday))
        // The plan is soonest first, and does not need the Adhan to be on.
        val both = AlertSettings(enabled = false).withAlarm(afterDhuhr.copy(days = PrayerAlert.ALL_DAYS)).withAlarm(beforeFajr)
        val plan = AlarmPlanner.plan(dubai, both, at(2026, 11, 16, 0, 30))
        assertEquals(listOf(Prayer.FAJR, Prayer.DHUHR), plan.map { it.alarm.prayer })
    }

    @Test fun afterIshaCanRingPastMidnight() {
        // London in July: Isha is late, and three hours after it is the next morning, on the day after Isha's date.
        val london = dubai.copy(point = GeoPoint(51.5074, -0.1278, 11.0), zoneId = "Europe/London", profileId = Profiles.MWL.id)
        val day = java.time.LocalDate.of(2026, 7, 10)
        val isha = london.day(day).instant(Prayer.ISHA)!!
        val alarm = AlertSettings().withAlarm(PrayerAlarm(0, Prayer.ISHA, 180, true)).alarms[0]
        val ring = isha.plus(Duration.ofMinutes(180))
        assertTrue(ring.atZone(london.zone).toLocalDate().isAfter(day), "rings after midnight")
        // Seen from after midnight, before it rings: the day before's Isha counts.
        val p = AlarmPlanner.next(london, alarm, ring.minus(Duration.ofMinutes(20)))!!
        assertEquals(day, p.date)
        assertEquals(ring, p.at)
        assertNotNull(AlarmPlanner.onFire(london, AlertSettings().withAlarm(alarm), alarm.id, day, ring))
    }

    @Test fun aFiringAlarmIsCheckedAgain() {
        val s = AlertSettings().withAlarm(beforeFajr)
        val p = AlarmPlanner.next(dubai, s.alarms[0], at(2026, 11, 16, 0, 30))!!
        fun fire(t: java.time.Instant, x: AlertSettings = s) = AlarmPlanner.onFire(dubai, x, p.alarm.id, p.date, t)
        assertNotNull(fire(p.at))
        assertNotNull(fire(p.at.plus(Duration.ofMinutes(9))))
        assertNull(fire(p.at.plus(Duration.ofMinutes(11))), "too late to wake anyone for")
        assertNull(fire(p.at.minus(Duration.ofMinutes(5))), "the prayer moved later: armed again, not rung early")
        assertNull(fire(p.at, s.withAlarm(s.alarms[0].copy(enabled = false))), "turned off")
        assertNull(fire(p.at, s.withoutAlarm(p.alarm.id)), "deleted")
    }

    @Test fun oldRemindersBecomeAlarms() {
        val on = AlertSettings(enabled = true)
        // 0.9.9's one lead for every prayer, and 0.16's per-prayer leads and days.
        val old = AlertSettings.decode(on.copy(reminderMinutes = 15).encode())
        val m = old.remindersAsAlarms()
        assertEquals(0, m.reminderMinutes)
        assertEquals(Prayer.entries.filter { old.alert(it).enabled }, m.alarms.map { it.prayer })
        assertTrue(m.alarms.all { !it.after && it.minutes == 15 && it.sound == AlarmSound.NOTIFICATION && it.enabled })
        val v016 = on.with(Prayer.ASR, on.alert(Prayer.ASR).copy(beforeMinutes = 10, afterMinutes = 20, days = 0b0011111))
        val m2 = v016.remindersAsAlarms()
        assertEquals(listOf(false to 10, true to 20), m2.alarms.map { it.after to it.minutes })
        assertTrue(m2.alarms.all { it.prayer == Prayer.ASR && it.days == 0b0011111 })
        assertTrue(Prayer.entries.all { m2.alert(it).beforeMinutes == 0 && m2.alert(it).afterMinutes == 0 })
        // Alerts that were off: the alarms come over off too (they did not remind).
        assertTrue(v016.copy(enabled = false).remindersAsAlarms().alarms.none { it.enabled })
        // Nothing to bring over: the same settings.
        assertEquals(on, on.remindersAsAlarms())
        assertEquals(m2, m2.remindersAsAlarms())
    }
}
