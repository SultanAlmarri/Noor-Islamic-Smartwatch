# The watch app redesigned (0.25): notes

22 November 2026. The owner asked for the whole product "as if it was made by Apple themselves ... the visuals and
everything", and later the same day: "You are free to do what you see fit ... criticize your decisions ... is this
what Apple would do? ... finish the job." This is the watch app's part of that. It changes how every screen of the
watch app and both tiles look, and nothing about what they compute.

**Everything below was judged on software renders** (Robolectric, 450 px and 384 px round, `wear/build/screenshots`),
**not on a watch.** Nothing here has been run on the Galaxy Watch 5 Pro or on any Wear OS device or emulator. Fonts,
colours, the round edge, touch, scrolling, haptics and the tiles' real renderer can all differ from these pictures.

## What did not change

Prayer times, the countdown's format (`1:07:35`, `7:35`), the Qibla's mathematics and its 3°/6° alignment rule, the
tasbih's counting, undo, bezel/crown gestures and haptic grammar, the calendar's dates, alerts and their permission
flow, sync, the phone↔watch formats and Data Layer paths, Watch Face Push, Minhad Studio's face, the complications
(data and code untouched), the tiles' timelines, validity and the dynamic countdown expression, navigation and deep
links, the settings' structure of 0.18, version numbers, Gradle files. No new dependency. Only `wear/` and
`docs/WATCH.md` (and this file) were edited.

## The system

| Part | Before | Now |
| --- | --- | --- |
| Rows | Stock Wear Material 3 navy pills, gold icons | A neutral dark platter with an 18 dp corner (`NoorChip`), the icon on a disc of colour (`RoundSign`), label in SemiBold, its state under it in grey |
| Main action | Tonal navy button | Gold, full width (`PrimaryButton`, or a row with `prominent = true`) |
| Switches | Stock | On a platter; green when on (`NoorSwitch`) |
| Titles | Gold, medium weight | White, SemiBold (`Header`); group labels in small tracked capitals (`Caption`) |
| Numbers | Plex Light | Plex SemiBold, tabular, much larger (`Numeral`) |
| Type | Wear's defaults over Plex Light/Regular/Medium ("SemiBold" was the Medium file) | One scale (`Type`) with the real SemiBold file, copied from the phone app (`plex_arabic_semibold.ttf`, 245 KB more in the watch APK) |
| Rings | Stock progress indicator | One drawing (`Ring`): track, gold, a bright head, gap for the clock |
| Colour | Navy surfaces | Black; platters `#1C1C1F` / `#2E2E33`; grey text `#9A9AA2` (7:1 on black, 5.8:1 on a platter) |

## Screen by screen

**Home.** Was: a small Light countdown in a ring with a faint blue glow, the place line and the first prayer row
already crowding in, then six rows and four long chips. Now the first screenful is the moment and nothing else: the
sky as it is now (blue by day, indigo with stars at night, amber at the horizon around sunrise and sunset: the
phone's own `SkyPalette`), dunes along the bottom, the next prayer's sign and name, the countdown at 48–58 sp
SemiBold (40–48 with hours), the time it is at, the interval ring, and the Hijri date and place written on the
dunes. Scroll and the sky moves away with it and fades to black. Then "TODAY" and the six times: the next one is a
gold row with dark text, the current one a raised grey row, those gone by are grey, those to come white. Then the
alert card (only when alerts are on the watch), Qibla and Tasbih as two tiles side by side showing their figure
(`258°`, `254`), the Hijri calendar row with the next event, and a round gear for Settings. The page is shorter than
before (two tiles, a row and a button in place of four chips).

**Qibla.** Bezel ticks every 5°, letters SemiBold with North in red, the Kaaba riding the rim, a faint arc from the
mark to the Kaaba showing which way to turn, the degrees at 46–56 sp, "Turn right →" / "← Turn left" with the arrow on
the side it points to in both languages. On arrival the whole screen turns to a green field, the marks turn white, a
check lands with a spring, "Facing the Qibla". Two changes of presentation that are more than style: (1) while the
wrist is tilted or the compass is starting, the figure and "Turn …" are hidden and only the instruction is shown
(before, a figure that could not be trusted stood large above the hint); with a poor calibration the figure stays
but greyed; (2) the line under the figure is now `258° · 1,633 km` (was `258° from North · 1,633 km`), so it stays
clear of the turning letters; a screen reader still hears the full sentence. The cardinal letters moved inside the
Kaaba's track so they never collide with it (in Dubai the Qibla is almost due West: they overlapped before, too).

**Tasbih.** The count at 62–76 sp SemiBold (a size down for each further digit, sized by calculation to stay between
the two buttons; only one- and two-digit counts were rendered),
the phrase in white SemiBold with its meaning in grey, the ring thicker and gliding to each count, undo and options
as grey discs. Round complete: a green disc with a check. Options: platter rows, the chosen one raised with a green
check at its end.

**Hijri calendar.** The day of the month at 52–62 sp in gold, the month and year in white SemiBold, the Gregorian
date in grey, a short bar for how far through the month. Events: platters with the number of days large in gold at
the row's end and its unit under it (new plural `days_unit`, with Arabic's forms); "Today", "Tomorrow" and "Now" stay
words.

