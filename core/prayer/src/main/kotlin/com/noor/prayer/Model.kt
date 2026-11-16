package com.noor.prayer

/** The six daily events. Shuruq (sunrise) is shown with the prayers but is never one of the five. */
enum class Prayer(val en: String, val ar: String, val isPrayer: Boolean) {
    FAJR("Fajr", "الفجر", true),
    SHURUQ("Shuruq", "الشروق", false),
    DHUHR("Dhuhr", "الظهر", true),
    ASR("Asr", "العصر", true),
    MAGHRIB("Maghrib", "المغرب", true),
    ISHA("Isha", "العشاء", true);
}

/** A point on Earth. Elevation is metres above sea level. */
data class GeoPoint(val latitude: Double, val longitude: Double, val elevationM: Double = 0.0) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}

enum class Rounding { UP, NEAREST }

enum class AsrSchool(val shadowFactor: Double) { STANDARD(1.0), HANAFI(2.0) }

/** How Fajr/Isha are bounded where twilight lasts all night or the angle gives an extreme time. */
enum class HighLatitudeRule { NONE, ANGLE_BASED, MIDDLE_OF_NIGHT, ONE_SEVENTH }

/** User-level choices that apply on top of a calculation profile. */
data class UserAdjustments(
    val asrSchool: AsrSchool? = null,                  // null = the profile's default
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    val minuteOffsets: Map<Prayer, Int> = emptyMap(),  // personal +/- minutes per event
)
