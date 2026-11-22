# Noor on the watch (0.7.0; watch face 0.9.8)

How the Wear OS app is put together, what it does on its own, how it works with the phone, and what has and has not
been verified. Written for maintainers; the user-facing install guide is [INSTALL.md](INSTALL.md).

## What the watch offers

| Surface | What it shows / does |
| --- | --- |
| **Home** (app) | 0.25: the first screenful is the moment alone: the sky as it is now over the user's place (the phone's sky colours, stars at night, dunes along the bottom), the next prayer's sign and name, the countdown very large, the time it is at, the interval drawn round the edge, the Hijri date and the place on the dunes. Scrolling, the sky leaves with it and the page is black: today's six times (the next one a gold row, the current one raised, those gone by quiet), prayer-alert status when alerts are on the watch, then the tools: Qibla (bearing) and Tasbih (today's count) as two tiles side by side, the Hijri calendar (next event and how far), and a round Settings button |
| **Qibla** (app) | A compass for the wrist: the bezel turns with the world (cardinal letters in English or Arabic, North in red), the Kaaba rides the rim with a beam as wide as the sensor's own uncertainty, a faint arc shows the way to turn, a fixed mark at 12 o'clock. Centre: degrees to turn, large, and "Turn right/left" with an arrow on that side; on arrival the whole screen turns to a green field with a check and "Facing the Qibla" (success haptic). While the wrist is tilted or the compass is starting only the instruction is shown (no figure that cannot be trusted); with a poor calibration the figure stays, greyed, over the instruction to calibrate. A static bearing on watches without a compass; a plain message inside Makkah. Keeps the screen on for up to 3 minutes, sensors only while visible |
| **Tasbih** (app) | Whole screen is the button. Tap, or turn the bezel/crown clockwise, to count; counter-clockwise or ↶ to take one back. "After prayer" walks 33 SubhanAllah, 33 Alhamdulillah, 33 Allahu Akbar and the completing tahlil (Sahih Muslim 597); single-phrase presets (33 or 100) and a free counter (mark every 33). Haptics: tick, firmer pulse at a phrase change, celebration at a round. Day total (starts at midnight) and all-time total; stays on the watch. In English the phrase is shown transliterated with its meaning; in Arabic, in Arabic script |
| **Hijri calendar** (app) | Today's Hijri date large, Gregorian date, day of month; then Ramadan, Eid al-Fitr, Day of Arafah, Eid al-Adha, Islamic New Year, Ashura and the next White Days, each with its date and "in N days". Umm al-Qura with the user's Hijri adjustment, and a note that the actual day can follow local moon sighting |
| **Settings** (app) | 0.18: one page whose rows say their state and open the details: Watch face (Minhad Studio and the Minhad faces), where the times come from (Sync right there; the phone or this watch, place, Asr, Hijri adjustment), Prayer alerts (set here when the watch is set up on its own, otherwise what the phone chose and whether the watch is ready), Language and numbers (watch set-up only: language, digits, 12/24-hour clock), "Minhad for your phone" when no phone app is found, method and version. The crown or bezel scrolls every page |
| **Welcome / Location** (app) | First run without a phone: "Use my location" (watch GPS or the phone's position, named offline) or "Choose a city" (voice/keyboard search across ~34,000 places, plus the curated cities) |
| **Prayer times tile** | 0.25: the next prayer in gold, its time very large, under it a countdown the watch keeps current itself ("in 1 h 23 min", every minute, rounded up like the app; "Now" if the tile has not switched yet), the two prayers after it in one quiet line; switches at each prayer from a two-day timeline (the countdown expression and the timeline are unchanged) |
| **Noor tile** | Hijri date, three round shortcuts each in its colour (Qibla gold, Tasbih green, Calendar indigo; ordered right-to-left in Arabic), today's tasbih count, "Times" edge button |
| **Complications** | Next prayer (short, long, ranged; countdown drawn by the watch face), Hijri date (short: day + month; long: full date; switches at midnight), Qibla (short: bearing; monochrome image; opens the compass), Tasbih (short: today's count; opens the counter; resets at midnight) |
| **Ongoing Activity** | While an Adhan plays on the watch, Noor's icon appears on the watch face and in Recents, one tap from the Stop button |

## The look of the watch app (0.25)

The owner asked (22 November 2026) for the app "as if it was made by Apple themselves". The watch app is on one
system of its own since then, in place of stock Wear Material's navy pills; what each screen computes, its gestures
and haptics, the tiles' timelines and the complications did not change. The details, the decisions the owner may want
to change and what was and was not tested are in [WATCH_025_NOTES.md](WATCH_025_NOTES.md).

- **Black, platters, one accent.** Pages are pure black. Every row and card sits on a neutral dark platter with an
  18 dp corner (`Platter`, `PlatterShape`; `NoorChip` is the row). Gold is the accent: the next prayer, the page's
  one main action (`PrimaryButton`, or a row with `prominent = true`), the rings. Green means done (the Qibla found, a
  round complete, a switch that is on).
- **Round signs.** Settings rows and the home screen's tools carry their icon on a disc of colour, one meaning each
  (`RoundSign`, `Tint`): blue for where the times come from, red for alerts, indigo for the calendar and the face,
  green for the tasbih and a finished sync, orange for the Asr choice and sound, teal for language, grey for
  secondary things.
- **Type.** IBM Plex Sans Arabic with real SemiBold (`plex_arabic_semibold.ttf`, the phone's file), from one scale
  (`Type`). Large figures go through `Numeral`: tabular, always left to right, in a box as tall as the figures (Plex
  Arabic's own line is 1.6 times its size). Quiet labels are small tracked capitals in English (`Caption`), untracked
  in Arabic. Arabic display names stay in Reem Kufi.
- **The sky of the moment** (`WatchSky.kt`). The home screen's first screenful is drawn over the phone's own sky
  colours for the Sun's height at the user's place (`SkyPalette`), zenith to horizon, each darkened only as far as
  needed for the text (`WatchSky.readable`: 5:1 for white, the same hue and saturation), with stars as night comes
  (never among the countdown's figures) and two dunes along the bottom that end in the page's black. Nature only. No
  ambient motion: nothing on the watch app animates by itself.
- **The ring** (`Ring`). One drawing for the interval to the next prayer and a phrase of the tasbih: a track, the
  part done in gold, a bright head, a gap at the top for the clock. Plain colours (no gradient on a stroke).
- **Motion.** Things that can be pressed give a little and spring back (`pressable`); the tasbih ring glides to each
  count; the Qibla's check lands with a spring. All of it follows the watch's "Remove animations" setting, because
  Compose scales its animations by the system's animator scale.

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

**Choosing a face.** On the phone: Watch page → Noor watch face: a picture of each (0.25: said to be a sample; the
page's one large watch is the user's Minhad Studio face, drawn live), design, colour, "Show on my watch". The
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
again. The emulator also found faults that Google's validator does not catch, all fixed (the first two now checked
at build time): a complication fallback type not among a slot's supported types (Wear OS then refuses the whole face,
even when the user picks it), `max()` in an expression (WFF has no `min`/`max`: the element silently disappears), a
complication's drawing clipped to its bounding box (the ring round the edge needs the whole face as its box; found
with diagnostic builds), Watch Face Push called from a broadcast receiver (the manager then fails for every later
call), and the Hijri date too long for its space. The emulator's one activation attempt failed before the slot fix
("unknown error") and succeeded after it.

**Building.** `tools/watchface/noor_face.py` draws the designs (XML, the dunes, hands, a digits-only subset of IBM Plex
Sans Arabic Light, the picker previews) into `tools/watchface/faces/<id>/res`, committed, and the phone editor's
pictures; `tools/watchface/build_pushed_face.py` packages all 16 under one package name, signs them with the public
development key and validates each with Google's validator at every build (about 20 s), writing the tokens for the
watch app. `WatchFacesTest` checks the phone's list of faces against the build's.

## Minhad Studio: the face the user designs (0.18)

The owner asked for "a watch face editor on the phone app, unlike any app in the world": what is shown, the theme,
colour, font, seconds, the always-on look, and one tap to put it on the watch. The pushed faces above cannot do that:
each Watch Face Push face is a prebuilt APK with a validation token made at build time. So the studio face is drawn
by the watch app itself, and the phone's editor shows it with the very same drawing code.

**How it is built.**

| Part | Where | What |
| --- | --- | --- |
| The design | `core/prayer/.../face/FaceDesign.kt` | Seven places on the round face (`Slot`: top edge, above, centre, below, bottom edge, left, right), each holding one element (`Element`: time, date, weekday, Hijri date, next prayer, countdown, today's six times, prayer gauge, Sun's arc, Qibla, battery, words), with a size, a colour (the theme's accent/text/soft or one's own) and a touch action (open Minhad, Qibla, tasbih, calendar, nothing); under them hands (classic, slim, bold), marks (dots, minutes, numerals) and an edge (prayer ring or the day's dial); a theme (seven palettes or one's own colours; black, colour, gradient or night-dunes background; Plex Light/Regular/Bold or Reem Kufi); seconds; always-on (dimmed design, time and next prayer as outlines, time only, or chosen places; pixel shift; outlines); language, digits and clock (or as the app). Tolerant `key=value` format like `WatchConfig` |
| Geometry | `FaceGeometry.kt` | Where each place sits (fractions of the radius); used by the renderer, the editor's taps and drags, and the watch's touch targets |
| The data | `FaceData.kt` | What the face writes, built from the same `WatchConfig` and engine as the rest of the app (next prayer, countdown rounded up like the app, the interval, the six times, Hijri date with the user's adjustment, Qibla bearing, Sun's place), in the design's language and digits, from Minhad's own word tables (not the platform's locale data, so phone, watch and tests write the same). Rebuilt once a minute or at the next prayer, whichever is sooner |
| Templates | `FaceTemplates.kt` | Horizon, Dial, Minimal (as the pushed faces), Day dial, Timetable, Kufi, Sun; the seven palettes |
| The renderer | `face/` (new Android library, plain `android.graphics`) | `FaceRenderer.draw(canvas, bounds, design, data, frame)`: paints, paths and shaders made once; a frame allocates nothing on the watch (the strings come from `FaceData`) |
| The watch face | `wear/.../studio/StudioFaceService.kt` | `androidx.wear.watchface` 1.2.1 `WatchFaceService` with `CanvasRenderer2`; reads the design (`StudioStore`, a preferences file) and the app's configuration, redraws the moment either changes; a frame a second with seconds, every 15 s without; once a minute always-on (the system's pace), with pixel shift; battery from the sticky broadcast (no permission); touches go to the element's action |
| Sending | `app/.../ui/watch/studio/StudioRepository.kt` → `/noor/studio` → `wear/.../data/ConfigListenerService.kt` | The design and a stamp over the Data Layer; the watch stores it and reports `studioSupported`, `studioActive` and the stamp back in `WatchReport` |
| The editor | `app/.../ui/watch/studio/` | `StudioScreen` (route `watch/studio`, from the Watch page's Minhad Studio card) |

**The editor.** A large live preview (the watch's renderer, the watch's data, to the second) is the editor itself:
tap a place on the face (or its chip, for TalkBack and small places) to open what goes there, its size, colour and
touch action; touch and hold an element and drag it to another place (the two swap; a haptic tick at each place it
passes, the place under the finger lit). Undo and redo for every change; "Active / Always-on" switches the preview,
and the Always-on panel shows it by itself. Panels: Designs (templates and one's saved designs, as small faces), Style
(palettes, the three colours with a colour picker, background, font), Dial (hands, marks, edge, seconds), Always-on,
Options (language, digits, clock). One button held at the bottom, "Set on my watch", sends the design; if a watch in
reach has the studio face but is not showing it, the watch app opens on its face page there. The line above the
button says what the watch reported: showing now; on the watch but not chosen (with how to choose it); the watch
cannot run it; Minhad on the watch needs updating.

Why places and not free positions (the owner left the interaction open): on a 1.2-inch round screen free positions
make it easy to build a face that is crowded, clipped at the round edge, unreadable or hard to tap. Seven places tuned
to the round screen keep every design balanced and legible, still let anything go anywhere, and make the editor
quick: two taps to change what a place shows. Dragging stays where it helps: moving an element from one place to
another.

**Choosing the face on the watch.** Wear OS lets the user, not an app, choose an app-drawn watch face. The watch's
Settings → Watch face page shows the design live, whether it is in use, and a "Show Minhad Studio" button that asks
the system to show it with its own confirmation (`WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER`); where the system
has no such screen the page says how: touch and hold the watch face, swipe to Minhad Studio, tap it. Once chosen,
every later design shows at once.

**Which watches can run it.** Checked 16 November 2026 against Google's documents:

- "Watches that launch with Wear OS 6 only support watch faces that use the Watch Face Format"
  ([Wear OS 6 behaviour changes](https://developer.android.com/training/wearables/versions/6/changes)).
- Since 14 January 2026 legacy (AndroidX/Jetpack) faces cannot be installed from Google Play on any watch, cannot
  be updated there, and faces already installed "will continue to work"
  ([Android Developers Blog, June 2025](https://android-developers.googleblog.com/2025/06/upcoming-changes-to-wear-os-watch-faces.html);
  [Watch Face Format](https://developer.android.com/training/wearables/wff)).

So: the owner's Galaxy Watch 5 Pro (launched with Wear OS 3.5, updated since) runs it; Minhad is installed from
GitHub, not Play. A watch that launched with Wear OS 6 (Galaxy Watch 8, Pixel Watch 4) does not: there the watch app
disables the studio face at start (it reads the public `ro.product.first_api_level` property; unknown counts as an
updated watch) and says so on its face page and to the phone, whose editor then says the Minhad faces work instead. A
Play release of the watch app with this face would meet Play's legacy-face rules.

**The route that works everywhere (for later).** Google's Watch Face Push guide names exactly this use: "a phone app
that allows users to ... design and customize watch faces for installation directly on their Wear OS watch", and
offers a validator that runs on Android (`validator-push-android`) and the Pack library "for building APKs on-device,
for use with the Android-based validator" ([Watch Face Push](https://developer.android.com/training/wearables/watch-face-push)).
The route: `FaceDesign` → Watch Face Format XML → an APK built and signed on the phone or watch (Pack) → validated
there → installed and updated in place with Watch Face Push, as the pushed faces are now. The design format is kept
independent of the renderer for this. `FaceWff.kt` is a first, tested prototype of the first step (not wired to
anything): it writes a design as WFF with Minhad's complications for prayer data, and lists what WFF cannot yet show
from that data (the whole timetable, the Sun's arc, the day's dial, the dunes picture and hand pictures until they
are packed as resources). The cost: the WFF face is drawn by the system, so the phone's preview would be close to,
not the same pixels as, the watch.

**Tested here** (software, not on a watch): `FaceDesignTest` (core: every template and a design using every option
survive the trip; defaults; unknown keys and values from a newer phone ignored; label escaping; moving and swapping;
every place is found by a tap at its centre and stays inside the round face; the face's six times are the engine's
own to the minute; an Arabic face writes no Latin letter and, with Arabic-Indic digits, no Western digit, at four
times of day, for every template; an English face no Arabic letter; without a place it still tells the time),
`FaceWffTest` (core: well-formed WFF for every template), `StudioRenderTest` (phone, Robolectric: every template and
two variants drawn by the shared renderer in English and Arabic, by day and by night, active and always-on, to
`app/build/design/studio`; checks the texts actually drawn for the language rule, that no face is blank and that
always-on lights far fewer pixels), `StudioEditorTest` (phone: the editor in English and Arabic, choosing an element
through the UI, undo, each panel, the send states, the Watch page card; language check on every text and label;
renders in `app/build/screenshots`, 90–99), `StudioWearTest` (watch: the new settings pages and the face page in use,
not chosen, and unsupported, in English and Arabic).

**Needs a real watch.** That the face appears in the Galaxy Watch 5 Pro's face list and draws as the renders show; the
"Show Minhad Studio" confirmation on Samsung's One UI Watch (or only the touch-and-hold guidance); always-on look,
pixel shift and its battery cost over a day; touch actions; the redraw when a design arrives; the frame pace with
seconds; the Arabic text shaping of Reem Kufi at small sizes. The watch-face emulator workflow (`watch-face.yml`)
covers the pushed faces only; it has not been extended to the studio face.

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
| `ui/Theme.kt`, `ui/Components.kt` | Colours (`Platter`, `Tint`), the type scale (`Type`), screen-size scaling, haptics; the shared parts of 0.25: the platter row (`NoorChip`), `RoundSign`, `NoorSwitch`, `PrimaryButton`, `Numeral`, `Ring`, `Header`, `Caption` |
| `ui/WatchSky.kt` | 0.25: the sky behind the home screen: its colours for a Sun height (pure functions, tested), the stars, the dunes |
| `data/ConfigRepository.kt` | Phone config, watch setup, the choice between them, rebuilding, refreshing tiles and complications |
| `data/TasbihRepository.kt`, `data/WatchLocation.kt`, `data/PhoneLink.kt` | Counter storage, own location, phone link |
| `tile/PrayerTileService.kt`, `tile/ToolsTileService.kt` | Tiles |
| `complication/*` | Next prayer, Hijri date, Qibla, Tasbih complications |
| `NoorWearApp.kt` | App container; Ongoing Activity for the Adhan (`AlertHost.decoratePlaying`) |
| `facepush/FacePush.kt`, `facepush/FaceUpdateReceiver.kt`, `ui/FaceSetup.kt` | The Noor watch face: install, switch in place, activate once, update after an app update; the face screen and on-watch picker |
| `studio/StudioFaceService.kt`, `studio/StudioStore.kt`, `ui/StudioScreen.kt` | Minhad Studio (0.18): the face the app draws (shared renderer in the `face` module), the design from the phone, whether the watch can run it; the watch face page |
| `ui/SettingsScreens.kt` | 0.18: Settings page and its detail pages (times, alerts, language and numbers) |

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

- 0.25: `WatchSkyTest` (4, plain JVM: white text reads at 4.5:1 or better on every colour of the sky at every Sun
  height from -90° to 90° in quarter degrees, morning and evening; a sky that already reads is left as the phone has
  it; darkening keeps the hue; the sky is still blue by day, warm at the evening horizon, near black at night; no
  star among the countdown's figures). `WearScreensTest.homeThroughTheDay` renders the first screenful at six
  moments of a day and in Arabic (`h01`–`h09`), and the home test now also checks the first screenful's texts, each
  of the six times by name and time, and what the tools say to a screen reader. `StudioWearTest` renders the Minhad
  faces' picker by itself (`t11`).

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

- Watch icons for settings, location, city, search, sync, undo, more, phone, watch, bell, check, reset, vibrate,
  calendar and the arrow (0.25) are Google's Material icons (Apache License 2.0). The prayer-bead, Kaaba and crescent
  marks, and 0.25's globe, clock, number sign and speaker, are Noor's own.
- Place names: GeoNames (CC BY 4.0), as on the phone.
