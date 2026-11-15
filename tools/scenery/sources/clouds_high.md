# Sources: the middle and high cloud sets

Sets: `ac_flock`, `ac_rolls`, `as_veil` (middle layer), `ci_wisps`, `cc_ripples`, `cs_veil` (high layer), in
`app/src/main/assets/sky2/clouds/<id>/`. What is shipped is only the baked pictures (WebP) and `sky.json`.

## The cloudlets of the cellular sets: modelled clouds by CGHeven

The cloudlets of `ac_flock` (and of any other set listed in `PUFFSETS` in `tools/scenery/sky_high.py`) are small
copies of modelled cumulus clouds, set out by the thousand, turned, mirrored and flattened.

- What: eight cumulus cloud volumes (OpenVDB, one float grid `density`): "Hero Cloud 01" versions 1, 11, 20, 32,
  33, 35, 39 and 43.
- From: CGHeven, https://cgheven.com (free VDB clouds). Author: Ammar Khan / CGHEVEN.
- Licence: https://cgheven.com/licensing — free to use, also commercially, in finished work including renders;
  **the files themselves may not be redistributed.** So no `.vdb` is in this repository or in the app: they stay
  in `%USERPROFILE%\ProjectMinhad\NoorTools\assets\cgheven\` on the owner's PC (the reduced copies the bake uses in
  `...\NoorTools\work\clouds_high\puffs\`), and only pictures rendered from them are shipped.
- How they are placed (the ranks, the fields of cloud and open sky, the far edge breaking up) is this project's
  own work: `cloudgen_high.rank_points`, `sky_high.build_puffs`.

## Everything else: this project's own procedural work

The veils (`as_veil`, `cs_veil`), the cirrus (`ci_wisps`) and any cellular set not in `PUFFSETS` use no outside
asset at all.

- The patterns (where the cloud is, how thick, how dense) are computed by `tools/scenery/cloudgen_high.py` and
  `tools/scenery/sky_high.py` from hashed noise, cells and fibres drawn as curves, with numpy.
- They are written as OpenVDB volumes with the `openvdb` module bundled with Blender 4.5 LTS (Blender is GPL; its
  output is not covered by its licence) and path traced with Blender Cycles by `tools/scenery/skybake.py`.

Photographs of real skies (Wikimedia Commons categories for altocumulus and cirrus) were looked at to judge the
renders; none was copied, traced or used as data.

## To make a set again

```
set NOOR_SET=ac_flock
blender -b --factory-startup -P tools/scenery/sky_high.py -- bake <work dir>
```

then copy `<work dir>/sky/*` into `app/src/main/assets/sky2/clouds/<id>/`. (`NOOR_SCALE=0.75` was used for the
bakes; `python tools/scenery/sky_high.py -- plan <work dir>` shows a procedural pattern in seconds, without path
tracing; `tools/scenery/skyview_high.py` shows a baked set roughly as the phone will light it.)

## The skies of 0.12 (`mi_*`, `hi_*`): composed skies in the wide format

Sets: `mi_patches`, `mi_bands`, `mi_sheet`, `mi_veil` (middle layer) and `hi_wisps`, `hi_sprays`, `hi_veil`,
`hi_ripples`, `hi_mixed` (high layer), made by `tools/scenery/sky_mh.py`. They use the same two kinds of material
as the sets above and nothing else:

- **Cells**: the same eight CGHEVEN cumulus volumes (same author, licence and rule: free to use in finished work
  including renders, also commercially; the files may not be redistributed; no `.vdb` in the repository; the
  reduced copies are in `...\NoorTools\work\clouds_mh\puffs\`). Used, made small, as the cells of the mats of
  `mi_patches` (five mats) and `mi_bands` (bands of rolls and flocks) and as the grains of cirrocumulus in
  `hi_ripples` and `hi_mixed`.
- **Everything else is this project's own procedural work** (`cloudgen_high.py`, `sky_mh.py`): where the mats,
  bands, holes and edges lie (`Mat`); the thin cloud the cells stand in; the whole of `mi_sheet` (a deck of pads
  grown together, made as one layer: no modelled cells) and of `mi_veil` (altostratus); all the cirrus and
  cirrostratus (`hi_wisps`, `hi_sprays`, `hi_veil`, and the streaks and veils of `hi_ripples` and `hi_mixed`).

Photographs looked at to compose and judge them (none copied, traced or used as data): Wikimedia Commons, fetched
through its API as thumbnails: altocumulus stratiformis, floccus and undulatus, altostratus, cirrus uncinus and
fibratus, cirrostratus, cirrocumulus stratiformis, and the featured sky pictures in
`...\NoorTools\work\refs2` and `...\NoorTools\work\refs_mh`.

To make one again (the wide sky: 94 degrees across, see `skybake.WIDE`):

```
set NOOR_SET=mi_patches
python tools/scenery/sky_mh.py -- plan <work dir>          (where its clouds stand, in seconds; NOOR_SEED tries another sky)
blender -b --factory-startup -P tools/scenery/sky_mh.py -- bake <work dir>      (NOOR_SCALE=0.75 was used)
blender -b --factory-startup -P tools/scenery/skyview_mh.py -- <work dir>/sky <sheet.jpg>
```

then copy `<work dir>/sky/*` into `app/src/main/assets/sky2/clouds/<id>/`.
