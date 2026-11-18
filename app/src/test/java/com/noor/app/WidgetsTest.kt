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
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import com.noor.app.notify.StatusTicker
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
 * The home-screen widgets (0.9.10; countdown 0.9.14; 0.24 drawn in Plex, one full update a minute), placed on a
 * simulated home screen and updated as Android would: each says the next prayer and its time with a live countdown in
 * Noor's digits, as the home screen and the notification do; the Today widget lists the day's six times in order with
 * the next one marked; the next update is armed (non-waking) for the countdown's next minute, built ahead and handed
 * over on the change, so the minute shown is the right one just after it. The seconds are a wheel of Noor's digits
 * turned by Android's AnalogClock: the test draws a widget and reads which second its window shows, against the real
 * clock. The widgets' words are pictures: what they say is read from the model of what was drawn. One test method
 * (DataStore allows one instance per process).
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
        // One layout per widget (the simulated home screen applies one), and the changes stepped by the test.
        Widgets.sizeMaps = { false }
        Widgets.inProcess = false
        val repo = app.container.settings
        repo.setOnboardingDone(); repo.setLocationMode(LocationMode.MANUAL); repo.setManualCity("ist")
        val manager = AppWidgetManager.getInstance(app)
        val shadow = shadowOf(manager)
        val next = shadow.createWidget(NextPrayerWidget::class.java, R.layout.widget_v3)
        val today = shadow.createWidget(TodayWidget::class.java, R.layout.widget_v3)
        val sky = shadow.createWidget(SkyWidget::class.java, R.layout.widget_v3)
        // Wednesday 4 November 2026 in Istanbul, 15:50: Asr (15:37) has passed and its "+" time is running.
        Widgets.refresh(app, at)

        fun drawn(id: Int) = Widgets.builtFor(id)!!.design.drawn
        fun said(id: Int, role: String) = drawn(id).first { it.role == role }.text
        fun visible(v: View, id: Int) = v.findViewById<View>(id).visibility == View.VISIBLE
        fun main(v: View) = (v.findViewById<ImageView>(R.id.widget_count_main).drawable as BitmapDrawable).bitmap
        fun content(now: Instant) = runBlocking {
            val s = repo.current()
            StatusNotification.content(WearSync.configFor(s, now.toEpochMilli(), app.container.timetables)!!, s, now, false)
        }
        /** The countdown's picture as built, checked to be the one on the widget, and its words. */
        fun count(id: Int): String {
            val spec = Widgets.builtFor(id)!!.countSpec!!
            assertTrue(main(shadow.getViewFor(id)).sameAs(CountPicture.draw(app, spec)))
            return spec.leading
        }
        val names = listOf("Fajr", "Shuruq", "Dhuhr", "Asr", "Maghrib", "Isha")
        for (id in listOf(next, today, sky)) {
            val v = shadow.getViewFor(id)
            val title = said(id, "name")
            assertTrue("prayer: $title", names.any { title.startsWith(it) })
            // (0.24) AM/PM a thin space (U+2009) from the digits.
            assertTrue(title, title.matches(Regex("\\w+ \\d{1,2}:\\d{2} [AP]M")))
            // The countdown: Noor's picture of "+13" and ":", then the seconds wheel; nothing in the phone's font.
            assertTrue(visible(v, R.id.widget_count_row))
            assertEquals("+13", count(id))
            assertEquals(Widgets.wheelFor(content(at), false).zone, v.findViewById<AnalogClock>(R.id.widget_count_wheel).timeZone)
            assertNotNull((v.findViewById<ImageView>(R.id.widget_bg).drawable))
        }
        // At 15:50 the widgets count the time since Asr, as the home screen does.
        assertTrue(said(next, "name").startsWith("Asr "))
        // Today: the six times in order, Asr marked: in its capsule, in white over the sky (0.24: gold on the capsule
        // there would read under 3:1; the gold is in its sign), in the design's gold over dark.
        val cols = drawn(today).filter { it.role.startsWith("col_name") }
        assertEquals(names, cols.map { it.text })
        assertEquals("col_name_marked", cols[3].role)
        assertEquals(0xFFFFFFFF.toInt(), cols[3].color)
        val onDark = Widgets.previewBuilt(app, com.noor.app.widget.WidgetKind.TODAY, com.noor.app.widget.WidgetOptions(background = com.noor.app.widget.WidgetBackground.DARK, opacity = 100), 340, 190, at) { b ->
            assertEquals(0xFFFFD36B.toInt(), b.design.drawn.first { it.role == "col_name_marked" }.color)
        }
        assertNotNull(onDark)
        assertTrue(cols.filterIndexed { i, _ -> i != 3 }.all { it.role == "col_name" })

        // The next update: non-waking, ahead of the moment the countdown's minute turns (to "+14"), bringing that moment.
        fun alarm() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .single { shadowOf(it.operation).savedIntent.component?.className == WidgetRefreshReceiver::class.java.name }
        fun alarmFor() = shadowOf(alarm().operation).savedIntent.getLongExtra(Widgets.EXTRA_AT, 0L)
        assertEquals(AlarmManager.RTC, alarm().type)
        val change = StatusTicker.nextChange(content(at), at.toEpochMilli(), false)
        assertEquals(change, alarmFor())
        assertEquals(change - 450, alarm().triggerAtTime)

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
        picture("widget_next", next, 180 to 96); picture("widget_today", today, 340 to 190); picture("widget_sky", sky, 340 to 190)

        /**
         * The second the widget's window shows: the widget is drawn with and without its wheel, and each of the 60
         * labels, laid over the second picture where the window is, is compared with the first. The closest is shown.
         */
        fun secondShown(id: Int, arabicDigits: Boolean): Int {
            val v = shadow.getViewFor(id)
            lay(v, 340 to 190)
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
                val label = app.getDrawable(app.resources.getIdentifier("noor_sec_${set}_%02d".format(n), "drawable", app.packageName))!!.mutate()
                // (0.24) The wheel is tinted the seconds' colour (62 % of the text's) and drawn at the widget's scale:
                // its clock is made smaller or larger, and Android scales the canvas about the clock's centre, the
                // label at its intrinsic size; the same here, about the top.
                label.setTint(0x9EFFFFFF.toInt())
                val k = Widgets.builtFor(id)!!.countScale
                val lw = label.intrinsicWidth; val lh = label.intrinsicHeight
                val canvas = Canvas(c); canvas.clipRect(r)
                canvas.scale(k, k, r.exactCenterX(), r.top.toFloat())
                label.setBounds((r.exactCenterX() - lw / 2f).toInt(), r.top, (r.exactCenterX() - lw / 2f).toInt() + lw, r.top + lh); label.draw(canvas)
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
            // at least twice as close as any other (as 0.23 asked).
            val perChannel = scores[best].toDouble() / (r.width() * r.height() * 3)
            assertTrue("a clear label (${scores.sorted().take(3)}, $perChannel per channel)", perChannel < 8 && scores.sorted()[1] > 2.0 * scores[best])
            return best
        }
        /** What the wheel must show at the real time [now] (the clock it turns on is the real one). */
        fun expected(now: Instant, countAt: Instant, elapsed: Boolean): Int {
            val tSec = Math.floorMod(now.epochSecond, 60L).toInt(); val p = Math.floorMod(countAt.epochSecond, 60L).toInt()
            // (0.24) Before the prayer the time left is rounded up (Countdowns.timeLeft: the prayer's time less the clock's),
            // so at real second t + ε it has p − t seconds; after it, t − p.
            return if (elapsed) Math.floorMod(tSec - p, 60) else Math.floorMod(p - tSec, 60)
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
            // (Looking takes time, and on a busy machine more than a second: every second from the start of the look to
            // its end is a right answer, and no other.)
            val last = end.plusMillis(500)
            val ok = generateSequence(start) { it.plusSeconds(1) }.takeWhile { it.isBefore(last) }
                .map { expected(it, c.countAt, c.elapsed) }.toSet() + expected(last, c.countAt, c.elapsed)
            assertTrue("seconds shown $shown, expected one of $ok", shown in ok)
        }
        // Since Asr, counting up.
        checkSeconds(today, false, content(at))

        // Noor in Arabic: the widgets too.
        repo.setLanguage(com.noor.app.data.Language.ARABIC)
        Widgets.refresh(app, at)
        assertTrue(said(next, "name").startsWith(com.noor.prayer.Prayer.ASR.ar))
        assertEquals(com.noor.prayer.Prayer.FAJR.ar, drawn(today).first { it.role.startsWith("col_name") }.text)
        Thread.sleep(1500)   // Noor's own update after the settings above (half a second later) is done first

        // Arabic-Indic digits chosen in Noor on a phone that writes Western ones: Noor's digits, hours, minutes and seconds.
        repo.setLanguage(com.noor.app.data.Language.ENGLISH)
        repo.setNumerals(Numerals.ARABIC_INDIC)
        Thread.sleep(1500)
        Widgets.refresh(app, at)
        assertEquals("+١٣", count(next))
        checkSeconds(today, true, content(at))
        picture("widget_next_arabic_digits", next, 180 to 96)

        // Before a prayer: at 14:10, "−1:27" and the seconds counting down to Asr (15:37).
        val before = ZonedDateTime.of(2026, 11, 4, 14, 10, 0, 0, istanbul).toInstant()
        moment = before; since = android.os.SystemClock.elapsedRealtime()
        repo.setNumerals(Numerals.WESTERN)
        Thread.sleep(1500)
        moment = before; since = android.os.SystemClock.elapsedRealtime()
        Widgets.refresh(app, before)
        assertTrue(said(next, "name").startsWith("Asr "))
        assertEquals("−1:27", count(next))
        checkSeconds(today, false, content(before))
        picture("widget_next_before", next, 180 to 96); picture("widget_today_before", today, 340 to 190); picture("widget_sky_before", sky, 340 to 190)

        // The owner's phone (13 November 2026): the phone in Arabic (Android writes Arabic-Indic digits), Noor in
        // Western digits. The widget's countdown is Noor's, in Western digits, its seconds included.
        StatusNotification.systemZeroDigit = { '٠' }
        moment = before; since = android.os.SystemClock.elapsedRealtime()
        Widgets.refresh(app, before)
        for (id in listOf(next, today, sky)) {
            assertTrue(visible(shadow.getViewFor(id), R.id.widget_count_row))
            assertEquals("−1:27", count(id))
        }
        checkSeconds(next, false, content(before))
        // At 14:10:00 it reads "−1:27" (and 00) through that whole second (0.24: the time left is rounded up, so it is the
        // prayer's time less the clock's), and at 14:10:01 "−1:26:59": the update for that instant is armed ahead of it.
        val crossing = before.plusSeconds(1).toEpochMilli()
        assertEquals(crossing, alarmFor())
        assertEquals(crossing - 450, alarm().triggerAtTime)
        // The minute pushed for the crossing (the alarm's): built ahead, handed over just before it, and showing the
        // minute that is right just after it ("−1:26"), not the one before ("−1:27").
        moment = Instant.ofEpochMilli(crossing - 450); since = android.os.SystemClock.elapsedRealtime()
        val t0 = System.currentTimeMillis()
        Widgets.ahead(app, crossing)
        val waited = Widgets.pushedAtMs - t0
        assertTrue("handed over just before the change, not at once ($waited ms after the alarm)", waited in 350..450)
        assertEquals(Widgets.leading(content(Instant.ofEpochMilli(crossing + 1)), Instant.ofEpochMilli(crossing + 1), com.noor.app.util.Formatter(false, Numerals.WESTERN, com.noor.app.data.TimeFormat.H12)), count(today))
        assertEquals("−1:26", count(today))
        assertEquals("−1:27", Widgets.leading(content(Instant.ofEpochMilli(crossing - 1)), Instant.ofEpochMilli(crossing - 1), com.noor.app.util.Formatter(false, Numerals.WESTERN, com.noor.app.data.TimeFormat.H12)))
        // Then the next one a minute later, at 14:11:01 ("−1:25:59"); the alarm that brought this one is not done twice.
        assertEquals(before.plusSeconds(61).toEpochMilli(), alarmFor())
        val pushedBefore = Widgets.pushedAtMs
        Widgets.ahead(app, crossing)
        assertEquals(pushedBefore, Widgets.pushedAtMs)

        // In Minhad's own process the same, timed by itself: built ahead and handed over at the change.
        Widgets.inProcess = true
        val soon = before.plusSeconds(61).toEpochMilli()
        moment = Instant.ofEpochMilli(soon - 700); since = android.os.SystemClock.elapsedRealtime()
        Widgets.refresh(app, moment)
        assertEquals("−1:26", count(today))
        Thread.sleep(1000)
        assertEquals("−1:25", count(today))
        Widgets.inProcess = false

        // Before Android 12 (no seconds wheel): Noor's countdown to the minute, a picture in Noor's font.
        Widgets.wheelSupported = { false }
        moment = before; since = android.os.SystemClock.elapsedRealtime()
        Widgets.refresh(app, before)
        shadow.getViewFor(next).let { v ->
            assertTrue(!visible(v, R.id.widget_count_slot))
            assertEquals("−1:27", drawn(next).first { it.role == "countdown" }.text)
        }
    }

    private val savedZero = StatusNotification.systemZeroDigit

    @After fun restore() {
        Widgets.clock = Instant::now
        Widgets.wheelSupported = { android.os.Build.VERSION.SDK_INT >= 31 }
        Widgets.sizeMaps = { true }
        Widgets.inProcess = true
        StatusNotification.systemZeroDigit = savedZero
    }
}
