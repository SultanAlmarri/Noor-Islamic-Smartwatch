package com.noor.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.noor.app.R
import kotlin.math.ceil
import kotlin.math.max

/**
 * The widgets' words, drawn in the app's own fonts (0.9.12): a widget's text views cannot use an app's fonts (Android
 * loads a widget's layout in a restricted mode), so the prayer's name and time, the place and the day's times are
 * pictures, in IBM Plex Sans Arabic (Reem Kufi for an Arabic prayer name) with the home screen's soft shadow, exactly as
 * on the home screen. Each picture carries its words for screen readers.
 */
internal object TextArt {
    private val fonts = HashMap<Int, Typeface>()

    fun font(ctx: Context, id: Int): Typeface = synchronized(fonts) {
        fonts.getOrPut(id) { runCatching { ctx.resources.getFont(id) }.getOrNull() ?: Typeface.DEFAULT }
    }

    /** The home screen's text shadow (SkyText.Shadow: navy at 25 %, 1.5 px down, blurred 10 px). */
    const val SHADOW = 0x40081530
    const val WHITE = 0xFFFFFFFF.toInt()
    /** Sky.OnSkySecondary, Sky.OnSkyMuted, Sky.GoldOnSky. */
    const val SECONDARY = 0xD9FFFFFF.toInt()
    const val MUTED = 0x99FFFFFF.toInt()
    const val GOLD = 0xFFF6D696.toInt()

    /** A piece of a line: its words in one font, size (px) and colour. */
    class Run(val text: String, val font: Typeface, val size: Float, val color: Int, val letterSpacing: Float = 0f)

    fun paint(r: Run) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = r.font; textSize = r.size; color = r.color; letterSpacing = r.letterSpacing
        setShadowLayer(10f, 0f, 1.5f, SHADOW)
    }

    private const val PAD = 12f

    /** One line of [runs] on a common baseline, laid out right to left when [rtl]. */
    fun line(runs: List<Run>, rtl: Boolean): Bitmap {
        val paints = runs.map { paint(it) }
        val widths = runs.indices.map { paints[it].measureText(runs[it].text) }
        val ascent = paints.maxOf { -it.fontMetrics.ascent }
        val descent = paints.maxOf { it.fontMetrics.descent }
        val w = ceil(widths.sum() + 2 * PAD).toInt().coerceAtLeast(1)
        val h = ceil(ascent + descent + 2 * PAD).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        var x = if (rtl) w - PAD else PAD
        for (i in runs.indices) {
            if (rtl) x -= widths[i]
            c.drawText(runs[i].text, x, PAD + ascent, paints[i])
            if (!rtl) x += widths[i]
        }
        return bmp
    }

    /** The prayer's name and its time, as the home screen's title: "Fajr · 6:09 AM" (the name in Kufi in Arabic). */
    fun title(ctx: Context, name: String, time: String?, arabic: Boolean, sizeDp: Float, density: Float): Bitmap {
        val s = sizeDp * density
        val runs = ArrayList<Run>()
        runs += if (arabic) Run(name, font(ctx, R.font.reem_kufi), (sizeDp + 2) * density, WHITE)
        else Run(name, font(ctx, R.font.plex_arabic_semibold), s, WHITE, -0.4f / sizeDp)
        if (time != null) {
            runs += Run("  ·  ", font(ctx, R.font.plex_arabic_medium), max(12f, sizeDp * 0.6f) * density, MUTED)
            runs += Run(time, font(ctx, R.font.plex_arabic_regular), s * 0.68f, SECONDARY)
        }
        return line(runs, arabic)
    }

    /** A small line (the place and the Hijri date). */
    fun small(ctx: Context, text: String, sizeDp: Float, density: Float, color: Int = SECONDARY, rtl: Boolean): Bitmap =
        line(listOf(Run(text, font(ctx, R.font.plex_arabic_regular), sizeDp * density, color)), rtl)

    /**
     * The day's six times in three columns and two rows, each name above its time; the [marked] one (counted to or
     * from) in gold. [width] px wide; right to left in Arabic.
     */
    fun grid(ctx: Context, names: List<String>, times: List<String>, marked: Int, width: Int, density: Float, rtl: Boolean): Bitmap {
        val nameSize = 11.5f * density; val timeSize = 16.5f * density
        val nameRun = { t: String, c: Int -> Run(t, font(ctx, R.font.plex_arabic_regular), nameSize, c) }
        val timeRun = { t: String, c: Int -> Run(t, font(ctx, R.font.plex_arabic_medium), timeSize, c) }
        // Lines as tight as the words (Plex's own line height is half again its size).
        val nameH = 1.3f * nameSize
        val timeH = 1.3f * timeSize
        val rowH = nameH + timeH + 4f * density
        val h = ceil(2 * rowH + PAD).toInt()
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val col = (width - 2 * PAD) / 3f
        for (i in 0 until minOf(6, names.size)) {
            val r = i / 3; val k = i % 3
            val x = if (rtl) width - PAD - k * col else PAD + k * col
            val on = i == marked
            val n = paint(nameRun(names[i], if (on) GOLD else SECONDARY)); val t = paint(timeRun(times[i], if (on) GOLD else WHITE))
            val y0 = PAD / 2 + r * rowH
            if (rtl) { n.textAlign = Paint.Align.RIGHT; t.textAlign = Paint.Align.RIGHT }
            c.drawText(names[i], x, y0 + 0.98f * nameSize, n)
            c.drawText(times[i], x, y0 + nameH + 0.98f * timeSize, t)
        }
        return bmp
    }
}
