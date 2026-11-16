package com.noor.prayer.face

/**
 * Minhad Studio (0.18): a watch face the user designs on the phone and the watch app draws itself.
 *
 * A design is a round face with named places ([Slot]) that each hold one piece of information ([Element]), a dial
 * layer under them (hands, marks, a ring round the edge), a theme (colours, font, background), the way it looks
 * always-on, and what a touch does. Slots instead of free positions: every design the user can make stays balanced and
 * legible on a 1.2-inch screen, any element can still go anywhere (drag it to another slot, or tap a slot and pick),
 * and the same geometry ([FaceGeometry]) is shared by the phone's editor, the watch's renderer and its touch targets.
 *
 * Wire format: one `key=value` per line, as [com.noor.prayer.WatchConfig]. Unknown keys and values are ignored and
 * missing ones fall back to defaults, so phone and watch of different versions keep working with each other.
 */
data class FaceDesign(
    val id: String = "studio",
    val name: String = "",
    val slots: Map<Slot, Placement> = emptyMap(),
    val hands: Hands = Hands.NONE,
    val marks: Marks = Marks.NONE,
    val edge: Edge = Edge.NONE,
    val seconds: Boolean = false,
    val theme: FaceTheme = FaceTheme(),
    val ambient: Ambient = Ambient.DIMMED,
    /** With [Ambient.CUSTOM]: the slots that stay on always-on (the time is always kept). */
    val ambientSlots: Set<Slot> = setOf(Slot.CENTER),
    /** Moves the always-on picture by a few pixels each minute, against burn-in on OLED screens. */
    val pixelShift: Boolean = true,
    /** Draws the always-on picture as outlines (fewer lit pixels). */
    val ambientOutline: Boolean = false,
    val language: FaceLanguage = FaceLanguage.FOLLOW,
    val digits: FaceDigits = FaceDigits.FOLLOW,
    val clock: FaceClock = FaceClock.FOLLOW,
) {
    fun at(slot: Slot): Placement? = slots[slot]?.takeIf { it.element != Element.NONE }

    fun with(slot: Slot, p: Placement?): FaceDesign = copy(slots = if (p == null || p.element == Element.NONE) slots - slot else slots + (slot to p))

    /** Moves what is in [from] to [to]; what was in [to] goes to [from] (a swap). */
    fun move(from: Slot, to: Slot): FaceDesign {
        if (from == to) return this
        val a = slots[from]; val b = slots[to]
        var m = slots - from - to
        if (a != null) m = m + (to to a)
        if (b != null) m = m + (from to b)
        return copy(slots = m)
    }

    /** True when the design has a clock: digital time somewhere, or hands. */
    val showsTime: Boolean get() = hands != Hands.NONE || slots.values.any { it.element == Element.TIME }

    fun encode(): String = buildString {
        fun put(k: String, v: Any?) { if (v != null) append(k).append('=').append(v.toString()).append('\n') }
        put("v", VERSION)
        put("id", esc(id)); put("name", esc(name))
        put("hands", hands.id); put("marks", marks.id); put("edge", edge.id); put("sec", seconds)
        put("accent", hex(theme.accent)); put("text", hex(theme.text)); put("soft", hex(theme.soft))
        put("bg", theme.background.id); put("bg1", hex(theme.bg1)); put("bg2", hex(theme.bg2))
        put("font", theme.font.id)
        put("aod", ambient.id); put("aodSlots", ambientSlots.sortedBy { it.ordinal }.joinToString(",") { it.id })
        put("shift", pixelShift); put("outline", ambientOutline)
        put("lang", language.id); put("digits", digits.id); put("clock", clock.id)
        for (s in Slot.entries) slots[s]?.takeIf { it.element != Element.NONE }?.let { p ->
            put("slot.${s.id}", listOfNotNull(
                p.element.id, "size:" + p.size.id, "color:" + p.color.id,
                p.custom?.let { "rgb:" + hex(it) }, p.tap?.let { "tap:" + it.id }, p.text.takeIf { it.isNotEmpty() }?.let { "txt:" + esc(it) },
            ).joinToString("|"))
        }
    }

    companion object {
        const val VERSION = 1
        /** Data Layer item the phone publishes and the watch's studio face draws. */
        const val PATH = "/noor/studio"
        const val KEY = "design"
        const val KEY_AT = "at"
        /** Longest label text a design keeps. */
        const val MAX_TEXT = 24

        fun decode(text: String?): FaceDesign? = runCatching {
            if (text.isNullOrBlank()) return null
            val m = text.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            if (m["v"] == null) return null
            val d = FaceDesign()
            val t = d.theme
            val slots = Slot.entries.mapNotNull { s -> m["slot.${s.id}"]?.let(::placement)?.let { s to it } }.toMap()
            FaceDesign(
                id = m["id"]?.let(::unesc)?.take(40)?.ifBlank { null } ?: d.id,
                name = m["name"]?.let(::unesc)?.take(40) ?: "",
                slots = slots,
                hands = pick(m["hands"], Hands.entries, Hands::id) ?: d.hands,
                marks = pick(m["marks"], Marks.entries, Marks::id) ?: d.marks,
                edge = pick(m["edge"], Edge.entries, Edge::id) ?: d.edge,
                seconds = m["sec"] == "true",
                theme = FaceTheme(
                    accent = argb(m["accent"]) ?: t.accent, text = argb(m["text"]) ?: t.text, soft = argb(m["soft"]) ?: t.soft,
                    background = pick(m["bg"], Background.entries, Background::id) ?: t.background,
                    bg1 = argb(m["bg1"]) ?: t.bg1, bg2 = argb(m["bg2"]) ?: t.bg2,
                    font = pick(m["font"], FaceFont.entries, FaceFont::id) ?: t.font,
                ),
                ambient = pick(m["aod"], Ambient.entries, Ambient::id) ?: d.ambient,
                ambientSlots = m["aodSlots"]?.split(',')?.mapNotNull { v -> Slot.entries.firstOrNull { it.id == v } }?.toSet() ?: d.ambientSlots,
                pixelShift = m["shift"]?.let { it == "true" } ?: d.pixelShift,
                ambientOutline = m["outline"] == "true",
                language = pick(m["lang"], FaceLanguage.entries, FaceLanguage::id) ?: d.language,
                digits = pick(m["digits"], FaceDigits.entries, FaceDigits::id) ?: d.digits,
                clock = pick(m["clock"], FaceClock.entries, FaceClock::id) ?: d.clock,
            )
        }.getOrNull()

        private fun placement(v: String): Placement? {
            val parts = v.split('|')
            val e = Element.entries.firstOrNull { it.id == parts[0] && it != Element.NONE } ?: return null
            var p = Placement(e)
            for (part in parts.drop(1)) {
                val k = part.substringBefore(':'); val x = part.substringAfter(':', "")
                p = when (k) {
                    "size" -> pick(x, ElementSize.entries, ElementSize::id)?.let { p.copy(size = it) } ?: p
                    "color" -> pick(x, ColorRole.entries, ColorRole::id)?.let { p.copy(color = it) } ?: p
                    "rgb" -> argb(x)?.let { p.copy(custom = it) } ?: p
                    "tap" -> pick(x, TapAction.entries, TapAction::id)?.let { p.copy(tap = it) } ?: p
                    "txt" -> p.copy(text = unesc(x).take(MAX_TEXT))
                    else -> p
                }
            }
            return p
        }

        private fun <T> pick(v: String?, all: List<T>, id: (T) -> String): T? = v?.let { s -> all.firstOrNull { id(it) == s } }
        internal fun hex(c: Int): String = "%08X".format(c)
        internal fun argb(v: String?): Int? = v?.takeIf { it.length == 8 }?.toLongOrNull(16)?.toInt()

        /** Percent-escapes the characters the format uses (newline, '|', '=', '%'), so any text survives. */
        internal fun esc(s: String): String = buildString {
            for (ch in s) when (ch) {
                '%', '|', '=', '\n', '\r' -> append('%').append("%02X".format(ch.code))
                else -> append(ch)
            }
        }
        internal fun unesc(s: String): String = buildString {
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                if (ch == '%' && i + 2 < s.length) {
                    val code = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (code != null) { append(code.toChar()); i += 3; continue }
                }
                append(ch); i++
            }
        }
    }
}

