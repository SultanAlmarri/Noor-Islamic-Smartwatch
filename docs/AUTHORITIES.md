# Prayer-time authorities in Noor

How Noor chooses the source of prayer times for a place, which authorities it knows, what each entry is based on,
and how sure we are. Code: `core/prayer/src/main/kotlin/com/noor/prayer/authority/Authorities.kt`.
Last updated 16 November 2026 (0.18: every country of the world mapped; tests in `WorldAuthoritiesTest`).

## How a day's times are produced

1. **Place → authority.** The country (and, in the UAE, the emirate) of the position, found in this order: Android's
   geocoder; offline, the time-zone map's zone at the position (`tzmap.bin`; each country's zones are drawn to its
   border, and `ZoneCountries` gives the zone's country from the time-zone database's `zone.tab`); the nearest town of
   the offline gazetteer within 30 km; a bundled city within 150 km. So in automatic mode the authority follows the
   user to any country, with or without network, at the next location fix (when the app opens, and every 6 hours in
   the background if background location is allowed). Dubai emirate → IACAD; rest of the UAE → Awqaf; otherwise the country's entry below; any other
   country → Muslim World League method, labelled as a recognised method, not an official local timetable. A method
   chosen in Settings always wins over the automatic choice (shown as "the method you chose"). The watch on its own
   uses the gazetteer (30 km, then the nearest town within 300 km).
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

Confidence: **high** = measured against the authority's own times; **medium** = parameters documented consistently by
several secondary references (praytimes.org, Aladhan, Batoul Apps' Adhan libraries, salahtimes, bladi.net) but not
measured; **low** = one secondary reference only, or the authority's practice is known to vary.

### National and community authorities

| Authority | Coverage | Category | Parameters | Sources | Confidence and evidence |
| --- | --- | --- | --- | --- | --- |
| IACAD (Dulook DXB) | Dubai emirate (60 km around the city reference point) | Validated calculation + official timetable for carried dates | Fajr/Isha 18°, Sun's upper limb + 34′ refraction + 0.10°, seconds rounded up, fixed minutes (−2, −4, +2, 0, +2, 0); reference 25.12 N 55.189 E | iacad.gov.ae | High. 588 official times (Aug 2020 – Nov 2026): 527 exact (89.6%), rest ±1 min; 18 December dates predicted before publication: 17 exact. See `PRAYER_ACCURACY.md`. |
| Awqaf (UAE) | UAE outside Dubai | Validated calculation (provisional) | Fajr/Isha 18°, +0.19° horizon, rounding to nearest, minutes (−1, −3, +3, +3, +2, +1) | awqaf.gov.ae | Medium. One month of Awqaf-attributed times (Nov 2026, secondary source): 164/180 exact. To be re-checked against Awqaf's own tables. |
| MUIS | Singapore | Official timetable 2024–2026 + validated calculation | Fajr 20°, Isha 18°, upper limb + 34′, rounded up, Dhuhr +1; reference 1.25 N 103.80 E | data.gov.sg (Singapore Open Data Licence) | High. Fitted on 2024–2025, held out on 2026: 74% exact, every time within 2 minutes; the published table is used wherever it exists. |
| Umm al-Qura | Saudi Arabia | Authority parameters | Fajr 18.5°, Isha 90 min after Maghrib (120 in Ramadan) | ummulqura.org.sa; praytimes.org | Medium. Not yet compared with a published Saudi timetable. |
| JAKIM | Malaysia | Authority parameters | Fajr 18° (national Muzakarah decision, Nov 2019, in force from late 2019 / 2020 by state), Isha 18°, Dhuhr +1, Maghrib +2 | e-solat.gov.my; Berita Harian and Malay Mail, Nov 2019 | Medium. Against JAKIM's e-Solat table for zone WLY01 (Kuala Lumpur), 17–23 Nov 2026, calculated at KL's centre: 5/42 exact; Fajr, Dhuhr, Asr and Isha 1–3 min earlier than JAKIM's (consistent with precautionary minutes and JAKIM's own zone points, neither modelled; not tuned). Note: Aladhan still lists JAKIM as 20°, the pre-2019 value. |
| Kemenag | Indonesia | Authority parameters | Fajr 20°, Isha 18°, Dhuhr +1 | bimasislam.kemenag.go.id | Medium. Not yet compared with Kemenag's tables (Kemenag adds precautionary minutes, ihtiyat, not modelled). Muhammadiyah uses 18° for Fajr. |
| Egyptian General Authority of Survey | Egypt | Authority parameters | Fajr 19.5°, Isha 17.5°, Dhuhr +1 | praytimes.org; Aladhan method 5 | Medium. No official Egyptian table checked. |
| Kuwait, Ministry of Awqaf | Kuwait | Authority parameters | Fajr 18°, Isha 17.5° | praytimes.org; Aladhan method 9 | Medium. Not checked. |
| Qatar, Ministry of Awqaf | Qatar | Authority parameters | Fajr 18°, Isha 90 min after Maghrib | Aladhan method 10; Adhan | Medium. Not checked. |
| Diyanet | Türkiye | Authority parameters | Fajr 18°, Isha 17°, temkin Shuruq −7, Dhuhr +5, Asr +4, Maghrib +7; Asr first shadow | Adhan (Turkey); namazvakitleri.diyanet.gov.tr | High for Istanbul: against Diyanet's published times, 16–22 Nov 2026 (42 times, at Istanbul's centre 41.0082 N 28.9784 E): 31 exact, every other one 1 minute. Other cities and seasons not yet measured. |
| Institute of Geophysics, Tehran | Iran | Authority parameters | Fajr 17.7°, Isha 14°, Maghrib at 4.5° | praytimes.org; Aladhan method 7 | Medium. Not checked. |
| Ministry of Habous and Islamic Affairs (0.18) | Morocco, and Western Sahara (EH), where Moroccan times are used | Authority parameters | Fajr 19°, Isha 17°, Shuruq −3, Dhuhr +5, Maghrib +5 | Aladhan method 21 (angles); bladi.net (angles and minutes) | Medium. The ministry's own city tables (habous.gov.ma) were offline for maintenance on 16 Nov 2026: not compared. Some sites calculate Morocco with MWL 18°; the 19° figure is the one attributed to the ministry. |
| Ministry of Religious Affairs and Endowments (0.18) | Algeria | Authority parameters | Fajr 18°, Isha 17° | Aladhan method 19; salahtimes | Medium for the angles. Whether the ministry adds minutes (e.g. to Maghrib) is not documented anywhere we found: none added. |
| Ministry of Religious Affairs (0.18) | Tunisia | Authority parameters | Fajr 18°, Isha 18° | Aladhan method 18 | Low: one secondary reference. |
| Ministry of Awqaf, Islamic Affairs and Holy Places (0.18) | Jordan | Authority parameters | Fajr 18°, Isha 18°, Maghrib 5 min after sunset | Aladhan method 23 | Low–medium: one secondary reference. |
| Comunidade Islâmica de Lisboa (0.18) | Portugal | Authority parameters (community body) | Fajr 18°, Maghrib 3 min after sunset, Isha 77 min after that Maghrib | Aladhan method 22 | Low–medium: one secondary reference. |
| Spiritual Administration of Muslims (0.18) | Russia | Authority parameters | Fajr 16°, Isha 15° | Aladhan method 14 (placed at Ufa, seat of the Central Spiritual Administration) | Low. Russia has several spiritual administrations; Tatarstan's (DUM RT) publishes its own times and changed its method in 2014; not separated (no sub-national code yet). Above about 50° N in summer the angles are not reached and the high-latitude rule applies. |
| Brunei Darussalam (0.18) | Brunei | Authority parameters | Fajr 20°, Isha 18° | Brunei Astronomical Society (bruneiastronomy.org: "the existing Brunei Darussalam calculation setting") | Low–medium. The Mufti Department's own timetable not compared. |

