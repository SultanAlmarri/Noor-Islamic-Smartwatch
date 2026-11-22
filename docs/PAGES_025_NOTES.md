# 0.25: Qibla, the Galaxy Watch page, Prayer-time accuracy and Location

An independent design review of 0.24 looked at every page of the phone app. This is the answer for four of them:
what each finding was, what was done about it (or why not), what else changed on the way, what the owner may want
to decide differently, and what was tested and how. Software renders are not a phone: nothing here has been seen on
a phone, with a real compass, or with a watch.

Code: `app/.../ui/qibla/QiblaScreen.kt`, `ui/watch/` (`WatchScreen.kt`, `WatchPreview.kt`, `FaceEditor.kt`,
`studio/StudioCard.kt`), `ui/settings/AccuracyScreen.kt`, `ui/location/LocationScreen.kt`. Strings: edited in place,
new ones in one block under `<!-- 0.25 pages -->` at the end of both `strings.xml`.

## The findings

### Qibla

**5 (must). False precision, a duplicate line, a dead band.**

- *Hundredths of a degree.* Done. What the phone's compass says is in whole degrees: "Turn right 83°", "Qibla
  direction 258°", "Phone heading 175°" (`Formatter.wholeDegrees` in `QiblaScreen.kt`; 359.6 reads 0°, never 360°).
  The Qibla's own bearing is calculated on the ellipsoid, far finer than any phone can point, and is given once,
  quietly, under the readings: "Exact Qibla direction: 258.3° from true north." (a tenth of a degree: at Dubai's
  distance a tenth is about 3 km of sideways error in where one stands, which is what a city-level place can
  honestly carry; two decimals were more than that).
- *The third reading, "Difference", is gone.* The line under the dial already says it ("Turn right 83°"), and three
  rounded figures side by side do not always add up (258 − 175 = 83, but 258.31 − 174.60 rounds to 84): shown as a
  sum, that would have looked like a mistake about a third of the time. The owner may want it back: see "Decisions".
- *"Hold your phone flat" twice.* Done. The dial says it (one line now), and the line under the dial says how
  ("Lay it face up, level, for an accurate reading."), more quietly. Until 0.24 the dial said both and the line
  repeated the first. The line keeps its height, so nothing moves when the phone is tilted.
- *The dead band in "Compass accuracy".* Done. The card is as tall as what it says, with 16 dp of padding. It was
  given room for three lines in 0.9.4 so that nothing would jump when the level changed; it is the last thing on the
  page, so nothing under it can move, and its own edge now glides to the new height (`animateContentSize`, 0.32 s)
  instead of jumping. The level itself still changes on the very next frame (`QiblaAccuracyTest`), and the meter
  still does not move sideways when the word changes.

**23 (should). The large title differed from the other tabs.** Done. The Qibla page is the shared page of every tab
(`NoorPage`): the same `LargeTitle` (36 sp Bold), the same margin (`Page.Margin`, 16 dp), and, new for this page, the
slim bar with "Qibla" once the title has scrolled away. The place and the distance ("Dubai · 1,633 km to the Kaaba")
sit close under the title as before. The dial keeps its size and its place. On a tablet or an opened fold the dial
is no wider than 420 dp (it grew with the screen until it was taller than the page).

**30 (should). The calibration sheet.** Done, and checked first as asked: on a phone it was a sheet, but Material's
own (its grabber, on the flat `surface` colour), not the app's, and without a way to close it other than dragging.
It is now the app's sheet (`NoorSheet`: the 36 × 5 dp grabber, the glass of the page), with three steps where there
were five (move away from metal and magnets; trace a figure 8; lay it flat and wait), the compass's level live under
them, and a "Done" button. The drawing is the one under the dial (a phone in the hand gliding along a figure 8; the
sheet still showed 0.22's first drawing, the phone turned along the line like a car, which the owner's reference
video had replaced on the page). It stands still when the phone's animations are off. The step "rotate the phone a
full turn while flat" is gone: the figure 8 already turns the phone about every axis.

**The dial's letters and figures.** Confirmed and fixed: they were drawn with a bare Android `Paint`, which uses the
phone's own typeface (Roboto), thickened. They are now in the app's IBM Plex Sans Arabic (SemiBold for N, E, S, W and
ش ق ج غ; Regular with tabular figures for the numbers). Nothing else about the dial changed.

### The Galaxy Watch page

