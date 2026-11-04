package com.noor.app.data

import com.noor.prayer.CalculationProfile
import com.noor.prayer.authority.Authorities
import com.noor.prayer.authority.Authority
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
    val contextEn: String? = null,
    val contextAr: String? = null,
    val precise: Boolean = false,
)

/** One day of times and where they come from. */
data class ResolvedDay(val day: PrayerDay, val official: Boolean)

object PrayerProvider {

    fun place(s: AppSettings): ResolvedPlace? {
        if (s.locationMode == LocationMode.MANUAL) {
            s.manualPlace?.let { m ->
                return ResolvedPlace(m.point, runCatching { ZoneId.of(m.zoneId) }.getOrDefault(ZoneId.systemDefault()),
                    m.placeName ?: "%.3f, %.3f".format(m.point.latitude, m.point.longitude), m.placeNameAr, false,
                    m.countryCode, m.regionCode, null, m.placeContext, m.placeContextAr)
            }
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
            nameAr = last.placeNameAr ?: if (closeToCity && last.placeName == null) near.nameAr else null,
            fromGps = true,
            countryCode = last.countryCode,
            regionCode = last.regionCode,
            fixedAtEpochMs = last.fixedAtEpochMs,
            contextEn = last.placeContext,
            contextAr = last.placeContextAr,
            precise = last.precise,
        )
    }

    /** The authority for this place, or the one behind a manually chosen method (null for methods without one). */
    fun authority(s: AppSettings, place: ResolvedPlace): Authority? =
        if (s.profileId != null) Authorities.forProfile(s.profileId) else Authorities.forPlace(place.countryCode ?: Places.nearest(place.point).countryCode, place.regionCode ?: Places.nearest(place.point).region)

    fun profile(s: AppSettings, place: ResolvedPlace): CalculationProfile =
        s.profileId?.let { Profiles.byId(it) } ?: authority(s, place)?.profile ?: Profiles.MWL

    /**
     * The day's times: the authority's own published times when the app has them for that date and the place is in
     * the authority's area; otherwise calculated.
     */
    fun resolve(date: LocalDate, place: ResolvedPlace, profile: CalculationProfile, s: AppSettings, tables: OfficialTimetables?): ResolvedDay {
        val auth = authority(s, place)?.takeIf { it.profile.id == profile.id }
        val inArea = PrayerEngine.referencePointFor(profile, place.point) != null
        val table = auth?.timetableId?.takeIf { inArea }?.let { tables?.get(it) }
        val user = UserAdjustments(s.asrSchool, s.highLatitudeRule, s.offsets)
        table?.day(date, place.zone, profile.id, user)?.let { return ResolvedDay(it, official = true) }
        return ResolvedDay(day(date, place, profile, s), official = false)
    }

    fun day(date: LocalDate, place: ResolvedPlace, profile: CalculationProfile, s: AppSettings): PrayerDay =
        PrayerEngine.compute(
            date, place.point, place.zone, profile,
            UserAdjustments(s.asrSchool, s.highLatitudeRule, s.offsets),
            isRamadan = HijriDate.of(date, s.hijriAdjust).isRamadan,
        )
}
