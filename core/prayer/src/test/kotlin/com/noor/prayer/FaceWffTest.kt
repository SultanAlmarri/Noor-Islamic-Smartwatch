package com.noor.prayer

import com.noor.prayer.face.Element
import com.noor.prayer.face.FaceTemplates
import com.noor.prayer.face.FaceWff
import com.noor.prayer.face.Placement
import com.noor.prayer.face.Slot
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The prototype that writes a studio design as Watch Face Format (0.18, not wired to Watch Face Push yet): every
 * template gives a well-formed document with Minhad's complications where prayer data goes, and what WFF cannot show
 * is reported, not faked. (Google's validator is not run here: that is the next step of the route, docs/WATCH.md.)
 */
class FaceWffTest {
    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream()).documentElement

    @Test fun templatesBecomeWellFormedFaces() {
        for (t in FaceTemplates.all) {
            val r = FaceWff.write(t)
            val root = parse(r.xml)
            assertEquals("WatchFace", root.tagName, t.id)
            val slots = root.getElementsByTagName("ComplicationSlot")
            val ids = (0 until slots.length).map { slots.item(it).attributes.getNamedItem("slotId").nodeValue }
            assertEquals(ids.size, ids.toSet().size, "${t.id}: slot ids are unique")
            if (t.slots.values.any { it.element == Element.TIME }) assertEquals(1, root.getElementsByTagName("DigitalClock").length, t.id)
        }
        val horizon = FaceWff.write(FaceTemplates.HORIZON)
        assertTrue(horizon.xml.contains("NextPrayerComplicationService")); assertTrue(horizon.xml.contains("HijriComplicationService"))
        assertTrue(horizon.unsupported.any { it.startsWith("background:dunes") })
        assertTrue(FaceWff.write(FaceTemplates.TIMETABLE).unsupported.contains("element:times"))
        assertTrue(FaceWff.write(FaceTemplates.DAY).unsupported.contains("edge:day"))
        // Words of any kind survive as text.
        val label = FaceWff.write(FaceTemplates.MINIMAL.with(Slot.UPPER, Placement(Element.LABEL, text = "سبحان الله <&> \"1\"")))
        assertTrue(parse(label.xml).textContent.contains("سبحان الله <&> \"1\""))
    }
}
