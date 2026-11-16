package com.noor.prayer.face

/**
 * The designs the studio starts from, and the theme presets. Three follow the pushed faces (Horizon, Dial, Minimal);
 * the others show what the studio adds: the day as a dial, the whole timetable, Kufi lettering, the Sun's arc.
 */
object FaceTemplates {
    enum class Palette(val id: String, val accent: Int, val text: Int, val soft: Int, val bg1: Int, val bg2: Int) {
        GOLD("gold", 0xFFE3C27E.toInt(), 0xFFF7F2E7.toInt(), 0xFFB7C0D2.toInt(), 0xFF0D1626.toInt(), 0xFF000000.toInt()),
        EMERALD("emerald", 0xFF5FCB9C.toInt(), 0xFFF1F7F3.toInt(), 0xFFA9C7B9.toInt(), 0xFF0B2219.toInt(), 0xFF000000.toInt()),
        TURQUOISE("turquoise", 0xFF58C6D2.toInt(), 0xFFEFF8FA.toInt(), 0xFFA7C3CB.toInt(), 0xFF09212A.toInt(), 0xFF000000.toInt()),
        ROSE("rose", 0xFFE99CAB.toInt(), 0xFFFBF1F3.toInt(), 0xFFCBB0B8.toInt(), 0xFF2A1219.toInt(), 0xFF000000.toInt()),
        SILVER("silver", 0xFFC9D1DB.toInt(), 0xFFFFFFFF.toInt(), 0xFF9AA4B2.toInt(), 0xFF1A1E25.toInt(), 0xFF000000.toInt()),
        SAND("sand", 0xFFF0B46A.toInt(), 0xFFFFF4E4.toInt(), 0xFFD1B796.toInt(), 0xFF3A2410.toInt(), 0xFF0E0904.toInt()),
        DUSK("dusk", 0xFFB9A3F0.toInt(), 0xFFF4F0FF.toInt(), 0xFFAFA8C9.toInt(), 0xFF1D1638.toInt(), 0xFF05030C.toInt()),
        ;

        fun apply(t: FaceTheme): FaceTheme = t.copy(accent = accent, text = text, soft = soft, bg1 = bg1, bg2 = bg2)

        companion object {
            fun of(t: FaceTheme): Palette? = entries.firstOrNull { it.accent == t.accent && it.text == t.text && it.soft == t.soft }
        }
    }

    /** Colours offered for one element, besides the theme's three (ARGB). */
    val swatches: List<Int> = listOf(
        0xFFFFFFFF, 0xFFE3C27E, 0xFFF0B46A, 0xFFFF8A65, 0xFFE99CAB, 0xFFB9A3F0, 0xFF7FA7FF, 0xFF58C6D2, 0xFF5FCB9C, 0xFFB5D96B,
    ).map { it.toInt() }

    private fun p(e: Element, size: ElementSize = ElementSize.M, color: ColorRole = e.defaultColor) = Placement(e, size, color)

    val HORIZON = FaceDesign(
        id = "tpl_horizon",
        slots = mapOf(
            Slot.UPPER to p(Element.DATE, ElementSize.S), Slot.TOP to p(Element.HIJRI),
            Slot.CENTER to p(Element.TIME, ElementSize.L), Slot.BOTTOM to p(Element.NEXT_PRAYER), Slot.LOWER to p(Element.COUNTDOWN, ElementSize.S),
        ),
        edge = Edge.PRAYER_RING, theme = FaceTheme(background = Background.DUNES),
    )

    val DIAL = FaceDesign(
        id = "tpl_dial",
        slots = mapOf(
            Slot.TOP to p(Element.HIJRI, ElementSize.S), Slot.BOTTOM to p(Element.NEXT_PRAYER, ElementSize.S),
            Slot.LEFT to p(Element.QIBLA), Slot.RIGHT to p(Element.BATTERY),
        ),
        hands = Hands.CLASSIC, marks = Marks.MINUTES, edge = Edge.PRAYER_RING, seconds = true,
        theme = FaceTheme(background = Background.GRADIENT), ambient = Ambient.MINIMAL,
    )

    val MINIMAL = FaceDesign(
        id = "tpl_minimal",
        slots = mapOf(Slot.CENTER to p(Element.TIME, ElementSize.L), Slot.BOTTOM to p(Element.NEXT_PRAYER, ElementSize.S)),
        edge = Edge.PRAYER_RING, theme = FaceTheme(font = FaceFont.LIGHT), ambient = Ambient.TIME_ONLY,
    )

    val DAY = FaceDesign(
        id = "tpl_day",
        slots = mapOf(
            Slot.TOP to p(Element.NEXT_PRAYER), Slot.CENTER to p(Element.TIME, ElementSize.L),
            Slot.BOTTOM to p(Element.COUNTDOWN, ElementSize.S, ColorRole.ACCENT), Slot.LOWER to p(Element.HIJRI, ElementSize.S, ColorRole.SOFT),
        ),
        edge = Edge.DAY_DIAL, theme = FaceTemplates.Palette.TURQUOISE.apply(FaceTheme(background = Background.GRADIENT)),
    )

    val TIMETABLE = FaceDesign(
        id = "tpl_timetable",
        slots = mapOf(Slot.UPPER to p(Element.TIME), Slot.CENTER to p(Element.PRAYER_TIMES, ElementSize.L), Slot.LOWER to p(Element.HIJRI, ElementSize.S)),
        edge = Edge.PRAYER_RING, theme = FaceTemplates.Palette.EMERALD.apply(FaceTheme(font = FaceFont.REGULAR)), ambient = Ambient.CUSTOM,
        ambientSlots = setOf(Slot.UPPER, Slot.CENTER),
    )

    val KUFI = FaceDesign(
        id = "tpl_kufi",
        slots = mapOf(
            Slot.TOP to p(Element.WEEKDAY, ElementSize.S), Slot.CENTER to p(Element.TIME, ElementSize.L),
            Slot.BOTTOM to p(Element.HIJRI), Slot.LOWER to p(Element.LABEL, ElementSize.S),
        ),
        marks = Marks.DOTS, theme = FaceTemplates.Palette.SAND.apply(FaceTheme(font = FaceFont.KUFI, background = Background.GRADIENT)),
        language = FaceLanguage.AR, digits = FaceDigits.ARABIC,
    )

    val SUN = FaceDesign(
        id = "tpl_sun",
        slots = mapOf(
            Slot.UPPER to p(Element.SUN_ARC), Slot.CENTER to p(Element.TIME), Slot.LEFT to p(Element.PRAYER_GAUGE), Slot.RIGHT to p(Element.BATTERY),
            Slot.BOTTOM to p(Element.DATE, ElementSize.S), Slot.LOWER to p(Element.QIBLA, ElementSize.S),
        ),
        theme = FaceTemplates.Palette.DUSK.apply(FaceTheme(font = FaceFont.MEDIUM, background = Background.GRADIENT)), seconds = true,
    )

    val all: List<FaceDesign> = listOf(HORIZON, DIAL, MINIMAL, DAY, TIMETABLE, KUFI, SUN)

    /** The design a watch shows before the phone has sent one. */
    val DEFAULT: FaceDesign = HORIZON

    fun byId(id: String): FaceDesign? = all.firstOrNull { it.id == id }
}
