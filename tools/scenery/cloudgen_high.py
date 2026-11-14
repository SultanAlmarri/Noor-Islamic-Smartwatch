"""The middle and high clouds as volumes for the path tracer: thin layers many tens of kilometres wide.

A layer is a pattern seen from above (how thick the cloud is over every place, how dense) given real thickness and
laid over the round Earth, so that it sinks to the horizon in perspective as it does in life. The pattern is a
function of the place alone (hashed cells and noise, or fibres drawn as curves), so it can be sampled finely near
the camera and coarsely far away and still be one sky: the layer is written as a few OpenVDB grids, one per band of
distance, each with voxels about as large as a pixel sees there.

Only numpy and Blender's own `openvdb` module are used.
"""
import math

import numpy as np

EARTH = 6371000.0
HALF_ANGLE = math.radians(31.5)        # the picture (PAN times the phone's view) sees 28.5 degrees to either side


# ---- Noise that is a function of the place ----------------------------------------------------------------------

def _hash(ix, iy, seed):
    """A number 0..1 for every lattice point (arrays of integers), the same at any resolution."""
    with np.errstate(over='ignore'):
        h = (ix.astype(np.int64).astype(np.uint64) * np.uint64(0x9E3779B97F4A7C15)
             ^ iy.astype(np.int64).astype(np.uint64) * np.uint64(0xC2B2AE3D27D4EB4F)
             ^ np.uint64((seed * 0x165667B19E3779F9 + 0x27D4EB2F165667C5) & 0xFFFFFFFFFFFFFFFF))
        h ^= h >> np.uint64(29)
        h *= np.uint64(0xBF58476D1CE4E5B9)
        h ^= h >> np.uint64(32)
        h *= np.uint64(0x94D049BB133111EB)
        h ^= h >> np.uint64(29)
    return (h >> np.uint64(40)).astype(np.float32) / np.float32(1 << 24)


def vnoise(x, y, scale, seed):
    """Smooth noise 0..1 with features `scale` metres across."""
    gx, gy = x / scale, y / scale
    ix, iy = np.floor(gx).astype(np.int64), np.floor(gy).astype(np.int64)
    fx, fy = (gx - ix).astype(np.float32), (gy - iy).astype(np.float32)
    ux, uy = fx * fx * fx * (fx * (fx * 6 - 15) + 10), fy * fy * fy * (fy * (fy * 6 - 15) + 10)
    a, b = _hash(ix, iy, seed), _hash(ix + 1, iy, seed)
    c, d = _hash(ix, iy + 1, seed), _hash(ix + 1, iy + 1, seed)
    return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy


