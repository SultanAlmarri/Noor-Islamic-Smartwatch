"""Cuts the pieces of the USGS elevation models that the Red Rock scenery is built from out of their tiles.

The place is Oak Creek at Red Rock Crossing under Cathedral Rock (Sedona, Arizona). Two tiles of the 3D Elevation
Program (public domain; see sources/canyon.md), fetched with curl into <NoorTools>/assets/usgs:

    B=https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation
    curl -A scenery-tools/1.0 -O $B/1m/Projects/AZ_NorthCentral_B23/TIFF/USGS_1M_12_x42y386_AZ_NorthCentral_B23.tif
    curl -A scenery-tools/1.0 -O $B/13/TIFF/current/n35w112/USGS_13_n35w112.tif

The first is the lidar model at one metre (UTM zone 12 north, NAD83): the creek, its banks and Cathedral Rock itself.
The second is the 1/3 arc-second model (ten metres): the land seen beyond the first one's edge. Both are
LZW-compressed, which geotiff.py does not read; this script uses the `tifffile` package (pip install tifffile
imagecodecs). Run once, with any Python 3 that has numpy:

    python3 tools/scenery/canyon_dem.py [<folder with the two tiles>] [<work folder>]

Writes cathedral_1m.npy (float32 heights in metres, row 0 the north edge) with cathedral_1m.json (easting of its west
edge, northing of its north edge, a pixel's size in metres), and cathedral_10m.npy with cathedral_10m.json (longitude
and latitude of its top-left corner, a pixel's size in degrees) into the work folder (<NoorTools>/work/canyon).
"""
import json
import os
import sys

import numpy as np
import tifffile

TOOLS = os.environ.get('NOOR_TOOLS', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools'))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(TOOLS, 'assets', 'usgs')
DST = sys.argv[2] if len(sys.argv) > 2 else os.path.join(TOOLS, 'work', 'canyon')
os.makedirs(DST, exist_ok=True)

# What is kept of the one-metre tile (columns and rows of the tile): the creek from below the crossing to the tile's
# east and south edges. We look east-south-east.
C0, C1, R0, R1 = 5400, 10012, 4700, 10012
# What is kept of the ten-metre tile (degrees).
NORTH, SOUTH, WEST, EAST = 34.90, 34.55, -111.86, -111.45


def page(name):
    p = tifffile.TiffFile(os.path.join(SRC, name)).pages[0]
    return p, p.tags['ModelPixelScaleTag'].value, p.tags['ModelTiepointTag'].value


p, scale, tie = page('USGS_1M_12_x42y386_AZ_NorthCentral_B23.tif')
a = p.asarray()[R0:R1, C0:C1].astype(np.float32)
bad = ~np.isfinite(a) | (a < -500.0)
print('one metre:', a.shape, 'heights', float(a[~bad].min()), float(a[~bad].max()), 'no data:', int(bad.sum()), flush=True)
a[bad] = np.nan
np.save(os.path.join(DST, 'cathedral_1m.npy'), a)
json.dump(dict(east=tie[3] + C0 * scale[0], north=tie[4] - R0 * scale[1], cell=scale[0]), open(os.path.join(DST, 'cathedral_1m.json'), 'w'))
del a

p, scale, tie = page('USGS_13_n35w112.tif')
lon0, lat0 = tie[3] - tie[0] * scale[0], tie[4] + tie[1] * scale[1]
c0, c1 = int(round((WEST - lon0) / scale[0])), int(round((EAST - lon0) / scale[0]))
r0, r1 = int(round((lat0 - NORTH) / scale[1])), int(round((lat0 - SOUTH) / scale[1]))
a = p.asarray()[r0:r1, c0:c1].astype(np.float32)
print('ten metres:', a.shape, 'heights', float(a.min()), float(a.max()), flush=True)
np.save(os.path.join(DST, 'cathedral_10m.npy'), a)
json.dump(dict(lon=lon0 + c0 * scale[0], lat=lat0 - r0 * scale[1], sx=scale[0], sy=scale[1]), open(os.path.join(DST, 'cathedral_10m.json'), 'w'))
