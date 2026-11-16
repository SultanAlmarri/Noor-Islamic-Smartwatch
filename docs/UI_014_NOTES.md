# The interface of 0.14: notes for the documents

A draft for the lead to fold into `DESIGN.md`, `HANDOVER.md` and `ARCHITECTURE.md`. Branch `ui/014`, phone app only
(`app`). Nothing in `core/prayer`, `alerts`, `shared`, `wear`, `legacy`, `tools`, the sky's renderer
(`app/.../sky`, `assets/sky2`, `res/raw/*.agsl`) or the signing was touched. No version number was changed.

What the owner asked (16 November 2026): an opening animation "as soon as I open the app"; the interface made
"amazing" while keeping the Apple look he liked, more intuitive; a hunt for bugs; "very close to releasing".
He lifted rule 5 for this: every phone screen may change except the Qibla screen's design and the sky.

## 1. The design, in one page

One system for every page instead of a look per screen (`ui/components/Page.kt`, `ui/settings/SettingsComponents.kt`,
`ui/adhan/AdhanComponents.kt`):

- **Pages** (`NoorPage`). A tab's page opens on a large title (34 sp, bold) that scrolls away with the content; once
  it has gone under the top of the screen a slim bar carries the title. A page pushed from a tab has the slim bar
  from the start: a back chevron in gold (mirrored in Arabic, named "Back" for TalkBack, 48 dp to touch) and the
  title centred; the bar turns solid, with a hairline, only once something has scrolled under it.
