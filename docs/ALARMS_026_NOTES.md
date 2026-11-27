# The alarms in 0.26: as Samsung's Clock

27 November 2026, a helper on `worktree-agent-aef0ab79e2dca3395`, for the lead to merge. The owner, who uses a Galaxy
phone (One UI) every day: "And the alarms, I kind of like it, but I feel like the way it should be done should like look
very similar to the Samsung clock app, like the alarms section. It should look very similar to that, very intuitive.
Even when you set an alarm, it should look like it."

Nothing here has been run on a phone. Every picture named below is a software render (Robolectric); what the pages do
when touched was checked by the code and by the render tests named at the end, not on a device.

## What changed

Only the look and the way about; what an alarm is and does is unchanged (the model `PrayerAlarm`, the planner, the
ringing, sounds, snooze intervals, days, names, the limits `MAX`, `MAX_MINUTES`, `MAX_LABEL`, `SNOOZE_CHOICES`).

### The list (Adhan tab → Alarms)

- **At the top, as Samsung's**: "Alarm in 6 hours 6 minutes", large (26 sp) and centred, with "Tomorrow at 4:58 AM ·
  Wake up for Fajr" under it (the alarm's name, or "25 min before Fajr" when it has none). With every alarm off: "All
  alarms are off" (Samsung's words). A number and its unit never part across the line ("6 hours" / "6 minutes").
- **"+" at the end of the row under it**, where Samsung's Clock has it. 0.25's "Add alarm" button under the list is
  gone; with no alarms the empty card keeps its own Add alarm button. At 20 alarms the "+" is dimmed and the line "20
  alarms: the most Minhad can hold" stands under the list.
- **A card per alarm** (Samsung's), not one group with hairlines (0.25). In each: the name small over the time (when it
  has one, as Samsung), the time it rings next large and light (38 sp) with AM or PM at half size, its switch at the
  card's end beside the time; under them what it is ("25 min before Fajr", "1 h 30 min after Asr") and, at the end of
  that line, "Every day" or the week's seven letters with its days lit in gold. Every card is built the same way, named
  or not; at large text the letters go to a line of their own when they do not fit.
