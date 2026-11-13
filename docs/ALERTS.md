# Adhan and prayer alerts

Version 0.9.0. How Noor alerts at prayer times on the phone and the watch, which Android permissions it uses and why,
and what has and has not been verified.

## What the user can choose

| Choice | Where | Notes |
| --- | --- | --- |
| Alerts on/off | Adhan tab | Off until the user turns it on. Turning it on asks for the notification permission (Android 13+). |
| Alert on: Phone / Watch / Both | Adhan tab | The chosen device alerts; the other stays quiet. With "Both", each device posts its own alert and the phone's notification is local-only, so the watch never shows it twice. |
| Per prayer: on/off | Adhan tab | Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. Sunrise is off by default. |
| Per prayer: type | Prayer page | Adhan (full), Short Adhan, Sound, My sound, Notification, Silent. Sunrise never offers the Adhan. |
| Per prayer: sound | Prayer page | Fajr lists Fajr Adhans first; a Fajr Adhan is never offered for other prayers. Choosing a non-Fajr Adhan for Fajr shows a note. |
| Per prayer: volume, vibration, stop after | Prayer page | Volume is a percentage of the alarm volume on a perceptual curve. Stop after 30 s, 1, 2 or 3 min, or play to the end (with a short fade). |
| Reminder before each prayer (0.9.12) | Adhan tab | Off (default), 5, 10, 15, 20 or 30 minutes before. A notification such as "Asr in 15 minutes" (before sunrise: "Fajr time ends in 10 minutes") with the prayer's time and the place, on its own channel `noor_reminder` (the device's notification sound; follows Do Not Disturb like any notification). It comes for every prayer whose alert is on, on the device that alerts (both with "Both"), and disappears when the prayer begins. |
| Gradual volume | Adhan tab | Off, 5, 10 (default), 20, 30 or 60 seconds. |
| Play in silent mode and Do Not Disturb | Adhan tab | **On by default since 0.9**: the Adhan plays on the alarm stream like an alarm clock, whether the phone is on vibrate, silent or not; under Do Not Disturb the system's own rule for alarms decides (if that rule blocks alarms, Noor says so instead of "playing" silently). Off: while the device is silent, on vibrate or in Do Not Disturb, the alert becomes a silent notification (vibrate-only when the ringer is on vibrate). Up to 0.8 this was off by default, which silenced the Adhan on every phone kept on vibrate: the main cause of "the notification comes but no Adhan". |
| Test this alert | Prayer page, Alert history | Arms a real alarm 10 s ahead and runs the whole path (alarm → receiver → player service → notification). This is the way to check alerts on a device. |
| Alert history | Adhan tab | The last 40 alerts on this phone and what actually happened to each: played (and for how long), stopped by the user, replaced by the default Adhan, played by the backup channel, quiet and why, late. "Share report" sends it with the phone's alert set-up as plain text. |
| Random from favourites | Prayer page (Adhan or Short Adhan) | Rotates through the Adhans marked ♡ in the library: each plays once before any repeats, never the same twice in a row; Fajr rotates its own (Fajr) recordings; for the full Adhan, favourites that exist only as an opening are skipped. Decided from the date and prayer (`RandomAdhan`), so phone and watch always pick the same; the watch receives every favourite it may need. Alert history names the recording that played. |
| Adhan library | Adhan tab | 18 recordings from 9 countries: search by place, country, mosque or reciter; filter by region, Fajr or favourites; preview at once (tapping another switches, to compare voices); ♡ favourites; "About this recording" with notes, both versions and the full credit. |
| My sounds | Adhan tab | Import MP3, M4A, AAC or WAV (also OGG, Opus, FLAC) up to 40 MB; rename, replace (keeps the id, so prayers using it keep working), delete (prayers using it move to the default Adhan). |

## How it works

```
core/prayer   AlertSettings (choices, wire format) · AlertPlanner (which alarms; is a firing alarm still valid?)
alerts        AlertScheduler · AlertReceiver · SystemEventsReceiver · AdhanService · AlertPlayer
              AlertNotifications · SoundLibrary · CustomSounds          (Android library used by phone and watch)
app           Adhan screens, settings storage, WatchSoundSender
wear          AlertSection (permissions on the watch), ReceivedSounds
```

- **Same times everywhere.** Alarms are planned from `WatchConfig`, the object the screens, the tile and the
  complication use, including official timetables. `AlertPlanner` is pure Kotlin and unit-tested (DST included).
