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
