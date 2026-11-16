package com.noor.prayer.face

import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.qibla.Qibla
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Everything a Minhad Studio face shows, already written in the design's language and digits. Built from the same
 * [WatchConfig] and engine as the rest of the app (so the face agrees with it to the minute), once a minute and when
 * something changes; the renderer only reads it, so drawing a frame allocates nothing.
 */
data class FaceData(
    val text: FaceText,
    /** "10:09" (12- or 24-hour as chosen) and, for each minute of the hour, nothing else: hours and minutes come from the frame. */
    val time: String,
    /** "ص"/"م" or "AM"/"PM" with a 12-hour clock, else null. */
    val ampm: String?,
    val date: String,
    val weekday: String,
    val hijri: String,
    val next: Next?,
    val prayers: List<Line>,
    val sun: Sun?,
    val qibla: String?,
    val qiblaDeg: Float?,
    val battery: String?,
    val batteryLevel: Float?,
    val appName: String,
    /** Shown where prayer information would be when the watch has no place yet. */
    val setupHint: String,
    /** When the snapshot must be rebuilt at the latest (the next minute, or the next prayer if sooner). */
    val validUntil: Instant,
) {
    data class Next(
        val prayer: Prayer, val name: String, val time: String,
        /** "in 1 h 23 min", "بعد ١ س ٢٣ د". */
        val countdown: String,
        /** "1:23" (hours:minutes) or "23 min"/"٢٣ د" under an hour. */
        val countdownShort: String,
        /** 0..1 through the interval from the last time to the next. */
        val progress: Float,
        /** The time that started the interval ("Dhuhr"), or null before the first of the day. */
        val currentName: String?,
    ) {
        /** "3:14 PM  ·  in 1 h 23 min" (made once, not on every frame). */
        val detail: String = "$time  ·  $countdown"
        /** "Asr  1:23". */
        val nameAndShort: String = "$name  $countdownShort"
    }

    data class Line(val prayer: Prayer, val name: String, val time: String, val isNext: Boolean, val isCurrent: Boolean)

    /**
     * The Sun's day: [dayFraction] 0 at sunrise to 1 at sunset (null at night, with [nightFraction] from sunset to the
     * next sunrise instead); [marks] each time of the day as a fraction of the 24 hours from midnight; [now] likewise.
     */
    data class Sun(
        val dayFraction: Float?, val nightFraction: Float?, val marks: List<Pair<Prayer, Float>>, val now: Float, val nextPrayer: Prayer?,
        val sunriseTime: String? = null, val sunsetTime: String? = null,
    ) {
        val sunrise: Float? = marks.firstOrNull { it.first == Prayer.SHURUQ }?.second
        val sunset: Float? = marks.firstOrNull { it.first == Prayer.MAGHRIB }?.second
    }

    companion object {
        /**
         * The snapshot at [now]. [config] null: the watch is not set up yet (time, date, battery still show).
         * [deviceArabic] and [device24h] answer "follow the watch" when neither the design nor the app chose.
         */
        fun build(
            design: FaceDesign, config: WatchConfig?, now: Instant, zone: ZoneId = config?.zone ?: ZoneId.systemDefault(),
            battery: Float? = null, deviceArabic: Boolean = false, device24h: Boolean = false,
        ): FaceData {
            val arabic = when (design.language) {
                FaceLanguage.AR -> true; FaceLanguage.EN -> false
                FaceLanguage.FOLLOW -> when (config?.language) { "ar" -> true; "en" -> false; else -> deviceArabic }
            }
            val arabicDigits = when (design.digits) {
                FaceDigits.ARABIC -> true; FaceDigits.WESTERN -> false
                FaceDigits.FOLLOW -> config?.arabicDigits ?: arabic
            }
            val h24 = when (design.clock) { FaceClock.H24 -> true; FaceClock.H12 -> false; FaceClock.FOLLOW -> config?.use24h ?: device24h }
            val t = FaceText(arabic, arabicDigits, h24)
            val local = now.atZone(zone)
            val today = local.toLocalDate()
            // Prayer times are written in the place's zone, as everywhere in the app (a city chosen elsewhere keeps its own).
            val pz = config?.zone ?: zone
            val placeToday = now.atZone(pz).toLocalDate()
            val nextMinute = local.withSecond(0).withNano(0).plusMinutes(1).toInstant()

            val st = config?.let { c -> runCatching { c.state(now) }.getOrNull() }
            val next = st?.let {
                val left = Duration.between(now, it.next.at)
                Next(it.next.prayer, t.name(it.next.prayer), t.clock(it.next.at.atZone(pz)), t.countdown(left), t.countdownShort(left),
                    it.progress.coerceIn(0f, 1f), it.current?.prayer?.let(t::name))
            }
            val prayers = st?.let { s ->
                Prayer.entries.mapNotNull { p ->
                    s.today.instant(p)?.let { at ->
                        Line(p, t.name(p), t.clock(at.atZone(pz)), isNext = s.next.prayer == p && s.next.date == placeToday,
                            isCurrent = s.current?.prayer == p && s.current.date == placeToday)
                    }
                }
            }.orEmpty()
            val sun = st?.let { s ->
                val dayStart = placeToday.atStartOfDay(pz).toInstant()
                fun frac(i: Instant) = (Duration.between(dayStart, i).seconds / 86_400f).coerceIn(0f, 1f)
                val rise = s.today.instant(Prayer.SHURUQ); val set = s.today.instant(Prayer.MAGHRIB)
                val isDay = rise != null && set != null && !now.isBefore(rise) && now.isBefore(set)
                val dayF = if (isDay) Duration.between(rise, now).seconds.toFloat() / Duration.between(rise, set).seconds.coerceAtLeast(1) else null
                val nightF = if (!isDay && rise != null && set != null) {
                    // From last sunset to the next sunrise (yesterday's and tomorrow's are about a minute off: close enough for a picture).
                    val (from, to) = if (now.isBefore(rise)) set.minus(Duration.ofDays(1)) to rise else set to rise.plus(Duration.ofDays(1))
                    (Duration.between(from, now).seconds.toFloat() / Duration.between(from, to).seconds.coerceAtLeast(1)).coerceIn(0f, 1f)
                } else null
                Sun(dayF?.coerceIn(0f, 1f), nightF, Prayer.entries.mapNotNull { p -> s.today.instant(p)?.let { p to frac(it) } }, frac(now), s.next.prayer,
                    rise?.let { t.clock(it.atZone(pz)) }, set?.let { t.clock(it.atZone(pz)) })
            }
            val qibla = config?.let { Qibla.from(it.point) }?.takeIf { !it.veryClose }
            val validUntil = st?.next?.at?.takeIf { it.isBefore(nextMinute) } ?: nextMinute
            return FaceData(
                text = t,
                time = t.hhmm(local.hour, local.minute),
                ampm = if (h24) null else t.ampm(local.hour),
                date = t.date(today),
                weekday = t.weekday(today),
                hijri = config?.let { t.hijri(HijriDate.of(today, it.hijriAdjust)) } ?: t.hijri(HijriDate.of(today)),
                next = next, prayers = prayers, sun = sun,
                qibla = qibla?.let { t.qibla(it.bearingDeg) }, qiblaDeg = qibla?.bearingDeg?.toFloat(),
                battery = battery?.let { t.percent(it) }, batteryLevel = battery,
                appName = if (arabic) "منهاد" else "Minhad",
                setupHint = if (arabic) "افتح منهاد" else "Open Minhad",
                validUntil = validUntil,
            )
        }
    }
}

