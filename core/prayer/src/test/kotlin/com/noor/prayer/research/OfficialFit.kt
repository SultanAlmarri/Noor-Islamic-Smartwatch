package com.noor.prayer.research

import com.noor.prayer.CalculationProfile
import com.noor.prayer.GeoPoint
import com.noor.prayer.Prayer
import com.noor.prayer.PrayerEngine
import com.noor.prayer.UserAdjustments
import com.noor.prayer.astro.SolarPosition
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test

/**
 * Research tool, not a regular test: infers an authority's conventions from its official timetable the same way the
 * Dubai profile was derived (no date-specific adjustments; one constant offset per prayer; held-out year).
 * Run with: ./gradlew :core:prayer:test --tests '*OfficialFit*' -Pnoor.research=true
 */
class OfficialFit {
    data class Row(val date: LocalDate, val minutes: List<Int>)

    private fun load(name: String): List<Row> = javaClass.classLoader.getResource(name)!!.readText().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("date") }
        .map { l -> l.split(',').let { c -> Row(LocalDate.parse(c[0]), c.drop(1).take(6).map { t -> t.split(':').let { it[0].toInt() * 60 + it[1].toInt() } }) } }

    enum class R { UP, NEAREST, DOWN }

    private fun round(sec: Double, mode: R) = when (mode) {
        R.UP -> ceil(sec / 60.0 - 1e-9).toInt()
        R.NEAREST -> floor(sec / 60.0 + 0.5).toInt()
        R.DOWN -> floor(sec / 60.0 + 1e-9).toInt()
    }

    /** Best constant offset and its exact-match count for one series. */
    private fun bestOffset(raw: List<Double>, official: List<Int>, mode: R): Pair<Int, Int> =
        (-4..8).map { off -> off to raw.indices.count { i -> !raw[i].isNaN() && round(raw[i], mode) + off == official[i] } }.maxBy { it.second }

    @Test fun muisSingapore() {
        assumeTrue(System.getProperty("noor.research") == "true")
        val rows = load("muis_singapore_2024_2026.csv")
        val train = rows.filter { it.date.year < 2026 }; val test = rows.filter { it.date.year == 2026 }
        val zone = ZoneId.of("Asia/Singapore")
        val points = buildList {
            for (lat in listOf(1.25, 1.29, 1.33)) for (lon in listOf(103.70, 103.80, 103.85, 103.90)) add(GeoPoint(lat, lon))
        }
        data class Cand(val score: Int, val desc: String)
        val results = mutableListOf<Cand>()
        for (pt in points) {
            fun series(profile: CalculationProfile, rs: List<Row>, p: Prayer): List<Double> = rs.map { r ->
                val offset = zone.rules.getOffset(r.date.atTime(12, 0))
                val mid = r.date.atStartOfDay().toEpochSecond(offset).toDouble()
                PrayerEngine.rawEvents(r.date, mid, pt, profile, UserAdjustments(), SolarPosition.deltaT(r.date.year), false).getValue(p)
            }
            val base = CalculationProfile("x", "", "", 20.0, 18.0)
            var total = 0; val parts = mutableListOf<String>(); var testExact = 0
            for ((i, p) in Prayer.entries.withIndex()) {
                val variants: List<Pair<String, CalculationProfile>> = when (p) {
                    Prayer.FAJR -> listOf(19.5, 20.0, 20.5).map { "fajr=$it" to base.copy(fajrAngle = it) }
                    Prayer.ISHA -> listOf(17.5, 18.0, 18.5, 19.0).map { "isha=$it" to base.copy(ishaAngle = it) }
                    Prayer.SHURUQ, Prayer.MAGHRIB -> listOf(0.0, 0.1, 0.2, 0.3).map { "hz=$it" to base.copy(horizonExtraDeg = it) }
                    else -> listOf("-" to base)
                }
                var best: Triple<String, Int, Pair<R, Int>>? = null
                for ((label, prof) in variants) {
                    val raw = series(prof, train, p)
                    for (mode in R.entries) {
                        val (off, n) = bestOffset(raw, train.map { it.minutes[i] }, mode)
                        if (best == null || n > best.second) best = Triple(label, n, mode to off)
                    }
                }
                val b = best!!
                total += b.second
                val prof = variants.first { it.first == b.first }.second
                val rawTest = series(prof, test, p)
                testExact += rawTest.indices.count { k -> round(rawTest[k], b.third.first) + b.third.second == test[k].minutes[i] }
                val rawAll = series(prof, rows, p)
                val hist = rows.indices.map { k -> rows[k].minutes[i] - (round(rawAll[k], b.third.first) + b.third.second) }.groupingBy { it }.eachCount().toSortedMap()
                // fractional part of the raw time for misses vs hits: shows whether a different rounding rule would help
                parts += "$p ${b.first} ${b.third.first} off=${b.third.second} ${b.second}/${train.size} resid=$hist"
            }
            results += Cand(total, "pt=${pt.latitude},${pt.longitude} train=$total/${train.size * 6} test2026=$testExact/${test.size * 6} | ${parts.joinToString("; ")}")
        }
        results.sortedByDescending { it.score }.take(8).forEach { println(it.desc) }
    }
}