**6 (must). "A development build: install the watch app with the guide."** Done. It now reads "This copy of Minhad
was not installed from the Play Store. The guide shows how to put it on your watch." It is still shown only when the
phone app did not come from Google Play (`WatchSetup.installedFromPlay`: the installer is not `com.android.vending`),
which is every build until Minhad is published. `ScreenshotTest` now also checks the other side: a copy that came
from the Play Store is never told so, and is offered "Install on watch".

**7 (must). A row whose second line repeated its first.** Done: "Works without your phone / Keeps the prayer times
with its own location" (Arabic: «تعمل من دون هاتفك / تحسب أوقات الصلاة بموقعها الخاص»).

**8 (must). A sub-page drawn as a tab.** The app was already right: `MainActivity` opens the page with a way back,
so on a phone it has the slim bar, the centred title and the gold chevron. Only the tests rendered it without
(`WatchScreen(pad)`), so the review saw a large title that no user sees. The three tests now open the page as the
app does (`onBack` given), so the renders show the truth.

**28 (should). Two different watches on one page.** Partly, and this is what is possible:

- The top of the page has **one watch, and it shows the face**: the user's own design from Minhad Studio in a drawn
  watch's case, drawn by the very renderer the watch draws it with (the `face` module), from the configuration the
  phone sends the watch. So it shows this moment's time, today's date and the next prayer, in the language of the
  app (Arabic on the Arabic page). Under it the page says "On your watch, showing now." only when the watch itself
  reports that the Studio face is the one in use; otherwise it says what the Studio is and offers "Design your watch
  face". The Studio card that stood under the old watch is this, so there are no longer two watches at the top.
- The hand-drawn copy of the watch app's home screen (the ring and the countdown "2:10:39") is gone from the page.
  It was a second thing to keep true by hand, and the watch app's home screen is being redesigned in 0.25: it would
  have shown a screen the watch no longer has.
- **What is not possible:** the ready-made faces under step 3 (Horizon, Dial, Minimal in five colours, and the
  Original) are pictures (`res/drawable-nodpi/face_*.webp`, drawn once by `tools/watchface/noor_face.py`): they show
  10:08 on Saturday 8 November, in English. The phone has no renderer for those faces (they are Watch Face Format
  files that only the watch draws), so they cannot show today's date or Arabic. They are now smaller (132 dp, a
  picture to choose by, not a second watch), and the page says what they are: "Sample picture. Your watch shows the
  time, date and next prayer of the moment." A user whose watch shows one of those faces therefore still sees, at
  the top, their Studio design and not the face on their wrist; the page does not claim otherwise (step 3 names the
  face in use: "On your watch: Horizon · Gold."). Making those pictures live would need the faces drawn a second
  time on the phone, or Arabic pictures made by the tool: both are work outside these files.
- Step 3 is now also done, and says "On your watch: Minhad Studio.", when the watch reports that the Studio face is
  in use. Until 0.24 a user showing their own Studio design still saw step 3 as a step to take.

**29 (should). "On the watch" mixed kinds of row.** Done. The list is a plain list of what Minhad puts on the watch
(the app, two tiles, four complications, the Minhad watch faces, works without your phone): none of its rows leads
anywhere and none has a chevron. The one row that does lead somewhere, "Prayer alerts on the watch" (it opens the
Adhan tab), stands in a group of its own above it.

### Prayer-time accuracy

**26 (should). A dozen cards with the same sentence.** Done. The first two cards stay (Dubai's IACAD in full, the
Emirates' Awqaf). The other 25 methods are in three grouped lists:

- **Other authorities and methods** (11): each with its own statement, because each statement is different (what it
  was checked against and how well, or what the method is).
- **Published parameters** (8: Muslim World League, Umm al-Qura, Egypt, Karachi, ISNA, Kuwait, Qatar, Tehran): names
  only, and the sentence once under the list, "Calculated with each method's published parameters."
- **Documented in references** (6: Morocco, Algeria, Tunisia, Jordan, Russia, Portugal): names only, and once under
  the list, "Calculated with the parameters documented for each authority by widely used prayer-time references; not
  yet checked against their own timetables."

Nothing the page claims has changed. The groups are not sorted by hand: a method stands in a list of names only if
its statement in `Profiles` is, word for word, the statement of the others in that list
(`accuracyGroups`, `AccuracyGroupsTest`: every method shown exactly once; a shared statement is each member's own in
both languages; the two sentences worded for several methods are still the profiles' own, and if a profile's
statement ever changes it is shown as the profile gives it). The only wording that is new is the plural of those two
sentences ("each method's", "each authority ... their own timetables").

Also on this page: every statement now follows the user's digits (only Dubai's did); in Arabic an angle keeps its
sign after its number ("18°"; after Arabic letters the sign jumped to the other side, unlike on the Qibla page and in
coordinates); in English the statements' apostrophes are typographic. These are set on the page; the texts in `core`
are untouched. "Data sources and licences" no longer sends the reader to "docs/AUTHORITIES.md in the Minhad
repository" but to "Minhad's documentation" (the same place, in words a person uses).