- **Width**. Lists and cards never grow wider than 640 dp and sit in the middle, so a foldable's inner screen or a
  tablet shows a calm column, not stretched rows (the owner's phone is a Samsung Fold). The tab bar's five items keep
  together in the middle the same way.
- **Grid**. One margin (16 dp), groups 24 dp apart, rows at least 56 dp high, 8-point steps.
- **Grouped lists**. A quiet title over a rounded group, the same icon tile in every list of the app (the location
  page had its own round ones), dividers aligned with the text, and a footer under a group wherever its rows need a
  sentence. A row is one of four kinds, and looks it: a link (chevron), a choice (its value at the end of the row
  before the chevron, "Numerals   Western ›"), a switch (the whole row is the switch, one thing for TalkBack), or a
  row holding a control (the stepper). With large text a choice's value moves under its title instead of being
  squeezed beside it.
- **One accent**. Gold is what can be acted on or is chosen: back, links in a sentence, the chosen row, the main
  button. The segmented control no longer fills its chosen segment with gold: as on iOS it is a quiet track with a
  raised thumb that slides (spring) to the chosen segment.
- **Choices open in a sheet from the bottom**, where the thumb is, instead of a dialog in the middle of the screen
  (`ChoiceDialog` kept its name and callers; a label's second line is shown as its small print, which the authority
  list uses). Personal adjustments is a sheet too, with steppers, Reset and Done.
- **Controls**. Noor's switch keeps one thumb size on and off (gold track on, grey track off). The stepper is one
  capsule (− | +), each half 48 × 44 dp and named for TalkBack. One button (`NoorButton`: filled, tonal or plain;
  50 dp, corners 14 dp). One search field (a filled capsule with a clear button). Chips are always one line that
  scrolls sideways past the page's edges (`ChoiceChips`), never a ragged wrapped block.
- **Type**. Titles 34/17 sp; rows 16 sp; small print 13 sp/18 (was 12 sp) for what stands under a row's title and
  under a group. The Qibla screen keeps its own sizes.
- **Motion**. Tabs cross-fade; a pushed page comes in from the side one reads towards (from the left in Arabic: it
  came from the right in both languages); the tab bar's chosen icon settles with a small spring; the segmented
  thumb slides; "Today" pops in on the panel's edge.
- **Haptics** (a light tick): changing tab (as before), any switch row, the segmented control, picking a choice in a
  sheet, the stepper, the kinds of alert.

## 2. Screen by screen

| Screen | What changed | Why |
|---|---|---|
| **Tab bar** | Icon and name only, gold for the one on show; no pill behind the icon; 58 dp; centred group on wide screens; rises into place during the opening | iOS's tab bar; less drawn, more content |
| **Today** | A bell at the end of each time says what will happen then (Adhan or sound, quiet notification, nothing); touching a row opens that prayer's alert. The place capsule opens the location page directly (it opened Settings). "Today" (another date shown) floats on the panel's upper edge instead of pushing the times down. With large text the date takes its short form. The header's parts and the rows take part in the opening. The sky, the panel's position and its glass are untouched | Turning a prayer's Adhan on or off was three screens away from the home screen; a short phone lost Isha when "Today" appeared |
| **Adhan** | Order of use: the one switch with what happens next; "Needs your attention" as a group of its own, there only when something is wrong (each row is its fix); the prayers, each with its switch, the row opening its sound; the reminder; phone / watch / both; how the sound behaves; the library; the history. The battery advice left the warnings (nearly every phone has it "on") and sits with the history | The warnings were stacked inside the master card, four deep, before anything one could do |
| **A prayer's alert** | The six kinds of alert as tiles, three to a row, all visible, with icons (four tiles in a 2 × 2 block for Shuruq); sounds in titled groups with their notes as footers; volume, vibration and length as one group; the test as a full-width button | The chips wrapped into a ragged second line; notes floated between cards |
| **Adhan library** | Slim bar; search field; one line of filters that scrolls; the list; the alert sounds | The filters took three lines |
| **My sounds, Alert history, Accuracy, Home scenery** | The same page, groups and buttons. Scenery's two switches and the sky quality list are groups like any other | One system |
| **Watch** | The same page and buttons; steps in the same type | One system |
| **Settings** | Groups in the order they are needed: Location (the row names the place, as the home screen does) · Prayer times · Prayer alerts · Notification bar · Appearance (theme, scenery) · Language and numbers · Calendar · Watch · About. Short choices at the end of their rows. Search unchanged in what it finds (it now also matches a row's value at the end). A separate group can be added after About by giving its rows a group title of their own (see the comment at the end of the row list in `SettingsScreen.kt`) | "Location mode" said nothing; language and appearance were one long group |
| **Location** | The slim bar, the app's search field and icon tiles. Its flow is as it was | One system; the flow was redesigned in 0.9.7 and works |
| **Welcome (first run)** | Scrolls when it must (short screen, large text); words keep a readable width on a wide screen; two dots show the step; the step survives the permission dialog | The buttons could be cut off; see the bugs |
| **Date picker** | The Gregorian / Hijri switch is the app's segmented control (48 dp to touch); the sheet has the app's grabber and corners | One control |
| **Qibla** | Design unchanged (pixel-compared with 0.13: only the accuracy card's chevron differs). Fixes only: that chevron now mirrors in Arabic; the place prompt and the card are buttons for TalkBack; "Qibla needs a location" opens the location page directly | The owner likes it as it is |

Strings added (English and Arabic): `back`, `decrease`, `increase`, `settings_appearance`, `settings_language`,
`adhan_attention`, `adhan_prayers_footer`, `adhan_master_off_here`, `adhan_turn_on`, `today_row_alert`,
`today_row_open`, `opening_skip`, `size_megabytes`. No string was removed (`adhan_master_off_short` and
`settings_location_mode` are no longer shown).

## 3. The opening: first light

Code: `ui/opening/Opening.kt` (the timetable `Opening`, the clock `OpeningState`, the veil `OpeningVeil`, the modifier
`arrives`), `MainActivity` (the hand-over from the launch screen), `TodayScreen.SkyHeader` (the sky's sweep), the
theme `Theme.Noor.Splash`.

What one sees, on a cold start with the home screen:

1. **The launch screen** (Android draws it before the app runs): dusk blue with Noor's crescent and star. Its colour
   is now the sky's own waiting colour (`RealSky.WAITING_SKY`, `#26355C`; `@color/noor_dusk`), not near-black.
   It stays until the app's first real frame exists, so there is no blank page between.
2. **The hand-over**: the app draws the same dusk and the same mark where the launch screen had it (it asks the
   launch screen where its icon was), and removes the launch screen: no cut.
3. **Light gathers** round the mark (0 to about 0.5 s), the mark dissolves into it, and the light leaves for the
   place of the Sun in the sky behind (the middle of the sky at night).
4. **The dusk opens** from there like an iris with a soft edge (0.3 to 1.3 s), onto the home screen, where meanwhile
   the sky's own sweep of 0.9.6 runs: the scene comes in, the Sun runs from the start of the current prayer's time
   to this moment and settles (1.5 to 1.9 s, by how far it has to run).
5. **The home screen settles**: the place, the prayer and the countdown rise into the sky one after another (from
   0.5 s, settled by about 1.3 s); the date and the day's times come up one after another (from 0.76 s, the last
   settled by about 1.6 s); the tab bar rises.

All of it is over in **2.26 s at most** (`Opening.FULL_MS`; `OpeningTest.itIsShort` keeps it between 1.5 and 2.5 s).
The prayer, its time and the countdown are in the page from the first frame (TalkBack has them at once); they can be
read from about one second in.

- **A touch anywhere** finishes it: the rest runs at eight times the speed (a quarter of a second), so nothing cuts.
  Once the dusk has opened (1.3 s) touches reach the home screen as usual, and dragging the Sun takes the sky over
  as before.
- **"Remove animations"**: nothing moves; the launch screen is followed at once by the home screen.
- **Cold and warm**: a cold start (Noor was not running) plays it in full, every time. The activity being rebuilt in
  the same process (language, theme) plays nothing. Noor brought back from the background plays nothing, unless it
  was away for 15 minutes or more and was on the home screen: then the home screen comes in again without the veil
  (the Sun runs to now; the place, countdown and times settle, half a second earlier than under the veil, so the
  times are all there within about 1.1 s; the scene itself does not fade), since by then the sky has moved on.
  `Opening.RETURN_AFTER_MS` is the one number to change, or set it very large to turn the return off.
- **First run**: the welcome page has its own sunrise, so there the launch screen only dissolves (0.46 s).
- The 0.9.6 rule "in full only if none was seen for half an hour, otherwise a brief fade" is gone, with its stored
  time (`noor_ui` / `opening_full_at` is no longer read or written).

## 4. Bugs found and fixed

1. **"Choose a city instead" on the welcome page never reached the location page.** The app's start page was read
   from the settings on every recomposition; recording the welcome page as done changed it, the navigation library
   rebuilt the whole graph on the home screen, and the user landed on "Prayer times begin with where you are"
   instead of the list of ways to choose. The start page is now decided once. `MainFlowTest` starts the real
   activity and fails on the old code (checked by putting the old line back).
2. **A preview kept playing** after its page was left by a tab, by a link, or by the home button (a four-minute
   Adhan with no stop button in sight). It now stops when the page stops (`StopPreviewOnLeave`).
3. **"Next on this phone" went stale.** It was worked out only when settings changed; with the tabs' saved state the
   page could show a prayer that had passed hours ago. It is now worked out again when the page comes back and once
   the alert it names has come.
4. **Background location could not be turned on in Settings on Android 8 and 9**: the switch asked for a permission
   that does not exist there and did nothing. It now simply turns on (the welcome page already did).
5. **The home screen worked out every row of the day's times again every second** (18 rows with the neighbouring
   days, the date bar and the place): a new formatter object each second made them all look changed. It is kept
   now, so only the countdown and the status lines change. (Reasoned from the code; not measured on a phone.)
6. **A late answer could land under the wrong confirmation** on the location pages: choosing a place, going back and
   choosing another while the first was still being named (a slow geocoder) could show the first under the second's
   confirmation. Only the place asked for last is resolved now.
7. **The whole activity was rebuilt when a foldable was opened or closed, and when Android's dark mode changed**
   (`smallestScreenSize` and `uiMode` were not in `configChanges`): the location flow went back to its first page,
   open sheets closed. Compose now simply lays out again.
8. **Mirroring in Arabic**: pages pushed from a tab came in from the right in both languages; the Qibla accuracy
   card's chevron pointed right in both.
9. **TalkBack**: every back arrow, the map's back button, the location search's clear button and the stepper's − and +
   were unnamed buttons; switches were read without their row's title; the countdown was read digit by digit (it is
   one thing now, read as a time); the Qibla's two cards were not announced as buttons.
10. **Touch targets under 48 dp**: the calendar's Gregorian / Hijri switch (36 dp), play buttons (40 dp), the map's
    round buttons (46 dp), the welcome page's second button (40 dp), the place capsule on short screens (36 dp, now
    40 in a 40 dp row: the header has no more height there).
