# Noor design language

Version 0.8.0. How Noor looks and moves, and why.

## Idea: the sky is the clock

Prayer times are positions of the sun: Fajr when it is about 18° below the horizon, Shuruq when it rises, Dhuhr just
after its highest point, Asr when shadows reach a set length, Maghrib when it sets, Isha when the last light goes.
So Noor shows the sky.

- **The Today screen opens on the real sky above the user**: its colour comes from the sun's elevation at that place and
  moment (`SkyPalette`, shared by phone and watch), cooler at dawn and warmer at dusk, with stars appearing in twilight
  and the moon, shaped by the Hijri date, riding the sun's own arc as far behind it as it is old.
- **The sun path** across the sky is the day's actual elevation curve, with each prayer placed where it happens on it.
  The time axis is stretched smoothly so daylight takes most of the width (the vertical positions are the true
  elevations). The day so far is drawn solid, the rest dotted; the night part runs under the land; the next prayer
  wears a gold ring that breathes slowly.
- **Under the sky is a landscape, never a city**: nothing man-made and no people, only land, air and light (see "The
  home screen" below).
- **Everything else is calm**: one accent (gold, Noor's light), solid surfaces, quiet section titles. The sky is the
  only place with colour, so it reads as the heart of the app.

## The home screen

The Today screen is one scene, drawn in code from the sun's real position (`ui/today/LivingSky.kt`), in layers from
far to near:

1. **Sky**: the live sky colours; warm air gathering round a low sun; at night stars that twinkle, a faint Milky Way,
   and now and then a meteor high in a corner of the sky, clear of the countdown.
2. **Clouds**: a few fair-weather cumulus low over the land and thin high streaks, different each day and place
   (fewer over the desert). Each is painted as light in vapour, not outlined: white tops and blue-grey bases by day,
   dusky mauve with peach and gold undersides towards a low sun, thin veils at night. They drift slowly with the wind
   and never rise into the text.
3. **Sun**: its halo and long, faint, slowly turning rays (longer and warmer when it is low), the path, and the sun.
4. **Land**: four ridges fading with distance (atmospheric perspective). Arid countries get dunes (long windward
   slopes, steeper slip faces, warm sand); elsewhere far mountain ridges soften into rolling hills. The shape is fixed
   for a place, so home always looks like home. The ground takes the colour of the moment (sand by day, rose at sunset,
   deep blue at night), crests facing a low sun catch its light, mist lies in the valleys at dawn, and the nearest ridge
   fades into the page, so the day's times sit on the ground under the sky.
5. **Over the land**: the night part of the path, the prayers on it, and at dawn and dusk a small flock of birds now and
   then.

**Touch**: drag sideways across the sky to move the sun along its path. The sky, clouds and land relight as it moves,
it catches on each prayer with a haptic tick, and the header shows that moment instead of the countdown: the sun's
elevation, the prayer it has caught on, the clock time and how long ago or ahead that is. Let go and it springs back
to now. The countdown and the timeline are unchanged by it, and nothing is lost without it (TalkBack reads the sky's
description, which mentions the gesture).

**Calm and cost**: the scene changes once a minute, not with each tick of the countdown; paths are built once per size;
the ambient clock ticks about 30 times a second rather than every frame, rests when the sky is scrolled out of sight
or the app is in the background, and stops entirely (with a still, complete scene) when "Remove animations" is on.
Short screens (under 800 dp tall) get a more compact sky and countdown so more of the timeline shows.

## Appearance

Night (ink and gold), Day (warm paper, ink, deeper gold), System, and **Follow the sun** (the default): light from
sunrise until Maghrib at the user's place, dark through the night.

## Type

IBM Plex Sans Arabic for Latin and Arabic text (one voice in both scripts; tabular figures for every time), Reem Kufi
for Arabic display names and the wordmark. The countdown is Plex Light at 78 sp; it follows the system font size but is
capped so it always fits on one line. Each language shows only its own words (0.9): no Arabic beside English names and no English beside Arabic ones; the
Kufi "نور" logo is the one exception, as artwork. `ScreenshotTest` and the watch screen tests fail if a screen shows
letters of the other script, apart from names that are not language (devices, licences, web addresses, credited people).

## Colour

| Token | Night | Day | Use |
| --- | --- | --- | --- |
| background | `#080C14` | `#F4F1EA` | page |
| surface / raised | `#121826` / `#1A2232` | `#FFFFFF` / `#FBF6EA` | grouped lists, cards |
| gold / goldText | `#E3C27E` / `#E8CB8E` | `#A8792C` / `#8A6524` | accent, selection, actions |
| text primary / secondary / muted | `#F5F2EA` / `#A9B1C1` / `#8A93A4` | `#17191F` / `#575E6B` / `#676D78` | |

Every text colour meets WCAG AA (4.5:1) on every surface in both themes, white on the sky meets 7:1 at every solar
elevation, and gold on the sky 4.5:1 (`ThemeContrastTest`, `SkyPaletteTest`).

## Components

- **Large screen titles** on every tab; sub-pages use a back arrow and a title.
- **Grouped lists**: rounded groups, an icon in a soft gold tile at the start of each row, dividers aligned with the
  text, quiet uppercase section titles, footnotes under groups.
- **Pills** for small actions (gold text on a soft gold pill) with full 48 dp touch targets; **solid gold buttons** for
  the one main action on a screen.
- **The timeline** (Today): a vertical line through the day's moments; past ones quiet, the current one lit, the line
  filling with gold towards the next prayer as time passes; "Now", "in 2 h 11 min", and in Ramadan "Suhoor ends" and
  "Iftar".
- **The Qibla dial** turns with the phone; a beam of light runs from the centre to the Kaaba on the rim, as wide as the
  compass is uncertain (the sensor's own estimate), turning green with a haptic tap when the phone faces the Qibla.
- **The watch preview** (Watch tab) is a drawn watch showing exactly what the Noor watch app shows now, with the glow
  of the current sky under it.
- **Tab bar**: five destinations; the selected one gold with a soft pill behind its icon and a light haptic tick.
  The Today icon is Noor's sun path.

## Motion

Every animation has one of three jobs: show where something came from, confirm a change, or keep the sky alive.

- Entrances: the day's path draws itself from midnight to now and the sun travels into place (1.2 s); onboarding
  opens on a sunrise (3.2 s).
- State: countdown digits roll; the next prayer's name slides in when it changes; tabs cross-fade; pages slide in from
  the reading direction; the "Today" pill appears only when another date is shown.
- Ambient (home screen): clouds drift, stars twinkle, the sun's rays turn, the next prayer's ring breathes, birds and
  meteors pass now and then. All of it stops when Android's "Remove animations" is on.
- Interaction (home screen): drag the sun through the day; it springs back to now when let go.
- Scrolling: the sky and clouds move at about half speed behind the land and the content (parallax) and the header
  text fades away.

## The watch

Same family on a black OLED screen: the same typeface, the same order (prayer, countdown, time), gold for what matters,
and a soft glow in the colour of the current sky behind the countdown (blue by day, amber at sunrise and sunset, none at
night). The interval ring keeps a gap at the top for the system clock.

## Arabic and right-to-left

Layouts mirror, including the sun path (time flows from the right) and the timeline; numbers, times and the countdown
always read left to right. Arabic display names use Reem Kufi; the Hijri date leads in gold.