### Location

**36 (polish).**

- *Coordinates under the city.* Done. "Prayer times are for" shows the place and where it lies (or "Chosen by you"),
  not five decimals. Coordinates remain where a point is chosen or confirmed: the coordinates' own dialog, the map's
  card, the confirmation.
- *The back chevron 4 dp further right.* Done, in the one place that causes it: `NavBar` in
  `ui/components/Page.kt` (a shared file: see "Outside my files"). It now stands 4 dp from the screen's edge, exactly
  where `NoorPage` puts it.
- *Dividers inset on both sides.* Done: the four ways to choose are the rows of every other grouped list
  (`SettingsItem`, `SettingsDivider`: from under the text to the card's edge), and the two separators in the lists
  of countries and areas run from under the rows' text too.
- *The Arabic attribution.* Done, with one remark. Each run of Latin letters now stands in an isolate (U+2066 …
  U+2069), so "GeoNames (geonames.org)" reads as one name left to right (it was split in two by a mark and shown as
  "(geonames.org) GeoNames"), and "CC BY 4.0" cannot break in the middle (it did on a 360 dp phone). The full stop
  after "CC BY 4.0" is still drawn at its left: in a right-to-left sentence that *is* after it, as the full stop of
  every Arabic sentence is. Moving it to the right would put it before the licence's name for an Arabic reader.

### 37 (polish). Apostrophes and ellipses

Done in these pages' strings: typographic apostrophes in the Studio's ("Today's times", "Sun's arc", "The day's
dial") and in "Data sources and licences"; ellipses were already "…". The straight apostrophes in the methods'
statements come from `core` and are set typographically on the page.

## Beyond the list (two rounds of looking at every render and asking "would Apple ship this?")

- **Qibla, no compass.** The page for a phone without a compass says "Use the bearing below with a separate
  compass": the exact bearing under the readings is what such a user needs.
- **Qibla.** The compass's "unsure" line is a button for TalkBack too; the calibration steps' numbers follow the
  user's digits; the gap that the folded figure-8 card left between the line and the readings is gone.
- **Watch.** The watch's sign in the watch card and the empty card is the white sign on its graphite tile of every
  list (it was gold on gold: gold is for what can be acted on). The page is no longer composed again every second
  (it was, for the old countdown; the face at the top redraws its own seconds without composing). Cards on the
  8-point grid (16 dp, corners 24 dp as the other grouped lists). The "Design your watch face" button is tonal, so
  the page's one filled button is the set-up step that waits ("Get Galaxy Wearable", "Install on watch", "Show on my
  watch"). One word for the face in Arabic («واجهة»; one string said «وجه»).
- **Location.** The confirmation's two buttons were two boxes side by side of a shape no other page has: now the
  app's buttons, "Use this location" filled and full width, "Adjust on the map" quiet under it (the map's "Use this
  location" too). What is chosen now ("Use my current location") carries the gold check of every chosen thing (it
  was a green dot), and TalkBack says "Chosen". The time zone on the confirmation: its name on the line, its offset
  with the small print under it (on one line they broke after the dot between them).

## Decisions the owner may want to change

1. **"Difference" removed from the Qibla readings** (two readings instead of three). To bring it back: one line in
   `QiblaContent`, and the string `qibla_difference` (removed) again.
2. **The exact bearing to a tenth** ("258.3°") instead of a hundredth, and kept at all. It is one quiet line; it can
   go, or carry two decimals.
3. **The watch page's one watch is the Studio design**, not a copy of the watch app's screen. Someone who never
   opens the Studio sees its first design there with "Design your watch face".
4. **Step 3 counts the Studio face as "Minhad watch face" in use.**
5. **The accuracy page's three headings** ("Other authorities and methods", "Published parameters", "Documented in
   references") are new words; the sentences under them are the profiles' own.
6. **The wording for a build that is not from the Play Store.** After Minhad is published it will be seen only by
   someone who installs the file by hand.
7. **"Rotate the phone a full turn while flat" is no longer a calibration step.**

## Outside my files

- `ui/components/Page.kt`, `NavBar`: the back chevron's offset, −8 dp → −12 dp (one number and its comment), so the
  chevron of pages that lay themselves out stands where `NoorPage` puts it. This also moves the chevron of the Adhan
  pages that use `BackHeader` (it is the same `NavBar`) by the same 4 dp, to the same place.
- `ScreenshotTest.kt`, `design/PagesDesignTest.kt`: only what renders or checks these pages (below).
- `docs/DESIGN.md` (two bullets under "Components"), `docs/LOCATION.md` (one), `docs/WATCH.md` (one sentence).
- `values/strings_face.xml`: three apostrophes (the Studio's strings).

## Tests

Changed, each keeping its meaning:

- `ScreenshotTest`: the watch page is rendered as the app opens it (with the way back, and the Studio's first design
  at its top); the calibration guide on a stand-in with the sheet's grabber; it waits for the watch page's
  configuration itself instead of for the old mock's "Dubai"; "development build" → "not installed from the Play
  Store". New checks: the turn, the Qibla and the heading are in whole degrees and no hundredths are shown; the
  exact bearing is given once; "Hold your phone flat" is said once; a Play Store install is never told it is not
  one. The licence name in the language check may have fixed spaces.
- `PagesDesignTest`: the watch page with the way back; the same licence name.
- New: `QiblaDegreesTest` (2), `AccuracyGroupsTest` (3).

Run on the cloud machine, through the shared Gradle lock, `--no-parallel`, with the Robolectric mirror:

- The whole `:app:testDebugUnitTest`, once, on the finished code: **139 tests in 65 classes, 0 failures, 0 errors,
  0 skipped** (15 minutes).
- While working, three times (a first attempt did not compile the tests), the tests of these pages: `ScreenshotTest`, `PagesDesignTest`,
  `QiblaCalibrateDesignTest`, `QiblaAccuracyTest`, `QiblaDegreesTest`, `AccuracyGroupsTest`, the location tests
  (`LocationFlowTest`, `LocationSmallScreenTest`, `LocationWideScreenTest`, `TileMapTest`) and the watch tests
  (`FaceEditorTest`, `StudioEditorTest`, `StudioRenderTest`): 16 tests in 13 classes. One of those runs failed, and
  rightly: a line height read from a text style that has none stopped the Qibla page ("Only Sp can convert to Px")
  in three tests; fixed, and the next run and the whole suite passed.
- Not run: `:core:prayer:test`, `:alerts`, `:wear` (nothing there was touched), the release builds, and anything on
  an emulator.

Every render of these pages was looked at after each round: English and Arabic, night and day, 412 dp and 360 dp
wide, text at 1.5 (`PagesDesignTest`, `QiblaCalibrateDesignTest`), and the location pages at 360 × 640 and 840 dp.

## Not tested

- Nothing on a phone or a watch. The compass, the haptic tap, the sheet's drag, TalkBack, the watch's reports
  (`studioActive`, `faceActive`) and the live face at the top of the watch page have only been rendered by
  Robolectric with made-up readings and reports.
- The glide of the accuracy card's height and the fold of the figure-8 card were not watched in motion (the renders
  are stills).
- The watch page on a phone where Minhad Studio cannot run (a watch that came with Wear OS 6): the top still shows
  the Studio design and its invitation, as the Studio card did before; the editor then explains that it cannot run
  there. Not rendered in that state.

## Second review

A second independent review judged the app against the owner's standard ("as if Apple made it"). These are its findings
on the location pages, the accuracy page and the Qibla page, by its numbers. Software renders (Robolectric), not a
phone: nothing here has been seen on a phone or with a real compass.

Code: `ui/location/` (`LocationScreen.kt`, `LocationViewModel.kt`, new `PlaceText.kt`), `ui/settings/AccuracyScreen.kt`,
`ui/qibla/QiblaScreen.kt`. Strings: edited in place, new ones at the end of both `strings.xml` under
`<!-- 0.25 review 2: pages -->`; `accuracy_note_published` and `accuracy_note_documented` are gone (no longer used).

**6. The accuracy page was a documentation dump.** Done. At the top, the method the user's times are calculated with
(gold), "Used for your prayer times", and where this project has measured it against the authority's own times, two
large figures and one line saying against what: for Dubai "89.6 % to the minute", "1 min largest difference", "588
official IACAD times, Aug 2020 – Nov 2026" (`docs/PRAYER_ACCURACY.md`: 527 of 588 exact, all 61 others exactly 1
minute). Seven methods have measured figures, each as its own statement gives them (`MEASURED`): IACAD, Awqaf (96.2 %,
1 min, the UAE calendar for Abu Dhabi), Diyanet (64 %, 2 min, Istanbul), JAKIM (58–67 %, 94 % within 1 minute, 4
zones), Kemenag (93–94 %, 1 min, three cities), DUMK (90–94 %, 1 min, 13 cities), the Takvim (56 %, 2 min, Sarajevo).
Every other method shows no number: its name, its phrase and its statement. Singapore has none at the top on purpose:
its table is used as published, and its other dates' figure is the statement's "about 75 %". Under the card, every
method (27) is one row: its name and a phrase ("Checked against its own tables", "Official timetable", "Recognised
method", "Published parameters", "Documented in references"); a tap opens the statement the page gave before, word for
word (Dubai: its paragraph; the methods that shared a sentence: each profile's own sentence, of which the page showed a
plural rewording). The phrase is derived from the statement's own words (`accuracyStatus`), never set by hand. One title
style, 17 sp SemiBold; gold only for the method in use. "Data sources and licences" stays at the foot.
Tests: `AccuracyGroupsTest` (4: every method once; a shared phrase only for the very same statement; every phrase is
the one its statement earns, with a table of today's; every figure at the top stands in that method's statement, in
both languages) and the new `AccuracyStatementsTest` (the page in English and Arabic, every row opened: each statement
appears; IACAD's figures and source at the top, every number of the source in IACAD's paragraph).

**7. Raw romanisation and "District" under every row.** Done. In English, names that GeoNames romanises from the Arabic
script (the Arab League's countries, Iran, Afghanistan) are shown in plain letters: "Abu Hayl", "Ad Daghayah",
"Al Usbij", "Ras al Khaymah" (`PlaceText.en`). Names elsewhere keep their letters, which are their language's own (São
Paulo, Zürich, İstanbul; Russian and Chinese names whose ’ is a soft sign or a syllable mark): decided by the place's
country, not by the marks. Display only: what is saved and the gazetteer are untouched; the filters of a country, a
region and a city's areas match both forms, and the search box also finds a name typed as shown ("Al Usbij", whose
list spelling is "Al ‘Usbij": `PlaceText.search` adds the places that contain the whole query after searching its
longest word). The areas list has one heading, "Districts and places", and no "District" under each row (the search
results keep it: there it tells a district from a town). A region says "7 places"; "Largest: …" only where the largest
place is not the region's own name ("Eastern Province · Largest: Dammam"). A point chosen on the map or by coordinates
that took its name from a district is confirmed under its city: "Makkah", "Harat al Bab wa ash Shamiyah, Saudi Arabia".
A place picked from the list keeps its own name as the title (the user chose that district; its city is under it), and
the name saved is the one saved before. Accepted limit, as asked: a place with no Arabic name shows its Latin name on
the Arabic page (now in plain letters).

