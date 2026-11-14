"""Clouds as volumes for the path tracer, written as OpenVDB grids.

Heaped clouds (cumulus) are built the way they grow and the way the effects trade models them: bubble on bubble (a
body of large lumps on a flat base, towers of stacked bubbles, and heads on heads on every upper side), the whole
surface then pushed out into cauliflower by cells at falling sizes ("pyroclastic" displacement). Parts of every
cloud are let go soft and ragged, as the evaporating side of a real one is.

Layers (stratocumulus, stratus) are built from maps of where their base and their top lie.

Only numpy and Blender's own `openvdb` module are used.
"""
import math

import numpy as np


# ---- Noise ------------------------------------------------------------------------------------------------------

def cells(shape, voxel, cell, rng, origin=(0.0, 0.0, 0.0)):
    """Distance (in cells) from every voxel of a grid (z, y, x; `voxel` metres) to the nearest of a set of points
    scattered one to a cell of `cell` metres."""
    nz, ny, nx = shape
    z = (np.arange(nz, dtype=np.float32) * voxel + origin[2]) / cell
    y = (np.arange(ny, dtype=np.float32) * voxel + origin[1]) / cell
    x = (np.arange(nx, dtype=np.float32) * voxel + origin[0]) / cell
    iz, iy, ix = np.floor(z).astype(np.int32), np.floor(y).astype(np.int32), np.floor(x).astype(np.int32)
    fz, fy, fx = z - iz, y - iy, x - ix
    lo = (iz.min() - 1, iy.min() - 1, ix.min() - 1)
    dims = (iz.max() - lo[0] + 2, iy.max() - lo[1] + 2, ix.max() - lo[2] + 2)
    pts = rng.random(dims + (3,), dtype=np.float32)
    best = np.full(shape, 9.0, np.float32)
    tmp = np.empty(shape, np.float32)
    for dz in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                p = pts[np.ix_(iz + dz - lo[0], iy + dy - lo[1], ix + dx - lo[2])]
                np.add((dz + p[..., 0] - fz[:, None, None]) ** 2, (dy + p[..., 1] - fy[None, :, None]) ** 2, out=tmp)
                tmp += (dx + p[..., 2] - fx[None, None, :]) ** 2
                np.minimum(best, tmp, out=best)
    return np.sqrt(best, out=best)


def domes(shape, voxel, cell, rng, origin=(0.0, 0.0, 0.0)):
    """Rounded heads with creases between them, 0..1: what a cumulus is covered in."""
    d = cells(shape, voxel, cell, rng, origin)
    d *= 1.0 / 0.85
    np.clip(d, 0.0, 1.0, out=d)
    return np.sqrt(1.0 - d * d)


def smooth_field(shape, voxel, scale, rng):
    """Smooth random field, mean 0 and unit spread, features `scale` metres across (any number of dimensions)."""
    f = np.fft.rfftn(rng.standard_normal(shape).astype(np.float32))
    k2 = 0.0
    for a, n in enumerate(shape):
        k = np.fft.rfftfreq(n) if a == len(shape) - 1 else np.fft.fftfreq(n)
        sl = [None] * len(shape)
        sl[a] = slice(None)
        k2 = k2 + (k[tuple(sl)]) ** 2
    f *= np.exp(-k2 * (math.pi * scale / voxel) ** 2 * 0.5)
    out = np.fft.irfftn(f, s=shape).astype(np.float32)
    return out / (out.std() + 1e-9)


def fbm2(shape, voxel, scale, rng, octaves=5, gain=0.5):
    """Fractal 2D field, mean 0, unit spread: clouds' outlines are ragged at every size."""
    out = np.zeros(shape, np.float32)
    a = 1.0
    for _ in range(octaves):
        if scale < 2.0 * voxel:
            break
        out += a * smooth_field(shape, voxel, scale, rng)
        a *= gain
        scale *= 0.5
    return out / (out.std() + 1e-9)


