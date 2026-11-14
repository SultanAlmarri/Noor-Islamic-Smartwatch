"""The low cloud sets of the home screen (see skybake.py): heaped clouds and low layers, as volumes.

    NOOR_SET=<id> blender -b --factory-startup -P tools/scenery/sky_low.py -- preview|look|bakefast|bake <out dir> [bearing height]

    cu_fair      scattered fair-weather cumulus          cu_field     a full field of cumulus, some of it tall
    cu_towers    towering cumulus among smaller          cb_storm     a far thunderhead under its anvil
    sc_sheet     stratocumulus with breaks of blue       st_overcast  an overcast

The heaped clouds are not drawn by us: they are film-quality cloud volumes by CGHEVEN (Ammar Khan; see
tools/scenery/sources/clouds_low.md: free to use in finished work, the files themselves must never be passed on, so
they live outside the repository and only the baked pictures are shipped). `cloudprep.py` notes each one's size and
makes the coarser copies used far away. Here they are set out as a sky: many small and few large, turned and
mirrored, every base on one level, thinning into the distance, with the Sun's road kept open.

The overcast is our own: maps of base and top over the whole sky, cut into three pieces of falling fineness.

NOOR_CPU=1 renders on the processor (when other renders hold the GPU a volume render there can all but stand
still); NOOR_SCALE, NOOR_BAKE_SPP, NOOR_BOUNCES, NOOR_STEP set the picture's size and the render's care.
"""
import json
import math
import os
import sys
import time

import bpy
import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake          # noqa: E402
import cloudgen      # noqa: E402
import skybake       # noqa: E402

SET = os.environ.get('NOOR_SET', 'cu_fair')
OUT = bake.args()[1]
WORK = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'work', 'clouds_low')
LIB = os.path.join(WORK, 'lib')                      # our own layers' volumes
HERO = json.load(open(os.path.join(WORK, 'cg', 'info.json')))      # the CGHEVEN clouds (cloudprep.py)
CUMULUS = sorted(n for n in HERO if n.startswith('cumulus'))
CONGESTUS = sorted(n for n in HERO if n.startswith('congestus'))
HALF = math.radians(32.0)          # the sky is filled this far to either side of the view's middle
BASE = 1300.0                      # metres: the one level every heaped cloud stands on
SCALE = float(os.environ.get('NOOR_SCALE', 1.0))
SUN_ROAD = bake.RISE_AZIMUTH       # degrees either side of the middle: where the Sun rises, sets and travels low


SIGMA = float(os.environ.get('NOOR_SIGMA', 0.10))      # extinction per metre where a cloud's grid is 1


def weather(rng):
    """Fields of cloud and the gaps between them: a number round 1 for every place (x, y in metres)."""
    waves = [(rng.uniform(0, 2 * math.pi), rng.uniform(0, 2 * math.pi), 2 * math.pi / rng.uniform(12000, 40000)) for _ in range(5)]

    def at(x, y):
        return 1.0 + 0.42 * sum(math.sin(ph + k * (x * math.cos(th) + y * math.sin(th))) for ph, th, k in waves)
    return at


