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
3. **Clouds and weather** (0.9.9): real clouds, cut out of public-domain and CC0 photographs from Wikimedia Commons
   (`tools/sky/photo_clouds.py`; the photographs, their authors and licences in `tools/sky/photos/manifest.json`):
   fair-weather and middling cumulus, towering cumulus and thunderstorms with their anvils, ragged fractus. Each is
   separated from the sky it was photographed against (the sky's blue fitted across the picture, the cloud's opacity
   solved per pixel), kept as its own light and shade, and recoloured on the phone with the light of the moment from
   the same atmosphere as the sky: white with blue-grey shade by day, gold at sunrise and sunset, flat grey under an
   overcast, dim and moonlit at night; its lit side is turned to the Sun; far ones take the colour of the air.
   Placement is true perspective: each cloud has a real width and distance (6 to 110 km), so small clouds crowd in
   rows towards the horizon and larger ones stand nearer and higher; towers and storms rise from behind the land.
   The **weather** (`Weather` in `SkyLook.kt`) gives each day at each place a character — clear, fair, convective
   (cumulus building into towers and afternoon thunderstorms), stormy, layered, overcast, rainy or mixed; deserts are
   more often clear, mountains more often stormy — and lets it evolve hour by hour with smooth randomness: heaped
   clouds build through the day and melt at night, storms come late in the afternoon and linger into the evening, an
   overcast thins and thickens. Clouds come in isolated, clustered and broken arrangements; under showery clouds hang
   shafts of rain, or virga that evaporates before it reaches the ground; thunderstorms flash now and then (the cloud
   lights up violet-white, often a stroke to the ground); rain falls in front of the land; a layer of stratocumulus
   and a grey overcast deck are drawn by the sky shader, with high cirrus and distant banks along the horizon. Each
   cloud fades in and out on its own as the weather changes, so dragging the Sun through the day never makes the sky
   jump (`CloudFieldTest`). Clouds keep clear of the text written on the sky by fading out below it.
   0.9.8 sampled its clouds for every pixel of the sky, 15 times a second, in the sky shader; now the phone draws a few
   dozen pictures through one colour matrix each, the same on every phone, and the sky shader (about a third smaller,
   its overcast and cloud-layer parts skipped when the weather has none) is redrawn once a second by day.
4. **Sun**: a disc with limb darkening, its colour the sunlight that reaches the ground (white at noon, orange-red at
   the horizon), a bright core of glare and a wide soft bloom. It sinks behind the true horizon and behind the land.
