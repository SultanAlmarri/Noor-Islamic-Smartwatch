package com.noor.app

import android.app.AlarmManager
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.noor.app.data.AppSettings
import com.noor.app.data.Language
import com.noor.app.data.LocationMode
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import com.noor.prayer.Prayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The persistent prayer notification (0.9.3): what it says at any moment, that it is silent and ongoing in its own
 * low-importance category, that its next update is armed at the next prayer with a non-waking alarm, and that turning it
 * off removes it. One test method (DataStore allows one instance per process).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class StatusNotificationTest {
    @Test fun showsTheNextPrayerAndUpdatesAtItsTime() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        val base = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = "dxb", statusNotification = true)
        val config = WearSync.configFor(base, 0L, app.container.timetables)!!
        val dubai = ZoneId.of("Asia/Dubai")

        // 1 · Content at fixed moments (pure): Wednesday 4 Nov 2026 in Dubai.
        val asr = ZonedDateTime.of(2026, 11, 4, 15, 29, 21, 0, dubai).toInstant()
        val c = StatusNotification.content(config, base, asr, arabic = false)
        assertEquals("Maghrib", c.prayer)
        assertEquals("5:40 PM", c.time)
        assertEquals("Dubai  ·  24 Jumada al-Ula 1448 AH", c.line)
        assertEquals(config.state(asr).next.at, c.nextAt)
        assertEquals(c.nextAt.plusSeconds(1), c.refreshAt)
        // After Isha the next prayer is tomorrow's Fajr, but the date line changes at midnight, so it updates then.
        val late = ZonedDateTime.of(2026, 11, 4, 22, 0, 0, 0, dubai).toInstant()
        val n = StatusNotification.content(config, base, late, arabic = false)
        assertEquals("Fajr", n.prayer)
        assertEquals(ZonedDateTime.of(2026, 11, 5, 0, 0, 1, 0, dubai).toInstant(), n.refreshAt)
        // Friday: Jumu'ah; Arabic: Arabic only, with the digits chosen.
        val friday = ZonedDateTime.of(2026, 11, 6, 11, 0, 0, 0, dubai).toInstant()
        assertEquals("Jumu’ah", StatusNotification.content(config, base, friday, arabic = false).prayer)
        val ar = StatusNotification.content(config, base.copy(numerals = com.noor.app.data.Numerals.ARABIC_INDIC), asr, arabic = true)
        assertEquals("المغرب", ar.prayer)
        assertTrue(ar.line.startsWith("دبي"))
        assertFalse(Regex("[A-Za-z]").containsMatchIn(ar.prayer + ar.time + ar.line))

        // 2 · Posted for real: silent, ongoing, its own low-importance category, the next update armed (non-waking).
        repo.setManualCity("dxb"); repo.setLocationMode(LocationMode.MANUAL); repo.setOnboardingDone(); repo.setStatusNotification(true)
        StatusNotification.refresh(app)
        val nm = app.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(nm).allNotifications.single { it.channelId == StatusNotification.CHANNEL }
        assertTrue(posted.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(NotificationManager.IMPORTANCE_LOW, nm.getNotificationChannel(StatusNotification.CHANNEL).importance)
        assertNull(nm.getNotificationChannel(StatusNotification.CHANNEL).sound)
        assertEquals(NotificationCompat.CATEGORY_STATUS, posted.category)
        assertNotNull(posted.contentView)
        val now = java.time.Instant.now()
        val expected = StatusNotification.content(config, base, now, arabic = false)
        val title = posted.extras.getString(NotificationCompat.EXTRA_TITLE)!!
        assertTrue(title, Prayer.entries.any { title.startsWith(it.en) } || title.startsWith("Jumu’ah"))
        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single { it.operation?.creatorPackage == app.packageName && it.type == AlarmManager.RTC }
        assertEquals(expected.refreshAt.toEpochMilli(), alarm.triggerAtTime)

        // A software render of the notification as Android builds it, for review (not a photo of a phone's shade).
        runCatching {
            val parent = android.widget.FrameLayout(app)
            val v = posted.contentView.apply(app, parent)
            val d = app.resources.displayMetrics
            val w = (360 * d.density).toInt()
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, w, v.measuredHeight)
            val bmp = android.graphics.Bitmap.createBitmap(w, v.measuredHeight.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp); canvas.drawColor(0xFFF2F2F2.toInt()); v.draw(canvas)
            val dir = java.io.File("build/screenshots").apply { mkdirs() }
            java.io.File(dir, "55_status_notification_en.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }

        // 3 · Off: removed, and nothing left armed.
        repo.setStatusNotification(false)
        StatusNotification.refresh(app)
        assertTrue(shadowOf(nm).allNotifications.none { it.channelId == StatusNotification.CHANNEL })
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.none { it.type == AlarmManager.RTC })
        Unit
    }
}
