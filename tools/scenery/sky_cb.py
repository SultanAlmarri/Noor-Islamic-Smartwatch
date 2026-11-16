"""Distant thunderstorms (0.14): low skies with real cumulonimbus in them, in the wide format of skybake.py.

    NOOR_SET=<id> blender -b --factory-startup -t 2 -P tools/scenery/sky_cb.py -- plan|preview|look|bakefast|bake|pack <out dir> [bearing height]

    lo_cb_far      one great cumulonimbus far off, alone in a fair sky: towers, anvil, dark base, a veil of rain
    lo_cb_line     a line of thunderstorms along the horizon: a young tower, a mature storm, an old anvil blown far
    lo_congestus   a field of swelling cumulus, a few of them towering and near; no anvils

The owner: "Sometimes you see really cumulonimbus clouds, which are very beautiful... look at actual photos of
cumulonimbus clouds, how they look, how beautiful they are, and you should implement them."

What a thunderstorm is built from here:
- its towers: CGHEVEN's towering cumulus (tools/scenery/sources/clouds_low.md: free to use in renders, the files
  never passed on), **stacked**: a tower ten kilometres high is one cloud standing on another, their joins buried,
  so it is cauliflower from its foot to its head and not one cloud blown up until its lumps are kilometres wide;
- its anvil, its dark base, the rain under it: our own volumes (cloudgen_cb.py, which says what photographs of
  real anvils show and how that is built);
- a flanking line of younger towers stepping up to it on its upwind side, heaped cumulus round its foot.

These skies stand on a higher cloud base than the other low sets (2100 m, a hot dry day's): from 60 km a base at
1300 m lies under one degree and nothing of the storm's foot, its base or its rain would be seen.

This machine has no GPU: NOOR_SCALE bakes at a lower size and the packer scales the pictures up to the full
format (see `pack`); NOOR_BAKE_SPP, NOOR_BOUNCES and NOOR_STEP as in sky_lo.py; NOOR_BORDER=x0,x1,y0,y1 renders a
part of the picture only (previews).
"""
import hashlib
import json
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake               # noqa: E402
import cloudgen_cb as CB  # noqa: E402
import skybake            # noqa: E402
import sky_low as L       # noqa: E402
import sky_lo as LO       # noqa: E402

SET = os.environ.get('NOOR_SET', 'lo_cb_far')
MODE = bake.args()[0]
PLAN = MODE == 'plan'
WORK = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'work', 'cb')
LIB = os.path.join(WORK, 'lib')
AZ = skybake.AZ_HALF
BASE = 2100.0                             # metres: the cloud base of these skies
LO.BASE = BASE                            # (sky_lo.place keeps towers under a height by it)
SCALE = float(os.environ.get('NOOR_SCALE', 1.0))

# The clouds of these sets are other clouds than those of the 0.12 sets (the owner: the same few keep coming round).
FLAT = ['cumulus_07', 'cumulus_13', 'cumulus_17', 'cumulus_24', 'cumulus_25', 'cumulus_49']        # wide and shallow
HEAPED = ['cumulus_05', 'cumulus_09', 'cumulus_14', 'cumulus_38', 'cumulus_44', 'cumulus_47']      # taller heads
COLUMNS = ['congestus_24', 'congestus_26', 'congestus_11', 'congestus_10', 'congestus_18']         # towers taller than wide
MASSES = ['congestus_03', 'congestus_07', 'congestus_13', 'congestus_04', 'congestus_02', 'congestus_14']
TOWERS = {}                               # set -> bearings of its thunderheads' main towers (for the lightning)

smooth, bearing, ends = LO.smooth, LO.bearing, LO.ends


def own(name, make, **kw):
    """One of our own volumes, made once: `make(path, **kw)` writes it; made again when its numbers change."""
    os.makedirs(LIB, exist_ok=True)
    key = hashlib.md5(json.dumps(kw, sort_keys=True).encode()).hexdigest()[:8]
    path = os.path.join(LIB, '%s_%s.vdb' % (name, key))
    if not os.path.exists(path) or os.environ.get('NOOR_REGEN'):
        for old in os.listdir(LIB):
            if old.startswith(name + '_') and old.endswith('.vdb') and len(old) == len(name) + 13:
                os.remove(os.path.join(LIB, old))
        make(path, **kw)
        print('made', path, flush=True)
    return path


