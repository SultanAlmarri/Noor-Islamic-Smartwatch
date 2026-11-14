"""The Meadow's ground: real hill country, from an elevation model.

The place is the Diablo Range in California, the hills round Calaveras Reservoir above Sunol: grass hills dotted
with oaks, ridge behind ridge to Mount Hamilton's range. The heights are the USGS 3D Elevation Program's 1/3
arc-second model (ten metres; public domain; see sources/meadow.md), read as they are: nothing is added to the
hills and nothing cut from them. This file finds a place to stand, and hands the scene the ground in scene metres
(the camera at x = 0, y = 0 looking along +y, +x to its right) with a few maps made from it. Pure numpy.

    python meadow_terrain.py views <out dir>      looks for places to stand; writes a sheet of what each would see
    python meadow_terrain.py land <out dir>       the ground for the chosen view (SITE), and a picture of its view
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hills    # noqa: E402

TOOLS = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools')
TILE = os.environ.get('NOOR_MEADOW_DEM', os.path.join(TOOLS, 'assets', 'usgs', 'USGS_13_n38w122.tif'))
LAKE_NEAR = (37.474, -121.815)      # somewhere on Calaveras Reservoir, to find its water by
EYE = 1.7

# Where we stand and which way we look (degrees clockwise from north): chosen from `views` (see find_views).
SITE = dict(lat=37.47876, lon=-121.83574, bearing=110.0)

N = 448                 # the near ground's maps: cells of 16 m
CELL = 16.0
UP = 4                  # ... and its heights four times finer
BACK = 480.0            # metres of ground kept behind the camera
EARTH = 6371000.0

_DEM = {}


def dem():
    """The elevation model: heights (row 0 north), longitude and latitude of its top-left corner, a pixel's size in
    degrees. (A plain array beside the tile, made from it by meadow_dem.py.)"""
    if not _DEM:
        keep = TILE[:-4] + '.npy'
        if not os.path.exists(keep):
            raise SystemExit('run meadow_dem.py on %s first (it turns the tile into a plain array)' % TILE)
        g = json.load(open(TILE[:-4] + '.json'))
        _DEM.update(a=np.load(keep, mmap_mode='r'), lon=g['lon'], lat=g['lat'], sx=g['sx'], sy=g['sy'])
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


def raw_height(x, y, site=SITE, smooth=True):
    """The model's height (metres above the sea) at scene positions."""
    r, c = rowcol(x, y, site)
    a = dem()['a']
    return (hills.sample_smooth if smooth else hills.sample)(a, c, r)


def curve(x, y):
    """How far the Earth's roundness (and the bending of light) drops a distant place below our level."""
    return 0.87 * (np.asarray(x, np.float64) ** 2 + np.asarray(y, np.float64) ** 2) / (2.0 * EARTH)


_WATER = {}


def lake():
    """The reservoir's water as the model holds it (a dead-level surface): a mask over a window of the model, the
    window's first row and column, and the water's height."""
    if not _WATER:
        d = dem()
        r0 = int((d['lat'] - LAKE_NEAR[0]) / d['sy']) - 520
        c0 = int((LAKE_NEAR[1] - d['lon']) / d['sx']) - 520
        w = np.asarray(d['a'][r0:r0 + 1040, c0:c0 + 1040], np.float32)
        gy, gx = np.gradient(w, 10.0)
        flat = np.hypot(gx, gy) < 0.004
        level = float(np.median(w[500:540, 500:540][flat[500:540, 500:540]])) if flat[500:540, 500:540].any() else float(w[520, 520])
        cand = flat & (np.abs(w - level) < 0.6)
        mask = np.zeros_like(cand)
        seeds = np.argwhere(cand[500:540, 500:540])
        if len(seeds):
            mask[seeds[0][0] + 500, seeds[0][1] + 500] = True
        for _ in range(2000):                               # spread from the seed over the level water
            p = np.pad(mask, 1)
            grown = (p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:] | mask) & cand
            if grown.sum() == mask.sum():
                break
            mask = grown
        _WATER.update(mask=mask, r0=r0, c0=c0, level=level)
    return _WATER


