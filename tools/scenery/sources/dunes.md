# Arabian dunes (`dunes`): sources

Nothing is downloaded. Everything in this scenery is made here:

- **The sand sea**: `tools/scenery/dunesim.py`, a Werner sand-transport simulation (sand moved by wind until it forms
  dunes; integer slabs, seeded, so the same command gives the same field on any machine):
  `python dunesim.py field768.npy 768 500 12 11`. `dunes.py` sharpens its brinks and slip faces, lays it out under
  Noor's camera, and adds wind ripples and the sand's colour (procedural noise). Far off, the same field read seven
  times larger stands for the great dunes.
- **The camera's place** in that field is pinned in `dunes.py` (`STAND`): the view the app has shipped since 0.11.

## 0.19: sand only

The owner (17 November 2026), of the High graphics level: "on the sand dunes, I don't want the distant terrain.
Remove the distant terrain from the sand dunes. I just want the sand dunes." Until then a range of bare rock (made by
formula: ridged noise, 46 to 70 km off, "the Hajar seen from the desert's edge") stood along the whole horizon. It
was taken out of `dunes.py` and the scenery was baked again (in a cloud session, CPU, in four parts). The far great
dunes were lowered (`GREAT` 3.6 to 2.4): alone on the skyline, the highest of them showed against a low Sun as a long
flat-topped ridge; now the horizon is a low line of dunes.

The place and the view are the shipped ones (same dunes, same ripples underfoot), but the sharpened surface made in
the cloud session is not quite the PC's (its cached copy is gone): the near crest stands about 18 pixels (0.8 degree)
lower in the full-size picture than in the 0.11 bake, and the middle distance differs a little in the same way.
`land.json`: `lift` 0.0209 / 0.0225 (rise / set, tangents) became 0.0081 / 0.0032; the skyline's highest point
0.0306 became 0.0113 (it was the rock range; now it is dunes).

The Settings picture (`res/drawable-nodpi/scenery_real_dunes.webp`) was made again on the design bench with
`bench.thumb` (560 x 420, webp quality 86): `biome: 'dunes'`, `skyHigh: 'hi_wisps'`, no low or middle cloud,
`tod: 0.69`, `doy: 318`, `lat: 25.2`, `season: 0.5`, text and panel off. (How the 0.11 picture was set up was not
written down; this is the closest look found.)
