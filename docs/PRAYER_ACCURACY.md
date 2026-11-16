# Prayer-time accuracy

Status on 4 November 2026. Every figure here is produced by tests in this repository; none is estimated.

## Dubai — IACAD (Dulook DXB)

The app's Dubai profile is research candidate **C1**:

| Element | Value | Status |
|---|---|---|
| Reference point (IACAD's "Dubai City") | 25.12° N, 55.189° E | Deduced from official data (latitude ±0.03°; longitude is an equivalent value, see below) |
| Fajr / Isha | Sun 18.0° below the horizon | Deduced; 18.0° fits 2017–2020 exactly, current era fits 17.4–18.2° |
| Sunrise / Maghrib horizon | Sun's radius + 34′ refraction + 0.10° | Deduced |
| Dhuhr | Meridian transit | Deduced (96 of 98 days fit) |
| Asr | One shadow length | Deduced |
| Rounding | Seconds rounded up to the minute | Equivalent to round-to-nearest at 55.31° E with one more minute on every prayer; the two give identical times |
| Precaution minutes | Fajr −2, Shuruq −4, Dhuhr +2, Asr 0, Maghrib +2, Isha 0 | Recovered unchanged from 2020–2023 alone and from November 2026 alone |

**Result against 588 official IACAD times (98 days, 20 Aug 2020 – 30 Nov 2026): 527 exact (89.6%); all 61 others
differ by exactly 1 minute.** By prayer: Fajr 82/98, Shuruq 90, Dhuhr 91, Asr 91, Maghrib 87, Isha 86. The test
`PrayerEngineTest.iacadOfficialAgreementIsAsDocumented` locks these numbers, and every mismatch is listed in
`accuracy/results/discrepancies_candidate_C1__lat_25_12__E3.csv`.

The remaining 1-minute differences are unexplained. Every transparent model tested leaves 7–13% of times on the other
side of a minute boundary; IACAD's tables also show manual edits in earlier years. No date-specific adjustment is used.

### Official data used

| Source | Dates | Days |
|---|---|---|
| IACAD website feed (iacad.gov.ae/en/prayer-times) | Nov 2026 | 30 |
| IACAD SmartPortal pages archived by the Internet Archive | Nov 2014 – Dec 2023 | 503 |

IACAD changed conventions with the 1439 AH calendar (21 Sep 2017) and the 1442 AH calendar (20 Aug 2020); only data
from 20 Aug 2020 validates the current profile. Details: `accuracy/README.md`.

### Engine verification

The Kotlin engine (`core/prayer`) is a port of the NREL Solar Position Algorithm. Against the research reference
(pvlib) it agrees to **0.007 s** on all 37,254 Dubai event times 2014–2030 and to 0.005 s on 1,560 times for Makkah,
Cairo, New York, Jakarta and Karachi (`PrayerEngineTest`, also runnable without Gradle via `tools/verify/LocalVerify.kt`).

## Hijri date

The app uses the Umm al-Qura calendar built into Java, with a ±2-day user adjustment. It can differ from the UAE
calendar by a day: on 4 November 2026 Umm al-Qura gives 24 Jumada al-Ula 1448, IACAD's feed gives 23.

## Other authorities

| Profile | Status |
|---|---|
| UAE — Awqaf | Provisional: 164/180 (91%) against one secondary month (godubai.com, November 2026). To be validated against Awqaf's own tables |
| Muslim World League, Umm al-Qura, Egypt, Karachi, ISNA, Kuwait, Qatar, Singapore, Türkiye, Tehran | The methods' published parameters, as in watch face 1.3.2; not yet compared with each authority's own tables |

## Continuing tests

- **Blind test:** C1 is frozen; each new official IACAD month is scored when it is published.
- **Historical:** `accuracy/tools/validate.py` re-scores every candidate against every era.
