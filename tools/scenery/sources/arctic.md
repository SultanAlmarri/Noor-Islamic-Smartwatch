# Arctic scenery: sources

Everything in `app/src/main/assets/sky2/land/arctic/` is a rendering made by `tools/scenery/arctic.py`. Nothing
below is shipped itself: the elevation data is only the shape the mountains were rendered from.

| What | Where from | Licence | Used for |
|---|---|---|---|
| **Kartverket, Nasjonal høydemodell (NHM), DTM** ("nhm_dtm_topo_25833", fetched at 5 m) | The Norwegian Mapping Authority's open elevation service, `https://wcs.geonorge.no/skwms1/wcs.hoyde-dtm-nhm-25833` (WCS; "fees: free, access constraints: none"); see hoydedata.no | Creative Commons Attribution 4.0 (CC BY 4.0), © Kartverket | The shape of the mountains within 15 km of the camera (Reinefjorden, Lofoten, Norway) |
| **Copernicus DEM GLO-30** (tiles N67E012, N67E013, N68E013; N68E014, N68E015, N69E016 and N69E017 were only searched) | `https://copernicus-dem-30m.s3.amazonaws.com/` (the public bucket of the Copernicus programme) | Free of charge for any use, redistribution included, with this notice: "© DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved" | Finding the views (`arctic_prep.py`), and the shape of the land farther than the fine model reaches |

Scans of real places, from **Poly Haven** (https://polyhaven.com, all CC0: public domain, no attribution required),
fetched through its API (`https://api.polyhaven.com/files/<id>`, the 2k .blend with its textures):

| Scan | Kind | Used for |
|---|---|---|
| `coast_land_rocks_02`, `coast_land_rocks_03` | model | the shelves of shore rock to either side of the water in front of us (true size) |
| `coast_rocks_05`, `boulder_01` | model | single boulders on the bank and in the shallows (0.5 to 1.1 of true size) |
| `sand_rocks_small_01` | model | the small stones at the water's edge (true size) |
| `coast_rocks_03` | model | the reefs running out on the left and the right (true size, 0.9) |
| `coast_rocks_01`, `coast_rocks_02` | model | the skerries far out (0.5 to 0.7 of true size) |
| `snow_02` | ground texture | the snow of the bank (colour, relief, normals) and of the caps on the rocks |
| `low_tide_rocks` | ground texture | the wet stones of the sea's edge and its bed (colour, relief, normals) |
| `dark_rock_02` | rock-face texture | the colour of the mountains' bare rock and half of the pattern in which snow catches on it (laid on at 27 to 45 times its size) |

Attribution to show wherever the app lists its sources:

- Contains data from Kartverket (the Norwegian Mapping Authority), Nasjonal høydemodell, CC BY 4.0.
- Contains modified Copernicus DEM data: © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018,
  provided under COPERNICUS by the European Union and ESA; all rights reserved.

Made here by formula, where no scan exists: the shape of the bank we stand on (a smooth rise under the scanned
snow); where snow lies on the mountains and the crags and ledges finer than the elevation models; the caps of snow
on the scanned rocks (grown from the scans' own surfaces); the changes that take the scanned rocks north and into
winter (greyed and darkened, wet at the water, a glaze of ice above it); the sea's bed far out.

The view is one real place, as the land has it: the shore at Reine (Moskenesøya, Lofoten; 67.949° N, 13.012° E),
looking north by east (bearing 16°) over Reinefjorden at the wall of peaks behind it. No mountain was moved, cut, lowered or
made taller. Two things were done to the elevation data: the Earth's curve was applied to far land, and the even
slopes of the fine model were smoothed over about fifteen metres, because that model is terraced where it was
drawn from contour lines (cliffs and ridges were left as they are). What stands on the real shore (the village,
its quays and bridges) is not in a terrain model and is not in the picture; the near ground, from the camera to
about 300 metres, is not Reine's but the scanned shore described above.
