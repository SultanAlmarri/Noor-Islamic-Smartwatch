# The Adhan and alarms area in 0.25

22 November 2026, a helper on `worktree-agent-a1091400f9faf8892`, for the lead to merge. The owner: "make this
application as if it was made by Apple themselves ... criticize yourself ... finish the job. Really finish the job."
An independent reviewer looked at the renders of 0.24 and wrote of this area: "organised around the app's problems and
options, not the person. A wall of four warnings repeats on both segments, the same choice appears in two different
UIs, the hero's switch is ambiguous, and there is a 'More settings' drawer."

This file says what was done about each finding, what else changed, what the owner may want otherwise, and what was
tested. Nothing here has been run on a phone: every picture is a software render (Robolectric), and what the pages do
when touched was checked by reading the code and by the tests named below, not on a device.

## What the area is now

- **The tab is "Adhan" and so is its page.** Two pages under one control: **Prayers | Alarms**.
- **Prayers**, top to bottom: the one switch, always called **Prayer alerts**, with one line under it ("Next: Fajr
  5:20 AM", or "Off", or in red "Will not sound"); **one line with one button** for whatever needs fixing, only when
  something does; **All prayers**: the Adhan's voice (one row, opening the voices) and the volume (one slider); the six
  prayers with today's times and their switches; then **Test the Adhan in 10 seconds**, **Alert history**, and
  **Sound and devices** (what is set once: phone, watch or both; gradual volume; silent mode; the library; my sounds).
- **Alarms**: the same single line if something would stop or delay an alarm; "Next alarm in 7 h 37 min"; the alarms in
  one list; Add alarm.
- **An alarm's editor** has Cancel and Save in its bar and nothing else to leave by.
- **The ringing screen** shows the time, what is sounding, and Stop, Snooze and "Hide and keep playing" at the foot.

## The reviewer's findings

| # | Finding | Done? | What was done |
|---|---|---|---|
| 2 (must) | A wall of four warnings on the Adhan tab, repeated on Alarms | Done | `FixCard` (AdhanScreen.kt): one row. One thing wrong: its sentence and a gold **Fix** that does it. Several: "4 things to fix" and **Fix** opens a sheet that lists them, the most serious first, each with its own action (the rows of 0.24, `WarningRow`); a thing fixed leaves the list when the app comes back, and the row leaves with the last. Each page shows only what affects it: Prayers what would stop or delay the prayers' alerts (and a watch's), Alarms what would stop or delay an alarm (there titled "Alarms will not ring"). The four paragraphs took about 220 dp; the row takes 60. |
| 3 (must) | Dark segmented control: the chosen segment almost invisible; a second, gold-filled style on the Home screen page | Done, with other numbers | One control (`Segmented`, AdhanComponents.kt) everywhere: the Adhan tab, Sound and devices, an alarm's sound, the calendar picker (its call is unchanged), and both controls of Settings → Home screen (`SceneryScreen.kt`, which had its own). At night the track is white at 7% and the chosen segment white at 15%, lit along its top edge (0.5 dp) with a soft shadow drawn outside it; labels 15 sp, the chosen one SemiBold in the primary ink, the others in the secondary. **Not the reviewer's 8% and 20%**: measured, the chosen label on a 20% thumb fell to 3.98:1 on a sheet under a bright morning sky, below the 4.5:1 that is the rule here; 15% on 7% passes everywhere (chosen label 4.59:1 at worst, the others 4.67:1) and the thumb's luminance stands 1.47 to 1 against the track at worst (it was 1 to 1). By day the track and thumb are as they were (the reviewer: "already right"); the labels of the segments not chosen are now in the primary ink too (the grey gave 2.54:1 at the foot of a page at night in the light theme). `SegmentedContrastTest` locks all of it. |
| 4 (must) | The hero's switch has no name when on; "See below" is a pointer | Done | Always "Prayer alerts" (20 sp SemiBold; `HeroCard` got a `titleSize`), with "Next: Fajr 5:20 AM", "Off", "On your watch", or in the danger colour "Will not sound". What to do about it is the row directly under the card. |
| 9 (must) | The alarm editor's wheel unevenly built; three exits; the bar's fade cutting a row | Done, weights differ | The row of names over the wheels is gone; the units stand in the band beside the numbers ("0 h", "25 min", then "before", "Fajr"; `Wheel` got a `unit`). Cancel (start) and Save (end, SemiBold) in the page's bar, in the accent; no chevron, no bottom bar (`NoorPage` got `barStart`/`barEnd`); Android's Back is Cancel. Gutters of 10 dp, widths 0.7 : 1 : 1.35 : 1.5, not the 1 : 1 : 1.3 : 1.3 with 12 dp asked for: on a 360 dp phone that leaves the prayer's wheel 71 dp, and "Maghrib" at the band's size needs 76. Each wheel is as wide as what it holds. |
| 12 | Stock Material slider | Done | `NoorSlider`: a 4 dp track, gold up to the value, a 24 dp round white thumb with a soft shadow, no stop dot; it runs from the right in Arabic. A volume is one line: its sign, the slider, the figure (`VolumeRow`), on the tab and on a prayer's page. |
| 13 | The same choice in two UIs (six voice tiles on the tab, and the voices page) | Done | The tiles are gone. Under **All prayers** one row, "Adhan voice · Makkah ›" ("Mixed" when the prayers differ), opens the voices page, which says **All prayers** over its list. A touch on a voice chooses it for every prayer and plays its opening, as the tiles did (a second touch stops it); the round button still plays the whole recording. "Al-Quds / Add a recording" has a plus at its end. The owner's "one voice for every prayer at once" is one tap from the tab, as before. |
| 14 | Controls stay lit when their switch is off | Done | `OffWhen`: what hangs on a switch that is off is shown at 38%, takes no touch and is not read out by TalkBack (the page still scrolls with a finger on it). On a prayer's page: everything under "Alert at Fajr" (it used to vanish when the prayer's own alert was off and stay lit when all alerts were off; now the page keeps its shape either way). On the tab with all alerts off: the voice and the volume. The six prayers stay reachable while the alerts are off, as 0.15 decided (a prayer's switch turned on then turns the alerts on for that prayer alone), but look off: dim, grey signs, switches off. |
| 15 | Alert type as a 3+2 grid of unequal tiles | Done | One list of five rows (four for sunrise): sign, name, what it is in three words, a gold check on the chosen one. "Birds, rain…" already was a real ellipsis (U+2026) in both languages; the app's typeface draws it as three full stops on the baseline. |
| 16 | Ringing screen: no time, an empty lower half, "Keep playing" | Done | The time now (64 sp Light, AM/PM at half size) at the top under the sign, what is sounding under it (20 sp), then the prayer's time and place; Stop, an alarm's Snooze and "Hide and keep playing" on the foot of the screen. The time uses the digits and the 12 or 24 hours chosen in Minhad and turns with the minute. Both states (an alarm, the Adhan) are rendered. The Arabic-Indic digits in the reviewer's Arabic render were typed into the test; the app writes these texts with the digits chosen in Minhad (Western at first). The test's strings now have Western digits. |
| 27 | Alarms list: a card per alarm; AM/PM (and "ص") at full size | Done | One group with hairlines. The time 40 sp Light with its marker at half size (`ClockText`), also in the editor (46 sp) and, at the list's size, on the prayers' rows. |
| 31 | Alert history leads with a test button | Done | The page opens on the history; Share report under it. The test is on the Adhan tab only (one step back), with the line that stood under the history's button ("Lock the phone after pressing it."). |
| 38 | Names: tab "Adhan", page "Adhan & alerts", segment "Adhan"; "More settings" | Done | Page "Adhan"; segments "Prayers" and "Alarms"; "More settings" is "Sound and devices" (الصوت والأجهزة), and the history moved out of it to the first page, beside the test. Settings' search still lists the tab as "Adhan & alerts" (`adhan_title`, used only there). |
| 37 | Typographic apostrophes and ellipses | Done in this area's strings | "Phone’s alarm sound", "Phone’s ringtone", "Phone’s usual sound". No "..." was found. **Not changed**: "The Prophet's Mosque" on the library page has a straight apostrophe; it comes from `assets/sounds/library.json`, which `tools/sounds/build_library.py` writes (not mine to edit). |

## Other things changed

- **The prayers' signs** are white on tiles like every other sign since 0.25, each in the colour of its hour (Fajr
  indigo, Shuruq orange, Dhuhr gold, Asr blue, Maghrib pink, Isha purple; grey when it will not alert). They were gold
  outlines on gold tint, beside six gold switches.
