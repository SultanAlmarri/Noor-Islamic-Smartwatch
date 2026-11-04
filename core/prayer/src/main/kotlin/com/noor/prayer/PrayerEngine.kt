package com.noor.prayer

import com.noor.prayer.astro.SolarPosition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.tan

/** Times for one civil day. [seconds] are unrounded; [minutes] are the published (rounded + adjusted) values. */
data class PrayerDay(
    val date: LocalDate,
    val zone: ZoneId,
    val profileId: String,
    /** Unrounded event time, seconds after local midnight (NaN when the event does not occur). */
    val seconds: Map<Prayer, Double>,
    /** Final displayed time in minutes after local midnight, or null when the event does not occur. */
    val minutes: Map<Prayer, Int?>,
) {
    /**
     * The moment of [p]. Minutes count from midnight on the clock in force at local noon (the engine's reference),
     * so on daylight-saving change days the conversion uses that same offset rather than the one at midnight.
     */
    fun instant(p: Prayer): Instant? {
        val m = minutes[p] ?: return null
        val noonOffset = zone.rules.getOffset(date.atTime(12, 0))
        return Instant.ofEpochSecond(date.atStartOfDay().toEpochSecond(noonOffset) + m * 60L)
    }
}

/**
 * The prayer-time engine. Pure Kotlin, no Android dependencies, deterministic.
 * Solar positions: NREL SPA (topocentric, refraction handled explicitly by the profile).
 */
object PrayerEngine {

    private const val REFRACTION_DEG = 0.5667          // 34 arc-minutes at the horizon
    private const val IAC_DIP_PER_SQRT_M = 0.0353333    // IAC elevation formula, deg per sqrt(metre)

    fun compute(
        date: LocalDate,
        location: GeoPoint,
        zone: ZoneId,
        profile: CalculationProfile,
        user: UserAdjustments = UserAdjustments(),
        isRamadan: Boolean = false,
    ): PrayerDay {
        val point = profile.referencePoint ?: location
        val offset: ZoneOffset = zone.rules.getOffset(date.atTime(12, 0))
        val mid = date.atStartOfDay().toEpochSecond(offset).toDouble()
        val dT = SolarPosition.deltaT(date.year)

        val raw = rawEvents(date, mid, point, profile, user, dT, isRamadan)
        val minutes = LinkedHashMap<Prayer, Int?>()
        for (p in Prayer.entries) {
            val s = raw.getValue(p)
            minutes[p] = if (s.isNaN()) null else round(s, profile.rounding) + profile.offset(p) + (user.minuteOffsets[p] ?: 0)
        }
        return PrayerDay(date, zone, profile.id, raw, minutes)
    }

    private fun round(sec: Double, mode: Rounding): Int = when (mode) {
        Rounding.UP -> ceil(sec / 60.0 - 1e-9).toInt()
        Rounding.NEAREST -> floor(sec / 60.0 + 0.5).toInt()
    }