def field(rng, mix, cover, near=3500.0, far=75000.0, thin=26000.0, fade=14000.0, clump=1.0, sun_road=0.3,
          small=0.016, big=0.20, spacing=0.55, keep_out=None):
    """Where the heaped clouds of a sky stand. `mix`: [(share of the covered sky, [cloud names], width from, width
    to[, no nearer than[, how tall the clouds are for their width]])]: clouds given a tallness are towers, set no
    nearer than keeps their tops under 15 degrees (higher, they would stand behind the text and over the Sun's
    whole path), and kept off the Sun's road by their own width as well. `cover`: the part of the sky they cover out to `thin` metres; beyond, they thin out (far
    cloud is lost in the haze, and the horizon must not close). No cloud is set where it would look smaller than
    `small` or larger than `big` (its width over its distance: no specks, and nothing huge hanging over us).
    `sun_road`: how much of the cloud is kept on the Sun's road (bearings round +-14.7 degrees, low in the sky).
    `spacing`: how close neighbours may stand (0.5: just touching; less: merging).
    Returns [(name, x, y, width, turn, mirrored)]."""
    w_at = weather(rng)
    area = HALF * (thin ** 2 - near ** 2) + 2 * HALF * fade * (thin + fade)       # the sky's area, thinned
    px, py, pw = np.zeros(20000), np.zeros(20000), np.zeros(20000)
    n, out = 0, []
    for entry in mix:
        share, names, w0, w1 = entry[:4]
        nearest = entry[4] if len(entry) > 4 else near
        rise = entry[5] if len(entry) > 5 else 0.0
        want = cover * share * area
        have, tries = 0.0, 0
        while have < want and tries < 400000 and n < len(px):
            tries += 1
            r = math.sqrt(rng.uniform(nearest ** 2, far ** 2))
            if r > thin and rng.uniform() > math.exp(-(r - thin) / fade):
                continue
            b = rng.uniform(-HALF, HALF)
            x, y = r * math.sin(b), r * math.cos(b)
            if rng.uniform() > 0.5 * (1.0 + clump * (w_at(x, y) - 1.0) * 1.6):
                continue
            if keep_out and keep_out(x, y):
                continue
            w = w0 * (w1 / w0) ** (rng.uniform() ** 1.7)             # many small, few large
            if w < small * r or w > big * r:
                continue
            if rise and (BASE + rise * w) / r > math.tan(math.radians(15.0)):
                continue
            road = 4.0 + (math.degrees(math.atan(0.5 * w / r)) if rise else 0.0)
            if abs(abs(math.degrees(b)) - SUN_ROAD) < road and r > 4500.0 and rng.uniform() > sun_road:
                continue
            if n and np.any((x - px[:n]) ** 2 + (y - py[:n]) ** 2 < (spacing * (w + pw[:n])) ** 2):
                continue
            px[n], py[n], pw[n] = x, y, w
            n += 1
            out.append((names[int(rng.integers(0, len(names)))], x, y, w, rng.uniform(0, 2 * math.pi), rng.uniform() < 0.5))
            have += 0.45 * w * w
    return out


def set_out(clouds, sigma=SIGMA, anisotropy=0.5, base=BASE, flatten=1.0, rng=None, coarser=0):
    """Makes the volume objects of a sky: each cloud turned about the upright, mirrored or not, its base on the one
    level (lowered as the round Earth asks), and read from the copy whose fineness its distance asks for.

    Cycles takes a volume's density per unit of the object's own space, so a cloud enlarged k times would be k times
    thinner: every cloud gets a material (on the object, not on the shared volume) whose density is `sigma` times its
    scale, taken from a ladder of scales a seventh apart."""
    rng = rng or np.random.default_rng(1)
    made, mats = {}, {}
    for name, x, y, w, turn, mirrored in clouds:
        h = HERO[name]
        k = w / max(h['x'][1] - h['x'][0], h['z'][1] - h['z'][0])          # metres to the file's unit
        dist = math.hypot(x, y)
        want = dist * 0.0016 / SCALE                                        # two pixels of the picture to a voxel
        lod = int(np.clip(math.floor(math.log2(max(want / (h['voxel'] * k), 1.0))) + coarser, 0, len(h['lods']) - 1))
        key = (name, lod)
        if key not in made:
            vol = bpy.data.volumes.new('%s_%d' % key)
            vol.filepath = h['lods'][lod]
            vol.materials.append(None)
            made[key] = vol
        ob = bpy.data.objects.new(name, made[key])
        bpy.context.scene.collection.objects.link(ob)
        # (A towering cloud is kilometres deep: at a small cumulus's density no light would get into it, and it
        # would stand there grey. Half as dense, its sunlit side is white and its far side still dark.)
        thin = 0.5 if name.startswith('congestus') else 1.0
        rung = (int(round(math.log(k) / math.log(1.15))), thin)
        if rung not in mats:
            mats[rung] = skybake.cloud_material('Cloud x%d %g' % rung, sigma=sigma * thin * 1.15 ** rung[0], anisotropy=anisotropy)
        ob.material_slots[0].link = 'OBJECT'
        ob.material_slots[0].material = mats[rung]
        tall = k * flatten * float(rng.uniform(0.85, 1.2))
        s = Matrix.Diagonal((-k if mirrored else k, tall, k, 1.0))
        m = Matrix.Rotation(turn, 4, 'Z') @ Matrix.Rotation(math.pi / 2, 4, 'X') @ s      # the files are y-up
        mid = Vector((0.5 * (h['x'][0] + h['x'][1]), h['base'], 0.5 * (h['z'][0] + h['z'][1])))
        ob.matrix_world = Matrix.Translation(Vector((x, y, base - skybake.drop(dist))) - (m @ mid)) @ m
    print(len(clouds), 'clouds,', len(made), 'volumes', flush=True)