- **The days** (a prayer's, an alarm's): a day that is on is a ring of the accent round a tinted disc. Seven solid gold
  discs were the loudest thing on those pages.
- **"Add a recording" rows, voices and sounds everywhere**: a touch chooses and plays (as an alarm's sounds already
  did); the round button stops what is playing of that row instead of starting it again from the top.
- **Random** says what it is on the voices page ("A different voice each time"; the string existed).
- **The battery advice** ("If an alert comes late, set Minhad’s battery use to Unrestricted") moved with the history
  to the foot of the history page, where lateness is seen.
- **The history's row** on the tab is the row alone; under it stood the last alert's whole sentence, three lines when
  something had gone wrong.
- **Fix**: on a voice with a Fajr recording the page's round button, pressed while the opening played, started the
  whole recording instead of stopping.

## Edits outside the Adhan files (each the smallest that would do)

- `ui/components/Page.kt`, `NoorPage`: two optional slots, `barStart` and `barEnd`, drawn in the bar in place of the
  back chevron and at its other end. A page with either has the slim bar (not the large title). Nothing changes for a
  page that does not pass them. Why: Cancel and Save in the alarm editor's bar.
- `ui/components/Hero.kt`, `HeroCard`: an optional `titleSize` (24 sp as before when not given). Why: "Prayer alerts"
  beside a medallion and a switch broke into two lines at 24 sp on a 360 dp phone; the reviewer asked for 20.
