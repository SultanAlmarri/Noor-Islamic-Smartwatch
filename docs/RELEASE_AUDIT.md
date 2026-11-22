# Release-readiness audit (22 November 2026, code at 0.24.1)

What the code does today, read from the manifests, the Gradle files and the sources at commit `7fd6637`
(phone 0.24.1 / versionCode 49, watch 0.24.1 / versionCode 35), with the place of every fact. The checklist that
follows from it is `docs/RELEASE.md`; the privacy policy written from it is `docs/PRIVACY.md`.

**How this was made, and its limits.**

- Read, not run: no Gradle build, no emulator, no phone, no watch. Two facts come from a build that was already on
  the machine (the lead's checkout, built the same day): the merged release manifest of the phone app and the
  release APK's contents. They are marked "(built APK)".
- Google Play's rules are written here **as far as the author knows them** (knowledge up to mid-2026, from memory, with
  no access to the Play Console). Policies and dates change. Every sentence about what Play requires must be checked
  in the Play Console at submission: the Console itself shows the forms an app must fill and refuses an upload that
  breaks a hard rule. Where the author is unsure, the text says so.
- Nothing here is legal advice.

Paths are from the repository root. `app/.../x` means `app/src/main/java/com/noor/app/x`; `wear/.../x` means
`wear/src/main/java/com/noor/wear/x`; `alerts/.../x` means `alerts/src/main/java/com/noor/alerts/x`.

---

## 1. Permissions

### 1.1 Phone app (`com.noor.smartwatch`, `app`)

Declared in `app/src/main/AndroidManifest.xml` and, through the `alerts` library, in
`alerts/src/main/AndroidManifest.xml` (Gradle merges the two; WorkManager adds one more). The merged release manifest
(built APK) lists exactly the sixteen below and no advertising-ID permission.

| Permission | Declared at | What the code uses it for | Asked of the user? |
|---|---|---|---|
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | app manifest 7–8 | One position on demand for prayer times and the Qibla: `app/.../data/LocationRepository.kt` 68–88 (fused provider, one fix, no tracking). Precise gives neighbourhood names. | Yes: welcome page (`ui/onboarding/OnboardingScreen.kt` 149) or the location page (`ui/location/LocationScreen.kt` 207, 238). The user may refuse and choose a place by hand. |
| `ACCESS_BACKGROUND_LOCATION` | app manifest 12 | The optional refresh about every 6 hours when the app is closed: `data/LocationWorker.kt` 20–28 (runs only if the setting is on and the permission is held), scheduled at 36–44. | Yes, separately: welcome page step 2 (`OnboardingScreen.kt` 151, text `bg_perm_title`/`bg_perm_body`, `res/values/strings.xml` 86–87) and Settings → "Update in the background" (`ui/settings/SettingsScreen.kt` 135, 210, dialog 479–493). Off unless granted. |
| `INTERNET` | app manifest 10 | The three requests of section 2 (map pictures, place search fallback, Singapore timetable). | No (install-time). |
| `ACCESS_NETWORK_STATE` | added by WorkManager (built APK, merged manifest line 45) | WorkManager's own constraints. Minhad's code does not read it. | No. |
| `POST_NOTIFICATIONS` | alerts manifest 5 | Prayer alerts with Stop, reminders, the persistent countdown notification. | Yes, Android 13+: when the user turns the Adhan on in the welcome (`OnboardingScreen.kt` 97), on the Adhan tab (`ui/components/Permissions.kt` 54), or turns the status notification on (`SettingsScreen.kt` 229–232). |
| `SCHEDULE_EXACT_ALARM` | alerts manifest 8 | Alarms at the prayer's minute: `alerts/.../AlertScheduler.kt` 226–228 (`setAlarmClock` on the phone, `setExactAndAllowWhileIdle`), with the inexact fallback at 228–232 and the check at 57. | The user allows "Alarms & reminders" in Android's settings; the Adhan tab opens that page (`ui/adhan/AdhanScreen.kt` 349, `AlertHistoryScreen.kt` 161). Not asked in the welcome. |
| `USE_FULL_SCREEN_INTENT` | app manifest 14 | The Adhan screen over the lock screen while the Adhan plays (`adhan/AdhanAlertActivity.kt`; `alerts/.../AlertNotifications.kt` 164 skips it where Android does not allow it). | Android 14+: the user can turn it off; the Adhan tab links to the setting (`AdhanScreen.kt` 344) and reads the state (`AdhanViewModel.kt` 261). |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | alerts manifest 12–13; service at 31–34 | `AdhanService` plays the Adhan with a visible Stop (`alerts/.../AdhanService.kt` 116–117). | No. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | app manifest 20–21; service at 94–102 with the subtype text | `notify/StatusCountdownService.kt` (89–90) holds the persistent notification so its countdown keeps ticking each second when the app is closed. | No. The notification itself is on by default (`data/AppSettings.kt` line 103), shown once notifications are allowed. |
| `POST_PROMOTED_NOTIFICATIONS` | app manifest 17 | Android 16 Live Update: the countdown as a chip in the status bar (`notify/StatusNotification.kt` 352). On by default (`AppSettings.kt` 108). | No (a normal permission); the user can turn promotion off in Android's settings. |
| `RECEIVE_BOOT_COMPLETED` | alerts manifest 10 | Re-arm alarms, notification and widgets after a restart (receivers: app manifest 59–63, 76–85, 139–148). | No. |
| `VIBRATE`, `WAKE_LOCK` | alerts manifest 14, 16 | Vibration with an alert; CPU awake while the Adhan plays. | No. |
| `com.noor.smartwatch.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | added by AndroidX (built APK) | Internal to AndroidX. | No. |

Not declared (checked by search): `USE_EXACT_ALARM`, `ACCESS_NOTIFICATION_POLICY`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (the Adhan tab only opens Android's battery page, `AdhanScreen.kt` 306), any
storage, contacts, camera, microphone, phone, SMS, Bluetooth or advertising-ID permission. Imported sounds come
through Android's file picker (`ActivityResultContracts.OpenDocument`: `ui/adhan/SoundLibraryScreens.kt` 219,
`PrayerAlertScreen.kt` 127, `AlarmScreen.kt` 123), which needs no permission.

Debug and bench builds only (never in the release build; the merged release manifest has none of it):
`CHANGE_CONFIGURATION`, `WRITE_SETTINGS` and the test hook receiver (`app/src/debug/AndroidManifest.xml`,
`app/src/bench/AndroidManifest.xml`).

### 1.2 Watch app (`com.noor.smartwatch`, `wear`)

`wear/src/main/AndroidManifest.xml` plus the same `alerts` library manifest.

| Permission | Declared at | Used for |
|---|---|---|
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | wear manifest 10–11 | Only when the user taps "Use my location" on the watch (`wear/.../ui/NoorWearNav.kt` 78): one balanced-power fix, named with the offline list (`data/WatchLocation.kt` 45–49, 52–64, 76–94). No background location. |
| `com.google.wear.permission.PUSH_WATCH_FACES` | 14 | Adds and replaces Minhad's own watch face (Watch Face Push, Wear OS 6): `facepush/FacePush.kt`. |
| `com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE` | 16 | Makes that face the active one, once, after the user agrees on the watch. |
| `WAKE_LOCK` | 18 (and alerts) | The Studio face's drawing; the Adhan playing. |
| `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `VIBRATE` | alerts manifest | Prayer alerts on the watch (`ui/Permissions.kt` 43 asks for notifications; `ui/AlertSection.kt` 77 opens the exact-alarm setting). |

