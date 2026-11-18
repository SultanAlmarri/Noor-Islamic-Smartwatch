package com.noor.app.widget

import com.noor.prayer.sky.SkyPalette

/**
 * A widget's colours as drawn (0.24): the text's and the accent's, from the options, kept legible. A colour of one's
 * own that would not read on the widget's background (white text under 4.5:1 on a pale colour, an accent under 3:1)
 * is not used: the widget falls back to the colour chosen for the background, and the settings page says so
 * ([textFallback], [accentFallback]).
 */
internal class WidgetLooks(
    /** The text is dark (the background light). */
    val light: Boolean,
    val ink: Int,
    /** The accent: the next prayer's sign and capsule, the bar, the ring. */
    val accent: Int,
    /** The accent as text straight on the background (the "colour only" highlight): the accent where it reads, else the ink. */
    val accentOnBack: Int,
    /** The background is a colour or a picture under the text (the quieter text then less quiet). */
    val vivid: Boolean,
    val textFallback: Boolean,
    val accentFallback: Boolean,
    /** The quieter text's strengths (of the ink): secondary, tertiary, past; each the design's, or more where it must read. */
    val secK: Float = 0.72f,
    val terK: Float = 0.55f,
    val pastK: Float = 0.5f,
    /** The background's opacity as drawn (0..100): the one chosen, or more where its text needs it. */
    val opacity: Int = 100,
    /** The next prayer's capsule: white at 18 % (on light #F2F2F7), or black at 18 % where white text needs it. */
    val capsule: Int = 0x2EFFFFFF,
    /** The marked prayer's words on its capsule: the accent where it reads there, else the ink. */
    val markOnCapsule: Int = 0xFFFFFFFF.toInt(),
) {
    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val INK = 0xFF1C1C1E.toInt()
        /** What is under a see-through widget, for judging it: a middling wallpaper (the gallery's). */
        const val WALLPAPER = 0xFF7A6A72.toInt()

        fun contrast(a: Int, b: Int) = SkyPalette.contrast(a or 0xFF000000.toInt(), b or 0xFF000000.toInt())

        private fun over(c: Int, under: Int): Int {
            val a = (c ushr 24) / 255f
            fun ch(s: Int) = (((c shr s) and 255) * a + ((under shr s) and 255) * (1 - a)).toInt()
            return 0xFF000000.toInt() or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        /** The colours the widget's text stands on (as opaque colours over a middling wallpaper), or null when unknown. */
        fun backs(r: WidgetCtx, opacity: Int = r.o.opacity): List<Int>? {
            val o = r.o
            val a = (opacity.coerceIn(0, 100) * 255 + 50) / 100
            fun alpha(c: Int) = (c and 0x00FFFFFF) or (a shl 24)
            val raw = when (r.pal.background) {
                WidgetBackground.CLEAR -> return null
                WidgetBackground.SKY -> WidgetBack.skyLook(WidgetBack.elevationFor(r)).let { l -> listOf(l.zenith, l.mid, l.horizon).map { WidgetBack.clampColor(it) } }
                WidgetBackground.SOLID, WidgetBackground.GRADIENT -> customColors(o).let { cs -> if (needsDarkening(cs)) cs.map { WidgetBack.clampColor(it) } else cs }
                WidgetBackground.DARK -> listOf(0xFF1C1C1E.toInt())
                WidgetBackground.LIGHT -> listOf(WHITE)
                // (With the soft light along its top, 8 % white.)
                WidgetBackground.GLASS -> return listOf(0xFF2A2D36.toInt(), 0xFF15171D.toInt()).map { over(0x14FFFFFF, over(alpha(it), WALLPAPER)) } + over(alpha(0xFF15171D.toInt()), WALLPAPER)
                WidgetBackground.YOU -> listOf(if (r.pal.fill != 0) r.pal.fill else 0xFF232327.toInt())
                WidgetBackground.AUTO -> listOf(if (r.pal.light) WHITE else 0xFF1C1C1E.toInt())
            }
            return raw.map { over(alpha(it or 0xFF000000.toInt()), WALLPAPER) }
        }

        private fun worst(fg: Int, backs: List<Int>) = backs.minOf { contrast(fg, it) }

        /** A colour (or gradient) of one's own, as its colours. */
        fun customColors(o: WidgetOptions): List<Int> = if (o.background == WidgetBackground.SOLID) listOf(o.color1 or 0xFF000000.toInt())
            else listOf(o.color1, o.color2, SkyPalette.mix(o.color1, o.color2, 0.5f)).map { it or 0xFF000000.toInt() }

        /**
         * Whether a colour of one's own is a middle tone on which neither white nor the ink reads at 4.5:1 (a mid blue
         * gives 4.1:1 and 4.2:1): it is then darkened just enough for white text, its hue kept, as the sky is.
         */
        fun needsDarkening(colors: List<Int>): Boolean = worst(WHITE, colors) < 4.5 && worst(INK, colors) < 4.5

        fun of(r: WidgetCtx): WidgetLooks {
            val o = r.o
            // A see-through background is made as much more solid as its text needs to read over a middling
            // wallpaper (4.5:1), in steps of 5 %.
            var opacity = o.opacity.coerceIn(0, 100)
            var backs = backs(r, opacity)
            while (backs != null && opacity < 100 && r.pal.background != WidgetBackground.CLEAR) {
                val best = maxOf(worst(WHITE, backs), worst(INK, backs), if (r.pal.light) worst(INK, backs) else worst(r.pal.text, backs))
                if (best >= 4.6) break
                opacity = minOf(100, opacity + 5); backs = backs(r, opacity)
            }
            // The text chosen for the background: white or the ink, whichever reads better (on a colour of one's own),
            // or the palette's (the sky, dark, light, the wallpaper's colours).
            val auto = when {
                backs != null && (r.pal.background == WidgetBackground.SOLID || r.pal.background == WidgetBackground.GRADIENT || opacity < 100) ->
                    if (worst(WHITE, backs) >= worst(INK, backs)) WHITE else INK
                r.pal.light -> if (r.pal.background == WidgetBackground.YOU) r.pal.text else INK
                else -> r.pal.text
            }
            val chosen = when (o.textColor) {
                TextColor.AUTO -> auto
                TextColor.WHITE -> WHITE
                TextColor.BLACK -> INK
                TextColor.CUSTOM -> o.textCustom or 0xFF000000.toInt()
            }
            // Over no background the wallpaper is unknown: only white or the ink chosen for it.
            val readable = if (backs == null) chosen == auto || chosen == WHITE else worst(chosen, backs) >= 4.5
            val ink = if (readable) chosen else auto
            val light = SkyPalette.contrast(ink, INK) < SkyPalette.contrast(ink, WHITE)
            val defaultAccent = if (!light && o.accent == WidgetAccent.GOLD) 0xFFFFD36B.toInt()
                else WidgetPalette.accentOn(if (o.accent == WidgetAccent.CUSTOM) WidgetAccent.GOLD else o.accent, light, r.ctx).let { if (!light && o.accent == WidgetAccent.CUSTOM) 0xFFFFD36B.toInt() else it }
            val custom = o.accentCustom or 0xFF000000.toInt()
            val customReads = o.accent == WidgetAccent.CUSTOM && (backs == null || worst(custom, backs) >= 3.0)
            val accent = if (o.accent == WidgetAccent.CUSTOM && customReads) custom else defaultAccent
            // Words in the accent straight on the background (a date's days to come, a highlight in colour only).
            val accentOnBack = if (backs != null && worst(accent, backs) >= (if (r.pal.background == WidgetBackground.SKY) 3.05 else 4.55)) accent else ink
            val vivid = r.pal.background !in setOf(WidgetBackground.DARK, WidgetBackground.LIGHT, WidgetBackground.AUTO) || o.opacity < 90 ||
                (backs != null && worst(ink, backs) < 7.0)
            // The quieter text: the design's strength (72 %, 52 %, 50 %; on light 72 %, 62 %, 60 %), raised where it would
            // read under 4.5:1 on its background (3:1 over the sky, which varies): the least strength that reads.
            val target = if (r.pal.background == WidgetBackground.SKY) 3.05 else 4.55
            fun least(base: Float): Float {
                if (backs == null) return maxOf(base, 0.8f)
                var a = base
                while (a < 1f && backs.any { contrast(over((ink and 0x00FFFFFF) or ((255 * a).toInt() shl 24), it), it) < target }) a += 0.02f
                return a.coerceAtMost(1f)
            }
            val past = if (o.past == PastStyle.NORMAL) 1f else least(if (light) 0.6f else 0.5f)
            // The capsule under the next prayer, and its words: as the design (white 18 %, gold), or, where the text
            // would not read on it, black 18 % and the ink.
            val capsule = when {
                light -> 0xFFF2F2F7.toInt()
                backs != null && worst(ink, backs.map { over(0x2EFFFFFF, it) }) < target -> 0x2E000000
                else -> 0x2EFFFFFF
            }
            val onCapsule = backs?.map { over(capsule, it) }
            val markOnCapsule = if (onCapsule != null && worst(accent, onCapsule) >= target) accent else ink
            return WidgetLooks(light, ink, accent, accentOnBack, vivid, textFallback = !readable, accentFallback = o.accent == WidgetAccent.CUSTOM && !customReads,
                secK = least(0.72f), terK = least(if (light) 0.62f else 0.52f), pastK = past, opacity = opacity,
                capsule = capsule, markOnCapsule = markOnCapsule)
        }
    }
}
