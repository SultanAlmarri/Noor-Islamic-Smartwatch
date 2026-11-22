package com.noor.app.ui.location

import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.util.Formatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 0.25, second review: how place names and time zones are written on the location pages. Names romanised from the
 * Arabic script are shown in plain letters in English, and every other name exactly as it is (its marks are its own
 * language's letters); a typed filter matches a name in both forms. The time zone is its name and "UTC+4".
 */
class PlaceTextTest {
    @Test fun romanisedNamesAreShownInPlainLetters() {
        assertEquals("Harat al Bab wa ash Shamiyah", PlaceText.en("Ḩārat al Bāb wa ash Shāmīyah", "SA"))
        assertEquals("Abu Hayl", PlaceText.en("Abū Hayl", "AE"))
        assertEquals("Ad Daghayah", PlaceText.en("Aḑ Ḑaghāyah", "AE"))
        assertEquals("Al Baharnah", PlaceText.en("Al Baḩārnah", "AE"))
        assertEquals("Al Usbij", PlaceText.en("Al ‘Usbij", "AE"))
        assertEquals("Ras al Khaymah", PlaceText.en("Ra’s al Khaymah", "AE"))
        assertEquals("Fes", PlaceText.en("Fès", "MA"))
        assertEquals("Al Bada'a", PlaceText.en("Al Bada'a", "AE")) // a plain apostrophe is the name's own spelling
        assertEquals("Dubai Marina", PlaceText.en("Dubai Marina", "AE"))
    }

    @Test fun otherLanguagesKeepTheirLetters() {
        for ((name, cc) in listOf("São Paulo" to "BR", "Zürich" to "CH", "İstanbul" to "TR", "Kraków" to "PL", "Mar’yanovka" to "RU",
            "Yong’an" to "CN", "Malmö" to "SE", "Ḩārat" to null)) assertEquals(name, PlaceText.en(name, cc))
    }

    @Test fun aFilterMatchesBothForms() {
        assertEquals(PlaceText.key("Al Usbij"), PlaceText.key("Al ‘Usbij"))
        assertTrue(PlaceText.key("Abū Hayl").contains(PlaceText.key("abu hayl")))
        assertTrue(PlaceText.key("Ra’s al Khaymah").contains(PlaceText.key("Ras al")))
    }

    @Test fun theTimeZoneAsPeopleWriteIt() {
        val en = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        val lrm = "‎"
        assertEquals("+4", utcOffset(4 * 3600, en))
        assertEquals("+5:30", utcOffset(5 * 3600 + 1800, en))
        assertEquals("+5:45", utcOffset(5 * 3600 + 2700, en))
        assertEquals("-3:30", utcOffset(-(3 * 3600 + 1800), en))
        assertEquals("", utcOffset(0, en))
        assertEquals("$lrm+٣$lrm", utcOffset(3 * 3600, Formatter(true, Numerals.ARABIC_INDIC, TimeFormat.H12)))
        // (In Arabic the offset is kept left to right; in English it needs no mark.) The name without the database's id.
        val (name, offset) = zoneText("Asia/Riyadh", false, en, Instant.parse("2026-11-22T12:00:00Z"))
        assertEquals("Arabian Standard Time", name)
        assertEquals("+3", offset)
        val (dubai, _) = zoneText("Asia/Dubai", false, en, Instant.parse("2026-11-22T12:00:00Z"))
        assertTrue(dubai!!, '/' !in dubai && "Dubai" !in dubai)
    }
}