The watch app does not hold `INTERNET` (neither manifest declares it, and the merged manifest of the lead's debug
build has none): it makes no network request of its own (confirmed by search: no URL connection in `wear/src/main`).

### 1.3 What Google Play treats as sensitive (as far as the author knows; check each in the Console)

| Item | What Play asks, as far as known | Does Minhad's use plausibly qualify? |
|---|---|---|
| **Background location** (`ACCESS_BACKGROUND_LOCATION`) | A declaration form in the Console (App content → sensitive permissions → location), a short **video** showing the feature and the in-app disclosure, a privacy policy, and a **prominent in-app disclosure** shown before the system prompt. As known, the disclosure must say that the app collects **location**, that it does so **in the background / when the app is closed**, and for which feature. Play approves it only when the feature is core and could not work with foreground access. This is the review step most often refused. | **Uncertain.** The use is small and honest (one low-power fix about every 6 hours, only when the user turned it on, to keep prayer times right while travelling). But the app works without it (it refreshes when opened and on a time-zone change), so a reviewer may call it non-essential. And the present disclosure text (`strings.xml` 86–87: "To keep times right when you travel, choose 'Allow all the time'. Minhad checks rarely and uses little battery.") does not contain the word "location" or say "even when the app is closed": **as written it very likely does not meet the disclosure rule.** Either fix the text and make the video, or remove the permission for the first release (see RELEASE.md, decision D3). |
| **Foreground service types** (Android 14+ targets) | A declaration in the Console for each type used, with a description and, as known, a video for each. `mediaPlayback` is a standard type; `specialUse` is reviewed case by case against the free-text reason in the manifest. | `mediaPlayback` for the Adhan (phone and watch): yes, it is audio the user asked for, with a Stop button. `specialUse` for the live countdown (`StatusCountdownService`): **uncertain**; the reason is real (a per-second countdown the system's own chronometer cannot draw in the app's digits), but a reviewer may answer that a notification chronometer needs no service. The setting "Timer counted by the phone itself" (`AppSettings.statusChipSystemTimer`) already avoids the service; see risk R7. |
| **Exact alarms** | `USE_EXACT_ALARM` is restricted to alarm-clock, timer and calendar apps and needs a declaration. `SCHEDULE_EXACT_ALARM` (what Minhad declares) has, as far as known, no Play form: on Android 14+ it is simply **off by default** for a new install until the user allows "Alarms & reminders". | Minhad's use fits `SCHEDULE_EXACT_ALARM`. Because the app's centre is an alarm (the Adhan at its minute, and since 0.17 the user's own alarms with Snooze), it may also qualify for `USE_EXACT_ALARM`, which would be granted at install; `alerts/src/main/AndroidManifest.xml` 6–7 records that it was left out on purpose. Whether Play would accept the declaration is **not known**. See risk R6: without it, a new user's first Adhans can be late or fall back to the short backup Adhan. |
| **Full-screen intent** (`USE_FULL_SCREEN_INTENT`) | For apps targeting Android 14+, Play grants it by default only to apps whose core function is calling or alarms; as known there is a declaration for it in the Console. Others keep the permission declared but not granted (the user can grant it in settings). | Plausible (an alarm-style screen with Stop for the Adhan and for the user's alarms). **Not certain** that a prayer app is accepted as an "alarm app". The code already works without it (heads-up notification with Stop). |
| **Notifications** (`POST_NOTIFICATIONS`) | No form. Runtime permission on Android 13+. | Fine: asked in context. |
| **Live Updates** (`POST_PROMOTED_NOTIFICATIONS`) | No Play form known. Android's own guidance (as known) reserves Live Updates for activities that are ongoing, **started by the user** and time-sensitive. | **Uncertain**: the countdown is on by default, not started by the user for one activity. Android may refuse to promote it (the code already handles that: `LivePromotion`), and a reviewer could object. Low enforcement risk, but see R7. |
| **Location in general** | A privacy policy (in the Console **and inside the app**, as known), the Data safety form, and an accurate in-app explanation. | The explanation exists, but one sentence in it is not accurate: see risk R4. There is no privacy-policy link in the app yet. |
| **Watch Face Push permissions** | Not known to need a Play form. Faces must be Watch Face Format and pass Google's validator, which the build does (`wear/build.gradle.kts` 105–122, `tools/watchface/build_pushed_face.py`). | Not sure of any further Play rule for apps that push faces; check when adding the Wear OS form factor. |

---

## 2. Network requests and what moves between devices

Search used: `http`, `URL(`, `HttpURLConnection`, `openConnection`, `Socket`, `WebView`, `DownloadManager`, `okhttp`
in all main sources. There are **three** places where Minhad itself opens a connection, all in the phone app, all
HTTPS, none with an account, key, cookie or device identifier. There is no analytics, advertising or crash-reporting
library (search for firebase, crashlytics, analytics, admob, billing, sentry, advertising id, `ANDROID_ID`: nothing).
The developer runs no server.

| # | Host | Code | When | What is sent | What comes back | Does the user's location leave the phone? |
|---|---|---|---|---|---|---|
| N1 | `server.arcgisonline.com` (Esri: World Imagery, World Boundaries and Places, World Street Map) | `app/.../ui/location/TileMap.kt` 71–78 (addresses), 220–236 (fetch), 228 (User-Agent) | Only while a map is on screen: "Choose on the map" (`LocationScreen.kt` 574) and the "Confirm the location" page (490). Never from the home screen or in the background. | The address of each map square needed (zoom, row, column), the phone's IP address (as with any request), and a User-Agent: `Minhad/<version> (Android; prayer times; <the repository's GitHub address>)` (`data/WorldStore.kt` 200). | Map pictures, kept in the app's cache (`cacheDir/map-tiles`, 30 days, at most about 150 MB: `TileMap.kt` 164, 224, 239–243). | **Indirectly, yes**: the squares requested are the area the user is looking at, which is normally where they are or the place they chose (the map's "my location" button centres it on them). At the deepest zoom a square is roughly 150 m across. Esri sees area + IP address, not a name or account. |
| N2 | `photon.komoot.io` (komoot's public Photon search, OpenStreetMap data) | `data/WorldStore.kt` 178–197 | Only when the user types a place search of 3 or more letters **and** the phone has no Android geocoder (`WorldStore.kt` 130, 137). On phones with Google Play services this path is normally never taken. | The words typed, the language, IP address, the same User-Agent. | Up to 12 places with coordinates. | No position is sent, only the typed words (which may name the user's town). |
| N3 | `data.gov.sg` (Singapore's open-data portal) | `data/TimetableUpdater.kt` 28–39, 44–48; started from `ui/today/TodayViewModel.kt` 136 | Only when the prayer-time authority in use is MUIS (Singapore), at most once a week, and not while the carried table reaches 330 days ahead. | A plain request for the public dataset `d_a6a206cba471fe04b62dd886ef5eaf22`; IP address. The code sets no User-Agent here, so Android's default one goes out, which as known names the Android version and the phone's model. Nothing else about the user. | The MUIS timetable (dates and times), stored in `filesDir/timetables/muis_singapore.csv` (`shared/.../geo/GeoData.kt` 54). | No. (That the request is made at all tells the server the user follows the Singapore timetable.) |

**Requests made for the app by Android, not by its own code** (they still carry the user's data off the phone):

| # | Service | Code | When | What leaves |
|---|---|---|---|---|
| A1 | Android's `Geocoder` (the phone's built-in place-name service; Google's on phones with Google Play services), reverse lookup | `data/LocationRepository.kt` 99–100, 140–156; `data/WorldStore.kt` 106–107, 152–163 | In automatic mode, each time a new position is more than 300 m (precise) or 3 km (approximate) from the last named one, including the background refresh; and when the user pins a point on the map or types coordinates. Twice each time (English and Arabic names). | **The position's latitude and longitude**, to the geocoder's provider. The app's comment says "nothing sent by Noor itself" (`LocationRepository.kt` 38), which is true of Minhad's own connections but not of the position: it does leave the phone. |
| A2 | Android's `Geocoder`, search by name | `data/WorldStore.kt` 126–139, 165–175 | When the user types a place search (3 or more letters; `ui/location/LocationViewModel.kt` 66). | The words typed. |
| A3 | Google Play services location (fused provider) | `LocationRepository.kt` 71–80; watch: `wear/.../data/WatchLocation.kt` 52–64 | Each position fix. | Works on the device; what Google's location service itself sends to Google depends on the phone's own "Location accuracy" setting, not on Minhad. |
| A4 | Android's backup (see section 3) | `app/src/main/AndroidManifest.xml` 25; `wear/src/main/AndroidManifest.xml` 22 | When the user has device backup on. | A copy of the app's settings and files, to the user's own Google account. |

**Links the user taps** (they open the browser or the Play Store; nothing is sent by Minhad):
the install guide on GitHub (`ui/watch/WatchScreen.kt` 110, 136), the Galaxy Wearable app's Play page (376), each
recording's "Source" and "Licence" pages (`ui/adhan/SoundLibraryScreens.kt` 188–189), Minhad's Play page on the watch
(`data/WatchSetup.kt` 94), and from the watch the download page on GitHub opened on the phone
(`wear/.../data/PhoneLink.kt` 45–51, 55).

**Sharing the user starts**: "Share report" (`ui/adhan/AlertHistoryScreen.kt` 94–96) hands a plain text to the app
the user picks: Minhad's version, the phone's maker, model and Android version, the alert settings, permission
states and the last 40 alerts (`ui/adhan/AdhanViewModel.kt` 468–493). It holds no position and no place name.

### Phone ↔ watch (Wear OS Data Layer, through Google Play services, between the user's own paired devices)

| Path | Direction | Content | Code |
|---|---|---|---|
| `/noor/config` | phone → watch | The resolved settings: **the position (latitude, longitude, elevation)**, time zone, place name (English, Arabic), country, method, Asr school, offsets, Hijri adjustment, language, digits, clock format, the alert choices, and eight weeks of prayer times | `app/.../data/WearSync.kt` 47–54, 89–132 |
| `/noor/sound/<id>/<ext>` | phone → watch | A full Adhan recording or a sound the user imported, when chosen for an alert on the watch | `data/WatchSoundSender.kt` 78–92, 107; received in `wear/.../data/ReceivedSounds.kt` |
| `/noor/face` | phone → watch | The id of the chosen watch face and the time of asking | `data/WatchSetup.kt` 115–121 |
| `/noor/studio` | phone → watch | A Minhad Studio face design | `ui/watch/studio/StudioRepository.kt` 56–57 |
| `/noor/sync-request` | watch → phone | An empty message: "send the settings again" | `wear/.../data/PhoneLink.kt` 35–42; `app/.../data/WatchRequestService.kt` 15–19 |
| `/noor/watch/status` | watch → phone | App version, Wear OS level, face state, whether notifications and exact alarms are allowed, how many alarms are armed, which sounds the watch holds, which settings it uses. **No position.** | `wear/.../data/StatusPublisher.kt` 28–54 |
| capabilities `noor_phone_app`, `noor_watch_app` | both | "Minhad is installed here" | `app/src/main/res/values/wear.xml` 5, `wear/src/main/res/values/wear.xml` 5 |
| remote activity | both | Opens a page on the other device (`noor://open`, `noor://face/activate`, `noor://studio`, a Play Store page, a web page) | `data/WatchSetup.kt` 94–102, 123–127; `PhoneLink.kt` 45–51 |

The watch's own position (when set up on the watch) is not sent anywhere: no geocoder, no report field
(`WatchLocation.kt` 76–94, `StatusPublisher.kt`).

---

## 3. What is stored on the device, and backup

### Phone

| What | Where | Code |
|---|---|---|
| All settings: location mode, the place chosen by hand, **the last position (latitude, longitude, elevation, time, place names)**, method, offsets, language, digits, theme, alert choices, scenery choices | DataStore file `noor_settings` (credential-protected app storage) | `data/SettingsRepository.kt` 25, keys 37–76, position 166–176 |
| Small preference files: `minhad_permissions` (which permissions were asked), `minhad_studio` (watch-face designs), `noor_watch` (chosen face), `noor_watch_push`, `noor_migrations`, `noor_timetables` (last check), `noor_status_live` (timer tuning), `noor_ui` and the sky's quality files, the widgets' options | SharedPreferences | `ui/components/Permissions.kt` 66; `ui/watch/studio/StudioRepository.kt` 21; `data/WatchSetup.kt` 109; `NoorApplication.kt` 75, 123; `data/TimetableUpdater.kt` 26; `notify/StatusNotification.kt` 213–214; `sky/RealSky.kt`, `sky/SkyLevels.kt`; `widget/WidgetOptions.kt` 197 |
| Sounds the user imported (up to 40 MB each) | `filesDir/sounds/custom` | `alerts/.../CustomSounds.kt` 32, 114 |
| The refreshed MUIS timetable | `filesDir/timetables` | `shared/.../geo/GeoData.kt` 54 |
| Alert history, the last 40 alerts | `noBackupFilesDir/alert_history.json` in **device-protected** storage (readable before the first unlock) | `alerts/.../AlertLog.kt` 99–113 |
| The alarms' own copy of the coming weeks' times and alert choices (it includes the position), the prayers already handled, snoozes | SharedPreferences `noor_alert_state`, `noor_snoozes` in device-protected storage | `alerts/.../AlertStore.kt` 26–34; `AlertScheduler.kt` 154 |
| Map pictures | `cacheDir/map-tiles` (30 days, about 150 MB at most) | `ui/location/TileMap.kt` 164, 224, 239–243 |
| Compiled sky shaders | `cacheDir/sky-programs` | `sky/RealSkyEngine.kt` 372 |

### Watch

Settings and the phone's configuration (with the position) in DataStore `noor_watch`
(`wear/.../data/ConfigRepository.kt` 30); the tasbih counts in `noor_tasbih` (`data/TasbihRepository.kt` 17);
`noor_watch_ui` (`ui/WearViewModel.kt` 28); received sounds in `filesDir/sounds/received`
(`data/ReceivedSounds.kt` 25); the Studio design (`studio/StudioStore.kt` 29); face choice (`facepush/FacePush.kt`
91); the same alert files as the phone.

### Backup

Both manifests say `android:allowBackup="true"` (app manifest 25, wear manifest 22) and neither names backup rules
(no `android:fullBackupContent`, no `android:dataExtractionRules`; `res/xml` holds only widget and face files).
So Android's automatic backup, where the user has it on, may copy the app's files and preferences to the user's
Google account (and offer them to a new phone): that includes **the saved position and place**, all settings, the
imported sounds and the refreshed timetable. Not copied: the cache (map pictures) and anything in
`noBackupFilesDir` (the alert history). As far as the author knows, the automatic backup has a size limit of about
25 MB per app and skips the app entirely when its data is larger, so a user with large imported sounds may get no
backup at all; and whether device-protected preferences (`noor_alert_state`) are included was **not determined**.
What a restore onto a new phone does to the alarms' remembered state was not tested.

---

## 4. Third-party code, services and bundled material

### 4.1 Code and services (`gradle/libs.versions.toml`, `app/build.gradle.kts` 76–111, `wear/build.gradle.kts` 62–102)

| What | Licence / terms | Notes |
|---|---|---|
| AndroidX (core, appcompat, activity, lifecycle, navigation, DataStore, WorkManager, splash screen, profile installer, concurrent-futures), Jetpack Compose (BOM 2025.05.00, Material 3, extended Material icons), Wear Compose, Tiles, ProtoLayout, watch-face, complications, Watch Face Push, ongoing, input, remote-interactions | Apache License 2.0 | Apache 2.0 asks that a copy of the licence travel with redistributed code. Some AndroidX licence files are inside the APK's `META-INF` (built APK); there is **no "open-source licences" page in the app**. Common practice is such a page; low risk. |
| Kotlin standard library, kotlinx-coroutines | Apache License 2.0 | Same. |
| Google Play services: location 21.3.0, wearable 19.0.0 | Android SDK / Google APIs terms | Used on phone and watch. No Firebase, ads or analytics. |
| Google's watch-face validator (`validator-push-cli` 1.1.0-alpha01) | Build tool only | Not shipped. |
| Native libraries in the phone APK: `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` (four ABIs) | AndroidX | Both aligned to 16 KB (built APK, read from the ELF headers), which Play requires of apps targeting Android 15+ as far as known. |

### 4.2 Bundled material, its obligations, and where the attribution is shown

| Material | In the app as | Licence | What it requires | Shown in the app? |
|---|---|---|---|---|
| IBM Plex Sans Arabic (5 weights phone, 3 watch), Reem Kufi | `app/src/main/res/font`, `wear/src/main/res/font` | SIL Open Font License 1.1 (`licenses/`) | The copyright notice and licence must travel with the fonts (the font file's own metadata counts if viewable). | The font files carry their copyright and OFL statement in their name tables (read from the files). **No mention in the app.** Acceptable under the OFL's wording as understood; a line on a licences page would be better. |
| The watch faces' fonts: a digits-only subset of IBM Plex Sans Arabic Light (`tools/watchface/faces/*/res/font/noor_digits.ttf`), and in the "original" face 60 font files remade from 17 OFL families (`legacy/watchface-v1.3.2/.../res/font`, licences in `legacy/watchface-v1.3.2/licenses`) | Inside the face APKs the watch app carries | OFL 1.1 | As above; **and** a modified font may not keep a Reserved Font Name. | The subset `noor_digits.ttf` still names itself "IBM Plex Sans Arabic Light", and the legacy one "NoorWF IBM Plex Arabic": both are modified fonts that keep IBM's reserved name "Plex". A small licence fault; the legacy folder must not be edited (rule 2), so the remedy there is to leave the "original" face out of a store build or rename in the copy the build makes. |
| GeoNames: `app/src/main/assets/world.bin` (839,301 places), `shared/src/main/assets/gazetteer.bin` (34,197 places, phone and watch) | Assets | CC BY 4.0 | Credit GeoNames. | Phone: yes, on the location page (`LocationScreen.kt` 247, string `geonames_credit`) and on Settings → Prayer-time accuracy (`AccuracyScreen.kt` 60, `sources_body`). **Watch: no** (only in `docs/WATCH.md`). |
| Time-zone boundaries (timezone-boundary-builder, OpenStreetMap data): `app/src/main/assets/tzmap.bin` | Asset (phone) | ODbL 1.0 | Credit OpenStreetMap contributors; a database derived from it must be offered under the ODbL with the way it was made. | Credit: yes (`loc_tz_credit`, `LocationScreen.kt` 247). The way it was made is `tools/geo/build_timezones.py` in the public repository, which serves the second part **only while the repository stays public**; the app does not link to it. |
| Esri map pictures (fetched, not bundled) | N1 | Esri's terms of use | Attribution on the map; and the code's own comment says "free for non-commercial use with attribution" (`TileMap.kt` 67–69). | Attribution: yes, under the map (`LocationScreen.kt` 623, texts at `TileMap.kt` 75, 77). **Whether an app on Google Play may use these servers without an Esri account or key, and may keep the pictures 30 days, is not established**: see risk R5. |
| Photon (fetched) | N2 | Public demo server, OpenStreetMap data (ODbL) | Fair use; OSM credit for results. | No credit line for Photon results. Rarely reached. |
| MUIS timetable (`shared/src/main/assets/timetables/muis_singapore.csv`, and N3) | Asset + refresh | Singapore Open Data Licence | Acknowledge the source. | Yes (`sources_body`), though not in the licence's suggested wording. |
| IACAD Dubai timetable, 97 days (`shared/src/main/assets/timetables/iacad_dubai.csv`) | Asset | **No licence**: copied from IACAD's website feed and from Internet Archive copies of its pages (`docs/PRAYER_ACCURACY.md`, "Official data used", "Routes to exact times": "no published API or licence. Needs IACAD's permission before a phone fetches it") | Unknown. Prayer times are facts, and the app only carries, not fetches, them; but there is no permission on record. | Named as the source (`sources_body`). See risk R8 (also about implying affiliation). |
| Adhan: Makkah (3omar Faruq, CC BY 3.0), Madinah (ejaz215, CC BY 3.0), Egypt (3bagbrew, CC0) | `app/src/main/assets/sounds`, openings also in `wear/src/main/assets/sounds`; `alerts/src/main/res/raw/noor_backup_adhan.ogg` | As stated in `docs/SOUNDS.md` and `assets/sounds/library.json` | CC BY: name the author, the licence, link the source, say it was changed. | Phone: yes: Adhan tab → the library → a recording → "Credit and licence" with Source and Licence links and the note of changes (`ui/adhan/SoundLibraryScreens.kt` 180–193). **Watch: no credit anywhere**, though it carries and plays the Makkah and Madinah openings. |
| Nature sounds: birds and stream (US National Park Service, public domain, "please credit"), rooster (Vincent Duseigne, CC BY 3.0), waves and rain (Wu Tsan-Cheng, CC BY 3.0) | Same folders, phone and watch | As stated | CC BY as above. | Phone: yes, same page. **Watch: no.** |
| **Adhan: Dubai, Abdulrahman Al-Hindi (two recordings, each whole and as an opening)** | `app/src/main/assets/sounds/adhan_dubai*.ogg`, openings in `wear/src/main/assets/sounds`; **the two original files are also in the public repository**, `tools/sounds/owner/dubai.m4a` and `dubai_fajr.m4a` | "Owner's rights" (`library.json`); `docs/SOUNDS.md`: "supplied by the app's owner on 16 November 2026, who states that he owns these recordings and holds all the rights to use them" | A licence or written permission from whoever holds the rights to the recordings. | Shown as "Abdulrahman Al-Hindi; recording supplied by the app's owner · Owner's rights". **No document is on record. Both original files carry, in their own tags, the address of a YouTube video** (read from the files: `dubai.m4a` has the title "Adhan of Abdulrahman Al Hindi [from Dubai]" and a `youtube.com/watch` address; `dubai_fajr.m4a` another), which is what a copy downloaded from YouTube looks like. The "Source" link the app shows for them is `owner:tools/sounds/owner/dubai.m4a`, not a web address, so tapping it does nothing useful. See risk R1. |
| The sceneries (`app/src/main/assets/sky2/land/*`, `assets/medium/*`) | Rendered pictures | Sources in `tools/scenery/sources/*.md`: Poly Haven scans (CC0), USGS 3DEP elevation (public domain), NASA sky and Moon maps (public domain, credit asked), **Kartverket elevation (CC BY 4.0)** and **Copernicus DEM (free with a required notice)** for the Arctic | CC0 and public domain: nothing. Kartverket and Copernicus: the notices written in `tools/scenery/sources/arctic.md` under "Attribution to show wherever the app lists its sources". | **No.** `sources_body` does not mention them; no string in the app names Kartverket, Copernicus, NASA or Poly Haven. A gap for the Arctic scenery. |
| Cloud pictures (`assets/sky2/clouds/*`) | Rendered pictures of CGHEVEN's cloud volumes | CGHEVEN's licence as recorded in `tools/scenery/sources/clouds_low.md` / `clouds_high.md`: free to use in renders, also commercially; **the volume files may not be redistributed** | Do not ship or commit a `.vdb`. | Kept: `git ls-files` has no `.vdb`, and the release APK has none (built APK). |
| Material icons (phone and watch) | Drawn by code / vector drawables | Apache 2.0 | As 4.1. | Only in `docs/WATCH.md`. |

Three strings that would be untrue if they were ever shown again are still in the resources but no longer used by
any screen (search for their names finds no code): `adhan_library_intro` ("Every recording here is freely
licensed…"), `lib_intro`, `adhan_sounds_credit` ("Original sounds made for Minhad… CC0")
(`app/src/main/res/values/strings.xml` 294, 420, 305). Remove them with the release clean-up.

The repository itself has no licence file at its root (only `licenses/` for the two fonts). That is not a Play
matter, but a public repository without a licence gives nobody the right to reuse the code, and the app's sources
text points readers to it.

---

## 5. What must go or change before a store release

### 5.1 The tester's options (the owner's word: removed before release)

Everything that refers to them:

| Place | What |
|---|---|
| `app/src/main/java/com/noor/app/testing/Tester.kt` (165 lines), `TestingScreen.kt` (308), `NotificationTests.kt` (142) | The package itself. Its screen is written in English only. |
| `app/.../MainActivity.kt` 278–279 (`Routes.TESTING`), 429–441 (the page in the navigation), 447 and 460–461 (`TesterBanner` over every page; the banner's height is part of every page's bottom padding, 458) | Route, page, banner. |
| `app/.../ui/settings/SettingsScreen.kt` 302–304 | The "Tester's options" row in Settings. |
| `app/.../NoorApplication.kt` 117–118 | `Tester.restoreAfterDemo` at start. |
| `app/.../ui/today/LivingSky.kt` 480–482, 587–593, 650–654, 670 | The home sky reading the tester's time, weather, season and fog. |
| `app/.../sky/RealSkyFrame.kt` 784 | The fog parameter the tester sets. |
| `app/.../notify/StatusNotification.kt` 106–107 (`test?.let …`), 172–186 (`Shown`, `TestSource`, `test`), and the tuning the page writes: `slotMode` 191–214, `StatusTicker.saveTiming` 744–745 | The notification test hook. The "icon slot" modes 0–6 and the timer's lead time are chosen on the tester's page only: before it goes, one mode and one lead time must be settled as the shipped default (today: mode 3, 80 ms). Modes 2, 3, 5 and 6 write undocumented Samsung keys (`android.ongoingActivityNoti.*`, 409–416). |
| `app/src/main/AndroidManifest.xml` 87–90 | `com.samsung.android.support.ongoing_activity`, added for the tester's "Samsung live card" experiment. |
| `app/src/main/res/values/strings.xml` 70–74, `values-ar/strings.xml` 70–73 | `settings_testing`, `testing_row`, `testing_row_note`, `kw_testing`. |
| Tests: `app/src/test/java/com/noor/app/testing/DemoRestoreTest.kt`, `NotificationTestsTest.kt`, and references in `design/HomeFrameDesignTest.kt`, `design/PagesDesignTest.kt` | To remove or adapt with the code (rule 4: by removing the feature they test, not by weakening others). |
| Documents: `CLAUDE.md`, `docs/HANDOVER.md` sections 24, 37, 40, `docs/ARCHITECTURE.md`, `README.md` | Mentions. |

The debug hooks are already outside the release build: `app/src/debug` (`TestHookReceiver`), `app/src/bench`
(`BenchStrictMode`, the `bench` build type, `app/build.gradle.kts` 42–53), `wear/src/debug` (`WatchTestHook`). The
merged release manifest contains none of them. Never upload a `debug` or `bench` build.

### 5.2 Signing

- Today every build, release included, is signed with `signing/noor-dev.keystore`, whose password is in the public
  repository (`app/build.gradle.kts` 19–27, 36; `wear/build.gradle.kts` 20–27, 35; `signing/README.md`). Anyone can
  sign an "update" with it. It must not become the key of a store app.
- A Play release needs **Play App Signing**: Google keeps the app's real signing key, and the owner signs uploads
  with a private **upload key** that is never in the repository. The Gradle files need a release signing
  configuration read from outside the repository (a private file or CI secrets), keeping the dev key for
  development builds.
- **The owner's installed builds.** Android does not let an app signed with one key be replaced by the same app id
  signed with another. The Play build cannot install over a `dev-latest` build: the owner (and any tester) must
  uninstall Minhad from the phone and from the watch first, which erases its settings and imported sounds.
- **Phone ↔ watch.** The Data Layer connects only apps with the same id and the same signature. On Play both are
  signed by Google with the one app key, so they pair. A Play phone app and a side-loaded dev watch app (or the
  reverse) do **not** talk to each other. The owner can no longer install the watch build by `adb`
  (CLAUDE.md); Play's internal testing track is therefore also his practical way to put builds on the watch.
- **Watch faces pushed by the watch app.** They are separate small APKs (package `com.noor.smartwatch.watchfacepush.noor`)
  signed at build time with `signing/noor-watchface-dev.keystore`, also public
  (`tools/watchface/build_pushed_face.py` 29–34, 120–121). A store build should sign them with a private key
  (Watch Face Push wants a key other than the app's, per `signing/README.md`). Whether Wear OS lets a face signed
  with a new key replace an installed one signed with the old key was **not determined**; since the dev watch app
  must be uninstalled anyway, the moment of the first Play install is the moment to change it. If the application id
  changes (5.4), this package name changes with it (`build_pushed_face.py` 29–30; `wear/src/main/AndroidManifest.xml`
  109, 125, 156).
- **The frozen face v1.3.2** (`com.noorface.prayer`) is a different app with its own private key
  (`noor-release.jks`, not in the repository). Nothing here touches it; it stays installed and keeps working. It is
  not part of this release.

### 5.3 Target API level, bundles, version codes, shrinking

- `targetSdk = 35`, `compileSdk = 36` for both apps (`app/build.gradle.kts` 9, 14; `wear/build.gradle.kts` 9, 15).
  As far as the author knows, Play has required new apps and updates to target Android 15 (API 35) since 31 August
  2025 and raises the level by one each August, so **since 31 August 2026 a new phone app probably has to target
  Android 16 (API 36)**, with Wear OS apps allowed one level lower. If so, the phone app's target is one level too
  low and the Console will refuse the upload. Not verified: check the Console's message. Moving to 36 changes
  behaviour (among others, as known: edge-to-edge can no longer be opted out of, and on large screens the system
  ignores orientation and resize restrictions), so it needs a test pass on emulators, not just a number change.
- Play takes **Android App Bundles** (`.aab`), not APKs, for new apps. CI builds APKs only
  (`.github/workflows/android.yml` 36: `assembleRelease`). A release needs `:app:bundleRelease` and
  `:wear:bundleRelease`.
- **Version codes.** Phone 49, watch 35 (`app/build.gradle.kts` 15, `wear/build.gradle.kts` 16). Under one
  application id every uploaded bundle needs its own code, never reused. The two sequences overlap (the watch will
  reach 36…49, which the phone has used): harmless for builds never uploaded, a refusal for any code Play has seen.
  Choose a scheme before the first upload (for example the watch always the phone's code plus 100000) and keep it.
- `versionName` "0.24.1": the owner may want the store version to read 1.0.0; his decision.
- R8 and resource shrinking are on for release (`app/build.gradle.kts` 32–34, `wear/build.gradle.kts` 32–34); the
  rule files are empty apart from comments (`app/proguard-rules.pro`, `wear/proguard-rules.pro`,
  `alerts/consumer-rules.pro`), which matches code without reflection. The released build has been the owner's
  daily build, so the shrunk app is the tested one. Nothing to change.
- The phone APK is 61 MB (built APK), of which the sky pictures are 32 MB, sounds 9 MB, the world's places 9 MB.
  Within Play's limits as known; a large first download on mobile data.

### 5.4 The name and the application id

- Every name the user sees says Minhad / منهاد: `app_name` (`app/src/main/res/values/strings.xml` 3, Arabic file 3;
  `wear/src/main/res/values/strings.xml` 3, Arabic 3), the tile, the faces (`tools/watchface/noor_face.py` 356,
  358). A search of all string resources in both languages for "Noor" and "نور" finds no user text (only style names
  such as `Theme.Noor`, which users never see).
- **Still "Noor" to a user:**
  1. The "original" watch face, when chosen, is listed by the watch as **"Noor Prayer Watch Face" / "واجهة نور
     للصلاة"** (`legacy/watchface-v1.3.2/NoorWatchFace/watchface/src/main/res/values/strings.xml` 3 and `values-ar`
     3, packaged unchanged by `tools/watchface/build_pushed_face.py` as the variant `original`).
  2. The links that open GitHub show the repository's name "Noor-Islamic-Smartwatch" and pages that say Noor
     throughout: the install guide (`ui/watch/WatchScreen.kt` 136 → `docs/INSTALL.md`) and the watch's "Minhad for
     your phone" (`wear/.../data/PhoneLink.kt` 55 → the `dev-latest` release page with `Noor-*.apk`). **Both must
     change for a store release in any case**: a store app should send users to its Play page, not to a development
     download and `adb` instructions.
  3. The same repository address is sent to Esri and Photon in the User-Agent (`data/WorldStore.kt` 200).
  4. The application id `com.noor.smartwatch` is visible in the Play Store address and in Android's app details.
- **The application id is permanent once published.** It was kept through the rename "so installed builds keep
  updating". With Play App Signing the installed builds cannot update to the Play build anyway (5.2), so that reason
  no longer holds for the store build: this is the one moment the id could become something with "minhad" in it at
  no cost to anyone. Changing it touches `applicationId` in both Gradle files, the watch-face package name
  (above), and the debug hooks' action names; the code packages (`com.noor.*`) can stay. The owner's decision
  (RELEASE.md, D2).
- The sources text in the app says "listed in docs/AUTHORITIES.md in the Minhad repository" without an address
  (`strings.xml` 191).

### 5.5 The watch app on Play

As far as the author knows: the watch app is published under the **same listing and application id**, as its own
bundle on the Wear OS release track, after adding the Wear OS form factor in the Console (which starts a review
against the Wear OS quality guidelines and requires at least one Wear OS screenshot). What the code already has:
the `watch` feature and the standalone flag (`wear/src/main/AndroidManifest.xml` 4, 30), tiles with previews
(169–170, 183–188), complications, a splash theme, swipe-to-dismiss navigation, min Wear OS 4 (`minSdk = 33`).
Open points:

- **Minhad Studio's face is a legacy (AndroidX) watch face** (`wear/src/main/AndroidManifest.xml` 60–74,
  `wear/.../studio/StudioFaceService.kt`, library `androidx.wear.watchface` 1.2.1). `docs/WATCH.md` (lines 117–130)
  records, with Google's sources, that since 14 January 2026 such faces cannot be installed or updated from Google
  Play on any watch. Whether Play refuses the whole watch app because it contains one, or only the face, is **not
  known**; it is the largest open question for the watch. See risk R2.
- The "Minhad for your phone" link and the install guide (5.4).
- No credit for GeoNames or the recordings on the watch (4.2).
- Nothing in the watch app has run on a real watch (`docs/WATCH.md`, "Needs a real watch").

### 5.6 Things Play asks for that do not exist yet

- A **privacy policy** at a public web address, entered in the Console, and (as known) reachable **inside the app**.
  There is no such link in the app. Draft: `docs/PRIVACY.md`.
- The **Data safety** form (answers prepared in RELEASE.md).
- A **store listing**: texts (`docs/STORE_LISTING.md`), a 512 × 512 icon, a 1024 × 500 feature graphic, screenshots
  for phone and Wear OS.
- The other "App content" forms the Console lists (content rating, target audience, ads, app access, and whichever
  declarations it currently requires of every app).
- A contact e-mail address shown publicly on the listing (the owner's to choose).

---

## 6. Risks, ranked

Ranked by how likely each is to stop or damage the release, and how hard it is to undo.

**R1. The Dubai Adhan recordings have no proof of rights, and their files point to YouTube.** Four files in the
phone app, two in the watch app, the two originals in the public repository (section 4.2). The voice is a named,
well-known muezzin; the files' own tags hold YouTube addresses. The owner states he holds the rights; nothing on
record shows it. A copyright complaint to Google can take the whole app down and counts against the developer
account; the public repository is already distributing the originals. This also touches rule 7 ("No unlicensed
copyrighted recordings"). And one of them is what every user hears unless they change it: the default Fajr
Adhan is the Dubai Fajr recording (`core/prayer/src/main/kotlin/com/noor/prayer/alerts/AlertSettings.kt` 245,
`DEFAULT_FAJR_ADHAN = "adhan.dubai_fajr"`; the default for the other prayers is Makkah, line 240). Only the owner can settle it: produce the licence or written permission and keep it with
the project, or the recordings leave the app and the repository before release. Not fixed here.

**R2. The watch app contains a legacy watch face (Minhad Studio).** By the project's own research Play no longer
installs such faces (5.5). Possible outcomes: the watch bundle is refused, or accepted with the face unusable.
Decide before submitting the watch app: remove the Studio face service from the store build (the sixteen Watch
Face Format faces remain), or rebuild it on the Watch Face Format route sketched in `docs/WATCH.md`.

**R3. Nothing has been tested on a real phone or watch by anyone but the owner's own use, and the watch not at
all.** `docs/ARCHITECTURE.md` ("Known limitations"), `docs/ALERTS.md` ("Not yet tested"), `docs/WATCH.md` ("Needs a
real watch"). The alarm path is proven on emulators only; phone makers' battery managers, the lock screen, Do Not
Disturb, the watch's alerts and sound transfer are unproven. An Adhan app that misses Fajr on Samsung phones gets
one-star reviews in its first week. Play's closed-testing period (RELEASE.md) is the chance to find this.

**R4. The app tells users their location "stays on this phone", and it does not.** `perm_body`
(`app/src/main/res/values/strings.xml` 83; Arabic file 82): "Minhad uses your location to work out prayer times. It
stays on this phone." In fact the position goes to the phone's geocoder service to be named (A1), the area viewed
goes to Esri when a map is open (N1), the position goes to the user's watch (`/noor/config`), and Android's backup
may copy it to the user's Google account (section 3). None of this is hidden or unusual, but the sentence is
false as written, it is the text shown right before the permission prompt, and a privacy policy that tells the
truth (PRIVACY.md does) will contradict it. Play's user-data policy is about exactly this kind of mismatch. Reword
the string in both languages.

**R5. The map uses Esri's servers on terms the code itself describes as "non-commercial", without an account or
key, and keeps the pictures for 30 days.** (4.2, N1.) Whether that is allowed for an app distributed on Google
Play was not established; Esri could block the app's User-Agent at any time, and the map would go blank (the rest
of the location page keeps working). Check Esri's current terms or register for a key, or change provider in the
one place the code names (`MapLayer`, `TileMap.kt` 71–78).

**R6. On Android 14 and later a new user's Adhan is not exact until they find a setting.**
`SCHEDULE_EXACT_ALARM` is off by default for new installs; the welcome turns the Adhan on and asks only for
notifications (`OnboardingScreen.kt` 91–98). Until the user opens the Adhan tab and allows "Alarms & reminders",
alarms are inexact and Android may refuse to start the player from the background, in which case the short backup
Adhan plays (`docs/ALERTS.md`). The app says so on the Adhan tab, but a first-day user may never look. Either lead
the user to the setting in the welcome, or try for `USE_EXACT_ALARM` (1.3).

**R7. Three declarations a reviewer may refuse: background location, the "special use" foreground service, and the
Live Update on by default.** (1.3.) Each has a fallback already in the code (refresh on opening; "Timer counted by
the phone itself"; the ordinary notification). The background-location disclosure text does not meet the rule as
known. The default status-bar notification also carries a **transparent icon** (`StatusNotification.kt` 371,
`ic_notification_clear`) at the owner's wish, so the status bar shows a timer with no sign of which app it belongs
to; and four of the seven "icon slot" modes use undocumented Samsung keys. The simplest first release drops
background location and ships one documented notification mode.

**R8. Official bodies are named without their permission.** The app carries IACAD's times copied from its website
and archives with no licence on record (4.2), and names IACAD, Awqaf, MUIS and others throughout. The in-app
wording is careful ("official timetable where published", "no official local timetable is implied"). The store
listing must be equally careful: Play acts against apps that suggest a government affiliation they do not have.
`docs/STORE_LISTING.md` says "not affiliated" in so many words; keep it.

**R9. Accuracy must be described as the evidence allows.** The measured figures (`docs/PRAYER_ACCURACY.md`,
`accuracy/results/EVIDENCE.md`) are good but not "exact": Dubai 89.6% to the minute and the rest one minute off;
several countries' methods never compared with an official table; all of it computed on a computer, none measured
on a device. Words like "the most accurate" or "exact everywhere" in the listing would be untrue and are the kind
of claim users test on day one.

**R10. Attribution gaps** (4.2): no credits on the watch (GeoNames, CC BY recordings); Kartverket and Copernicus
notices missing from the phone app; no open-source licences page; two modified fonts keeping the reserved name
"Plex"; the Dubai entries' dead "Source" link. Each is small; together they are an afternoon's work and remove
every licence complaint anyone could make.

**R11. Backup copies the saved position off the phone by default and may fail silently with large imported
sounds** (section 3). Decide what should be backed up and write the rules (`dataExtractionRules`), or state it in
the policy as PRIVACY.md does now.

**R12. Leftovers a store user should never meet**: the tester's options (5.1); links to GitHub's development
download and `adb` guide (5.4); the face named "Noor Prayer Watch Face"; three untrue unused strings (4.2).

**R13. Version codes and the application id are forever once uploaded** (5.3, 5.4). A mistake here cannot be
corrected later; settle both before the first upload, even to internal testing.

**R14. A display rule with a political meaning.** Israel is shown as Palestine with the Palestinian flag
(`core/prayer/.../geo/Flags.kt` `displayCountry`, `data/WorldStore.kt` 206–214; rule 9, the owner's decision, kept).
Recorded here only so that the owner publishes knowing that the store listing is visible worldwide and that some
users or reviewers may report it; no Play rule against it is known to the author.

**Looked for and not found:** a crash on first run without location (every screen read handles "no place yet":
`ui/today/TodayScreen.kt` 277, `ui/qibla/QiblaScreen.kt` 146, `widget/Widgets.kt` 122, the Adhan tab's "Set a
location first"; read, not run); missing Arabic strings (975 of 975 in the phone app, 166 of 166 on the watch, 42
of 42 in `alerts`, counted by script); cleartext traffic (all three hosts are HTTPS; no network-security override);
secrets in the repository other than the two development keystores that are public on purpose; cloud volume files.
