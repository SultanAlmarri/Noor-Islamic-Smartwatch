# The month's timetable: notes

A draft for the lead to fold into `DESIGN.md`, `HANDOVER.md` and `ARCHITECTURE.md`. Phone app only (`app`). New code
is in `app/src/main/java/com/noor/app/ui/timetable/`; nothing in `core`, `alerts`, `shared`, `wear`, `face`,
`legacy`, `signing`, `tools`, the shared components (`ui/components`, `ui/settings/SettingsComponents.kt`,
`ui/theme`), the Gradle files or the version numbers was touched.

The owner's first brief asked for a weekly or monthly timetable view; it had never been built. This is the monthly
one.

## 1. What the page is

A whole month of prayer times as a table, one row for each day.

- **The bar**: back; in the middle, where a page's name would be, the calendar, **Gregorian | Hijri** (the app's own
  segmented control; iOS puts such a choice in its bar); Share at the end.
- **The month**: its name in large type between two round arrows (to the month before and after; mirrored in
  Arabic), and under it, in gold, the months of the other calendar it runs through ("Jumada al-Ula – Jumada
  al-Akhirah 1448 AH" under November 2026; "8 February – 8 March 2027" under Ramadan 1448 AH).
- **The table**, on a pane of glass that runs to the foot of the page. The six names (Fajr, Shuruq, Dhuhr, Asr,
  Maghrib, Isha) stay at its head while the days scroll. A row is the weekday over the day's number, the other
  calendar's day small at the end of that cell (a quiet column of its own), then the six times.
  - **Today's row is lit** (a lighter glass with a thin gold edge, its number in gold) and the **next prayer's
    time is in gold**. After Isha the next prayer is tomorrow's Fajr: that cell is the gold one.
  - Days of this month that have gone by are a shade quieter. Shuruq's column is quieter than the five prayers',
    as its row is on the home screen.
  - **Fridays**: the weekday is in gold and the noon time has "Jumu’ah" under it, the app's own name for Dhuhr on
    a Friday (`Formatter.prayerName(p, date)`).
  - **Ramadan**, quietly: the Hijri day is in gold, Fajr and Maghrib (the two times the fast turns on) are heavier,
    a gold line "Ramadan 1448 AH" stands where it begins, and one line under the table says "Ramadan: Suhoor ends
    at Fajr, Iftar is at Maghrib." Whether a day is in Ramadan is known the way the home screen knows it
    (`HijriDate.of(date, hijriAdjust).isRamadan`).
  - Where a month of the other calendar begins, its name stands between the rows in gold.
  - Under the last day: the place and what the times follow ("Dubai · IACAD — Dubai (Dulook DXB)"), and on what
    grounds, in Settings' own words ("Its own published times, for each day." and the others; a new sentence for a
    month that is partly published and partly calculated). By Hijri month, also the calendar's own caveat (Umm
    al-Qura; a month may start a day apart).
- **Month to month**: the arrows, or a swipe sideways (the pages follow the finger; the months either side are
  ready before the swipe). Every month with a day between 2021 and 2035 (`DateWindow`): 180 Gregorian months, 187
  Hijri ones. **"This month"** (a small capsule over the tab bar) is there only while another month is on show.
  This month opens with today near the top (two days before it still in view).
- **By Hijri month** as well: the rows are then the Hijri month's days, with the Gregorian day beside each, and the
  Gregorian months named between the rows. Ramadan by Hijri month is the timetable people hang on the wall.
  Changing the calendar keeps to the same days (the month that holds today, or most of the month on show).
- **Share**: the month as plain text through Android's share sheet (no file, no permission): what it is and the
  month, the other calendar's months, the place and the method or authority, then a line for each day (its number
  and the six times in the order of the day; "Jumu’ah" closes a Friday's line). In the app's language and numerals.
  Day numbers and times are padded with figure spaces so the columns line up wherever digits share a width; in
  Arabic every line carries a right-to-left mark, so the day leads from the right as in the table. A sample is
  written by the render test to `app/build/design/timetable_share_en.txt` and `..._ar.txt`.
- **While a month is worked out** the table shows its skeleton (the days are there at once, soft bars where the
  times will be), and the times fade in. A month already seen comes back at once.
- **TalkBack** reads each row as one sentence: "Today, Wednesday 4 November 2026, 24 Jumada al-Ula 1448 AH. Fajr
  5:09 AM, Shuruq 6:25 AM, Dhuhr 12:05 PM, Asr 3:14 PM, Maghrib 5:40 PM (Next prayer), Isha 6:55 PM". The page
  announces itself as "Timetable". Back, Share and the arrows are 48 dp to touch and named.
- **With no place chosen** the page says so and offers "Set location".

## 2. Where it is opened from

- **The date sheet** on the home screen (touch the date): a button, "Month’s timetable", under the calendar. It
  opens the timetable of the month the sheet is showing, by the calendar the sheet is showing.
