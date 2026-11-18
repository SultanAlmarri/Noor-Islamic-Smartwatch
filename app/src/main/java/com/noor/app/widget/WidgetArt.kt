package com.noor.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * The widgets' small pictures (0.23), drawn with the ordinary Canvas at the widget's size: the ring of the Countdown
 * ring and the slim bar of Today's prayers, each from the last prayer to the next. Small (a few tens of kilobytes),
 * redrawn with the countdown each minute while the phone is awake.
 */
internal object WidgetArt {
    /**
     * A ring [px] across: the track all round, and from the top, clockwise, the part of the time from the last prayer to
     * the next that has gone ([progress] 0..1), in [accent], with a dot of light at its head.
     */
    fun ring(px: Int, density: Float, progress: Float, accent: Int, track: Int, stroke: Float = 5f): Bitmap {
        val size = px.coerceIn(16, 720)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.density = (density * 160).toInt()
        val c = Canvas(bmp)
        val sw = stroke * density
        val r = RectF(sw, sw, size - sw, size - sw)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND; color = track }
        c.drawArc(r, 0f, 360f, false, p)
        val f = progress.coerceIn(0f, 1f)
        if (f > 0.002f) {
            p.color = accent
            c.drawArc(r, -90f, 360f * f, false, p)
        }
        // The head: where the time is now on the ring.
        val a = Math.toRadians(-90.0 + 360.0 * f)
        val cx = size / 2f + (r.width() / 2f) * cos(a).toFloat(); val cy = size / 2f + (r.height() / 2f) * sin(a).toFloat()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        dot.color = (accent and 0x00FFFFFF) or (0x40 shl 24); c.drawCircle(cx, cy, sw * 1.15f, dot)
        dot.color = accent; c.drawCircle(cx, cy, sw * 0.72f, dot)
        return bmp
    }

    /** A slim bar [w]×[h] px: the track, and the part gone ([progress]) from the reading side ([rtl]: from the right). */
    fun bar(w: Int, h: Int, progress: Float, accent: Int, track: Int, rtl: Boolean): Bitmap {
        val bw = w.coerceIn(8, 1600); val bh = h.coerceIn(2, 40)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = bh / 2f
        p.color = track; c.drawRoundRect(RectF(0f, 0f, bw.toFloat(), bh.toFloat()), r, r, p)
        val f = progress.coerceIn(0f, 1f)
        if (f > 0f) {
            val len = maxOf(bh.toFloat(), bw * f)
            p.color = accent
            c.drawRoundRect(if (rtl) RectF(bw - len, 0f, bw.toFloat(), bh.toFloat()) else RectF(0f, 0f, len, bh.toFloat()), r, r, p)
        }
        return bmp
    }
}
