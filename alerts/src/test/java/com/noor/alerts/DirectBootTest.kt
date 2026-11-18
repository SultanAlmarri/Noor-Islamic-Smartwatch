package com.noor.alerts

import android.app.AlarmManager
import android.os.UserManager
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlert
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 0.22: after a restart, before the phone is first unlocked (Direct Boot: Samsung phones restart themselves at night),
 * the prayer alarms are armed and sound from the copy kept with them in device-protected storage; the app's own
 * settings, which cannot be read then, are not asked. One test method (see AlertReliabilityTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHostApp::class, sdk = [34])
class DirectBootTest {

    private val app get() = org.robolectric.RuntimeEnvironment.getApplication() as TestHostApp
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )

    @Test fun beforeTheFirstUnlockTheAlarmsRunFromTheirOwnCopy() = runBlocking {
        AlertStore.clearForTests(app)
        AlertLog.clear(app)
        val real = app.getSystemService(AlarmManager::class.java)
        val am = shadowOf(real)
        val users = shadowOf(app.getSystemService(UserManager::class.java))
        // Asr plays the user's own recording.
        val alerts = AlertSettings(enabled = true).with(Prayer.ASR, PrayerAlert(true, AlertMode.CUSTOM, "custom.myvoice"))
        app.config = dubai.copy(alerts = alerts)
        assertEquals(5, AlertScheduler.reschedule(app).count)

        // The phone restarts at night: its alarms are gone, and it is not unlocked yet. What the app's settings would
        // say now is not what counts (here: alerts off), and they are not read.
        am.scheduledAlarms.toList().forEach { a -> a.operation?.let { real.cancel(it) } }
        assertEquals(0, am.scheduledAlarms.size)
        users.setUserUnlocked(false)
        app.config = dubai.copy(alerts = alerts.copy(enabled = false))
        // LOCKED_BOOT_COMPLETED: the five prayers are armed again from the copy.
        assertEquals(5, AlertScheduler.reschedule(app).count)
        assertEquals(5, am.scheduledAlarms.count { shadowOf(it.operation).savedIntent.action == AlertScheduler.ACTION_FIRE })
        val copy = AlertScheduler.configFor(app)!!
        assertEquals(alerts, copy.alerts)

        // Asr comes before the unlock: the recording (in the app's credential-protected files) cannot be read, so the
        // default Adhan plays, and the notification and the history say why.
        val adhan = SoundEntry(com.noor.prayer.alerts.SoundIds.DEFAULT_ADHAN, SoundKind.ADHAN, "Makkah", "مكة", null, null, emptySet(), false,
            "sounds/full.ogg", 176.0, "sounds/short.ogg", 32.0, null)
        SoundLibrary.replaceForTests(SoundLibrary(listOf(adhan), { true }, null))
        val date = LocalDate.of(2026, 11, 4)
        val at = copy.day(date).instant(Prayer.ASR)!!
        AlertDispatcher.dispatch(app, copy, copy.alerts!!, Prayer.ASR, date, at, copy.alerts!!.alert(Prayer.ASR), test = false, firedAtMs = at.toEpochMilli())
        val started = shadowOf(app).nextStartedService
        assertEquals("sounds/full.ogg", started.getStringExtra("asset"))
        assertTrue(started.getStringExtra("detail"), started.getStringExtra("detail")!!.contains("not unlocked yet"))
        assertTrue(AlertLog.all(app).first().detail!!.contains("first unlock"))

        // Unlocked: the app's own settings count again (and the copy follows them).
        users.setUserUnlocked(true)
        assertEquals(0, AlertScheduler.reschedule(app).count)
        assertEquals(false, AlertStore.snapshot(app)?.alerts?.enabled)
        SoundLibrary.replaceForTests(null)
    }
}
