package com.noor.alerts

import android.content.Context
import android.content.res.Configuration
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/** Notification wording in the language and number style the user chose in Noor (not just the system's). */
internal class AlertText(context: Context, private val config: WatchConfig) {

    val arabic: Boolean = when (config.language) {
        "ar" -> true
        "en" -> false
        else -> context.resources.configuration.locales[0].language == "ar"
    }
    private val userLocale: Locale = if (arabic) Locale("ar") else Locale.ENGLISH
    // Note: inside Configuration.apply{} a bare `locale` would mean Configuration.locale, hence the distinct name.
    val ctx: Context = context.createConfigurationContext(Configuration(context.resources.configuration).also { it.setLocale(userLocale) })

    fun string(id: Int, vararg args: Any): String = ctx.getString(id, *args)

    fun prayer(p: Prayer, date: LocalDate): String = when {
        p == Prayer.DHUHR && date.dayOfWeek == DayOfWeek.FRIDAY -> string(R.string.noor_alert_jumuah)
        arabic -> p.ar
        else -> p.en
    }

    fun time(at: Instant): String {
        val pattern = if (config.use24h) "HH:mm" else "h:mm a"
        var f = DateTimeFormatter.ofPattern(pattern, userLocale).withZone(config.zone)
        f = f.withDecimalStyle(if (config.arabicDigits) DecimalStyle.STANDARD.withZeroDigit('٠') else DecimalStyle.STANDARD)
        return f.format(at)
    }

    val place: String get() = (if (arabic) config.placeAr ?: config.placeEn else config.placeEn).ifBlank { "" }

    fun title(p: Prayer, date: LocalDate): String =
        if (p == Prayer.SHURUQ) string(R.string.noor_alert_title_sunrise) else string(R.string.noor_alert_title, prayer(p, date))

    /** A whole number in the digits chosen in Noor. */
    fun number(n: Int): String = n.toString().let { s -> if (config.arabicDigits) s.map { c -> if (c in '0'..'9') '٠' + (c - '0') else c }.joinToString("") else s }

    /** 0.9.9: "Asr in 15 minutes", "Jumu’ah in 10 minutes"; before sunrise, "Fajr time ends in 15 minutes". */
    fun reminderTitle(p: Prayer, date: LocalDate, minutes: Int): String {
        val m = ctx.resources.getQuantityString(R.plurals.noor_remind_minutes, minutes, number(minutes))
        return if (p == Prayer.SHURUQ) string(R.string.noor_remind_title_sunrise, m) else string(R.string.noor_remind_title, prayer(p, date), m)
    }

    /** 0.16: "Asr began 20 minutes ago" (the iqamah); after sunrise, "Sunrise was 20 minutes ago". */
    fun afterTitle(p: Prayer, date: LocalDate, minutes: Int): String {
        val m = ctx.resources.getQuantityString(R.plurals.noor_remind_minutes, minutes, number(minutes))
        return if (p == Prayer.SHURUQ) string(R.string.noor_after_title_sunrise, m) else string(R.string.noor_after_title, prayer(p, date), m)
    }

    /** 0.17: "25 minutes before Fajr", "10 minutes after Dhuhr". */
    fun alarmRelation(a: com.noor.prayer.alerts.PrayerAlarm, date: LocalDate): String {
        val m = ctx.resources.getQuantityString(R.plurals.noor_remind_minutes, a.minutes, number(a.minutes))
        return string(if (a.after) R.string.noor_alarm_after else R.string.noor_alarm_before, m, prayer(a.prayer, date))
    }

    /** 0.17: an alarm's name: its label, or what it is ("25 minutes before Fajr"). */
    fun alarmTitle(a: com.noor.prayer.alerts.PrayerAlarm, date: LocalDate): String = a.label.ifBlank { alarmRelation(a, date) }

    /** 0.17: "Fajr 4:52 AM · Dubai" (with the label as the title, what it is first). */
    fun alarmBody(a: com.noor.prayer.alerts.PrayerAlarm, date: LocalDate, prayerAt: Instant): String =
        listOfNotNull(if (a.label.isNotBlank()) alarmRelation(a, date) else null, prayer(a.prayer, date) + " " + time(prayerAt), place.ifBlank { null })
            .joinToString(" · ")

    fun body(p: Prayer, at: Instant): String = listOf(time(at), place).filter { it.isNotBlank() }.joinToString(" · ")
}