class Storm:
    """Where a storm stands and how it is turned: its own axes are u (downwind, the way its anvil streams) and v
    (across, to the left of the wind). `wind`: the way the wind blows, in degrees anticlockwise from the picture's
    right (0: to the right, 180: to the left, -90: towards us)."""

    def __init__(self, b, r, wind, base=BASE):
        self.b, self.r, self.turn = b, r, math.radians(wind)
        self.cx, self.cy = r * math.sin(math.radians(b)), r * math.cos(math.radians(b))
        self.base = base
        self.mats = {}

    def at(self, u, v):
        c, s = math.cos(self.turn), math.sin(self.turn)
        return self.cx + u * c - v * s, self.cy + u * s + v * c

    def bearing(self, u, v):
        x, y = self.at(u, v)
        return math.degrees(math.atan2(x, y))

    def stand(self, parts, sigma=0.05, anisotropy=0.5, coarser=0, taken=None):
        """The storm's towers. parts: [(cloud, u, v, width, lift, tall, turn, mirrored)]: `lift` metres over the
        storm's base (a cloud standing on another), `tall`: how much it is drawn out upwards."""
        from mathutils import Matrix, Vector
        for name, u, v, w, lift, tall, turn, mirrored in parts:
            x, y = self.at(u, v)
            if taken is not None:
                taken.add(x, y, w)
            if PLAN:
                LO.PLANNED.append(([(name, x, y, w, turn, mirrored)], self.base + lift, tall))
                continue
            h = L.HERO[name]
            k = w / max(h['x'][1] - h['x'][0], h['z'][1] - h['z'][0])
            dist = math.hypot(x, y)
            want = dist * 0.0016 / SCALE
            lod = int(np.clip(math.floor(math.log2(max(want / (h['voxel'] * k), 1.0))) + coarser, 0, len(h['lods']) - 1))
            vol = bpy.data.volumes.get('%s_%d' % (name, lod))
            if vol is None:
                vol = bpy.data.volumes.new('%s_%d' % (name, lod))
                vol.filepath = h['lods'][lod]
                vol.materials.append(None)
            ob = bpy.data.objects.new(name, vol)
            bpy.context.scene.collection.objects.link(ob)
            rung = int(round(math.log(k) / math.log(1.15)))
            if rung not in self.mats:
                self.mats[rung] = skybake.cloud_material('Tower x%d' % rung, sigma=sigma * 1.15 ** rung, anisotropy=anisotropy)
            ob.material_slots[0].link = 'OBJECT'
            ob.material_slots[0].material = self.mats[rung]
            m = Matrix.Rotation(turn, 4, 'Z') @ Matrix.Rotation(math.pi / 2, 4, 'X') @ Matrix.Diagonal((-k if mirrored else k, k * tall, k, 1.0))
            mid = Vector((0.5 * (h['x'][0] + h['x'][1]), h['base'], 0.5 * (h['z'][0] + h['z'][1])))
            ob.matrix_world = Matrix.Translation(Vector((x, y, self.base + lift - skybake.drop(dist))) - (m @ mid)) @ m

    def volume(self, path, material, u=0.0, v=0.0, lift=0.0, mirror=False):
        """One of our own volumes in the storm's axes (its x along the wind), `lift` metres over the base."""
        if PLAN:
            return
        x, y = self.at(u, v)
        skybake.add_volume(path, (x, y, self.base + lift - skybake.drop(math.hypot(x, y))), material, rotation=self.turn,
                           scale=(1.0, -1.0 if mirror else 1.0, 1.0))


def tall(name):
    """How high a cloud of the library stands for its width."""
    return LO.tallness(name)


