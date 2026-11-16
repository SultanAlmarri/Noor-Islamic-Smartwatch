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
the location screen (0.9.7: search, map, coordinates, country → city → area; [LOCATION.md](LOCATION.md)), the accuracy/source page, and the Adhan sub-pages (prayer page, sound library, My sounds).

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
| Pre-prayer reminders | Built in 0.9.12 (section 17) |
| Quiet hours, Jumu'ah reminders, mosque-timetable comparison, weekly timetable view | Not built (from the first brief; Jumu'ah is shown on Fridays) |
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
| 4 · Persistent notification | 0.9.3 | Ongoing notification: next prayer, time, live countdown with seconds (system chronometer, no wake-ups), Hijri date, place; updated at each prayer, midnight and on changes | Done (tests and render); needs a phone to see it in the real shade |
| 5 · Calendar, Settings, Qibla | 0.9.4 | Gregorian/Hijri switch in the date picker; Settings in clearer groups with search and the method choice; Qibla accuracy box of fixed height with hysteresis, mini meter in the calibration tips (three levels kept: the sensor is too coarse for four) | Done (tests and renders); the compass behaviour itself needs a phone |
| 6 · Adhan library | 0.9.5 | Much larger, diverse collection (countries, regions, mosques, reciters) from verified licensed sources; browse, search, preview, compare, favourites; Random Adhan; more alert sounds | Done: 18 Adhans from 9 countries (was 7 from 6), 14 alert sounds (was 7), library with search, regions, favourites and previews, Random from favourites. Every recording found on Commons was reviewed; growing further needs the owner (see below) |

Owner decisions: scenery stays artistic and offline (no weather service); build the library from the best available
licensed sources and explore ways to obtain more (famous reciters need permission from rights holders); Claude Code
runs every test it can and guides the owner, in simple steps, through tests that need his phone.

Owner action, done: GitHub's default branch is now `main` (checked 8 November 2026), so the monthly blind test runs
on its schedule (first scoring due once IACAD publishes December 2026).

Growing the Adhan library further (needs the owner):

- **Freesound** holds many more freely licensed Adhan field recordings (CC0 and CC BY: Istanbul, Cairo, Jerusalem,
  Marrakech and more). Its API needs a free personal key: the owner registers at freesound.org and pastes the key into
  the secure form Claude Code offers (`request_credential`); it is never put in the repository. Only CC0 and CC BY
  would be used (no "non-commercial" licences, since a store release is planned).
- **Famous muezzins** (the Haramain, well-known reciters) are copyrighted by broadcasters or labels. They can only be
  added with written permission from the rights holder, or by commissioning recordings; both are the owner's business
  decisions.

## 11. Home sky, opening, scrubbing and Qibla (0.9.6, 7 November 2026)

The owner asked for a dramatically more beautiful, immersive and realistic home screen (top priority), a rethought
opening, smooth transitions when dragging the Sun past Fajr, Shuruq, Maghrib and Isha, and a Qibla accuracy box that
changes at once. Done as follows (details in [DESIGN.md](DESIGN.md#the-home-screen)):

- **Rendering**: the sky is now computed by a physical model (`Atmosphere.kt`: single scattering by air, haze and ozone
  over a round Earth with its shadow, daylight white balance, twilight exposure, filmic tone curve) into a small table
  per moment, off the main thread. On Android 13+ two AGSL shaders (`app/src/main/res/raw`) draw the sky (Sun, moon
  with its real phase, stars, Milky Way, cirrus, cloud banks, cumulus lit by marching light through them) at reduced
  resolution in a cached layer, and the land (mountains and wooded hills, or dunes with lit and shaded faces; haze,
  rim light, cloud shadows, mist) at full resolution. Below Android 13 the Canvas renderer draws the same computed sky.
- **Readability**: a graduated darkening keeps white text at 4.5:1 (3:1 for the large countdown) at every Sun height,
  tested; the Sun itself is never dimmed.
- **Opening**: two to three seconds, from the start of the current prayer's time to now (fade up, Sun runs and
  settles, clouds run fast then slow, land rises, path and prayers draw in); brief fade if seen in the last half hour;
  a touch takes over.
- **Dragging**: soft detents instead of snapping, a quick spring instead of jumps, twilight given more width (measured:
  peak colour change per pixel about a third lower), prayer marks fill gradually, no on/off switches in the lighting.
- **Qibla**: the hold (1 s, 0.6 s in the tips) and the fades are gone; the level shown is always the latest reading's;
  the magnetometer is read at the heading's rate. `QiblaAccuracyTest` proves the change appears on the next frame.

How it was checked: unit tests (sky model contrast and continuity, detents, Qibla), software renders of the Canvas
renderer, a browser bench that compiles the same shader files (WebGL, headless Chromium) for whole-day contact sheets,
and the `home-sky.yml` workflow on Android 14 and 15 emulators (the GPU shaders compiled and drawn by Android itself,
photographs through the day, recordings of the opening and of a drag, the Qibla box with injected magnetic fields).
Emulators draw with a software GPU: the pictures are faithful, but frame rate and battery use on a real phone are not
measured yet. The first emulator run found that Android refused the first sky shader ("program is too large": SkSL
unrolls loops and inlines calls, limit ~100 000 operations); the clouds were rewritten to ~44 000 and guarded by
`SkyShaderSizeTest`. A slow phone steps the sky down by itself (`SkyQuality`): lower resolution, then the Canvas sky.

Still needs the owner's phone: smoothness of the opening and of dragging, battery use with the home screen open, the
look on a real OLED screen, and a real compass near metal (the emulator check injected magnetometer readings).

## 12. Countdown, notification, Qibla, Adhan, location, Sun marks (0.9.7, 7 November 2026)

Phone app only (the watch is unchanged). What the owner asked, and what was done:

- **Countdown − / +** (`core/prayer` `Countdowns`): before a prayer "−1:23:45"; once it has come, "+04:12" since it
  began, for a while, then the next prayer's "−". How long: Fajr 25 min, Shuruq 15 (the time after sunrise before
  prayer is allowed), Dhuhr, Asr and Isha 20, Maghrib 10 — about the wait between Adhan and iqama in most mosques, as
  prayer apps that show elapsed time do; it ends earlier if the next prayer comes first. The home screen and the
  persistent notification use the same function and format (h:mm:ss, or mm:ss under an hour, as Android's own
  Chronometer writes it); a test compares them at every 97 s over two days across midnight.