- `ui/components/Wheel.kt` (named as mine): `unit`; the words no longer grow past a tenth with the phone's text size.
- `ui/settings/SceneryScreen.kt` (named as mine for this): both of its controls are `Segmented` now.
- Tests: `ScreenshotTest` and `PagesDesignTest` look for the new texts where the old ones are gone (listed below).

## Strings

Edited in place: `adhan_hero_blocked` ("Will not sound"), `adhan_alert_options` ("Sound and devices"),
`adhan_screen_close` ("Hide and keep playing"), `alarm_before_word` and `alarm_after_word` (lower case: they are read in
the band as part of a sentence), the three apostrophes. Added at the end of both files under `<!-- 0.25 adhan -->`:
`adhan_hero_next_at`, `adhan_all_prayers`, `adhan_fix`, the plural `adhan_fix_count`, `alarms_fix_blocked`,
`alarm_warn_fullscreen`, `alarm_warn_volume` (the two warnings worded for alarms: they said "the Adhan" on the alarms'
page), `alarm_unit_hours`, `alarm_unit_minutes`. No string was removed. These are no longer used by any code and can go
when the lead likes: `adhan_hero_next`, `adhan_section_adhan`, `adhan_attention`, `voice_all_title`, `history_intro`,
`history_row_empty`. (`alarm_hours`, `alarm_minutes_head` and `alarm_prayer` are still used: they are TalkBack's names
for the wheels.)

## Something found on the way, outside this area

`SegmentedContrastTest` measures the control on the bare page as well as on glass. In the **light theme at night** the
foot of a page is dusky enough that the secondary grey directly on it gives 2.54:1. `ThemeContrastTest` guarantees text
on the glass only; section titles and footers, which stand on the bare page in the muted grey, are not covered by it
and are likely below 4.5:1 near the foot of a long page at night in the light theme (the bare page itself was not
measured: 2.54:1 is on the control's grey track there). Not changed here (`ui/theme` and
`SettingsComponents.kt` are shared); for the lead to judge. Also for the lead: DESIGN.md's 0.25 section still says
"on the Adhan tab's 'Needs your attention', the action of each row is a capsule": those rows are now in the Fix sheet.

## Decisions the owner may want otherwise

1. **Cancel and Save at the top of the alarm editor**, as on an iPhone. 0.17 put them in a bar at the foot "as in the
   Clock app" (Samsung's), within the thumb's reach. The reviewer allowed either; the top was chosen because the bar at
   the foot always covered a row. Going back to the foot is a small change (`AlarmScreen.kt`).
2. **A voice is chosen on the voices page, one tap from the tab**, not on the tab itself (the six tiles). One tap more
   than 0.18 for someone who changes the voice often; in exchange the tab's first screen holds the switch, the fix, the
   voice, the volume and the first prayers.
3. **The prayers' signs in six colours.** If the owner finds it too colourful, one line (`prayerTint`) makes them all
   gold again.
4. **With the alerts off, a prayer's page shows its settings dimmed** and they cannot be changed until its switch is
   on. Until 0.24 they could be set first (and looked on). The six prayers on the tab are still reachable and their
   switches still work while the alerts are off.
5. **"Sound and devices"** as the name of the page of what is set once.
6. **The test is only on the Adhan tab**, no longer also at the head of the history.

## Tests

All through Gradle on the cloud machine (`:app:testDebugUnitTest`, Robolectric with SDK 34 for the renders). Unit
tests and software renders only.

