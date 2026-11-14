"""The middle and high cloud sets: altocumulus (flocks, rolls), altostratus, cirrus, cirrocumulus, cirrostratus.

    set NOOR_SET=ac_flock        (which set; see SETS below)
    blender -b --factory-startup -P tools/scenery/sky_high.py -- preview|look|bakefast|bake|repack <out dir> [bearing height]
    python tools/scenery/sky_high.py -- plan <out dir>      (a quick look at the pattern alone: no path tracing,
                                                             Blender's own Python is enough)

The volumes are written once into <out dir>/vdb and kept (delete them, or set NOOR_REGEN=1, after changing a
pattern). Everything here is procedural: see cloudgen_high.py.
"""
import glob
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cloudgen_high as ch       # noqa: E402
from cloudgen_high import fbm, vnoise, worley, smooth    # noqa: E402

SET = os.environ.get('NOOR_SET', 'ac_flock')
ARGS = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
MODE = ARGS[0] if ARGS else 'preview'
OUT = ARGS[1] if len(ARGS) > 1 else '.'
if MODE != 'plan':                # (the quick look runs in plain Python, without Blender)
    import bake                  # noqa: E402
    import skybake               # noqa: E402


def rot(x, y, deg):
    a = math.radians(deg)
    return x * math.cos(a) + y * math.sin(a), -x * math.sin(a) + y * math.cos(a)


# ---- Altocumulus in flocks: a mackerel sky ----------------------------------------------------------------------

def frac(a):
    return a - np.floor(a)


def cloudlets(x, y, v, seed, cover, cell, long=1.0, turn=28.0, tmax=210.0, jitter=0.55):
    """A layer of separate cloudlets, as altocumulus is made: convection cells of about one size standing in ranks,
    each a small heap with a flat base and a domed, lumpy top, blue lanes between them. `cell` is their spacing
    (metres), `long` how much longer than wide they are (rolls), `cover` (0..1 over the sky) how full the layer is:
    where it is low the cloudlets are small, many are missing, and the layer breaks up. Some cloudlets are ragged
    and dissolving. Returns thickness (metres) and density (0..1)."""
    # The ranks bend gently across the sky; no two outlines are alike.
    wx = (fbm(x, y, 13000.0, 3, seed + 3) - 0.5) * 6500.0
    wy = (fbm(x, y, 13000.0, 3, seed + 4) - 0.5) * 6500.0
    u, w = rot(x + wx, y + wy, turn)
    if v < 0.3 * cell:
        u = u + (fbm(x, y, 0.8 * cell, 2, seed + 5) - 0.5) * 0.36 * cell
        w = w + (fbm(x, y, 0.8 * cell, 2, seed + 6) - 0.5) * 0.36 * cell
    f1, f2, ident = worley(u / cell, w / (long * cell), 1.0, seed + 7, jitter=jitter)
    r2, r3 = frac(ident * 7919.13), frac(ident * 104729.7)
    # How large this cloudlet is (in cells); whether it is there at all; whether it is dissolving.
    radius = (0.27 + 0.25 * cover) * (0.78 + 0.44 * r2)
    there = (ident < 0.25 + 0.95 * cover) & (cover > 0.02)
    old = r3 < 0.16
    q = f1 / radius
    if v < 0.3 * cell:
        q = q + (fbm(x, y, 0.33 * cell, 2, seed + 8) - 0.5) * np.where(old, 1.1, 0.55)       # a frayed outline
    dome = np.sqrt(np.clip(1.0 - q * q, 0.0, 1.0))
    thick = tmax * dome ** 0.8 * (0.7 + 0.6 * r2) * np.where(old, 0.55, 1.0)
    if v < 0.18 * cell:
        g1, _, _ = worley(u / (0.30 * cell), w / (0.30 * cell), 1.0, seed + 9)                # heads on its top
        thick = thick * (0.78 + 0.34 * (1.0 - g1))
    dens = smooth(0.0, 0.22, 1.0 - q) * np.where(old, 0.30, 1.0) * (0.82 + 0.36 * fbm(x, y, 0.5 * cell, 2, seed + 10))
    dens = np.where(there, dens, 0.0)
    return np.where(dens > 0, thick, 0.0), dens


