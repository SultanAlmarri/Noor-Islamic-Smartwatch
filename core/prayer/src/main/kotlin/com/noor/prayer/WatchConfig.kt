package com.noor.prayer

import com.noor.prayer.authority.Timetable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the watch needs to calculate exactly the same times as the phone, with no phone or network.
 * The phone resolves every automatic choice (authority, place name) before sending, so the watch never guesses.
 *
 * Wire format: one `key=value` per line, UTF-8. Unknown keys are ignored and missing keys fall back to defaults,
 * so older and newer app versions on phone and watch keep working with each other.
 */
data class WatchConfig(
    val point: GeoPoint,
    val zoneId: String,
    val placeEn: String,
    val placeAr: String?,
    val fromGps: Boolean,
    val profileId: String,
    val asrSchool: AsrSchool?,
    val highLatitudeRule: HighLatitudeRule,
    val offsets: Map<Prayer, Int>,
    val hijriAdjust: Int,
    /** "en", "ar" or "" for the watch's own language. */
    val language: String,
    val arabicDigits: Boolean,
    val use24h: Boolean,
    val updatedAtEpochMs: Long,
    /** ISO country of the place, for the flag (display only). */
    val countryCode: String? = null,
    /** The authority's own published times for the coming weeks, when the phone has them; used as published. */
    val official: Timetable? = null,
) {
    val zone: ZoneId get() = ZoneId.of(zoneId)
    val profile: CalculationProfile get() = Profiles.byId(profileId) ?: Profiles.MWL

    fun day(date: LocalDate): PrayerDay {
        val user = UserAdjustments(asrSchool, highLatitudeRule, offsets)
        return official?.day(date, zone, profileId, user) ?: PrayerEngine.compute(
            date, point, zone, profile, user, isRamadan = HijriDate.of(date, hijriAdjust).isRamadan,
        )
    }

    fun state(now: Instant): ScheduleState = Schedule.state(now, zone, ::day)

    /** Every event from [from] (inclusive) for [days] days, in order. Used for complication and tile timelines. */
    fun events(from: Instant, days: Int): List<TimedEvent> {
        val start = from.atZone(zone).toLocalDate().minusDays(1)
        return (0..days + 1).flatMap { i ->
            val d = day(start.plusDays(i.toLong()))
            Prayer.entries.mapNotNull { p -> d.instant(p)?.let { TimedEvent(p, d.date, it) } }
        }.sortedBy { it.at }
    }

    fun encode(): String = buildString {
        fun put(k: String, v: Any?) { if (v != null) append(k).append('=').append(v.toString().replace("\n", " ")).append('\n') }
        put("v", VERSION)
        put("lat", point.latitude); put("lon", point.longitude); put("elev", point.elevationM)
        put("zone", zoneId); put("placeEn", placeEn); put("placeAr", placeAr); put("gps", fromGps)
        put("profile", profileId); put("asr", asrSchool?.name); put("highLat", highLatitudeRule.name)
        put("offsets", Prayer.entries.joinToString(",") { (offsets[it] ?: 0).toString() })
        put("hijri", hijriAdjust); put("lang", language); put("arabicDigits", arabicDigits); put("h24", use24h)
        put("updated", updatedAtEpochMs)
        put("cc", countryCode)
        if (official != null && official.size > 0) put("official", official.id + "|" + official.encode().replace('\n', ';'))
    }

    companion object {
        const val VERSION = 1
        const val PATH = "/noor/config"
        const val CAPABILITY_WATCH = "noor_watch_app"
        const val CAPABILITY_PHONE = "noor_phone_app"

        fun decode(text: String): WatchConfig? = runCatching {
            val m = text.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            val offs = m["offsets"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
            WatchConfig(
                point = GeoPoint(m.getValue("lat").toDouble(), m.getValue("lon").toDouble(), m["elev"]?.toDoubleOrNull() ?: 0.0),
                zoneId = m["zone"]?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: "UTC",
                placeEn = m["placeEn"] ?: "",
                placeAr = m["placeAr"],
                fromGps = m["gps"] == "true",
                profileId = m["profile"]?.takeIf { Profiles.byId(it) != null } ?: Profiles.MWL.id,
                asrSchool = m["asr"]?.let { n -> AsrSchool.entries.firstOrNull { it.name == n } },
                highLatitudeRule = m["highLat"]?.let { n -> HighLatitudeRule.entries.firstOrNull { it.name == n } } ?: HighLatitudeRule.ANGLE_BASED,
                offsets = Prayer.entries.mapIndexedNotNull { i, p -> offs.getOrNull(i)?.takeIf { it != 0 }?.let { p to it.coerceIn(-30, 30) } }.toMap(),
                hijriAdjust = (m["hijri"]?.toIntOrNull() ?: 0).coerceIn(-2, 2),
                language = m["lang"]?.takeIf { it in setOf("", "en", "ar") } ?: "",
                arabicDigits = m["arabicDigits"] == "true",
                use24h = m["h24"] == "true",
                updatedAtEpochMs = m["updated"]?.toLongOrNull() ?: 0L,
                countryCode = m["cc"]?.takeIf { it.length == 2 },
                official = m["official"]?.let { v ->
                    val id = v.substringBefore('|'); Timetable.parse(id, v.substringAfter('|').replace(';', '\n')).takeIf { it.size > 0 }
                },
            )
        }.getOrNull()
    }
}
