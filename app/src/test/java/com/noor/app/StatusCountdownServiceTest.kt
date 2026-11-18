package com.noor.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import com.noor.app.data.LocationMode
import com.noor.app.notify.StatusCountdownService
import com.noor.app.notify.StatusNotification
import com.noor.app.notify.StatusTicker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * (0.22.4) The countdown keeps ticking when Minhad is closed (the owner: it froze once the app was swiped away). Where
 * Minhad writes the countdown itself (here: the phone in Arabic, Arabic-Indic digits, Western digits chosen in Minhad,
 * the owner's case), a foreground service of the "special use" type holds the notification; it ticks only while the
 * screen is on and starts again the moment it comes on; it stays when the app is swiped away; it stops when the
 * notification is turned off or Android's own Chronometer can tick it; and where Android refuses to start it, the
 * countdown is written to the minute (never frozen seconds), kept right by the non-waking minute alarm.
 * One test method (DataStore allows one instance per process). Robolectric, not a phone: whether One UI keeps the
 * service through "Close all" or its sleeping apps is seen only on the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class StatusCountdownServiceTest {
    @Test fun theCountdownKeepsTickingWhenMinhadIsClosed() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        repo.setManualCity("dxb"); repo.setLocationMode(LocationMode.MANUAL); repo.setOnboardingDone(); repo.setStatusNotification(true)
        val nm = app.getSystemService(NotificationManager::class.java)
        val pm = shadowOf(app.getSystemService(PowerManager::class.java))
        pm.setIsInteractive(true)
        val original = StatusNotification.systemZeroDigit
        val defaultStarter = StatusCountdownService.starter
        // (Graphics first: Robolectric's native graphics are loaded by them, and a view needs them.)
        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888); android.graphics.Paint()
        val parent by lazy { android.widget.FrameLayout(app) }
        fun countdownOf(n: android.app.Notification): String =
            n.contentView.apply(app, parent).findViewById<android.widget.TextView>(R.id.status_countdown_text).text.toString()
        fun idleTicker() = shadowOf(StatusTicker.looper).idle()
        try {
            // The phone writes Arabic-Indic digits; Minhad's are Western: Minhad writes the countdown.
            StatusNotification.systemZeroDigit = { '٠' }

            // 1 · The rebuild asks Android for the service.
            StatusNotification.refresh(app)
            val started = shadowOf(app).nextStartedService
            assertNotNull("the service was not started", started)
            assertEquals(StatusCountdownService::class.java.name, started.component!!.className)
            assertTrue(StatusCountdownService.wanted)

            // In the manifest: special use, not stopped with the app's task.
            val info = app.packageManager.getServiceInfo(android.content.ComponentName(app, StatusCountdownService::class.java), 0)
            assertEquals(0, info.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
            assertFalse(info.exported)

            // 2 · It starts: the status notification held as a foreground notification of the special-use type.
            val controller = Robolectric.buildService(StatusCountdownService::class.java, started).create()
            controller.startCommand(0, 1)
            val service = controller.get()
            val sh = shadowOf(service)
            assertEquals(StatusNotification.ID, sh.lastForegroundNotificationId)
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.foregroundServiceType)
            assertTrue(StatusCountdownService.running)
            val held = sh.lastForegroundNotification
            assertEquals(StatusNotification.CHANNEL, held.channelId)
            // In the app's digits (Western), whatever the phone's (Arabic-Indic), the sign first, with the seconds.
            val text = countdownOf(held)
            assertTrue(text, Regex("^[−+]([0-9]+:)?[0-9]{2}:[0-9]{2}$").matches(text))
            assertTrue(held.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
            // And the other way round: Arabic-Indic chosen in Minhad on a phone in English.
            run {
                val f = com.noor.app.util.Formatter(false, com.noor.app.data.Numerals.ARABIC_INDIC, com.noor.app.data.TimeFormat.H12)
                val s = repo.current()
                val c = StatusNotification.content(com.noor.app.data.WearSync.configFor(s, 0L, app.container.timetables)!!, s, java.time.Instant.now(), false)
                val ai = countdownOf(StatusNotification.build(app, app, c, java.time.Instant.now(), f))
                assertTrue(ai, Regex("^[−+]([٠-٩]+:)?[٠-٩]{2}:[٠-٩]{2}$").matches(ai))
                // The Live Update's chip and title: the sign first, kept left to right, in the app's digits.
                val chip = StatusNotification.chipText(c.countdown(java.time.Instant.now(), f))
                assertTrue(chip, chip.startsWith("⁦‎−") || chip.startsWith("⁦‎+"))
            }

            // 3 · Swiped away from the recent apps: it stays, and keeps the notification.
            service.onTaskRemoved(Intent())
            assertFalse(sh.isStoppedBySelf)
            assertFalse(sh.isForegroundStopped)
            assertTrue(StatusCountdownService.running)

            // 4 · It ticks every second while the screen is on.
            idleTicker()
            assertTrue("not ticking with the screen on", StatusTicker.scheduled)
            assertTrue(StatusTicker.ticking!!.seconds)
            val t0 = StatusTicker.ticks
            shadowOf(StatusTicker.looper).idleFor(Duration.ofMillis(3100))
            assertTrue("ticks: ${StatusTicker.ticks - t0}", StatusTicker.ticks - t0 >= 2)

            // Screen off: no more ticks, nothing waiting (the service's own receiver).
            pm.setIsInteractive(false)
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            idleTicker()
            assertFalse(StatusTicker.scheduled)
            val t1 = StatusTicker.ticks
            shadowOf(StatusTicker.looper).idleFor(Duration.ofSeconds(5))
            assertEquals(t1, StatusTicker.ticks)

            // Screen on: ticking again at once, without waiting for an alarm.
            pm.setIsInteractive(true)
            app.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val until = System.nanoTime() + 10_000_000_000L
            while (!StatusTicker.scheduled && System.nanoTime() < until) { Thread.sleep(20); shadowOf(android.os.Looper.getMainLooper()).idle(); idleTicker() }
            assertTrue("not ticking again after the screen came on", StatusTicker.scheduled)
            assertTrue("the service must still hold it", StatusCountdownService.running)

            // 5 · Android ended the service, and does not let it start again now (the minute alarm, in the background):
            // no crash, the notification still there, its countdown to the minute (never frozen seconds), the minute
            // alarm armed (non-waking) as the safety net.
            controller.destroy()
            assertFalse(StatusCountdownService.running)
            var tries = 0
            StatusCountdownService.starter = { _, _ -> tries++; throw android.app.ForegroundServiceStartNotAllowedException("from the background") }
            StatusNotification.refresh(app)
            assertEquals(1, tries)
            val fallback = shadowOf(nm).allNotifications.single { it.channelId == StatusNotification.CHANNEL }
            val minute = countdownOf(fallback)
            assertTrue(minute, Regex("^[−+][0-9]+:[0-9]{2}$").matches(minute))
            idleTicker()
            assertFalse(StatusTicker.ticking!!.seconds)
            val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single { it.operation?.creatorPackage == app.packageName && it.type == AlarmManager.RTC }
            assertTrue(alarm.triggerAtTime - System.currentTimeMillis() <= 61_000L)

            // 6 · The phone's digits are the app's again: Android's Chronometer ticks; no service is asked for.
            StatusNotification.systemZeroDigit = { '0' }
            StatusNotification.refresh(app)
            assertEquals(1, tries)
            assertFalse(StatusCountdownService.wanted)
            idleTicker()
            assertFalse(StatusTicker.running)

            // 7 · Started again (Minhad on screen), then the notification turned off in Settings: the service stops and
            // takes the notification away; nothing is left armed.
            StatusNotification.systemZeroDigit = { '٠' }
            StatusCountdownService.starter = defaultStarter
            StatusNotification.refresh(app)
            val again = shadowOf(app).nextStartedService
            assertNotNull(again)
            val c2 = Robolectric.buildService(StatusCountdownService::class.java, again).create()
            c2.startCommand(0, 2)
            assertTrue(StatusCountdownService.running)
            repo.setStatusNotification(false)
            StatusNotification.refresh(app)
            val sh2 = shadowOf(c2.get())
            assertTrue(sh2.isStoppedBySelf)
            assertTrue(sh2.isForegroundStopped)
            assertTrue(sh2.notificationShouldRemoved)
            assertFalse(StatusCountdownService.running)
            assertTrue(shadowOf(nm).allNotifications.none { it.channelId == StatusNotification.CHANNEL })
            assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.none { it.type == AlarmManager.RTC })
            assertNull(StatusNotification.latest)
        } finally {
            StatusNotification.systemZeroDigit = original
            StatusCountdownService.starter = defaultStarter
        }
        Unit
    }
}
