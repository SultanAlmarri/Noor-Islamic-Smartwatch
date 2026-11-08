"""Bakes the home sky's cumulus (0.9.8) into app/src/main/res/raw/clouds_a.png and clouds_b.png.

Real cumulus are volumes of vapour: light enters them, scatters many times and comes out softened, so their sunlit
billows glow, their creases and bases fall into soft grey, and their thin edges shine when the Sun is behind them. A
phone cannot follow light through a volume like that 15 times a second, so the clouds are rendered here once, as
volumes, and the phone relights them (the technique games call "six-way lighting"):

1. Each cloud is a density field on a voxel grid: a heap of rounded billows on a flat base (spheres), worn by
   large-scale Perlin-Worley noise and covered in smaller billows by Worley noise (the cauliflower of a cumulus).
2. For light arriving from each of six directions (right, left, above, below, behind, in front), the light reaching
   every voxel is computed from the vapour in its way, with the usual approximation of multiple scattering (several
   decaying exponentials), and the cloud is rendered as seen from the front: the light it sends towards us for that
   direction, and how much of the sky behind it it hides.
3. Eight clouds (flat fair-weather cumulus, taller ones with towers, a congestus, small puffs, a fragment) and their
   half-size copies (for distant clouds, so that they do not shimmer) are packed into two images:
   A = (lit from the right, from the left, from above, cover), B = (from below, from behind, from the front, cover).
   The light is stored divided by the cover (the decoder multiplies it back), so 8 bits keep the faint edges.

The sky shader blends the six images by where the Sun (or the moon) is relative to each cloud, adds skylight, the
forward scattering that silvers thin edges, haze with distance, and draws them drifting with the wind.

Usage: python3 tools/sky/build_clouds.py [out_dir]   (deterministic; about two minutes)
"""
import itertools
import os
import sys

import numpy as np
from PIL import Image
from scipy.ndimage import map_coordinates

NX, NY, NZ = 384, 192, 160          # voxels: the sprite is NX × NY pixels, NZ deep
BASE = 7                             # the cloud's base, voxels above the bottom of the sprite
COLS, ROWS = 2, 4                    # atlas layout of the full-size sprites
ATLAS_W, ATLAS_H = NX * COLS, NY * ROWS + NY // 2 * 2   # full sprites, then two rows of four half-size ones


def perlin3(n, period, rng):
    """Tileable gradient noise on an n³ grid with [period] lattice cells per side, about −1…1."""
    g = rng.normal(size=(period, period, period, 3))
    g /= np.linalg.norm(g, axis=-1, keepdims=True)
    c = (np.arange(n) + 0.5) / n * period
    X, Y, Z = np.meshgrid(c, c, c, indexing="ij")
    xi, yi, zi = np.floor(X).astype(int), np.floor(Y).astype(int), np.floor(Z).astype(int)
    xf, yf, zf = X - xi, Y - yi, Z - zi
    fade = lambda t: t * t * t * (t * (t * 6 - 15) + 10)
    u, v, w = fade(xf), fade(yf), fade(zf)
    out = np.zeros_like(X)
    for dx, dy, dz in itertools.product((0, 1), repeat=3):
        gg = g[(xi + dx) % period, (yi + dy) % period, (zi + dz) % period]
        dot = gg[..., 0] * (xf - dx) + gg[..., 1] * (yf - dy) + gg[..., 2] * (zf - dz)
        wx = u if dx else 1 - u
        wy = v if dy else 1 - v
        wz = w if dz else 1 - w
        out += wx * wy * wz * dot
    return out * 1.6


def worley3(n, cells, rng):
    """Tileable cellular noise: distance to the nearest of one random point per cell, in cell units (0…~1)."""
    pts = rng.random((cells, cells, cells, 3))
    c = (np.arange(n) + 0.5) / n * cells
    X, Y, Z = np.meshgrid(c, c, c, indexing="ij")
    xi, yi, zi = np.floor(X).astype(int), np.floor(Y).astype(int), np.floor(Z).astype(int)
    best = np.full(X.shape, 9.0)
    for dx, dy, dz in itertools.product((-1, 0, 1), repeat=3):
        cx, cy, cz = xi + dx, yi + dy, zi + dz
        p = pts[cx % cells, cy % cells, cz % cells]
        d = (X - cx - p[..., 0]) ** 2 + (Y - cy - p[..., 1]) ** 2 + (Z - cz - p[..., 2]) ** 2
        best = np.minimum(best, d)
    return np.sqrt(best)


