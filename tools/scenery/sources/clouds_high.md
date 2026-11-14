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