def fbm(x, y, scale, octaves, seed, gain=0.5):
    """Noise in several sizes, 0..1 (crowding round 0.5)."""
    out = np.zeros(np.broadcast(x, y).shape, np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += amp * vnoise(x, y, scale, seed + 101 * o)
        tot += amp
        amp *= gain
        scale /= 2.03
    return out / tot


def worley(x, y, cell, seed, jitter=0.92):
    """Cells: distance to the nearest and to the second nearest cell point (in cells), and a random number 0..1
    that belongs to the nearest."""
    gx, gy = x / cell, y / cell
    ix, iy = np.floor(gx).astype(np.int64), np.floor(gy).astype(np.int64)
    fx, fy = (gx - ix).astype(np.float32), (gy - iy).astype(np.float32)
    f1 = np.full(fx.shape, 9.0, np.float32)
    f2 = np.full(fx.shape, 9.0, np.float32)
    ident = np.zeros(fx.shape, np.float32)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            px = dx + 0.5 + (_hash(ix + dx, iy + dy, seed) - 0.5) * jitter
            py = dy + 0.5 + (_hash(ix + dx, iy + dy, seed + 7) - 0.5) * jitter
            d = (px - fx) ** 2 + (py - fy) ** 2
            nearer = d < f1
            f2 = np.where(nearer, f1, np.minimum(f2, d))
            ident = np.where(nearer, _hash(ix + dx, iy + dy, seed + 13), ident)
            f1 = np.where(nearer, d, f1)
    return np.sqrt(f1), np.sqrt(f2), ident


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def blur(a, sx, sy):
    """Gaussian blur of a picture by sx, sy pixels (its sum is kept; edges wrap, so leave a margin)."""
    if sx <= 0.05 and sy <= 0.05:
        return a
    f = np.fft.rfft2(a)
    ky = np.fft.fftfreq(a.shape[0])[:, None]
    kx = np.fft.rfftfreq(a.shape[1])[None, :]
    f *= np.exp(-2.0 * math.pi ** 2 * ((kx * sx) ** 2 + (ky * sy) ** 2))
    return np.fft.irfft2(f, s=a.shape).astype(np.float32)


# ---- The layer --------------------------------------------------------------------------------------------------

def bands(near, far, first_voxel, growth=2.0):
    """Bands of distance (metres along the view) from `near` to `far`, and the voxel each is sampled with: about as
    large as a pixel sees there. Returns [(y0, y1, voxel)]."""
    out = []
    y0, v = near, first_voxel
    while y0 < far:
        y1 = min(y0 * growth + 2000.0, far)
        out.append((y0, y1, v))
        y0, v = y1, v * growth
    return out


def band_fields(pattern, near, far, first_voxel, fade=(0.62, 1.0), overlap=0.08, growth=2.0):
    """The layer sampled band by band: yields (x of the first column's edge, y of the first row's edge, voxel,
    X, Y, base, thickness, density) for each band of distance, the density already cross-faded with the neighbouring
    bands, cut to the picture's wedge and thinned out towards `far`."""
    bs = bands(near, far, first_voxel, growth)
    for n, (y0, y1, v) in enumerate(bs):
        lo = y0 * (1 - overlap) if n > 0 else y0
        hi = y1 * (1 + overlap) if n < len(bs) - 1 else y1
        half = hi * math.tan(HALF_ANGLE) + 4 * v
        nx, ny = int(2 * half / v), int((hi - lo) / v)
        xs = (np.arange(nx, dtype=np.float32) + 0.5) * v - half
        ys = (np.arange(ny, dtype=np.float32) + 0.5) * v + lo
        X, Y = np.meshgrid(xs, ys)                                   # (ny, nx)
        base, thick, dens = pattern(X, Y, v)
        r = np.hypot(X, Y)
        w = smooth(0.0, 3 * v, Y * math.tan(HALF_ANGLE) + 3 * v - np.abs(X))
        if n > 0:
            w *= smooth(lo, y0 * (1 + overlap), Y)
        if n < len(bs) - 1:
            w *= 1.0 - smooth(y1 * (1 - overlap), hi, Y)
        w *= 1.0 - smooth(fade[0] * far, fade[1] * far, r)
        dens = (dens * w).astype(np.float32)
        thick = np.where(dens > 1e-4, thick, 0.0).astype(np.float32)
        yield -half, lo, v, X, Y, base.astype(np.float32), thick, dens


def write_layer(prefix, pattern, altitude, near, far, first_voxel, vz_of=None, fade=(0.62, 1.0), overlap=0.08,
                growth=2.0):
    """Writes a layer as one VDB per band of distance; returns their paths.

    `pattern(x, y, voxel)` gives, for arrays of places (metres; the camera at the origin looking along +y) sampled
    `voxel` apart: the cloud's base (metres above `altitude`), its thickness (metres) and its density (0..1 and
    more) there. `fade`: the layer thins out to nothing between these fractions of `far`, so that the horizon
    never closes into a solid band. Bands overlap a little and cross-fade.
    """
    import openvdb
    paths = []
    for n, (x0, lo, v, X, Y, base, thick, dens) in enumerate(band_fields(pattern, near, far, first_voxel, fade, overlap, growth)):
        ny, nx = X.shape
        r = np.hypot(X, Y)
        zb = (altitude + base - r * r / (2.0 * EARTH)).astype(np.float32)   # over the round Earth
        zt = zb + thick
        vz = vz_of(v) if vz_of else max(v * 0.5, 20.0)
        has = thick > 0
        if not has.any():
            continue
        z0 = float(zb[has].min()) - vz
        nz = int(math.ceil((float(zt[has].max()) + vz - z0) / vz))
        arr = np.zeros((nx, ny, nz), np.float32)
        for k in range(nz):
            a, b = z0 + k * vz, z0 + (k + 1) * vz
            cov = np.clip((np.minimum(zt, b) - np.maximum(zb, a)) / vz, 0.0, 1.0) * dens   # how much of the voxel is cloud
            arr[:, :, k] = cov.T
        g = openvdb.FloatGrid()
        g.copyFromArray(arr, tolerance=0.0015)
        g.transform = openvdb.createLinearTransform([[v, 0, 0, 0], [0, v, 0, 0], [0, 0, vz, 0],
                                                     [float(x0), float(lo), z0, 1]])
        g.name = 'density'
        g.gridClass = openvdb.GridClass.FOG_VOLUME
        path = '%s_%d.vdb' % (prefix, n)
        openvdb.write(path, grids=[g])
        print('band %d: from %.0f km, voxel %.0f x %.0f m, %d x %d x %d, filled %.2f' % (
            n, lo / 1000, v, vz, nx, ny, nz, float((arr > 0).mean())), flush=True)
        paths.append(path)
        del arr
    return paths


def plan_view(pattern, altitude, near, far, first_voxel, sigma, fade=(0.62, 1.0), growth=2.0, scale=0.5,
              width=1350, rows=1080, horizon=1056, focal=1242.0):
    """A quick look at a layer without the path tracer: how opaque it is from the camera for every pixel of the
    picture (rows x width at `scale`), sampled from the very grids the volumes are made of. Returns (opacity,
    optical depth looking straight up), both rows x width."""
    w, h = int(width * scale), int(rows * scale)
    px = (np.arange(w, dtype=np.float32) + 0.5) / scale - width / 2
    py = horizon - (np.arange(h, dtype=np.float32) + 0.5) / scale
    PX, PY = np.meshgrid(px, py)
    az = np.arctan2(PX, focal)
    te = PY / np.hypot(PX, focal)                                    # tangent of the elevation
    r = EARTH * (-te + np.sqrt(te * te + 2.0 * altitude / EARTH))    # where the ray meets the layer
    r = np.where(PY > 0, r, 1e9)
    x, y = r * np.sin(az), r * np.cos(az)
    tau_v = np.zeros((h, w), np.float32)
    for x0, lo, v, X, Y, base, thick, dens in band_fields(pattern, near, far, first_voxel, fade, growth=growth):
        ny, nx = X.shape
        ix = np.floor((x - x0) / v).astype(np.int64)
        iy = np.floor((y - lo) / v).astype(np.int64)
        ok = (ix >= 0) & (ix < nx) & (iy >= 0) & (iy < ny)
        t = (thick * dens * sigma)
        tau_v[ok] += t[iy[ok], ix[ok]]
    slope = te + r / EARTH                                           # the ray against the (curved) layer
    sin_e = np.clip(slope / np.sqrt(1.0 + slope * slope), 0.01, 1.0)
    return 1.0 - np.exp(-tau_v / sin_e), tau_v


# ---- Fibres: cirrus drawn as curves ------------------------------------------------------------------------------

def fibre_points(rng, head, heading, length, spread, count, hook, step):
    """The points of one bundle of fibres. It starts in a tuft at its head (x, y), swings round in a hook and trails
    away along `heading` (radians from +x), its fibres fanning out over `spread` metres. Returns x, y and the weight
    of every point (how much ice it stands for), sampled `step` metres apart."""
    hx, hy = head
    L = float(length)
    n = int(count)
    s = np.arange(0.0, L, step, dtype=np.float32)[None, :]
    # the bundle's own slow wander, shared by its fibres
    ph = rng.uniform(0, 6.28, 3)
    wander = 0.10 * np.sin(s / (L * 0.45) + ph[0]) + 0.05 * np.sin(s / (L * 0.17) + ph[1])
    u = rng.normal(0.0, 0.33, (n, 1)).astype(np.float32)             # where across the bundle each fibre runs
    ln = (L * rng.uniform(0.30, 1.0, (n, 1))).astype(np.float32)
    start = (rng.uniform(0.0, 0.30, (n, 1)) * L).astype(np.float32)
    beat = rng.uniform(900.0, 2600.0, (n, 1)).astype(np.float32)
    phase = rng.uniform(0, 6.28, (n, 1)).astype(np.float32)
    gain = np.minimum(np.exp(rng.normal(-0.5, 0.7, (n, 1))), 2.2).astype(np.float32)    # a few strong fibres, many faint
    # the hook: a fibre leaves the head across the wind and turns into it
    turn = hook * (1.0 + 0.3 * u) * np.exp(-s / (0.07 * L + 600.0))
    ang = heading + wander + turn + 0.05 * u
    px = hx + np.cumsum(np.cos(ang) * step, axis=1) - math.sin(heading) * u * spread * (0.25 + 0.75 * s / L)
    py = hy + np.cumsum(np.sin(ang) * step, axis=1) + math.cos(heading) * u * spread * (0.25 + 0.75 * s / L)
    t = (s - start) / ln
    w = np.minimum(np.clip(t, 0.0, 1.0) / 0.04, 1.0) * np.clip(1.0 - t, 0.0, 1.0) ** 1.6
    # a fibre is brighter in places along its length
    w = w * (0.30 + 0.70 * np.sin(s / beat + phase) ** 2) * gain
    keep = w > 1e-3
    return px[keep].astype(np.float32), py[keep].astype(np.float32), (w[keep] * step).astype(np.float32)


def splat(px, py, w, X, Y, v):
    """Adds points to a picture whose pixel centres are X, Y (a regular grid `v` apart): the amount per square
    metre that the points stand for (bilinear)."""
    ny, nx = X.shape
    gx = (px - (X[0, 0] - 0.5 * v)) / v - 0.5
    gy = (py - (Y[0, 0] - 0.5 * v)) / v - 0.5
    ok = (gx >= 0) & (gx < nx - 1) & (gy >= 0) & (gy < ny - 1)
    gx, gy, w = gx[ok], gy[ok], w[ok]
    ix, iy = gx.astype(np.int64), gy.astype(np.int64)
    fx, fy = gx - ix, gy - iy
    out = np.zeros(ny * nx, np.float64)
    for ddx, ddy, ww in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)), (0, 1, (1 - fx) * fy), (1, 1, fx * fy)):
        out += np.bincount((iy + ddy) * nx + ix + ddx, weights=w * ww, minlength=ny * nx)
    return (out.reshape(ny, nx) / (v * v)).astype(np.float32)


