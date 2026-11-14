"""Clouds as volumes for the path tracer: heaps built the way the effects trade builds them (a cluster of spheres
for the body and its turrets, its surface broken up by cellular noise at falling sizes: "pyroclastic" displacement),
written as OpenVDB grids.

Only numpy and Blender's own `openvdb` module are used.
"""
import math

import numpy as np


def worley(shape, voxel, cell, rng, origin=(0.0, 0.0, 0.0)):
    """Cellular noise on a grid of `shape` voxels (z, y, x) of `voxel` metres: 1 at a cell's point, falling to 0
    between points. `cell` is the cells' size in metres."""
    nz, ny, nx = shape
    z = (np.arange(nz, dtype=np.float32) * voxel + origin[2]) / cell
    y = (np.arange(ny, dtype=np.float32) * voxel + origin[1]) / cell
    x = (np.arange(nx, dtype=np.float32) * voxel + origin[0]) / cell
    iz, iy, ix = np.floor(z).astype(np.int32), np.floor(y).astype(np.int32), np.floor(x).astype(np.int32)
    fz, fy, fx = z - iz, y - iy, x - ix
    # one point in every cell of a lattice big enough for the grid and its neighbours
    lo = (iz.min() - 1, iy.min() - 1, ix.min() - 1)
    dims = (iz.max() - lo[0] + 2, iy.max() - lo[1] + 2, ix.max() - lo[2] + 2)
    pts = rng.random(dims + (3,), dtype=np.float32)
    best = np.full(shape, 9.0, np.float32)
    for dz in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                p = pts[np.ix_(iz + dz - lo[0], iy + dy - lo[1], ix + dx - lo[2])]
                d = ((dz + p[..., 0] - fz[:, None, None]) ** 2 + (dy + p[..., 1] - fy[None, :, None]) ** 2
                     + (dx + p[..., 2] - fx[None, None, :]) ** 2)
                np.minimum(best, d, out=best)
    return 1.0 - np.sqrt(np.minimum(best, 1.0))


def smooth_field(shape, voxel, scale, rng):
    """Smooth random field with mean 0 and unit spread, features `scale` metres across (filtered white noise)."""
    f = np.fft.rfftn(rng.standard_normal(shape).astype(np.float32))
    kz = np.fft.fftfreq(shape[0])[:, None, None]
    ky = np.fft.fftfreq(shape[1])[None, :, None]
    kx = np.fft.rfftfreq(shape[2])[None, None, :]
    k2 = kz * kz + ky * ky + kx * kx
    f *= np.exp(-k2 * (math.pi * scale / voxel) ** 2 * 0.5)
    out = np.fft.irfftn(f, s=shape).astype(np.float32)
    return out / (out.std() + 1e-9)


def spheres_sdf(shape, voxel, origin, centres, radii, blend=0.35):
    """Signed distance (metres, negative inside) to a smooth union of spheres. centres: (n, 3) as x, y, z."""
    nz, ny, nx = shape
    z = np.arange(nz, dtype=np.float32) * voxel + origin[2]
    y = np.arange(ny, dtype=np.float32) * voxel + origin[1]
    x = np.arange(nx, dtype=np.float32) * voxel + origin[0]
    sdf = np.full(shape, 1e6, np.float32)
    for (cx, cy, cz), r in zip(centres, radii):
        # only the box the sphere (and its blend) can reach
        m = r * (1.0 + blend) + 2 * voxel
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


