package com.noor.app

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.view.View
import com.noor.app.data.LocationMode
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import com.noor.app.widget.DateWidget
import com.noor.app.widget.NextPrayerWidget
import com.noor.app.widget.TodayWidget
import com.noor.app.widget.WidgetRefreshReceiver
import com.noor.app.widget.Widgets
import com.noor.prayer.HijriDate
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
 * The widgets when what they say changes (0.22 bug hunt; 0.24 every update a full one): the end of a prayer's "+"
 * period, a prayer's time with the "+" period turned off, and midnight. Until 0.22 a minute's update redrew the
 * countdown alone, so the widget went on naming the old prayer while counting to the next. Each update the widgets are
 * given here is the one they armed (its alarm, bringing the change it is for), as Android would deliver it. One test
 * method (DataStore allows one instance per process).
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
        Widgets.sizeMaps = { false }
        Widgets.inProcess = false
        val repo = app.container.settings
        repo.setOnboardingDone(); repo.setLocationMode(LocationMode.MANUAL); repo.setManualCity("ist")
        val manager = AppWidgetManager.getInstance(app)
        val shadow = shadowOf(manager)
        val next = shadow.createWidget(NextPrayerWidget::class.java, R.layout.widget_v3)
        val today = shadow.createWidget(TodayWidget::class.java, R.layout.widget_v3)
        val date = shadow.createWidget(DateWidget::class.java, R.layout.widget_v3)
        Thread.sleep(1500)   // Noor's own update after the settings above is done first

        // What the widgets say, from the model of what they drew.
        fun drawn(id: Int) = Widgets.builtFor(id)!!.design.drawn
        // (AM/PM a thin space from the digits, as the app writes it; compared with the words said, spaced plainly.)
        fun title(id: Int) = drawn(id).first { it.role == "name" }.text.replace(' ', ' ')
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
                val change = shadowOf(a.operation).savedIntent.getLongExtra(Widgets.EXTRA_AT, 0L)
                if (Instant.ofEpochMilli(change).isAfter(until)) return
                moment = Instant.ofEpochMilli(a.triggerAtTime)
                Widgets.ahead(app, change)
            }
        }
        /** The Today widget's marked prayer (its column in the gold capsule). */
        fun marked() = drawn(today).filter { it.role.startsWith("col_name") }.indexOfFirst { it.role == "col_name_marked" }

        // Asr's "+" period: built half a minute before it ends.
        val asr = content(start).countAt
        moment = asr.plusSeconds(29 * 60 + 30)
        Widgets.refresh(app, moment)
        assertTrue(title(next).startsWith("Asr "))
        assertEquals(3, marked())
        // Its end, and a minute more: the widgets now count to Maghrib, and say so.
        val end = asr.plusSeconds(30 * 60)
        runUntil(end.plusSeconds(60))
        val c = content(end.plusSeconds(60))
        assertEquals("Maghrib", c.prayer)
        assertEquals("${c.prayer} ${c.time}", title(next))
        // What it says to a screen reader: the time left to Maghrib.
        assertTrue(shadow.getViewFor(next).contentDescription.toString().startsWith("Maghrib at ${c.time}, in "))
        assertEquals("Maghrib marked", 4, marked())

        // The "+" period turned off: at Maghrib's time, straight to Isha.
        val maghrib = c.countAt
        moment = maghrib.minusSeconds(30)
        repo.setElapsedAfterPrayer(false)
        Thread.sleep(1500)
        Widgets.refresh(app, moment)
        assertTrue(title(next).startsWith("Maghrib "))
        runUntil(maghrib.plusSeconds(60))
        assertEquals("Isha ${content(maghrib.plusSeconds(60)).time}", title(next))
        assertEquals(5, marked())

        // Midnight: the new day's Hijri date on the date widget.
        val midnight = ZonedDateTime.of(2026, 11, 5, 0, 0, 0, 0, istanbul).toInstant()
        moment = midnight.minusSeconds(90)
        Widgets.refresh(app, moment)
        val dayBefore = drawn(date).first { it.role == "day" }.text
        runUntil(midnight.plusSeconds(60))
        val newDay = HijriDate.of(midnight.atZone(istanbul).toLocalDate(), repo.current().hijriAdjust).day.toString()
        assertEquals(newDay, drawn(date).first { it.role == "day" }.text)
        assertTrue(dayBefore != newDay)
        assertTrue(shadow.getViewFor(today).findViewById<View>(R.id.widget_count_row).visibility == View.VISIBLE)
    }

    @After fun restore() {
        Widgets.clock = java.time.Instant::now
        Widgets.wheelSupported = { android.os.Build.VERSION.SDK_INT >= 31 }
        Widgets.sizeMaps = { true }
        Widgets.inProcess = true
    }
}
