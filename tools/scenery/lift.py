"""Writes `lift` (bake.sun_lift) into the land.json of sceneries that were packed before the packer did it itself.

    blender -b --factory-startup -P tools/scenery/lift.py -- <land folder> [<land folder> ...]
"""
import json
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

for d in sys.argv[sys.argv.index('--') + 1:]:
    meta = json.load(open(os.path.join(d, 'land.json')))
    img = bpy.data.images.load(os.path.join(d, 'a.webp'))
    img.alpha_mode = 'CHANNEL_PACKED'
    w, h = img.size
    px = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(px)
    alpha = px.reshape(h, w, 4)[::-1, :, 3]                      # (Blender's rows run from the bottom)
    meta['lift'] = bake.sun_lift(alpha, meta['top'], meta['horizon'], meta.get('focal', 1.15) * meta['width'], meta['height'])
    json.dump(meta, open(os.path.join(d, 'land.json'), 'w'), indent=1)
    print('lift of %s: %s' % (d, meta['lift']), flush=True)
