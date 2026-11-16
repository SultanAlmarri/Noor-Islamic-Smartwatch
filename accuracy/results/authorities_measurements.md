# Prayer-time authorities: the app against their own published times

Measured 16 November 2026. Every figure below is produced by code in this repository and locked by
`core/prayer/src/test/kotlin/com/noor/prayer/AuthorityTablesTest.kt`; none is estimated. (IACAD and Awqaf, the UAE's
authorities, are covered in `EVIDENCE.md` and `docs/PRAYER_ACCURACY.md`.)

## How it was measured

1. **The official times.** `accuracy/tools/fetch_authority_tables.py` downloads one year (2026) per city from each
   authority's own public service and stores it, as published, in `core/prayer/src/test/resources/authorities/`
   (one CSV per city; the first lines say where it came from and when):

   | Authority | Service | Cities |
   | --- | --- | --- |
   | Diyanet (Türkiye) | namazvakitleri.diyanet.gov.tr, the page's yearly table | Istanbul |
   | JAKIM (Malaysia) | e-Solat API, e-solat.gov.my | zones WLY01 (Kuala Lumpur), JHR02 (Johor Bahru), PNG01 (Penang), KTN01 (Kota Bharu) |
   | Kemenag (Indonesia) | Kemenag's schedule as relayed by api.myquran.com (a third-party relay, not Kemenag's own service) | Jakarta, Makassar, Medan |
   | DUMK (Kazakhstan) | api.muftyat.kz, with each city's own coordinates from the same service | 13 cities from 42.4° N to 52.3° N |
   | Islamic Community of Bosnia and Herzegovina (Takvim) | vaktija.ba's public API, which publishes the Takvim | Sarajevo |

2. **The app's times.** Exactly what the phone shows with default settings: `PrayerEngine.compute` with the profile
   the app picks for that country, at the city's coordinates, in the local time zone (Kazakhstan: UTC+5, its single
   zone since March 2024).
3. **Conventions inferred from a table** (`core/prayer/src/test/kotlin/com/noor/prayer/research/AuthorityFit.kt`): for
   each prayer separately, one angle (or Asr shadow, or horizon), one rounding rule and ONE constant number of minutes
   for the whole year, chosen on January–June and scored on July–December, which the choice never saw. No date is
   adjusted individually. Where several cities exist, the result is also checked on cities never used for fitting.
4. **Run it again:** `bash accuracy/tools/measure_authorities.sh` (writes `authorities_measurements_table.md`).

"Exact" means the same minute as published; differences are published minus app.

## Results (all 365 days of 2026, six times a day)

| Official table | App profile | Exact | Within 1 min | Within 2 min | Largest difference |
| --- | --- | --- | --- | --- | --- |
| Diyanet, Istanbul | Diyanet (unchanged) | **1392/2190 (64%)** | 2143 (98%) | 2190 (100%) | 2 min |
| JAKIM WLY01 Kuala Lumpur | JAKIM before | 255/2190 (12%) | 1141 (52%) | 1995 (91%) | 3 min |
| | **JAKIM now** | **1318/2190 (60%)** | 2123 (97%) | 2190 (100%) | 2 min |
| JAKIM JHR02 Johor Bahru | JAKIM before | 378/2190 (17%) | 1656 (76%) | 2190 | 2 min |
| | **JAKIM now** | **1459/2190 (67%)** | 2168 (99%) | 2190 (100%) | 2 min |
| JAKIM PNG01 Penang | JAKIM before | 88/2190 (4%) | 1687 (77%) | 2190 | 2 min |
| | **JAKIM now** | **1267/2190 (58%)** | 2149 (98%) | 2190 (100%) | 2 min |
| JAKIM KTN01 Kota Bharu | JAKIM before | 371/2190 (17%) | 1216 (56%) | 1939 (89%) | 4 min |
| | **JAKIM now** | **1330/2190 (61%)** | 1802 (82%) | 1825 (83%) | 5 min (Isha) |
| Kemenag Jakarta | Kemenag before | 0/2190 (0%) | 0 | 664 (30%) | 4 min |
| | **Kemenag now** | **2026/2190 (93%)** | 2190 (100%) | 2190 | 1 min |
| Kemenag Makassar (never used for fitting) | Kemenag before | 0/2190 | 0 | 664 (30%) | 4 min |
| | **Kemenag now** | **2057/2190 (94%)** | 2190 (100%) | 2190 | 1 min |
| Kemenag Medan (never used for fitting) | Kemenag before | 0/2190 | 0 | 841 (38%) | 4 min |
| | **Kemenag now** | **2027/2190 (93%)** | 2190 (100%) | 2190 | 1 min |
| DUMK, 13 Kazakh cities | Muslim World League before (e.g. Shymkent) | 0/2190 | 1 | 367 (17%) | 77 min |
| | **DUMK now**, each city | **1977–2052/2190 (90–94%)** | 2190 (100%) in every city | 2190 | 1 min |
| Takvim, Sarajevo | Muslim World League before | 806/2190 (37%) | 1089 (50%) | 1095 (50%) | 11 min |
| | **Takvim approximation now** | **1236/2190 (56%)** | 2136 (98%) | 2190 (100%) | 2 min |

