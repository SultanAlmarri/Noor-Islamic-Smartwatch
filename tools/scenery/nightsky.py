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
