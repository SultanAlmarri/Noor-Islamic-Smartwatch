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
     * UAE - Awqaf (outside Dubai). Fitted (16 Nov 2026) to the UAE Hijri Calendar 1445 of the Sheikh Zayed Grand Mosque
     * Centre, Abu Dhabi City column (353 days, Jul 2023 - Jul 2024; times by the International Astronomy Center), held
     * out on alternate months (95.8% and 95.3% exact), and checked on Awqaf's own website (Abu Dhabi region, 16-17 Nov
     * 2026): 12/12 exact. Whole calendar: 2038/2118 exact (96.2%), every other time 1 minute
     * (accuracy/tools/awqaf_fit.py, accuracy/results/EVIDENCE.md). Fajr/Isha 18.1 deg and the reference longitude are
     * equivalent fitted values, not published ones. Inside 60 km of Abu Dhabi the city timetable is used, as Awqaf
     * publishes one table per region; elsewhere the same conventions at the user's own position (not yet checked against
     * Awqaf's tables for other regions).
     */
    val UAE_AWQAF = CalculationProfile(
        id = "uae_awqaf",
        nameEn = "UAE — Awqaf (provisional)",
        nameAr = "الإمارات — الهيئة العامة للشؤون الإسلامية والأوقاف (مبدئي)",
        fajrAngle = 18.1, ishaAngle = 18.1,
        horizonExtraDeg = 0.31,
        rounding = Rounding.NEAREST,
        roundingByPrayer = mapOf(SHURUQ to Rounding.UP, ISHA to Rounding.UP),
        offsets = mapOf(FAJR to -1, SHURUQ to -3, DHUHR to 2, ASR to 0, MAGHRIB to 1, ISHA to -1),
        referencePoint = GeoPoint(24.44, 54.28, 0.0),
        validationEn = "Matches the official UAE calendar for Abu Dhabi to the minute on 96.2% of 2,118 times (2023–2024), the rest by 1 minute; matched Awqaf's website on all 12 times checked (Nov 2026).",
        validationAr = "تطابق التقويم الرسمي لأبوظبي بالدقيقة في 96.2% من 2118 وقتًا (2023–2024)، والباقي بفارق دقيقة؛ وطابقت موقع الهيئة في جميع الأوقات الـ12 التي فُحصت (نوفمبر 2026).",
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
    val ISNA = method("isna", "North America (ISNA)", "أمريكا الشمالية — الجمعية الإسلامية لأمريكا الشمالية", 15.0, 15.0, offsets = mapOf(DHUHR to 1))
    val KUWAIT = method("kwt", "Kuwait", "الكويت", 18.0, 17.5)
    val QATAR = method("qat", "Qatar", "قطر", 18.0, null, ishaMin = 90)
    /**
     * Singapore - MUIS. Fitted to MUIS's official timetable 2024-2025 (data.gov.sg open data), held out on 2026:
     * Fajr 20.0, Isha 18.0, sunset/sunrise at the Sun's upper limb with 34' refraction, rounded up, Dhuhr +1.
     * Exact on about 75% of times, the rest within 1-2 minutes; where MUIS's own table covers a date, the app shows
     * MUIS's times directly (official timetable source), so this profile is only the fallback for other dates.
     */
    val SINGAPORE = CalculationProfile(
        id = "sgp", nameEn = "Singapore — MUIS", nameAr = "سنغافورة — المجلس الإسلامي",
        fajrAngle = 20.0, ishaAngle = 18.0, rounding = Rounding.UP, offsets = mapOf(DHUHR to 1),
        referencePoint = GeoPoint(1.25, 103.80),
        validationEn = "Official MUIS timetable used where published (2024–2026). Other dates: calculated with conventions fitted to it (about 75% exact, the rest within 1–2 minutes).",
        validationAr = "يُستخدم جدول المجلس الإسلامي الرسمي حيث نُشر (2024–2026). التواريخ الأخرى: محسوبة بمعايير مطابقة له (نحو 75% تطابقاً تاماً والباقي بفارق دقيقة إلى دقيقتين).",
    )

    /**
     * Malaysia - JAKIM. Fajr 18 deg nationwide since 1 Ramadan 1441 (April 2020; Muzakarah decision of Nov 2019),
     * Isha 18 deg. JAKIM publishes zone timetables (e-Solat); this profile calculates at the user's position and is
     * not validated against those tables.
     */
    val MALAYSIA = method("jakim", "Malaysia — JAKIM", "ماليزيا — دائرة التنمية الإسلامية", 18.0, 18.0, offsets = mapOf(DHUHR to 1, MAGHRIB to 2)).copy(
        validationEn = "Calculated with JAKIM's published angles (Fajr 18°, Isha 18°). Not validated against JAKIM's zone timetables yet.",
        validationAr = "محسوبة بالزوايا المنشورة لدائرة التنمية الإسلامية (الفجر 18°، العشاء 18°). لم تُقارن بجداول المناطق الرسمية بعد.",
    )

    /** Indonesia - Kementerian Agama: Fajr -20 deg, Isha -18 deg (reaffirmed by Kemenag, December 2020). Not validated against its tables. */
    val INDONESIA = method("kemenag", "Indonesia — Kemenag", "إندونيسيا — وزارة الشؤون الدينية", 20.0, 18.0, offsets = mapOf(DHUHR to 1)).copy(
        validationEn = "Calculated with Kemenag's published angles (Fajr 20°, Isha 18°). Not validated against Kemenag's timetables yet.",
        validationAr = "محسوبة بالزوايا المنشورة لوزارة الشؤون الدينية الإندونيسية (الفجر 20°، العشاء 18°). لم تُقارن بجداولها بعد.",
    )
    val TURKIYE = method("tur", "Türkiye (Diyanet)", "تركيا (ديانت)", 18.0, 17.0,
        offsets = mapOf(SHURUQ to -7, DHUHR to 5, ASR to 4, MAGHRIB to 7))
    val TEHRAN = method("teh", "Tehran (Institute of Geophysics)", "طهران (معهد الجيوفيزياء)", 17.7, 14.0, maghrib = 4.5)

    /** All profiles, in the order shown to the user. */
    val ALL: List<CalculationProfile> = listOf(
        DUBAI_IACAD, UAE_AWQAF, MWL, UMM_AL_QURA, EGYPT, KARACHI, ISNA, KUWAIT, QATAR, SINGAPORE, MALAYSIA, INDONESIA, TURKIYE, TEHRAN,
    )

    fun byId(id: String): CalculationProfile? = ALL.firstOrNull { it.id == id }
}