# ---- Puffs: small heaps from modelled clouds -----------------------------------------------------------------------

def reduce_vdb(src, dst, across=72):
    """A modelled cloud (a VDB with a float grid `density`, y up) made ready to be set out by the hundred as the
    cloudlets of a layer: turned z up, its densities brought to 0..1, sampled down to `across` voxels along its
    longer side, one unit wide, its middle over the origin and its base on z = 0. Returns (width, depth, height)
    in units of its width."""
    import openvdb
    g = openvdb.read(src, 'density')
    lo, hi = g.evalActiveVoxelBoundingBox()
    dims = [hi[i] - lo[i] + 1 for i in range(3)]
    arr = np.zeros(dims, np.float32)
    g.copyToArray(arr, ijk=lo)
    arr = np.ascontiguousarray(arr.transpose(0, 2, 1)[:, ::-1, :])            # (x, y up, z) -> (x, -z, y up)
    top = float(np.percentile(arr[arr > 0], 97.0))
    arr = np.clip(arr / top, 0.0, 1.0)
    # cut the empty margins away
    for ax in range(3):
        other = tuple(a for a in range(3) if a != ax)
        m = np.nonzero(arr.max(axis=other) > 0.02)[0]
        arr = np.take(arr, np.arange(m[0], m[-1] + 1), axis=ax)
    f = max(1, int(math.ceil(max(arr.shape[0], arr.shape[1]) / across)))
    pad = [(0, (-n) % f) for n in arr.shape]
    arr = np.pad(arr, pad)
    nx, ny, nz = (n // f for n in arr.shape)
    arr = arr.reshape(nx, f, ny, f, nz, f).mean(axis=(1, 3, 5))
    v = 1.0 / max(nx, ny)
    out = openvdb.FloatGrid()
    out.copyFromArray(np.ascontiguousarray(arr), tolerance=0.004)
    out.transform = openvdb.createLinearTransform([[v, 0, 0, 0], [0, v, 0, 0], [0, 0, v, 0],
                                                   [-0.5 * nx * v, -0.5 * ny * v, 0.0, 1]])
    out.name = 'density'
    out.gridClass = openvdb.GridClass.FOG_VOLUME
    openvdb.write(dst, grids=[out])
    return nx * v, ny * v, nz * v


def rank_points(seed, cell, long, turn, near, far, jitter=0.4, bend=6500.0, stagger=0.5):
    """Where the cloudlets of a layer stand: a lattice of ranks (`cell` metres from rank to rank, `long` times
    that from one cloudlet to the next along a rank, the ranks lying `turn` degrees off the x axis), every point a
    little out of place and the whole bent gently across the sky; only the picture's wedge between `near` and `far`.
    `stagger`: how far every second cloudlet of a rank stands out of it, in cells (0.5: no ranks but a honeycomb;
    0: clean ranks, for rolls).
    Returns x, y and three random numbers 0..1 for each point."""
    a = math.radians(turn)
    reach = far / math.cos(HALF_ANGLE) + bend
    nu, nw = int(2 * reach / cell) + 2, int(2 * reach / (long * cell)) + 2
    I, J = np.meshgrid(np.arange(nu) - nu // 2, np.arange(nw) - nw // 2, indexing='ij')
    r1, r2, r3 = _hash(I, J, seed + 1), _hash(I, J, seed + 2), _hash(I, J, seed + 3)
    u = (I + (_hash(I, J, seed + 4) - 0.5) * jitter + stagger * (J % 2)) * cell
    w = (J + (_hash(I, J, seed + 5) - 0.5) * jitter) * long * cell
    x = u * math.cos(a) - w * math.sin(a)
    y = u * math.sin(a) + w * math.cos(a)
    xf, yf = x.astype(np.float32), y.astype(np.float32)
    x = x + (fbm(xf, yf, 13000.0, 3, seed + 6) - 0.5) * bend
    y = y + (fbm(xf, yf, 13000.0, 3, seed + 7) - 0.5) * bend
    ok = (y > near) & (np.hypot(x, y) < far) & (np.abs(x) < y * math.tan(HALF_ANGLE) + 2 * cell)
    return x[ok].astype(np.float32), y[ok].astype(np.float32), r1[ok], r2[ok], r3[ok]
