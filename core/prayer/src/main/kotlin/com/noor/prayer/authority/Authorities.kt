package com.noor.prayer.authority

import com.noor.prayer.CalculationProfile
import com.noor.prayer.Profiles

/**
 * How much official backing a set of times has. Shown to the user for every date, never overstated.
 */
enum class SourceKind {
    /** The authority's own published times for that date, used as published. */
    OFFICIAL_TIMETABLE,
    /** Calculated with conventions inferred from the authority's official timetable and checked against it. */
    VALIDATED_CALCULATION,
    /** Calculated with the parameters the authority is documented to use; not yet checked against its timetable. */
    AUTHORITY_PARAMETERS,
    /** A widely used method; no official local timetable is implied. */
    RECOGNISED_METHOD,
}

/** One prayer-time authority, with where its information comes from. */
data class Authority(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    /** ISO country codes it covers. */
    val countries: Set<String>,
    /** Narrower coverage inside a country (e.g. "AE-DU" for Dubai emirate); null = whole country. */
    val region: String? = null,
    val profile: CalculationProfile,
    /** Basis of the calculated times (dates without an official timetable). */
    val kind: SourceKind,
    /** Official timetable dataset id, when the app carries the authority's own times for some dates. */
    val timetableId: String? = null,
    /** Where the parameters or timetable come from. */
    val sourceUrl: String,
    val sourceNoteEn: String,
)

/**
 * Picks the authority for a place. Dubai emirate → IACAD; the rest of the UAE → Awqaf; other countries map to their
 * recognised authority where one is documented, and otherwise to the Muslim World League method, labelled as a
 * recognised method rather than an official local timetable. The user can always override the choice.
 * Sources and status for every entry: docs/AUTHORITIES.md.
 */
object Authorities {
    const val DUBAI_REGION = "AE-DU"

