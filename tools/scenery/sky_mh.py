"""The middle and high cloud skies of 0.12: composed skies, not even fields.

A real sky is never one kind of cloud evenly spread (the owner, of 0.11's: "that looks fake and artificial and
bland"). Structure comes first here: the cloud of a sky lies in a few large *mats* (see Mat), each with its own
outline, ragged edge, grain and size of cell, and wholly open sky between them. Inside a mat the cells have grown
together edge to edge over a thin sheet (the lanes between them are cracks, not blue sky), coarser at its heart, fine
and dissolving at its fringe, and parts of it are a translucent sheet with no cells at all. The cirrus is gathered
the same way. A set is baked as "the wide sky" (skybake.WIDE): 47 degrees to either side of the view's middle, so it
can drift across the phone's view; its clouds thin out over the last six degrees at each end, where the next set is
faded in.

    set NOOR_SET=mi_patches        (see SETS below)
    python tools/scenery/sky_mh.py -- plan <out dir>          (where the clouds stand, in seconds; no path tracing)
    blender -b --factory-startup -P tools/scenery/sky_mh.py -- preview|look|bakefast|bake|pack <out dir> [bearing height]

The cells are CGHEVEN's cumulus volumes (see sources/clouds_high.md; never in the repository), made small; the
sheets, veils and cirrus are procedural (cloudgen_high.py).
"""
import glob
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cloudgen_high as ch       # noqa: E402
from cloudgen_high import fbm, smooth, worley    # noqa: E402

ch.HALF_ANGLE = math.radians(50.0)          # the wide sky sees 47 degrees to either side
SET = os.environ.get('NOOR_SET', 'mi_patches')
ARGS = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
MODE = ARGS[0] if ARGS else 'preview'
OUT = ARGS[1] if len(ARGS) > 1 else '.'
if MODE != 'plan':                # (the quick look runs in plain Python, without Blender)
    import bake                  # noqa: E402
    import skybake               # noqa: E402
ICE_SIGMA = 0.0012        # extinction per metre of ice cloud where the density is 1
# (NOOR_SEED: another sky of the same kind. Where a kind is drawn by chance, the one kept was chosen among several.)
SEED = int(os.environ.get('NOOR_SEED', {'mi_bands': 2}.get(SET, 0)))
PUFF_DIR = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'cgheven')
PUFFS = ['cumulus_01', 'cumulus_11', 'cumulus_20', 'cumulus_32', 'cumulus_33', 'cumulus_35', 'cumulus_39', 'cumulus_43']
KM = 1000.0


def rot(x, y, deg):
    a = math.radians(deg)
    return x * math.cos(a) + y * math.sin(a), -x * math.sin(a) + y * math.cos(a)


def frac(a):
    return a - np.floor(a)


def bearing(x, y):
    """Degrees right of the view's middle."""
    return np.degrees(np.arctan2(x, y))


def ends(x, y):
    """1 in the sky's middle, falling to 0 over its last six degrees at either end."""
    return 1.0 - smooth(41.0, 47.0, np.abs(bearing(x, y)))


def wander(x, y, seed, scale=25000.0, reach=6500.0):
    """The place a little out of place, smoothly: streaks combed through it are no longer ruled lines."""
    return (x + (fbm(x, y, scale, 3, seed) - 0.5) * 2.0 * reach, y + (fbm(x, y, scale, 3, seed + 1) - 0.5) * 2.0 * reach)


# ---- Mats: where the cloud of a sky lies --------------------------------------------------------------------------------

class Mat:
    """One mat of cloud: an ellipse on the ground plan (centre x, y; half-axes a across and b along its own
    direction `turn`, all in km) with a torn outline, islands off its edge and bays into it. `grain`: the bearing its
    rows of cells lie along; `cell`: their spacing at its heart (metres); `long`, `stretch`, `stagger`: see
    cloudgen_high.rank_points (rolls: long < 1, stretch > 1, stagger near 0). `thin`: the share of it that is a
    translucent sheet without cells."""

    def __init__(self, x, y, a, b, turn=0.0, grain=30.0, cell=230.0, seed=1, long=1.2, stretch=1.0, stagger=0.45,
                 thin=0.22, rag=1.0, lobes=0.9, alt=0.0, tau=1.0, heart_at=(0.66, 0.90), body_at=(0.90, 1.06),
                 ring_at=(0.40, 0.70, 0.74, 1.08), fine=0.0, thin_scale=None):
        self.x, self.y, self.a, self.b, self.turn = x * KM, y * KM, a * KM, b * KM, turn
        self.grain, self.cell, self.seed, self.long, self.stretch, self.stagger = grain, cell, seed + 1000 * SEED, long, stretch, stagger
        self.thin_share, self.rag, self.lobes, self.alt, self.tau = thin, rag, lobes, alt, tau
        self.heart_at, self.body_at, self.ring_at, self.fine = heart_at, body_at, ring_at, fine
        self.thin_scale = thin_scale or 0.55 * min(self.a, self.b)

    def box(self):
        """A rectangle of the ground plan that holds all of it (metres: x0, x1, y0, y1)."""
        t = math.radians(self.turn)
        k = 1.12 + 0.45 * (self.rag + self.lobes)
        hx = k * math.hypot(self.a * math.cos(t), self.b * math.sin(t))
        hy = k * math.hypot(self.a * math.sin(t), self.b * math.cos(t))
        return (self.x - hx, self.x + hx, self.y - hy, self.y + hy)

    def d(self, X, Y):
        """0 at the mat's middle, 1 at its outline (torn), more outside."""
        u, w = rot(X - self.x, Y - self.y, self.turn)
        s = min(self.a, self.b)
        return (np.sqrt((u / self.a) ** 2 + (w / self.b) ** 2)
                + self.rag * 2.0 * (fbm(X, Y, 0.42 * s, 4, self.seed) - 0.5)
                + self.lobes * 2.0 * (fbm(X, Y, 1.25 * s, 3, self.seed + 1) - 0.5)
                + (self.fine * 2.0 * (fbm(X, Y, 2500.0, 3, self.seed + 2) - 0.5) if self.fine else 0.0))

    def body(self, X, Y):
        return (1.0 - smooth(self.body_at[0], self.body_at[1], self.d(X, Y))) * ends(X, Y)

    def heart(self, X, Y):
        return (1.0 - smooth(self.heart_at[0], self.heart_at[1], self.d(X, Y))) * ends(X, Y)

    def thin(self, X, Y):
        """Where the mat is only a sheet."""
        t = fbm(X, Y, self.thin_scale, 3, self.seed + 5)
        return smooth(0.5 + 0.30 * (0.5 - self.thin_share), 0.56 + 0.30 * (0.5 - self.thin_share), t)

    # the fields of its two lattices of cells (cover 0..1: how many of the lattice's cells stand, and how large)
    def heart_cells(self, X, Y, seed):
        return self.heart(X, Y) * (1.0 - self.thin(X, Y))

    def fringe_cells(self, X, Y, seed):
        d = self.d(X, Y)
        r0, r1, r2, r3 = self.ring_at
        ring = (1.0 - smooth(r2, r3, d)) * smooth(r0, r1, d)
        return ring * (1.0 - 0.8 * self.thin(X, Y)) * ends(X, Y)

    def sheet(self, X, Y):
        """The thin cloud its cells stand in (optical depth looking up, before `tau`)."""
        d = self.d(X, Y)
        h, t = self.heart(X, Y), self.thin(X, Y)
        # (Thin everywhere, a little milk in the heart, a veil where there are no cells; it fades out to the edge.)
        return self.tau * (1.0 - smooth(min(0.50, self.heart_at[0]), self.body_at[1], d)) * ends(X, Y) * (0.06 + 0.20 * h + 0.16 * t)


