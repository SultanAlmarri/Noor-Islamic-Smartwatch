# Sky bench

Renders the home sky's AGSL shaders (`app/src/main/res/raw/*.agsl`, unchanged) in WebGL 2 in headless Chromium, with a
JavaScript copy of the sky model, to iterate on looks quickly and make contact sheets and videos. The app's own sky
model is `Atmosphere.kt` (authoritative; `atmo.js` is a port kept close to it). Confirm on Android with the
`home-sky.yml` workflow: the bench is WebGL, not Android's shader compiler.

```
cd tools/skybench
python3 scenes.py 1.5                     # writes scenes.json (Dubai and Istanbul through a day)
NODE_PATH=$(npm root -g) node run.js scenes.json out
python3 sheet.py dxb.png 7 out/dxb_*.png  # contact sheet (needs Pillow)
python3 sksl_size.py ../../app/src/main/res/raw/sky_common.agsl ../../app/src/main/res/raw/sky_layer.agsl
python3 mkvid.py open_dxb && node run.js open_dxb.json v && ffmpeg -framerate 30 -i v/f%04d.png out.mp4
```

`render.js` adds a GLSL prelude (`float2` → `vec2`, …, `child.eval` → `texture`) and draws the overlay (path,
marks, text) roughly as the app does. `sksl_size.py` estimates SkSL's unrolled program size (keep under 65 000).
