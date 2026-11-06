# Handover: Noor Islamic Smartwatch

Written 6 November 2026, at version 0.8.0 (commit after `fc2854b`), when development moved from a Claude chat
workspace to Claude Code. It records what exists, how it came to be, the decisions behind it and what remains open,
so work can continue without rebuilding or re-deciding anything. Rules for working on the project are in
`/CLAUDE.md`. Each part is described in detail in the other files in `docs/`.

## 1. State at handover

- `main` is clean and pushed. CI (`Android build and tests`) passed for 0.8.0, and `dev-latest` serves the 0.8.0 APKs.
- Test counts at 0.8.0: `core/prayer` 73 (1 skipped by design: a research assumption for Singapore), `alerts` 10,
  `app` 26, `wear` 22. All pass.
- Nothing has been tested on a real phone or watch. Every result comes from JVM and Robolectric tests and software
  renders. The owner installs the builds himself.
- The handover itself must not change the product. Verify, read, report. Start new work only when the owner asks.

## 2. History

| When (2026) | Version / commit | What happened |
|---|---|---|
| 3 Nov | before this repo | Built the original **Noor Prayer watch face** for the owner's Galaxy Watch 5 Pro (Watch Face Format v4, package `com.noorface.prayer`). Iterated 1.2.1 → 1.3.2: countdown formatting, Always-On Display, the prayer name stuck on Fajr, prayer-info layout, element sizes. **v1.3.2 works on his watch** and is frozen in `legacy/` |
| 4 Nov | research → `c518140` Phase 1 | Owner's "premium ecosystem" brief: accuracy first. Dubai IACAD research (`accuracy/`): official data from 2014 to 2026, conventions deduced, blind test frozen. Prayer engine (`core/prayer`, NREL SPA port), phone app foundation, legacy face preserved |
| 4 Nov | `fd6e757` Phase 3 | Wear OS app, phone↔watch Data Layer sync, tile, complication, Watch Face Push |
| 4 Nov | `caf8b42`, `ff6f93b`, `b5408f7` | Today screen browses any date 2021–2035 with a dual calendar; Qibla compass; Dulook DXB recording used as a regression check |
| 4 Nov | 0.3.0 `1cf2e54` | Phone-to-watch setup: watch detection, opening the Play Store on the watch, face activation from the phone, watch status reports |
| 4 Nov | 0.4.0 `a87aab7` | Worldwide location (precise or approximate naming, offline gazetteer of about 34,000 places), authority registry, bundled official timetables (Dubai IACAD, Singapore MUIS) |
| 4–5 Nov | 0.5.0 `d5842ae` | Adhan and prayer alerts: per-prayer type and sound, imported sounds, phone, watch or both; licensed Adhan recordings and original sounds |
| 5 Nov | 0.6.0 `22058db` | Design transformation of every screen: "the sky is the clock" (live sky colour and sun path on Today), follow-the-sun appearance, redesigned Qibla, Adhan, Watch, Settings and onboarding |
| 5 Nov | 0.7.0 `3626f47` | Watch experience: standalone setup, Qibla, tasbih, Hijri calendar, two tiles, four complications, Ongoing Activity |
| 5 Nov | not committed | A second whole-app visual redesign was started, then **stopped by the owner and fully reverted** (see section 4) |
| 6 Nov | 0.8.0 `fc2854b` | Home screen only: a living natural scene built on the sun path, with drag-the-sun interaction. Every other screen pixel-identical to 0.7.0 |

## 3. What exists now

### Phone app (`app`)

Bottom tabs: **Prayers** (Today, the home screen), **Qibla**, **Adhan**, **Watch**, **Settings**. Also onboarding,
the city picker, the accuracy/source page, and the Adhan sub-pages (prayer page, sound library, My sounds).

- **Home (Today)**: the next prayer and a live countdown over a scene of the real sky at the user's place and moment
  (details in section 5); a date bar with arrows, a calendar sheet and "Today"; a pager of days from 2021 to 2035
  with the day's timeline (Fajr, Shuruq, Dhuhr or Jumu'ah, Asr, Maghrib, Isha); an "Official timetable" badge where
  one applies; a source footer.
- **Location**: automatic (fused location, precise or approximate, named offline) or a chosen city; follows travel;
  works offline.
