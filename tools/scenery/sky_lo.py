"""The low skies of the home screen (0.12): eight skies of low cloud, each composed as a real sky is.

    NOOR_SET=<id> blender -b --factory-startup -P tools/scenery/sky_lo.py -- plan|preview|look|bakefast|bake|pack <out dir> [bearing height]

    lo_few              a few small cumulus in a group or two, the rest blue
    lo_scattered        scattered cumulus of every size: clusters, lanes, streets, one or two large
    lo_towers           scattered cumulus, a group of towering cumulus in one third of the sky
    lo_broken           rafts of stratocumulus and heaped cumulus, ragged holes between
    lo_front            nearly clear at one end, thickening to a dense bank at the other
    lo_storm            a cumulonimbus under its anvil, flanking towers, scud; a clear slot elsewhere
    lo_overcast_breaks  stratocumulus overcast, a few breaks, a slit of clear sky along part of the horizon
    lo_overcast         a grey overcast

The owner's verdict on 0.11's clouds: "It's not the entire sky is just one type of cloud and they're all evenly
spaced apart. No, that looks fake and artificial and bland. In real life there's variety." So nothing here is an even
sprinkling: every sky has a structure larger than its clouds (groups and empty quarters, streets, rafts and holes, a
dense side and an open side), clouds of very mixed sizes that are largest where the cloud is thickest, and more
than one kind of cloud.

A set is a window of the sky twice as wide as the phone's view (skybake.py, "the wide sky": 47 degrees to either
side of the middle, from the horizon to 40 degrees up), and drifts across the view from one end to the other. So
every 47 degrees of it must be a picture by itself, and it thins to nothing over its last 6 degrees at either end,
where the next set is faded in. The Sun is nobody's business here: the phone parts the cloud for it.

The heaped clouds are CGHEVEN's cloud volumes (tools/scenery/sources/clouds_low.md: free to use in finished work,
never to be passed on; they stay outside the repository), set out with sky_low.py's `set_out`; the sheets and the
anvil are our own, from maps (`layer_pieces`). `plan` draws where the heaped clouds will stand (as plain ovals) and
says how much of the sky they cover, in seconds, before anything is rendered.
"""
import json
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake               # noqa: E402
import cloudgen           # noqa: E402
import skybake            # noqa: E402
import sky_low as L       # noqa: E402
import sky_low2 as L2     # noqa: E402

SET = os.environ.get('NOOR_SET', 'lo_scattered')
MODE = bake.args()[0]
WORK = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'work', 'clouds_lo')
L.LIB = os.path.join(WORK, 'lib')
AZ = skybake.AZ_HALF                      # degrees to either side of the middle that a set covers
END = 6.0                                 # degrees at either end over which it thins to nothing
L.HALF = math.radians(AZ + 2.0)           # (how far to either side sky_low's layers are made)
BASE = L.BASE
FLAT = ['cumulus_32', 'cumulus_35', 'cumulus_39', 'cumulus_43']        # fair-weather cumulus: wide and shallow
HEAPED = ['cumulus_01', 'cumulus_11', 'cumulus_20', 'cumulus_33']      # taller heads
PLAN = MODE == 'plan'
PLANNED = []                              # [(clouds, base, flatten)] when only planning


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def bearing(x, y):
    return np.degrees(np.arctan2(x, np.maximum(y, 1.0)))


def ends(b):
    """1 inside the set, falling to nothing over its last END degrees."""
    return smooth(AZ - 0.5, AZ - END, np.abs(b))


def tallness(name):
    h = L.HERO[name]
    return (h['y'][1] - h['base']) / max(h['x'][1] - h['x'][0], h['z'][1] - h['z'][0])


class Taken:
    """The clouds already standing (so that later ones keep clear of them)."""

    def __init__(self):
        self.x, self.y, self.w, self.n = np.zeros(40000), np.zeros(40000), np.zeros(40000), 0

    def add(self, x, y, w):
        self.x[self.n], self.y[self.n], self.w[self.n] = x, y, w
        self.n += 1

    def clear_of(self, x, y, w, spacing):
        n = self.n
        return not (n and np.any((x - self.x[:n]) ** 2 + (y - self.y[:n]) ** 2 < (spacing * (w + self.w[:n])) ** 2))


def place(rng, weight, mix, cover, taken, near=2300.0, far=70000.0, thin=24000.0, fade=11000.0, small=0.03, big=0.30,
          spacing=0.55, grow=1.0, merge=0.38):
    """Where the heaped clouds of a sky stand. `weight(x, y)` (metres, arrays): the sky's structure, 0 where there
    is to be no cloud .. 1 where it is thickest. `cover`: the share of the ground under cloud where the weight is 1
    (out to `thin` metres; beyond, the field thins out: far cloud is lost in the haze and the horizon must not
    close). `mix`: [(share of the cloud, [names], width from, width to[, no nearer than[, top no higher than
    (degrees)]])]. Clouds are many small and few large, and larger where the weight is high (`grow`); where it is
    high they also stand closer, and grow together into larger, irregular masses (`merge`). None is set
    where it would look smaller than `small` or larger than `big` (width over distance). Returns [(name, x, y,
    width, turn, mirrored)]."""
    az = math.radians(AZ)

    def thinning(r):
        return np.where(r < thin, 1.0, np.exp(-(r - thin) / fade))

    r = np.sqrt(rng.uniform(near ** 2, far ** 2, 60000))
    b = rng.uniform(-az, az, 60000)
    eff = float(np.mean(np.clip(weight(r * np.sin(b), r * np.cos(b)), 0, 1) * ends(np.degrees(b)) * thinning(r))) * az * (far ** 2 - near ** 2)
    out = []
    for entry in mix:
        share, names, w0, w1 = entry[:4]
        nearest = entry[4] if len(entry) > 4 and entry[4] else near
        top = entry[5] if len(entry) > 5 else None
        want, have, tries = cover * share * eff, 0.0, 0
        while have < want and tries < 1500000:
            n = 8192
            tries += n
            r = np.sqrt(rng.uniform(nearest ** 2, far ** 2, n))
            b = rng.uniform(-az, az, n)
            x, y = r * np.sin(b), r * np.cos(b)
            wt = np.clip(weight(x, y), 0, 1)
            keep = np.nonzero(rng.uniform(size=n) < wt * ends(np.degrees(b)) * thinning(r))[0]
            for i in keep:
                if have >= want:
                    break
                e = max(2.0 - 1.1 * grow * wt[i], 0.55)
                w = w0 * (w1 / w0) ** (rng.uniform() ** e)
                if w < small * r[i] or w > big * r[i]:
                    continue
                if abs(math.degrees(b[i])) + math.degrees(math.atan(0.5 * w / r[i])) > AZ - 0.3:
                    continue
                name = names[int(rng.integers(0, len(names)))]
                if top and BASE + tallness(name) * w - skybake.drop(r[i]) > r[i] * math.tan(math.radians(top)):
                    continue
                if not taken.clear_of(x[i], y[i], w, spacing * (1.0 - merge * wt[i])):
                    continue
                taken.add(x[i], y[i], w)
                out.append((name, float(x[i]), float(y[i]), float(w), float(rng.uniform(0, 2 * math.pi)), bool(rng.uniform() < 0.5)))
                have += 0.45 * w * w
    return out