def sheet_of(mats, comb=30.0, seed=17, thick=260.0, sigma=0.0040):
    def pattern(x, y, v):
        u, w = rot(*wander(x, y, seed + 4, 12000.0, 3000.0), comb)
        streak = 0.55 + 0.45 * smooth(0.36, 0.70, fbm(u / 6.0, w, 2400.0, 3, seed))
        tau = sum(m.sheet(x, y) for m in mats)
        # (Thin, and in patches the size of a few cells: the cells must stand out against it, grey on white.)
        patch = 0.30 + 0.70 * smooth(0.42, 0.60, fbm(x, y, 700.0, 3, seed + 6))
        return (fbm(x, y, 9000.0, 2, seed + 2) - 0.5) * 100.0, np.full(x.shape, thick, np.float32), np.clip(tau * streak * patch * 0.6, 0.0, 2.5) / (sigma * thick)
    return pattern


def mask_of(mats, floor=0.0):
    def mask(x, y):
        m = np.zeros(np.broadcast(x, y).shape, np.float32)
        for k in mats:
            m = np.maximum(m, 1.0 - smooth(0.80, 1.15, k.d(x, y)))
        return floor + (1.0 - floor) * m
    return mask


# ---- Cirrus: bundles of fibres ---------------------------------------------------------------------------------------

class Fibres:
    """Cirrus: bundles of fibres laid over the sky as curves (see cloudgen_high.fibre_points). `mask(x, y)`: the
    share of heads kept at a place (0..1), to gather them where the sky wants them."""

    def __init__(self, seed, far, per_km2, heading, fields=(0.46, 0.62), length=(9000.0, 48000.0), spread=(700.0, 5200.0),
                 count=(24, 110), hook=0.95, strength=9.0, head=0.006, wander=0.06, thin_far=0.8, mask=None, fan=1.0, near=1.5):
        self.head = head
        rng = np.random.default_rng(seed)
        area = math.tan(ch.HALF_ANGLE) * (far / 1000.0) ** 2
        n = int(area * per_km2)
        y = far * np.sqrt(rng.random(n))
        x = y * math.tan(ch.HALF_ANGLE) * 1.15 * (2 * rng.random(n) - 1)
        xf, yf = x.astype(np.float32), y.astype(np.float32)
        f = fbm(xf, yf, 70000.0, 3, seed + 1) * 0.7 + fbm(xf, yf, 17000.0, 2, seed + 2) * 0.3
        p = smooth(fields[0], fields[1], f) * (1.0 - thin_far * smooth(50000.0, 230000.0, np.hypot(x, y)))
        if mask is not None:
            p = p * mask(xf, yf)
        keep = rng.random(n) < p
        self.heads = np.stack([x[keep], y[keep]], 1)
        m = len(self.heads)
        hx, hy = self.heads[:, 0].astype(np.float32), self.heads[:, 1].astype(np.float32)
        self.heading = math.radians(heading) + (fbm(hx, hy, 90000.0, 2, seed + 3) - 0.5) * fan + rng.normal(0, wander, m)
        self.length = np.exp(rng.uniform(math.log(length[0]), math.log(length[1]), m))
        self.spread = np.exp(rng.uniform(math.log(spread[0]), math.log(spread[1]), m)) * (self.length / 20000.0) ** 0.5
        self.count = rng.integers(count[0], count[1], m)
        self.hook = rng.normal(hook, 0.3, m) * np.where(rng.random(m) < 0.85, 1.0, -0.4)
        # (Cirrus high in the sky is looked through nearly straight, and is faint; a little more ice in what is
        # near keeps the upper sky from standing empty.)
        self.gain = rng.uniform(0.5, 1.3, m) * strength * (1.0 + near * np.exp(-np.hypot(hx, hy) / 30000.0))
        self.seeds = rng.integers(0, 2 ** 31, m)
        print('cirrus:', m, 'bundles,', int(self.count.sum()), 'fibres', flush=True)

    def tau(self, X, Y, v, width=55.0):
        """Optical depth (looking straight up) of the fibres over a grid: sharp, and softened."""
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
            nh = 600                                                                  # the head: a tuft of ice
            sp = 0.22 * self.spread[b] + 250.0
            c, sn = math.cos(self.heading[b]), math.sin(self.heading[b])
            a, d = rng.normal(0, sp * 1.6, nh), rng.normal(0, sp * 0.7, nh)
            px = np.concatenate([px, (hx + a * c - d * sn).astype(np.float32)])
            py = np.concatenate([py, (hy + a * sn + d * c).astype(np.float32)])
            w = np.concatenate([w, np.full(nh, self.head * L * self.count[b] / nh, np.float32)])
            xs.append(px); ys.append(py); ws.append(w * self.gain[b])
        if xs:
            out = ch.splat(np.concatenate(xs), np.concatenate(ys), np.concatenate(ws), X, Y, v)
        return ch.blur(out, max(width / v, 0.6), max(width / v, 0.6)), ch.blur(out, max(900.0 / v, 0.6), max(900.0 / v, 0.6))


