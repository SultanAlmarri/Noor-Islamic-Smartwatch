package com.noor.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * The widgets' small pictures (0.23), drawn with the ordinary Canvas at the widget's size: the ring of the Countdown
 * ring and the slim bar of Today's prayers, each from the last prayer to the next, with a tick where the next Adhan is
 * (the ring's top, the bar's far end). Small (a few tens of kilobytes), redrawn with the countdown each minute while
 * the phone is awake.
 */
internal object WidgetArt {
    /**
     * A ring [px] across: the track all round, and from the top, clockwise (time, so in Arabic too), the part of the time
     * from the last prayer to the next that has gone ([progress] 0..1), in [accent], round at both ends, a solid knob at
     * its head; a tick across the track at the top, where the next Adhan is, in [tick].
     */
    fun ring(px: Int, density: Float, progress: Float, accent: Int, track: Int, stroke: Float = 4f, tick: Int = 0x99FFFFFF.toInt()): Bitmap {
        val size = px.coerceIn(16, 900)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.density = (density * 160).toInt()
        val c = Canvas(bmp)
        val sw = stroke * density
        val inset = sw * 1.4f
        val r = RectF(inset, inset, size - inset, size - inset)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND; color = track }
        c.drawArc(r, 0f, 360f, false, p)
        val f = progress.coerceIn(0f, 1f)
        if (f > 0.002f) {
            p.color = accent
            c.drawArc(r, -90f, 360f * f, false, p)
        }
        // The Adhan: a short tick across the track at the top.
        val cx0 = size / 2f
        p.color = tick; p.strokeWidth = 1.6f * density; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(cx0, inset - sw * 1.1f, cx0, inset + sw * 1.1f, p)
        // The head: where the time is now on the ring.
        val a = Math.toRadians(-90.0 + 360.0 * f)
        val cx = size / 2f + (r.width() / 2f) * cos(a).toFloat(); val cy = size / 2f + (r.height() / 2f) * sin(a).toFloat()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = accent }
        c.drawCircle(cx, cy, sw * 0.95f, dot)
        return bmp
    }

    /**
     * A slim bar [w]×[h] px: a 3 dp track through its middle and the part gone ([progress]) from the reading side ([rtl]:
     * from the right), with a tick at the far end, where the next Adhan is.
     */
    fun bar(w: Int, h: Int, progress: Float, accent: Int, track: Int, rtl: Boolean, density: Float = 3f, tick: Int = 0x99FFFFFF.toInt()): Bitmap {
        val bw = w.coerceIn(8, 1600); val bh = h.coerceIn(2, 60)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val th = (3f * density).coerceAtMost(bh.toFloat())
        val top = (bh - th) / 2f
        val r = th / 2f
        // The track runs to a small round target at the end the bar fills towards: the next Adhan.
        val dot = bh / 2f
        val left = if (rtl) dot else 0f
        val right = if (rtl) bw.toFloat() else bw - dot
        p.color = track; c.drawRoundRect(RectF(left, top, right, top + th), r, r, p)
        val f = progress.coerceIn(0f, 1f)
        if (f > 0f) {
            val len = maxOf(th, (right - left) * f)
            p.color = accent
            c.drawRoundRect(if (rtl) RectF(right - len, top, right, top + th) else RectF(left, top, left + len, top + th), r, r, p)
        }
        p.color = tick
        c.drawCircle(if (rtl) dot else bw - dot, bh / 2f, dot * 0.72f, p)
        return bmp
    }

    /** The scrims laid over a sky picture, for the text of each layout: black, from the top to the foot. */
    enum class Scrim(val stops: FloatArray, val alphas: IntArray) {
        TOP(floatArrayOf(0f, 1f), intArrayOf(0x59, 0x00)),
        BOTTOM(floatArrayOf(0f, 0.5f, 1f), intArrayOf(0x00, 0x4D, 0xB3)),
        EVEN(floatArrayOf(0f, 0.5f, 1f), intArrayOf(0x4D, 0x40, 0x73)),
        FULL(floatArrayOf(0f, 0.3f, 0.55f, 1f), intArrayOf(0x40, 0x00, 0x00, 0x59)),
        DENSE(floatArrayOf(0f, 0.3f, 0.5f, 1f), intArrayOf(0x4D, 0x14, 0x2E, 0xA6)),
    }

    /** Lays [s] over [bmp] (inside its rounded corners only). */
    fun scrim(bmp: Bitmap, s: Scrim) {
        val c = Canvas(bmp)
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, bmp.height.toFloat(), s.alphas.map { it shl 24 }.toIntArray(), s.stops, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        c.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), p)
    }

    /**
     * A plain background [w]×[h] px with corners [radius] px: Glass (translucent ink, lighter at the top, a sheen along
     * its top edge and a hairline of light round it), or [fill] (Dark, Light, the wallpaper's colour); [alpha] 0..255.
     */
    fun card(w: Int, h: Int, radius: Float, density: Float, kind: WidgetBackground, fill: Int, alpha: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(8), h.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.saveLayerAlpha(rect, alpha.coerceIn(0, 255))
        if (kind == WidgetBackground.GLASS) {
            p.shader = LinearGradient(0f, 0f, 0f, rect.bottom, intArrayOf(0xFF2A3449.toInt(), 0xFF1A2133.toInt(), 0xFF0E131E.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, radius, radius, p)
            p.shader = LinearGradient(0f, 0f, 0f, rect.bottom * 0.22f, 0x21FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, radius, radius, p)
            p.shader = null
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f * density; p.color = 0x2EFFFFFF
            val e = p.strokeWidth / 2
            c.drawRoundRect(RectF(e, e, rect.right - e, rect.bottom - e), radius - e, radius - e, p)
        } else {
            p.color = fill or 0xFF000000.toInt()
            c.drawRoundRect(rect, radius, radius, p)
        }
        c.restore()
        return bmp
    }
}
