package com.noor.app

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.AnalogClock
import android.widget.ImageView
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.Scenery
import com.noor.app.widget.CountdownArt
import com.noor.app.widget.CountdownWheelSpec
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
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * The home-screen widgets (0.9.10; 0.9.12), placed on a simulated home screen and updated as Android would.
 *
 * - Each says the prayer counted to or from and its time, as the home screen does; the Today widget lists the day's six
 *   times in order with that prayer in gold; each has its picture of the sky.
 * - Their words and countdown are pictures in the app's fonts (a widget cannot use an app's font: 0.9.11's were
 *   replaced on phones by the phone's font and digits): the countdown's "+13:" is exactly the app's, in the digits
 *   chosen in Noor whatever the phone's; its seconds are Android's own analog clock turning the wheel of labels, and
 *   attached to a screen it shows, second after second, the label of the second it is.
 * - The countdown's minutes are put up as each minute begins (one non-waking alarm, 1.5 s before it), the whole widget
 *   at each prayer and every 15 minutes.
 * - Where Noor may not set exact alarms, Android's own countdown ticks instead.
 * Pictures of each widget are saved (they are also the previews in the widget picker). One test method (DataStore
 * allows one instance per process).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetsTest {
    private val names = listOf("Fajr", "Shuruq", "Dhuhr", "Asr", "Maghrib", "Isha")

    @Test fun widgetsShowTheNextPrayerAndTheSky() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
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
        val skyTall = shadow.createWidget(SkyWidget::class.java, R.layout.widget_sky)
        val widgets = listOf(NextPrayerWidget::class.java to next, TodayWidget::class.java to today, SkyWidget::class.java to sky)
        // The sizes a launcher gives (dp): a 2 x 1 widget, two 4 x 2, and a 4 x 3 sky (laid out as the home screen's header).
        val dpSize = mapOf(next to (180 to 84), today to (340 to 170), sky to (340 to 170), skyTall to (340 to 260))
        for ((id, sz) in dpSize) manager.updateAppWidgetOptions(id, android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, sz.first); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, sz.first)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, sz.second); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, sz.second)
        })
        Thread.sleep(1500)   // Noor's own update after the settings above (half a second later) is done first
        // Wednesday 4 November 2026 in Istanbul, 15:50: Asr (15:37) has passed and its "+" time is running.
        Widgets.refresh(app, at)

        fun view(id: Int) = shadow.getViewFor(id)
        fun description(v: View, id: Int) = v.findViewById<View>(id).contentDescription.toString()
        fun bitmap(v: View, id: Int) = (v.findViewById<ImageView>(id).drawable as BitmapDrawable).bitmap
        val d = app.resources.displayMetrics.density
        val idOf: Map<Class<*>, Int> = mapOf(NextPrayerWidget::class.java to next, TodayWidget::class.java to today, SkyWidget::class.java to sky)
        /** The countdown's font size in the widget of [cls] (its size as given above). */
        fun em(cls: Class<*>) = dpSize.getValue(idOf.getValue(cls)).let { Widgets.countdownDp(cls, Widgets.Size(0, 0, 0f, it.first.toFloat(), it.second.toFloat())) } * d
        /** The widget's picture of the countdown is the app's line up to its last colon, for [text]. */
        fun assertCountdown(v: View, cls: Class<*>, text: String) {
            val want = CountdownArt.line(app, text, em(cls), seconds = true).main
            val got = bitmap(v, R.id.widget_count_main)
            assertTrue("${cls.simpleName}: the countdown's picture is not \"$text\" up to its last colon", got.sameAs(want))
            assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_count_window).visibility)
            assertEquals(View.GONE, v.findViewById<View>(R.id.widget_count_plain).visibility)
        }
        for ((cls, id) in widgets) {
            val v = view(id)
            val (prayer, time) = description(v, R.id.widget_title).split(", ")
            assertTrue("prayer: $prayer", prayer in names)
            assertTrue("time: $time", time.matches(Regex("\\d{1,2}:\\d{2} [AP]M")))
            assertEquals(prayer to time, Widgets.lastTitle[cls])
            assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_countdown_box).visibility)
            assertNotNull(v.findViewById<ImageView>(R.id.widget_sky).drawable)
            // At 15:50 the widgets count the time since Asr, as the home screen does: "+13:00".
            assertEquals("Asr", prayer)
            assertEquals("Asr, +13:00", description(v, R.id.widget_countdown_box))
            assertCountdown(v, cls, "+13:00")
        }
        // Today: the six times in order, Asr in gold.
        val t = view(today)
        assertEquals(names, Widgets.lastToday!!.names)
        assertEquals(3, Widgets.lastToday!!.marked)
        assertTrue(description(t, R.id.widget_grid).startsWith("Fajr "))
        val grid = bitmap(t, R.id.widget_grid)
        fun gold(col: Int, row: Int): Int {
            var n = 0
            val w = grid.width / 3; val h = grid.height / 2
            for (y in row * h until (row + 1) * h) for (x in col * w until (col + 1) * w) {
                val p = grid.getPixel(x, y)
                if (Color.alpha(p) > 200 && Color.red(p) > 220 && Color.blue(p) < 175) n++
            }
            return n
        }
        assertTrue("Asr in gold", gold(0, 1) > 50)
        assertEquals("Maghrib not in gold", 0, gold(1, 1))
        assertTrue(description(t, R.id.widget_place).startsWith("Istanbul"))

        // The next update: one non-waking alarm, 1.5 s before the next minute (15:51:00, when "+14" begins).
        fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .filter { shadowOf(it.operation).savedIntent.component?.className == WidgetRefreshReceiver::class.java.name }
        val minute = ZonedDateTime.of(2026, 11, 4, 15, 51, 0, 0, istanbul).toInstant()
        assertEquals(1, alarms().size)
        assertEquals(AlarmManager.RTC, alarms()[0].type)
        assertEquals(minute.toEpochMilli() - 1500, alarms()[0].triggerAtTime)

        // Pictures (and the previews in the widget picker).
        fun picture(name: String, id: Int, size: Pair<Int, Int>) {
            val v = view(id)
            val w = (size.first * d).toInt(); val h = (size.second * d).toInt()
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(File("build/screenshots").apply { mkdirs() }, "90_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        picture("widget_next", next, 180 to 84); picture("widget_today", today, 340 to 170); picture("widget_sky", sky, 340 to 170)
        picture("widget_sky_tall", skyTall, 340 to 260)
        // The tall sky is laid out as the home screen's header: the countdown under the prayer, centred.
        assertNotNull(view(skyTall).findViewById<View>(R.id.widget_place))
        assertEquals("Asr, +13:00", description(view(skyTall), R.id.widget_countdown_box))

        // The seconds: Android's own analog clock, with the wheel Noor gave it, on a screen. Second after second (real
        // seconds: the clock reads the real time), its window shows the label of the second it is: counting up after
        // Asr, in Western digits, as the app writes it.
        val screen = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val nv = view(next)
        screen.setContentView(nv)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val clockView = nv.findViewById<AnalogClock>(R.id.widget_count_seconds)
        val window = nv.findViewById<android.widget.FrameLayout>(R.id.widget_count_window)
        fun shown(): Bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also { window.draw(Canvas(it)) }
        val seen = HashSet<Int>()
        repeat(3) {
            Thread.sleep(1050)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            val s = Math.round(ReflectionHelpers.getField<Float>(clockView, "mSeconds")) % 60
            seen += s
            val got = shown()
            val want = label("%02d".format(s), em(NextPrayerWidget::class.java), got.width, got.height, app)
            assertTrue("second $s: the window does not show \"%02d\" (ink diff ${inkDiff(got, want)})".format(s), inkDiff(got, want) < 6.0)
        }
        assertTrue("the seconds tick: $seen", seen.size >= 2)

        // The minute's alarm: at 15:51 the countdown's minutes become "+14", put up as the minute begins (the simulated
        // home screen has no partial updates: they are applied to the widget as a launcher applies them).
        Widgets.partialUpdate = { _, id, v -> v.reapply(app, view(id)) }
        moment = minute.minusMillis(1500); since = android.os.SystemClock.elapsedRealtime()
        Widgets.tick(app, minute)
        for ((cls, id) in widgets) {
            assertCountdown(view(id), cls, "+14:00")
            assertEquals("Asr, +14:00", description(view(id), R.id.widget_countdown_box))
        }
        assertEquals(ZonedDateTime.of(2026, 11, 4, 15, 52, 0, 0, istanbul).toInstant().toEpochMilli() - 1500, alarms().single().triggerAtTime)

        // Noor in Arabic: the widgets too; Western digits still, whatever the language.
        moment = at; since = android.os.SystemClock.elapsedRealtime()
        repo.setLanguage(com.noor.app.data.Language.ARABIC)
        Widgets.refresh(app, at)
        assertEquals(com.noor.prayer.Prayer.ASR.ar, Widgets.lastTitle[NextPrayerWidget::class.java]!!.first)
        assertEquals(com.noor.prayer.Prayer.FAJR.ar, Widgets.lastToday!!.names[0])
        for ((cls, id) in widgets) assertCountdown(view(id), cls, "+13:00")
        assertWheel(clockView, up = true, arabic = false, em(NextPrayerWidget::class.java), app)
        // Laid out right to left, as Noor is in Arabic (the phone here is in English).
        for ((_, id) in widgets) assertEquals(View.LAYOUT_DIRECTION_RTL, view(id).layoutDirection)
        picture("widget_next_ar", next, 180 to 84); picture("widget_today_ar", today, 340 to 170); picture("widget_sky_ar", sky, 340 to 170)
        picture("widget_sky_tall_ar", skyTall, 340 to 260)

        // Arabic-Indic digits chosen in Noor: the countdown in Arabic-Indic digits, its seconds too.
        repo.setLanguage(com.noor.app.data.Language.ENGLISH)
        repo.setNumerals(Numerals.ARABIC_INDIC)
        Widgets.refresh(app, at)
        for ((_, id) in widgets) assertEquals(View.LAYOUT_DIRECTION_LTR, view(id).layoutDirection)
        for ((cls, id) in widgets) assertCountdown(view(id), cls, "+١٣:٠٠")
        assertWheel(clockView, up = true, arabic = true, em(NextPrayerWidget::class.java), app)
        picture("widget_next_arabic_digits", next, 180 to 84)

        // Before a prayer: at 14:10, "−1:27:00" to Asr (15:37), with the home screen's true minus sign; the seconds count down.
        val before = ZonedDateTime.of(2026, 11, 4, 14, 10, 0, 0, istanbul).toInstant()
        moment = before; since = android.os.SystemClock.elapsedRealtime()
        repo.setNumerals(Numerals.WESTERN)
        Widgets.refresh(app, before)
        assertEquals("Asr", Widgets.lastTitle[NextPrayerWidget::class.java]!!.first)
        for ((cls, id) in widgets) assertCountdown(view(id), cls, "−1:27:00")
        assertWheel(clockView, up = false, arabic = false, em(NextPrayerWidget::class.java), app)
        picture("widget_next_before", next, 180 to 84); picture("widget_today_before", today, 340 to 170); picture("widget_sky_before", sky, 340 to 170)
        picture("widget_sky_tall_before", skyTall, 340 to 260)

        // Where Noor may not set exact alarms (its minutes could not change on time): Android's own countdown, ticking.
        moment = at; since = android.os.SystemClock.elapsedRealtime()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        Widgets.refresh(app, at)
        val plain = view(next).findViewById<android.widget.Chronometer>(R.id.widget_count_plain)
        assertEquals(View.VISIBLE, plain.visibility)
        assertEquals(View.GONE, view(next).findViewById<View>(R.id.widget_count_main).visibility)
        assertEquals("+%s", plain.format)
        fun seconds(t: CharSequence) = t.toString().trim('+', '−').split(':').fold(0) { acc, part -> acc * 60 + part.toInt() }
        val start = seconds(plain.text)
        assertEquals(13 * 60, start)
        for (k in 1..3) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            assertEquals("ticking: ${plain.text}", start + k, seconds(plain.text))
        }
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    /** The clock's second hand is the wheel counting [up] or down in [arabic] or Western digits: drawn at second 7. */
    private fun assertWheel(clock: AnalogClock, up: Boolean, arabic: Boolean, em: Float, ctx: android.content.Context) {
        val hand = ReflectionHelpers.getField<android.graphics.drawable.Drawable>(clock, "mSecondHand")
        val dial = ctx.getDrawable(R.drawable.cd_dial)!!
        val size = Math.round(CountdownWheelSpec.D_EM * em)
        val win = Math.round(CountdownWheelSpec.WIN_W_EM * em)
        val h = kotlin.math.ceil((CountdownWheelSpec.A_EM + CountdownWheelSpec.B_EM) * em).toInt()
        val bmp = Bitmap.createBitmap(win, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(Math.round((win - size) / 2f).toFloat(), 0f)
        val x = size / 2; val y = size / 2
        c.scale(size.toFloat() / dial.intrinsicWidth, size.toFloat() / dial.intrinsicWidth, x.toFloat(), y.toFloat())
        c.rotate(7 / 60f * 360f, x.toFloat(), y.toFloat())
        hand.setBounds(x - hand.intrinsicWidth / 2, y - hand.intrinsicHeight / 2, x + hand.intrinsicWidth / 2, y + hand.intrinsicHeight / 2)
        hand.draw(c)
        val v = if (up) 7 else 52
        val text = "%02d".format(v).map { if (arabic) '٠' + (it - '0') else it }.joinToString("")
        val want = label(text, em, win, h, ctx)
        assertTrue("the wheel is not the one counting ${if (up) "up" else "down"} in ${if (arabic) "Arabic-Indic" else "Western"} digits (ink diff ${inkDiff(bmp, want)})", inkDiff(bmp, want) < 6.0)
    }

    private fun label(text: String, em: Float, w: Int, h: Int, ctx: android.content.Context): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = CountdownArt.paint(ctx, em).apply { alpha = (255 * CountdownWheelSpec.SECONDS_ALPHA).toInt() }
        Canvas(bmp).drawText(text, CountdownWheelSpec.P_EM * em - CountdownArt.spacing(p, text) / 2f, CountdownWheelSpec.A_EM * em, p)
        return bmp
    }

    private fun inkDiff(a: Bitmap, b: Bitmap): Double {
        var sum = 0.0
        for (y in 0 until minOf(a.height, b.height)) for (x in 0 until minOf(a.width, b.width)) {
            fun ink(p: Int) = if (Color.red(p) > 180) Color.alpha(p) else 0
            sum += abs(ink(a.getPixel(x, y)) - ink(b.getPixel(x, y)))
        }
        return sum / (a.width * a.height)
    }

    @After fun reset() {
        Widgets.clock = java.time.Instant::now
        Widgets.partialUpdate = { manager, id, v -> manager.partiallyUpdateAppWidget(id, v) }
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }
}
