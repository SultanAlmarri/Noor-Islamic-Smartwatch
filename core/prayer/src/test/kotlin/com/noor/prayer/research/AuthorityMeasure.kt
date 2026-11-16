package com.noor.prayer.research

import com.noor.prayer.CalculationProfile
import com.noor.prayer.GeoPoint
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.PrayerEngine
import com.noor.prayer.Profiles
import com.noor.prayer.UserAdjustments
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test

/**
 * Research tool: the app's own profiles (as the app computes them: PrayerEngine.compute with the default user settings)
 * against each authority's published year (core/prayer/src/test/resources/authorities). Writes
 * accuracy/results/authorities_measurements_table.md, which accuracy/results/authorities_measurements.md quotes.
 * Run: ./gradlew :core:prayer:test --tests '*AuthorityMeasure*' -Pnoor.research=true
 */
class AuthorityMeasure {
    data class Row(val date: LocalDate, val minutes: List<Int>)
    data class Case(val file: String, val label: String, val point: GeoPoint, val zone: String, val profile: CalculationProfile)

    private fun load(name: String): List<Row> = javaClass.classLoader.getResource("authorities/$name.csv")!!.readText().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("date") }
        .map { l -> l.split(',').let { c -> Row(LocalDate.parse(c[0]), c.drop(1).take(6).map { t -> t.split(':').let { it[0].toInt() * 60 + it[1].toInt() } }) } }

    /** Published minus computed, per day and prayer. */
    fun diffs(c: Case): List<Pair<LocalDate, List<Int>>> = load(c.file).map { r ->
        val d = PrayerEngine.compute(r.date, c.point, ZoneId.of(c.zone), c.profile, UserAdjustments(), HijriDate.of(r.date).isRamadan)
        r.date to Prayer.entries.mapIndexed { i, p -> r.minutes[i] - d.minutes[p]!! }
    }

    private fun line(c: Case): String {
        val all = diffs(c)
        val flat = all.flatMap { it.second }
        val per = Prayer.entries.mapIndexed { i, _ -> all.count { it.second[i] == 0 } }
        val months = (1..12).map { m -> all.filter { it.first.monthValue == m }.flatMap { it.second }.let { v -> if (v.isEmpty()) "-" else "%.1f".format(v.average()) } }
        return "| ${c.label} | ${c.profile.id} | ${all.size} days | **${flat.count { it == 0 }}/${flat.size}** (${"%.0f".format(100.0 * flat.count { it == 0 } / flat.size)}%) | " +
            "${flat.count { abs(it) <= 1 }}/${flat.size} | ${flat.count { abs(it) <= 2 }}/${flat.size} | ${flat.maxOf { abs(it) }} | " +
            per.joinToString(" / ") + " |"
    }

    @Test fun measureAll() {
        assumeTrue(System.getProperty("noor.research") == "true")
        val cases = CASES
        val out = StringBuilder()
        out.append("| Official table | App profile | Days | Exact | Within 1 min | Within 2 min | Max diff (min) | Exact per prayer (Fajr / Shuruq / Dhuhr / Asr / Maghrib / Isha) |\n")
        out.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n")
        for (c in cases) out.append(line(c)).append('\n')
        val f = File("../../accuracy/results/authorities_measurements_table.md")
        f.writeText(out.toString())
        println(out)
        for (c in cases) {
            val all = diffs(c)
            println(c.label + " mean diff by month (all prayers): " + (1..12).joinToString(" ") { m -> "%.1f".format(all.filter { it.first.monthValue == m }.flatMap { it.second }.average()) })
            println("   by prayer, mean: " + Prayer.entries.mapIndexed { i, p -> p.en + " " + "%.2f".format(all.map { it.second[i] }.average()) }.joinToString(", "))
            for ((i, p) in Prayer.entries.withIndex()) println("   ${p.en} by month: " + (1..12).joinToString(" ") { m -> "%.1f".format(all.filter { it.first.monthValue == m }.map { it.second[i] }.average()) })
        }
    }

    /** Candidate conventions (before they become profiles), scored the same way. */
    @Test fun candidates() {
        assumeTrue(System.getProperty("noor.research") == "true")
        fun prof(f: Double, i: Double, r: com.noor.prayer.Rounding, o: Map<Prayer, Int>, hz: Double = 0.0) =
            CalculationProfile("cand", "", "", f, i, rounding = r, offsets = o, horizonExtraDeg = hz)
        val up = com.noor.prayer.Rounding.UP; val nr = com.noor.prayer.Rounding.NEAREST
        val jakim = mapOf(
            "J1 up +1 -1 +2 +1 +1 +1" to prof(18.0, 18.0, up, mapOf(Prayer.FAJR to 1, Prayer.SHURUQ to -1, Prayer.DHUHR to 2, Prayer.ASR to 1, Prayer.MAGHRIB to 1, Prayer.ISHA to 1)),
            "J2 up +1 0 +2 +1 +1 +1" to prof(18.0, 18.0, up, mapOf(Prayer.FAJR to 1, Prayer.SHURUQ to 0, Prayer.DHUHR to 2, Prayer.ASR to 1, Prayer.MAGHRIB to 1, Prayer.ISHA to 1)),
            "J3 nr +2 -1 +3 +2 +2 +2" to prof(18.0, 18.0, nr, mapOf(Prayer.FAJR to 2, Prayer.SHURUQ to -1, Prayer.DHUHR to 3, Prayer.ASR to 2, Prayer.MAGHRIB to 2, Prayer.ISHA to 2)),
        )
        val kemenag = mapOf(
            "K1 up +2 -2 +2 +2 +2 +2 (documented)" to prof(20.0, 18.0, up, mapOf(Prayer.FAJR to 2, Prayer.SHURUQ to -2, Prayer.DHUHR to 2, Prayer.ASR to 2, Prayer.MAGHRIB to 2, Prayer.ISHA to 2)),
            "K2 up +2 -2 +3 +2 +2 +2, horizon -1 deg" to prof(20.0, 18.0, up, mapOf(Prayer.FAJR to 2, Prayer.SHURUQ to -2, Prayer.DHUHR to 3, Prayer.ASR to 2, Prayer.MAGHRIB to 2, Prayer.ISHA to 2), hz = 1.0 - 0.8333),
            "K4 up +2 -3 +3 +2 +2 +2, horizon -1 deg" to prof(20.0, 18.0, up, mapOf(Prayer.FAJR to 2, Prayer.SHURUQ to -3, Prayer.DHUHR to 3, Prayer.ASR to 2, Prayer.MAGHRIB to 2, Prayer.ISHA to 2), hz = 1.0 - 0.8333),
            "K3 up +2 -3 +3 +2 +3 +2" to prof(20.0, 18.0, up, mapOf(Prayer.FAJR to 2, Prayer.SHURUQ to -3, Prayer.DHUHR to 3, Prayer.ASR to 2, Prayer.MAGHRIB to 3, Prayer.ISHA to 2)),
        )
        val bosnia = prof(18.0, 16.0, nr, mapOf(Prayer.SHURUQ to -6, Prayer.DHUHR to 1, Prayer.MAGHRIB to 6))
        for (c in CASES.filter { it.file.startsWith("iz_bih") }) println("B1 18/16 -6 +1 0 +6 | " + line(c.copy(profile = bosnia)))
        for (t in listOf(3, 5)) {
            val dumk = prof(15.0, 15.0, nr, mapOf(Prayer.SHURUQ to -t, Prayer.DHUHR to t, Prayer.ASR to t, Prayer.MAGHRIB to t)).copy(asrSchool = com.noor.prayer.AsrSchool.HANAFI)
            for (c in DUMK_CASES) println("D temkin $t | " + line(c.copy(profile = dumk)))
        }
        for ((label, p) in jakim) for (c in CASES.filter { it.file.startsWith("jakim") }) println("$label | " + line(c.copy(profile = p)))
        for ((label, p) in kemenag) for (c in CASES.filter { it.file.startsWith("kemenag") }) println("$label | " + line(c.copy(profile = p)))
    }

    companion object {
        private fun kz(file: String, lat: Double, lon: Double) = Case("dumk_$file", "DUMK, ${file.replaceFirstChar { it.uppercase() }} 2026", GeoPoint(lat, lon), "+05:00", Profiles.MWL)
        val DUMK_CASES = listOf(
            kz("shymkent", 42.368009, 69.612769), kz("almaty", 43.238293, 76.945465), kz("aktau", 43.635379, 51.169135),
            kz("kyzylorda", 44.842544, 65.502563), kz("taldykorgan", 45.017837, 78.382123), kz("atyrau", 47.116667, 51.883333),
            kz("zhezkazgan", 47.799711, 67.714090), kz("ayagoz", 47.966667, 80.433333), kz("karkaraly", 49.412240, 75.470095),
            kz("karaganda", 49.806406, 73.085485), kz("aktobe", 50.300377, 57.154555), kz("astana", 51.133333, 71.433333),
            kz("pavlodar", 52.315556, 76.956389),
        )
        val CASES = listOf(
            Case("diyanet_istanbul", "Diyanet, Istanbul 2026", GeoPoint(41.0082, 28.9784), "Europe/Istanbul", Profiles.TURKIYE),
            Case("jakim_wly01", "JAKIM e-Solat WLY01 (Kuala Lumpur) 2026", GeoPoint(3.139, 101.6869), "Asia/Kuala_Lumpur", Profiles.MALAYSIA),
            Case("jakim_jhr02", "JAKIM e-Solat JHR02 (Johor Bahru) 2026", GeoPoint(1.4927, 103.7414), "Asia/Kuala_Lumpur", Profiles.MALAYSIA),
            Case("jakim_png01", "JAKIM e-Solat PNG01 (Penang) 2026", GeoPoint(5.4141, 100.3288), "Asia/Kuala_Lumpur", Profiles.MALAYSIA),
            Case("jakim_ktn01", "JAKIM e-Solat KTN01 (Kota Bharu) 2026", GeoPoint(6.1254, 102.2381), "Asia/Kuala_Lumpur", Profiles.MALAYSIA),
            Case("kemenag_jakarta", "Kemenag, Jakarta 2026 (via api.myquran.com)", GeoPoint(-6.2088, 106.8456), "Asia/Jakarta", Profiles.INDONESIA),
            Case("dumk_almaty", "DUMK, Almaty 2026", GeoPoint(43.238293, 76.945465), "+05:00", Profiles.MWL),
            Case("dumk_astana", "DUMK, Astana 2026", GeoPoint(51.133333, 71.433333), "+05:00", Profiles.MWL),
            Case("iz_bih_sarajevo", "Islamic Community of BiH (Takvim), Sarajevo 2026", GeoPoint(43.8563, 18.4131), "Europe/Sarajevo", Profiles.MWL),
        )
    }
}
