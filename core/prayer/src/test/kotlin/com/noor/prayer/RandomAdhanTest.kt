package com.noor.prayer

import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.RandomAdhan
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Random Adhan (0.9.5): fair rotation, no back-to-back repeats, Fajr rules, same answer everywhere. */
class RandomAdhanTest {
    private val favs = listOf("adhan.a", "adhan.b", "adhan.c", "adhan.fajr1", "adhan.d")
    private val fajr: (String) -> Boolean = { it.contains("fajr") }
    private val prayers = listOf(Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    @Test fun everyFavouritePlaysOnceBeforeAnyRepeatsAndNeverTwiceInARow() {
        val start = LocalDate.of(2026, 11, 1)
        val seq = (0 until 120).flatMap { d -> prayers.map { p -> RandomAdhan.pick(favs, p, start.plusDays(d.toLong()), fajr)!! } }
        // Aligned to the rounds: every block of 4 (the general favourites) holds each one once.
        val first = (start.toEpochDay() * 4).let { Math.floorMod(it, 4L).toInt() }
        assertEquals(0, first)
        seq.chunked(4).forEach { assertEquals(setOf("adhan.a", "adhan.b", "adhan.c", "adhan.d"), it.toSet()) }
        assertTrue(seq.zipWithNext().none { (a, b) -> a == b }, "no Adhan twice in a row")
        assertTrue("adhan.fajr1" !in seq, "a Fajr recording never plays at another prayer")
    }

    @Test fun fajrRotatesItsOwnRecordingsAndTheAnswerIsStable() {
        val d = LocalDate.of(2026, 11, 7)
        assertEquals("adhan.fajr1", RandomAdhan.pick(favs, Prayer.FAJR, d, fajr))
        // Without Fajr recordings among the favourites, Fajr rotates through the others.
        val noFajr = listOf("adhan.a", "adhan.b")
        val fajrSeq = (0 until 10).map { RandomAdhan.pick(noFajr, Prayer.FAJR, d.plusDays(it.toLong()), fajr) }
        fajrSeq.chunked(2).forEach { assertEquals(setOf("adhan.a", "adhan.b"), it.toSet()) }
        // Same inputs, same pick (phone and watch agree); sunrise has no Adhan; no favourites, no pick.
        assertEquals(RandomAdhan.pick(favs, Prayer.ASR, d, fajr), RandomAdhan.pick(favs.reversed(), Prayer.ASR, d, fajr))
        assertNull(RandomAdhan.pick(favs, Prayer.SHURUQ, d, fajr))
        assertNull(RandomAdhan.pick(emptyList(), Prayer.ASR, d, fajr))
        assertEquals("adhan.a", RandomAdhan.pick(listOf("adhan.a"), Prayer.ISHA, d, fajr))
    }

    @Test fun favouritesTravelInTheSettings() {
        val s = AlertSettings(enabled = true, favourites = listOf("adhan.makkah_maghrib_2012", "adhan.casablanca_2019"))
        assertEquals(s, AlertSettings.decode(s.encode()))
        // Imported sounds and damaged ids are not favourites (a favourite must exist on both devices).
        assertEquals(listOf("adhan.x"), AlertSettings.decode("v=1;on=1;fav=adhan.x,custom.abc,BAD ID").favourites)
    }
}
