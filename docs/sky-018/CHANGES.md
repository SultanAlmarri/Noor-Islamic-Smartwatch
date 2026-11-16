# The home sky: fixes from the owner's report of 16 November 2026 (0.18 work branch)

Pictures in this folder are from the design bench (the phone's own shaders in headless Chromium, a software GPU), with
the Sun's path drawn over them the way the app draws it (`P.arc` in `tools/sky3d/bench.js`). They are not phone
screenshots.

| Owner's report | Cause found | Fix |
|---|---|---|
| The sun-path arc is good on some sceneries, horrible on others | The path was cut only at the level horizon, so over the fjord, the forest lake, the red rock and the green hills it ran down the mountains and trees to that line; and where the skyline is high it was held under the land (next row). On bright hazy skies the white dashes vanished. | The path is drawn behind the land, above each scenery's own skyline (`land.json` `skyline`, `tools/scenery/skyline.py`, `SunLift.above`); a soft dark track under it, stronger by day. `arc_*_before_after.jpg`, `day_*_before_after.jpg` |
| The Sun disappears as it rises and sets | Where the skyline at the rising or setting point is higher than 0.72 of the arc's height, the path was held under the skyline: the Sun rose and set behind the mountain, out of sight at Shuruq and Maghrib (the fjord on most phones; the forest lake, the red rock and the hills on smaller ones). On fog mornings over the sea the Sun was put out entirely by kilometres of fog. | The horizon lies lower over such a scenery, just enough for the path to stand on the skyline (`HeroGeometry.horizonFor`; at least 30 dp of foreground kept above the glass). The Sun shows through fog as a pale disc. `fog_sunrise_after.jpg` |
| The progress line cuts off where it meets the Sun | It was clipped by a vertical edge through the Sun's middle. | The line (and the dashes ahead) thin out towards the disc and are gone under it (a radial brush). |
| Clouds pop in and out, above all while dragging | A set was shown only once every shading picture of the moment was read; a drag asks for new ones all the time, so whole sets left the sky and came back. New sets were read only once already in the view. The clouds were also carried on for "time since the minute" whenever the moment shown was within 150 s of the clock: crossing that line (dragging through now, the spring back) threw the low clouds about 5 degrees at a stroke. | A lit set stays on show while new shading is read; the sets next on their way are read ahead (`SkyDirector.ahead`; locked by `SkyDirectorTest.theNextSetsAreKnownBeforeTheyComeIn`), and the neighbouring Suns' shading while dragging; a set still late comes in over 0.7 s. The carry-on fades with distance from now. |
| Choppy start, opening never smooth, drag stutters | See "Performance" below. | |

Other bugs found and fixed: the forest lake's and the red rock's distance pictures had 2 481 and 1 571 texels along
their treelines holding a constant 2.5 m / 2 m, which showed in fog as dark specks (`tools/scenery/fix_distance.py`);
the fog's edge round near trees went in steps (half-sharp distance picture read texel by texel).

## Performance (no change to what is drawn)

- The renderer builds its shaders and starts reading the scenery behind the launch screen (`RealSky.prewarm`).
- Pictures are decoded by 2–3 threads, what the screen needs first, into reused bitmaps and buffers (no 4–10 MB of
  garbage per picture); at most ~6 MB uploaded to the GPU per frame while the scene moves; the night's three large
  pictures no longer delay a daytime first picture.
- The day's state, the days either side and the sun path are worked out off the main thread (`TodayViewModel`,
  `SunPaths`).
- A drag or the opening recomposes only the sky (`AtMoment`, `derivedStateOf`), not the whole header; the path's and
  marks' layers are built once per scene and size, not every frame.
- A baseline profile (`app/src/main/baseline-prof.txt` + profileinstaller) so the start and the home screen are
  compiled ahead on a phone the APK is installed on by hand.

## Not fixed (needs the owner or a re-render)

- The forest lake's tall fir stands in the Sun's morning path: the Sun is behind it for a while on most days. That is
  the scenery's composition; moving it needs a Blender bake.
- Nothing here has been seen on a phone.
