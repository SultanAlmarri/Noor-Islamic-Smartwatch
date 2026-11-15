"""Looks at a baked wide sky (skybake's equirectangular window, 94 degrees across) the way the phone will show it:
three windows as wide as the phone's view (the left end, the middle, the right end), each under a high Sun, a low
Sun and after sunset, from the raw bake (the EXRs of skybake).

    blender -b --factory-startup -P tools/scenery/skyview_lo.py -- <bake out dir> <picture.jpg> [gain]

Rows: high Sun (from the right and above), the Sun low in the picture (on the right for the left and middle
windows' Sun: the set's low Suns stand 14.7 degrees to either side of its middle), after sunset (light from under the
horizon on the bases). The sky behind is a plain gradient: this is for judging the clouds, not the sky. The windows
are cut from the picture as it is (equal angles), not turned into the phone's flat view: shapes at a window's edge
are a little narrower here than on the phone.
"""
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:]
src, out = os.path.join(argv[0], 'raw'), argv[1]
gain = float(argv[2]) if len(argv) > 2 else 1.0


def rd(name):
    return bake.read_exr(os.path.join(src, name + '0001.exr'))


alpha = np.clip(rd('alpha')[..., 0], 0, 1)[..., None]
h, w = alpha.shape[:2]
up = np.linspace(1.0, 0.0, h)[:, None, None]                 # 1 at the top .. 0 at the horizon
cache = {}


def lum(name):
    if name not in cache:
        cache[name] = bake.luma(rd(name)[..., :3])[..., None]
    return cache[name]


def picture(suns, sky_top, sky_low, amb, ground, expose):
    """suns: [(light name, colour)]; the sky's colour high and low; the colour of skylight and of the ground's light."""
    bg = np.array(sky_low) * (1 - up) ** 2.2 + np.array(sky_top) * (1 - (1 - up) ** 2.2)
    c = lum('sky') * np.array(amb) + lum('gnd') * np.array(ground)
    for name, col in suns:
        c = c + lum('sun_' + name) * np.array(col)
    img = bg * (1 - alpha) + c * expose * gain
    img = 1.3 * img / (1.0 + 0.45 * img)                 # a soft shoulder: bright tops keep their modelling
    return np.clip(img, 0, 1) ** (1 / 2.2)


rows = [
    picture([('R6', (1.0, 0.98, 0.95))], (0.10, 0.22, 0.50), (0.42, 0.56, 0.74), (0.20, 0.30, 0.50), (0.22, 0.19, 0.15), 2.6),
    picture([('R1', (1.0, 0.50, 0.22)), ('GR', (0.55, 0.30, 0.16))], (0.10, 0.15, 0.34), (0.85, 0.52, 0.25), (0.13, 0.16, 0.30), (0.05, 0.04, 0.04), 3.6),
    picture([('RU0', (0.95, 0.30, 0.16)), ('GR', (0.35, 0.16, 0.12))], (0.04, 0.07, 0.20), (0.55, 0.28, 0.20), (0.07, 0.09, 0.19), (0.02, 0.02, 0.02), 4.0),
]
half = w // 2
wins = [(0, half), (w // 4, w // 4 + half), (w - half, w)]
gap = np.ones((h, 8, 3), np.float32)
sheet = np.concatenate([np.concatenate(sum([[r[:, a:b], gap] for a, b in wins], [])[:-1], axis=1) for r in rows], axis=0)
sheet = sheet[::2, ::2]                                   # half size: a sheet to look at
px = np.ones(sheet.shape[:2] + (4,), np.float32)
px[..., :3] = sheet
img = bpy.data.images.new('sheet', sheet.shape[1], sheet.shape[0], alpha=False)
img.pixels.foreach_set(px[::-1].ravel())
s = bpy.context.scene
s.view_settings.view_transform = 'Standard'
s.render.image_settings.file_format = 'JPEG' if out.lower().endswith('.jpg') else 'PNG'
s.render.image_settings.quality = 90
img.save_render(out, scene=s)
seen = slice(int(h * 10.0 / 41.0), int(h * 38.0 / 41.0))
print('wrote', out, sheet.shape, 'cover between 2 and 30 degrees: %.3f (left %.3f, middle %.3f, right %.3f)' % (
    float(alpha[seen].mean()), *[float(alpha[seen, a:b].mean()) for a, b in wins]), flush=True)
