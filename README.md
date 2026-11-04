# Noor Islamic Smartwatch

Companion Android app and Wear OS app for the Noor prayer watch face on Samsung Galaxy Watch, with an offline
prayer-time engine tuned to Dubai's official IACAD timetable.

**Install:** [latest development build](https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch/releases/tag/dev-latest)
· [step-by-step guide](docs/INSTALL.md)

## Status (4 November 2026)

| Part | State |
|---|---|
| Prayer-time engine (`core/prayer`) | Built and tested: NREL SPA port, 12 calculation profiles, high-latitude rules, Hijri date. Dubai profile matches 527 of 588 official IACAD times exactly, the rest by 1 minute |
| Phone app (`app`) | Builds and passes its tests on every push: dashboard, settings, city picker, onboarding, location, watch sync |
| Watch app (`wear`) | Builds and passes its tests: receives settings from the phone, calculates offline, app screen, "Prayer times" tile, "Next prayer" complication (short, long, ranged) |
| Watch face via the watch app | v1.3.2 face packaged for Watch Face Push (Wear OS 6); passes all 10 checks of Google's official validator at every build |
| Watch face v1.3.2 (`com.noorface.prayer`) | Preserved unchanged in `legacy/`; still installed separately |
| Blind accuracy test | Predictions for Dec 2026 – Jun 2027 frozen; scored monthly against IACAD (`accuracy/blind/`) |
| Watch-face editor | Next stage |
| Device testing | **Not done yet.** Everything above is tested on the computer (unit tests, software-rendered screens), not on a phone or watch |

## Layout

| Path | What it is |
|---|---|
| `core/prayer` | Pure Kotlin prayer engine and the phone↔watch settings format (`WatchConfig`), shared by both apps |
| `app` | Phone app: Jetpack Compose, Material 3, DataStore, WorkManager, fused location, Wear OS Data Layer |
| `wear` | Watch app: Compose for Wear OS (Material 3), tile (ProtoLayout Material 3), complication data source, Watch Face Push |
| `legacy/` | Frozen copy of the working watch face v1.3.2 and its APK |
| `tools/watchface/` | Packages the watch face for Watch Face Push and validates it with Google's validator |
| `accuracy/` | Official IACAD datasets, research tools, blind test |
| `signing/` | Public development keys (see its README); no release key is stored here |
| `docs/` | `INSTALL.md`, `PRAYER_ACCURACY.md` |

## How phone and watch work together

- The phone resolves everything (place, time zone, authority, Asr school, high-latitude rule, personal minute offsets,
  Hijri adjustment, language, numerals, 12/24 h) into one small `WatchConfig` and publishes it on the Wear OS Data Layer
  whenever it changes. The system delivers it when the watch connects, even with both apps closed.
- The watch stores the last configuration and calculates with the same engine, so its times equal the phone's to the
  minute and it keeps working with the phone off, out of range or without internet.
- The complication sends two days of entries at once; the watch face switches to the next prayer on time and draws the
  countdown itself, so the watch app does not need to wake up every minute.

## Build

Requires JDK 17 and the Android SDK (compileSdk 35 for the phone, 36 for the watch), and Python 3 for the watch-face step.

```
./gradlew :core:prayer:test :app:testDebugUnitTest :wear:testDebugUnitTest
./gradlew :app:assembleRelease :wear:assembleRelease
```

GitHub Actions runs the same on every push to `main` and publishes `Noor-phone.apk` and `Noor-watch.apk` as the
`dev-latest` pre-release, with test reports and rendered screenshots.
