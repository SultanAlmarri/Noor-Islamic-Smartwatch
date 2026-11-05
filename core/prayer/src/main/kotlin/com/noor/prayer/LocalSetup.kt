package com.noor.prayer

import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlert
import com.noor.prayer.alerts.SoundIds
import com.noor.prayer.authority.Authorities
import com.noor.prayer.authority.Timetable
import java.time.ZoneId

/** A place chosen on the watch itself: from the watch's own location, or from the city list. */
data class SetupPlace(
    val point: GeoPoint,
    val zoneId: String,
    val nameEn: String,
    val nameAr: String?,
    val countryCode: String?,
    val regionCode: String?,
    val fromGps: Boolean,
    val fixedAtEpochMs: Long? = null,
)

/** How prayer alerts sound when they are set up on the watch itself. One choice for all prayers keeps it simple on a wrist. */
enum class WatchAlertStyle(val mode: AlertMode) {
    /** The opening of the Adhan. */
    ADHAN_SHORT(AlertMode.ADHAN_SHORT),
    /** Noor's soft chime. */
    CHIME(AlertMode.SOUND),
    /** A standard notification (the watch's notification sound and vibration; follows Do Not Disturb). */
    NOTIFICATION(AlertMode.NOTIFICATION),
}

/**
 * The watch's own settings, used when the watch is set up on its own (no phone app, or the user chose this).
 * Calculation follows the same rules as the phone: the place's official authority, its published timetable where
 * Noor has one, and the same engine.
 */
data class LocalSetup(
    val place: SetupPlace? = null,
    /** null = the authority's own Asr rule. */
    val asrSchool: AsrSchool? = null,
    /** "", "en" or "ar"; "" follows the watch's language. */
    val language: String = "",
    /** null = follow the watch's own 12/24-hour setting. */
    val use24h: Boolean? = null,
    val arabicDigits: Boolean = false,
    val hijriAdjust: Int = 0,
    val alertsOn: Boolean = false,
    val alertStyle: WatchAlertStyle = WatchAlertStyle.ADHAN_SHORT,
    /** Prayers that alert when [alertsOn] (sunrise is off unless chosen). */
    val alertPrayers: Set<Prayer> = Prayer.entries.filter { it.isPrayer }.toSet(),
) {
    /** The alert settings these choices stand for, in the format the phone uses, so both devices alert the same way. */
    fun alertSettings(): AlertSettings = AlertSettings(
        enabled = alertsOn,
        device = AlertDevice.WATCH,
        perPrayer = Prayer.entries.associateWith { p ->
            val mode = if (p == Prayer.SHURUQ && alertStyle == WatchAlertStyle.ADHAN_SHORT) AlertMode.SOUND else alertStyle.mode
            val sound = when (mode) {
                AlertMode.ADHAN_SHORT -> if (p == Prayer.FAJR) SoundIds.DEFAULT_FAJR_ADHAN else SoundIds.DEFAULT_ADHAN
                AlertMode.SOUND -> SoundIds.DEFAULT_SOUND
                else -> null
            }
            PrayerAlert(p in alertPrayers, mode, sound).normalized(p)
        },
    )

    /**
     * The configuration the watch calculates from. [deviceZone] is used for a position from the watch's own location
     * (it follows the network when travelling, like the phone's automatic mode); [device24h] for "follow the watch".
     * [timetable] returns a bundled official timetable by id.
     */
    fun toConfig(nowMs: Long, deviceZone: ZoneId, device24h: Boolean, timetable: (String) -> Timetable?): WatchConfig? {
        val p = place ?: return null
        val zone = if (p.fromGps) deviceZone else runCatching { ZoneId.of(p.zoneId) }.getOrDefault(deviceZone)
        val near = Places.nearest(p.point)
        val authority = Authorities.forPlace(p.countryCode ?: near.countryCode, p.regionCode ?: near.region)
        val profile = authority.profile
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val official = authority.timetableId?.takeIf { PrayerEngine.referencePointFor(profile, p.point) != null }
            ?.let(timetable)?.window(today.minusDays(1), today.plusDays(56))?.takeIf { it.size > 0 }
        return WatchConfig(
            point = p.point,
            zoneId = zone.id,
            placeEn = p.nameEn,
            placeAr = p.nameAr,
            fromGps = p.fromGps,
            profileId = profile.id,
            asrSchool = asrSchool,
            highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
            offsets = emptyMap(),
            hijriAdjust = hijriAdjust,
            language = language,
            arabicDigits = arabicDigits,
            use24h = use24h ?: device24h,
            updatedAtEpochMs = nowMs,
            countryCode = p.countryCode,
            official = official,
            alerts = alertSettings(),
        )
    }

    fun encode(): String = buildString {
        fun put(k: String, v: Any?) { if (v != null) append(k).append('=').append(v.toString().replace("\n", " ")).append('\n') }
        put("v", 1)
        place?.let { p ->
            put("lat", p.point.latitude); put("lon", p.point.longitude); put("elev", p.point.elevationM)
            put("zone", p.zoneId); put("nameEn", p.nameEn); put("nameAr", p.nameAr)
            put("cc", p.countryCode); put("region", p.regionCode); put("gps", p.fromGps); put("fixed", p.fixedAtEpochMs)
        }
        put("asr", asrSchool?.name); put("lang", language); put("h24", use24h); put("arabicDigits", arabicDigits)
        put("hijri", hijriAdjust); put("alerts", alertsOn); put("style", alertStyle.name)
        put("prayers", alertPrayers.joinToString(",") { it.name })
    }

    companion object {
        fun decode(text: String?): LocalSetup? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val m = text.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
                val place = if (m["lat"] != null && m["lon"] != null) SetupPlace(
                    point = GeoPoint(m.getValue("lat").toDouble(), m.getValue("lon").toDouble(), m["elev"]?.toDoubleOrNull() ?: 0.0),
                    zoneId = m["zone"]?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: "UTC",
                    nameEn = m["nameEn"] ?: "",
                    nameAr = m["nameAr"],
                    countryCode = m["cc"]?.takeIf { it.length == 2 },
                    regionCode = m["region"],
                    fromGps = m["gps"] == "true",
                    fixedAtEpochMs = m["fixed"]?.toLongOrNull(),
                ) else null
                LocalSetup(
                    place = place,
                    asrSchool = m["asr"]?.let { n -> AsrSchool.entries.firstOrNull { it.name == n } },
                    language = m["lang"]?.takeIf { it in setOf("", "en", "ar") } ?: "",
                    use24h = m["h24"]?.let { it == "true" },
                    arabicDigits = m["arabicDigits"] == "true",
                    hijriAdjust = (m["hijri"]?.toIntOrNull() ?: 0).coerceIn(-2, 2),
                    alertsOn = m["alerts"] == "true",
                    alertStyle = m["style"]?.let { n -> WatchAlertStyle.entries.firstOrNull { it.name == n } } ?: WatchAlertStyle.ADHAN_SHORT,
                    alertPrayers = m["prayers"]?.split(',')?.mapNotNull { n -> Prayer.entries.firstOrNull { it.name == n } }?.toSet()
                        ?: Prayer.entries.filter { it.isPrayer }.toSet(),
                )
            }.getOrNull()
        }
    }
}
