# The welcome of 0.25: notes

22 November 2026. The first-run welcome (onboarding) of the phone app, redesigned. The owner's words: "as if it was
made by Apple themselves ... everything from the opening animation to how settings are organized", and later the same
day: "ask yourself, is this the best we can do? Is this what Apple would do? ... I want you to finish the job."

Everything here was judged on software renders (Robolectric, the app's real code). **Nothing was run on a phone**:
Android's own permission dialogs, its "Alarms & reminders" page, and all the motion are untested there (see the end).

## What it is now

Before: one page. A dawn gradient, the Kufi wordmark at 96 sp, a tagline, and at the foot three dots, a title, two
lines and two buttons that changed in place. Pleasant, but a poster with a form under it.

Now: a welcome of pages, on one picture.

- **The picture is Minhad's icon, as a place.** The Earth's edge at first light (the launcher icon's subject, redrawn
  by the lead the same day) crosses the foot of the screen; the buttons stand on the Earth, within the thumb's reach;
  the words stand in the sky. Each page lets in more light (deep twilight on the cover, minutes before sunrise on the
  Adhan's page), and leaving the welcome is the sunrise: the words go, the Sun's edge comes over the Earth's, and the
  next page opens (0.6 s). Nature only: sky, stars, light, the Earth.
- **The cover** says what Minhad is: the mark (crescent and star), the Kufi wordmark as artwork ("MINHAD" under it in
  English only), one large line ("Prayer times from the sky above you") and four things it gives, each a small tile
  of colour with a sign, a few bold words and one quiet line: exact prayer times; the sky is your clock; the Adhan, on
  time; Qibla and Galaxy Watch. One button: Continue. The cover asks for nothing.
- **Then one page for each thing Minhad asks for**, each with a symbol drawn in code (a disc of glass, a gold sign,
  its own signal round it), a large title, the honest reason, the main button in gold and a quiet second choice:
  1. the place: "Use my location" / "Choose a city instead";
  2. keeping it right when travelling (background location): "Continue" / "Not now";
  3. the Adhan: "Turn on the Adhan" / "Not now";
  4. only when it is needed, exact alarms: "Allow exact alarms" / "Not now" (new, see below).
- **Progress** is four dots on the Earth (the exact alarms' page is the second half of the Adhan's: the fourth dot
  stays lit), and the sky itself.
- **Motion** (when Android's "Remove animations" is off): on a first start the mark is drawn where the launch screen
  drew it, behind the launch screen's dissolving veil, and settles from there into its place on a spring; the name,
  the line, the four rows and the button follow one after another (about 1.8 s in all) while the sky comes out of the
  night. Pages turn in the reading direction (from the right in English, from the left in Arabic) on a spring; each
  page's symbol grows into place and its words rise; the buttons' words change with the page. The stars twinkle and
  each symbol's signal moves (rings from the pin, steps along the way, the call's waves, the clock's marks). The main
  button gives a little under the finger; every button ticks (the app's haptics). With "Remove animations" on,
  nothing moves: the next page is simply there (a test checks it pixel for pixel).

## What changed in what the welcome does (the logic)

The steps, the permissions, the moment each is asked and what each answer records are as they were (the reason is on
the page before Android's dialog; `setLocationMode(AUTO)`, `setBackgroundUpdates`, `setAlertsEnabled(true)`,
`completeOnboarding`, then the home screen, or the location page when the place is to be chosen by hand; the step
survives the permission dialog and a rebuilt activity). These are the differences:

1. **A cover before the first question.** One tap more. Its reason: the old page asked for the location in its first
   second, before saying what the app is.
2. **The way back.** Android's back gesture returns to the page one came from (it used to leave the app from every
   step). On the cover it still leaves the app. Going back from "Choose a city instead" and then using the location
   ends on the home screen, not the location page. Going back from the exact alarms' page to the Adhan's and choosing
   "Not now" there turns the Adhan off again.
3. **Exact alarms (the lead's release audit).** After "Turn on the Adhan" and the notification permission's answer,
   if Android does not let Minhad ring at an exact time (`AlertScheduler.canScheduleExact`, Android 12 and later;
   off by default for a new install since Android 14), one more page says why and its main button opens Android's
   own page for this app (`Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, as the Adhan tab's fix does). Coming back
   from that page, whatever was chosen there, ends the welcome; so does "Not now" (the Adhan stays on; the Adhan tab
   still shows its warning). It is never shown when exact alarms are already allowed or below Android 12. If a phone
   has no such page the welcome simply ends.
4. **The welcome is left only once it is recorded as done.** The record is written in the page's own scope, which
   ends when the page is left; with the 0.6 s way out, `MainFlowTest` caught the welcome left unrecorded in the test's
   compressed time. The navigation now waits for the write to finish (a few milliseconds on a phone).
5. **The texts are true (the lead's release audit).**
   - Location: "It stays on this phone" was not true (Android's geocoder, the map server when the map is open, the
     user's own watch, Android's backup). It now says: "Minhad uses your location to work out your prayer times. It
     has no account and no server of its own: your location is never sent to Minhad." The unused string
     `onboarding_private` ("Your location stays on this phone.") is removed.
   - Background location (`bg_perm_body`, also the dialog in Settings; the names are kept): "Minhad uses your
     location in the background, even when the app is closed or not in use, to keep prayer times right when you
     travel. To allow it, choose “Allow all the time”." (Google Play's prominent disclosure: location, background,
     closed or not in use, what for.) The sentence about the battery went, to keep it short.
   - Both in Arabic too.

## Decisions the owner may want to change

- **The cover costs one tap.** Starting at the place's page instead is a small change.
- **The four things named on the cover**, and their words. They are short on purpose (each line fits a small phone).
- **Gold for the main button** (it was white): gold is the app's colour for the one thing to do on a page.
- **The tiles' colours** (orange, blue, green, violet, as an iPhone's Settings has them). Apple's own welcome sheets
  use one tint for all four signs; one gold tint is a small change if the colours feel like too much.
- **The Adhan's symbol is Minhad's crescent with sound going out from it**, not a bell and not a mosque (no
  buildings in pictures; a bell is not the Adhan).
- **The Kufi wordmark stays** as artwork in both languages, smaller than before (50 sp, 96 before), under the mark.
- **On a short screen the words come first.** The symbol has the size the page with the most words leaves it, the
  same on every page; on a small phone with large text there is no symbol at all, so that the reason is read whole
  without scrolling before Android is asked.
- **"Not now" on the exact alarms' page leaves the Adhan on** (possibly a few minutes late; the Adhan tab says so).
- **The exact alarms' page keeps the fourth dot** rather than adding a fifth that most people never see.

## "Would Apple ship this?": what was judged, what was fixed, what still falls short

Every final render was looked at (7 screens x 2 languages x 5 pages, the sky's stages, frames of the motion) and
asked that question. Found and fixed on the way:

- The first version centred each page's block, so the symbol jumped from page to page and changed size with the
  length of the words. Now it has one size and one place on every page (measured from the page with the most words).
- Titles broke with one word left alone ("... where / you are"). Titles and reasons now break into balanced lines.
- On a small phone with large text the reason was cut exactly between two lines and looked whole. The symbol now
  steps aside so the words fit; where a page must scroll, its last lines fade so that it does not look whole.
- A phone on its side had 120 dp left for the page. It now has a thinner band of light, the two buttons side by
  side, and the cover's four rows two and two.
- An unfolded Fold got the phone's sizes in a wider column; it now has larger type and the cover fits without
  scrolling. Turned (841 x 673), it has the two-and-two cover.
- The glow of first light reached into the words on the thin band (4.2:1): the contrast test caught it; the glow's
  height now goes by the band's.
- With "Remove animations" on, the page before stayed for a few frames over the next one (the test caught it).
- The mark crossed the name's place while the name was already coming in; the name now waits for it.

Where it still falls short of Apple, honestly:

- **The motion has only been seen as still frames.** Springs, timings and the mark's journey from the launch screen
  are reasoned, not felt. This is the part most likely to need tuning on a phone.
- **The symbols are plain geometry** (a pin, steps over a round Earth, a crescent with waves, a clock). Apple would
  have richer drawn or animated symbols; these are clean, not special.
- **The glass is a tint, not glass**: nothing behind it is blurred.
- **The tiles' signs are Google's Material icons** (the sky's is Minhad's own); they are close to, not the same as,
  the family of signs an Apple app would draw for itself.
- **Wide screens get a wider single column**, not a layout designed for them (an iPad's welcome is a sheet in the
  middle of the screen).
- **Large text on a small phone** shows the cover's rows only by scrolling (the last one fades), and no symbols.
- **Large empty sky under short pages** (the Adhan's): calm, but the eye has a long way down to the buttons.
- **TalkBack** is told each page's title as it comes and the title is a heading; it was not listened to on a phone.

## What was tested, and how

All on this cloud machine, Robolectric with native graphics (software renders and the app's real code; no phone, no
emulator).

- `WelcomeDesignTest` (new, `app/src/test/java/com/noor/app/design/`): one test, **passes**. It
  - draws **94 pictures** to `app/build/design/welcome`: the 5 pages in English and Arabic on 7 screens (412 x 915;
    the same with text at 1.5; 360 x 640; the same with text at 1.3; an unfolded Fold 673 x 841; the Fold turned
    841 x 673; a phone on its side 915 x 412) = 70; the sky alone at 6 stages; 16 frames of the way in, a page
    turning and the way out; 2 frames of the stillness check;
  - **contrast, on the drawn pixels**: the sky is drawn alone (without stars) at 17 stages from the night before the
    cover to the last page, with the full band and with the thin one, and every pixel of the part that can hold words
    is compared with white, with the quiet white (88 %) laid over it, and (on the cover's stages) with the gold of
    the name; and under every text of all 70 pages the page's own sky is compared with that text's colour. The least
    found: white 8.09:1, the quiet text 6.71:1, both in the last page's light (4.5:1 is the rule). The main button's
    ink on its gold is computed from its two colours;
  - on every page: the main button and the second one are whole on the screen; on the pages that ask for something
    every word is on the screen without scrolling; exactly one heading; English shows no Arabic letters but the
    wordmark, Arabic no Latin letters but "Galaxy Watch";
  - **what it does**, with the permissions granted beforehand so that each request answers at once: every answer
    yes records the place from the phone, the background check, the Adhan and the welcome as done, and opens the
    home screen; "Choose a city instead" then "Not now" opens the location page; the way back at each page, and that
    on the cover nothing holds the back gesture; exact alarms not allowed: the page comes, its button starts
    `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` for this package, the welcome waits, and the answer's return ends it;
    "Not now" there ends it with the Adhan on; back and "Not now" on the Adhan's page turns the Adhan off;
  - with ambient motion off, the screen three frames after a tap and four seconds later are the same pixel for pixel.
- `MainFlowTest` (changed): the real `MainActivity` from a first run; it now taps "Continue" on the cover first,
  checks that the cover asks for nothing and that the location's reason is on the page before anything is asked,
  then as before ("Choose a city instead", "Not now" leaves the alerts off, the location page stays). **Passes.**
- `ScreenshotTest` (changed: shot 42 waits for the cover's line instead of "Use my location"; still 62 pictures) and
  `PagesDesignTest` (unchanged): **pass**.
- The whole of `:app:testDebugUnitTest`, run once at the end on this branch as committed (so with the mark this
  branch has, not the lead's new one): **133 tests in 62 classes, none failed, none skipped**. The release builds were
  not made here.

## What was not tested

- **Anything on a phone or an emulator.** In particular: Android's location dialogs (precise/approximate, "Allow all
  the time" on Android 10 and on 11+, where it is a settings page), the notification dialog, and Android's "Alarms &
  reminders" page and the return from it (here the result is handed back by the test); a denied permission (here they
  are all granted; a refused location goes where "Choose a city instead" goes, and a refused background location
  records "off" and goes on, as before, but neither was driven);
  the activity being rebuilt while a dialog or Android's page is open (the step is saved and the launchers are the
  kind that survive it, as before, but it was not exercised).
- **All motion as motion**: the launch screen's hand-over to the mark, the springs, the sunrise on the way out, the
  twinkle; and its cost in frames (the sky is redrawn every frame while ambient motion is on: `smooth.yml` was not
  run for the welcome).
- **The haptic ticks, TalkBack, a real Fold's hinge and cover screen, tablets, Android below 14.**
- The pictures were made with the lead's new mark (`ic_splash_mark.xml` from the main checkout, not yet committed)
  copied in for the renders only; this branch does not contain it.

## For whoever merges this

- Files: `ui/onboarding/OnboardingScreen.kt` (rewritten), `WelcomeSky.kt` and `WelcomeArt.kt` (new), the two
  `strings.xml` (changed in place: `perm_body`, `bg_perm_body`, `onboarding_tagline`, `onb_adhan_body`; removed:
  `onboarding_private`; added in one block at the end: thirteen `welcome_*`), `WelcomeDesignTest.kt` (new),
  `MainFlowTest.kt`, `ScreenshotTest.kt` (one line).
- `WelcomeArt.kt`'s `Mark` holds where the crescent sits in `ic_splash_mark` (centre 52.5, 46, radius 22 of 108, from
  `tools/brand/logo.py`). If the mark's geometry changes there, change it here, or the mark will sit off its place.
- `bg_perm_body` is also the text of Settings' background-location dialog: it is longer now.
- `docs/DESIGN.md` ("Motion": "onboarding opens on a sunrise (3.2 s)") and `docs/UI_014_NOTES.md`'s row on the
  welcome describe the old page; they were not edited (outside this work's files).