_FIB = {}


def fibres(key, *a, **k):
    if key not in _FIB:
        _FIB[key] = Fibres(a[0] + 7919 * SEED, *a[1:], **k)
    return _FIB[key]


def ice(tau, thick=400.0):
    """Optical depth looking up -> (base, thickness, density) of an ice layer."""
    return np.zeros(tau.shape, np.float32), np.full(tau.shape, thick, np.float32), np.clip(tau, 0.0, 2.5) / (ICE_SIGMA * thick)


# ---- The middle skies -----------------------------------------------------------------------------------------------------
# (Places in km on the ground plan, the camera at the origin looking along +y. At 4.5 km up, 30 degrees over the
# horizon is 8 km away, 12 degrees 21 km, 5 degrees 50 km.)

# mi_patches: altocumulus in a few mats with open sky between: two near and high, two far and low, a scrap.
PATCHES = [
    Mat(-6.0, 12.5, 4.2, 7.5, turn=-10.0, grain=35.0, cell=215.0, seed=11, alt=0.0),
    Mat(3.0, 7.0, 3.4, 4.2, turn=20.0, grain=-28.0, cell=200.0, seed=21, long=0.62, stretch=1.45, stagger=0.12, alt=140.0, thin=0.30),
    Mat(8.0, 43.0, 19.0, 9.0, turn=10.0, grain=62.0, cell=320.0, seed=31, alt=-120.0, thin=0.15),
    Mat(-22.0, 30.0, 8.0, 6.5, turn=-30.0, grain=8.0, cell=290.0, seed=41, long=0.7, stretch=1.3, stagger=0.2, alt=60.0),
    Mat(12.5, 17.0, 2.3, 3.2, turn=35.0, grain=50.0, cell=190.0, seed=51, alt=90.0, thin=0.40, tau=0.8),
]

# mi_bands: altocumulus in bands that fan out of the horizon: long narrow mats, unevenly spaced, some broken short,
# their cells drawn out across them in rolls.
BAND_TURN = 76.0


def _band_mats():
    rng = np.random.default_rng(606 + SEED)
    out = []
    off = -26.0
    k = 0
    while off < 30.0:
        half = rng.uniform(1.2, 3.2) * (1.5 if rng.random() < 0.2 else 1.0)
        length = rng.uniform(26.0, 55.0) * (0.5 if rng.random() < 0.2 else 1.0)
        along = rng.uniform(18.0, 42.0)
        t = math.radians(BAND_TURN + rng.uniform(-7.0, 7.0))
        # the band's own axis runs along (cos t, sin t); it stands `off` km to the side of the line through us
        cx = along * math.cos(t) + off * math.sin(t)
        cy = along * math.sin(t) - off * math.cos(t)
        rolls = rng.random() < 0.65
        out.append(Mat(cx, cy, length, half, turn=math.degrees(t), grain=math.degrees(t) + (90.0 if rolls else 30.0),
                       cell=rng.uniform(210.0, 300.0), seed=600 + 7 * k, long=0.55 if rolls else 1.2,
                       stretch=1.6 if rolls else 1.0, stagger=0.10 if rolls else 0.45, thin=rng.uniform(0.10, 0.35),
                       rag=0.9, lobes=1.2, alt=rng.uniform(-150.0, 150.0)))
        off += half * 2.0 + rng.uniform(0.8, 3.8)
        k += 1
    return out


# mi_sheet: a deck over nearly the whole sky (altocumulus stratiformis opacus / altostratus with billows). From below
# such a deck is grey on grey: swells and billows of every size from eight kilometres down to a few hundred metres,
# the seams between them lighter and soft, never white, and only two or three real breaks. So it is one layer whose
# depth is a domain-warped fractal with a floor (the thinnest seam still hides most of the blue), thicker and darker
# to one side; its edge, which slants across the left of the sky, thins over some kilometres and is eaten by the
# same fractal into ragged fragments of every size; both ends of the window thin out the same way.
# (Earlier tries, kept out: modelled cells along the edge read as confetti; cellular pads read as bubble wrap, and
# their cracks went white.)
BREAKS = [(-3.5, 8.5, 1.7, 0.55), (9.0, 16.0, 3.4, 1.0), (16.0, 40.0, 8.0, 2.0)]      # x, y, half length, half width (km)
BREAK_TURN = 28.0                                                                    # they lie along one direction


def deck_fractal(x, y):
    """The deck's unevenness, 0..1 (crowding round 0.5): swells of 8 km down to billows of 400 m, drawn out a
    little along one direction, the whole warped so that nothing in it is ruled or round."""
    xa, ya = wander(x, y, 721, 9000.0, 2600.0)
    xa, ya = wander(xa, ya, 723, 2200.0, 520.0)
    u, w = rot(xa, ya, 20.0)
    return fbm(u / 1.7, w, 8000.0, 5, 725, gain=0.56)


def deck_cover(x, y):
    """How whole the deck is at a place, 0..1: 0 beyond its edge and at the window's ends, 1 well inside. The edge
    slants across the left of the sky (we stand some two kilometres inside it), wandering by a few kilometres."""
    inside = (x + 2500.0) * 0.94 + (y - 3200.0) * 0.34 + 9000.0 * (fbm(x, y, 20000.0, 3, 727) - 0.5) * 2.0
    edge = smooth(-500.0, 5500.0, inside)
    end = 1.0 - smooth(39.0, 47.0, np.abs(bearing(x, y)) + 3.0 * (fbm(x, y, 9000.0, 2, 729) - 0.5) * 2.0)
    return np.minimum(edge, end)


