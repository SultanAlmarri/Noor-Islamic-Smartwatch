package com.noor.prayer

import com.noor.prayer.Prayer.ASR
import com.noor.prayer.Prayer.DHUHR
import com.noor.prayer.Prayer.FAJR
import com.noor.prayer.Prayer.ISHA
import com.noor.prayer.Prayer.MAGHRIB
import com.noor.prayer.Prayer.SHURUQ

/**
 * The calculation profiles shipped with the app: the 11 methods of the Noor watch face (with
 * Dubai upgraded to the research candidate C1), the UAE Awqaf profile, Malaysia, Indonesia, and (0.18) the other
 * national and community methods with documented parameters. Sources and status: docs/AUTHORITIES.md.
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
    val MALAYSIA = CalculationProfile(
        id = "jakim", nameEn = "Malaysia — JAKIM", nameAr = "ماليزيا — دائرة التنمية الإسلامية",
        fajrAngle = 18.0, ishaAngle = 18.0, rounding = Rounding.UP,
        offsets = mapOf(FAJR to 1, SHURUQ to -1, DHUHR to 2, ASR to 1, MAGHRIB to 1, ISHA to 1),
        validationEn = "JAKIM's angles (Fajr 18°, Isha 18°) with precautionary minutes inferred from JAKIM's e-Solat tables (4 zones, 2026): 58–67% of times to the minute, 94% within 1 minute, all within 2 except Kota Bharu's Isha (Kelantan's table is about 4 minutes earlier). JAKIM calculates each zone at its westernmost point; the app calculates at your position, so in the east of a zone times can be up to 2 minutes earlier than JAKIM's table.",
        validationAr = "زوايا الدائرة (الفجر 18°، العشاء 18°) مع دقائق احتياط مستنتجة من جداول الدائرة الرسمية (4 مناطق، 2026): 58–67% من الأوقات بالدقيقة، و94% بفارق دقيقة واحدة، والجميع ضمن دقيقتين إلا عشاء كوتا بهارو (جدول كلنتن أبكر بنحو 4 دقائق). تحسب الدائرة كل منطقة عند أقصى نقطة غربية فيها، ويحسب التطبيق عند موقعك، فقد تبكر الأوقات في شرق المنطقة حتى دقيقتين عن جدول الدائرة.",
    )

    /**
     * Indonesia - Kementerian Agama: Fajr -20 deg, Isha -18 deg (reaffirmed by Kemenag, December 2020), the Sun's
     * centre 1 deg below the horizon at sunrise and sunset (one academic source, a UINSA thesis), ihtiyat 2 minutes
     * (sunrise -2) as documented (BHR Mimika, after Kemenag); the rounding up, and Dhuhr 3 minutes after transit (the
     * documented 2 and one more), were found in Kemenag's own tables. Fitted on Jakarta January-June 2026, checked on
     * the rest of the year and on Makassar and Medan.
     */
    val INDONESIA = CalculationProfile(
        id = "kemenag", nameEn = "Indonesia — Kemenag", nameAr = "إندونيسيا — وزارة الشؤون الدينية",
        fajrAngle = 20.0, ishaAngle = 18.0, horizonExtraDeg = 1.0 - (0.2667 + 0.5667), rounding = Rounding.UP,
        offsets = mapOf(FAJR to 2, SHURUQ to -3, DHUHR to 3, ASR to 2, MAGHRIB to 2, ISHA to 2),
        validationEn = "Kemenag's angles and precautionary minutes (ihtiyat), checked against Kemenag's tables for 2026: 93–94% of times to the minute and every time within 1 minute in Jakarta, Makassar and Medan.",
        validationAr = "زوايا الوزارة ودقائق الاحتياط، وقد قورنت بجداول الوزارة لعام 2026: 93–94% من الأوقات بالدقيقة، وجميعها بفارق لا يتجاوز دقيقة في جاكرتا ومكاسر وميدان.",
    )
    val TURKIYE = method("tur", "Türkiye (Diyanet)", "تركيا (ديانت)", 18.0, 17.0,
        offsets = mapOf(SHURUQ to -7, DHUHR to 5, ASR to 4, MAGHRIB to 7)).copy(
        validationEn = "Diyanet's angles and temkin minutes. Against Diyanet's own Istanbul year (2026): 64% of times to the minute, 98% within 1 minute, all within 2.",
        validationAr = "زوايا رئاسة الشؤون الدينية ودقائق التمكين. مقارنةً بجدولها الرسمي لإسطنبول (2026): 64% من الأوقات بالدقيقة، و98% بفارق دقيقة، والجميع ضمن دقيقتين.",
    )
    val TEHRAN = method("teh", "Tehran (Institute of Geophysics)", "طهران (معهد الجيوفيزياء)", 17.7, 14.0, maghrib = 4.5)

    // ---- 0.18: the other documented national and community methods (docs/AUTHORITIES.md has every source) ----

    private const val SECONDARY_EN = "Calculated with the parameters documented for this authority by widely used prayer-time references; not yet checked against its own timetable."
    private const val SECONDARY_AR = "محسوبة بالمعايير الموثقة لهذه الجهة في المراجع الشائعة لمواقيت الصلاة؛ لم تُقارن بجدولها الرسمي بعد."

    private fun CalculationProfile.note(en: String, ar: String) = copy(validationEn = en, validationAr = ar)

    /**
     * Moonsighting Committee Worldwide (Syed Khalid Shaukat): Fajr and Isha 18 deg, bounded by the committee's
     * seasonal minutes from sunrise and sunset (general shafaq), a seventh of the night above 55 N; Dhuhr +5 and
     * Maghrib +3 minutes. Parameters as in Batoul Apps' Adhan libraries (MoonsightingCommittee).
     */
    val MOONSIGHTING = CalculationProfile(
        id = "msc", nameEn = "Moonsighting Committee Worldwide", nameAr = "لجنة رؤية الهلال العالمية",
        fajrAngle = 18.0, ishaAngle = 18.0, seasonalTwilight = Shafaq.GENERAL,
        offsets = mapOf(DHUHR to 5, MAGHRIB to 3),
        validationEn = "The committee's published method (18° with its seasonal limits, made for high latitudes). Reproduces the Adhan library's reference times for London; a recognised method, not an official timetable.",
        validationAr = "طريقة اللجنة المنشورة (18° مع حدودها الموسمية المصممة للعروض العليا). تطابق الأوقات المرجعية لمكتبة «أذان» البرمجية في لندن؛ طريقة معتمدة شائعة وليست جدولاً رسمياً.",
    )

    /** Shia Ithna Ashari, Leva Institute, Qum: Fajr 16, Isha 14, Maghrib when the Sun is 4 deg down (praytimes.org). */
    val JAFARI = method("jafari", "Shia Ithna Ashari (Leva Institute, Qum)", "الشيعة الإمامية (مؤسسة ليفا، قم)", 16.0, 14.0, maghrib = 4.0)
        .note("Fajr 16°, Isha 14°, Maghrib at 4° (after the eastern redness is gone), as published for the Leva Institute, Qum.",
            "الفجر 16°، العشاء 14°، المغرب عند 4° (بعد ذهاب الحمرة المشرقية)، كما نُشرت لمؤسسة ليفا في قم.")

    /** France - UOIF (now Musulmans de France): Fajr and Isha 12 deg (praytimes.org, Aladhan method 12). */
    val FRANCE_UOIF = method("uoif", "France — UOIF (Musulmans de France)", "فرنسا — اتحاد المنظمات الإسلامية (مسلمو فرنسا)", 12.0, 12.0)
        .note("Fajr 12°, Isha 12°: the UOIF's method, widely used in France. France has no single official authority; many mosques use 15° or 18°.",
            "الفجر 12°، العشاء 12°: طريقة اتحاد المنظمات الإسلامية الشائعة في فرنسا. لا توجد في فرنسا جهة رسمية واحدة، وتعتمد مساجد كثيرة 15° أو 18°.")

    /** Russia - Spiritual Administration of Muslims: Fajr 16, Isha 15 (Aladhan method 14). */
    val RUSSIA = method("dumr", "Russia — Spiritual Administration of Muslims", "روسيا — الإدارة الدينية لمسلمي روسيا", 16.0, 15.0)
        .note(SECONDARY_EN, SECONDARY_AR)

    /** Tunisia - Ministry of Religious Affairs: Fajr 18, Isha 18 (Aladhan method 18). */
    val TUNISIA = method("tun", "Tunisia — Ministry of Religious Affairs", "تونس — وزارة الشؤون الدينية", 18.0, 18.0)
        .note(SECONDARY_EN, SECONDARY_AR)

    /** Algeria - Ministry of Religious Affairs and Endowments: Fajr 18, Isha 17 (Aladhan method 19). */
    val ALGERIA = method("dza", "Algeria — Ministry of Religious Affairs and Endowments", "الجزائر — وزارة الشؤون الدينية والأوقاف", 18.0, 17.0)
        .note(SECONDARY_EN, SECONDARY_AR)

    /**
     * Morocco - Ministry of Habous and Islamic Affairs: Fajr 19, Isha 17 (Aladhan method 21); Shuruq -3, Dhuhr +5,
     * Maghrib +5 minutes (as documented by bladi.net for the ministry's method).
     */
    val MOROCCO = method("mar", "Morocco — Ministry of Habous and Islamic Affairs", "المغرب — وزارة الأوقاف والشؤون الإسلامية", 19.0, 17.0,
        offsets = mapOf(SHURUQ to -3, DHUHR to 5, MAGHRIB to 5))
        .note(SECONDARY_EN, SECONDARY_AR)

    /** Jordan - Ministry of Awqaf, Islamic Affairs and Holy Places: Fajr 18, Isha 18, Maghrib 5 minutes after sunset (Aladhan method 23). */
    val JORDAN = method("jor", "Jordan — Ministry of Awqaf", "الأردن — وزارة الأوقاف والشؤون والمقدسات الإسلامية", 18.0, 18.0,
        offsets = mapOf(MAGHRIB to 5))
        .note(SECONDARY_EN, SECONDARY_AR)

    /**
     * Portugal - Comunidade Islamica de Lisboa: Fajr 18, Maghrib 3 minutes after sunset, Isha 77 minutes after that
     * Maghrib (Aladhan method 22). The engine counts Isha's interval from sunset, hence 3 + 77 = 80.
     */
    val PORTUGAL = method("por", "Portugal — Comunidade Islâmica de Lisboa", "البرتغال — الجالية الإسلامية في لشبونة", 18.0, null,
        ishaMin = 80, offsets = mapOf(MAGHRIB to 3))
        .note(SECONDARY_EN, SECONDARY_AR)

    /** Brunei: Fajr 20, Isha 18, the setting the Brunei Astronomical Society keeps as Brunei Darussalam's. */
    val BRUNEI = method("brn", "Brunei Darussalam (Fajr 20°, Isha 18°)", "بروناي دار السلام (الفجر 20°، العشاء 18°)", 20.0, 18.0)
        .note("Fajr 20°, Isha 18°, the Brunei setting as reported by the Brunei Astronomical Society; not checked against the Mufti Department's timetable.",
            "الفجر 20°، العشاء 18°، إعداد بروناي كما تذكره الجمعية الفلكية في بروناي؛ لم يُقارن بجدول دائرة الإفتاء.")

    // ---- 0.18, round 2: conventions inferred from the authorities' own published tables ----

    /** Karachi's angles with the Hanafi Asr (twice the shadow), the practice of most mosques in South Asia. */
    val KARACHI_HANAFI = KARACHI.copy(
        id = "kar_hanafi", nameEn = "University of Islamic Sciences, Karachi — Hanafi Asr", nameAr = "جامعة العلوم الإسلامية بكراتشي — العصر الحنفي",
        asrSchool = AsrSchool.HANAFI,
        validationEn = "Fajr 18°, Isha 18°, Asr when the shadow is twice an object's length (Hanafi), as most mosques in Pakistan, India, Bangladesh and Afghanistan keep it. A recognised method, not an official timetable.",
        validationAr = "الفجر 18°، العشاء 18°، والعصر حين يصير الظل مثلي الشيء (المذهب الحنفي)، كما تعمل به أكثر المساجد في باكستان والهند وبنغلاديش وأفغانستان. طريقة معتمدة شائعة وليست جدولاً رسمياً.",
    )

    /**
     * Kazakhstan - DUMK (Spiritual Administration of Muslims of Kazakhstan): inferred from its own tables
     * (api.muftyat.kz, 13 cities, 2026): Fajr 15, Isha 15, the Hanafi Asr, the angle-based rule where twilight lasts
     * all night, and a temkin of 3 minutes (sunrise -3, Dhuhr, Asr and Maghrib +3) up to 47.97 N (Ayagoz) and of 5
     * minutes from 49.41 N (Karkaraly). Where the change happens in between is not known: Noor uses the middle, 48.7 N.
     */
    val KAZAKHSTAN = CalculationProfile(
        id = "dumk", nameEn = "Kazakhstan — Spiritual Administration of Muslims (DUMK)", nameAr = "كازاخستان — الإدارة الدينية لمسلمي كازاخستان",
        fajrAngle = 15.0, ishaAngle = 15.0, asrSchool = AsrSchool.HANAFI,
        offsets = mapOf(SHURUQ to -3, DHUHR to 3, ASR to 3, MAGHRIB to 3),
        offsetsFromLatitude = 48.7,
        offsetsAtHighLatitude = mapOf(SHURUQ to -5, DHUHR to 5, ASR to 5, MAGHRIB to 5),
        validationEn = "Conventions inferred from DUMK's own timetables (13 cities, 2026): 90–94% of times to the minute and every time within 1 minute in each city.",
        validationAr = "معايير مستنتجة من الجداول الرسمية للإدارة الدينية (13 مدينة، 2026): 90–94% من الأوقات بالدقيقة، وجميعها بفارق لا يتجاوز دقيقة في كل مدينة.",
    )

    /**
     * Bosnia and Herzegovina - the Islamic Community's Takvim, inferred from its published Sarajevo year (2026, via
     * vaktija.ba): Fajr 18, Isha 16, sunrise 6 minutes earlier and Maghrib 6 minutes later than the Sun's limb, Dhuhr
     * +1. The Takvim's sunrise and Maghrib margins vary through the year (5 to 7 minutes) and it gives other places by
     * corrections from Sarajevo; neither is reproduced, so this is an approximation.
     */
    val BOSNIA = CalculationProfile(
        id = "iz_bih", nameEn = "Bosnia and Herzegovina — Islamic Community (Takvim)", nameAr = "البوسنة والهرسك — المشيخة الإسلامية (التقويم)",
        fajrAngle = 18.0, ishaAngle = 16.0,
        offsets = mapOf(SHURUQ to -6, DHUHR to 1, MAGHRIB to 6),
        validationEn = "Approximation inferred from the Takvim's Sarajevo year (2026): 56% of times to the minute and all within 2 minutes (the Muslim World League method differs there by up to 11 minutes). Other places in the country are calculated at their own position.",
        validationAr = "تقريب مستنتج من تقويم المشيخة لسراييفو (2026): 56% من الأوقات بالدقيقة، وجميعها ضمن دقيقتين (بينما تختلف طريقة رابطة العالم الإسلامي هناك حتى 11 دقيقة). تُحسب الأماكن الأخرى في البلاد عند موقعها.",
    )

    /** All profiles, in the order shown to the user. */
    val ALL: List<CalculationProfile> = listOf(
        DUBAI_IACAD, UAE_AWQAF, MWL, UMM_AL_QURA, EGYPT, KARACHI, ISNA, KUWAIT, QATAR, SINGAPORE, MALAYSIA, INDONESIA, TURKIYE, TEHRAN,
        MOONSIGHTING, JAFARI, MOROCCO, ALGERIA, TUNISIA, JORDAN, RUSSIA, FRANCE_UOIF, PORTUGAL, BRUNEI,
        KARACHI_HANAFI, KAZAKHSTAN, BOSNIA,
    )

    fun byId(id: String): CalculationProfile? = ALL.firstOrNull { it.id == id }
}
