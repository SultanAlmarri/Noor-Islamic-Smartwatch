# Noor design language

Version 0.9.2. How Noor looks and moves, and why.

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

The Today screen is one scene, drawn in code from the sun's real position (`ui/today/LivingSky.kt`), above the day's
times. Since 0.9.2 the six times always fit above the tab bar without scrolling at the default text size: the date
bar and the rows take what they need and the sky takes the rest (`HomeLayout`). Tests render the screen on 360 × 640,
360 × 740 and 412 × 915 dp phones and fail if any prayer is cut off. Shuruq, which ends Fajr's time but is not a
prayer, has a slimmer, quieter row. How the times are calculated (the authority, the official timetable) is in
Settings, not on the home screen.

The header, top to bottom: the place, the next prayer with its time on the same line, the countdown, then the sky.
The text is measured once laid out, and the noon Sun always sits below it. On short screens the place shows on one
line, and on very short ones the prayer and the countdown sit side by side, so the arc keeps its room.

The scene (0.9.6), far to near:

1. **Sky, computed, not painted** (`Atmosphere.kt`): sunlight scattered by the air (blue first), by haze (the glow
   round the Sun) and partly absorbed by ozone (which keeps the sky blue after sunset), over a round Earth whose shadow
   rises opposite the Sun at dusk with the pink Belt of Venus above it. One table of colours per moment (48 × 32,
   about 4 ms on a computer, computed off the main thread), with a daylight white balance, an exposure that follows
   the Sun down through twilight as the eye adapts, and a filmic tone curve. Where text is written on the sky, a
   graduated darkening (as a photographer's filter) keeps white text at 4.5:1 (3:1 for the large countdown) at every
   height of the Sun; `AtmosphereTest` checks it from −40° to 90°.
2. **Night** (0.9.8: the real sky, `NightSky.kt`): the 1,600 stars brighter than magnitude 5 of the Yale Bright Star
   Catalogue (public domain, `res/raw/stars.bin`, built by `tools/sky/build_stars.py`) at their true places for the
   user's position and the moment shown, sized by brightness and tinted by temperature; fainter stars on the turning
   celestial sphere; the Milky Way where it really is (galactic coordinates: the bright centre in Sagittarius, the
   Great Rift); the airglow low down on a dark night; now and then a shooting star. The view faces the side where the
   scene's Sun rises east (south when the scene runs left to right, north in Arabic, where the Pole Star and the Plough
   show): a real, unmirrored sky. The moon on the Sun's own path, as far behind it as it is old, lit as a sphere from
   the Sun's side (its phase is real), with earthshine and a halo; by its light (how much depends on the phase) the sky
   turns a deep blue, clouds are silvered and the land shows its shapes in silver-blue. Dragging the Sun through the
   night turns the stars with it. `NightSkyTest` checks sidereal time against Meeus and known stars (Polaris, Sirius,
   the galactic centre) where an almanac puts them.