def sh_deck(x, y, v):
    # (The fractals crowd round their middle: both are spread over 0..1 first, or nothing below would bite.)
    f = np.clip((deck_fractal(x, y) - 0.27) / 0.45, 0.0, 1.0)
    fine = np.clip((fbm(x, y, 700.0, 3, 731) - 0.22) / 0.56, 0.0, 1.0)
    c = deck_cover(x, y)
    # Where the deck gives out it is eaten from its thin places over some six kilometres: what is left are fragments
    # of every size, fewer, smaller and thinner the further out, translucent before they are gone.
    fn = 0.75 * f + 0.25 * fine
    whole = smooth(0.0, 0.30, fn - (1.0 - 1.35 * c))
    lean = 0.12 + 0.88 * smooth(0.15, 0.85, c)                   # how deep what is left stands
    faint = 0.25 + 0.75 * smooth(0.10, 0.80, c)                  # and how dense
    # The breaks: long ragged openings. The deck thins into each over a kilometre or more (a rim of light grey,
    # not a bright line), and the last of it is eaten by the billows.
    for bx, by, ba, bb in BREAKS:
        u, w = rot(x - bx * KM, y - by * KM, BREAK_TURN)
        d = np.sqrt((u / (ba * KM)) ** 2 + (w / (bb * KM)) ** 2) + 0.7 * (fine - 0.5) + 0.5 * (f - 0.5)
        near = smooth(0.55, 2.4, d)
        lean = lean * (0.15 + 0.85 * near)
        faint = faint * (0.30 + 0.70 * near)
        whole = whole * smooth(0.55, 0.95, d)
    # Its depth: a floor everywhere (the thinnest seam still hides most of the blue), wide soft swells and billows
    # on it; thicker and darker to the right and far off. Looking up, about 3 at a seam to 18 at a swell's heart.
    side = 0.82 + 0.36 * smooth(-25000.0, 45000.0, 0.8 * x + 0.3 * y) + 0.16 * (fbm(x, y, 16000.0, 3, 733) - 0.5) * 2.0
    thick = (135.0 + 330.0 * smooth(0.05, 0.95, f)) * side * lean
    dens = (0.82 + 0.18 * fine) * faint * whole
    # (The billows hang down: the deck's top is nearly level, its underside is the relief.)
    base = -0.7 * (thick - 290.0) + (fbm(x, y, 12000.0, 2, 735) - 0.5) * 120.0
    return base, np.where(dens > 1e-3, thick, 0.0), dens


# mi_veil: altostratus, a grey fibrous veil, thick over part of the sky and thinning to windows and to nothing.
VEIL = [Mat(-4.0, 30.0, 46.0, 58.0, turn=-15.0, seed=91, rag=0.3, lobes=0.5)]


def ve_veil(x, y, v):
    u, w = rot(*wander(x, y, 97, 22000.0, 6000.0), 20.0)
    streak = smooth(0.30, 0.72, fbm(u / 9.0, w, 2600.0, 4, 91))
    bnd = smooth(0.25, 0.75, fbm(u / 5.0, w, 11000.0, 3, 96))
    mid = fbm(x, y, 15000.0, 3, 93)
    body = 0.45 + 0.55 * smooth(0.30, 0.75, fbm(x, y, 60000.0, 3, 92) * 0.6 + mid * 0.4)
    d = body * (0.40 + 0.45 * bnd + 0.40 * streak)
    d *= 0.22 + 0.78 * smooth(0.40, 0.58, fbm(x, y, 30000.0, 3, 94) * 0.8 + mid * 0.2)       # the windows
    d *= VEIL[0].body(x, y) * (0.35 + 0.65 * VEIL[0].heart(x, y))
    d *= 1.0 - 0.6 * smooth(40000.0, 96000.0, np.hypot(x, y))
    return (fbm(x, y, 20000.0, 2, 95) - 0.5) * 300.0, np.full(x.shape, 900.0, np.float32), np.clip(d, 0.0, 2.0)


# ---- The high skies (9.5 km up: 30 degrees is 16 km away, 12 degrees 42 km, 5 degrees 90 km) ---------------------------------

# hi_wisps: cirrus tufts and hooks in a few gatherings, some dense sprays, much open sky.
WISPS = [Mat(-14.0, 30.0, 14.0, 20.0, turn=-20.0, seed=141), Mat(16.0, 17.0, 8.0, 10.0, turn=25.0, seed=143),
         Mat(8.0, 95.0, 50.0, 30.0, turn=5.0, seed=145), Mat(-40.0, 75.0, 16.0, 22.0, turn=-35.0, seed=147)]


def hw_wisps(x, y, v):
    m = mask_of(WISPS, 0.05)
    every = (0.0, 0.01)                                     # (the gatherings place them, not a field)
    a = fibres('hw_a', 141, 330000.0, 0.11, 62.0, fields=every, length=(14000.0, 60000.0), spread=(500.0, 3600.0),
               count=(60, 240), strength=2.0, head=0.003, wander=0.22, thin_far=0.85, near=3.0, mask=m)
    b = fibres('hw_b', 191, 330000.0, 0.60, 28.0, fields=every, length=(2500.0, 13000.0), spread=(250.0, 1500.0),
               count=(14, 70), hook=1.3, strength=2.0, head=0.006, wander=0.55, thin_far=0.93, near=3.0, mask=m)
    c = fibres('hw_c', 231, 330000.0, 0.006, 50.0, fields=every, length=(40000.0, 95000.0), spread=(4000.0, 11000.0),
               count=(500, 1100), strength=1.4, head=0.002, wander=0.15, thin_far=0.5, mask=m)
    ta, sa = a.tau(x, y, v, width=26.0)
    tb, sb = b.tau(x, y, v, width=22.0)
    tc, sc = c.tau(x, y, v, width=30.0)
    return ice((ta + tb + tc + 0.20 * (sa + sb) + 0.35 * sc) * ends(x, y), 350.0)


# hi_sprays: long fibres streaming in from the left and fanning out across the sky; tufts under their near end.
SPRAY = [Mat(-34.0, 46.0, 26.0, 44.0, turn=-25.0, seed=311)]
TUFTS = [Mat(-10.0, 20.0, 9.0, 11.0, turn=10.0, seed=313), Mat(24.0, 60.0, 20.0, 16.0, turn=0.0, seed=315)]


