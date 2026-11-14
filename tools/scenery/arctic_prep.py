"""The Arctic scenery's ground work, in plain Python (numpy only; run it with Blender's own python.exe):

    python -I arctic_prep.py tiles <dem dir> <work dir>       reads the Copernicus tiles into one grid (dem.npz)
    python -I arctic_prep.py wall  <work dir> [region]        looks for places to stand (stands_<region>.json)
    python -I arctic_prep.py show  <work dir> <region> <n>..  drawings of what those places see (views_<region>_wall.png)

Two elevation models are read here without any library beyond numpy (see sources/arctic.md): the Copernicus DEM
GLO-30 (one-degree tiles of Lofoten and Senja; for finding the view, and for the far land) and Kartverket's national
height model of Norway (fetched in squares at five metres; for the mountains themselves).
"""
import json
import math
import os
import struct
import sys
import zlib

import numpy as np

R_EARTH = 6371000.0
REFRACT = 0.87            # the air bends light round the Earth a little: far things sink less than geometry says


# ---- Reading a GeoTIFF -------------------------------------------------------------------------------------------

def read_geotiff(path):
    """A single-band float32 or int16 GeoTIFF (tiles or strips; no compression, deflate; predictor 1, 2 or 3).
    Returns the grid (row 0 is the north edge), the longitude and latitude of its top-left corner, and the size of
    a pixel in degrees (x, y)."""
    with open(path, 'rb') as f:
        data = f.read()
    bo = '<' if data[:2] == b'II' else '>'
    magic = struct.unpack(bo + 'H', data[2:4])[0]
    if magic != 42:
        raise ValueError('not a classic TIFF: ' + path)
    off = struct.unpack(bo + 'I', data[4:8])[0]
    n = struct.unpack(bo + 'H', data[off:off + 2])[0]
    sizes = {1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 6: 1, 7: 1, 8: 2, 9: 4, 10: 8, 11: 4, 12: 8, 16: 8}
    fmts = {1: 'B', 2: 'c', 3: 'H', 4: 'I', 6: 'b', 8: 'h', 9: 'i', 11: 'f', 12: 'd', 16: 'Q'}
    tags = {}
    for i in range(n):
        e = off + 2 + 12 * i
        tag, typ, cnt = struct.unpack(bo + 'HHI', data[e:e + 8])
        size = sizes.get(typ, 1) * cnt
        at = e + 8 if size <= 4 else struct.unpack(bo + 'I', data[e + 8:e + 12])[0]
        if typ in fmts and typ != 2:
            tags[tag] = struct.unpack(bo + str(cnt) + fmts[typ], data[at:at + size])
    w, h = tags[256][0], tags[257][0]
    bits = tags[258][0]
    comp = tags.get(259, (1,))[0]
    pred = tags.get(317, (1,))[0]
    fmt = tags.get(339, (1,))[0]
    dt = {(32, 3): 'f4', (16, 2): 'i2', (16, 1): 'u2', (32, 2): 'i4'}[(bits, fmt)]
    nb = bits // 8
    out = np.zeros((h, w), np.float32)

    def block(raw, bw, bh):
        if comp in (8, 32946):
            raw = zlib.decompress(raw)
        elif comp != 1:
            raise ValueError('compression %d not handled' % comp)
        a = np.frombuffer(raw, np.uint8)[:bw * bh * nb].reshape(bh, bw * nb)
        if pred == 3:
            a = np.cumsum(a, axis=1, dtype=np.uint8)               # the bytes of a row, differenced
            a = a.reshape(bh, nb, bw)[:, ::-1, :] if bo == '<' else a.reshape(bh, nb, bw)
            a = np.ascontiguousarray(a.transpose(0, 2, 1)).view(bo + dt).reshape(bh, bw)
            if bo == '<':
                pass
        else:
            a = np.ascontiguousarray(a).view(bo + dt).reshape(bh, bw)
            if pred == 2:
                a = np.cumsum(a, axis=1, dtype=a.dtype)
        return a.astype(np.float32)

    if 322 in tags:
        tw, th = tags[322][0], tags[323][0]
        offs, cnts = tags[324], tags[325]
        k = 0
        for ty in range(0, h, th):
            for tx in range(0, w, tw):
                if cnts[k]:                                 # (an empty tile holds nothing: sea)
                    a = block(data[offs[k]:offs[k] + cnts[k]], tw, th)
                    out[ty:ty + th, tx:tx + tw] = a[:min(th, h - ty), :min(tw, w - tx)]
                k += 1
    else:
        rps = tags.get(278, (h,))[0]
        offs, cnts = tags[273], tags[279]
        for k, y in enumerate(range(0, h, rps)):
            bh = min(rps, h - y)
            out[y:y + bh] = block(data[offs[k]:offs[k] + cnts[k]], w, bh)
    out[~np.isfinite(out) | (out < -500.0) | (out > 9000.0)] = 0.0       # 'no data' is the sea
    scale = tags[33550]
    tie = tags[33922]
    return out, tie[3] - tie[0] * scale[0], tie[4] + tie[1] * scale[1], scale[0], scale[1]


