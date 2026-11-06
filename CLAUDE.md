# Noor Islamic Smartwatch — instructions for Claude Code

Read this file, then `docs/HANDOVER.md` (history, decisions, current state, open work) before changing anything.
The other documents in `docs/` describe each part in detail and are kept up to date with the code.

## What this is

An Islamic prayer-time product for Samsung Galaxy Watch (the owner wears a Galaxy Watch 5 Pro) and Android phones:

- **Phone app** (`app`, package `com.noor.smartwatch`): Today (home), Qibla, Adhan, Watch and Settings screens.
- **Wear OS app** (`wear`, same package `com.noor.smartwatch`): works alone or synced with the phone; tiles,
  complications, alerts, and installs the Noor watch face through Watch Face Push.
- **Shared modules**: `core/prayer` (pure Kotlin prayer engine and everything both apps must compute identically),
  `alerts` (Adhan and alerts), `shared` (compass, offline gazetteer, official timetables).
- **The original watch face v1.3.2** (`com.noorface.prayer`), frozen in `legacy/`. It still runs on the owner's watch.

Current version: **0.9.5** for both apps (versionCode 14). Everything is on `main`; there are no other branches.

## The owner

- Not a developer. Explain results and instructions in plain, simple language, with exact steps.
- Wants you to make technical and design decisions yourself. Ask only when a decision is genuinely theirs to make.
- At the end of every report, always include both download links:
  - Phone: https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch/releases/download/dev-latest/Noor-phone.apk
  - Watch: https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch/releases/download/dev-latest/Noor-watch.apk
- Demands honesty: say exactly what was tested and how. Software renders and unit tests are not device tests. Never
  claim that something works on a phone or watch unless it has been run there.
- Time zone: Asia/Dubai. Languages in the app: English and Arabic (right-to-left), Western and Arabic-Indic digits.

## Non-negotiable rules

1. **Prayer-time accuracy comes first**, above every visual feature. Never hard-code prayer times, never add
   date-specific or arbitrary minute offsets to force a match, never claim accuracy without evidence. Every figure in
   `docs/PRAYER_ACCURACY.md` is locked by tests. Do not loosen those tests.
2. **Never overwrite or break the original watch face v1.3.2.** Do not edit `legacy/`. New watch-face work goes in new
   modules.
