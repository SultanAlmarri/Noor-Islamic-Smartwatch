# Sources: the Ocean scenery

Every solid thing in this scenery is a photogrammetry scan from Poly Haven (https://polyhaven.com), all under
**CC0 1.0** (public domain dedication: free to use and redistribute, no attribution required). They were fetched
through Poly Haven's public API (`https://api.polyhaven.com/files/<id>`, the 2k `.blend` and its textures) and are not
stored in this repository: only the pictures rendered from them are (`app/src/main/assets/sky2/land/ocean/`).

| Poly Haven id | What it is here |
|---|---|
| `damp_beach_sand_02` (texture) | the damp sand of the tidal flat |
| `sand_rocks_small_01` | sand with small stones, underfoot |
| `coast_land_rocks_02`, `coast_land_rocks_03` | shelves of rock on the flat |
| `coast_rocks_03`, `coast_rocks_05` | rocks in the first shallows; scaled up, the far islets |
| `coast_rocks_01`, `coast_rocks_02` | the reef further out |
| `coastal_cliff_01`, `coastal_cliff_02`, `coastal_cliff_04` | the headlands at the picture's edges; scaled up, a far islet |

The arrangement, the tidal flat's shape and everything else in `tools/scenery/ocean.py` is this project's own work.
The sea itself is drawn by the phone.