def fair(rng, taken, groups, cover=0.24, far_cover=0.12, keep=None, largest=2600.0):
    """The fair sky round a storm: a few small cumulus in loose groups at different distances (bearing, metres off,
    degrees wide, metres deep), a thin far line, most of the sky empty. `keep(x, y)`: 0 where nothing may stand."""
    pat = L.Pattern(int(rng.integers(1, 10 ** 6)), [24000.0, 8000.0])

    def weight(x, y):
        b, r = bearing(x, y), np.hypot(x, y)
        w = sum(np.exp(-((b - gb) / gw) ** 2 - ((r - gr) / gd) ** 2) for gb, gr, gw, gd in groups)
        far = smooth(0.55, 1.25, pat.at(x, y) / 1.41) * smooth(16000.0, 26000.0, r)
        return np.clip(w + far, 0.0, 1.0) * (keep(x, y) if keep else 1.0)

    clouds = LO.place(rng, weight, [(0.5, FLAT, 350.0, 900.0), (0.5, FLAT + HEAPED[:3], 900.0, 2000.0)], cover, taken,
                      near=2600.0, far=15000.0, thin=15000.0, fade=1.0, small=0.05, big=0.34, spacing=0.6)
    clouds += LO.place(rng, weight, [(0.6, FLAT, 450.0, 1300.0), (0.4, FLAT + HEAPED, 1300.0, largest)], far_cover, taken,
                       near=15000.0, far=60000.0, thin=28000.0, fade=13000.0, small=0.03, big=0.24, spacing=0.62)
    out = []
    for name, x, y, w, _, _ in clouds:                       # torn scraps beside the larger ones
        if w < 1300.0:
            continue
        for _ in range(int(rng.integers(0, 3))):
            a, d, sw = rng.uniform(0, 2 * math.pi), w * rng.uniform(0.5, 0.95), w * rng.uniform(0.13, 0.36)
            sx, sy = x + d * math.cos(a), y + d * math.sin(a)
            sr = math.hypot(sx, sy)
            if sw < 0.022 * sr or sr < 2200.0 or abs(math.degrees(math.atan2(sx, sy))) > AZ - LO.END or (keep and keep(np.float64(sx), np.float64(sy)) < 0.5):
                continue
            taken.add(sx, sy, sw)
            out.append((FLAT[int(rng.integers(0, len(FLAT)))], sx, sy, sw, float(rng.uniform(0, 2 * math.pi)), bool(rng.uniform() < 0.5)))
    return clouds + out


def clear_of(storms, half=4.0, reach=1.25):
    """A weight that is 0 between us and each storm (and for a way beyond it): nothing stands in front of one."""
    def keep(x, y):
        b, r = bearing(x, y), np.hypot(x, y)
        k = 1.0
        for st, b0, b1 in storms:
            k = k * (1.0 - smooth(b0 - half, b0, b) * (1.0 - smooth(b1, b1 + half, b)) * (r < reach * st.r))
        return k
    return keep


# ---- The skies ---------------------------------------------------------------------------------------------------

