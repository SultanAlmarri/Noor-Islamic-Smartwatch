# Prayer-time authorities in Noor

How Noor chooses the source of prayer times for a place, which authorities it knows, what each entry is based on,
and how sure we are. Code: `core/prayer/src/main/kotlin/com/noor/prayer/authority/Authorities.kt`.
Last updated 16 November 2026. Measured agreement with each authority's own tables: `accuracy/results/EVIDENCE.md`.

## How a day's times are produced

1. **Place → authority.** Country (and, in the UAE, emirate) from the geocoder or the offline gazetteer.
   Dubai emirate → IACAD; rest of the UAE → Awqaf; otherwise the country's entry below; any other country → Muslim
   World League method, labelled as a recognised method, not an official local timetable. The user can pick any method
   in Settings (shown as "the method you chose").
2. **Official timetable first.** If Noor carries the authority's own published times for that date, and the user is in
   the authority's area (within 60 km of its reference point), the times are shown exactly as published, with the
   user's personal minute offsets on top. The screen says "Official timetable".
3. **Otherwise calculated**, with the authority's conventions, at the authority's reference point when inside its
   area (one city timetable), or at the user's own position elsewhere.
4. **The Today screen always says which**, for the date shown: official timetable / calculated with conventions checked
   against the official timetable / calculated with the authority's published parameters (not yet checked) / widely
   used method (no official local timetable implied) / user-adjusted.

## Source categories

| Category | Meaning |
| --- | --- |
| Official timetable | The authority's own times for that date, as published. |
| Validated calculation | Conventions inferred from the authority's official timetable (no date-specific tweaks) and measured against it. |
| Authority parameters | Angles/rules the authority is documented to use; not yet measured against its own timetable. |
| Recognised method | A widely used method; no claim of official status for the place. |

## Authorities