def cells2(shape, voxel, cell, rng, stretch=1.0):
    """2D version of cells(): distance (in cells) to the nearest point; `stretch` draws the cells out along x."""
    ny, nx = shape
    y = np.arange(ny, dtype=np.float32) * voxel / cell
    x = np.arange(nx, dtype=np.float32) * voxel / (cell * stretch)
    iy, ix = np.floor(y).astype(np.int32), np.floor(x).astype(np.int32)
    fy, fx = y - iy, x - ix
    pts = rng.random((iy.max() + 3, ix.max() + 3, 2), dtype=np.float32)
    best = np.full(shape, 9.0, np.float32)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            p = pts[np.ix_(iy + dy + 1, ix + dx + 1)]
            d = (dy + p[..., 0] - fy[:, None]) ** 2 + (dx + p[..., 1] - fx[None, :]) ** 2
            np.minimum(best, d, out=best)
    return np.sqrt(best)


# ---- Heaped clouds ----------------------------------------------------------------------------------------------

def spheres_sdf(shape, voxel, origin, centres, radii, blend=0.15, squash=1.0, reach=0.0):
    """Signed distance (metres, negative inside) to a union of spheres, their joins a little rounded. centres: (n,
    3) as x, y, z. `squash` flattens the lot (heights are divided by it before distances are taken). The distance is true
    out to `reach` metres from every sphere (beyond that it is only "far")."""
    nz, ny, nx = shape
    z = (np.arange(nz, dtype=np.float32) * voxel + origin[2]) / squash
    y = np.arange(ny, dtype=np.float32) * voxel + origin[1]
    x = np.arange(nx, dtype=np.float32) * voxel + origin[0]
    sdf = np.full(shape, 1e6, np.float32)
    for (cx, cy, cz), r in zip(centres, radii):
        m = r * (1.0 + blend) + reach + 2 * voxel
        z0, z1 = np.searchsorted(z, cz - m), np.searchsorted(z, cz + m)
        y0, y1 = np.searchsorted(y, cy - m), np.searchsorted(y, cy + m)
        x0, x1 = np.searchsorted(x, cx - m), np.searchsorted(x, cx + m)
        if z0 >= z1 or y0 >= y1 or x0 >= x1:
            continue
        d = np.sqrt((z[z0:z1, None, None] - cz) ** 2 + (y[None, y0:y1, None] - cy) ** 2 + (x[None, None, x0:x1] - cx) ** 2) - r
        s = sdf[z0:z1, y0:y1, x0:x1]
        k = blend * r
        h = np.clip(0.5 + 0.5 * (s - d) / k, 0.0, 1.0)
        sdf[z0:z1, y0:y1, x0:x1] = s * (1 - h) + d * h - k * h * (1 - h)
    return sdf