    val IACAD = Authority(
        "iacad", "IACAD — Dubai (Dulook DXB)", "دائرة الشؤون الإسلامية والعمل الخيري — دبي (دلوك دبي)", setOf("AE"), DUBAI_REGION,
        Profiles.DUBAI_IACAD, SourceKind.VALIDATED_CALCULATION, timetableId = "iacad_dubai",
        sourceUrl = "https://www.iacad.gov.ae/en/prayer-times",
        sourceNoteEn = "Conventions inferred from 588 official IACAD times (Aug 2020 – Nov 2026): 89.6% exact, the rest within 1 minute. Official month Nov 2026 carried as published.",
    )
    val AWQAF = Authority(
        "awqaf", "Awqaf — UAE", "الهيئة العامة للشؤون الإسلامية والأوقاف", setOf("AE"), null,
        Profiles.UAE_AWQAF, SourceKind.VALIDATED_CALCULATION,
        sourceUrl = "https://www.awqaf.gov.ae",
        sourceNoteEn = "Conventions fitted to the official UAE Hijri Calendar for Abu Dhabi (353 days, 2023–2024): 96.2% exact, the rest within 1 minute; all 12 times checked on Awqaf's website (Nov 2026) exact. Other regions calculated at your position, not yet checked.",
    )
    val MUIS = Authority(
        "muis", "MUIS — Singapore", "المجلس الإسلامي في سنغافورة", setOf("SG"), null,
        Profiles.SINGAPORE, SourceKind.VALIDATED_CALCULATION, timetableId = "muis_singapore",
        sourceUrl = "https://data.gov.sg/datasets/d_a6a206cba471fe04b62dd886ef5eaf22/view",
        sourceNoteEn = "Official MUIS timetable 2024–2026 (data.gov.sg, Singapore Open Data Licence) used as published; other dates calculated with conventions fitted to it.",
    )
    val UMM_AL_QURA = Authority(
        "uqu", "Umm al-Qura — Saudi Arabia", "تقويم أم القرى — المملكة العربية السعودية", setOf("SA"), null,
        Profiles.UMM_AL_QURA, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://www.ummulqura.org.sa",
        sourceNoteEn = "Fajr 18.5°, Isha 90 minutes after Maghrib (120 in Ramadan), the Umm al-Qura standard used in the Kingdom.",
    )
    val JAKIM = Authority(
        "jakim", "JAKIM — Malaysia", "دائرة التنمية الإسلامية الماليزية", setOf("MY"), null,
        Profiles.MALAYSIA, SourceKind.VALIDATED_CALCULATION,
        sourceUrl = "https://www.e-solat.gov.my",
        sourceNoteEn = "Fajr 18° (national decision of November 2019), Isha 18°, with precautionary minutes inferred from JAKIM's e-Solat tables (4 zones, 2026): 58–67% exact, 94% within 1 minute. JAKIM's official times are zone timetables computed at each zone's westernmost point; these are calculated for your exact position.",
    )
    val KEMENAG = Authority(
        "kemenag", "Kemenag — Indonesia", "وزارة الشؤون الدينية الإندونيسية", setOf("ID"), null,
        Profiles.INDONESIA, SourceKind.VALIDATED_CALCULATION,
        sourceUrl = "https://bimasislam.kemenag.go.id/jadwalshalat",
        sourceNoteEn = "Fajr −20°, Isha −18° (reaffirmed December 2020), the Sun 1° down at sunrise and Maghrib, 2 minutes of ihtiyat rounded up, Dhuhr +3: against Kemenag's 2026 tables for Jakarta, Makassar and Medan, 93–94% exact and every time within 1 minute.",
    )
    val EGYPT = Authority(
        "egas", "Egyptian General Authority of Survey", "الهيئة المصرية العامة للمساحة", setOf("EG"), null,
        Profiles.EGYPT, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 19.5°, Isha 17.5° (parameters as documented; not checked against an official Egyptian timetable).",
    )
    val KUWAIT = Authority(
        "kwt", "Kuwait — Ministry of Awqaf", "الكويت — وزارة الأوقاف", setOf("KW"), null,
        Profiles.KUWAIT, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 18°, Isha 17.5° (parameters as documented; not checked against an official Kuwaiti timetable).",
    )
    val QATAR = Authority(
        "qat", "Qatar — Ministry of Awqaf", "قطر — وزارة الأوقاف", setOf("QA"), null,
        Profiles.QATAR, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 18°, Isha 90 minutes after Maghrib (parameters as documented; not checked against an official Qatari timetable).",
    )
    val KARACHI = Authority(
        "kar_hanafi", "University of Islamic Sciences, Karachi (Hanafi Asr)", "جامعة العلوم الإسلامية بكراتشي (العصر الحنفي)", setOf("PK", "IN", "BD", "AF"), null,
        Profiles.KARACHI_HANAFI, SourceKind.RECOGNISED_METHOD,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 18°, Isha 18°: the method most used in South Asia, with the Hanafi Asr that most mosques there keep (the standard Asr can be chosen in Settings).",
    )
    /** The same method with the standard Asr, when chosen by hand. */
    val KARACHI_STANDARD = Authority(
        "kar", "University of Islamic Sciences, Karachi", "جامعة العلوم الإسلامية بكراتشي", emptySet(), null,
        Profiles.KARACHI, SourceKind.RECOGNISED_METHOD,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 18°, Isha 18°, the standard Asr (one shadow length).",
    )
    val DIYANET = Authority(
        "diyanet", "Diyanet — Türkiye", "رئاسة الشؤون الدينية التركية", setOf("TR"), null,
        Profiles.TURKIYE, SourceKind.VALIDATED_CALCULATION,
        sourceUrl = "https://namazvakitleri.diyanet.gov.tr",
        sourceNoteEn = "Fajr 18°, Isha 17° with Diyanet's precautionary minutes (temkin). Against Diyanet's own Istanbul year (2026): 64% exact, 98% within 1 minute, all within 2. Other cities not yet measured.",
    )
    val TEHRAN = Authority(
        "teh", "Institute of Geophysics, University of Tehran", "معهد الجيوفيزياء بجامعة طهران", setOf("IR"), null,
        Profiles.TEHRAN, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 17.7°, Isha 14°, Maghrib 4.5° (parameters as documented).",
    )
    val ISNA = Authority(
        "isna", "ISNA — North America", "الجمعية الإسلامية لأمريكا الشمالية", setOf("US", "CA"), null,
        Profiles.ISNA, SourceKind.RECOGNISED_METHOD,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 15°, Isha 15°: widely used in North America; local mosques often publish their own timetables.",
    )
    val MWL = Authority(
        "mwl", "Muslim World League", "رابطة العالم الإسلامي", emptySet(), null,
        Profiles.MWL, SourceKind.RECOGNISED_METHOD,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 18°, Isha 17°: a widely used method. No official local timetable is implied for this place.",
    )

    // ---- 0.18: every other documented national authority, and the recognised method for each other country ----

    private const val PRAYTIMES = "https://praytimes.org/docs/methods"
    private const val ALADHAN = "https://api.aladhan.com/v1/methods"

