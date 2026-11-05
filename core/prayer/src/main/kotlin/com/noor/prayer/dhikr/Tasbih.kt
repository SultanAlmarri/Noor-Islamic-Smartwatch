package com.noor.prayer.dhikr

import java.time.LocalDate

/** A phrase of remembrance, in Arabic with a transliteration and an English meaning. */
enum class Dhikr(val ar: String, val translit: String, val en: String) {
    SUBHANALLAH("سبحان الله", "SubhanAllah", "Glory be to Allah"),
    ALHAMDULILLAH("الحمد لله", "Alhamdulillah", "All praise is for Allah"),
    ALLAHU_AKBAR("الله أكبر", "Allahu Akbar", "Allah is the Greatest"),
    /** The words that complete the hundred after prayer (Sahih Muslim 597). */
    TAHLIL_FULL(
        "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير",
        "La ilaha illallahu wahdahu la sharika lah, lahul-mulku wa lahul-hamd, wa huwa 'ala kulli shay'in qadir",
        "None has the right to be worshipped but Allah alone, without partner; His is the dominion and His the praise, and He has power over all things",
    ),
    TAHLIL("لا إله إلا الله", "La ilaha illallah", "None has the right to be worshipped but Allah"),
    ISTIGHFAR("أستغفر الله", "Astaghfirullah", "I seek Allah's forgiveness"),
    SALAWAT("اللهم صل على محمد", "Allahumma salli 'ala Muhammad", "O Allah, send blessings upon Muhammad"),
    HAWQALA("لا حول ولا قوة إلا بالله", "La hawla wa la quwwata illa billah", "There is no power and no strength except with Allah"),
}

/** One stretch of a preset: a phrase and how many times to say it (0 = no target, count freely). */
data class DhikrStep(val dhikr: Dhikr?, val target: Int)

/** What the counter offers. [AFTER_PRAYER] walks through the 33 + 33 + 33 + 1 said after each prayer. */
enum class TasbihPreset(val steps: List<DhikrStep>) {
    AFTER_PRAYER(listOf(DhikrStep(Dhikr.SUBHANALLAH, 33), DhikrStep(Dhikr.ALHAMDULILLAH, 33), DhikrStep(Dhikr.ALLAHU_AKBAR, 33), DhikrStep(Dhikr.TAHLIL_FULL, 1))),
    SUBHANALLAH(listOf(DhikrStep(Dhikr.SUBHANALLAH, 33))),
    ALHAMDULILLAH(listOf(DhikrStep(Dhikr.ALHAMDULILLAH, 33))),
    ALLAHU_AKBAR(listOf(DhikrStep(Dhikr.ALLAHU_AKBAR, 33))),
    TAHLIL(listOf(DhikrStep(Dhikr.TAHLIL, 100))),
    ISTIGHFAR(listOf(DhikrStep(Dhikr.ISTIGHFAR, 100))),
    SALAWAT(listOf(DhikrStep(Dhikr.SALAWAT, 100))),
    HAWQALA(listOf(DhikrStep(Dhikr.HAWQALA, 100))),
    /** No phrase and no target: a plain counter with a gentle mark every 33. */
    FREE(listOf(DhikrStep(null, 0)));

    /** The sum of all targets in one round (0 for [FREE]). */
    val roundTotal: Int get() = steps.sumOf { it.target }
}

/** What a tap did, so the watch can answer with the right feel (a tick, a firmer pulse, a celebration). */
enum class TasbihEvent {
    /** One more, nothing else happened. */
    COUNT,
    /** A multiple of 33 in free counting. */
    MILESTONE,
    /** One phrase reached its target and the next phrase begins. */
    STEP_DONE,
    /** The whole preset is complete; the next tap starts a new round. */
    ROUND_DONE,
    /** Undo had nothing to take back. */
    NOTHING,
}

/**
 * The counter's state. Immutable: every action returns the new state and what happened. Counts survive app restarts
 * (see [encode]) and keep a per-day total that starts again at local midnight.
 */