def heap(rng, width, height, towers=None, smallest=20.0, lumps=None):
    """The spheres of one heaped cloud: a body `width` metres across standing on a flat base (z = 0), bubbles
    stacked on its lumps up to a pile about `height` high in the middle and lower towards the rim, and heads on heads
    over every one of them, down to `smallest` metres. `towers`: how many of the stacks may reach the full height
    (None: one or two). Returns centres (n, 3) and radii."""
    c, r = [], []

    def add(p, rr):
        c.append(p); r.append(rr)
        return len(c) - 1

    # The body: lumps side by side over a drawn-out disc, sunk into the base so that it is cut flat.
    n0 = lumps or int(rng.integers(7, 13))
    stretch = rng.uniform(1.0, 1.6)
    ang = rng.uniform(0, math.pi)
    ca, sa = math.cos(ang), math.sin(ang)
    half = 0.5 * width
    off = rng.normal(0, 0.10 * width, 2)                      # the pile's top is seldom over the middle
    body = []
    for i in range(n0):
        rr = min(width * rng.uniform(0.13, 0.21), 0.6 * height)
        rad = (half / stretch - 0.8 * rr) * math.sqrt(rng.uniform(0.0, 1.0)) if i else 0.0
        th = rng.uniform(0, 2 * math.pi)
        u, v = rad * math.cos(th) * stretch, rad * math.sin(th)
        body.append(add((u * ca - v * sa, u * sa + v * ca, rr * rng.uniform(-0.10, 0.22)), rr))
    # Growth: bubbles stacked on the lumps, each a little smaller and a little aside, up to the pile's outline
    # (highest near its top, low at the rim). A few stacks reach it; most stop short.
    full = towers if towers is not None else int(rng.integers(1, 3))
    # (The tallest stand on big lumps near the pile's top: a tower is a mass, never a spire.)
    reach = sorted(body, key=lambda b: math.hypot(c[b][0] - off[0], c[b][1] - off[1]) - 2.0 * r[b])
    for k, b in enumerate(reach):
        (px, py, pz), pr = c[b], r[b]
        rho = math.hypot(px - off[0], py - off[1]) / half
        top = height * max(1.0 - 0.8 * rho ** 1.4, 0.12) * (1.0 if k < full else rng.uniform(0.35, 0.8))
        guard, r0 = 0, pr
        while pz + pr < top * 0.95 and guard < 60:
            guard += 1
            rr = max(pr * rng.uniform(0.86, 1.0), 0.6 * r0)
            px, py = px + rng.normal(0, 0.24 * pr), py + rng.normal(0, 0.24 * pr)
            pz = pz + pr * rng.uniform(0.55, 1.0)
            if pz + rr > top:
                rr = max(top - pz, 0.4 * pr)
            add((px, py, pz), rr)
            pr = rr
            if rr < 0.035 * width:
                break
    # Heads: smaller bubbles all over the upper and outer side of every bubble, and smaller ones on those.
    parents = list(range(len(c)))
    while parents:
        nxt = []
        for p in parents:
            (px, py, pz), pr = c[p], r[p]
            if pr * 0.42 < smallest:
                continue
            for _ in range(int(rng.integers(5, 10))):
                th = rng.uniform(0, 2 * math.pi)
                up = rng.uniform(-0.15, 1.0)
                rr = pr * rng.uniform(0.32, 0.55)
                d = pr * rng.uniform(0.62, 0.90)             # sunk in: a bump on its parent, not a ball beside it
                h = math.sqrt(max(1 - up * up, 0.0))
                q = (px + d * math.cos(th) * h, py + d * math.sin(th) * h, pz + d * up)
                if q[2] < 0.7 * rr:           # none hanging under the base
                    continue
                nxt.append(add(q, rr))
        parents = nxt
    return np.array(c, np.float32), np.array(r, np.float32)


def cumulus(rng, width, height, voxel, squash=1.0, towers=None, crisp=1.0, lumps=None):
    """Density (0..1) of one heaped cloud on a grid of `voxel` metres, and the grid's origin (metres; the cloud's
    base is z = 0, its middle about x = y = 0). `squash` < 1 flattens it (fair-weather humilis); `crisp` < 1 lets
    more of it go soft. Returns (array z, y, x; origin x, y, z)."""
    centres, radii = heap(rng, width, height / squash, towers=towers, smallest=max(2.0 * voxel, 0.012 * width), lumps=lumps)
    rmax = float(radii.max())
    # (Room round every sphere for the heads the cells will push out of it.)
    pad = 0.25 * rmax + 0.08 * width + 5 * voxel
    zs = centres[:, 2] * squash
    lo = np.array([(centres[:, 0] - radii).min() - pad, (centres[:, 1] - radii).min() - pad, -3 * voxel])
    hi = np.array([(centres[:, 0] + radii).max() + pad, (centres[:, 1] + radii).max() + pad,
                   (zs + radii * squash).max() + pad])
    n = np.ceil((hi - lo) / voxel).astype(int)
    shape = (int(n[2]), int(n[1]), int(n[0]))
    # (The cells below push the surface out by up to about a fifth of the largest lump.)
    sdf = spheres_sdf(shape, voxel, lo, centres, radii, blend=0.22, squash=squash, reach=0.2 * rmax + 0.03 * width + 3 * voxel)
    # Where the cloud is growing its heads are crisp; where it is evaporating it is soft and torn. A smooth field
    # decides which part is which (the lower and outer parts more often soft).
    soft = smooth_field(shape, voxel, 0.35 * width, rng)
    z = np.arange(shape[0], dtype=np.float32) * voxel + lo[2]
    soft += (0.25 - 1.4 * (z / max(height, 1.0)))[:, None, None]
    soft = np.clip((soft - (0.75 * crisp + 0.15)) / 0.9, 0.0, 1.0)
    # Cauliflower: cells at falling sizes push the surface out into heads.
    size = 0.45 * rmax
    amp = 0.26 * size
    total = 0.0
    while size >= 2.2 * voxel:
        d = domes(shape, voxel, size, rng, lo)
        d *= amp
        sdf -= d
        total += amp
        size *= 0.5
        amp *= 0.66
    sdf += 0.50 * total
    # The torn parts: a finer, deeper, irregular bite.
    bite = smooth_field(shape, voxel, 5.0 * voxel, rng) * 0.6 + smooth_field(shape, voxel, 14.0 * voxel, rng)
    sdf += soft * (np.abs(bite) * 4.0 * voxel + 0.02 * width)
    # The whole heap leans and swells a little.
    sdf += smooth_field(shape, voxel, 0.5 * width, rng) * 0.025 * width
    # A flat base, not a clean cut: it lies a little higher here and lower there, and its lowest part is thin.
    b2 = fbm2(shape[1:], voxel, 0.25 * width, rng, octaves=4) * (0.012 * width + 1.5 * voxel)
    above = z[:, None, None] - b2[None]
    base = np.clip(above / (0.035 * height + 2.0 * voxel), 0.0, 1.0)
    edge = voxel * (1.0 + 3.0 * soft)
    dens = np.clip(-sdf / edge + 0.5, 0.0, 1.0) * base * (1.0 - 0.45 * soft)
    return dens.astype(np.float32), lo


