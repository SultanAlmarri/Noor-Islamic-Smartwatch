"""The Red Rock scenery's ground: Monument Valley as it stands, from an elevation model.

The heights are the USGS 3D Elevation Program's 1/3 arc-second model (ten metres; public domain; see
sources/canyon.md), cut out of its tiles by canyon_dem.py and read as they are: no butte is moved, added, removed,
raised or lowered. This file finds what a place would see, and hands the scene the ground in scene metres (the
camera at x = 0, y = 0 looking along +y, +x to its right). Pure numpy.

    python canyon_terrain.py views <out dir>      what each place of VIEWS would see (a sheet of rough pictures)
"""
import json
import math
import os
import struct
import sys
import zlib

import numpy as np

TOOLS = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools')
PIECE = os.environ.get('NOOR_CANYON_DEM', os.path.join(TOOLS, 'assets', 'usgs', 'monument_valley.npy'))
EYE = 1.7
EARTH = 6371000.0

# Where we stand and which way we look (degrees clockwise from north): chosen from `views`.
# (On the valley floor east of the buttes, looking west-north-west: Mitchell Mesa at the left edge, Merrick Butte
# left of the middle, the West Mitten far off right of the middle, the East Mitten nearest, on the right.)
SITE = dict(lat=36.9770, lon=-110.0452, bearing=282.1)

_DEM = {}


def dem():
    """The elevation model: heights (row 0 north), longitude and latitude of its top-left corner, a pixel's size in
    degrees."""
    if not _DEM:
        if not os.path.exists(PIECE):
            raise SystemExit('run canyon_dem.py first (it cuts %s out of the USGS tiles)' % PIECE)
        g = json.load(open(PIECE[:-4] + '.json'))
        _DEM.update(a=np.load(PIECE, mmap_mode='r'), lon=g['lon'], lat=g['lat'], sx=g['sx'], sy=g['sy'])
    return _DEM


def rowcol(x, y, site=SITE):
    """Where scene positions lie on the model (fractional row and column)."""
    d = dem()
    b = math.radians(site['bearing'])
    x = np.asarray(x, np.float64)
    y = np.asarray(y, np.float64)
    east, north = x * math.cos(b) + y * math.sin(b), -x * math.sin(b) + y * math.cos(b)
    lat = site['lat'] + north / 111320.0
    lon = site['lon'] + east / (111320.0 * math.cos(math.radians(site['lat'])))
    return (d['lat'] - lat) / d['sy'] - 0.5, (lon - d['lon']) / d['sx'] - 0.5


def scene_xy(lat, lon, site=SITE):
    """Where a place of the world lies in the scene (metres right of the camera, metres ahead of it)."""
    b = math.radians(site['bearing'])
    east = (lon - site['lon']) * 111320.0 * math.cos(math.radians(site['lat']))
    north = (lat - site['lat']) * 111320.0
    return east * math.cos(b) - north * math.sin(b), east * math.sin(b) + north * math.cos(b)


def heights(x, y, site=SITE, smooth=True):
    """The model's heights (metres above the sea) at scene positions: read smoothly between its points (cubic), or
    plainly (bilinear)."""
    a = dem()['a']
    r, c = rowcol(x, y, site)
    r = np.clip(r, 1.0, a.shape[0] - 3.0)
    c = np.clip(c, 1.0, a.shape[1] - 3.0)
    ir, ic = np.floor(r).astype(np.int64), np.floor(c).astype(np.int64)
    tr, tc = (r - ir).astype(np.float32), (c - ic).astype(np.float32)
    if not smooth:
        return (a[ir, ic] * (1 - tr) * (1 - tc) + a[ir, ic + 1] * (1 - tr) * tc + a[ir + 1, ic] * tr * (1 - tc) + a[ir + 1, ic + 1] * tr * tc).astype(np.float32)

    def w(t):       # Catmull-Rom: passes through the model's own heights
        return [-0.5 * t ** 3 + t ** 2 - 0.5 * t, 1.5 * t ** 3 - 2.5 * t ** 2 + 1, -1.5 * t ** 3 + 2 * t ** 2 + 0.5 * t, 0.5 * t ** 3 - 0.5 * t ** 2]

    wr, wc = w(tr), w(tc)
    out = np.zeros(r.shape, np.float32)
    for j in range(4):
        acc = np.zeros(r.shape, np.float32)
        for i in range(4):
            acc += a[ir - 1 + j, ic - 1 + i] * wc[i]
        out += acc * wr[j]
    return out