def lo_cb_far():
    """One great cumulonimbus 58 km off, left of the middle, alone: its main tower stands 12 km high and breaks
    through its anvil as an overshooting top; the anvil streams to the right and a little away from us (towards us
    its far end stood higher in the picture than its head: true, and it looked wrong); a younger tower and a
    flanking line step up to it from the left (the upwind side); under its dark base hangs a veil of rain. The rest
    of the sky is fair: a few small cumulus at different distances."""
    rng = np.random.default_rng(1401)
    taken = LO.Taken()
    st = Storm(-10.0, 58000.0, 8.0)
    top = 10300.0                                       # the anvil's top over the cloud base (12.4 km over the ground)
    # (cloud, downwind, across, width, lift, drawn out upwards, turn, mirrored)
    st.stand([
        ('congestus_07', 0.0, 0.0, 9800.0, 0.0, 1.05, 0.6, False),            # the main updraught's foot
        ('congestus_24', 300.0, -400.0, 7500.0, 2900.0, 1.00, 2.2, False),    # ... and the tower standing on it, into the anvil
        ('congestus_26', 1700.0, 1300.0, 6200.0, 3400.0, 1.00, 0.4, True),    # a second tower beside it, behind
        ('congestus_11', -1500.0, -1100.0, 5600.0, 4000.0, 1.00, 3.5, False), # ... and a third, before it
        ('congestus_03', -4600.0, 1800.0, 7600.0, 0.0, 1.0, 4.0, True),       # the younger tower upwind
        ('congestus_18', -4300.0, 1500.0, 4800.0, 3300.0, 1.0, 1.1, False),   # ... its head, not yet at the anvil
        ('congestus_13', 3800.0, -2600.0, 7800.0, 0.0, 0.85, 5.1, False),     # the mass under the anvil, towards us
        ('congestus_04', 1500.0, 4200.0, 7400.0, 0.0, 0.8, 3.0, True),        # ... and behind
    ], sigma=0.05, taken=taken)
    # The flanking line: younger towers stepping down upwind, each smaller than the last.
    st.stand([
        ('congestus_02', -9200.0, 2600.0, 5200.0, 0.0, 1.05, 0.3, False),
        ('congestus_10', -12800.0, 1900.0, 4000.0, 0.0, 1.0, 2.9, True),
        ('congestus_14', -15800.0, 3300.0, 3000.0, 0.0, 1.0, 4.4, False),
    ], sigma=0.06, taken=taken)
    if not PLAN:
        st.volume(own('far_anvil', CB.anvil, seed=1402, down=16500.0, up=5400.0, half=7000.0, top=top, core=1700.0, sheet=1700.0,
                      dome=500.0, flare=3200.0, torn=1.25, voxel=80.0, zvoxel=40.0),
                  skybake.cloud_material('Anvil', sigma=0.05, anisotropy=0.5), u=300.0, v=-400.0)
        st.volume(own('far_shelf', CB.shelf, seed=1403, ax=5400.0, ay=3600.0, thick=750.0),
                  skybake.cloud_material('StormBase', sigma=0.05, anisotropy=0.4), u=-1200.0, v=600.0)
        st.volume(own('far_rain', CB.rain, seed=1404, ax=4300.0, ay=3600.0, depth=BASE - 150.0, lean=0.45, reach=0.75),
                  skybake.cloud_material('Rain', sigma=0.00030, anisotropy=0.3, colour=(0.72, 0.72, 0.72)), u=2400.0, v=-900.0)
    TOWERS['lo_cb_far'] = [st.bearing(300.0, -400.0)]
    # Heaped cumulus round the storm's foot (its inflow), small beside it.
    foot = []
    for k in range(9):
        u, v = rng.uniform(-19000.0, 9000.0), rng.uniform(-9500.0, -4500.0) if k % 3 else rng.uniform(5000.0, 9000.0)
        w = rng.uniform(1500.0, 3200.0)
        x, y = st.at(u, v)
        if taken.clear_of(x, y, w, 0.45):
            taken.add(x, y, w)
            foot.append((HEAPED[int(rng.integers(0, len(HEAPED)))], x, y, w, float(rng.uniform(0, 6.28)), bool(rng.uniform() < 0.5)))
    keep = clear_of([(st, -31.0, 9.0)])
    clouds = fair(rng, taken, [(27.0, 9500.0, 8.0, 3000.0), (-37.0, 15000.0, 6.0, 4000.0), (36.0, 26000.0, 9.0, 7000.0), (8.0, 5200.0, 7.0, 1300.0)],
                  cover=0.25, far_cover=0.10, keep=keep)
    LO.set_out(foot + clouds, rng=rng, base=BASE)