### Recognised methods (no official status claimed for the place)

| Method | Countries | Parameters | Why this method | Confidence |
| --- | --- | --- | --- | --- |
| Moonsighting Committee Worldwide (0.18) | United Kingdom | Fajr/Isha 18°, never earlier (Fajr) or later (Isha) than the committee's seasonal minutes from sunrise/sunset (latitude and days since the winter solstice; general shafaq); a seventh of the night above 55° N; Dhuhr +5, Maghrib +3 | Widely used by UK mosques and designed for the long summer twilight of high latitudes (18° is not reached in London from May to July) | High for the implementation: reproduces the Adhan libraries' London reference (5 days of 2016, Hanafi Asr) 30/30 to the minute. Not an official timetable: UK mosques publish their own. |
| UOIF (Musulmans de France) (0.18) | France | Fajr 12°, Isha 12° | The method most associated with France (praytimes.org, Aladhan method 12) | Medium that it is widely used; France has no single authority and many mosques use 15° or 18°. |
| University of Islamic Sciences, Karachi | Pakistan, India, Bangladesh, Afghanistan | Fajr 18°, Isha 18°, Dhuhr +1 | praytimes.org lists it for these four countries | Medium. Most mosques there use the Hanafi Asr: the method's default here is the standard Asr (unchanged from earlier versions); change it in Settings. |
| ISNA | United States, Canada | Fajr 15°, Isha 15°, Dhuhr +1 | praytimes.org: North America | Medium. Many mosques publish their own timetables. |
| Egyptian method (regional) (0.18) | Syria, Lebanon, Libya, Sudan, South Sudan, and Africa south of the Sahara (AO BF BI BJ BW CD CF CG CI CM CV DJ ER ET GA GH GM GN GQ GW KE KM LR LS MG ML MR MU MW MZ NA NE NG RW SC SL SN SO ST SZ TD TG TZ UG ZA ZM ZW) | Fajr 19.5°, Isha 17.5°, Dhuhr +1 | praytimes.org lists the Egyptian method for "Africa, Syria, Lebanon" | Low–medium: a regional statement, not a survey of each country; individual countries (Nigeria, Senegal, South Africa…) have councils with their own tables. |
| Umm al-Qura (regional) (0.18) | Bahrain, Oman, Yemen | Fajr 18.5°, Isha 90 min (120 in Ramadan) | praytimes.org lists Umm al-Qura for the Arabian Peninsula | Low–medium: the ministries of Bahrain and Oman publish their own times, not compared. |
| Shia Ithna Ashari (Leva Institute, Qum) (0.18) | None automatically (choose it in Settings) | Fajr 16°, Isha 14°, Maghrib at 4° | praytimes.org; Aladhan method 0: the method of Shia communities worldwide | Medium for the parameters. Its "Jafari midnight" (half of sunset to Fajr) is not shown: Noor shows six times and no midnight. |
| Muslim World League | Every other country, among them Germany, Netherlands, Belgium and the rest of Europe, the Balkans (Bosnia, Albania, Kosovo, North Macedonia), Iraq, Palestine (and Israel, shown as Palestine), Azerbaijan, Central Asia (Kazakhstan, Uzbekistan, Kyrgyzstan, Tajikistan, Turkmenistan), Maldives, Sri Lanka, Thailand, the Philippines, China, Japan, Korea, Latin America, Australia and New Zealand | Fajr 18°, Isha 17°, Dhuhr +1 | praytimes.org lists MWL for Europe and the Far East; for the rest it is the common international default | Low as a statement about local practice: these countries' own bodies (the Islamic Community of Bosnia's Takvim, Kazakhstan's DUMK, the Caucasus Muslims Board, Iraq's Sunni and Shia endowments, the Palestinian Ministry of Awqaf…) publish their own times, whose parameters we could not find documented. |