/** What goes in a slot. */
data class Placement(
    val element: Element,
    val size: ElementSize = ElementSize.M,
    val color: ColorRole = element.defaultColor,
    /** With [ColorRole.CUSTOM]. */
    val custom: Int? = null,
    /** What a touch on it does (null: the element's own, [Element.defaultTap]). */
    val tap: TapAction? = null,
    /** The words of a [Element.LABEL] (empty: the app's name in the face's language). */
    val text: String = "",
) {
    val tapAction: TapAction get() = tap ?: element.defaultTap
}

/**
 * The places on the face. Positions are in [FaceGeometry] (fractions of the radius from the centre), shared by the
 * renderer, the phone's editor and the watch's touch targets.
 */
enum class Slot(val id: String) {
    UPPER("upper"), TOP("top"), CENTER("center"), BOTTOM("bottom"), LOWER("lower"), LEFT("left"), RIGHT("right");
    val small: Boolean get() = this == LEFT || this == RIGHT
}

/** The pieces of information a slot can hold. */
enum class Element(val id: String, val defaultColor: ColorRole, val defaultTap: TapAction) {
    NONE("none", ColorRole.TEXT, TapAction.NONE),
    TIME("time", ColorRole.TEXT, TapAction.APP),
    DATE("date", ColorRole.SOFT, TapAction.CALENDAR),
    WEEKDAY("weekday", ColorRole.SOFT, TapAction.CALENDAR),
    HIJRI("hijri", ColorRole.ACCENT, TapAction.CALENDAR),
    NEXT_PRAYER("next", ColorRole.ACCENT, TapAction.APP),
    COUNTDOWN("countdown", ColorRole.TEXT, TapAction.APP),
    PRAYER_TIMES("times", ColorRole.SOFT, TapAction.APP),
    PRAYER_GAUGE("gauge", ColorRole.ACCENT, TapAction.APP),
    SUN_ARC("sun", ColorRole.ACCENT, TapAction.APP),
    QIBLA("qibla", ColorRole.ACCENT, TapAction.QIBLA),
    BATTERY("battery", ColorRole.SOFT, TapAction.NONE),
    LABEL("label", ColorRole.ACCENT, TapAction.TASBIH),
    ;

