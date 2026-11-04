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
        sourceNoteEn = "Provisional: fitted to one month of Awqaf-attributed times (Nov 2026), 91% exact; to be re-checked against Awqaf's own tables.",
    )
    val MUIS = Authority(
        "muis", "MUIS — Singapore", "المجلس الإسلامي في سنغافورة (MUIS)", setOf("SG"), null,
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
        "jakim", "JAKIM — Malaysia", "دائرة التنمية الإسلامية الماليزية (JAKIM)", setOf("MY"), null,
        Profiles.MALAYSIA, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://www.e-solat.gov.my",
        sourceNoteEn = "Fajr 18° nationwide since Ramadan 1441 (2020), Isha 18°. JAKIM's official times are zone timetables; these are calculated for your exact position.",
    )
    val KEMENAG = Authority(
        "kemenag", "Kemenag — Indonesia", "وزارة الشؤون الدينية الإندونيسية", setOf("ID"), null,
        Profiles.INDONESIA, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://bimasislam.kemenag.go.id/jadwalshalat",
        sourceNoteEn = "Fajr −20°, Isha −18°, as reaffirmed by Kemenag in December 2020.",
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
        "kar", "University of Islamic Sciences, Karachi", "جامعة العلوم الإسلامية بكراتشي", setOf("PK", "IN", "BD", "AF"), null,
        Profiles.KARACHI, SourceKind.RECOGNISED_METHOD,
        sourceUrl = "https://praytimes.org/docs/methods",
        sourceNoteEn = "Fajr 18°, Isha 18°: the method most used in South Asia. Many mosques there use the Hanafi Asr (change it in Settings).",
    )
    val DIYANET = Authority(
        "diyanet", "Diyanet — Türkiye", "رئاسة الشؤون الدينية التركية", setOf("TR"), null,
        Profiles.TURKIYE, SourceKind.AUTHORITY_PARAMETERS,
        sourceUrl = "https://namazvakitleri.diyanet.gov.tr",
        sourceNoteEn = "Fajr 18°, Isha 17° with Diyanet's precautionary minutes (temkin). Approximation of Diyanet's method; not checked against its published times.",
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

    val ALL = listOf(IACAD, AWQAF, MUIS, UMM_AL_QURA, JAKIM, KEMENAG, EGYPT, KUWAIT, QATAR, KARACHI, DIYANET, TEHRAN, ISNA, MWL)

    /** The authority for a country (ISO code) and optional region; MWL when the country has no mapped authority. */
    fun forPlace(countryCode: String?, region: String?): Authority {
        val cc = countryCode?.uppercase()
        if (cc == "AE") return if (region == DUBAI_REGION) IACAD else AWQAF
        return ALL.firstOrNull { cc != null && cc in it.countries && it.region == null } ?: MWL
    }

    /** The authority whose profile is [profileId] (manual choice in Settings), or null for profiles without one. */
    fun forProfile(profileId: String): Authority? = ALL.firstOrNull { it.profile.id == profileId }
}