11. **"MB" in Latin letters on the Arabic "My sounds" page**, with a number written by the phone's locale rather
    than by Noor's digits setting.
12. **The welcome page's step was lost** if Android rebuilt the activity behind its permission dialog, and its
    buttons could be cut off on a short screen or with large text (it did not scroll).
13. **"Today" pushed the day's times down** when another date was shown; on a short phone Isha left the screen.
14. **Dead ends**: "Set a location first" on the Adhan page had no way to the location page; "Mark at least two
    Adhans as favourites" had no way to the library; a prayer's alert page said "turn them on in the Adhan tab" when
    all alerts were off. The first two rows now lead there, and the third has the switch on the page itself (and asks
    for the notification permission, as the Adhan tab does).
15. **State lost when the activity is rebuilt**: the open sheet in Settings, the two choice sheets on the Adhan
    page, the open recording in the library, the "replace" target in My sounds.
16. **Status bar icons** were dark on the dark launch-to-app hand-over for a frame on a phone in light mode; the bars
    now start light and are set per page.
17. **"Remove animations" was read once** per activity; changed while Noor was in the background, it was not seen
    until the activity was rebuilt. It is read again each time Noor comes back.

## 5. Found and not fixed

- **The location flow's pages are not saved** across a rebuild of the activity (`LocationScreen`'s stack is plain
  `remember`). With fix 7 the usual causes are gone; the rest (a font-size change, process death) still go back to
  its first page. Saving it means making the steps parcelable.
