# Meadow (green hills and oaks): sources

The place is real: the Diablo Range of California above Sunol, seen from a hillside west of Calaveras Reservoir
(37.4788 N, 121.8357 W, 410 m above the sea, 186 m above the water) looking east-south-east (bearing 110 degrees)
across the reservoir to the ridges that climb towards Mount Hamilton. Nothing is added to the hills and nothing is
cut from them.

## The land (elevation model)

| Data | Source | Licence |
|---|---|---|
| Heights of the whole view, near ground to far ridges: USGS 3D Elevation Program (3DEP), 1/3 arc-second (about ten metres), tile `USGS_13_n38w122.tif` (file dated 27 August 2025) | U.S. Geological Survey, The National Map: `https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/13/TIFF/current/n38w122/USGS_13_n38w122.tif` | Public domain (a work of the U.S. Government). Credit as a courtesy: "U.S. Geological Survey, 3D Elevation Program" |

The tile (500 MB) is kept outside the repository in `%USERPROFILE%\ProjectMinhad\NoorTools\assets\usgs\`. It is
LZW-compressed; `meadow_dem.py` turns it into a plain array once (through Blender's image reader), and
`meadow_terrain.py` reads that. What the model does not hold is made up only where it must be: the reservoir's bed
(the model knows the water's surface; a shallow bed is hollowed under it so the phone's water has something to lie
over) and hummocks a few strides across near the camera (the model's posts are ten metres apart).

## Scans (photogrammetry of real plants, stones and ground)

All from Poly Haven (https://polyhaven.com), licence **CC0 1.0** (public domain dedication: may be used, changed and
shipped without attribution). Downloaded through its public API (`https://api.polyhaven.com/files/<id>`, the
`.blend` with its textures), kept outside the repository in `%USERPROFILE%\ProjectMinhad\NoorTools\assets\polyhaven\<id>\`.
None of these files is in the repository or in the app: only the pictures rendered from the scene are shipped.

| Asset id | What it is here |
|---|---|
| `grass_medium_01` | the grass near the camera: clumps (tall, mid, large, small) |
| `grass_medium_02` | more clumps of the same sward |
| `celandine_01` | yellow flowers |
| `flower_ursinia` | orange and yellow daisies |
| `periwinkle_plant` | violet flowers |
| `flower_heliophila` | small blue-violet flowers |
| `island_tree_02` | small gnarled trees on the open slopes |
| `rock_moss_set_01`, `rock_moss_set_02` | mossy stones in the grass near the camera |
| `boulder_01` | boulders on our slope |
| `grass_ground` (texture) | the soil and thatch between the clumps near the camera |
| `aerial_grass_rock` (texture) | the far grassland: its mottling, its relief in the shading, and the bare soil and rock on the steepest slopes (the green itself is set in the script) |
| `jolcham_oak_bark_01` (texture) | the bark of the oaks |

The scanned grass is dry grass; its hue is turned to spring green in `meadow.py` (`dress`). Plants that stand far
from the camera are enlarged, one standing for a patch.

Downloaded and looked at but not used: `jacaranda_tree` (a real tree, but layered like an acacia, not an oak),
`grass_bermuda_01`, `dandelion_01`, `shrub_02`, `leafy_grass`, `sparse_grass`.

## Made by the scripts in this folder (no scan exists, or none can)

| Part | Where it comes from | Licence |
|---|---|---|
| The oaks | `broadleaf.py`: Poly Haven has no broad round oak, so they are grown: branches reaching for places for leaves (space colonisation: Runions, Lane and Prusinkiewicz, "Modeling Trees with a Space Colonization Algorithm", 2007, implemented here from the paper's description), thickness by the pipe model. Their bark is the scan above. Where they stand is read from the land: the slopes that face north and the hollows where water gathers (`meadow.py`, `oak_density`; the water's lines by `hills.py`) | own work |
| Tall blades and seed stalks among the clumps, tufts on the far slope of our hill | `meadow.py` (`stalks`): single blades as geometry | own work |
| The greens of the far grassland (by hollow and crest), the drifts of flowers seen as colour, the dark woods on far north slopes | `meadow.py`: procedural, over the aerial scan's pattern | own work |

`hills.py` also holds the small landscape-evolution model that made an earlier, invented version of these hills; the
scenery no longer uses it for the land (only its helpers: sampling, the lines water follows).

Reference photographs of the Sunol and Diablo Range hills in spring were looked at on the web to judge the renders
by. None was copied, traced or used as a texture.
