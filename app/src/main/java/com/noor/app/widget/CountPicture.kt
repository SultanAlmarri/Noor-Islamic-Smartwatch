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
    private const val QUIET = 0xB3FFFFFF.toInt()
    /** As the widgets' other text (Widget.Noor.Text). */
    private const val SHADOW = 0x73000000

    /**
     * What is drawn: [leading] ("−1:23"), at [scale] of the wheel's size, in [color]; the ":" before the seconds in
     * [quiet] when [colon] (the seconds are shown), and a soft shadow when [shadow]. (0.24) [secondsScale]: the seconds
     * (and their ":") smaller than the hours and minutes by this, on the same baseline, as the home screen writes them.
     */
    data class Spec(val leading: String, val scale: Float = 1f, val color: Int = BRIGHT, val quiet: Int = QUIET,
                    val shadow: Boolean = true, val colon: Boolean = true, val secondsScale: Float = 1f)

    @Volatile private var plex: Typeface? = null

    /** (0.24) Plex SemiBold, the seconds wheel's font (tools/widget/SecondsWheel.java), figures of one width. */
    private fun paint(context: Context, scale: Float): Paint {
        val face = plex ?: (runCatching { ResourcesCompat.getFont(context, R.font.plex_arabic_semibold) }.getOrNull() ?: Typeface.DEFAULT).also { plex = it }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = context.resources.getDimension(R.dimen.noor_count_text) * scale; fontFeatureSettings = "'tnum' 1, 'lnum' 1" }
    }

    /** Arabic-Indic digits are smaller than Western ones in Plex: drawn this much larger, as on the seconds wheel. */
    const val ARABIC_SCALE = 1.15f
    /** The sign, quieter than the digits, and a hair's space after it. */
    private const val SIGN_ALPHA = 0.62f
    private const val SIGN_GAP_EM = 0.06f

    private fun isSign(ch: Char) = ch == '\u2212' || ch == '+' || ch == '-'
    private fun isArabicDigit(ch: Char) = ch in '\u0660'..'\u0669'

    /**
     * The glyphs of [text] laid left to right, one by one (never reordered: "−١:٠٤" keeps its sign first), with their x
     * positions and sizes; returns the total advance.
     */
    private fun layout(text: String, base: Paint, lastScale: Float = 1f, each: (ch: Char, x: Float, size: Float) -> Unit): Float {
        var x = 0f
        val size = base.textSize
        // With Arabic-Indic digits the zero is a dot at the colon's height ("١:٠٤" read as ":·"): the colons are
        // smaller (so lower) with a little air on each side, unmistakably colons.
        val arabic = text.any { isArabicDigit(it) }
        for ((index, ch) in text.withIndex()) {
            val colon = ch == ':' && arabic
            val k = if (index == text.length - 1) lastScale else 1f
            val sz = k * when { isArabicDigit(ch) -> size * ARABIC_SCALE; colon -> size * 0.8f; else -> size }
            if (colon) x += size * COLON_GAP_EM
            base.textSize = sz
            each(ch, x, sz)
            x += base.measureText(ch.toString())
            // (0.24) The design's tracking, -0.035 em: the countdown set as one tight figure.
            x -= size * TRACK_EM
            if (isSign(ch)) x += size * SIGN_GAP_EM
            if (colon) x += size * COLON_GAP_EM
            base.textSize = size
        }
        return x
    }

    private const val COLON_GAP_EM = 0.07f
    private const val TRACK_EM = 0.035f

    fun draw(context: Context, leading: String): Bitmap = draw(context, Spec(leading))

    fun draw(context: Context, spec: Spec): Bitmap {
        val r = context.resources
        val paint = paint(context, spec.scale)
        if (spec.shadow) paint.setShadowLayer(6f * spec.scale, 0f, 1f, SHADOW)
        // The ":" before the seconds is the last glyph, quieter (set like the others).
        val text = spec.leading + if (spec.colon) ":" else ""
        val last = if (spec.colon) spec.secondsScale else 1f
        val total = layout(text, Paint(paint), last) { _, _, _ -> }
        val w = maxOf(1, kotlin.math.ceil(total + (if (spec.colon) 0f else 2f * spec.scale)).toInt())
        val h = maxOf(1, kotlin.math.ceil(r.getDimension(R.dimen.noor_count_height) * spec.scale).toInt())
        val baseline = r.getDimension(R.dimen.noor_count_ascent) * spec.scale
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.density = r.displayMetrics.densityDpi
            val canvas = Canvas(bmp)
            val sign = (spec.color and 0x00FFFFFF) or ((((spec.color ushr 24) * SIGN_ALPHA).toInt()) shl 24)
            val p = Paint(paint)
            var i = 0
            layout(text, Paint(paint), last) { ch, x, size ->
                val last = spec.colon && i == text.length - 1
                p.textSize = size
                p.color = when { last -> spec.quiet; isSign(ch) -> sign; else -> spec.color }
                if (last) p.clearShadowLayer()
                canvas.drawText(ch.toString(), x, baseline, p)
                i++
            }
        }
    }

    /** How wide the countdown is, in dp, at [scale]: the picture of [leading] and, with [seconds], the wheel's window. */
    fun widthDp(context: Context, leading: String, scale: Float, seconds: Boolean, arabicDigits: Boolean = leading.any { isArabicDigit(it) }, secondsScale: Float = 1f): Float {
        val r = context.resources
        val d = r.displayMetrics.density
        val p = paint(context, scale)
        val lead = layout(leading + if (seconds) ":" else "", p, if (seconds) secondsScale else 1f) { _, _, _ -> } / d
        return lead + if (seconds) r.getDimension(if (arabicDigits) R.dimen.noor_count_window_arabic else R.dimen.noor_count_window_latin) * scale * secondsScale / d else 0f
    }

    /** The baseline below the picture's top, in dp, at [scale]. */
    fun ascentDp(context: Context, scale: Float): Float = context.resources.let { it.getDimension(R.dimen.noor_count_ascent) * scale / it.displayMetrics.density }

    /** How high the countdown is, in dp, at [scale]. */
    fun heightDp(context: Context, scale: Float): Float = context.resources.let { it.getDimension(R.dimen.noor_count_height) * scale / it.displayMetrics.density }
}