def far_thin(x, y, cover, start, end, seed):
    """The layer's far edge is no straight line: towards it the cover falls away unevenly, so the cloudlets grow
    small and few and the last of them stand alone."""
    r = np.hypot(x, y) * (1.0 + 0.5 * (fbm(x, y, 24000.0, 3, seed) - 0.5))
    return cover * (1.0 - smooth(start, end, r)) - 0.25 * smooth(start, end, r)


def averaged(fn, x, y, v, cell):
    """Far away a voxel is as large as a cloudlet: the pattern is then averaged over the voxel (four places in it),
    so that nothing falls between the samples."""
    if v < 0.22 * cell:
        return fn(x, y)
    t, d = 0.0, 0.0
    for ox, oy in ((-0.25, -0.25), (0.25, -0.25), (-0.25, 0.25), (0.25, 0.25)):
        ti, di = fn(x + ox * v, y + oy * v)
        t = t + ti * di
        d = d + di
    d = d / 4.0
    return np.where(d > 1e-4, t / 4.0 / np.maximum(d, 1e-4), 0.0), d


def ac_flock(x, y, v):
    seed = int(os.environ.get('NOOR_SEED', 29))
    # Fields of cloudlets with open sky between them, tens of kilometres across, their edges ragged.
    big = fbm(x, y, 46000.0, 4, seed + 1)
    med = fbm(x, y, 11000.0, 3, seed + 2)
    cover = smooth(0.43, 0.66, big * 0.62 + med * 0.28 + fbm(x, y, 2600.0, 2, seed + 12) * 0.10)
    cover = far_thin(x, y, cover, 42000.0, 96000.0, seed + 13)
    cell = 320.0
    thick, dens = averaged(lambda a, b: cloudlets(a, b, v, seed + 20, np.clip(cover, 0.0, 1.0), cell, long=1.3, tmax=175.0,
                                                   jitter=0.38), x, y, v, cell)
    base = (fbm(x, y, 9000.0, 2, seed + 10) - 0.5) * 100.0
    return base, thick, dens


# ---- Altocumulus in rolls (undulatus) ----------------------------------------------------------------------------

def ac_rolls(x, y, v):
    seed = int(os.environ.get('NOOR_SEED', 31))
    big = fbm(x, y, 52000.0, 4, seed + 1)
    med = fbm(x, y, 12000.0, 3, seed + 2)
    cover = smooth(0.38, 0.70, big * 0.66 + med * 0.26 + fbm(x, y, 2600.0, 2, seed + 12) * 0.08)
    cover = far_thin(x, y, cover, 42000.0, 96000.0, seed + 13)
    # Rolls: the cloudlets drawn out across the wind and standing end to end, rank behind rank, a lane of blue
    # between each rank and the next.
    cell = 460.0
    thick, dens = averaged(lambda a, b: cloudlets(a, b, v, seed + 20, np.clip(cover * 1.15, 0.0, 1.0), cell, long=3.4,
                                                   turn=-22.0, tmax=240.0, jitter=0.45), x, y, v, cell)
    base = (fbm(x, y, 9000.0, 2, seed + 10) - 0.5) * 100.0
    return base, thick, dens


# ---- Altostratus: a grey veil ------------------------------------------------------------------------------------

def as_veil(x, y, v):
    seed = int(os.environ.get('NOOR_SEED', 71))
    u, w = rot(x, y, 20.0)
    # A sheet drawn out along the wind in broad bands and fine streaks, with thinner windows where the Sun would
    # show as through ground glass.
    streak = smooth(0.30, 0.72, fbm(u / 9.0, w, 2600.0, 4, seed + 1))
    bands_ = smooth(0.25, 0.75, fbm(u / 5.0, w, 11000.0, 3, seed + 6))
    big = fbm(x, y, 60000.0, 3, seed + 2)
    mid = fbm(x, y, 15000.0, 3, seed + 3)
    body = 0.35 + 0.65 * smooth(0.30, 0.75, big * 0.6 + mid * 0.4)
    dens = body * (0.30 + 0.55 * bands_ + 0.50 * streak)
    dens *= 0.16 + 0.84 * smooth(0.40, 0.56, fbm(x, y, 34000.0, 3, seed + 4) * 0.8 + mid * 0.2)      # the windows
    thick = np.full(x.shape, 900.0, np.float32)
    base = (fbm(x, y, 20000.0, 2, seed + 5) - 0.5) * 300.0
    return base, thick, np.clip(dens, 0.0, 2.0)


# ---- The ice clouds ----------------------------------------------------------------------------------------------