def tiles(src, work):
    """Every tile in `src`, each resampled to the same grid (one arc-second of latitude, two of longitude: what the
    model itself uses this far north), kept as a dictionary of tiles."""
    out = {}
    for name in sorted(os.listdir(src)):
        if not name.lower().endswith('.tif'):
            continue
        a, lon, lat, sx, sy = read_geotiff(os.path.join(src, name))
        key = '%+.0f_%+.0f' % (math.floor(lat - 0.5), math.floor(lon + 0.5))
        print(name, a.shape, 'top-left', round(lon, 5), round(lat, 5), 'pixel', sx, sy, 'max', float(a.max()), flush=True)
        out[key] = a
        out[key + '_geo'] = np.array([lon, lat, sx, sy])
    np.savez_compressed(os.path.join(work, 'dem.npz'), **out)


# ---- The land as a grid of metres ----------------------------------------------------------------------------------

class Land:
    """One region of the model as a metric grid: x east, y north, `cell` metres, row 0 the south edge."""

    def __init__(self, work, lat0, lat1, lon0, lon1, cell=30.0):
        d = np.load(os.path.join(work, 'dem.npz'))
        self.cell = cell
        self.lat_c, self.lon_c = 0.5 * (lat0 + lat1), 0.5 * (lon0 + lon1)
        self.my = math.pi / 180.0 * R_EARTH
        self.mx = self.my * math.cos(math.radians(self.lat_c))
        ny = int((lat1 - lat0) * self.my / cell)
        nx = int((lon1 - lon0) * self.mx / cell)
        lat = lat0 + (np.arange(ny) + 0.5) * cell / self.my
        lon = lon0 + (np.arange(nx) + 0.5) * cell / self.mx
        z = np.zeros((ny, nx), np.float32)
        for key in d.files:
            if key.endswith('_geo'):
                continue
            a = d[key]
            glon, glat, sx, sy = d[key + '_geo']
            fy = (glat - lat) / sy - 0.5                      # rows from the tile's top
            fx = (lon - glon) / sx - 0.5
            oky = (fy >= 0) & (fy <= a.shape[0] - 1)
            okx = (fx >= 0) & (fx <= a.shape[1] - 1)
            if not oky.any() or not okx.any():
                continue
            yy, xx = fy[oky], fx[okx]
            y0 = np.minimum(yy.astype(int), a.shape[0] - 2)
            x0 = np.minimum(xx.astype(int), a.shape[1] - 2)
            ty, tx = (yy - y0)[:, None], (xx - x0)[None, :]
            v = (a[np.ix_(y0, x0)] * (1 - ty) * (1 - tx) + a[np.ix_(y0, x0 + 1)] * (1 - ty) * tx
                 + a[np.ix_(y0 + 1, x0)] * ty * (1 - tx) + a[np.ix_(y0 + 1, x0 + 1)] * ty * tx)
            z[np.ix_(np.where(oky)[0], np.where(okx)[0])] = v
        self.z = z
        self.ny, self.nx = z.shape

    def at(self, x, y):
        """Heights at metric points (bilinear; the sea outside the grid)."""
        fx = np.clip(x / self.cell - 0.5, 0, self.nx - 1.001)
        fy = np.clip(y / self.cell - 0.5, 0, self.ny - 1.001)
        x0, y0 = fx.astype(np.int32), fy.astype(np.int32)
        tx, ty = fx - x0, fy - y0
        z = self.z
        v = (z[y0, x0] * (1 - ty) * (1 - tx) + z[y0, x0 + 1] * (1 - ty) * tx
             + z[y0 + 1, x0] * ty * (1 - tx) + z[y0 + 1, x0 + 1] * ty * tx)
        inside = (x > 0) & (y > 0) & (x < self.nx * self.cell) & (y < self.ny * self.cell)
        return np.where(inside, v, 0.0)

    def smooth(self, x, y):
        """Heights at metric points, smoothly interpolated (cubic B-spline: no creases along the cells)."""
        fx = np.clip(x / self.cell - 0.5, 1, self.nx - 3.001)
        fy = np.clip(y / self.cell - 0.5, 1, self.ny - 3.001)
        x0, y0 = np.floor(fx).astype(np.int32), np.floor(fy).astype(np.int32)
        tx, ty = (fx - x0).astype(np.float32), (fy - y0).astype(np.float32)

        def w(t):
            return [(1 - t) ** 3 / 6, (3 * t ** 3 - 6 * t ** 2 + 4) / 6, (-3 * t ** 3 + 3 * t ** 2 + 3 * t + 1) / 6, t ** 3 / 6]

        wx, wy = w(tx), w(ty)
        out = np.zeros(x.shape, np.float32)
        for j in range(4):
            row = sum(self.z[y0 - 1 + j, x0 - 1 + i] * wx[i] for i in range(4))
            out += row * wy[j]
        inside = (x > 0) & (y > 0) & (x < self.nx * self.cell) & (y < self.ny * self.cell)
        return np.where(inside, out, 0.0)


