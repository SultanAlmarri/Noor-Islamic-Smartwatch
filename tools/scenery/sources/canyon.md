# Red Rock (`canyon`): sources

The scenery is **Cathedral Rock over Oak Creek, from the slabs just below Red Rock Crossing** (Sedona, Arizona),
made by `tools/scenery/canyon.py` (plants: `canyon_plants.py`; ground: `canyon_terrain.py`, `canyon_dem.py`). We
stand on a slab of red sandstone at the edge of a long pool of the creek (34.8254 N, 111.8086 W; the water at
1 205.1 m) and look east-south-east (113 degrees) up the creek's valley: the pool, a bar of slickrock and cobbles
across it, the creek's wood, and over it the red skirts and the two domes and the spires of Cathedral Rock, 1.4 km
off, its top 300 m above the water (11 degrees up).

Only pictures rendered from all this are shipped (`app/src/main/assets/sky2/land/canyon/`); no elevation file and no
scan is in the repository or in the app. The downloads live outside the repository (`NoorTools/assets/usgs`,
`NoorTools/assets/polyhaven`).

## The land's shape (USGS, public domain)

| What | Where from | Licence |
|---|---|---|
| Heights of the land at one metre: USGS 3D Elevation Program, lidar-derived 1 m model, project `AZ_NorthCentral_B23`, tile `USGS_1M_12_x42y386_AZ_NorthCentral_B23.tif` (UTM zone 12 north, NAD83; file dated 1 June 2026) | `https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/1m/Projects/AZ_NorthCentral_B23/TIFF/USGS_1M_12_x42y386_AZ_NorthCentral_B23.tif` | public domain (U.S. Geological Survey) |
| Heights of the land beyond that tile (more than 4 km off): 3D Elevation Program, 1/3 arc-second (ten metres), tile `USGS_13_n35w112.tif` | `https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/13/TIFF/current/n35w112/USGS_13_n35w112.tif` | public domain (U.S. Geological Survey) |

`canyon_dem.py` cuts the two pieces out of the tiles; `canyon_terrain.py` reads them. **The heights are used as
they are**: Cathedral Rock, its skirts, the hills and the creek's banks are the lidar's, a point for every pixel of
the picture across and every metre and a quarter in depth. No rock is moved, added, removed, raised or lowered, and
nothing is cut open for the Sun. Three things are done to the heights, all of them at the creek:

- The lidar gives the water's surface, not what lies under it. Where the model lies at the pool's level a bed is
  hollowed out by formula (deeper away from the shore, to 0.8 m), because the phone draws the water over a bed.
- The water's edge is smoothed over about a metre, so that it does not follow the model's one-metre squares where
  it passes our feet.
- Between the model's points near us (within 140 m) the ground is read smoothly (Catmull-Rom) and carries the
  relief of the scanned rock below (a few centimetres), instead of flat facets a metre across.

The bare-earth model also holds what people have graded: a road's bench at the very right edge of the view (120 m
off and more, almost all of it outside the picture), a straight ditch or track along the flood plain's edge, and
the edge of a field at the far left (600 m off). None is more than a metre or two of relief; in the pictures they
lie under the wood and the brush and do not show (looked for in the full-size pictures; none seen). They were not
taken out of the heights. No building, road surface, fence or other made thing is modelled: the lidar's bare
earth has none, and nothing of the kind was added.

## Scans used (Poly Haven, CC0)