def on_lake(x, y, site=SITE):
    """1 where scene positions lie on the reservoir's water (0..1, a little soft at the shore)."""
    w = lake()
    r, c = rowcol(x, y, site)
    m = w['mask'].astype(np.float32)
    rr, cc = r - w['r0'], c - w['c0']
    inside = (rr > 1) & (rr < m.shape[0] - 2) & (cc > 1) & (cc < m.shape[1] - 2)
    out = np.zeros(np.shape(rr), np.float32)
    out[inside] = hills.sample(m, cc[inside], rr[inside])
    return out


# ---- Finding a place to stand -----------------------------------------------------------------------------------

def look(site, cols=108, rows=150, up=12.0, down=14.0, reach=42000.0):
    """What the camera would see from a place: for a grid of directions (rows from `up` degrees over the horizon to
    `down` under it, columns across the picture's 47 degrees), how far the ground is (0 for sky) and whether it is
    water. Also the skyline's height at every column."""
    eye = float(raw_height([0.0], [0.0], site)[0]) + EYE
    az = np.radians(np.linspace(-23.5, 23.5, cols))
    t = np.geomspace(12.0, reach, 520)
    x = np.sin(az)[:, None] * t[None, :]
    y = np.cos(az)[:, None] * t[None, :]
    h = raw_height(x, y, site, smooth=False) - curve(x, y)
    el = np.degrees(np.arctan2(h - eye, t[None, :]))
    top = np.maximum.accumulate(el, axis=1)                  # the highest thing met so far along each column
    want = np.linspace(up, -down, rows)
    dist = np.zeros((rows, cols), np.float32)
    water = np.zeros((rows, cols), bool)
    wet = on_lake(x, y, site) > 0.5
    for j in range(cols):
        k = np.searchsorted(top[j], want, side='left')
        hit = k < len(t)
        kk = np.clip(k, 0, len(t) - 1)
        dist[:, j] = np.where(hit, t[kk], 0.0)
        water[:, j] = hit & wet[j, kk]
    return dict(dist=dist, water=water, skyline=top[:, -1], rows=want, eye=eye)


def score(v):
    """How good a view is for the picture: the band the phone shows whole (from the skyline down to ten degrees
    under the horizon) should hold water and many ranks of hills, little of it our own slope; the skyline should
    stand a few degrees up all the way across (hill country, no empty gap) without walling off the sky."""
    rows = v['rows']
    band = (rows <= 9.0) & (rows >= -10.0)
    d = v['dist'][band]
    land = d > 0
    near = float(np.mean((d > 0) & (d < 90.0)))
    lake_part = float(np.mean(v['water'][band]))
    jumps = (d[:-1] > 0) & (d[1:] > 0) & (d[:-1] / np.maximum(d[1:], 1.0) > 1.35)
    ranks = float(np.mean(np.sum(jumps, axis=0)))
    far = float(np.mean(land & (d > 6000.0)))
    sky = v['skyline']
    s = (1.6 * min(ranks, 4.0) / 4.0 + 1.4 * min(lake_part, 0.14) / 0.14 + 0.8 * min(far, 0.06) / 0.06
         - 2.5 * max(near - 0.10, 0.0) - 0.5 * max(float(sky.max()) - 10.5, 0.0) - 1.2 * max(1.2 - float(sky.min()), 0.0)
         - 0.4 * max(2.5 - float(np.mean(sky)), 0.0))
    return s, dict(ranks=round(ranks, 2), lake=round(lake_part, 3), near=round(near, 2), far=round(far, 3),
                   sky=[round(float(q), 1) for q in (sky.min(), np.mean(sky), sky.max())])


