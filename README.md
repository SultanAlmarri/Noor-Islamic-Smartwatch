# Noor Islamic Smartwatch

Companion Android app for the Noor prayer watch face on Samsung Galaxy Watch (Wear OS), with an offline prayer-time
engine tuned to Dubai's official IACAD timetable.

## Status (4 November 2026)

| Part | State |
|---|---|
| Prayer-time engine (`core/prayer`) | Built and verified: NREL SPA port, 12 calculation profiles, high-latitude rules, Hijri date. Tests pass locally |
| Phone app (`app`) | First version written: dashboard, settings, city picker, onboarding, location services. **Not yet compiled**; the first CI run is its first build |
| Watch sync and watch-face editor | Not started (next stages) |
| Watch face v1.3.2 | Preserved unchanged in `legacy/` |
| Device testing | Not done yet |

## Layout

| Path | What it is |
|---|---|
| `core/prayer` | Pure Kotlin prayer engine, no Android dependencies, shared later by phone and watch |
| `app` | Phone app: Jetpack Compose, Material 3, DataStore, WorkManager, fused location |
| `legacy/` | Frozen copy of the working watch face v1.3.2 and its APK |
| `accuracy/` | Official IACAD datasets, research tools and validation reports |
| `docs/PRAYER_ACCURACY.md` | What is verified, what is deduced, every remaining difference |
| `tools/` | Generators and the stand-alone engine verifier |

## Architecture

- **Offline first.** All times are calculated on the device from the bundled engine. Location, settings and the last
  known position are stored with DataStore, so the app works with no network.
- **Location.** One balanced-power fix when the app opens (if the last one is older than 30 minutes), on time-zone
  changes, and, if the user allows "all the time", a WorkManager job about every 6 hours. No continuous tracking.
  Manual city selection works without any permission.
- **Authority.** Chosen from the location: Dubai emirate → IACAD; rest of the UAE → Awqaf; Saudi Arabia → Umm al-Qura;
  and so on. The user can override it.
- **Watch (next).** The phone will push prayer settings and design choices over the Wear OS Data Layer; the watch keeps
  calculating with its own copy of the engine, so it works without the phone or internet.

## Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```
./gradlew :core:prayer:test        # engine accuracy tests
./gradlew :app:assembleDebug       # phone APK
```

The GitHub Actions workflow runs both on every push and publishes the APKs as the `dev-latest` pre-release.