def remap(x, a, b, c, d):
    return c + (x - a) / np.maximum(b - a, 1e-6) * (d - c)


def noise_textures(rng):
    n = 64
    # Perlin-Worley (as in Guerrilla's Nubis): billowy, connected lumps for the cloud's large forms.
    perlin = sum(perlin3(n, p, rng) * a for p, a in ((4, 1.0), (8, 0.5), (16, 0.25))) / 1.75
    perlin = np.clip(perlin * 0.5 + 0.5, 0, 1)
    wf = lambda cells: 1 - np.clip(worley3(n, cells, rng), 0, 1)
    w_low = wf(4) * 0.625 + wf(8) * 0.25 + wf(16) * 0.125
    pw = np.clip(remap(perlin, w_low - 1, 1, 0, 1), 0, 1)
    # Worley fbm for the small billows on the billows.
    detail = wf(4) * 0.625 + wf(8) * 0.25 + wf(16) * 0.125
    return pw.astype(np.float32), detail.astype(np.float32)


def sample(tex, coords):
    n = tex.shape[0]
    return map_coordinates(tex, [c % n for c in coords], order=1, mode="grid-wrap").reshape(coords[0].shape)


def heap(recipe, rng):
    """The spheres of a cloud: (x, y, z, r) in voxels, y up from the bottom of the sprite."""
    w_frac, h_frac, n_base, towers, depth_frac = recipe["w"], recipe["h"], recipe["base"], recipe["towers"], recipe.get("d", 0.55)
    W = NX * w_frac
    Hc = (NY - BASE - 6) * h_frac
    x0 = (NX - W) / 2
    zc = NZ / 2
    spheres = []
    for i in range(n_base):
        u = (i + 0.5) / n_base + rng.uniform(-0.05, 0.05)
        r = Hc * (0.26 + 0.30 * np.sin(np.pi * u) + rng.uniform(0, 0.16)) * recipe.get("rb", 1.0) * rng.uniform(0.8, 1.15)
        r = min(r, NZ * depth_frac)
        # Raised so that the sides round inwards to a flat base narrower than the cloud, as on a real cumulus;
        # the billows at the ends sit higher still.
        end = abs(u - 0.5) * 2
        spheres.append((x0 + u * W, BASE + r * rng.uniform(0.55, 0.75) + end * r * 0.25, zc + rng.uniform(-0.12, 0.12) * NZ, r))
        # A second, smaller row behind and in front: the cloud has depth.
        if rng.random() < 0.7:
            r2 = r * rng.uniform(0.55, 0.8)
            spheres.append((x0 + u * W + rng.uniform(-0.3, 0.3) * r, BASE + r2 * 0.7, zc + rng.choice([-1, 1]) * r * 0.55, r2))
    # Towers: piled on one another, each leaning a little from the one below.
    tu = rng.uniform(0.35, 0.65)
    top = max(s[1] + s[3] * 0.6 for s in spheres)
    for t in range(towers):
        tu = float(np.clip(tu + rng.choice([-1, 1]) * rng.uniform(0.06, 0.16), 0.2, 0.8))
        r = Hc * rng.uniform(0.22, 0.30) * (1.0 - 0.15 * t)
        y = min(top + r * 0.10, BASE + Hc - r * 0.85)
        spheres.append((x0 + tu * W, y, zc + rng.uniform(-0.08, 0.08) * NZ, r))
        if rng.random() < 0.6:
            spheres.append((x0 + tu * W + rng.choice([-1, 1]) * r * 0.7, y - r * 0.35, zc + rng.uniform(-0.1, 0.1) * NZ, r * 0.7))
        top = y + r * 0.55
    # Smaller billows on the upper side of each: the outline turns lumpy and uneven, as on a real cloud.
    for (sx, sy, sz, r) in list(spheres):
        for _ in range(rng.integers(1, 4) if rng.random() < recipe.get("kids", 0.85) else 0):
            th = rng.uniform(-1.25, 1.25)
            rc = r * rng.uniform(0.32, 0.55)
            cy = sy + np.cos(th) * r * 0.78
            if cy + rc > BASE + Hc * 1.02: continue
            spheres.append((sx + np.sin(th) * r * 0.78, cy, sz + rng.uniform(-0.35, 0.35) * r, rc))
    # Keep the whole cloud (and the noise that wears it) inside the sprite: shrink it about its base if needed.
    margin = 10 + 0.14 * Hc
    lo = min(s[0] - s[3] for s in spheres); hi = max(s[0] + s[3] for s in spheres)
    f = min(1.0, (NX - 2 * margin) / (hi - lo))
    mid = (lo + hi) / 2
    spheres = [(NX / 2 + (sx - mid) * f, BASE + (sy - BASE) * f, sz, r * f) for (sx, sy, sz, r) in spheres]
    return spheres, Hc * f


