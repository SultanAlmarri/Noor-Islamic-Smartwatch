"""Grows a dune field the way wind does (Werner's model, 1995), for the Dunes scenery.

Sand lies on the ground in slabs. A slab is picked up where the wind reaches it, carried a few cells downwind and
set down again: surely in the wind shadow behind a crest, likely on sand, less likely on bare ground. Wherever a
slope gets steeper than sand can stand (about 34 degrees) it slides. Nothing else: crests, slip faces, the horns
of barchans and the forks where ridges join all come out of that by themselves.

    python dunesim.py <out.npy> [size=512] [time=600] [depth=11] [seed=7]

Writes the heights (float32 metres, cell = CELL metres) and a shaded picture next to it (<out>.png).
Runs with the Python that Blender ships (numpy only).
"""
import struct
import sys
import zlib

import numpy as np

CELL = 5.0            # metres per cell
SLAB = CELL / 3.0     # metres per slab: two slabs per cell is the angle of repose


def write_png(path, a):
    a = np.ascontiguousarray(a, dtype=np.uint8)
    h, w = a.shape[:2]
    ch = 1 if a.ndim == 2 else a.shape[2]
    raw = b''.join(b'\x00' + a[y].tobytes() for y in range(h))

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    kind = {1: 0, 3: 2, 4: 6}[ch]
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, kind, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))


def shadow(h, drop):
    """True where a crest upwind (the wind blows towards +x) keeps the wind off a cell."""
    n = h.shape[1]
    s = h.astype(np.float32).copy()
    prev = s[:, -1].copy()
    for _ in range(2):                     # twice round, for the wrap
        for x in range(n):
            col = s[:, x]
            np.maximum(col, prev - drop, out=col)
            prev = col
    return s > h + 1e-4


def relax(h, times=1):
    for _ in range(times):
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, 1), (1, -1), (-1, -1)):
            nb = np.roll(h, (dy, dx), axis=(0, 1))
            limit = 2 if dy == 0 or dx == 0 else 3
            mv = (h - nb) > limit
            h -= mv
            h += np.roll(mv, (-dy, -dx), axis=(0, 1))


def grow(n=512, time=600, depth=11, seed=7, hop=5, p_sand=0.6, p_bare=0.4, log=None):
    rng = np.random.default_rng(seed)
    h = (depth + rng.integers(-1, 2, (n, n))).astype(np.int32)
    per = n * n // 16
    for it in range(time * 16):
        sh = shadow(h, 0.8)                # 15 degrees behind a crest
        ys = rng.integers(0, n, per)
        xs = rng.integers(0, n, per)
        ok = (h[ys, xs] > 0) & ~sh[ys, xs]
        ys, xs = ys[ok], xs[ok]
        np.subtract.at(h, (ys, xs), 1)
        np.maximum(h, 0, out=h)
        moving = np.ones(len(xs), bool)
        for _ in range(14):
            xs = np.where(moving, (xs + hop) % n, xs)
            ys = np.where(moving, (ys + rng.integers(-1, 2, len(ys))) % n, ys)     # a little sideways, as gusts do
            p = np.where(sh[ys, xs], 1.0, np.where(h[ys, xs] > 0, p_sand, p_bare))
            down = moving & (rng.random(len(xs)) < p)
            np.add.at(h, (ys[down], xs[down]), 1)
            moving &= ~down
            if not moving.any():
                break
        np.add.at(h, (ys[moving], xs[moving]), 1)
        relax(h, 1)
        if log and it % (16 * 50) == 0:
            log(it // 16, h)
    relax(h, 6)
    return h


def blur(a, r):
    k = np.exp(-0.5 * (np.arange(-3 * r, 3 * r + 1) / max(r, 1e-6)) ** 2)
    k /= k.sum()
    for ax in (0, 1):
        a = sum(np.roll(a, i - len(k) // 2, axis=ax) * k[i] for i in range(len(k)))
    return a


def shade(z, cell, az=-0.9, el=0.5):
    gx = (np.roll(z, -1, 1) - np.roll(z, 1, 1)) / (2 * cell)
    gy = (np.roll(z, -1, 0) - np.roll(z, 1, 0)) / (2 * cell)
    nrm = np.stack([-gx, -gy, np.ones_like(z)], -1)
    nrm /= np.linalg.norm(nrm, axis=-1, keepdims=True)
    light = np.array([np.cos(el) * np.cos(az), np.cos(el) * np.sin(az), np.sin(el)])
    return np.clip(nrm @ light, 0, 1)


if __name__ == '__main__':
    out = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 512
    time = int(sys.argv[3]) if len(sys.argv) > 3 else 600
    depth = int(sys.argv[4]) if len(sys.argv) > 4 else 11
    seed = int(sys.argv[5]) if len(sys.argv) > 5 else 7

    def log(t, h):
        print('time', t, 'height', h.min(), h.max(), flush=True)

    h = grow(n, time, depth, seed, log=log)
    z = blur(h.astype(np.float32), 0.8) * SLAB
    np.save(out, z.astype(np.float32))
    write_png(out + '.png', (shade(z, CELL) * 255).astype(np.uint8))
    print('done', z.min(), z.max())