ICE_SIGMA = 0.0012        # extinction per metre where the density is 1


class Fibres:
    """Cirrus: bundles of fibres laid over the sky as curves (see cloudgen_high.fibre_points)."""

    def __init__(self, seed, far, per_km2, heading, fields=(0.46, 0.62), length=(9000.0, 48000.0), spread=(700.0, 5200.0),
                 count=(24, 110), hook=0.95, strength=9.0, head=0.006, wander=0.06, thin_far=0.8):
        self.head = head
        rng = np.random.default_rng(seed)
        area = math.tan(ch.HALF_ANGLE) * (far / 1000.0) ** 2
        n = int(area * per_km2)
        y = far * np.sqrt(rng.random(n))
        x = y * math.tan(ch.HALF_ANGLE) * 1.15 * (2 * rng.random(n) - 1)
        f = fbm(x.astype(np.float32), y.astype(np.float32), 70000.0, 3, seed + 1) * 0.7 + \
            fbm(x.astype(np.float32), y.astype(np.float32), 17000.0, 2, seed + 2) * 0.3
        # (Fewer far away: seen so flat, the far sky would close into one white band.)
        keep = rng.random(n) < smooth(fields[0], fields[1], f) * (1.0 - thin_far * smooth(50000.0, 230000.0, np.hypot(x, y)))
        self.heads = np.stack([x[keep], y[keep]], 1)
        m = len(self.heads)
        self.heading = math.radians(heading) + (fbm(self.heads[:, 0].astype(np.float32), self.heads[:, 1].astype(np.float32), 90000.0, 2, seed + 3) - 0.5) * 1.0 \
            + rng.normal(0, wander, m)
        self.length = np.exp(rng.uniform(math.log(length[0]), math.log(length[1]), m))
        self.spread = np.exp(rng.uniform(math.log(spread[0]), math.log(spread[1]), m)) * (self.length / 20000.0) ** 0.5
        self.count = rng.integers(count[0], count[1], m)
        self.hook = rng.normal(hook, 0.3, m) * np.where(rng.random(m) < 0.85, 1.0, -0.4)
        self.gain = rng.uniform(0.5, 1.3, m) * strength
        self.seeds = rng.integers(0, 2 ** 31, m)
        print('cirrus:', m, 'bundles,', int(self.count.sum()), 'fibres', flush=True)

    def tau(self, X, Y, v, width=55.0):
        """Optical depth (looking straight up) of the fibres over a grid."""
        x0, x1, y0, y1 = float(X.min()), float(X.max()), float(Y.min()), float(Y.max())
        out = np.zeros(X.shape, np.float32)
        step = max(0.5 * v, 12.0)
        xs, ys, ws = [], [], []
        for b in range(len(self.heads)):
            hx, hy = self.heads[b]
            L = self.length[b]
            if hx < x0 - L or hx > x1 + L or hy < y0 - L or hy > y1 + L:
                continue
            rng = np.random.default_rng(int(self.seeds[b]))
            px, py, w = ch.fibre_points(rng, (hx, hy), self.heading[b], L, self.spread[b], self.count[b], self.hook[b], step)
            # the head: a tuft of ice the fibres fall from
            nh = 600
            sp = 0.22 * self.spread[b] + 250.0
            c, sn = math.cos(self.heading[b]), math.sin(self.heading[b])
            a, d = rng.normal(0, sp * 1.6, nh), rng.normal(0, sp * 0.7, nh)          # drawn out along the wind
            px = np.concatenate([px, (hx + a * c - d * sn).astype(np.float32)])
            py = np.concatenate([py, (hy + a * sn + d * c).astype(np.float32)])
            w = np.concatenate([w, np.full(nh, self.head * L * self.count[b] / nh, np.float32)])
            xs.append(px); ys.append(py); ws.append(w * self.gain[b])
        if xs:
            out = ch.splat(np.concatenate(xs), np.concatenate(ys), np.concatenate(ws), X, Y, v)
        sharp = ch.blur(out, max(width / v, 0.6), max(width / v, 0.6))
        return sharp, ch.blur(out, max(900.0 / v, 0.6), max(900.0 / v, 0.6))


_FIB = {}


