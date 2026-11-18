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
/** The landscape under the home screen's sky (0.9.8): chosen by place, or one the user prefers. */
/**
 * The home screen's scenery. There are six: [DUNES], [RAINFOREST] (the Pacific Northwest), [ARCTIC], [SEA] (the open
 * ocean), [SAVANNA] (since 0.11 the green hills and oaks that took the savanna's place: the name is what earlier
 * versions stored) and [CANYON]. [HILLS] and [MOUNTAINS] are the 0.9 names, kept so an earlier choice still means
 * something (hills show the Pacific Northwest, mountains the red rock); the 0.9 sky, where it is still used, draws
 * the nearest of its own four.
 */
enum class Scenery { AUTO, DUNES, HILLS, MOUNTAINS, SEA, RAINFOREST, ARCTIC, SAVANNA, CANYON }

/**
 * How the home sky is drawn (0.19, the owner's three levels): HIGH the real sky (rendered sceneries; its own quality
 * steps are chosen by itself), MEDIUM the 0.9 sky (the layered sky drawn on the phone), LOW the simplest sky
 * ([com.noor.app.ui.today.SimpleSkyLayer]); AUTO chooses one for the phone ([com.noor.app.sky.SkyLevels]).
 * Stored under a new key ("sky_level"): 0.10-0.18's "sky_graphics" (Ultra, High, Medium, Low were steps of the real sky,
 * Classic the 0.9 sky) is read once to carry the choice over.
 */
enum class SkyGraphics {
    AUTO, HIGH, MEDIUM, LOW;

    companion object {
        /** A choice stored by 0.10-0.18 under "sky_graphics". */
        fun fromOld(name: String?): SkyGraphics = when (name) {
            null, "AUTO" -> AUTO
            "CLASSIC" -> MEDIUM
            else -> HIGH   // Ultra, High, Medium and Low were all the real sky
        }
    }
}

/**
 * (0.23) How the home screen draws the Sun's path. SIMPLE (the default, the owner's word, 18 November 2026): the same arch
 * everywhere and every day, the Sun rising on the left at sunrise and setting on the right at sunset. REAL: the Sun's true
 * path for the place and the date ([com.noor.app.ui.today.SunPanorama]).
 */
enum class SunPathMode { SIMPLE, REAL }

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
    /** (0.22: on unless turned off, the owner's word) The ongoing notification counting to the next prayer. */
    val statusNotification: Boolean = true,
    /**
     * (0.22) That countdown also as a chip in the status bar, as Samsung's Clock shows its timer (Android 16's Live
     * Updates: a promoted ongoing notification; Android's own look, not Minhad's custom one, which Android does not promote).
     */
    val statusBarChip: Boolean = true,
    /** After a prayer's time: "+" time since it for a while (default), or straight to the next prayer's "−" (0.9.8). */
    val elapsedAfterPrayer: Boolean = true,
    /** The home screen's sky has been drawn along once: its hint ("Drag the sky...") is not shown again (0.15). */
    val scrubHintDone: Boolean = false,
    val scenery: Scenery = Scenery.AUTO,
    val skyGraphics: SkyGraphics = SkyGraphics.AUTO,
    /** (0.23) The Sun's path on the home screen: the same arch everywhere (default), or the real one. */
    val sunPath: SunPathMode = SunPathMode.SIMPLE,
    /** The scenery follows the real season of the place (snow in winter...). Off: its best look all year (0.11). */
    val seasons: Boolean = false,
    /** The stars and the Milky Way stand where they truly are for the place and time. Off: the finest night sky (0.11). */
    val trueStars: Boolean = false,
)