def scatter(rng, weight, cover, taken, far=60000.0, thin=25000.0, fade=11000.0, largest=5600.0, overhead=1.0):
    """Scattered cumulus by a structure: over us (out to 9 km: the clouds that stand high in the picture, few and
    large in it) and beyond, each with its own share, so that the sky overhead is as full as the structure says and
    not emptied in favour of the horizon."""
    near = place(rng, weight, [(0.45, FLAT, 300.0, 900.0), (0.55, FLAT + HEAPED, 900.0, 2600.0)], cover * overhead, taken,
                 near=1900.0, far=9000.0, thin=9000.0, fade=1.0, small=0.06, big=0.46, spacing=0.55)
    out = place(rng, weight, [(0.30, FLAT, 350.0, 1300.0), (0.45, FLAT + HEAPED, 1300.0, 3200.0, 4500.0), (0.25, HEAPED, 3200.0, largest, 9000.0)],
                cover, taken, near=8500.0, far=far, thin=thin, fade=fade, small=0.03, big=0.27, spacing=0.52)
    return near + out + scraps(rng, near + out, taken)


def scraps(rng, clouds, taken):
    """Torn scraps round the larger clouds: a heap of cumulus is never alone, it sheds and gathers small cloud at
    its edges."""
    out = []
    for name, x, y, w, _, _ in clouds:
        if w < 1300.0:
            continue
        r = math.hypot(x, y)
        for _ in range(int(rng.integers(0, 4))):
            a = rng.uniform(0, 2 * math.pi)
            d = w * rng.uniform(0.50, 0.95)
            sw = w * rng.uniform(0.13, 0.36)
            sx, sy = x + d * math.cos(a), y + d * math.sin(a)
            sr = math.hypot(sx, sy)
            if sw < 0.022 * sr or sr < 1800.0 or abs(math.degrees(math.atan2(sx, sy))) > AZ - END:
                continue
            taken.add(sx, sy, sw)
            out.append((FLAT[int(rng.integers(0, len(FLAT)))], sx, sy, sw, float(rng.uniform(0, 2 * math.pi)), bool(rng.uniform() < 0.5)))
    return out


def at(name, b, r, w, turn=0.0, mirrored=False, taken=None):
    """One cloud by hand: at bearing b (degrees), r metres off, w metres wide."""
    x, y = r * math.sin(math.radians(b)), r * math.cos(math.radians(b))
    if taken is not None:
        taken.add(x, y, w)
    return (name, x, y, w, turn, mirrored)


TOWER_DENSITY = 0.85      # of a small cumulus's (sky_low set towers out at half: their shaded sides came out pale)


def set_out(clouds, sigma=L.SIGMA, anisotropy=0.5, base=BASE, flatten=1.0, rng=None, coarser=0):
    """sky_low.set_out (which see), with the towering clouds nearly as dense as the small ones: a tower kilometres
    deep is the most solid thing in the sky, brilliant where the Sun strikes it and properly grey in its shaded
    folds and at its base."""
    if PLAN:
        PLANNED.append((clouds, base, flatten))
        return
    from mathutils import Matrix, Vector
    rng = rng or np.random.default_rng(1)
    made, mats = {}, {}
    for name, x, y, w, turn, mirrored in clouds:
        h = L.HERO[name]
        k = w / max(h['x'][1] - h['x'][0], h['z'][1] - h['z'][0])          # metres to the file's unit
        dist = math.hypot(x, y)
        want = dist * 0.0016 / L.SCALE                                      # two pixels of the picture to a voxel
        lod = int(np.clip(math.floor(math.log2(max(want / (h['voxel'] * k), 1.0))) + coarser, 0, len(h['lods']) - 1))
        key = (name, lod)
        if key not in made:
            vol = bpy.data.volumes.new('%s_%d' % key)
            vol.filepath = h['lods'][lod]
            vol.materials.append(None)
            made[key] = vol
        ob = bpy.data.objects.new(name, made[key])
        bpy.context.scene.collection.objects.link(ob)
        thin = TOWER_DENSITY if name.startswith('congestus') else 1.0
        rung = (int(round(math.log(k) / math.log(1.15))), thin)
        if rung not in mats:
            mats[rung] = skybake.cloud_material('Cloud x%d %g' % rung, sigma=sigma * thin * 1.15 ** rung[0], anisotropy=anisotropy)
        ob.material_slots[0].link = 'OBJECT'
        ob.material_slots[0].material = mats[rung]
        tall = k * flatten * float(rng.uniform(0.85, 1.2))
        m = Matrix.Rotation(turn, 4, 'Z') @ Matrix.Rotation(math.pi / 2, 4, 'X') @ Matrix.Diagonal((-k if mirrored else k, tall, k, 1.0))
        mid = Vector((0.5 * (h['x'][0] + h['x'][1]), h['base'], 0.5 * (h['z'][0] + h['z'][1])))
        ob.matrix_world = Matrix.Translation(Vector((x, y, base - skybake.drop(dist))) - (m @ mid)) @ m
    print(len(clouds), 'clouds,', len(made), 'volumes', flush=True)