def hs_sprays(x, y, v):
    every = (0.0, 0.01)
    a = fibres('hs_a', 311, 330000.0, 0.032, -6.0, fields=every, length=(50000.0, 170000.0), spread=(2500.0, 9000.0),
               count=(200, 620), hook=0.5, strength=2.4, head=0.0015, wander=0.10, thin_far=0.55, mask=mask_of(SPRAY, 0.03), fan=1.3)
    b = fibres('hs_b', 351, 330000.0, 0.5, 10.0, fields=every, length=(3000.0, 15000.0), spread=(300.0, 1600.0),
               count=(14, 60), hook=1.2, strength=2.2, head=0.006, wander=0.5, thin_far=0.9, mask=mask_of(TUFTS, 0.02))
    ta, sa = a.tau(x, y, v, width=28.0)
    tb, sb = b.tau(x, y, v, width=22.0)
    return ice((ta + tb + 0.30 * sa + 0.2 * sb) * ends(x, y), 350.0)


# hi_veil: cirrostratus: milk over most of the sky, thick in one broad part, thin or open in another, combed.
CSVEIL = [Mat(10.0, 60.0, 75.0, 95.0, turn=20.0, seed=411, rag=0.3, lobes=0.6)]


def hv_veil(x, y, v):
    f = fibres('hv', 411, 330000.0, 0.007, 55.0, fields=(0.40, 0.60), length=(20000.0, 70000.0), spread=(3000.0, 12000.0),
               count=(40, 140), hook=0.3, strength=4.0)
    sharp, soft = f.tau(x, y, v, width=140.0)
    u, w = rot(*wander(x, y, 419, 30000.0, 9000.0), 35.0)
    part = CSVEIL[0].body(x, y) * (0.30 + 0.70 * CSVEIL[0].heart(x, y))
    body = (0.05 + 0.27 * smooth(0.25, 0.80, fbm(x, y, 80000.0, 3, 415) * 0.65 + fbm(x, y, 20000.0, 3, 416) * 0.35)) * part
    comb = np.clip(0.75 + (fbm(u / 12.0, w, 1800.0, 3, 417) - 0.5), 0.3, 1.8)
    bnd = 0.7 + 0.6 * smooth(0.35, 0.65, fbm(u / 6.0, w, 26000.0, 3, 418))
    return ice((body * comb * bnd + 0.4 * sharp + 0.7 * soft) * ends(x, y), 500.0)


# hi_ripples: cirrocumulus in three mats: a fine grain of tiny cloudlets standing in rows, the rows fuller and
# thinner in ripples across the mat, blue between the grains; hardly any sheet; cirrus streaks between the mats.
RIPPLES = [
    Mat(-9.0, 24.0, 9.0, 13.0, turn=-15.0, grain=38.0, cell=150.0, seed=511, long=0.70, stretch=1.25, stagger=0.06, thin=0.10, tau=0.16),
    Mat(11.0, 17.0, 5.5, 7.0, turn=30.0, grain=-20.0, cell=135.0, seed=521, long=0.70, stretch=1.25, stagger=0.06, thin=0.10, tau=0.16),
    Mat(-4.0, 78.0, 34.0, 15.0, turn=8.0, grain=55.0, cell=290.0, seed=531, long=0.70, stretch=1.25, stagger=0.06, thin=0.10, tau=0.16),
]


def ripple(m, x, y):
    """Ripples across a mat's rows: 0.3 in a trough, 1 on a crest, the crests wandering and breaking."""
    u, w = rot(*wander(x, y, m.seed + 7, 5000.0, 1100.0), m.grain)
    wave = 0.5 + 0.5 * np.cos(2 * math.pi * w / (4.6 * m.cell) + 3.0 * (fbm(x, y, 12.0 * m.cell, 2, m.seed + 8) - 0.5))
    return 0.34 + 0.66 * smooth(0.25, 0.75, wave) * (0.60 + 0.40 * smooth(0.40, 0.60, fbm(x, y, 9.0 * m.cell, 2, m.seed + 9)))


def ripple_heart(m):
    return lambda x, y, seed: m.heart_cells(x, y, seed) * ripple(m, x, y)


def ripple_fringe(m):
    return lambda x, y, seed: m.fringe_cells(x, y, seed) * ripple(m, x, y)


RIPPLE_H = dict(size=(0.62, 0.42), keep=(0.10, 1.20), old=0.05, least=0.12)
RIPPLE_F = dict(size=(0.42, 0.56), keep=(-0.10, 1.25), old=0.22, least=0.15)


def hr_ice(x, y, v):
    out = lambda px, py: 1.0 - 0.9 * mask_of(RIPPLES)(px, py)        # noqa: E731
    a = fibres('hr_a', 551, 330000.0, 0.30, 40.0, fields=(0.45, 0.60), length=(9000.0, 45000.0), spread=(500.0, 3000.0),
               count=(40, 160), strength=2.2, head=0.003, wander=0.2, mask=out)
    ta, sa = a.tau(x, y, v, width=26.0)
    u, w = rot(*wander(x, y, 557, 14000.0, 3000.0), 38.0)
    comb = np.clip(0.75 + (fbm(u / 10.0, w, 1500.0, 3, 559) - 0.5), 0.3, 1.8)
    return ice((ta + 0.25 * sa + sum(m.sheet(x, y) for m in RIPPLES) * comb) * ends(x, y), 350.0)


# hi_mixed: a mat of cirrocumulus on the left, cirrus across the middle, a thin veil on the right.
MIXED = [Mat(-15.0, 22.0, 8.5, 12.0, turn=-25.0, grain=20.0, cell=145.0, seed=611, long=0.62, stretch=1.2, stagger=0.05, thin=0.35, tau=0.45)]
MIX_CI = [Mat(4.0, 34.0, 16.0, 26.0, turn=15.0, seed=651), Mat(-8.0, 100.0, 40.0, 30.0, turn=0.0, seed=653)]
MIX_VEIL = [Mat(42.0, 52.0, 30.0, 50.0, turn=-20.0, seed=655, rag=0.3, lobes=0.6)]