- **The location pages keep their own buttons and section labels** (the same look, not yet the shared components).
- **A place's name with no Arabic** in the gazetteer still shows in Latin letters (known since 0.9.1).
- **Dialogs and sheets are not in the renders**: they open in their own window, which the window capture cannot see.
  The choice sheet, the adjustments sheet and the recording's sheet are therefore **not seen at all** here.
- **If a place cannot be resolved** (the time-zone map or the gazetteer failing to load) the location view model has
  no error path; I found no way to reach it and left it: guessing a zone would be worse than failing.

## 6. What was tested, and how

Everything below is Robolectric on this machine (software renders and the app's real code on a simulated phone).
**Nothing was run on a phone or an emulator.** Motion, smoothness, haptics, the hand-over from Android's launch
screen and the real sky during the opening have not been seen at all.

- `ScreenshotTest` (62 pictures now, 57 before: the scenery page in Arabic, Settings by day, Settings with large
  text, a prayer's alert page with all alerts off in both languages) with its language check on every picture,
  unchanged in strictness.
- `TodayOpeningDesignTest`: frames through the opening as the app frames it (veil, home screen, tab bar): 0, 250,
  450, 650, 850, 1050, 1300, 1700 ms and the end; asserts that the prayer, its time and the countdown are in the
  page at the first frame, that the veil has gone by 1.3 s, that the opening is over within its time, that all six
  times then fit above the tab bar; then the same opening touched at 250 ms is over within half a second more; then
  a return (no veil). Also asserts that the launch screen's colour is `RealSky.WAITING_SKY`.
- `OpeningTest` (plain JUnit, new): when it plays (once per process; never with animations off; the launch screen
  only dissolves on a first run; a return only after 15 minutes and only on the home screen), that it is short, that
  every part runs 0 → 1 once and never backwards, that the veil leaves, that a touch asks for the end, that the sky
  sweeps once per opening.
- `MainFlowTest` (new): the real `MainActivity` from a first run to the location page (bug 1).
- `TodayDesignTest`, `TodaySmallDesignTest`, `TodayTinyDesignTest`: the home screen through the day and on small
  phones, all six times fitting, with the new rows.
- The Qibla pictures were compared pixel by pixel with 0.13's: identical but for the chevron (11 pixels in English,
  mirrored in Arabic).

Not tested anywhere: TalkBack itself (only the semantics it reads were checked in code), the sheets, the opening on
Android below 12 (the compatibility launch screen), a foldable.

## 7. What the opening needs from the sky

Nothing was built on the sky's side. As it is, the opening uses only what was there: `RealSky.WAITING_SKY` as the
colour of the launch screen and the veil, the `opening()` value the home screen already hands to `LivingSky`
(0 → 1: the scene's fade and the path drawing itself) and the sweep of `shown`.

Asked for, in order of use:

1. **A sign that the scene's first picture is on the screen**, readable from Compose: for instance
   `RealSky.pictureShown: State<Boolean>`, set true on the main thread when the engine first draws a scene after
   `attach` (where it logs "Pictures read"), false again on `detach`. With it the opening would hold the dusk closed
   (for at most about 0.6 s more) until there is a picture to open onto, and start the Sun's run from that moment.
   Without it, on a cold start the dusk may open onto the waiting colour and the scene comes in by the engine's own
   ramp a little later, with part of the Sun's run already over. The colours match, so nothing flashes; it is only
   less than it could be. The very first run after an install (shaders being prepared) is the worst case.
2. **`WAITING_SKY` and `@color/noor_dusk` must stay the same colour** (the test says so if they part).
3. **On a return** (no veil) the home screen leaves `opening()` at 1 and only runs `shown` from the start of the
   current prayer's time to now with `scrubbing = true`, exactly as a drag does. If the real sky should do something
   else then (nothing, or a shorter run), `SkyHeader`'s `LaunchedEffect(app.run)` is the place.
4. The veil's light goes to the Sun's place as `SkyGeometry` gives it (with `SunLift`); if the renderer draws the
   Sun elsewhere, the home screen can be told (`OpeningState.focus`, window pixels).
