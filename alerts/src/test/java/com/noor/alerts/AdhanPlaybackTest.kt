package com.noor.alerts

import android.app.AlarmManager
import android.media.AudioManager
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 0.9.7, the scheduled Adhan that stopped after a second or two: on Android 17, sound from an app that is not in front
 * is restricted, and the player used to end the Adhan as soon as it lost audio focus. Now prayer alarms are
 * alarm-clock alarms (the app may show its Adhan screen and play), and only a call ends the Adhan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHostApp::class, sdk = [34])
class AdhanPlaybackTest {
    private val app get() = org.robolectric.RuntimeEnvironment.getApplication() as TestHostApp
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )

    @Test fun prayerAlarmsAreAlarmClocksAndOnlyACallStopsTheAdhan() = runBlocking {
        // 1 · On the phone, each prayer is armed as an alarm clock (exact, never deferred, may show its screen).
        app.alarmClock = true
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.config = dubai.copy(alerts = AlertSettings(enabled = true))
        val am = app.getSystemService(AlarmManager::class.java)
        AlertScheduler.reschedule(app)
        val alarms = shadowOf(am).scheduledAlarms
        assertEquals(5, alarms.size)
        assertTrue("every prayer alarm is an alarm clock", alarms.all { it.alarmClockInfo != null })
        assertNotNull(am.nextAlarmClock)
        // The in-app test goes the same way.
        assertTrue(AlertScheduler.scheduleTest(app, com.noor.prayer.Prayer.ASR, 10))
        assertTrue(shadowOf(am).scheduledAlarms.all { it.alarmClockInfo != null })
        // The watch keeps plain exact alarms.
        app.alarmClock = false
        AlertScheduler.reschedule(app)
        assertTrue(shadowOf(am).scheduledAlarms.filter { it.operation?.creatorPackage != null }.isNotEmpty())

        // 2 · Losing audio focus (another app, or Android 17 withdrawing it from a background app) does not end it.
        val audio = app.getSystemService(AudioManager::class.java)
        val file = File("/data/ok/adhan_full.ogg")
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.absolutePath), ShadowMediaPlayer.MediaInfo(180_000, 0))
        val player = AlertPlayer(app)
        var result: PlayResult? = null
        player.play(SoundSource.LocalFile(file), 80, 0, 0) { result = it }
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertTrue(player.isPlaying)
        val listener = shadowOf(audio).lastAudioFocusRequest.listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS)
        assertTrue("still playing after losing focus", player.isPlaying)
        assertNull(result)

        // 3 · A call starting ends it, and the history says why.
        audio.mode = AudioManager.MODE_IN_CALL
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertFalse(player.isPlaying)
        val r = result!!
        assertTrue(r.played)
        assertTrue("report: ${r.report}", r.report!!.contains("a call started"))
        assertTrue("report: ${r.report}", r.report!!.contains("focus lost"))
        audio.mode = AudioManager.MODE_NORMAL
    }

}

/**
 * Android 13 here: on 14 and later Android first asks whether the user allows full-screen alerts for Noor, which
 * Robolectric cannot answer (on a phone it is allowed by default for an alarm app installed outside Google Play).
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = TestHostApp::class, sdk = [33])
class AdhanFullScreenTest {
    /**
     * 0.9.7: the notification shown while the Adhan plays opens the full-screen Adhan over the lock screen. It must not
     * be a "silent" group child (Android suppresses every interruption, the full-screen one included, for those), and
     * its channel makes no sound of its own (the player plays the Adhan).
     */
    @Test fun theAdhanNotificationCanOpenTheFullScreenAlert() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        AlertNotifications.ensureChannels(app)
        val n = AlertNotifications.playing(app, "Maghrib", "5:38 PM", java.time.Instant.now(), null, "Stop")
        org.junit.Assert.assertNotNull("full-screen intent", n.fullScreenIntent)
        assertEquals(android.app.Notification.CATEGORY_ALARM, n.category)
        assertFalse("not a silent group child", n.group == "silent" || n.groupAlertBehavior == android.app.Notification.GROUP_ALERT_SUMMARY)
        val ch = app.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(AlertNotifications.CHANNEL_ADHAN)
        assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, ch.importance)
        org.junit.Assert.assertNull(ch.sound)
        assertFalse(ch.shouldVibrate())
    }
}
