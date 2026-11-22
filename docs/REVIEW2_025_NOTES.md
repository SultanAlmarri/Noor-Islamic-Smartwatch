# The second review (0.25.0, 22 November 2026)

After every part of 0.25 had been merged, a second independent reviewer, who had not seen the first review, judged
about 170 renders of the finished phone app against the owner's standard ("as if Apple made it ... is this what
Apple would do?"): **"about nine-tenths of the way"**. At the level: the home screen (also on a small phone, the
Fold, large text, Arabic), the welcome ("the most Apple-like part"), the month's timetable and date sheet, the Qibla
compass, Settings' first page, the alarm editor, the Adhan tab's states with its one Fix sheet, Arabic mirroring.
It listed 30 findings. All three "must" and every "should" were acted on; the "polish" ones too, except two left on
purpose (below).

The work was split: a helper began the shared parts and was stopped by a usage limit halfway (the session went on
with another model, Claude Opus, which finished it); two helpers took the Adhan area and the location, accuracy and
Qibla pages (their own accounts: ADHAN_025_NOTES.md and PAGES_025_NOTES.md, "Second review"); the lead the rest.

| # | Finding | What was done |
|---|---|---|
| 1 (must) | The floating tab bar was see-through: a table's figures were read through it and ran into the tabs' names | The bar is all but solid (`NoorColors.chrome` 97%), and on every page that scrolls its foot fades into the page's own colour before it reaches the bar (`TabBarFade`); pages keep 24 dp more so their last row scrolls clear (`TabBarClear`). Not on the home screen, whose day stands right over the bar. No blur: it cannot be checked without a phone |
| 2 (must) | The page bar was see-through: content showed through its title | The bar is the sky's own colour, opaque, and there within 2 dp of scrolling (8 dp on a tab, as the large title goes under) |
| 3 (must) | Numbers written into sentences ("لمدة ٣٠ دقيقة") ignored the numerals setting | Every such sentence takes its number formatted with `Formatter.number` (`rememberAppFormatter` for pages without settings at hand); `NumeralsInStringsTest` fails if an Arabic string holds an Arabic-Indic digit again (two samples of the choice excepted) |
| 4 | The second kind of button read as disabled | One second button everywhere: the text's ink on a neutral fill (white 12% / black 6%); disabled is the whole button at 38%; `NoorButton(compact = true)` for 36 dp pills; `ButtonContrastTest` |
| 5 | Small sentence-case labels letter-spaced (a stock-Material tell) | No sentence-case style is spaced; only the capital section headers |
| 6 | The accuracy page was a wall of text | A top card with the measured figures of the method in use (only where the project measured them, each figure checked against its own statement), then every method as one row opening to its statement word for word (`AccuracyStatementsTest`) |
| 7 | Raw romanised place names ("Aḑ Ḑaghāyah"), "District" under every row | Plain letters in English for Arabic-script countries (display only; search finds both); one heading over the districts; the confirm page leads with the city |
| 8 | The data-licence footnote on the location page | Removed there; it is under About Minhad → Acknowledgements; the map's own attribution stays |
| 9 | Home screen settings: two heading styles, too much explanation | One heading style, "Scenery" over the cards, one line under each control, "Only nature..." gone |
| 10 | A page holding one switch | The countdown notification's page shows the notification itself, counting (`StatusPreview`, from the code that builds the real one); the row is "Countdown notification" |
| 11 | The watch page led with an invitation to design a face before the watch had Minhad | The set-up comes first until it is done; the Studio waits under it, picture and name only; while the Studio face is on the watch the ready-made faces' sample pictures wait behind one button |
| 12–17, 23, 24, 26 | Alarm rows, prayer rows' chevron, the ringing screen, the voices, dangling dots, "Length", empty states | ADHAN_025_NOTES.md, "Second review" |
| 15, 16, 19, 21, 22, 29 | The Fold's width, the map's controls, time zones, the Kaaba's glyph by day, "calibrate" said three times, no compass | PAGES_025_NOTES.md, "Second review" |
| 18 | The drag hint on a short sky | Not shown where the sky is too short for it (it already goes for ever at the first drag) |
| 20 | "Dates from 2021 to 2035" | Gone; the arrows and years stop there |
| 25 | The welcome's gradient gold | The app's one flat gold |
| 28 | By day the passed prayers too faint | `textSecondary` by day |
| 29 | Wording | "Asr method", "Adjust Hijri date", prayer-time accuracy in one place (the Prayer times page) |

**Left on purpose**: the Today widget's empty band (27: the widgets were redesigned in 0.24 and are measured on a
launcher by `widgets.yml`, not here) and the notification's line breaks on a small shade (23, third item: Android
draws the notification). The hero's "Next: / Fajr 5:20 AM" on two lines at font scale 1.3 stays.

**Tested**: see HANDOVER.md section 42. Software renders and unit tests; nothing on a phone.