def ground(site=SITE):
    """The height of the ground we stand on (metres above the sea)."""
    return float(heights(np.array([0.0]), np.array([0.0]), site)[0])


def scene_z(x, y, site=SITE, smooth=True, base=None):
    """Heights in scene metres: above the ground we stand on, and sunk by the Earth's curve with distance."""
    base = ground(site) if base is None else base
    d2 = np.asarray(x, np.float64) ** 2 + np.asarray(y, np.float64) ** 2
    return (heights(x, y, site, smooth) - base - d2 / (2 * EARTH) * 0.87).astype(np.float32)


# ---- Looking at a place before building it ----------------------------------------------------------------------

def rough_view(site, w=270, h=420, horizon=168, focal=1.15):
    """What Noor's camera would see from a place, drawn roughly from the model alone (far to near, each column of
    the picture swept outward): brightness by the slope's facing and by distance. The sky is left white."""
    base = ground(site) + EYE
    img = np.full((h, w), 255, np.uint8)
    cols = np.arange(w)
    tan_az = (cols + 0.5 - w / 2) / (focal * w)
    dist = np.geomspace(4.0, 70000.0, 1700)
    top = np.full(w, h, np.int64)                      # the highest row drawn so far in each column
    prev = None
    for d in dist:
        x, y = d * tan_az, np.full(w, d)               # (y is the distance along the view's middle)
        z = heights(x, y, site, smooth=False) - base - (x * x + y * y) / (2 * EARTH) * 0.87
        row = np.floor(horizon - focal * w * z / d).astype(np.int64)
        if prev is None:
            prev = z
        slope = (z - prev) / max(d * 0.004, 1.0)       # how the ground rises along the ray
        prev = z
        shade = np.clip(150 - 55 * np.tanh(slope * 1.2) - 60 * (1 - math.exp(-d / 9000.0)) + 60 * (1 - math.exp(-d / 30000.0)) * 2, 30, 235)
        row = np.clip(row, 0, h)
        for c in np.nonzero(row < top)[0]:
            img[row[c]:top[c], c] = shade[c]
            top[c] = row[c]
    return img


def write_png(path, img):
    h, w = img.shape
    raw = b''.join(b'\x00' + img[r].tobytes() for r in range(h))

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0)) +
                           chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))


# Places that were looked at (the last is the one taken: all three buttes in the picture, none of them so high under
# the Sun's arch that it would hide much of the Sun's day).
VIEWS = [
    dict(name='visitor centre terrace, east', lat=36.9820, lon=-110.1120, bearing=73.0),
    dict(name='floor, close under the East Mitten', lat=36.9802, lon=-110.0504, bearing=285.0),
    dict(name='a rise to the south-east', lat=36.9595, lon=-110.0559, bearing=326.0),
    dict(name='floor east of the buttes', lat=36.9770, lon=-110.0452, bearing=282.1),
]


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else 'views'
    out = sys.argv[2] if len(sys.argv) > 2 else '.'
    os.makedirs(out, exist_ok=True)
    if mode == 'views':
        tiles = []
        for v in VIEWS:
            img = rough_view(v)
            tiles.append(img)
            print('%-28s ground %.0f m' % (v['name'], ground(v)), flush=True)
        write_png(os.path.join(out, 'views.png'), np.concatenate([np.pad(t, ((0, 0), (0, 6)), constant_values=0) for t in tiles], 1))


if __name__ == '__main__':
    main()