def hm_ice(x, y, v):
    a = fibres('hm_a', 651, 330000.0, 0.40, 70.0, fields=(0.0, 0.01), length=(12000.0, 70000.0), spread=(500.0, 4200.0),
               count=(60, 260), strength=2.4, head=0.003, wander=0.25, mask=mask_of(MIX_CI, 0.03))
    ta, sa = a.tau(x, y, v, width=26.0)
    u, w = rot(*wander(x, y, 661, 30000.0, 9000.0), 50.0)
    comb = np.clip(0.75 + (fbm(u / 12.0, w, 1800.0, 3, 659) - 0.5), 0.3, 1.8)
    veil = MIX_VEIL[0].body(x, y) * (0.05 + 0.24 * MIX_VEIL[0].heart(x, y)) * comb
    u2, w2 = rot(*wander(x, y, 663, 14000.0, 3000.0), 20.0)
    comb2 = np.clip(0.75 + (fbm(u2 / 10.0, w2, 1500.0, 3, 665) - 0.5), 0.3, 1.8)
    return ice((ta + 0.25 * sa + veil + MIXED[0].sheet(x, y) * comb2) * ends(x, y), 400.0)


# ---- The sets -----------------------------------------------------------------------------------------------------------

# (Cells deep enough to shade themselves: under a high Sun each has a grey heart and underside and a bright edge.)
MID_PUFF = dict(near=3300.0, far=96000.0, altitude=4500.0, sigma=0.11, g=0.72, step=12.0, flat=0.78, jitter=0.36)
MID_LAYER = dict(altitude=4500.0, near=3300.0, far=100000.0, voxel=16.0, sigma=0.0040, g=0.72, colour=(1.0, 1.0, 1.0),
                 vz=lambda v: min(max(v * 0.5, 30.0), 120.0), fade=(0.90, 0.99), growth=1.8)
HIGH_LAYER = dict(altitude=9500.0, near=7400.0, far=210000.0, voxel=22.0, sigma=ICE_SIGMA, g=0.50, colour=(0.985, 0.992, 1.0),
                  vz=lambda v: min(max(v * 0.5, 40.0), 160.0), fade=(0.40, 0.95), growth=2.0)
HIGH_PUFF = dict(near=7400.0, far=150000.0, altitude=9500.0, sigma=0.022, g=0.50, step=8.0, flat=0.36, jitter=0.30,
                 colour=(0.985, 0.992, 1.0))

# (The grains of hi_ripples: small, apart, and dense enough to be seen each for itself.)
RIPPLE_PUFF = dict(HIGH_PUFF, sigma=0.10, step=7.0, flat=0.58)


def P(base, **k):
    return ('puffs', dict(base, **k))


def L(base, **k):
    return ('layer', dict(base, **k))


def mat_parts(mats, base, heart=None, fringe=None, hk=None, fk=None):
    """The two lattices of every mat: the cells of its heart, grown together, and the finer ones of its fringe,
    smaller and fewer towards the edge, many of them dissolving. `heart(m)`, `fringe(m)`: other fields for a mat's
    lattices; `hk`, `fk`: other settings for them."""
    parts = []
    for i, m in enumerate(mats):
        common = dict(long=m.long, turn=m.grain, stretch=m.stretch, stagger=m.stagger, altitude=base['altitude'] + m.alt, box=m.box())
        parts.append(P(base, **dict(dict(name='h%d' % i, seed=m.seed, cell=m.cell, size=(0.95, 0.75), keep=(0.15, 1.2), old=0.04,
                                         fields=heart(m) if heart else m.heart_cells, **common), **(hk or {}))))
        parts.append(P(base, **dict(dict(name='f%d' % i, seed=m.seed + 3, cell=m.cell * 0.60, size=(0.60, 0.95), keep=(-0.10, 1.25),
                                         old=0.24, fields=fringe(m) if fringe else m.fringe_cells,
                                         **dict(common, altitude=common['altitude'] + 40.0)), **(fk or {}))))
    return parts


def sets():
    bands = _band_mats()
    return {
        'mi_patches': dict(info={'layer': 1, 'level': 'mid', 'altitude': 4.6, 'kind': 'altocumulus in mats'}, spp=256, ql=40,
                           parts=mat_parts(PATCHES, MID_PUFF) + [L(MID_LAYER, name='sheet', pattern=sheet_of(PATCHES))]),
        'mi_bands': dict(info={'layer': 1, 'level': 'mid', 'altitude': 4.6, 'kind': 'altocumulus in bands'}, spp=256, ql=40,
                         parts=mat_parts(bands, MID_PUFF) + [L(MID_LAYER, name='sheet', pattern=sheet_of(bands, comb=BAND_TURN, seed=67))]),
        'mi_sheet': dict(info={'layer': 1, 'level': 'mid', 'altitude': 4.6, 'kind': 'altocumulus deck'}, spp=128, ql=40, bounces=14,
                         # (One layer, no modelled cells. A deck this thick wants few bounces: its grey is right.)
                         parts=[L(MID_LAYER, name='deck', pattern=sh_deck, sigma=0.034, voxel=14.0, growth=1.6)]),
        'mi_veil': dict(info={'layer': 1, 'level': 'mid', 'altitude': 5.0, 'kind': 'altostratus'}, spp=448, ql=62, parts=[
            L(MID_LAYER, name='veil', pattern=ve_veil, altitude=4900.0, voxel=40.0, fade=(0.60, 0.97), growth=2.0, sigma=0.0011,
              vz=lambda v: min(max(v * 0.5, 60.0), 200.0))]),
        'hi_wisps': dict(info={'layer': 2, 'level': 'high', 'altitude': 9.7, 'kind': 'cirrus'}, spp=448, ql=62, parts=[
            L(HIGH_LAYER, name='ci', pattern=hw_wisps)]),
        'hi_sprays': dict(info={'layer': 2, 'level': 'high', 'altitude': 9.7, 'kind': 'cirrus sprays'}, spp=448, ql=62, parts=[
            L(HIGH_LAYER, name='ci', pattern=hs_sprays)]),
        'hi_veil': dict(info={'layer': 2, 'level': 'high', 'altitude': 9.7, 'kind': 'cirrostratus'}, spp=448, ql=62, parts=[
            L(HIGH_LAYER, name='cs', pattern=hv_veil, voxel=45.0)]),
        'hi_ripples': dict(info={'layer': 2, 'level': 'high', 'altitude': 9.5, 'kind': 'cirrocumulus and cirrus'}, spp=320, ql=46,
                           parts=mat_parts(RIPPLES, RIPPLE_PUFF, heart=ripple_heart, fringe=ripple_fringe, hk=RIPPLE_H, fk=RIPPLE_F)
                           + [L(HIGH_LAYER, name='ice', pattern=hr_ice)]),
        'hi_mixed': dict(info={'layer': 2, 'level': 'high', 'altitude': 9.6, 'kind': 'cirrus, cirrocumulus, cirrostratus'}, spp=320, ql=46,
                         parts=mat_parts(MIXED, HIGH_PUFF) + [L(HIGH_LAYER, name='ice', pattern=hm_ice)]),
    }