# ---- The fine model of Norway (Kartverket's national height model, see sources/arctic.md) -------------------------

def utm33(lat, lon):
    """Latitude and longitude (degrees, arrays) to ETRS89 / UTM zone 33N metres (east, north)."""
    a, f, k0 = 6378137.0, 1 / 298.257222101, 0.9996
    n = f / (2 - f)
    A = a / (1 + n) * (1 + n * n / 4 + n ** 4 / 64)
    al = (n / 2 - 2 * n * n / 3 + 5 * n ** 3 / 16, 13 * n * n / 48 - 3 * n ** 3 / 5, 61 * n ** 3 / 240)
    phi, dl = np.radians(lat), np.radians(np.asarray(lon) - 15.0)
    c = 2 * math.sqrt(n) / (1 + n)
    t = np.sinh(np.arctanh(np.sin(phi)) - c * np.arctanh(c * np.sin(phi)))
    xi = np.arctan2(t, np.cos(dl))
    eta = np.arctanh(np.sin(dl) / np.sqrt(1 + t * t))
    e, nn = eta.copy(), xi.copy()
    for j in (1, 2, 3):
        e = e + al[j - 1] * np.cos(2 * j * xi) * np.sinh(2 * j * eta)
        nn = nn + al[j - 1] * np.sin(2 * j * xi) * np.cosh(2 * j * eta)
    return 500000.0 + k0 * A * e, k0 * A * nn


NHM = 'https://wcs.geonorge.no/skwms1/wcs.hoyde-dtm-nhm-25833'
NHM_TILE = 10000.0        # metres: the squares the fine model is fetched in
NHM_CELL = 5.0


