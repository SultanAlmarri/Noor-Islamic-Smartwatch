# Red Rock (`canyon`): sources

The scenery is made by `tools/scenery/canyon.py`. Its surfaces and everything that stands on the land are
photogrammetry of real rock, ground and plants from **Poly Haven** (https://polyhaven.com), all of it **CC0** (public
domain: free to use and to ship, no attribution required; it is recorded here all the same). Only pictures rendered
from the scans are shipped (`app/src/main/assets/sky2/land/canyon`), never the scans themselves; they are fetched
with `NoorTools/assets/polyhaven/get.py <id> <resolution>` from Poly Haven's public API.

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

## Made by formula (no scan exists for it)

- The shape of the land: the floor's swells, washes and ledges; each butte's form (outline, talus cone, bench,
  wall, cap) and the great joints that split its walls; the far mesas on the horizon. (No scan or elevation data of
  a butte may be shipped with its colours as a picture of Monument Valley: the forms are our own.)
- Brush beyond 400 m, where a bush is a few points of the picture: a lump of its colour.
- Wind ripples in loose sand near us, and the specks that stand for brush beyond a kilometre.

## Tools

| What | Licence |
|---|---|
| Blender 4.5 LTS, Cycles (renderer; nothing of it is shipped) | GPL |

## Looked at, never copied into the scene

To judge the render against real places (Monument Valley's Mittens and Merrick Butte), on Wikimedia Commons:
"West Mitten Butte - Monument Valley" (Luca Galuzzi, 2007, CC BY-SA 2.5); "West Mitten Butte, East Mitten Butte,
and Merrick Butte" (CC BY-SA); "View east-northeast from Lookout Point toward the Mittens in Monument Valley Navajo
Tribal Park" (2013); "Monument Valley Sunset MC".