Photogrammetry of real rock, ground, bark and plants from **Poly Haven** (https://polyhaven.com), all of it **CC0**
(public domain dedication: free to use and to ship, no attribution required; recorded here all the same). Fetched
with `python3 tools/scenery/polyhaven_get.py 2k @canyon` from Poly Haven's public API
(`https://api.polyhaven.com/files/<id>`; pages at `https://polyhaven.com/a/<id>`).

| Id | By | Kind, resolution | Used for |
|---|---|---|---|
| `cliff_side` | James Ray Cock, Jenelle van Heerden, Dario Barresi | surface (colour, relief), 1k | the thin beds of the walls and ledges: their light and dark, and their relief in the shading |
| `rock_face` | Greg Zaal, Dario Barresi | surface (colour), 1k | weathered faces of the walls (mixed with the above, laid on at many times its true size) |
| `red_laterite_soil_stones` | Amal Kumar | surface (colour), 1k | the red soil and rubble of the slopes |
| `red_mud_stones` | Rob Tuytel | surface (colour), 1k | the same, its finer mottling |
| `sandstone_cracks` | Rob Tuytel | surface (colour, relief), 2k | the slickrock we stand on and along the pool, with its relief (real displacement) |
| `dry_riverbed_rock` | Amal Kumar | surface (colour), 2k | the bedded edges of the slabs |
| `river_small_rocks` | Rob Tuytel, Rico Cilliers | surface (colour, relief), 2k | patches of cobbles on the bars |
| `red_dirt_mud_01` | Rob Tuytel | surface (colour), 1k | the flood plain's sand |
| `forest_leaves_02` | Rob Tuytel | surface (colour), 1k | leaf litter under the creek's trees (tinted by the season) |
| `bark_willow_02` | Charlotte Baglioni | surface (colour, relief), 1k | the bark of the cottonwoods, ash, willows, junipers and brush |
| `bark_platanus` | Dimitrios Savva | surface (colour, relief), 1k | the sycamores' bark |
| `namaqualand_boulder_02`, `namaqualand_boulder_05` | Greg Zaal, Rico Cilliers; Jenelle van Heerden, Dario Barresi | models, 1k | blocks of rock in and by the pool (tinted to the red of the rock here) |
| `namaqualand_boulders_01` | Greg Zaal, Jenelle van Heerden | models, 1k | stones on the slab and the bars |
| `grass_medium_01` | Rob Tuytel, Rico Cilliers | models, 1k | tufts of grass and sedge on the banks (their colour turned with the season) |
| `dead_tree_trunk_02` | Jenelle van Heerden, Rico Cilliers | model, 1k | driftwood: three trunks the floods have left |

The scans' colours are tinted to the red of the Schnebly Hill sandstone (their own light and dark and their own
detail are kept: a scan's colour is divided by its own mean and laid on a colour chosen against photographs of the
place). Fetched as well and not used in the end: `worn_rock_natural_01`, `rock_wall_02`, `rocky_terrain_02`,
`searsia_burchellii`, `searsia_lucida`, `wild_rooibos_bush`, `grass_medium_02`, `dry_branches_medium_01`,
`root_cluster_02`.

## Made by this project's scripts, with no outside data

- **Every tree and bush.** No scan of a cottonwood, a sycamore or a juniper exists that may be shipped, so they are
  grown branch by branch (`broadleaf.py`, space colonisation) to the measure of the real ones: Fremont cottonwood,
  Arizona sycamore, velvet ash, Goodding's willow, thickets of coyote willow, one-seed juniper and pinyon, and the
  brush of the dry slopes. Their leaves are plain small shapes coloured by the script; only their bark is scanned.
- **Where things stand.** The lidar used here is bare earth: it does not say where a tree stands. The wood is laid
  on the creek's flood plain (ground within a few metres above the creek), willows on its bars and at the water's
  edge, junipers and brush on dry ground that is not steep: by the land's own shape, in groves. Eight trees near
  us, the stones and the driftwood are set by hand. Under Cathedral Rock's domes, and where the Sun rises and sets,
  trees are kept low enough (by making them smaller or leaving them out) that the wood hides the skirts' foot, as
  it does there, and not the rock, and that the Sun's day is not shortened by a tree.
- **The rock's colours**: which bed is which red, the paler tops of the domes, the dark joints and the streaks of
  varnish down the walls are formulas keyed to the real heights and the real steepness (no colour photograph or
  orthoimage of the place is used). The joints are also shaded as relief. The shapes they sit on are the lidar's.
- **The seasons** (what the colour pictures of autumn, winter and spring show): the leaves' colours and whether
  they are there, the grass's colour, the dusting of snow on what is level enough to hold it.
- **The pool's bed** (see above).

## Tools

| What | Licence |
|---|---|
| Blender 4.5 LTS, Cycles (renderer; nothing of it is shipped) | GPL |
| `tifffile`, `imagecodecs` (reading the USGS tiles; nothing of them is shipped) | BSD |

## Looked at, never copied into the scene

To judge the render against the real place (Wikimedia Commons; kept outside the repository in
`NoorTools/work/canyon/ref`, never committed):

- "Cathedral Rock Water-27527-1.jpg" (Ken Thomas, 19 July 2010, public domain): the rock over the creek in summer.
- "Cathedral Rock at Red Rock Crossing.jpg" (Flickr, 27 September 2009, CC BY 2.0): the slabs and the pool.
- "Cathedral Rock from Red Rock Crossing.jpg" (Flickr, 8 April 2011, CC BY 2.0): early spring, bare cottonwoods.
- "Fall color at Crescent Moon Ranch (November 15, 2017)" (Coconino National Forest, public domain): autumn.
- "Cathedral Rock (25270326947).jpg" (Coconino National Forest, 7 February 2018, public domain): the domes' joints.
- "Slide Rock State Park in snow.jpg" (Flickr, CC BY 2.0): snow on red rock in Oak Creek Canyon.
