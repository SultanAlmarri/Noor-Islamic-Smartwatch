"""Cuts the piece of the USGS elevation model that the Red Rock scenery is built from out of its four tiles.

The place is Monument Valley (Arizona and Utah). The tiles are the 3D Elevation Program's 1/3 arc-second model (ten
metres; public domain; see sources/canyon.md): n37w111, n38w111, n37w110, n38w110. They are LZW-compressed, which
plain numpy does not read; Blender's own image reader does. Run once:

    blender -b --factory-startup -P tools/scenery/canyon_dem.py -- <folder with the four USGS_13_*.tif>

Writes monument_valley.npy (float32 heights in metres, row 0 the north edge) and monument_valley.json (longitude and
latitude of its top-left corner, a pixel's size in degrees) into the same folder.
"""
import json
import os
import struct
import sys

import bpy
import numpy as np

NORTH, SOUTH, WEST, EAST = 37.42, 36.80, -110.76, -109.96     # what is kept: the valley and the land seen beyond it (we look west)

folder = sys.argv[sys.argv.index('--') + 1]


def georef(path):
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
    return tie[3] - tie[0] * scale[0], tie[4] + tie[1] * scale[1], scale[0], scale[1]


out = None
for name in ('n37w111', 'n38w111', 'n37w110', 'n38w110'):
    path = os.path.join(folder, 'USGS_13_%s.tif' % name)
    lon0, lat0, sx, sy = georef(path)
    if out is None:
        # The whole piece on the first tile's own grid (the tiles share one grid: their corners are whole pixels apart).
        c0 = int(round((WEST - lon0) / sx))
        r0 = int(round((lat0 - NORTH) / sy))
        cols = int(round((EAST - WEST) / sx))
        rows = int(round((NORTH - SOUTH) / sy))
        base = (lon0, lat0, sx, sy, c0, r0)
        out = np.zeros((rows, cols), np.float32)
        filled = np.zeros((rows, cols), bool)
    img = bpy.data.images.load(path)
    img.colorspace_settings.name = 'Non-Color'
    w, h = img.size
    px = np.empty(w * h * img.channels, np.float32)
    img.pixels.foreach_get(px)
    a = px.reshape(h, w, img.channels)[::-1, :, 0]          # Blender keeps pictures bottom row first
    del px
    bpy.data.images.remove(img)
    # Where this tile's pixels fall in the piece.
    dc = int(round((lon0 - base[0]) / sx)) - base[4]
    dr = int(round((base[1] - lat0) / sy)) - base[5]
    r_a, r_b = max(0, dr), min(out.shape[0], dr + h)
    c_a, c_b = max(0, dc), min(out.shape[1], dc + w)
    if r_b > r_a and c_b > c_a:
        part = a[r_a - dr:r_b - dr, c_a - dc:c_b - dc]
        good = np.isfinite(part) & (part > -500.0) & (part < 9000.0)
        tgt = out[r_a:r_b, c_a:c_b]
        tgt[good] = part[good]
        filled[r_a:r_b, c_a:c_b] |= good
    print(name, 'corner', lon0, lat0, 'pixel', sx, sy, 'placed rows', r_a, r_b, 'cols', c_a, c_b, flush=True)
    del a
print('filled', float(filled.mean()), 'heights', float(out[filled].min()), float(out.max()), out.shape, flush=True)
np.save(os.path.join(folder, 'monument_valley.npy'), out)
json.dump(dict(lon=base[0] + base[4] * base[2], lat=base[1] - base[5] * base[3], sx=base[2], sy=base[3]),
          open(os.path.join(folder, 'monument_valley.json'), 'w'))
