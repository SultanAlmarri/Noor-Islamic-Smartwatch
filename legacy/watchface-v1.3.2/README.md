# Noor Prayer Watch Face (Galaxy Watch 5 Pro, Wear OS 6)

A minimalist Islamic digital watch face in **Watch Face Format v4** (the only
watch-face format Wear OS installs since January 2026). Prayer times are
computed on the watch from astronomical formulas; one event (Fajr, Shuruq,
Dhuhr, Asr, Maghrib, Isha) is shown at a time with an exact countdown /
elapsed timer.

- Installable package: `out/NoorPrayerWatchFace-1.3.2.apk` (signed, v3 scheme, version code 7)
- v1.3.2: countdown fix - 1.3.1 spaced the countdown with the private-use character U+E001, which the watch
  drew as foreign symbols. The spacing is now the standard no-break space (U+00A0), drawn 1/32 em wide by the
  clock fonts; font files carry the version code in their names (`c01_v7.ttf`) so the watch always loads
  this release's fonts; a test forbids private-use characters outside the icon font (except the zero-width pad)
- v1.3.1: the countdown sits 16 px after the name/time block (a run of 1/32-em no-break spaces (since 1.3.2), chosen by the
  same `pick()`, absorbs the event's block width), the pair is centred and size settings move both halves
  together; the Fajr icon gains stars beside its crescent (all other icons unchanged)
- v1.3.0: **prayer name fixed** - on the watch, a `<Condition>` that reads a Reference (a computed prayer
  time) is not refreshed, so v1.2.1's name/icon branches stayed on Fajr. Now nothing but Template
  parameters read the prayer times: name, icon, time and countdown are all chosen by the same `pick()`
  expression. The icon is a glyph of `icons.ttf`, traced from the very same SVG drawings as the PNG icons.
  Countdown without seconds everywhere (`−1:07`, `−7`). New prayer layout: icon + name over time,
  countdown beside them (mirrored in Arabic)
- v1.2.1: numerals never follow the system language - numbers come only from integer data sources
  (no *_Z strings) and every font maps Western, Arabic-Indic and Persian digits (and Arabic separators)
  to the numeral system chosen in Language & numerals
- v1.2.0: countdown H:MM:SS (two-digit minutes only with hours), seconds-free Always-On countdown,
  health row kept on Always-On, larger layout, six size settings (normal + Always-On), divider removed,
  new Fajr icon; Language+Numerals and Asr+High-latitude merged (20-setting limit)
- v1.1.0: fixes whitespace inside text templates (layout shift, missing seconds), adds option pictures,
  20 clock fonts, 10 text fonts, 24 accent and 12 clock colours, and a current-event Reference with an
  inline fallback
- Install guide + verification report: the shared document (link in chat);
  accuracy tables in `docs/prayer_comparison.md`; test log in `docs/test_results.json`

## Layout of this project

| Path | What it is |
| --- | --- |
| `NoorWatchFace/` | Android Studio project (Gradle, AGP 9). Resources only — WFF has no code. |
| `NoorWatchFace/watchface/src/main/res/raw/watchface.xml` | The watch face. **Generated** — edit the generator, not this file. |
| `generator/data.py` | Cities, calculation methods, event names, adjustment range. |
| `generator/engine.py` | Prayer-time formulas, written once as an expression tree that is emitted to WFF *and* evaluated by the tests. |
| `generator/gen_watchface.py` | Writes watchface.xml, strings (EN/AR), watch_face_info.xml, copies fonts/icons. |
| `generator/wffsim.py` | WFF expression evaluator + scene simulator used by tests and previews. |
| `generator/tests.py` | 177 automated checks (countdown format, rotation, name/icon/time/countdown agreement, midnight, Arabic, AOD, settings, layout at 120 %, float32, v1.2.1 regression). |
| `generator/regression/watchface_v1.2.1.xml` | The v1.2.1 face, kept to prove the simulator reproduces the frozen-name bug and v1.3 does not. |
| `generator/compare.py`, `accuracy_report.py` | Astronomy vs PyEphem; engine vs published Dubai timetables. |
| `generator/fonts.py`, `icons.py`, `option_icons.py`, `render.py` | Fonts (tabular + Arabic-Indic variants, an empty zero-width padding glyph U+E000), icons, option pictures, simulated renders/preview. |
| `generator/icon_font.py`, `check_icons.py` | The six event icons as a font (traced from the icon SVGs) and its end-to-end check against the PNGs. |
| `reference_data/` | The published Dubai timetables used for comparison (not used at runtime). |
| `build_apk.sh` | One-command build without Gradle: generate → validate → test → aapt2 → zipalign → sign. |
| `signing/noor-release.jks` | Signing key of this build (password `noorwatchface`). Keep it: updates must be signed with the same key. |
| `renders/` | Simulated screenshots (produced by wffsim + Chromium, not by a watch). |

## How it works

* **Carriers + References.** Each prayer time (minutes after local midnight)
  is evaluated once, into the width of an invisible 1-px PartDraw, and
  published with `<Reference>` (WFF v4) as `e0`…`e7`
  (yesterday's Isha, Fajr, Shuruq, Dhuhr, Asr, Maghrib, Isha, tomorrow's Fajr).
* **Rotation - one selection for everything (v1.3).** `pick()` in
  `gen_watchface.py` returns the value belonging to the first event whose
  `time + 1800 s` is still ahead of `[SECONDS_IN_DAY]` (else tomorrow's Fajr).
  The name (`subText` over the six names), the icon (`subText` over six icon
  glyphs), the scheduled time and the countdown are all Template parameters
  built from it. **Rule: no `Condition`, `Transform` or `Variant` may read a
  Reference** - on the watch such a Condition is not refreshed (v1.2.1 bug);
  `tests.py` enforces the rule and the simulator models the watch's behaviour.
  The location/time-zone guard ("—") computes Dhuhr inline instead.
* **Countdown.** No seconds (normal and Always-On): `−M` below one hour, `−H:MM`
  from one hour; seconds are dropped (−1:07:35 → −1:07). Pieces come from
  fixed-width `subText` tables padded with an empty zero-width glyph, so the
  text never contains a visible space.
* **Numerals.** One mechanism for every number: values come from integer data sources only, and each
  font file draws every digit code point (0-9, ٠-٩, ۰-۹) in one numeral system: `<id>.ttf` Western,
  `<id>i.ttf` Arabic-Indic. Samsung Health text sent in Arabic is normalised the same way.
* **Time zone.** `[TIMEZONE_OFFSET_MINUTES_DST]` converts to the watch's local clock.

## Rebuild

```
./build_apk.sh                 # needs aapt2, zipalign, apksigner, android-23 jar, python libs (see script)
```
or open `NoorWatchFace/` in Android Studio and run `assembleRelease`
(after `python3 generator/gen_watchface.py NoorWatchFace/watchface/src/main`).
The Gradle route was not run in the build environment (no access to Google's Maven repository).

## Licences

Fonts (all SIL Open Font License 1.1, texts in `licenses/`): Readex Pro, Alexandria, Cairo, Tajawal,
IBM Plex Sans Arabic, Noto Kufi Arabic, Changa, El Messiri, Reem Kufi, Amiri, Lalezar, Vazirmatn,
Lemonada, Baloo Bhaijaan 2, Marhey, Kufam, Almarai - instanced, subset, made tabular and digit-remapped
by `generator/fonts.py` (source: github.com/google/fonts). Gradle wrapper files from
android/wear-os-samples (Apache 2.0). Icons drawn for this project.
