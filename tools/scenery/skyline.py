"""(0.18) Writes `skyline` (the land's whole skyline, bake.skyline_profile) into the land.json of sceneries packed
before the packer wrote it itself. No Blender needed: it reads the land's own picture (a.webp) with Pillow.

    python3 tools/scenery/skyline.py app/src/main/assets/sky2/land/*

The app draws the Sun's path behind the land with it (RealSky.skyline, LivingSky): the path passes behind a
mountain or a tree as the Sun does, and never runs down over the land to the level horizon.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

BINS = 360


def profile(alpha, top, horizon, focal_px, rows, bins=BINS):
    h, w = alpha.shape
    solid = alpha >= 0.5
    first = np.where(solid.any(axis=0), solid.argmax(axis=0), h)
    out = []
    for i in range(bins):
        a, b = int(i * w / bins), max(int((i + 1) * w / bins), int(i * w / bins) + 1)
        y = int(first[a:b].min())
        t = -0.05 if y >= h else (horizon - (top + y * rows / h)) / focal_px
        out.append(round(float(min(max(t, -0.05), 0.8)), 4))
    return out


for d in sys.argv[1:]:
    path = os.path.join(d, 'land.json')
    if not os.path.exists(path):
        continue
    meta = json.load(open(path))
    alpha = np.asarray(Image.open(os.path.join(d, 'a.webp')).convert('RGBA'))[..., 3] / 255.0
    meta['skyline'] = profile(alpha, meta['top'], meta['horizon'], meta.get('focal', 1.15) * meta['width'], meta['height'])
    json.dump(meta, open(path, 'w'), indent=1)
    print('skyline of %s: highest %.3f, at the middle %.3f' % (d, max(meta['skyline']), meta['skyline'][BINS // 2]), flush=True)