def density(recipe, seed, pw, detail):
    rng = np.random.default_rng(seed)
    spheres, Hc = heap(recipe, rng)
    x = np.arange(NX, dtype=np.float32)[:, None, None]
    y = np.arange(NY, dtype=np.float32)[None, :, None]
    z = np.arange(NZ, dtype=np.float32)[None, None, :]
    # Signed distance to the heap's surface, in voxels (positive inside): the billows' bumps are then the same size
    # on a big billow as on a small one, as on a real cloud.
    S = np.full((NX, NY, NZ), -1e3, dtype=np.float32)
    # Billows that would reach the front or the back of the volume are flattened in depth to fit (seen from the
    # front, that changes nothing but the light through them).
    room = NZ / 2 - 6 - 0.16 * Hc
    for (sx, sy, sz, r) in spheres:
        sz = NZ / 2 + float(np.clip(sz - NZ / 2, -room * 0.4, room * 0.4))
        zr = max(min(r, room - abs(sz - NZ / 2)), 4.0)
        S = np.maximum(S, r - np.sqrt((x - sx) ** 2 + (y - sy) ** 2 + ((z - sz) * (r / zr)) ** 2))
    h = np.clip((y - BASE) / Hc, 0, 1)
    X, Y, Z = np.broadcast_arrays(x, y, z)
    off = rng.uniform(0, 64, 3)
    # Large forms: the noise tile spans about the cloud's height.
    k1 = 64.0 / (Hc * 1.1)
    low = sample(pw, (X * k1 + off[0], Y * k1 + off[1], Z * k1 + off[2]))
    f = S + recipe.get("lump", 1.0) * Hc * 0.22 * (low - 0.55)
    # Small billows: the tile spans a third of it; rounder at the top, more ragged near the base.
    k2 = 64.0 / (Hc * 0.36)
    det = sample(detail, (X * k2 + off[2], Y * k2 + off[0], Z * k2 + off[1]))
    f = f + recipe.get("bill", 1.0) * Hc * 0.10 * (det - 0.60) * (0.6 + 0.4 * h)
    dens = np.clip(f / recipe.get("soft", 3.5), 0, 1)
    dens = dens * dens * (3 - 2 * dens)
    # The flat base, a little ragged.
    base = np.clip((y - BASE + 2.5 * (low - 0.5)) / 3.0, 0, 1)
    sigma = (recipe.get("sigma", 0.30) * dens * base).astype(np.float32)
    # Nothing at the edges of the sprite (no bleeding between sprites in the atlas).
    sigma[:3] = 0; sigma[-3:] = 0; sigma[:, -3:] = 0
    return sigma


def multiple(tau):
    """Light reaching a point through optical depth [tau], multiple scattering included (Wrenninge's octaves)."""
    return (np.exp(-tau) + 0.4 * np.exp(-0.3 * tau) + 0.15 * np.exp(-0.09 * tau)) / 1.55