- **Off is dimmed** (45%), as Samsung greys an alarm that is off.
- **Long press selects**, as Samsung's: a round check on every card, the top says "2 selected" (or "Select"), "All" at
  the start of the row and Cancel at its end, and a Delete button under the cards; Back leaves the choosing. Deleting
  asks nothing (Samsung's does not either). A touch on a card while choosing chooses it.
- In the order of the day as before (by prayer, then before it, then after it).

### The editor

- **One large card at the top**: when it rings next, very large (58 sp Light), "Tomorrow · 25 min before Fajr" under it,
  answering as the wheels turn; then the four wheels that read as the sentence [0 h] [25 min] [before] [Fajr] with
  **three rows in sight, the chosen one large and bright, the others small and faint, no band behind**, as Samsung's
  Clock turns its hour and minute. The units stay beside the chosen number. Mirrored in Arabic (it reads from the
  right: "٠ س ٢٥ د قبل الفجر"); digits keep their order.
- **The days as Samsung's**: a card with a line that says them ("Every day", "Mon–Fri") and the week's seven letters
  as round toggles in the language's own week order (Sunday first in English, as in the rest of Minhad), a day that
  rings lit in gold, the others a plain letter. At least one stays on.
- **The name** in a card of its own, "Alarm name" until typed.
- **Sound, Vibration, Snooze as Samsung's rows**: the name, what is chosen under it in gold, a switch at the end.
  Snooze is in two parts as Samsung's: the words open the interval (5, 10 or 15 minutes), a thin upright line, then the
  switch (off = no Snooze, the `0` that was "Off" in the old sheet; on again gives back the interval it had). Vibration
  is one switch. Sound opens the sound page (unchanged) and has a chevron, **not a switch** (see below).
- **Cancel and Save at the foot**, side by side, each half the width, in gold, Save the heavier, as Samsung's editor
  ends (0.25 had moved them into the top bar, as on an iPhone). The page fades into the foot's colour above them and
  its last row scrolls clear of them. The slim bar at the top keeps only the page's name ("New alarm", "Edit alarm").
  Android's Back is still Cancel.
- **Try it in 5 seconds** and **Delete alarm** are quiet text buttons under the rest (they were full-width buttons).
- "1 h 30 min before Fajr" in place of "90 min before Fajr" wherever an alarm is described (as the wheels set it; new
  strings `alarm_amount_hm`, `alarm_amount_h`). Under an hour it reads as before ("25 min before Fajr").

## Files

- `app/src/main/java/com/noor/app/ui/adhan/AlarmScreen.kt`: the editor, the list and the cards rewritten; the sound page
  (`AlarmSoundPage`) and `soundLabel`, `daysLabel`, `ringWhen`, `ringDay` unchanged. `AlarmsList` keeps the signature
  `AdhanScreen` calls, with one added parameter at the end, `vm: AdhanViewModel = viewModel()` (the page's own view
  model, for deleting what is chosen). **No change to `AdhanScreen.kt` is needed.**
- `app/src/main/java/com/noor/app/ui/components/Wheel.kt`: five optional parameters (`visible`, `awaySize`, `weight`,
  `unitSize`, `numberShare`); their defaults keep the wheel exactly as it was. The alarm editor is its only user.
- `app/src/main/res/values{,-ar}/strings.xml`: added at the end under `<!-- 0.26 alarms -->`: `alarm_header_in`,
  `alarm_header_in_hm`, the plurals `alarm_hours_long` and `alarm_minutes_long` (Arabic in the genitive after "بعد":
  ساعتين، دقيقتين), `alarms_all_off`, `alarms_selected`, `alarms_select_all`, `alarms_delete_selected`, `alarms_select`,
  `alarm_vibration`, `alarm_amount_hm`, `alarm_amount_h`. Numbers are arguments. No string was edited or removed. No
  longer used by any code, can go when the lead likes: `alarm_next_in_hm`, `alarm_next_in_m`.
- New tests: `app/src/test/java/com/noor/app/design/AlarmsDesignTest.kt` (two classes, one `@Test` each).
- Nothing in `AdhanComponents.kt`, `AdhanScreen.kt`, `PrayerAlertScreen.kt`, `VoiceScreen.kt`, `AdhanViewModel.kt`,
  `core/`, `alerts/`, sounds, settings, theme or `MainActivity.kt` was touched.

## For the lead: one line outside my files

**The editor still shows the floating tab bar** (its route is a top-level one), so on a phone Cancel and Save stand
just above the tab bar: two bars at the foot. Samsung's editor is a page of its own with no tabs. The one-line change,
in `MainActivity.kt`: `val withoutTabBar = setOf(ONBOARDING, CITIES, WATCH_STUDIO, ADHAN_ALARM)`. The editor works and
looks right either way (its foot sits over whatever the frame's padding leaves at the bottom); the `phone_edit_*`
renders show it without the tab bar, as it would be with that line.

## Decisions the owner may want otherwise

1. **Sound has no switch.** Samsung's "Alarm sound" switch makes an alarm vibrate only. Minhad has no such alarm; the
   nearest, "Notification only", is a different kind of alarm (a notification, nothing to stop), and a switch named
   Sound that turned an alarm into a notification would mislead. So the Sound row opens the sounds, as before, with a
   chevron. If the owner wants a silent, vibrate-only alarm, that is a change to the alarm itself (the model and the
   ringing), not to this page.
2. **Snooze has no "times"** (Samsung's "5 minutes, 3 times"): Minhad's Snooze has an interval only.
3. **A card per alarm** (Samsung's), where the 0.25 reviewer had asked for one group with hairlines.
4. **The name over the time** (Samsung's), where the 0.25 second review had put it under the time. Every card still
   has the same shape: the time beside the switch, what it is under it.
5. **The "+" alone in the row over the list** (Samsung's place); there is no "Add alarm" button under the list any more.
6. **The editor keeps a slim title** ("New alarm"); Samsung's has none. It costs one bar's height.
7. **Deleting from the list asks nothing**, as in Samsung's.
8. The "Alarm in…" line is worked out when the page is drawn and does not count down while it is open (as in 0.25).

## Self-critique (two rounds over the renders)

First round, against One UI and against "would Apple ship this":
- The header broke "9" from "minutes" ("Alarm in 6 hours 9" / "minutes"): numbers and units are now held together.
- The day letters sat beside the time on one card and under it on the next, by the width of the time: the cards looked
  built three ways. Now the switch always stands beside the time and the letters at the end of the line under it.
- At large text the seven letters crowded together ("SMTWTFS"): their places now grow with the letters.
- The foot under Cancel and Save was 94% opaque and the rows showed through it: now solid, with a short fade above.
- The long press in the first selection render landed on the header's copy of the name (a test mistake, not the app's):
  the render now presses the card.
Second round: the list, the choosing, the editor in English, Arabic, light and at 1.3 read as Samsung's Clock with
Minhad's glass and gold. What I would still look at on a phone: whether the three-row wheels are easy enough to turn
with a thumb (each row is 52 dp), the long-press feel, and the foot's colour against the real sky at the bottom of the
screen (the renders' sky is Robolectric's).

## Tests

Through Gradle on the cloud machine, Robolectric with SDK 34. Unit tests and software renders only.

- New: `AlarmsDesignTest` (1 test; 360 × 1400 dp, `app/build/design/alarms`): the list empty (English, Arabic), with
  five alarms on and off (English, Arabic, 1.3, light), while choosing two (English, Arabic), all off (English,
  Arabic); the editor whole, new and set (English, Arabic, 1.3, light), and for an alarm with a name and every day and
  one of 1 h 30 min on two days. `AlarmsPhoneDesignTest` (1 test; 360 × 780 dp, `phone_*`): the list, the choosing, and
  the editor new and set in the four languages and themes, as first seen on the owner's phone. Both pass and only
  assert that pictures were written: they are for looking at.
- Run with the final code: `ScreenshotTest` (1 test: its 62 pictures, the alarms 87–91 among them, and its check that
  an English screen shows no Arabic letters and an Arabic one no Latin letters), `design.AdhanDesignTest` (1),
  `ThemeContrastTest` (5), `ButtonContrastTest` (1), `NumeralsInStringsTest` (1), `AlarmsDesignTest` (1),
  `AlarmsPhoneDesignTest` (1): **11 tests, 0 failures, 0 errors, 0 skipped.** No assertion of an existing test was
  changed: their texts ("25 min before Fajr", "New alarm", "Edit alarm", "Sound", "–" while the time is worked out) are
  all still there.
- Not run: the rest of `:app:testDebugUnitTest` (the lead runs the whole suite), `:core:prayer:test`,
  `:alerts:testDebugUnitTest`, `:wear:testDebugUnitTest` (nothing of theirs was touched), the release builds and the
  emulator workflows.

## Not tested

- Nothing on a phone. In particular: the wheels' feel with three rows; the long press and the choosing; the Delete of
  several alarms (each is the view model's `deleteAlarm`, as the editor's Delete always was); the snooze switch; the
  foot over the navigation bar and the keyboard; TalkBack (reasoned from the semantics only: the cards are buttons with
  "Select" as their long-press action, the day letters read as "Mon–Fri", the "+" reads as "Add alarm").
- The ringing screen was not changed and not looked at again.

## The 0.26 review (27 November 2026), what changed

- **The editor's foot** is an opaque bar (`FootBar`, `AdhanComponents.kt`), as the page's top bar is: the page's own
  sky drawn where the bar stands (its gradient and the horizon's glow, so it is the page and not a wash over it), a
  hairline above, and it takes the touches that land on it. Until then it was the foot's colour at 94% with a fade,
  and Vibration and Snooze showed through. The page's end is lifted clear of it.
- **Delete while choosing** stands in the same bar at the page's foot (as Samsung's), not under the last card, off the
  screen with five alarms. What is chosen is held by the Adhan tab (`AlarmChoice`), whose foot shows it
  (`AlarmsDeleteBar`); `AlarmsList` takes it as a last parameter with a default.
- **The days**: the one selector of the app (`WeekDays`): a day that is on is now a solid gold disc with a dark letter.
- **The name** wraps onto a second line inside the card's margins (it ran into the edge at 1.3, cut in a letter); a
  line break typed or pasted becomes a space.
- **"Test in 5 seconds"** (was "Try it in 5 seconds"), worded as the prayers' tests.
