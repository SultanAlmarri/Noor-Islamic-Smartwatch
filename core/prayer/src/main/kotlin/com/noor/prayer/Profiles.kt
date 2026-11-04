package com.noor.prayer

import com.noor.prayer.Prayer.ASR
import com.noor.prayer.Prayer.DHUHR
import com.noor.prayer.Prayer.FAJR
import com.noor.prayer.Prayer.ISHA
import com.noor.prayer.Prayer.MAGHRIB
import com.noor.prayer.Prayer.SHURUQ

/**
 * The calculation profiles shipped with the app: the 11 methods of the Noor watch face (with
 * Dubai upgraded to the research candidate C1) plus the UAE Awqaf profile.
 */
object Profiles {

    /**
     * Dubai - IACAD (Dulook DXB). Research candidate C1 (docs/PRAYER_ACCURACY.md):
     * official "Dubai City" reference point modelled at 25.12 N 55.189 E, Fajr/Isha 18.0 deg,
     * horizon = Sun's radius + 34' refraction + 0.10 deg, seconds rounded up, then fixed minutes.
     * Against 588 official IACAD times (Aug 2020 - Nov 2026): 527 exact (89.6%), every other one 1 minute.
     */
    val DUBAI_IACAD = CalculationProfile(
        id = "dubai_iacad",
        nameEn = "Dubai — IACAD (Dulook DXB)",
        nameAr = "دبي — دائرة الشؤون الإسلامية (دلوك دبي)",
        fajrAngle = 18.0, ishaAngle = 18.0,
        horizonExtraDeg = 0.10,
        rounding = Rounding.UP,
        offsets = mapOf(FAJR to -2, SHURUQ to -4, DHUHR to 2, ASR to 0, MAGHRIB to 2, ISHA to 0),
        referencePoint = GeoPoint(25.12, 55.189, 0.0),
        validationEn = "Matches IACAD's official timetable to the minute on 89.6% of 588 times checked (2020–2026); the rest differ by 1 minute.",
        validationAr = "تطابق الجدول الرسمي للدائرة بالدقيقة في 89.6% من 588 وقتًا (2020–2026)، والباقي بفارق دقيقة واحدة.",
    )

    /**
     * UAE - Awqaf, provisional. Fitted to one secondary month (godubai.com, Nov 2026, Dubai):
     * 164/180 times exact (91%). To be re-validated against Awqaf's own tables.
     */
    val UAE_AWQAF = CalculationProfile(
        id = "uae_awqaf",
        nameEn = "UAE — Awqaf (provisional)",
        nameAr = "الإمارات — الهيئة العامة للشؤون الإسلامية والأوقاف (مبدئي)",
        fajrAngle = 18.0, ishaAngle = 18.0,
        horizonExtraDeg = 0.19,
        rounding = Rounding.NEAREST,
        offsets = mapOf(FAJR to -1, SHURUQ to -3, DHUHR to 3, ASR to 3, MAGHRIB to 2, ISHA to 1),
        validationEn = "Provisional: matched 91% of one month of Awqaf-attributed times (Nov 2026); differences up to 1 minute.",
        validationAr = "مبدئي: طابق 91% من أوقات شهر واحد منسوبة للأوقاف (نوفمبر 2026)، بفارق لا يتجاوز دقيقة.",
    )

    private fun method(
        id: String, en: String, ar: String, fajr: Double, isha: Double?, ishaMin: Int? = null,
        ishaMinRamadan: Int? = null, maghrib: Double? = null, offsets: Map<Prayer, Int> = emptyMap(),
    ) = CalculationProfile(
        id = id, nameEn = en, nameAr = ar, fajrAngle = fajr, ishaAngle = isha,
        ishaMinutesAfterMaghrib = ishaMin, ishaMinutesAfterMaghribRamadan = ishaMinRamadan,
        maghribAngle = maghrib, offsets = offsets,
    )

    val MWL = method("mwl", "Muslim World League", "رابطة العالم الإسلامي", 18.0, 17.0, offsets = mapOf(DHUHR to 1))
    val UMM_AL_QURA = method("uqu", "Umm al-Qura (Makkah)", "أم القرى (مكة)", 18.5, null, ishaMin = 90, ishaMinRamadan = 120)
    val EGYPT = method("egy", "Egyptian General Authority of Survey", "الهيئة المصرية العامة للمساحة", 19.5, 17.5, offsets = mapOf(DHUHR to 1))
    val KARACHI = method("kar", "University of Islamic Sciences, Karachi", "جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0, offsets = mapOf(DHUHR to 1))
    val ISNA = method("isna", "North America (ISNA)", "أمريكا الشمالية (ISNA)", 15.0, 15.0, offsets = mapOf(DHUHR to 1))
    val KUWAIT = method("kwt", "Kuwait", "الكويت", 18.0, 17.5)
    val QATAR = method("qat", "Qatar", "قطر", 18.0, null, ishaMin = 90)
    val SINGAPORE = method("sgp", "Singapore (MUIS)", "سنغافورة", 20.0, 18.0, offsets = mapOf(DHUHR to 1))
    val TURKIYE = method("tur", "Türkiye (Diyanet)", "تركيا (ديانت)", 18.0, 17.0,
        offsets = mapOf(SHURUQ to -7, DHUHR to 5, ASR to 4, MAGHRIB to 7))
    val TEHRAN = method("teh", "Tehran (Institute of Geophysics)", "طهران (معهد الجيوفيزياء)", 17.7, 14.0, maghrib = 4.5)

    /** All profiles, in the order shown to the user. */
    val ALL: List<CalculationProfile> = listOf(
        DUBAI_IACAD, UAE_AWQAF, MWL, UMM_AL_QURA, EGYPT, KARACHI, ISNA, KUWAIT, QATAR, SINGAPORE, TURKIYE, TEHRAN,
    )

    fun byId(id: String): CalculationProfile? = ALL.firstOrNull { it.id == id }
}
