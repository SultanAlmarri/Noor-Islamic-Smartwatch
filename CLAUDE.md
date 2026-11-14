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

Current version: phone **0.11.0** (versionCode 25), watch **0.9.12** (versionCode 17); 0.9.7, 0.9.9–0.9.11,
0.9.13, 0.9.14, 0.10.0 and 0.11.0 changed only the phone. Everything is on `main`. The work branches `sky/096`, `sky/097`,
`phone/097`, `adhan/097`, `noor/098`, `noor/099`, `sky/real` (0.10) and `sky/photoreal` (0.11) are fully merged;
`probe/emu37` and `adhan/097-repro` hold only abandoned Android 17 emulator experiments (that image does not run
reliably on GitHub's machines). All of them can be deleted (a cloud session is not allowed to delete branches).

Development also happens on the owner's Windows PC (Claude Code desktop, checkout in `ProjectMinhad\Noor`). There:
JDK 17 in `%USERPROFILE%\.jdks\temurin-17` (listed in `~/.gradle/gradle.properties` as
`org.gradle.java.installations.paths`), the SDK in `%LOCALAPPDATA%\Android\Sdk` (`local.properties`), and Python
3.12 (embeddable, standard library only) in `%LOCALAPPDATA%\Programs\Python\py312-embed`, which must come first on
`PATH` so `python3` is not the Microsoft Store stub. Found on 13 November 2026, needed for Gradle to run there:
- Set `JAVA_HOME` to the Temurin 17 above for the Gradle run (the PC's default `JAVA_HOME` is Corretto 21), and set
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\Public`; without both, Gradle stops at once with
  "Unable to establish loopback connection".
- `local.properties` must use forward slashes (`sdk.dir=C:/Users/.../Android/Sdk`): single backslashes are dropped
  when the file is read ("The filename, directory name, or volume label syntax is incorrect").
- Add `-Pkotlin.compiler.execution.strategy=in-process`: the Kotlin compile daemon cannot be reached there either
  (it falls back by itself, but with long stack traces).
- In the Claude Code desktop app, Gradle needs to run outside the command sandbox, and `gradlew.bat` is called with its
  full path and `-p <project>` from `cmd /c`.

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
| `app` | Phone app (Jetpack Compose, Material 3). Home = `ui/today/` (`TodayScreen.kt`, `LivingSky.kt`, `HomeSky.kt`); its scene since 0.10 = `sky/` (the real sky's renderer) with shaders and baked pictures in `app/src/main/assets/sky2` |
| `wear` | Watch app (Compose for Wear OS M3, tiles, complications, Watch Face Push) |
| `alerts`, `shared` | Android libraries used by both apps |
| `legacy/` | Frozen watch face v1.3.2: source and APK. **Read-only** |
| `tools/sky3d` | The real sky's design bench: the phone's own shaders and pictures in a web page (WebGL 2) |
| `tools/scenery` | Makes the real sky's pictures (Blender Cycles on the owner's PC): each scenery's land, each kind of cloud, the Milky Way |
| `tools/` | Watch-face packaging and validation, sound pipeline, gazetteer, world places and time-zone map builders (`tools/geo`), SPA table generators, device tests, verification |
| `accuracy/` | Official IACAD datasets, research tools, the blind test (scored monthly by a GitHub workflow) |
| `docs/` | `HANDOVER.md`, `ARCHITECTURE.md`, `DESIGN.md`, `WATCH.md`, `ALERTS.md`, `SOUNDS.md`, `AUTHORITIES.md`, `PRAYER_ACCURACY.md`, `INSTALL.md` |
| `.github/workflows/` | `android.yml` (tests, builds, publishes the `dev-latest` release), `device-alerts.yml` (the real alarm → Adhan path on Android 14/15 emulators), `real-sky.yml` (the real sky on an Android 14 emulator), `iacad-monthly.yml` (blind test), `fetch-sounds.yml` (verified Adhan downloads), `discover-sounds.yml` (Commons research list) |

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

### The home sky's GPU shaders (0.9.6)

- Source: `app/src/main/res/raw/sky_common.agsl`, `sky_layer.agsl`, `land_layer.agsl` (AGSL, Android 13+); the
  sky model is `Atmosphere.kt`. Below Android 13, or if a shader cannot be built, the Canvas renderer is used.
- AGSL pitfalls met so far: loop bounds must be literals (not even a `const int`); every loop is unrolled and every
  call inlined when Android sizes a program, and it refuses programs above ~100 000 operations ("program is too
  large": the first 0.9.6 sky did that on the emulators). `SkyShaderSizeTest` estimates the size and must stay under
  65 000; `SkyShaderCompileTest` compiles both with the SkSL in Robolectric's native graphics (an older dialect:
  `sample(child, p)` for `child.eval(p)`; it does not enforce the size limit).
- Iterate on looks with a WebGL bench that compiles the same files (headless Chromium, a small GLSL prelude), then
  confirm on Android with `.github/workflows/home-sky.yml` (Android 14 and 15 emulators; pictures also go to the
  `home-sky-preview` pre-release, since workflow artifacts cannot be downloaded from a Claude Code cloud session).
  The emulators draw with a software GPU: faithful pictures, but slow, so stills are taken with animations off.

### The real sky (0.11): the home screen's renderer and its pictures

Read `docs/DESIGN.md` ("The real sky (0.11)"), `docs/HANDOVER.md` sections 20 and 21 and `tools/scenery/README.md`
first. The owner judges this against photographs of real places, and rejected 0.10 (drawn from formulas on the
phone) as bland and fake: never call a render beautiful on your own judgment; put it next to real photographs.

- **What is not moving is rendered once, offline.** The land of each scenery and each kind of cloud is path traced
  with Blender Cycles through Noor's fixed camera under a set of unit Suns (`tools/scenery`: `bake.py`, one script
  per scenery, `skybake.py` and `cloudgen*.py` for clouds, `nightsky.py`). The outputs are small pictures in
  `app/src/main/assets/sky2/land/<id>`, `.../clouds/<id>` and `.../night`. Blender 4.5 LTS lives outside the
  repository in `%USERPROFILE%\ProjectMinhad\NoorTools` on the owner's PC (with the work files and the downloaded
  elevation data); it is not available in CI or in a cloud session, where the pictures can only be used as they are.
- **The phone only puts pictures together**: shaders in `app/src/main/assets/sky2/*` (GLSL ES 3.00), renderer in
  `app/src/main/java/com/noor/app/sky/` (`RealSkyEngine.kt` the passes and the loading of pictures, `RealSky.kt`
  sceneries, tiers and fallbacks, `RealSkyFrame.kt` the moment, the light weights and the weather director). The
  0.9 sky (`LivingSky.kt` and the AGSL shaders above) is the fallback: keep it working.
- Look at changes in the design bench, which runs the same shader files on the same pictures: serve the repository
  root with `python tools/sky3d/serve.py` (port 8765), open `/tools/sky3d/index.html`, wait for "ready", then drive
  it from the console: `bench.set({biome: 'dunes', tod: 0.72}).settle()`, `await bench.loadSky('cu_fair')` then
  `bench.set({skyLow: 'cu_fair'})`, `bench.sheet('x.jpg', [...])` (contact sheets in `tools/sky3d/out`,
  git-ignored). `bench.set` merges: give every tile of a sheet a full set of values. The bench's pass order must
  stay the same as `RealSkyEngine.frame`; a uniform is set by name in both (`RealSkyFrame.fill` / the engine, and
  `bench.js`): add it in both. `LightWeights` (Kotlin) and `lightWeights` (bench) must agree.
- Rules a scenery must keep: nature only; every source and licence written down in `tools/scenery/sources/`;
  built from real-world data (scans, elevation models) wherever such data exists, not from formulas; and a natural
  skyline: never cut a range or a treeline open so the Sun rises and sets on level ground (the owner rejected that;
  the Sun may rise over a mountain). The times stay exact to the eye because the Sun's path stands on the land's
  own skyline where it rises and sets (`bake.sun_lift` writes `lift` into `land.json` when a scenery is packed,
  `tools/scenery/lift.py` adds it to one packed earlier; `RealSky.sunLift`, `SunLift`, `SkyGeometry.y(el, fraction)`;
  the bench does the same). `bake.skyline_limit` is only advice against hiding the Sun's whole day.
- The data the sceneries and clouds are built from lives outside the repository, in `NoorToolsssets\<source>`:
  Poly Haven and ambientCG scans (CC0), elevation models (USGS 3DEP, Copernicus GLO-30, Kartverket; read with
  `tools/scenery/geotiff.py`, which does not read LZW: see `meadow_dem.py`), NASA's sky maps, and CGHEVEN's cloud
  VDBs (free to use in renders, also commercially; the files themselves may not be redistributed: never commit a
  `.vdb`). Fetch files with curl (User-Agent `scenery-tools/1.0`), not through a browser's download button: that
  opens a save dialog on the owner's screen.
- Several Blender jobs at once starve each other. At most three, and a bake at AboveNormal priority; a volume
  enlarged k times renders k times thinner (set density per instance scale).
- Pitfalls met: never write a value near 65 000 into a half-float target (0.10's Sun did, and was black on the
  owner's phone); Blender ignores `mat.cycles.displacement_method` (use `mat.displacement_method`); a generated
  image must be saved and reloaded before Cycles sees it; volume emission is per metre whatever the density;
  BitmapFactory premultiplies alpha unless told not to; `java.util.Random` gives neighbouring seeds nearly the same
  first numbers (scramble the seed).
- On an emulator (`real-sky.yml`, `tools/devicetest/real_sky.sh`) the GPU is software: faithful pictures, slowly.
  Its log lines (`NoorRealSky`) say how long each stage took. An emulator cannot run on the owner's PC until "SVM
  Mode" is enabled in its BIOS (HANDOVER.md section 20).
- `SkyDirectorTest` locks the weather director (bounds, smoothness through the day, the hour of colour, night, the
  cloud sets it chooses), the light weights, and that Kotlin's camera is the shaders' camera.

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
