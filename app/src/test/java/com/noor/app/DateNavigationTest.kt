package com.noor.app

import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.ui.today.DateWindow
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DateNavigationTest {
    @Test fun windowCoversFifteenFullYears() {
        assertEquals(LocalDate.of(2021, 1, 1), DateWindow.dateOf(0))
        assertEquals(LocalDate.of(2035, 12, 31), DateWindow.dateOf(DateWindow.pageCount - 1))
        assertEquals(5478, DateWindow.pageCount) // 2021-2035 inclusive, with leap days 2024, 2028, 2032
    }

    @Test fun everyPageMapsToItsDateAndBack() {
        var d = DateWindow.FIRST
        var page = 0
        while (d <= DateWindow.LAST) {
            assertEquals(page, DateWindow.pageOf(d))
            assertEquals(d, DateWindow.dateOf(page))
            d = d.plusDays(1); page++
        }
    }

    @Test fun datesOutsideAreClamped() {
        assertEquals(0, DateWindow.pageOf(LocalDate.of(1999, 5, 5)))
        assertEquals(DateWindow.pageCount - 1, DateWindow.pageOf(LocalDate.of(2099, 1, 1)))
        assertFalse(LocalDate.of(2020, 12, 31) in DateWindow)
        assertTrue(LocalDate.of(2028, 2, 29) in DateWindow)
    }

    @Test fun fridayDhuhrIsJumuah() {
        val en = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        val ar = Formatter(true, Numerals.WESTERN, TimeFormat.H12)
        val friday = LocalDate.of(2026, 11, 13); val thursday = friday.minusDays(1)
        assertEquals("Jumu’ah", en.prayerName(Prayer.DHUHR, friday))
        assertEquals("الجمعة", ar.prayerName(Prayer.DHUHR, friday))
        assertEquals("Dhuhr", en.prayerName(Prayer.DHUHR, thursday))
        assertEquals("Asr", en.prayerName(Prayer.ASR, friday))
        assertEquals("الجمعة", en.prayerNameOther(Prayer.DHUHR, friday))
    }

    @Test fun dateTexts() {
        val en = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        assertEquals("4 November 2026", en.gregorianDate(LocalDate.of(2026, 11, 4)))
        assertEquals("Wednesday", en.weekday(LocalDate.of(2026, 11, 4)))
        assertEquals("November 2026", en.monthYear(YearMonth.of(2026, 11)))
        val ar = Formatter(true, Numerals.ARABIC_INDIC, TimeFormat.H12)
        assertEquals("٤ نوفمبر ٢٠٢٦", ar.gregorianDate(LocalDate.of(2026, 11, 4)))
        assertEquals("الأربعاء", ar.weekday(LocalDate.of(2026, 11, 4)))
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class BundledDataTest {
    /** The packaged assets load as the app loads them (catches asset renaming by the build, e.g. ".gz"). */
    @org.junit.Test fun gazetteerAndTimetablesLoadFromAssets() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as NoorApplication
        val gaz = kotlinx.coroutines.runBlocking { app.container.gazetteer.get() }
        org.junit.Assert.assertTrue(gaz.places.size > 30_000)
        org.junit.Assert.assertEquals("Jebel Ali", gaz.nearest(com.noor.prayer.GeoPoint(25.01, 55.11))?.name)
        org.junit.Assert.assertEquals(1096, app.container.timetables.get("muis_singapore")?.size)
        org.junit.Assert.assertTrue((app.container.timetables.get("iacad_dubai")?.size ?: 0) >= 90)
    }

    /** Every sound the library lists is really packaged, credited and licensed for redistribution. */
    @org.junit.Test fun soundLibraryIsCompleteAndCredited() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val text = app.assets.open(com.noor.alerts.SoundLibrary.ASSET_PATH).bufferedReader().readText()
        val entries = com.noor.alerts.SoundLibrary.parse(text)
        fun exists(path: String) = runCatching { app.assets.open(path).close(); true }.getOrDefault(false)
        val allowed = setOf("CC0", "Public domain", "CC BY 3.0", "CC BY 4.0", "CC BY-SA 3.0", "CC BY-SA 4.0")
        org.junit.Assert.assertTrue(entries.size >= 10)
        for (e in entries) {
            e.file?.let { org.junit.Assert.assertTrue("missing ${e.id} full", exists(it)) }
            e.shortFile?.let { org.junit.Assert.assertTrue("missing ${e.id} short", exists(it)) }
            org.junit.Assert.assertTrue("no audio for ${e.id}", e.file != null || e.shortFile != null)
            val credit = e.credit
            org.junit.Assert.assertNotNull("no credit for ${e.id}", credit)
            org.junit.Assert.assertTrue("licence ${credit!!.licence}", credit.licence in allowed)
            if (e.kind == com.noor.alerts.SoundKind.ADHAN) {
                org.junit.Assert.assertTrue(credit.author.isNotBlank())
                org.junit.Assert.assertTrue(credit.sourceUrl!!.startsWith("https://commons.wikimedia.org/"))
                org.junit.Assert.assertTrue(e.titleAr.isNotBlank() && e.placeAr != null)
                org.junit.Assert.assertNotNull(e.shortFile)
            }
        }
        val lib = com.noor.alerts.SoundLibrary.get(app)
        // The defaults exist, with their full recordings; Fajr has a Fajr Adhan; other prayers never get it.
        listOf(com.noor.prayer.alerts.SoundIds.DEFAULT_ADHAN, com.noor.prayer.alerts.SoundIds.DEFAULT_FAJR_ADHAN).forEach {
            org.junit.Assert.assertTrue(it, lib.byId(it)?.let(lib::hasFull) == true)
        }
        org.junit.Assert.assertTrue(lib.byId(com.noor.prayer.alerts.SoundIds.DEFAULT_FAJR_ADHAN)!!.fajr)
        org.junit.Assert.assertNotNull(lib.byId(com.noor.prayer.alerts.SoundIds.DEFAULT_SOUND))
        org.junit.Assert.assertTrue(lib.adhansFor(com.noor.prayer.Prayer.ASR).none { it.fajr })
        org.junit.Assert.assertTrue(lib.sounds.map { it.categories.first() }.toSet().containsAll(setOf("brief", "short", "long")))
    }
}
