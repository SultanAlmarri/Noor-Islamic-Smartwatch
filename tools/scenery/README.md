# The land of each scenery

The home screen's camera never moves. So the land of every scenery is not drawn by the phone: it is **rendered here,
once, by a path tracer** (Blender Cycles), through Noor's camera, under a set of Suns of unit strength and under a
plain white sky. The phone adds those pictures together in the proportions and the colours of the moment, and so
relights real geometry with real shadows for any time of day or night. The sky, the clouds, the Sun, the Moon, the
stars, the water and the weather are drawn live on top (`app/src/main/assets/sky2`).

A scenery is one script in this folder (`dunes.py` is the worked example) that builds a Blender scene and hands it to
`bake.run`. Its output is a folder of small pictures in `app/src/main/assets/sky2/land/<id>/`.

## Tools

- Blender 4.5 LTS: `%USERPROFILE%\ProjectMinhad\NoorTools\blender-4.5.16-windows-x64\blender.exe`
  (Cycles on the GPU through HIP; its own Python 3.11 with numpy: `...\4.5\python\bin\python.exe`).
- Work files (never committed): `%USERPROFILE%\ProjectMinhad\NoorTools\work\<id>\`.
  Downloads (never committed): `%USERPROFILE%\ProjectMinhad\NoorTools\assets\<source>\`.

```
blender -b --factory-startup -P tools/scenery/<id>.py -- preview  <out dir> [sun bearing] [sun height]
blender -b --factory-startup -P tools/scenery/<id>.py -- look     <out dir> [sun bearing] [sun height]
blender -b --factory-startup -P tools/scenery/<id>.py -- bakefast <out dir>
blender -b --factory-startup -P tools/scenery/<id>.py -- bake     <out dir>
```

- `preview`: a small picture (432 x 1056) under a physical sky, a quarter of a minute. `look`: the same at full size.
  Bearing in degrees right of the view's middle, height in degrees. Try at least `60 25` (afternoon), `-50 12`
  (morning), `15 3` (the Sun setting in the picture, at its notch) and `-15 2` (rising).
- `bakefast`, `bake`: every light at once (one render, a light group each), then packed into `<out dir>/land`.
  The packer prints whether the skyline keeps its limits (below).
- `NOOR_TAG` (environment) is added to a preview's file name; `NOOR_EXPOSURE` changes its exposure (default -2.3).

## The camera, and what the phone shows of the picture

- Scene units are metres. The camera stands at `eye` (pass it to `bake.run`), looks level along **+y**, +x is to
  the right, +z is up. Its lens is shifted like a view camera's, so the horizon lies on a fixed row and upright
  things stay upright.
- The picture is 1080 x 2640 with the horizon on row 1056. It sees 23.5 degrees to either side of its middle, 40
  degrees up and 52 degrees down.
- On the phone, text is written over the upper sky, and the day's prayer times stand on dark glass over the lower
  land. **What is seen whole is a band: from about 16 degrees above the horizon to about 10 degrees below it.** In a
  preview picture that is rows 280 to 510 (the horizon is row 422). Put the scenery's beauty there: the far and the
  middle distance. Land lower than 10 degrees under the horizon is seen through the glass, darkened: it should be a
  good, simple foreground (texture, a few large forms), not the place for the main subject.
- A scenery needs a foreground, a middle distance and a far distance, and it needs life: plants that move in the
  wind, water, things that give scale.

## The skyline: natural, never cut open

The Sun and the Moon are drawn on the phone where the prayer times put them: they rise 14.7 degrees left of the
view's middle, set 14.7 degrees right of it, and cross the sky between on an arch that tops out 14.2 degrees above
the horizon.

**A scenery's skyline is whatever the place's skyline is.** The Sun may rise over a mountain and set behind trees,
as it does in the world. Never cut a range, a treeline or a row of rocks open so that the Sun meets level ground
(the owner's decision, 14 November 2026: "what I don't like is cutting mountains in order to have the sunrise and
set on the ground... make everything look natural"). A level horizon where the Sun rises or sets is welcome when
the place has one (the sea, a plain), and wrong when it is forced.

The one thing to keep in mind is the middle of the day: land so high under the arch that the Sun is hidden for most
of its day is too much. `bake.skyline_limit(bearing)` gives a height to stay under between the two points (about two
thirds of the arch; 11.7 degrees in the middle), and the packer prints how the skyline stands against it, as advice.

## Light and materials

- Add **no lights and no emission**; `bake.run` makes the Suns and the sky. Use physically plausible materials
  (Principled BSDF, base colours no brighter than fresh snow, about 0.85).
- The phone multiplies one picture of the land's colour (`a.webp`) by pictures of how brightly it is lit: coloured
  light bouncing between surfaces is kept only as brightness. That is fine.
- Real geometry beats shading tricks: use displacement (`mat.displacement_method = 'DISPLACEMENT'`, with
  `bake.adaptive(object)` for polygons the size of a pixel), instanced plants and rocks.

## Water, wind, snow: the mask

The phone draws water, moves plants in the wind and lays snow in winter. It needs to know where, from a picture
called the mask. Give every material its place in it with `bake.add_mask(material, wind=..., snow=...,
water_level=...)` and pass `aovs=('mask',)` to `bake.run`.

- **Water is never modelled.** Model the bed and the shore (they show through clear shallows), and pass the water's
  height to both `add_mask(..., water_level=z)` (for every material that goes under it) and `bake.run(...,
  water_level=z)`. The phone draws the surface: reflections of the sky and of the land, waves, the Sun's glitter.
  Previews show a plain dark mirror in its place.
- `wind`: 0 for rock and soil, up to 1 for grass and leaves (these are also the pixels that turn with the seasons).
- `snow`: how much snow a surface takes in winter, 0..1 (open ground, the tops of conifers; not cliffs, not water).

## What may be used

- Nature only: sky, light, land, water, plants, mist, snow. **No buildings, roads, vehicles, fences, people or
  anything man-made.** Animals are drawn by the phone (distant birds), not baked.
- Only assets that may legally be shipped: your own procedural work, public-domain data (USGS 3DEP elevation,
  NASA), CC0 (Poly Haven, ambientCG), or data whose licence allows redistribution with attribution (Copernicus
  DEM). **Write down every source** (what, where from, its licence) in `tools/scenery/sources/<id>.md`.
- No scraping of sites that do not allow it. Reference photographs are for looking at, never for copying into the
  scene.

## What is shipped (`land/<id>/`)

| File | What |
|---|---|
| `a.webp` | the land's colour under white light; alpha = where there is land |
| `k.webp` | the land lit by a plain white sky |
| `s_l0..l7`, `s_r0..r7`, `s_top` | how brightly each pixel is lit by a Sun on the left / right at 0.7, 2.5, 5, 9, 14, 22, 34, 50 degrees, and from high in front |
| `s_gl`, `s_gr` | ... and by the glow of the sky round a Sun just under the horizon, left and right |
| `g.png` | how far each pixel is (16 bits, log scale, half size) |
| `m.webp` | the mask: R water, G wind, B snow |
| `land.json` | sizes, the camera's height, the water's level, the Suns |

Keep a scenery under about 3 MB.

## The standard to meet

The owner judges these pictures against photographs of real places, and so must you: put a preview next to real
photographs of such a place and ask what gives the render away (shapes too smooth or too regular, repeating
patterns, colours too pale or too even, no small detail, plants like plastic, no sense of scale). Fix that, and
look again. A render that only looks good for a render is not good enough.