data class TasbihState(
    val preset: TasbihPreset = TasbihPreset.AFTER_PRAYER,
    val stepIndex: Int = 0,
    val count: Int = 0,
    val rounds: Int = 0,
    val day: LocalDate? = null,
    val today: Int = 0,
    val lifetime: Long = 0,
) {
    val step: DhikrStep get() = preset.steps[stepIndex.coerceIn(0, preset.steps.lastIndex)]
    val target: Int get() = step.target

    /** Progress through the current phrase, 0..1 (free counting fills a ring every 33). */
    val stepProgress: Float get() = if (target > 0) count.toFloat() / target else (count % 33) / 33f

    /** Progress through the whole round, 0..1. */
    val roundProgress: Float get() {
        val total = preset.roundTotal
        if (total == 0) return stepProgress
        val before = preset.steps.take(stepIndex).sumOf { it.target }
        return (before + count).toFloat() / total
    }

    /** The day total as seen on [date] (yesterday's total is not today's). */
    fun todayOn(date: LocalDate): Int = if (day == date) today else 0

    fun increment(date: LocalDate): Pair<TasbihState, TasbihEvent> {
        val t = todayOn(date) + 1
        val base = copy(day = date, today = t, lifetime = lifetime + 1)
        if (target == 0) {
            val n = count + 1
            return base.copy(count = n) to if (n % 33 == 0) TasbihEvent.MILESTONE else TasbihEvent.COUNT
        }
        val n = count + 1
        if (n < target) return base.copy(count = n) to TasbihEvent.COUNT
        return if (stepIndex < preset.steps.lastIndex) {
            base.copy(stepIndex = stepIndex + 1, count = 0) to TasbihEvent.STEP_DONE
        } else {
            base.copy(stepIndex = 0, count = 0, rounds = rounds + 1) to TasbihEvent.ROUND_DONE
        }
    }

    /** Takes back the last count, across a phrase or round boundary if that is where the last count went. */
    fun undo(date: LocalDate): Pair<TasbihState, TasbihEvent> {
        val back = copy(day = date, today = maxOf(0, todayOn(date) - 1), lifetime = maxOf(0, lifetime - 1))
        return when {
            count > 0 -> back.copy(count = count - 1) to TasbihEvent.COUNT
            target == 0 -> this to TasbihEvent.NOTHING
            stepIndex > 0 -> back.copy(stepIndex = stepIndex - 1, count = preset.steps[stepIndex - 1].target - 1) to TasbihEvent.COUNT
            rounds > 0 -> back.copy(stepIndex = preset.steps.lastIndex, count = preset.steps.last().target - 1, rounds = rounds - 1) to TasbihEvent.COUNT
            else -> this to TasbihEvent.NOTHING
        }
    }

    /** Starts the current preset again (the day and lifetime totals stay). */
    fun reset(): TasbihState = copy(stepIndex = 0, count = 0, rounds = 0)

    fun choose(p: TasbihPreset): TasbihState = if (p == preset) this else copy(preset = p, stepIndex = 0, count = 0, rounds = 0)

    fun encode(): String = listOf(
        "p=${preset.name}", "s=$stepIndex", "c=$count", "r=$rounds", "d=${day ?: ""}", "t=$today", "l=$lifetime",
    ).joinToString(";")

    companion object {
        fun decode(text: String?): TasbihState {
            if (text.isNullOrBlank()) return TasbihState()
            val m = text.split(';').mapNotNull { kv -> kv.indexOf('=').takeIf { it > 0 }?.let { kv.substring(0, it) to kv.substring(it + 1) } }.toMap()
            val preset = TasbihPreset.entries.firstOrNull { it.name == m["p"] } ?: TasbihPreset.AFTER_PRAYER
            val step = (m["s"]?.toIntOrNull() ?: 0).coerceIn(0, preset.steps.lastIndex)
            val target = preset.steps[step].target
            val count = (m["c"]?.toIntOrNull() ?: 0).let { if (target > 0) it.coerceIn(0, target - 1) else maxOf(0, it) }
            return TasbihState(
                preset = preset, stepIndex = step, count = count,
                rounds = maxOf(0, m["r"]?.toIntOrNull() ?: 0),
                day = m["d"]?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                today = maxOf(0, m["t"]?.toIntOrNull() ?: 0),
                lifetime = maxOf(0L, m["l"]?.toLongOrNull() ?: 0L),
            )
        }
    }
}