def ci_wisps(x, y, v):
    seed = int(os.environ.get('NOOR_SEED', 41))
    if 'ci' not in _FIB:
        # Long sheaves combed out along the wind, each of many fine fibres; and, everywhere between and across
        # them, short tufts with their fall streaks, hooked, lying every which way.
        _FIB['ci'] = Fibres(seed, 330000.0, 0.10, 62.0, fields=(0.42, 0.68), length=(14000.0, 60000.0), spread=(500.0, 3600.0),
                            count=(60, 240), strength=2.6, head=0.003, wander=0.22)
        _FIB['ci2'] = Fibres(seed + 50, 330000.0, 0.38, 28.0, fields=(0.36, 0.70), length=(2500.0, 13000.0), spread=(250.0, 1500.0),
                             count=(14, 70), hook=1.3, strength=2.8, head=0.006, wander=0.55, thin_far=0.9)
    a, a_soft = _FIB['ci'].tau(x, y, v, width=26.0)
    b, b_soft = _FIB['ci2'].tau(x, y, v, width=22.0)
    # The fibres, a breath of milk round them, and here and there the faintest veil: blue shows through it all.
    tau = a + b + 0.20 * (a_soft + b_soft) + 0.015 * smooth(0.52, 0.70, fbm(x, y, 40000.0, 3, seed + 5))
    thick = np.full(x.shape, 350.0, np.float32)
    return np.zeros(x.shape, np.float32), thick, np.clip(tau, 0.0, 2.0) / (ICE_SIGMA * 350.0)


def cs_veil(x, y, v):
    seed = int(os.environ.get('NOOR_SEED', 61))
    if 'cs' not in _FIB:
        _FIB['cs'] = Fibres(seed, 330000.0, 0.006, 55.0, fields=(0.40, 0.60), length=(20000.0, 70000.0), spread=(3000.0, 12000.0),
                            count=(40, 140), hook=0.3, strength=5.0)
    sharp, soft = _FIB['cs'].tau(x, y, v, width=140.0)
    u, w = rot(x, y, 35.0)
    # A milky veil, thicker in places, faintly combed along the wind.
    body = 0.05 + 0.22 * smooth(0.25, 0.80, fbm(x, y, 80000.0, 3, seed + 5) * 0.65 + fbm(x, y, 20000.0, 3, seed + 6) * 0.35)
    comb = 0.75 + 0.5 * (fbm(u / 12.0, w, 1800.0, 3, seed + 7) - 0.5) * 2.0
    tau = body * np.clip(comb, 0.3, 1.8) + 0.5 * sharp + 0.8 * soft
    thick = np.full(x.shape, 500.0, np.float32)
    return np.zeros(x.shape, np.float32), thick, np.clip(tau, 0.0, 2.5) / (ICE_SIGMA * 500.0)


def cc_ripples(x, y, v):
    seed = int(os.environ.get('NOOR_SEED', 51))
    big = fbm(x, y, 70000.0, 4, seed + 1)
    med = fbm(x, y, 15000.0, 3, seed + 2)
    cover = smooth(0.38, 0.52, big * 0.66 + med * 0.26 + fbm(x, y, 3000.0, 2, seed + 12) * 0.08)
    cover = far_thin(x, y, cover, 70000.0, 150000.0, seed + 13)
    # Ripples like sand on a beach: the grains stand in rows, fuller on a ripple's crest, thin or missing between.
    wx = (fbm(x, y, 6000.0, 3, seed + 3) - 0.5) * 2400.0
    wy = (fbm(x, y, 6000.0, 3, seed + 4) - 0.5) * 2400.0
    u, _ = rot(x + wx, y + wy, 38.0)
    ripple = 0.5 + 0.5 * np.cos(2 * math.pi * (u / 760.0 + (fbm(x, y, 2500.0, 2, seed + 5) - 0.5) * 0.5))
    cov = np.clip(cover * (0.45 + 0.75 * ripple), 0.0, 1.0)
    cell = 210.0
    thick, dens = averaged(lambda a, b: cloudlets(a, b, v, seed + 20, cov, cell, long=1.15, turn=38.0, tmax=120.0,
                                                   jitter=0.6), x, y, v, cell)
    # (Thin ice: grains without shading, as the atlas has it.)
    return np.zeros(x.shape, np.float32), thick, dens * 0.55


MID = dict(altitude=4500.0, near=4500.0, far=100000.0, voxel=14.0, sigma=0.034, g=0.72, colour=(1.0, 1.0, 1.0),
           vz=lambda v: min(max(v * 0.5, 18.0), 60.0), fade=(0.90, 0.99), growth=1.6)