/**
 * The face's words and numbers, in English or Arabic, with Western or Arabic-Indic digits. Its own tables (not the
 * platform's locale data), so the phone's preview, the watch and the JVM tests write exactly the same text.
 */
class FaceText(val arabic: Boolean, val arabicDigits: Boolean, val h24: Boolean) {
    /** "0".."59" in the chosen digits, two-digit forms "00".."59", made once. */
    private val two = Array(60) { digits(String.format(java.util.Locale.ROOT, "%02d", it)) }
    private val one = Array(60) { digits(it.toString()) }

    fun digits(s: String): String {
        if (!arabicDigits) return s
        val b = StringBuilder(s.length)
        for (ch in s) b.append(if (ch in '0'..'9') '٠' + (ch - '0') else ch)
        return b.toString()
    }

    /** "05"/"٠٥", without allocating (for seconds on every frame). */
    fun twoDigits(n: Int): String = two[n.coerceIn(0, 59)]

    fun hour(h: Int): Int = if (h24) h else (h + 11) % 12 + 1

    fun hhmm(h: Int, m: Int): String = (if (h24) two[h.coerceIn(0, 23)] else one[hour(h)]) + ":" + two[m.coerceIn(0, 59)]

    fun ampm(h: Int): String = if (arabic) (if (h < 12) "ص" else "م") else (if (h < 12) "AM" else "PM")

