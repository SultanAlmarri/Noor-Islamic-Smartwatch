# Red Rock (`canyon`): sources

The scenery is **Monument Valley as it stands**, made by `tools/scenery/canyon.py`: we are on the valley floor east
of the buttes (36.9770 N, 110.0452 W, 1 520 m), looking west-north-west (282 degrees): Mitchell Mesa at the left
edge, Merrick Butte left of the middle, the West Mitten far off right of the middle (end on, its thumb before it),
the East Mitten nearest, on the right.

## The land's shape (USGS, public domain)

| What | Where from | Licence |
|---|---|---|
| Heights of the land: USGS 3D Elevation Program, 1/3 arc-second (ten metres), tiles `USGS_13_n37w111`, `n38w111`, `n37w110`, `n38w110` | `https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/13/TIFF/current/<tile>/USGS_13_<tile>.tif` | public domain (U.S. Geological Survey) |

`canyon_dem.py` cuts the valley out of the four tiles; `canyon_terrain.py` reads it. The heights are used as they
are: no butte is moved, added, removed, raised or lowered, and nothing is cut open for the Sun. (The 1 m lidar of
the 3DEP stops at the Utah line, a kilometre north of the Mittens, so the ten-metre model is the best there is for
them.) Only pictures rendered from the model are shipped, not the model.

Its surfaces and everything that stands on the land are photogrammetry of real rock, ground and plants from
**Poly Haven** (https://polyhaven.com), all of it **CC0** (public domain: free to use and to ship, no attribution
required; it is recorded here all the same). The scans are fetched with `NoorTools/assets/polyhaven/get.py <id>
<resolution>` from Poly Haven's public API; none of them is shipped as such.

## Scans used (Poly Haven, CC0)

| Id | Kind | Resolution | Used for |
|---|---|---|---|
| `rock_face_03` | surface (colour, relief) | 2k | the buttes' great walls of massive sandstone, laid on many times its true size |
| `cliff_side` | surface | 2k | walls of thin beds (the bench round each butte's foot, the ledges, the cap, the upper talus), and bare rock on the floor and the rise we stand on |
| `red_laterite_soil_stones` | surface | 2k | the talus and the skirts of the buttes (enlarged: its stones stand for blocks), stony patches of ground near us |
| `red_dirt_mud_01` | surface | 2k | the red earth of the floor: at its true size near us, enlarged far off |
| `red_sand` | surface | 2k | loose sand in the washes and the drifts |
| `namaqualand_boulder_02` | model | 2k | fallen blocks round us, rock breaking out along the floor's ledges, blocks on the buttes' talus (enlarged) |
| `namaqualand_boulders_01` | model | 2k | stones round us |
| `wild_rooibos_bush` | model | 1k | the brush of the floor within 400 m |
| `searsia_burchellii` | model | 1k | the dark evergreen shrubs that stand for junipers |
| `grass_medium_01` | model | 1k | tussocks of dry grass near us (tinted straw) |
| `dead_tree_trunk_02` | model | 1k | the dead trunk lying near us |

The scans' colours are tinted towards the red of the Colorado Plateau (their own light and dark and their own
detail are kept).

## Made by formula (no data exists for it)

- What is finer than the model's ten metres: the joints and beds cut back into the walls it rounds off (relief on
  the model's own walls; outlines, heights and positions stay the model's), and the smallest swells of the ground
  near us (a hand to a knee high).
- What the ground is made of, read from the model's shape: bare rock where it steps, a sandy bed in its hollows,
  brush thick near the hollows.
- Brush beyond 400 m, where a bush is a few points of the picture: a lump of its colour; wind ripples in loose
  sand near us; the specks that stand for brush beyond a kilometre.

## Tools

| What | Licence |
|---|---|
| Blender 4.5 LTS, Cycles (renderer; nothing of it is shipped) | GPL |

## Looked at, never copied into the scene

To judge the render against real places (Monument Valley's Mittens and Merrick Butte), on Wikimedia Commons:
"West Mitten Butte - Monument Valley" (Luca Galuzzi, 2007, CC BY-SA 2.5); "West Mitten Butte, East Mitten Butte,
and Merrick Butte" (CC BY-SA); "View east-northeast from Lookout Point toward the Mittens in Monument Valley Navajo
Tribal Park" (2013); "Monument Valley Sunset MC".
