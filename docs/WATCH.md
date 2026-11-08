# Noor on the watch (0.7.0; watch face 0.9.8)

How the Wear OS app is put together, what it does on its own, how it works with the phone, and what has and has not
been verified. Written for maintainers; the user-facing install guide is [INSTALL.md](INSTALL.md).

## What the watch offers

| Surface | What it shows / does |
| --- | --- |
| **Home** (app) | Next prayer with a live countdown, the interval drawn around the edge, the sky glow of the moment, place and Hijri date, today's six times (current tinted, next outlined), prayer-alert status when alerts are on the watch, then shortcuts with live detail: Qibla (bearing), Tasbih (today's count), Hijri calendar (next event and how far), Settings |
| **Qibla** (app) | A compass for the wrist: the bezel turns with the world (cardinal letters in English or Arabic), the Kaaba sits on the rim with a beam as wide as the sensor's own uncertainty, a fixed mark at 12 o'clock. Centre: degrees to turn and "Turn right/left", or "Facing the Qibla" (green, success haptic). Hints for tilted wrist and poor calibration; a static bearing on watches without a compass; a plain message inside Makkah. Keeps the screen on for up to 3 minutes, sensors only while visible |
| **Tasbih** (app) | Whole screen is the button. Tap, or turn the bezel/crown clockwise, to count; counter-clockwise or ↶ to take one back. "After prayer" walks 33 SubhanAllah, 33 Alhamdulillah, 33 Allahu Akbar and the completing tahlil (Sahih Muslim 597); single-phrase presets (33 or 100) and a free counter (mark every 33). Haptics: tick, firmer pulse at a phrase change, celebration at a round. Day total (starts at midnight) and all-time total; stays on the watch. In English the phrase is shown transliterated with its meaning; in Arabic, in Arabic script |
| **Hijri calendar** (app) | Today's Hijri date large, Gregorian date, day of month; then Ramadan, Eid al-Fitr, Day of Arafah, Eid al-Adha, Islamic New Year, Ashura and the next White Days, each with its date and "in N days". Umm al-Qura with the user's Hijri adjustment, and a note that the actual day can follow local moon sighting |
| **Settings** (app) | Where the times come from (phone, with "updated X ago" and "Sync with phone"; or this watch, with place), the switch between phone and watch settings, watch-only settings (location, Asr, prayer alerts: on/off, sound, which prayers), the Noor watch face, "Noor for your phone" when no phone app is found, method and version |
| **Welcome / Location** (app) | First run without a phone: "Use my location" (watch GPS or the phone's position, named offline) or "Choose a city" (voice/keyboard search across ~34,000 places, plus the curated cities) |
| **Prayer times tile** | Next prayer, its time, a countdown the watch keeps current itself ("in 1 h 23 min", every minute, rounded up like the app; "Now" if the tile has not switched yet), the two prayers after it; switches at each prayer from a two-day timeline |
| **Noor tile** | Hijri date, three round shortcuts (Qibla, Tasbih, Calendar; ordered right-to-left in Arabic), today's tasbih count, "Times" edge button |
| **Complications** | Next prayer (short, long, ranged; countdown drawn by the watch face), Hijri date (short: day + month; long: full date; switches at midnight), Qibla (short: bearing; monochrome image; opens the compass), Tasbih (short: today's count; opens the counter; resets at midnight) |
| **Ongoing Activity** | While an Adhan plays on the watch, Noor's icon appears on the watch face and in Recents, one tap from the Stop button |

## The Noor watch face (0.9.8)

The watch app carries the Noor watch face and installs it with Watch Face Push (Wear OS 6 and later). It is written in
Watch Face Format and shows **Noor's own data through Noor's complications**: the next prayer (name, countdown, and a
ring that fills through the interval to it) and the Hijri date, so the face always agrees with the app (same place,
method, official timetable and adjustments). Nothing about prayer times is calculated in the face itself.

| Design | What it shows |
| --- | --- |
| Horizon | Time large in the middle; Hijri and Gregorian date above; next prayer and countdown below; the prayer ring round the edge; the night dunes of the home screen at the bottom |
| Dial | Hands and minute ticks; the prayer ring inside the ticks; Hijri date; next prayer below; two complications chosen on the watch (Noor's Qibla bearing and the battery by default) |
| Minimal | Time very large on black; next prayer and countdown in one line; a thin ring (the lightest on the battery) |
| Original | The v1.3.2 face, resources unchanged |

Each design comes in gold, emerald, turquoise, rose and silver (16 faces). On the watch's own editor (touch and hold
the face) the user can turn the seconds on or off and, on the Dial, choose its two complications. Ambient mode keeps
the time and the next prayer, dimmed, and drops the background, ring and dunes.

**Choosing a face.** On the phone: Watch page → Noor watch face: large previews, design, colour, "Show on my watch". The
phone publishes the choice (`/noor/face`, Data Layer); the watch app replaces the face in its slot in place
(`updateWatchFace`, same package and slot), so the face stays active, the user's watch-editor settings are kept, and
the change shows at once. The same choice is available on the watch (Settings → Noor watch face, or the face screen).
The watch reports the face in its slot, the face asked for, and whether it is active back to the phone.

**Making it the active face.** Wear OS lets an app do this only once, with the user's permission. The watch app asks
at most once (remembered); if the system says that chance has been used (0.9.7 asked again each time and showed "The
maximum number of attempts to set the watch face as active has been reached"), it says instead: "Added to your watch
faces. To show it, touch and hold your watch face, then choose Noor." That is a normal state, not an error.

**Keeping it current.** After the watch app is updated (`MY_PACKAGE_REPLACED`), a Noor face from an older build is
replaced in place with this build's: 0.9.7's face (the original design) becomes the Horizon face in gold, unless the
user chooses otherwise. The Horizon face in gold is also the face Wear OS adds by itself when the watch app is first
installed (`assets/default_watchface.apk`).

**Checked on Wear OS 6.0** (an emulator in CI, `watch-face.yml` and `tools/devicetest/watch_face.sh`, photographs in
the `watch-face-preview` pre-release): Wear OS adds the Horizon face by itself when the watch app is installed; the
watch app makes it the active face (after the permission); Noor's complications fill it (next prayer and countdown,
the prayer ring, the Hijri date; on the Dial the Qibla bearing and the battery); every switch between designs and
colours (eight in a row, and the original) appears on the watch in place; opening the face screen again does not ask
again. The emulator also found four faults that Google's validator does not catch, all fixed and now checked at
build time where possible: a complication fallback type not among a slot's supported types (Wear OS then refuses the
whole face), `max()` in an expression (WFF has none: the ring silently disappeared), Watch Face Push called from a
broadcast receiver (the manager then fails for every later call), and the Hijri date too long for its space.

**Building.** `tools/watchface/noor_face.py` draws the designs (XML, the dunes, hands, a digits-only subset of IBM Plex
Sans Arabic Light, the picker previews) into `tools/watchface/faces/<id>/res`, committed, and the phone editor's
pictures; `tools/watchface/build_pushed_face.py` packages all 16 under one package name, signs them with the public
development key and validates each with Google's validator at every build (about 20 s), writing the tokens for the
watch app. `WatchFacesTest` checks the phone's list of faces against the build's.

## Working without the phone

The watch is declared standalone (`com.google.android.wearable.standalone = true`): it is fully usable with no phone app.

- `LocalSetup` (core) holds the watch's own choices: place, Asr school, language/digits/clock (default: follow the
  watch), Hijri adjustment, and alerts (on/off, one sound style for all prayers, which prayers).