The full table with per-prayer counts, all 13 Kazakh cities and the per-city rows: `authorities_measurements_table.md`.

## What each profile is, and how sure we are

- **Diyanet** (unchanged): Fajr 18°, Isha 17°, temkin sunrise −7, Dhuhr +5, Asr +4, Maghrib +7. Good to the minute on
  64% of Istanbul's year and never more than 2 minutes off. Only Istanbul measured; Diyanet's own reference points for
  each district are not published.
- **JAKIM**: Fajr and Isha 18° (the 2019 decision), times rounded up, then Fajr +1, sunrise −1, Dhuhr +2, Asr +1,
  Maghrib +1, Isha +1 minutes. JAKIM is documented to add precautionary minutes (ihtiyati) without a published amount;
  these amounts were found in JAKIM's own tables of four zones, the same for all four. What remains: JAKIM computes each
  zone at its westernmost point (sunrise at the easternmost; Malaysian academic sources on the e-Solat zones), and the
  app at the user's position, so the residual is about ±1 minute and depends on the zone; Kelantan's Isha (KTN01) is
  about 4 minutes earlier than 18° gives in all 365 days, so Kelantan probably uses a different Isha, not modelled.
  Without JAKIM's zone boundaries the zone mechanism cannot be reproduced.
- **Kemenag**: Fajr 20°, Isha 18°, 2 minutes of ihtiyat on every time (sunrise −2), as documented (Kemenag's
  criteria as published by BHR Mimika); the Sun's centre 1° below the horizon at sunrise and Maghrib (one academic
  source, a UINSA thesis). Found in Kemenag's tables, not documented: the rounding up, and Dhuhr 3 minutes after the
  meridian, one more than the documented 2. Fitted on Jakarta's
  first half-year; Makassar and Medan, on other islands and in other time zones, were never used and agree as well
  (93–94% exact, never more than 1 minute). High confidence.
- **DUMK (Kazakhstan), new**: Fajr 15°, Isha 15°, the Hanafi Asr, where 15° is not reached in summer nights the
  angle-based rule (a quarter of the night), and a temkin of 3 minutes (sunrise −3, Dhuhr, Asr and Maghrib +3) in the
  eight cities up to 47.97° N and of 5 minutes in the five from 49.41° N. Nothing is published between those
  latitudes; the app switches at 48.7° N, so a place between 48.0° and 49.4° N may be 2 minutes off on four of the
  times. High confidence elsewhere: 13 cities, 90–94% exact, every time within 1 minute.
- **Takvim (Bosnia and Herzegovina), new, an approximation**: Fajr 18°, Isha 16°, sunrise 6 minutes before and Maghrib
  6 minutes after the standard horizon, Dhuhr +1. The Takvim's margins at sunrise and Maghrib change through the year
  (5 to 7 minutes) and its Isha differs from 16° by up to 2 minutes in summer; no simple rule reproduces them (fitted on
  January–June, the best constant rules fall to 12–18% exact on July–December for those three times). Medium confidence;
  much closer than the method used before (never more than 2 minutes instead of up to 11).

## Searched, not measured

- **Umm al-Qura (Saudi Arabia)**: ummulqura.org.sa serves its times to its own page through a signed API; not used.
- **Morocco (Habous)**: the ministry's site was under maintenance (16 November 2026), its tables unavailable.
- **Egypt, Qatar, Kuwait, Bahrain, Oman, Iraq, Palestine, Azerbaijan, Uzbekistan, Kyrgyzstan, Tajikistan,
  Turkmenistan, Nigeria, South Africa**: no official machine-readable or plain published table was found in this
  session (Qatar's ministry publishes a printed desk calendar; Egypt's survey authority's own site was not found;
  for South Africa vendors disagree on whether the MJC follows MWL or Karachi). Their profiles stay as documented in
  `docs/AUTHORITIES.md`.
