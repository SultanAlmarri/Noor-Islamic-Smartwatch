package com.noor.app

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.AnalogClock
import android.widget.ImageView
import android.widget.TextView
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.Scenery
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import com.noor.app.widget.CountPicture
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The home-screen widgets (0.9.10; countdown 0.9.14), placed on a simulated home screen and updated as Android would:
 * each says the next prayer and its time with a live countdown in Noor's digits, as the home screen and the notification
 * do; the Today widget lists the day's six times in order with the next one marked; every widget has its picture of the
 * sky; the next update is armed (non-waking) no later than the next prayer and within 15 minutes for the picture, and
 * each minute for the countdown's hours and minutes, which then changes alone. The seconds are a wheel of Noor's digits
 * turned by Android's AnalogClock: the test draws a widget and reads which second its window shows, against the real
 * clock. Pictures of each widget are saved (they are also the previews in the widget picker). One test method (DataStore
 * allows one instance per process).
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
        fun visible(v: View, id: Int) = v.findViewById<View>(id).visibility == View.VISIBLE
        fun main(v: View) = (v.findViewById<ImageView>(R.id.widget_count_main).drawable as BitmapDrawable).bitmap
        fun content(now: Instant) = runBlocking {
            val s = repo.current()
            StatusNotification.content(WearSync.configFor(s, now.toEpochMilli(), app.container.timetables)!!, s, now, false)
        }
        val names = listOf("Fajr", "Shuruq", "Dhuhr", "Asr", "Maghrib", "Isha")
        for (id in listOf(next, today, sky)) {
            val v = shadow.getViewFor(id)
            assertTrue("prayer: ${text(v, R.id.widget_prayer)}", text(v, R.id.widget_prayer) in names)
            assertTrue(text(v, R.id.widget_time).matches(Regex("\\d{1,2}:\\d{2} [AP]M")))
            // The countdown: Noor's picture of "+13" and ":", then the seconds wheel; not Android's digital countdown.
            assertTrue(visible(v, R.id.widget_count_row))
            assertTrue(!visible(v, R.id.widget_countdown) && !visible(v, R.id.widget_countdown_text))
            assertTrue(main(v).sameAs(CountPicture.draw(app, "+13")))
            assertEquals(Widgets.wheelFor(content(at), false).zone, v.findViewById<AnalogClock>(R.id.widget_count_wheel).timeZone)
            assertNotNull((v.findViewById<ImageView>(R.id.widget_sky).drawable))
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

        // The next update: non-waking, when the countdown's minute turns (15:51, "+14"), and only the countdown changes then.
        fun alarm() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .single { shadowOf(it.operation).savedIntent.component?.className == WidgetRefreshReceiver::class.java.name }
        assertEquals(AlarmManager.RTC, alarm().type)
        assertEquals(at.plusSeconds(60).plusMillis(30).toEpochMilli(), alarm().triggerAtTime)
        assertTrue(shadowOf(alarm().operation).savedIntent.getBooleanExtra(Widgets.EXTRA_MINUTE, false))

        // Pictures (and the previews in the widget picker).
        val d = app.resources.displayMetrics.density
        fun lay(v: View, size: Pair<Int, Int>) {
            val w = (size.first * d).toInt(); val h = (size.second * d).toInt()
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
        }
        fun shot(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        fun picture(name: String, id: Int, size: Pair<Int, Int>) {
            val v = shadow.getViewFor(id)
            lay(v, size)
            File(File("build/screenshots").apply { mkdirs() }, "90_$name.png").outputStream().use { shot(v).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        picture("widget_next", next, 180 to 84); picture("widget_today", today, 340 to 170); picture("widget_sky", sky, 340 to 170)

        /**
         * The second the widget's window shows: the widget is drawn with and without its wheel, and each of the 60
         * labels, laid over the second picture where the window is, is compared with the first. The closest is shown.
         */
        fun secondShown(id: Int, arabicDigits: Boolean): Int {
            val v = shadow.getViewFor(id)
            lay(v, 340 to 170)
            val wheel = v.findViewById<AnalogClock>(R.id.widget_count_wheel)
            val window = v.findViewById<View>(R.id.widget_count_window)
            val r = Rect(0, 0, window.width, window.height)
            (v as android.view.ViewGroup).offsetDescendantRectToMyCoords(window, r)
            val full = shot(v)
            wheel.visibility = View.INVISIBLE
            val bare = shot(v)
            wheel.visibility = View.VISIBLE
            val set = if (arabicDigits) "arabic" else "latin"
            fun diff(n: Int): Long {
                val c = bare.copy(Bitmap.Config.ARGB_8888, true)
                val label = app.getDrawable(app.resources.getIdentifier("noor_sec_${set}_%02d".format(n), "drawable", app.packageName))!!
                val lw = label.intrinsicWidth; val lh = label.intrinsicHeight
                val canvas = Canvas(c); canvas.clipRect(r)
                label.setBounds(r.centerX() - lw / 2, r.top, r.centerX() - lw / 2 + lw, r.top + lh); label.draw(canvas)
                var sum = 0L
                for (y in r.top until r.bottom) for (x in r.left until r.right) {
                    val a = full.getPixel(x, y); val b = c.getPixel(x, y)
                    sum += Math.abs((a shr 16 and 255) - (b shr 16 and 255)) + Math.abs((a shr 8 and 255) - (b shr 8 and 255)) + Math.abs((a and 255) - (b and 255))
                }
                return sum
            }
            val scores = (0 until 60).map { diff(it) }
            val best = scores.indices.minByOrNull { scores[it] }!!
            // The window shows one label clearly: almost exactly (a few levels of 255 on average, from smoothing), and
            // better than any other (one that shares a digit, or differs only by a thin stroke, can come within 1.5 times).
            val perChannel = scores[best].toDouble() / (r.width() * r.height() * 3)
            assertTrue("a clear label (${scores.sorted().take(3)}, $perChannel per channel)", perChannel < 6 && scores.sorted()[1] > 1.1 * scores[best])
            return best
        }
        /** What the wheel must show at the real time [now] (the clock it turns on is the real one). */
        fun expected(now: Instant, countAt: Instant, elapsed: Boolean): Int {
            val tSec = Math.floorMod(now.epochSecond, 60L).toInt(); val p = Math.floorMod(countAt.epochSecond, 60L).toInt()
            return if (elapsed) Math.floorMod(tSec - p, 60) else Math.floorMod(p - tSec - 1, 60)
        }
        fun checkSeconds(id: Int, arabicDigits: Boolean, c: StatusNotification.Content) {
            val start = Instant.now()
            // The clock reads the time when the wheel is set or seen; set it again now, as a home screen would on showing it.
            val wheel = shadow.getViewFor(id).findViewById<AnalogClock>(R.id.widget_count_wheel)
            wheel.timeZone = wheel.timeZone
            val shown = secondShown(id, arabicDigits)
            val end = Instant.now()
            // Android's clock rounds to the nearest second when it reads the time between its ticks (here, as the wheel
            // is set): up to half a second ahead until its next tick, which falls on the second.
            val ok = setOf(expected(start, c.countAt, c.elapsed), expected(end.plusMillis(500), c.countAt, c.elapsed))
            assertTrue("seconds shown $shown, expected one of $ok", shown in ok)
        }
        // Since Asr, counting up.
        checkSeconds(today, false, content(at))

        // Noor in Arabic: the widgets too.
        repo.setLanguage(com.noor.app.data.Language.ARABIC)
        Widgets.refresh(app, at)
        assertEquals(com.noor.prayer.Prayer.ASR.ar, text(shadow.getViewFor(next), R.id.widget_prayer))
        assertEquals(com.noor.prayer.Prayer.FAJR.ar, text(shadow.getViewFor(today), R.id.widget_name_0))
        Thread.sleep(1500)   // Noor's own update after the settings above (half a second later) is done first

        // Arabic-Indic digits chosen in Noor on a phone that writes Western ones: Noor's digits, hours, minutes and seconds.
        repo.setLanguage(com.noor.app.data.Language.ENGLISH)
        repo.setNumerals(Numerals.ARABIC_INDIC)
        Thread.sleep(1500)
        Widgets.refresh(app, at)
        assertTrue(main(shadow.getViewFor(next)).sameAs(CountPicture.draw(app, "+١٣")))
        checkSeconds(today, true, content(at))
        picture("widget_next_arabic_digits", next, 180 to 84)

        // Before a prayer: at 14:10, "−1:27" and the seconds counting down to Asr (15:37).
        val before = ZonedDateTime.of(2026, 11, 4, 14, 10, 0, 0, istanbul).toInstant()
        moment = before; since = android.os.SystemClock.elapsedRealtime()
        repo.setNumerals(Numerals.WESTERN)
        Thread.sleep(1500)
        Widgets.refresh(app, before)
        val bv = shadow.getViewFor(next)
        assertEquals("Asr", text(bv, R.id.widget_prayer))
        assertTrue(main(bv).sameAs(CountPicture.draw(app, "−1:27")))
        checkSeconds(today, false, content(before))
        picture("widget_next_before", next, 180 to 84); picture("widget_today_before", today, 340 to 170); picture("widget_sky_before", sky, 340 to 170)

        // The owner's phone (13 November 2026): the phone in Arabic (Android writes Arabic-Indic digits), Noor in
        // Western digits. The widget's countdown is Noor's, in Western digits, its seconds included.
        StatusNotification.systemZeroDigit = { '٠' }
        Widgets.refresh(app, before)
        for (id in listOf(next, today, sky)) {
            val v = shadow.getViewFor(id)
            assertTrue(visible(v, R.id.widget_count_row))
            assertTrue(main(v).sameAs(CountPicture.draw(app, "−1:27")))
        }
        checkSeconds(next, false, content(before))
        // At 14:10:00 exactly it reads "−1:27" (and 00), and the next instant "−1:26:59": the update for that is armed at
        // once, and changes the countdown alone, the sky picture untouched.
        assertEquals(before.plusMillis(30).toEpochMilli(), alarm().triggerAtTime)
        assertTrue(shadowOf(alarm().operation).savedIntent.getBooleanExtra(Widgets.EXTRA_MINUTE, false))
        val skyPicture = shadow.getViewFor(today).findViewById<ImageView>(R.id.widget_sky).drawable
        // Robolectric's home screen has no partial updates: apply them as a home screen does, over the widget as it is.
        Widgets.partialUpdate = { _, id, views -> views.reapply(app, shadow.getViewFor(id)) }
        Widgets.refresh(app, before.plusMillis(30), minuteOnly = true)
        assertTrue(main(shadow.getViewFor(today)).sameAs(CountPicture.draw(app, "−1:26")))
        assertTrue("the picture is left as it was", skyPicture === shadow.getViewFor(today).findViewById<ImageView>(R.id.widget_sky).drawable)
        // Then the next one a minute later, at 14:11:00 ("−1:25:59" just after).
        assertEquals(before.plusSeconds(60).plusMillis(30).toEpochMilli(), alarm().triggerAtTime)

        // Before Android 12 (no seconds wheel): where the phone writes other digits, Noor's, to the minute; where it
        // writes Noor's, Android's own ticking countdown.
        Widgets.wheelSupported = { false }
        Widgets.refresh(app, before)
        shadow.getViewFor(next).let { v ->
            assertTrue(visible(v, R.id.widget_countdown_text) && !visible(v, R.id.widget_count_row) && !visible(v, R.id.widget_countdown))
            assertEquals("−1:27", text(v, R.id.widget_countdown_text))
        }
        StatusNotification.systemZeroDigit = { '0' }
        Widgets.refresh(app, before)
        shadow.getViewFor(next).let { v ->
            assertTrue(visible(v, R.id.widget_countdown) && !visible(v, R.id.widget_count_row))
            assertEquals("−1:27:00", v.findViewById<android.widget.Chronometer>(R.id.widget_countdown).text.toString())
        }
        // Android ticks it: no update each minute then.
        assertTrue(!shadowOf(alarm().operation).savedIntent.getBooleanExtra(Widgets.EXTRA_MINUTE, false))
    }

    private val savedZero = StatusNotification.systemZeroDigit

    @After fun restore() {
        Widgets.clock = java.time.Instant::now
        Widgets.wheelSupported = { android.os.Build.VERSION.SDK_INT >= 31 }
        StatusNotification.systemZeroDigit = savedZero
        Widgets.partialUpdate = { m, id, v -> m.partiallyUpdateAppWidget(id, v) }
    }
}
