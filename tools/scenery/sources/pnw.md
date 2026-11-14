# Pacific Northwest scenery (`pnw`): sources and licences

Everything in the scene is either made by `tools/scenery/pnw.py` itself (shapes by formula) or comes from the
sources below. Nothing else is used; the reference photographs that were looked at while making it are not in it.

## Real data

| What | Where from | Licence | Used for |
|---|---|---|---|
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
