package com.noor.prayer.research

import com.noor.prayer.AsrSchool
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
 * Research tool, not a regular test: what conventions reproduce an authority's own published table
 * (core/prayer/src/test/resources/authorities, fetched by accuracy/tools/fetch_authority_tables.py). For each prayer
 * separately: the angle (Fajr, Isha), the Asr shadow, the rounding and ONE constant number of minutes for the whole
 * year, chosen on January–June and scored on July–December (held out). No date-specific adjustment.
 * Run: ./gradlew :core:prayer:test --tests '*AuthorityFit*' -Pnoor.research=true -i
 */
class AuthorityFit {
    data class Row(val date: LocalDate, val minutes: List<Int>)
    data class DataSet(val file: String, val point: GeoPoint, val zone: String, val fixed: CalculationProfile? = null)

    private fun load(name: String): List<Row> = javaClass.classLoader.getResource("authorities/$name.csv")!!.readText().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("date") }
        .map { l -> l.split(',').let { c -> Row(LocalDate.parse(c[0]), c.drop(1).take(6).map { t -> t.split(':').let { it[0].toInt() * 60 + it[1].toInt() } }) } }

    enum class R { UP, NEAREST, DOWN }

    private fun round(sec: Double, mode: R) = when (mode) {
        R.UP -> ceil(sec / 60.0 - 1e-9).toInt()
        R.NEAREST -> floor(sec / 60.0 + 0.5).toInt()
        R.DOWN -> floor(sec / 60.0 + 1e-9).toInt()
    }

    private fun raw(s: DataSet, rows: List<Row>, profile: CalculationProfile, p: Prayer, asr: AsrSchool): List<Double> {
        val zone = ZoneId.of(s.zone)
        return rows.map { r ->
            val offset = zone.rules.getOffset(r.date.atTime(12, 0))
            val mid = r.date.atStartOfDay().toEpochSecond(offset).toDouble()
            PrayerEngine.rawEvents(r.date, mid, s.point, profile, UserAdjustments(asrSchool = asr, highLatitudeRule = com.noor.prayer.HighLatitudeRule.NONE),
                SolarPosition.deltaT(r.date.year), false).getValue(p)
        }
    }

    private fun fit(s: DataSet) {
        val rows = load(s.file)
        val train = rows.filter { it.date.monthValue <= 6 }; val test = rows.filter { it.date.monthValue > 6 }
        val base = CalculationProfile("x", "", "", 18.0, 18.0)
        val out = StringBuilder("${s.file} at ${s.point.latitude},${s.point.longitude}:")
        var trainTotal = 0; var testTotal = 0; var within1 = 0
        for ((i, p) in Prayer.entries.withIndex()) {
            val fixed = s.fixed
            val variants: List<Triple<String, CalculationProfile, AsrSchool>> = if (fixed != null) listOf(Triple("fixed", fixed, fixed.asrSchool)) else when (p) {
                Prayer.FAJR -> (24..44).map { it * 0.5 }.map { Triple("fajr=$it", base.copy(fajrAngle = it), AsrSchool.STANDARD) }
                Prayer.ISHA -> (24..40).map { it * 0.5 }.map { Triple("isha=$it", base.copy(ishaAngle = it), AsrSchool.STANDARD) }
                Prayer.ASR -> AsrSchool.entries.map { Triple("asr=$it", base, it) }
                Prayer.SHURUQ, Prayer.MAGHRIB -> listOf(0.0, 0.1, 0.2, 0.3, 0.5, 0.7, 0.9, 1.0, 1.1, 1.2, 1.4, 1.6).map { Triple("hz=$it", base.copy(horizonExtraDeg = it), AsrSchool.STANDARD) }
                Prayer.DHUHR -> listOf(Triple("transit", base, AsrSchool.STANDARD))
            }
            data class Best(val label: String, val prof: CalculationProfile, val asr: AsrSchool, val mode: R, val off: Int, val n: Int)
            var best: Best? = null
            for ((label, prof, asr) in variants) {
                val r = raw(s, train, prof, p, asr)
                for (mode in R.entries) for (off in -6..15) {
                    val n = r.indices.count { k -> !r[k].isNaN() && round(r[k], mode) + off == train[k].minutes[i] }
                    if (best == null || n > best.n) best = Best(label, prof, asr, mode, off, n)
                }
            }
            val b = best!!
            val rt = raw(s, test, b.prof, p, b.asr)
            val exact = rt.indices.count { k -> !rt[k].isNaN() && round(rt[k], b.mode) + b.off == test[k].minutes[i] }
            val near = rt.indices.count { k -> !rt[k].isNaN() && kotlin.math.abs(round(rt[k], b.mode) + b.off - test[k].minutes[i]) <= 1 }
            trainTotal += b.n; testTotal += exact; within1 += near
            out.append("\n  $p ${b.label} ${b.mode} ${if (b.off >= 0) "+" else ""}${b.off} min: train ${b.n}/${train.size}, held-out exact $exact/${test.size}, within 1 min $near/${test.size}")
        }
        out.append("\n  TOTAL train $trainTotal/${train.size * 6}, held-out exact $testTotal/${test.size * 6}, within 1 min $within1/${test.size * 6}")
        println(out)
    }

    private val DUMK = CalculationProfile("d", "", "", 15.0, 15.0, asrSchool = AsrSchool.HANAFI)
    private val JAKIM18 = CalculationProfile("j", "", "", 18.0, 18.0)

    @Test fun fitAll() {
        assumeTrue(System.getProperty("noor.research") == "true")
        listOf(
            DataSet("jakim_wly01", GeoPoint(3.139, 101.6869), "Asia/Kuala_Lumpur"),
            DataSet("jakim_jhr02", GeoPoint(1.4927, 103.7414), "Asia/Kuala_Lumpur"),
            DataSet("jakim_png01", GeoPoint(5.4141, 100.3288), "Asia/Kuala_Lumpur"),
            DataSet("jakim_ktn01", GeoPoint(6.1254, 102.2381), "Asia/Kuala_Lumpur"),
            DataSet("kemenag_jakarta", GeoPoint(-6.2088, 106.8456), "Asia/Jakarta"),
            DataSet("dumk_almaty", GeoPoint(43.238293, 76.945465), "+05:00"),
            DataSet("dumk_astana", GeoPoint(51.133333, 71.433333), "+05:00"),
            // the same angles everywhere: only the minutes and the rounding are fitted
            DataSet("dumk_almaty", GeoPoint(43.238293, 76.945465), "+05:00", DUMK),
            DataSet("dumk_astana", GeoPoint(51.133333, 71.433333), "+05:00", DUMK),
            DataSet("dumk_shymkent", GeoPoint(42.368009, 69.612769), "+05:00", DUMK),
            DataSet("dumk_aktobe", GeoPoint(50.300377, 57.154555), "+05:00", DUMK),
            DataSet("dumk_pavlodar", GeoPoint(52.315556, 76.956389), "+05:00", DUMK),
            DataSet("dumk_aktau", GeoPoint(43.635379, 51.169135), "+05:00", DUMK),
            DataSet("dumk_kyzylorda", GeoPoint(44.842544, 65.502563), "+05:00", DUMK),
            DataSet("dumk_taldykorgan", GeoPoint(45.017837, 78.382123), "+05:00", DUMK),
            DataSet("dumk_atyrau", GeoPoint(47.116667, 51.883333), "+05:00", DUMK),
            DataSet("dumk_zhezkazgan", GeoPoint(47.799711, 67.714090), "+05:00", DUMK),
            DataSet("dumk_karaganda", GeoPoint(49.806406, 73.085485), "+05:00", DUMK),
            DataSet("dumk_karkaraly", GeoPoint(49.412240, 75.470095), "+05:00", DUMK),
            DataSet("dumk_ayagoz", GeoPoint(47.966667, 80.433333), "+05:00", DUMK),
            DataSet("jakim_wly01", GeoPoint(3.139, 101.6869), "Asia/Kuala_Lumpur", JAKIM18),
            DataSet("jakim_jhr02", GeoPoint(1.4927, 103.7414), "Asia/Kuala_Lumpur", JAKIM18),
            DataSet("jakim_png01", GeoPoint(5.4141, 100.3288), "Asia/Kuala_Lumpur", JAKIM18),
            DataSet("jakim_ktn01", GeoPoint(6.1254, 102.2381), "Asia/Kuala_Lumpur", JAKIM18),
            DataSet("kemenag_jakarta", GeoPoint(-6.2088, 106.8456), "Asia/Jakarta", CalculationProfile("k", "", "", 20.0, 18.0)),
            DataSet("iz_bih_sarajevo", GeoPoint(43.8563, 18.4131), "Europe/Sarajevo"),
            DataSet("diyanet_istanbul", GeoPoint(41.0082, 28.9784), "Europe/Istanbul"),
        ).forEach { fit(it) }
    }
}