HIGH = dict(altitude=9500.0, near=10000.0, far=210000.0, voxel=22.0, sigma=ICE_SIGMA, g=0.50, colour=(0.985, 0.992, 1.0),
            vz=lambda v: min(max(v * 0.5, 40.0), 160.0), fade=(0.40, 0.95))
SETS = {
    'ac_flock': dict(MID, info={'layer': 'mid', 'altitude': 4.6, 'kind': 'altocumulus flocks'}, pattern=ac_flock),
    'ac_rolls': dict(MID, info={'layer': 'mid', 'altitude': 4.6, 'kind': 'altocumulus rolls'}, pattern=ac_rolls),
    'as_veil': dict(MID, info={'layer': 'mid', 'altitude': 5.0, 'kind': 'altostratus'}, pattern=as_veil, voxel=40.0, fade=(0.50, 0.96), growth=2.0,
                    sigma=0.0040, vz=lambda v: min(max(v * 0.5, 60.0), 200.0)),
    'ci_wisps': dict(HIGH, info={'layer': 'high', 'altitude': 9.7, 'kind': 'cirrus'}, pattern=ci_wisps),
    'cs_veil': dict(HIGH, info={'layer': 'high', 'altitude': 9.7, 'kind': 'cirrostratus'}, pattern=cs_veil, voxel=45.0),
    'cc_ripples': dict(HIGH, info={'layer': 'high', 'altitude': 9.5, 'kind': 'cirrocumulus'}, pattern=cc_ripples, voxel=16.0,
                       far=160000.0, fade=(0.90, 0.99), growth=1.8, sigma=ICE_SIGMA * 4.0, vz=lambda v: min(max(v * 0.5, 30.0), 160.0)),
}


# ---- Cloudlets set out as real small clouds -----------------------------------------------------------------------
# The cells of altocumulus and cirrocumulus are little heaps of cloud. Here each is one: a modelled cumulus (the
# CGHeven cloud VDBs, see sources/clouds_high.md; the files stay outside the repository), made small and a little
# flat, turned and mirrored, hundreds to thousands of them standing in ranks.

PUFF_DIR = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'cgheven')
PUFFS = ['cumulus_01', 'cumulus_11', 'cumulus_20', 'cumulus_32', 'cumulus_33', 'cumulus_35', 'cumulus_39', 'cumulus_43']


def puff_files():
    """The modelled clouds, reduced once to small grids (kept with the work files)."""
    import glob as _g
    keep = os.path.join(os.path.dirname(OUT.rstrip('/\\')), 'puffs')
    os.makedirs(keep, exist_ok=True)
    out = []
    for name in PUFFS:
        dst = os.path.join(keep, name + '.vdb')
        if not os.path.exists(dst):
            src = _g.glob(os.path.join(PUFF_DIR, name, '*.vdb'))[0]
            print('reduced', name, ch.reduce_vdb(src, dst), flush=True)
        out.append(dst)
    return out


def flock_fields(x, y, seed):
    big = fbm(x, y, 46000.0, 4, seed + 1)
    med = fbm(x, y, 11000.0, 3, seed + 2)
    cover = smooth(0.43, 0.66, big * 0.62 + med * 0.28 + fbm(x, y, 2600.0, 2, seed + 12) * 0.10)
    return np.clip(far_thin(x, y, cover, 40000.0, 95000.0, seed + 13), 0.0, 1.0)


def rolls_fields(x, y, seed):
    # Wide fields of rolls; inside a field a roll is whole for a kilometre or two, then thins or breaks.
    big = fbm(x, y, 52000.0, 4, seed + 1)
    med = fbm(x, y, 12000.0, 3, seed + 2)
    cover = smooth(0.40, 0.62, big * 0.66 + med * 0.34)
    cover = cover * (0.20 + 1.1 * smooth(0.33, 0.41, fbm(x, y, 2400.0, 2, seed + 14)))
    return np.clip(far_thin(x, y, cover, 40000.0, 95000.0, seed + 13), 0.0, 1.0)


