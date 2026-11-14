# Sources: the Ocean scenery

Every solid thing in this scenery is a photogrammetry scan of real ground, all under **CC0 1.0** (public domain
dedication: free to use and redistribute, no attribution required). The scans are not stored in this repository:
only the pictures rendered from them are (`app/src/main/assets/sky2/land/ocean/`).

| Source | Id | What it is here |
|---|---|---|
| ambientCG (https://ambientcg.com) | `Ground054` | dry sand, uneven underfoot (colour, relief, roughness, normals) |
| ambientCG | `Ground055L` | sand rippled by the wind: most of the dry beach |
| ambientCG | `Ground061` | wet, tide-rippled sand: the beach's face, where the sea reaches |
| Poly Haven (https://polyhaven.com) | `sand_rocks_small_01` | a few sea-worn stones on the sand beside us |

- ambientCG: fetched as its 2K-JPG archive (`https://ambientcg.com/get?file=<id>_2K-JPG.zip`).
- Poly Haven: fetched through its public API (`https://api.polyhaven.com/files/<id>`, the 2k `.blend` and its
  textures).

What is this project's own work (`tools/scenery/ocean.py`): the beach's shape (a level top, a face that falls to the
sea's edge, a shelving bed) and where each scan lies on it. One change is made to the scans: their colour is brought
to the colour of beach sand (each scan's colours are multiplied so that its mean becomes a warm, pale gold), because
the owner found the scans' own grey-brown "not beach sand colour"; their grain, their light and dark and their relief
are the scans' own.

The sea is not a scan and not a picture: the phone draws it (the swell, the breakers and their foam, the sheet of
water that runs up the sand and back, the wet sand's shine, the mirrored sky), from the beach's shape, which
`ocean.py` writes into `land.json` (`shore`).
