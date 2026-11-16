package com.noor.prayer

import com.noor.prayer.research.AuthorityMeasure
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the agreement of the app's profiles with the authorities' own published years
 * (core/prayer/src/test/resources/authorities; accuracy/results/authorities_measurements.md). The figures are
 * measured, not tuned: a change to a profile that moves them must update the documents too.
 */
class AuthorityTablesTest {
    private val m = AuthorityMeasure()

    private fun check(c: AuthorityMeasure.Case, exact: Int, maxDiff: Int) {
        val flat = m.diffs(c).flatMap { it.second }
        assertEquals(2190, flat.size, c.label)
        assertEquals(exact, flat.count { it == 0 }, "${c.label} (${c.profile.id}) exact")
        assertEquals(maxDiff, flat.maxOf { abs(it) }, "${c.label} (${c.profile.id}) largest difference")
    }

    @Test fun theAppsProfilesAgainstThePublishedYears() {
        val tr = AuthorityMeasure.CASES.first { it.file == "diyanet_istanbul" }
        check(tr, 1392, 2)
        val my = AuthorityMeasure.MALAYSIA
        check(my[0], 1318, 2); check(my[1], 1459, 2); check(my[2], 1267, 2)
        check(my[3], 1330, 5) // Kota Bharu: Kelantan's Isha is about 4 minutes earlier than 18 degrees gives
        val id = AuthorityMeasure.INDONESIA
        check(id[0], 2026, 1); check(id[1], 2057, 1); check(id[2], 2027, 1)
        // Kazakhstan: every one of the 13 cities 90% exact or better, every time within 1 minute
        for (c in AuthorityMeasure.DUMK_CASES) {
            val flat = m.diffs(c.copy(profile = Profiles.KAZAKHSTAN)).flatMap { it.second }
            assertTrue(flat.count { it == 0 } >= 1977, "${c.label}: ${flat.count { it == 0 }}")
            assertEquals(1, flat.maxOf { abs(it) }, c.label)
        }
        val ba = AuthorityMeasure.CASES.last { it.file == "iz_bih_sarajevo" }
        assertEquals(Profiles.BOSNIA, ba.profile)
        check(ba, 1236, 2)
    }
}