- **The whole of `:app:testDebugUnitTest`: 65 test classes, 136 tests, 0 failures, 0 errors, 0 skipped** (16 min 58 s).
  It ran on the final code but for one line changed after it (the Fix sheet stays open while one thing of several is
  left). After that line, the five classes this work touches were run again and pass: `ScreenshotTest` (1),
  `PagesDesignTest` (1), `AdhanDesignTest` (1), `SegmentedContrastTest` (1), `ThemeContrastTest` (5).
- **New: `SegmentedContrastTest`** (plain JVM): the chosen label on the thumb and the other labels on the track, on
  the bare page, on a card's glass and on a sheet, for every degree of the Sun's height from −90° to 90°, morning and
  evening, at eleven heights of the page, in both themes: all at 4.5:1 or better; and at night the thumb's luminance
  at least 1.3 to 1 against the track. Worst figures it printed: night, chosen label 4.59:1, other labels 4.67:1,
  thumb against track 1.47; day, other labels 6.84:1, chosen label 17.57:1, thumb against track 1.23 (a white thumb
  on a pale track, lifted by its shadow, as before).
- **New: `AdhanDesignTest`** (`app/build/design/adhan`, 65 pictures at 360 dp): the tab with the alerts off, on with
  four things in the way, with one, with one that only delays, and with nothing in the way; the alarms' page in the
  same states; the things to fix as the sheet lists them; a prayer's page on, off, and with all alerts off; the
  voices for every prayer and for Fajr; Sound and devices; a new alarm, an alarm being edited, an alarm's sounds; the
  history; Settings → Home screen (the segmented control); the ringing screen for an alarm and for the Adhan. In
  English, Arabic, light, and with large text (1.3). It asserts only that pictures were written: it is for looking.
  The "nothing in the way" and "one thing" states are made by putting Android's answers straight into the page's state,
  because Robolectric 4.14 cannot be told that the Adhan's screen may show over the lock screen.
- **Changed assertions** (the meaning kept, the text or the order new):
  - `ScreenshotTest`: 26 waits for "Prayer alerts" (was "Adhan voice for every prayer": with the alerts off that
    group is now dimmed and hidden from the semantics the test reads); 27 waits for "Adhan voice", which is there only
    with the alerts on; "More settings" → "Sound and devices" (27/83, 32/84 in Arabic, 34); going back from the alarms'
    page in Arabic clicks "الصلوات" (was "الأذان"); the ringing screen gets a clock ("4:47") and its Arabic texts are
    typed with Western digits, as the app writes them when its digits are as first set.
  - `PagesDesignTest`: clicks "Sound and devices" / "الصوت والأجهزة" (was "More settings").
  - Both still check that an English screen shows no Arabic letters and an Arabic one no Latin letters; both pass.
    Note that what is dimmed by a switch that is off is not in the semantics tree, so that check does not read it there
    (it reads the same texts where the switch is on).
- **Not run**: `:core:prayer:test`, `:alerts:testDebugUnitTest`, `:wear:testDebugUnitTest` (nothing of theirs was
  touched); the release builds (`:app:assembleRelease`); the emulator workflows on GitHub.

## Not tested

- Nothing on a phone or a watch. In particular: the Fix sheet opening and closing as things are fixed in Android's
  settings; the permission dialog from Fix; the wheels' feel with their units; the slider's thumb under a finger; a
  voice's opening playing on touch; the ringing screen over a real lock screen, and its clock turning with the minute.
- TalkBack was not run; what it reads was reasoned from the semantics in the code (a dimmed block is hidden from it).
- The Fix sheet itself is a window of its own, which the renders do not see; its content is rendered inside a stand-in
  (`fix_list_*.png`).

## Second review

22 November 2026, a helper on `worktree-agent-a99ea7e69fd298cfa`, for the lead to merge. A second independent
reviewer judged the renders against "is this what Apple would do?". What was done in this area, by the reviewer's
numbers. Software renders and unit tests only: nothing here has been run on a phone.