def heap(rng, width, height, turrets=1.0):
    """The spheres of one heaped cloud: a body `width` metres across standing on a flat base (z = 0), with towers
    that rise to about `height` metres and smaller heads all over them, each generation on the upper side of the
    one before (which is how a cumulus is built: bubble on bubble). Returns centres (n, 3) and radii."""
    c, r, gen = [], [], []

    def add(p, rr, g):
        c.append(p); r.append(rr); gen.append(g)
        return len(c) - 1

    # the body: lumps over a disc, largest in the middle, sunk into the base so that it is cut flat
    n0 = int(rng.integers(4, 8))
    stretch = rng.uniform(1.0, 1.6)
    ang = rng.uniform(0, math.pi)
    body_r = 0.5 * width / stretch
    for i in range(n0):
        rad = body_r * math.sqrt(rng.uniform(0.0, 0.55))
        th = rng.uniform(0, 2 * math.pi)
        u, v = rad * math.cos(th) * stretch, rad * math.sin(th)
        rr = min(width * rng.uniform(0.17, 0.26) * (1.0 - 0.45 * rad / body_r), 0.75 * height)
        add((u * math.cos(ang) - v * math.sin(ang), u * math.sin(ang) + v * math.cos(ang), rr * rng.uniform(0.25, 0.5)), rr, 0)
    # towers: bubbles stacked on the biggest lumps, each a little smaller, leaning as they climb
    order = np.argsort(-np.array(r))
    n_t = int(rng.integers(1, 4))
    for k in range(n_t):
        b = int(order[k % len(order)])
        (px, py, pz), pr = c[b], r[b]
        top = height * (1.0 if k == 0 else rng.uniform(0.55, 0.85))
        lean = rng.normal(0, 0.18, 2)
        while pz + pr < top:
            rr = pr * rng.uniform(0.74, 0.9)
            px, py, pz = px + lean[0] * pr + rng.normal(0, 0.12 * pr), py + lean[1] * pr + rng.normal(0, 0.12 * pr), pz + pr * rng.uniform(0.65, 0.9)
            if pz + rr > top:
                rr = max(top - pz, 0.3 * pr)
            b = add((px, py, pz), rr, 0)
            pr = rr
            if rr < 0.05 * width:
                break
    # heads: smaller bubbles on the upper side of every lump, and smaller ones on those
    parents = list(range(len(c)))
    for g in (1, 2):
        nxt = []
        for p in parents:
            (px, py, pz), pr = c[p], r[p]
            for _ in range(max(1, int(round(rng.integers(3, 7) * turrets)))):
                th = rng.uniform(0, 2 * math.pi)
                up = rng.uniform(0.15, 1.0) if pz > 0.35 * height else rng.uniform(0.45, 1.0)
                rr = pr * rng.uniform(0.34, 0.52)
                d = pr * rng.uniform(0.78, 0.98)
                h = math.sqrt(1 - up * up)
                nxt.append(add((px + d * math.cos(th) * h, py + d * math.sin(th) * h, pz + d * up), rr, g))
        parents = nxt
    return np.array(c, np.float32), np.array(r, np.float32)


def cumulus(rng, width, height, voxel, crumble=1.0):
    """Density (0..1) of one heaped cloud on a grid, and the grid's origin (metres; the cloud's base is z = 0 and
    its middle x = y = 0). Returns (array z,y,x; origin x,y,z)."""
    centres, radii = heap(rng, width, height)
    pad = 0.3 * float(radii.max()) + 0.03 * width + 4 * voxel
    lo = np.array([centres[:, 0].min() - radii.max() - pad, centres[:, 1].min() - radii.max() - pad, -2 * voxel])
    hi = np.array([centres[:, 0].max() + radii.max() + pad, centres[:, 1].max() + radii.max() + pad, height + pad])
    n = np.ceil((hi - lo) / voxel).astype(int)
    shape = (int(n[2]), int(n[1]), int(n[0]))
    sdf = spheres_sdf(shape, voxel, lo, centres, radii, blend=0.22)
    # Cells at falling sizes push the surface out into cauliflower heads; a smooth field leans the whole heap.
    size = 0.55 * float(radii.max())
    amp = 0.26 * size * crumble
    disp = np.zeros(shape, np.float32)
    total = 0.0
    for o in range(6):
        if size < 2.2 * voxel:
            break
        disp += worley(shape, voxel, size, rng, lo) * amp
        total += amp
        size *= 0.5
        amp *= 0.56
    sdf -= disp - 0.5 * total
    sdf += smooth_field(shape, voxel, 0.4 * width, rng) * 0.03 * width
    z = np.arange(shape[0], dtype=np.float32) * voxel + lo[2]
    # A flat base: nothing under it, and the lowest part thin and ragged (wisps hang down).
    base = np.clip(z / (0.05 * height + voxel), 0.0, 1.0)[:, None, None]
    dens = np.clip(-sdf / (1.2 * voxel), 0.0, 1.0) * base
    return dens.astype(np.float32), lo


def write_vdb(path, dens, origin, voxel):
    """Writes a density array (z, y, x) as a VDB fog volume in metres."""
    import openvdb
    g = openvdb.FloatGrid()
    g.copyFromArray(np.ascontiguousarray(dens.transpose(2, 1, 0)), tolerance=0.002)       # (x, y, z)
    g.transform = openvdb.createLinearTransform([[voxel, 0, 0, 0], [0, voxel, 0, 0], [0, 0, voxel, 0],
                                                 [float(origin[0]), float(origin[1]), float(origin[2]), 1]])
    g.name = 'density'
    g.gridClass = openvdb.GridClass.FOG_VOLUME
    openvdb.write(path, grids=[g])