def layer_pieces(*a, **kw):
    if not PLAN:
        L.layer_pieces(*a, **kw)


def add_volume(*a, **kw):
    if not PLAN:
        skybake.add_volume(*a, **kw)


def plan_picture(out):
    """The heaped clouds as plain ovals in the set's own picture (half size): where they stand, how much of the sky
    they cover. (The sheets are not drawn: they are judged in previews.)"""
    w, h = int(AZ * 2 * skybake.PX_PER_DEGREE / 2), int((skybake.EL_MAX - skybake.EL_MIN) * skybake.PX_PER_DEGREE / 2)
    azs = (np.arange(w) + 0.5) / w * 2 * AZ - AZ
    els = skybake.EL_MAX - (np.arange(h) + 0.5) / h * (skybake.EL_MAX - skybake.EL_MIN)
    img = np.zeros((h, w), np.float32)
    n = 0
    for clouds, base, flatten in PLANNED:
        for name, x, y, cw, _, _ in clouds:
            n += 1
            r = math.hypot(x, y)
            zb = base - skybake.drop(r)
            b = math.degrees(math.atan2(x, y))
            half = math.degrees(math.atan(0.45 * cw / r))
            lo = math.degrees(math.atan(zb / (r + 0.4 * cw)))
            hi = max(math.degrees(math.atan(zb / max(r - 0.4 * cw, 300.0))), math.degrees(math.atan((zb + tallness(name) * flatten * cw) / r)))
            c0, c1 = np.searchsorted(azs, [b - half, b + half])
            r0, r1 = np.searchsorted(-els, [-hi, -lo])
            if c1 <= c0 or r1 <= r0:
                continue
            u = (azs[c0:c1] - b) / half
            v = (els[r0:r1] - 0.5 * (lo + hi)) / (0.5 * (hi - lo))
            img[r0:r1, c0:c1] = np.maximum(img[r0:r1, c0:c1], (u[None, :] ** 2 + v[:, None] ** 2 < 1.0) * (0.55 + 0.45 * min(r / 30000.0, 1.0)))
    seen = (els < 30.0) & (els > 2.0)
    solid = (img > 0)[seen]
    third = w // 2
    print('%s: %d clouds; covered between 2 and 30 degrees: %.3f (left window %.3f, middle %.3f, right %.3f)' % (
        SET, n, solid.mean(), solid[:, :third].mean(), solid[:, w // 4:w // 4 + third].mean(), solid[:, third:].mean()), flush=True)
    for e0, e1 in ((2, 5), (5, 10), (10, 20), (20, 30), (30, 40)):
        rows = (els >= e0) & (els < e1)
        print('   %2d-%2d degrees: %.3f' % (e0, e1, (img > 0)[rows].mean()), flush=True)
    os.makedirs(out, exist_ok=True)
    bake.save8(os.path.join(out, 'plan_%s.png' % SET), img)


# ---- Sheets (our own) --------------------------------------------------------------------------------------------

def sheet_maps(seed, mask, base=1185.0, thick=430.0, full=0.50):
    """A sheet of stratocumulus (as sky_low2's): pads grown together, thick in their hearts and thin at their rims,
    cracks between. `mask(x, y, mid, fine)`: how much of it there is at a place, 0..1 (where it falls off, the sheet
    breaks up into single pads and ends raggedly); `full`: how nearly the pads close (0.5: cracks; more: fewer)."""
    pat = L.Pattern(seed, [30000.0, 9000.0, 3000.0, 1000.0, 350.0])

    def maps(x, y, voxel):
        big = pat.at(x, y, (30000.0, 9000.0), voxel) / 1.41
        mid = pat.at(x, y, (3000.0,), voxel)
        fine = pat.at(x, y, (1000.0,), voxel) + 0.5 * pat.at(x, y, (350.0,), voxel)
        d = np.minimum(L.cells_at(x + 700.0 * mid + 220.0 * fine, y + 380.0 * fine - 300.0 * mid, 1800.0, seed + 1, 1.5, 0.5),
                       L.cells_at(x - 500.0 * mid, y + 260.0 * fine, 1050.0, seed + 2, 1.2, 2.1) + 0.10)
        m = np.clip(mask(x, y, mid, fine), 0.0, 1.0)
        pad = np.clip((full + 0.13 * big + 0.05 * fine - d - 0.75 * (1.0 - m)) / 0.28, 0.0, 1.0)
        b = base + 25.0 * mid + 14.0 * fine + 70.0 * (1.0 - pad)
        return b, b + thick * pad ** 0.7 * (0.8 + 0.2 * np.tanh(big)) - 2.0
    return maps


SHEET_PIECES = [(900.0, 12000.0, 25.0, 14.0), (12000.0, 40000.0, 60.0, 22.0), (40000.0, 95000.0, 150.0, 36.0)]


# ---- The skies ---------------------------------------------------------------------------------------------------

def lo_few():
    """A few small cumulus: a loose group here, a pair there, a line of them far off; most of the sky empty."""
    rng = np.random.default_rng(101)
    pat = L.Pattern(102, [24000.0, 8000.0])
    taken = Taken()
    groups = [(-31.0, 8500.0, 7.0, 2600.0), (9.0, 13500.0, 8.0, 4000.0), (34.0, 6500.0, 6.0, 2000.0), (-8.0, 4200.0, 9.0, 1200.0)]

    def weight(x, y):                      # the groups (bearing, distance, how wide, how deep), and a far line
        b, r = bearing(x, y), np.hypot(x, y)
        w = sum(np.exp(-((b - gb) / gw) ** 2 - ((r - gr) / gd) ** 2) for gb, gr, gw, gd in groups)
        far = smooth(0.55, 1.25, pat.at(x, y) / 1.41) * smooth(15000.0, 24000.0, r)
        return np.clip(w + far, 0.0, 1.0)

    clouds = place(rng, weight, [(0.55, FLAT, 300.0, 800.0), (0.45, FLAT + HEAPED[:2], 800.0, 1900.0)], 0.27, taken,
                   near=2200.0, far=14000.0, thin=14000.0, fade=1.0, small=0.05, big=0.34, spacing=0.6)
    clouds += place(rng, weight, [(0.65, FLAT, 400.0, 1200.0), (0.35, FLAT + HEAPED, 1200.0, 2600.0)], 0.15, taken,
                    near=14000.0, far=50000.0, thin=24000.0, fade=10000.0, small=0.032, big=0.24, spacing=0.62)
    clouds += scraps(rng, clouds, taken)
    # (And a straggler or two out in the blue, where the pattern left nothing.)
    clouds += place(rng, lambda x, y: np.ones_like(x), [(1.0, FLAT, 350.0, 900.0)], 0.006, taken,
                    near=3500.0, far=40000.0, thin=22000.0, fade=9000.0, small=0.035, big=0.2, spacing=1.6)
    set_out(clouds, rng=rng)


def street_weight(pat, x, y):
    """The structure of a fair-weather sky: fields of cloud and empty quarters; in part of it the clouds stand in
    streets along the wind."""
    n = pat.at(x, y, (30000.0, 11000.0)) / 1.41 + 0.30 * pat.at(x, y, (4000.0,))
    fields = smooth(-0.15, 0.85, n)
    th = 0.55
    u = x * math.cos(th) - y * math.sin(th) + 900.0 * pat.at(x, y, (11000.0,))
    street = (0.5 + 0.5 * np.cos(2.0 * math.pi * u / 3400.0)) ** 2.5
    in_streets = smooth(0.1, 0.9, pat.at(x + 9000.0, y - 4000.0, (30000.0,)))
    return (0.05 + 0.95 * fields) * (1.0 - in_streets * (1.0 - 1.9 * street)).clip(0.0, 1.0)


def lo_scattered():
    """A fair day with scattered cumulus: clusters and lanes, streets in part of the sky, clouds of every size from
    scraps to a couple of large ones, empty quarters between."""
    rng = np.random.default_rng(201)
    pat = L.Pattern(202, [30000.0, 11000.0, 4000.0])
    taken = Taken()
    # The large ones first, by hand: one in the left half, one in the right, neither in the middle of a window.
    large = [at('cumulus_11', -27.0, 17000.0, 5000.0, 0.7, False, taken), at('cumulus_01', 31.0, 22000.0, 6400.0, 2.3, True, taken)]
    clouds = scatter(rng, lambda x, y: street_weight(pat, x, y), 0.34, taken, thin=22000.0, fade=10000.0)
    set_out(large + clouds, rng=rng)


def lo_towers():
    """Scattered cumulus with a group of towering cumulus standing in the left third of the sky; the rest more
    open."""
    rng = np.random.default_rng(301)
    pat = L.Pattern(302, [30000.0, 11000.0, 4000.0])
    taken = Taken()

    def group(x, y):                       # where the towers stand: round bearing -24 degrees, 12 to 32 km off
        b, r = bearing(x, y), np.hypot(x, y)
        return np.exp(-((b + 24.0) / 10.0) ** 2 - ((r - 21000.0) / 9500.0) ** 2)

    towers = place(rng, group, [(0.75, L.CONGESTUS, 3200.0, 7600.0, 10000.0, 19.0), (0.25, L.CONGESTUS, 2200.0, 3200.0, 8000.0, 19.0)],
                   0.50, taken, near=8000.0, far=40000.0, thin=40000.0, fade=1.0, small=0.03, big=0.5, spacing=0.40)
    # Heaped cumulus crowd round their feet; the rest of the sky has scattered small cumulus, thinner to the right.
    crowd = place(rng, lambda x, y: np.clip(1.6 * group(x, y), 0, 1), [(0.5, HEAPED, 1200.0, 3600.0), (0.5, FLAT + HEAPED, 500.0, 1200.0)],
                  0.42, taken, near=5000.0, far=45000.0, thin=45000.0, fade=1.0, small=0.03, big=0.3, spacing=0.5)

    def rest(x, y):
        return street_weight(pat, x, y) * (0.85 - 0.45 * smooth(-10.0, 30.0, bearing(x, y))) * (1.0 - 0.7 * np.clip(1.5 * group(x, y), 0, 1))

    clouds = scatter(rng, rest, 0.44, taken, thin=23000.0, largest=4200.0, overhead=1.7)
    set_out(towers, rng=rng, coarser=0)
    set_out(crowd + clouds, rng=rng)


def lo_broken():
    """A broken sky: rafts of stratocumulus kilometres across with heaped cumulus grown through and round them,
    ragged holes and bright gaps of blue between the rafts."""
    rng = np.random.default_rng(401)
    pat = L.Pattern(402, [26000.0, 9000.0, 5000.0])
    taken = Taken()

    def rafts(x, y):
        return smooth(-0.85, -0.10, pat.at(x, y, (26000.0, 9000.0)) / 1.41) * (1.0 - 0.9 * smooth(0.85, 1.4, pat.at(x + 3000.0, y, (5000.0,))))

    def mask(x, y, mid, fine):
        far = np.clip((np.hypot(x, y) - 34000.0) / 45000.0, 0.0, 1.0)
        return (rafts(x, y) + 0.12 * mid) * ends(bearing(x, y)) * (1.0 - far)

    mat = skybake.cloud_material('Sheet', sigma=0.038, anisotropy=0.6) if not PLAN else None
    # (The sheet only from 12 km: a layer made from maps is smooth, and overhead, seen from a kilometre or two, it
    # is a featureless pillow. There the rafts are many small cells of real cloud grown together.)
    layer_pieces('broken', sheet_maps(403, mask, full=0.56), mat, SHEET_PIECES[1:], margin=0.0)
    near = place(rng, rafts, [(0.45, FLAT + HEAPED, 380.0, 800.0), (0.55, FLAT + HEAPED, 800.0, 1700.0)], 0.95, taken,
                 near=1300.0, far=12500.0, thin=12500.0, fade=1.0, small=0.05, big=0.5, spacing=0.30, merge=0.5)
    far = place(rng, rafts, [(0.25, FLAT, 800.0, 1600.0), (0.60, FLAT + HEAPED, 1600.0, 4200.0), (0.15, L.CONGESTUS, 2600.0, 4600.0, 14000.0, 14.0)],
                0.56, taken, near=11500.0, far=85000.0, thin=32000.0, fade=14000.0, small=0.022, big=0.42, spacing=0.29)
    # Scraps in the holes.
    scraps = place(rng, lambda x, y: 1.0 - rafts(x, y), [(1.0, FLAT, 350.0, 1000.0)], 0.05, taken,
                   near=3000.0, far=40000.0, thin=22000.0, fade=9000.0, small=0.03, big=0.2, spacing=0.9)
    hands = L2.groups(near + far, rng, 3)
    for hand, base, flat in zip(hands, (1150.0, 1210.0, 1270.0), (0.60, 0.78, 1.0)):
        set_out(hand, sigma=0.085, base=base, flatten=flat, rng=rng)
    set_out(scraps, rng=rng)


def lo_front():
    """A bank coming in: at the left end clear sky with a few outliers, then lines of cumulus, thickening to a dense
    bank of stratocumulus and heaped cloud at the right end, towers along its edge."""
    rng = np.random.default_rng(501)
    pat = L.Pattern(502, [26000.0, 9000.0, 4000.0])
    taken = Taken()

    def edge(x, y):                         # the bank's edge wanders: it is not a ruled line across the sky
        return bearing(x, y) + 7.0 * pat.at(x, y, (26000.0,)) + 3.0 * pat.at(x, y, (9000.0,))

    def bank(x, y):
        return smooth(2.0, 24.0, edge(x, y))

    def mask(x, y, mid, fine):
        far = np.clip((np.hypot(x, y) - 36000.0) / 45000.0, 0.0, 1.0)
        return (smooth(8.0, 26.0, edge(x, y)) + 0.10 * mid) * ends(bearing(x, y)) * (1.0 - far)

    mat = skybake.cloud_material('Sheet', sigma=0.040, anisotropy=0.6) if not PLAN else None
    layer_pieces('front', sheet_maps(503, mask, full=0.60), mat, SHEET_PIECES[1:], margin=0.0)
    towers = place(rng, lambda x, y: smooth(-2.0, 8.0, edge(x, y)) * (1.0 - smooth(14.0, 24.0, edge(x, y))),
                   [(1.0, L.CONGESTUS, 2800.0, 6200.0, 11000.0, 17.0)], 0.30, taken,
                   near=9000.0, far=42000.0, thin=42000.0, fade=1.0, small=0.03, big=0.45, spacing=0.42)
    dense = place(rng, bank, [(0.55, FLAT + HEAPED, 380.0, 800.0), (0.45, FLAT + HEAPED, 800.0, 1400.0)], 0.60, taken,
                  near=1400.0, far=12500.0, thin=12500.0, fade=1.0, small=0.05, big=0.5, spacing=0.33, merge=0.45)
    dense += place(rng, bank, [(0.3, FLAT, 800.0, 1600.0), (0.7, FLAT + HEAPED, 1600.0, 4200.0)], 0.46, taken,
                   near=11500.0, far=85000.0, thin=32000.0, fade=14000.0, small=0.025, big=0.42, spacing=0.31)
    # Ahead of the bank: lines of cumulus, fewer and smaller towards the clear end.
    lines = scatter(rng, lambda x, y: street_weight(pat, x, y) * smooth(-34.0, -4.0, edge(x, y)) * (1.0 - bank(x, y)), 0.30, taken,
                    far=55000.0, thin=24000.0, fade=10000.0, largest=4000.0, overhead=1.6)
    outliers = place(rng, lambda x, y: 1.0 - smooth(-36.0, -20.0, edge(x, y)), [(1.0, FLAT, 350.0, 900.0)], 0.012, taken,
                     near=3500.0, far=40000.0, thin=22000.0, fade=9000.0, small=0.035, big=0.2, spacing=1.5)
    set_out(towers, rng=rng)
    for hand, base, flat in zip(L2.groups(dense, rng, 3), (1150.0, 1210.0, 1270.0), (0.60, 0.78, 1.0)):
        set_out(hand, sigma=0.085, base=base, flatten=flat, rng=rng)
    set_out(lines + outliers, rng=rng)


CB_B, CB_R = 14.0, 33000.0                # where the thunderhead stands: bearing (degrees), metres off


def anvil(path):
    """The thunderhead's anvil, as a volume: a sheet of ice spread under the tropopause, drawn out downwind. Its top
    is not a ruled line: it domes up over the updraught, swells and sags along its length and sinks towards the rim;
    the ice is drawn out in fibres along the wind, thick in some and thin in others, and the rim is torn into them;
    its underside is ragged and hangs in pouches."""
    rng = np.random.default_rng(72)
    v, zv = 100.0, 40.0
    x = np.arange(-9500.0, 24000.0, v, dtype=np.float32)[None, :]
    y = np.arange(-12000.0, 12000.0, v, dtype=np.float32)[:, None]
    shape = (y.shape[0], x.shape[1])

    def drawn(scale, by, octaves=4):          # a pattern fine across the wind and drawn out `by` times along it
        short = cloudgen.fbm2((shape[0], shape[1] // by + 2), v, scale, rng, octaves=octaves)
        xi = np.arange(shape[1]) / float(by)
        i0 = np.floor(xi).astype(int)
        return short[:, i0] * (1.0 - (xi - i0)) + short[:, i0 + 1] * (xi - i0)

    rag = cloudgen.fbm2(shape, v, 9000.0, rng, octaves=6, gain=0.62)
    fibres = drawn(1500.0, 6)
    hair = drawn(500.0, 10, octaves=3)
    swell = cloudgen.fbm2(shape, v, 6500.0, rng, octaves=3)
    lumps = cloudgen.fbm2(shape, v, 1300.0, rng, octaves=5, gain=0.55)
    a = np.where(x > 0, 18000.0, 6500.0)
    rho = np.sqrt((x / a) ** 2 + (y / 9000.0) ** 2) + 0.18 * rag + 0.12 * fibres + 0.07 * hair
    rim = np.clip((1.0 - rho) / 0.30, 0.0, 1.0)
    over = 900.0 * np.exp(-((x + 300.0) ** 2 + y * y) / (2.0 * 2500.0 ** 2))                 # the overshooting top
    top = 10150.0 + over + 260.0 * swell + 130.0 * fibres + 90.0 * lumps + 45.0 * hair - 800.0 * np.clip(rho, 0.0, 1.2) ** 2
    thick = 2300.0 * np.clip(1.0 - rho * rho, 0.0, 1.0) ** 0.75 * rim ** 0.6 + (110.0 * fibres + 60.0 * hair) * rim
    flare = 3200.0 * np.exp(-(x * x + y * y) / (2.0 * 5200.0 ** 2))
    pouch = cloudgen.cells2(shape, v, 1300.0, rng)
    small = cloudgen.cells2(shape, v, 650.0, rng)
    under = top - thick - flare * rim - (280.0 * np.sqrt(np.clip(1.0 - (pouch / 0.85) ** 2, 0.0, 1.0))
                                         + 120.0 * np.sqrt(np.clip(1.0 - (small / 0.85) ** 2, 0.0, 1.0)) + 130.0 * lumps) * rim
    dens, z0 = cloudgen.layer(under, top, v, zv, soft=3.0)
    # (Ice thins towards the rim, and lies in fibres: dense streaks and nearly empty ones side by side.)
    dens *= ((0.25 + 0.75 * np.clip((1.0 - rho) / 0.5, 0.0, 1.0)) * np.clip(1.0 + 1.0 * fibres + 0.6 * hair, 0.06, 2.2))[None]
    cloudgen.write_vdb(path, dens, (float(x[0, 0]), float(y[0, 0]), z0), v, zv)


def storm_base(path):
    """The storm's own base: a dark, flat, ragged shelf of dense cloud under the towers (what makes a thunderhead's
    foot heavy), as a volume whose level 0 is the cloud base."""
    rng = np.random.default_rng(73)
    v, zv = 120.0, 30.0
    x = np.arange(-12000.0, 12000.0, v, dtype=np.float32)[None, :]
    y = np.arange(-9500.0, 9500.0, v, dtype=np.float32)[:, None]
    shape = (y.shape[0], x.shape[1])
    rho = np.sqrt((x / 10000.0) ** 2 + (y / 7800.0) ** 2) + 0.22 * cloudgen.fbm2(shape, v, 5000.0, rng, octaves=5)
    m = np.clip((1.0 - rho) / 0.3, 0.0, 1.0)
    low = cloudgen.fbm2(shape, v, 1800.0, rng, octaves=4)
    dens, z0 = cloudgen.layer(-130.0 * m - 90.0 * low * m, 520.0 * m - 1.0, v, zv, soft=2.0)
    cloudgen.write_vdb(path, dens, (float(x[0, 0]), float(y[0, 0]), z0), v, zv)


def lo_storm():
    """A cumulonimbus some forty kilometres off, right of the middle: a mass of towers ten kilometres high under an
    anvil of ice drawn out to the left; towering cumulus flank it, torn scud hangs under its base; to the far left a
    slot of clear sky, to the right heaped cumulus."""
    rng = np.random.default_rng(601)
    pat = L.Pattern(602, [30000.0, 11000.0, 4000.0])
    taken = Taken()
    cx, cy = CB_R * math.sin(math.radians(CB_B)), CB_R * math.cos(math.radians(CB_B))
    fall = skybake.drop(CB_R)
    base = BASE - 150.0
    body = [('congestus_27', 0.0, 0.0, 12500.0), ('congestus_23', 5600.0, 1500.0, 9500.0), ('congestus_21', -5000.0, 2500.0, 8200.0),
            ('congestus_47', -800.0, 5600.0, 7000.0)]
    for n, dx, dy, w in body:
        taken.add(cx + dx, cy - dy, w)
    set_out([(n, cx + dx, cy - dy, w, t, False) for (n, dx, dy, w), t in zip(body, (0.4, 2.1, 3.9, 5.2))], sigma=0.05,
            base=base, rng=rng, coarser=1)
    if not PLAN:
        os.makedirs(L.LIB, exist_ok=True)
        path = os.path.join(L.LIB, 'storm_anvil.vdb')
        if not os.path.exists(path) or os.environ.get('NOOR_REGEN'):
            anvil(path)
        # (Drawn out to the left: the volume's downwind side is +x, so it is mirrored.)
        skybake.add_volume(path, (cx, cy, base - fall), skybake.cloud_material('Anvil', sigma=0.008, anisotropy=0.5), scale=(-1.0, 1.0, 1.0))
        shelf = os.path.join(L.LIB, 'storm_base.vdb')
        if not os.path.exists(shelf) or os.environ.get('NOOR_REGEN'):
            storm_base(shelf)
        skybake.add_volume(shelf, (cx, cy, base - fall), skybake.cloud_material('StormBase', sigma=0.05, anisotropy=0.4))
        # (No rain is modelled: a veil of it under the base, lit from the side by a low Sun, was a bright bar.)
    b0 = math.degrees(math.atan2(cx, cy))

    def storm(x, y):                       # the storm's own ground: nothing stands in front of it
        return (np.abs(bearing(x, y) - (b0 - 6.0)) < 25.0) & (np.hypot(x, y) < 50000.0)

    flank = []
    for k in range(13):                    # the flanking line round the storm's foot
        side = -1.0 if k % 2 else 1.0
        x = cx + side * rng.uniform(7500.0, 17000.0)
        y = cy + rng.uniform(-9000.0, 2500.0)
        w = rng.uniform(2400.0, 5200.0)
        taken.add(x, y, w)
        flank.append((L.CONGESTUS[int(rng.integers(0, len(L.CONGESTUS)))], x, y, w, rng.uniform(0, 6.28), bool(rng.uniform() < 0.5)))
    # Turrets: younger towers crowding the storm's near side, so that its mass is a turmoil of heads, not one smooth wall.
    for k in range(7):
        x = cx + rng.uniform(-7000.0, 7000.0)
        y = cy - rng.uniform(2500.0, 7500.0)
        w = rng.uniform(2800.0, 4800.0)
        taken.add(x, y, w)
        flank.append((L.CONGESTUS[int(rng.integers(0, len(L.CONGESTUS)))], x, y, w, rng.uniform(0, 6.28), bool(rng.uniform() < 0.5)))
    # Scud: torn low cloud under and before the storm's base.
    scud = place(rng, lambda x, y: np.exp(-((bearing(x, y) - b0) / 13.0) ** 2 - ((np.hypot(x, y) - 26500.0) / 3500.0) ** 2),
                 [(1.0, FLAT, 500.0, 1700.0)], 0.30, Taken(), near=20000.0, far=31000.0, thin=31000.0, fade=1.0, small=0.02, big=0.2, spacing=0.45)

    def rest(x, y):
        b = bearing(x, y)
        slot = smooth(-40.0, -30.0, b) * (1.0 - smooth(-18.0, -9.0, b))          # a slot of clear sky left of the anvil
        return np.clip(1.5 * street_weight(pat, x, y), 0.0, 1.0) * (1.0 - 0.95 * slot) * (0.6 + 0.4 * smooth(18.0, 34.0, b)) * (1.0 - storm(x, y))

    clouds = scatter(rng, rest, 0.42, taken, thin=26000.0, largest=4800.0, overhead=1.8)
    set_out(flank + clouds, rng=rng)
    set_out(scud, sigma=0.04, base=640.0, flatten=0.55, rng=rng)


def lo_overcast_breaks():
    """Stratocumulus over the whole sky, grey beneath, with a few ragged breaks; over part of the horizon the sheet
    ends some way off and a slit of clear sky shows under its edge (where a low Sun gets in under the cloud)."""
    rng = np.random.default_rng(701)
    pat = L.Pattern(702, [30000.0, 11000.0, 5000.0])
    taken = Taken()

    def reach(x, y):                       # how far off the sheet ends, by bearing: near over the slit, far elsewhere
        b = bearing(x, y)
        slit = smooth(-40.0, -30.0, b) * (1.0 - smooth(6.0, 18.0, b))
        return 95000.0 - slit * (95000.0 - (25000.0 + 3500.0 * np.sin(b / 5.5) + 2000.0 * np.sin(b / 2.1 + 1.0)))

    def breaks(x, y):
        return smooth(1.15, 1.75, pat.at(x, y, (11000.0, 5000.0)) / 1.41 + 0.45 * pat.at(x, y, (30000.0,)))

    def cover(x, y):
        r = np.hypot(x, y)
        return (1.0 - breaks(x, y)) * (1.0 - smooth(-1500.0, 2500.0, r - reach(x, y)))

    def mask(x, y, mid, fine):
        return (cover(x, y) + 0.10 * mid) * ends(bearing(x, y))

    # The cells are real cloud (small ones overhead, where they are seen from near); over them lies a thinner sheet
    # of our own that closes the cracks between them with paler cloud, as a stratocumulus deck does.
    mat = skybake.cloud_material('Sheet', sigma=0.020, anisotropy=0.6) if not PLAN else None
    layer_pieces('breaks', sheet_maps(703, mask, full=0.74, thick=330.0, base=1420.0), mat, SHEET_PIECES, margin=0.0)
    near = place(rng, cover, [(0.55, FLAT + HEAPED, 380.0, 800.0), (0.45, FLAT + HEAPED, 800.0, 1400.0)], 0.66, taken,
                 near=1100.0, far=12500.0, thin=12500.0, fade=1.0, small=0.05, big=0.5, spacing=0.31, merge=0.45)
    far = place(rng, cover, [(0.30, FLAT, 800.0, 1500.0), (0.70, FLAT + HEAPED, 1500.0, 3400.0)], 0.50, taken,
                near=11500.0, far=85000.0, thin=36000.0, fade=16000.0, small=0.022, big=0.40, spacing=0.28)
    for hand, base, flat in zip(L2.groups(near + far, rng, 3), (1150.0, 1200.0, 1250.0), (0.50, 0.62, 0.78)):
        set_out(hand, sigma=0.085, base=base, flatten=flat, rng=rng)


def lo_overcast():
    """An overcast: one deep layer everywhere, no blue; long soft rolls and pads beneath, paler where it is shallow
    and darker where it is deep, torn scud drifting under it (sky_low2's, made for the wide sky)."""
    mat = skybake.cloud_material(sigma=0.0055, anisotropy=0.6) if not PLAN else None
    layer_pieces('overcast', L2.overcast_maps(), mat, [(300.0, 15000.0, 30.0, 16.0), (15000.0, 46000.0, 70.0, 24.0),
                                                       (46000.0, 115000.0, 170.0, 40.0)], margin=0.25)
    rng = np.random.default_rng(801)
    pat = L.Pattern(802, [24000.0, 8000.0])
    scud = place(rng, lambda x, y: smooth(0.0, 1.0, pat.at(x, y) / 1.41), [(0.6, FLAT, 380.0, 900.0), (0.4, FLAT, 900.0, 1700.0)], 0.22, Taken(),
                 near=2200.0, far=42000.0, thin=24000.0, fade=8000.0, small=0.03, big=0.5, spacing=0.6)
    for hand, base, flat in zip(L2.groups(scud, rng, 2), (390.0, 470.0), (0.45, 0.6)):
        set_out(hand, sigma=0.035, base=base, flatten=flat, rng=rng)


SETS = {'lo_few': lo_few, 'lo_scattered': lo_scattered, 'lo_towers': lo_towers, 'lo_broken': lo_broken, 'lo_front': lo_front,
        'lo_storm': lo_storm, 'lo_overcast_breaks': lo_overcast_breaks, 'lo_overcast': lo_overcast}
INFO = {
    'lo_few': {'layer': 'low', 'altitude': 1.5, 'kind': 'few'},
    'lo_scattered': {'layer': 'low', 'altitude': 1.6, 'kind': 'scattered'},
    'lo_towers': {'layer': 'low', 'altitude': 2.6, 'kind': 'towers'},
    'lo_broken': {'layer': 'low', 'altitude': 1.5, 'kind': 'broken'},
    'lo_front': {'layer': 'low', 'altitude': 1.7, 'kind': 'front'},
    'lo_storm': {'layer': 'low', 'altitude': 5.0, 'kind': 'storm'},
    'lo_overcast_breaks': {'layer': 'low', 'altitude': 1.3, 'kind': 'overcast_breaks'},
    'lo_overcast': {'layer': 'low', 'altitude': 1.1, 'kind': 'overcast'},
}
# How finely each set is packed (WebP quality of the lit pictures; the opacity is packed 8 finer), and, for the sets
# that stand between the Sun and the ground, the least share of the open ground's light their undersides are given
# (see sky_low2.shade_ground: the phone lights every underside by the ground as if it lay in sunshine).
QUALITY = {'lo_overcast': 96, 'lo_overcast_breaks': 86, 'lo_broken': 78, 'lo_front': 76, 'lo_storm': 76}
SHADE_FLOOR = {'lo_broken': 0.55, 'lo_overcast_breaks': 0.32, 'lo_overcast': 0.50}
# The ground under a cloud lies in that cloud's shadow, the more of it the larger the cloud: a small cumulus's base
# is lit by sunlit ground all round its little shadow, a tower's base looks down on kilometres of shade. The bake
# lights every base by ground in the open, so the light thrown up by the ground (g.webp) is packed at this share
# for the sets of scattered cloud (it is what keeps a base grey under a white top at midday, when the phone lights
# the cloud from above and in front and all of its body that we see is lit by sky and ground alone).
GROUND = {'lo_few': 0.85, 'lo_scattered': 0.70, 'lo_towers': 0.55, 'lo_front': 0.55, 'lo_storm': 0.45}


# An overcast's light and dark are faint in the bake (its thin places are a sixth brighter than its thick ones): on
# the phone, under every light at once, it came out one even grey. Its own pattern (the picture lit by the sky,
# over its own average across a wide neighbourhood) is laid on every one of its lit pictures again, to this power.
STRUCTURE = {'lo_overcast': 1.7}


def blur(a, r):
    """A box blur, r pixels to every side, twice (edges repeated)."""
    for _ in range(2):
        for axis in (0, 1):
            pad = [(0, 0), (0, 0)]
            pad[axis] = (r + 1, r)
            c = np.cumsum(np.pad(a, pad, mode='edge'), axis=axis, dtype=np.float64)
            n = a.shape[axis]
            a = ((np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis) - np.take(c, np.arange(0, n), axis=axis)) / (2 * r + 1)).astype(np.float32)
    return a


def pack(out):
    """Packs the set again at its own quality, and darkens the ground's light under the sets that cover the sky."""
    raw, sky = os.path.join(out, 'raw'), os.path.join(out, 'sky')
    q = int(os.environ.get('NOOR_PACK_QUALITY', QUALITY.get(SET, 74)))
    meta = skybake.bake_pack(raw, sky, skybake.lights(), INFO[SET], quality=q)
    if SET in SHADE_FLOOR:
        def rd(name):
            return bake.read_exr(os.path.join(raw, name + '0001.exr'))
        alpha = np.clip(rd('alpha')[..., 0], 0, 1)
        up = slice(0, alpha.shape[0] // 3)                                    # the part of the picture that looks up
        solid = alpha[up] > 0.9
        through = float(np.clip(bake.luma(rd('sky')[..., :3])[up][solid].mean(), 0.05, 1.0)) if solid.any() else 1.0
        cover = float(alpha[up].mean())
        share = max(1.0 - cover * (1.0 - through), SHADE_FLOOR[SET])
        g = np.sqrt(np.clip(bake.luma(rd('gnd')[..., :3]) * share, 0, skybake.R_MAX) / skybake.R_MAX)
        bake.save8(os.path.join(sky, 'g.webp'), g, quality=q)
        meta['groundShade'] = share
        json.dump(meta, open(os.path.join(sky, 'sky.json'), 'w'), indent=1)
        print('ground light under the set: %.2f of that in the open (the cloud lets %.2f through, cover overhead %.2f)' % (share, through, cover), flush=True)
    elif SET in GROUND:
        gnd = bake.luma(bake.read_exr(os.path.join(raw, 'gnd0001.exr'))[..., :3])
        bake.save8(os.path.join(sky, 'g.webp'), np.sqrt(np.clip(gnd * GROUND[SET], 0, skybake.R_MAX) / skybake.R_MAX), quality=q)
        meta['groundShade'] = GROUND[SET]
        json.dump(meta, open(os.path.join(sky, 'sky.json'), 'w'), indent=1)
    if SET in STRUCTURE:
        def lum(name):
            return bake.luma(bake.read_exr(os.path.join(raw, name + '0001.exr'))[..., :3])
        k = blur(lum('sky'), 2)
        mult = np.clip((k / np.maximum(blur(k, max(k.shape[1] // 22, 8)), 1e-4)) ** STRUCTURE[SET], 0.55, 1.6)

        def save(name, file, share=1.0):
            bake.save8(os.path.join(sky, file), np.sqrt(np.clip(lum(name) * mult * share, 0, skybake.R_MAX) / skybake.R_MAX), quality=q)

        save('sky', 'k.webp')
        save('gnd', 'g.webp', meta.get('groundShade', 1.0))
        for name, _, _, _ in skybake.lights():
            save('sun_' + name, 's_%s.webp' % name.lower())
        meta['structure'] = STRUCTURE[SET]
        json.dump(meta, open(os.path.join(sky, 'sky.json'), 'w'), indent=1)
        print('structure laid on: the lit pictures times %.2f..%.2f' % (float(mult.min()), float(mult.max())), flush=True)
    size = sum(os.path.getsize(os.path.join(sky, f)) for f in os.listdir(sky))
    print('%s: %d files, %d KB, coverSeen %.3f, quality %d' % (SET, len(os.listdir(sky)), size // 1024, meta.get('coverSeen', -1.0), q), flush=True)


if __name__ == '__main__':
    if PLAN:
        SETS[SET]()
        plan_picture(bake.args()[1])
    elif MODE == 'pack':
        pack(bake.args()[1])
    else:
        def build():
            s = bpy.context.scene
            if os.environ.get('NOOR_CPU'):
                s.cycles.device = 'CPU'
            s.cycles.volume_step_rate = float(os.environ.get('NOOR_STEP', 2.0))
            SETS[SET]()

        skybake.run(build, INFO[SET], samples=int(os.environ.get('NOOR_BAKE_SPP', 384)), bounces=int(os.environ.get('NOOR_BOUNCES', 32)))
        if MODE in ('bake', 'bakefast'):
            pack(bake.args()[1])
