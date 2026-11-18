package com.noor.alerts

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertPlanner
import com.noor.prayer.alerts.AlertSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Test host: a phone in Dubai. */
class TestHostApp : Application(), AlertHost {
    var config: WatchConfig? = null
    var alarmClock = false
    override val alarmClockAlarms: Boolean get() = alarmClock
    override val alertDevice = AlertDevice.PHONE
    override val notificationIcon = android.R.drawable.ic_lock_idle_alarm
    override suspend fun alertConfig(): WatchConfig? = config
    override fun launchIntent(context: Context): Intent = Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
    override fun adhanScreen(context: Context): Intent = Intent("com.noor.test.ADHAN_SCREEN").setPackage(context.packageName)
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestHostApp::class, sdk = [34])
class AlertSchedulerTest {

    private val app get() = org.robolectric.RuntimeEnvironment.getApplication() as TestHostApp
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )

    @Test fun armsOneAlarmPerEnabledPrayerAndCancelsTheRest() = runBlocking {
        val am = shadowOf(app.getSystemService(AlarmManager::class.java))
        app.config = dubai.copy(alerts = AlertSettings(enabled = true))
        val status = AlertScheduler.reschedule(app)
        assertEquals(5, status.count)
        assertEquals(5, am.scheduledAlarms.size)
        assertTrue(am.scheduledAlarms.all { it.triggerAtTime > System.currentTimeMillis() })
        // Alarms are exactly the displayed times.
        val plan = AlertPlanner.plan(app.config!!, app.config!!.alerts!!, AlertDevice.PHONE, Instant.now())
        assertEquals(plan.map { it.at.toEpochMilli() }.sorted(), am.scheduledAlarms.map { it.triggerAtTime }.sorted())

        // Turning alerts off cancels every alarm; moving them to the watch does too (on the phone).
        app.config = dubai.copy(alerts = AlertSettings(enabled = true, device = AlertDevice.WATCH))
        assertEquals(0, AlertScheduler.reschedule(app).count)
        assertEquals(0, am.scheduledAlarms.size)
        app.config = dubai.copy(alerts = AlertSettings(enabled = true))
        AlertScheduler.reschedule(app)
        app.config = dubai.copy(alerts = AlertSettings(enabled = false))
        AlertScheduler.reschedule(app)
        assertEquals(0, am.scheduledAlarms.size)
        // No location yet: nothing armed, nothing crashes.
        app.config = null
        assertEquals(0, AlertScheduler.reschedule(app).count)
    }

    @Test fun remindersAreArmedBesideTheAlertsAndCancelledWhenTurnedOff() = runBlocking {
        val am = shadowOf(app.getSystemService(AlarmManager::class.java))
        val s = AlertSettings(enabled = true, reminderMinutes = 15)
        app.config = dubai.copy(alerts = s)
        // The status counts the prayer alerts only; the reminders are extra alarms 15 minutes ahead of each.
        assertEquals(5, AlertScheduler.reschedule(app).count)
        assertEquals(10, am.scheduledAlarms.size)
        val now = Instant.now()
        val expected = (AlertPlanner.plan(app.config!!, s, AlertDevice.PHONE, now).map { it.at.toEpochMilli() } +
            AlertPlanner.reminders(app.config!!, s, AlertDevice.PHONE, now).map { it.remindAt.toEpochMilli() }).sorted()
        assertEquals(expected, am.scheduledAlarms.map { it.triggerAtTime }.sorted())
        // Reminder off: only the alerts stay. Alerts moved to the watch: nothing on the phone.
        app.config = dubai.copy(alerts = s.copy(reminderMinutes = 0))
        AlertScheduler.reschedule(app)
        assertEquals(5, am.scheduledAlarms.size)
        app.config = dubai.copy(alerts = s)
        AlertScheduler.reschedule(app)
        app.config = dubai.copy(alerts = s.copy(device = AlertDevice.WATCH))
        AlertScheduler.reschedule(app)
        assertEquals(0, am.scheduledAlarms.size)
    }

    @Test fun aReminderSaysWhatIsComingAndLeavesWhenThePrayerComes() {
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val settings = AlertSettings(enabled = true, reminderMinutes = 15)
        val date = LocalDate.of(2026, 11, 4) // a Wednesday
        val asr = dubai.day(date).instant(Prayer.ASR)!!
        AlertNotifications.postReminder(app, dubai, com.noor.prayer.alerts.DueReminder(Prayer.ASR, date, asr, 15))
        val n = nm.allNotifications.single()
        assertEquals(AlertNotifications.CHANNEL_REMIND, n.channelId)
        assertEquals("Asr in 15 minutes", n.extras.getString("android.title"))
        assertTrue(n.extras.getString("android.text")!!.endsWith("Dubai"))
        // When the prayer itself is called, its reminder is taken away.
        AlertDispatcher.dispatch(app, dubai, settings, Prayer.ASR, date, asr,
            settings.alert(Prayer.ASR).copy(mode = com.noor.prayer.alerts.AlertMode.NOTIFICATION), test = false)
        assertTrue(nm.allNotifications.none { it.channelId == AlertNotifications.CHANNEL_REMIND })
        app.getSystemService(NotificationManager::class.java).cancelAll()

        AlertNotifications.postReminder(app, dubai, com.noor.prayer.alerts.DueReminder(Prayer.ISHA, date, dubai.day(date).instant(Prayer.ISHA)!!, 1))
        assertEquals("Isha in 1 minute", nm.allNotifications.single().extras.getString("android.title"))
        app.getSystemService(NotificationManager::class.java).cancelAll()
        AlertNotifications.postReminder(app, dubai, com.noor.prayer.alerts.DueReminder(Prayer.SHURUQ, date, dubai.day(date).instant(Prayer.SHURUQ)!!, 10))
        assertEquals("Fajr time ends in 10 minutes", nm.allNotifications.single().extras.getString("android.title"))
        app.getSystemService(NotificationManager::class.java).cancelAll()

        // Arabic, with Arabic-Indic digits as chosen in Noor.
        val ar = dubai.copy(language = "ar", arabicDigits = true)
        AlertNotifications.postReminder(app, ar, com.noor.prayer.alerts.DueReminder(Prayer.ASR, date, asr, 15))
        val t = nm.allNotifications.single().extras.getString("android.title")!!
        assertTrue(t, t.contains("العصر") && t.endsWith("بعد ١٥ دقيقة"))
    }

    @Test fun usesInexactAlarmsWhenExactOnesAreNotAllowed() = runBlocking {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        try {
            app.config = dubai.copy(alerts = AlertSettings(enabled = true))
            val status = AlertScheduler.reschedule(app)
            assertFalse(status.exact)
            assertEquals(5, shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        } finally {
            org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
    }

    @Test fun quietReasonsRespectTheUsersChoice() {
        val nm = app.getSystemService(NotificationManager::class.java)
        val am = app.getSystemService(AudioManager::class.java)
        AlertNotifications.ensureChannels(app)
        val quiet = AlertSettings(enabled = true, quietWhenSilent = true) // "stay quiet when my phone is silent"
        val alarm = AlertSettings(enabled = true) // the default: the Adhan plays like an alarm clock
        assertNull(AlertDispatcher.quietReason(app, quiet))
        assertNull(AlertDispatcher.quietReason(app, alarm))
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        // Do Not Disturb that lets alarms through (Android's default rule).
        nm.notificationPolicy = NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertEquals(AlertDispatcher.Quiet.DND, AlertDispatcher.quietReason(app, quiet))
        assertNull(AlertDispatcher.quietReason(app, alarm))
        // Do Not Disturb that blocks alarms too: the system would mute the Adhan, so Noor says so instead of "playing" silently.
        nm.notificationPolicy = NotificationManager.Policy(0, 0, 0)
        assertEquals(AlertDispatcher.Quiet.DND_BLOCKS_ALARMS, AlertDispatcher.quietReason(app, alarm))
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertEquals(AlertDispatcher.Quiet.DND_BLOCKS_ALARMS, AlertDispatcher.quietReason(app, alarm))
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        assertNull(AlertDispatcher.quietReason(app, alarm))
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        // Vibrate and silent: quiet only for those who chose it. This default was why Adhans stayed silent before 0.9.
        am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertEquals(AlertDispatcher.Quiet.RINGER_SILENT, AlertDispatcher.quietReason(app, quiet))
        assertNull(AlertDispatcher.quietReason(app, alarm))
        am.ringerMode = AudioManager.RINGER_MODE_SILENT
        assertEquals(AlertDispatcher.Quiet.RINGER_SILENT, AlertDispatcher.quietReason(app, quiet))
        assertNull(AlertDispatcher.quietReason(app, alarm))
        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
        am.mode = AudioManager.MODE_IN_CALL
        assertEquals(AlertDispatcher.Quiet.IN_CALL, AlertDispatcher.quietReason(app, alarm)) // calls always win
        am.mode = AudioManager.MODE_NORMAL
        shadowOf(nm).setNotificationsEnabled(false)
        assertEquals(AlertDispatcher.Quiet.NO_NOTIFICATIONS, AlertDispatcher.quietReason(app, alarm)) // never play without a Stop button
    }

    @Test fun notificationOnlyAndSilentPostOnTheirChannels() {
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val settings = AlertSettings(enabled = true)
        val date = LocalDate.of(2026, 11, 6) // a Friday
        val at = dubai.day(date).instant(Prayer.DHUHR)!!
        AlertDispatcher.dispatch(app, dubai, settings, Prayer.DHUHR, date, at,
            settings.alert(Prayer.DHUHR).copy(mode = com.noor.prayer.alerts.AlertMode.NOTIFICATION), test = false)
        val n = nm.allNotifications.single()
        assertEquals(AlertNotifications.CHANNEL_NOTIFY, n.channelId)
        assertEquals("Time for Jumu’ah", n.extras.getString("android.title"))
        assertTrue(n.extras.getString("android.text")!!.endsWith("Dubai"))
        app.getSystemService(NotificationManager::class.java).cancelAll()
        val ar = dubai.copy(language = "ar", arabicDigits = true)
        AlertDispatcher.dispatch(app, ar, settings, Prayer.ASR, date, ar.day(date).instant(Prayer.ASR)!!,
            settings.alert(Prayer.ASR).copy(mode = com.noor.prayer.alerts.AlertMode.SILENT), test = false)
        val s = nm.allNotifications.single()
        assertEquals(AlertNotifications.CHANNEL_SILENT, s.channelId)
        assertEquals("حان وقت صلاة العصر", s.extras.getString("android.title"))
        assertTrue(s.extras.getString("android.text")!!.contains('٣')) // Arabic-Indic digits as chosen
    }

    /**
     * 0.22: a prayer's notification goes when the next prayer begins. Until 0.21 it stayed 90 minutes whatever came
     * next: in Dubai Maghrib's "Time for Maghrib" was still in the shade at Isha (about 80 minutes later), beside Isha's,
     * and Fajr's after sunrise, when Fajr's time had ended.
     */
    @Test fun aPrayersNotificationLeavesWhenTheNextPrayerBegins() {
        // Dubai, 4 November 2026: Maghrib to Isha is less than 90 minutes; Fajr to sunrise too.
        val date = LocalDate.of(2026, 11, 4)
        val d = dubai.day(date)
        val maghrib = d.instant(Prayer.MAGHRIB)!!; val isha = d.instant(Prayer.ISHA)!!
        val fajr = d.instant(Prayer.FAJR)!!; val sunrise = d.instant(Prayer.SHURUQ)!!
        assertTrue(java.time.Duration.between(maghrib, isha) < java.time.Duration.ofMinutes(90))
        assertEquals(isha, AlertNotifications.nextPrayerAt(dubai, maghrib))
        assertEquals(sunrise, AlertNotifications.nextPrayerAt(dubai, fajr))
        assertEquals(isha.toEpochMilli() - maghrib.toEpochMilli(),
            AlertNotifications.prayerNoteStays(isha.toEpochMilli(), nowMs = maghrib.toEpochMilli()))
        assertEquals(sunrise.toEpochMilli() - fajr.toEpochMilli(), AlertNotifications.prayerNoteStays(sunrise.toEpochMilli(), nowMs = fajr.toEpochMilli()))
        // Isha to the next Fajr is long: it stays its 90 minutes, as before. Unknown next prayer: 90 minutes; a minute at least.
        val nextFajr = AlertNotifications.nextPrayerAt(dubai, isha)!!
        assertEquals(dubai.day(date.plusDays(1)).instant(Prayer.FAJR), nextFajr)
        assertEquals(AlertNotifications.PRAYER_NOTE_MS, AlertNotifications.prayerNoteStays(nextFajr.toEpochMilli(), nowMs = isha.toEpochMilli()))
        assertEquals(AlertNotifications.PRAYER_NOTE_MS, AlertNotifications.prayerNoteStays(0L))
        assertEquals(60_000L, AlertNotifications.prayerNoteStays(isha.toEpochMilli(), nowMs = isha.toEpochMilli() + 1_000))

        // The real path: a notification-only Maghrib, with Isha 80 minutes after it, as the times of a day around now
        // (a place whose clock reads about noon now, so the day's times stay in order).
        val now = System.currentTimeMillis()
        val utc = Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC)
        val zone = java.time.ZoneOffset.ofHours(12 - utc.hour)
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val m = local.hour * 60 + local.minute
        val today = local.toLocalDate()
        val table = com.noor.prayer.authority.Timetable("test", mapOf(today to intArrayOf(300, 400, 500, 600, m, m + 80)))
        val config = dubai.copy(zoneId = zone.id, official = table)
        val at = config.day(today).instant(Prayer.MAGHRIB)!!
        val ishaAt = config.day(today).instant(Prayer.ISHA)!!
        val settings = AlertSettings(enabled = true)
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        AlertDispatcher.dispatch(app, config, settings, Prayer.MAGHRIB, today, at,
            settings.alert(Prayer.MAGHRIB).copy(mode = com.noor.prayer.alerts.AlertMode.NOTIFICATION), test = false)
        val n = nm.allNotifications.single()
        val expected = ishaAt.toEpochMilli() - now
        assertTrue("timeout ${n.timeoutAfter} ms, expected about $expected", kotlin.math.abs(n.timeoutAfter - expected) < 5_000)
        app.getSystemService(NotificationManager::class.java).cancelAll()
        // The Adhan's own notification, left when the sound ends, carries the same end.
        val req = PlayRequest(AlertNotifications.idFor(Prayer.MAGHRIB), "t", "b", at.toEpochMilli(), null, "Stop", SoundSource.Asset("x.ogg"),
            80, 0, 0, false, keepNotification = true, nextPrayerAtMs = ishaAt.toEpochMilli())
        assertEquals(ishaAt.toEpochMilli(), PlayRequest.from(req.toIntent(app))!!.nextPrayerAtMs)
    }
}
