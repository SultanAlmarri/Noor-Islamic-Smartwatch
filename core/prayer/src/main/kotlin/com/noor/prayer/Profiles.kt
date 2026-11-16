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