def find_views(count=8, seed=3):
    """Places on the slopes round the reservoir to stand, best first: (score, site, what was measured)."""
    rng = np.random.default_rng(seed)
    w = lake()
    d = dem()
    found = []
    ys, xs = np.nonzero(w['mask'])
    for _ in range(900):
        k = rng.integers(0, len(ys))
        lat = d['lat'] - (ys[k] + w['r0'] + 0.5) * d['sy'] + rng.normal() * 0.006
        lon = d['lon'] + (xs[k] + w['c0'] + 0.5) * d['sx'] + rng.normal() * 0.008
        site = dict(lat=float(lat), lon=float(lon), bearing=float(rng.uniform(0.0, 360.0)))
        if on_lake([0.0], [0.0], site)[0] > 0.01:
            continue
        ground = float(raw_height([0.0], [0.0], site)[0])
        if not (25.0 < ground - w['level'] < 230.0):
            continue
        # We stand on a rise: the ground falls away ahead of us.
        ahead = raw_height([0.0, 0.0], [40.0, 120.0], site)
        if ahead[0] > ground - 2.0 or ahead[1] > ground - 8.0:
            continue
        s, info = score(look(site, cols=40, rows=70))
        found.append((s, site, info))
    found.sort(key=lambda q: -q[0])
    out = []
    for q in found:
        if all(abs(q[1]['lat'] - o[1]['lat']) + abs(q[1]['lon'] - o[1]['lon']) > 0.004 or abs(q[1]['bearing'] - o[1]['bearing']) > 40.0 for o in out):
            out.append(q)
        if len(out) == count:
            break
    return out


def picture(site, cols=216, rows=330):
    """A quick grey picture of a view (far = pale, water = blue, sky = white), to choose by."""
    v = look(site, cols=cols, rows=rows, up=14.0, down=19.0)
    d = v['dist']
    g = np.where(d > 0, 0.15 + 0.6 * np.clip(np.log10(np.maximum(d, 10.0)) - 1.0, 0, 3.6) / 3.6, 1.0)
    # every fold shows: darken where the distance jumps from one row to the next
    edge = np.zeros_like(g)
    edge[1:] = (d[:-1] > 0) & (d[1:] > 0) & (d[:-1] / np.maximum(d[1:], 1.0) > 1.2) | ((d[:-1] == 0) & (d[1:] > 0))
    g = g * (1.0 - 0.6 * edge)
    rgb = np.stack([g, g, g], -1)
    rgb[v['water']] = (0.25, 0.45, 0.8)
    hz = int(np.argmin(np.abs(v['rows'])))
    rgb[hz, ::6] = (1.0, 0.3, 0.2)
    return rgb


# ---- The ground handed to the scene -----------------------------------------------------------------------------

