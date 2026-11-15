# Low cloud sets: sources

Sets: `cu_fair`, `cu_field`, `cu_towers`, `cb_storm`, `sc_sheet`, `st_overcast`
(`app/src/main/assets/sky2/clouds/<id>/`). What is shipped is only the baked pictures of each set (`a.webp`,
`f.webp`, `k.webp`, `g.webp`, `s_*.webp`, `sky.json`): renders made by us with Blender Cycles.

## Heaped clouds: CGHEVEN cloud volumes (used for rendering, not shipped)

The cumulus and towering cumulus in these sets are rendered from volumetric cloud models (OpenVDB) by
**CGHEVEN (Ammar Khan)**, https://cgheven.com.

- Asset pages: `https://cgheven.com/assets/hero-cumulus-cloud-vdb-<NN>` (01, 11, 20, 32, 33, 35, 39, 43) and
  `https://cgheven.com/assets/hero-congestus-cloud-vdb-<NN>` (20, 21, 23, 27, 47, 50).
- Licence (https://cgheven.com/licensing, read 14 November 2026): free to use, also commercially, in finished work
  such as renders and games; **the asset files themselves may not be redistributed**.
- So: the `.vdb` files are kept outside this repository (`%USERPROFILE%\ProjectMinhad\NoorTools\assets\cgheven\`, with
  coarser working copies in `...\NoorTools\work\clouds_low\cg\`) and must never be committed, uploaded or passed on.
  Only pictures rendered from them are in the app. Credit: "Cloud volumes by CGHEVEN (Ammar Khan)".
- Which set uses which: `cu_fair` the eight cumulus; `cu_field` the eight cumulus and the six congestus; `cu_towers`
  the six congestus and the eight cumulus; `cb_storm` congestus 27, 23, 21 and 47 grown together as the storm's
  towers, the six congestus round its foot and the eight cumulus in the rest of the sky (its anvil is our own, see
  below). `sc_sheet` and `st_overcast` are made by `tools/scenery/sky_low2.py`: see what that script uses.
- The volumes are used cut off flat at each cloud's base (the originals trail wisps under it) and, far away, at
  half and a quarter of their fineness: `tools/scenery/cloudprep.py` writes those working copies, outside the
  repository too.

## Our own work

- How each sky is set out (where every cloud stands, its size, turn and mirroring; the Sun's road kept open), the
  thunderhead's anvil and the overcast layer (sheets from maps of their base and top): `tools/scenery/sky_low.py`, `tools/scenery/cloudgen.py`, random numbers
  from fixed seeds.
- `sc_sheet` and `st_overcast` are made by `tools/scenery/sky_low2.py`. Ours in them: the sheet the stratocumulus
  cells are grown together in (maps of pads and cracks) and the whole overcast layer (maps of its underside and its
  depth), from random patterns with fixed seeds; and one change to what `skybake.py` packs: the picture of the light
  thrown up by the ground (`g.webp`) is darkened by the share of light that gets through the layer to that ground
  (`groundShade` in `sky.json`), since the ground under a sheet or an overcast does not lie in the open.
- `tools/scenery/cloudprep.py` measures the CGHEVEN volumes and writes the coarser working copies.
- Rendering: Blender 4.5 LTS, Cycles, through `tools/scenery/skybake.py`.

No photograph is used or shipped. Reference photographs of real skies were only looked at.

## The low skies of 0.12 (`lo_*`, `tools/scenery/sky_lo.py`)

The same CGHEVEN volumes, under the same terms (only pictures rendered from them are shipped; the files stay
outside the repository), set out as eight composed skies in the wide format of `skybake.py`:

| Set | CGHEVEN clouds used | Ours |
|---|---|---|
| `lo_few` | cumulus 32, 35, 39, 43 (the shallow ones), a few of 01 and 11 | where they stand (groups, a far line) |
| `lo_scattered` | the eight cumulus | the structure (fields, empty quarters, streets), two large clouds by hand |
| `lo_towers` | the six congestus (the group of towers), the eight cumulus | the same |
| `lo_broken` | the eight cumulus, the six congestus (a few, far off) | the rafts and holes; the sheet that closes the rafts beyond 12 km (maps) |
| `lo_front` | the eight cumulus, the six congestus (along the bank's edge) | the bank and its edge; its sheet beyond 12 km (maps) |
| `lo_storm` | congestus 27, 23, 21, 47 grown together (the storm's towers), the six congestus (flanking line and the turrets on its near side), the eight cumulus, cumulus 32, 35, 39, 43 flattened (scud) | the anvil (a volume from maps: domed, swelling, fibrous, pouched beneath), the dark shelf of the storm's base (a volume from maps) |
| `lo_overcast_breaks` | the eight cumulus, flattened (the deck's cells) | the thinner sheet over the cells that closes the cracks (maps); the breaks; where the deck ends over the horizon |
| `lo_overcast` | cumulus 32, 35, 39, 43, flattened and thinned (scud) | the whole layer (sky_low2's maps); its own pattern of light and dark laid on its lit pictures again when it is packed (`structure` in `sky.json`): in the bake a thin place is only a sixth brighter than a thick one, and on the phone that was one even grey |

Under `lo_broken`, `lo_overcast_breaks` and `lo_overcast` the ground's light (`g.webp`) is darkened as for
`sc_sheet` (`groundShade` in `sky.json`: the share of light that reaches the ground through the set). The other
five are packed with the ground's light at a fixed share too (0.85 for `lo_few` down to 0.45 for `lo_storm`): the
ground under a cloud lies in that cloud's shadow, the more of it the larger the cloud, and the bake lights every
base by ground in the open. In `lo_towers`, `lo_storm`, `lo_front` and `lo_broken` the towering clouds are set out
nearly as dense as the small ones (0.85; `sky_low.py` set them out at half), so their shaded sides are grey. Reference photographs (Wikimedia Commons, fetched through its API) were
only looked at.