**Settings and its pages, location, prayers.** Same structure as 0.18. Rows are platters with round signs (blue:
where the times come from; red: alerts; indigo: watch face, Hijri date, digits; teal: language; orange: Asr, sound;
green/orange/grey: the sync row by its state); new signs drawn for language, digits, clock and sound. Long labels
wrap to two lines instead of being cut ("Language and numbers"). "Use my location" is the gold row.

**Welcome.** The first screenful is whole: Minhad's mark and name on one line, one sentence, and the gold "Use my
location" row; "Choose a city" under it. Before, the action was below the fold.

**Alert card, face pages.** The alert status is a platter with the red bell, a gold "Allow" when a permission is
missing, the next alert in gold. The studio face's preview has a rim; "Show Minhad Studio" is the gold button; the
Minhad faces' designs are platter rows with a check.

**Tiles.** Prayer times: the prayer in gold, its time at 40 sp bold (29 on small watches), the live countdown under
it, the two after it in one grey line, "Today" on the edge button. Minhad: the Hijri date bold, the three shortcuts
each in its colour, today's count. The large texts are now written with explicit sizes rather than Material's
"display" tokens, which also means the test renders finally show them. Tiles cannot use Plex: ProtoLayout draws with
the watch's own font. The two pictures in the tile picker were redrawn to match.

## "Would Apple ship this?" The last round, screen by screen

Each final render was looked at with that question. What I changed because of it, and what still falls short:

| Screen | Verdict | What was fixed in the round / what still falls short |
| --- | --- | --- |
| Home, first screenful | Yes, with one reservation | Fixed: a star sat between the countdown's digits like a stray point (stars now keep out of the text; tested); the Hijri date was cut on the small watch. Short: by day the sky is one calm blue; there is no Sun or Moon in it. I left them out because a bright disc behind white figures breaks the contrast rule, and a Moon would have to be the real phase to be honest |
| Home, times and tools | Yes | Fixed: an Arabic-Indic zero alone in the Tasbih tile read as a dot (no figure until something is counted). Short: the gear has no word (it is spoken for a screen reader) |
| Qibla | Yes | Fixed: letters colliding with the Kaaba and with the text; a figure shown while the wrist is tilted; an oversize calibration hint. Short: the calibration hint (three lines) can still touch a letter while the dial is greyed |
| Tasbih | Yes | Short: nothing I can see in a render; the feel of the spring and the ring is unjudged without a watch |
| Calendar | Yes | Fixed: the month line touched the edge on the small watch |
| Settings pages | Yes | Fixed: rows without signs beside rows with them; a label cut with "…" |
| Welcome | Yes | Fixed: the action was below the fold; then the mark was hidden under the clock |
| Tiles | Not sure | The test renderer draws the three buttons as squares and cannot show the real tile font; whether the layout is as good on the watch as in the picture is unknown until it is seen there. No interval ring on the tile: it would need a second dynamic expression I cannot verify here |
| Lists at the round edge | As Wear OS does it | Rows are cut by the round screen at top and bottom while scrolling, as in every Wear OS list; Apple's square screen never has this |

## Decisions the owner may want to change

1. **Coloured round signs** in Settings, on the tools and on the Minhad tile (Apple's Settings look). If gold only is
   wanted: point the colours in `Tint` (Theme.kt) at gold; the tile's three colours are in `ToolsTileService.layout`.
2. **How dark the sky is.** White text is held to 5:1 on every sky colour (`WatchSky.MIN_CONTRAST`; WCAG asks 4.5).
   A lower figure gives a brighter, more vivid day sky and less margin; the test fails below 4.5.
3. **The first screenful shows only the next prayer.** The day's times are one turn of the crown away (before, the
   first rows peeked in under a much smaller countdown).