- **Authorities**: chosen by country and region (`docs/AUTHORITIES.md`). Bundled official timetables are used where
  they exist: Dubai IACAD; Singapore MUIS, refreshed from data.gov.sg at most weekly.
- **Qibla**: Vincenty bearing on WGS-84, true north via the World Magnetic Model, accuracy meter, calibration guide.
- **Adhan and alerts**: `docs/ALERTS.md`. "Test this alert" runs the real alarm path, which is how to check on a
  device.
- **Watch tab**: detects the watch and walks through three steps (Noor on the watch, prayer settings, Noor watch
  face), each confirmed by the watch itself.

### Watch app (`wear`)

`docs/WATCH.md` covers: home with countdown, Qibla, tasbih (tap or bezel), Hijri calendar, settings, on-watch setup
(own location or city search), two tiles, four complications, Ongoing Activity during an Adhan, and the Noor face
packaged from v1.3.2's resources for Watch Face Push (Wear OS 6), validated at every build. The watch keeps working
without the phone, using the last configuration or its own setup.

### Prayer engine and accuracy (`core/prayer`, `accuracy/`)

- NREL SPA port, agreeing with pvlib to 0.007 s on 37,254 Dubai event times.
- Dubai profile "C1": 527 of 588 official IACAD times exact; the other 61 differ by exactly 1 minute and are still
  **unexplained**. No date-specific adjustment is used. `PrayerEngineTest.iacadOfficialAgreementIsAsDocumented` locks
  these numbers. Full detail in `docs/PRAYER_ACCURACY.md`.
- Blind test: predictions for Dec 2026 – Jun 2027 are frozen. `iacad-monthly.yml` scores each new IACAD month and
  commits the result, so pull before you push.
- Hijri: Umm al-Qura with a ±2-day user adjustment. It can differ from the UAE calendar by a day.

### Phone ↔ watch

The phone resolves everything into one `WatchConfig` and publishes it on the Data Layer. The watch calculates with
the same engine, so the times match to the minute. Both apps must carry the same package name and signature
(development key in `signing/`). Details: `docs/ARCHITECTURE.md`, `docs/WATCH.md`.

## 4. Design history and the owner's taste

- The owner wants a premium, minimalist, refined, **Apple-inspired** look that keeps Noor's own Islamic identity:
  gold accent, Kufi type for Arabic, sky colours from the real sun. `docs/DESIGN.md` is the design reference.