def fetch_nhm(dst, e0, n0, e1, n1):
    """Fetches (once) the squares of the fine model that cover a box of UTM 33 metres. Returns their names."""
    import urllib.request
    os.makedirs(dst, exist_ok=True)
    out = []
    px = int(NHM_TILE / NHM_CELL)
    for te in range(int(e0 // NHM_TILE), int(e1 // NHM_TILE) + 1):
        for tn in range(int(n0 // NHM_TILE), int(n1 // NHM_TILE) + 1):
            name = os.path.join(dst, 'nhm%d_%d_%d.tif' % (int(NHM_CELL), te, tn))
            if not os.path.exists(name):
                url = ('%s?SERVICE=WCS&VERSION=1.0.0&REQUEST=GetCoverage&COVERAGE=nhm_dtm_topo_25833&CRS=EPSG:25833'
                       '&BBOX=%d,%d,%d,%d&WIDTH=%d&HEIGHT=%d&FORMAT=GeoTIFF'
                       % (NHM, te * NHM_TILE, tn * NHM_TILE, (te + 1) * NHM_TILE, (tn + 1) * NHM_TILE, px, px))
                print('fetching', os.path.basename(name), flush=True)
                with urllib.request.urlopen(url, timeout=600) as r:
                    body = r.read()
                if body[:2] not in (b'II', b'MM'):
                    raise IOError('the height service answered: ' + body[:300].decode('utf-8', 'replace'))
                with open(name, 'wb') as f:
                    f.write(body)
            out.append(name)
    return out


class Fine:
    """The fine model's squares that have been fetched, as one sparse grid."""

    def __init__(self, src):
        self.tiles = {}
        for name in os.listdir(src) if os.path.isdir(src) else []:
            if name.startswith('nhm%d_' % int(NHM_CELL)) and name.endswith('.tif'):
                te, tn = (int(v) for v in name[:-4].split('_')[1:])
                self.tiles[(te, tn)] = os.path.join(src, name)
        self.data = {}

    def tile(self, key):
        if key not in self.data:
            a, _, _, _, _ = read_geotiff(self.tiles[key])
            a = a[::-1].copy()                                   # row 0 the south edge
            # Where the height model was drawn from contour lines (most of these slopes) it is terraced: every
            # contour a step, which low light shows as stripes along the hillside. The even slopes are eased out
            # over some fifteen metres; cliffs and ridges (steeper than 45 degrees or so) are left as they are, and
            # so is the shoreline.
            land = a > 0.05

            def soft(v, times):
                for _ in range(times):
                    q = np.pad(v, 2, mode='edge')
                    v = (q[:-4, 2:-2] + q[4:, 2:-2] + 4 * (q[1:-3, 2:-2] + q[3:-1, 2:-2]) + 6 * q[2:-2, 2:-2]) / 16.0
                    q = np.pad(v, 2, mode='edge')
                    v = (q[2:-2, :-4] + q[2:-2, 4:] + 4 * (q[2:-2, 1:-3] + q[2:-2, 3:-1]) + 6 * q[2:-2, 2:-2]) / 16.0
                return v

            b = soft(a, 4)
            gy, gx = np.gradient(soft(a, 1), NHM_CELL)
            t = np.clip((np.hypot(gx, gy) - 0.75) / 0.45, 0.0, 1.0)
            w = 1.0 - t * t * (3 - 2 * t)
            a = np.where(land, a * (1 - w) + b * w, a)
            self.data[key] = a.astype(np.float32)
        return self.data[key]

    def at(self, e, n):
        """Heights at UTM points (cubic B-spline), and where the model has them (elsewhere 0, False)."""
        out = np.zeros(e.shape, np.float32)
        have = np.zeros(e.shape, bool)
        te, tn = np.floor(e / NHM_TILE).astype(int), np.floor(n / NHM_TILE).astype(int)
        px = int(NHM_TILE / NHM_CELL)

        def w(t):
            return [(1 - t) ** 3 / 6, (3 * t ** 3 - 6 * t ** 2 + 4) / 6, (-3 * t ** 3 + 3 * t ** 2 + 3 * t + 1) / 6, t ** 3 / 6]

        for key in set(zip(te.ravel().tolist(), tn.ravel().tolist())):
            if key not in self.tiles:
                continue
            a = self.tile(key)
            m = (te == key[0]) & (tn == key[1])
            fx = np.clip((e[m] - key[0] * NHM_TILE) / NHM_CELL - 0.5, 1, px - 3.001)
            fy = np.clip((n[m] - key[1] * NHM_TILE) / NHM_CELL - 0.5, 1, px - 3.001)
            x0, y0 = np.floor(fx).astype(np.int32), np.floor(fy).astype(np.int32)
            wx, wy = w((fx - x0).astype(np.float32)), w((fy - y0).astype(np.float32))
            v = np.zeros(fx.shape, np.float32)
            for j in range(4):
                v += sum(a[y0 - 1 + j, x0 - 1 + i] * wx[i] for i in range(4)) * wy[j]
            out[m] = v
            have[m] = True
        return out, have


def region_utm(region, x, y):
    """A region's metres to UTM 33."""
    lat0, _, lon0, _ = REGIONS[region]
    my = math.pi / 180.0 * R_EARTH
    mx = my * math.cos(math.radians(0.5 * (REGIONS[region][0] + REGIONS[region][1])))
    return utm33(lat0 + y / my, lon0 + x / mx)


def fetch_sector(dst, region, x, y, bearing, mirror, az, near, reach):
    """Fetches the fine model under one fan of a view (see arctic.py), out to `reach` metres."""
    a = np.radians(bearing + mirror * np.linspace(az[0] - 0.5, az[1] + 0.5, 12))
    r = np.array([near * 0.7, reach * 0.5, reach * 1.03])
    e, n = region_utm(region, x + np.sin(a)[:, None] * r[None, :], y + np.cos(a)[:, None] * r[None, :])
    return fetch_nhm(dst, e.min() - 100, n.min() - 100, e.max() + 100, n.max() + 100)


REGIONS = {
    'lofoten': (67.75, 68.75, 12.9, 16.0),
    'senja': (69.0, 69.75, 16.6, 18.0),
}

RISE = 14.7
ARC_TOP = 14.2


def limit(az):
    """bake.skyline_limit, for arrays (degrees)."""
    a = np.abs(az)
    x = np.tan(np.radians(np.minimum(a, 89.0))) / math.tan(math.radians(RISE))
    inner = np.maximum(0.3, ARC_TOP * np.cos(0.5 * math.pi * np.minimum(x, 1.0)) - 2.5)
    return np.where(a > RISE + 1.8, 90.0, np.where(a > RISE - 1.8, 0.3, inner))


def panorama(land, ex, ey, eye, step=0.5, reach=70000.0, n=220):
    """All round a place: for every bearing (degrees clockwise from north, `step` apart) how high the skyline
    stands (degrees), how far the skyline's land is, and how far the first land is (metres)."""
    b = np.radians(np.arange(0.0, 360.0, step))
    t = np.geomspace(60.0, reach, n)
    x = ex + np.sin(b)[:, None] * t[None, :]
    y = ey + np.cos(b)[:, None] * t[None, :]
    h = land.at(x, y)
    drop = t ** 2 / (2 * R_EARTH) * REFRACT
    el = np.degrees(np.arctan2(h - drop[None, :] - eye, t[None, :]))
    el = np.where(h > 0.5, el, -90.0)
    k = np.argmax(el, axis=1)
    sky = el[np.arange(len(b)), k]
    far = t[k]
    isl = h > 0.5
    first = np.where(isl.any(axis=1), t[np.argmax(isl, axis=1)], reach)
    return sky, far, first


def panoramas(work, region, count=5000):
    """The skylines all round `count` places on the shores of a region (kept: it takes minutes)."""
    keep = os.path.join(work, 'pano_%s.npz' % region)
    if os.path.exists(keep):
        d = np.load(keep)
        return d['pts'], d['sky'], d['far'], d['first']
    land = Land(work, *REGIONS[region])
    z = land.z
    sea = z <= 0.5
    near_sea = np.zeros_like(sea)
    for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        near_sea |= np.roll(sea, (dy, dx), axis=(0, 1))
    ys, xs = np.nonzero((~sea) & (z < 12.0) & near_sea)
    rng = np.random.default_rng(3)
    pick = rng.choice(len(xs), size=min(len(xs), count), replace=False)
    pts = np.stack([(xs[pick] + 0.5) * land.cell, (ys[pick] + 0.5) * land.cell], -1).astype(np.float32)
    sky = np.zeros((len(pick), 720), np.float32)
    far = np.zeros_like(sky)
    first = np.zeros_like(sky)
    for k in range(len(pick)):
        sky[k], far[k], first[k] = panorama(land, float(pts[k, 0]), float(pts[k, 1]), 2.2, 0.5)
    np.savez(keep, pts=pts, sky=sky, far=far, first=first)
    return pts, sky, far, first


def wall(work, region='lofoten', count=16):
    """Real places to stand, best first: on a shore, looking across water at a wall of peaks that runs unbroken
    from one edge of the picture to the other, rank behind rank, as the land itself has it (nothing is moved or
    cut). The middle of the view stays under about eleven degrees, so the Sun is not hidden for most of its day."""
    pts, sky, far, first = panoramas(work, region)
    step = 0.5
    n = sky.shape[1]
    az = np.arange(-23.5, 23.51, step)
    mid = np.abs(az) < 12.0
    front = np.abs(az) < 17.0
    idx = np.arange(n)
    win = (idx[:, None] + np.round(az / step).astype(int)[None, :]) % n
    found = []
    for k in range(len(pts)):
        s_, f_, fl = sky[k][win], far[k][win], first[k][win]
        whole = (s_ > 1.2).mean(axis=1)                               # how much of the view the wall fills
        lowest = np.sort(s_, axis=1)[:, 4]                            # (its lowest, but for a notch or two)
        peak_mid = s_[:, mid].max(axis=1)
        peak = s_.max(axis=1)
        water = fl[:, front].min(axis=1)
        mean = s_.mean(axis=1)
        rough = np.abs(np.diff(s_, axis=1)).mean(axis=1)
        ranks = np.log(np.maximum(f_, 100.0)).std(axis=1)             # peaks near and far, not one ridge
        ok = (whole > 0.93) & (lowest > 0.8) & (peak_mid < 11.3) & (peak_mid > 5.5) & (water > 700.0) & (fl.min(axis=1) > 250.0) & (peak < 24.0)
        sc = mean / 6.0 + np.minimum(rough, 0.8) * 1.2 + np.minimum(ranks, 0.9) * 1.3 + np.minimum(lowest, 3.0) * 0.25 + np.minimum(peak_mid, 10.0) * 0.08
        sc = np.where(ok, sc, -9.0)
        b = int(np.argmax(sc))
        if sc[b] > -9.0:
            found.append((float(sc[b]), k, b * step, dict(mean=round(float(mean[b]), 1), lowest=round(float(lowest[b]), 1),
                          peak_mid=round(float(peak_mid[b]), 1), peak=round(float(peak[b]), 1), rough=round(float(rough[b]), 2),
                          ranks=round(float(ranks[b]), 2), water=round(float(water[b])))))
    found.sort(key=lambda c: -c[0])
    out = []
    for c in found:
        if all(math.hypot(*(pts[c[1]] - pts[o[1]])) > 1500.0 for o in out):
            out.append(c)
        if len(out) == count:
            break
    res = [dict(score=c[0], x=float(pts[c[1], 0]), y=float(pts[c[1], 1]), bearing=c[2], **c[3]) for c in out]
    name = os.path.join(work, 'stands_%s.json' % region)
    d = json.load(open(name)) if os.path.exists(name) else {'region': region}
    d['wall'] = res
    json.dump(d, open(name, 'w'), indent=1)
    lat0, _, lon0, _ = REGIONS[region]
    my = math.pi / 180.0 * R_EARTH
    mx = my * math.cos(math.radians(0.5 * (REGIONS[region][0] + REGIONS[region][1])))
    for i, c in enumerate(res):
        print('wall', i, 'lat %.4f lon %.4f' % (lat0 + c['y'] / my, lon0 + c['x'] / mx), {k: (round(v, 2) if isinstance(v, float) else v) for k, v in c.items()}, flush=True)


# ---- Pictures without a renderer -----------------------------------------------------------------------------------

def write_png(path, rgb):
    """An 8-bit RGB picture (h, w, 3) as a PNG."""
    h, w, _ = rgb.shape
    raw = b''.join(b'\x00' + rgb[y].tobytes() for y in range(h))

    def chunk(tag, body):
        return struct.pack('>I', len(body)) + tag + body + struct.pack('>I', zlib.crc32(tag + body) & 0xffffffff)

    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))


def view(land, ex, ey, yaw, eye=2.2, w=432, h=250, horizon=200, focal=1.15, sun=(-40.0, 20.0), reach=70000.0):
    """What a camera standing at (ex, ey), looking towards bearing `yaw`, sees: a shaded drawing (distance as haze,
    slopes lit from `sun` = bearing in the view, height), with the skyline's limit drawn in red. For choosing."""
    px = (np.arange(w) + 0.5 - w / 2) / (focal * w)
    az = np.degrees(np.arctan(px))
    b = np.radians(yaw + az)
    t = np.geomspace(40.0, reach, 700)
    x = ex + np.sin(b)[:, None] * t[None, :]
    y = ey + np.cos(b)[:, None] * t[None, :]
    z = land.at(x, y)
    e = 12.0
    gx = (land.at(x + e, y) - land.at(x - e, y)) / (2 * e)
    gy = (land.at(x, y + e) - land.at(x, y - e)) / (2 * e)
    drop = t ** 2 / (2 * R_EARTH) * REFRACT
    # rows of the picture each sample projects to (perspective: the row goes with the tangent along the view's axis)
    fwd = t[None, :] * np.cos(np.radians(az))[:, None]
    row = horizon - (z - drop[None, :] - eye) / fwd * (focal * w)
    sb = math.radians(yaw + sun[0])
    sl = np.array([math.sin(sb) * math.cos(math.radians(sun[1])), math.cos(sb) * math.cos(math.radians(sun[1])), math.sin(math.radians(sun[1]))])
    nrm = np.stack([-gx, -gy, np.ones_like(gx)], -1)
    nrm /= np.linalg.norm(nrm, axis=-1, keepdims=True)
    lit = np.clip(nrm @ sl, 0, 1)
    steep = np.hypot(gx, gy)
    img = np.zeros((h, w, 3), np.float32)
    img[:horizon] = np.array([0.55, 0.66, 0.82])
    img[horizon:] = np.array([0.10, 0.16, 0.24])
    top = np.full(w, float(h))
    for k in range(len(t)):                                     # near to far: nearer land hides farther
        r = row[:, k]
        isl = z[:, k] > 0.5
        snow = np.where(steep[:, k] > 0.95, 0.25, 0.92)
        shade = (0.25 + 0.75 * lit[:, k]) * snow
        fog = 1 - math.exp(-t[k] / 45000.0)
        col = shade[:, None] * np.array([1.0, 1.0, 1.02]) * (1 - fog) + np.array([0.62, 0.72, 0.86]) * fog
        r0 = np.clip(np.floor(r).astype(int), 0, h)
        for c in np.nonzero(isl & (r0 < top))[0]:
            a, bb = r0[c], int(top[c])
            if bb >= h:                                             # the first land of this column: down to its foot
                bb = min(h, int(horizon + eye / fwd[c, k] * (focal * w)) + 1)
            img[a:bb, c] = col[c]
            top[c] = a
    lim_row = horizon - np.tan(np.radians(limit(az))) * (focal * w) / np.cos(np.radians(az)) * 0 - np.tan(np.radians(np.minimum(limit(az), 60))) * (focal * w)
    for c in range(w):
        rr = int(lim_row[c])
        if 0 <= rr < h:
            img[rr, c] = np.array([1.0, 0.1, 0.1])
    return (np.clip(img, 0, 1) * 255).astype(np.uint8)


def show(work, region, which):
    """Drawings of the places `wall` found, side by side."""
    land = Land(work, *REGIONS[region])
    st = json.load(open(os.path.join(work, 'stands_%s.json' % region)))['wall']
    row = []
    for n in which:
        img = view(land, st[n]['x'], st[n]['y'], st[n]['bearing'])
        row += [img, np.zeros((img.shape[0], 6, 3), np.uint8)]
    write_png(os.path.join(work, 'views_%s_wall.png' % region), np.concatenate(row[:-1], axis=1))


if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'tiles':
        tiles(sys.argv[2], sys.argv[3])
    elif mode == 'wall':
        wall(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else 'lofoten')
    elif mode == 'show':
        show(sys.argv[2], sys.argv[3], [int(v) for v in sys.argv[4:]])
