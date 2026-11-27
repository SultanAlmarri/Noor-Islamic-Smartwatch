# The prayers' part of the Adhan tab in 0.26

27 November 2026, a helper on `worktree-agent-a29ae7bf8e128882e`, for the lead to merge. The owner, on 0.25: "the
master volume doesn't really make sense ... the section that says all prayers, it's kind of weird. Adhan voice, I
don't really like it ... why can't I hear the Fajr version as well? And why is it that the Adhan is still starting up
gradually? I want it to be on its normal volume from the beginning. Like make it an option ... by default it should be at
the normal volume from the beginning ... use the icons you used on the home screen ... I want you to totally redesign
the Adhan section ... I don't like the way you organized it, like Adhan and then short Adhan and then sound,
notification, silent."

Nothing here has been run on a phone. Every picture is a software render (Robolectric, `PrayersDesignTest`,
`AdhanDesignTest`, `ScreenshotTest`); what the pages do when touched was checked by reading the code and by the unit
tests named below. The alarms (another helper) and the sounds themselves (a third helper) are not part of this work.

## Why the Adhan still rose for some people

The recordings start at full loudness. `AlertPlayer` rises only when it is given a rise; until 0.25 every prayer was
given the one global "Gradual volume". 0.24.1 turned it off once, but only where it was exactly 10 s (the old default),
so a phone left at 5, 20, 30 or 60 s still rose. 0.26 makes the rise each prayer's own ("Start softly", off by default),
and its first start turns every rise off once, whatever its length (below, "The migration").

## The design, screen by screen

### The Adhan tab, Prayers

Top to bottom: the **Prayers | Alarms** control and the **Prayer alerts** card (unchanged); the **Fix** row when
something is in the way (unchanged); then:

1. **The six prayers.** Each row: the prayer's **sky disc from the home screen** (`SkyDisc`, the owner liked it there;
   0.25 had coloured tiles), its name and, at the end of the same line, today's time written as the home screen writes
   it (light figures, AM/PM small); under them **what it will do** in a word or two: "Adhan · Makkah", "Fajr Adhan ·
   Dubai" (Fajr on a Fajr recording, so that it is clear why Fajr's voice differs: Makkah has none), "Adhan opening · Makkah", "Birds", "Notification", "Silent notice",
   "Off", and its days when not every day ("Mon–Fri"); a chevron; its switch. Touching the row opens the prayer; the
   switch turns it on or off. An off prayer has a dim sky and grey words, so the ones that will sound stand out.