# ---- Layers (our own) --------------------------------------------------------------------------------------------

def layer_pieces(name, maps, mat, pieces, margin=0.03):
    """Cuts a layer into pieces of falling fineness. `maps(x, y, voxel)` gives (base, top) for arrays of places
    (metres) at a level of detail; `pieces`: [(y from, y to, voxel, z voxel)]; `margin`: how far (radians) beyond the
    sky that is seen the layer goes on (an overcast must not let the Sun in at its side)."""
    os.makedirs(LIB, exist_ok=True)
    for i, (y0, y1, voxel, zv) in enumerate(pieces):
        path = os.path.join(LIB, '%s_%d.vdb' % (name, i))
        half = y1 * math.tan(min(HALF + margin, 1.2)) + 2000.0
        if not os.path.exists(path) or os.environ.get('NOOR_REGEN'):
            t0 = time.time()
            x = np.arange(-half, half, voxel, dtype=np.float32)
            y = np.arange(y0, y1, voxel, dtype=np.float32)
            base, top = maps(x[None, :], y[:, None], voxel)
            fall = (x[None, :] ** 2 + y[:, None] ** 2) / (2.0 * skybake.EARTH)
            seen = np.abs(np.arctan2(x[None, :], np.maximum(y[:, None], 1.0))) < HALF + margin
            top = np.where(seen, top, base - 1.0)
            dens, z0 = cloudgen.layer(base - fall, top - fall, voxel, zv)
            cloudgen.write_vdb(path, dens, (-half, y0, z0), voxel, zv)
            print('layer', name, i, dens.shape, '%.0f s' % (time.time() - t0), flush=True)
        skybake.add_volume(path, (0.0, 0.0, 0.0), mat)


class Pattern:
    """A random pattern over the whole sky that can be read at any place and fineness (so the pieces of a layer
    agree where they meet): waves of many sizes and directions."""

    def __init__(self, seed, sizes, count=7):
        rng = np.random.default_rng(seed)
        self.w = []
        for size in sizes:
            for _ in range(count):
                th = rng.uniform(0, 2 * math.pi)
                k = 2 * math.pi / (size * rng.uniform(0.7, 1.4))
                self.w.append((k * math.cos(th), k * math.sin(th), rng.uniform(0, 2 * math.pi), size))
        self.norm = {s: 1.0 / math.sqrt(0.5 * count) for s in sizes}

    def at(self, x, y, sizes=None, voxel=0.0):
        out = 0.0
        for kx, ky, ph, size in self.w:
            if (sizes is None or size in sizes) and size > 3.0 * voxel:
                out = out + np.sin(kx * x + ky * y + ph) * self.norm[size]
        return out