- `LocalSetup.toConfig()` turns them into the same `WatchConfig` the phone would send: the authority comes from
  `Authorities.forPlace` (country + region), the authority's bundled official timetable is attached when the place is in
  its area (Dubai IACAD, Singapore MUIS), and the same engine calculates. A position from the watch's own location uses
  the watch's time zone (follows travel); a chosen city keeps its own zone.
- Location: `WatchLocation` asks the fused provider for a balanced-power fix (city level is enough: one minute of
  prayer time is roughly 25 km east–west), falls back to the platform's last known fix, and names it with the offline
  gazetteer. No network geocoding; the position never leaves the watch.
- The gazetteer and timetables moved from the phone app to the `shared` Android library, so both apps bundle the same data.
- `ConfigRepository.refreshLocal()` rebuilds the watch's own configuration when the time zone or 12/24-hour setting
  changed or it is a week old (keeps the official-times window fresh). It runs on app start and on time/zone broadcasts.

## Phone and watch together

- **Which settings win.** The phone's settings are used whenever the phone has sent some (`ConfigSource.PHONE`).
  If the user set the watch up on the watch, that choice is kept even when phone settings arrive later; Settings then
  shows "Use this watch's settings" so they can switch either way. The phone's Watch page reports which one is used
  (`WatchReport.usingWatchSettings`).
- **Sync with phone.** The watch sends `/noor/sync-request` (MessageClient) to reachable nodes with the
  `noor_phone_app` capability; the phone's `WatchRequestService` republishes `WatchConfig`. The watch waits up to
  8 s for it and says "Up to date" or "Phone not reachable" (the watch keeps working either way).
- **Get the phone app.** With no phone settings and no phone app reachable, Settings offers "Noor for your phone",
  which opens the download page on the paired phone (RemoteActivityHelper).
- Status publishing to the phone is bounded by a timeout so alarms and set-up never wait on the connection.

## Code map (wear)

