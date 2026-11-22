# Noor design language

Version 0.9.2. How Noor looks and moves, and why.

## Idea: the sky is the clock

Prayer times are positions of the sun: Fajr when it is about 18° below the horizon, Shuruq when it rises, Dhuhr just
after its highest point, Asr when shadows reach a set length, Maghrib when it sets, Isha when the last light goes.
So Noor shows the sky.

- **The Today screen opens on the real sky above the user**: its colour comes from the sun's elevation at that place and
  moment (`SkyPalette`, shared by phone and watch), cooler at dawn and warmer at dusk, with stars appearing in twilight
  and the moon, shaped by the Hijri date, riding the sun's own arc as far behind it as it is old.
- **The sun path** across the sky is the Sun's real path (0.23): an equal-angle picture as a person facing the noon Sun
  sees it, the azimuth across and the elevation up on one scale, so its shape is the real one (in Dubai it leaves the
  horizon at about 63°, rounds over the south and comes down as steeply; in June it nearly reaches the zenith with a
  flat top; in a polar summer it circles the whole sky, low). Fajr..Isha fits the width; noon fits under the countdown;
  deep night is carried to the screen's edges. (Until 0.22, below: the elevation curve with the time stretched.)
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
   **0.19.1, the medium graphics' clouds are the real sky's** (`SetClouds.kt`). The owner, of 0.19.0's: "straight
   lines coming out of them ... artifacts ... the clouds don't seem to appear above a certain level ... like a line
   blocking them". The photographed sprites (kept below the text), the shader's wind-combed cirrus streaks, its banks,
   its stratocumulus layer and its overcast deck are gone from the medium sky (the widget still draws the sprites).
   Now the real sky's own director chooses the clouds (`SkyDirector.at` with the day's weather for the scenery's sky,
   `skyBiomeOf`, in the time of year the scenery is shown in): the same baked sets (`assets/sky2/clouds/<id>`,
   path traced in Blender), at the same places, passing side by side across the sky and giving way to one another in
   patches, as on the High level; its overcast and rain grey the sky and light the land. Each set is read at half its
   size on a background thread (its opacity, distance, light from the sky and from the ground, and the pictures under
   the few Suns the moment lies between), lit for the moment into one picture (the Sun as high as it stands over each
   cloud, so far clouds keep their colour after sunset; the glow round a low Sun; the dome's blue in the shade; light
   through thin cloud near the Sun; undersides lit from below once the Sun is lower than the cloud; far clouds fading
   into the air's colour; the lower clouds standing apart round the Sun when the weather lets it through; the glare
   of the Sun over them), and drawn as one bitmap, the same on every phone, GPU sky or Canvas sky. They fill the whole
   sky; the text stays readable as on the High level: behind it the brightest parts of the clouds are eased down
   (at most 0.34 linear luminance where the countdown stands, fading out over an oval). A set is lit again only when
   the light changes or it has drifted a quarter of a degree; between, its picture glides with the wind.
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
   **0.19, the medium graphics**: the 0.9 sky draws each of the real sky's six sceneries (`Terrain.of(Biome)`: the
   meadow is the hills, the open sea the sea), the three that had no land of their own now with their own:
   *Forest* (the forest lake): a far range with snow on its summits, a wooded ridge across a lake, the lake (layer 2,
   flat: still water mirroring the sky and its far shore, the Sun's sheen on it only when the Sun is low), wooded
   knolls at one side of the near shore or both, a bank in front; a dark conifer treeline of pointed spires along the
   crests (tiers of branches, larger and fewer on nearer layers, none where the ground falls to the water), conifer
   crowns in rows below it, more mist. *Arctic*: broad massifs across the width, each with several jagged summits,
   snowed nearly to the water: gentle snowfields that take the light, steep faces turned to the Sun or away (little
   light thrown back into the shade, so they keep their blue shadows), dark rock along the crests and aretes and in
   ribs down the steep faces, snow in the couloirs; a fjord's dark still water; snow and dark boulders on the shore. *Canyon*:
   a far rim, buttes and mesas (flat tops on sheer walls over skirts of red scree, a spire now and then, some with a
   second tier), walls of bedded sandstone (red, orange, a pale cap rock, desert varnish, fluting, their sides lit or
   shaded by the Sun's side), smooth talus; in front flat treads of slickrock with short red risers, less mist. **0.19.1**:
   the canyon's walls read as brickwork and its ground as terraces, the arctic's snow as blotches with holes and its
   shore as black domes. Now the red rock has soft uneven strata, fluting and dark varnish streaks from the rim down, a
   pale cap rock, rough talus with gullies, and in front the desert floor of red sand with sage scattered over it; the
   arctic has pointed summits of dark rock with snow on their ledges over smooth aprons of snow (as the real sky's
   fjord), the fjord mirroring them, rocks standing in its water; the forest lake runs in between the wooded knolls;
   the dunes' slip faces stay in shade under a low Sun and their ripples show; every farthest layer is shaded by
   altitude (by the depth below the crest straight above, each notch let a vertical streak of light down the face). No
   skyline is cut open where the Sun rises and sets any more (the Sun's path is drawn in front of the land), and the
   land has no cloud shadows of its own (the clouds are the sets). `LandProfile.skylineY` is the top of the land as the
   shader draws it. (0.20 drew the medium land from the real sky's pictures; the owner rejected it: Medium is to be
   between High and Low, painted.) **0.21, painted** (HANDOVER 30.3): every scenery in the manner of the dunes the owner
   liked: each face of a hill, a mountain or a butte lit as one smooth surface (`faceSlope`), colour in broad soft fields,
   fine texture only near, haze from layer to layer, still water as a calm mirror. The green hills over their reservoir,
   the forest's snow-capped volcano over firs and a lake, the arctic's peaks over the fjord and its rocks, the red rock's
   great butte with its spires over a floor of cottonwoods and red sand and a pool, the beach and the open sea with a far
   headland; the dunes unchanged. Their weather: the forest wetter and
   greyer, the arctic overcast more often, the canyon clearer. The land shader shades only the layers a pixel shows
   (at most three, front to back), which made room for them; the four older sceneries draw exactly as before. The
   Canvas renderer (below Android 13) draws the same sceneries more simply (treelines, lakes, snowy ranges, mesas;
   `TodayMediumSceneriesDesignTest` renders them).
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

**Calm and cost**: the scene's moment changes once a minute; the clouds redraw about 15 times a second as they drift
(0.19.1: one bitmap per set; each set lit again off the main thread only when the light changes or it has drifted a
quarter of a degree),
the sky shader once a second by day (15 times at night, for the twinkling stars), the land 4 times a second; both rest when the sky is scrolled away or the app is
in the background, and stop entirely under "Remove animations". While the Sun is dragged or during the opening, both
layers redraw every frame.

## The real sky (0.11)

The owner's brief (13 November 2026): the home screen should look *real*, not drawn. "When someone opens Noor, they
should see a real place through a window, at this moment, with a sky more beautiful than they expected." 0.10
answered with a small 3D renderer that drew everything on the phone from formulas: the owner found its land bland
and lifeless, its clouds ugly, and its Sun was not to be seen on his phone ("I want the app to look like real life...
indistinguishable from real life"). 0.11 keeps what 0.10 got right (one physical atmosphere, the true Sun, Moon and
stars, the weather director, the fallback) and changes how the picture is made:

**The camera never moves, so everything that does not move is rendered once, as a film would render it.** The land
of each scenery and every kind of cloud is path traced on a workstation (Blender Cycles, `tools/scenery`), through
Noor's own camera, under a set of Suns of unit strength, a white sky and (for clouds) white ground: a few small
pictures each. The phone lights them with the Sun of the moment and puts them together with what must be live: the
air, the Sun, the Moon, the stars, the water, the wind, the weather. Real geometry, real shadows, real light inside
clouds, on any phone, because all the phone does is add pictures together.

The 0.9 scene is kept, untouched, as the fallback ("Classic") where the new one cannot run.

**The header's text on a bright sky (0.12).** The sky is darkened only in a soft oval behind the text itself (the
letters also carry their own shadow). 0.11 darkened the whole upper sky, which turned every bright cloud there grey.

**The whole screen is the window.** The scene fills the screen behind everything. The next prayer and the countdown
are written on its sky; the day's six times stand on clear dark glass over its lower part (the land is seen through
it as through sunglasses). The sun path and the prayers on it are drawn over the scene as before.

**What is seen**

- **Six sceneries** (`Biome`; Settings → Home scenery, or "Automatic" by place), each a rendered place with
  foreground, middle distance and far distance, built from real-world data wherever it exists (elevation models for
  the shape of the land, photogrammetry scans for its surfaces, stones, logs and plants): **Arabian dunes** (ranks of
  dunes with sharp crests and slip faces from a simulated sand sea, ripples underfoot, and since 0.19 nothing but
  sand to the horizon: the far range of bare rock that stood behind it until then is gone, at the owner's word),
  **Pacific Northwest** (a still lake, dark conifers, scanned stones and logs on the shore, and since 0.14 a chain of
  snow mountains over it: the Stuart Range and the Enchantments of the Washington Cascades from the USGS elevation
  model, found by searching the model for a real view of a chain; 0.11 to 0.13 had Mount Rainier alone), **Arctic** (Reinefjorden in Lofoten as Norway's elevation model gives it: an unbroken wall of
  snow peaks over the fjord; the aurora), **Ocean** (a beach of golden sand, scanned from real beaches, its shore
  running away into the distance, the open sea coming in on it to an open horizon: the far mountains of 0.11 to
  0.13 were taken out in 0.14 at the owner's word, "I want it to be like open ocean", by
  `tools/scenery/open_horizon.py`), **Green hills** (the Diablo Range above a reservoir in spring, from the USGS
  elevation model, oaks, a meadow of scanned grass and flowers; it took the savanna's place in 0.11) and **Red
  rock** (since 0.14, made again from nothing at the owner's word: Cathedral Rock over a pool of Oak Creek at Red
  Rock Crossing, Sedona, from the USGS lidar at one metre, scanned rock and ground, the creek's cottonwoods,
  sycamores and willows grown branch by branch, the rock mirrored in the pool; 0.11 to 0.13 had Monument Valley's
  buttes over a flat plain). Only nature: nothing man-made, no people.
  What each is made of and where its data comes from: `tools/scenery/sources/`.
- **Natural skylines, exact times.** A scenery is never cut open for the Sun (the owner's decision: "what I don't
  like is cutting mountains in order to have the sunrise and set on the ground"): a range runs unbroken, a forest
  stands where it stands. Instead the Sun's path stands on the scenery's own skyline: the app reads from the land's
  picture how high the land is where the Sun rises and where it sets (`RealSky.sunLift`), and draws the path from
  the one to the other over the same top (`SkyGeometry.y(el, fraction)`, `SunLift`). So the Sun comes up over the
  mountain exactly at Shuruq and goes behind the trees exactly at Maghrib; over the sea or a plain it meets the
  level horizon as before. The disc itself is drawn only between those two moments (its true height is -0.83
  degrees at both), so no gap in the land can show it early or late; its glow behind the land is the dawn and the
  dusk. The colour of the sky and of the light still comes from the Sun's true height.
- **Clouds as real skies have them (0.12).** Seventeen skies, each a path-traced set of volumes (`skybake.py`,
  `sky_lo.py`, `sky_mh.py`): eight low (a few isolated cumulus; scattered; scattered with towers; a broken sky; a
  bank thickening to one side; a thunderhead; an overcast with breaks; a full overcast), four middle (altocumulus in
  mats; in bands; a dense sheet; an altostratus veil) and five high (cirrus tufts; long sprays; a cirrostratus veil;
  cirrocumulus; a mixed sky). Each is composed the way photographs show skies: structure first (fields and empty
  quarters, streets, mats with frayed edges, a dense side and an open side), then the clouds, of very mixed sizes
  and several kinds together; never an even sprinkling (the owner, of 0.11: "they're all evenly spaced apart... that
  looks fake"). The heaped clouds are film-quality cumulus volumes (CGHEVEN's free cloud VDBs; only pictures
  rendered from them are shipped). Each is lit by the Sun as it stands at that level's own height, from under the
  horizon after sunset, by the glow of the twilight sky, by the blue above and the light the ground throws up; low
  clouds shade the land and every level is mirrored in water.
- **They pass, and never jump (0.12, 0.13).** A set is a window of the sky twice as wide as the view (94 degrees,
  in equal angles). The sets of a level follow one another across the sky side by side, 64 degrees from one middle
  to the next, as the clouds of a real day do: one passes out of the view on one side while the next comes in on
  the other. Each level has its own pace and start (a set of the low clouds passes in 33 minutes, of the middle in
  an hour, of the high in almost two), so no two levels change together, and the nearer clouds overtake the far
  ones. Nothing fades in or out. Over the 28 degrees two neighbours share, the one breaks up into patches and
  thins out as the other gathers (`layer.frag`: lobes, cells and a fringe of the noise tile), as a sheet of cloud
  really ends; never along a line, which brought the edge of an overcast across the sky as a wall. And a closed
  sky never stands beside an open one: the set next to an overcast is at least a broken sky (`SkyDirector.setFor`).
  Which set comes comes from the weather when it passes and a throw of the dice; never from the Sun's height or a
  prayer time (0.11 chose again whenever the Sun crossed certain heights and swapped all three levels in seven
  seconds: "the entire sky... suddenly transforms"; 0.12 let a set drift 41 degrees over hours, too slowly to be
  seen, and then dissolved it into the next). `SkyDirector.legs`; `SkyDirectorTest.theCloudsNeverChangeAtAStroke`.
- **They are alive (0.13).** The low clouds cross the view at a thirtieth of a degree a second: slow, and seen.
  The home screen tells the moment once a minute; between its frames the renderer carries each set on at its own
  speed (`CloudLeg.speed`, `RealSkyFrame.shownAtMs`), so they glide. In themselves they are never still either: a
  slow swirl carries every part of a set a third of a degree this way and that, so outlines swell and sink, and
  their thin parts form and melt (`layer.frag`). Lightning lights a thunderhead from within, where its tower
  stands: a pale glow by day, the brightest thing in the sky at dusk and by night, and the land flashes with it
  (`uFlash`; until 0.13 the flash was worked out and never drawn).
- **Distant thunderstorms (0.14).** The owner: "There should be distant thunderstorms with lightning. Sometimes you
  see really cumulonimbus clouds, which are very beautiful." Three low sets made for it (`tools/scenery/sky_cb.py`,
  beside photographs of real cumulonimbus): `lo_cb_far` (one great storm 60 to 120 km off in a fair sky: towers of
  real cumulus volumes stacked, an anvil with a fibrous edge, a dark flat base, a veil of rain), `lo_cb_line` (a
  line of storms at different ages along the horizon) and `lo_congestus` (swelling towers, no anvils). The
  director brings them in a far storm's hours (`farStormHour`: from the afternoon for three to six hours, often into
  the night, on most convective and stormy days and some others; never over the arctic) and their lightning
  strikes at their towers (`THUNDER`, from each set's `sky.json`). Every low cloud is lit at the height it really
  stands (from its distance and the angle it is seen at): after sunset a storm's head stays red and gold over its
  grey base, as in the photographs.
- **Lightning that lights the cloud (0.14).** 0.13's flash was a round white glow a quarter of the sky wide laid
  over the thunderhead: the cloud was gone under it. A stroke inside a cloud lights the cloud: a bright heart where
  it is, a glow through the body round it, the cloud's own folds answering (the bright ones more, so its forms
  stand out in the flash), veins of light that differ with every stroke. Two strokes in five reach the ground: a
  thin crooked line of light under the cloud's base, with a branch (`uBolt`, `bolt()` in `scene.frag`). Every set
  named in `SkyDirector.THUNDER` flashes, at each of its towers, and while one is in the view the picture is
  redrawn at every frame (a stroke lasts a few of them).
- **The day is a walk through different skies (0.14).** The owner, of 0.13: "the same cloud types just keep
  cycling over and over again, which is extremely boring... It's just overcast, and then it's overcast again... I
  want the entire day to have a lot of variety... sometimes it's overcast, sometimes it's broken, sometimes it's
  scattered, sometimes it's isolated clouds... That's not how real life works." Until then a leg's set was read off
  the day's weather, and a day's weather has one character from midnight to midnight: an overcast day was
  forty-four overcasts in a row, each the same picture passing again. Now the weather only says where the day
  leans (how closed the low sky wants to be, hour by hour: open with a swell on a clear day, building through a
  convective afternoon, closed for a spell of two and a half to five hours on an overcast or rainy day and open
  before and after it), and each leg takes the sky nearest to that which the rules allow
  (`SkyDirector.programme`, `walkLow`, `walkMid`, `walkHigh`):
  - never the set that has just passed, and rather not the one before it;
  - a sky closes and opens by degrees (no overcast wall to wall beside an open sky);
  - a closed sky holds for three legs at most (an hour and three quarters), then breaks for a while, and of any
    twelve hours it takes four and a half at most;
  - the hours of sunrise and sunset (the place's own) keep room for the Sun under cloud that takes its colour,
    unless the day's closed spell falls on them; deep night is mostly clear;
  - thunderstorms in their hours, towers with the warmth of the day;
  - every set also passes turned about, left for right, every other time (`CloudLeg.turned`; `uLay.w` in
    `layer.frag`; lit from the other side; its own pictures in memory): the very same picture comes back half as
    often.
  How often a cloud stands over the Sun follows the sets on show. Measured over 240 days a scenery: the low sky is
  closed for 1 % of the daylight over the dunes, 7 % over the forest lake, 2 to 5 % elsewhere; nearly every day
  shows four or more kinds of low sky.
- **Morning fog (0.14).** The owner: "early in the morning, you might have fog." On the mornings that have it
  (most often over the forest lake and in the green hills' valley, in the cold half of the year; now and then over
  the sea, the fjord, the red rock and, as on the Gulf's winter mornings, the dunes; never on a stormy day) a
  shallow fog gathers in the last hours of the night, is deepest about sunrise and is burnt off an hour or two
  after it (`SkyState.fog`, `uFog`). It lies on the water where the scenery has water, else on the ground at the
  eye's foot, some tens of metres deep, deeper where it is looked down on from a hill (a white sea in the green
  hills' valley): along the ground the sight runs out in a few hundred metres, tree tops and mountains stand out of
  it, the upper sky is clear over it, water mirrors it, and towards the low Sun it glows with the Sun's colour. It
  lies in banks that drift.
- **Rain and snow (0.13, 0.14).** What is far off stands behind more rain: the land fades into a grey veil with
  its distance, as rain is in a photograph. In front of it, drops near the eye: short fine streaks of five sizes,
  leaning with the wind. Snow: flakes large and soft near the eye, small far off, swaying down. Since 0.14 a far
  ridge fades to a shape and does not go (a light rain leaves it nearly whole, the heaviest a third of it), and it
  rains only out of a sky that is closed, as far as it is closed (`SkyDirector.at`, `falling`).
- **Far land stays in sight (0.14).** The owner, of 0.13: "distant terrain just turns invisible during midday".
  Two causes, both in `landAt` (`scene.frag`). Mist was spread through the whole depth of the air (one scale height
  for haze and mist alike), so the least mist, and the forest lake always has some, took a far mountain away: mist
  now lies on the ground, some tens of metres deep (`HM`), where a far shore stands in it and a mountain's flank
  does not. And the air in front of far land was given the colour of the sky right behind that land, which paints
  a hazy mountain in exactly the colour it stands against: it now has the colour of the air near the horizon, taken
  at two fixed heights (bluer than the horizon's own: a few miles of air are not all the air there is).
- **The day's exposure (0.13).** With the Sun high everything is bright, and the filmic curve's shoulder pressed
  sunlit cloud, its shaded side and the blue of the sky into one pale range: 0.12's clouds were flat at midday,
  though their pictures hold a light and shade of ten to one (measured: `bench.meter()`). So the day is exposed for
  its brightest things (`exposure` in `final.frag`: 0.7 once the Sun is 28 degrees up, eased in from 6): the blue
  deepens, the clouds keep their modelling, and the land is given some of the light back (`landAt`). The night
  keeps the exposure it had.
- **Sunrise and sunset (0.14).** The owner: "make the sunset and sunrise really beautiful... When you see a really
  beautiful sunset, in real life... it reflects its colors on the clouds." Put beside photographs (Wikimedia
  Commons: lakes at sunrise, a river at sunset, altocumulus just after sunset), 0.13's evening was grey clouds with
  pale pink rims in a violet sky over one flat orange stripe. What was changed, and why:
  - *Each cloud has the Sun of the place where it stands* (`layer.frag`, `elThere`). The view looks towards where
    the Sun rises and sets, so a far cloud is nearer the Sun's side of the Earth: there the Sun stands a degree
    higher for every 111 km, and the cloud is high besides. When the Sun has set for us the far clouds are still
    gold, nearer ones orange and red, the nearest grey, and the high clouds burn longest. Until 0.13 a whole level
    of cloud had one light: all lit, then all grey.
  - *Light through thin cloud, and light under cloud.* Rendered under a low Sun, a cloud seen from the Sun's far
    side is nearly black (the pictures are right: measured, a hundredth of the Sun's light). What a camera sees in
    it is light coming through where the cloud is thin, most of it nearly straight on (`through`: thin cloud near
    the Sun blazes; how thin, the picture lit by the sky above knows), and, once the Sun is lower than the cloud,
    light running along its underside and catching its folds (the low Sun's own picture, its relief kept and its
    strength raised, with a little of the picture lit from the ground).
  - *The colour of the light.* `warm` (atmosphere.glsl) kept the last sunlight from being "only red" by lifting
    its green and blue so far that every evening cloud was the same pale salmon: it now keeps much less. The sky
    model had 2.2 times the real ozone (to keep twilight blue), which made sunlight pink and the upper sky violet:
    now 1.5 times. Shade in the evening is slate (the whole blue dome lights it), not khaki.
  - *The exposure and the tone.* The hour of colour is exposed for the bright sky round the Sun (`exposure` in
    `final.frag`, `hourOfColour`), the land given most of that back; and the brighter a coloured light, the more of
    it is toned channel by channel (`toneLinear`: orange goes to yellow and to white, as on film), so the glow runs
    out of the Sun's place by degrees and the Sun is not lost in it. A fuller glare round a low Sun.
- **The Sun and the clouds (0.12).** The Sun is drawn low in the picture, where far clouds crowd together, though
  it stands high in the real sky, where gaps are wide: left to the picture it was nearly always hidden. So whether
  a cloud is in front of the Sun is the weather's say (`SkyState.sunPart`): clouds pass over it now and then, the
  more often the more of them there are, and under an overcast it is gone. Otherwise the low and middle clouds
  stand apart round it: in a wide ring only their thin parts go (their thick hearts stay), and only close to the
  Sun does cloud give way altogether (`parted` in `scene.frag`). High cloud is never parted: through thin cirrus the
  Sun shines on, through an altostratus veil it is a dim disc, and a veil of ice draws the 22-degree halo.
- **The land under the clouds (0.12).** The shadow of a low cloud falls on the land where that cloud stands in the
  picture. The cloud a sunbeam meets is often outside the picture (overhead, behind us): there the land gets what
  the sky as a whole takes from the Sun (`SkyState.shade`): under a full overcast no sunshine at all, under a
  broken sky some, and when a cloud passes over the Sun the land near us dims with it. Under a closed sky
  (`SkyState.overcast`, from the sets on show: `SkyDirector.CLOSES`) the land's light comes evenly from the whole
  grey sky, without the blue of an open one. A full overcast and a thunderhead never stand apart for the Sun.
  (Before this the land under a grey overcast was in full sunshine with hard shadows.)
- **The Sun, bold.** Its disc is drawn a little over twice its real size (it is also the hand of this clock), white-hot
  when high and a red-gold ball when the air has dimmed it, with the glare and the fine rays a lens draws round a
  point of light. Nothing in the picture is brighter than a fiftieth of what a half-float can hold (0.10's Sun was
  written at up to 60 000, which overflows half-floats on some phone GPUs and turns to black: the likely reason it
  could not be seen).
- **The night (0.12).** Round the galactic centre, where the finest night looks, the sky is NASA's star map at the
  screen's own sharpness (Deep Star Maps 2020, 8k: the Milky Way's glow with its dust lanes, and the real stars
  over it, `night/starfield.webp`); the whole-sky map stays for everywhere else, and for "Real stars". The 1,600
  catalogue stars are points over it (faint ones faint, a wide range of sizes), the Moon carries its real face (NASA's
  Lunar Reconnaissance Orbiter map) and phase, the land is seen by starlight as a long exposure finds it. A shooting
  star falls now and then.
- **Water, live.** Still or ruffled, it mirrors the sky, the clouds and the land beyond (a thing mirrored at the
  right height for its distance), shows its bed through clear shallows, and carries the Sun's and the Moon's road.
  A pixel of water is never one facet (0.13): the height it mirrors wanders within the pixel, and by the ripples too
  small to be seen. So the mirror is asked at four heights across that spread, each from a copy of the land's
  picture as soft as the step between them (its mipmaps), and the land's image is as strong as the share that found
  land. Asked once, as until 0.12, the image of a treeline was all there or not there pixel by pixel: hard dashes
  of sky across it and stair steps along its edges. A wave is drawn only once it is fifteen pixels long on the
  screen; smaller ones only spread the mirror.
- **A beach with waves** (the Ocean scenery; `uShore` in `scene.frag`, written into `land.json` by `ocean.py`). The
  swell comes in as lines that rise as the bed shelves, steepen, break (the larger ones further out) and run on as
  white water with the foam they made trailing behind; the last of each wave runs up the sand as a thin sheet with a
  foam edge, slides back, and leaves the sand dark and shining until it sinks away. The waves have real height: the
  view ray is walked down onto them, so a wave shows its dark face and hides the water behind it. Over the pale
  sand the shallows are clear and green. The sea's edge is worked out on the beach's own plane, not from the
  picture's samples, so it is a clean line.
- **Life.** Grass and leaves stir (and bend to a touch's breath of wind), a flock of distant birds crosses the day
  sky every few minutes, water moves, clouds drift, stars twinkle, the aurora plays over the Arctic.
- **Seasons, if asked for.** Settings → Home scenery → Seasons (off by default, the owner's decision): on, the
  scenery follows the real season of the user's place; off, every scenery keeps the time of year it looks best in
  (`Biome.signatureYear`). Until 0.13 a season was snow or no snow, and the owner said so: "it's just snow and
  that's it. The trees don't change color... I want it to be like actual seasons." Since 0.14 a scenery may hold
  its land's colours again for its other seasons (`a_<season>.webp`, `k_<season>.webp` beside the base pictures,
  named in `land.json`: `season`, `seasons`, `winterBaked`; the light pictures are shared, so the land's forms stay
  the same or nearly). The renderer shows the two seasons the day lies between (`Seasons` in `RealSkyFrame.kt`,
  the same in the bench): each whole for a while round its fullest (late January, late April, late July, the start
  of November: the land follows the Sun by a month and more), the one passing into the other over some weeks.
  Where the winter's pictures carry their own snow, the renderer lays none of its own. Sceneries with pictures of
  their own seasons: the Red Rock (base summer; autumn's cottonwoods gold, unevenly; winter bare, the willows' red
  stems, a dusting of snow on the ledges; spring's small fresh leaves). The green hills' are made the same way
  (`meadow.py`: base spring; summer's straw-gold grass and dusty blue-green oaks; autumn tawny, the blue and valley
  oaks gold to russet; winter's new green, those oaks bare; the live oaks evergreen) and are added when their
  renders are done (HANDOVER.md section 25 says which are in). The others change with the seasons as they did (snow), which is
  true to them: the dunes, the sea and the arctic do not turn, and the Pacific Northwest's conifers keep their
  needles. The season pictures are rendered by a colour pass with that season's materials (`bake.colour_render`;
  `tools/scenery/README.md`).
- **Weather** ("Showcase"): invented, never copied from a forecast, but always something the real atmosphere does.
  `SkyDirector` gives each day at each place a character, evolving hour by hour and arranged as a landscape
  photographer hopes to find it (cloud to catch the colour at sunrise and sunset with room left for the Sun, mostly
  clear deep nights), and chooses the cloud sets that show it.
- **Touch.** Dragging the Sun through the day relights the whole world at once. A tap sends a breath of wind
  through the scene.

**How it is put together** (`RealSkyEngine.kt`; shaders in `app/src/main/assets/sky2`)

| Pass | Shader | When | What |
|---|---|---|---|
| Air | `trans.frag`, `meter.frag`, `sky.frag` | when the Sun moves | the air's transmittance table, the light of the moment (exposure, sunlight at each height, skylight, the glow round a low Sun, the Sun's own disc), and the clear sky for the camera's directions, small |
| Shading | `lit.frag` | when the light changes | for the land and for each cloud set: the shading under the few rendered Suns the moment lies between, mixed (`LightWeights`) |
| Clouds | `layer.frag` | with the shading | each layer of cloud in the colours of the moment, two sets cross-fading while the weather changes |
| Scene | `scene.frag`, `stars.vert` | ten times a second; every frame while the Sun is dragged, water moves, birds cross | sky, Sun, Moon, Milky Way, stars, aurora, the three cloud layers sliding with the wind, the land relit with the air between, water, birds, rain and snow |
| Finish | `blur.frag`, `final.frag` | with the scene | glow, the filmic curve, the darkening behind the text, the glass, grain |

The pictures (`assets/sky2/land/<id>`, `assets/sky2/clouds/<id>`; what each file is: `tools/scenery/README.md` and
`skybake.py`) are read from the APK as they are needed, on a thread of their own, and forgotten when they have not
been used for a while: only the few the moment lies between are in memory.

**The light of a moment** (`LightWeights`, mirrored in `tools/sky3d/bench.js`). The land is rendered under 17 Suns
(eight heights from 0.7 to 50 degrees on the left for the morning, the same on the right for the afternoon, one
high in front for noon) and two broad glows; clouds also under four Suns below the horizon. Low Suns stand where the
Sun is seen to rise and set; higher ones swing round to the side, so the scene is modelled by light and shade
through the day. A moment's light is a mix of at most five of them. The colour of the light always comes from the
Sun's true elevation through the atmosphere model.

**Text on the sky.** Behind the header text the sky is darkened like a photographer's graduated filter, as in 0.10
(limits 0.34 and 0.50); neither the Sun nor the Moon is dimmed.

**The glass.** Dark glass is clear: the scene behind it a little out of focus and dimmed, all over, by as much as
its brightest part asks (never brighter than 0.24 where white text stands), so the land's forms and texture show
through. Light glass (the light theme) is as in 0.10.

**Graphics levels (0.19)** (Settings → Home scenery → Graphics; the owner's three levels): **High** is the real sky;
**Medium** the 0.9 sky (the layered sky drawn on the phone: GPU shaders on Android 13 and later, Canvas below), with
every one of the six sceneries (0.19 gave the forest, the arctic and the red rock their own land; the green hills are
the 0.9 hills); **Low** the simplest sky (`SimpleSkyLayer`: the sky's colours, the Sun and the moon, stars at night,
the scenery's land as flat silhouettes; nothing moves by itself and nothing is drawn again unless the moment changes);
**Automatic** (the default) chooses for the phone (`SkyLevels.guess`: memory, Android version, a low-memory device,
OpenGL ES 3) and steps down a level by itself when the frames prove the phone too slow (the real sky at its lowest
step, the 0.9 sky at its lower resolution), kept until the app is updated. Every level draws the same scenery for a
place (`Terrain.of(Biome)`). Within High, the real sky still chooses its own steps (`RealSky.Tier`: Ultra, High,
Medium, Low: the size of the picture, how often it is redrawn, whether the baked pictures are read at half size),
guessed from the GPU and memory and stepped down by itself. Compiled shader programs are cached.

**While the Sun is dragged (0.18.2).** On a phone whose frames of a drag come slower than about 48 a second, the scene
is drawn a step smaller (down to 0.6 of its size, `RealSkyEngine.busyScale`) and enlarged by the last pass, so the
drag keeps pace with the finger, a little softer while it moves; each new drag tries a step finer again, and a still
picture is always drawn whole. On a phone that keeps up nothing changes.

**Until the first picture; when it cannot run.** As in 0.10: a still dusky sky holds the scene's place until the
first picture; without OpenGL ES 3.0 and float render targets, on a software GPU (unless a quality is named), after
a refused shader or three starts that never showed a picture, the 0.9 sky is used.

**The tools**

- `tools/scenery/` renders the pictures: `bake.py` (the camera, the Suns, the bake and its packing, the skyline
  check, the mask), one script per scenery, `cloudgen*.py` and `skybake.py` for clouds, `nightsky.py`; see its
  `README.md`. Blender 4.5 LTS with Cycles on the workstation's GPU; nothing of this runs on the phone or in CI.
- `tools/sky3d/` is the design bench: a web page that runs the phone's own shader files on the same pictures in
  WebGL 2, pass for pass. Serve the repository root with `python tools/sky3d/serve.py` and open
  `/tools/sky3d/index.html`.
- `tools/devicetest/real_sky.sh` (`real-sky.yml`) photographs the renderer on an Android emulator.

**Not in 0.11** (ideas kept for later): the user's real local weather; meteor showers on their real dates, eclipses
and planets; rainbows; a long press that fast-forwards the day; the widgets (they keep the 0.9 pictures); sound.

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
### The widgets of 0.24: drawn in Plex, sized for the real widget, every part customisable

The owner on 0.23 (18 November 2026, his Galaxy, Arabic UI with Western digits, at night): "everything is so small and
it's not easily readable and it's not visually appealing ... should look a lot better and more professional and
Apple-like"; then, on the mock-up, "more Apple-like", and "I want to be able to completely customise the widgets". The
visual target is the lead's mock-up (vivid iOS-Weather-like skies, SemiBold names and countdown, colourful signs);
its dp are the spec at the base sizes 2×1 176×84, 2×2 176×176, 4×1 360×84, 4×2 360×176, 4×4 360×372.

**How a widget is drawn** (`widget/WidgetSheet.kt`, `WidgetInk.kt`, `WidgetDesigns.kt`, `WidgetBack.kt`, `WidgetLooks.kt`).
A home screen ignores an app's fonts in TextViews (the owner's photos showed Samsung's font beside our Plex countdown),
so every text is drawn in IBM Plex (`res/font/plex_arabic_*`: SemiBold names, Regular times and secondary text,
figures of one width) into pictures. A composition places lines, signs, bars and capsules in dp, from the reading edge
(an Arabic widget is the English one mirrored; digits, signs and the countdown never reversed; the sky's arc never
mirrored), and groups them into blocks; each block becomes one picture as small as its ink, at the screen's density,
placed by its ImageView's padding in one layout (`res/layout/widget_v3.xml`, written by hand), which lets a picture
shrink (never stretch or clip) if the space is smaller than drawn. The live countdown is CountPicture (Plex SemiBold,
tracking −0.035 em) beside the seconds wheel (`tools/widget/SecondsWheel.java`, regenerated in SemiBold), the seconds at
62 %, at min(40 dp, 0.74 × the minutes) on the minutes' baseline (the wheel's drawables are made at 40 dp; larger it
would be a picture thousands of pixels across, which Android draws from a cache of at most 2048 px). Every text a
composition draws is recorded (`Drawn`: role, words, size, ink, box): the tests read the widget through it.

**Corners and size.** No corner of our own: the root is `@android:id/background`, clipped to its outline, a transparent
shape with the system's widget radius (`drawable-v31/widget_v3_shape.xml`; 24 dp before Android 12), the background
picture full-bleed under it, so the launcher's clip and ours are one corner (0.23 showed a second, smaller corner inside
Samsung's). On Android 12 and later each widget gets a map of layouts by size (`Widgets.viewsFor`): one for each size
in `OPTION_APPWIDGET_SIZES` (or the portrait min W × max H and landscape max W × min H), and each also 16 % shorter, for
home screens that give less than they say (Samsung's 4×1 cut its bottom row); the home screen shows the one made for
the size it really gives. Before Android 12, one layout for the portrait size.

**Size classes.** Each kind's composition is chosen by the class whose base gives the largest scale min(w / base w,
h / base h) (a taller class when it is nearly as large: it shows more), and scaled by it, so larger widgets get larger
text. Floors: nothing under 11 dp, the marked time and the countdown 12 dp; content gives way first (AM/PM, then
Shuruq where room is short unless asked for, then names before times, the list after the prayer before the main block).

| Kind | 2×1 | 2×2 | 4×1 | 4×2 | tall (2×4, 4×4) |
| --- | --- | --- | --- | --- | --- |
| Next prayer | name and time on one line, countdown under (110×40: name and countdown to the minute) | sign and name, Adhan time, bar, countdown 37 | sign, name over time, countdown 42 at the end (under 300 dp wide: as 2×1) | name and time large, place, countdown 54; "Later today" with the next two (no list where it would be small) | name, time, place, countdown, bar, the next prayers spread over the height |
| Today | as Next 2×1 | six rows, the next in a capsule, the past at half strength | the strip | header (name, time, countdown 30); six columns of sign 26, name and time | header, countdown 54, bar, six rows with signs |
| Strip | | as Today 2×2 | five columns, name over a large time; short: names at 11 over times at 13 or the times alone; tall: signs too | (the strip, taller) | |
| Ring | | the ring; the name, countdown, time inside (the time goes under its floor) | | | |
| Sky | as Next 2×1 | as Next 2×2 | as Next 4×1 | the arc solid gold to the Sun, dotted after, the Sun with its glow, sunrise and sunset at the ends; by night tomorrow's path dotted and dim, no Sun | |
| Date | "24 Jumada al-Ula" (short month only if it must), or the day beside the month over the year | weekday in gold capitals, the day's number large, month, year and Gregorian, the occasion | the number, month and year over the Gregorian, the occasion at the end | the 4×1 enlarged | as 2×2 |

**Colours.** The sky: a smooth vertical gradient (day #2F7FD8 → #4B97E6 → #6FB0EF, golden #3A4F9A → #8A6AA0 →
#E0896A, dawn rosier, night #0B1430 → #16244C → #233564 with faint stars), blended by the Sun's elevation, each pixel
darkened where white text would read under 4.6:1. Dark #1C1C1E, Light #FFFFFF with ink #1C1C1E and capsule #F2F2F7;
Glass and the wallpaper's colours as before. The accent on colour is #FFD36B; the bar 6 dp in it; the capsule white 18 %
with no stroke. The signs in their own colours (Fajr #8FD0FF, Shuruq #FFB14A, Dhuhr #FFD23F, Asr #FFBE3B, Maghrib
#FF8A4C, Isha #E9EDFF; deeper on light); past prayers dim their text, never their signs. `WidgetLooks` resolves what is
drawn so it reads, from the background's colours as they are (over a middling wallpaper where it is see-through):
the text colour (one's own only where it reads at 4.5:1, else the automatic one, and the page warns); the accent (one's
own where it shows at 3:1); the quieter text's strengths (72 %, 52 %, 50 %, raised just enough to read at 4.5:1, 3:1
over the sky); the marked prayer's words (gold where it reads on its capsule, else the ink: over the vivid sky gold on
a white capsule is 2.4:1, so there the name is white and the gold is in its sign and capsule); the capsule (white 18 %,
or black 18 % where white text needs it); the opacity (raised in 5 % steps where text would not read); a middle-tone
colour of one's own (neither white nor ink at 4.5:1) is darkened just enough for white, as the sky is.

**Options** (`WidgetOptions`, kept per widget id; 0.23's saved options read as before, their new options at the
defaults): background (Sky following the time of day or at Dawn, Day, Golden or Night; a colour; a gradient of two;
Dark; Light; as the phone; Glass; Wallpaper colours; Transparent) and opacity; text colour (automatic, white, black,
one's own); accent (the six, or one's own); signs (colourful, one colour, off); text size 80–130 %; alignment (Next and
Date); the main number (countdown or Adhan time); the countdown as a timer or in words; each part shown or hidden (sign,
name, Adhan time, countdown, seconds, bar, the next prayers, Hijri and Gregorian dates on the information line,
Jumu'ah, AM/PM); the place (area and city, area only, off); the prayers shown (Shuruq where there is room, always,
never); row order (as the language reads, or Fajr on the left); time format, digits and language (as in the app, or the
widget's own); past prayers (dimmed, normal, hidden); the next prayer's highlight (filled, outline, colour only); after
the Adhan (the time since, or straight to the next prayer); what a tap opens. Defaults for new widgets are kept per
kind (`WidgetOptionsStore.defaults`). The page (`WidgetConfigActivity`, in the 0.15 design system) shows the widget on
a home-screen backdrop at its own size and the kind's small, wide and large ones, then Look, Content, Show, Language and
Tap, with Reset to default and Apply to all my widgets. Settings → Widgets (`ui/settings/WidgetsScreen.kt`) lists the
widgets on the home screen with a small picture each, the defaults for new widgets of each kind, and how to add one
(with a button where the home screen supports pinning).

**Updates.** Every update is a full one (a partial update would not reach into the map of layouts), each minute while
a prayer widget is placed, at each change of display and at midnight. The minute is made on its instant: the views for
the instant just after a change are built 800 ms ahead and handed over 50 ms before it, timed in Minhad's process while
it lives, with an alarm 900 ms ahead (exact where the app may set one, never waking the phone) as the backstop; the
change is `StatusTicker.nextChange` (before a prayer the time left is rounded up: the minutes turn one second after the
time left is a whole minute). Building every widget takes some tens of milliseconds (40 ms for three in the tests).

**Tests.** `WidgetGalleryTest` (about 1,070 widgets: every kind at its sizes, in English, in Arabic with Western digits
and with Arabic-Indic, by day, at the golden hour and at night, over the sky, Dark and Light; every option alone at two
sizes; 60 random combinations; built for a reported size and shown shorter): nothing outside the widget or its picture,
no picture shown larger than drawn or stretched, no two texts touching, the floors, the app's digits only, the same
content in English and Arabic and on every background, the day, golden and night skies distinct, the seconds' window
inside the widget beside the minutes and on their baseline (1 dp), every text against the pixels under it (4.5:1, or
3:1 over the sky or the wallpaper); a colour that would not read gives way. `WidgetsTest`: what the widgets say, the
seconds wheel against the real clock, the minute built ahead and handed over on the change (right just after it), the
alarm. `WidgetsChangeTest`: the prayer named across the end of a "+" period, a prayer with the "+" off, midnight.
`WidgetOptionsTest`: every option kept and read back; 0.23's lines. Software renders, not a launcher.

**For a debug host** (the emulator harness): the receivers are `com.noor.app.widget.NextPrayerWidget`, `TodayWidget`,
`SkyWidget`, `RingWidget`, `StripWidget`, `DateWidget`; set a widget's options with
`WidgetOptionsStore.save(context, appWidgetId, WidgetOptions(...))` and then `Widgets.refreshAsync(context)`; or build
any kind at any size without placing it with `Widgets.preview(context, kind, options, widthDp, heightDp)` (0.24: what
a widget drew, `Widgets.builtFor(id).design.drawn`). From a shell
(debug builds): `adb shell am broadcast -a com.noor.smartwatch.debug.WIDGET_OPTIONS --ei id <appWidgetId> --es options
"bg=GLASS;ac=TEAL;sec=false" -n com.noor.smartwatch/com.noor.app.debug.TestHookReceiver` (keys as `WidgetOptions.encode`).

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

## The interface of 0.25: as if Apple had made it

The owner (22 November 2026): "totally improve the app in every way possible ... really focus on Apple's design
philosophy. Really, make this application as if it was made by Apple themselves ... everything from the opening
animation to how settings are organized ... The app's logo ... You have full freedom." Every screen of 0.24 was
rendered and looked at first. The phone's pages already stood on one system (0.14) in the sky (0.15), so 0.25 does
not change the look again; it goes for what still told Minhad from an app of Apple's: one-colour signs, pages that
only faded, a first page of Settings that grew with every option, a mark that was a clip-art crescent on flat navy,
a watch app in stock Material, a wide screen treated as a tall phone, and no way to see a month.

- **The mark** (`tools/brand/logo.py`, which writes every file that shows it): the crescent and its eight-pointed
  star (the khatam) in pale gold over the Earth's edge at first light: a sky from ink through indigo and violet to
  the dawn's amber, the Sun's glow behind the dark Earth, a lit rim. It is Minhad in one picture: the crescent (the
  calendar), the horizon and the Sun under it (the times: Fajr is the first light). Launcher icons of phone and watch
  (adaptive: the sky and the Earth are the background, the crescent and star the foreground, so a launcher's parallax
  moves them apart; a one-colour layer for themed icons), the launch screen's mark, the status bar's sign. Six
  candidates were drawn and compared at launcher sizes beside the old icon before this one was chosen.
- **The opening** follows the mark: as the app takes over from the launch screen, the Earth's edge rises into the
  foot of the screen with the dawn behind it (`Opening.DAWN_MS`), then, as before, the light gathers at the mark,
  travels to where the Sun is and the dusk opens from there. The launch is the icon come to life. Still 2.3 s at
  most; a touch still finishes it.
- **A colour for every kind of sign** (`Tints`, `IconTile` in `ui/settings/SettingsComponents.kt`): as in an
  iPhone's Settings, the sign at the start of a row is white on a small tile of its kind's colour, lit from above:
  where one is in blue, the times in Minhad's gold, what is heard in red and pink, what is seen in indigo, teal and
  purple, the calendar in red, the watch in graphite, what is checked in green, warnings in orange and red. A row
  is found by its colour before its words are read. The colour comes from the sign itself (`Tints.of(icon)`), so
  every list of the app has it without saying so. Gold stays the one colour of what can be acted on (links,
  switches, the chosen thing, the main button).
- **Motion, as on an iPhone** (`MainActivity`'s transitions, `NoorMotion.Page`/`Glide`, `Modifier.pressScale`):
  a page pushed from another slides in from the side one reads towards while the page under it draws back a little
  and dims; going back is the same film backwards, and with Android's predictive back (Android 14 and later;
  `android:enableOnBackInvokedCallback`) the back gesture pulls the page off by hand and shows the one under it.
  What is touched gives under the finger: buttons, hero cards and tabs draw in a little and spring back.
- **Settings in fewer rows.** The notification bar has a page of its own (its switch and, on Android 16, the
  status-bar timer's three options: on such a phone they were four rows of the first page); its row says On or Off.
  **About Minhad** is a page: the mark as the launcher shows it, the name, what Minhad is in a line, how the times
  are checked, the recordings' sources and licences, the version. Widgets stands with Theme and Home screen (what is
  seen), before Language and region. Search finds the rows of the new pages and says where they live.
- **What's New, once** (`ui/components/WhatsNew.kt`): after an update that has something to show, a sheet over the
  home screen, as the apps of an iPhone do it: a few rows, each a coloured sign, a few bold words and a quiet line,
  and Continue. Only to someone who had Minhad before (`AppSettings.newsSeen` below `WhatsNew.EDITION`), never after
  the welcome, never twice. Rewrite its rows and raise `EDITION` when a later version has news.
- **A wide screen is not a tall phone** (`HomeLayout.columns`): from 600 dp of width (a folding phone opened out,
  a tablet, a phone on its side) the day's times stand in two columns of three (Fajr, Shuruq, Dhuhr | Asr, Maghrib,
  Isha) and the sky is taller. Until 0.24 the opened Galaxy Fold left the sky so little height that the Sun's path
  lay flat and ran through the countdown's caption (`TodayFoldDesignTest` renders it).
- **After an independent review** (the owner, the same day: "criticize the app and criticize your decisions ... is
  this what Apple would do?"; a reviewer given only the renders ranked 39 findings, "about four-fifths of the way"):
  the Adhan and alarms area was reorganised ("The Adhan and alarms area (0.25)" below); the Qibla shows whole degrees,
  the watch page one watch, the accuracy page three grouped lists (PAGES_025_NOTES.md); and on the home screen: the
  compact rows wherever the roomy ones would leave the sky under 300 dp (a 360 × 780 dp phone, the commonest size,
  had a Sun's arch 30 dp high running through the caption), no caption in a short sky (`HeroGeometry.caption`), no
  bells while the alerts are off altogether (six struck-through bells said "off" six times), the lit row a closed
  shape, and the Sun's drag kept from Android's back gesture (`systemGestureExclusion`: on an emulator a drag begun
  12 dp from the edge closed the app). By day filled controls are a true gold (`NoorColors.goldFill`; they were
  mustard brown). A row's state in a word stands at its end; a line under the title is kept for what needs a sentence.
- **For a release**: Settings → About Minhad has the privacy policy (docs/PRIVACY.md) and Acknowledgements (whose
  work Minhad stands on, with the notices the licences ask for, in both languages); the welcome tells the truth
  about where the location goes and leads to exact alarms (WELCOME_025_NOTES.md); the tester's options are out of a
  store build (`-Pminhad.store=true`). What remains before Google Play is in docs/RELEASE.md.
- **A month's timetable** (TIMETABLE_NOTES.md): from the date sheet, every time of the month in one table, by
  Gregorian or Hijri month, shared as text; its times are the home screen's own (`TimetableTest`).

## The interface of 0.15: the app lives in the sky

The owner (16 November 2026), of 0.14: "I see absolutely nothing changed with the user interface. I want you to
totally revolutionize the user interface, make it better in every single way." 0.14 had changed the structure of
the pages (order, groups, sheets) but kept their look: flat ink at night, paper by day, solid cards. 0.15 changes
the look of every page at once, through the parts they share:

- **The pages stand on the sky of the moment** at the user's place (`ui/theme/Backdrop.kt`, `SkyBackdrop`): the
  colours of `Sky.colors` for the Sun's height there now, re-read every minute (`rememberSun` in `MainActivity`).
  Deep blue at midday, rose and amber round sunrise and sunset with a glow at the foot of the page, indigo with a
  few faint stars at night; in the light theme the same sky washed pale. As Apple's Weather app lives in its
  weather, Noor lives in its light. The home screen keeps its real sky.
- **Glass.** Lists and cards are panes of glass over that sky (`NoorColors.glass`, `glassRaised`) with a lit upper
  edge (`Modifier.glassEdge`), larger corners (24 to 30 dp); buttons and the search field are capsules; sheets are
  the glass over the page's sky made solid. `ThemeContrastTest.textOnTheGlassOverThePagesSkyIsAlwaysReadable`
  checks every text colour on the glass at every height of the Sun, morning and evening, in both themes (WCAG AA,
  4.5:1); the dark theme's secondary and muted greys were lightened for it.
- **The tab bar floats**: a capsule of thicker glass (`chrome`) over the foot of every tab's page, as on iOS 26,
  the page running on under it; the tab on show sits in a lens of gold light that slides (a spring) to the tab
  chosen, mirrored in Arabic.
- **Hero cards** (`ui/components/Hero.kt`): a tab's page opens on what it is about now, in big type, on a pane lit
  from one corner by Noor's gold with a gold medallion: Adhan, the next Adhan, its sound and the one switch;
  Settings, the place the times are for and the authority they follow (the card opens the location page).

### How Noor is organised (0.15)

The owner (16 November 2026): "see how professionals in user interface design... understand the human mind... Apply
that to my app, like organizing all the features and settings and options... intuitive. Like a person will know how
to use my app with their instinct." The whole app was inventoried (every page, row, option, entry point, duplicate
and test that depends on it), then reorganised by these rules, from Apple's Human Interface Guidelines (settings,
tab bars), Android's settings design guidelines (source.android.com/docs/core/settings/settings-guidelines), Nielsen
Norman Group's ten usability heuristics, progressive disclosure and mobile navigation articles, and the laws of
interaction design (Hick, Fitts, Jakob, Miller):

- **A setting lives where people look for it, in their words.** Prayer times, then the Adhan, then how Noor looks,
  then the watch, then about: the order of need. Names say what a thing is, the most important word first, no
  slashes and no jargon: "Calculation method" (was "Authority / method"; every prayer app calls it so), "Theme"
  (the row was "Appearance" inside a group called Appearance), "Home screen" (the scenery, the sky and now the
  countdown's way after a prayer, which is seen there), "Language and region" (as on iOS: language, numerals, time
  format and the Hijri calendar).
- **Few choices on the first page** (Hick; Android: ten to fifteen at most, the most used first). Settings' first
  page has nine rows under the place's hero card (twenty before). What most people set once and never again is one
  level down, on a page named for what it holds: **Prayer times** (opens on what the times follow and on what
  grounds; then the method, Asr, personal adjustments; the high-latitude rule apart, with a footer saying it only
  matters far north or south; the accuracy page, where the question "can I trust these?" arises) and **Language
  and region**. Two levels, never three (NN/g: more than two "typically have low usability").
- **One home for each thing.** Alert history lives on the Adhan tab (Settings had a second copy); the location's
  rows sit under the place's card they belong to. Search still finds every setting on every page, says which page
  it is on ("In Prayer times"), and works it from the results.
- **The way out is always in view** (user control and freedom; recognition rather than recall). The tab bar stays
  on every page of a tab, with the tab one is in lit, as on iOS; only the welcome page and the location chooser,
  tasks with a keyboard or a map, hide it. Tapping the lit tab returns to its first page.
- **Links, not directions.** "Choose Watch or Both in the Adhan tab" on the Watch tab is now a link to it.
- **The heart of the app is offered, not hidden.** Prayer alerts start off (Android must be asked for each
  permission), and until 0.15 nothing on the way in said they existed. The welcome now has a third step, "Hear the
  Adhan at each prayer", after the place (also when the place is chosen by hand): "Turn on the Adhan" switches the
  alerts on and asks for Android's notification permission at that moment, when its reason is plain; "Not now"
  leaves them off (MainFlowTest checks it).
- **Help in context, once.** The home screen's hidden gesture (drag the sky to follow the Sun) is said once, on
  the sky, in a small capsule, until the first drag; it never returns (`AppSettings.scrubHintDone`).

### Self-critique and review (0.15, second round)

The owner, the same night: "look at your own work and criticize yourself... Is this really how intuitive software
should look like? And if it's not, how can you improve it?" The pages were reviewed again, by the lead and by an
independent reviewer that had not seen the design's reasoning (the renders only, against Apple's Settings, Clock and
Weather, Google Clock, Muslim Pro, Athan, Pillars). What it found and what was done:

- **A number with only a sign** ("+15:21") read as a time of day. The countdown now says what it counts, under it:
  "since Asr began", "since sunrise", "until Maghrib" (the sign stays: the owner's design, shared with the watch).
- **Turning on one prayer's Adhan took three screens.** Each prayer's bell on the home screen is now a switch of its
  own (gold when that prayer will alert); the row still opens the details. With all alerts off, the first bell or
  switch turned on turns them on for that prayer alone; the notification permission is asked at that moment.
- **The Adhan tab was empty while alerts were off** (the state everyone starts in): the prayers are now always
  listed, quiet and switched off until then, so what would happen can be seen and set first.
- **"Fajr 5:17 AM" in big type over a problem that silences it.** What stops an alert altogether (notifications
  off, the Adhan category off, the alarm volume at zero, a watch's notifications off) is now said on the hero card in
  the danger colour ("Will not sound until the problem below is fixed.") and listed first, before what only makes it
  late. The exact-alarm warning is shorter.
- **"Will it really sound tonight?"** "Test the Adhan in 10 seconds" is on the Adhan tab itself, with the history.
- **A recording's city read as the place** ("Adhan · Malmö" for someone in Dubai): recordings carry a note mark,
  "Adhan · ♪ Malmö".
- **"Sound", "Notification", "Silent" had to be tried to be understood**: each kind of alert says what it is in three
  words ("The full call", "A brief tone", "Your phone's sound", "No sound"), and "Plays: …" names what will play.
- **Jargon and silence**: Asr names the school in use, also when it is the method's own; the high-latitude rule is
  listed only where it can matter (45° and beyond) or once changed; the Prayer times card says on what grounds in a
  sentence ("Its own published times, for each day."); the Hijri row shows the date it gives ("Today: 6 Jumada
  al-Akhirah 1448 AH"); the calendar marks the year's great days with a gold dot, and the date line names them.
- **Small things**: the drag hint keeps out of the way while another day is shown; the Qibla screen says to
  calibrate first when the compass is unsure; the welcome's second way is an outlined button; "Notification bar".
- **Kept on purpose**: "Shuruq" and the countdown's sign (the owner's), the Watch tab (the owner's product).

### The Adhan section (0.16)

The owner (16 November 2026): "If I want to change the adhan sound for all my prayers at once, I can't even do that...
setting alerts before Adhan or after Adhan and on specific days of the week"; the Adhans of his Al-Fajr desk clock,
"the same names" (its dial: Makkah, Madinah, Al-Quds, Egypt, Riyadh, Random); "The AI generated sounds... some of the
sounds even sound like music. I don't want any music in my app. Zero music"; and fewer words everywhere ("is this
really even necessary to tell the user?"). Modelled on the clock (one dial for every prayer), alarm clocks (repeat
days) and prayer apps (a reminder before, an iqamah reminder after):

- **One voice for every prayer**, first on the Adhan tab ("Adhan voice · Makkah"), opening the voices page: the
  clock's six by its names, then the other recordings of the library, then the user's own sounds, each with ▶ to hear
  it first. Choosing one sets every prayer that calls the Adhan (or plays the user's sound); Fajr keeps a Fajr Adhan
  (with "As-salatu khayrun min an-nawm") unless the voice is one, or it is chosen on Fajr's own page. A prayer's page
  opens the same voices for that prayer alone.
- **The clock's voices.** Noor has licensed recordings from Makkah and Madinah only: Wikimedia Commons, searched in
  full (tools/sounds research list, 191 entries), has none from al-Aqsa, Egypt or Riyadh. The clock's own recordings
  belong to Al-Fajr and cannot be shipped without its permission (the owner will arrange licences before release).
  Meanwhile Al-Quds, Egypt and Riyadh read "Add a recording": the user's file (a recording of their own clock, say)
  takes the voice's name and stays on their phone. Licensed recordings replace these when there are any.
- **When**: each prayer's page has seven day toggles (the week in the language's order; at least one stays on), a
  reminder before the Adhan and one after it (the iqamah: "Asr began 20 minutes ago"); Alert options sets either for
  every prayer at once ("Mixed" when they differ). A prayer's row on the tab shows small signs for these.
- **No tones.** The phone's kinds of alert are the Adhan, its opening, the user's sound, a notification (the phone's
  own sound) and a silent notice; the fourteen generated alert sounds are gone from the phone (the watch keeps them for
  its own setting on versions up to 0.9.12). A prayer set to one alerts with a notification.
- **Fewer words**: warnings in a line ("Alerts may come late. Allow exact alarms."), notes in a phrase, the Qibla's
  note on true north, the ellipsoid and the decimals removed.

### Alarms and the Adhan tab (0.17)

The owner (16 November 2026), on 0.16: "I am still not satisfied at all with the Adhan tab... The UI is not
intuitive. And what I meant with the alarms is not what you implemented... like your phone clock app... set this
alarm, let's say 10 minutes after Dhuhr Adhan, or set another alarm 25 minutes before Al-Fajr Adhan." 0.16 had read
"alerts before or after the Adhan" as reminder notifications hidden on each prayer's page; he meant alarms.

- **Alarms**, a page of their own: the Adhan tab opens with a switch, Adhan | Alarms. The alarms page reads as the
  Clock app's: "Next alarm in 9 h 12 min" at the top, then each alarm as a card with its name, the time it rings next
  in large type ("4:52 AM"), what it is and its days ("25 min before Fajr · Mon–Fri"), and its switch; Add alarm
  under them. With none, what an alarm is for and the button. They do not depend on the Adhan being on, and ring on
  the phone.
- **The editor** (after the owner, on the first version with − and + and preset minutes: "Don't give me presets for
  the amount of time. I want to control the exact amount of time like my clock app. Also I want to name the alarms"):
  at the top, the time it will ring in large type, and the day and what it is under it, answered as the wheels turn;
  then four scroll wheels that read as the sentence, [hours] [minutes] [before/after] [prayer] (`ui/components/Wheel.kt`:
  five rows in sight, a band behind the middle one, a tick as rows pass, the minutes going round); then the alarm's
  name; the days; the sound, vibration and Snooze; Try it in 5 seconds and Delete; and Cancel and Save in a bar at
  the bottom, always in reach. 0 h 0 min cannot be saved. Back or Cancel leaves the alarm as it was.
- **Never the Adhan.** An alarm rings with the phone's own alarm sound (the one the Clock app uses), a notification
  (for a reminder), or a recording of the user's. The Adhan is never an alarm's sound: heard half asleep 25 minutes
  before Fajr it could be taken for Fajr having come, and a prayer prayed before its time. The editor says so in a
  line.
- **Never over the Adhan.** An alarm due while the Adhan sounds comes as a notification; the Adhan due while an
  alarm rings replaces it, and the alarm stays in the shade. Alarms ring on vibrate and silent like the Clock app's
  (the Adhan's "quiet in silent mode" is the Adhan's alone); a call, or Do Not Disturb blocking alarms, quiets them.
- **Ringing** like an alarm clock: full screen over the lock screen with an alarm clock (not the Adhan's sign), the
  sound over and over and a repeating vibration for up to ten minutes, a big Stop and a wide Snooze; Snooze and Stop
  in the notification too; a snoozed alarm's note says until when, with Dismiss.
- **The tab, self-critique.** 0.16's rows repeated "Adhan · ♪ Makkah" five times and hid what was set behind three
  unexplained small icons. Now each prayer row is its name, today's time and its switch, and a line only where it
  differs from the others (Fajr's own recording, the opening only, a notification, some days). The voice for them all
  is the first row of the same group. Each prayer's page has four kinds of alert, two by two (the user's own
  recording is a voice, not a kind) and its days. "Alert options" is "More settings".
- 0.16's reminders before and after the Adhan (and 0.9.9's single lead) became notification-only alarms, so nothing
  set is lost and all of the kind is in one place.

### Priority by use (0.15.2)

The owner: "some features are used more than others... the more used features should get the priority... clean,
simple, sleek... nothing complicated or overwhelming... I don't think the watch should be included on the bottom
buttons... just put in the settings." And: of what was just built, "is this really needed? Does this make things
more complicated?" So every page was sorted into what is used often and what is set once and left:

- **Four tabs, in the order of use**: Today, Qibla, Adhan, Settings. The watch is set up once: its page opens from
  Settings ("Galaxy Watch · Noor on your wrist"), and keeps the tab bar with Settings lit.
- **Adhan, two levels**: the first page holds only what is used often (the next Adhan and its switch, what needs
  fixing, the six prayers, the test) and one row, **Alert options**, says what is set there ("Phone · Off") and opens
  it: the reminder, phone or watch or both, the gradual volume, silent mode, the library, my sounds, the history and
  the battery advice. The first page went from eleven groups' worth to four.
- **The home screen's place**: one line, the flag and the name (a small arrow when it follows the phone); its second
  line and the refresh button on the sky are gone (the location follows by itself; Settings keeps "Refresh").
- **Settings** no longer repeats the Adhan tab; the watch's row stands without a group title; the notification
  bar's description is one line.

### The Adhan and alarms area (0.25)

An independent reviewer of 0.24's renders: "organised around the app's problems and options, not the person. A wall
of four warnings repeats on both segments, the same choice appears in two different UIs, the hero's switch is
ambiguous, and there is a 'More settings' drawer." What was done about each finding, with the measurements and the
decisions the owner may want otherwise, is in [ADHAN_025_NOTES.md](ADHAN_025_NOTES.md). In short:

- **The tab is "Adhan" and so is its page**; under one control, **Prayers | Alarms**.
- **One switch, one name.** The hero is always "Prayer alerts", with one line under it: "Next: Fajr 5:20 AM", "Off",
  or in the danger colour "Will not sound".
- **One line and one button for what needs fixing** (`FixCard`), and only what affects the page it is on. One thing:
  its sentence, and Fix does it. Several: "4 things to fix", and Fix opens them in a sheet, the most serious first.
- **All prayers**: the Adhan's voice is one row that opens the voices (a touch there chooses and plays the opening);
  the volume is one slider (`NoorSlider`: a thin track, a round white thumb). Then the six prayers, each sign in the
  colour of its hour; then the test, the history, and **Sound and devices** (what is set once).
- **What hangs on a switch that is off looks off** (`OffWhen`: 38%, no touch, not read out) and keeps its place.
- **A kind of alert is a row of a list** with a check, not a tile of a grid.
- **One segmented control** for the whole app (`Segmented`), the home screen's settings included; its fills and inks
  are measured by `SegmentedContrastTest` (4.5:1 for both labels wherever it stands, at every height of the Sun).
- **An alarm's editor** has Cancel and Save in its bar and no other way out; its wheels carry their units in the band
  ("0 h  25 min  before  Fajr"). A clock time is written with its AM or PM at half size (`ClockText`), in the alarms'
  list, the editor and the ringing screen.
- **The ringing screen** shows the time at the top, what is sounding under it, and Stop, Snooze and "Hide and keep
  playing" on the foot of the screen.

## The interface of 0.14: one system, and an opening

The owner (15 November 2026): "totally revolutionize the user interface and keep the Apple aesthetic... make the
user interface a lot more intuitive"; "as soon as I open the app, I want to have a beautiful animation". The whole
account, screen by screen, with the seventeen bugs fixed on the way, is in [UI_014_NOTES.md](UI_014_NOTES.md). In
short:

- **One page for every screen** (`ui/components/Page.kt`): a tab opens on a large title that scrolls away into a
  slim bar; a pushed page has the slim bar with a gold back chevron. Content is never wider than 640 dp (the
  owner's phone folds open). One margin, one grid, one kind of grouped list, one button, one search field, one
  segmented control; choices open in a sheet from the bottom, where the thumb is. Gold means "this can be acted
  on, or is chosen", and nothing else.
- **Order of use.** Today: a bell at the end of each time says what will happen then, and the row opens that
  prayer's alert; the place opens the location page. Adhan: the one switch, what needs attention (only when
  something does, each row its own fix), the prayers, then everything else. Settings: where, how the times are
  worked out, alerts, the notification bar, appearance, language, calendar, watch, about (and, until release,
  testing).
- **The Qibla screen keeps its design.**
- **The opening, "first light".** Android's launch screen is the sky's own waiting colour with Noor's crescent
  and star; the app takes it over without a cut; light gathers round the mark, the mark dissolves into it, the
  light travels to where the Sun stands in the sky behind, and the dusk opens from there like a soft iris onto the
  home screen, whose Sun runs to this moment while the place, the countdown and the day's times settle in. 2.3
  seconds at most; a touch finishes it in a quarter of a second; with "Remove animations" nothing moves; the dusk
  waits up to 0.7 s for the real sky's first picture (`RealSky.waitingForPicture`). It plays on a cold start, and
  again when Noor comes back to the home screen after fifteen minutes away (without the dusk).
- **Tester's options** (Settings → Testing; `com.noor.app.testing`; English only; nothing stored; to be removed
  before release): the time of day, a day that runs by itself, the season, the kind of weather, fog, the scenery,
  the place chooser, and a demo through every scenery. A strip over the tab bar says what is being shown while
  they are on; a tap on it goes back to the real sky.

## Components

(The list below is 0.6's; 0.14's components are described in [UI_014_NOTES.md](UI_014_NOTES.md), section 1.)


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
  When the compass is unsure (0.22.1, the owner: "it should display the figure 8"), "Calibrate the compass" stands
  under the dial over a glass card that shows the gesture: a phone gliding along a figure 8 with a fading gold trail
  (2.8 s a loop; a still drawing when animations are off), and one line, "Move your phone in a figure 8"; tapping it
  opens the calibration guide, and it folds away once the compass is sure (`CalibrateNudge`, renders in
  `QiblaCalibrateDesignTest`). 0.25 ([PAGES_025_NOTES.md](PAGES_025_NOTES.md)): what the phone's compass says is in
  whole degrees ("Turn right 83°", "258°", "175°"), as a compass good to a few degrees can honestly say; the Qibla's
  own bearing, which is calculated, stands once under the readings to a tenth ("258.3° from true north"). The dial's
  letters and figures are in Plex. "Hold your phone flat" is said once, in the dial. The page is the shared tab page
  (`NoorPage`); the accuracy card is as tall as its words; the calibration guide is the app's sheet with three steps
  and Done.
- **The watch page's one watch** (0.25; `WatchCase`, `StudioCard`) is the user's own watch face in a drawn watch's
  case, drawn by the renderer the watch draws it with (this moment's time, today's date, the app's language), with
  the glow of the current sky under it; it says "on your watch" only when the watch reports it. (Until 0.24 a
  hand-drawn copy of the watch app's home screen stood there, and the face further down as a second watch.) The
  ready-made faces under step 3 are chosen from small pictures that say they are samples.
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

- Entrances: the day's path draws itself from midnight to now and the sun travels into place (1.2 s); the welcome
  (0.25, WELCOME_025_NOTES.md) is one picture, the Earth's edge at first light, that lets in more light page by page
  and leaves on the sunrise.
- State: countdown digits roll; the next prayer's name slides in when it changes; tabs cross-fade; pages slide in from
  the reading direction; the "Today" pill appears only when another date is shown.
- Ambient (home screen): clouds drift, stars twinkle, the sun's rays turn, the next prayer's ring breathes, birds and
  meteors pass now and then. All of it stops when Android's "Remove animations" is on.
- Interaction (home screen): drag the sun through the day; it springs back to now when let go.
- Scrolling: the sky and clouds move at about half speed behind the land and the content (parallax) and the header
  text fades away.

## The watch

(0.25: redesigned; the account, screen by screen, is in [WATCH_025_NOTES.md](WATCH_025_NOTES.md) and WATCH.md, "The
look of the watch app (0.25)".) Same family on a black OLED screen: the same typeface in SemiBold, the same order
(prayer, countdown, time), gold for what matters. The home screen's first screenful is the next prayer alone over the
sky of the moment, in the phone's own sky colours darkened just enough for white text (5:1, `WatchSkyTest`), with
stars at night and dunes along the bottom; everything else is black with dark platters (18 dp corners), a coloured
disc for each sign, green switches, and one type scale. The interval ring keeps a gap at the top for the system clock.
Facing the Qibla is an arrival: the whole field turns green with a check. The two tiles follow (the prayer in gold,
its time very large; three coloured tools).

## Arabic and right-to-left

Layouts mirror, including the timeline; numbers, times and the countdown always read left to right. The sky scene does
not mirror (0.23: the Sun's path, the land, the light and dragging are as in real life whatever the language; the owner,
18 November 2026: "make sure the sunpath reflects real life exactly (regardless of app language setting)"). Arabic display names use Reem Kufi; the Hijri date leads in gold.

**The one exception to "nothing changes at a stroke" (0.18 review):** cloud sets are read ahead of the moment they come
into view (`SkyDirector.ahead`), so they are normally on the GPU before they are seen. If a phone is too slow and a set's
pictures arrive after it has entered the view, the set fades in over 0.7 s rather than appearing at a stroke; the
renderer logs it (`NoorRealSky`). It is a fallback, not a design: the read-ahead is meant to make it never happen.