SETS = sets()


# ---- Building -----------------------------------------------------------------------------------------------------------

def puff_files():
    """The modelled clouds, reduced once to small grids (kept with the work files)."""
    keep = os.path.join(os.path.dirname(OUT.rstrip('/\\')), 'puffs')
    os.makedirs(keep, exist_ok=True)
    out = []
    for name in PUFFS:
        dst = os.path.join(keep, name + '.vdb')
        if not os.path.exists(dst):
            src = glob.glob(os.path.join(PUFF_DIR, name, '*.vdb'))[0]
            print('reduced', name, ch.reduce_vdb(src, dst), flush=True)
        out.append(dst)
    return out


def puff_points(cfg):
    """Where the cells of one lattice stand, and how: x, y, three random numbers, the cover there."""
    x, y, r1, r2, r3 = ch.rank_points(cfg['seed'], cfg['cell'], cfg['long'], cfg['turn'], cfg['near'], cfg['far'],
                                      jitter=cfg['jitter'], stagger=cfg['stagger'], box=cfg.get('box'))
    cover = cfg['fields'](x, y, cfg['seed'])
    k0, k1 = cfg['keep']
    there = (r1 < k0 + k1 * cover) & (cover > cfg.get('least', 0.02))      # (`least`: no stray cells where there is next to nothing)
    return tuple(a[there] for a in (x, y, r1, r2, r3, cover))


_DATA = {}


def put_puffs(cfg):
    import bpy
    seed = cfg['seed']
    files = puff_files()
    x, y, r1, r2, r3, cover = puff_points(cfg)
    if not len(x):
        return
    old = r3 < cfg['old']                                                               # these are dissolving
    width = cfg['cell'] * (cfg['size'][0] + cfg['size'][1] * cover) * (0.80 + 0.45 * r2)
    r = np.hypot(x, y)
    z = cfg['altitude'] + (fbm(x, y, 9000.0, 2, seed + 10) - 0.5) * 100.0 - r * r / (2.0 * ch.EARTH)
    col = cfg.get('colour', (1.0, 1.0, 1.0))
    key = (cfg['sigma'], cfg['g'], col, cfg['step'])
    if key not in _DATA:                        # (every lattice of a set shares the same few volumes and materials)
        mats = [skybake.cloud_material('Cloudlet%d' % len(_DATA), sigma=cfg['sigma'], anisotropy=cfg['g'], colour=col),
                skybake.cloud_material('CloudletOld%d' % len(_DATA), sigma=cfg['sigma'] * 0.5, anisotropy=cfg['g'], colour=col)]
        data = []
        for kind in (0, 1):
            for f in files:
                vol = bpy.data.volumes.new(os.path.basename(f)[:-4] + ('_old' if kind else ''))
                vol.filepath = f
                vol.materials.append(mats[kind])
                vol.render.space = 'WORLD'
                vol.render.step_size = cfg['step']
                data.append(vol)
        _DATA[key] = data
    data = _DATA[key]
    coll = bpy.data.collections.new('Cloudlets_' + cfg['name'])
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
            ob.rotation_euler = (0.0, 0.0, a + math.pi / 2 + (float(r1[k]) - 0.5) * 0.3)
            ob.scale = (wd * cfg['stretch'] * float(mirror[k]), wd, wd * cfg['flat'] * (0.45 if old[k] else 1.0))
        else:
            ob.rotation_euler = (0.0, 0.0, float(turn[k]))
            ob.scale = (wd * float(mirror[k]), wd, wd * cfg['flat'] * (0.45 if old[k] else 1.0))
        coll.objects.link(ob)
    print(SET, cfg['name'], ':', len(x), 'cells,', int(old.sum()), 'of them dissolving', flush=True)


def put_layer(cfg):
    vdb = os.path.join(OUT, 'vdb')
    os.makedirs(vdb, exist_ok=True)
    # (NOOR_VER: a new name for the grids after a pattern has changed, so that old ones are not read again.)
    prefix = os.path.join(vdb, SET + '_' + cfg['name'] + os.environ.get('NOOR_VER', ''))
    paths = sorted(glob.glob(prefix + '_*.vdb'))
    if not paths:
        paths = ch.write_layer(prefix, cfg['pattern'], cfg['altitude'], cfg['near'], cfg['far'], cfg['voxel'],
                               vz_of=cfg.get('vz'), fade=cfg.get('fade', (0.62, 1.0)), growth=cfg.get('growth', 2.0))
    mat = skybake.cloud_material('Cloud_' + SET + '_' + cfg['name'], sigma=cfg['sigma'], anisotropy=cfg['g'], colour=cfg['colour'])
    vzs = [(cfg.get('vz') or (lambda v: max(v * 0.5, 20.0)))(v) for _, _, v in ch.bands(cfg['near'], cfg['far'], cfg['voxel'], cfg.get('growth', 2.0))]
    for p in paths:
        ob = skybake.add_volume(p, (0.0, 0.0, 0.0), mat)
        n = int(os.path.splitext(p)[0].rsplit('_', 1)[1])
        try:
            ob.data.render.space = 'WORLD'
            ob.data.render.step_size = 2.0 * vzs[n]            # (the scene's step rate of 0.5 halves it again)
        except Exception as e:
            print('step size not set:', e, flush=True)


