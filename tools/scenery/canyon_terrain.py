"""The Red Rock scenery's ground: Oak Creek under Cathedral Rock as it stands, from elevation models.

The heights are the USGS 3D Elevation Program's (public domain; see sources/canyon.md): the lidar model at one metre
for the creek, its banks and Cathedral Rock, the ten-metre model for the land beyond; cut out of their tiles by
canyon_dem.py and read as they are: no rock is moved, added, removed, raised or lowered. This file finds what a
place would see, and hands the scene the ground in scene metres (the camera at x = 0, y = 0 looking along +y, +x to
its right). Pure numpy.

    python3 canyon_terrain.py views <out dir>      what each place of VIEWS would see (a sheet of rough pictures)
"""
import json
import math
import os
import sys

import numpy as np

TOOLS = os.environ.get('NOOR_TOOLS', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools'))
WORK = os.environ.get('NOOR_CANYON_WORK', os.path.join(TOOLS, 'work', 'canyon'))
EARTH = 6371000.0

# Where we stand (UTM zone 12 north, NAD83: easting, northing in metres) and which way we look (degrees clockwise
# from grid north): chosen from `views`. On a slab of red sandstone at the edge of a long pool of Oak Creek, just
# below Red Rock Crossing, looking east-south-east up the creek's valley at Cathedral Rock (1.4 km off, 300 m above
# the water). `water` is the level of the pool (metres above the sea), as the lidar found it.
SITE = dict(east=426058.0, north=3853975.0, bearing=113.0, water=1205.08)

_DEM = {}


def dem():
    if not _DEM:
        one = os.path.join(WORK, 'cathedral_1m.npy')
        if not os.path.exists(one):
            raise SystemExit('run canyon_dem.py first (it cuts %s out of the USGS tiles)' % one)
        _DEM['a'] = np.load(one, mmap_mode='r')
        _DEM['g'] = json.load(open(one[:-4] + '.json'))
        _DEM['b'] = np.load(os.path.join(WORK, 'cathedral_10m.npy'), mmap_mode='r')
        _DEM['h'] = json.load(open(os.path.join(WORK, 'cathedral_10m.json')))
    return _DEM


def lonlat(east, north):
    """Longitude and latitude (degrees, NAD83) of UTM zone 12 positions."""
    a, f, k0 = 6378137.0, 1 / 298.257222101, 0.9996
    e2 = f * (2 - f)
    e1 = (1 - math.sqrt(1 - e2)) / (1 + math.sqrt(1 - e2))
    m = np.asarray(north, np.float64) / k0
    mu = m / (a * (1 - e2 / 4 - 3 * e2 ** 2 / 64 - 5 * e2 ** 3 / 256))
    p1 = (mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * np.sin(2 * mu) + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * np.sin(4 * mu)
          + (151 * e1 ** 3 / 96) * np.sin(6 * mu))
    ep2 = e2 / (1 - e2)
    c1 = ep2 * np.cos(p1) ** 2
    t1 = np.tan(p1) ** 2
    n1 = a / np.sqrt(1 - e2 * np.sin(p1) ** 2)
    r1 = a * (1 - e2) / (1 - e2 * np.sin(p1) ** 2) ** 1.5
    d = (np.asarray(east, np.float64) - 500000.0) / (n1 * k0)
    lat = p1 - (n1 * np.tan(p1) / r1) * (d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * d ** 4 / 24
                                           + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * d ** 6 / 720)
    lon = (d - (1 + 2 * t1 + c1) * d ** 3 / 6 + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * d ** 5 / 120) / np.cos(p1)
    return -111.0 + np.degrees(lon), np.degrees(lat)


def utm(x, y, site=SITE):
    """Easting and northing of scene positions."""
    b = math.radians(site['bearing'])
    x = np.asarray(x, np.float64)
    y = np.asarray(y, np.float64)
    return site['east'] + x * math.cos(b) + y * math.sin(b), site['north'] - x * math.sin(b) + y * math.cos(b)


def scene_xy(east, north, site=SITE):
    """Where a place of the world lies in the scene (metres right of the camera, metres ahead of it)."""
    b = math.radians(site['bearing'])
    de, dn = np.asarray(east, np.float64) - site['east'], np.asarray(north, np.float64) - site['north']
    return de * math.cos(b) - dn * math.sin(b), de * math.sin(b) + dn * math.cos(b)


def _bilinear(a, r, c):
    r = np.clip(r, 0.0, a.shape[0] - 1.001)
    c = np.clip(c, 0.0, a.shape[1] - 1.001)
    ir, ic = np.floor(r).astype(np.int64), np.floor(c).astype(np.int64)
    tr, tc = (r - ir).astype(np.float32), (c - ic).astype(np.float32)
    return a[ir, ic] * (1 - tr) * (1 - tc) + a[ir, ic + 1] * (1 - tr) * tc + a[ir + 1, ic] * tr * (1 - tc) + a[ir + 1, ic + 1] * tr * tc


EDGE = 60.0       # metres over which the one-metre model runs into the ten-metre one at its edge


def heights(x, y, site=SITE):
    """The models' heights (metres above the sea) at scene positions: the one-metre model where it reaches, the
    ten-metre model beyond."""
    d = dem()
    e, n = utm(x, y, site)
    shape = e.shape
    e, n = e.ravel(), n.ravel()
    g = d['g']
    c = (e - g['east']) / g['cell'] - 0.5
    r = (g['north'] - n) / g['cell'] - 0.5
    a = d['a']
    inside = np.minimum(np.minimum(c, a.shape[1] - 1 - c), np.minimum(r, a.shape[0] - 1 - r))
    out = np.zeros(len(e), np.float32)
    near = inside > 0.0
    w = np.zeros(len(e), np.float32)
    if near.any():
        v = _bilinear(a, r[near], c[near])
        ok = np.isfinite(v)
        out[near] = np.where(ok, v, 0.0)
        w[near] = np.where(ok, np.clip(inside[near] / EDGE, 0.0, 1.0), 0.0)
    far = w < 1.0
    if far.any():
        h = d['h']
        lon, lat = lonlat(e[far], n[far])
        v = _bilinear(d['b'], (h['lat'] - lat) / h['sy'] - 0.5, (lon - h['lon']) / h['sx'] - 0.5)
        out[far] = out[far] * w[far] + v * (1.0 - w[far])
    return out.reshape(shape)


def scene_z(x, y, site=SITE):
    """Heights in scene metres: above the pool's water, and sunk by the Earth's curve with distance."""
    d2 = np.asarray(x, np.float64) ** 2 + np.asarray(y, np.float64) ** 2
    return (heights(x, y, site) - site['water'] - d2 / (2 * EARTH) * 0.87).astype(np.float32)


# ---- The ground near us, finer than the model ---------------------------------------------------------------------

CELL = 0.25                 # metres
NEAR_X = (-150.0, 150.0)    # what the near ground covers: metres right of the camera
NEAR_Y = (-30.0, 330.0)     # ... and ahead of it


def _cubic(a, r, c):
    """Catmull-Rom between the model's points: passes through its own heights, without the creases of straight
    lines between them."""
    r = np.clip(r, 1.0, a.shape[0] - 3.001)
    c = np.clip(c, 1.0, a.shape[1] - 3.001)
    ir, ic = np.floor(r).astype(np.int64), np.floor(c).astype(np.int64)
    tr, tc = (r - ir).astype(np.float32), (c - ic).astype(np.float32)

    def w(t):
        return [-0.5 * t ** 3 + t ** 2 - 0.5 * t, 1.5 * t ** 3 - 2.5 * t ** 2 + 1, -1.5 * t ** 3 + 2 * t ** 2 + 0.5 * t, 0.5 * t ** 3 - 0.5 * t ** 2]

    wr, wc = w(tr), w(tc)
    out = np.zeros(r.shape, np.float32)
    for j in range(4):
        acc = np.zeros(r.shape, np.float32)
        for i in range(4):
            acc += a[ir - 1 + j, ic - 1 + i] * wc[i]
        out += acc * wr[j]
    return out


def _box(a, n):
    c = np.cumsum(np.cumsum(np.pad(a.astype(np.float64), ((n, n + 1), (n, n + 1)), mode='edge'), 0), 1)
    k = 2 * n + 1
    return ((c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) / (k * k)).astype(np.float32)


def near_field(site=SITE):
    """The ground within a few hundred metres on a grid of CELL metres (rows = metres ahead, columns = metres to the
    right): the one-metre model read smoothly, and the pool. The lidar gives the water's surface, not what lies
    under it: where the model lies at the pool's level, a bed is hollowed out (by formula: deeper away from the
    shore, to 0.8 m), so that the phone can draw the water over it. Returns z (scene metres over the water),
    `shore` (metres from the water's edge: negative under water, positive on land, clipped to +-8)."""
    path = os.path.join(WORK, 'near2_%d_%d_%d.npz' % (site['east'], site['north'], round(site['bearing'] * 10)))
    if os.path.exists(path):
        f = np.load(path)
        return dict(z=f['z'], shore=f['shore'])
    d = dem()
    xs = np.arange(NEAR_X[0], NEAR_X[1], CELL) + CELL / 2
    ys = np.arange(NEAR_Y[0], NEAR_Y[1], CELL) + CELL / 2
    X, Y = np.meshgrid(xs, ys)
    e, n = utm(X, Y, site)
    g = d['g']
    z = _cubic(d['a'], (g['north'] - n) / g['cell'] - 0.5, (e - g['east']) / g['cell'] - 0.5) - site['water']
    wet = z < 0.035
    # Specks of 'water' a cell or two across on the slabs are not pools, and specks of rock in the pool are the
    # lidar's noise: keep what a 1.5 m window agrees on.
    wet = _box(wet, 3) > 0.5
    # Distance to the shore, by growing each side cell by cell (8 m is enough).
    shore = np.where(wet, -0.0, 0.0).astype(np.float32)
    inside, outside = wet.copy(), ~wet
    dist_in = np.zeros(z.shape, np.float32)
    dist_out = np.zeros(z.shape, np.float32)
    for k in range(int(8.0 / CELL)):
        p = np.pad(inside, 1, mode='edge')
        inside = p[1:-1, 1:-1] & p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]
        dist_in += inside * CELL
        p = np.pad(outside, 1, mode='edge')
        outside = p[1:-1, 1:-1] & p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]
        dist_out += outside * CELL
    shore = np.where(wet, -dist_in - CELL / 2, dist_out + CELL / 2)
    # (The water's edge must not follow the grid's squares: the distance is smoothed, and the bed and the land
    # meet in a slope across the edge instead of a step from one cell to the next.)
    shore = _box(_box(shore, 3), 3)
    depth = np.clip(0.16 * np.maximum(-shore, 0.0), 0.0, 0.8)
    bed = np.minimum(z, -depth)
    land = np.maximum(z, np.clip(shore * 0.05, 0.0, 0.04))
    w = np.clip(shore / 0.6 * 0.5 + 0.5, 0.0, 1.0)              # 0 under water .. 1 on land, over 1.2 m
    w = w * w * (3.0 - 2.0 * w)
    z = bed * (1.0 - w) + land * w - 0.03 * (1.0 - np.abs(2.0 * w - 1.0))
    np.savez_compressed(path, z=z.astype(np.float32), shore=shore.astype(np.float32))
    return dict(z=z.astype(np.float32), shore=shore.astype(np.float32))