    val MOROCCO = Authority(
        "habous", "Ministry of Habous and Islamic Affairs — Morocco", "وزارة الأوقاف والشؤون الإسلامية — المغرب", setOf("MA", "EH"), null,
        Profiles.MOROCCO, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://www.habous.gov.ma",
        sourceNoteEn = "Fajr 19°, Isha 17°, Shuruq −3, Dhuhr +5, Maghrib +5 minutes, as documented by secondary references (Aladhan, bladi.net); the ministry publishes its own city timetables, not yet compared.",
    )
    val ALGERIA = Authority(
        "dz_marw", "Ministry of Religious Affairs and Endowments — Algeria", "وزارة الشؤون الدينية والأوقاف — الجزائر", setOf("DZ"), null,
        Profiles.ALGERIA, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = ALADHAN,
        sourceNoteEn = "Fajr 18°, Isha 17°, as documented by secondary references (Aladhan, salahtimes); not checked against the ministry's timetable.",
    )
    val TUNISIA = Authority(
        "tn_mar", "Ministry of Religious Affairs — Tunisia", "وزارة الشؤون الدينية — تونس", setOf("TN"), null,
        Profiles.TUNISIA, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = ALADHAN,
        sourceNoteEn = "Fajr 18°, Isha 18°, as documented by Aladhan (method 18); not checked against the ministry's timetable.",
    )
    val JORDAN = Authority(
        "jo_awqaf", "Ministry of Awqaf, Islamic Affairs and Holy Places — Jordan", "وزارة الأوقاف والشؤون والمقدسات الإسلامية — الأردن", setOf("JO"), null,
        Profiles.JORDAN, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = ALADHAN,
        sourceNoteEn = "Fajr 18°, Isha 18°, Maghrib 5 minutes after sunset, as documented by Aladhan (method 23); not checked against the ministry's timetable.",
    )
    val PORTUGAL = Authority(
        "cil", "Comunidade Islâmica de Lisboa — Portugal", "الجالية الإسلامية في لشبونة — البرتغال", setOf("PT"), null,
        Profiles.PORTUGAL, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = ALADHAN,
        sourceNoteEn = "Fajr 18°, Maghrib 3 minutes after sunset, Isha 77 minutes after Maghrib, as documented by Aladhan (method 22).",
    )
    val RUSSIA = Authority(
        "dum", "Spiritual Administration of Muslims — Russia", "الإدارة الدينية لمسلمي روسيا", setOf("RU"), null,
        Profiles.RUSSIA, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = ALADHAN,
        sourceNoteEn = "Fajr 16°, Isha 15°, as documented by Aladhan (method 14, placed at Ufa). Russia has several spiritual administrations (Tatarstan's publishes its own times); not checked against any of them.",
    )
    val BRUNEI = Authority(
        "bn", "Brunei Darussalam", "بروناي دار السلام", setOf("BN"), null,
        Profiles.BRUNEI, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://bruneiastronomy.org/prayer/",
        sourceNoteEn = "Fajr 20°, Isha 18°, the Brunei setting reported by the Brunei Astronomical Society; the Mufti Department's own timetable not yet compared.",
    )

    val DUMK = Authority(
        "dumk", "Spiritual Administration of Muslims of Kazakhstan (DUMK)", "الإدارة الدينية لمسلمي كازاخستان", setOf("KZ"), null,
        Profiles.KAZAKHSTAN, SourceKind.VALIDATED_CALCULATION,
        sourceUrl = "https://www.muftyat.kz",
        sourceNoteEn = "Conventions inferred from DUMK's own 2026 timetables for 13 cities (api.muftyat.kz): Fajr 15°, Isha 15°, Hanafi Asr, angle-based rule in the white nights, temkin 3 minutes in the south and 5 in the north. 90–94% exact, every time within 1 minute.",
    )
    val IZ_BIH = Authority(
        "iz_bih", "Islamic Community in Bosnia and Herzegovina (Takvim)", "المشيخة الإسلامية في البوسنة والهرسك (التقويم)", setOf("BA"), null,
        Profiles.BOSNIA, SourceKind.VALIDATED_CALCULATION,
        sourceUrl = "https://www.islamskazajednica.ba",
        sourceNoteEn = "An approximation inferred from the Takvim's Sarajevo year 2026 (via vaktija.ba): Fajr 18°, Isha 16°, sunrise −6 and Maghrib +6 minutes, Dhuhr +1. 56% exact, all within 2 minutes; the Takvim's own margins vary through the year and are not reproduced.",
    )

    /** Moonsighting Committee Worldwide: the recognised method for the United Kingdom (made for high latitudes). */
    val MOONSIGHTING = Authority(
        "msc", "Moonsighting Committee Worldwide", "لجنة رؤية الهلال العالمية", setOf("GB"), null,
        Profiles.MOONSIGHTING, SourceKind.RECOGNISED_METHOD,
        sourceUrl = "https://www.moonsighting.com",
        sourceNoteEn = "Fajr and Isha 18° within the committee's seasonal limits: a method widely used in the UK and designed for its long summer twilight. UK mosques publish their own timetables; none is implied.",
    )
    val UOIF = Authority(
        "uoif", "UOIF (Musulmans de France)", "اتحاد المنظمات الإسلامية في فرنسا", setOf("FR"), null,
        Profiles.FRANCE_UOIF, SourceKind.RECOGNISED_METHOD,
        sourceUrl = PRAYTIMES,
        sourceNoteEn = "Fajr 12°, Isha 12°: the UOIF's method, widely used in France. France has no single official authority; many mosques use 15° or 18° instead.",
    )
    val JAFARI = Authority(
        "jafari", "Shia Ithna Ashari (Leva Institute, Qum)", "الشيعة الإمامية (مؤسسة ليفا، قم)", emptySet(), null,
        Profiles.JAFARI, SourceKind.RECOGNISED_METHOD,
        sourceUrl = PRAYTIMES,
        sourceNoteEn = "Fajr 16°, Isha 14°, Maghrib at 4°: the method of Shia communities worldwide (choose it in Settings).",
    )