def ripple_fields(x, y, seed):
    # Patches of ripples, a few tens of kilometres across, with open sky round them; the ripples themselves come
    # and go in broader waves across the patch.
    big = fbm(x, y, 60000.0, 4, seed + 1)
    med = fbm(x, y, 14000.0, 3, seed + 2)
    cover = smooth(0.40, 0.54, big * 0.64 + med * 0.28 + fbm(x, y, 3000.0, 2, seed + 12) * 0.08)
    u, _ = rot(x, y, 38.0)
    wave = 0.5 + 0.5 * np.cos(2 * math.pi * (u / 2600.0 + (fbm(x, y, 9000.0, 2, seed + 5) - 0.5) * 1.6))
    cover = cover * (0.62 + 0.38 * wave)
    return np.clip(far_thin(x, y, cover, 60000.0, 150000.0, seed + 13), 0.0, 1.0)


PUFFSETS = {
    'ac_flock': dict(seed=29, cell=300.0, long=1.25, turn=28.0, near=4500.0, far=96000.0, altitude=4500.0, fields=flock_fields,
                     size=(0.64, 0.62), flat=0.62, stretch=1.0, sigma=0.045, g=0.72, step=10.0),
    # Rolls: clean ranks half a kilometre apart, the cloudlets of a rank drawn out along it and standing so close
    # that they join; a lane of blue between each rank and the next, narrow where the layer is full.
    'ac_rolls': dict(seed=31, cell=520.0, long=0.50, turn=-22.0, near=4500.0, far=96000.0, altitude=4500.0, fields=rolls_fields,
                     size=(0.46, 0.50), flat=0.60, stretch=1.7, sigma=0.045, g=0.72, step=10.0, jitter=0.34, stagger=0.10,
                     keep=(-0.30, 3.2), old=0.05),
    # Cirrocumulus: grains of thin ice cloud in ripple rows, far smaller than altocumulus and without shade.
    'cc_ripples': dict(seed=51, cell=290.0, long=0.62, turn=38.0, near=10000.0, far=150000.0, altitude=9500.0, fields=ripple_fields,
                       size=(0.52, 0.40), flat=0.50, stretch=1.2, sigma=0.050, g=0.50, step=6.0, jitter=0.30, stagger=0.0,
                       keep=(0.12, 2.4), old=0.10, colour=(0.985, 0.992, 1.0)),
}


def build_puffs():
    import bpy
    cfg = PUFFSETS[SET]
    seed = int(os.environ.get('NOOR_SEED', cfg['seed']))
    files = puff_files()
    x, y, r1, r2, r3 = ch.rank_points(seed, cfg['cell'], cfg['long'], cfg['turn'], cfg['near'], cfg['far'],
                                      jitter=cfg.get('jitter', 0.4), stagger=cfg.get('stagger', 0.5))
    cover = cfg['fields'](x, y, seed)
    k0, k1 = cfg.get('keep', (0.22, 0.95))
    there = (r1 < k0 + k1 * cover) & (cover > 0.02)
    x, y, r1, r2, r3, cover = (a[there] for a in (x, y, r1, r2, r3, cover))
    old = r3 < cfg.get('old', 0.09)                                                     # these are dissolving: thin and low
    width = cfg['cell'] * (cfg['size'][0] + cfg['size'][1] * cover) * (0.80 + 0.45 * r2)
    r = np.hypot(x, y)
    z = cfg['altitude'] + (fbm(x, y, 9000.0, 2, seed + 10) - 0.5) * 100.0 - r * r / (2.0 * ch.EARTH)
    col = cfg.get('colour', (1.0, 1.0, 1.0))
    mats = [skybake.cloud_material('Cloudlet', sigma=cfg['sigma'], anisotropy=cfg['g'], colour=col),
            skybake.cloud_material('CloudletOld', sigma=cfg['sigma'] * 0.5, anisotropy=cfg['g'], colour=col)]
    data = []
    for kind in (0, 1):
        for f in files:
            vol = bpy.data.volumes.new(os.path.basename(f)[:-4] + ('_old' if kind else ''))
            vol.filepath = f
            vol.materials.append(mats[kind])
            vol.render.space = 'WORLD'
            vol.render.step_size = cfg['step']
            data.append(vol)
    coll = bpy.data.collections.new('Cloudlets')
    bpy.context.scene.collection.children.link(coll)
    which = (r2 * 997.0).astype(np.int64) % len(files)
    turn = r1 * 6.2832 * 7.0
    mirror = np.where(frac(r3 * 53.0) < 0.5, -1.0, 1.0)
    a = math.radians(cfg['turn'])
    for k in range(len(x)):
        ob = bpy.data.objects.new('c', data[int(which[k]) + (len(files) if old[k] else 0)])
        wd = float(width[k])
        ob.location = (float(x[k]), float(y[k]), float(z[k]))
        if cfg['stretch'] > 1.0:
            # rolls: drawn out along the rank, all lying the same way
            ob.rotation_euler = (0.0, 0.0, a + math.pi / 2 + (float(r1[k]) - 0.5) * 0.3)
            ob.scale = (wd * cfg['stretch'] * float(mirror[k]), wd, wd * cfg['flat'] * (0.45 if old[k] else 1.0))
        else:
            ob.rotation_euler = (0.0, 0.0, float(turn[k]))
            ob.scale = (wd * float(mirror[k]), wd, wd * cfg['flat'] * (0.45 if old[k] else 1.0))
        coll.objects.link(ob)
    print(SET, ':', len(x), 'cloudlets,', int(old.sum()), 'of them dissolving', flush=True)


