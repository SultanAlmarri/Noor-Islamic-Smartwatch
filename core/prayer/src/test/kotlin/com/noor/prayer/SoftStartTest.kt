package com.noor.prayer

import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlert
import com.noor.prayer.alerts.PrayerAlarm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 0.26: "Start softly" is each prayer's own choice, off by default (the Adhan at its full volume from the first second),
 * and settings written before 0.26, with one rise for every prayer, are read as they were meant.
 */
class SoftStartTest {
    private val on = AlertSettings(enabled = true)

    @Test fun offByDefaultForEveryPrayer() {
        assertTrue(Prayer.entries.all { AlertSettings().alert(it).gradualSeconds == 0 })
        assertTrue(Prayer.entries.all { AlertSettings.decode(on.encode()).alert(it).gradualSeconds == 0 })
        assertTrue(Prayer.entries.all { AlertSettings.decode("garbage").alert(it).gradualSeconds == 0 })
    }

    @Test fun eachPrayerKeepsItsOwnRiseThroughARoundTrip() {
        val s = on.with(Prayer.FAJR, on.alert(Prayer.FAJR).copy(gradualSeconds = PrayerAlert.SOFT_START_SECONDS))
            .with(Prayer.ISHA, on.alert(Prayer.ISHA).copy(gradualSeconds = 30, days = 0b0011111))
        val back = AlertSettings.decode(s.encode())
        assertEquals(s, back)
        assertEquals(10, back.alert(Prayer.FAJR).gradualSeconds)
        assertEquals(30, back.alert(Prayer.ISHA).gradualSeconds)
        assertEquals(0b0011111, back.alert(Prayer.ISHA).days)
        assertEquals(0, back.alert(Prayer.DHUHR).gradualSeconds)
        // Through the watch's config as well.
        val dubai = WatchConfig(
            point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
            profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
            offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
        )
        assertEquals(s, WatchConfig.decode(dubai.copy(alerts = s).encode())!!.alerts)
        // Alarms and the rest survive beside it.
        val withAlarm = s.withAlarm(PrayerAlarm(0, Prayer.FAJR, 25, after = false))
        assertEquals(withAlarm, AlertSettings.decode(withAlarm.encode()))
    }

    @Test fun theNewFieldStandsAtTheEndWhereOlderReadersStop() {
        val s = on.with(Prayer.ASR, on.alert(Prayer.ASR).copy(gradualSeconds = 10))
        val asr = s.encode().split(';').first { it.startsWith("ASR=") }.substringAfter('=').split(',')
        // Enabled, mode, sound, volume, vibrate, length (read by every version), then days, before, after (0.16), then the rise.
        assertEquals(10, asr.size)
        assertEquals("1", asr[0]); assertEquals("ADHAN", asr[1]); assertEquals("127", asr[6]); assertEquals("10", asr[9])
        // A prayer without its own rise, every day, no reminders: its line is as short as before 0.16.
        assertEquals(6, s.encode().split(';').first { it.startsWith("DHUHR=") }.substringAfter('=').split(',').size)
        // A reader that knows only the six first fields reads the same alert.
        val old = AlertSettings.decode(s.encode().split(';').joinToString(";") { f ->
            if (Prayer.entries.any { f.startsWith(it.name + "=") }) f.split(',').take(6).joinToString(",") else f })
        assertEquals(s.alert(Prayer.ASR).copy(gradualSeconds = 0), old.alert(Prayer.ASR))
        // The global field, read by watches up to 0.25, keeps its own value.
        assertTrue(s.encode().contains("grad=0"))
    }

    @Test fun settingsFromBefore026GiveTheirOneRiseToEveryPrayer() {
        // As 0.25 wrote them: one rise ("grad") for all, no "pgrad".
        val old = "v=1;on=1;dev=PHONE;grad=20;dnd=1;quiet=0;FAJR=1,ADHAN,adhan.dubai_fajr,80,1,0;ASR=1,ADHAN,adhan.makkah,60,1,0,31,0,0"
        val s = AlertSettings.decode(old)
        assertEquals(20, s.gradualSeconds)
        assertTrue(Prayer.entries.all { s.alert(it).gradualSeconds == 20 }, "every prayer rises as before")
        assertEquals(31, s.alert(Prayer.ASR).days)
        // Written again, it says so in each prayer's own line, and reads back the same.
        assertEquals(s, AlertSettings.decode(s.encode()))
        // A stored value beyond what the player takes is held to a minute; a damaged one is none.
        assertEquals(60, AlertSettings.decode("v=1;pgrad=1;FAJR=1,ADHAN,,80,1,0,127,0,0,900").alert(Prayer.FAJR).gradualSeconds)
        assertEquals(0, AlertSettings.decode("v=1;pgrad=1;FAJR=1,ADHAN,,80,1,0,127,0,0,x").alert(Prayer.FAJR).gradualSeconds)
    }

    @Test fun theOneTimeChangeTurnsEveryRiseOff() {
        val s = AlertSettings.decode("v=1;on=1;dev=BOTH;grad=20;dnd=1;quiet=0").withAlarm(PrayerAlarm(0, Prayer.FAJR, 25, after = false))
            .with(Prayer.MAGHRIB, AlertSettings().alert(Prayer.MAGHRIB).copy(volumePercent = 55, vibrate = false, gradualSeconds = 20))
        val full = s.fullVolumeFromStart()
        assertEquals(0, full.gradualSeconds)
        assertTrue(Prayer.entries.all { full.alert(it).gradualSeconds == 0 })
        // Nothing else changes.
        assertEquals(s.alarms, full.alarms)
        assertEquals(AlertDevice.BOTH, full.device)
        assertEquals(55, full.alert(Prayer.MAGHRIB).volumePercent)
        assertFalse(full.alert(Prayer.MAGHRIB).vibrate)
        assertEquals(Prayer.entries.map { s.alert(it).copy(gradualSeconds = 0) }, Prayer.entries.map { full.alert(it) })
        assertEquals(full, AlertSettings.decode(full.encode()))
    }
}
