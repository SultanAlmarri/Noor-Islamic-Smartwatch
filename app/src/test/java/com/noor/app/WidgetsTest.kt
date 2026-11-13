package com.noor.app

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import com.noor.app.data.LocationMode
import com.noor.app.data.Scenery
import com.noor.app.widget.NextPrayerWidget
import com.noor.app.widget.SkyWidget
import com.noor.app.widget.TodayWidget
import com.noor.app.widget.WidgetRefreshReceiver
import com.noor.app.widget.Widgets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The home-screen widgets (0.9.10), placed on a simulated home screen and updated as Android would: each says the
 * next prayer and its time, with Android's own live countdown, as the home screen and the notification do; the Today
 * widget lists the day's six times in order with the next one marked; every widget has its picture of the sky; the next
 * update is armed (non-waking) no later than the next prayer and within 15 minutes for the picture. Pictures of each
 * widget are saved (they are also the previews in the widget picker). One test method (DataStore allows one instance
 * per process).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetsTest {
    @Test fun widgetsShowTheNextPrayerAndTheSky() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        // The test's moment, moving with the test's clock: the updates Noor makes by itself (on a change of settings)
        // are for the same moment as the test's own.
        val istanbul = ZoneId.of("Europe/Istanbul")
        val at = ZonedDateTime.of(2026, 11, 4, 15, 50, 0, 0, istanbul).toInstant()
        var moment = at; var since = android.os.SystemClock.elapsedRealtime()
        Widgets.clock = { moment.plusMillis(android.os.SystemClock.elapsedRealtime() - since) }
        val repo = app.container.settings
        repo.setOnboardingDone(); repo.setLocationMode(LocationMode.MANUAL); repo.setManualCity("ist"); repo.setScenery(Scenery.HILLS)
        val manager = AppWidgetManager.getInstance(app)
        val shadow = shadowOf(manager)
        val next = shadow.createWidget(NextPrayerWidget::class.java, R.layout.widget_next)
        val today = shadow.createWidget(TodayWidget::class.java, R.layout.widget_today)
        val sky = shadow.createWidget(SkyWidget::class.java, R.layout.widget_sky)
        // Wednesday 4 November 2026 in Istanbul, 15:50: Asr (15:37) has passed and its "+" time is running.
        Widgets.refresh(app, at)

        fun text(v: View, id: Int) = v.findViewById<TextView>(id).text.toString()
        val names = listOf("Fajr", "Shuruq", "Dhuhr", "Asr", "Maghrib", "Isha")
        for (id in listOf(next, today, sky)) {
            val v = shadow.getViewFor(id)
            assertTrue("prayer: ${text(v, R.id.widget_prayer)}", text(v, R.id.widget_prayer) in names)
            assertTrue(text(v, R.id.widget_time).matches(Regex("\\d{1,2}:\\d{2} [AP]M")))
            // The phone writes Noor's digits (Western here): the countdown ticks as the home screen's does, Android's own seconds.
            assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_countdown).visibility)
            assertEquals(View.GONE, v.findViewById<View>(R.id.widget_countdown_text).visibility)
            assertNotNull((v.findViewById<android.widget.ImageView>(R.id.widget_sky).drawable))
        }
        // At 15:50 the widgets count the time since Asr, as the home screen does.
        assertEquals("Asr", text(shadow.getViewFor(next), R.id.widget_prayer))
        // Today: the six times in order, Asr marked in gold.
        val t = shadow.getViewFor(today)
        val ids = listOf(R.id.widget_name_0, R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4, R.id.widget_name_5)
        assertEquals(names, ids.map { text(t, it) })
        assertEquals(0xFFF6D696.toInt(), t.findViewById<TextView>(R.id.widget_time_3).currentTextColor)
        assertEquals(0xFFFFFFFF.toInt(), t.findViewById<TextView>(R.id.widget_time_4).currentTextColor)
        assertTrue(text(t, R.id.widget_place).startsWith("Istanbul"))

        // The next update: non-waking, before the end of Asr's 30 minutes (16:05) and within 15 minutes.
        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .filter { shadowOf(it.operation).savedIntent.component?.className == WidgetRefreshReceiver::class.java.name }
        assertEquals(1, alarm.size)
        assertEquals(AlarmManager.RTC, alarm[0].type)
        assertTrue(alarm[0].triggerAtTime <= at.plusSeconds(15 * 60).toEpochMilli())

        // Pictures (and the previews in the widget picker).
        val d = app.resources.displayMetrics.density
        fun picture(name: String, id: Int, size: Pair<Int, Int>) {
            val v = shadow.getViewFor(id)
            val w = (size.first * d).toInt(); val h = (size.second * d).toInt()
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(File("build/screenshots").apply { mkdirs() }, "90_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        picture("widget_next", next, 180 to 84); picture("widget_today", today, 340 to 170); picture("widget_sky", sky, 340 to 170)

        // Noor in Arabic: the widgets too.
        repo.setLanguage(com.noor.app.data.Language.ARABIC)
        Widgets.refresh(app, at)
        assertEquals(com.noor.prayer.Prayer.ASR.ar, text(shadow.getViewFor(next), R.id.widget_prayer))
        assertEquals(com.noor.prayer.Prayer.FAJR.ar, text(shadow.getViewFor(today), R.id.widget_name_0))

        // At 15:50 the countdown reads "+13:xx" (time since Asr), exactly as the home screen writes it: "+", minutes,
        // seconds; before a prayer Android writes "−h:mm:ss" with a true minus sign, as the home screen does.
        fun count(v: View, id: Int) = v.findViewById<android.widget.Chronometer>(id)
        val nv = shadow.getViewFor(next)
        count(nv, R.id.widget_countdown).let { c ->
            assertEquals("+%s", c.format)
            assertTrue("counting since Asr: ${c.text}", c.text.toString().matches(Regex("\\+1[23]:\\d{2}")))
        }
        // It ticks by itself, every second, as the home screen's does: on a screen, five seconds later it reads five
        // seconds more, with no update from Noor.
        fun seconds(t: CharSequence) = t.toString().trim('+', '\u2212').split(':').fold(0) { acc, part -> acc * 60 + part.toInt() } *
            (if (t.startsWith("\u2212")) -1 else 1)
        Thread.sleep(1500)   // Noor's own update after the settings above (half a second later) is done first
        val screen = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        screen.setContentView(nv)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val start = seconds(count(nv, R.id.widget_countdown).text)
        for (k in 1..5) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            assertEquals("ticking: ${count(nv, R.id.widget_countdown).text}", start + k, seconds(count(nv, R.id.widget_countdown).text))
        }

        // Arabic-Indic digits chosen in Noor, on a phone that writes Western ones: a home screen ignores fonts packed in
        // an app, so Android's countdown cannot be redrawn in Noor's digits (0.9.11 tried). Noor writes it, to the minute:
        // 13 minutes since Asr.
        repo.setLanguage(com.noor.app.data.Language.ENGLISH)
        repo.setNumerals(com.noor.app.data.Numerals.ARABIC_INDIC)
        Widgets.refresh(app, at)
        for (id in listOf(next, today, sky)) {
            val v = shadow.getViewFor(id)
            assertEquals(View.GONE, v.findViewById<View>(R.id.widget_countdown).visibility)
            assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_countdown_text).visibility)
            assertEquals("+\u0660:\u0661\u0663", text(v, R.id.widget_countdown_text))
        }
        // The next update: when that minute changes (non-waking), not before.
        val a = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .single { shadowOf(it.operation).savedIntent.component?.className == WidgetRefreshReceiver::class.java.name }
        assertEquals(AlarmManager.RTC, a.type)
        assertEquals(at.plusSeconds(60).plusMillis(500).toEpochMilli(), a.triggerAtTime)
        picture("widget_next_arabic_digits", next, 180 to 84)

        // Before a prayer: at 14:10, "−1:27:00" to Asr (15:37), with the home screen's true minus sign.
        val before = ZonedDateTime.of(2026, 11, 4, 14, 10, 0, 0, istanbul).toInstant()
        moment = before; since = android.os.SystemClock.elapsedRealtime()
        repo.setNumerals(com.noor.app.data.Numerals.WESTERN)
        Widgets.refresh(app, before)
        val bv = shadow.getViewFor(next)
        assertEquals("Asr", text(bv, R.id.widget_prayer))
        assertEquals("\u22121:27:00", count(bv, R.id.widget_countdown).text.toString())
        picture("widget_next_before", next, 180 to 84); picture("widget_today_before", today, 340 to 170); picture("widget_sky_before", sky, 340 to 170)

        // The owner's phone (13 November 2026): the phone in Arabic (Android writes Arabic-Indic digits), Noor in
        // Western digits. Noor writes the countdown in Western digits, to the minute, never Android's.
        com.noor.app.notify.StatusNotification.systemZeroDigit = { '\u0660' }
        Widgets.refresh(app, before)
        for (id in listOf(next, today, sky)) {
            val v = shadow.getViewFor(id)
            assertEquals(View.GONE, v.findViewById<View>(R.id.widget_countdown).visibility)
            assertEquals("\u22121:27", text(v, R.id.widget_countdown_text))
        }
        picture("widget_next_minutes", next, 180 to 84); picture("widget_today_minutes", today, 340 to 170)
        // A minute on: still the minute written by Noor, one less.
        Widgets.refresh(app, before.plusSeconds(60))
        assertEquals("\u22121:26", text(shadow.getViewFor(next), R.id.widget_countdown_text))
        // Phone and Noor both Arabic-Indic: Android's ticking countdown again.
        repo.setNumerals(com.noor.app.data.Numerals.ARABIC_INDIC)
        Widgets.refresh(app, before)
        assertEquals(View.VISIBLE, shadow.getViewFor(next).findViewById<View>(R.id.widget_countdown).visibility)
    }

    @After fun realClock() {
        Widgets.clock = java.time.Instant::now
        com.noor.app.notify.StatusNotification.systemZeroDigit = saved
    }

    private val saved = com.noor.app.notify.StatusNotification.systemZeroDigit
}
