
## The answer in plain words (16 November 2026)

- **No authority's timetable is reproduced 100% by Noor's calculation, and for IACAD it cannot be by any single rule.**
  The best the app does by calculation: Awqaf (Abu Dhabi) 96.2%, IACAD 89.6%; every other IACAD and Awqaf time is
  1 minute off, never more.
- **100% is reached only by carrying the authority's own table.** Noor shows the official times exactly, by
  construction, on the dates it carries them: IACAD, 97 official days (2021–2023 and November 2026); MUIS Singapore,
  2024–2026 (refreshed from data.gov.sg on the phone). On every other date the times are calculated.
- Nothing in this file is a test on a phone or watch: these are the app's own engine and profiles, run on a computer,
  compared with published tables.

<!-- tables -->

## IACAD (Dubai): why calculation stops short of 100%

1. **The test that settles it.** Dhuhr is the Sun crossing the meridian: it depends on nothing but the longitude and
   the equation of time. If IACAD's Dhuhr came from one smooth calculation plus one fixed rule (any rounding, any
   precaution minutes), the differences "official minute minus calculated time" would all fit inside one minute.
   They do not: they spread over **65 s** (98 official days). So no constant rule can give every Dhuhr exactly, and the
   same holds for every other prayer (spreads 62–86 s).
2. **Simpler ephemerides do not explain it** (`ephemeris_variants.py`, results in `results/ephemeris_variants.txt`).
   Tested: the USNO approximate Sun and Meeus's low-precision Sun (each at the event, at local noon and at 0 h UT),
   Spencer's equation-of-time series, and "perpetual" tables copied from one base year (2016–2027). None brings the
   Dhuhr spread under 60 s (best 62.2 s). With the best possible constant per prayer, fitted to the data (an upper
   bound), the ceiling is 550/588 with the app's NREL SPA, 553 with Meeus, 554 with a 2026 perpetual table: no
   variant comes near 588.
3. **Refitting does not help on unseen days** (`iacad_refit.py`, `results/iacad_refit.txt`). Richer rule sets
   (separate Sunrise/Maghrib horizons, rounding chosen per prayer, all angles and the reference point re-fitted) reach
   544/588 on the days they were fitted to, but on days they never saw they do worse than the current profile C1:
   fitted on IACAD's own 98 days and tested on 61 Emirates 24|7 days, 293/366 against C1's 300/366; fitted on
   2020–2023 and tested on 2026 + Emirates 24|7, 451/546 and 447/546 against C1's 470/546. C1 stays. Its numbers are
   unchanged: 527/588 official, 39/42 on the Dulook DXB recording, 305/372 on the Emirates 24|7 copy.
4. The remaining 1-minute differences are therefore not a fault a better formula can fix from the outside: IACAD's
   program differs from any standard model by up to about ±3 s on the day, or edits its tables. Only IACAD's own
   table gives every minute.

**Data added for this check:** 62 days of 2023–2026 (May 2023 – March 2026) from the Internet Archive's captures of
Emirates 24|7's prayer page, which republishes IACAD (it matched IACAD's own feed on all 180 times of November 2026).
It is a secondary copy, used to test, never shown in the app. Its dates were cleaned without looking at any model:
captures made between 20:00 and 04:00 Dubai time were dropped (two such pairs disagreed), and so were the months
(September 2025 – February 2026) when the page showed the same day's times every day.

## Awqaf (UAE outside Dubai): profile replaced, from evidence

- **Official data found.** (a) The UAE Hijri Calendar 1445 of the Sheikh Zayed Grand Mosque Centre (a federal
  publication, PDF on szgmc.gov.ae), whose prayer times "the International Astronomy Center – Abu Dhabi calculated
  ... based on the unified standards of the Sharia and Astronomical Committee": Abu Dhabi City, 353 days, July 2023 –
  July 2024. (b) Awqaf's own website (awqaf.gov.ae/prayer-times, region "Abu Dhabi", "official times"), 16–17
  November 2026, read on a visit (the page shows two days).
- **The old provisional profile** (fitted to one secondary month for Dubai, where Awqaf is not used) gave 1,134/2,118
  (53.5%) on the calendar, Asr wrong on every day, and 8/12 on Awqaf's website.
