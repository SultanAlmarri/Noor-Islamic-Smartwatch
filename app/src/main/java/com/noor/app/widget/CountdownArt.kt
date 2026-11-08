package com.noor.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.noor.app.R
import com.noor.app.widget.CountdownWheelSpec as Spec
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The widgets' countdown, drawn as the home screen draws it (0.9.12): "−1:23" in the app's own font (IBM Plex Sans Arabic
 * Light), in the digits chosen in Noor, white with the app's soft shadow, and ":45" quieter.
 *
 * A widget cannot use an app's fonts (Android loads its layout in a restricted mode that ignores them; 0.9.11's fonts
 * were silently replaced on phones by the phone's font and digits). So:
 * - the hours and minutes ("−1:23:") are a picture Noor draws, redrawn on the minute ([Widgets] does it as each minute
 *   begins, while the phone is awake);
 * - the seconds are an analog clock whose second hand is a wheel of the sixty labels in Noor's font and digits
 *   (tools/widget/countdown_wheels.py), turned by Android itself one step each second, seen through a small window that
 *   shows only the label standing at the top — the current second's. Android 12 and later; before that the countdown
 *   is shown to the minute.
 * - Where Noor may not set exact alarms (so its minutes could not change on time), Android's own countdown (in the
 *   phone's font and digits) is shown instead.
 *
 * The seconds count down before a prayer and up after it, as on the home screen: during the clock's second s the label
 * is 59 − s, or s (prayer times fall on whole minutes, so the minutes change as the clock's minute does).
 */
internal object CountdownArt {
    @Volatile private var plex: Typeface? = null

    private fun font(ctx: Context): Typeface = plex ?: runCatching { ctx.resources.getFont(R.font.plex_arabic_light) }.getOrNull()
        .let { it ?: Typeface.create("sans-serif-light", Typeface.NORMAL) }.also { plex = it }

    /** How the countdown is shown on this phone. */
    enum class Mode {
        /** Noor's picture of the hours and minutes, and the seconds wheel (Android 12 and later, exact alarms allowed). */
        WHEEL,
        /** Noor's picture, to the minute (Android 8 to 11). */
        MINUTES,
        /** Android's own countdown, in the phone's font and digits (exact alarms not allowed). */
        SYSTEM,
    }

    /** One countdown, laid out for a font size of [em] px. */
    class Line(val main: Bitmap, val height: Int, val windowLeft: Int, val windowWidth: Int, val clockSize: Int, val clockLeft: Int)

    /**
     * The picture of [text] ("−1:23:45", as the home screen writes it) up to the last colon: the part before it bright,
     * the colon itself quieter; with [seconds], room is left after it for the wheel's window.
     */
    fun line(ctx: Context, text: String, em: Float, seconds: Boolean): Line {
        val cut = text.lastIndexOf(':').takeIf { it > 0 } ?: text.length
        val main = text.substring(0, cut)
        val paint = paint(ctx, em)
        val pad = Spec.P_EM * em
        val withColon = if (seconds && cut < text.length) "$main:" else main
        val advance = paint.measureText(withColon)
        val w = ceil(pad + advance + pad).toInt().coerceAtLeast(1)
        val h = ceil((Spec.A_EM + Spec.B_EM) * em).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Android puts half the letter spacing before the first character, so the first character's origin is at pad (in
        // Arabic script, Arabic-Indic digits included, Android sets no letter spacing at all).
        val x = pad - spacing(paint, main) / 2f
        val baseline = Spec.A_EM * em
        c.drawText(main, x, baseline, paint)
        if (withColon.length > main.length) {
            paint.alpha = (255 * Spec.SECONDS_ALPHA).roundToInt()
            c.drawText(":", x + paint.measureText(main), baseline, paint)
        }
        // The seconds' first digit continues the line where the next character would stand.
        val windowLeft = advance.roundToInt()
        val clock = (Spec.D_EM * em).roundToInt()
        val window = (Spec.WIN_W_EM * em).roundToInt()
        return Line(bmp, h, windowLeft, window, clock, ((window - clock) / 2f).roundToInt())
    }

    /** The letter spacing Android gives each character of [text] with [paint] (none in Arabic script). */
    fun spacing(paint: Paint, text: String): Float {
        if (text.isEmpty()) return 0f
        val plain = Paint(paint).apply { letterSpacing = 0f }
        return (paint.measureText(text) - plain.measureText(text)) / text.length
    }

    fun paint(ctx: Context, em: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = font(ctx)
        textSize = em
        letterSpacing = Spec.LS_EM
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(Spec.SHADOW_RADIUS_EM * em, 0f, Spec.SHADOW_DY_EM * em, Spec.SHADOW_ARGB)
    }

    /** The seconds wheel for counting down (before a prayer) or up (after it), in Western or Arabic-Indic digits. */
    fun wheel(elapsed: Boolean, arabicDigits: Boolean): Int = when {
        arabicDigits && elapsed -> R.drawable.cd_wheel_up_arabic
        arabicDigits -> R.drawable.cd_wheel_down_arabic
        elapsed -> R.drawable.cd_wheel_up_latin
        else -> R.drawable.cd_wheel_down_latin
    }

    /**
     * Shows [text] in the countdown views of [v] (widget_countdown.xml) at [em] px: the picture and, in [Mode.WHEEL],
     * the seconds window and its clock with the right wheel. [systemBase] (Android's elapsed-realtime clock at the
     * prayer's time) is for [Mode.SYSTEM].
     */
    fun show(v: RemoteViews, ctx: Context, text: String, em: Float, mode: Mode, elapsed: Boolean, arabicDigits: Boolean, systemBase: Long, description: String) {
        v.setContentDescription(R.id.widget_countdown_box, description)
        if (mode == Mode.SYSTEM) {
            v.setViewVisibility(R.id.widget_count_main, View.GONE)
            v.setViewVisibility(R.id.widget_count_window, View.GONE)
            v.setViewVisibility(R.id.widget_count_plain, View.VISIBLE)
            v.setTextViewTextSize(R.id.widget_count_plain, TypedValue.COMPLEX_UNIT_PX, em)
            v.setChronometer(R.id.widget_count_plain, systemBase, if (elapsed) "+%s" else null, true)
            v.setChronometerCountDown(R.id.widget_count_plain, false)
            return
        }
        val wheel = mode == Mode.WHEEL && Build.VERSION.SDK_INT >= 31
        val line = line(ctx, text, em, wheel)
        v.setViewVisibility(R.id.widget_count_plain, View.GONE)
        v.setViewVisibility(R.id.widget_count_main, View.VISIBLE)
        v.setImageViewBitmap(R.id.widget_count_main, line.main)
        if (!wheel) { v.setViewVisibility(R.id.widget_count_window, View.GONE); return }
        val px = TypedValue.COMPLEX_UNIT_PX
        v.setViewLayoutWidth(R.id.widget_count_main, line.main.width.toFloat(), px)
        v.setViewLayoutHeight(R.id.widget_count_main, line.height.toFloat(), px)
        v.setViewVisibility(R.id.widget_count_window, View.VISIBLE)
        v.setViewLayoutWidth(R.id.widget_count_window, line.windowWidth.toFloat(), px)
        v.setViewLayoutHeight(R.id.widget_count_window, line.height.toFloat(), px)
        v.setViewLayoutMargin(R.id.widget_count_window, RemoteViews.MARGIN_LEFT, (line.windowLeft - line.main.width).toFloat(), px)
        v.setViewLayoutWidth(R.id.widget_count_seconds, line.clockSize.toFloat(), px)
        v.setViewLayoutHeight(R.id.widget_count_seconds, line.clockSize.toFloat(), px)
        v.setViewLayoutMargin(R.id.widget_count_seconds, RemoteViews.MARGIN_LEFT, line.clockLeft.toFloat(), px)
        v.setIcon(R.id.widget_count_seconds, "setSecondHand", Icon.createWithResource(ctx.packageName, wheel(elapsed, arabicDigits)))
    }
}
