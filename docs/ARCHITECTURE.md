# Noor architecture

Living document: each development phase adds its section. Last updated 6 November 2026 (version 0.9.5).

## Modules

| Module | What it holds | Android? |
| --- | --- | --- |
| `core/prayer` | Prayer engine, calculation profiles, Hijri date, schedule, phone↔watch formats (`WatchConfig`, `WatchReport`), the watch's own set-up (`LocalSetup`), Qibla geodesy, compass maths, country flags, tasbih counter, Islamic calendar events | No (pure Kotlin, tested on the JVM) |
| `app` | Phone app: Today, Qibla, Adhan, Watch, Settings; location, Data Layer sync (and the watch's sync requests), watch setup | Yes |
| `shared` | Compass sensor, offline gazetteer, official timetables: code and bundled data used by both apps | Yes (Android library) |
| `alerts` | Prayer alerts shared by phone and watch: alarm scheduling, Adhan player service, notifications, sound library, imported sounds | Yes (Android library) |
| `wear` | Watch app: home, Qibla, tasbih, Hijri calendar, settings, on-watch set-up; two tiles, four complications, Ongoing Activity, Watch Face Push, settings receiver, status reporter, prayer alerts ([WATCH.md](WATCH.md)) | Yes (Wear OS 4+) |
| `legacy/watchface-v1.3.2` | The original watch face, frozen | Resources only |

Anything that must give the same answer on phone and watch lives in `core/prayer`, so both run the same code.

## Prayer times for any date (Today screen)

- The Today screen is a pager over every day from 1 January 2021 to 31 December 2035 (`DateWindow`, 5,478 days).
  Each page is calculated on demand by the engine and cached (last 96 days), so there is no stored timetable and no
  network use. Arrows step one day; the calendar jumps anywhere; "Today" returns.
- The range starts in 2021 because every date is calculated with today's conventions; for Dubai those are IACAD's
  conventions in force since 20 August 2020. Older IACAD timetables used other conventions (see `PRAYER_ACCURACY.md`).
- Time zones and daylight saving: each day is calculated in the place's zone with the offset in force at local noon;
  instants are derived with that same offset, so clock-change days are correct (tests: `DaylightSavingTest`).
- The Hijri date is the Umm al-Qura calendar (`java.time.chrono.HijrahDate`) plus the user's ±2-day adjustment. It
  matched every Hijri date in the official Dulook DXB app recording (17 dates, Apr–Dec 2026).
- Fridays show Dhuhr as Jumu'ah.
- The date picker browses by Gregorian or Hijri month (0.9.4): `HijriMonth` (core) maps each Hijri day to the Gregorian
  date on which the app shows it, with the user's adjustment; `HijriMonthTest` checks every day of 2021–2035 under
  every adjustment in both directions.

## Qibla

- **Bearing** (`core/.../qibla/Qibla.kt`): initial bearing of the shortest path on the WGS-84 ellipsoid to the Kaaba
  (21.422487° N, 39.826206° E), by Vincenty's inverse formula. The classical great-circle bearing on a sphere is
  computed too; the two differ by up to about 0.2°. Tested against GeographicLib (Karney) for 13 cities: agreement
  better than 0.001° and 10 m.
- **Edge cases**: within 5 km of the Kaaba the screen says to face the Masjid al-Haram directly; near the point
  opposite Makkah (where all directions are nearly equally short and Vincenty does not converge) it uses the
  great-circle bearing and says so.
- **Heading**: Android's rotation-vector sensor (fused magnetometer, accelerometer, gyroscope), remapped for the screen
  rotation, converted to true north with the World Magnetic Model declination (`GeomagneticField`). Fallback without
  that sensor: accelerometer + magnetometer. Smoothed as a unit vector with a 180 ms time constant (no 359°→0° swing).
  Sensors are registered only while the Qibla screen is visible.
- **Accuracy meter** (`CompassAssessor`), from what the phone actually reports:
  the rotation-vector sensor's own heading-accuracy estimate (good ≤ 10°, fair ≤ 25°), the magnetometer status
  (Android's high/medium/low/unreliable), and the measured field strength against the strength the World Magnetic
  Model expects at that place (> 15 % off: fair, > 25 %: poor, reported as interference). The worst of these is shown.
- Degrees are shown with two decimals as requested; the screen states the sensor's estimated accuracy next to them.
- Steady display (0.9.4): a new accuracy level is shown only once it has stayed the same for 1 s (0.6 s in the
  calibration tips), so a reading on a boundary cannot make the meter flicker; the estimate is shown in steps (whole
  degrees to 5°, then fives); the box keeps a fixed height. "Calibrate first" replaces "Poor" when the sensor itself
  reports it is unreliable. Three levels are kept: phones report heading accuracy too coarsely for a fourth to mean
  anything, and every level shown is one the sensor reported.

## Phone → watch installation

What Android and Google Play allow (researched November 2026):

- A phone app **cannot install** an app on a watch. Installation always goes through the Play Store, with the user
  confirming.
- When the phone and watch apps share one Play listing (same package name, which Noor uses), the phone's Play Store
  offers the watch in its Install button's device list, and the watch shows a notification offering the install.
- A phone app can open its own Play Store page **on the watch** (`RemoteActivityHelper.startRemoteActivity` with
  `market://details?id=com.noor.smartwatch`); the user taps Install on the watch.
- A phone app can open a screen of its installed watch app the same way (deep link `noor://…`).
- On Wear OS 6, a watch app can add its own watch face (Watch Face Push) and, after a one-time permission the user
  grants on the watch, make it active. Other watch faces are never changed.

What Noor does (`app/.../data/WatchSetup.kt`, Watch tab):

1. Detects watches (`NodeClient`) and which have Noor (`CapabilityClient`, capability `noor_watch_app`), live.
2. "Install on watch" opens Noor's Play Store page on that watch and shows progress until the capability appears;
   after five minutes without it, offers to open the page again. Development builds (not installed by Play) show the
   install guide instead, because the Play page does not exist yet.
3. As soon as a watch has Noor but has not confirmed receiving settings, the phone re-sends them.
4. "Use the Noor watch face" opens the watch app's face setup (`noor://face/activate`), which adds or updates the face
   and asks the user once for permission to make it active.
5. The watch reports back (`WatchReport`, Data Layer item `/noor/watch/status`): app version, Wear OS level, face
   installed/active, and when it received settings. The phone shows only what the watch has confirmed.

Still required for the one-tap Play experience: publishing on Google Play (owner's developer account).

## Phone ↔ watch sync

- The phone resolves place, zone, authority, Asr school, high-latitude rule, minute offsets, Hijri adjustment,
  language, numerals and clock format into `WatchConfig` and publishes it (`/noor/config`) whenever it changes.
- The watch stores it and calculates with the same engine, offline.
- Both formats are versioned `key=value` text: unknown keys are ignored, missing keys default, values are clamped.
- The watch can also be set up on its own (`LocalSetup` → the same `WatchConfig`); the phone's settings win unless the
  user chose the watch's own on the watch. "Sync with phone" sends `/noor/sync-request`; the phone's
  `WatchRequestService` republishes. See [WATCH.md](WATCH.md).

## Worldwide location, authorities and official timetables

Full details and sources: `AUTHORITIES.md`.

- **Precise or approximate**: Android asks the user; Noor works with either. Precise → high-accuracy fix and
  neighbourhood names; approximate → balanced-power fix and town names.
- **Names**: Android Geocoder in English and Arabic, cached while the user stays within 300 m (precise) or 3 km
  (approximate); offline fallback to the bundled GeoNames gazetteer (34,197 places). Manual mode searches the same
  gazetteer worldwide, offline, and stores the chosen place with its own time zone.
- **Authority** (`Authorities.forPlace`): one registry, used by phone and watch. Each entry records its coverage,
  category (official timetable / validated calculation / authority parameters / recognised method) and source URL.
- **Official timetables** (`authority/Timetable.kt`, `assets/timetables`): IACAD Dubai (carried days) and MUIS Singapore
  2024–2026 are used exactly as published when the user is in the authority's area; MUIS is refreshed from data.gov.sg
  (open data) at most weekly when in use. The watch receives the next eight weeks of official times in `WatchConfig`.
- **Reference points** apply only within 60 km: IACAD's city timetable for Dubai, but IACAD's conventions at the user's
  own position elsewhere.
- **Flags** (`geo/Flags.kt`): shared by phone and watch; Israel shows the Palestinian flag (product decision).

## Adhan and prayer alerts

Full details: `ALERTS.md`; sounds and credits: `SOUNDS.md`.

- Choices live in `AlertSettings` (core), stored on the phone and carried to the watch inside `WatchConfig`.
- 0.9: the Adhan plays like an alarm by default (vibrate and silent no longer stop it); every alert ends in a recorded
  outcome (`AlertLog`, the Alert history page); a failing sound falls back to the default Adhan, and when Android forbids
  Noor's player, a backup notification channel whose own sound is a short Adhan is used (played by Android itself).
  `tools/devicetest/alert_e2e.sh` runs the real alarm path on emulators in CI (`device-alerts.yml`).
- `AlertPlanner` (core, pure Kotlin) decides which alarms to arm and whether a firing alarm is still valid; both devices
  use it with the same `WatchConfig`, so alerts happen exactly at the displayed times.
- The `alerts` library does the Android side: `AlertScheduler` (AlarmManager, exact when allowed), `AlertReceiver`,
  `AdhanService` (foreground media playback with Stop), `AlertPlayer` (alarm stream, focus, gradual volume, limit),
  `AlertNotifications` (three channels), `SoundLibrary` and `CustomSounds`.
- Phone/Watch/Both: each device alerts only when chosen; phone notifications are local-only, so nothing is duplicated.
- Full Adhans and imported sounds for watch alerts travel over a Data Layer channel; the watch reports what it holds.

## Persistent prayer notification (0.9.3)

`app/notify/StatusNotification.kt`, switched on in Settings → Notification bar.

- Shows the next prayer and its time, the place and the Hijri date, and a large countdown with seconds. The seconds are
  ticked by Android (a `Chronometer` counting down inside the notification's own layout), so Noor does not run between
  updates: no foreground service, no per-second work.
- Rebuilt only when its content changes: one second after each prayer, just after midnight (the date line), and on
  any change of place, times, language, digits or clock format (the app's settings flow), on restart, app update,
  clock, time-zone and language change (`StatusRefreshReceiver`). Its own updates use non-waking alarms (`RTC`): they
  run as soon as the phone wakes, the only time the notification can be seen. A prayer alarm, which does wake the
  phone, also refreshes it (`AlertHost.onPrayerAlarm`), so it is right as the screen lights up for the Adhan.
- Its own silent, low-importance channel (`noor_status`), `CATEGORY_STATUS`, local only (not mirrored to the watch).
- Limits: on Android 14+ the user can swipe it away (it returns at the next update); the countdown's digits follow the
  phone's language, not Noor's digit setting (Android formats them); if exact alarms are not allowed, an update can come
  a little late and the countdown can briefly show a negative time until it does.
- Tested by `StatusNotificationTest` (content at fixed moments, Friday, Arabic, channel, ongoing, next update armed,
  turning off); render `55_status_notification_en.png`.

## Design system

Full description: `DESIGN.md`.

- `core/prayer`: `astro/SunPath` (the day's solar elevation curve) and `sky/SkyPalette` (sky colours by elevation, OKLab
  blending) are pure Kotlin, so the phone's sky and the watch's glow are the same colours.
- `app/ui/today/LivingSky` (0.8.0, home screen only): the layered scene (sky, stars and Milky Way, moon by Hijri day,
  clouds, sun and sun path, land by terrain, mist, birds), `SkyGeometry` (time warp and elevation scale) and the
  `Terrain` choice by country; `TodayScreen.SkyHeader` adds the drag-the-sun interaction. `app/ui/components`: `Motion`
  (timings, the ambient-motion switch), `ScreenTitle`, `PillButton`, grouped-list rows (`settings/SettingsComponents`).
- `ThemeMode.SUN` follows Shuruq and Maghrib at the current place (`MainActivity.rememberDaylight`).

## Known limitations (0.9.5)

- The 0.8.0 home screen's motion (drifting clouds, drag-the-sun, haptics, frame rate and battery) is checked only in
  code and in still, software-rendered frames; software rendering cannot show animation, real-device smoothness, or
  the exact look of soft gradients on a phone's GPU.
- Nothing in this version has been tested on a real phone or watch yet; all results above come from unit tests and
  software-rendered screens. Alerts in particular (alarm timing, playback, lock screen, Do Not Disturb, watch transfer)
  need a device test; "Test this alert" exists for that.
- Wear OS may not show a settings page for exact alarms on every watch; the watch then falls back to the app's settings
  page, and alerts there may be a few minutes late until it is allowed.
- The Play Store install path cannot be exercised until Noor is published on Google Play.
- The Hijri date follows Umm al-Qura; months decided by local moon sighting can differ by a day (user adjustment ±2).
- The watch's compass, bezel counting, haptics, own location, tiles and complications are tested only in software;
  the list of what needs a real watch is in [WATCH.md](WATCH.md#needs-a-real-watch).
