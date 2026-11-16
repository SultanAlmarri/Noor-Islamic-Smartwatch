package com.noor.wear

import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.TimedEvent
import com.noor.prayer.WatchConfig
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Text on the watch follows the phone's language, numeral and clock choices, with the watch's language as fallback. */
class WearText(private val config: WatchConfig?, deviceLocale: Locale = Locale.getDefault()) {
    val arabic: Boolean = when (config?.language) {
        "ar" -> true
        "en" -> false
        else -> deviceLocale.language == "ar"
    }
    private val arabicDigits = config?.arabicDigits == true
    private val h24 = config?.use24h == true

    /** The digits chosen, whatever produced [s] (String.format writes the default locale's digits: 0.9.8). */
    fun digits(s: String): String = buildString(s.length) {
        for (ch in s) {
            val d = when (ch) { in '0'..'9' -> ch - '0'; in '\u0660'..'\u0669' -> ch - '\u0660'; in '\u06F0'..'\u06F9' -> ch - '\u06F0'; else -> -1 }
            append(if (d < 0) ch else if (arabicDigits) '\u0660' + d else '0' + d)
        }
    }

    fun name(p: Prayer): String = if (arabic) p.ar else p.en

    fun place(c: WatchConfig): String = if (arabic) c.placeAr ?: c.placeEn else c.placeEn

    /** Place with its country flag (shared mapping with the phone: com.noor.prayer.geo.Flags). */
    fun placeWithFlag(c: WatchConfig): String = (com.noor.prayer.geo.Flags.emoji(c.countryCode)?.let { "$it " } ?: "") + place(c)

    /** "3:14 PM", "15:14", "٣:١٤ م". */
    fun time(at: Instant, zone: ZoneId): String {
        val t = at.atZone(zone)
        if (h24) return digits("%d:%02d".format(t.hour, t.minute))
        val h = (t.hour + 11) % 12 + 1
        val suffix = if (arabic) (if (t.hour < 12) "ص" else "م") else (if (t.hour < 12) "AM" else "PM")
        return digits("%d:%02d".format(h, t.minute)) + " " + suffix
    }

    /** Time without the AM/PM marker, for tight places. */
    fun timeShort(at: Instant, zone: ZoneId): String {
        val t = at.atZone(zone)
        val h = if (h24) t.hour else (t.hour + 11) % 12 + 1
        return digits("%d:%02d".format(h, t.minute))
    }

    /** Live countdown: "1:23:07" or "23:07". */
    fun countdown(d: Duration): String {
        val s = maxOf(0L, d.seconds)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return digits(if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec))
    }

    /** Rounded-up remaining time without seconds: "1 h 23 min", "7 min", "١ س ٢٣ د". */
    fun remaining(d: Duration): String {
        val total = (maxOf(0L, d.seconds) + 59) / 60
        val h = total / 60; val m = total % 60
        return digits(
            when {
                arabic && h > 0 -> "$h س $m د"
                arabic -> "$m د"
                h > 0 -> "$h h $m min"
                else -> "$m min"
            }
        )
    }

    fun hijri(h: HijriDate): String =
        digits("${h.day} ") + (if (arabic) h.monthNameAr else h.monthNameEn) + digits(" ${h.year}")

    fun eventLine(e: TimedEvent, zone: ZoneId): String = name(e.prayer) + "  " + time(e.at, zone)

    fun eventName(e: com.noor.prayer.calendar.IslamicEvent): String = if (arabic) e.ar else e.en

    /** "Thu 8 Feb", "الإثنين ٨ فبراير" (the year only when it is not this year). */
    fun date(d: java.time.LocalDate, today: java.time.LocalDate): String {
        val loc = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
        val day = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, loc)
        val month = d.month.getDisplayName(java.time.format.TextStyle.SHORT, loc)
        val year = if (d.year != today.year) " " + d.year else ""
        return digits("$day ${d.dayOfMonth} $month$year")
    }

    /** Full Gregorian date for the calendar header: "Thursday 5 November". */
    fun longDate(d: java.time.LocalDate): String {
        val loc = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
        return digits(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, loc) + " " + d.dayOfMonth + " " +
            d.month.getDisplayName(java.time.format.TextStyle.FULL, loc))
    }

    /** "258°" kept as one left-to-right run, so Arabic text around it cannot move the degree sign. */
    fun degrees(value: Double): String = "\u2066" + digits("%.0f".format(value)) + "°\u2069"

    /** "24–26 Nov", or "30 Nov – 2 Dec" across months. */
    fun dateRange(a: java.time.LocalDate, b: java.time.LocalDate, today: java.time.LocalDate): String {
        if (a == b) return date(a, today)
        val loc = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
        val ma = a.month.getDisplayName(java.time.format.TextStyle.SHORT, loc)
        val mb = b.month.getDisplayName(java.time.format.TextStyle.SHORT, loc)
        return digits(if (a.month == b.month) "${a.dayOfMonth}–${b.dayOfMonth} $ma" else "${a.dayOfMonth} $ma – ${b.dayOfMonth} $mb")
    }

    /** "1,234" with the chosen digits. */
    fun count(n: Long): String = digits("%,d".format(Locale.ENGLISH, n))
}

/** Resources in the language chosen on the phone (Arabic or English), or the watch's own language when it says "system". */
fun android.content.Context.localizedFor(config: WatchConfig?): android.content.Context {
    val tag = config?.language?.takeIf { it.isNotEmpty() } ?: return this
    val conf = android.content.res.Configuration(resources.configuration)
    val locale = Locale.forLanguageTag(tag)
    conf.setLocale(locale); conf.setLayoutDirection(locale)
    return createConfigurationContext(conf)
}