    /** Continuous event times in seconds after local midnight. */
    internal fun rawEvents(
        date: LocalDate, mid: Double, p: GeoPoint, profile: CalculationProfile, user: UserAdjustments,
        dT: Double, isRamadan: Boolean,
    ): Map<Prayer, Double> {
        val lat = p.latitude
        val lon = p.longitude
        val transit = transitLocal(date, mid, lon, dT)
        val altNoon = SolarPosition.elevation(mid + transit, lat, lon, 0.0, dT)
        val zNoon = (90.0 - altNoon) * PI / 180.0
        val shadow = (user.asrSchool ?: profile.asrSchool).shadowFactor
        val asrAlt = atan(1.0 / (shadow + tan(zNoon))) * 180.0 / PI

        val dip = profile.horizonExtraDeg + if (profile.elevationAffectsHorizon) IAC_DIP_PER_SQRT_M * sqrt(maxOf(p.elevationM, 0.0)) else 0.0
        val horizon = -(semidiameter(date) + REFRACTION_DEG + dip)

        val tH = transit / 3600.0
        fun solve(target: Double, guessH: Double, before: Boolean): Double {
            val t = solveAltitude(mid, guessH, target, lat, lon, dT)
            if (t.isNaN()) return Double.NaN
            // must be on the correct side of noon and inside a sane window
            if (before && t >= transit) return Double.NaN
            if (!before && t <= transit) return Double.NaN
            if (t < -6 * 3600 || t > 30 * 3600) return Double.NaN
            return t
        }

        val shuruq = solve(horizon, tH - 5.8, true)
        val sunset = solve(horizon, tH + 5.8, false)
        val asr = solve(asrAlt, tH + 3.3, false)
        var fajr = solve(-profile.fajrAngle, tH - 7.0, true)
        val maghrib = profile.maghribAngle?.let { solve(-it, tH + 5.9, false) } ?: sunset
        val ishaMin = if (isRamadan) profile.ishaMinutesAfterMaghribRamadan ?: profile.ishaMinutesAfterMaghrib else profile.ishaMinutesAfterMaghrib
        var isha = when {
            ishaMin != null -> if (sunset.isNaN()) Double.NaN else sunset + ishaMin * 60.0
            else -> solve(-(profile.ishaAngle ?: 18.0), tH + 7.0, false)
        }

        // High-latitude safeguard (applies only when twilight is missing or longer than the rule allows)
        if (user.highLatitudeRule != HighLatitudeRule.NONE && !shuruq.isNaN() && !sunset.isNaN()) {
            val night = 86400.0 - (sunset - shuruq)
            fun portion(angle: Double) = when (user.highLatitudeRule) {
                HighLatitudeRule.ANGLE_BASED -> angle / 60.0
                HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
                HighLatitudeRule.ONE_SEVENTH -> 1.0 / 7.0
                HighLatitudeRule.NONE -> 1.0
            }
            val fajrLimit = portion(profile.fajrAngle) * night
            if (fajr.isNaN() || shuruq - fajr > fajrLimit) fajr = shuruq - fajrLimit
            if (ishaMin == null) {
                val ishaLimit = portion(profile.ishaAngle ?: 18.0) * night
                if (isha.isNaN() || isha - sunset > ishaLimit) isha = sunset + ishaLimit
            }
        }

        return linkedMapOf(
            Prayer.FAJR to fajr, Prayer.SHURUQ to shuruq, Prayer.DHUHR to transit,
            Prayer.ASR to asr, Prayer.MAGHRIB to maghrib, Prayer.ISHA to isha,
        )
    }

    /** Meridian transit in seconds after local midnight. */
    private fun transitLocal(date: LocalDate, mid: Double, lon: Double, dT: Double): Double {
        var utDay = date.toEpochDay() * 86400.0
        var t = SolarPosition.transit(utDay, lon, dT) - mid
        if (t < 0) { utDay += 86400.0; t = SolarPosition.transit(utDay, lon, dT) - mid }
        else if (t >= 86400) { utDay -= 86400.0; t = SolarPosition.transit(utDay, lon, dT) - mid }
        return t
    }

    /** Newton iteration on the Sun's topocentric geometric altitude (same scheme as the research reference). */
    private fun solveAltitude(mid: Double, guessH: Double, target: Double, lat: Double, lon: Double, dT: Double): Double {
        var t = mid + guessH * 3600.0
        repeat(12) {
            val f = SolarPosition.elevation(t, lat, lon, 0.0, dT) - target
            val d = (SolarPosition.elevation(t + 30, lat, lon, 0.0, dT) - SolarPosition.elevation(t - 30, lat, lon, 0.0, dT)) / 60.0
            if (d == 0.0 || d.isNaN()) return Double.NaN
            val step = (f / d).coerceIn(-3600.0, 3600.0)
            t -= step
            if (abs(step) < 0.01) {
                val err = SolarPosition.elevation(t, lat, lon, 0.0, dT) - target
                return if (abs(err) < 1e-4) t - mid else Double.NaN
            }
        }
        return Double.NaN
    }

    /** Solar semidiameter used by the research reference (959.63"/r with a 3-term distance series). */
    internal fun semidiameter(date: LocalDate): Double {
        val n = (date.toEpochDay() - LocalDate.of(2000, 1, 1).toEpochDay()) - 0.5 + 0.33
        val g = (357.529 + 0.98560028 * n) * PI / 180.0
        val r = 1.00014 - 0.01671 * cos(g) - 0.00014 * cos(2 * g)
        return 959.63 / 3600.0 / r
    }
}
