package com.noor.app.data

import com.noor.prayer.AsrSchool
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertSettings

enum class LocationMode { AUTO, MANUAL }
enum class Language(val tag: String) { SYSTEM(""), ENGLISH("en"), ARABIC("ar") }
enum class Numerals { WESTERN, ARABIC_INDIC }
enum class TimeFormat { H12, H24 }
/** SUN: light from sunrise until Maghrib, dark through the night, following the prayer times of the current place. */
enum class ThemeMode { SUN, SYSTEM, DARK, LIGHT }

/** The last position the app knows about (or the city chosen by hand). Kept so the app works offline and after restarts. */
data class KnownLocation(
    val point: GeoPoint,
    val zoneId: String,
    /** Most specific reliable name: neighbourhood/district with precise location, town/city with approximate. */
    val placeName: String?,
    val countryCode: String?,
    val regionCode: String?,
    val fixedAtEpochMs: Long,
    val placeNameAr: String? = null,
    /** Where the place is: city or province ("Dubai" for Za'abeel). */
    val placeContext: String? = null,
    val placeContextAr: String? = null,
    /** True when the fix came from precise (fine) location. */
    val precise: Boolean = false,
) {
    /** Compact one-line encoding for storage (tab-separated; tabs never occur in place names from our sources). */
    fun encode(): String = listOf(point.latitude, point.longitude, point.elevationM, zoneId, placeName ?: "", countryCode ?: "",
        regionCode ?: "", fixedAtEpochMs, placeNameAr ?: "", placeContext ?: "", placeContextAr ?: "", precise).joinToString("\t")

    companion object {
        fun decode(s: String): KnownLocation? = runCatching {
            val f = s.split('\t')
            KnownLocation(GeoPoint(f[0].toDouble(), f[1].toDouble(), f[2].toDouble()), f[3], f[4].ifEmpty { null }, f[5].ifEmpty { null },
                f[6].ifEmpty { null }, f[7].toLong(), f[8].ifEmpty { null }, f[9].ifEmpty { null }, f[10].ifEmpty { null }, f[11] == "true")
        }.getOrNull()
    }
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val locationMode: LocationMode = LocationMode.AUTO,
    val manualCityId: String? = null,
    /** City chosen by hand from the worldwide list (supersedes [manualCityId]). */
    val manualPlace: KnownLocation? = null,
    val backgroundUpdates: Boolean = false,
    val lastLocation: KnownLocation? = null,
    /** null = choose the authority automatically from the location. */
    val profileId: String? = null,
    val asrSchool: AsrSchool? = null,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    val offsets: Map<Prayer, Int> = emptyMap(),
    val language: Language = Language.SYSTEM,
    val numerals: Numerals = Numerals.WESTERN,
    val timeFormat: TimeFormat = TimeFormat.H12,
    val theme: ThemeMode = ThemeMode.SUN,
    val hijriAdjust: Int = 0,
    /** Adhan and prayer notifications (shared with the watch through the watch config). */
    val alerts: AlertSettings = AlertSettings(),
    /** The persistent notification with the next prayer and a live countdown (phone only). */
    val statusNotification: Boolean = false,
    /** After a prayer's time: "+" time since it for a while (default), or straight to the next prayer's "−" (0.9.8). */
    val elapsedAfterPrayer: Boolean = true,
)