    /** A prayer's time: "3:14 PM", "15:14", "٣:١٤ م". */
    fun clock(t: ZonedDateTime): String = hhmm(t.hour, t.minute) + (if (h24) "" else " " + ampm(t.hour))

    fun name(p: Prayer): String = if (arabic) p.ar else p.en

    fun weekday(d: LocalDate): String = (if (arabic) WEEKDAYS_AR else WEEKDAYS_EN)[d.dayOfWeek.value - 1]

    /** "Mon 16 Nov", "الإثنين ١٦ نوفمبر". */
    fun date(d: LocalDate): String = if (arabic) WEEKDAYS_AR[d.dayOfWeek.value - 1] + " " + digits(d.dayOfMonth.toString()) + " " + MONTHS_AR[d.monthValue - 1]
    else WEEKDAYS_EN[d.dayOfWeek.value - 1].take(3) + " " + d.dayOfMonth + " " + MONTHS_EN[d.monthValue - 1].take(3)

    /** "25 Jumada al-Ula", "٢٥ جمادى الأولى" (without the year: it fits a watch). */
    fun hijri(h: HijriDate): String = digits(h.day.toString()) + " " + (if (arabic) h.monthNameAr else h.monthNameEn)

    /** Rounded up to the minute, like the app: "in 1 h 23 min", "بعد ١ س ٢٣ د". */
    fun countdown(d: Duration): String {
        val total = (maxOf(0L, d.seconds) + 59) / 60
        val h = total / 60; val m = total % 60
        return digits(when {
            arabic && h > 0 -> "بعد $h س $m د"
            arabic -> "بعد $m د"
            h > 0 -> "in $h h $m min"
            else -> "in $m min"
        })
    }

    /** "1:23", or "23 min"/"٢٣ د" under an hour. */
    fun countdownShort(d: Duration): String {
        val total = (maxOf(0L, d.seconds) + 59) / 60
        val h = total / 60; val m = total % 60
        return if (h > 0) digits("$h:") + two[m.toInt()] else digits("$m") + (if (arabic) " د" else " min")
    }

    /** "Qibla 258°" kept as one left-to-right run for the number. */
    fun qibla(deg: Double): String = (if (arabic) "القبلة " else "Qibla ") + "⁦" + digits(String.format(java.util.Locale.ROOT, "%.0f", deg)) + "°⁩"

    fun qiblaShort(deg: Float): String = "⁦" + digits(String.format(java.util.Locale.ROOT, "%.0f", deg)) + "°⁩"

    fun percent(level: Float): String = digits(String.format(java.util.Locale.ROOT, "%.0f", level * 100)) + if (arabic) "٪" else "%"

    /** The twelve numerals of a dial. */
    val numerals: List<String> = (1..12).map { digits(it.toString()) }

    companion object {
        val WEEKDAYS_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val WEEKDAYS_AR = listOf("الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
        val MONTHS_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val MONTHS_AR = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    }
}