- 0.6.0 redesigned every screen, and the owner liked it, above all **the sun-path concept** on the home screen.
- On 5 November he asked for another redesign of the whole app. Partway through, he narrowed it: **only the home
  screen may change; every other phone page must stay exactly as it was**, with the same layouts, colours,
  typography, navigation and components. All non-home changes were reverted (never committed), and 0.8.0 was checked
  by pixel-comparing every non-home render against renders taken beforehand. Only screens that show the live clock
  (the watch preview and the Adhan page's next-alert line) differ, and only in the time they show. Treat the non-home
  screens as finished unless he asks for changes.
- Home-screen direction (his words, summarised): build on the sun path rather than replace it; beautiful, immersive,
  atmospheric, a natural environment that evolves through the day (sky, sunlight, clouds, atmosphere, scenery),
  interactive elements that make it feel alive; **natural only, no man-made objects, no people**; it must complement
  the prayer information, not compete with it; performant, readable, accessible.
- Countdown format, from the watch-face days and applied everywhere: no leading zero on hours; two-digit minutes
  when hours are shown (`1:07:35`), otherwise no leading zero (`7:35`); no seconds where the display updates once a
  minute (Always-On).

## 5. The home screen (0.8.0) in brief

Files: `app/src/main/java/com/noor/app/ui/today/TodayScreen.kt` (layout, header, drag-the-sun gesture, timeline),
`LivingSky.kt` (the scene), `HomeSky.kt` (per-prayer sky discs on the timeline). The old `components/SkyBackdrop.kt`
was removed; only the home screen used it. Full description: `docs/DESIGN.md` ("The home screen").

- **Layers, far to near**:
  - sky colours from `SkyPalette`, stars, a faint Milky Way, a rare meteor, and the moon by Hijri day on the sun's arc;
  - soft clouds lit by the time of day, drifting;
  - the sun's halo, rays and path;
  - the land, by `Terrain` (dunes for arid countries, mountains and hills elsewhere; fixed per place), with mist at
    dawn and rim light at golden hour, its nearest ridge fading into the page;
  - the night path, prayer markers (the next one has a breathing gold ring), and birds at dawn and dusk.
- **Drag sideways** across the sky to scrub the sun through the day:
  - it catches on each prayer with a haptic tick;
  - the header shows the elevation, the prayer, the clock time and how far away it is;
  - it springs back to now on release.
- **Performance**: the scene changes per minute, not per second; the paths are cached; the ambient clock runs at about
  30 Hz and pauses off screen; all ambient motion is off under "Remove animations"; short screens (under 800 dp) get a
  compact layout.
- **Not yet seen on a device**: the motion, haptics, frame rate, battery use, and how the soft gradients look on a
  real GPU.
- **Tests**: `LivingSkyTest` (logic), `TodayDesignTest` and `TodaySmallDesignTest` (renders).

## 6. The owner's requirements, and what is still open

From his briefs (paraphrased). **Do not start any of this during the handover.** It is listed so nothing is forgotten.

| Requirement | Status |
|---|---|
| Prayer times exact to the minute against the official local timetable, starting with Dubai; global correctness; no faked accuracy | Engine done; Dubai 89.6% exact, the rest ±1 min, unexplained. Ongoing: blind test. Other authorities use published parameters; most are not yet validated against their own tables (UAE Awqaf provisional 91%) |
| Automatic GPS location, travel updates, time zones, offline-first, manual override | Done; awaiting device test |
| Simplest possible phone-to-watch install | Done within platform limits (a phone cannot install a watch app; the Play Store on the watch can be opened from the phone). One-tap install works only once Noor is on Google Play |
| Date navigation over ten years, dual calendar, "Today" | Done (2021–2035) |
| Accurate Qibla compass with accuracy information and calibration guidance | Done; awaiting device test |
| Complete Adhan system: library, separate Fajr choices, custom sounds | Done; awaiting device test |
| Premium Apple-inspired design | 0.6.0 (all screens) plus 0.8.0 (home) |
| **Watch-face customisation studio**: live preview editor for positions, sizes, fonts, colours, themes, profiles and AOD, keeping the v1.3.2 fonts, colours and icons; no fake controls | **Not started.** The README calls it the "next stage". Watch Face Format is declarative, so check what is genuinely possible before promising anything |
| Pre-prayer reminders, quiet hours, Jumu'ah reminders, mosque-timetable comparison, weekly timetable view | Not built (from the first brief; Jumu'ah is shown on Fridays) |
| Real-device testing on the Galaxy Watch 5 Pro and a phone | **Not done.** Checklists: `docs/WATCH.md` ("Needs a real watch"), `docs/ALERTS.md`, `docs/ARCHITECTURE.md` (limitations) |
| Commercial release on Google Play and the Galaxy Store | Not started: needs a private upload key with Play App Signing (never in the repo), a privacy policy, a store listing and a distribution plan |

Smaller known limitations are in `docs/ARCHITECTURE.md` ("Known limitations") and `docs/WATCH.md` ("Not done").

## 7. Outside this repository (keep safe, never commit)

- **`noor-release.jks`**: the signing key of the original watch face v1.3.2. Without it, the installed face cannot be
  updated in place. The owner holds copies in his backups: the complete archive
  `noor-watchface-v1.3.2-full-project.tar.gz` (SHA-256 `9721af4e…c89edc7c`) and the 1.3.2 source zip in
  `Downloads\platform-tools-latest-windows` on his Windows computer. Ask him for it only if the v1.3.2 face itself
  must be rebuilt.
- **The owner's original briefs** (watch-face master prompt, ecosystem brief, comprehensive feature brief, design
  brief, home-screen brief) and his screen recording of the official Dulook DXB app. The data taken from the
  recording is in `accuracy/data/dulook_dxb_recording_2026.csv`; this document and `CLAUDE.md` summarise the briefs.

## 8. Installing builds on the owner's devices

- **Phone**: open the phone link on the phone, download, open, and allow installs from the browser once. It installs
  over the previous build because the signing key is the same.
- **Watch**: the owner uses `adb` from the `platform-tools` folder on his Windows computer over wireless debugging
  (`docs/INSTALL.md`). If `adb` says `more than one device/emulator`, run `adb devices` and then
  `adb -s <watch-address:port> install Noor-watch.apk`.
- The original face v1.3.2 (`com.noorface.prayer`) is a separate app. Noor builds never replace it.