3. **Never commit a private key.** `noor-release.jks` (the v1.3.2 face's signing key) must never enter this public
   repository. `signing/noor-dev.keystore` and `signing/noor-watchface-dev.keystore` are public development keys on
   purpose: phone and watch must share one signature, and every build must install over the last. Do not replace
   them, or users' installed builds stop updating and phone↔watch pairing breaks.
4. **Do not remove or weaken tests to make a build pass.** Fix the cause.
5. **Do not break working features.** Change only what the task needs. Phone screens outside the approved plan
   (HANDOVER.md section 10: home, Adhan and alerts, Settings, the date picker, small Qibla fixes, the persistent
   notification) stay exactly as they are (see HANDOVER.md, "Design history"). The Qibla screen is liked as it is:
   fix, do not redesign.
6. **Do not fabricate** test results, data, or certainty. Do not invent missing data.
7. **No scraping** of websites or apps without authorisation. **No unlicensed copyrighted recordings.** Adhan audio
   comes only from verified licensed sources (`docs/SOUNDS.md`).
8. **Never bypass Android or Wear OS permission or system restrictions.**
9. **Display rule:** Israel (`IL`) is displayed as Palestine (`PS`), with the Palestinian flag and name
   (`Flags.displayCountry`). Keep it.
10. **Home-screen scenery is natural only:** sky, light, clouds, land, mist, stars, moon and birds. No buildings,
    roads, vehicles, streetlights or any other man-made objects, and no people.

## Repository map

| Path | What |
|---|---|
| `core/prayer` | Prayer engine (NREL SPA port), 12 calculation profiles, Hijri date, schedule, `WatchConfig`/`WatchReport` (phone↔watch formats), `LocalSetup` (watch's own setup), Qibla geodesy, tasbih, Islamic calendar, flags |
| `app` | Phone app (Jetpack Compose, Material 3). Home = `ui/today/` (`TodayScreen.kt`, `LivingSky.kt`, `HomeSky.kt`) |
| `wear` | Watch app (Compose for Wear OS M3, tiles, complications, Watch Face Push) |
| `alerts`, `shared` | Android libraries used by both apps |
| `legacy/` | Frozen watch face v1.3.2: source and APK. **Read-only** |
| `tools/` | Watch-face packaging and validation, sound pipeline, gazetteer and SPA table generators, verification |
| `accuracy/` | Official IACAD datasets, research tools, the blind test (scored monthly by a GitHub workflow) |
| `docs/` | `HANDOVER.md`, `ARCHITECTURE.md`, `DESIGN.md`, `WATCH.md`, `ALERTS.md`, `SOUNDS.md`, `AUTHORITIES.md`, `PRAYER_ACCURACY.md`, `INSTALL.md` |
| `.github/workflows/` | `android.yml` (tests, builds, publishes the `dev-latest` release), `device-alerts.yml` (the real alarm → Adhan path on Android 14/15 emulators), `iacad-monthly.yml` (blind test), `fetch-sounds.yml` (verified Adhan downloads), `discover-sounds.yml` (Commons research list) |

## Build and test

Requires **JDK 17** (the modules use `jvmToolchain(17)`; Gradle finds a JDK 17 installed in a standard location),
the **Android SDK** (platforms 35 and 36, build-tools 35.0.0 and 36.0.0, platform-tools), and **Python 3** (the
watch-face packaging step). Point Gradle at the SDK with `ANDROID_HOME` or with a `local.properties` file
(`sdk.dir=...`). `local.properties` is git-ignored.

```
./gradlew :core:prayer:test
./gradlew :alerts:testDebugUnitTest :app:testDebugUnitTest :wear:testDebugUnitTest -Probolectric.repo=https://maven-central.storage-download.googleapis.com/maven2/
./gradlew :app:assembleRelease
./gradlew :wear:assembleRelease
```

- The Robolectric mirror flag is required in some networks (Maven Central refused CI runners). Keep using it.
- On machines with little memory, add `-Dorg.gradle.jvmargs=-Xmx3g --no-parallel`, build the two release APKs one
  at a time, and run `./gradlew --stop` if the daemon dies.
- `app/build.gradle.kts` sets `forkEvery = 1` for unit tests because DataStore allows one instance per process.
  Keep it. For the same reason, each Robolectric render test class uses a single `@Test` method.
- Rendered screens: `ScreenshotTest` writes 57 PNGs (plus 55 from `StatusNotificationTest`), and fails if any screen shows text in the other language to `app/build/screenshots` (CI publishes them as
  `screenshots.zip`). The home-screen design renders (`app/src/test/.../design/Today*DesignTest`) write to
  `app/build/design`. These are software renders, useful for reviewing layout, not proof of device behaviour.
- Release builds are signed with the development key, so they install over earlier builds.
- If you cannot build locally, for example because the Android SDK cannot be downloaded, GitHub Actions builds and
  tests every push. A push to `main` also republishes the owner's download links.

## Delivery workflow (how every change has reached the owner so far)

1. Run the relevant tests and both release builds.
2. Commit to `main` with a clear message, then `git push`. Pull first, because the monthly blind-test workflow
   commits to `main` on its own.
3. GitHub Actions (`android.yml`) runs every test, builds both APKs, validates the watch face with Google's
   validator, and republishes the `dev-latest` pre-release (`Noor-phone.apk`, `Noor-watch.apk`, test reports,
   screenshots). Check that the run succeeded before you tell the owner the build is ready.
4. Report in plain language: what changed, what was tested and how, what was not tested, and both download links.

Version numbers: `versionCode`/`versionName` live in `app/build.gradle.kts` and `wear/build.gradle.kts`. Both apps
have moved together so far. Update `README.md` (status table), `docs/ARCHITECTURE.md` (header and limitations) and
the relevant doc when a version changes.
