package com.noor.prayer

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Accuracy checks shared by the JUnit tests and the stand-alone verifier.
 * Reference data: research golden vectors (NREL SPA via pvlib) and official IACAD timetables.
 */
object GoldenChecks {
    private val order = listOf(Prayer.FAJR, Prayer.SHURUQ, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    fun resource(name: String): List<String> {
        val stream = GoldenChecks::class.java.classLoader.getResourceAsStream(name)
        val text = stream?.bufferedReader()?.readText() ?: File("src/test/resources/$name").readText()
        return text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
    }

    data class RawResult(val compared: Int, val maxAbsSeconds: Double, val worst: String, val minuteMismatches: Int, val ambiguous: Int = 0)

    /** Dubai C1 raw seconds vs the research reference, every day 2014-2030. */
    fun dubaiRaw(): RawResult {
        val rows = resource("golden_dubai_c1_raw_seconds.csv").drop(1)
        val zone = ZoneOffset.ofHours(4)
        var maxAbs = 0.0; var worst = ""; var n = 0; var mm = 0; var ambiguous = 0
        for (line in rows) {
            val c = line.split(',')
            val date = LocalDate.parse(c[0])
            val day = PrayerEngine.compute(date, GeoPoint(25.12, 55.189), zone, Profiles.DUBAI_IACAD)
            for ((i, p) in order.withIndex()) {
                val ref = c[i + 1].toDouble()
                val got = day.seconds.getValue(p)
                val d = abs(got - ref)
                n++
                if (d > maxAbs) { maxAbs = d; worst = "$date $p ref=$ref got=$got" }
                // the reference is stored to 0.01 s: within 0.02 s of a whole minute its rounding is undecidable
                val toBoundary = abs(ref - Math.round(ref / 60.0) * 60.0)
                if (toBoundary < 0.02) { ambiguous++; continue }
                val refMin = kotlin.math.ceil(ref / 60.0 - 1e-9).toInt() + Profiles.DUBAI_IACAD.offset(p)
                if (refMin != day.minutes[p]) mm++
            }
        }
        return RawResult(n, maxAbs, worst, mm, ambiguous)
    }

    /** Other places and angles, raw seconds without rounding, offsets or high-latitude rules. */
    fun genericRaw(): RawResult {
        val rows = resource("golden_generic_raw_seconds.csv").drop(1)
        var maxAbs = 0.0; var worst = ""; var n = 0
        for (line in rows) {
            val c = line.split(',')
            val profile = CalculationProfile("t", "t", "t", fajrAngle = c[3].toDouble(), ishaAngle = c[4].toDouble())
            val zone = ZoneOffset.ofHours(c[5].toInt())
            val date = LocalDate.parse(c[6])
            val day = PrayerEngine.compute(date, GeoPoint(c[1].toDouble(), c[2].toDouble()), zone, profile,
                UserAdjustments(highLatitudeRule = HighLatitudeRule.NONE))
            for ((i, p) in order.withIndex()) {
                if (c[7 + i] == "nan") continue      // reference solver failed on this day; nothing to compare
                val ref = c[7 + i].toDouble()
                val d = abs(day.seconds.getValue(p) - ref)
                n++
                if (!(d <= maxAbs)) { maxAbs = d; worst = "${c[0]} $date $p ref=$ref got=${day.seconds[p]}" }
            }
        }
        return RawResult(n, maxAbs, worst, 0)
    }

    data class OfficialResult(val compared: Int, val exact: Int, val perPrayer: Map<Prayer, Int>, val maxAbsMinutes: Int, val misses: List<String>)

    /** Dubai profile vs official IACAD times of the current convention era (from 20 Aug 2020). */
    fun iacadOfficial(): OfficialResult = compareDubai(
        resource("iacad_official_archive_2014_2023.csv").drop(1).map { it.split(',') } +
            resource("iacad_api_2026-11.iso.csv").drop(1).map { it.split(',') },
    )

    /** Dates read from the user's recording of the official Dulook DXB app (outside the Nov 2026 feed: Apr and Dec 2026). */
    fun dulookRecording(): OfficialResult {
        val feedDates = resource("iacad_api_2026-11.iso.csv").drop(1).map { it.substringBefore(',') }.toSet()
        return compareDubai(resource("dulook_dxb_recording_2026.csv").drop(1).map { it.split(',') }.filter { it[0] !in feedDates })
    }

    fun muisOfficial(): OfficialResult {
        val rows = resource("muis_singapore_2024_2026.csv").filter { it.isNotBlank() && it[0].isDigit() }.map { it.split(',') }
        val zone = ZoneId.of("Asia/Singapore")
        var n = 0; var exact = 0; var maxAbs = 0
        val per = order.associateWith { 0 }.toMutableMap()
        val misses = mutableListOf<String>()
        for (c in rows) {
            val day = PrayerEngine.compute(LocalDate.parse(c[0]), GeoPoint(1.3521, 103.8198), zone, Profiles.SINGAPORE)
            for ((i, p) in order.withIndex()) {
                val hm = c[i + 1].split(':'); val official = hm[0].toInt() * 60 + hm[1].toInt()
                val got = day.minutes[p]!!
                n++
                if (got == official) { exact++; per[p] = per.getValue(p) + 1 } else if (misses.size < 20) misses += "${c[0]} $p ${official - got}"
                maxAbs = maxOf(maxAbs, abs(got - official))
            }
        }
        return OfficialResult(n, exact, per, maxAbs, misses)
    }

    private fun compareDubai(rows: List<List<String>>): OfficialResult {
        val zone = ZoneId.of("Asia/Dubai")
        var n = 0; var exact = 0; var maxAbs = 0
        val per = order.associateWith { 0 }.toMutableMap()
        val misses = mutableListOf<String>()
        for (c in rows) {
            val date = LocalDate.parse(c[0])
            if (date < LocalDate.of(2020, 8, 20)) continue
            val day = PrayerEngine.compute(date, GeoPoint(25.2, 55.27), zone, Profiles.DUBAI_IACAD)
            for ((i, p) in order.withIndex()) {
                val hm = c[i + 1].split(':'); val official = hm[0].toInt() * 60 + hm[1].toInt()
                val got = day.minutes[p]!!
                n++
                if (got == official) { exact++; per[p] = per.getValue(p) + 1 } else misses += "$date $p official=${c[i + 1]} engine=${"%02d:%02d".format(got / 60, got % 60)} diff=${official - got}"
                maxAbs = maxOf(maxAbs, abs(got - official))
            }
        }
        return OfficialResult(n, exact, per, maxAbs, misses)
    }
}