- **The new profile** was fitted to the calendar (`awqaf_fit.py`, `results/awqaf_fit.txt`): Fajr/Isha 18.1°,
  horizon dip 0.31°, rounding to the nearest minute (Sunrise and Isha rounded up), minutes −1, −3, +2, 0, +1, −1,
  reference point 24.44 N 54.28 E (an equivalent value, like C1's longitude). Fitted on alternate months it gave
  95.8% and 95.3% on the months it had not seen; on the whole calendar 2,038/2,118 (96.2%), every other time 1 minute.
  On Awqaf's website, never used in the fit: **12/12 exact**.
- **Limits.** Twelve Awqaf times are a small check; the calendar is the Centre's, computed by the IAC, not a table
  Awqaf itself published. Other regions (Al Ain, the Western Region, the northern emirates) are calculated at the
  user's position with the same conventions and have not been compared with any official table.

## Other authorities: measured, not changed here

Their profiles belong to the authority work being done separately; the counts above are locked as floors in
`OfficialAgreementTest` so they cannot get worse unnoticed. What the measurements say:

- **Kemenag (Indonesia):** 0/180, every time 2–4 minutes early, yet each prayer's spread is under 60 s: Kemenag's
  table is reproducible exactly by a fixed rule (it adds precaution minutes, "ihtiyat", that the profile lacks: about
  +2 for Fajr and Isha, +3 for Dhuhr). One month only.
- **JAKIM (Malaysia, zone WLY01):** 255/2,190 (11.6%). Dhuhr is reproducible on all 365 days with a fixed shift
  (+2 min 17 s after transit); the other prayers point to a different zone reference point and angles.
- **Umm al-Qura (Makkah, 1448 AH):** 1,006/2,130 (47.2%), nearly all others 1 minute; a fixed shift per prayer would
  reach 344–348 of 355 days for Sunrise, Dhuhr, Maghrib and Isha.
- **Diyanet (Istanbul, 2026):** 1,392/2,190 (63.6%), nearly all others within 2 minutes.
- **MUIS (Singapore):** 4,866/6,576 (74.0%) by calculation; the official table is carried, so the app is exact
  2024–2026.
- **Not checked:** Muslim World League, ISNA and Karachi are methods with no official city table to compare with;
  Egypt (no official online table found); Kuwait's Awqaf and Tehran's Institute of Geophysics (their websites did not
  answer from here: HTTP 502); Qatar (not attempted).

## Routes to exact times, and what each needs

| Authority | Route | Status (16 Nov 2026) |
|---|---|---|
| IACAD | The website's prayer-times feed (the request iacad.gov.ae/en/prayer-times makes) | Refused from this cloud machine and from GitHub's (Cloudflare "Attention Required", HTTP 403); IACAD's terms page is refused too. No published API or open-data licence found. Not used: it needs IACAD's permission. New months can still be carried from a browser visit. |
| Awqaf | Official API "Prayer Timings" on the UAE Government API portal (api.government.ae; endpoint `api.gsb.government.ae/gateway/retrievePrayerTimeDetails_AWQAF/1.0`, header `GW-APIKey`) | Awqaf's open-data page offers it to "synchronise your applications ... with our approved prayer times" and says its open data may be used, reused and shared without restriction. Needs an API key from registering on the portal (the owner's step). Not implemented: without a key the response format cannot be seen, and a client tested on invented fixtures would prove nothing. |
| MUIS | data.gov.sg open data | Done (bundled 2024–2026, refreshed weekly on the phone). |
| Umm al-Qura | KACST's service behind ummulqura.org.sa (a Hijri year of times for any latitude and longitude) | Reachable; no published terms found. Ask KACST before an app uses it. |
| JAKIM | e-Solat's JSON service (zone timetables) | Reachable and widely used by apps; no published terms; needs the 63 zone boundaries. |
| Diyanet | "Awqat Salah" API | Registration required. |
| Kemenag | SIHAT tables on bimasislam.kemenag.go.id | Page only; no API terms found. |

## Files

- Data (each file's header says where it came from and when): `data/iacad_*`, `data/dulook_dxb_recording_2026.csv`,
  `data/iacad_via_emirates247_wayback.csv`, `data/szgmc_abudhabi_1445.csv`, `data/awqaf_website_abudhabi_2026-11.csv`,
  `data/ummalqura_makkah_1448.csv`, `data/jakim_wly01_2026.csv`, `data/diyanet_istanbul_2026.csv`,
  `data/kemenag_jakarta_2026-11.csv`, `data/muis_singapore_2024_2026.csv`.
- Fetchers: `tools/fetch_official_tables.py` (JAKIM, Diyanet, Umm al-Qura: one request each),
  `tools/fetch_kemenag_month.cjs` (one page visit), `tools/fetch_emirates247_wayback.py`,
  `tools/parse_szgmc_calendar.py` (from the calendar PDF).
- Every difference, date by date: `results/evidence_misses.csv`.
- Regenerate this file: `python3 accuracy/tools/evidence.py`.