| Authority | Coverage | Category | Basis | Evidence |
| --- | --- | --- | --- | --- |
| IACAD (Dulook DXB) | Dubai emirate (60 km around the city reference point) | Validated calculation + official timetable for carried dates | Fajr/Isha 18°, Sun's upper limb + 34′ refraction + 0.10°, seconds rounded up, fixed minutes (−2, −4, +2, 0, +2, 0); reference 25.12 N 55.189 E | 588 official times (Aug 2020 – Nov 2026): 527 exact (89.6%), rest ±1 min; 18 December dates predicted before publication: 17 exact. Official days carried: 68 archived days 2021–2023 + November 2026. See `PRAYER_ACCURACY.md`. |
| Awqaf (UAE) | UAE outside Dubai (Abu Dhabi city timetable within 60 km of 24.44 N 54.28 E) | Validated calculation (Abu Dhabi) | Fajr/Isha 18.1°, +0.31° horizon, nearest minute (Sunrise and Isha rounded up), minutes (−1, −3, +2, 0, +1, −1) | Fitted 16 Nov 2026 to the UAE Hijri Calendar 1445 for Abu Dhabi City (Sheikh Zayed Grand Mosque Centre, times by the IAC; 353 days): 2,038/2,118 exact (96.2%), rest ±1 min, 95%+ on held-out months; Awqaf's own website (Abu Dhabi, 16–17 Nov 2026): 12/12. Other regions not yet checked. See `PRAYER_ACCURACY.md`. |
| MUIS | Singapore | Official timetable 2024–2026 + validated calculation | Fajr 20°, Isha 18°, upper limb + 34′, rounded up, Dhuhr +1; reference 1.25 N 103.80 E | Official dataset (data.gov.sg, Singapore Open Data Licence, 1,096 days). Fitted on 2024–2025, held out on 2026: 74% exact overall, every time within 2 minutes. MUIS's ±1-minute pattern even for Dhuhr (pure solar transit) shows their tables are not reproducible exactly from standard astronomy, so the published table is used wherever it exists. The app refreshes it from data.gov.sg at most weekly when the user is in Singapore. |
| Umm al-Qura | Saudi Arabia | Authority parameters | Fajr 18.5°, Isha 90 min after Maghrib (120 in Ramadan) | The Umm al-Qura standard of the Kingdom; not yet compared with a published Saudi timetable. |
| JAKIM | Malaysia | Authority parameters | Fajr 18° nationwide since 1 Ramadan 1441 / April 2020 (National Muzakarah decision, Nov 2019), Isha 18°, Maghrib about 2 minutes after sunset | JAKIM publishes zone timetables (63 zones, e-solat.gov.my); Noor calculates at the user's exact position and has not yet compared them. |
| Kemenag | Indonesia | Authority parameters | Fajr −20°, Isha −18° (reaffirmed by Kemenag, Dec 2020) | Not yet compared with Kemenag's tables. Note: Muhammadiyah uses −18° for Fajr. |
| Egyptian General Authority of Survey | Egypt | Authority parameters | Fajr 19.5°, Isha 17.5° | Parameters as documented (praytimes.org); no official Egyptian table checked. |
| Kuwait, Ministry of Awqaf | Kuwait | Authority parameters | Fajr 18°, Isha 17.5° | As documented; not checked. |
| Qatar, Ministry of Awqaf | Qatar | Authority parameters | Fajr 18°, Isha 90 min after Maghrib | As documented; not checked. |
| Diyanet | Türkiye | Authority parameters (approximation) | Fajr 18°, Isha 17°, precautionary minutes | Approximation; Diyanet publishes its own times (namazvakitleri.diyanet.gov.tr), not yet compared. |
| Institute of Geophysics, Tehran | Iran | Authority parameters | Fajr 17.7°, Isha 14°, Maghrib 4.5° | As documented. |
| University of Islamic Sciences, Karachi | Pakistan, India, Bangladesh, Afghanistan | Recognised method | Fajr 18°, Isha 18° | Widely used in South Asia; Hanafi Asr selectable. |
| ISNA | United States, Canada | Recognised method | Fajr 15°, Isha 15° | Widely used; many mosques publish their own timetables. |
| Muslim World League | Everywhere else | Recognised method | Fajr 18°, Isha 17° | No official local timetable implied. |

## Researched but not yet integrated

- **JAKIM e-Solat** (Malaysia) publishes official zone timetables online. Integrating them needs the zone boundaries
  (63 zones) and confirmation that the service may be used by apps.
- **Diyanet** offers an API to registered developers; registration is needed before integration.
- **IACAD**: the website feed answers browsers but refuses requests from servers (Cloudflare, HTTP 403; checked again
  16 Nov 2026); IACAD publishes no API or data licence. It is not used by the app at runtime: that needs IACAD's
  permission. New official months are added to the app (and the blind test) when published.
- **Awqaf (UAE)**: an official "Prayer Timings" API is listed on the UAE Government API portal (api.government.ae),
  offered by Awqaf's open-data page to apps; it needs an API key (registration). Integrating it is the route to exact
  times outside Dubai.
- **Umm al-Qura (KACST)**, **JAKIM e-Solat**, **Kemenag SIHAT**: official tables reachable on their sites, compared in
  `accuracy/results/EVIDENCE.md`; no published terms for use by apps.

## Locations and names

- Position: Android fused location; high accuracy when the user granted precise location, balanced power otherwise.
- Name: Android's Geocoder (English and Arabic), most specific reliable level: neighbourhood/district with precise
  location ("Za'abeel", context "Dubai"), town/city with approximate. Codes and numbers are never shown as names.
- Offline: GeoNames gazetteer (CC BY 4.0), 34,197 places (every place of 15,000+ people and many districts), bundled.
- Not used: OpenStreetMap's public Nominatim service. Its usage policy allows one request per second for an entire
  application and discourages client apps, so it is unsuitable for an app used by many people.
- Flags: country flag of the identified country; for locations identified as Israel the Palestinian flag is shown
  (owner's product decision). The place name and the country used for calculations are not changed.