def build():
    cfg = SETS[SET]
    vdb = os.path.join(OUT, 'vdb')
    os.makedirs(vdb, exist_ok=True)
    prefix = os.path.join(vdb, SET)
    paths = sorted(glob.glob(prefix + '_*.vdb'))
    if not paths or os.environ.get('NOOR_REGEN'):
        for p in paths:
            os.remove(p)
        paths = ch.write_layer(prefix, cfg['pattern'], cfg['altitude'], cfg['near'], cfg['far'], cfg['voxel'],
                               vz_of=cfg.get('vz'), fade=cfg.get('fade', (0.62, 1.0)), growth=cfg.get('growth', 2.0))
    mat = skybake.cloud_material('Cloud_' + SET, sigma=cfg['sigma'], anisotropy=cfg['g'], colour=cfg['colour'])
    # (The layers are far thicker than their voxels are high: the path tracer need not step finer than a voxel.)
    vzs = [(cfg.get('vz') or (lambda v: max(v * 0.5, 20.0)))(v) for _, _, v in ch.bands(cfg['near'], cfg['far'], cfg['voxel'], cfg.get('growth', 2.0))]
    for p in paths:
        ob = skybake.add_volume(p, (0.0, 0.0, 0.0), mat)
        n = int(os.path.splitext(p)[0].rsplit('_', 1)[1])
        try:
            ob.data.render.space = 'WORLD'
            ob.data.render.step_size = 2.0 * vzs[n]            # (the scene's step rate of 0.5 halves it again)
        except Exception as e:
            print('step size not set:', e, flush=True)


if MODE == 'plan':
    # A quick look at the pattern without the path tracer: the clouds' opacity over a plain blue sky.
    from dunesim import write_png
    cfg = SETS[SET]
    op, tau = ch.plan_view(cfg['pattern'], cfg['altitude'], cfg['near'], cfg['far'], cfg['voxel'], cfg['sigma'],
                           fade=cfg.get('fade', (0.62, 1.0)), growth=cfg.get('growth', 2.0),
                           scale=float(os.environ.get('NOOR_SCALE', 0.6)))
    rows = np.linspace(0, 1, op.shape[0], dtype=np.float32)[:, None, None] ** 2
    sky = np.array([0.20, 0.40, 0.75], np.float32) * (1 - rows) + np.array([0.62, 0.76, 0.90], np.float32) * rows
    shade = 1.0 - 0.42 * (1.0 - np.exp(-tau * 0.35))                 # thick parts are grey from below
    img = sky * (1 - op[..., None]) + op[..., None] * shade[..., None]
    path = os.path.join(OUT, 'plan%s.png' % os.environ.get('NOOR_TAG', ''))
    write_png(path, (np.clip(img, 0, 1) ** (1 / 2.2) * 255).astype(np.uint8))
    print('saved', path, 'cover %.2f' % float(op.mean()), flush=True)
    sys.exit(0)

if MODE == 'repack':
    # The last bake packed again, a little smaller: NOOR_QUALITY (WebP, default 72).
    skybake.bake_pack(os.path.join(OUT, 'raw'), os.path.join(OUT, 'sky'), skybake.lights(), SETS[SET]['info'],
                      quality=int(os.environ.get('NOOR_QUALITY', 72)))
    sys.exit(0)

skybake.run(build_puffs if SET in PUFFSETS and not os.environ.get('NOOR_PROCEDURAL') else build, SETS[SET]['info'], samples=int(os.environ.get('NOOR_BAKE_SPP', 768)),
            bounces=int(os.environ.get('NOOR_BOUNCES', 48)))
