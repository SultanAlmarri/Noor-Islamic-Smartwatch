# Noor Islamic Smartwatch

Companion Android app and Wear OS app for the Noor prayer watch face on Samsung Galaxy Watch, with an offline
prayer-time engine tuned to Dubai's official IACAD timetable.

**Install:** [latest development build](https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch/releases/tag/dev-latest)
· [step-by-step guide](docs/INSTALL.md)

## Status (7 November 2026, version 0.9.6)

| Part | State |
|---|---|
| Prayer-time engine (`core/prayer`) | Built and tested: NREL SPA port, 12 calculation profiles, high-latitude rules, Hijri date. Dubai profile matches 527 of 588 official IACAD times exactly, the rest by 1 minute |
| Phone app (`app`) | Builds and passes its tests on every push: dashboard, settings, city picker, onboarding, location, watch sync. 0.9.6: the home sky is computed by a physical model and drawn by GPU shaders on Android 13+ (sky, clouds, land), checked on Android 14 and 15 emulators ([DESIGN.md](docs/DESIGN.md#the-home-screen)) |
| Watch app (`wear`) | Builds and passes its tests: works on its own or with the phone; home with live countdown, Qibla compass, tasbih counter (tap or bezel), Hijri calendar with upcoming days, on-watch set-up (own location or city search), settings and sync; two tiles and four complications; Ongoing Activity during the Adhan ([WATCH.md](docs/WATCH.md)) |
| Watch face via the watch app | v1.3.2 face packaged for Watch Face Push (Wear OS 6); passes all 10 checks of Google's official validator at every build |
| Watch face v1.3.2 (`com.noorface.prayer`) | Preserved unchanged in `legacy/`; still installed separately |
| Blind accuracy test | Predictions for Dec 2026 – Jun 2027 frozen; scored monthly against IACAD (`accuracy/blind/`) |
| Adhan and prayer alerts (`alerts`) | 0.9: the Adhan now plays like an alarm (also on vibrate), never fails silently (default Adhan, then a backup Adhan played by Android itself), and every alert is recorded in Alert history; tested on Android 14 and 15 emulators with Noor closed, screen off and Doze. Built and tested on the computer: per-prayer Adhan/short/sound/imported/notification/silent, phone/watch/both, gradual volume, Do Not Disturb choice, exact alarms, 18 licensed Adhan recordings from 9 countries and 14 original sounds, a library with search, regions, favourites and previews, and "Random from favourites" (0.9.5) ([ALERTS.md](docs/ALERTS.md), [SOUNDS.md](docs/SOUNDS.md)) |
| Home screen (0.9.2) | All six times visible without scrolling on small, common and large phones; the sky takes the rest. The sun path stays in the sky and sets behind the land; Fajr and Isha sit on the skyline; stars thin out towards the horizon; moonlit night clouds; Earth's shadow and the Belt of Venus at dawn and dusk; a 0.9 s opening time-lapse. Since 0.8.0: a living natural scene under the real sky: drifting clouds lit by the time of day, dunes or hills by country, mist at dawn, stars, Milky Way and moon at night, birds at dawn and dusk; drag the sun along its path through the day. Every other screen unchanged ([DESIGN.md](docs/DESIGN.md#the-home-screen)) |
| Design (0.6.0) | Living-sky Today screen (real sky colour and sun path), Follow-the-sun appearance, redesigned Qibla, Adhan, Watch, Settings and onboarding, watch app in the same family; WCAG AA contrast checked by tests ([DESIGN.md](docs/DESIGN.md)) |
| Persistent notification (0.9.3) | Optional (Settings → Notification bar): next prayer, its time, a live countdown with seconds run by Android itself, the Hijri date and the place; updated only at each prayer and at midnight |
| Calendar, Settings, Qibla (0.9.4) | Browse dates by Gregorian or Hijri month; Settings in clear groups with search, and the method's basis for today; Qibla accuracy box of fixed size whose level changes only once the sensor holds it, with a live mini meter in the calibration tips |
| Watch-face editor | Next stage |
| Device testing | **Not done yet.** Everything above is tested on the computer (unit tests, software-rendered screens), not on a phone or watch |

Working on the project with Claude Code: start with [`CLAUDE.md`](CLAUDE.md) and [`docs/HANDOVER.md`](docs/HANDOVER.md).

## Layout

| Path | What it is |
|---|---|
| `core/prayer` | Pure Kotlin prayer engine, the phone↔watch settings format (`WatchConfig`), the watch's own set-up (`LocalSetup`), tasbih and Islamic calendar logic, shared by both apps |
| `app` | Phone app: Jetpack Compose, Material 3, DataStore, WorkManager, fused location, Wear OS Data Layer |
| `alerts` | Prayer alerts shared by both apps: AlarmManager scheduling, Adhan player service, notifications, sound library |
| `shared` | Android pieces for both apps: compass sensor, offline gazetteer and official timetables (code and data) |
| `wear` | Watch app: Compose for Wear OS (Material 3) with navigation, tiles (ProtoLayout Material 3), complication data sources, Ongoing Activity, Watch Face Push |
| `legacy/` | Frozen copy of the working watch face v1.3.2 and its APK |
| `tools/watchface/` | Packages the watch face for Watch Face Push and validates it with Google's validator |
| `tools/sounds/` | Generates the alert sounds, fetches and verifies the licensed Adhan recordings, builds the sound library |
| `accuracy/` | Official IACAD datasets, research tools, blind test |
| `signing/` | Public development keys (see its README); no release key is stored here |
| `docs/` | `HANDOVER.md` (history, decisions, current state, open work), `ARCHITECTURE.md` (how each part works), `WATCH.md`, `DESIGN.md`, `ALERTS.md`, `SOUNDS.md`, `INSTALL.md`, `PRAYER_ACCURACY.md` |

## How phone and watch work together

- The phone resolves everything (place, time zone, authority, Asr school, high-latitude rule, personal minute offsets,
  Hijri adjustment, language, numerals, 12/24 h) into one small `WatchConfig` and publishes it on the Wear OS Data Layer
  whenever it changes. The system delivers it when the watch connects, even with both apps closed.
- The watch stores the last configuration and calculates with the same engine, so its times equal the phone's to the
  minute and it keeps working with the phone off, out of range or without internet.
- The complication sends two days of entries at once; the watch face switches to the next prayer on time and draws the
  countdown itself, so the watch app does not need to wake up every minute.
- Without the phone app, the watch can be set up on the wrist (its own location or a city) and builds the same kind of
  configuration itself, with the same authority rules and bundled official timetables. "Sync with phone" on the watch
  asks the phone to send its settings again. Details: [WATCH.md](docs/WATCH.md).

## Build

Requires JDK 17 and the Android SDK (compileSdk 35 for the phone, 36 for the watch), and Python 3 for the watch-face step.

```
./gradlew :core:prayer:test :alerts:testDebugUnitTest :app:testDebugUnitTest :wear:testDebugUnitTest
./gradlew :app:assembleRelease :wear:assembleRelease
```

GitHub Actions runs the same on every push to `main` and publishes `Noor-phone.apk` and `Noor-watch.apk` as the
`dev-latest` pre-release, with test reports and rendered screenshots.
