package com.noor.app.ui.settings

import com.noor.prayer.Profiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.25: the accuracy page shows the methods after the first two in grouped lists. The page may say no more and no less
 * than each profile's own statement: every method is shown exactly once; a statement stands once under several names
 * only when it is, word for word and in both languages, the statement of each of them; and the two statements the
 * page words for several methods are still the profiles' own.
 */
class AccuracyGroupsTest {
    private val rest = Profiles.ALL.drop(2)

    @Test fun everyMethodIsShownExactlyOnce() {
        val g = accuracyGroups()
        val shown = g.own.map { it.id } + g.shared.flatMap { (_, members) -> members.map { it.id } }
        assertEquals(rest.map { it.id }.sorted(), shown.sorted())
        assertEquals("a method is shown twice", shown.size, shown.toSet().size)
        // The first two have cards of their own: Dubai's authority and the Emirates'.
        assertEquals(listOf(Profiles.DUBAI_IACAD.id, Profiles.UAE_AWQAF.id), Profiles.ALL.take(2).map { it.id })
    }

    @Test fun aSharedStatementIsEachMembersOwn() {
        for ((statement, members) in accuracyGroups().shared) {
            assertTrue(members.size > 1)
            for (p in members) {
                assertEquals(p.id, statement, p.validationEn)
                assertEquals("${p.id}: the Arabic statement differs within a group", members.first().validationAr, p.validationAr)
            }
        }
        // A method listed with a statement of its own shares it with no other.
        for (p in accuracyGroups().own) assertEquals(p.id, 1, rest.count { it.validationEn == p.validationEn })
    }

    @Test fun theTwoStatementsWordedForSeveralMethodsAreTheProfilesOwn() {
        val shared = accuracyGroups().shared.toMap()
        assertEquals(setOf(STATEMENT_PUBLISHED, STATEMENT_DOCUMENTED), shared.keys)
        assertEquals(STATEMENT_PUBLISHED, Profiles.MWL.validationEn)
        assertEquals(STATEMENT_DOCUMENTED, Profiles.RUSSIA.validationEn)
    }
}