3. **Clouds**: high cirrus combed by the wind (lit pink long after sunset), distant cloud banks along the horizon, and
   (0.9.8) real cumulus. Eight clouds (flat fair-weather cumulus, taller ones with towers, small puffs, a fragment) are
   rendered once, offline, as volumes of vapour (`tools/sky/build_clouds.py`: a heap of billows worn by Perlin-Worley
   and Worley noise, light followed through the volume with multiple scattering) and stored lit from six directions
   (`res/raw/clouds_a.png`, `clouds_b.png`, as games light smoke). The sky shader blends the six by where the Sun
   (at its true elevation) or the moon stands relative to each cloud, adds skylight, the forward scattering that silvers
   thin edges, and haze with distance: a cloud beside the Sun is lit on that side, one in front of a low Sun glows at
   its edges, after sunset they are lit from below; at night they are dim silhouettes or moonlit. Farther clouds are
   smaller, lower and slower; the weather changes from day to day and place to place (fewer over the desert); clouds
   keep to the band of sky below the text (`CloudAtlasTest` checks every cloud's height against the shader). They are
   sharper than 0.9.6's clouds and far cheaper to draw (the sky shader halved in size).
4. **Sun**: a disc with limb darkening, its colour the sunlight that reaches the ground (white at noon, orange-red at
   the horizon), a bright core of glare and a wide soft bloom. It sinks behind the true horizon and behind the land.
5. **Land** (`LandProfile.kt`, `land_layer.agsl`): five layers fading into the haze with distance, the nearest the
   page itself. Four sceneries (0.9.8, Settings → Home scenery, or Automatic by place): Dunes, Hills, Mountains (sharp
   ranges with snow on the summits that takes the colours of sunrise and sunset) and Sea (the water mirrors the sky,
   the Sun's path glitters on it, the moon lays a glade, a pale beach with grass in front). Automatic: high places
   (1,400 m and above) and alpine countries get mountains, islands the sea, arid countries dunes, the rest hills. Hills: a mountain range of several summits far away, rolling wooded hills nearer, tree crowns along
   the crests. Dunes: from each summit a sharp crest runs down across the dune, so a low Sun lights the windward face
   and leaves the slip face in blue shade; faint ripples on the nearest. Lit per pixel from the Sun's side, a rim of
   light along crests with the Sun low behind them, shadows of passing cumulus, mist in the valleys at dawn.
6. **Over the land**: the sun path (behind the land, gold where the day has passed) and the prayers: on the path while
   the Sun is up, on the skyline at Fajr, Shuruq, Maghrib and Isha. The next prayer's ring breathes; a mark fills as
   the Sun passes it, over a few minutes rather than at once. Birds at dawn and dusk.

**How it is drawn**: on Android 13 and later by two GPU shaders (`res/raw/*.agsl`, AGSL): the sky at about 1.5 pixels
per dp in a cached layer scaled up (it is soft everywhere), the land at full resolution (the skyline must be crisp),
each redrawn only when what it shows changes. Below Android 13, or if a shader cannot be built, the Canvas renderer
draws the same computed sky with its own clouds and land. The shaders are developed with a browser bench that compiles
the very same files (WebGL), and checked on Android 14 and 15 emulators in CI (`home-sky.yml`).

**Opening** (0.9.6): the day's sky comes in. The scene fades up from the page while the Sun runs, moving off gently and
settling slowly, from the start of the current prayer's time (at least 50 minutes, at most 6 hours back) to this
moment: sunrise into morning, dusk into night with the stars coming out. The clouds run fast and slow to their real
drift, the land rises a little, the path and its prayers draw themselves. About two to three seconds, once per launch;
if the full opening was seen in the last half hour, only a brief fade. A touch on the sky takes over at once. The
prayer, its time and the countdown are the real ones from the first frame. Off under "Remove animations".
`TodayOpeningDesignTest` renders frames through it.

**Touch** (0.9.6): drag sideways across the sky to move the Sun along its path. It follows the finger on a quick,
critically damped spring (no jump when the finger lands far from it). Each prayer is a soft detent: near a mark the Sun
slows and rests exactly on the prayer (a haptic tick, the header shows the prayer and its time), then moves off gently;
nothing snaps, so the sky never jumps (`SkyDetentTest`). Twilight has more of the width than before (dragging through
dawn and dusk changed the colours up to nine times faster per pixel than at midday; now about a third less). The scene
relights continuously; it springs back to now when let go.

**Calm and cost**: the scene's moment changes once a minute; the sky layer's clock ticks about 15 times a second for
drifting clouds and twinkling stars, the land's 4 times a second; both rest when the sky is scrolled away or the app is
in the background, and stop entirely under "Remove animations". While the Sun is dragged or during the opening, both
layers redraw every frame.

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
- **The countdown** (0.9.7): "−1:23:45" before a prayer (a true minus sign), "+04:12" for a while after it, the same
  on the home screen and in the notification; the title then names the prayer that has come.
- **Choosing a location** (0.9.7, [LOCATION.md](LOCATION.md)): one search box first; then four calm rows (current
  location, map, coordinates, browse) in one group; every path ends on a confirmation card with the point on a
  satellite picture, the time zone in words and today's times, and one gold "Use this location" button. The map is
  full-bleed with a gold pin fixed at its centre (it lifts while the map moves), round floating buttons on the side,
  and the place under the pin in a card at the bottom. In Arabic, coordinates read "25.20480° ش، 55.27080° ق" and the
  time zone is named in Arabic.
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