- **Scheduling.** One alarm per prayer (its next occurrence) with `setExactAndAllowWhileIdle`; without the exact-alarm
  permission, `setAndAllowWhileIdle` (may be late) and the Adhan tab says so. Re-armed after every alarm, on settings,
  location or timetable changes, after a restart, an app update, a clock or time-zone change, and when the exact-alarm
  permission is granted.
- **Firing.** The receiver re-checks the alarm against current times and choices: on time → alert; more than 10 minutes
  late (device off or in deep sleep) → a quiet "the time began at …" notification instead of a late Adhan; moved,
  turned off or moved to the other device → nothing.
- **Reminders (0.9.12).** `AlertPlanner.reminders` gives each alerting prayer's next reminder; `AlertScheduler` arms
  one plain exact alarm per prayer for it (not an alarm-clock alarm, so Android's "next alarm" stays the Adhan; inexact
  without the exact-alarm permission), re-armed with the prayer alarms. A reminder that would come before the previous
  prayer has begun (short gaps at high latitudes or with large personal offsets) is left out. When it fires,
  `AlertPlanner.onRemind` re-checks it: turned off, times moved later, or under a minute left → nothing; a little
  late → it states the minutes really left. Reminders are not recorded in Alert history (they are only a
  notification). Stored as `pre=<minutes>` in `AlertSettings`, written only when set, so watches up to 0.9.11 ignore
  it and settings without a reminder encode exactly as before.
- **Never silently failing (0.9).** Every alert ends in one recorded outcome (`AlertLog`, shown as Alert history). If
  the chosen sound cannot be played, Noor's default short Adhan plays instead and the notification says so. If Android
  refuses to let Noor start its player at all (Android 12+ forbids starting it from the background without the
  "Alarms & reminders" permission), the **backup channel** `noor_adhan_backup` is used: its own sound is the short
  Makkah Adhan (`alerts/res/raw/noor_backup_adhan.ogg`, byte-identical to the credited library file, checked by
  `BackupSoundTest`), which Android's notification system plays itself on the alarm stream, so no permission is needed.
  The player no longer gives up when another app keeps audio focus, and a player that is not ready within 8 s counts
  as a failure rather than hanging in silence.
- **Playing.** A foreground service (`mediaPlayback`) plays on the alarm stream with audio focus (music pauses; a call
  stops the Adhan), a wake lock while playing, the gradual rise, the optional limit with fade, and a notification with
  **Stop** (dismissing it stops too). Nothing ever plays without a visible Stop: if notifications are not allowed,
  nothing plays.
- **Substitutions are always stated.** If the chosen sound cannot be used (an imported file was removed, a recording has
  only its opening, a full recording has not reached the watch yet), the notification says what played instead.
- **Watch.** The watch carries the short Adhans and the alert sounds. A full Adhan or an imported sound chosen for a
  prayer that alerts on the watch is sent from the phone over a Data Layer channel (`/noor/sound/<id>/<ext>`) once;
  the watch reports what it holds (`WatchReport.sounds`) and removes files no longer used. The watch also reports
  whether it may post notifications and set exact alarms; the phone shows that in the Adhan tab.

### Android 17 and audio started from the background (0.9.7)

Android 17 restricts audio that an app starts while it is not visible: focus can be refused and playback silenced
unless the app is running a suitable foreground service or the user can see it. The owner's Fold (Android 17) logged
every scheduled Adhan as "played 0 s" while tests played. Since 0.9.7:

- Prayer alarms are **alarm-clock alarms** (`AlarmManager.setAlarmClock`, `AlertHost.alarmClockAlarms`, phone): shown to
  the user as the next alarm, exempt from Doze and from the background-start limits.
- The Adhan notification has a **full-screen alert** (`AdhanAlertActivity`: over the lock screen, turns the screen on,
  large Stop) where Android allows it (`USE_FULL_SCREEN_INTENT`; on Android 14+ the user can turn this off). The
  playing notification must not use `setSilent()`: that makes it a "silent" group child, for which Android suppresses
  heads-up and full-screen alerts (the Adhan channel has no sound of its own; `setOnlyAlertOnce` is used instead).
- `AdhanService` starts the sound only once Android reports it in the foreground (it waits up to 1 s).
- `AlertPlayer` no longer stops when audio focus is lost or refused: only a phone call stops the Adhan. If playback ends
  early or the player fails, it resumes where it was (at most three times). The history records why it ended.

