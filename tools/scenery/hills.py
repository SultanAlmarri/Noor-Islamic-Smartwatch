"""Rolling hills with real drainage.

Hills cut out of noise look like noise. Real ones are made by water and by creeping soil: rain runs downhill, gathers
and cuts valleys that join like the branches of a tree, while the soil creeps and rounds the tops. This is that
process as a small landscape-evolution model (stream-power incision and hillslope diffusion), run on a surface that is
first laid out for the picture. Pure numpy; no Blender needed.

    python hills.py <out.npy> [seed]      (also writes <out>.png, a shaded map seen from above)
"""
import heapq
import math
import struct
import sys
import zlib

import numpy as np


def noise(n, seed, long_cells, short_cells, slope=2.2):
    """Smooth random relief on an n x n grid, zero mean and unit deviation: waves between `short_cells` and
    `long_cells` across, the long ones the stronger (spectrum falling as wavelength**-slope)."""
    rng = np.random.default_rng(seed)
    f = np.fft.fftfreq(n)
    k = np.hypot(f[:, None], f[None, :])
    k[0, 0] = 1.0
    amp = k ** (-slope / 2.0 - 0.5)
    amp *= 1.0 / (1.0 + (1.0 / (k * long_cells)) ** 6)       # nothing longer than long_cells
    amp *= 1.0 / (1.0 + (k * short_cells) ** 6)              # nothing shorter than short_cells
    amp[0, 0] = 0.0
    spec = amp * np.exp(2j * np.pi * rng.random((n, n)))
    z = np.real(np.fft.ifft2(spec))
    return ((z - z.mean()) / z.std()).astype(np.float32)


def flood(z, outlet, eps=2e-3):
    """Fills every hollow that has no way out to the brim (priority flood), leaving a hair's slope towards the
    way out: after it, water from every cell reaches an outlet."""
    h, w = z.shape
    zf = z.ravel().tolist()
    closed = bytearray(h * w)
    heap = []
    for i in np.flatnonzero(outlet.ravel()).tolist():
        closed[i] = 1
        heap.append((zf[i], i))
    heapq.heapify(heap)
    pop, push = heapq.heappop, heapq.heappush
    while heap:
        zc, i = pop(heap)
        y, x = divmod(i, w)
        for yy in (y - 1, y, y + 1):
            if yy < 0 or yy >= h:
                continue
            for xx in (x - 1, x, x + 1):
                if xx < 0 or xx >= w:
                    continue
                j = yy * w + xx
                if closed[j]:
                    continue
                closed[j] = 1
                zj = zf[j]
                if zj <= zc:
                    zj = zc + eps
                    zf[j] = zj
                push(heap, (zj, j))
    return np.asarray(zf, np.float32).reshape(h, w)


_NB = ((0, 1, 1.0), (0, -1, 1.0), (1, 0, 1.0), (-1, 0, 1.0), (1, 1, 1.41421356), (1, -1, 1.41421356),
       (-1, 1, 1.41421356), (-1, -1, 1.41421356))


def receivers(z, cell):
    """For every cell, the neighbour its water runs to (flat index; itself if none is lower) and the slope there."""
    h, w = z.shape
    p = np.pad(z, 1, constant_values=np.inf)
    best = np.zeros((h, w), np.float32)
    rcv = np.arange(h * w).reshape(h, w)
    idx = rcv.copy()
    for dy, dx, dist in _NB:
        nb = p[1 + dy:1 + dy + h, 1 + dx:1 + dx + w]
        s = (z - nb) / (dist * cell)
        take = s > best
        best = np.where(take, s, best)
        rcv = np.where(take, idx + dy * w + dx, rcv)
    return rcv.ravel(), best


def accumulate(rcv):
    """How many cells drain through each cell (itself included)."""
    n = len(rcv)
    own = np.arange(n)
    go = rcv != own
    src, dst = own[go], rcv[go]
    acc = np.ones(n, np.float64)
    for _ in range(6 * int(math.sqrt(n))):
        new = 1.0 + np.bincount(dst, weights=acc[src], minlength=n)
        if np.abs(new - acc).max() < 0.5:
            acc = new
            break
        acc = new
    return acc


def evolve(z, outlet, cell, steps=240, incise=0.012, creep=0.10, every=8, report=None):
    """Lets rain and creeping soil work on a surface. `outlet` marks cells that keep their height (where the water
    leaves). `incise`: how fast streams cut (per step, times sqrt(cells drained) times slope times the cell);
    `creep`: how fast soil spreads (fraction of the cell's curvature per step, under 0.2)."""
    z = z.astype(np.float32).copy()
    h, w = z.shape
    keep = outlet.copy()
    for step in range(steps):
        if step % every == 0:
            z = flood(z, outlet)
            rcv, slope = receivers(z, cell)
            acc = accumulate(rcv).reshape(h, w).astype(np.float32)
            if report:
                report(step, z, acc)
        zr = z.ravel()[rcv].reshape(h, w)
        cut = np.minimum(incise * np.sqrt(acc) * np.maximum(z - zr, 0.0), 0.45 * np.maximum(z - zr, 0.0))
        z = np.where(keep, z, z - cut)
        p = np.pad(z, 1, mode='edge')
        lap = p[:-2, 1:-1] + p[2:, 1:-1] + p[1:-1, :-2] + p[1:-1, 2:] - 4.0 * z
        z = np.where(keep, z, z + creep * lap)
    z = flood(z, outlet)
    rcv, slope = receivers(z, cell)
    acc = accumulate(rcv).reshape(h, w).astype(np.float32)
    return z, acc


