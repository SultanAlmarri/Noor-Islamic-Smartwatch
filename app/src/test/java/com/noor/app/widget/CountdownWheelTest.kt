package com.noor.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noor.app.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import com.noor.app.widget.CountdownWheelSpec as Spec

/**
 * 0.9.12: the seconds wheels (tools/widget/countdown_wheels.py) as Android's analog clock shows them. The clock scales
 * its dial (the wheel's size) to the view and turns the second hand by s / 60 of a turn during second s; the window
 * shows the top of it. For every second, both directions and both kinds of digits, the window must show exactly the
 * label the home screen would write — 59 − s counting down, s counting up — in Plex Light at the seconds' brightness,
 * where the line continues after "−7:08:", and nothing of the labels before and after it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CountdownWheelTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** The window of a widget whose countdown font is [em] px, at second [s] of the clock, as Android draws it. */
    private fun window(wheelId: Int, em: Float, s: Int): Bitmap {
        val dial = ctx.getDrawable(R.drawable.cd_dial)!!
        val hand = ctx.getDrawable(wheelId)!!
        val clock = (Spec.D_EM * em).let { Math.round(it) }
        val win = (Spec.WIN_W_EM * em).let { Math.round(it) }
        val h = kotlin.math.ceil((Spec.A_EM + Spec.B_EM) * em).toInt()
        val left = Math.round((win - clock) / 2f)
        val bmp = Bitmap.createBitmap(win, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // As AnalogClock.onDraw: the view's centre, the dial scaled to fit, the hand turned about the centre.
        c.translate(left.toFloat(), 0f)
        val x = clock / 2; val y = clock / 2
        val scale = minOf(clock.toFloat() / dial.intrinsicWidth, clock.toFloat() / dial.intrinsicHeight)
        c.scale(scale, scale, x.toFloat(), y.toFloat())
        c.rotate(s / 60f * 360f, x.toFloat(), y.toFloat())
        val w = hand.intrinsicWidth; val hh = hand.intrinsicHeight
        hand.setBounds(x - w / 2, y - hh / 2, x + w / 2, y + hh / 2)
        hand.draw(c)
        return bmp
    }

    /** The label as the app draws it: Plex Light, the seconds' alpha, its first digit's origin at P em, on the baseline. */
    private fun label(text: String, em: Float, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = CountdownArt.paint(ctx, em).apply { alpha = (255 * Spec.SECONDS_ALPHA).toInt() }
        Canvas(bmp).drawText(text, Spec.P_EM * em - CountdownArt.spacing(p, text) / 2f, Spec.A_EM * em, p)
        return bmp
    }

    /** Mean difference of the white ink (alpha where the pixel is light), 0–255. */
    private fun inkDiff(a: Bitmap, b: Bitmap): Double {
        var sum = 0.0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            fun ink(p: Int) = if (Color.red(p) > 180) Color.alpha(p) else 0
            sum += abs(ink(a.getPixel(x, y)) - ink(b.getPixel(x, y)))
        }
        return sum / (a.width * a.height)
    }

    private fun inkCentreX(b: Bitmap): Double {
        var m = 0.0; var sx = 0.0
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val p = b.getPixel(x, y)
            val a = if (Color.red(p) > 180) Color.alpha(p).toDouble() else 0.0
            m += a; sx += a * x
        }
        return if (m == 0.0) -1.0 else sx / m
    }

    @Test fun everySecondShowsItsLabelAndNothingElse() {
        val out = File("build/screenshots").apply { mkdirs() }
        for ((em, digits) in listOf(110f to "latin", 92f to "arabic")) {
            val arabic = digits == "arabic"
            for (down in listOf(true, false)) {
                val id = CountdownArt.wheel(elapsed = !down, arabicDigits = arabic)
                var worst = 0.0
                val strip = Bitmap.createBitmap(10 * (Spec.WIN_W_EM * em).toInt(), 6 * kotlin.math.ceil((Spec.A_EM + Spec.B_EM) * em).toInt(), Bitmap.Config.ARGB_8888)
                val sc = Canvas(strip).apply { drawColor(0xFF223355.toInt()) }
                for (s in 0 until 60) {
                    val got = window(id, em, s)
                    val v = if (down) 59 - s else s
                    val text = "%02d".format(v).map { if (arabic) '٠' + (it - '0') else it }.joinToString("")
                    val want = label(text, em, got.width, got.height)
                    val d = inkDiff(got, want)
                    worst = maxOf(worst, d)
                    assertTrue("$digits ${if (down) "down" else "up"} second $s: label $text differs (ink diff $d)", d < 6.0)
                    assertTrue("$digits second $s: label $text out of place", abs(inkCentreX(got) - inkCentreX(want)) < 1.5)
                    sc.drawBitmap(got, (s % 10) * got.width.toFloat(), (s / 10) * got.height.toFloat(), null)
                }
                strip.compress(Bitmap.CompressFormat.PNG, 100, File(out, "92_seconds_${digits}_${if (down) "down" else "up"}.png").outputStream())
                println("$digits ${if (down) "down" else "up"}: worst ink diff $worst")
            }
        }
    }

    @Test fun theSecondsContinueTheLine() {
        // "−7:08:34" as the home screen draws it (one line: "−7:08" bright, ":34" quieter) against the widget's picture of
        // "−7:08:" with the wheel's window laid where CountdownArt puts it.
        for ((em, text) in listOf(120f to "−7:08:34", 96f to "+13:05", 100f to "−٧:٠٨:٣٤")) {
            val line = CountdownArt.line(ctx, text, em, seconds = true)
            val w = line.windowLeft + line.windowWidth + 4
            val got = Bitmap.createBitmap(maxOf(w, line.main.width), line.height, Bitmap.Config.ARGB_8888)
            val gc = Canvas(got)
            gc.drawBitmap(line.main, 0f, 0f, null)
            val secs = text.substring(text.lastIndexOf(':') + 1)
            val arabic = secs.first() in '٠'..'٩'
            val v = secs.map { if (arabic) it - '٠' else it - '0' }.joinToString("").toInt()
            val down = text.startsWith("−")
            gc.drawBitmap(window(CountdownArt.wheel(!down, arabic), em, if (down) 59 - v else v), line.windowLeft.toFloat(), 0f, null)

            val want = Bitmap.createBitmap(got.width, got.height, Bitmap.Config.ARGB_8888)
            val p = CountdownArt.paint(ctx, em)
            val cut = text.lastIndexOf(':')
            val x = Spec.P_EM * em - CountdownArt.spacing(p, text) / 2f
            Canvas(want).drawText(text.substring(0, cut), x, Spec.A_EM * em, p)
            p.alpha = (255 * Spec.SECONDS_ALPHA).toInt()
            Canvas(want).drawText(text.substring(cut), x + p.measureText(text.substring(0, cut)), Spec.A_EM * em, p)
            val d = inkDiff(got, want)
            File(File("build/screenshots").apply { mkdirs() }, "92_line_${em.toInt()}.png").outputStream().use { o ->
                val pair = Bitmap.createBitmap(got.width, got.height * 2, Bitmap.Config.ARGB_8888)
                Canvas(pair).apply { drawColor(0xFF223355.toInt()); drawBitmap(got, 0f, 0f, null); drawBitmap(want, 0f, got.height.toFloat(), null) }
                pair.compress(Bitmap.CompressFormat.PNG, 100, o)
            }
            assertTrue("$text: the widget's line differs from the home screen's (ink diff $d)", d < 4.0)
        }
    }
}
