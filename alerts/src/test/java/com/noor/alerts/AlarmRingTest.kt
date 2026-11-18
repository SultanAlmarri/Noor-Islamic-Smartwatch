package com.noor.alerts

import android.app.NotificationManager
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlarmSound
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PlannedAlarm
import com.noor.prayer.alerts.PrayerAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate

/**
 * 0.21: the user's alarms as they ring (0.17). One test method (see AlertReliabilityTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHostApp::class, sdk = [34])
class AlarmRingTest {

    private val app get() = org.robolectric.RuntimeEnvironment.getApplication() as TestHostApp
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )

    @Test fun aNotificationAlarmBeforeAPrayerLeavesWhenThePrayerBegins() {
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val date = LocalDate.of(2026, 11, 4)
        val asr = dubai.day(date).instant(Prayer.ASR)!!
        val now = System.currentTimeMillis()
        // (The prayer as if it were 15 minutes from now: the note is posted as the alarm rings.)
        val prayerAt = java.time.Instant.ofEpochMilli(now).plus(Duration.ofMinutes(15))
        val before = PrayerAlarm(1, Prayer.ASR, 15, after = false, sound = AlarmSound.NOTIFICATION)
        AlarmDispatcher.ring(app, dubai, AlertSettings(), PlannedAlarm(before, date, prayerAt, prayerAt.minus(Duration.ofMinutes(15))))
        val n = nm.allNotifications.single()
        assertEquals(AlertNotifications.CHANNEL_REMIND, n.channelId)
        assertEquals("15 minutes before Asr", n.extras.getString("android.title"))
        // It goes when Asr begins, as the reminder before a prayer always did (it stayed an hour until 0.20, saying
        // "15 minutes before Asr" long after Asr had come).
        val left = n.timeoutAfter - Duration.ofMinutes(15).toMillis()
        assertTrue("timeout ${n.timeoutAfter} ms", kotlin.math.abs(left) < 5_000)
        app.getSystemService(NotificationManager::class.java).cancelAll()

        // One after a prayer stays its hour.
        val after = before.copy(id = 2, after = true)
        AlarmDispatcher.ring(app, dubai, AlertSettings(), PlannedAlarm(after, date, asr, asr.plus(Duration.ofMinutes(15))))
        assertEquals(AlertNotifications.NOTE_STAYS_MS, nm.allNotifications.single().timeoutAfter)
        app.getSystemService(NotificationManager::class.java).cancelAll()

        // A note whose prayer has come already (rung late, within its ten minutes) still shows for a moment.
        assertEquals(60_000L, AlarmDispatcher.noteStays(PlannedAlarm(before, date, asr, asr.minusSeconds(900)), tried = false, nowMs = asr.toEpochMilli() + 30_000))
        // One being tried stays as long as any.
        assertEquals(AlertNotifications.NOTE_STAYS_MS, AlarmDispatcher.noteStays(PlannedAlarm(before, date, asr, asr), tried = true))
    }
}
