package com.noor.prayer

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

/**
 * 0.9.8: the watch faces the phone can choose are exactly the ones the watch app carries (the build script's list),
 * every id parses back to its design and colour, and the watch's report of its face survives the trip to the phone.
 */
class WatchFacesTest {
    @Test fun idsMatchTheBuild() {
        assertEquals(16, WatchFaces.ids.size)
        assertEquals("original", WatchFaces.ids.first())
        for (id in WatchFaces.ids) assertEquals(id, WatchFaces.parse(id)!!.id)
        assertNull(WatchFaces.parse("horizon")); assertNull(WatchFaces.parse("dial_blue")); assertNull(WatchFaces.parse(null))
        assertEquals("horizon_gold", WatchFaces.DEFAULT.id)
        // The build script lists the same faces in the same order.
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "tools/watchface/noor_face.py").exists() }
        val py = File(root, "tools/watchface/noor_face.py").readText()
        val designs = Regex("""DESIGNS = \[([^\]]*)\]""").find(py)!!.groupValues[1].split(',').map { it.trim().trim('"') }
        val colours = Regex("""COLOURS = \{([^}]*)\}""").find(py)!!.groupValues[1].lines().mapNotNull { Regex(""""(\w+)":""").find(it)?.groupValues?.get(1) }
        assertEquals(listOf("original") + designs.flatMap { d -> colours.map { "${d}_$it" } }, WatchFaces.ids)
        // And the same accent colours.
        for (c in WatchFaces.Colour.entries) {
            val hex = Regex(""""${c.id}": "#([0-9A-F]{6})"""").find(py)!!.groupValues[1]
            assertEquals(0xFF000000 or hex.toLong(16), c.argb, c.id)
        }
    }

    @Test fun reportCarriesTheFace() {
        val r = WatchReport(98, "0.9.8", 36, true, 2, 2, false, null, 1L, faceVariant = "dial_rose", faceChosen = "dial_rose", faceNeedsPick = true)
        val back = WatchReport.decode(r.encode())!!
        assertEquals("dial_rose", back.faceVariant); assertEquals("dial_rose", back.faceChosen); assertEquals(true, back.faceNeedsPick)
        // An older watch app's report has none of it; a bad id is dropped.
        val old = WatchReport.decode(r.copy(faceVariant = null, faceChosen = null, faceNeedsPick = null).encode())!!
        assertNull(old.faceVariant); assertNull(old.faceNeedsPick)
        assertNull(WatchReport.decode(r.encode().replace("dial_rose", "nope"))!!.faceVariant)
        assertTrue(back.faceActive.not())
    }
}
