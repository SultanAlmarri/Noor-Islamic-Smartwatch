package com.noor.app.data

import com.noor.prayer.CalculationProfile
import com.noor.prayer.GeoPoint
import com.noor.prayer.HijriDate
import com.noor.prayer.Places
import com.noor.prayer.PrayerDay
import com.noor.prayer.PrayerEngine
import com.noor.prayer.Profiles
import com.noor.prayer.UserAdjustments
import java.time.LocalDate
import java.time.ZoneId

/** Where the times are calculated for, and how the app knows it. */
data class ResolvedPlace(
    val point: GeoPoint,
    val zone: ZoneId,
    val nameEn: String,
    val nameAr: String?,
    val fromGps: Boolean,
    val countryCode: String?,
    val regionCode: String?,
    val fixedAtEpochMs: Long?,
)

object PrayerProvider {

    fun place(s: AppSettings): ResolvedPlace? {
        if (s.locationMode == LocationMode.MANUAL) {
            val city = s.manualCityId?.let { Places.byId(it) } ?: return null
            return ResolvedPlace(city.point, ZoneId.of(city.zoneId), city.nameEn, city.nameAr, false, city.countryCode, city.region, null)
        }
        val last = s.lastLocation ?: return null
        val near = Places.nearest(last.point)
        val closeToCity = Places.distanceKm(near.point, last.point) < 40
        return ResolvedPlace(
            point = last.point,
            // In automatic mode the phone's own zone is authoritative (it follows the network when travelling).
            zone = ZoneId.systemDefault(),
            nameEn = last.placeName ?: if (closeToCity) near.nameEn else "%.3f, %.3f".format(last.point.latitude, last.point.longitude),
            nameAr = if (closeToCity && last.placeName == null) near.nameAr else null,
            fromGps = true,
            countryCode = last.countryCode,
            regionCode = last.regionCode,
            fixedAtEpochMs = last.fixedAtEpochMs,
        )
    }

    fun profile(s: AppSettings, place: ResolvedPlace): CalculationProfile =
        s.profileId?.let { Profiles.byId(it) } ?: Places.defaultProfileFor(place.point, place.countryCode, place.regionCode)

    fun day(date: LocalDate, place: ResolvedPlace, profile: CalculationProfile, s: AppSettings): PrayerDay =
        PrayerEngine.compute(
            date, place.point, place.zone, profile,
            UserAdjustments(s.asrSchool, s.highLatitudeRule, s.offsets),
            isRamadan = HijriDate.of(date, s.hijriAdjust).isRamadan,
        )
}