def lo_cb_line():
    """A line of thunderstorms along the horizon, 72 to 90 km off, each at its own stage, the upper wind blowing
    to the left: on the right a young tower with no anvil yet (a cap of smooth cloud over its head), in the middle
    a mature storm under a full anvil with rain beneath, on the left an old one whose anvil has blown far
    downwind and is thinning away over the stump of its towers. Clear sky between them; small cumulus nearer."""
    rng = np.random.default_rng(1501)
    taken = LO.Taken()
    # The mature storm. (With the wind to the left, a part's `across` is positive towards us.)
    m = Storm(-1.0, 82000.0, 187.0)
    m.stand([
        ('congestus_03', 0.0, 0.0, 10500.0, 0.0, 1.0, 1.9, False),
        ('congestus_24', 200.0, 300.0, 7200.0, 2700.0, 1.0, 5.0, True),
        ('congestus_11', 1700.0, -1100.0, 6000.0, 4300.0, 1.0, 0.8, False),
        ('congestus_18', -1700.0, 900.0, 5200.0, 4600.0, 1.0, 2.6, False),
        ('congestus_14', 4200.0, 2400.0, 8200.0, 0.0, 0.9, 4.1, False),
        ('congestus_02', -4400.0, -1500.0, 8000.0, 0.0, 1.0, 3.3, True),
        ('congestus_26', -4300.0, -1200.0, 4600.0, 3600.0, 1.0, 0.2, False),
    ], sigma=0.05, taken=taken)
    m.stand([
        ('congestus_26', -9000.0, 1500.0, 5000.0, 0.0, 1.0, 1.2, True),
        ('congestus_10', -12500.0, 2400.0, 4000.0, 0.0, 1.0, 5.6, False),
        ('congestus_13', -15500.0, 1200.0, 3300.0, 0.0, 1.0, 2.0, False),
    ], sigma=0.06, taken=taken)
    # The young one: one cloud on another on another, 9 km high, its head still hard and round.
    y = Storm(22.0, 72000.0, 187.0)
    y.stand([
        ('congestus_07', 0.0, 0.0, 8600.0, 0.0, 1.0, 3.0, False),
        ('congestus_26', 300.0, 200.0, 6400.0, 2300.0, 1.0, 4.4, False),
        ('congestus_18', 600.0, 0.0, 4500.0, 5000.0, 1.0, 1.0, True),
        ('congestus_04', 3700.0, 1500.0, 6500.0, 0.0, 1.0, 0.5, False),
        ('congestus_10', -4600.0, -800.0, 4300.0, 0.0, 1.0, 2.2, True),
        ('congestus_13', -7500.0, 1300.0, 3000.0, 0.0, 1.0, 4.0, False),
    ], sigma=0.055, taken=taken)
    # The old one: what is left of its towers, and over them the anvil, longer than the storm was ever high.
    o = Storm(-19.5, 90000.0, 190.0)
    o.stand([
        ('congestus_13', 0.0, 0.0, 9000.0, 0.0, 0.8, 0.9, True),
        ('congestus_14', -3600.0, 1000.0, 7500.0, 0.0, 0.8, 2.7, False),
        ('congestus_11', 500.0, 200.0, 5600.0, 2600.0, 1.0, 4.9, True),
        ('congestus_02', 4500.0, -1200.0, 6000.0, 0.0, 0.7, 5.5, False),
    ], sigma=0.045, taken=taken)
    if not PLAN:
        ice = skybake.cloud_material('Anvil', sigma=0.05, anisotropy=0.5)
        m.volume(own('line_anvil', CB.anvil, seed=1502, down=16500.0, up=5200.0, half=7200.0, top=10600.0, core=1700.0, sheet=1700.0,
                     dome=500.0, flare=3200.0, torn=1.25, voxel=100.0, zvoxel=50.0), ice, u=200.0, v=300.0)
        m.volume(own('line_shelf', CB.shelf, seed=1503, ax=5600.0, ay=3800.0, thick=750.0),
                 skybake.cloud_material('StormBase', sigma=0.05, anisotropy=0.4), u=-1000.0, v=0.0)
        m.volume(own('line_rain', CB.rain, seed=1504, ax=4600.0, ay=3600.0, depth=BASE - 150.0, lean=0.45, reach=0.8),
                 skybake.cloud_material('Rain', sigma=0.00030, anisotropy=0.3, colour=(0.72, 0.72, 0.72)), u=2200.0, v=1200.0)
        o.volume(own('line_old', CB.anvil, seed=1505, down=25000.0, up=3600.0, half=8600.0, top=10300.0, core=900.0, sheet=850.0,
                     dome=0.0, flare=5200.0, droop=600.0, torn=1.5, holes=0.75, voxel=110.0, zvoxel=40.0),
                 skybake.cloud_material('OldAnvil', sigma=0.035, anisotropy=0.5), u=800.0, v=0.0)
        o.volume(own('line_oldrain', CB.rain, seed=1506, ax=3600.0, ay=3000.0, depth=BASE - 150.0, lean=0.6, reach=0.35),
                 skybake.cloud_material('Rain', sigma=0.00022, anisotropy=0.3, colour=(0.72, 0.72, 0.72)), u=1500.0, v=600.0)
        y.volume(own('line_cap', CB.cap, seed=1507, radius=2500.0, rise=380.0, thick=120.0),
                 skybake.cloud_material('Cap', sigma=0.012, anisotropy=0.6), u=600.0, v=0.0, lift=5000.0 + 0.89 * 4500.0 - 260.0)
    TOWERS['lo_cb_line'] = [m.bearing(200.0, 300.0), y.bearing(600.0, 0.0), o.bearing(500.0, 200.0)]
    # Lesser cells of the same line between the storms, and heaped cumulus round every foot.
    for st, parts in ((Storm(10.5, 79000.0, 187.0), [('congestus_02', 0.0, 0.0, 6000.0, 0.0, 1.0, 1.0, False), ('congestus_10', 300.0, 0.0, 4200.0, 2300.0, 1.0, 3.0, False)]),
                      (Storm(-10.5, 88000.0, 187.0), [('congestus_14', 0.0, 0.0, 5200.0, 0.0, 1.0, 2.0, True)]),
                      (Storm(33.0, 80000.0, 187.0), [('congestus_04', 0.0, 0.0, 5600.0, 0.0, 1.0, 4.0, False)])):
        st.stand(parts, sigma=0.06, taken=taken)
    foot = []
    for st, n in ((m, 9), (y, 6), (o, 5)):
        for k in range(n):
            u, v = rng.uniform(-17000.0, 9000.0), rng.uniform(4500.0, 10000.0) * (1.0 if k % 3 else -1.0)
            w = rng.uniform(1500.0, 3200.0)
            x, yy = st.at(u, v)
            if taken.clear_of(x, yy, w, 0.45) and abs(math.degrees(math.atan2(x, yy))) < AZ - LO.END:
                taken.add(x, yy, w)
                foot.append((HEAPED[int(rng.integers(0, len(HEAPED)))], x, yy, w, float(rng.uniform(0, 6.28)), bool(rng.uniform() < 0.5)))
    keep = clear_of([(o, -41.0, -12.0), (m, -17.0, 7.0), (y, 15.0, 29.0)])
    clouds = fair(rng, taken, [(-30.0, 11000.0, 8.0, 3500.0), (12.0, 7000.0, 9.0, 2200.0), (37.0, 17000.0, 6.0, 5000.0), (-6.0, 21000.0, 10.0, 5000.0)],
                  cover=0.26, far_cover=0.13, keep=keep)
    LO.set_out(foot + clouds, rng=rng, base=BASE)