**8. The licence footnote on the location page.** Done: removed; it is in Settings → About Minhad → Acknowledgements
(the strings are kept, they are used there). The map's own attribution stays under the map.

**15. The location pages on the Fold.** Already capped at 640 dp in 0.9.7: the review read 770 dp from a 1,680 px render
at 2 px per dp; the rows measure 608 dp (640 less the margins). What was not right: the bar was inside the narrow
column, so the chevron stood 240 dp in from the screen's edge. Every location page but the map now has the bar across
the screen (the chevron where every page has it) and the column of `Page.MaxWidth` under it, centred (`Pane`).

**16. The map's controls.** Done. Back is the app's gold chevron and the search is the app's pill field, together on
one pane of glass floating over the picture; the offline message is a card of the bottom panel's glass with a
cloud-off sign (it was a black banner). The round buttons keep their place, with the panels' lit edge. **The zoom
buttons stay**: the map zooms in with a double tap but has no one-finger way to zoom out (and TalkBack users cannot
pinch), so without them the map could not be used with one hand.

**19. The time zone.** Done: "Arabian Standard Time", and "UTC+3 · No daylight saving time here." under it ("UTC+5:30",
"UTC+5:45", "UTC-3:30" where the offset has minutes); in Arabic «توقيت …» and «غرينتش +3», in the user's digits. If
Android has no name for a zone in that language, the line is the offset alone.