`AdhanPlaybackTest` covers the alarm type and the focus and call rules; `tools/devicetest/alert_e2e.sh` has `scheduled`
and `scheduled-hardened` scenarios that let the real prayer alarm fire (device clock moved to 45 s before it), the latter
with Android 17's hardening at its strictest (`cmd audio set-enable-hardening enable`).

## Permissions

| Permission | Why | If refused |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Show alerts and the Stop button | No alert can appear or sound; the Adhan tab shows "Allow". |
| `SCHEDULE_EXACT_ALARM` | Alert at the exact minute; start the Adhan from the alarm | Alerts use inexact alarms and may be minutes late; on Android 12+ the Adhan may be unable to start from the background, and the notification says so. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep the Adhan playing with the screen off | — (granted at install) |
| `RECEIVE_BOOT_COMPLETED` | Re-arm after a restart | — |
| `VIBRATE`, `WAKE_LOCK` | Vibration; keep playing with the screen off | — |

Not requested, deliberately: `USE_EXACT_ALARM` (Google Play reserves it for alarm-clock and calendar apps),
`USE_FULL_SCREEN_INTENT` (restricted to calling and alarm apps on Android 14+), `ACCESS_NOTIFICATION_POLICY`
(Noor never changes Do Not Disturb), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (the tab links to the system's battery page
instead). A user who force-stops Noor cancels its alarms until Noor is opened again; that is Android's rule.

## Sounds

Eighteen Adhan recordings from Wikimedia Commons (CC0, public domain, CC BY 3.0, CC BY-SA 3.0 and 4.0) and fourteen
original alert sounds. Credits, licences, provenance and the exact edits: [SOUNDS.md](SOUNDS.md). Pipeline: `tools/sounds/README.md`.

## Verified so far

| What | How | Result |
| --- | --- | --- |
| Planning, firing decisions, DST, settings format | `core/prayer` unit tests (`AlertPlannerTest`) | Pass |
| Alarms armed and cancelled, inexact fallback, quiet reasons, notification channels and text (English, Arabic, Jumu'ah) | `alerts` Robolectric tests | Pass |
| Sound library: every file present, credited, allowed licence; defaults exist; Fajr rules | `app` `BundledDataTest`, `alerts` `SoundLibraryTest` | Pass |
| Imported sounds: import, reject non-audio, rename, replace, delete, survive restart | `alerts` `SoundLibraryTest` (audio check replaced by a test probe) | Pass |
| Screens | Software-rendered screenshots (phone 26–35, watch w05–w06) | Rendered |
| Fallback chain, backup channel, history, vibrate no longer silencing the Adhan | `alerts` `AlertReliabilityTest`, `AlertSchedulerTest` (Robolectric) | Pass |
| Alarm → Adhan on a real Android system, with Noor's process killed, the screen off and the device in Doze: normal, on vibrate, and without exact alarms (backup channel) | `tools/devicetest/alert_e2e.sh` on Android 14 and 15 emulators (`.github/workflows/device-alerts.yml`); passes only if Android itself reports Noor playing on the alarm stream for at least 25 s | See the workflow's latest run |
| Real playback heard on a phone, a phone maker's battery manager, lock screen, watch transfer | Needs a real phone and watch | **Not yet tested** (see "Testing on a phone") |

The audio decoding check for imported files (`MediaMetadataRetriever`) cannot run under Robolectric, so it is only
exercised on a device.

## Testing on a phone

Emulators prove the Android side; only a phone proves that a person hears the Adhan, and that the phone maker's battery
manager (Samsung's "sleeping apps", for example) lets the alarm through. The steps, for anyone:

1. Install the latest `Noor-phone.apk`, open Noor, go to **Adhan**, turn **Prayer alerts** on and press every **Allow**
   the screen shows (notifications, "Alarms & reminders").
2. Open **Alert history** and press **Test the Adhan in 10 seconds**, then lock the phone straight away.
3. The Adhan should start within about 10 seconds, with the screen off. Let it play a little, then press **Stop**.
4. Repeat with the phone on **vibrate**: the Adhan must still play (unless "Play in silent mode and Do Not Disturb" was
   turned off on purpose).
5. Leave the phone alone until the next real prayer time. Afterwards, Alert history shows what happened, on time or late.
6. If anything did not sound, press **Share report** and send the report: it lists each alert, what Android allowed and
   what Noor did, which is enough to find the cause without developer tools.

With a computer and `adb`, `tools/devicetest/alert_e2e.sh` (debug build) runs the same scenarios as on the emulators.

