package com.noor.prayer

/**
 * A complete, documented calculation convention. Every number here has a source or a
 * validation note (see docs/PRAYER_ACCURACY.md); none is date-specific.
 */
data class CalculationProfile(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val fajrAngle: Double,
    /** Isha depression angle, or null when [ishaMinutesAfterMaghrib] is used. */
    val ishaAngle: Double?,
    val ishaMinutesAfterMaghrib: Int? = null,
    val ishaMinutesAfterMaghribRamadan: Int? = null,
    /** Maghrib depression angle (Tehran style), or null for sunset. */
    val maghribAngle: Double? = null,
    val asrSchool: AsrSchool = AsrSchool.STANDARD,
    /** Extra horizon depression (deg) for sunrise and sunset, beyond the Sun's radius and 34' refraction. */
    val horizonExtraDeg: Double = 0.0,
    /** When true, the observer's elevation lowers the horizon by 0.0353333 * sqrt(h) deg (IAC formula). */
    val elevationAffectsHorizon: Boolean = false,
    val rounding: Rounding = Rounding.NEAREST,
    /** Fixed precaution minutes per event, applied after rounding. */
    val offsets: Map<Prayer, Int> = emptyMap(),
    /** Official reference point: when set, all users of this profile get the authority's published city times. */
    val referencePoint: GeoPoint? = null,
    /** One-line accuracy status shown to the user. */
    val validationEn: String = "Calculated with the method's published parameters.",
    val validationAr: String = "محسوبة وفق المعايير المنشورة للطريقة.",
) {
    fun offset(p: Prayer): Int = offsets[p] ?: 0
}