def near_at(a, x, y):
    """A near-field grid read at scene positions (bilinear; the edge repeats)."""
    return _bilinear(a, (np.asarray(y, np.float64) - NEAR_Y[0]) / CELL - 0.5, (np.asarray(x, np.float64) - NEAR_X[0]) / CELL - 0.5)


def land_height(x, y, site=SITE, near=None):
    """The ground's height anywhere (scene metres over the pool's water): the near grid, running into the models
    beyond it."""
    x = np.asarray(x, np.float64)
    y = np.asarray(y, np.float64)
    near = near_field(site) if near is None else near
    edge = np.minimum(np.minimum(x - NEAR_X[0], NEAR_X[1] - x), np.minimum(y - NEAR_Y[0], NEAR_Y[1] - y))
    w = np.clip(edge / 25.0, 0.0, 1.0).astype(np.float32)
    out = np.zeros(x.shape, np.float32)
    inside = w > 0.0
    if inside.any():
        out[inside] = near_at(near['z'], x[inside], y[inside]) * w[inside]
    rest = w < 1.0
    if rest.any():
        out[rest] += scene_z(x[rest], y[rest], site) * (1.0 - w[rest])
    return out


# ---- Looking at a place before building it ----------------------------------------------------------------------

def rough_view(site, eye=2.0, w=540, h=760, horizon=420, focal=1.15):
    """What Noor's camera would see from a place, drawn roughly from the models alone (each column of the picture
    swept outward): brightness by the slope along the ray and by distance, the pool in blue, the path of the Sun (as
    the app draws it, standing on the skyline) in yellow. Returns the picture [h, w, 3] and the skyline (degrees)."""
    base = eye
    img = np.zeros((h, w, 3), np.uint8)
    img[:] = (205, 220, 240)
    tan_az = (np.arange(w) + 0.5 - w / 2) / (focal * w)
    dist = np.concatenate([np.arange(2.0, 400.0, 0.5), np.geomspace(400.0, 30000.0, 2600)])
    top = np.full(w, h, np.int64)
    prev = None
    for k, d in enumerate(dist):
        x, y = d * tan_az, np.full(w, d)
        z = scene_z(x, y, site) - base
        wet = z + base < 0.0
        row = np.clip(np.floor(horizon - focal * w * z / d).astype(np.int64), 0, h)
        if prev is None:
            prev = z
        step = dist[k] - dist[k - 1] if k else 1.0
        slope = (z - prev) / step
        prev = z
        fog = 1 - math.exp(-d / 6000.0)
        shade = np.clip(105 + 95 * np.tanh(slope * 1.1), 20, 235) * (1 - fog) + 200 * fog
        for c in np.nonzero(row < top)[0]:
            if wet[c]:
                img[row[c]:top[c], c] = (60, 110, 190)
            else:
                s = shade[c]
                img[row[c]:top[c], c] = (min(255, s * 1.12), s * 0.72, s * 0.55)
            top[c] = row[c]
    sky_el = np.degrees(np.arctan((horizon - top) / (focal * w)))
    # The Sun's path: rises 14.7 degrees left, sets 14.7 right, tops out 14.2 degrees up, standing on the skyline.
    def lift_at(u):
        cols = np.clip(((u - 0.0125 + 0.0025 * np.arange(11)) * w).astype(int), 0, w - 1)
        return max(0.0, min((horizon - top[cols]).min(), 0.5 * focal * w))
    arc = math.tan(math.radians(14.2)) * focal * w
    la, lb = lift_at(0.5 - 0.3024), lift_at(0.5 + 0.3024)
    hidden = 0
    for i in range(201):
        t = i / 200.0
        u = 0.1976 + 0.6048 * t
        lift = min(arc * 0.72, la + (lb - la) * t * t * (3 - 2 * t))
        yy = int(horizon - lift - math.sin(math.pi * t) * (arc - lift))
        xx = int(u * w)
        hid = yy >= top[min(xx, w - 1)]
        hidden += hid
        img[max(yy - 1, 0):yy + 2, max(xx - 1, 0):xx + 2] = (200, 60, 0) if hid else (255, 235, 0)
    img[horizon, ::4] = (0, 0, 0)
    for deg in (5, 10, 15, -10):
        img[int(horizon - math.tan(math.radians(deg)) * focal * w), ::16] = (90, 90, 90)
    return img, sky_el, hidden / 201.0, (la, lb)