    /**
     * The Egyptian method where it is the recognised method rather than the national authority: praytimes.org lists
     * it for Africa, Syria and Lebanon. Countries with their own documented authority (Egypt, Morocco, Algeria,
     * Tunisia) are mapped to it instead.
     */
    val EGYPT_REGION = Authority(
        "egas_region", "Egyptian General Authority of Survey (regional method)", "الهيئة المصرية العامة للمساحة (طريقة إقليمية)",
        setOf(
            "SY", "LB", "LY", "SD", "SS",
            // Africa south of the Sahara
            "AO", "BF", "BI", "BJ", "BW", "CD", "CF", "CG", "CI", "CM", "CV", "DJ", "ER", "ET", "GA", "GH", "GM", "GN",
            "GQ", "GW", "KE", "KM", "LR", "LS", "MG", "ML", "MR", "MU", "MW", "MZ", "NA", "NE", "NG", "RW", "SC", "SL",
            "SN", "SO", "ST", "SZ", "TD", "TG", "TZ", "UG", "ZA", "ZM", "ZW",
        ), null,
        Profiles.EGYPT, SourceKind.RECOGNISED_METHOD,
        sourceUrl = PRAYTIMES,
        sourceNoteEn = "Fajr 19.5°, Isha 17.5°: the method praytimes.org lists for Africa, Syria and Lebanon. No official local timetable is implied.",
    )

    /** Umm al-Qura where it is the recognised method of the Arabian Peninsula (praytimes.org), outside Saudi Arabia. */
    val UMM_AL_QURA_REGION = Authority(
        "uqu_region", "Umm al-Qura (Arabian Peninsula)", "أم القرى (شبه الجزيرة العربية)", setOf("BH", "OM", "YE"), null,
        Profiles.UMM_AL_QURA, SourceKind.RECOGNISED_METHOD,
        sourceUrl = PRAYTIMES,
        sourceNoteEn = "Fajr 18.5°, Isha 90 minutes after Maghrib (120 in Ramadan): the method praytimes.org lists for the Arabian Peninsula. No official local timetable is implied.",
    )

    /**
     * Every authority. The first entry for a profile is the one shown for that method when it is chosen by hand
     * ([forProfile]), so national authorities come before the regional uses of the same profile.
     */
    val ALL = listOf(
        IACAD, AWQAF, MUIS, UMM_AL_QURA, JAKIM, KEMENAG, EGYPT, KUWAIT, QATAR, KARACHI, DIYANET, TEHRAN, ISNA, MWL,
        MOROCCO, ALGERIA, TUNISIA, JORDAN, PORTUGAL, RUSSIA, BRUNEI, MOONSIGHTING, UOIF, JAFARI, EGYPT_REGION, UMM_AL_QURA_REGION,
        KARACHI_STANDARD, DUMK, IZ_BIH,
    )

    private val byCountry: Map<String, Authority> by lazy {
        val m = HashMap<String, Authority>()
        for (a in ALL) if (a.region == null) for (c in a.countries) check(m.put(c, a) == null || c == "AE") { "$c is mapped twice" }
        m
    }

    /** Every country with an authority of its own or a recognised method chosen for it (the rest get [MWL]). */
    val mappedCountries: Set<String> get() = byCountry.keys

    /**
     * The authority for a country (ISO 3166-1 alpha-2) and optional region; MWL when the country has no mapped
     * authority. Israel (IL) and Palestine (PS) share one entry (the Muslim World League method), so the same times
     * are produced whichever code a geocoder returns (Flags.displayCountry shows both as Palestine).
     */
    fun forPlace(countryCode: String?, region: String?): Authority {
        val cc = countryCode?.uppercase()
        if (cc == "AE") return if (region == DUBAI_REGION) IACAD else AWQAF
        return cc?.let { byCountry[it] } ?: MWL
    }

    /** The authority whose profile is [profileId] (manual choice in Settings), or null for profiles without one. */
    fun forProfile(profileId: String): Authority? = ALL.firstOrNull { it.profile.id == profileId }
}
