"""The Milky Way for the night sky, from NASA's Deep Star Maps 2020 (Scientific Visualization Studio, svs.gsfc.nasa.gov/4851;
public domain): the map without its stars, in celestial coordinates, made small enough to ship.

    blender -b --factory-startup -P tools/scenery/nightsky.py -- make <folder with milkyway_2020_4k.exr> <out.webp>

The picture is 2048 x 1024, plate carree: right ascension across (see `milkyWay` in scene.frag for which way), declination
+90 at the top. Light is stored as its 2.2 root, against the brightness printed below (the shader undoes both).
"""
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

mode, src, rest = bake.args()
out = rest[0]

# The part of the sky the finest night shows: 90 degrees square round the galactic centre, cut from the star map
# (stars and all) at about the screen's own sharpness.
#     blender -b --factory-startup -P tools/scenery/nightsky.py -- hi <folder with starmap_2020_8k.exr> <out.webp>
# HI_U0, HI_V0, HI_DU, HI_DV say where the cut lies in the whole map (as fractions of it); scene.frag has the same.
HI_U0, HI_V0, HI_DU, HI_DV = 0.635, 0.411, 0.250, 0.500
if mode == 'hi':
    def cut_of(name):
        img = bake.read_exr(os.path.join(src, name))[..., :3]
        h, w = img.shape[:2]
        cut = img[int(HI_V0 * h):int((HI_V0 + HI_DV) * h), int(HI_U0 * w):int((HI_U0 + HI_DU) * w)]
        while cut.shape[1] > 2048:
            ch, cw = cut.shape[:2]
            cut = cut[:ch // 2 * 2, :cw // 2 * 2].reshape(ch // 2, 2, cw // 2, 2, 3).mean(axis=(1, 3))
        return cut

    # The Milky Way's glow, sharp (the same map as the whole-sky picture, against the same reference brightness),
    # and on it the stars of the star map, which holds every star and the glow at its true, fainter brightness.
    REF_GLOW = 0.3988              # (what milkyway.webp is stored against: printed by the other mode)
    glow = cut_of('milkyway_2020_8k.exr') / REF_GLOW
    stars = cut_of('starmap_2020_8k.exr')
    lum = bake.luma(stars)
    k = 16
    blocks = lum[:lum.shape[0] // k * k, :lum.shape[1] // k * k].reshape(lum.shape[0] // k, k, lum.shape[1] // k, k)
    faint = float(np.median(blocks, axis=(1, 3)).max())          # the brightest of the star map's own glow (not a star)
    ref = 6.0 * faint                                            # stars up to six times that keep their brightness
    print('glow max', float(bake.luma(glow).max()), 'star map glow', faint, 'reference', ref, 'stars clipped', float((lum > ref).mean()), flush=True)
    # Stored: 0.6 of the glow (in the whole-sky picture's own units) and the stars over it; scene.frag knows the 0.6.
    enc = np.clip(0.6 * np.clip(glow, 0.0, 1.2) + 0.9 * np.clip(stars / ref, 0.0, 1.0), 0.0, 1.0) ** (1 / 2.2)
    bake.save8(out, enc, quality=int(os.environ.get('NOOR_QUALITY', '88')))
    print('wrote', out, os.path.getsize(out), flush=True)
    sys.exit(0)
img = bake.read_exr(os.path.join(src, 'milkyway_2020_4k.exr'))[..., :3]
h, w = img.shape[:2]
# Halve it (the mean of each block of four), twice if needed, to 2048 across.
while w > 2048:
    img = img[:h // 2 * 2, :w // 2 * 2].reshape(h // 2, 2, w // 2, 2, 3).mean(axis=(1, 3))
    h, w = img.shape[:2]
lum = bake.luma(img)
ref = float(np.percentile(lum, 99.9))
# Where the brightest part is (the galactic centre: right ascension 266.4 degrees, declination -28.9): says which
# way the map runs.
k = 32
blocks = lum[:h // k * k, :w // k * k].reshape(h // k, k, w // k, k).mean(axis=(1, 3))
by, bx = np.unravel_index(np.argmax(blocks), blocks.shape)
print('size', w, h, 'reference', ref, 'brightest at x %.3f y %.3f of the picture' % ((bx + 0.5) * k / w, (by + 0.5) * k / h), flush=True)
enc = np.clip(img / ref, 0.0, 1.0) ** (1 / 2.2)
bake.save8(out, enc, quality=80)
print('wrote', out, os.path.getsize(out), flush=True)