def build():
    for kind, cfg in SETS[SET]['parts']:
        (put_puffs if kind == 'puffs' else put_layer)(cfg)


if MODE == 'plan':
    # A quick look at where the clouds stand, without the path tracer: the wide sky's picture (6 pixels a degree),
    # each part's opacity roughly as the eye would meet it. For composing; says nothing of light.
    from dunesim import write_png
    ppd = 6.0
    az = (np.arange(int(94 * ppd), dtype=np.float32) + 0.5) / ppd - 47.0
    el = 40.0 - (np.arange(int(40 * ppd), dtype=np.float32) + 0.5) / ppd
    AZ, EL = np.meshgrid(np.radians(az), np.radians(el))
    te = np.tan(EL)
    clear = np.ones(AZ.shape, np.float32)
    count = 0
    for kind, cfg in SETS[SET]['parts']:
        alt = cfg['altitude']
        r = ch.EARTH * (-te + np.sqrt(te * te + 2.0 * alt / ch.EARTH))
        x, y = (r * np.sin(AZ)).astype(np.float32), (r * np.cos(AZ)).astype(np.float32)
        slope = te + r / ch.EARTH
        sin_e = np.clip(slope / np.sqrt(1.0 + slope * slope), 0.02, 1.0)
        if kind == 'puffs':
            c = cfg['fields'](x, y, cfg['seed'])
            k0, k1 = cfg['keep']
            n = np.clip(k0 + k1 * c, 0.0, 1.0) * (c > cfg.get('least', 0.02))
            wdt = cfg['size'][0] + cfg['size'][1] * c
            area = np.clip(n * 0.785 * wdt * wdt * cfg['stretch'] / cfg['long'], 0.0, 1.0)      # share of the sky seen from below
            tall = cfg['flat'] * wdt * cfg['cell']                                             # and their height closes the gaps
            op = 1.0 - (1.0 - area) ** (1.0 + tall / (cfg['cell'] * cfg['long']) / sin_e)
            g = 0.80 + 0.40 * (fbm(x, y, cfg['cell'] * 1.2, 2, 5) - 0.5) * 2.0                  # a grain, so the eye reads cells
            op = np.clip(op * g, 0.0, 1.0)
            count += len(puff_points(cfg)[0])
        else:
            # (A layer's pattern wants a regular grid of places: it is worked out on one, coarsely, and looked up.)
            v0 = 320.0 if alt > 7000.0 else 160.0
            reach = min(cfg['far'], 150000.0)
            gx = np.arange(-reach * 1.2, reach * 1.2, v0, dtype=np.float32) + 0.5 * v0
            gy = np.arange(cfg['near'], reach, v0, dtype=np.float32) + 0.5 * v0
            GX, GY = np.meshgrid(gx, gy)
            _, thick, dens = cfg['pattern'](GX, GY, v0)
            tv = thick * dens * cfg['sigma']
            ix = np.clip(((x - gx[0]) / v0 + 0.5).astype(np.int64), 0, len(gx) - 1)
            iy = np.clip(((y - gy[0]) / v0 + 0.5).astype(np.int64), 0, len(gy) - 1)
            inside = (np.abs(x) < reach * 1.2) & (y < reach) & (y > cfg['near'])
            op = np.where(inside, 1.0 - np.exp(-tv[iy, ix] / sin_e), 0.0)
        r_ok = (np.hypot(x, y) < cfg['far']) & (y > cfg['near'])
        clear *= 1.0 - np.where(r_ok, op, 0.0)
    op = 1.0 - clear
    lo, hi = int((40 - 30) * ppd), int((40 - 2) * ppd)
    up0, up1 = int((40 - 36) * ppd), int((40 - 13) * ppd)
    print('%s: %d cells; upper sky (13-36 degrees) L %.2f M %.2f R %.2f; coverSeen about %.2f (windows L %.2f M %.2f R %.2f)' % (
        SET, count, float(op[up0:up1, :int(47 * ppd)].mean()), float(op[up0:up1, int(23.5 * ppd):int(70.5 * ppd)].mean()),
        float(op[up0:up1, int(47 * ppd):].mean()), float(op[lo:hi].mean()), float(op[lo:hi, :int(47 * ppd)].mean()),
        float(op[lo:hi, int(23.5 * ppd):int(70.5 * ppd)].mean()), float(op[lo:hi, int(47 * ppd):].mean())), flush=True)
    rows = np.linspace(0, 1, op.shape[0], dtype=np.float32)[:, None, None] ** 2
    sky = np.array([0.20, 0.40, 0.75], np.float32) * (1 - rows) + np.array([0.62, 0.76, 0.90], np.float32) * rows
    img = sky * (1 - op[..., None]) + op[..., None] * 0.93
    img[:, int(23.5 * ppd)] *= 0.8; img[:, int(47 * ppd)] *= 0.8; img[:, int(70.5 * ppd)] *= 0.8
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, 'plan%s.png' % os.environ.get('NOOR_TAG', ''))
    write_png(path, (np.clip(img, 0, 1) ** (1 / 2.2) * 255).astype(np.uint8))
    sys.exit(0)

# (The lit pictures bear far more compression than the opacity: NOOR_QL for them, NOOR_QA for a.webp.)
_save8 = bake.save8


def _save(path, arr, quality=None, **k):
    name = os.path.basename(path)
    if name.endswith('.webp'):
        quality = int(os.environ.get('NOOR_QA', 84)) if name == 'a.webp' else int(os.environ.get('NOOR_QL', SETS[SET]['ql']))
    return _save8(path, arr, quality=quality, **k) if quality is not None else _save8(path, arr, **k)


bake.save8 = _save

if MODE == 'pack':
    skybake.bake_pack(os.path.join(OUT, 'raw'), os.path.join(OUT, 'sky'), skybake.lights(), SETS[SET]['info'])
    sys.exit(0)

skybake.run(build, SETS[SET]['info'], samples=int(os.environ.get('NOOR_BAKE_SPP', SETS[SET]['spp'])),
            bounces=int(os.environ.get('NOOR_BOUNCES', SETS[SET].get('bounces', 22))))