def lo_congestus():
    """A field of swelling cumulus: three great towers near enough to show every fold of them (17 to 34 km off,
    13 to 23 degrees high), one under a cap of smooth cloud, lesser towers farther off, flat-based cumulus of every
    size between and blue sky over all of it. No anvils: nothing here has turned to ice."""
    rng = np.random.default_rng(1601)
    pat = L.Pattern(1602, [30000.0, 11000.0, 4000.0])
    taken = LO.Taken()
    a = Storm(-19.0, 17500.0, 20.0)
    a.stand([('congestus_02', 0.0, 0.0, 4800.0, 0.0, 1.0, 0.7, False), ('congestus_24', 200.0, 300.0, 4200.0, 800.0, 1.0, 3.9, False),
             ('congestus_10', -2600.0, -900.0, 2600.0, 0.0, 1.0, 2.0, True)], sigma=0.075, taken=taken)
    b = Storm(14.0, 26000.0, -30.0)
    b.stand([('congestus_03', 0.0, 0.0, 7000.0, 0.0, 1.0, 5.2, True), ('congestus_26', -300.0, 500.0, 5200.0, 1600.0, 1.0, 1.4, False),
             ('congestus_11', 1300.0, -700.0, 5600.0, 600.0, 1.0, 2.9, False),          # a shoulder: the tower is no block on a plinth
             ('congestus_18', 3200.0, -1400.0, 3200.0, 0.0, 1.0, 0.3, False)], sigma=0.07, taken=taken)
    c = Storm(33.0, 34000.0, 60.0)
    c.stand([('congestus_14', 0.0, 0.0, 6500.0, 0.0, 1.0, 2.5, False), ('congestus_11', 300.0, -200.0, 4800.0, 1800.0, 1.0, 4.4, True)],
            sigma=0.065, taken=taken)
    d = Storm(-36.0, 41000.0, 100.0)
    d.stand([('congestus_07', 0.0, 0.0, 7600.0, 0.0, 1.0, 1.1, True), ('congestus_18', 400.0, 300.0, 4600.0, 2200.0, 1.0, 3.0, False)],
            sigma=0.06, taken=taken)
    e = Storm(-2.0, 52000.0, 0.0)
    e.stand([('congestus_13', 0.0, 0.0, 6800.0, 0.0, 1.0, 5.9, False), ('congestus_26', 0.0, 300.0, 4600.0, 2400.0, 1.0, 2.2, True),
             ('congestus_04', -5200.0, 1500.0, 5200.0, 0.0, 1.0, 0.9, False)], sigma=0.06, taken=taken)
    if not PLAN:
        b.volume(own('con_cap', CB.cap, seed=1603, radius=1700.0, rise=520.0, thick=90.0, voxel=40.0, zvoxel=14.0),
                 skybake.cloud_material('Cap', sigma=0.010, anisotropy=0.6), u=-300.0, v=500.0, lift=1600.0 + 0.89 * 5200.0 - 430.0)
    TOWERS['lo_congestus'] = []

    def near_tower(x, y):                      # nothing stands before the three near towers
        bb, r = bearing(x, y), np.hypot(x, y)
        k = 1.0
        for st, half in ((a, 11.0), (b, 10.0), (c, 8.0)):
            k = k * (1.0 - (np.abs(bb - st.b) < half) * (r < 1.15 * st.r))
        return k

    def weight(x, y):
        return LO.street_weight(pat, x, y) * near_tower(x, y)

    # Lesser towers far off, where the field is thickest; then the flat-based cumulus of every size.
    far = LO.place(rng, weight, [(1.0, MASSES + COLUMNS, 2600.0, 5200.0, 30000.0, 9.0)], 0.10, taken,
                   near=30000.0, far=70000.0, thin=50000.0, fade=14000.0, small=0.03, big=0.3, spacing=0.5)
    near = LO.place(rng, weight, [(0.45, FLAT, 400.0, 1000.0), (0.55, FLAT + HEAPED, 1000.0, 2600.0)], 0.30, taken,
                    near=2600.0, far=10000.0, thin=10000.0, fade=1.0, small=0.06, big=0.44, spacing=0.55)
    out = LO.place(rng, weight, [(0.25, FLAT, 450.0, 1400.0), (0.45, FLAT + HEAPED, 1400.0, 3200.0, 5000.0), (0.30, HEAPED, 3200.0, 5200.0, 10000.0)],
                   0.42, taken, near=9500.0, far=62000.0, thin=24000.0, fade=11000.0, small=0.03, big=0.27, spacing=0.52)
    LO.set_out(far, rng=rng, base=BASE)
    LO.set_out(near + out, rng=rng, base=BASE)