- **Notification**: Android's own Chronometer ticks the seconds, always counting from the prayer's time (it writes
  "−00:17" before it; "+04:12" after it, with Noor's plus); rebuilt half a second after each change and at midnight. If
  the rebuild comes late, it passes through "00:00" (an emulator run showed the first design, a count-down with
  Noor's own minus, reading "−−00:01" for a moment; fixed and tested on the Chronometer's text). Collapsed (Android allows 48 dp): prayer and time with the large countdown, then the Hijri date and the
  place on one line that shrinks to fit (the date first, so only an extremely long place name can be shortened there).
  Expanded: the place and the date each on their own lines, wrapped, never cut. Renders: `55_status_notification_*`.
- **Qibla flicker**: a reading near a limit crossed it many times a second. Now (phone only, `CompassTracker`) the
  field strength, the sensor's estimate and the tilt are averaged over 0.1 s, a level gets worse at once at the same
  limits, and better only once clearly past them (field 12 %/21 %, estimate 8.5°/21°, flat 25°); no timer. The label
  has a fixed-width slot so the meter does not move. Tests: noisy readings at each limit (≤ 1 change in 10 s, where
  judging each reading alone changes 50+ times), and real changes shown within 0.1 s (0.25 s for a 35° estimate jump).
- **Adhan stopping after 1–2 s** (the owner's report: Samsung Fold, Android 17, every scheduled entry "played 0 s",
  tests fine): Android 17 restricts audio started from the background, and the player stopped on any loss of audio
  focus. Now: prayer alarms are alarm-clock alarms (`setAlarmClock`, which Android treats as user-visible), the Adhan
  notification has a full-screen alert (`AdhanAlertActivity`, over the lock screen, with Stop; the emulator showed it
  never opened while the notification was marked "silent", which Android never lets interrupt: fixed, and
  `AdhanFullScreenTest` fails on the old code), the service waits until
  it is really in the foreground before starting the sound, a focus loss no longer stops the Adhan (only a phone call
  does), and an early end or player error resumes where it was (up to 3 times). The alert history now records the
  reason an Adhan ended. Checked on Android 14 and 15 emulators with a real scheduled prayer alarm (clock moved to just
  before the prayer, Noor's process killed, screen off, Doze): the full test Adhan plays (31.5 s), even with audio
  focus refused. The Android 17 emulator image does not run reliably on GitHub's machines (its system services drop
  out after boot), so Android 17 itself is checked only on the owner's phone: after installing, the alert history
  should show "played" with the whole duration at the next prayer.
- **Location**: redesigned completely ([LOCATION.md](LOCATION.md)).
- **Sun marks and the black Sun** (committed earlier the same day): Shuruq and Maghrib marks exactly where the drawn
  path meets the horizon (the Sun's upper edge with refraction), Fajr and Isha below ground at the Sun's real
  depression; the land opens a little where the Sun rises and sets so it is never hidden; the Sun's disc colour comes
  from its light through the air (it reddens and dims, never black). `SunMarkTest` covers five cities from Reykjavik to
  Jakarta, three dates and two screen sizes.

Evidence: unit and Robolectric tests (counts in the release notes), software renders in `app/build/screenshots`, the
`device-alerts.yml` emulator runs, and `phone-preview.yml` (Android 15 emulator photographs of the countdown before and
after a prayer, the notification, the location screens with real satellite imagery, the Qibla box held at its limit,
the full-screen Adhan), published to the `phone-preview` pre-release.

On the emulator, at a prayer's time the notification switched from "−" to "+" 5 to 10 seconds late (showing "00:05"
meanwhile, never a wrong sign); the device log shows nothing wrong, the emulator was slow (software GPU, constant screen
captures). On a phone the switch should come within about a second.

Tests at 0.9.7: `core/prayer` 97 (1 skipped by design), `alerts` 14, `app` 46, `wear` 22; all pass.

Still needs the owner's phone: the scheduled Adhan on Android 17 (the decisive check), the full-screen Adhan over the
lock screen, the notification's switch at a prayer's time, map gestures and imagery speed, the phone's geocoder
results, and the compass near metal.

## 13. Notification digits, location regions, smooth map, scenery, night sky, clouds, the watch face (0.9.8, 8 November 2026)

What the owner asked, and what was done:

- **Notification digits** (the owner's photo: Noor in English, the phone in Arabic, the countdown in Arabic-Indic
  digits). Three causes, all fixed: (1) Android's Chronometer writes its digits in the phone's language and cannot be
  told otherwise; now, when the phone's digits differ from Noor's, Noor writes the countdown itself (`StatusTicker`:
  once a second, only while the screen is on). (2) To know the phone's language Noor must ask Android
  (`LocaleManager`): on Android 13+ the language chosen in Noor is applied to its whole process, so the process's own
  configuration said "English" on an Arabic phone (found on the emulator: Noor in Arabic on an English phone showed
  Western digits). (3) Noor's formatter wrote numbers in the process's default locale and only ever converted Western
  digits to Arabic-Indic, never back (found on the emulator: "−٣:٠٤:٢٣" with Noor in English on an Arabic phone); every
  formatted number, on the phone and on the watch, is now normalised to the chosen digits. Tests: every combination of
  phone and Noor digits (`StatusNotificationTest`), the formatter under ar-SA, ar-EG, fa-IR and en-US default locales
  (`FormatterTest`, `WearTextTest`), and Android 15 emulator photographs and logs (`phone_098.sh`). The emulator's
  language switch is not always complete (sometimes the status bar stays half English), so the decisive check is the
  owner's phone.
- **"Next prayer" twice** in the expanded notification: the header line Android draws already says it; the layout's
  own caption is gone (the design is otherwise unchanged).
- **Time since each prayer** (the "+" after a prayer): longer, and now with a switch (Settings → Time since each
  prayer). Fajr, Dhuhr, Asr and Isha 30 minutes, Maghrib 15, Shuruq 20. Why: the "+" should last while a person is
  plausibly still between the Adhan and the end of the congregation. The wait from Adhan to iqama is commonly about
  20–25 minutes at Fajr and 15–20 at Dhuhr, Asr and Isha, and short at Maghrib (about 5–10); the prayer itself takes
  another 5–10 minutes. 30 / 15 covers the iqama wait and the prayer in most
  mosques without lingering into the next part of the day. Shuruq's 20 minutes is about how long after sunrise the Sun
  takes to rise "a spear's length", when the forbidden time ends and Duha may be prayed. It always ends earlier if the
  next prayer comes first.
- **Arabic Settings title** (الإعدادات's last letter dropping): the screen titles used negative letter spacing, which
  breaks Arabic joining; Arabic titles now have none.
- **Location: country → state/emirate/region → city → area** ([LOCATION.md](LOCATION.md)): for countries with many
  places, the country opens on its largest cities and its regions (emirates, states, governorates, provinces,
  regions: named as each country does).
- **Map** (no dark tiles while panning and zooming): one tile cache for the whole app (kept between screens), tiles
  fetched nearest the centre first by six workers, old requests dropped as the view moves, the parent tile (up to six
  levels up) or the children shown while a tile loads, and a zoom level that changes with hysteresis.
- **Home scenery** (Settings → Home scenery): Automatic (by place: high places and alpine countries mountains,
  islands the sea, arid countries dunes, the rest hills), or Dunes, Hills, Mountains (snow on the summits) or Sea
  (water mirroring the sky, the Sun's glitter and the moon's glade, a beach).
- **Night** ([DESIGN.md](DESIGN.md#the-home-screen)): the real sky for the user's place and the moment shown, 1,600
  catalogue stars, the Milky Way where it is, moonlight on the sky, clouds and land by the moon's phase, airglow,
  shooting stars.
- **Clouds**: real cumulus, rendered offline as volumes and relit on the phone for the Sun or the moon; sharper and
  cheaper to draw. Checked on Android 14 and 15 emulators (`home-sky.yml`: the GPU shaders compiled and drew every
  moment).
- **Watch face** ([WATCH.md](WATCH.md#the-noor-watch-face-098)): the error in the owner's photo ("The maximum number of
  attempts to set the watch face as active has been reached") came from the watch app asking Wear OS again to make its
  face active; Wear OS allows that once per app. It now asks at most once and otherwise guides the user to pick Noor
  on the watch. The face the watch app installed was still the 1.3.2 design; there is now a new Noor face (Horizon,
  Dial, Minimal, in five colours; the original stays available) that shows Noor's own data through Noor's
  complications. A watch-face editor on the phone's Watch page (and on the watch) chooses design and colour; the watch
  replaces the face in place, so it changes at once. After the watch app is updated, an older Noor face is replaced
  with the new one automatically.

Evidence: unit and Robolectric tests (counts below), software renders in `app/build/screenshots` and
`wear/build/screenshots`, all 16 watch faces validated by Google's Watch Face Push validator at every build, the
`home-sky.yml` (Android 14/15), `phone-preview.yml` (`phone_098.sh`, Android 15) and `watch-face.yml` (Wear OS 6.0
emulator: the watch app installs the face, makes it active and switches designs in place; photographs) runs, published
to the `home-sky-preview`, `phone-preview` and `watch-face-preview` pre-releases.

Tests at 0.9.8: `core/prayer` 101 (1 skipped by design), `alerts` 14, `app` 53, `wear` 29; all pass.

Still needs the owner's devices: the notification's digits on the owner's phone in Arabic (the emulator's language
switch is not always complete), the Noor face on a real watch (it is installed, activated, filled with Noor's data
and switched between designs on a Wear OS 6.0 emulator; Samsung's One UI 8 Watch may differ), the face's ring and
seconds over a full day, the phone editor's "Show on my watch" over Bluetooth, and the new clouds and night sky on a
real phone's GPU.

## 14. Clouds from photographs, weather, scenery, speed (0.9.9, 8 November 2026)

What the owner asked (phone app only; the watch app was not part of this round): 0.9.8 started with nice clouds,
then after a few seconds switched to old, poor clouds with bad performance; the clouds looked like rectangles, few,
bland, and bigger than the mountains; the hills and the beach looked like artifacts. Later: much more variety —
isolated clouds, clusters, broken and layered skies, overcast, towering storms, lightning, rain, rain shafts and virga —
evolving naturally through day and night, at a realistic scale and perspective.

What was found and done:

- **The switch to the old clouds** was the app protecting itself: 0.9.8 drew the star picture on the main thread
  nearly every frame during the opening and while dragging at night, and computed every cloud for every pixel of the
  sky 15 times a second; the frames slowed, and the quality guard switched the phone to the old Canvas renderer and
  remembered it. Now the stars are drawn in the background, the guard only lowers the resolution (never switches
  renderer, and starts afresh under a new key), and the clouds left the sky shader (about 21 600 → 15 000 operations,
  the new overcast and cloud-layer parts skipped when the weather has none); the sky shader redraws once a second by
  day instead of 15 times (4 times under an overcast, 15 at night for the stars). The emulators draw on the CPU, so
  their frame times say nothing about a phone's GPU; how smooth it is must be judged on a phone.
- **Clouds** ([DESIGN.md](DESIGN.md#the-home-screen)): real clouds cut out of public-domain and CC0 photographs
  (`tools/sky/photo_clouds.py`, `tools/sky/photos/manifest.json`), placed in true perspective (6 to 110 km away), lit
  by the moment with one colour matrix each, the same on every phone (Canvas sky included). The computer-generated
  clouds tried first in this round were dropped: the owner rightly found them unrealistic.
- **Weather**: a character for each day and place, evolving hour by hour; clusters, layers, overcast, storms with
  lightning, rain, rain shafts and virga (`Weather`, `CloudField`, `CloudLayer.kt`).
- **Grey boxes**: the text-readability limit dimmed any cloud behind the text to grey; clouds now fade out below the
  text instead.
- **Time since each prayer**: 30 minutes after every prayer, Shuruq and Maghrib included (the owner's choice; 0.9.8
  had Maghrib 15 and Shuruq 20), on the home screen and in the notification; it still ends earlier if the next prayer
  comes first, and Settings → Time since each prayer still turns it off.
- **Scenery**: hills soft (groves, meadows, round tree crowns near) instead of blocky noise; mountains shaded and
  snowed by altitude (no vertical bands); a plain beach of dry and wet sand; the headland slopes into the sea; gentler
  crest rims; sea glitter sparkles rather than a white blot; land and sea lit flat and grey under an overcast.

Evidence: unit and Robolectric tests (`CloudFieldTest`: every kind of day comes up, a day evolves without jumps,
storms flash, sizes and places, lighting; `CloudAtlasTest`: every cloud whole and where the table says; the shaders'
size and compilation), design-bench renders of every weather and time of day, and the `home-sky.yml` Android 14/15
emulator run (GPU sky, clouds and land drawn by Android itself), published to the `home-sky-preview` pre-release.

Tests at 0.9.9: `core/prayer` 101 (1 skipped by design), `alerts` 14, `app` 58, `wear` 29; all pass.

Still needs the owner's phone: how smooth it feels on a real (and an old) phone's GPU, and how the clouds, weather and
scenery look on a real screen. Phones older than Android 13 get the new clouds and weather but keep the simpler Canvas
land.

## 15. Home-screen widgets (0.9.10, 8 November 2026)

The owner asked for widgets. Three, designed to match the home screen ([DESIGN.md](DESIGN.md#home-screen-widgets-0910-0911)):
**Next prayer** (2×1), **Today's prayers** (4×2) and **Noor sky** (4×2, the home sky in small with the Sun's path and
the prayers). They show the same countdown as the home screen and the notification, in Noor's language and digits,
and update themselves at each prayer, at midnight and every 15 minutes for the picture, without waking a sleeping
phone. Tested with Robolectric on a simulated home screen (`WidgetsTest`: content, Arabic, the phone's other digits,
the next update, pictures); not yet seen on a real launcher.

## 16. The widgets' countdown, as on the home screen (0.9.11, 8 November 2026)

The owner wanted the widgets to show the home screen's countdown exactly: −/+, hours, minutes and seconds, ticking.
0.9.10 already ticked seconds where the phone's digits matched Noor's, but on the owner's phone (Arabic phone, Noor in
English) it fell back to "−1 h 23 min". Now every widget shows "−1:23:45" / "+13:00" with "−1:23" bright and ":45"
quieter, whatever the phone's language: Android ticks it (a Chronometer, so Noor need not run), and Noor's own fonts,
built by `tools/widget/count_fonts.py` from Plex, draw whatever digits Android writes as Noor's digits and leave the
seconds out of the bright copy laid over a quieter one ([DESIGN.md](DESIGN.md#home-screen-widgets-0910-0911)). The
per-minute updates of 0.9.10's fallback are gone. `WidgetsTest` checks the text Android writes ("−1:27:00" at 14:10,
"+13:00" at 15:50 in Istanbul on 4 November 2026), that on a screen it ticks by itself, one second each second with
no update from Noor, the pair shown for each choice of digits and the next update, and saves pictures of each state. The fonts' shaping was checked with HarfBuzz. Not yet seen on a real launcher: whether
every launcher applies a widget's own fonts is the thing to look at on the phone (if one did not, the countdown would
still tick, but in the system font and the phone's digits, with the seconds bright).

## 17. Reminder before each prayer (0.9.12, 13 November 2026)

Started on the owner's PC after 0.9.8 and left unsaved there; saved, moved onto 0.9.11 and finished in the first
session on the PC. Adhan tab → **Reminder before each prayer**: Off (default), 5, 10, 15, 20 or 30 minutes. A
notification "Asr in 15 minutes" (before sunrise "Fajr time ends in 10 minutes"), with the time and the place, in
Noor's language and digits, for every prayer whose alert is on, on the device that alerts; it goes away when the
prayer begins. It uses the device's notification sound and Do Not Disturb, not the alarm stream. Never before the
previous prayer has begun; re-checked when it fires, like the Adhan. The watch acts on it from 0.9.12 (both apps
moved to 0.9.12; earlier watches ignore the setting). Details: [ALERTS.md](ALERTS.md).

Evidence: `AlertPlannerTest` (4 new: setting round trip and old settings unchanged, which prayers and when, never
before the previous prayer, re-check and minutes left) and `AlertSchedulerTest` (2 new, Robolectric: alarms armed
beside the Adhan alarms and cancelled when off or moved to the watch; the notification's channel, English and Arabic
titles with Arabic-Indic digits, singular minute, the sunrise wording, removed when the prayer is called). Not yet
seen on a phone or watch: that the reminder arrives on time on a real device and how it looks in the shade.

Also fixed for building on Windows: `tools/watchface/build_pushed_face.py` now finds `apksigner.bat`.

Tests at 0.9.12 (run on the owner's PC): `core/prayer` 105 (1 skipped by design), `alerts` 16, `app` 59, `wear` 29; all pass.
## 18. Widget countdown on a real phone (0.9.13, 13 November 2026, phone only)

The owner's first look at the widgets on his Samsung phone (Arabic phone, Noor in English with Western digits): the
countdown showed the phone's Arabic-Indic digits ("−١:٤٤") and its seconds as "...", so it looked stuck. Cause: a home
screen builds widgets in a restricted mode in which a TextView does not load an app's font resources, so 0.9.11's
countdown fonts (which drew any digits as Noor's and blanked the seconds of a bright copy) were never used outside the
Robolectric tests; the "..." was the text being cut short. Fix: where the phone writes Noor's digits, Android's
Chronometer ticks the seconds as before (one copy, the phone's light font); otherwise Noor writes the countdown in its
own digits to the minute ("−1:44", "+0:13") and updates it as each minute turns (non-waking alarm, so only while the
phone is awake), as 0.9.10 did but in the home screen's h:mm form. The countdown is never ellipsized. The fonts and
`tools/widget/count_fonts.py` are removed. To get ticking seconds back on such a phone, the owner can choose the same
digits in Noor as the phone uses (or set the phone's numbers to Western, where One UI offers it).

Evidence: `WidgetsTest` (the owner's case simulated: phone writes Arabic-Indic, Noor Western → "−1:27" in Western
digits on all three widgets, "−1:26" a minute later, next update half a second after the minute turns; matching digits
→ Android's ticking countdown; Noor Arabic-Indic on a Western phone → "+٠:١٣"); pictures `90_widget_*_minutes.png`.
Not yet seen on the owner's phone after the fix. Tests at 0.9.13: `app` 59, all pass (other modules unchanged).
## 19. Real-time seconds in Noor's digits on the widgets (0.9.14, 13 November 2026, phone only)

The owner asked for the widgets' seconds in real time, and the numerals always Noor's, whatever the phone's language.
Android's digital clocks cannot do both on a phone whose language writes other digits (above), so the seconds are now
a wheel of Noor's digits turned by an AnalogClock's second hand, seen through a one-label window, and the hours and
minutes a picture Noor draws each minute (partial update, sky pictures untouched). Details and limits:
[DESIGN.md](DESIGN.md#home-screen-widgets-0910-0911-0913-0914). Android 12 or later; older phones keep 0.9.13.

The wheel's drawables are generated: `java tools/widget/SecondsWheel.java` from the repository root (JDK 17) rewrites
`res/drawable/noor_sec_*`, `noor_wheel_*` and `res/values/noor_wheel.xml` (change the size or font there, not by hand).

Evidence: `WidgetsTest` draws widgets and reads the second shown in the window against the real clock (counting up
after Asr, down before it, Western and Arabic-Indic digits, and the owner's case: phone writing Arabic-Indic, Noor
Western), checks the picture of "−1:27" / "+13" pixel for pixel, the minute's update (armed at the minute, partial,
picture untouched), and the old-Android fallbacks. Not yet seen on a real phone: whether Samsung's home screen turns an
AnalogClock's second hand once a second (Android's default) as the emulator-free tests assume, and how the wheel looks.
Tests at 0.9.14: `app` 59, all pass (other modules unchanged).

## 20. The real sky (0.10.0, 14 November 2026, phone only)

The owner's brief (evening of 13 November): "When someone opens Noor, they should see a real place through a window,
at this moment, with a sky more beautiful than they expected." The 0.9 clouds looked flat to him. He asked for
something real and breathtaking, never cartoonish, changing through the day, the night and the year by itself, and
answering to touch; the clouds matter most, and sunrise and sunset must take the breath away. His decisions: a fixed
camera; quality tiers, automatic by default; six sceneries as different as possible, the Pacific Northwest among them
(his favourite); seasons by date and hemisphere; invented "Showcase" weather by default, with real local weather
possible later; Sun, moon and stars true to place and time; only distant birds, nothing man-made; graphics only (no
sound, no live wallpaper yet). Claude Fable 5.2 worked through the night as the maintainer of the home screen's
graphics, free to set CLAUDE.md's rules aside where that clearly made the graphics better, except four: prayer
times and their tests, honest reports, the signing keys, `legacy/`.

**What was built.** A 3D renderer for the home screen, described in [DESIGN.md](DESIGN.md#the-real-sky-010): volumetric
clouds lit by the real Sun and moon, a physical atmosphere, six 3D sceneries with seasons, a weather director, the
real stars and aurora, the whole screen as the window with the day's times on frosted glass, a breath of wind on
touch. The 0.9 sky is untouched and is the fallback ("Classic").

| What | Where |
|---|---|
| Shaders (GLSL ES 3.00), shared by the phone and the design bench | `app/src/main/assets/sky/` |
| Renderer: its own thread, EGL, passes, caches, tiers | `app/src/main/java/com/noor/app/sky/RealSkyEngine.kt`, `RealSky.kt` |
| The moment as the renderer needs it; the weather director | `RealSkyFrame.kt` (`SkyDirector`, `SkyState`, `RealSkyFrame`) |
| Where it joins the home screen | `LivingSky.kt` (`real` parameter), `TodayScreen.kt` (`RealSkyLayer` behind the page) |
| Settings → Home scenery: six sceneries, Sky quality | `SceneryScreen.kt`, `AppSettings.kt` (`Scenery`, `SkyGraphics`) |
| Design bench (the same shaders in a browser) | `tools/sky3d/` |
| Pictures on an Android emulator | `tools/devicetest/real_sky.sh`, `.github/workflows/real-sky.yml` (pre-release `real-sky-preview`) |
| Tests | `app/src/test/java/com/noor/app/sky/SkyDirectorTest.kt`, `SceneryScreenTest.kt` |

**Decisions, and why**

- *A renderer of our own in OpenGL ES 3.0*, not more AGSL: volumetric clouds need 3D textures, several render
  targets and float buffers, and AGSL refuses programs of that size (section 11). ES 3.0 is on nearly every phone
  since 2014; where float render targets are missing the 0.9 sky is used.
- *The camera never moves* (the owner's decision), which is what makes it affordable: the land is found in 3D once
  and only relit; one cloud pixel in sixteen is redone per update; the clear sky is drawn small.
- *The Sun is drawn where the day's path puts it* (0.9's chart), and the direction through the camera lights the
  scene; its colour comes from its true elevation. By day its light falls from the side (where it rose in the morning,
  where it will set in the afternoon), so land and clouds are modelled by light and shade.
- *0.10.0, not 1.0*: nothing of it has been seen on a phone yet.
- *Old scenery choices carry over*: Hills → rainforest, Mountains → red rock, Sea → open ocean, Dunes → dunes;
  Automatic picks by place (Arctic above 60° latitude, dunes in arid countries, ocean on islands, savanna on
  grasslands, red rock for rock or high ground, otherwise the rainforest).
- *Real local weather is not built.* It would plug in at `SkyDirector.at`, which returns the `SkyState` everything
  else draws from.

**Rules of CLAUDE.md set aside** (as the brief allowed), and why

- Rule 5 ("change only what the task needs", the home screen and the scenery screen as they were): the home screen
  is recomposed (scene behind the whole page, times on glass) and Settings → Home scenery now offers six sceneries
  and a quality choice. That was the task.
- The 0.9 guarantee of 4.5:1 for bare white text on the sky does not hold over the real sky: the limits behind the
  text are 0.34 and 0.50 with a deeper letter shadow, because a sky dimmed further looked dull. The guarantee and its
  tests still stand for the classic sky.
- Kept: prayer times and their tests (not touched), honest reporting, the keys, `legacy/`, nature-only scenery
  (rule 10), Palestine (rule 9), no scraping, no unlicensed assets (the renderer ships no pictures or recordings at
  all: every texture is computed on the phone; the six scenery cards in Settings are renders of our own shaders).

**Found on the way**

- `SkyDirector.weather` seeded `java.util.Random` with neighbouring numbers for neighbouring days, which gives nearly
  the same first values: every day of a week got the same kind of weather. The seed is scrambled first now.
- Rays that skim flat ground or water ran out of steps in the land pass and left a strip of sky showing through the
  land (canyon, savanna), and the foot of a far mountain missing. The shortest step now grows with distance.
- Bright scenes were clipped to one tone by the glass; it now dims in proportion.
- In the light theme the white text on the sky would have stood on the cream page for the seconds before the
  scene's first picture: a dusky "waiting sky" now holds its place.
- An overcast day showed blue gaps once heaped clouds stood on flat bases (the noise that shapes them is read in
  columns): where much of the sky is covered the gaps now fill in.
- On this PC `avdmanager` writes sizes in Arabic-Indic digits into an emulator's `config.ini` (the Windows locale),
  which the emulator cannot read; they have to be rewritten in Western digits.

**The owner's PC and emulators.** An Android emulator cannot run on the owner's PC as it is: hardware
virtualisation is switched off in its BIOS ("Virtualization Enabled In Firmware: No"), and without it the emulator
does not start the system (it sat at "offline" using no CPU until it was stopped). The emulator and an Android 14
system image were installed under the SDK (`emulator`, `system-images;android-34;default;x86_64`, about 2.6 GB) and
left there for a later session; the virtual device made for the attempt was removed. With "SVM Mode" enabled in
the BIOS, a device made with `avdmanager` and started with `-gpu host` would draw the real sky with the PC's own
graphics card (far closer to a phone than GitHub's software GPU).

**How it was tested**

- *By eye, in the design bench* (`tools/sky3d`): the phone's own shader files in Chromium (WebGL 2) on the owner's
  PC (a Radeon RX 6900 XT): every scenery at many moments from Fajr to deep night, the director's own weather for
  the coming days in Dubai, storms, rain, overcast, dust, mist, the seasons, both themes, Arabic mirroring, the three
  lower tiers, with the header's text drawn in to judge how it reads. That card is many times faster than any phone:
  the bench shows the look, not the speed.
- *Unit tests*: `app` 67 (the 59 of 0.9.14 and 8 new in `SkyDirectorTest`), all pass; `core/prayer` 105, `alerts`
  16, `wear` 29, unchanged and passing. Both release APKs build.
- *On Android* (GitHub's Android 14 emulator, whose GPU is software, 360x800, Low tier, the dunes): the renderer
  compiled every shader, built itself, drew the land, kept it and loaded it from the cache on the next start, and
  showed the scene inside the Today screen with the times on glass, in the dark and the light theme (log: "Real sky
  built", "Land drawn", "Land from the cache", "Real sky shown: dunes"; screenshots half an hour after sunrise with
  its lit cloud bands, at midday, in the early afternoon, just after sunset and at dusk; the first two of the final
  build). The log's timings say where that emulator's ten minutes per picture go: about 3.5 minutes in the land
  and nearly 5 in the *first* drawing of the scene, which is its shader compiler at work; once compiled, the same
  scene takes it 50 ms and sixteen cloud updates 40 ms. A real phone compiles in its GPU driver instead (and Noor
  keeps the compiled programs), but how long that takes on a given phone is not known: the first start may show
  the waiting sky for a while. The rainforest could not be photographed there: while that emulator compiled its
  land shader the Android system around it gave up and restarted ("The system died"), as it had at a larger screen
  size. Earlier in the night the old home-sky test, which closes the app after 20 seconds, did so three times before
  a first picture: the three-starts guard switched to the 0.9 sky, as designed.
- *GitHub's runs on `main`* (three pushes, the last `fb71184`): every test, both APKs, the watch face validation,
  the Adhan on Android 14 and 15 emulators, and the 0.9 sky's emulator test (which now asks for "Classic") passed
  each time; the download links carry the last build.

**Not tested: any phone.** Unknown until the owner opens it: the speed, the battery use and warmth, the look on a
real screen, whether a real phone's GPU driver (Adreno, Mali, Xclipse) accepts every shader (if one does not, the 0.9
sky is shown), the first start's wait while the shaders are compiled (later starts use the cache), the breath of wind
and dragging the Sun over the new scene, TalkBack, Arabic and the light theme on a device.

**Open work**

1. The owner's phone first. `adb logcat -s NoorRealSky` shows the GPU, the tier, how long the land and each picture
   took ("Picture: scene N ms, finish N ms"): tune the tiers (`RealSky.Tier`, `RealSky.guess`) from that.
2. Real local weather as an option (at `SkyDirector.at`).
3. Ideas not built: a long press that fast-forwards the day; each prayer's own sky sign; rainbows, Sun dogs, meteor
   showers on their dates; the widgets and the notification still use the 0.9 pictures.
4. Known weak spots: the foregrounds under the glass are plain (the rainforest's bluff, the savanna's grass); cloud
   edges show a fine stipple, coarser at the Low tier; the emulator pictures are too few and too small to judge
   anything but that it runs.

## 21. The real sky from pictures of real places (0.11.0, 14 November 2026, phone only)

The owner opened 0.10.0 and was "severely disappointed": the sceneries "bland and empty... lifeless", the clouds
"absolutely disgusting", "the sun doesn't even appear". His brief for 0.11: "completely redo everything... I want the
app to look like real life... indistinguishable from real life", six sceneries (no savanna), complete freedom to use
his PC and the web, as much time as needed; the same four untouchable rules as in section 20. Through the day he
judged previews and gave these decisions, all in force:

- **Photorealism is judged against photographs**, by him. "Nothing should look flat and artificial. It should have
  depth and look photorealistic." Whatever was shaped by formulas he called fake at once (0.10's land and clouds, a
  first procedural beach, the first noise clouds of 0.11).
- **Natural skylines.** "What I don't like is cutting mountains in order to have the sunrise and set on the
  ground... make everything look natural." A level sunrise is fine where the place has one.
- **Exact times all the same**: he asked for "a clever solution" (below: the Sun's path stands on the skyline).
- **Beauty first by default**: seasons and the true stars for the place are switches in Settings → Home scenery,
  both off by default ("by default it should show the most beautiful views that app has to offer").
- **The beach**: real sand colour, waves that come in on the shore, no rocks in the way of the sunrise, depth.
- Do not make dialogs pop up on his screen (downloads are fetched with curl, not through a browser's save dialog).

**What 0.11 is.** Everything that does not move is rendered once, offline, as a film would render it, through
Noor's fixed camera under a set of unit Suns, and the phone only relights and composes those pictures; what moves is
drawn live (sky, Sun, Moon, stars, water, waves, birds, weather). [DESIGN.md](DESIGN.md#the-real-sky-011) describes
what is seen and how it is put together; `tools/scenery/README.md` is the brief a scenery must meet.

| What | Where |
|---|---|
| Baking: the framework, one script per scenery, clouds, the night sky | `tools/scenery/` (`bake.py`, `skybake.py`, `<scenery>.py`, `sky_low.py`, `sky_high.py`, `nightsky.py`, `geotiff.py`, `lift.py`) |
| Every source and licence | `tools/scenery/sources/*.md` |
| The pictures the app ships | `app/src/main/assets/sky2/land/<id>`, `.../clouds/<id>`, `.../night` |
| Shaders (GLSL ES 3.00), shared with the bench | `app/src/main/assets/sky2/*.frag`, `*.glsl` |
| Renderer, sceneries and tiers, the moment and the weather director | `app/src/main/java/com/noor/app/sky/` |
| Design bench (the same shaders on the same pictures, WebGL 2) | `tools/sky3d/` (`python tools/sky3d/serve.py`, then `/tools/sky3d/index.html`) |
| Blender 4.5 LTS, downloads, work files (not in the repository) | `%USERPROFILE%\ProjectMinhad\NoorTools` (`blender-4.5.16-windows-x64`, `assets`, `work`) |

**The six sceneries, and what each is made from**

| Scenery (`Biome` id) | The place | Real data | Still made by formula |
|---|---|---|---|
| Arabian dunes (`dunes`) | a sand sea | none exists at this scale: a Werner sand-transport simulation | all of it; the far ranges are unbroken |
| Pacific Northwest (`pnw`) | a lake under Mount Rainier | USGS elevation model (the mountain), Poly Haven scans (stones, logs, ferns, bark, twigs) | tree trunks and branching, the shores' outlines |
| Arctic (`arctic`) | Reinefjorden, Lofoten | Kartverket's 5 m model and Copernicus GLO-30, as they are; Poly Haven snow and shore rocks | where snow lies, crags finer than 5 m |
| Ocean (`ocean`) | a beach south of Ras Al Khaimah, looking towards Musandam | ambientCG sand scans (toned to beach sand), Copernicus GLO-30 for the mountains across the water | the beach's simple profile; the sea is drawn live |
| Green hills (`meadow`) | the Diablo Range over Calaveras Reservoir, in spring | USGS 10 m model as it is; Poly Haven grass, flowers, stones | the oaks |
| Red rock (`canyon`) | Monument Valley from the valley floor | USGS 10 m model as it is; Poly Haven rock, earth, brush | wall relief finer than 10 m |

**Clouds.** Twelve sets (`clouds/<id>`), each path traced under the same lights plus Suns under the horizon, a white
sky and white ground. The first sets, made from noise fields, looked computer-made beside photographs (blobs,
"metaballs") and were thrown away. The heaped kinds are now built from film-quality cumulus volumes: CGHEVEN's free
cloud VDBs (author Ammar Khan; free also commercially in finished work such as renders and games; the files
themselves may not be redistributed, so only pictures made from them are in the repository). Low clouds are fields
of them on one base; altocumulus and cirrocumulus are thousands of small copies in ranks, rolls and ripples; cirrus
and the veils are procedural.

**Decisions, and why**

- *Baked pictures, not a 3D renderer on the phone.* 0.10 proved that what a phone can raymarch in real time looks
  like a game. A path tracer gives real shadows, light bouncing in a forest, light inside a cloud; the fixed camera
  makes it affordable (a scenery is about 3 to 4 MB).
- *Real data first.* Elevation models for shapes, photogrammetry for surfaces. Where the owner could tell, he
  preferred the real thing every time; the two sceneries first arranged by formula (Arctic's lone peak, Red rock's
  buttes) were rebuilt from real viewpoints, nothing moved or cut.
- *The Sun's path stands on the scenery's own skyline* (`bake.sun_lift` writes `lift` into `land.json`;
  `RealSky.sunLift`, `SunLift`, `SkyGeometry.y(el, fraction)`): it clears the land exactly at Shuruq and goes behind
  it exactly at Maghrib, over a mountain as over the sea. Prayer times themselves are untouched.
- *A showcase night by default* (`RealSkyFrame.showcaseSky`): the Milky Way's centre stands in the picture every
  clear night; the true sky for the place is the "Real stars" switch.
- *The sea on a beach is drawn, not baked* (`uShore`, `scene.frag`): waves with real height (the view ray is walked
  onto them), breaking by size, foam in patches, a sheet running up the sand in lobes and leaving it wet. A sea is
  darker and bluer than its sky because unseen waves tilt it: rough-surface Fresnel, and the sky above the horizon
  mirrored; far off, the Sun's road is the sum of its glints (drawn wave by wave it flickered in dashes).
- *The Sun is drawn at no more than 60* in a half-float picture (0.10 wrote up to 60 000: the likely reason it was
  black on the owner's phone), and the text filter no longer dims it under the header.

**Rules of CLAUDE.md set aside**, and why: rule 5 again (the home screen and Settings → Home scenery are changed:
that was the task; `Scenery.SAVANNA` now shows Green hills). Kept: prayer times and their tests, honest reports,
the keys, `legacy/`, nature only (the coastal plain in front of Musandam is pressed flat so nothing built on it is
in the picture; a reservoir's dam is outside the Green hills' frame), no scraping (files are fetched one by one from
pages that offer them free, or from public APIs and buckets), every source and licence written down.

**Found on the way**

- Several Blender jobs at once starve each other: at normal priority a bake could be fed the GPU at under 10 %.
  Three at a time, the baking one at AboveNormal priority, was the fastest.
- Cycles takes volume density per object unit: a VDB enlarged k times renders k times thinner.
- `WidgetsTest` failed on a PC busy rendering: it accepted only the first and last second of its look at the
  widget's seconds wheel, and the look took more than a second. It now accepts every second of the look.
- The mirror image of a far shore broke into dashes of sky where the search for it ended on the water in front of
  the shore; and far waves that a pixel cannot show flickered. Both fixed in `scene.frag`.
- On GitHub's emulator (a software GPU) a picture takes over a minute and Android shows "isn't responding" for its
  own launcher and for Noor over the screenshots. The renderer does not hold the main thread; the one thing 0.11
  did there, decoding a picture to find the skyline, is gone (`lift`).

**How it was tested**

- *By eye, in the design bench* (the phone's own shader files on the phone's own pictures, in Chromium on the
  owner's PC): every scenery from before sunrise to deep night with the header's text and the glass panel drawn
  in, every cloud set over several sceneries at midday, low Sun and after sunset, the beach's waves at several
  moments of their cycle. Each picture was put beside photographs of such places and its faults named before it was
  accepted; the owner saw the previews as they were made and sent several back. The bench shows the look, not the
  speed, and not a phone's screen.
- *Unit tests*: `app` 71 (among them `SkyDirectorTest`: the director, the light weights, the camera, that every
  cloud set the director can choose exists, the showcase night; `LivingSkyTest`: the Sun's path on a skyline),
  `core/prayer` 105, `alerts` 16, `wear` 29: all pass. Both release APKs build.
- *On Android* (GitHub's Android 14 emulator, a software GPU, 720x1600, High tier; `real-sky.yml`): the beach and
  the dunes at sunrise, midday and sunset: every shader compiled, the pictures were read and "Real sky shown"
  logged, with no GL error. A picture takes that emulator over a minute, and Android showed "isn't responding"
  dialogs over the screenshots (see above): the pictures prove that it runs there, nothing about speed.
- *GitHub's run on `main`* (`05ee9d7`, 14 November 2026): "Android build and tests" passed (every test, both
  APKs, the watch face validation) and republished the download links: `Noor-phone.apk` is 0.11.0, 59.8 MB (the
  pictures are about 28 MB of it). On the work branch before the merge, `real-sky.yml` photographed all six
  sceneries at midday and sunset on the emulator: each logged "Real sky shown" with its skyline's lift, no error.

**Not tested: any phone.** Everything that only a phone can tell is still unknown: whether the Sun is seen on the
owner's phone (0.10's was not), the speed and the wait at the first start, battery and warmth, the look on a real
screen, whether a real GPU driver accepts every shader (if one does not, the 0.9 sky is shown), Arabic and the
light theme on a device, TalkBack.

**Open work**

1. The owner's phone first (`adb logcat -s NoorRealSky`: the GPU, the tier, how long each picture took).
2. Size: the six sceneries and twelve cloud sets add about 30 MB to the APK. The lit pictures tolerate more
   compression than they have; `bake.run` should take samples and pack quality as arguments (three scenery
   scripts wrap it to set them).
3. Known weak spots: on ruffled water the mirror image of a far shore breaks into hard little dashes; the
   thunderhead's anvil has a ruled-straight top; the Pacific Northwest's far forest is one even dark wall, its
   mountain is in shade at low Sun (no alpenglow), and one tall fir crosses the countdown's digits; the Arctic's
   snow slopes are smoother than Reine's; Red rock's buttes are small in the frame; the dunes are a simulation,
   not a real place; the oaks of Green hills are grown by formula.
4. Real local weather as an option (`SkyDirector.at`); the widgets and the notification still use the 0.9
   pictures; the watch has no part in any of this.
5. The work branches of this version (`sky/photoreal`) can be deleted once merged.

## 22. The sky: clouds that vary and drift, the Sun through them, a sharp night (0.12.0, 15 November 2026, phone only)

The owner installed 0.11.0 on his phone: it works, and the Sun shows (0.10's did not). His verdict, which set this
version: the clouds are the hardest thing and the next update must be "totally" about them. "During some times of the
day, the entire sky, all the clouds in the sky suddenly transform into a different type of cloud." "It's not the
entire sky is just one type of cloud and they're all evenly spaced apart. No, that looks fake and artificial and
bland. In real life there's variety... isolated clouds, scattered clouds, broken clouds, and then overcast... There's
randomness." The Sun "is close to being invisible, as it always appears behind clouds". The night: "the direction
you are going is nice, but it's not up to my level". And: "you are the decision maker... finish the job."

**What was wrong, and what replaced it** ([DESIGN.md](DESIGN.md#the-real-sky-011), the 0.12 paragraphs)

| Complaint | Cause | Now |
|---|---|---|
| The whole sky transforms at certain times | `SkyDirector.at` chose the cloud sets again from values that depend on the Sun's height (the hour of colour, after sunset, night), and the renderer cross-faded all three levels in seven seconds | Each level's sets are legs of a slow drift (`SkyDirector.legs`): a set crosses the view in 1.6 to 3.1 hours and gives way over a fifth of that; the choice never depends on the Sun; a test forbids any step |
| One kind of cloud, evenly spaced | Each set was one kind, scattered evenly; three sets at once, each thinned by an amount | Seventeen composed skies (structure first, mixed kinds and sizes), twice the view wide, drifting |
| The Sun always behind cloud | It is drawn within 14 degrees of the horizon, where far clouds stack up | The weather decides whether it is covered (`sunPart`); otherwise low and middle cloud stands apart round it, thin parts first |
| Clouds bland on the phone | The filter that keeps the header's white text readable darkened the whole upper sky to a third: every bright cloud there was grey | The darkening is a soft oval behind the text only |
| The night not good enough | The Milky Way was a 2048-pixel map of the whole sky: 270 pixels across the screen | NASA's 8k star map at the screen's sharpness round the galactic centre, glow and real stars |

**How the cloud sets are made now.** `skybake.py` bakes "the wide sky": an equal-angle window, 47 degrees to either
side of the view's middle and from 1 degree under the horizon to 40 over it (2068 x 902), so that a set can turn
past the view as the sky does (`sky.json`: `projection`, `azHalf`, `elMin`, `elMax`, `coverSeen`). `layer.frag`
draws each level's sets, each turned by its drift, into a window of the sky round the view (26 degrees to either
side: `SKYWIN_*` in `common.glsl`), and `scene.frag` reads the three windows. `sky_lo.py` composes the low sets,
`sky_mh.py` the middle and high ones (both have a `plan` step that draws the structure before anything is baked).
The twelve sets of 0.11 are no longer in the app (kept in `NoorTools/work/clouds_011`).

**Found on the way**

- The helpers' first new sets repeated the fault (a sprinkling of equal puffs). What worked was the instruction
  "structure first": a large-scale mask of where cloud is, then the filling.
- Far towers were flat ghosts in 0.11's first tests because `layer.frag` hazed every far cloud equally; haze now
  thins with height.
- `layer.frag` took a set's level from its slot (0 high) while it meant 0 low: high clouds were lit with the low
  clouds' sunlight. It now reads the set's own `layer`.
- `stars.vert` has its own copy of the cloud lookup: it has to be changed with `layerAt`.
- Under the full overcast the land was in full sunshine with hard shadows: a cloud's shadow was looked up where the
  cloud stands in the picture, and the cloud that shades the land near us is overhead, outside it. The land now
  also gets what the sky as a whole takes from the Sun (`SkyState.shade`, `overcast`; `uSunPart.z`, `.w`), and a
  closed sky lights it evenly in grey ([DESIGN.md](DESIGN.md), "The land under the clouds").
- `mi_sheet` (the dense middle sheet) took four tries. Cellular pads read as bubble wrap; a deck with too high a
  floor of thickness was a flat slab. What shipped is a fractal deck thinning gradually into a few breaks. Its
  weak points: little relief at midday (it is fine with a low Sun) and a straight end at the far right of its
  window, seen only at the very end of its drift while it is already fading. `hi_ripples` needed real grain
  (cells in ripple rows); the first was a smooth sheet.
- In the bench, drawing a sheet before the page says "ready" leaves every later picture black: reload.
- On this PC `python3` in Git Bash is the Microsoft Store stub and does nothing, silently: a patch script run that
  way changes no file. Use the Edit tool, or Blender's Python by its full path.

**How it was tested**

- Unit tests on the owner's PC, on the final content: phone 72, watch 29, alerts 16, no failures (the prayer engine
  was not touched; its 105 tests were not run again). `SkyDirectorTest` has two new locks: every set the director
  can choose has its day and exists (`theDirectorChoosesCloudSetsThatExist`, which also checks that under a full
  overcast the Sun is not seen and the land is not in sunshine), and from one minute to the next no set comes or
  goes by more than a small step, at any hour, in any weather (`theCloudsNeverChangeAtAStroke`).
- Both release builds on the PC, and GitHub's build of `main` (run 43719326009, 15 November 2026: every test,
  both APKs, the watch face's validation; `dev-latest` republished with 0.12.0).
- The design bench (the phone's shader files and pictures in a web page): every one of the seventeen sets at three
  places of its drift and four hours, whole skies of three levels with the header's text over them, the overcast
  over all six sceneries, the night. Sheets in `tools/sky3d/out` (not in the repository).
- An Android 14 emulator on GitHub (`real-sky.yml`, software GPU): the shaders build and the sky is shown for all
  six sceneries at sunrise, afternoon, sunset and night. In the long run (24 pictures) the emulator starved: the
  system's "Pixel Launcher isn't responding" dialog lies over every picture, and "Noor isn't responding" over
  three ("GPU hang", "timed out while trying to bind": the software GPU needs 85 seconds for a first picture).
  Of the five short runs (two sceneries, two moments) four are clean (dunes and the forest lake three times, the
  canyon and the green hills once) and one starved as well (arctic and sea: 93 seconds for a first picture, the
  launcher frozen, Noor "timed out while trying to bind" at start). So the arctic and the sea were seen on Android
  only under those dialogs.

- The Adhan test on emulators (`device-alerts.yml`, Android 14, 15 and 17): its first run on this release failed
  one scenario of five on each emulator, the one that was running at 13:35 UTC, 5:35 pm in Dubai: that day's
  Maghrib. The emulator's real Maghrib alarm fired with the test's own alarm, both records stayed at `STARTING`,
  and the test did not find `PLAYED`. Run again a few minutes later it passed on all three. So: a run of that
  workflow that overlaps a prayer time in Dubai can fail one scenario; run it again before looking for a fault.
  (What Noor does when a test Adhan and a real one start in the same second has not been looked at.)

**What was not tested**

- Nothing of 0.12 has been seen on a real phone. In particular: how the clouds look at the phone's own sharpness,
  the drift and the hand-over between sets in real time (a set takes hours to cross; the bench and the emulator
  show stills), the cost on a real GPU of drawing each level's window, and whether the emulator's "not responding"
  is only the emulator's (0.11 showed the same dialogs there and ran well on the owner's phone).
- The weather director's choices over many real days were tested as numbers, not looked at as pictures.

**Open after 0.12**

- `mi_sheet` at midday and the body of `lo_storm`'s anvil are the two weakest pictures; `mi_patches` could carry
  more shading at midday.
- The Sun still shows faintly through the storm's tower (the tower's picture is not fully opaque there).
- Leftovers in the code from 0.11's cloud lay-out (`slides`, `uCloudF*`, `SkyDirector.slide`) do nothing and can go.
- Reflections of the sky on rippled water still break into hard dashes (known since 0.11).

## 23. A living sky: depth at midday, water's mirror, clouds that pass and change (0.13.0, 15 November 2026, phone only)

After 0.12.0 was released (not yet seen on his phone) the owner asked where to go next. Three things were proposed
and he said: "Ok do all three." He also said "Delete them" of the merged work branches.

**Branches.** Twelve branches whose content is in `main` (checked: an ancestor of `main`, or the same tree as a
commit of `main`) were deleted on GitHub and on the PC. Three hold work that never reached `main` and were left:
`probe/emu37`, `adhan/097-repro` (abandoned emulator experiments) and `noor/0912` (a first widget countdown that
0.9.13 replaced). Deleting those needs his yes.

**1. Clouds at midday** ([DESIGN.md](DESIGN.md#the-real-sky-011), "The day's exposure")

The helper who baked the low clouds had noted that their pictures hold strong modelling and that the flatness was
the phone's. Measured in the bench (`bench.meter()`, new): on a solid cloud the Sun's light runs from 0.01 to 0.5
and the light from sky and ground adds 0.2: ten to one. The filmic curve then maps 0.25 to 1.25 onto 0.65 to 0.93
of the display: the whole midday picture sat on the curve's shoulder. Lowering the ambient light changed nothing
visible; exposing the day at 0.7 did (a deeper blue, shaded sides, white tops). The land gets some of it back.
A thunderhead now hides the Sun altogether (0.12 let 15 % of the disc through its tower).

**2. Water** ("Water, live")

The hard dashes were not aliasing of the waves but of the mirror: each pixel asked the land's picture once, so the
image of a treeline was there or not there. Now four heights across what the pixel's facets and its unseen ripples
spread, each read from a mipmap of the land's picture (`a` and `m` now have mipmaps; the land itself is read with
`textureLod(..., 0.0)`, as before). Tried and dropped on the way: eight sharp samples with a dither (a stipple
under the far shore); smearing by the sea's own roughness (`rough`: the mirror vanished, since that number was
tuned for the sea's look, not as a slope; `smear` is the separate, physical one). Waves are drawn from fifteen
pixels of length (they were a ruling of lines at three), ripples are judged by a pixel's long side.

**3. Life** ("They pass, and never jump", "They are alive", "Rain and snow")

- 0.12's drift was 41 degrees in 1.6 to 3.1 hours: a tenth of a pixel a second, and since the home screen tells the
  moment once a minute, a hop of ten pixels each minute. Now the sets pass side by side, the low ones at a thirtieth
  of a degree a second, and the renderer carries them on between frames (`CloudLeg.speed`, `shownAtMs`).
- The join between two sets took four tries: an upright ragged end (a wall with a white rim), a leaning seam that
  both sets knew by a number (still a line; the 94-degree window leaves too little room to lean it far), a noise
  threshold of smooth fields (a wavy coast), and what shipped: the noise tile's cells with large lobes, so the
  sheet ends in patches. An overcast is also never next to an open sky now.
- The first swirl and feed could not be seen at all: the tile's smooth fields vary by about 0.2 round 0.5, not
  by 0.5. Its channels are: R, G smooth (4 and 8 cells to the tile), B cells, A grain.
- `uSky.z` (lightning) had been set since 0.10 and read by no shader. `uFlash` is the stroke's direction and
  strength; it strikes only while `lo_storm` is in the view, where its tower stands in the set.
- `sq` is a function in common.glsl: a local variable of that name (waterSlope has one) hides it. `half` and `out`
  are reserved words in GLSL ES.

**What the emulator's timings showed, and what was done**

The first emulator run of 0.13 took 2.6 to 2.9 seconds for a picture after the first (0.12: 0.7 to 1.1): two or
three sets of each level are on show now (0.12: one, two while it changed), each with more to work out, and the
levels' windows were drawn again at every frame. So: a level's window is drawn again five times a second (the
clouds move under a pixel a second; at once when the light or the sets change, at every frame while the Sun is
dragged), and a set works nothing out where it has given way to its neighbour or where its sky is open. On the
emulator that gave 1.5 to 2.2 seconds (it is too slow for the five-a-second gate to bite: every frame of its is
later than that). On a phone the windows cost less than in 0.12, which drew them at every frame; the scene pass
itself is heavier where there is water (the mirror's four heights, the second slope for the pixel below). The
first picture, where the emulator compiles the scene shader, took 102 to 111 seconds (0.12: 67 to 93): the shader
is longer. On a phone that is the GPU driver's compile at the first start after the update (Noor keeps the
compiled programs); how long it takes there is not known.

**How it was tested**

- Unit tests on the owner's PC, on the final content: phone 72, watch 29, alerts 16, prayer engine 105, no
  failures. `SkyDirectorTest.theCloudsNeverChangeAtAStroke` was rewritten for the new order: from one minute to
  the next every set has only moved on at its own speed (never more than 2.5 degrees) and changed its amount by a
  small step; a set appears or disappears only where it has no part of the view; the Sun's window, the land's
  shade and the overcast change by small steps. `theDirectorChoosesCloudSetsThatExist` also checks that the
  shaders' `SKY_PITCH` is the director's `PITCH`.
- Both release builds on the PC, and GitHub's build of `main` (run 43741720244, 15 November 2026: every test,
  both APKs, the watch face's validation; `dev-latest` republished with 0.13.0). The Adhan test on emulators
  (`device-alerts.yml`, Android 14, 15 and 17) passed on the same commit.
- The design bench: the day's exposure on all six sceneries and through the morning and evening; the water of the
  forest lake, the arctic, the green hills and the sea at full size; one set passing into the next (broken into
  overcast, a middle sheet into patches); the same sky 0, 20 and 60 seconds apart; lightning by day, at dusk and
  at night; rain and snow. Stills only: the bench does not run the clock.
- An Android 14 emulator on GitHub (`real-sky.yml`), three runs of two sceneries at two moments: the shaders build
  and the sky is shown, no GL errors. The emulator starved in all three (the launcher's "isn't responding" dialog
  over every picture; once Noor's own, "timed out while trying to bind" at start, with Google's services timing
  out beside it), so no picture of 0.13 from Android can be judged.

**What was not tested**

- Nothing of 0.13 has been seen on a real phone, and nothing of it has been seen moving anywhere: the glide, the
  swirl and the lightning's flicker were checked as numbers and as stills. Whether the swirl reads as a cloud
  billowing or as a picture wobbling is the first thing to ask the owner.
- The cost on a real GPU, the first start's compile time, heat and battery.
- 0.12 itself had not been seen on the owner's phone when 0.13 was built on it.

**Open after 0.13**

- The Sun in near water is still a few hard white patches (it lies under the glass panel at midday).
- `mi_sheet` at midday and the body of `lo_storm`'s anvil (as after 0.12).
- Leftovers that do nothing: `uCloudF*`, the bench's `mix*` values, `uSky.z` (the flash is `uFlash` now).
- A thunderhead's tower is taken to stand 14 degrees right of its set's middle (the helper's note); the stroke is
  placed by that number, not by the picture.

## 24. Off the PC; far land, sunsets, variety, fog, lightning, seasons, a new interface, tester's options (0.14.0, 16 November 2026, phone only)

**What the owner asked.** On 15 November, an hour after 0.13.0, he installed it ("it's fine") and wrote a long
list into the PC's chat: sunrise and sunset "really beautiful... it reflects its colors on the clouds", judged by
photographs; the beach without its distant land ("open ocean"); the Pacific Northwest's mountain "a chain of
mountains"; "distant terrain just turns invisible during midday... in all sceneries"; the red rock "totally
redone... I hate the way it looks"; real seasons ("it's just snow and that's it. The trees don't change color");
tester's options ("where I can control the seasons and times and location... a demo... just for me, the
developer... removed once the app is released"); an opening animation ("as soon as I open the app"); the
interface ("totally revolutionize the user interface and keep the Apple aesthetic... a lot more intuitive");
bugs; "finish the job... This update should bring us very close to releasing the app." With that he lifted rule 5
of CLAUDE.md for the interface (not for the Qibla screen's design, which he likes). The PC chat worked on it for
seven minutes; then the PC was gone.

On 16 November, in a cloud session from his phone, he added: "the same cloud types just keep cycling over and
over again, which is extremely boring... It's just overcast, and then it's overcast again... I want the entire day
to have a lot of variety"; "distant thunderstorms with lightning... look at actual photos of cumulonimbus
clouds"; "early in the morning, you might have fog"; "If you think of more features. And I want you to finish
the job. Prove it how you see fit."

**The PC is gone ("I lost access to my PC for a long time").** Nothing on it can be reached: the seven minutes of
0.14 work (branch `sky/014`, never pushed), Blender and `NoorTools` (the downloaded scans, elevation models and
cloud volumes; the work files), `adb` for the watch, and one of the two copies of `noor-release.jks` section 7
names (the other is in the owner's backup archive: he was asked to make sure that archive is not on the PC too).
Everything in the repository was safe. Development moved to Claude Code cloud sessions the same day, and
everything the PC did was shown to work there (CLAUDE.md, "Working in a cloud session"):

- the Android build and its tests (JDK 17 and the SDK are fetched by the environment's set-up script; Maven
  Central answers "429 Too Many Requests" there, so Gradle asks Google's mirror of it first);
- the design bench, in the session's headless Chromium (a software GPU: about a minute for a sheet of a dozen
  tiles);
- Blender 4.5.16 with Cycles on the CPU: the dunes preview took 66 seconds where the PC took a quarter of a
  minute. Four cores and 15 GB are all there is: one bake at a time, a second Blender only for composing, and a
  render that takes more than the machine has is killed without a word (it happened to the red rock's helper
  once).

**What 0.14.0 is** (each is in [DESIGN.md](DESIGN.md#the-real-sky-011) under its own heading)

1. *Far land stays in sight.* Two causes of "invisible at midday", both in `landAt`: mist was spread through the
   whole depth of the air (the forest lake always has some, and it took the mountain), and the air in front of far
   land had the colour of the sky right behind that land (a hazy mountain painted in exactly what it stood
   against). The PC chat had suspected 0.13's rain veil: that too is gentler now (a far ridge fades to a shape),
   and it rains only out of a closed sky.
2. *The beach has an open horizon* (`tools/scenery/open_horizon.py`: nothing rendered again).
3. *Sunrise and sunset*, beside photographs from Wikimedia Commons: each cloud has the Sun of the place where it
   stands (far clouds stay gold after sunset, the high ones last), light through thin cloud near the Sun, light
   catching the folds of cloud undersides, slate shade, less salmon in the last light, ozone 1.5 instead of 2.2
   times the real amount, bright colours toned channel by channel, the hour of colour exposed for its sky.
4. *The day is a walk through different skies* (`SkyDirector.programme`), and every set also passes turned about.
5. *Morning fog* (`SkyState.fog`, `uFog`).
6. *Lightning that lights the cloud, and strokes to the ground* (`uBolt`).
7. *Seasons as pictures* (`Seasons`; `a_<season>.webp`, `k_<season>.webp`): the mechanism. Which sceneries have
   such pictures is said below.
8. *The interface* ([UI_014_NOTES.md](UI_014_NOTES.md), the helper's own account: one page system, grouped
   lists, sheets, the tab bar, Today's bells and floating "Today", Adhan in order of use, Settings in order of
   need, 17 bugs fixed) and *the opening* ("first light": the launch screen is the sky's waiting colour with
   Noor's mark; light gathers, travels to where the Sun is, and the dusk opens from there; 2.3 s at most, a touch
   skips it; it waits up to 0.7 s for the real sky's first picture, `RealSky.waitingForPicture`).
9. *Tester's options* (`com.noor.app.testing`: `Tester`, `TestingScreen`, `TesterBanner`; Settings → Testing):
   time of day, a day that runs by itself, season, kind of weather and "another sky of this kind", fog, scenery,
   the place chooser, and a demo of nine stops (every scenery through a whole day in a season and a weather of
   its own). Nothing is stored; only the home screen's sky is changed; a strip over the tab bar says so while it
   is on. **For testing only: to be removed before release** (the owner's word). Search for `Tester.` and
   `Routes.TESTING`.

**How it was made.** The lead did the sky (shaders, director, engine, bench) and the tester's options; helpers
in git worktrees of their own did the interface, the red rock and the cumulonimbus sets (one Blender bake at a
time). The lead looked at every picture a change made, beside photographs where the owner had asked for that.

**Decisions taken for the owner** (he wants technical and design decisions made; these are the ones he may want
to change)

- The opening replays when Noor comes back to the home screen after 15 minutes away, not only on a cold start
  (Android keeps the app alive, so "as soon as I open the app" would otherwise seem to work only sometimes).
  `Opening.RETURN_AFTER_MS`.
- The desert keeps the most open sky (one day in four shows three kinds of low cloud, not four or more).
- Tester's options are English only and are not stored.
- Released in parts rather than held for the slowest bake: what needs Blender arrives as each bake is done.

**How 0.14.0 was tested**

- Unit tests in the cloud session, on the merged work: prayer engine 105 (1 skipped by design), alerts 16, phone
  83, watch 29, no failures. New: `SkyDirectorTest.theDayIsAWalkThroughDifferentSkies`,
  `someMorningsHaveFogAndTheSunBurnsItOff`, `theSeasonsTurnSmoothlyAndEachHasItsTime`; the helper's
  `MainFlowTest` (the real `MainActivity` from onboarding to the location page) and `OpeningTest`. The director's
  older tests pass unchanged.
- The design bench (stills, a software GPU): far land at midday in mist and rain on four sceneries; the sunset
  and the sunrise at five heights of the Sun under several skies on all six sceneries, beside photographs; a
  rainy midday and the night on all six; fog at dawn, sunrise and mid-morning on all six; a set both ways round;
  lightning by day, at dusk and by night; the seasons' turning with made-up pictures.
- An Android 14 emulator on GitHub (`real-sky.yml`, run 43843501616, on the commit with the far land, the open
  ocean, the sunset and the seasons' shader): the shaders build with Android's own compiler and the sky is shown,
  no GL errors; the beach has its open horizon, the lake its golden evening. The emulator starved as before (the
  first picture of a start took 137 to 156 seconds, later ones 2.4 to 4 seconds; "isn't responding" dialogs over
  three of eight pictures).
- Software renders of every screen (`ScreenshotTest`, 62 pictures; the opening frame by frame): looked at by the
  helper and by the lead.
- GitHub's build of `main` (run 43852204944, 16 November 2026, commit `6463d4a`: every test, both APKs, the watch
  face's validation; `dev-latest` republished: the phone link was downloaded and reads 0.14.0, versionCode 28,
  the watch link 0.9.12). The Adhan test on emulators (`device-alerts.yml`, run 43852204892) passed on the same
  commit.

**What was not tested**

- **Nothing of 0.14 has been seen on a phone.** Not the sky, not the interface, not the opening, not the
  tester's options. The owner is the first to see it move.
- The cost of the heavier shaders on a real GPU: the clouds' pass now looks up the air's table and works two
  powers for every pixel of cloud, the scene has the fog and the stroke. On the emulator a picture after the
  first took 2.4 to 4 seconds (0.13: 1.5 to 2.2), but that machine was starved in both runs; nothing can be
  concluded from it for a phone.
- The opening's hand-over from Android's launch screen, haptics, TalkBack itself, dialogs and sheets (they open
  in windows of their own, which the renders cannot capture).
- Lightning, fog banks, the demo and a running day were seen as stills or not at all: they move.

**Open after 0.14.0**

- The red rock redone, cumulonimbus sets, the Pacific Northwest's chain of mountains and the seasons' pictures
  need Blender bakes on a CPU: they follow as each is done (see below, as they are added).
- Sunbeams through broken cloud at a low Sun were tried as a screen effect (what is bright along the line to the
  Sun) and dropped: it made ghosts of the clouds and washed the picture out. If wanted, it needs the clouds'
  shadow worked out in the air, not a smear of the picture.
- The evening's horizon is still one bright band under a deck of cloud (true to photographs, but flat).
- A scenery's water mirrors the base season's outline of the land while the seasons turn.
- Tester's options: English only; cannot yet be driven from the emulator workflow.
- Leftovers from 0.13 stay: `uCloudF*`, the bench's `mix*` values.

## 25. The app lives in the sky; a chain of mountains, Red Rock's seasons, a line of storms (0.15.0 and 0.15.1, 16 November 2026, phone only)

Made in a cloud session (Claude Opus, picking up from Claude Fable when its limit was reached).

**What the owner asked**

- Of 0.14's interface, the same day: "I see absolutely nothing changed with the user interface. I want you to
  totally revolutionize the user interface, make it better in every single way." (0.14 had reordered and regrouped
  every page but kept the look: flat ink or paper and solid cards, so on the phone it read as the same app.)
- Still open from 0.14's list: the Pacific Northwest's mountain as "a chain of mountains", real seasons ("the trees
  don't change color"), cumulonimbus and distant thunderstorms with lightning.

**What was done**

- **The interface** (DESIGN.md, "The interface of 0.15"): every page stands on the sky of the moment at the user's
  place (`Backdrop`, `SkyBackdrop`; `rememberSun` re-reads the Sun's height every minute); lists and cards are glass
  with a lit edge; the tab bar is a floating capsule of glass with a sliding lens (`NoorTabBar`; the pages run on
  under it, `TabBarSpace`); Adhan and Settings open on hero cards (`HeroCard`). The leftover solid tiles (a prayer's
  kinds of alert, the sceneries, the location fields) are glass. Contrast is locked for every text colour on the
  glass at every height of the Sun in both themes (`ThemeContrastTest`); the dark theme's greys were lightened and
  its danger red softened for it, the light theme's muted grey and gold text darkened.
- **Pacific Northwest: the Stuart Range** behind the forest lake (pnw.py, pnw_dem.py, pnw_chain.py; sources/pnw.md):
  USGS 3DEP one-arc-second elevation, the chain baked alone through the same camera and Suns and laid behind the
  0.13 pictures by distance (the whole scene is too heavy for this machine).
- **Red Rock's seasons**: autumn (the cottonwoods gold, unevenly) and winter (bare trees, red willow stems, snow on
  the ledges; `winterBaked`) and spring (small fresh leaves; it is the scenery's own season when the seasons are not
  followed, so its Settings picture is spring's).
- **A line of thunderstorms on the horizon** (`lo_cb_line`, sky_cb.py): three storms 72 to 90 km off at different
  stages, lightning from each (`SkyDirector.THUNDER`). Five near cumulus that had lined up on one bearing and read
  as a column of flat cloudlets were taken out of its pictures (tools/scenery/erase_clouds.py).
- **How it is organised** (the owner, the same evening: "see how professionals in user interface design... understand
  the human mind... organizing all the features and settings and options... intuitive... world class"; DESIGN.md,
  "How Noor is organised (0.15)"). A helper inventoried every page, row, option, entry point, duplicate and test
  that depends on the structure; the rules came from Apple's Human Interface Guidelines, Android's settings
  guidelines, Nielsen Norman Group (the ten heuristics, progressive disclosure, mobile navigation) and the laws of
  interaction design. Settings' first page: the place's card, then Prayer (Prayer times, Adhan & alerts, the
  notification bar), Appearance (Theme, Home screen, Language and region), the watch, About, Testing: nine rows,
  was twenty. Two pages below it, inside `SettingsScreen` (`SettingsPage`): Prayer times (a card with what the times
  follow and on what grounds; the method, Asr, adjustments; the high-latitude rule with a footer; accuracy) and
  Language and region (language, numerals, time format, the Hijri date). Search reaches every row of every page,
  says where it lives and works it from the results. Renamed: Calculation method, Theme, Home screen, Language and
  region. "Time since each prayer" moved to the Home screen page (Countdown). Alert history is on the Adhan tab only
  (search still finds it from Settings). The tab bar is on every page except the welcome and the location chooser
  (`Routes.withoutTabBar`), lighting the tab the page belongs to (the nearest tab in the back stack); the lit tab
  tapped again goes back to its first page (`LocalTabReselect` for Settings' own pages). The Watch tab's "Choose
  Watch or Both in the Adhan tab" is a link. The welcome has a third step that offers the Adhan (alerts start off),
  also on the way to choosing a city by hand. The home screen's drag is said once on the sky
  (`AppSettings.scrubHintDone`, set at the first drag).

- **Two more rounds of self-critique** (the owner: "look at your own work and criticize yourself... keep improving
  the software... until it's becoming so intuitive"; DESIGN.md, "Self-critique and review"). Two independent
  reviewers, each given only the renders and the facts (not the design's reasoning), ranked what still fell short;
  the second judged the first round's changes and said what remained, ending "not yet" at the level of the best
  apps for the alert controls and the watch and compass error states. Done in rounds 2 and 3: the countdown's
  caption; the bell as a switch; prayers always listed on the Adhan tab, "Off · would play …" while off, a button to
  turn the Adhan on for every prayer; blocking problems on the hero in the danger colour and first, each fix named
  for what it does; "Test the Adhan" on the tab; "♪" before recordings; three-word descriptions of each kind of
  alert and "Plays: …"; Asr's school named; the high-latitude rule only where it matters; the Hijri row's date; the
  calendar's great days; the Qibla's "Calibrate first" as its main line when the compass is unsure (it opens the
  guide); the Watch tab's no-watch state with "Get Galaxy Wearable" and the guide; recent history records carry
  their fix; the other day's view says the sky's countdown is "Today, now". Kept on purpose: "Shuruq", the
  countdown's sign, the Watch tab (the owner's choices).

**What was tested, and how**

- Unit and render tests on this machine: the phone app's 84 tests pass (`:app:testDebugUnitTest`),
  including `ThemeContrastTest.textOnTheGlassOverThePagesSkyIsAlwaysReadable` (new), `MainFlowTest` through the
  welcome's new step (it checks that "Not now" leaves the alerts off), and `ScreenshotTest` with three new pictures
  (80 and 82: the Prayer times page in English and Arabic; 81: Language and region) and its language check on every
  picture. `SkyDirectorTest` passes with the storm line.
- The renders were looked at: every page in the dark theme at the afternoon of the test's clock, Settings, Adhan and
  a prayer's alert by day, Arabic Settings, Adhan and the Prayer times page, the tab bar's lens in Arabic.
- The sceneries on the design bench (software GPU): the chain at sunrise, morning, noon, afternoon, sunset, dusk,
  night and in winter; Red Rock's autumn and winter at morning and evening; the storm line by day, at sunset, at dusk
  with lightning and at night, before and after the column was taken out.

**What was not tested**

- **Nothing of 0.15 has been seen on a phone**: not the sky behind the pages, the glass, the floating tab bar, the
  hero cards, the new Settings pages, the welcome's Adhan step (its notification permission request runs only on a
  device), the drag hint, the chain of mountains, Red Rock's seasons or the storm line.
- On a real GPU the pages' sky costs one gradient and a few dozen dots per page: nothing was measured.

**Open after 0.15.0**

- The container restarted at about 13:49 UTC and killed the running renders (Red Rock's spring, the towering
  cumulus `lo_congestus`); they were started again (`/opt/noortools/work/queue_after_restart.sh`); Red Rock's spring
  is in. The green hills' summer and autumn colour passes were killed once more for lack of memory while the release
  APKs were built (a Gradle daemon holds 3 GB; the meadow's colour pass needs about 6): never build while a meadow
  pass runs. They run again after its winter (`meadow_redo.sh`). `lo_congestus` and the green hills' seasons are
  added when they are done (0.15.1).
- The Testing page is still English only, and goes before release with `com.noor.app.testing`.
- 0.15.1 (versionCode 30) carries the two rounds of review, Red Rock's spring and `lo_congestus` (swelling cumulus,
  a few towering near; a column of flat cloudlets taken out of it as from `lo_cb_line`). GitHub's build of the work
  branch with the third round (run 43898096062, workflow_dispatch): every test, both APKs, the watch face's check.
  The green hills' seasons are still rendering and come in a later update.
- 0.15.2 (versionCode 31), "priority by use" (DESIGN.md): four tabs (the Watch page opens from Settings; `Routes.tabs`
  no longer holds WATCH, the route stays for the debug hook and the device scripts); the Adhan tab in two levels
  (`AdhanScreen`, page 0 and "Alert options"; `LocalTabReselect` returns to the first); the home screen's place on
  one line, no refresh button on the sky; Settings without the Adhan row. `ScreenshotTest` scrolls to and opens
  "Alert options" (83 English, 84 Arabic) and waits there for "Gradual volume" and "Alert on". The green hills'
  winter is rendered; its summer and autumn passes were started again by hand at 15:51 (a stale shell still held
  the name the redo script waited on).
- The green hills' winter is in 0.15.2 (`a_winter`, `k_winter`; new green grass, no flowers, the blue and valley oaks
  bare, the live oaks evergreen), seen on the design bench beside spring.

## 26. The Adhan section redone; no tones; fewer words (0.16.0, 16 November 2026, phone and watch)

**What the owner asked**: change the Adhan for all prayers at once; reminders before and after the Adhan, on chosen
days of the week; the Adhans of his Al-Fajr desk clock by the same names (its dial: Makkah, Madinah, Al-Quds, Egypt,
Riyadh, Random; he sent a photograph); no music, nothing that sounds like it (the generated tones); fewer words ("Some
things which long can be said in one or two words... use icons"). On the clock's recordings: told they belong to
Al-Fajr and cannot be shipped in a public repository without its permission, he said he will buy the licences before
release; they were not copied (none is published by Al-Fajr; copies online are unauthorised; the repository is
public, so committing them would publish them at once).

**What was done** (DESIGN.md, "The Adhan section (0.16)"):

- core: `PrayerAlert.days` (a bit a day, Monday lowest), `beforeMinutes`, `afterMinutes`, new fields at the end of a
  prayer's line (older readers stop before them; settings without them encode as before); the single 0.9.9 lead
  still serves every prayer until one is set on its own (`AlertSettings.beforeOf`, `withBefore`, `withBeforeForAll`);
  "pre" is still written for watches up to 0.9.12 when the leads agree. `AlertPlanner` looks 8 days ahead, skips days
  that are off (also when an alarm fires) and plans after-reminders (`afterReminders`, `onAfter`). AlertPlannerTest:
  three new tests.
- alerts: the after-reminder's alarm (request code base + 30 + prayer, `EXTRA_AFTER`) and notification ("Asr began 20
  minutes ago", English and Arabic); a missing default tone falls back to the default Adhan's opening.
- phone: the voices page (`VoiceScreen`, route `adhan/voice/{p}`, `ClockVoice`), "Adhan voice" on the tab,
  `AdhanViewModel.applyVoice` / `importVoice` / `setDays` / `setBefore` / `setAfter` and the "every prayer" setters;
  a prayer's page (kind, voice, when, sound, test; `DaysRow`); the phone's library without the 14 alt tones
  (`build_library.py`: `PHONE_ALTS = False`; the watch keeps them) and `SettingsRepository.phoneAlerts` (a prayer set
  to a tone becomes a notification). Shorter English and Arabic texts; the Qibla's method note removed.
- watch: no code of its own changed, but it is rebuilt with the shared core and alerts (days, reminders after, the
  fallback), so its version moves to 0.16.0 (versionCode 18).

**Tests**: core 108 (all pass, 3 new); `ScreenshotTest` with the voices page (85, 86) and sunrise with a reminder
after and five days (29); `DateNavigationTest` now checks the phone's library has no tones. The whole suite and both
APKs on GitHub (see below).

**Not tested**: on a phone or watch: the voices, the after-reminder's notification at a real prayer time, the days.
Al-Quds, Egypt and Riyadh have no licensed recording in Noor.


## 27. Alarms before and after the prayers; the Adhan tab simplified (0.17.0, 16 November 2026, phone; watch rebuilt)

**What the owner asked**: "what I meant with the alarms is not what you implemented... like your phone clock app...
set this alarm, let's say 10 minutes after Dhuhr Adhan, or set another alarm 25 minutes before Al-Fajr Adhan"; the
Adhan tab "not intuitive"; "completely redo and revolutionize the Adhan section... criticize yourself a lot, put
yourself in the place of the user. Is it too overwhelming?"

**What was done** (DESIGN.md, "Alarms and the Adhan tab (0.17)"):

- core: `PrayerAlarm` (id 1..20, prayer, minutes 1..180, before/after, days, label, `AlarmSound` PHONE_ALARM /
  NOTIFICATION / CUSTOM, vibrate, snooze 0/5/10/15) in `AlertSettings.alarms`, written as a new field `alarms=`
  (entries joined by `|`, the label percent-encoded; old readers ignore it); `withAlarm`, `withoutAlarm`,
  `nextAlarmId`; `remindersAsAlarms` turns the 0.9.9/0.16 reminders into notification alarms (applied on read by
  `SettingsRepository.phoneAlerts`). `AlarmPlanner.next` / `plan` / `onFire` (on time within 90 s early to 10 min
  late; days are the prayer's day; "after Isha" can ring after midnight). `PrayerAlarmTest`: 7 tests.
- alerts: one alarm-clock alarm per alarm (request code 7100 + 100 + id), snooze (+ 200 + id), a tried alarm (+ 60;
  the alarm travels in the intent). `AlarmDispatcher.ring` uses the Adhan's quiet rules, then `AdhanService` with
  `SoundSource.System` (the phone's alarm sound; else its ringtone), looping, 10 minutes at most, repeating vibration,
  Snooze in the notification and on the ringing screen; if nothing can play, a notification (never the backup
  Adhan). `AlertHost.ringsAlarms`: the phone only.
- phone: `AlarmScreen` (route `adhan/alarm/{id}`, 0 = new) and `AlarmRow`; the Adhan tab regrouped (prayers with
  today's times and a line only where they differ; Alarms; test; More settings); the reminder rows and setters
  removed; `AdhanAlertActivity` with Snooze and an alarm clock sign for alarms.
- watch: rebuilt with the shared code; nothing changes on it (its alerts follow the phone's device choice; the alarms
  do not ring there).

**Tests**: core (all pass, 7 new); `ScreenshotTest` 87–93 (the alarms list in English and Arabic, a new alarm, an
alarm being edited in Arabic and by day, ringing in English and Arabic). The whole suite and both APKs on GitHub.

**Not tested**: on a phone: an alarm ringing at a real prayer-relative time, Snooze, the phone's alarm sound,
notification-only alarms.