def cells_at(x, y, cell, seed, stretch=1.0, turn=0.0):
    """Distance (in cells) to the nearest point of a lattice of jittered points that is the same wherever and
    however finely it is read (hashed, not stored). `stretch` draws the cells out along the direction `turn`."""
    c, s = math.cos(turn), math.sin(turn)
    u = (x * c + y * s) / (cell * stretch)
    v = (-x * s + y * c) / cell
    iu, iv = np.floor(u), np.floor(v)
    fu, fv = u - iu, v - iv
    best = np.full(np.broadcast(u, v).shape, 9.0, np.float32)

    def h(a, b, k):
        n = np.sin(a * 127.1 + b * 311.7 + k * 74.7 + seed * 13.13) * 43758.5453
        return n - np.floor(n)

    for dv in (-1, 0, 1):
        for du in (-1, 0, 1):
            pu = du + h(iu + du, iv + dv, 1.0) - fu
            pv = dv + h(iu + du, iv + dv, 2.0) - fv
            best = np.minimum(best, pu * pu + pv * pv)
    return np.sqrt(best)


def dome(d):
    d = np.clip(d / 0.85, 0.0, 1.0)
    return np.sqrt(1.0 - d * d)


def overcast_maps():
    pat = Pattern(51, [38000.0, 14000.0, 5200.0, 1900.0, 700.0, 260.0])

    def maps(x, y, voxel):
        big = pat.at(x, y, (38000.0, 14000.0), voxel) / 1.41
        mid = pat.at(x, y, (5200.0,), voxel) + 0.55 * pat.at(x, y, (1900.0,), voxel)
        fine = pat.at(x, y, (700.0,), voxel) + 0.5 * pat.at(x, y, (260.0,), voxel)
        rolls = dome(cells_at(x + 500.0 * mid, y, 1700.0, 52, 2.6, 0.35))
        bellies = dome(cells_at(x - 300.0 * mid, y, 620.0, 53, 1.5, 1.2))
        # Its underside: long swells, darker bellies hanging from it, a fine raggedness; never the ground.
        base = np.maximum(680.0 + 140.0 * big - 210.0 * rolls - 70.0 * bellies + 85.0 * mid + 40.0 * fine, 330.0)
        # Its depth: shallow here (the light comes through: the bright patches of an overcast), deep there.
        deep = pat.at(x + 7000.0, y - 3000.0, (14000.0, 5200.0), voxel) / 1.41
        top = base + 420.0 + 520.0 * (0.5 + 0.5 * np.tanh(1.4 * deep)) + 240.0 * rolls
        return base, top
    return maps


# ---- The sets ----------------------------------------------------------------------------------------------------

def cu_fair():
    """A fair day: small flat cumulus scattered over a quarter of the sky, a few larger among them, in loose
    groups with lanes of blue; the Sun's road open."""
    rng = np.random.default_rng(5)
    clouds = field(rng, [(0.40, CUMULUS, 500.0, 1500.0), (0.60, CUMULUS, 1500.0, 4600.0, 7000.0)], 0.20,
                   clump=1.5, thin=27000.0, fade=12000.0, far=58000.0, sun_road=0.25, small=0.03, big=0.26)
    set_out(clouds, rng=rng)


def cu_field():
    """A sky half full of cumulus: ranks of flat-based heaps to the horizon, some grown tall."""
    rng = np.random.default_rng(8)
    clouds = field(rng, [(0.25, CUMULUS, 600.0, 1600.0), (0.50, CUMULUS, 1600.0, 4200.0, 6500.0),
                         (0.25, CONGESTUS, 2500.0, 4500.0, 12000.0)], 0.34,
                   clump=0.8, thin=30000.0, fade=13000.0, far=72000.0, sun_road=0.45, small=0.03, big=0.26)
    set_out(clouds, rng=rng)


def cu_towers():
    """Towering cumulus: great cauliflower heads standing along the horizon over smaller cloud."""
    rng = np.random.default_rng(12)
    clouds = field(rng, [(0.50, CONGESTUS, 2600.0, 4800.0, 10000.0, 0.8), (0.18, CONGESTUS, 4800.0, 7000.0, 20000.0, 0.8),
                         (0.22, CUMULUS, 1200.0, 3200.0, 6000.0), (0.10, CUMULUS, 500.0, 1200.0)], 0.24,
                   clump=1.0, thin=30000.0, fade=10000.0, far=62000.0, sun_road=0.3, small=0.03, big=0.45)
    set_out(clouds, rng=rng)


