package com.noor.app.data

import com.noor.prayer.AsrSchool
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer

enum class LocationMode { AUTO, MANUAL }
enum class Language(val tag: String) { SYSTEM(""), ENGLISH("en"), ARABIC("ar") }
enum class Numerals { WESTERN, ARABIC_INDIC }
enum class TimeFormat { H12, H24 }
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** The last position the app knows about. Kept so the app works offline and after restarts. */
data class KnownLocation(
    val point: GeoPoint,
    val zoneId: String,
    val placeName: String?,
    val countryCode: String?,
    val regionCode: String?,
    val fixedAtEpochMs: Long,
)

data class AppSettings(
    val onboardingDone: Boolean = false,
    val locationMode: LocationMode = LocationMode.AUTO,
    val manualCityId: String? = null,
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
    val theme: ThemeMode = ThemeMode.DARK,
    val hijriAdjust: Int = 0,
)
