package com.noor.prayer

import com.noor.prayer.geo.AddressParts
import com.noor.prayer.geo.Gazetteer
import com.noor.prayer.geo.PlaceLabel
import com.noor.prayer.geo.PlaceNaming
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoNamingTest {
    companion object {
        val gaz: Gazetteer by lazy {
            GZIPInputStream(GeoNamingTest::class.java.classLoader.getResourceAsStream("gazetteer.tsv.gz")!!).bufferedReader()
                .useLines { Gazetteer.parse(it.toList().asSequence()) }
        }
    }

    @Test fun preciseShowsTheNeighbourhood() {
        val a = AddressParts("Za'abeel", "Dubai", null, "Dubai", "AE", "United Arab Emirates")
        assertEquals(PlaceLabel("Za'abeel", "Dubai"), PlaceNaming.label(a, precise = true))
        assertEquals(PlaceLabel("Dubai", "United Arab Emirates"), PlaceNaming.label(a, precise = false))
    }

    @Test fun townsAbroad() {
        assertEquals(PlaceLabel("Baden-Baden", "Baden-Württemberg"),
            PlaceNaming.label(AddressParts(null, "Baden-Baden", "Stadtkreis Baden-Baden", "Baden-Württemberg", "DE", "Germany"), true))
        assertEquals(PlaceLabel("Surfers Paradise", "Queensland"),
            PlaceNaming.label(AddressParts(null, "Surfers Paradise", "Gold Coast City", "Queensland", "AU", "Australia"), true))
    }

    @Test fun neverInventsNames() {
        assertNull(PlaceNaming.label(AddressParts("7Q9W+X3", null, null, null, "AE", null), true))
        assertEquals(PlaceLabel("Dubai", null), PlaceNaming.label(AddressParts("7Q9W+X3", null, null, "Dubai", "AE", "Dubai"), true))
    }

    @Test fun gazetteerLoadsAndFindsPlaces() {
        assertTrue(gaz.places.size > 30_000)
        val jebelAli = gaz.nearest(GeoPoint(25.01, 55.11))
        assertEquals("Jebel Ali", jebelAli?.name)
        assertEquals("AE-DU", jebelAli?.region)
        assertEquals("Surfers Paradise", gaz.nearest(GeoPoint(-28.0, 153.43))?.name)
        assertEquals("Baden-Baden", gaz.nearest(GeoPoint(48.76, 8.24))?.name)
        assertNull(gaz.nearest(GeoPoint(-40.0, -120.0))) // open ocean
    }

    @Test fun searchIsAccentAndScriptInsensitive() {
        assertEquals("Baden-Baden", gaz.search("baden-b").first().name)
        assertTrue(gaz.search("Zurich").any { it.name == "Zürich" })
        assertTrue(gaz.search("دب").any { it.countryCode == "AE" && it.admin1Code == "03" })
        assertNotNull(gaz.search("Makkah").firstOrNull() ?: gaz.search("Mecca").firstOrNull())
    }
}
