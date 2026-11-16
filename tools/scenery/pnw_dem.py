"""Cuts the piece of the USGS elevation model that the Pacific Northwest scenery's chain of mountains is built from.

The owner (15 November 2026): "for the Pacific Northwest, I want the mountain to be like a chain of mountains to
look more beautiful." Until 0.13 the scenery's mountain was Mount Rainier alone, a single cone. Since 0.14 it is
the Stuart Range of the Washington Cascades seen from the south, across the Teanaway: Mount Stuart (2870 m) a little
left of the middle and the Enchantments' granite peaks (Dragontail, Colchuck, Cannon, Cashmere) running away to the
right, 16 to 30 km off. The view was found by searching the elevation model for low ground from which the real
skyline holds a chain (tools/scenery/pnw.py, CHAIN_*; the search is described in tools/scenery/sources/pnw.md).

Two tiles of the 3D Elevation Program at one arc-second (public domain; see sources/pnw.md), fetched with curl
into <NoorTools>/assets/usgs:

    B=https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/1/TIFF/current
    curl -A scenery-tools/1.0 -O $B/n48w121/USGS_1_n48w121.tif
    curl -A scenery-tools/1.0 -O $B/n48w122/USGS_1_n48w122.tif

They are LZW-compressed (geotiff.py does not read that): this script uses `tifffile` (pip install tifffile
imagecodecs). Run once, with any Python 3 that has numpy:

    python3 tools/scenery/pnw_dem.py [<folder with the tiles>] [<work folder>]

Writes stuart.npy (float32 heights in metres, row 0 the north edge, one arc-second a pixel) and stuart.json (the
longitude and latitude of its top-left pixel's centre, a pixel's size in degrees) into <NoorTools>/work/pnw.
"""
import json
import os
import sys

import numpy as np
import tifffile

TOOLS = os.environ.get('NOOR_TOOLS', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools'))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(TOOLS, 'assets', 'usgs')
DST = sys.argv[2] if len(sys.argv) > 2 else os.path.join(TOOLS, 'work', 'pnw')
os.makedirs(DST, exist_ok=True)

# What is kept (degrees): everything the view and its reflection can see from the viewpoint in pnw.py, 11 to 60 km
# out, 36 degrees to either side of its middle.
NORTH, SOUTH, WEST, EAST = 47.90, 47.30, -121.45, -120.35
N = 3600                                       # pixels a degree


def tile(name):
    with tifffile.TiffFile(os.path.join(SRC, 'USGS_1_%s.tif' % name)) as tf:
        p = tf.pages[0]
        a = p.asarray().astype(np.float32)
        tie = p.tags['ModelTiepointTag'].value
        sc = p.tags['ModelPixelScaleTag'].value
    return a, tie[3], tie[4], sc[0]


# The two tiles side by side: latitude 47 to 48, longitude -122 to -120 (each tile keeps 6 pixels of its
# neighbours round its edge: they are cut off).
whole = np.full((N, 2 * N), np.nan, np.float32)
for k, name in enumerate(('n48w122', 'n48w121')):
    a, x0, y0, step = tile(name)
    lat, lon = 48, -122 + k
    i0, j0 = int(round((y0 - lat) / step)), int(round((lon - x0) / step))
    whole[:, k * N:(k + 1) * N] = a[i0:i0 + N, j0:j0 + N]
whole[whole < -1000] = np.nan
r0, r1 = int(round((48 - NORTH) * N)), int(round((48 - SOUTH) * N))
c0, c1 = int(round((WEST + 122) * N)), int(round((EAST + 122) * N))
cut = np.nan_to_num(whole[r0:r1, c0:c1], nan=float(np.nanmin(whole)))
np.save(os.path.join(DST, 'stuart.npy'), cut)
json.dump({'lon': WEST, 'lat': NORTH, 'step': 1.0 / N}, open(os.path.join(DST, 'stuart.json'), 'w'))
print('stuart.npy: %d x %d, %.0f to %.0f m' % (cut.shape[1], cut.shape[0], cut.min(), cut.max()))