2. **Every prayer** (a group under the prayers, the same rows a prayer's page has, so one teaches the other):
   **Sound** ("Adhan · Makkah", or "Mixed" when they differ; opens the picker for every prayer), **Volume** (named, the
   figure at its end, the slider under it as wide as the row; "Mixed" while the prayers differ; letting go plays six
   seconds of the sound at that volume, the Adhan's opening for a voice), **Start softly** ("Full volume from the
   start" / "Rises to full volume over 10 seconds" / "On for some prayers"), **Vibrate** ("On for some prayers" when
   they differ). Footer: "Sets all prayers at once. Tap a prayer above to give it its own." Any change here sets every
   prayer. With the alerts off the group is dimmed (as in 0.25).
   - Why this answers "the master volume doesn't make sense": the slider had no name and no word for "these differ";
     it now says "Volume", says "Mixed" when it should, and is heard as it is set.
   - Why not "All prayers": the owner found that heading odd; the group now comes after the prayers, under "Every
     prayer", with a footer saying what it does.
3. **Test the Adhan in 10 seconds**, **Alert history**, and **Devices and silent mode** (was "Sound and devices":
   the gradual volume left that page; it now holds Phone/Watch/Both, Play in silent mode and Do Not Disturb, and the
   library and my sounds). Which device stands under its name ("Phone"): beside it, the name broke over two lines.

### A prayer's page

The prayer's sky disc with "Alert at Fajr", "Today at 5:23 AM" and its switch (the whole card is the switch); the same
rows as "Every prayer", for this prayer: **Sound**, **Volume**, **Start softly**, **Vibrate**, and **Stop after**;
then **Days** (the seven day circles in the language's week order, "Every day" / "Mon–Fri" under them); **Test this
alert**. A prayer set to a notification or a silent notice shows only Sound (its notification follows the phone's own
settings: no volume, rise or vibration of Minhad's). Sunrise says "Sunrise ends Fajr time; it has no Adhan." With the
prayer (or all alerts) off, everything under the switch is dimmed, as in 0.25.

The sign of the Sound row says what kind of thing plays: the mosque for the Adhan (and for "Mixed"), a bell for an
alert sound, a crossed bell for a silent notice, a file for one of my sounds.

### The sound picker ("Sound for every prayer", "Sound for Fajr", …)

The list of six kinds (Adhan / Short Adhan / Sound / Notification / Silent) is gone. One page, four parts, each a thing
a person wants:

- **Adhan**: a control **Whole Adhan | Opening only** (under it, "The whole call, to its end." or "Only the first
  “Allahu Akbar, Allahu Akbar”."), then the voices (Makkah, Madinah, Egypt, Dubai, and the user's own Al-Quds once
  added), each with its round ▶; then Random. On "Sound for every prayer" and "Sound for Fajr" each voice also shows
  **▶ Fajr version** (its Fajr recording, with "As-salatu khayrun min an-nawm") or, in grey, **No Fajr version**, and
  the footer says what happens at Fajr. Today only Dubai has a Fajr recording in the library.
- **Alert sounds**: the library's short sounds (`SoundLibrary.sounds`, not hard-coded: the other helper's beeps and
  birds appear here as they are), each with ▶ and its length.
- **Notification only**: "Notification sound" (the phone's usual sound, with ▶, played as the phone's notification
  sound) and "Silent notice" (on the screen, without a sound).
- **My sounds**: "Add the Al-Quds Adhan", the user's recordings, Import a sound (as in 0.25).

A touch on a row chooses it and plays it as it will sound at that prayer (the opening or the whole, at the prayer's
volume; at Fajr its Fajr recording); a second touch stops it; leaving the page stops it. The chosen one has a gold
check. Sunrise's picker has no Adhan part.

"For every prayer" sets the five prayers (sunrise keeps its own, as before). Choosing a voice for all keeps Fajr on a
Fajr Adhan when the voice has none (0.18's rule, kept): Fajr never gets a plain Adhan by a choice made for all.
**Changed from 0.25**: a choice for all now sets all five prayers, also those set to a notification or a sound (0.25's
voice row changed only the prayers already calling the Adhan). That is what "Sound for every prayer" says.

## The model and the formats (for the lead: `core` and `alerts`)

- `PrayerAlert.gradualSeconds: Int = 0` (new, last constructor parameter, with a default: source-compatible). Held
  to 0–60. `PrayerAlert.SOFT_START_SECONDS = 10` is what "Start softly" sets.
- **Encoding**: a prayer's line keeps its six first fields; days, before and after (0.16) follow when any is set, and
  now the rise as a tenth field when it is above 0 (then days, before, after are written too). A line of a prayer
  without its own rise, every day, no reminders, is still six fields. A new top-level key `pgrad=1` says "each prayer's
  rise is in its line". **Reading**: with `pgrad=1`, a prayer's rise is its tenth field (none = 0); without it (settings
  written before 0.26), every prayer gets the one global `grad`. Older readers (watches up to 0.25) ignore `pgrad` and
  the tenth field and go on using `grad`.
- `AlertSettings.gradualSeconds` (the global "grad") is kept and still written: the **alarms** use it
  (`AlarmDispatcher`, unchanged), and watches up to 0.25 read only it. Nothing in the 0.26 interface sets it; after the
  migration it is 0. So on a watch still on 0.25, a prayer set to "Start softly" on the phone plays at full volume
  from the start (the safe side).
- `AlertSettings.withEach(only, change)` and `AlertSettings.fullVolumeFromStart()` (both new).
- `alerts`: `AlertDispatcher` gives the player `alert.gradualSeconds` (the prayer's) in place of
  `alerts.gradualSeconds`. Nothing else in `alerts` changed. The `PlayRequest` intent format is unchanged.
- The stored modes are unchanged; the picker maps onto them (`ui/adhan/PrayerSound.kt`: `Plays`, `playsOf`,
  `PrayerAlert.playing`, `sharedPlays`): Adhan whole = ADHAN, opening = ADHAN_SHORT, a voice that is the user's
  recording = CUSTOM, alert sound = SOUND, my sound = CUSTOM, notification = NOTIFICATION, silent = SILENT.

### The migration

`NoorApplication.kt`: the one-time changes moved into `adhanMigrations(context, settings)` (same behaviour for 0.24.1's,
same preference file "noor_migrations"), and a second one, key `adhan_full_from_start_026`: `fullVolumeFromStart()`,
every prayer's rise and the global one set to 0. It runs once; a rise chosen afterwards is never undone.
`TestHookReceiver` (debug) sets the same instead of only the global rise.

## Decisions the owner may want otherwise

1. **The prayers first, "Every prayer" under them.** Putting the six prayers (what the tab is about) on the first
   screen meant the shared sound and volume moved below them; on a 360 dp phone the volume is one short scroll down.
2. **Start softly is a switch with one length (10 s)**, not a choice of 5/10/20/30/60 s. A stored length from an older
   version is shown in the line ("over 20 seconds") until it is switched.
3. **A choice "for every prayer" sets all five prayers**, also those on a notification or a sound (above).
4. **Volume plays six seconds when the finger lets go**, like Android's own volume sliders. It never plays while the
   alerts are off (the slider is dimmed and takes no touch).
5. **"No Fajr version" under three voices** on the picker for every prayer and for Fajr: honest, a little repetitive.
6. **"Adhan opening"** replaces "Short Adhan" everywhere it is shown (the string `adhan_mode_short` was edited in place:
   the home screen's bell reads it too).

## Strings

Added at the end of both files under `<!-- 0.26 adhan -->`: `a026_every_prayer`, `a026_every_prayer_footer`,
`a026_devices`, `a026_some_prayers`, `a026_start_softly`, `a026_soft_on` (the number as `%1$s`, joined by a no-break
space), `a026_soft_off`, `a026_today_at`, `a026_sound_for_all`, `a026_sound_for`, `a026_fajr_note_all`,
`a026_fajr_note_fajr`, `a026_whole`, `a026_opening`, `a026_whole_note`, `a026_opening_note`, `a026_no_fajr`,
`a026_fajr_version`, `a026_alert_sounds_note`, `a026_notification_only`, `a026_notification_sound`, `a026_silent`,
`a026_silent_detail`, `a026_fajr_adhan`. Edited in place: `adhan_mode_short` ("Adhan opening", "مطلع الأذان"). No Arabic string holds a
digit. Now used by no code (can go when the lead likes): `adhan_gradual`, `adhan_gradual_off`, `adhan_gradual_value`,
`adhan_alert_options`, `adhan_type`, `adhan_options`, `adhan_hint_*` (except `adhan_hint_notification`), `adhan_all_prayers`, `adhan_volume_all`,
`voice_for`.

## Files

Mine: `ui/adhan/AdhanScreen.kt`, `PrayerAlertScreen.kt`, `VoiceScreen.kt`, `AdhanViewModel.kt` (functions added at the
end: `choose`, `setSoftStart`, `setVibrate`, `setVolume`, `sample`, `preview`; `previewSystem` also plays the phone's
notification sound; the private `play` takes a length), new `PrayerSound.kt`; `core/.../AlertSettings.kt`;
`alerts/.../AlertReceiver.kt` (one line); `NoorApplication.kt` (the migration). `AdhanComponents.kt` and
`SoundLibraryScreens.kt` were not changed. `AlarmScreen.kt` was not touched; **this work uses its `daysLabel`** (as
0.25 did): if the alarms helper renames or moves it, the prayers' rows need the new name. `DaysRow`, `SoundRow`,
`voiceName`, `ClockVoice`, `minutesLabel`, `SYS_*`, `previewSystem` keep their signatures for `AlarmScreen.kt`.
Debug: `TestHookReceiver.kt` (one line). Docs: this file, DESIGN.md (a section), ALERTS.md (the choices table).

## Tests

All through Gradle on the cloud machine (one lock, `--no-parallel`), on the final code (commit before this file's last
edit). Unit tests and software renders only.

- `:core:prayer:test`: 33 classes, 159 tests, 0 failures, 0 errors, 4 skipped (the 4 skipped were skipped before).
  New: **`SoftStartTest`** (5 tests): off by default for every prayer; each prayer's rise survives a round trip, also
  inside the watch's config and beside alarms; the rise is the tenth field of a prayer's line, a prayer without one keeps
  a six-field line, and a reader of the six first fields reads the same alert; settings as 0.25 wrote them (one
  `grad=20`) give every prayer 20 s, write back with `pgrad=1` and read back the same; a stored 900 is held to 60, a
  damaged value is 0; `fullVolumeFromStart` turns every rise off and changes nothing else.
- `:alerts:testDebugUnitTest`: 8 classes, 21 tests, 0 failures. New: **`SoftStartDispatchTest`**: with the global
  rise at 20 s, the dispatcher starts the Adhan with Asr's own 10 s and Maghrib's own 0 (it gave 20 to both before
  0.26), and the request carries it through the service's intent.
- `:wear:testDebugUnitTest`: 16 classes, 44 tests, 0 failures (the watch compiles and passes with the new `core`).
- `:app:testDebugUnitTest`, the whole of it: **79 classes, 163 tests, 0 failures, 0 errors, 0 skipped** (19 min 41 s).
  New: **`AdhanMigrationTest`** (the app runs the 0.26 change as it starts; from 0.25's state, a 20 s rise for every
  prayer, the change turns every rise off, keeps volume, alarms and the switch, and does not run again over a rise
  chosen afterwards); **`PrayersDesignTest`** (32 renders in `app/build/design/adhan026`: the tab off, on, and with
  the prayers differing; Fajr's, Asr's (Start softly on), sunrise's and Isha's (a notification) pages; the picker for
  every prayer, for Fajr, Dhuhr and sunrise; the devices page; in English, Arabic, light and text at 1.3, 360 dp wide;
  it also checks that the prayers say what they play, that "Full volume from the start" is shown by default, that the
  Fajr picker offers "Fajr version" and says "No Fajr version", and that differing prayers read "Mixed" and "On for
  some prayers"). `ThemeContrastTest`, `SegmentedContrastTest`, `ButtonContrastTest`, `NumeralsInStringsTest` and
  `ScreenshotTest`'s language check pass unchanged.
- **Changed assertions** (the meaning kept, the words new): `ScreenshotTest` waits for "Start softly" (27, was "Adhan
  voice") and "البدء بهدوء" (32, was "صوت الأذان"), opens "Devices and silent mode" / "الأجهزة والوضع الصامت" (was
  "Sound and devices" / "الصوت والأجهزة") and waits there for "Play in silent mode" (83, was "Gradual volume"), and
  waits for "Devices and silent mode" on 34; `AdhanDesignTest` and `PagesDesignTest` click the new name.
- `:app:assembleRelease`: BUILD SUCCESSFUL. `:wear:assembleRelease` was not run (the watch's code did not change; its
  unit tests ran).
- Every render named above was looked at (English, Arabic, light, 1.3), as a person would, twice over: after the first
  look the prayers' names were cut ("Shuru", "Maghri") and some rows' lines broke, the volume had no name, the sound's
  sign was the mosque for sunrise, and "Devices and silent mode" broke beside "Phone"; all were changed and looked at
  again.

## Not tested

- Nothing on a phone or a watch. In particular: hearing that the Adhan starts at full volume (the player is given 0, as
  before for the phones the 0.24.1 change reached; the device tests on GitHub's emulators measure playback, not
  loudness over time); "Start softly" rising over 10 s; the six seconds heard when the volume is let go; the Fajr
  version chip playing; the phone's notification sound as a preview; the migration on a phone updated from 0.25.
- A watch on 0.25 receiving 0.26's settings was reasoned from its decoder (it ignores the new key and field), not run.
- TalkBack was not run.
- The renders show today's library (sea waves, rain…); the other helper's new alert sounds will be listed from the
  library as they are, but were not rendered here.