| File | Role |
| --- | --- |
| `ui/NoorWearNav.kt` | Navigation (`SwipeDismissableNavHost`), permissions (location, notifications), voice/keyboard search, deep links |
| `ui/WearViewModel.kt` | State and actions: config, tasbih, phone status, sync, locate, search, setup changes |
| `ui/NoorWearUi.kt` | Home screen (`HomeScreen`) and `NoorWearContent` (no-navigation entry for tests) |
| `ui/QiblaScreen.kt`, `ui/TasbihScreen.kt`, `ui/CalendarScreen.kt`, `ui/SettingsScreens.kt` | The screens; each takes plain state so tests render them directly |
| `ui/Theme.kt`, `ui/Components.kt` | Colours, fonts, screen-size scaling, haptics, shared chips and headers |
| `data/ConfigRepository.kt` | Phone config, watch setup, the choice between them, rebuilding, refreshing tiles and complications |
| `data/TasbihRepository.kt`, `data/WatchLocation.kt`, `data/PhoneLink.kt` | Counter storage, own location, phone link |
| `tile/PrayerTileService.kt`, `tile/ToolsTileService.kt` | Tiles |
| `complication/*` | Next prayer, Hijri date, Qibla, Tasbih complications |
| `NoorWearApp.kt` | App container; Ongoing Activity for the Adhan (`AlertHost.decoratePlaying`) |
| `facepush/FacePush.kt`, `facepush/FaceUpdateReceiver.kt`, `ui/FaceSetup.kt` | The Noor watch face: install, switch in place, activate once, update after an app update; the face screen and on-watch picker |

Core additions: `dhikr/Tasbih.kt` (counter state machine), `calendar/IslamicCalendar.kt` (events), `LocalSetup.kt`.

## Tests

- Core (JVM): `TasbihTest` (8: the 33/33/33/1 sequence, undo across phrase and round boundaries, free counting
  milestones, day rollover, storage format), `IslamicCalendarTest` (5: published Umm al-Qura dates for 1447, adjustment
  inverse, ordering, today, White Days rules), `LocalSetupTest` (5: round trip, same authority and zone as the phone,
  own-location zone and clock, alert mapping).
- Watch (Robolectric): `ConfigRepositoryTest` (watch setup → phone settings arriving → switching → travel → city,
  through real DataStore), `MainActivityNavTest` (real activity: welcome → set up → home → tasbih → count → back,
  count stored), `ShortcutLaunchTest` (tile/complication deep link opens the Qibla), `WearScreensTest` (every screen
  rendered at 225 dp, counting/undo/round completion through the UI, Qibla wording and alignment hysteresis, tile and
  complication content), `WearSmallScreenTest` (key screens at 192 dp).
- Renders are in `wear/build/screenshots` (CI artifact). They are software renders: the test renderer for tiles drops
  ProtoLayout's large "display" and "label large" text styles (the prayer name and the edge-button label), so the tests
  check those texts in the layout itself instead.

- 0.9.8: `FacePushTest` (5, against a stand-in for Watch Face Push: switching designs is an in-place update, never a
  second face; the activation is asked for at most once and "attempts used" becomes guidance, not an error; 0.9.7's
  face is replaced after an update), `WatchFacesTest` (core: the phone's list matches the build; the report carries
  the face), `FaceEditorTest` (phone: every face has a picture; design and colour choices).

## Needs a real watch

Not verifiable here (no emulator or device in this environment):

- Compass direction on the wrist: sign of the heading with the watch worn on either wrist, and with Wear OS's
  "crown on the left" orientation (the code applies the display rotation the system reports); calibration hints.
- Bezel/crown counting: one count per bezel click on Galaxy watches, a comfortable amount of crown turn on Pixel
  watches, and that clockwise counts up.
- Haptic feel of tick / phrase / round patterns, and that counting keeps up with fast tapping.
- Location on the watch: GPS fix time, phone-provided location while connected, permission dialog wording.
- The tile countdown rendering in the real tile renderer, and both tiles' look (the test renderer drops some styles).
- Complications on real watch faces (Samsung and Pixel faces), including midnight switching.
- Ongoing Activity icon on the watch face during an Adhan.
- "Sync with phone" round trip and "Noor for your phone" opening the browser on the phone.
- Battery: sensors only on the Qibla screen, keep-screen-on capped at 3 minutes, no periodic wakeups added.

## Not done (deliberately or for later)

- No ambient (always-on) mode for the app itself; the watch face shows the time when the screen dims.
- The tile countdown is omitted when Arabic-Indic digits are chosen (the tile renderer formats numbers its own way).
- Per-prayer sound choice and imported sounds are set on the phone; the watch's own setup offers one sound for all prayers.
- No dhikr sync to the phone (counts stay on the watch by design).

## Credits

- Watch icons for settings, location, city, search, sync, undo, more, phone, watch, bell, check, reset, vibrate and
  calendar are Google's Material icons (Apache License 2.0). The prayer-bead, Kaaba and crescent marks are Noor's own.
- Place names: GeoNames (CC BY 4.0), as on the phone.