def blur(a, r):
    """Gaussian blur (edges repeated), radius r cells."""
    if r <= 0:
        return a
    k = int(3 * r) + 1
    x = np.arange(-k, k + 1)
    g = np.exp(-x * x / (2.0 * r * r)).astype(np.float32)
    g /= g.sum()
    p = np.pad(a, k, mode='edge')
    out = np.zeros_like(a, dtype=np.float32)
    for i, wgt in enumerate(g):
        out += wgt * p[i:i + a.shape[0], k:k + a.shape[1]]
    p = np.pad(out, k, mode='edge')
    out2 = np.zeros_like(out)
    for i, wgt in enumerate(g):
        out2 += wgt * p[k:k + a.shape[0], i:i + a.shape[1]]
    return out2


def refine(a, up):
    """A grid `up` times finer, smoothly (cubic B-spline; edges repeated). Cell centres stay where they were."""
    h, w = a.shape
    p = np.pad(a, 2, mode='edge')

    def weights(m):
        f = (np.arange(m * up) + 0.5) / up - 0.5 + 2.0
        i = np.floor(f).astype(int)
        t = (f - i).astype(np.float32)
        return i, [(1 - t) ** 3 / 6, (3 * t ** 3 - 6 * t ** 2 + 4) / 6, (-3 * t ** 3 + 3 * t ** 2 + 3 * t + 1) / 6, t ** 3 / 6]

    iy, wy = weights(h)
    ix, wx = weights(w)
    rows = sum(p[iy - 1 + j] * wy[j][:, None] for j in range(4))
    return sum(rows[:, ix - 1 + j] * wx[j][None, :] for j in range(4)).astype(np.float32)


def sample(a, fx, fy):
    """Values of grid `a` at fractional cell positions (x, y arrays; cell centres at whole numbers), bilinear."""
    h, w = a.shape
    fx = np.clip(np.asarray(fx, np.float64), 0, w - 1.001)
    fy = np.clip(np.asarray(fy, np.float64), 0, h - 1.001)
    x0 = np.floor(fx).astype(int)
    y0 = np.floor(fy).astype(int)
    tx = (fx - x0).astype(np.float32)
    ty = (fy - y0).astype(np.float32)
    return (a[y0, x0] * (1 - tx) * (1 - ty) + a[y0, x0 + 1] * tx * (1 - ty)
            + a[y0 + 1, x0] * (1 - tx) * ty + a[y0 + 1, x0 + 1] * tx * ty)


def sample_smooth(a, fx, fy):
    """As sample(), but smoothly (cubic B-spline): no creases where cells meet."""
    h, w = a.shape
    fx = np.clip(np.asarray(fx, np.float64), 1.0, w - 2.001)
    fy = np.clip(np.asarray(fy, np.float64), 1.0, h - 2.001)
    x0 = np.floor(fx).astype(int)
    y0 = np.floor(fy).astype(int)
    tx = (fx - x0).astype(np.float32)
    ty = (fy - y0).astype(np.float32)

    def wts(t):
        return [(1 - t) ** 3 / 6, (3 * t ** 3 - 6 * t ** 2 + 4) / 6, (-3 * t ** 3 + 3 * t ** 2 + 3 * t + 1) / 6, t ** 3 / 6]

    wx, wy = wts(tx), wts(ty)
    out = np.zeros(fx.shape, np.float32)
    for j in range(4):
        row = sum(a[y0 - 1 + j, x0 - 1 + i] * wx[i] for i in range(4))
        out += row * wy[j]
    return out


def shade(z, cell, az=315.0, el=40.0):
    """A shaded map of the surface seen from above, 0..1."""
    gy, gx = np.gradient(z, cell)
    a, e = math.radians(az), math.radians(el)
    lx, ly, lz = math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)
    n = 1.0 / np.sqrt(gx * gx + gy * gy + 1.0)
    return np.clip((-gx * lx - gy * ly + lz) * n, 0.0, 1.0)


def write_png(path, img):
    """An 8-bit PNG from a 2D (grey) or 3D (rgb) array of 0..1; row 0 is written at the bottom (north up)."""
    a = np.clip(np.asarray(img)[::-1] * 255.0 + 0.5, 0, 255).astype(np.uint8)
    if a.ndim == 2:
        a = np.stack([a, a, a], -1)
    h, w = a.shape[:2]
    raw = b''.join(b'\x00' + a[y].tobytes() for y in range(h))

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    out = sys.argv[1]
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    n, cell = 256, 16.0
    z0 = noise(n, seed, 40, 6) * 30.0
    edge = np.zeros((n, n), bool)
    edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = True
    z, acc = evolve(z0, edge, cell, report=lambda s, zz, a: print(s, float(zz.min()), float(zz.max()), flush=True))
    np.save(out, z)
    write_png(out[:-4] + '.png', shade(z, cell))
