package com.noor.alerts

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.time.Duration
import java.time.LocalDate

/**
 * The paths that decide whether a prayer is actually heard: every outcome is recorded, a failing sound falls back to
 * Noor's default Adhan, and when nothing can be played by Noor the backup notification (played by Android) is used.
 * One test method: Robolectric shares DataStore-free state badly across methods in this module's forked JVMs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHostApp::class, sdk = [34])
class AlertReliabilityTest {

    private val app get() = org.robolectric.RuntimeEnvironment.getApplication() as TestHostApp
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )

    private fun request(logId: Long, source: SoundSource, fallback: SoundSource?) = PlayRequest(
        notificationId = AlertNotifications.idFor(Prayer.ASR), title = "Time for Asr", body = "3:24 PM · Dubai", atMs = 0L,
        detail = null, stopLabel = "Stop", source = source, volumePercent = 80, gradualSeconds = 0, maxSeconds = 0, vibrate = false,
        keepNotification = true, logId = logId, fallback = fallback, fallbackNote = "fallback note", failedNote = "failed note",
    )

    private fun runService(req: PlayRequest) {
        Robolectric.buildService(AdhanService::class.java, req.toIntent(app)).create().startCommand(0, 1)
        ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun startingRecord(id: Long) = AlertRecord(id, Prayer.ASR, false, 0L, 0L, AlertOutcome.STARTING, "adhan.x")

    @Test fun everyPathIsRecordedAndFallsBackToSomethingAudible() {
        // 1 · The history survives a round trip, keeps the newest first and stays bounded.
        AlertLog.clear(app)
        repeat(AlertLog.MAX + 5) { i -> AlertLog.add(app, startingRecord(i.toLong() + 1).copy(firedAtMs = i.toLong(), playedMs = i * 10L)) }
        val all = AlertLog.all(app)
        assertEquals(AlertLog.MAX, all.size)
        assertEquals(AlertLog.MAX + 5L, all.first().id)
        AlertLog.update(app, all.first().id) { it.copy(outcome = AlertOutcome.PLAYED, stoppedByUser = true) }
        assertEquals(AlertOutcome.PLAYED, AlertLog.all(app).first().outcome)
        assertTrue(AlertLog.all(app).first().stoppedByUser)
        AlertLog.clear(app)

        // 2 · The chosen sound cannot be read: Noor's default plays instead, and the history says so.
        val broken = SoundSource.LocalFile(File("/data/missing/chosen.ogg"))
        val good = SoundSource.LocalFile(File("/data/ok/default_short.ogg"))
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(good.file.absolutePath), ShadowMediaPlayer.MediaInfo(3_000, 0))
        AlertLog.add(app, startingRecord(101))
        runService(request(101, broken, good))
        ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.SECONDS)
        val fb = AlertLog.all(app).single { it.id == 101L }
        assertEquals(AlertOutcome.PLAYED_FALLBACK, fb.outcome)
        assertTrue("sound came out for a while", fb.playedMs > 0)
        assertTrue(fb.detail!!.startsWith("chosen sound:"))

        // 3 · Nothing at all can be played by Noor: the backup notification, whose sound Android plays itself.
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.cancelAll()
        AlertLog.add(app, startingRecord(202))
        runService(request(202, broken, SoundSource.LocalFile(File("/data/missing/also.ogg"))))
        val backup = AlertLog.all(app).single { it.id == 202L }
        assertEquals(AlertOutcome.BACKUP_SOUND, backup.outcome)
        val posted = shadowOf(nm).allNotifications.single { it.channelId == AlertNotifications.CHANNEL_BACKUP }
        assertNotNull(posted)
        val channel = nm.getNotificationChannel(AlertNotifications.CHANNEL_BACKUP)
        assertEquals(AlertNotifications.backupSound(app), channel.sound)
        assertEquals(android.media.AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)

        // 4 · The dispatcher records what it decided, with the device state of the moment.
        val adhan = SoundEntry(com.noor.prayer.alerts.SoundIds.DEFAULT_ADHAN, SoundKind.ADHAN, "Makkah", "مكة", null, null, emptySet(), false,
            "sounds/full.ogg", 176.0, "sounds/short.ogg", 32.0, null)
        SoundLibrary.replaceForTests(SoundLibrary(listOf(adhan), { true }, null))
        AlertLog.clear(app)
        nm.cancelAll()
        val date = LocalDate.of(2026, 11, 7)
        val at = dubai.day(date).instant(Prayer.ASR)!!
        val settings = AlertSettings(enabled = true)
        AlertDispatcher.dispatch(app, dubai, settings, Prayer.ASR, date, at, settings.alert(Prayer.ASR).copy(mode = AlertMode.NOTIFICATION),
            test = false, firedAtMs = at.toEpochMilli() + 1_500)
        val n = AlertLog.all(app).single()
        assertEquals(AlertOutcome.NOTIFICATION, n.outcome)
        assertEquals(Duration.ofMillis(1_500), Duration.ofMillis(n.delayMs))

        // On vibrate, with the default choice, the Adhan goes to the player (it used to become a silent notification).
        AlertLog.clear(app)
        app.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_VIBRATE
        AlertDispatcher.dispatch(app, dubai, settings, Prayer.ASR, date, at, settings.alert(Prayer.ASR), test = false, firedAtMs = at.toEpochMilli())
        val started = shadowOf(app).nextStartedService
        assertEquals(AdhanService.ACTION_PLAY, started?.action)
        val record = AlertLog.all(app).single()
        assertEquals(AlertOutcome.STARTING, record.outcome)
        assertEquals(1, record.ringerMode) // vibrate, recorded for the report
        assertEquals(record.id, started!!.getLongExtra("log", 0L))
        assertNotNull("a fallback is always attached", PlayRequest.from(started)?.fallback)

        // With "stay quiet when silent" chosen, vibrate gives a silent notification, recorded as the user's choice.
        AlertLog.clear(app)
        val quiet = settings.copy(quietWhenSilent = true)
        AlertDispatcher.dispatch(app, dubai, quiet, Prayer.ASR, date, at, quiet.alert(Prayer.ASR), test = false, firedAtMs = at.toEpochMilli())
        assertEquals(AlertOutcome.QUIET_BY_CHOICE, AlertLog.all(app).single().outcome)

        // An alarm that finds no settings is recorded too, instead of vanishing.
        AlertLog.clear(app)
        app.config = null
        app.sendBroadcast(Intent(app, AlertReceiver::class.java).setAction(AlertScheduler.ACTION_FIRE)
            .putExtra(AlertScheduler.EXTRA_PRAYER, Prayer.ISHA.name).putExtra(AlertScheduler.EXTRA_DATE, date.toString()))
        var waited = 0
        while (AlertLog.all(app).isEmpty() && waited < 5_000) { ShadowLooper.idleMainLooper(); Thread.sleep(50); waited += 50 }
        assertEquals(AlertOutcome.NO_SETTINGS, AlertLog.all(app).singleOrNull()?.outcome)
        SoundLibrary.replaceForTests(null)
    }
}