**21. The Kaaba's sign in the light theme.** Done: white on the day's deep gold and deep green (`kaabaGlyph`; dark on
the night's light gold and green, as before). `QiblaKaabaContrastTest` measures it: at least 3:1 on both discs in both
themes. By day on the green the white sign has 5.9:1 where the black one had 3.1:1. On the day's gold disc the numbers
go the other way (white 3.9:1, black 4.8:1): white was chosen there too, as the brief asked, so that the sign is one
colour by day whether the phone is turning or facing the Qibla; both pass. The owner may prefer the dark sign on gold.

**22. "Calibrate" three times when the compass is unsure.** Done. The line under the dial gives its place to the card,
whose title is "Calibrate the compass", with the figure 8 beside it (the drawing is smaller, beside the words, so the
card is about a row tall) and "Move your phone in a figure 8" under the title. The accuracy card ("Compass accuracy ·
Poor") comes straight under it while the compass is unsure, and leaves out its own "needs calibrating: tap for how"
then; when the compass is sure the page is as before (the card at the foot). `ScreenshotTest` now checks that
"Calibrate the compass" is said once and that the accuracy card ends above an 82 dp tab bar and a 24 dp gesture bar on
a 915 dp phone.

With the largest common text size (1.5) the card's words take three or four lines beside the drawing; nothing is cut,
but it is taller (render `qibla_calibrate_large_text_en`). Not seen on a phone in motion.

**23 (part). The dot left at a line's end.** Done: "place · distance" on one line when it fits, else the distance on
its own line without the dot (`PlaceAndDistance`).

**29 (part). No compass.** Done: the readings card shows the Qibla direction alone.

### Decisions the owner may want to change

1. The accuracy page's figures for methods other than Dubai's (Awqaf, Diyanet, JAKIM, Kemenag, DUMK, the Takvim): each
   is its statement's own figure, but only Dubai's was named in the brief.
2. The phrases of the rows, and "Recognised method" for the Moonsighting Committee and Karachi with the Hanafi Asr.
3. A point named after a district leads with its city on the confirmation, a district picked from the list does not.
   The home of the location page and the Today screen still show the saved name (the district, its city under it).
4. Plain letters for the Maghreb's French names too ("Fes" for "Fès"), since the rule goes by country.
5. Arabic writes the offset «غرينتش +3» (Greenwich), the name people use, rather than a Latin "UTC".

### Outside my files

- `ScreenshotTest.kt`: two checks after the unsure-compass render (finding 22).
- `docs/LOCATION.md`: one paragraph on how names and the time zone are shown.

### Tests

Run on the cloud machine through the shared Gradle lock, `--no-parallel`, with the Robolectric mirror, on the finished
code: `ScreenshotTest`, `PagesDesignTest`, `QiblaCalibrateDesignTest`, `ui.location.*` (`LocationFlowTest`,
`LocationSmallScreenTest`, `LocationWideScreenTest`, `TileMapTest`, new `PlaceTextTest`), `ui.qibla.*`
(`QiblaAccuracyTest`, `QiblaDegreesTest`, new `QiblaKaabaContrastTest`), `AccuracyGroupsTest`, new
`AccuracyStatementsTest`, `ThemeContrastTest`, `ButtonContrastTest`: **26 tests in 15 classes, 0 failures, 0
errors, 0 skipped**. Earlier rounds while working failed, rightly, three times: a "UTC+3" with a left-to-right mark inside
it in English, a check that took the apostrophe of "Today’s" for a transliteration mark, and a check that took two
methods sharing one statement for a row already open; each fixed (the first in the code, the two others in the tests).
Changed, each keeping its meaning: `LocationFlowTest` (it waits for "Arabian Standard Time" and "Gulf Standard Time"
where it waited for "Asia/Riyadh" and "Asia/Dubai", and opens Ras Al Khaimah by its row, whose subtitle is now "122
places"; new checks: no zone id and "UTC+3", the Kaaba's coordinates under "Makkah" in plain letters, one heading over
the areas, no "District" under them, "Abu Hayl"); `AccuracyGroupsTest` (to the page's new shape, above). Not run: the
whole suite (the lead runs it), `:core`, the release builds, anything on an emulator or a phone.
