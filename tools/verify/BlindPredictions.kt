import com.noor.prayer.GeoPoint
import com.noor.prayer.Prayer
import com.noor.prayer.PrayerEngine
import com.noor.prayer.Profiles
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Writes the frozen Dubai (C1) predictions used for the blind test. Usage: BlindPredictions <from> <to> <out.csv> */
fun main(args: Array<String>) {
    val from = LocalDate.parse(args[0]); val to = LocalDate.parse(args[1])
    val zone = ZoneId.of("Asia/Dubai")
    val sb = StringBuilder("date,fajr,shuruq,dhuhr,asr,maghrib,isha\n")
    var d = from
    while (!d.isAfter(to)) {
        val day = PrayerEngine.compute(d, GeoPoint(25.2, 55.27), zone, Profiles.DUBAI_IACAD)
        sb.append(d).append(',').append(Prayer.entries.joinToString(",") { val m = day.minutes[it]!!; "%02d:%02d".format(m / 60, m % 60) }).append('\n')
        d = d.plusDays(1)
    }
    File(args[2]).writeText(sb.toString())
}