def grid(n, cell):
    """Scene x and y of every cell centre of the near ground."""
    x = (np.arange(n) - n / 2 + 0.5) * cell
    y = (np.arange(n) + 0.5) * cell - BACK
    return np.meshgrid(x.astype(np.float32), y.astype(np.float32))


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def land(out):
    """The near ground on the scene's own grid, the camera's ground at 0. A dict of arrays:
      z       height on the fine grid (cell CELL / UP); under the reservoir a bed is hollowed (the model only knows
              the water's surface) so that the phone's water has something to lie over
      crest   how much a place stands above its surroundings (metres; negative in hollows), fine grid
      wet     0..1: how much water gathers there (the lines the streams follow), fine grid
      lake    0..1: 1 on the reservoir, fine grid
      north   -1..1: how much a slope faces north (oaks keep to the cooler side), coarse grid
      water   the reservoir's level;  ground  the camera's ground above the sea"""
    keep = os.path.join(out, 'meadow_land_%.4f_%.4f_%.0f.npz' % (SITE['lat'], SITE['lon'], SITE['bearing']))
    if os.path.exists(keep) and not os.environ.get('NOOR_REMAKE'):
        d = np.load(keep)
        return {k: d[k] for k in d.files}
    n = N * UP
    cell = CELL / UP
    xf, yf = grid(n, cell)
    ground = float(raw_height([0.0], [0.0])[0])
    zf = raw_height(xf, yf) - ground
    lk = on_lake(xf, yf)
    level = lake()['level'] - ground
    # The reservoir's bed: deeper the farther from the shore.
    inner = lk.copy()
    depth = np.zeros_like(lk)
    for _ in range(60):
        p = np.pad(inner, 1, mode='edge')
        inner = np.minimum(inner, np.minimum(np.minimum(p[:-2, 1:-1], p[2:, 1:-1]), np.minimum(p[1:-1, :-2], p[1:-1, 2:])))
        depth += inner
    zf = np.where(lk > 0.5, np.minimum(zf, level - 0.15 - 0.12 * depth), zf)
    # Small relief the ten-metre model cannot hold: hummocks a few strides across, near us.
    df = np.hypot(xf, yf)
    zf = zf + (hills.noise(n, 12, 10, 3, 2.2) * 0.06 + hills.noise(n, 13, 40, 8, 2.3) * 0.18 * smooth(15.0, 90.0, df)) * (lk < 0.5)
    # The coarse grid's maps.
    xc, yc = grid(N, CELL)
    zc = raw_height(xc, yc) - ground
    lc = on_lake(xc, yc) > 0.5
    crest = hills.refine(zc - hills.blur(zc, 7.0), UP)
    outlet = lc.copy()
    outlet[0, :] = outlet[-1, :] = outlet[:, 0] = outlet[:, -1] = True
    filled = hills.flood(zc, outlet)
    rcv, _ = hills.receivers(filled, CELL)
    acc = hills.accumulate(rcv).reshape(N, N).astype(np.float32)
    wet = hills.refine(np.clip(np.log10(np.maximum(acc, 1.0)) / 3.0, 0.0, 1.0), UP)
    gy, gx = np.gradient(hills.blur(zc, 1.5), CELL)                 # slopes in scene axes ...
    b = math.radians(SITE['bearing'])
    north_fall = -(-gx * math.sin(b) + gy * math.cos(b))             # ... how fast the ground falls towards the north
    north = np.clip(north_fall / 0.25, -1.0, 1.0)
    f = dict(z=zf.astype(np.float32), crest=crest.astype(np.float32), wet=wet.astype(np.float32),
             lake=lk.astype(np.float32), north=north.astype(np.float32), water=np.float32(level), ground=np.float32(ground))
    np.savez(keep, **f)
    return f


def height_at(f, x, y):
    """The near ground's height at scene positions (arrays)."""
    cell = CELL / UP
    n = f['z'].shape[0]
    return hills.sample_smooth(f['z'], np.asarray(x) / cell + n / 2 - 0.5, (np.asarray(y) + BACK) / cell - 0.5)


def field_at(a, x, y):
    cell = CELL / UP
    n = a.shape[0]
    return hills.sample(a, np.asarray(x) / cell + n / 2 - 0.5, (np.asarray(y) + BACK) / cell - 0.5)


def far_height(x, y, ground):
    """The country beyond the near ground: the model itself, lowered by the Earth's curve."""
    return (raw_height(x, y) - ground - curve(x, y)).astype(np.float32)


if __name__ == '__main__':
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    w = lake()
    print('reservoir: level %.1f m, %.0f ha' % (w['level'], w['mask'].sum() * 10.3 * 8.2 / 1e4), flush=True)
    if mode == 'views':
        tiles = []
        for i, (s, site, info) in enumerate(find_views()):
            print('view %d: score %.2f  lat %.5f lon %.5f bearing %.0f  %s' % (i, s, site['lat'], site['lon'], site['bearing'], info), flush=True)
            tiles.append(picture(site))
        hills.write_png(os.path.join(out, 'views.png'), np.concatenate(tiles, axis=1)[::-1])
    else:
        s, info = score(look(SITE))
        print('the view: score %.2f %s' % (s, info), flush=True)
        hills.write_png(os.path.join(out, 'view.png'), picture(SITE, 432, 660)[::-1])
        os.environ['NOOR_REMAKE'] = '1'
        f = land(out)
        print('ground %.1f m above the sea; the water %.1f m below us' % (float(f['ground']), -float(f['water'])), flush=True)
