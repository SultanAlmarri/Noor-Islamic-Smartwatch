"""Looks at a baked cloud set the way the phone will light it (roughly): a sheet of six moments.

    blender -b --factory-startup -P tools/scenery/skyview_high.py -- <set folder> <out.png>

Day with the Sun high on the right, a morning Sun low on the left, the Sun setting in the picture, the Sun just
under the horizon, deeper dusk, and the clouds' opacity alone. The colours are only stand-ins for the phone's sky.
"""
import json
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

src, out = sys.argv[sys.argv.index('--') + 1:][:2]
meta = json.load(open(os.path.join(src, 'sky.json')))


def load(name):
    img = bpy.data.images.load(os.path.join(src, name + '.webp'))
    img.colorspace_settings.name = 'Non-Color'
    w, h = img.size
    px = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(px)
    bpy.data.images.remove(img)
    return px.reshape(h, w, 4)[::-1, :, 0].copy()


def light(name):
    return load(name) ** 2 * meta['rMax']


_w = int(round(meta['viewWidth']))                 # the part of the picture the phone shows when nothing has drifted
_x0 = (meta['width'] - _w) // 2


def crop(p):
    return p[:, _x0:_x0 + _w]


_load = load
load = lambda name: crop(_load(name))               # noqa: E731
a, f, k, g = load('a'), load('f'), light('k'), light('g')
h, w = a.shape
row = (np.arange(h, dtype=np.float32) / meta['horizon'])[:, None, None]      # 0 top .. 1 horizon
t = np.clip(row, 0, 1) ** 2.2

MOMENTS = [  # sun picture, sun colour, sky above, horizon, light from the sky, light from the ground
    ('s_r6', (1.00, 0.98, 0.94), (0.16, 0.34, 0.70), (0.60, 0.74, 0.90), (0.22, 0.30, 0.45), (0.12, 0.10, 0.08)),
    ('s_l3', (1.00, 0.84, 0.62), (0.20, 0.36, 0.68), (0.80, 0.80, 0.78), (0.20, 0.26, 0.40), (0.08, 0.07, 0.05)),
    ('s_r1', (1.00, 0.52, 0.22), (0.20, 0.27, 0.52), (0.98, 0.62, 0.30), (0.12, 0.14, 0.24), (0.03, 0.02, 0.02)),
    ('s_ru0', (1.00, 0.36, 0.20), (0.09, 0.13, 0.31), (0.85, 0.45, 0.24), (0.06, 0.07, 0.13), (0.01, 0.01, 0.01)),
    ('s_ru1', (0.80, 0.22, 0.18), (0.05, 0.07, 0.19), (0.50, 0.26, 0.20), (0.03, 0.035, 0.07), (0.0, 0.0, 0.0)),
]
tiles = []
for name, sun, top, hor, amb, gnd in MOMENTS:
    s = light(name)
    bg = np.array(top, np.float32) * (1 - t) + np.array(hor, np.float32) * t
    cloud = s[..., None] * np.array(sun, np.float32) + k[..., None] * np.array(amb, np.float32) + g[..., None] * np.array(gnd, np.float32)
    haze = np.clip(f * 0.75, 0, 1)[..., None]                    # far cloud takes the colour of the air before it
    cloud = cloud * (1 - haze) + np.array(hor, np.float32) * haze * a[..., None]
    tiles.append(bg * (1 - a[..., None]) + cloud)
tiles.append(np.repeat(a[..., None], 3, -1) ** 2.2)
rows = [np.concatenate(tiles[i:i + 3], axis=1) for i in (0, 3)]
sheet = np.concatenate(rows, axis=0)
sheet = sheet[::2, ::2] if sheet.shape[1] > 2400 else sheet
bake.save8(out, np.clip(sheet, 0, 1) ** (1 / 2.2))
print('saved', out, flush=True)