def bake(sigma):
    """The cloud seen from the front (camera at z = 0 looking +z): six lit images and the cover."""
    ext = 1 - np.exp(-sigma)
    cz = np.cumsum(sigma, axis=2)
    view = np.exp(-(cz - sigma))                       # light from this voxel that reaches the eye
    wgt = view * ext
    out = {}
    def through(axis, reverse):
        s = np.flip(sigma, axis) if reverse else sigma
        c = np.cumsum(s, axis=axis)
        if reverse: c = np.flip(c, axis)
        return c - 0.5 * sigma
    for name, axis, rev in (("right", 0, True), ("left", 0, False), ("top", 1, True), ("bottom", 1, False), ("back", 2, True), ("front", 2, False)):
        out[name] = (wgt * multiple(through(axis, rev))).sum(axis=2)
    out["alpha"] = 1 - np.exp(-cz[..., -1])
    # Arrays are (x, y up): images are (row from the top, column).
    return {k: np.flipud(v.T) for k, v in out.items()}


RECIPES = [
    dict(name="humilis wide", w=0.86, h=0.40, base=6, towers=0, lump=0.8),
    dict(name="humilis", w=0.62, h=0.46, base=4, towers=1),
    dict(name="mediocris", w=0.76, h=0.66, base=5, towers=2),
    dict(name="mediocris tall", w=0.60, h=0.82, base=4, towers=3),
    dict(name="congestus", w=0.66, h=0.84, base=5, towers=3, sigma=0.32),
    dict(name="puffs", w=0.50, h=0.38, base=4, towers=1, rb=0.8, lump=1.5, bill=1.2),
    dict(name="flat wide", w=0.92, h=0.30, base=9, towers=0, rb=0.85, lump=1.3, bill=1.2),
    dict(name="fragment", w=0.44, h=0.34, base=4, towers=0, rb=0.8, lump=2.0, bill=1.8, sigma=0.16, soft=7.0, kids=1.0),
]


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "../../app/src/main/res/raw")
    rng = np.random.default_rng(1447)
    pw, detail = noise_textures(rng)
    A = np.zeros((ATLAS_H, ATLAS_W, 4), dtype=np.float32)
    B = np.zeros((ATLAS_H, ATLAS_W, 4), dtype=np.float32)
    tops = []
    for i, recipe in enumerate(RECIPES):
        sig = density(recipe, 100 + i, pw, detail)
        im = bake(sig)
        a = np.clip(im["alpha"], 0, 1)
        # Stored divided by the cover; the decoder multiplies it back (premultiplied alpha).
        un = lambda k: np.where(a > 1e-4, np.clip(im[k] / np.maximum(a, 1e-4), 0, 1), 0)
        ta = np.dstack([un("right"), un("left"), un("top"), a])
        tb = np.dstack([un("bottom"), un("back"), un("front"), a])
        col, row = i % COLS, i // COLS
        A[row * NY:(row + 1) * NY, col * NX:(col + 1) * NX] = ta
        B[row * NY:(row + 1) * NY, col * NX:(col + 1) * NX] = tb
        # Half size, for distant clouds: averaged premultiplied, so the edges stay right.
        def half(t):
            pm = t.copy(); pm[..., :3] *= pm[..., 3:4]
            h = pm.reshape(NY // 2, 2, NX // 2, 2, 4).mean(axis=(1, 3))
            h[..., :3] = np.where(h[..., 3:4] > 1e-4, np.clip(h[..., :3] / np.maximum(h[..., 3:4], 1e-4), 0, 1), 0)
            return h
        hy = NY * ROWS + (i // 4) * (NY // 2); hx = (i % 4) * (NX // 2)
        A[hy:hy + NY // 2, hx:hx + NX // 2] = half(ta)
        B[hy:hy + NY // 2, hx:hx + NX // 2] = half(tb)
        rows = np.where(a.max(axis=1) > 8 / 255)[0]
        tops.append((NY - 1 - BASE - rows.min()) / NY)
        print(f"{i} {recipe['name']}: cover {a.max():.2f}, lit from above {im['top'].max():.2f}, reaches {tops[-1]:.3f} of the sprite")
    for name, t in (("clouds_a.png", A), ("clouds_b.png", B)):
        Image.fromarray((np.clip(t, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(out_dir, name), optimize=True)
    print("atlas", ATLAS_W, "x", ATLAS_H)
    # sky_layer.agsl's cloudTop() must hold these (how high each cloud reaches): a cloud is never cut by the text.
    print("cloudTop:", ", ".join(f"{t + 0.01:.2f}" for t in tops))


if __name__ == "__main__":
    main()