SETS = {'lo_cb_far': lo_cb_far, 'lo_cb_line': lo_cb_line, 'lo_congestus': lo_congestus}
INFO = {
    'lo_cb_far': {'layer': 'low', 'altitude': 5.5, 'kind': 'cb_far'},
    'lo_cb_line': {'layer': 'low', 'altitude': 5.0, 'kind': 'cb_line'},
    'lo_congestus': {'layer': 'low', 'altitude': 3.2, 'kind': 'congestus'},
}
QUALITY = {'lo_cb_far': 80, 'lo_cb_line': 80, 'lo_congestus': 76}
# The share of the open ground's light the undersides are given (sky_lo.py, GROUND: the ground under a great cloud
# lies in its shadow).
GROUND = {'lo_cb_far': 0.50, 'lo_cb_line': 0.50, 'lo_congestus': 0.55}


def resize(a, h, w):
    """A picture (rows, columns) at another size, by straight-line interpolation (pixel centres kept in place)."""
    if a.shape == (h, w):
        return a
    ys = np.clip((np.arange(h) + 0.5) * a.shape[0] / h - 0.5, 0, a.shape[0] - 1)
    xs = np.clip((np.arange(w) + 0.5) * a.shape[1] / w - 0.5, 0, a.shape[1] - 1)
    y0, x0 = np.minimum(ys.astype(int), a.shape[0] - 2), np.minimum(xs.astype(int), a.shape[1] - 2)
    fy, fx = (ys - y0)[:, None].astype(np.float32), (xs - x0)[None, :].astype(np.float32)
    top = a[y0][:, x0] * (1 - fx) + a[y0][:, x0 + 1] * fx
    bot = a[y0 + 1][:, x0] * (1 - fx) + a[y0 + 1][:, x0 + 1] * fx
    return (top * (1 - fy) + bot * fy).astype(np.float32)