4. **Figures without words on the tools** (`258°`, `254`), and a gear without a word for Settings.
5. **North in red** on the compass (as on a real one), where it was gold.
6. **The Hijri date above the place** on the home screen (the place is the shorter text, and the round screen is
   narrowest at the bottom).
7. **No ambient motion** anywhere (no twinkling stars, no drifting anything): the watch's battery, and "Remove
   animations" then has nothing to turn off. The only motion is in response to a touch or a state change.
8. **The Qibla hides the figure while the wrist is tilted** (see above).
9. **245 KB** more in the watch APK for the SemiBold font.

## Tested, and how

- `:wear:testDebugUnitTest` (with the Robolectric mirror flag, through the shared Gradle lock): **39 tests, 0
  failures, 0 skipped** (34 before; five are new). Per class: WearScreensTest 9, FacePushTest 6, WearTextTest 5,
  IntervalsTest 4, WatchSkyTest 4, WearScreenshotTest 2, and one each in ComplicationTimelinesTest,
  ConfigRepositoryTest, MainActivityNavTest, OfficialRefreshTest, ReceivedSoundsTest, ReceivedStampTest,
  ShortcutLaunchTest, StudioWearTest, WearSmallScreenTest.
- New: `WatchSkyTest` (4) and `WearScreensTest.homeThroughTheDay`. 64 renders are written now (53 before): the home
  screen at six moments of the day and in Arabic (`h01`–`h09`), the day's times (`s03`), the faces' picker (`t11`).
- Assertions changed to the new truth, none removed: the home test now checks the first screenful's four texts, each
  of the six times by name and time, the bearing on the Qibla tile, what the two tiles and the gear say to a screen
  reader (the words "from North" and "Today 254" are spoken, no longer shown), and the calendar row as before;
  `MainActivityNavTest` waits for the place instead of "Fajr" (the first screenful shows only the next prayer,
  whichever it is at the real time of the run), still scrolls to Fajr, and checks the new day total through what the
  tile says. The script-mixing check passes on every screen in both languages, including the new ones.
- `:wear:assembleRelease` (R8, resource shrinking, the pushed faces packaged and validated by Google's validator as
  part of the build): succeeded; `wear-release.apk` is 12.27 MB. The APK was built, not installed anywhere.
- Each round of renders was looked at next to the baseline renders; three rounds of fixes came out of that.

## Not tested

Everything on a real watch: the sky's colours on an OLED screen; whether the sky's fade while scrolling is smooth
(it reads the list's layout each frame: cheap, but unmeasured); the size of the figures on the wrist; the press
spring; the ring gliding; the Qibla's arrival; haptics (unchanged code); both tiles in the real tile renderer
(shapes of the three buttons, the fonts, the 192 dp layout); right-to-left on the device; "Remove animations"; the
font's line metrics on a real device (the renders show Plex Arabic keeping its own room above a single line, which
is why large figures are boxed by `Numeral`; if a device differs, figures could sit a few pixels off centre).
Smoothness was not measured (`smooth.yml` measures the phone).

## Found on the way

- **Not mine, not fixed:** in `s15_qibla_ar` the system clock appears over the compass. It is in the baseline
  render too: the test changes the language while the Qibla screen is showing, and the scaffold's "no clock here" is
  lost when the theme's context is replaced. On a watch this could happen only if the phone changes the language
  while the compass is open. It needs a change outside the look (how the theme swaps its context), so I left it.
- **Not mine, not fixed:** `ToolsTileService`'s `icon(id)` call compiles with a deprecation warning (it did before).
- **Fixed:** the watch's "SemiBold" was the Medium file; several unused imports in the files I touched.
- `docs/DESIGN.md`'s short section "The watch" still describes the old look ("a soft glow ... none at night"). It is
  outside the files I may edit. Suggested text: "Same family on a black OLED screen: the same typeface in SemiBold,
  the same order (prayer, countdown, time), gold for what matters. The home screen's first screenful is the sky of
  the moment, in the phone's own sky colours darkened just enough for white text (5:1), with stars at night and
  dunes along the bottom; everything else is black with dark platters. The interval ring keeps a gap at the top for
  the system clock. See WATCH.md, 'The look of the watch app (0.25)'."
