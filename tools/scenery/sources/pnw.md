# Pacific Northwest scenery (`pnw`): sources and licences

Everything in the scene is either made by `tools/scenery/pnw.py` itself (shapes by formula) or comes from the
sources below. Nothing else is used; the reference photographs that were looked at while making it are not in it.

## Real data

| What | Where from | Licence | Used for |
|---|---|---|---|
| Elevation of the Stuart Range and the Enchantments (0.14): USGS 3D Elevation Program (3DEP), 1 arc-second, tiles `USGS_1_n48w121.tif` and `USGS_1_n48w122.tif`, cut by `tools/scenery/pnw_dem.py` | U.S. Geological Survey, The National Map: `https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/1/TIFF/current/<tile>/USGS_1_<tile>.tif`, fetched 16 November 2026 | Public domain (U.S. Government work) | The chain of mountains (since 0.14) |
| Elevation of Mount Rainier and its surroundings: USGS 3D Elevation Program (3DEP), 1 arc-second, tile `USGS_1_n47w122.tif` (published 13 August 2025) | U.S. Geological Survey, The National Map: `https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/1/TIFF/current/n47w122/USGS_1_n47w122.tif` | Public domain (work of the U.S. Government). Credit: U.S. Geological Survey. | The shape of the mountain: the tile's heights are turned to our view (from the north-west) and drawn 1.15 times taller; nothing else is done to its shape. Snow, rock, meadow and forest are laid on it by height and steepness in the script. |

## Scans from Poly Haven (all CC0 1.0, public domain dedication)

Fetched with `NoorTools/assets/polyhaven/get.py <id> 2k` (the fir at 1k) from `https://api.polyhaven.com/files/<id>`;
pages at `https://polyhaven.com/a/<id>`.

| Id | Kind | Used for |
|---|---|---|
| `dry_river_pebbles` | ground texture with its height map | the beach under our feet and the bed of the shallows |
| `aerial_rocks_02` | aerial ground scan (50 m) with its height map | the rock and moss of the points', the islet's and the far shore's ground, with its relief |
| `forrest_ground_03` | ground texture (needle litter) | the floor of the woods on them (colour only) |
| `rock_moss_set_01` | six mossy boulders | boulders in the shallows before us, and along every shore |
| `rock_moss_set_02`, `rock_07`, `rock_09` | rocks and stones | loose stones on the beach |
| `dead_tree_trunk`, `dead_tree_trunk_02` | fallen trunks | the driftwood logs on the beach and along the far shores |
| `root_cluster_01` | a root wad | on the beach, to our left |
| `grass_medium_01` | clumps of grass | the sedge among the beach's stones (drawn two to three times its scanned size) |
| `fern_02` | ferns | by the old wood on the beach |
| `fir_tree_01` (Rob Tuytel, Rico Cilliers) | only its twig sprays (`fir_tree_01_twig_main_a/b/c`, levels of detail 1 and 2) and their material with its photographed textures | the foliage of every tree and bush |

Downloaded as well but not used in the end: `ganges_river_pebbles`, `brown_mud_rocks_01`, `coast_land_rocks_01`,
`lichen_rock`, `boulder_01`, `tree_stump_01`, `dry_branches_medium_01`.

## Made by the script, with no outside data

- The trees' trunks and branching (a conifer generator: Douglas fir, hemlock, spruce, cedar, young trees, snags),
  clothed with the scanned twig sprays; the shore bushes' stems.
- The outlines and heights of the two points, the islet and the far shore; the wooded ridges behind the far shore
  (shape and colour: no scan of forest canopy exists; they stand two kilometres off and more); the bed of the
  deep water.
- Where things stand: every placement of a tree, rock, log or clump.
- The colours laid on the mountain by height and steepness (snow, rock, meadow, forest).

The downloads are kept outside the repository (`%USERPROFILE%\ProjectMinhad\NoorTools\assets\usgs` and
`...\assets\polyhaven`). What is shipped in the app is only the baked pictures in
`app/src/main/assets/sky2/land/pnw/`, which show these things as rendered.


## The chain of mountains (0.14)

The owner, of 0.13's single cone of Mount Rainier: "I want the mountain to be like a chain of mountains to look
more beautiful." The chain is a real one, and so is the view of it: the elevation model of the North Cascades
(1 arc-second, the four tiles n48w121, n48w122, n49w121, n49w122) was searched for low ground from which the real
skyline across the view (+-24 degrees, 11 to 60 km out) holds a chain: several snow peaks of unequal height across
most of its width, the highest off the middle, nothing over 11.5 degrees where the Sun crosses at midday, most of
it more than 14 km off (it has to stand behind the lake's own wooded ridges). The best was the Stuart Range seen
from the Teanaway, south of it (47.340 N, 120.820 W, looking 346 degrees): Mount Stuart a little left of the
middle, 16 km off, and the Enchantments' peaks running to the right. The search and its candidates were drawn and
looked at; the scripts that did it were working files and are not kept.

What is the scenery's own (pnw.py): the heights are drawn 1.3 times taller than they are (Rainier's were 1.15);
the snow line is set for early summer (about 2000 m, as the range is in June); the rock is pale granodiorite; the
land's near edge, at 12 km, sinks under the lake's ridges, which stand in front of it.

**How it was made without the owner's PC.** The whole scene (32 000 trees) is too heavy to bake again on a cloud
machine. Only what lies beyond the lake's ridges changed, so the chain was baked alone under the same Suns
(`NOOR_PNW_ONLY=mountain ... -- bake`) and laid behind the pictures of 0.13 by `tools/scenery/pnw_chain.py`:
everything nearer than 11.5 km in them is kept as it was and laid over the chain by its own coverage (colours in
linear light, the light pictures relative to each pixel's own colour, as the packer makes them), so the trees'
edges stand against the new mountains. The lake's ridges are those of 0.13, unchanged.