CB_AT = (-4500.0, 42000.0)         # where the thunderhead stands (metres right of the view's middle, and ahead)


def cb_storm():
    """A thunderhead some forty kilometres off: a mass of towers ten kilometres high (three of the towering clouds,
    grown together), spread out under the tropopause into an anvil of ice drawn out downwind (the anvil is our own:
    a sheet from maps); towering cumulus crowd its foot, smaller heaps fill the sky to either side, and nothing
    stands in front of it."""
    rng = np.random.default_rng(21)
    cx, cy = CB_AT
    fall = skybake.drop(math.hypot(cx, cy))
    base = BASE - 150.0
    # The towers: (cloud, metres right, metres nearer, width)
    body = [('congestus_27', 0.0, 0.0, 12500.0), ('congestus_23', -5600.0, 1500.0, 9500.0), ('congestus_21', 5000.0, 2500.0, 8200.0),
            ('congestus_47', 800.0, 5600.0, 7000.0)]
    set_out([(n, cx + dx, cy - dy, w, t, False) for (n, dx, dy, w), t in zip(body, (0.4, 2.1, 3.9, 5.2))], sigma=0.05,
            base=base, rng=rng, coarser=1)
    # The anvil.
    os.makedirs(LIB, exist_ok=True)
    anvil = os.path.join(LIB, 'cb_anvil.vdb')
    if not os.path.exists(anvil) or os.environ.get('NOOR_REGEN'):
        arng = np.random.default_rng(72)
        v, zv = 110.0, 45.0
        x = np.arange(-10000.0, 21000.0, v, dtype=np.float32)[None, :]
        y = np.arange(-10500.0, 10500.0, v, dtype=np.float32)[:, None]
        shape = (y.shape[0], x.shape[1])
        # An egg-shaped sheet of ice, longest downwind (+x): deep where the towers feed it (they flare out into
        # it), thinning in a wedge to a torn, fibrous rim; its underside hangs in soft pouches.
        rag = cloudgen.fbm2(shape, v, 8000.0, arng, octaves=6, gain=0.62)
        a = np.where(x > 0, 15000.0, 7000.0)
        rho = np.sqrt((x / a) ** 2 + (y / 8000.0) ** 2) + 0.13 * rag
        rim = np.clip((1.0 - rho) / 0.22, 0.0, 1.0)
        streak = cloudgen.fbm2(shape, v, 2500.0, arng, octaves=4)
        top = 10300.0 + 220.0 * np.clip(1.0 - rho, 0.0, 1.0) + 55.0 * streak
        thick = 2100.0 * np.clip(1.0 - rho * rho, 0.0, 1.0) ** 0.75 * rim ** 0.5 + 60.0 * streak * rim
        flare = 3000.0 * np.exp(-(x * x + y * y) / (2.0 * 5000.0 ** 2))
        pouch = cloudgen.cells2(shape, v, 1300.0, arng)
        under = top - thick - flare * rim - 130.0 * np.sqrt(np.clip(1.0 - (pouch / 0.85) ** 2, 0.0, 1.0)) * rim
        dens, z0 = cloudgen.layer(under, top, v, zv, soft=2.0)
        dens *= (0.40 + 0.60 * np.clip((1.0 - rho) / 0.45, 0.0, 1.0))[None]         # ice thins towards the rim
        cloudgen.write_vdb(anvil, dens, (float(x[0, 0]), float(y[0, 0]), z0), v, zv)
    skybake.add_volume(anvil, (cx, cy, base - fall), skybake.cloud_material('Anvil', sigma=0.008, anisotropy=0.5))
    # The rest of the sky.
    b0 = math.atan2(cx, cy)

    def in_front(x, y):
        return abs(math.atan2(x, y) - b0) < math.radians(15.0) and math.hypot(x, y) < 62000.0

    clouds = field(rng, [(0.55, CUMULUS, 500.0, 1500.0), (0.45, CUMULUS, 1500.0, 3600.0, 7000.0)], 0.18,
                   clump=1.2, far=58000.0, thin=28000.0, fade=11000.0, keep_out=in_front, small=0.03, sun_road=0.5)
    for k in range(8):                                             # the flanking line round the storm's foot
        side = -1.0 if k % 2 else 1.0
        x = cx + side * rng.uniform(7500.0, 13500.0)
        y = cy + rng.uniform(-4500.0, 2500.0)
        clouds.append((CONGESTUS[int(rng.integers(0, len(CONGESTUS)))], x, y, rng.uniform(2600.0, 4600.0), rng.uniform(0, 6.28), rng.uniform() < 0.5))
    set_out(clouds, rng=rng)