VIEWS = [
    dict(name='upper reach, 1.2 km', east=426424.0, north=3853961.0, bearing=118.5, water=1206.9),
    dict(name='big pool, 1.1 km', east=426504.0, north=3853930.0, bearing=121.0, water=1208.02),
    dict(name='slab pool, 104', east=426044.0, north=3853965.0, bearing=104.0, water=1205.08),
    dict(name='slab pool edge, 113', east=SITE['east'], north=SITE['north'], bearing=113.0, water=1205.08),
]


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else 'views'
    out = sys.argv[2] if len(sys.argv) > 2 else '.'
    os.makedirs(out, exist_ok=True)
    if mode == 'views':
        from PIL import Image
        tiles = []
        for v in VIEWS:
            img, sky, hidden, lifts = rough_view(v)
            az = np.degrees(np.arctan((np.arange(len(sky)) + 0.5 - len(sky) / 2) / (1.15 * len(sky))))
            print('%-24s ground %.1f m, skyline highest %.1f at %.1f, at rise %.1f, at set %.1f, Sun hidden %.0f %% of its day'
                  % (v['name'], float(heights(np.array([0.0]), np.array([0.0]), v)[0]), sky.max(), az[int(np.argmax(sky))],
                     sky[int(len(sky) * 0.1976)], sky[int(len(sky) * 0.8024)], 100 * hidden), flush=True)
            tiles.append(np.pad(img, ((0, 0), (0, 6), (0, 0))))
        Image.fromarray(np.concatenate(tiles, 1)).save(os.path.join(out, 'views.png'))


if __name__ == '__main__':
    main()
