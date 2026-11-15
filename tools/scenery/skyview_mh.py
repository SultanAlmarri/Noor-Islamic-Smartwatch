"""Looks at a baked wide cloud set roughly as the phone will light it: three windows of the phone's width (the left
end, the middle, the right end of the set's 94 degrees), each at three moments, and the opacity alone.

    blender -b --factory-startup -P tools/scenery/skyview_mh.py -- <set folder> <out.jpg>

Rows: the Sun high on the right; a low Sun in the picture; the Sun under the horizon (dusk). Last row: opacity.
The colours are only stand-ins for the phone's sky; the windows are cut straight out of the equal-angle picture
(the phone's own view differs a little towards its edges).
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


a, f, k, g = load('a'), load('f'), light('k'), light('g')
h, w = a.shape
el = meta['elMax'] - (np.arange(h, dtype=np.float32) + 0.5) / h * (meta['elMax'] - meta['elMin'])
t = (np.clip(1.0 - el / 40.0, 0, 1) ** 2.2)[:, None, None]
MOMENTS = [  # sun picture, sun colour, sky above, horizon, light from the sky, light from the ground
    ('s_r6', (1.00, 0.98, 0.94), (0.16, 0.34, 0.70), (0.60, 0.74, 0.90), (0.22, 0.30, 0.45), (0.12, 0.10, 0.08)),
    ('s_r1', (1.00, 0.52, 0.22), (0.20, 0.27, 0.52), (0.98, 0.62, 0.30), (0.12, 0.14, 0.24), (0.03, 0.02, 0.02)),
    ('s_ru1', (0.80, 0.22, 0.18), (0.05, 0.07, 0.19), (0.50, 0.26, 0.20), (0.03, 0.035, 0.07), (0.0, 0.0, 0.0)),
]
half = w // 2
cuts = [(0, half), (w // 4, w // 4 + half), (w - half, w)]
rows = []
for name, sun, top, hor, amb, gnd in MOMENTS:
    s = light(name)
    bg = np.array(top, np.float32) * (1 - t) + np.array(hor, np.float32) * t
    cloud = s[..., None] * np.array(sun, np.float32) * 1.5 + k[..., None] * np.array(amb, np.float32) + g[..., None] * np.array(gnd, np.float32)
    haze = np.clip(f * 0.5, 0, 1)[..., None]
    cloud = cloud * (1 - haze) + np.array(hor, np.float32) * haze * a[..., None]
    pic = bg * (1 - a[..., None]) + cloud
    rows.append(np.concatenate([np.pad(pic[:, x0:x1], ((0, 0), (0, 6), (0, 0))) for x0, x1 in cuts], axis=1))
al = np.repeat(a[..., None], 3, -1) ** 2.2
rows.append(np.concatenate([np.pad(al[:, x0:x1], ((0, 0), (0, 6), (0, 0))) for x0, x1 in cuts], axis=1))
sheet = np.concatenate(rows, axis=0)
while sheet.shape[1] > 1900:
    sheet = sheet[::2, ::2]
bake.save8(out, np.clip(sheet, 0, 1) ** (1 / 2.2), quality=90)
r0, r1 = int(h * (meta['elMax'] - 30.0) / (meta['elMax'] - meta['elMin'])), int(h * (meta['elMax'] - 2.0) / (meta['elMax'] - meta['elMin']))
print('saved', out, 'coverSeen %.2f (windows %.2f %.2f %.2f), alpha over 0.9: %.2f' % (
    float(a[r0:r1].mean()), *[float(a[r0:r1, x0:x1].mean()) for x0, x1 in cuts], float((a[r0:r1] > 0.9).mean())), flush=True)