| # | Finding | Done? | What was done |
|---|---|---|---|
| 3 (the lead's, a must) | Numbers written into strings ignore the numerals setting (the Arabic ones held "١٠" among Western digits) | Done in this area | `adhan_test_desc`, `adhan_import_too_large`, `alarm_full`, `alarms_empty_footer`, `alarm_try_sent` take the number as `%1$s` in both languages, edited in place; the pages and the view model pass it through `Formatter.number` (`f.number(25)`, `f.number(PrayerAlarm.MAX)`, `digits(5)`, and the size from `CustomSounds.MAX_BYTES`). The Arabic nouns stay as they are: each string is only ever given its one number (10 and 5 seconds "ثوانٍ", 20 "منبهًا", 25 "دقيقة", 40 "ميغابايت"), and each form is the right one for it. None of this area's Arabic strings holds an Arabic-Indic digit now. (`adhan_test_desc` is used by no code; changed all the same.) |
| 12 | Alarm rows change structure when an alarm has a name | Done | The time first on every row; the name, when there is one, the first line under it (15 sp SemiBold, the primary ink, one line); then "10 min after Dhuhr · Mon–Fri" (13 sp, secondary). Gold is no longer used for names. |
| 13 | Prayer rows give no sign that they open a page | Done | A split row: sign, name, time, a chevron; a thin upright line (1 × 24 dp, the separator's colour); the switch. What is left of the line opens the prayer's page; the switch toggles it. Mirrored in Arabic; at 360 dp with text at 1.3 "Maghrib 5:32 PM ›" still fits on one line. The chevron keeps its strength on a quiet row (the page opens either way). |
| 14 | Ringing screen repeats the time; Snooze too close to Stop | Done | Under what is sounding, the Adhan shows the place only ("Dubai"). The line comes from the alert's text (`AlertText.body`, "5:33 PM · Dubai", shared with the notification and in `alerts`, not mine): the screen leaves out its first fact for the Adhan (`ringingFacts`). An alarm keeps "Fajr 5:12 AM · Dubai" (its prayer's time is not the time now). Snooze: 28 dp under Stop, 52 dp high. |
| 17 | The voice list offers Al-Quds, of which Minhad has no recording | Done | The voices list shows only voices there is a recording of. A voice without one (Al-Quds today) is a row under **My sounds**: "Add the Al-Quds Adhan / From a recording of your own" (أضف أذان القدس / من تسجيل لديك) with the green "+" sign of the rows that add; it opens the file picker as before and the recording is named for the voice, so it then stands among the voices as a preset like the others (the logic of 0.18 is unchanged). Not on the sound-effects page (Shuruq, a prayer set to a sound). Random: a shuffle sign in the column where the others have their button, so the row is 64 dp like them. |
| 23 (part) | Separators left dangling when a line wraps | Done in this area | `FactsLine` (AdhanComponents.kt): facts joined by " · " that break before a fact, never after a dot, each fact whole unless it is wider than the line; TalkBack reads it as one text without the dots. Used on the history's line of facts (its "Isha · Test" title stays one text: two words), an alarm's row, the "next alarm" line, the alarm editor's line (centred), a prayer's line on the tab and the ringing screen. No-break spaces keep "Fajr 5:20 AM" in one piece on the hero (`adhan_hero_next_at` joins with `&#160;`; at 1.3 it now reads "Next:" over "Fajr 5:20 AM", where it was "Next: Fajr" over "5:20 AM"), a time and its AM/PM in the history and in "Today at 4:55 AM", and "10 seconds" in "Test the Adhan in 10 seconds" and "Try it in 5 seconds" (both strings edited in place). |
| 24 | Unlabelled Full / Short in the alarm sound picker | Done | Under a **LENGTH** header (المدة), with a line under it: "For Minhad’s sounds and the Adhan below: the whole recording, or its opening." |
| 26 | Two empty-state styles | Done | One: the card with the gold seal, a title, a sentence and the one action (`EmptyCard`), on the alarms' page and on My sounds (where the grey disc on the bare page was); My sounds keeps "Your sounds stay on this phone." under the card. |
| — | Secondary buttons that read as disabled | Checked | "Try it in 5 seconds", "Test this alert", "Share report" and the Fix sheet's actions were already the neutral kind. The history's "Let alarms through Do Not Disturb" / "Allow exact alarms" were lines of gold text: now `NoorButton(kind = Tonal, compact = true)`. The Fix row's gold **Fix** stays: it is that row's main action. |

Strings added at the end of both files under `<!-- 0.25 review 2: adhan -->`: `voice_add_named`,
`voice_add_named_detail`, `alarm_length_title`, `alarm_length_note`. Edited in place: the five with numbers above,
`adhan_hero_next_at`, `history_test`, `alarm_try`. `voice_add` is still used (a prayer's page, when its voice has no
recording). No test was changed.

What a person would want otherwise: "Next:" alone on the hero's first line at 1.3 is honest but not pretty; the hero's
title and line are `HeroCard`'s (shared) and were left as they are.