def pack(out):
    """Packs the raw bake as skybake.bake_pack does, at the full size of the format whatever size it was baked at
    (a bake at NOOR_SCALE 0.5 is scaled up here: the phone and every other set count on 2068 x 902), with the
    ground's light at this set's share and the bearings of its thunderheads (`towers`, for the lightning)."""
    raw, sky = os.path.join(out, 'raw'), os.path.join(out, 'sky')
    os.makedirs(sky, exist_ok=True)
    q = int(os.environ.get('NOOR_PACK_QUALITY', QUALITY.get(SET, 78)))
    W, H = int(round(2 * AZ * skybake.PX_PER_DEGREE)), int(round((skybake.EL_MAX - skybake.EL_MIN) * skybake.PX_PER_DEGREE))

    def rd(name):
        return bake.read_exr(os.path.join(raw, name + '0001.exr'))

    a0 = np.clip(rd('alpha')[..., 0], 0, 1)
    alpha = resize(a0, H, W)
    bake.save8(os.path.join(sky, 'a.webp'), alpha, quality=q + 8)
    # (How far: averaged over a few pixels where the cloud is thin, a veil of rain or an anvil's rim, whose distance
    # is otherwise a dither of the few samples that met anything.)
    f0, thin = bake.luma(rd('far')[..., :3]), LO.blur(a0, 2) < 0.6
    far = np.where(thin, LO.blur(f0, 2) / np.maximum(LO.blur(a0, 2), 1e-3), f0 / np.maximum(a0, 1e-3))
    far = resize(np.clip(far, 0, 1), H, W)
    bake.save8(os.path.join(sky, 'f.webp'), far * (alpha > 0.004), quality=q)

    def light(name, share=1.0):
        return np.sqrt(np.clip(resize(bake.luma(rd(name)[..., :3]), H, W) * share, 0, skybake.R_MAX) / skybake.R_MAX)

    ls = skybake.lights()
    bake.save8(os.path.join(sky, 'k.webp'), light('sky'), quality=q)
    bake.save8(os.path.join(sky, 'g.webp'), light('gnd', GROUND[SET]), quality=q)
    for name, _, _, _ in ls:
        bake.save8(os.path.join(sky, 's_%s.webp' % name.lower()), light('sun_' + name), quality=q)
    k = W / (bake.W * skybake.PAN)
    r0, r1 = int(H * (skybake.EL_MAX - 30.0) / (skybake.EL_MAX - skybake.EL_MIN)), int(H * (skybake.EL_MAX - 2.0) / (skybake.EL_MAX - skybake.EL_MIN))
    meta = dict(INFO[SET])
    meta.update({'projection': 'equirect', 'azHalf': AZ, 'elMin': skybake.EL_MIN, 'elMax': skybake.EL_MAX,
                 'coverSeen': float(alpha[r0:r1].mean()), 'width': W, 'height': H, 'horizon': bake.HORIZON * k, 'viewWidth': bake.W * k,
                 'rMax': skybake.R_MAX, 'farKm': skybake.FAR_KM, 'cover': float(alpha.mean()),
                 'suns': [{'name': n.lower(), 'azimuth': az, 'elevation': el} for n, az, el, _ in ls],
                 'groundShade': GROUND[SET], 'towers': [round(float(t), 1) for t in TOWERS.get(SET, [])],
                 'bakedAt': [int(a0.shape[1]), int(a0.shape[0])]})
    json.dump(meta, open(os.path.join(sky, 'sky.json'), 'w'), indent=1)
    size = sum(os.path.getsize(os.path.join(sky, f)) for f in os.listdir(sky))
    print('%s: %d files, %d KB, baked at %d x %d, coverSeen %.3f, towers %s, quality %d' % (
        SET, len(os.listdir(sky)), size // 1024, a0.shape[1], a0.shape[0], meta['coverSeen'], meta['towers'], q), flush=True)


if __name__ == '__main__':
    if PLAN:
        SETS[SET]()
        LO.SET = SET
        LO.plan_picture(bake.args()[1])
        print('towers', TOWERS.get(SET))
    elif MODE == 'pack':
        LO.PLAN = PLAN = True                           # (only the storms' bearings are wanted: nothing is built)
        SETS[SET]()
        pack(bake.args()[1])
    else:
        def build():
            s = bpy.context.scene
            if os.environ.get('NOOR_BORDER'):
                b = [float(v) for v in os.environ['NOOR_BORDER'].split(',')]
                s.render.use_border = True
                s.render.use_crop_to_border = True
                s.render.border_min_x, s.render.border_max_x, s.render.border_min_y, s.render.border_max_y = b
            s.cycles.device = 'CPU'
            s.cycles.volume_step_rate = float(os.environ.get('NOOR_STEP', 2.0))
            SETS[SET]()

        # (skybake.run packs a bake itself, at the size it was baked; `pack` then packs it again as these sets want.)
        skybake.run(build, INFO[SET], samples=int(os.environ.get('NOOR_BAKE_SPP', 256)), bounces=int(os.environ.get('NOOR_BOUNCES', 32)))
        if MODE in ('bake', 'bakefast'):
            pack(bake.args()[1])
