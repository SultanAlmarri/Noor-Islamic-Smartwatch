package com.noor.app

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.view.View
import android.widget.TextView
import com.noor.app.data.LocationMode
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import com.noor.app.widget.NextPrayerWidget
import com.noor.app.widget.TodayWidget
import com.noor.app.widget.WidgetRefreshReceiver
import com.noor.app.widget.Widgets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The widgets when what they say changes (0.22 bug hunt): the end of a prayer's "+" period, a prayer's time with the "+"
 * period turned off, and midnight. On Android 12 and later the countdown's minute is redrawn alone each minute, and the
 * minute's update falls a few hundredths of a second after the change, before the full update armed for it: until
 * 0.22 that minute's update redrew the countdown alone, so the widget went on naming the old prayer (and its time, its
 * caption and its marked row) while counting to the next one, until the sky picture's next update, up to 15 minutes
 * later. Each update the widgets are given here is the one they armed, as Android would deliver it. One test method
 * (DataStore allows one instance per process).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetsChangeTest {
    @Test fun theWidgetsNameThePrayerTheyCount() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val istanbul = ZoneId.of("Europe/Istanbul")
        val start = ZonedDateTime.of(2026, 11, 4, 14, 0, 0, 0, istanbul).toInstant()
        // Noor's own updates (after a change of settings) are for the moment the test is at.
        var moment = start
        Widgets.clock = { moment }
        Widgets.wheelSupported = { true }
        val repo = app.container.settings
        repo.setOnboardingDone(); repo.setLocationMode(LocationMode.MANUAL); repo.setManualCity("ist")
        val manager = AppWidgetManager.getInstance(app)
        val shadow = shadowOf(manager)
        val next = shadow.createWidget(NextPrayerWidget::class.java, R.layout.widget_next)
        val today = shadow.createWidget(TodayWidget::class.java, R.layout.widget_today)
        Widgets.partialUpdate = { _, id, views -> views.reapply(app, shadow.getViewFor(id)) }
        Thread.sleep(1500)   // Noor's own update after the settings above is done first

        fun text(id: Int, view: Int) = shadow.getViewFor(id).findViewById<TextView>(view).text.toString()
        fun content(now: Instant) = runBlocking {
            val s = repo.current()
            StatusNotification.content(WearSync.configFor(s, now.toEpochMilli(), app.container.timetables)!!, s, now, false)
        }
        fun alarm() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .single { shadowOf(it.operation).savedIntent.component?.className == WidgetRefreshReceiver::class.java.name }
        /** Delivers the armed updates, as Android would, until [until]. */
        suspend fun runUntil(until: Instant) {
            while (true) {
                val a = alarm()
                val at = Instant.ofEpochMilli(a.triggerAtTime)
                if (at.isAfter(until)) return
                moment = at
                Widgets.refresh(app, at, minuteOnly = shadowOf(a.operation).savedIntent.getBooleanExtra(Widgets.EXTRA_MINUTE, false))
            }
        }
        val gold = 0xFFF6D696.toInt()
        val times = listOf(R.id.widget_time_0, R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3, R.id.widget_time_4, R.id.widget_time_5)
        fun marked() = times.indexOfFirst { shadow.getViewFor(today).findViewById<TextView>(it).currentTextColor == gold }

        // Asr's "+" period: built half a minute before it ends.
        val asr = content(start).countAt
        moment = asr.plusSeconds(29 * 60 + 30)
        Widgets.refresh(app, moment)
        assertEquals("Asr", text(next, R.id.widget_prayer))
        assertEquals(3, marked())
        // Its end, and a minute more: the widgets now count to Maghrib, and say so.
        val end = asr.plusSeconds(30 * 60)
        runUntil(end.plusSeconds(60))
        val c = content(end.plusSeconds(60))
        assertEquals("Maghrib", c.prayer)
        assertEquals(c.prayer, text(next, R.id.widget_prayer))
        assertEquals(c.time, text(next, R.id.widget_time))
        assertEquals(app.getString(R.string.next_prayer), text(next, R.id.widget_caption))
        assertEquals("Maghrib marked", 4, marked())

        // The "+" period turned off: at Maghrib's time, straight to Isha.
        val maghrib = c.countAt
        moment = maghrib.minusSeconds(30)
        repo.setElapsedAfterPrayer(false)
        Thread.sleep(1500)
        Widgets.refresh(app, moment)
        assertEquals("Maghrib", text(next, R.id.widget_prayer))
        runUntil(maghrib.plusSeconds(60))
        assertEquals("Isha", text(next, R.id.widget_prayer))
        assertEquals(content(maghrib.plusSeconds(60)).time, text(next, R.id.widget_time))
        assertEquals(5, marked())

        // Midnight: the new day's Hijri date on the widgets.
        val midnight = ZonedDateTime.of(2026, 11, 5, 0, 0, 0, 0, istanbul).toInstant()
        moment = midnight.minusSeconds(90)
        Widgets.refresh(app, moment)
        runUntil(midnight.plusSeconds(60))
        val hijri = content(midnight.plusSeconds(60)).hijri
        assertTrue("${text(today, R.id.widget_place)} has $hijri", text(today, R.id.widget_place).endsWith(hijri))
        assertTrue(shadow.getViewFor(today).findViewById<View>(R.id.widget_count_row).visibility == View.VISIBLE)
    }

    @After fun restore() {
        Widgets.clock = java.time.Instant::now
        Widgets.wheelSupported = { android.os.Build.VERSION.SDK_INT >= 31 }
        Widgets.partialUpdate = { m, id, v -> m.partiallyUpdateAppWidget(id, v) }
    }
}
