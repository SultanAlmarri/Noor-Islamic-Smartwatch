package com.noor.app

import android.app.Notification
import com.noor.app.data.AppSettings
import com.noor.app.data.LocationMode
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * (0.22) The prayer countdown as a chip in the status bar, as Samsung's Clock shows its timer (the owner's wish): an
 * Android 16 Live Update. Android promotes only its own templates, so this notification has no custom view; its
 * chronometer counts to the prayer (or from it), and it asks to be promoted. On by default, with the notification.
 * (Robolectric stops at Android 15: whether a phone shows the chip is seen on the phone.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveUpdateTest {
    @Test fun theCountdownCanBeAChipInTheStatusBar() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        // On by default, both.
        assertTrue(AppSettings().statusNotification)
        assertTrue(AppSettings().statusBarChip)
        val s = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = "dxb")
        val config = WearSync.configFor(s, 0L, app.container.timetables)!!
        val dubai = ZoneId.of("Asia/Dubai")

        // 9:37:16 on 4 Nov 2026: Dhuhr (12:05) 2:27:44 away.
        val before = ZonedDateTime.of(2026, 11, 4, 9, 37, 16, 0, dubai).toInstant()
        val c = StatusNotification.content(config, s, before, arabic = false)
        val n = StatusNotification.buildLive(app, app, c)
        assertNull("a custom view is never promoted", n.contentView)
        assertNull(n.bigContentView)
        assertEquals("Dhuhr  ·  12:05 PM", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue("counts down to the prayer", n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(c.countAt.toEpochMilli(), n.`when`)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue("asks to be promoted", n.extras.getBoolean(StatusNotification.EXTRA_REQUEST_PROMOTED))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Jumada"))

        // After the prayer, during its "since" period: it counts up from it.
        val after = ZonedDateTime.of(2026, 11, 4, 12, 20, 0, 0, dubai).toInstant()
        val a = StatusNotification.content(config, s, after, arabic = false)
        val m = StatusNotification.buildLive(app, app, a)
        assertTrue(a.elapsed)
        assertFalse(m.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(a.countAt.toEpochMilli(), m.`when`)

        // Below Android 16 the notification keeps Minhad's own look (no chip there).
        assertFalse(StatusNotification.liveWanted(app, s))
        assertFalse(StatusNotification.promotedAllowed(app))
    }
}