def halve(dens):
    """The same cloud on a grid twice as coarse."""
    nz, ny, nx = (s - s % 2 for s in dens.shape)
    d = dens[:nz, :ny, :nx]
    return d.reshape(nz // 2, 2, ny // 2, 2, nx // 2, 2).mean(axis=(1, 3, 5)).astype(np.float32)


# ---- Layers -----------------------------------------------------------------------------------------------------

def layer(base, top, voxel, zvoxel=None, soft=1.5):
    """Density of a layer given maps (y, x; metres) of its base and its top: 1 between them, eased over `soft`
    voxels; nothing where the top is not above the base. Returns (array z, y, x; the height of its lowest slice)."""
    zv = zvoxel or voxel
    z0 = float(np.floor(base[top > base].min() / zv) * zv) - zv if (top > base).any() else 0.0
    z1 = float(top.max()) + zv
    z = np.arange(z0, z1, zv, dtype=np.float32)[:, None, None]
    e = soft * zv
    thick = np.clip((top - base) / (2.0 * e), 0.0, 1.0)[None]
    d = np.clip((z - base[None]) / e + 0.5, 0.0, 1.0) * np.clip((top[None] - z) / e + 0.5, 0.0, 1.0) * thick
    return d.astype(np.float32), z0


def write_vdb(path, dens, origin, voxel, zvoxel=None, tolerance=0.004):
    """Writes a density array (z, y, x) as a VDB fog volume in metres; `origin` is where its first voxel lies."""
    try:
        import openvdb
    except ImportError:
        # (Outside Blender its Python does not find the library the module needs: it lies beside blender.exe.)
        import os
        import sys
        root = os.path.abspath(os.path.join(os.path.dirname(sys.executable), '..', '..', '..'))
        for d in (root, os.path.join(root, 'blender.shared')):
            if os.path.isdir(d):
                os.add_dll_directory(d)
        import openvdb
    zv = zvoxel or voxel
    g = openvdb.FloatGrid()
    g.copyFromArray(np.ascontiguousarray(dens.transpose(2, 1, 0)), tolerance=tolerance)       # (x, y, z)
    g.transform = openvdb.createLinearTransform([[voxel, 0, 0, 0], [0, voxel, 0, 0], [0, 0, zv, 0],
                                                 [float(origin[0]), float(origin[1]), float(origin[2]), 1]])
    g.name = 'density'
    g.gridClass = openvdb.GridClass.FOG_VOLUME
    openvdb.write(path, grids=[g])