## Researched but not yet integrated

- **Bosnia and Herzegovina (Islamic Community, Takvim)**: the Takvim's own description of its method is published
  only as images (Takvim 2025 PDF); a search summary attributes Isha at 16° to it, which we could not read ourselves.
  Not implemented: no parameter is used that we have not read in a source.
- **Kazakhstan (DUMK, muftyat.kz)**, **Uzbekistan**, **Kyrgyzstan**, **Tajikistan**, **Turkmenistan**, **Azerbaijan**:
  their boards publish timetables; no documented parameters found. MWL until they are.
- **Tatarstan (DUM RT)**: publishes its own times; would need a sub-national region code for Russia.
- **Iraq, Palestine, Lebanon, Syria, Libya, Sudan, Bahrain, Oman, Yemen, Maldives, Sri Lanka, Thailand, the
  Philippines, Nigeria, Senegal, South Africa, Kenya, Tanzania, Somalia, Ethiopia, Mauritania, Germany, the
  Netherlands, Belgium, Albania, Kosovo, North Macedonia, Australia, New Zealand, Japan, China, Brazil**: no official
  parameters found; mapped to a recognised method above.
- **"Gulf Region" (Aladhan method 8: Fajr 19.5°, Isha 90 min)**: listed by Aladhan with no authority or source named;
  not included.
- **JAKIM e-Solat** (Malaysia) publishes official zone timetables online. Integrating them needs the zone boundaries
  (63 zones) and confirmation that the service may be used by apps.
- **Diyanet** offers an API to registered developers; registration is needed before integration.
- **IACAD**: the website feed answers browsers but refuses requests from servers; it is not used by the app at runtime.
  New official months are added to the app (and the blind test) when published.
- **Awqaf (UAE)**: official monthly tables exist on awqaf.gov.ae; a full validation is the next step for the UAE
  outside Dubai.

## Locations and names

- Position: Android fused location; high accuracy when the user granted precise location, balanced power otherwise.
- Name: Android's Geocoder (English and Arabic), most specific reliable level: neighbourhood/district with precise
  location ("Za'abeel", context "Dubai"), town/city with approximate. Codes and numbers are never shown as names.
- Offline: GeoNames gazetteer (CC BY 4.0), 34,197 places (every place of 15,000+ people and many districts), bundled.
- Not used: OpenStreetMap's public Nominatim service. Its usage policy allows one request per second for an entire
  application and discourages client apps, so it is unsuitable for an app used by many people.
- Flags: country flag of the identified country; for locations identified as Israel the Palestinian flag is shown
  (owner's product decision). The place name and the country used for calculations are not changed.