- **The route** is `Routes.TIMETABLE` = `"timetable/{month}"`; `Routes.timetable(month)` writes it:
  `timetable/now` (this month), `timetable/2026-11`, `timetable/h1448-9` (a Hijri month). To add a row for it
  elsewhere: `onClick = { nav.navigate(Routes.timetable()) }` (from Settings' row list: `onOpenRoute(Routes.timetable())`).
  The page is pushed on the tab it was opened from and keeps the tab bar.

## 3. How it gets its times (rule 1)

The table shows, for a day, exactly what the home screen shows on that day, because it asks the same thing:

- `Timetables.build(month, place, profile, settings, tables)` calls `PrayerProvider.resolve(date, place, profile,
  settings, tables)` for each day, with the place from `PrayerProvider.place(settings)`, the method from
  `PrayerProvider.profile`, the user's Asr school, high-latitude rule and personal minutes inside `settings`, and
  the app's `OfficialTimetables`. That is the call behind the home screen's `DayCache` (`TodayViewModel.day`). So
  Dubai's days come from IACAD's published table where the app carries it, and are calculated elsewhere, exactly
  as on the home screen. Nothing is hard-coded, rounded again or adjusted.
- Today's date and the next prayer come from `Schedule.state` over the home screen's own `DayCache` class.
- `TimetableViewModel` works a month out on `Dispatchers.Default`, never on the main thread, and keeps the last 36
  months (`MonthStore`). When anything the times depend on changes (the place, the method, an adjustment, the
  Hijri adjustment, a newer official timetable), the kept months are dropped and the page asks again.
- The page keeps the months either side of the one on show composed, so they are worked out before a swipe.

## 4. Decisions the owner may want to change

1. **12-hour times are written without AM or PM** ("5:13", not "5:13 AM"), in the table and in the shared text, as
   printed timetables do: the column says which prayer it is. It is what lets six times fit a 360 dp phone at a
   readable size. TalkBack says the full time. With the 24-hour format nothing is left out.
2. **The page has no title of its own**: the calendar switch stands where "Timetable" would be, and the month's
   name is the heading. A first version had the title, the switch under it across the page, and a head that folded
   away on scrolling; it took a quarter of a small screen before the first row and was more machinery than a table
   needs.
3. **"This month" floats over the foot of the table**, just over the tab bar, in the tab bar's own glass. It covers
   part of a row while another month is on show (every row can be scrolled clear of it). The other places tried
   in thought (a line of its own under the month, an icon in the bar) either moved the table up and down or said
   less.
4. **The table's figures follow the phone's text size only as far as they fit**: six times must share the width, so
   at large text sizes the times stop growing where a column is full (the head of the page and the words under
   the table grow as everywhere). The alternative is a table that scrolls sideways.
5. **Shuruq is in the table** (six columns, as on the home screen), a shade quieter.
6. **Only the month**: no week view. A week is seven rows of the same table; "this month" opening on today shows
   the week ahead at once.
7. **Names**: "Timetable" / "الجدول الشهري" (the page, for TalkBack), "Month’s timetable" / "جدول الشهر" (the
   button).
8. **The shared text's layout** (one line a day, numbers only). A two-line form with the prayers' names on every
   day would be clearer to a stranger and three times as long.

## 5. Edits to existing files

- `MainActivity.kt`: `Routes.TIMETABLE` and `Routes.timetable(...)` (3 lines in `Routes`); one `composable(Routes.TIMETABLE)`
  entry; and one more argument in the existing `TodayScreen(...)` call (`onOpenTimetable = { nav.navigate(Routes.timetable(it)) }`),
  which the date sheet's button needs.
- `ui/today/TodayScreen.kt`: a new last parameter `onOpenTimetable` on `TodayScreen` and `TodayContent` (default
  null: no button, so existing tests and renders of the date sheet are unchanged), handed to the date sheet.
- `ui/today/CalendarPicker.kt`: a new last parameter `onTimetable` on `CalendarPicker` and its two pickers; when
  given, the button (`TimetableEntry`) under each.
- `res/values/strings.xml`, `res/values-ar/strings.xml`: ten strings each, in one block at the end
  (`timetable_*`).

## 6. Self-critique: three rounds

**Round 1 (first renders).** The times were cut at large text sizes and with Arabic-Indic numerals (Android scales
large text less than small text, so a size measured at one scale and drawn at another did not fit; and this
typeface's Arabic-Indic figures do not share one width). The table now sets its own sizes in what is seen, measured
with the figures really used. The Hijri day stood close after the day's number and read as part of it ("1 21"); it
is now a column of its own at the end of the cell. The Hijri months' line broke its year onto a second line beside
the arrows; it now runs under them, the width of the page. Opening by Hijri month landed on the first day instead
of today (the two calendars shared their pages' kept places); fixed.

**Round 2 (structure).** The title, a full-width switch, the month and a folding head were too much above a table:
see decision 2. The page now has three fixed parts (bar, month, table) and nothing clever.

**Round 3 ("would Apple ship this?", after the owner's message of 22 November).** Looked at every render for
legibility at a glance, the alignment of the columns, the weight of each element and what could go.
- *Alignment*: with Arabic-Indic numerals the columns were ragged (figures of different widths). Every time is now
  set on its colon: hours end at it, minutes begin at it, so the colons of a column stand under each other in both
  numeral systems, and a column's times are centred under its name. The times are drawn (one drawing a row, not six
  texts), which is also less work while scrolling.
- *Weight*: one size and weight for ordinary times; heavier only for today's row and Ramadan's two times; gold only
  for today's number, the next prayer, Fridays, Ramadan and the other calendar's months. "Jumu’ah" was raised from
  9.5 to 10 (it was the smallest text on the page).
- *Removed*: the page title, the folding head, the solid ground of the bar.
- *The capsule*: restyled as the tab bar's glass and docked nearer to it (decision 3).

**What still falls short**, honestly:
- The Arabic-Indic figures of the app's typeface are not of one width, so within a time the figures do not stand
  in strict columns (the colons do). A typeface matter, the same on the home screen.
- On a 360 dp phone with the 24-hour format the six times fill their columns closely (about 7 dp between them).
  Readable, not generous.
- "This month" covers part of a row (decision 3).
- On a wide screen the six columns are far apart (the page keeps the app's 640 dp width); calm, but the eye
  travels.
- Nothing here has been seen on a phone: how the swipe between months feels, how smoothly thirty rows scroll, the
  haptic tick, the share sheet, TalkBack's reading. See below.

## 7. What was tested, and how

All on the cloud machine, with Robolectric (unit tests and software renders). **Nothing was run on a phone or an
emulator.**

- `TimetableTest` (7 tests):
  - Dubai, November 2026: all 30 rows equal the home screen's day (its own `DayCache`), every row is marked as the
    authority's own time, and seven days are compared directly with IACAD's table as the app carries it; Friday 6
    November has the home screen's times.
  - 767 more days, row by row against the home screen: Dubai, Abu Dhabi, Makkah, London and Singapore in
    December 2026, March 2027 (London's clocks change), January 2021 and December 2035 (the ends of the window);
    Ramadan 1448 by Hijri month in Makkah and Dubai; Dubai with the Hanafi Asr, personal minutes and the Hijri date
    moved a day (by both calendars); London with a method chosen by hand. A personal +2 minutes is shown to reach
    the table.
  - The shared text: its heading lines, and for each of the 30 days a line holding that day's six times as the
    home screen has them, in order; four Fridays closed with "Jumu’ah"; the Hijri date over the first day and
    where the next Hijri month begins; the same in Arabic with Arabic-Indic numerals and the 24-hour format (each
    line led by a right-to-left mark, no Latin letters or Western digits); the Hijri month's text.
  - The pages: 180 Gregorian months; by Hijri month, with every Hijri adjustment from −2 to +2, the pages hold each
    of the 5478 days of 2021–2035 exactly once, in order. Route arguments there and back. The words over a month.
    A month is worked out once and kept; a change of settings makes a different basis.
- `TimetableDesignTest`, `TimetableSmallDesignTest`, `TimetableWideDesignTest` (3 tests, 29 pictures in
  `app/build/design/timetable_*.png`, looked at one by one): 412 × 915 dp in English and Arabic at night and by
  day, by Hijri month, Arabic-Indic numerals with the 24-hour format, large text (1.3), Ramadan (a Gregorian month it
  begins in; the month itself; its foot), London in March with the 24-hour format, the foot of a month, the
  skeleton, the next month with "This month", the date sheet's button in both languages; 360 × 640 dp (English,
  Arabic, 24-hour, Arabic-Indic, large text in both languages, by day, by Hijri month); 673 × 841 dp (a foldable's
  inner screen) in both languages. They also check: what today's row and a Friday's row say (to TalkBack) against
  the home screen's times, in English and Arabic; that the six names stand inside the screen, in order, none over
  another, at every size; the arrows, a swipe sideways and back, "This month", the calendar switch (the Hijri
  month opens on today), Share (its text holds today's times), the date sheet's button (the month and calendar it
  asks for); and that an English screen shows no Arabic letters and an Arabic one no Latin letters.
- The phone app's whole unit-test task (`:app:testDebugUnitTest`), run once more at the end on the final code: 142
  tests in 65 classes, none failed, none skipped (the 132 that were there before, and these 10). No existing test
  was changed. `:app:assembleRelease` (R8) built.

Not tested: anything on a phone or emulator (touch, the swipe's feel, scrolling smoothness, haptics, Android's share
sheet opening, TalkBack, rotation, the page after the app is killed in the background, the smoothness workflow
`smooth.yml`). The renders are software renders at a fixed moment; in the render harness the tab bar stands a
little lower under the page than on a phone, so "This month" looks further from it than it will be.