def sc_sheet():
    """Stratocumulus: a low layer of flat cumulus grown together into rolls and pads, lumpy above, grey beneath,
    with breaks of blue between."""
    rng = np.random.default_rng(41)
    clouds = field(rng, [(0.3, CUMULUS, 900.0, 2000.0), (0.7, CUMULUS, 2000.0, 4800.0)], 0.9,
                   near=3800.0, clump=0.8, thin=40000.0, fade=16000.0, far=90000.0, sun_road=1.0, small=0.022, big=0.6,
                   spacing=0.33)
    set_out(clouds, sigma=0.07, base=1100.0, flatten=0.62, rng=rng, coarser=1)


def st_overcast():
    """An overcast: one deep layer everywhere, its underside in long soft swells and darker bellies, torn scud
    hanging under it here and there."""
    mat = skybake.cloud_material(sigma=0.014, anisotropy=0.6)
    layer_pieces('st', overcast_maps(), mat, [(300.0, 15000.0, 30.0, 16.0), (15000.0, 46000.0, 70.0, 24.0),
                                              (46000.0, 115000.0, 170.0, 40.0)], margin=0.30)
    rng = np.random.default_rng(53)
    scud = field(rng, [(1.0, CUMULUS, 500.0, 1500.0)], 0.06, clump=1.6, far=40000.0, thin=25000.0, fade=8000.0, sun_road=1.0)
    set_out(scud, sigma=0.03, base=430.0, flatten=0.6, rng=rng)


SETS = {'cu_fair': cu_fair, 'cu_field': cu_field, 'cu_towers': cu_towers, 'cb_storm': cb_storm, 'sc_sheet': sc_sheet,
        'st_overcast': st_overcast}
INFO = {
    'cu_fair': {'layer': 'low', 'altitude': 1.5, 'kind': 'cumulus'},
    'cu_field': {'layer': 'low', 'altitude': 1.7, 'kind': 'cumulus'},
    'cu_towers': {'layer': 'low', 'altitude': 2.6, 'kind': 'towers'},
    'cb_storm': {'layer': 'low', 'altitude': 5.0, 'kind': 'storm'},
    'sc_sheet': {'layer': 'low', 'altitude': 1.3, 'kind': 'sheet'},
    'st_overcast': {'layer': 'low', 'altitude': 1.1, 'kind': 'overcast'},
}

if __name__ == '__main__':
    def build():
        s = bpy.context.scene
        if os.environ.get('NOOR_BORDER'):
            b = [float(v) for v in os.environ['NOOR_BORDER'].split(',')]
            s.render.use_border = True
            s.render.use_crop_to_border = True
            s.render.border_min_x, s.render.border_max_x, s.render.border_min_y, s.render.border_max_y = b
        if os.environ.get('NOOR_CPU'):
            s.cycles.device = 'CPU'
        s.cycles.volume_step_rate = float(os.environ.get('NOOR_STEP', 1.0))
        SETS[SET]()

    skybake.run(build, INFO[SET], samples=int(os.environ.get('NOOR_BAKE_SPP', 512)), bounces=int(os.environ.get('NOOR_BOUNCES', 40)))
