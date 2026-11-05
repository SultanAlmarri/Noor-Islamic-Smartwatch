package com.noor.prayer

import com.noor.prayer.dhikr.Dhikr
import com.noor.prayer.dhikr.TasbihEvent
import com.noor.prayer.dhikr.TasbihPreset
import com.noor.prayer.dhikr.TasbihState
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TasbihTest {
    private val date = LocalDate.of(2026, 11, 5)

    private fun TasbihState.tap(n: Int, d: LocalDate = date): Pair<TasbihState, List<TasbihEvent>> {
        var s = this; val events = ArrayList<TasbihEvent>()
        repeat(n) { val (next, e) = s.increment(d); s = next; events += e }
        return s to events
    }

    @Test fun afterPrayerWalksThroughThirtyThreeThreeTimesThenCompletesTheHundred() {
        val (s, events) = TasbihState().tap(100)
        // The phrase changes after 33, 66 and 99; the hundredth completes the round.
        assertEquals(listOf(32, 65, 98), events.withIndex().filter { it.value == TasbihEvent.STEP_DONE }.map { it.index })
        assertEquals(TasbihEvent.ROUND_DONE, events.last())
        assertEquals(1, events.count { it == TasbihEvent.ROUND_DONE })
        assertEquals(1, s.rounds); assertEquals(0, s.stepIndex); assertEquals(0, s.count)
        assertEquals(100, s.todayOn(date)); assertEquals(100L, s.lifetime)
        assertEquals(TasbihPreset.AFTER_PRAYER.roundTotal, 100)
    }

    @Test fun stepsShowTheRightPhraseAndProgress() {
        val (s, _) = TasbihState().tap(40)
        assertEquals(Dhikr.ALHAMDULILLAH, s.step.dhikr)
        assertEquals(7, s.count)
        assertEquals(40 / 100f, s.roundProgress, 1e-6f)
        assertEquals(7 / 33f, s.stepProgress, 1e-6f)
    }

    @Test fun undoStepsBackAcrossPhraseAndRoundBoundaries() {
        val (atStep, _) = TasbihState().tap(33)
        assertEquals(1, atStep.stepIndex)
        val (back, e) = atStep.undo(date)
        assertEquals(TasbihEvent.COUNT, e)
        assertEquals(0, back.stepIndex); assertEquals(32, back.count); assertEquals(32, back.todayOn(date))

        val (afterRound, _) = TasbihState().tap(100)
        val (undone, _) = afterRound.undo(date)
        assertEquals(0, undone.rounds); assertEquals(3, undone.stepIndex); assertEquals(0, undone.count)
        assertEquals(99, undone.todayOn(date))

        assertEquals(TasbihEvent.NOTHING, TasbihState().undo(date).second)
    }

    @Test fun freeCountingMarksEveryThirtyThree() {
        val (s, events) = TasbihState().choose(TasbihPreset.FREE).tap(70)
        assertEquals(listOf(32, 65), events.withIndex().filter { it.value == TasbihEvent.MILESTONE }.map { it.index })
        assertEquals(70, s.count)
        assertEquals(TasbihEvent.NOTHING, TasbihState().choose(TasbihPreset.FREE).undo(date).second)
    }

    @Test fun singlePhraseRoundsRepeat() {
        val (s, events) = TasbihState().choose(TasbihPreset.ISTIGHFAR).tap(250)
        assertEquals(2, events.count { it == TasbihEvent.ROUND_DONE })
        assertEquals(2, s.rounds); assertEquals(50, s.count)
    }

    @Test fun theDayTotalStartsAgainTomorrowButTheLifetimeTotalStays() {
        val (s, _) = TasbihState().tap(10)
        val tomorrow = date.plusDays(1)
        assertEquals(0, s.todayOn(tomorrow))
        val (next, _) = s.tap(3, tomorrow)
        assertEquals(3, next.todayOn(tomorrow)); assertEquals(13L, next.lifetime)
    }

    @Test fun choosingAnotherPresetStartsItFreshAndKeepsTotals() {
        val (s, _) = TasbihState().tap(12)
        val c = s.choose(TasbihPreset.SALAWAT)
        assertEquals(0, c.count); assertEquals(0, c.stepIndex); assertEquals(12, c.todayOn(date))
        assertEquals(s, s.choose(TasbihPreset.AFTER_PRAYER))
        assertEquals(0, s.reset().count); assertEquals(12, s.reset().todayOn(date))
    }

    @Test fun savedStateRoundTripsAndDamagedTextIsSafe() {
        val (s, _) = TasbihState().choose(TasbihPreset.TAHLIL).tap(57)
        assertEquals(s, TasbihState.decode(s.encode()))
        assertEquals(TasbihState(), TasbihState.decode(null))
        assertEquals(TasbihState(), TasbihState.decode("garbage"))
        // A count beyond the target (from a damaged file) is pulled back inside it.
        assertEquals(32, TasbihState.decode("p=SUBHANALLAH;s=9;c=500;d=nope;t=-4")?.count)
        assertEquals(null, TasbihState.decode("p=SUBHANALLAH;s=9;c=500;d=nope;t=-4").day)
    }
}
