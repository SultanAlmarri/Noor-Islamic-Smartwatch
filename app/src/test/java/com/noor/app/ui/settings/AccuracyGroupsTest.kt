package com.noor.app.ui.settings

import com.noor.prayer.Profiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.25: the accuracy page may say no more and no less than each profile's own statement. Second review: every method
 * is one row that opens to that statement, under a phrase derived from it, and at the top the figures measured for the
 * method in use. So: every method is shown exactly once; a phrase that several methods share is given only to methods
 * whose statement is, word for word and in both languages, the same; each phrase is the one its statement earns; and
 * every figure at the top stands in that method's own statement, in both languages, and only methods measured against
 * the authority's own times have figures.
 */
class AccuracyGroupsTest {
    @Test fun everyMethodIsShownExactlyOnce() {
        val shown = accuracyRows().map { it.id }
        assertEquals(Profiles.ALL.map { it.id }.sorted(), shown.sorted())
        assertEquals("a method is shown twice", shown.size, shown.toSet().size)
        // Every row opens to the profile's own statement (Dubai's: the page's paragraph, which has the same figures).
        for (p in accuracyRows()) if (p.id != Profiles.DUBAI_IACAD.id) {
            assertEquals(p.validationEn, accuracyStatement(p, false)); assertEquals(p.validationAr, accuracyStatement(p, true))
        }
        assertEquals(IACAD_PARAGRAPH_EN, accuracyStatement(Profiles.DUBAI_IACAD, false))
        for (figure in listOf("588", "527", "89.6%", "1 minute")) assertTrue(figure, figure in IACAD_PARAGRAPH_EN)
        assertTrue("89.6%" in Profiles.DUBAI_IACAD.validationEn && "588" in Profiles.DUBAI_IACAD.validationEn)
    }

    @Test fun aSharedPhraseIsGivenOnlyToTheSameStatement() {
        val shared = Profiles.ALL.drop(2).groupBy { it.validationEn }.filter { it.value.size > 1 }
        // Today two statements are shared: the methods' published parameters, and parameters from references.
        assertEquals(setOf(STATEMENT_PUBLISHED, STATEMENT_DOCUMENTED), shared.keys)
        assertEquals(STATEMENT_PUBLISHED, Profiles.MWL.validationEn)
        assertEquals(STATEMENT_DOCUMENTED, Profiles.RUSSIA.validationEn)
        for ((statement, members) in shared) for (p in members) {
            assertEquals("${p.id}: the Arabic statement differs within a group", members.first().validationAr, p.validationAr)
            assertEquals(p.id, if (statement == STATEMENT_PUBLISHED) AccuracyStatus.PUBLISHED else AccuracyStatus.DOCUMENTED, accuracyStatus(p))
        }
    }

    /** The phrase of every method, as its statement earns it today: a changed statement must be looked at again. */
    @Test fun eachPhraseIsTheOneItsStatementEarns() {
        val expected = mapOf(
            Profiles.DUBAI_IACAD.id to AccuracyStatus.CHECKED, "uae_awqaf" to AccuracyStatus.CHECKED, "sgp" to AccuracyStatus.OFFICIAL,
            "jakim" to AccuracyStatus.CHECKED, "kemenag" to AccuracyStatus.CHECKED, Profiles.TURKIYE.id to AccuracyStatus.CHECKED,
            "dumk" to AccuracyStatus.CHECKED, "iz_bih" to AccuracyStatus.CHECKED,
            "msc" to AccuracyStatus.RECOGNISED, "kar_hanafi" to AccuracyStatus.RECOGNISED,
            "jafari" to AccuracyStatus.PUBLISHED, "uoif" to AccuracyStatus.PUBLISHED, "brn" to AccuracyStatus.DOCUMENTED,
        )
        for (p in Profiles.ALL) {
            val s = accuracyStatus(p)
            expected[p.id]?.let { assertEquals(p.id, it, s) }
            // And what each phrase says is in the statement itself.
            when (s) {
                AccuracyStatus.CHECKED -> assertTrue(p.id, '%' in p.validationEn && ("Against" in p.validationEn || "against" in p.validationEn || "Matches" in p.validationEn || "inferred from" in p.validationEn))
                AccuracyStatus.OFFICIAL -> assertTrue(p.id, p.validationEn.startsWith("Official"))
                AccuracyStatus.RECOGNISED -> assertTrue(p.id, "recognised method" in p.validationEn.lowercase() && "not an official timetable" in p.validationEn)
                AccuracyStatus.DOCUMENTED -> assertTrue(p.id, "not yet checked" in p.validationEn || "not checked" in p.validationEn)
                AccuracyStatus.PUBLISHED -> assertTrue(p.id, '%' !in p.validationEn && ("published" in p.validationEn.lowercase() || "method" in p.validationEn))
            }
        }
        // Profiles that the table above does not name are the shared ones, checked in the test before.
        val unnamed = Profiles.ALL.filter { it.id !in expected }
        assertTrue(unnamed.all { it.validationEn == STATEMENT_PUBLISHED || it.validationEn == STATEMENT_DOCUMENTED })
    }

    @Test fun theFiguresAtTheTopAreTheStatementsOwn() {
        for ((id, m) in MEASURED) {
            val p = Profiles.byId(id)!!
            assertEquals(id, AccuracyStatus.CHECKED, accuracyStatus(p))
            for (text in listOf(p.validationEn, p.validationAr)) {
                assertTrue("$id: ${m.toTheMinute} is not in «$text»", m.toTheMinute in text)
                when (val s = m.second) {
                    is SecondFigure.WithinOneMinute -> assertTrue("$id: ${s.percent}", s.percent in text)
                    is SecondFigure.LargestMinutes -> Unit
                }
            }
            val en = p.validationEn
            when (val s = m.second) {
                is SecondFigure.WithinOneMinute -> assertTrue(id, "${s.percent} within 1 minute" in en)
                is SecondFigure.LargestMinutes -> {
                    val n = s.minutes
                    val says = listOf("the rest differ by $n minute", "the rest by $n minute", "all within $n", "every time within $n minute").any { it in en }
                    assertTrue("$id: «$en» does not say that every time is within $n minute(s)", says)
                }
            }
        }
        // Only measured methods have figures; Singapore's uses the official table itself, and its other dates' figure
        // is an approximate one ("about 75%"), so it has none at the top.
        assertEquals(setOf(Profiles.DUBAI_IACAD.id, "uae_awqaf", Profiles.TURKIYE.id, "jakim", "kemenag", "dumk", "iz_bih"), MEASURED.keys)
    }
}
