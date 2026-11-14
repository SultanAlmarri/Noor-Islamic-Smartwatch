"""Turns the USGS elevation tile the Meadow is built from into a plain array meadow_terrain.py can read.

The tile is LZW-compressed, which geotiff.py does not read; Blender's own image reader does. Run once:

    blender -b --factory-startup -P tools/scenery/meadow_dem.py -- <USGS_13_n38w122.tif>

Writes <tile>.npy (float32 heights, row 0 the north edge) and <tile>.json (longitude and latitude of the top-left
corner, a pixel's size in degrees) beside the tile.
"""
import json
import struct
import sys

import bpy
import numpy as np

path = sys.argv[sys.argv.index('--') + 1]
with open(path, 'rb') as f:
    head = f.read(8)
    bo = '<' if head[:2] == b'II' else '>'
    off = struct.unpack(bo + 'I', head[4:8])[0]
    f.seek(off)
    n = struct.unpack(bo + 'H', f.read(2))[0]
    entries = f.read(12 * n)
    tags = {}
    for i in range(n):
        tag, typ, cnt = struct.unpack(bo + 'HHI', entries[12 * i:12 * i + 8])
        if tag in (33550, 33922):                       # pixel scale, tie point: doubles, stored elsewhere
            at = struct.unpack(bo + 'I', entries[12 * i + 8:12 * i + 12])[0]
            f.seek(at)
            tags[tag] = struct.unpack(bo + str(cnt) + 'd', f.read(8 * cnt))
scale, tie = tags[33550], tags[33922]
img = bpy.data.images.load(path)
img.colorspace_settings.name = 'Non-Color'
w, h = img.size
px = np.empty(w * h * img.channels, np.float32)
img.pixels.foreach_get(px)
a = px.reshape(h, w, img.channels)[::-1, :, 0]          # Blender keeps pictures bottom row first
a = np.where(np.isfinite(a) & (a > -500.0) & (a < 9000.0), a, 0.0).astype(np.float32)
np.save(path[:-4] + '.npy', a)
json.dump(dict(lon=tie[3] - tie[0] * scale[0], lat=tie[4] + tie[1] * scale[1], sx=scale[0], sy=scale[1]), open(path[:-4] + '.json', 'w'))
print('heights', a.shape, float(a.min()), float(a.max()), 'corner', tie[3], tie[4], 'pixel', scale[0], scale[1], flush=True)
