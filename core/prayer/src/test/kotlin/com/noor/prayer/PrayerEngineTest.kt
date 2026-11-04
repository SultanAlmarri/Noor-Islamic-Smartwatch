package com.noor.prayer

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrayerEngineTest {

    @Test fun dubaiMatchesResearchReferenceEveryDay2014to2030() {
        val r = GoldenChecks.dubaiRaw()
        assertTrue(r.maxAbsSeconds < 0.5, "max deviation ${r.maxAbsSeconds}s at ${r.worst}")
        assertEquals(0, r.minuteMismatches, "rounded minutes must equal the reference")
    }

    @Test fun otherPlacesAndAnglesMatchReference() {
        val r = GoldenChecks.genericRaw()
        assertTrue(r.compared > 1000)
        assertTrue(r.maxAbsSeconds < 0.5, "max deviation ${r.maxAbsSeconds}s at ${r.worst}")
    }

    /** Official Dulook DXB times for April and December 2026 (December was predicted before it was seen). */
    @Test fun dulookRecordingAgreement() {
        val r = GoldenChecks.dulookRecording()
        println("Dulook recording: ${r.exact}/${r.compared} exact, max ${r.maxAbsMinutes} min; ${r.misses}")
        assertEquals(42, r.compared)
        assertEquals(39, r.exact, "update docs/PRAYER_ACCURACY.md if this changes")
        assertEquals(1, r.maxAbsMinutes)
    }

    /** Locks the documented accuracy against official IACAD data; a change must be deliberate. */
    @Test fun iacadOfficialAgreementIsAsDocumented() {
        val r = GoldenChecks.iacadOfficial()
        assertEquals(588, r.compared)
        assertEquals(527, r.exact, "exact matches changed; update docs/PRAYER_ACCURACY.md if intended")
        assertEquals(1, r.maxAbsMinutes)
    }

    @Test fun dubaiProfileUsesTheCityTimetableAcrossTheCityOnly() {
        val zone = ZoneId.of("Asia/Dubai")
        val d = LocalDate.of(2026, 11, 4)
        val deira = PrayerEngine.compute(d, GeoPoint(25.27, 55.30), zone, Profiles.DUBAI_IACAD)
        val jebelAli = PrayerEngine.compute(d, GeoPoint(25.00, 55.11), zone, Profiles.DUBAI_IACAD)
        assertEquals(deira.minutes, jebelAli.minutes, "one city timetable inside the city")
        // Far from Dubai (London), IACAD's conventions are applied at the user's own position, not Dubai's times.
        val london = PrayerEngine.compute(d, GeoPoint(51.5074, -0.1278), ZoneId.of("Europe/London"), Profiles.DUBAI_IACAD)
        assertTrue(london.minutes[Prayer.DHUHR]!! in 11 * 60..12 * 60 + 30)
    }

    /** Singapore conventions fitted on MUIS 2024-2025 and held out on 2026 (docs/AUTHORITIES.md). */
    @Test fun muisAgreementIsAsDocumented() {
        val r = GoldenChecks.muisOfficial()
        println("MUIS: ${r.exact}/${r.compared} exact, max ${r.maxAbsMinutes} min, per prayer ${r.perPrayer}")
        assertEquals(6576, r.compared)
        assertTrue(r.exact >= 4850, "exact ${r.exact}")
        assertTrue(r.maxAbsMinutes <= 2)
    }

    @Test fun highLatitudeRuleProvidesFajrAndIshaInLondonSummer() {
        val day = PrayerEngine.compute(LocalDate.of(2026, 6, 21), GeoPoint(51.5074, -0.1278), ZoneId.of("Europe/London"), Profiles.MWL)
        assertTrue(Prayer.entries.all { day.minutes[it] != null })
        val m = day.minutes
        assertTrue(m[Prayer.FAJR]!! < m[Prayer.SHURUQ]!! && m[Prayer.ISHA]!! > m[Prayer.MAGHRIB]!!)
    }

    @Test fun ummAlQuraIshaIs90MinutesAfterMaghribAnd120InRamadan() {
        val z = ZoneId.of("Asia/Riyadh"); val d = LocalDate.of(2026, 11, 4); val mk = GeoPoint(21.4225, 39.8262)
        val n = PrayerEngine.compute(d, mk, z, Profiles.UMM_AL_QURA)
        val r = PrayerEngine.compute(d, mk, z, Profiles.UMM_AL_QURA, isRamadan = true)
        assertEquals(90, n.minutes[Prayer.ISHA]!! - n.minutes[Prayer.MAGHRIB]!!, )
        assertEquals(120, r.minutes[Prayer.ISHA]!! - r.minutes[Prayer.MAGHRIB]!!)
    }

    @Test fun userOffsetsApply() {
        val z = ZoneId.of("Asia/Dubai"); val d = LocalDate.of(2026, 11, 4)
        val base = PrayerEngine.compute(d, GeoPoint(25.2, 55.27), z, Profiles.DUBAI_IACAD)
        val adj = PrayerEngine.compute(d, GeoPoint(25.2, 55.27), z, Profiles.DUBAI_IACAD, UserAdjustments(minuteOffsets = mapOf(Prayer.ASR to 2)))
        assertEquals(base.minutes[Prayer.ASR]!! + 2, adj.minutes[Prayer.ASR])
    }
}
