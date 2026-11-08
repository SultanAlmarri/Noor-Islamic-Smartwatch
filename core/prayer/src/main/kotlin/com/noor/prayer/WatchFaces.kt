package com.noor.prayer

/**
 * The Noor watch faces the watch app carries (0.9.8), as the phone and the watch both name them. Built by
 * tools/watchface/build_pushed_face.py (the same ids, in the same order: a test checks it): the original v1.3.2 face,
 * and three designs of the Noor face in five colours.
 *
 * The phone's editor chooses one and publishes it at [PATH]; the watch app replaces the face in its slot with that one,
 * in place, so the change shows at once and the face stays the active one.
 */
object WatchFaces {
    enum class Design(val id: String) { HORIZON("horizon"), DIAL("dial"), MINIMAL("minimal"), ORIGINAL("original") }

    /** The accent colours (ARGB), as the faces draw them. */
    enum class Colour(val id: String, val argb: Long) {
        GOLD("gold", 0xFFE3C27E), EMERALD("emerald", 0xFF5FCB9C), TURQUOISE("turquoise", 0xFF58C6D2),
        ROSE("rose", 0xFFE99CAB), SILVER("silver", 0xFFC9D1DB),
    }

    data class Choice(val design: Design, val colour: Colour = Colour.GOLD) {
        /** The face's id: "original", or "<design>_<colour>". The original has its own colours. */
        val id: String get() = if (design == Design.ORIGINAL) ORIGINAL_ID else "${design.id}_${colour.id}"
    }

    const val ORIGINAL_ID = "original"
    const val PATH = "/noor/face"
    const val KEY = "face"
    /** When the user asked (so that asking again for the same face is still delivered and re-applied). */
    const val KEY_AT = "at"

    val DEFAULT = Choice(Design.HORIZON, Colour.GOLD)

    /** Every face, in the order of the build. */
    val ids: List<String> = listOf(ORIGINAL_ID) +
        Design.entries.filter { it != Design.ORIGINAL }.flatMap { d -> Colour.entries.map { c -> Choice(d, c).id } }

    fun parse(id: String?): Choice? {
        if (id == ORIGINAL_ID) return Choice(Design.ORIGINAL)
        val parts = id?.split('_')?.takeIf { it.size == 2 } ?: return null
        val d = Design.entries.firstOrNull { it.id == parts[0] && it != Design.ORIGINAL } ?: return null
        val c = Colour.entries.firstOrNull { it.id == parts[1] } ?: return null
        return Choice(d, c)
    }
}
