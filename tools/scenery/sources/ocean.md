# Sources: the Ocean scenery

The sand of this scenery is photogrammetry of real ground, all under **CC0 1.0** (public domain dedication: free to
use and redistribute, no attribution required); its far mountains are a real elevation model (below). Neither the
scans nor the model are stored in this repository:
only the pictures rendered from them are (`app/src/main/assets/sky2/land/ocean/`).

| Source | Id | What it is here |
|---|---|---|
| ambientCG (https://ambientcg.com) | `Ground054` | dry sand, uneven underfoot (colour, relief, roughness, normals) |
| ambientCG | `Ground055L` | sand rippled by the wind: most of the dry beach |
| ambientCG | `Ground061` | wet, tide-rippled sand: the beach's face, where the sea reaches |

- ambientCG: fetched as its 2K-JPG archive (`https://ambientcg.com/get?file=<id>_2K-JPG.zip`).

The mountains far across the water are the real ones: the coast of Ras Al Khaimah looking north-east towards the
Hajar and Musandam, where they fall into the sea at the Strait of Hormuz, from the **Copernicus DEM GLO-30** (tiles
N25E055, N25E056, N26E056; `https://copernicus-dem-30m.s3.amazonaws.com/`, the public bucket of the Copernicus
programme). Free of charge for any use, redistribution included, with this notice:

- Contains modified Copernicus DEM data: © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018,
  provided under COPERNICUS by the European Union and ESA; all rights reserved.

The model is a surface model (it includes whatever stands on the ground). The low coastal plain in front of the
mountains is pressed nearly flat in `ocean.py`, so nothing built on it is in the picture: nature only.

What is this project's own work (`tools/scenery/ocean.py`): the far mountains' colour (bare limestone; their
shapes are the model's), the beach's shape (a level top, a face that falls to the
sea's edge, a shelving bed) and where each scan lies on it. One change is made to the scans: their colour is brought
to the colour of beach sand (each scan's colours are multiplied so that its mean becomes a warm, pale gold), because
the owner found the scans' own grey-brown "not beach sand colour"; their grain, their light and dark and their relief
are the scans' own.

The sea is not a scan and not a picture: the phone draws it (the swell, the breakers and their foam, the sheet of
water that runs up the sand and back, the wet sand's shine, the mirrored sky), from the beach's shape, which
`ocean.py` writes into `land.json` (`shore`).
