package com.noor.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.noor.app.R

/**
 * The widgets' countdown before its seconds (0.9.14): "−1:23" bright and the ":" before the seconds quieter, in Noor's
 * Plex Light and the digits already chosen by the caller. A picture, because a home screen ignores fonts packed in an
 * app. It is exactly as high as the seconds wheel's window and shares its baseline (res/values/noor_wheel.xml, written
 * with the wheel by tools/widget/SecondsWheel.java), so the two read as one line.
 */
internal object CountPicture {
    private const val BRIGHT = 0xFFFFFFFF.toInt()
    /** The seconds' colour on the wheel, as on the home screen. */
    private const val QUIET = 0x80FFFFFF.toInt()
    /** As the widgets' other text (Widget.Noor.Text). */
    private const val SHADOW = 0x73000000

    @Volatile private var plex: Typeface? = null

    fun draw(context: Context, leading: String): Bitmap {
        val r = context.resources
        val face = plex ?: (runCatching { ResourcesCompat.getFont(context, R.font.plex_arabic_light) }.getOrNull() ?: Typeface.DEFAULT).also { plex = it }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = r.getDimension(R.dimen.noor_count_text)
            setShadowLayer(6f, 0f, 1f, SHADOW)
        }
        val colon = ":"
        val lead = paint.measureText(leading)
        val w = maxOf(1, kotlin.math.ceil(lead + paint.measureText(colon)).toInt())
        val h = r.getDimensionPixelSize(R.dimen.noor_count_height)
        val baseline = r.getDimension(R.dimen.noor_count_ascent)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.density = r.displayMetrics.densityDpi
            val canvas = Canvas(bmp)
            paint.color = BRIGHT
            canvas.drawText(leading, 0f, baseline, paint)
            paint.color = QUIET
            paint.clearShadowLayer()
            canvas.drawText(colon, lead, baseline, paint)
        }
    }
}
