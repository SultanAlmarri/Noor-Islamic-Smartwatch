package com.noor.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.noor.app.R

/**
 * The widgets' countdown before its seconds (0.9.14): "−1:23" bright and the ":" before the seconds quieter, in Minhad's
 * Plex Light and the digits already chosen by the caller. A picture, because a home screen ignores fonts packed in an
 * app. At [Spec.scale] 1 it is exactly as high as the seconds wheel's window and shares its baseline
 * (res/values/noor_wheel.xml, written with the wheel by tools/widget/SecondsWheel.java), so the two read as one line;
 * (0.23) each widget draws both smaller by one scale (the wheel by shrinking its clock, [Widgets.countdown]), in the
 * widget's own colours.
 */
internal object CountPicture {
    private const val BRIGHT = 0xFFFFFFFF.toInt()
    /** The seconds' colour on the wheel, as on the home screen. */
    private const val QUIET = 0x80FFFFFF.toInt()
    /** As the widgets' other text (Widget.Noor.Text). */
    private const val SHADOW = 0x73000000

    /**
     * What is drawn: [leading] ("−1:23"), at [scale] of the wheel's size, in [color]; the ":" before the seconds in
     * [quiet] when [colon] (the seconds are shown), and a soft shadow when [shadow].
     */
    data class Spec(val leading: String, val scale: Float = 1f, val color: Int = BRIGHT, val quiet: Int = QUIET,
                    val shadow: Boolean = true, val colon: Boolean = true)

    @Volatile private var plex: Typeface? = null

    private fun paint(context: Context, scale: Float): Paint {
        val face = plex ?: (runCatching { ResourcesCompat.getFont(context, R.font.plex_arabic_light) }.getOrNull() ?: Typeface.DEFAULT).also { plex = it }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = context.resources.getDimension(R.dimen.noor_count_text) * scale }
    }

    fun draw(context: Context, leading: String): Bitmap = draw(context, Spec(leading))

    fun draw(context: Context, spec: Spec): Bitmap {
        val r = context.resources
        val paint = paint(context, spec.scale)
        if (spec.shadow) paint.setShadowLayer(6f * spec.scale, 0f, 1f, SHADOW)
        val colon = if (spec.colon) ":" else ""
        val lead = paint.measureText(spec.leading)
        val w = maxOf(1, kotlin.math.ceil(lead + paint.measureText(colon) + (if (spec.colon) 0f else 2f * spec.scale)).toInt())
        val h = maxOf(1, kotlin.math.ceil(r.getDimension(R.dimen.noor_count_height) * spec.scale).toInt())
        val baseline = r.getDimension(R.dimen.noor_count_ascent) * spec.scale
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.density = r.displayMetrics.densityDpi
            val canvas = Canvas(bmp)
            paint.color = spec.color
            canvas.drawText(spec.leading, 0f, baseline, paint)
            if (spec.colon) {
                paint.color = spec.quiet
                paint.clearShadowLayer()
                canvas.drawText(colon, lead, baseline, paint)
            }
        }
    }

    /** How wide the countdown is, in dp, at [scale]: the picture of [leading] and, with [seconds], the wheel's window. */
    fun widthDp(context: Context, leading: String, scale: Float, seconds: Boolean): Float {
        val r = context.resources
        val d = r.displayMetrics.density
        val p = paint(context, scale)
        val lead = (p.measureText(leading) + (if (seconds) p.measureText(":") else 0f)) / d
        return lead + if (seconds) r.getDimension(R.dimen.noor_count_window) * scale / d else 0f
    }

    /** How high the countdown is, in dp, at [scale]. */
    fun heightDp(context: Context, scale: Float): Float = context.resources.let { it.getDimension(R.dimen.noor_count_height) * scale / it.displayMetrics.density }
}
