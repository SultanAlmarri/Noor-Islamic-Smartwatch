"""Takes the far land out of a packed scenery, so that its water runs to an open horizon.

The owner, of the Ocean scenery (0.13): "for the beach, I don't like the distant terrain. I want you to like the
entire beach scenery. It's absolutely amazing. But the distant land, I want you to remove that. I want it to be like
open ocean." The far mountains stand wholly above the horizon in the packed pictures (the sea's bed fills every row
under it), so nothing has to be rendered again: everything above the horizon's row is made empty in `a.webp` (the
land's alpha), set to "as far as can be" in `g.png`, and `lift` (how high the skyline stands where the Sun rises and
sets) becomes level ground. The other pictures are only read where there is land, and stay as they are.

    blender -b --factory-startup -P tools/scenery/open_horizon.py -- <land folder>

Only for a scenery whose land above the horizon is all far land (check a.webp first: a near headland or a tree
standing over the horizon would be cut off at the horizon's row).
"""
import json
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

FAR = 2.0e5      # metres: what the packer writes where there is no land


def load(path):
    """A picture's stored values as they are, rows from the top: [h, w, 4], 0..1."""
    img = bpy.data.images.load(path)
    img.alpha_mode = 'CHANNEL_PACKED'
    img.colorspace_settings.name = 'Non-Color'
    w, h = img.size
    px = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(px)
    bpy.data.images.remove(img)
    return px.reshape(h, w, 4)[::-1].copy()


d = sys.argv[sys.argv.index('--') + 1]
meta = json.load(open(os.path.join(d, 'land.json')))
row = int(round(meta['horizon'] - meta['top']))            # the first row under the horizon

a = load(os.path.join(d, 'a.webp'))
if a.shape[0] != meta['height']:
    sys.exit('a.webp is not the size land.json says')
gone = int((a[:row, :, 3] >= 0.5).sum())
# The horizon's own rows: where far land stood they are solid, elsewhere the sea's bed begins there softly. They are
# given the alpha they have in the columns that never had land above them.
free = (a[max(row - 12, 0):row, :, 3] < 0.04).all(0)
if free.any():
    for y in (row, row + 1):
        a[y, :, 3] = np.minimum(a[y, :, 3], np.median(a[y, free, 3]))
# (The emptied pixels keep a colour, the bed's just under the horizon: a filtered edge then has no bright fringe.)
a[:row, :, :3] = a[row:row + 1, :, :3]
a[:row, :, 3] = 0.0
bake.save8(os.path.join(d, 'a.webp'), a, quality=90)

g = load(os.path.join(d, 'g.png'))
scale = a.shape[0] / g.shape[0]
grow = int(row / scale)
v = int(round(np.log2(FAR) / 18.0 * 65535.0))
g[:grow, :, 0] = (v >> 8) / 255.0
g[:grow, :, 1] = (v & 255) / 255.0
bake.save8(os.path.join(d, 'g.png'), g[..., :3])

meta['lift'] = bake.sun_lift(a[..., 3], meta['top'], meta['horizon'], meta.get('focal', 1.15) * meta['width'], meta['height'])
meta['skyline'] = bake.skyline_profile(a[..., 3], meta['top'], meta['horizon'], meta.get('focal', 1.15) * meta['width'], meta['height'])
meta['openHorizon'] = True
json.dump(meta, open(os.path.join(d, 'land.json'), 'w'), indent=1)
print('open horizon in %s: %d pixels of far land taken out above row %d, lift now %s' % (d, gone, row, meta['lift']), flush=True)
