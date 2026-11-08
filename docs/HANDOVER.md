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
| 4 · Persistent notification | 0.9.3 | Ongoing notification: next prayer, time, live countdown with seconds (system chronometer, no wake-ups), Hijri date, place; updated at each prayer, midnight and on changes | Done (tests and render); needs a phone to see it in the real shade |
| 5 · Calendar, Settings, Qibla | 0.9.4 | Gregorian/Hijri switch in the date picker; Settings in clearer groups with search and the method choice; Qibla accuracy box of fixed height with hysteresis, mini meter in the calibration tips (three levels kept: the sensor is too coarse for four) | Done (tests and renders); the compass behaviour itself needs a phone |
| 6 · Adhan library | 0.9.5 | Much larger, diverse collection (countries, regions, mosques, reciters) from verified licensed sources; browse, search, preview, compare, favourites; Random Adhan; more alert sounds | Done: 18 Adhans from 9 countries (was 7 from 6), 14 alert sounds (was 7), library with search, regions, favourites and previews, Random from favourites. Every recording found on Commons was reviewed; growing further needs the owner (see below) |

Owner decisions: scenery stays artistic and offline (no weather service); build the library from the best available
licensed sources and explore ways to obtain more (famous reciters need permission from rights holders); Claude Code
runs every test it can and guides the owner, in simple steps, through tests that need his phone.

Open owner action: GitHub's default branch is still the old `phase-1-foundation`, and repository settings cannot be
changed from a Claude Code cloud session. Until it is set to `main` (github.com → the repository → Settings → General →
Default branch), the monthly blind test does not run on its schedule.

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
