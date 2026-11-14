"""Looks at a baked cloud set the way the phone will show it: lights the raw bake (the EXRs of skybake) with four
Suns of the day and writes them side by side.

    blender -b --factory-startup -P tools/scenery/skyview_low.py -- <bake out dir> <picture.jpg> [scale]

Afternoon (side light from the right), morning (lower, from the left), the Sun setting in the picture, and after
sunset (light from under the horizon on the bases). The sky behind is a plain gradient: this is for judging the
clouds, not the sky.
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


def lum(name):
    return bake.luma(rd(name)[..., :3])[..., None]


def picture(suns, sky_top, sky_low, amb, ground, expose):
    """suns: [(light name, colour)]; the sky's colour high and low; the colour of skylight and of the ground's light."""
    bg = np.array(sky_low) * (1 - up) ** 2.2 + np.array(sky_top) * (1 - (1 - up) ** 2.2)
    c = lum('sky') * np.array(amb) + lum('gnd') * np.array(ground)
    for name, col in suns:
        c = c + lum('sun_' + name) * np.array(col)
    img = bg * (1 - alpha) + c * expose * gain
    img = 1.3 * img / (1.0 + 0.45 * img)                 # a soft shoulder: bright tops keep their modelling
    return np.clip(img, 0, 1) ** (1 / 2.2)


tiles = [
    picture([('R6', (1.0, 0.98, 0.95))], (0.10, 0.22, 0.50), (0.42, 0.56, 0.74), (0.20, 0.30, 0.50), (0.22, 0.19, 0.15), 2.6),
    picture([('L4', (1.0, 0.93, 0.82))], (0.10, 0.21, 0.47), (0.50, 0.58, 0.70), (0.19, 0.28, 0.47), (0.18, 0.15, 0.12), 2.6),
    picture([('R1', (1.0, 0.50, 0.22)), ('GR', (0.55, 0.30, 0.16))], (0.10, 0.15, 0.34), (0.85, 0.52, 0.25), (0.13, 0.16, 0.30), (0.05, 0.04, 0.04), 3.6),
    picture([('RU0', (0.95, 0.30, 0.16)), ('GR', (0.35, 0.16, 0.12))], (0.04, 0.07, 0.20), (0.55, 0.28, 0.20), (0.07, 0.09, 0.19), (0.02, 0.02, 0.02), 4.0),
]
sheet = np.concatenate([np.concatenate(tiles[:2], axis=1), np.concatenate(tiles[2:], axis=1)], axis=0)
px = np.ones(sheet.shape[:2] + (4,), np.float32)
px[..., :3] = sheet
img = bpy.data.images.new('sheet', sheet.shape[1], sheet.shape[0], alpha=False)
img.pixels.foreach_set(px[::-1].ravel())
s = bpy.context.scene
s.view_settings.view_transform = 'Standard'
s.render.image_settings.file_format = 'JPEG' if out.lower().endswith('.jpg') else 'PNG'
s.render.image_settings.quality = 90
img.save_render(out, scene=s)
print('wrote', out, sheet.shape, 'cover %.2f' % float(alpha.mean()), flush=True)