5. **Land** (`LandProfile.kt`, `land_layer.agsl`): five layers fading into the haze with distance, the nearest the
   page itself. Four sceneries (0.9.8, Settings → Home scenery, or Automatic by place): Dunes, Hills, Mountains (sharp
   ranges with snow on the summits that takes the colours of sunrise and sunset) and Sea (the water mirrors the sky,
   the Sun's path glitters on it, the moon lays a glade, a plain beach of dry and wet sand in front). Automatic: high places
   (1,400 m and above) and alpine countries get mountains, islands the sea, arid countries dunes, the rest hills. Hills: a mountain range of several summits far away, rolling wooded hills nearer, tree crowns along
   the crests (0.9.9: soft groves and meadows from afar, round tree crowns only on the nearest ridges; 0.9.8's noise
   made square blotches). Mountains are shaded and snowed by altitude (the snowline is a height, ragged in the
   gullies), not by depth below each jagged crest, which made vertical bands. Dunes: from each summit a sharp crest runs down across the dune, so a low Sun lights the windward face
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

**Calm and cost**: the scene's moment changes once a minute; the clouds redraw about 15 times a second as they drift,
the sky shader once a second by day (15 times at night, for the twinkling stars), the land 4 times a second; both rest when the sky is scrolled away or the app is
in the background, and stop entirely under "Remove animations". While the Sun is dragged or during the opening, both
layers redraw every frame.

## The real sky (0.10)

The owner's brief (13 November 2026): the home screen should look *real*, not drawn. "When someone opens Noor, they
should see a real place through a window, at this moment, with a sky more beautiful than they expected." The 0.9
clouds were photographs sliding across the sky and the land was layers of flat colour. 0.10 replaces the picture with
a small 3D renderer of its own (OpenGL ES 3.0, `com.noor.app.sky`, shaders in `app/src/main/assets/sky`). The 0.9
scene described above is kept, untouched, as the fallback ("Classic") where the new one cannot run.

**The whole screen is the window.** The scene fills the screen behind everything. The next prayer and the countdown
are written on its sky; the day's six times lie on frosted glass over its lower part. The sun path and the prayers
on it are drawn over the scene as before.

**What is drawn**

- **Clouds as volumes.** Clouds are 3D bodies of mist (a 128³ noise volume built on the GPU), marched through for
  every pixel and lit by the real Sun or moon: bright where they face the light, grey in their own shade, silver-edged
  near the Sun, their crevices darker than their crests, their bases lit from beneath at sunset. Heaped clouds stand
  on flat, level bases and are heaps (as tall as their ground is wide; where cloud is sparse it stays low), towers
  are mountains of cloud on wider ground, a thunderstorm spreads an anvil; a sheet of stratus covers the sky. Middle
  clouds (altocumulus in flocks or rows, each cloudlet with a lit side and its own shade; altostratus) and high ice
  clouds (cirrus streaks, a cirrostratus veil with
  its 22° ring) are each lit by the Sun as it stands at their own height, so high cloud glows after the low cloud has
  gone dark. Clouds throw shadows on the land and are mirrored in water.
- **One Sun, one air.** The 0.9.6 atmosphere (air, haze, ozone, the Earth's shadow) now runs on the GPU in linear
  light and lights everything: the sky, the clouds, the land, the water, the mist. Haze lies low, so a far peak stands
  clear above a hazed foot; the air in front of far land is a little bluer and dimmer than the sky behind it, so ridge
  stands out from ridge.
- **Six sceneries**, each a real 3D landscape with its own camera (`Biome`, `land_*.glsl`): **Arabian dunes** (a sand
  sea from the crest of a high dune: smooth sinuous crests, slip faces, ripples underfoot, star dunes on the
  horizon), **Pacific Northwest rainforest** (tall firs framing a sea inlet, forested headlands in the mist, a
  snow-capped volcano with a broad broken summit), **Arctic** (a frozen sea: floes, leads of dark water, icebergs,
  an ice cap far off, the aurora), **open ocean** (waves, the Sun's glitter path, the moon's), **savanna** (grass to
  far hills, a lone acacia, more scattered over the plain) and **red rock canyon** (buttes and mesas of banded
  sandstone, tablelands along the horizon). The land leaves a gap in its far skyline where the Sun rises and sets, so
  the Sun meets the true horizon at Shuruq and Maghrib as before. Settings → Home scenery chooses one, or "Automatic"
  by place. Only nature: nothing man-made, no people, no animals but distant birds (which the 0.9 overlay still
  draws).
- **Seasons**, from the real date and the user's hemisphere: hazy desert summers and crisp winters; snow on the
  forest and low on the volcano in winter, fresh green in spring, maples turned gold in autumn; the frozen sea open in
  summer, with melt pools; the savanna green after its rains and gold in the dry season; snow dusting the red rock.
- **Weather** ("Showcase"): invented, never copied from a forecast, but always something the real atmosphere does.
  `SkyDirector` gives each day at each place a character (clear, fair, convective, stormy, layered, overcast, rainy,
  mixed), weighted by scenery and season, evolving hour by hour, then arranges it as a landscape photographer hopes
  to find it: cloud to catch the colour at sunrise and sunset with room left for the Sun, the day's heaped clouds
  sinking once the Sun has gone (the afterglow belongs to the middle and high clouds), mostly clear deep nights for
  the stars, drama now and then. Rain (snow, where snow lies), mist in the low ground, dust on a desert storm day,
  lightning in a storm, wet ground after rain; under an overcast the sky closes whole, lighter along the horizon. (The user's real local weather is not shown; the director is where it would plug in.)
- **True to the place and the time**: the Sun and the moon (its real phase, its seas, earthshine), the 1,600
  catalogue stars and the Milky Way where they stand for the user's latitude, longitude and moment, as in 0.9.8, now
  as points and a river of star dust in the 3D sky. The stars keep to the real, unmirrored sky in Arabic too.
- **Touch.** Dragging the Sun through the day relights the whole world, clouds and land included. A tap sends a
  breath of wind through the scene: the clouds slide on, grass bends, water ruffles, then it settles.

**How it is drawn** (the reason it is affordable on a phone: the camera never moves, and the world changes slowly)

| Pass | Shader | When | What |
|---|---|---|---|
| Noise | `noise.frag` | once | the cloud volume and a flat noise tile, built on the GPU (nothing shipped as pictures) |
| Air | `trans.frag`, `meter.frag`, `sky.frag` | when the Sun moves | the air's transmittance table, the light of the moment (exposure, sunlight at each height, skylight), and the clear sky for the camera's own directions, small |
| Land | `gbuffer.frag` + one `land_*.glsl` | once per scenery, season and size | what every pixel sees in 3D (colour, kind of surface, facing, distance) and how high the land stands towards the Sun (so shadows are cast afterwards for free). Drawn a band of rows at a time, each band timed and the next sized to about a fiftieth of a second, so no GPU is held by one long drawing. A phone that needs more than 0.6 s for it keeps the result in its cache directory (`sky-land`): later starts show the scene at once |
| Clouds | `cloud.frag` | a few times a second | the march through the clouds (long strides through clear air, the finest steps where a cloud begins); one pixel in sixteen is redone each time. What is stored is how much sunlight and skylight each pixel's clouds send to the eye, not a colour, so the colours of the moment follow the Sun at once |
| Scene | `scene.frag`, `stars.vert` | ten times a second when only clouds move; every frame while the Sun is dragged or water moves | sky, Sun, moon, stars, aurora, high clouds, the marched clouds, the land and water relit, the air between, mist, rain |
| Finish | `blur.frag`, `final.frag` | with the scene | glow, the filmic curve, the darkening behind the text, the frosted glass, grain |

With animations off ("Remove animations", or the system's animator scale at 0) each moment is drawn whole, once, and
the renderer rests until the next (no GPU use while nothing changes).

**Text on the sky.** Behind the header text the sky is darkened like a photographer's graduated filter: one smooth
darkening towards the top (60 % of what the brightest thing likely to stand behind the text would ask, leaning cool so
dimmed warm light does not turn muddy; a sky dimmed all the way is a dull sky), then whatever is still brighter than
the limit is eased down, its shapes kept. The limits are 0.34 (relative luminance) behind the small text and 0.50
behind the large countdown, lifting gradually below the text; the letters themselves carry a deeper soft shadow over
the real sky (`SkyText.deep`). The Sun itself is never dimmed (at midday it stands just under the text, and a dimmed
Sun is a smudge): its disc and the glare close round it keep their light. Heaped clouds also keep their distance from the zenith (seen from straight below a
cloud is a shapeless ceiling), which leaves the sky above the text mostly open. (0.9's guarantee of 4.5:1 for bare
white text on the sky belongs to the classic sky and is still tested there; over the real sky the shadow does part of
that work. Judged in the design bench with the header's text drawn in; to be judged on a phone.)

**The glass.** The scene behind it out of focus, tinted with the page's colour. Dark glass is as dark as the
brightest part of the scene behind it asks, all over, and cooled (smoked glass): sunlit sand and its shadows are
dimmed by the same amount, so the land's forms show through instead of being flattened into one tone; it is never
brighter than 0.105, so the page's white text reads. Light glass lifts the scene whole and never falls below 0.60, for
dark ink; the scene's colours keep their hue in it, much paler, so each scenery gives the page its own tint (sand,
sea, moss, snow).

**Quality tiers** (`RealSky.Tier`; Settings → Home scenery → Sky quality): Ultra, High, Medium, Low, or Automatic
(the default: guessed from the phone's GPU and memory, and stepped down by itself if pictures take too long), or
Classic (the 0.9 sky). A tier sets the size of the cloud buffers and of the picture, how coarse the cloud march is and
how often things are redrawn. Compiled shader programs are cached, so later starts are quick.

**When it cannot run.** It needs OpenGL ES 3.0 with float render targets and a real GPU (a software one, as in an
emulator, needs minutes for a picture; unless a quality is named in Settings). Without them, or if a shader is
refused, the 0.9 sky is used (for good, until the app is updated). A crash in a graphics driver cannot be caught, so each start is
counted before the renderer is built and cleared when a picture is on screen: after three starts of a version that
never got that far, the 0.9 sky is used. Leaving the home screen before the first picture does not count. A graphics
context the system takes back (a phone short of memory) is rebuilt.

**The design bench** (`tools/sky3d`): a web page that runs the very same shader files in WebGL 2, pass for pass in
the same order as the phone (`RealSkyEngine.kt`). Serve the repository root with `python tools/sky3d/serve.py` and
open `/tools/sky3d/index.html`; drive it from the console (`bench.set({biome: 'pnw', tod: 0.72}).settle()`,
`bench.sheet('name.jpg', [...])` saves contact sheets to `tools/sky3d/out`, `bench.thumb` the scenery pictures of
Settings). On a real Android system the renderer is photographed by `tools/devicetest/real_sky.sh` (`real-sky.yml`,
an emulator whose GPU is software: faithful pictures, but minutes for what a phone draws in a fraction of a second,
so it runs on a small screen at the lowest tier).

**Not in 0.10** (ideas kept for later): the user's real local weather; each prayer's own sky sign beyond what the
physics already gives (the zodiacal light before Fajr, a shadow reaching Asr's length); meteor showers on their real
dates, eclipses and planets; rainbows and Sun dogs; a long press that fast-forwards the day; the widgets (they keep
the 0.9 pictures); sound.

## Home-screen widgets (0.9.10, 0.9.11, 0.9.13, 0.9.14)

Three widgets, each in Noor's language and digits, opening Noor when tapped (`widget/Widgets.kt`, `widget/WidgetSky.kt`):

- **Next prayer** (2×1, resizable): the next prayer, its time and a live countdown over the sky of the moment.
- **Today's prayers** (4×2): the next prayer and the countdown, the place and the Hijri date, and all six times of the
  day (the one counted to or from in gold) over the land, darkened for reading.
- **Noor sky** (4×2, resizable): the home sky in small — the atmosphere's colours for this moment, the day's weather
  with its photographed clouds, stars and the moon at night, the user's scenery, the Sun on its path with the prayers —
  and the next prayer, place, date and countdown.

They say exactly what the home screen and the notification say (`StatusNotification.content`): − before a prayer, +
for 30 minutes after it, then the next.

**The countdown (0.9.14)** reads as on the home screen, "−1:23:45" before a prayer and "+13:05" after it, "−1:23" bright
and ":45" quieter, in Noor's Plex Light and **Noor's digits whatever the phone's language**, and its seconds change
every second without Noor running. On a home screen only Android itself can change a widget every second, and its
digital clocks (Chronometer, TextClock) write the phone's digits; fonts packed in an app are ignored there (Android
builds widgets in a restricted mode in which a TextView does not load an app's fonts). So:

- the part before the seconds ("−1:23" and the quiet ":") is a picture Noor draws in its own font and digits
  (`widget/CountPicture.kt`), updated as each minute turns: a partial update that leaves the sky pictures alone, by a
  non-waking alarm, so only while the phone is awake (the only time a widget is seen);
- the seconds are a wheel of the 60 labels "00"–"59" in Noor's digits and font (vector drawables made by
  `tools/widget/SecondsWheel.java` from Plex Light), set as the second hand of an `AnalogClock`, which Android turns
  one step (6°) a second on the real clock. The widget shows it through a window the size of one label at the top of
  the wheel (855 dp across), where the label for this second stands upright; its neighbours are outside the window.
  "Down" wheels count down, "up" wheels count up; the clock is shifted by whole seconds (its time zone, a pure offset)
  so the wheel lands on the countdown's own second, rounded down as the home screen counts (`Widgets.wheelFor`).

Android rounds the clock to the nearest second when it reads the time between its ticks, so in the half second after
the widget is set or shown its seconds can be one ahead, until the next tick. The minute's picture changes a few
hundredths of a second after the minute turns. Android 12 or later is needed (an AnalogClock's second hand); before
that, the widgets show Android's ticking countdown where the phone writes Noor's digits, otherwise Noor's countdown to
the minute (0.9.13).

History: 0.9.11 set Android's Chronometer in fonts that drew any digits as Noor's; it passed the tests, but on the
owner's Samsung phone (Arabic, Noor in Western digits) the countdown showed the phone's Arabic-Indic digits and its
seconds as "...", because the home screen ignored the fonts. 0.9.13 fell back to Noor's digits to the minute there; the
owner wanted the seconds in real time and Noor's digits always, which 0.9.14 does.

The pictures are drawn with the ordinary Canvas, off the main thread. Updates come at each prayer, at the end of a "+"
period, at midnight, every 15 minutes for the picture, each minute for the countdown, and when the place, times or
settings change, all by non-waking alarms, so a sleeping phone is never woken for a widget (`WidgetsTest`, which also
draws a widget and reads which second its window shows against the real clock, counting up and down, in both sets of
digits).
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