    companion object {
        /** The elements the editor offers, in its order. */
        val offered: List<Element> = entries.filter { it != NONE }
    }
}

enum class ElementSize(val id: String, val scale: Float) { S("s", 0.8f), M("m", 1f), L("l", 1.25f) }

/** Which of the theme's colours an element takes, or its own. */
enum class ColorRole(val id: String) { ACCENT("accent"), TEXT("text"), SOFT("soft"), CUSTOM("custom") }

enum class TapAction(val id: String) { NONE("none"), APP("app"), QIBLA("qibla"), TASBIH("tasbih"), CALENDAR("calendar") }

enum class Hands(val id: String) { NONE("none"), CLASSIC("classic"), SLIM("slim"), BOLD("bold") }

/** The marks round the dial. */
enum class Marks(val id: String) { NONE("none"), DOTS("dots"), MINUTES("minutes"), NUMERALS("numerals") }

/** What runs round the edge. */
enum class Edge(val id: String) {
    NONE("none"),
    /** The interval from the last prayer to the next, filling clockwise. */
    PRAYER_RING("ring"),
    /** The whole day as a dial: the six times as marks and the Sun's place now. */
    DAY_DIAL("day"),
}

enum class Background(val id: String) { BLACK("black"), SOLID("solid"), GRADIENT("gradient"), DUNES("dunes") }

/** The bundled typefaces (IBM Plex Sans Arabic and Reem Kufi, both SIL Open Font License; licences/). */
enum class FaceFont(val id: String) { LIGHT("light"), REGULAR("regular"), MEDIUM("medium"), KUFI("kufi") }

/** Always-on (ambient) look. */
enum class Ambient(val id: String) {
    /** The design itself, darkened, without background, ring fill or seconds. */
    DIMMED("dimmed"),
    /** The time and the next prayer only, as thin outlines. */
    MINIMAL("minimal"),
    /** The time alone. */
    TIME_ONLY("time"),
    /** The slots the user chose ([FaceDesign.ambientSlots]). */
    CUSTOM("custom"),
}

enum class FaceLanguage(val id: String) { FOLLOW("follow"), EN("en"), AR("ar") }
enum class FaceDigits(val id: String) { FOLLOW("follow"), WESTERN("western"), ARABIC("arabic") }
enum class FaceClock(val id: String) { FOLLOW("follow"), H12("12"), H24("24") }

/** Colours are ARGB ints. */
data class FaceTheme(
    val accent: Int = 0xFFE3C27E.toInt(),
    val text: Int = 0xFFF7F2E7.toInt(),
    val soft: Int = 0xFFB7C0D2.toInt(),
    val background: Background = Background.BLACK,
    val bg1: Int = 0xFF0D1626.toInt(),
    val bg2: Int = 0xFF000000.toInt(),
    val font: FaceFont = FaceFont.LIGHT,
)
