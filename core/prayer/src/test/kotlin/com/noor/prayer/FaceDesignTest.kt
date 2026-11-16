package com.noor.prayer

import com.noor.prayer.face.Ambient
import com.noor.prayer.face.Background
import com.noor.prayer.face.ColorRole
import com.noor.prayer.face.Edge
import com.noor.prayer.face.Element
import com.noor.prayer.face.ElementSize
import com.noor.prayer.face.FaceData
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceDigits
import com.noor.prayer.face.FaceFont
import com.noor.prayer.face.FaceGeometry
import com.noor.prayer.face.FaceLanguage
import com.noor.prayer.face.FaceTemplates
import com.noor.prayer.face.FaceText
import com.noor.prayer.face.Hands
import com.noor.prayer.face.Placement
import com.noor.prayer.face.Slot
import com.noor.prayer.face.TapAction
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Minhad Studio (0.18): the design that travels from the phone's editor to the watch, and what the face shows. */
class FaceDesignTest {
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 1L,
    )
    private val now: Instant = ZonedDateTime.of(2026, 11, 16, 14, 7, 30, 0, ZoneId.of("Asia/Dubai")).toInstant()

    @Test fun everyTemplateSurvivesTheTrip() {
        for (t in FaceTemplates.all) assertEquals(t, FaceDesign.decode(t.encode()), t.id)
        // A design using every option and an awkward label.
        val full = FaceTemplates.SUN.copy(
            name = "My | face = 100%\nnew", ambient = Ambient.CUSTOM, ambientSlots = setOf(Slot.TOP, Slot.LEFT), pixelShift = false, ambientOutline = true,
            slots = FaceTemplates.SUN.slots + (Slot.TOP to Placement(Element.LABEL, ElementSize.L, ColorRole.CUSTOM, 0xFF123456.toInt(), TapAction.TASBIH, "سبحان الله | 33")),
        )
        assertEquals(full, FaceDesign.decode(full.encode()))
    }

    @Test fun defaultsAndTolerance() {
        // Only a version: every default.
        val bare = FaceDesign.decode("v=1")!!
        assertEquals(FaceDesign(), bare)
        assertTrue(bare.pixelShift); assertEquals(Ambient.DIMMED, bare.ambient); assertEquals(Background.BLACK, bare.theme.background)
        assertNull(FaceDesign.decode("")); assertNull(FaceDesign.decode(null)); assertNull(FaceDesign.decode("garbage"))
        // Unknown keys, unknown values and unknown elements (a newer phone) are ignored, the rest is kept.
        val newer = FaceTemplates.DIAL.encode() + "future=1\nslot.upper=hologram|size:xl\n"
        val d = FaceDesign.decode(newer)!!
        assertEquals(Hands.CLASSIC, d.hands); assertNull(d.at(Slot.UPPER))
        val odd = FaceDesign.decode("v=1\nhands=sundial\nfont=comic\naccent=xyz\nslot.top=hijri|size:xl|color:plaid")!!
        assertEquals(Hands.NONE, odd.hands); assertEquals(FaceFont.LIGHT, odd.theme.font); assertEquals(FaceDesign().theme.accent, odd.theme.accent)
        assertEquals(Placement(Element.HIJRI), odd.at(Slot.TOP))
        assertEquals(FaceTemplates.DIAL.slots, d.slots)
        // A label is kept short.
        val long = FaceDesign().with(Slot.LOWER, Placement(Element.LABEL, text = "x".repeat(200)))
        assertEquals(FaceDesign.MAX_TEXT, FaceDesign.decode(long.encode())!!.at(Slot.LOWER)!!.text.length)
    }

    @Test fun movingSwapsAndRemoving() {
        val h = FaceTemplates.HORIZON
        val m = h.move(Slot.TOP, Slot.BOTTOM)
        assertEquals(Element.NEXT_PRAYER, m.at(Slot.TOP)!!.element); assertEquals(Element.HIJRI, m.at(Slot.BOTTOM)!!.element)
        val toEmpty = h.move(Slot.TOP, Slot.LEFT)
        assertNull(toEmpty.at(Slot.TOP)); assertEquals(Element.HIJRI, toEmpty.at(Slot.LEFT)!!.element)
        assertEquals(h, h.move(Slot.TOP, Slot.TOP))
        assertNull(h.with(Slot.TOP, null).at(Slot.TOP))
        assertTrue(h.showsTime); assertTrue(FaceTemplates.DIAL.showsTime); assertFalse(FaceDesign().showsTime)
        assertEquals(TapAction.QIBLA, Placement(Element.QIBLA).tapAction)
        assertEquals(TapAction.NONE, Placement(Element.QIBLA, tap = TapAction.NONE).tapAction)
    }

    @Test fun touchesFindTheirSlot() {
        for (s in Slot.entries) {
            val b = FaceGeometry.box(s)
            assertEquals(s, FaceGeometry.slotAt(b.cx, b.cy), s.id)
        }
        assertNull(FaceGeometry.slotAt(0.9f, 0.9f)) // outside the round face
        // Every box stays inside the face.
        for (s in Slot.entries) for (narrow in listOf(false, true)) {
            val b = FaceGeometry.box(s, if (narrow) FaceTemplates.DIAL else null)
            for ((x, y) in listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom)) assertTrue(x * x + y * y < 1f, "${s.id} corner")
        }
        // The centre narrows between left and right, so they never overlap it.
        val c = FaceGeometry.box(Slot.CENTER, FaceTemplates.DIAL)
        assertTrue(c.right < FaceGeometry.box(Slot.RIGHT).left)
    }

    @Test fun faceShowsWhatTheAppCalculates() {
        val d = FaceData.build(FaceTemplates.HORIZON, dubai, now, battery = 0.82f)
        val st = dubai.state(now)
        assertEquals(st.next.prayer, d.next!!.prayer)
        assertEquals(6, d.prayers.size)
        // Each time on the face is the engine's own, to the minute.
        val day = dubai.day(now.atZone(dubai.zone).toLocalDate())
        for (l in d.prayers) {
            val min = day.minutes[l.prayer]!!
            assertEquals(FaceText(false, false, false).hhmm(min / 60, min % 60), l.time.substringBefore(' '), l.prayer.name)
        }
        assertEquals(1, d.prayers.count { it.isNext })
        assertEquals("2:07", d.time); assertEquals("PM", d.ampm)
        assertEquals("Mon 16 Nov", d.date)
        assertEquals("82%", d.battery)
        assertTrue(d.next!!.progress in 0f..1f)
        assertTrue(d.validUntil.isAfter(now) && !d.validUntil.isAfter(now.plusSeconds(30)))
        assertNotNull(d.sun!!.dayFraction); assertNull(d.sun!!.nightFraction)
        assertTrue(d.qibla!!.contains("°"))
    }

    @Test fun arabicDesignsWriteNoLatinLetters() {
        val latin = Regex("[A-Za-z]")
        val arabicLetter = Regex("[؀-ۿ]")
        for (t in FaceTemplates.all) for (hour in listOf(3, 9, 14, 21)) {
            val at = ZonedDateTime.of(2026, 11, 16, hour, 59, 0, 0, ZoneId.of("Asia/Dubai")).toInstant()
            val ar = FaceData.build(t.copy(language = FaceLanguage.AR, digits = FaceDigits.ARABIC), dubai, at, battery = 0.5f)
            for (s in texts(ar)) {
                assertFalse(latin.containsMatchIn(s), "${t.id} at $hour: \"$s\"")
                assertFalse(Regex("[0-9]").containsMatchIn(s), "${t.id}: Western digit in \"$s\"")
            }
            // Arabic words with Western digits, when chosen.
            val arW = FaceData.build(t.copy(language = FaceLanguage.AR, digits = FaceDigits.WESTERN), dubai, at)
            for (s in texts(arW)) assertFalse(latin.containsMatchIn(s), "${t.id}: \"$s\"")
            assertTrue(texts(arW).any { Regex("[0-9]").containsMatchIn(it) })
            val en = FaceData.build(t.copy(language = FaceLanguage.EN, digits = FaceDigits.WESTERN), dubai, at)
            for (s in texts(en)) assertFalse(arabicLetter.containsMatchIn(s), "${t.id} en: \"$s\"")
        }
        // Following the app: its language and digits.
        val follow = FaceData.build(FaceTemplates.HORIZON, dubai.copy(language = "ar", arabicDigits = true), now)
        assertEquals("٢:٠٧", follow.time); assertEquals("م", follow.ampm)
        assertEquals("منهاد", follow.appName)
    }

    @Test fun withoutAPlaceTheFaceStillTellsTheTime() {
        val d = FaceData.build(FaceTemplates.HORIZON, null, now, ZoneId.of("Asia/Dubai"), device24h = true)
        assertNull(d.next); assertTrue(d.prayers.isEmpty()); assertNull(d.qibla)
        assertEquals("14:07", d.time); assertNull(d.ampm)
        assertEquals("Open Minhad", d.setupHint)
    }

    private fun texts(d: FaceData): List<String> = listOfNotNull(
        d.time, d.ampm, d.date, d.weekday, d.hijri, d.next?.name, d.next?.time, d.next?.countdown, d.next?.countdownShort, d.next?.currentName,
        d.qibla, d.battery, d.appName, d.setupHint,
    ) + d.prayers.flatMap { listOf(it.name, it.time) } + d.text.numerals + (0..59).map(d.text::twoDigits)

    @Test fun edgeAndDialEnumsAreAllOffered() {
        assertEquals(Element.entries.size - 1, Element.offered.size)
        assertTrue(Edge.entries.isNotEmpty())
    }
}