## 9. First session in Claude Code: verify, do not change

1. `git pull`, then read `CLAUDE.md`, this file, and the rest of `docs/`.
2. Set up the toolchain (JDK 17, Android SDK 35 and 36, Python 3). In a Claude Code cloud environment, use the setup
   below.
3. Run the four test commands from `CLAUDE.md`. Expect the counts in section 1.
4. Build both release APKs. Optionally check `aapt2 dump badging`: versionName `0.8.0`, versionCode `8`.
5. Check the latest `android.yml` run on GitHub succeeded.
6. Report to the owner in plain language: what you found, whether everything builds and passes, anything that
   differs from this document. Make no product changes, no version bump and no new features until he asks.

### Toolchain in a Claude Code cloud environment

The cloud VM is Ubuntu 24.04 with JDK 21. This project needs JDK 17 and the Android SDK. The SDK is downloaded from
`dl.google.com`, which the default "Trusted" network level may not allow. Set the environment's network access to
**Custom**, keep the default domains, and add `dl.google.com`. Then use this setup script:

```bash
#!/bin/bash
set -e
apt-get update -q && apt-get install -y -q openjdk-17-jdk-headless unzip
export ANDROID_HOME=/opt/android-sdk
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools" && cd /tmp
  # If this file name has changed, take the current Linux "command line tools" link from developer.android.com/studio
  curl -sSLo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  unzip -q -o clt.zip -d "$ANDROID_HOME/cmdline-tools" && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "platforms;android-36" "build-tools;35.0.0" "build-tools;36.0.0"
```

Also add the environment variable `ANDROID_HOME=/opt/android-sdk`. If the SDK still cannot be installed, rely on
GitHub Actions for builds and tests, and say so in reports.

## 10. After the handover: the approved plan (6 November 2026)

The owner approved this plan in full and asked Claude Code to carry it out end to end, deciding technical and design
questions itself. Each phase ships as its own version, with renders to review, and keeps every other screen unchanged.

| Phase | Version | Content | State |
|---|---|---|---|
| 1 · Adhan reliability | 0.9.0 | Adhan plays like an alarm (also on vibrate); never fails silently (default Adhan, then a backup channel whose sound Android plays itself); Alert history with Share report; emulator end-to-end test in CI | Done in code and on emulators; **phone test pending** (`docs/ALERTS.md`, "Testing on a phone") |
| 2 · Language separation | 0.9.1 | English mode entirely English, Arabic entirely Arabic (no second-language names, tasbih transliterated in English), on phone, watch and notifications; automated script-mixing check | Done; checked on 54 phone and all watch renders. Exceptions kept on purpose: the Kufi logo, device, licence and product names, web addresses, credited recordists. Known limit: a place with no Arabic name in the gazetteer shows its Latin name |
| 3 · Home screen | 0.9.2 | All five prayers visible without scrolling; no "Official timetable" badge or source line (method moves to Settings); fix the midnight star/cloud edge and the sun path cutting through mountains; a truer sky (sun-side glow, Earth's shadow, Belt of Venus, twilight steps); a < 1 s opening time-lapse | Done (renders and tests); motion, smoothness and battery need a phone |
| 4 · Persistent notification | | Ongoing notification: next prayer, time, live countdown with seconds (system chronometer, no wake-ups), Hijri date, place; updated at each prayer, midnight and on changes | |
| 5 · Calendar, Settings, Qibla | | Gregorian/Hijri switch in the date picker; Settings in clearer groups with search and the method choice; Qibla accuracy box of fixed height with hysteresis, mini meter in the calibration tips (three levels kept: the sensor is too coarse for four) | |
| 6 · Adhan library | | Much larger, diverse collection (countries, regions, mosques, reciters) from verified licensed sources; browse, search, preview, compare, favourites; Random Adhan; more alert sounds | |

Owner decisions: scenery stays artistic and offline (no weather service); build the library from the best available
licensed sources and explore ways to obtain more (famous reciters need permission from rights holders); Claude Code
runs every test it can and guides the owner, in simple steps, through tests that need his phone.

Open owner action: GitHub's default branch is still the old `phase-1-foundation`, and repository settings cannot be
changed from a Claude Code cloud session. Until it is set to `main` (github.com → the repository → Settings → General →
Default branch), the monthly blind test does not run on its schedule.

