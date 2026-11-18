package com.noor.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build

/**
 * A widget's colours (0.23), from its options: the text in three strengths, the accent (the prayer counted to, its
 * mark, the ring, the bar), and what is behind them. Over the sky, glass, dark and the wallpaper the text is white with
 * a soft shadow; over light backgrounds ink without one ([plain]: the layouts' "_plain" twins).
 */
internal class WidgetPalette(
    val background: WidgetBackground,
    /** The background's own colour (opaque; its opacity is [alpha]), or 0 for the sky and the wallpaper. */
    val fill: Int,
    /** 0..255. */
    val alpha: Int,
    val light: Boolean,
    val text: Int,
    val secondary: Int,
    val muted: Int,
    val accent: Int,
    /** Behind the prayer counted to or from. */
    val pill: Int,
    /** The unfilled part of a ring or bar. */
    val track: Int,
) {
    val plain: Boolean get() = light
    /** The countdown's seconds and ":", quieter than its hours and minutes. */
    val quiet: Int get() = (text and 0x00FFFFFF) or (if (light) 0x8C shl 24 else 0x80 shl 24)

    companion object {
        // Text over dark: white, as the app's night text; over light: the app's day ink (DESIGN.md "Colour").
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val INK = 0xFF17191F.toInt()

        /** The accent over dark and over light backgrounds (each meets 4.5:1 on its background: WidgetPaletteTest). */
        fun accentOn(a: WidgetAccent, light: Boolean, context: Context?): Int = when (a) {
            WidgetAccent.GOLD -> if (light) 0xFF765519.toInt() else 0xFFF6D696.toInt()
            WidgetAccent.TEAL -> if (light) 0xFF0A5F58.toInt() else 0xFF84DCCC.toInt()
            WidgetAccent.ROSE -> if (light) 0xFF963152.toInt() else 0xFFF7AFBF.toInt()
            WidgetAccent.BLUE -> if (light) 0xFF2A57B0.toInt() else 0xFFA3C6FF.toInt()
            WidgetAccent.WHITE -> if (light) INK else WHITE
            WidgetAccent.YOU -> you(context, if (light) android.R.color.system_accent1_700 else android.R.color.system_accent1_200)
                ?: accentOn(WidgetAccent.GOLD, light, null)
        }

        /** The wallpaper's colours (Android 12 and later), or null. */
        private fun you(context: Context?, res: Int): Int? =
            if (context != null && Build.VERSION.SDK_INT >= 31) runCatching { context.getColor(res) }.getOrNull() else null

        private fun night(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        fun of(context: Context, o: WidgetOptions): WidgetPalette {
            val bg = if (o.background == WidgetBackground.YOU && Build.VERSION.SDK_INT < 31) WidgetBackground.DARK else o.background
            val youLight = bg == WidgetBackground.YOU && !night(context)
            val light = bg == WidgetBackground.LIGHT || youLight
            val a = (o.opacity.coerceIn(0, 100) * 255 + 50) / 100
            val (fill, alpha) = when (bg) {
                WidgetBackground.SKY, WidgetBackground.CLEAR -> 0 to 0
                WidgetBackground.GLASS -> 0 to a
                WidgetBackground.DARK -> 0xFF121826.toInt() to a
                WidgetBackground.LIGHT -> 0xFFF4F1EA.toInt() to a
                WidgetBackground.YOU -> (you(context, if (youLight) android.R.color.system_neutral1_50 else android.R.color.system_neutral2_800) ?: 0xFF121826.toInt()) to 255
            }
            val text = if (bg == WidgetBackground.YOU) you(context, if (youLight) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50) ?: WHITE
                else if (light) INK else WHITE
            val secondary = if (light) 0xFF4A505C.toInt() else 0xE6FFFFFF.toInt()
            // Over the sky and the wallpaper, past prayers are quieter but still clear; over ink, quieter still.
            val muted = when { light -> 0xFF636976.toInt(); bg == WidgetBackground.SKY || bg == WidgetBackground.CLEAR -> 0xD0FFFFFF.toInt(); else -> 0xB8FFFFFF.toInt() }
            val accent = if (o.accent == WidgetAccent.YOU || bg != WidgetBackground.YOU) accentOn(o.accent, light, context)
                else accentOn(WidgetAccent.YOU, light, context)
            val pill = (accent and 0x00FFFFFF) or ((if (light) 0x26 else 0x2E) shl 24)
            val track = (text and 0x00FFFFFF) or ((if (light) 0x24 else 0x33) shl 24)
            return WidgetPalette(bg, fill, alpha, light, text, secondary, muted, accent, pill, track)
        }
    }
}

/**
 * Text that fits (0.23): the largest size, up to the one wanted, at which each text fits its width, measured in the
 * system's fonts as the home screen will draw them (with a margin for phones whose system font is wider). The widgets
 * never cut a prayer, a time or a date short.
 */
internal object Fit {
    private val faces = HashMap<String, Typeface>()
    private const val MARGIN = 0.92f

    @Synchronized private fun paint(family: String, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = faces.getOrPut(family) { Typeface.create(family, Typeface.NORMAL) }
        textSize = size
    }

    /** The width of [text] at [size] (in the same unit). */
    fun width(text: CharSequence, family: String, size: Float): Float = paint(family, size).measureText(text.toString())

    /** The size, at most [want] and at least [min], at which every one of [texts] fits [avail]. */
    fun size(texts: List<CharSequence>, family: String, want: Float, avail: Float, min: Float = 7f): Float {
        var s = want
        for (t in texts) {
            if (t.isEmpty()) continue
            val w = width(t, family, want)
            if (w > avail * MARGIN) s = minOf(s, want * avail * MARGIN / w)
        }
        return s.coerceAtLeast(min)
    }

    const val MEDIUM = "sans-serif-medium"
    const val REGULAR = "sans-serif"
    const val LIGHT = "sans-serif-light"
}
