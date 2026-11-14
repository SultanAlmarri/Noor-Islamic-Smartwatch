"""The Pacific Northwest scenery: still water under dark conifers, a snow-covered volcano beyond.

We stand on the stony shore of a lake. Wooded points reach into it to the left and the right, a rock with a few
trees stands off the left one; across the water runs the far shore, forest to the water's edge, unbroken; behind
it wooded ridges, one behind another; and over them the mountain: the real shape of Mount Rainier (USGS 3DEP
elevation), seen from the north-west. The skyline is the place's own: the Sun rises over the trees and sets
behind them.

    blender -b --factory-startup -P tools/scenery/pnw.py -- preview <out dir> [azimuth elevation]

What is scanned from life and what is made by formula, and every source and licence: tools/scenery/sources/pnw.md.
"""
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake                      # noqa: E402

TOOLS = os.environ.get('NOOR_TOOLS', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools'))
DEM_TIF = os.path.join(TOOLS, 'assets', 'usgs', 'USGS_1_n47w122.tif')
DEM_NPY = os.path.join(TOOLS, 'work', 'pnw', 'n47w122.npy')
SCANS = os.path.join(TOOLS, 'assets', 'polyhaven')        # Poly Haven's scans (CC0), fetched with its get.py
FIR = os.path.join(SCANS, 'fir_tree_01', 'fir_tree_01_1k.blend')
ONLY = os.environ.get('NOOR_PNW_ONLY', '')      # for trying parts alone: any of mountain,hills,island,points,trees,shore

WATER = 0.0                       # the water's surface (scene z)
GROUND = 0.8                      # the shore under our feet, above the water
EYE = (0.0, 0.0, GROUND + 1.7)
SHORE = 6.0                       # metres ahead of us to the water's edge

# ---- The mountain ---------------------------------------------------------------------------------------------
SUMMIT = (535, 868)               # row, column of Columbia Crest in the tile (row 0 = its north edge)
DX, DY = 30.87 * math.cos(math.radians(46.85)), 30.87      # metres per column and per row
FROM = float(os.environ.get('NOOR_PNW_FROM', 315.0))       # where we stand, as a bearing from the summit (degrees
                                                           # east of north): to its north-west
DATUM = float(os.environ.get('NOOR_PNW_DATUM', 1650.0))    # the real height that lies at the water's level
TALL = float(os.environ.get('NOOR_PNW_TALL', 1.15))        # heights are drawn this many times taller
STANDS = float(os.environ.get('NOOR_PNW_STANDS', 9.6))     # degrees over the horizon the summit is to stand
AIM = float(os.environ.get('NOOR_PNW_AIM', 3.5))           # ... and to the right of the view's middle
FAR = (4392.0 - DATUM) * TALL / math.tan(math.radians(STANDS)) * 0.985      # so far from us is the summit, then

# The points that reach into the lake, left and right (middle x, y, half-length, half-width, metres), the rock off
# the left one, and how far off the far shore lies.
LEFT = (-170.0, 150.0, 128.0, 52.0)
RIGHT = (352.0, 385.0, 262.0, 80.0)
ISLET = (-52.0, 420.0, 24.0, 14.0)
FAR_SHORE = 1250.0


def fbm(shape, beta=2.2, seed=1):
    """Smooth random relief (zero mean, unit spread): noise whose strength falls with its fineness."""
    rng = np.random.default_rng(seed)
    f = np.fft.rfft2(rng.standard_normal(shape))
    ky = np.fft.fftfreq(shape[0])[:, None]
    kx = np.fft.rfftfreq(shape[1])[None, :]
    k = np.sqrt(kx * kx + ky * ky)
    k[0, 0] = 1.0
    f *= k ** (-beta / 2)
    f[0, 0] = 0.0
    out = np.fft.irfft2(f, s=shape)
    return ((out - out.mean()) / out.std()).astype(np.float32)


def blur(a, r):
    """Gaussian blur (edges repeat)."""
    n = int(max(1, round(r * 3)))
    k = np.exp(-0.5 * (np.arange(-n, n + 1) / r) ** 2)
    k /= k.sum()
    p = np.pad(a, ((n, n), (0, 0)), mode='edge')
    a = sum(p[i:i + a.shape[0]] * k[i] for i in range(2 * n + 1))
    p = np.pad(a, ((0, 0), (n, n)), mode='edge')
    return sum(p[:, i:i + a.shape[1]] * k[i] for i in range(2 * n + 1)).astype(np.float32)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def load_dem():
    if not os.path.exists(DEM_NPY):
        img = bpy.data.images.load(DEM_TIF)
        img.colorspace_settings.name = 'Non-Color'
        w, h = img.size
        px = np.empty(w * h * img.channels, np.float32)
        img.pixels.foreach_get(px)
        np.save(DEM_NPY, px.reshape(h, w, img.channels)[::-1, :, 0].copy())
        bpy.data.images.remove(img)
    return np.load(DEM_NPY)


def surface_mesh(name, X, Y, Z, colors=None):
    """A mesh over a grid of points (2D arrays); `colors` {name: [ny, nx, 3]} become colour attributes."""
    ny, nx = X.shape
    co = np.stack([X, Y, Z], -1).reshape(-1, 3).astype(np.float32)
    i = np.arange((ny - 1) * (nx - 1))
    r, c = i // (nx - 1), i % (nx - 1)
    a = r * nx + c
    faces = np.stack([a, a + 1, a + nx + 1, a + nx], -1).astype(np.int32)
    me = bpy.data.meshes.new(name)
    me.vertices.add(len(co))
    me.vertices.foreach_set('co', co.ravel())
    me.loops.add(faces.size)
    me.loops.foreach_set('vertex_index', faces.ravel())
    me.polygons.add(len(faces))
    me.polygons.foreach_set('loop_start', np.arange(0, faces.size, 4, dtype=np.int32))
    me.polygons.foreach_set('loop_total', np.full(len(faces), 4, np.int32))
    me.polygons.foreach_set('use_smooth', np.ones(len(faces), bool))
    me.update(calc_edges=True)
    for key, val in (colors or {}).items():
        at = me.attributes.new(key, 'FLOAT_COLOR', 'POINT')
        rgba = np.ones((ny * nx, 4), np.float32)
        rgba[:, :3] = val.reshape(-1, 3)
        at.data.foreach_set('color', rgba.ravel())
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


# ---- Shader helpers ---------------------------------------------------------------------------------------------

def nodes(mat):
    mat.use_nodes = True
    nt = mat.node_tree
    return nt, nt.nodes, nt.links, nt.nodes['Principled BSDF']


def mix(N, L, fac, a, b):
    """Colours a and b mixed by fac; each a socket, a number or a colour."""
    m = N.new('ShaderNodeMixRGB')
    for key, val in (('Fac', fac), ('Color1', a), ('Color2', b)):
        if isinstance(val, (int, float)):
            m.inputs[key].default_value = float(val)
        elif isinstance(val, tuple):
            m.inputs[key].default_value = val if len(val) == 4 else val + (1.0,)
        else:
            L.new(val, m.inputs[key])
    return m.outputs[0]


def noise(N, L, vec, scale, detail=4.0, rough=0.55):
    n = N.new('ShaderNodeTexNoise')
    n.inputs['Scale'].default_value = scale
    n.inputs['Detail'].default_value = detail
    n.inputs['Roughness'].default_value = rough
    L.new(vec, n.inputs['Vector'])
    return n.outputs['Fac']


def ramp(N, L, val, a, b, lo=0.0, hi=1.0):
    r = N.new('ShaderNodeMapRange')
    r.interpolation_type = 'SMOOTHSTEP'
    r.inputs['From Min'].default_value = a
    r.inputs['From Max'].default_value = b
    r.inputs['To Min'].default_value = lo
    r.inputs['To Max'].default_value = hi
    L.new(val, r.inputs['Value'])
    return r.outputs[0]


def add(N, L, a, b):
    m = N.new('ShaderNodeMath')
    m.operation = 'ADD'
    for i, val in enumerate((a, b)):
        if isinstance(val, (int, float)):
            m.inputs[i].default_value = float(val)
        else:
            L.new(val, m.inputs[i])
    return m.outputs[0]


def lies(N, L, most=0.9):
    """How much snow a surface takes in winter: all of it where it lies flat, none where it is steep."""
    geo = N.new('ShaderNodeNewGeometry')
    up = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Normal'], up.inputs[0])
    return ramp(N, L, up.outputs['Z'], 0.5, 0.85, 0.0, most)


# ---- Scans (Poly Haven, CC0): real ground, real rocks, real wood ---------------------------------------------

_scans, _masked = {}, set()


def scan(aid, name=None, wind=0.0, snow=0.85):
    """A scanned model's object `name` (the model's own name if not given); each further call gives a copy that
    shares its mesh. Its materials take their place in the mask the first time."""
    key = name or aid
    if key not in _scans:
        d = os.path.join(SCANS, aid)
        blend = [f for f in os.listdir(d) if f.endswith('.blend')][0]
        with bpy.data.libraries.load(os.path.join(d, blend), link=False) as (src, dst):
            dst.objects = [n for n in src.objects if n == key]
        ob = dst.objects[0]
        for md in list(ob.modifiers):
            ob.modifiers.remove(md)
        for m in ob.data.materials:
            if m.name not in _masked:
                _masked.add(m.name)
                N, L = m.node_tree.nodes, m.node_tree.links
                bake.add_mask(m, wind=wind, snow=lies(N, L, snow) if snow > 0.0 else 0.0, water_level=WATER)
        ob.location = (0.0, 0.0, 0.0)
        bpy.context.scene.collection.objects.link(ob)
        _scans[key] = [ob, False]
    ob, used = _scans[key]
    if used:
        ob = ob.copy()
        ob.parent = None
        bpy.context.scene.collection.objects.link(ob)
    _scans[key][1] = True
    return ob


def box(ob):
    """A scan's own lowest and highest point (its z, unscaled)."""
    zs = [v[2] for v in ob.bound_box]
    return min(zs), max(zs)


def texture(nt, aid, kind, vector, colour=False):
    d = os.path.join(SCANS, aid, 'textures')
    name = [f for f in os.listdir(d) if '_%s_' % kind in f][0]
    node = nt.nodes.new('ShaderNodeTexImage')
    node.image = bpy.data.images.load(os.path.join(d, name), check_existing=True)
    if not colour:
        node.image.colorspace_settings.name = 'Non-Color'
    nt.links.new(vector, node.inputs['Vector'])
    return node.outputs['Color']


def scanned_ground(mat, aid, size, relief, fade=(40.0, 160.0)):
    """Lays a scanned ground (its colour, roughness, fine normals and its real relief) over a material by the
    ground's own metres: once at the scan's true size (`size` metres across) and once larger and turned, the two
    mixed in patches so that no tile is seen twice the same. The relief (metres) fades out between the two
    distances of `fade`. Returns (nodes, links, the surface shader, its colour socket, the position socket)."""
    mat.displacement_method = 'DISPLACEMENT'
    nt, N, L, bsdf = nodes(mat)
    geo = N.new('ShaderNodeNewGeometry')
    P = geo.outputs['Position']
    m1 = N.new('ShaderNodeMapping')
    m1.inputs['Scale'].default_value = (1.0 / size, 1.0 / size, 1.0)
    m2 = N.new('ShaderNodeMapping')
    m2.inputs['Scale'].default_value = (1.0 / (size * 2.3), 1.0 / (size * 2.3), 1.0)
    m2.inputs['Rotation'].default_value = (0.0, 0.0, 0.66)
    L.new(P, m1.inputs['Vector'])
    L.new(P, m2.inputs['Vector'])
    patch = ramp(N, L, noise(N, L, P, 1.6 / size, 3.0), 0.42, 0.58)

    def both(kind, colour=False):
        return mix(N, L, patch, texture(nt, aid, kind, m1.outputs[0], colour), texture(nt, aid, kind, m2.outputs[0], colour))

    diff = both('diff', True)
    L.new(both('rough'), bsdf.inputs['Roughness'])
    nm = N.new('ShaderNodeNormalMap')
    L.new(both('nor_gl'), nm.inputs['Color'])
    L.new(nm.outputs['Normal'], bsdf.inputs['Normal'])
    dist = N.new('ShaderNodeVectorMath')
    dist.operation = 'LENGTH'
    L.new(P, dist.inputs[0])
    disp = N.new('ShaderNodeDisplacement')
    disp.inputs['Midlevel'].default_value = 0.5
    L.new(both('disp'), disp.inputs['Height'])
    L.new(ramp(N, L, dist.outputs['Value'], fade[0], fade[1], relief, 0.0), disp.inputs['Scale'])
    L.new(disp.outputs[0], N['Material Output'].inputs['Displacement'])
    return N, L, bsdf, diff, P


# ---- Far land: the mountain, the ridges before it ---------------------------------------------------------------

def mountain():
    dem = load_dem()
    r0, c0 = SUMMIT
    rows = slice(0, 1500)
    cols = slice(0, 2200)
    z = dem[rows, cols][::-1].astype(np.float32)             # row 0 = south now: east x north = up
    ny, nx = z.shape
    east = (np.arange(cols.start, cols.stop) - c0) * DX
    north = ((rows.stop - 1 - np.arange(ny)) - r0) * -DY
    E, N = np.meshgrid(east, north)
    # What lies where, from the real heights: forest to the tree line, meadow and bare rock above it, snow and ice
    # on the high mountain: more of it in the hollows, none on the walls too steep to hold it or on the sharp ribs.
    gy, gx = np.gradient(z, DY, DX)
    slope = np.degrees(np.arctan(np.hypot(gx, gy)))
    hollow = blur(z, 5.0) - z                                 # positive in gullies and cirques, negative on ribs
    n1 = fbm(z.shape, 2.0, 3)
    n2 = fbm(z.shape, 1.2, 4)
    snowline = 2300.0 + 240.0 * n1 - 7.0 * np.clip(hollow, -40, 60) + 18.0 * np.clip(slope - 30.0, 0, 40)
    snow = smooth(-120.0, 160.0, z - snowline + 90.0 * n2)
    bare = np.maximum(smooth(41.0, 50.0, slope + 4.0 * n2), smooth(10.0, 26.0, -hollow + 5.0 * n2) * smooth(24.0, 34.0, slope))
    snow = snow * (1.0 - 0.92 * bare)
    forest = smooth(1950.0, 1700.0, z + 110.0 * n1 + 50.0 * n2) * smooth(44.0, 34.0, slope)
    meadow = smooth(2250.0, 1850.0, z + 130.0 * n2) * smooth(40.0, 28.0, slope) * (1.0 - forest)
    # To the scene: we stand FAR from the summit on the bearing FROM, looking at it; the datum lies at the water.
    b = math.radians(FROM)
    cam = np.array([math.sin(b), math.cos(b)]) * FAR
    aim = math.radians(FROM + 180.0 - AIM)
    fwd = np.array([math.sin(aim), math.cos(aim)])
    right = np.array([fwd[1], -fwd[0]])
    px, py = E - cam[0], N - cam[1]
    X = px * right[0] + py * right[1]
    Y = px * fwd[0] + py * fwd[1]
    dist = np.hypot(X, Y)
    Z = (z - DATUM) * TALL - dist * dist / (2 * 6371000.0) * 0.87      # the Earth curves away under it
    cls = np.stack([snow, forest * (1.0 - snow), meadow * (1.0 - snow) * (1.0 - forest)], -1)
    top = int(np.argmax(Z))
    print('mountain: %.0f m away, its summit %.1f degrees over the horizon at bearing %.1f' %
          (FAR, float(np.degrees(np.arctan2(Z.flat[top] - EYE[2], dist.flat[top]))),
           float(np.degrees(np.arctan2(X.flat[top], Y.flat[top])))), flush=True)
    Z = np.maximum(Z, -40.0)
    ob = surface_mesh('Mountain', X, Y, Z, {'cls': cls})

    mat = bpy.data.materials.new('Mountain')
    nt, N_, L, bsdf = nodes(mat)
    att = N_.new('ShaderNodeAttribute')
    att.attribute_name = 'cls'
    sep = N_.new('ShaderNodeSeparateColor')
    L.new(att.outputs['Color'], sep.inputs[0])
    geo = N_.new('ShaderNodeNewGeometry')
    P = geo.outputs['Position']
    # Rock: dark andesite, streaked. Forest: nearly black green from afar, mottled by stands of different age.
    rock = mix(N_, L, noise(N_, L, P, 0.004, 6.0, 0.65), (0.060, 0.043, 0.045), (0.170, 0.118, 0.108))
    woods = mix(N_, L, noise(N_, L, P, 0.012, 5.0), (0.010, 0.020, 0.014), (0.024, 0.042, 0.022))
    grass = mix(N_, L, noise(N_, L, P, 0.006, 5.0), (0.060, 0.075, 0.032), (0.120, 0.110, 0.055))
    c = mix(N_, L, sep.outputs['Blue'], rock, grass)
    c = mix(N_, L, sep.outputs['Green'], c, woods)
    # Snow: its edge broken up finer than the heights are known.
    edge = ramp(N_, L, add(N_, L, sep.outputs['Red'], ramp(N_, L, noise(N_, L, P, 0.02, 8.0, 0.7), 0.0, 1.0, -0.22, 0.22)), 0.42, 0.58)
    c = mix(N_, L, edge, c, (0.80, 0.82, 0.85))
    L.new(c, bsdf.inputs['Base Color'])
    L.new(ramp(N_, L, edge, 0.0, 1.0, 0.9, 0.55), bsdf.inputs['Roughness'])
    bsdf.inputs['Specular IOR Level'].default_value = 0.3
    bake.add_mask(mat, wind=0.0, snow=lies(N_, L, 0.85), water_level=WATER)
    ob.data.materials.append(mat)
    return ob


def hills():
    """Wooded ridges behind the far shore, one behind another, right across the view: the mountain's foothills."""
    cell = 15.0
    xs = np.arange(-9000.0, 9000.0 + cell, cell)
    ys = np.arange(2150.0, 11500.0 + cell, cell)
    X, Y = np.meshgrid(xs, ys)
    wander = blur(fbm(X.shape, 2.6, 23), 8.0)
    h = np.zeros(X.shape, np.float32)
    for k, (y0, width, tall) in enumerate(((2900.0, 430.0, 150.0), (4300.0, 620.0, 275.0), (6500.0, 880.0, 480.0), (9300.0, 1100.0, 700.0))):
        along = blur(fbm(X.shape, 2.2, 30 + k), 5.0)
        crest = y0 + 520.0 * np.roll(wander, 97 * k, axis=1)
        h = np.maximum(h, tall * (0.74 + 0.38 * np.tanh(2.5 * along)) * np.exp(-((Y - crest) / width) ** 2))
    h *= 1.0 - 0.22 * np.abs(fbm(X.shape, 2.0, 22))                     # gullies run down them
    h = np.maximum(h, 12.0)
    canopy = 13.0 * fbm(X.shape, 0.4, 24)                               # the tops of their trees
    Z = h + 20.0 + canopy - np.hypot(X, Y) ** 2 / (2 * 6371000.0) * 0.87
    el = np.degrees(np.arctan2(Z - EYE[2], np.hypot(X, Y)))
    az = np.degrees(np.arctan2(X, Y))
    print('hills: they stand', ', '.join('%.1f at %d' % (float(el[np.abs(az - a) < 0.5].max()), a) for a in (-20, -14, -8, -3, 3, 8, 14, 20)), flush=True)
    ob = surface_mesh('Hills', X, Y, Z)
    mat = bpy.data.materials.new('Hills')
    nt, N_, L, bsdf = nodes(mat)
    geo = N_.new('ShaderNodeNewGeometry')
    P = geo.outputs['Position']
    woods = mix(N_, L, noise(N_, L, P, 0.004, 6.0, 0.6), (0.011, 0.024, 0.014), (0.030, 0.052, 0.024))
    woods = mix(N_, L, ramp(N_, L, noise(N_, L, P, 0.05, 3.0), 0.35, 0.7), woods, (0.007, 0.014, 0.010))
    L.new(woods, bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.85
    bsdf.inputs['Specular IOR Level'].default_value = 0.1
    bake.add_mask(mat, wind=0.15, snow=lies(N_, L, 0.6), water_level=WATER)
    ob.data.materials.append(mat)
    return ob


# ---- Land near us: the points and the island --------------------------------------------------------------------

class Land:
    """A piece of ground on a regular grid: its heights above the water (Z) and how far inside its shore each
    place lies (Q: 0 at the shore, 1 at its middle), for the mesh and for standing trees on."""

    def __init__(self, name, spot, cell, top, seed, pad=40.0, ragged=0.22):
        cx, cy, ax, ay = spot
        self.xs = np.arange(cx - ax - pad, cx + ax + pad + cell, cell, dtype=np.float32)
        self.ys = np.arange(cy - ay - pad, cy + ay + pad + cell, cell, dtype=np.float32)
        self.cell = cell
        X, Y = np.meshgrid(self.xs, self.ys)
        k = max(1.0, 2.0 / cell)
        # Its outline, made irregular with coves and spits; a rocky ledge at the water, then a rise to `top`.
        self.Q = 1.0 - np.sqrt(((X - cx) / ax) ** 2 + ((Y - cy) / ay) ** 2) + ragged * blur(fbm(X.shape, 2.4, seed), 2.0 * k)
        q = self.Q
        rough = fbm(X.shape, 1.6, seed + 1)
        h = np.where(q > 0.0, 1.4 * smooth(0.0, 0.05, q) + top * smooth(0.02, 0.75, q) ** 0.8 + 0.5 * rough * smooth(0.0, 0.1, q), 14.0 * q)
        self.Z = np.maximum(h, -12.0).astype(np.float32)
        self.ob = surface_mesh(name, X, Y, self.Z)

    def _at(self, field, x, y):
        fx = np.clip((np.asarray(x) - self.xs[0]) / self.cell, 0, len(self.xs) - 1.001)
        fy = np.clip((np.asarray(y) - self.ys[0]) / self.cell, 0, len(self.ys) - 1.001)
        ix, iy = fx.astype(int), fy.astype(int)
        tx, ty = fx - ix, fy - iy
        return (field[iy, ix] * (1 - tx) + field[iy, ix + 1] * tx) * (1 - ty) + (field[iy + 1, ix] * (1 - tx) + field[iy + 1, ix + 1] * tx) * ty

    def height(self, x, y):
        return self._at(self.Z, x, y)

    def inside(self, x, y):
        return self._at(self.Q, x, y)


class FarShore(Land):
    """The far shore of the lake, right across the view: a shoreline that wanders in coves and low points, a rocky
    ledge at the water, ground rising under the forest to the first ridge. Q counts metres inland (0 at the shore,
    1 at 190 m), as a Land's does its own measure."""

    def __init__(self, name, cell=4.0, seed=61):
        rng = np.random.default_rng(seed)
        self.xs = np.arange(-1500.0, 1500.0 + cell, cell, dtype=np.float32)
        self.ys = np.arange(FAR_SHORE - 330.0, FAR_SHORE + 1150.0 + cell, cell, dtype=np.float32)
        self.cell = cell
        X, Y = np.meshgrid(self.xs, self.ys)
        shore = np.full(len(self.xs), FAR_SHORE, np.float32)
        for wave, size in ((900.0, 130.0), (410.0, 85.0), (170.0, 45.0), (70.0, 16.0), (29.0, 6.0)):
            shore += size * np.sin(self.xs * 2 * math.pi / wave + rng.uniform(0, 6.283)) * rng.uniform(0.6, 1.0)
        d = Y - shore[None, :]                                  # metres inland
        self.Q = (d / 190.0).astype(np.float32)
        rough = fbm(X.shape, 1.6, seed + 1)
        swell = blur(fbm(X.shape, 2.4, seed + 2), 6.0)
        h = 1.4 * smooth(0.0, 8.0, d) + 16.0 * smooth(5.0, 170.0, d) * (1.0 + 0.5 * swell) \
            + 120.0 * np.exp(-((d - 720.0) / 300.0) ** 2) * (0.7 + 0.45 * np.tanh(2.0 * swell)) + 0.5 * rough * smooth(0.0, 20.0, d)
        self.Z = np.where(d > 0.0, h, np.maximum(0.07 * d, -12.0)).astype(np.float32)
        self.ob = surface_mesh(name, X, Y, self.Z)


def shore_material():
    """The ground of the points and the island: scanned rock and moss (an aerial scan, fifty metres across, with
    its own relief) at the water and on the open places, the needle-strewn floor of a conifer wood above."""
    mat = bpy.data.materials.new('Shore')
    N, L, bsdf, rock, P = scanned_ground(mat, 'aerial_rocks_02', 50.0, 2.2, fade=(900.0, 2500.0))
    sep = N.new('ShaderNodeSeparateXYZ')
    L.new(P, sep.inputs[0])
    wet = ramp(N, L, sep.outputs['Z'], WATER + 0.05, WATER + 0.5)
    rock = mix(N, L, wet, mix(N, L, 0.55, rock, (0.02, 0.02, 0.02)), rock)                  # darker where it is wet
    rock = mix(N, L, ramp(N, L, sep.outputs['Z'], -0.4, -4.0), rock, (0.012, 0.018, 0.014))   # and lost in the deep
    fm = N.new('ShaderNodeMapping')
    fm.inputs['Scale'].default_value = (1.0 / 6.0, 1.0 / 6.0, 1.0)
    L.new(P, fm.inputs['Vector'])
    floor = mix(N, L, 0.45, texture(mat.node_tree, 'forrest_ground_03', 'diff', fm.outputs[0], True), (0.02, 0.02, 0.012))
    h = add(N, L, sep.outputs['Z'], ramp(N, L, noise(N, L, P, 0.07, 5.0, 0.6), 0.0, 1.0, -0.9, 0.9))
    L.new(mix(N, L, ramp(N, L, h, 2.4, 4.4), rock, floor), bsdf.inputs['Base Color'])
    bake.add_mask(mat, wind=0.0, snow=lies(N, L, 0.9), water_level=WATER)
    return mat


# ---- Trees ------------------------------------------------------------------------------------------------------

class Parts:
    """Gathers the pieces of one mesh."""

    def __init__(self):
        self.v, self.loops, self.sizes, self.uv, self.mat, self.n = [], [], [], [], [], 0

    def add(self, verts, loops, sizes, uv, mat):
        self.v.append(np.asarray(verts, np.float32))
        self.loops.append(np.asarray(loops, np.int32) + self.n)
        self.sizes.append(np.asarray(sizes, np.int32))
        self.uv.append(np.asarray(uv, np.float32))
        self.mat.append(np.full(len(sizes), mat, np.int32))
        self.n += len(verts)

    def mesh(self, name, materials):
        v = np.concatenate(self.v)
        loops = np.concatenate(self.loops)
        sizes = np.concatenate(self.sizes)
        me = bpy.data.meshes.new(name)
        me.vertices.add(len(v))
        me.vertices.foreach_set('co', v.ravel())
        me.loops.add(len(loops))
        me.loops.foreach_set('vertex_index', loops)
        me.polygons.add(len(sizes))
        me.polygons.foreach_set('loop_start', np.concatenate([[0], np.cumsum(sizes)[:-1]]).astype(np.int32))
        me.polygons.foreach_set('loop_total', sizes)
        me.polygons.foreach_set('use_smooth', np.ones(len(sizes), bool))
        me.polygons.foreach_set('material_index', np.concatenate(self.mat))
        me.update(calc_edges=True)
        uv = me.uv_layers.new(name='UVMap')
        uv.data.foreach_set('uv', np.concatenate(self.uv).ravel())
        for m in materials:
            me.materials.append(m)
        return me


class Card:
    """A spray of fir twigs from Poly Haven's 'Fir Tree 01' (CC0): a few bent polygons carrying a photograph of
    one. It lies along +y from its foot at the origin."""

    def __init__(self, ob):
        me = ob.data
        self.v = np.empty(len(me.vertices) * 3, np.float32)
        me.vertices.foreach_get('co', self.v)
        self.v = self.v.reshape(-1, 3)
        self.loops = np.empty(len(me.loops), np.int32)
        me.loops.foreach_get('vertex_index', self.loops)
        self.sizes = np.empty(len(me.polygons), np.int32)
        me.polygons.foreach_get('loop_total', self.sizes)
        self.uv = np.empty(len(me.loops) * 2, np.float32)
        me.uv_layers[0].data.foreach_get('uv', self.uv)
        self.uv = self.uv.reshape(-1, 2)

    def place(self, parts, M, T, mat):
        """Copies of it turned and scaled by the matrices M [k, 3, 3] and moved to T [k, 3]."""
        k, nv = len(M), len(self.v)
        v = np.einsum('kij,vj->kvi', M, self.v) + T[:, None, :]
        loops = (self.loops[None, :] + (np.arange(k) * nv)[:, None]).ravel()
        parts.add(v.reshape(-1, 3), loops, np.tile(self.sizes, k), np.tile(self.uv, (k, 1)), mat)


def tube(parts, pts, radii, sides, mat):
    """A round limb along the points `pts` [m, 3] with the given radii."""
    pts = np.asarray(pts, np.float32)
    m = len(pts)
    t = np.gradient(pts, axis=0)
    t /= np.maximum(np.linalg.norm(t, axis=1, keepdims=True), 1e-6)
    ref = np.where(np.abs(t[:, 2:3]) > 0.9, np.array([[1.0, 0.0, 0.0]]), np.array([[0.0, 0.0, 1.0]]))
    n = np.cross(t, ref)
    n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-6)
    b = np.cross(t, n)
    a = np.arange(sides) * 2 * math.pi / sides
    ring = np.cos(a)[None, :, None] * n[:, None, :] + np.sin(a)[None, :, None] * b[:, None, :]
    v = pts[:, None, :] + ring * np.asarray(radii, np.float32)[:, None, None]
    i, j = np.meshgrid(np.arange(m - 1), np.arange(sides), indexing='ij')
    j2 = (j + 1) % sides
    quads = np.stack([i * sides + j, i * sides + j2, (i + 1) * sides + j2, (i + 1) * sides + j], -1).reshape(-1, 4)
    parts.add(v.reshape(-1, 3), quads.ravel(), np.full(len(quads), 4), np.zeros((quads.size, 2), np.float32), mat)


def frames(d, roll, scale):
    """Matrices that lay a Card along the unit directions d [k, 3], rolled about them, scaled."""
    z = np.array([0.0, 0.0, 1.0])[None, :] - d * d[:, 2:3]
    bad = np.linalg.norm(z, axis=1) < 0.15
    z[bad] = np.array([1.0, 0.0, 0.0]) - d[bad] * d[bad, 0:1]
    z /= np.linalg.norm(z, axis=1, keepdims=True)
    x = np.cross(d, z)
    c, s = np.cos(roll)[:, None], np.sin(roll)[:, None]
    x, z = x * c + z * s, z * c - x * s
    return np.stack([x, d, z], -1) * np.asarray(scale)[:, None, None]


def conifer(name, cards, mats, seed, height, crown_from, spread, droop=0.5, hang=0.3, lift=0.5, dense=1.0, top='spire',
            card_scale=0.8, dead=0.0, lop=0.0):
    """A conifer as one mesh: a trunk, whorls of branches that sag under their own weight and lift at the tip, and
    on them sprays of twigs. `crown_from`: where the living crown begins, as a part of the height; `spread`: its
    greatest radius (m); `droop`: how much branches sag; `hang`: how many sprays hang under a branch (hemlock,
    cedar); `lift`: how steeply the uppermost branches rise; `dense`: more or fewer sprays; `top`: 'spire', 'nod'
    (a hemlock's bent leader) or 'broken'; `dead`: bare dead branches under the crown; `lop`: lopsidedness, the
    crown fuller towards +x."""
    rng = np.random.default_rng(seed)
    parts = Parts()
    BARK, TWIG, GREY = 0, 1, 2
    H = height
    # The trunk: never quite straight.
    hs = np.linspace(0.0, H, 28)
    sway = np.cumsum(rng.normal(0, 0.035 * H / 50.0, (28, 2)), axis=0) * (hs / H)[:, None]
    if top == 'nod':
        sway[-3:, 0] += np.array([0.15, 0.5, 1.1]) * H / 40.0
        hs[-2:] -= np.array([0.1, 0.5]) * H / 40.0
    r0 = H / 70.0 + 0.12
    rad = r0 * (1.0 - np.linspace(0.0, 1.0, 28)) ** 0.75 + 0.012
    rad[0] *= 1.35
    rad[1] *= 1.12
    trunk = np.column_stack([sway, hs])
    tube(parts, trunk, rad, 8, BARK)
    hk = np.linspace(0.0, H, 28)                              # (heights that only rise, to look the trunk up by)

    def at(h):
        return np.array([np.interp(h, hk, trunk[:, 0]), np.interp(h, hk, trunk[:, 1]), h])

    hb = crown_from * H
    step0 = 0.55 + H / 110.0
    h = hb - dead * (hb * 0.6)
    k = 0
    twig_d, twig_r, twig_s, twig_t = [], [], [], []
    spacing = 0.42 * card_scale / dense

    def clothe(pts, first, size=1.0):
        """Sprays along a limb: to either side, a little forward, each rolled its own way; some hang beneath."""
        seg = np.linalg.norm(np.diff(pts, axis=0), axis=1)
        cum = np.concatenate([[0.0], np.cumsum(seg)])
        total = cum[-1]
        for u in np.arange(first * total, total, spacing):
            i = min(int(np.searchsorted(cum, u, side='right')) - 1, len(pts) - 2)
            f = (u - cum[i]) / max(seg[i], 1e-6)
            p = pts[i] * (1 - f) + pts[i + 1] * f
            d = (pts[i + 1] - pts[i]) / max(seg[i], 1e-6)
            side = np.cross(d, [0.0, 0.0, 1.0])
            side /= max(np.linalg.norm(side), 1e-6)
            near_tip = u / total
            for sgn in (-1.0, 1.0):
                ang = math.radians(rng.uniform(38.0, 62.0))
                a = d * math.cos(ang) + side * sgn * math.sin(ang) + np.array([0.0, 0.0, -rng.uniform(0.05, 0.35) * (0.5 + droop)])
                twig_d.append(a / np.linalg.norm(a))
                twig_r.append(sgn * rng.uniform(0.1, 0.6))
                twig_s.append(size * card_scale * rng.uniform(0.6, 1.05) * (1.0 - 0.4 * near_tip))
                twig_t.append(p)
            if rng.uniform() < hang:
                a = d * 0.35 + np.array([rng.normal(0, 0.25), rng.normal(0, 0.25), -1.0])
                twig_d.append(a / np.linalg.norm(a))
                twig_r.append(rng.uniform(0, 6.28))
                twig_s.append(size * card_scale * rng.uniform(0.5, 0.85))
                twig_t.append(p)
        d = pts[-1] - pts[-2]
        twig_d.append(d / np.linalg.norm(d))
        twig_r.append(rng.uniform(-0.4, 0.4))
        twig_s.append(size * card_scale * rng.uniform(0.6, 0.9))
        twig_t.append(pts[-2])

    while h < H - 0.25:
        t = (h - hb) / (H - hb)                                # 0 at the crown's foot .. 1 at the top
        live = t >= 0.0
        tt = max(t, 0.0)
        profile = (1.0 - tt ** 1.5) ** 0.85 * (0.55 + 0.45 * float(smooth(0.0, 0.25, tt))) if live else 0.45
        if top == 'broken' and tt > 0.86:
            break
        count = rng.integers(3, 7) if live else rng.integers(0, 3)
        if live and rng.uniform() < 0.07:
            count = 1                                           # a gap in the crown
        turn0 = k * 2.39996 + rng.uniform(0, 1.0)
        for j in range(count):
            turn = turn0 + j * 2 * math.pi / max(count, 1) + rng.normal(0, 0.3)
            out = np.array([math.cos(turn), math.sin(turn), 0.0])
            length = spread * profile * rng.uniform(0.5, 1.15) * (1.0 + lop * out[0])
            if rng.uniform() < 0.08:
                length *= 1.3                                   # one that reaches out past the rest
            if not live:
                length = spread * 0.3 * rng.uniform(0.3, 1.0)
            if length < 0.35:
                continue
            # The branch: it leaves the trunk rising (near the top) or level, sags along its length, lifts at the tip.
            e0 = math.radians(-12.0 + (60.0 * lift + 12.0) * tt ** 1.6 + rng.normal(0, 7.0))
            s = np.linspace(0.0, 1.0, 7)
            sag = droop * (1.0 - 0.55 * tt) * (0.55 + 0.1 * length)
            el = e0 - sag * s ** 1.2 + math.radians(28.0) * smooth(0.6, 1.0, s) * (0.4 + 0.6 * droop)
            seg = length / 6.0
            pts = [at(h) + out * float(np.interp(h, hk, rad)) * 0.6]
            bend = rng.normal(0, 0.06)
            for i in range(6):
                a = turn + bend * i
                pts.append(pts[-1] + np.array([math.cos(a) * math.cos(el[i]), math.sin(a) * math.cos(el[i]), math.sin(el[i])]) * seg)
            pts = np.array(pts)
            tube(parts, pts, (0.012 + 0.013 * length) * (1.0 - s) ** 0.8 + 0.006, 5, BARK if live else GREY)
            if not live:
                continue
            clothe(pts, 0.18 if tt > 0.25 else 0.32)
            # It forks: side limbs fan out from it, so that its foliage lies in a plate.
            if length > 1.4:
                for q in range(int(rng.integers(2, 5)) if length > 3.0 else int(rng.integers(1, 3))):
                    u = rng.uniform(0.22, 0.78)
                    i = min(int(u * 6), 5)
                    p0 = pts[i] + (pts[i + 1] - pts[i]) * (u * 6 - i)
                    d = (pts[i + 1] - pts[i]) / seg
                    side = np.cross(d, [0.0, 0.0, 1.0])
                    side /= max(np.linalg.norm(side), 1e-6)
                    ang = math.radians(rng.uniform(26.0, 52.0)) * (1.0 if q % 2 else -1.0)
                    d2 = d * math.cos(ang) + side * math.sin(ang)
                    ln = (1.0 - u) * length * rng.uniform(0.7, 1.15)
                    sub = [p0]
                    for m in range(4):
                        d2 = d2 + np.array([0.0, 0.0, -0.10 * droop * (1.5 - m * 0.5)])
                        d2 /= np.linalg.norm(d2)
                        sub.append(sub[-1] + d2 * ln / 4.0)
                    sub = np.array(sub)
                    tube(parts, sub, np.linspace(0.010 + 0.006 * ln, 0.005, 5), 4, BARK)
                    clothe(sub, 0.12, 0.9)
        h += step0 * (1.0 - 0.45 * tt) * rng.uniform(0.7, 1.35) * (2.2 if not live else 1.0)
        k += 1
    if top != 'broken':
        d = trunk[-1] - trunk[-2]                              # the leader
        for q in range(3):
            twig_d.append(d / np.linalg.norm(d))
            twig_r.append(q * 2.1)
            twig_s.append(card_scale * 0.55)
            twig_t.append(trunk[-1] - d * 0.9)
    d, roll, sc, T = np.array(twig_d), np.array(twig_r), np.array(twig_s), np.array(twig_t)
    pick = rng.integers(0, len(cards), len(d))
    for c, card in enumerate(cards):
        sel = pick == c
        if sel.any():
            card.place(parts, frames(d[sel], roll[sel], sc[sel]), T[sel], TWIG)
    me = parts.mesh(name, mats)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob, len(me.polygons)


def tree_materials():
    """The twigs' own material from the Poly Haven fir (a photograph of fir twigs), darkened to the blue-green of
    the coast's firs and hemlocks and varied from tree to tree; bark; and the grey of dead wood."""
    twig = bpy.data.materials['fir_tree_01_twig']
    nt = twig.node_tree
    N, L = nt.nodes, nt.links
    hsv = N['Hue Saturation Value']
    info = N.new('ShaderNodeObjectInfo')
    L.new(ramp(N, L, info.outputs['Random'], 0.0, 1.0, 0.66, 1.12), hsv.inputs['Value'])
    tint = N.new('ShaderNodeMath')
    tint.operation = 'FRACT'
    scr = N.new('ShaderNodeMath')
    scr.operation = 'MULTIPLY'
    scr.inputs[1].default_value = 7.31
    L.new(info.outputs['Random'], scr.inputs[0])
    L.new(scr.outputs[0], tint.inputs[0])
    L.new(ramp(N, L, tint.outputs[0], 0.0, 1.0, 0.482, 0.528), hsv.inputs['Hue'])
    hsv.inputs['Saturation'].default_value = 1.0
    geo = N.new('ShaderNodeNewGeometry')
    up = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Normal'], up.inputs[0])
    top = N.new('ShaderNodeMath')
    top.operation = 'ABSOLUTE'
    L.new(up.outputs['Z'], top.inputs[0])
    bake.add_mask(twig, wind=0.9, snow=ramp(N, L, top.outputs[0], 0.35, 0.8, 0.0, 0.7))

    bark = bpy.data.materials.new('Bark')
    nt, N, L, bsdf = nodes(bark)
    tc = N.new('ShaderNodeTexCoord')
    st = N.new('ShaderNodeMapping')
    st.inputs['Scale'].default_value = (9.0, 9.0, 1.2)
    L.new(tc.outputs['Object'], st.inputs['Vector'])
    L.new(mix(N, L, noise(N, L, st.outputs[0], 1.0, 5.0, 0.65), (0.018, 0.014, 0.011), (0.075, 0.060, 0.048)), bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.9
    bsdf.inputs['Specular IOR Level'].default_value = 0.2
    bake.add_mask(bark, wind=0.08, snow=0.0)

    grey = bpy.data.materials.new('DeadWood')
    nt, N, L, bsdf = nodes(grey)
    tc = N.new('ShaderNodeTexCoord')
    st = N.new('ShaderNodeMapping')
    st.inputs['Scale'].default_value = (14.0, 14.0, 0.8)
    L.new(tc.outputs['Object'], st.inputs['Vector'])
    L.new(mix(N, L, noise(N, L, st.outputs[0], 1.0, 4.0, 0.6), (0.11, 0.10, 0.09), (0.30, 0.28, 0.25)), bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.8
    bake.add_mask(grey, wind=0.0, snow=lies(N, L, 0.5))
    return [bark, twig, grey]


def load_cards():
    names = ['fir_tree_01_twig_main_%s_LOD%d' % (v, l) for v in 'abc' for l in (1, 2)]
    with bpy.data.libraries.load(FIR, link=False) as (src, dst):
        dst.objects = [n for n in names if n in src.objects]
    obs = {o.name: o for o in dst.objects}
    near = [Card(obs['fir_tree_01_twig_main_%s_LOD1' % v]) for v in 'abc']
    far = [Card(obs['fir_tree_01_twig_main_%s_LOD2' % v]) for v in 'abc']
    return near, far


TREES = {
    # Douglas firs: tall, the crown high on the trunk in a closed stand, irregular.
    'fir_a': dict(seed=11, height=50.0, crown_from=0.34, spread=6.6, droop=0.45, hang=0.25, lift=0.55, dead=0.5),
    'fir_b': dict(seed=12, height=56.0, crown_from=0.42, spread=7.4, droop=0.55, hang=0.3, lift=0.4, dead=0.7, lop=0.25),
    'fir_c': dict(seed=13, height=44.0, crown_from=0.22, spread=6.2, droop=0.4, hang=0.2, lift=0.6, lop=-0.2),
    'fir_d': dict(seed=20, height=63.0, crown_from=0.48, spread=7.8, droop=0.6, hang=0.3, lift=0.35, dead=0.6, lop=-0.3),
    # An old one that lost its top to a winter storm.
    'fir_old': dict(seed=14, height=47.0, crown_from=0.38, spread=7.6, droop=0.6, hang=0.3, lift=0.3, top='broken', dead=0.8),
    # Western hemlock: soft, drooping, its leader nodding.
    'hemlock': dict(seed=15, height=39.0, crown_from=0.16, spread=5.4, droop=0.75, hang=0.7, lift=0.35, top='nod'),
    # Sitka spruce at the shore: stiff, dense, branched to the ground.
    'spruce': dict(seed=16, height=42.0, crown_from=0.07, spread=6.2, droop=0.3, hang=0.25, lift=0.5),
    # Western red cedar: a broad cone of sweeping, hanging sprays.
    'cedar': dict(seed=17, height=34.0, crown_from=0.06, spread=6.6, droop=0.95, hang=0.9, lift=0.25),
    # Young trees at the forest's edge, green to the ground.
    'young_a': dict(seed=18, height=17.0, crown_from=0.04, spread=3.8, droop=0.35, hang=0.2, lift=0.6, card_scale=0.62),
    'young_b': dict(seed=19, height=24.0, crown_from=0.05, spread=4.5, droop=0.45, hang=0.4, lift=0.5, card_scale=0.7),
}


def tree_kinds(near, far, mats, which=None):
    """The trees of the coast's forest, each kind once finely (for trees near us) and once lightly (far ones):
    {name: (fine, light, height, crown radius)}."""
    kinds = {}
    for name, kw in TREES.items():
        if which and name not in which:
            continue
        fine, n1 = conifer(name, near, mats, **kw)
        kw2 = dict(kw)
        kw2['card_scale'] = kw.get('card_scale', 0.8) * 1.45
        kw2['dense'] = kw.get('dense', 1.0) * 0.7
        light, n2 = conifer(name + '_far', far, mats, **kw2)
        kinds[name] = (fine, light, kw['height'], kw['spread'])
        print('tree', name, n1, 'and', n2, 'polygons', flush=True)
    # A snag: a dead trunk, silver, with the stubs of its branches.
    parts = Parts()
    rng = np.random.default_rng(31)
    hs = np.linspace(0.0, 26.0, 16)
    trunk = np.column_stack([np.cumsum(rng.normal(0, 0.05, (16, 2)), axis=0), hs])
    tube(parts, trunk, 0.42 * (1.0 - hs / 34.0) ** 0.8, 8, 2)
    for h in np.arange(9.0, 25.0, 1.3):
        a = rng.uniform(0, 6.28)
        ln = rng.uniform(0.8, 3.2)
        p0 = np.array([np.interp(h, hs, trunk[:, 0]), np.interp(h, hs, trunk[:, 1]), h])
        pts = p0[None, :] + np.linspace(0, 1, 5)[:, None] * np.array([math.cos(a), math.sin(a), rng.uniform(-0.3, 0.2)])[None, :] * ln
        tube(parts, pts, np.linspace(0.07, 0.015, 5), 5, 2)
    ob = bpy.data.objects.new('snag', parts.mesh('snag', mats))
    bpy.context.scene.collection.objects.link(ob)
    kinds['snag'] = (ob, ob, 26.0, 1.5)
    return kinds


def instancer(name, child, places):
    """Draws `child` at every place [n, 7]: x, y, z, turn (radians), size, lean east, lean north. (One small square
    per place; Blender draws the child on each face, turned and scaled as the face is.)"""
    places = np.asarray(places, np.float32).reshape(-1, 7)
    n = len(places)
    c, s = np.cos(places[:, 3]), np.sin(places[:, 3])
    half = 0.5 * places[:, 4]
    co = np.zeros((n, 4, 3), np.float32)
    for k, (cx, cy) in enumerate(((-1, -1), (1, -1), (1, 1), (-1, 1))):
        ox, oy = half * (cx * c - cy * s), half * (cx * s + cy * c)
        co[:, k, 0] = places[:, 0] + ox
        co[:, k, 1] = places[:, 1] + oy
        co[:, k, 2] = places[:, 2] - ox * places[:, 5] - oy * places[:, 6]
    me = bpy.data.meshes.new(name)
    me.vertices.add(n * 4)
    me.vertices.foreach_set('co', co.ravel())
    me.loops.add(n * 4)
    me.loops.foreach_set('vertex_index', np.arange(n * 4, dtype=np.int32))
    me.polygons.add(n)
    me.polygons.foreach_set('loop_start', np.arange(0, n * 4, 4, dtype=np.int32))
    me.polygons.foreach_set('loop_total', np.full(n, 4, np.int32))
    me.update(calc_edges=True)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.instance_type = 'FACES'
    ob.use_instance_faces_scale = True
    ob.instance_faces_scale = 1.0
    ob.show_instancer_for_render = False
    ob.show_instancer_for_viewport = False
    child.parent = ob
    child.location = (0.0, 0.0, 0.0)
    return ob


def plant(land, kinds, name, seed, spacing, lod_far, mixture, edge=('spruce', 'cedar', 'young_a', 'young_b', 'hemlock'), deep=9.0):
    """Stands a forest on a piece of land. `mixture`: {kind: share} for the trees within; at the shore stand the
    kinds of `edge`, smaller, green to the ground. `deep`: how far inland (in the land's own measure Q) trees are
    still stood (beyond, they would not be seen)."""
    rng = np.random.default_rng(seed)
    gx, gy = np.meshgrid(np.arange(land.xs[0], land.xs[-1], spacing), np.arange(land.ys[0], land.ys[-1], spacing))
    x = (gx + rng.uniform(-0.45, 0.45, gx.shape) * spacing).ravel()
    y = (gy + rng.uniform(-0.45, 0.45, gy.shape) * spacing).ravel()
    q = land.inside(x, y)
    z = land.height(x, y)
    keep = (q > 0.035) & (q < deep) & (z > 0.9) & (rng.uniform(size=len(x)) < smooth(0.02, 0.12, q) * 0.9 + 0.08)
    x, y, z, q = x[keep], y[keep], z[keep], q[keep]
    names = [k for k in mixture if k in kinds]
    share = np.array([mixture[k] for k in names], float)
    which = rng.choice(len(names), size=len(x), p=share / share.sum())
    edge = [k for k in edge if k in kinds] or names
    dist = np.hypot(x, y)
    az = np.degrees(np.arctan2(x, y))
    places, counts = {}, {}
    for i in range(len(x)):
        kind = names[which[i]]
        if q[i] < 0.13 and rng.uniform() < 0.8:
            kind = edge[rng.integers(0, len(edge))]
        if rng.uniform() < 0.012 and 'snag' in kinds:
            kind = 'snag'
        fine, light, h0, r0 = kinds[kind]
        size = (0.42 + 0.78 * rng.uniform() ** 0.55) * (0.72 + 0.28 * float(smooth(0.03, 0.3, q[i])))
        # The Sun's path: the tree's whole crown must stay under it.
        half = math.degrees(math.atan2(r0 * size, dist[i]))
        lim = min(bake.skyline_limit(az[i] - half), bake.skyline_limit(az[i] + half), bake.skyline_limit(az[i]))
        if lim < 45.0 and abs(az[i]) < 9.0:                  # (only under the top of the Sun's arch)
            room = math.tan(math.radians(lim - 0.4)) * dist[i] + EYE[2] - z[i]
            if room < h0 * size:
                size = room / h0
                if size < 0.3:
                    continue
        lean = rng.normal(0, 0.025, 2)
        places.setdefault((kind, bool(dist[i] > lod_far)), []).append((x[i], y[i], z[i] - 0.15, rng.uniform(0, 6.283), size, lean[0], lean[1]))
        counts[kind] = counts.get(kind, 0) + 1
    for (kind, is_far), pl in places.items():
        fine, light, h0, r0 = kinds[kind]
        child = light if is_far else fine
        if child.parent is not None:                           # one child per instancer: a copy that shares its mesh
            child = child.copy()
            bpy.context.scene.collection.objects.link(child)
        instancer('%s_%s_%d' % (name, kind, is_far), child, pl)
    print('forest', name, sum(counts.values()), counts, flush=True)


# ---- Our own shore ----------------------------------------------------------------------------------------------

def beach_height(x, y):
    """The ground where we stand and the bed of the bay before it (metres over the water)."""
    d = SHORE + 0.9 * np.sin(x / 6.5 + 0.7) + 0.5 * np.sin(x / 2.3 + 2.1) - y        # inland of the water's edge
    return np.where(d > 0.0, 1.075 * (1.0 - np.exp(-d / 4.4)), np.maximum(0.06 * d - 0.0012 * d * d, -14.0))


def beach():
    """The beach under our feet and the bed of the shallows: scanned river pebbles, with their own relief."""
    ys = np.concatenate([np.linspace(-8.0, 0.0, 9)[:-1], np.geomspace(1.0, 140.0, 110) - 1.0])
    half = np.geomspace(1.0, 120.0, 70) - 1.0
    xs = np.concatenate([-half[::-1], half[1:]])
    X, Y = np.meshgrid(xs, ys)
    ob = bake.grid_mesh('Beach', xs, ys, beach_height(X, Y))
    bake.adaptive(ob, 1.0)
    mat = bpy.data.materials.new('Beach')
    N, L, bsdf, stones, P = scanned_ground(mat, 'dry_river_pebbles', 2.0, 0.13, fade=(30.0, 110.0))
    sep = N.new('ShaderNodeSeparateXYZ')
    L.new(P, sep.inputs[0])
    # Wet at the water's edge, dark and a little green under it, lost in the deep.
    dry = ramp(N, L, sep.outputs['Z'], WATER + 0.02, WATER + 0.2)
    stones = mix(N, L, dry, mix(N, L, 0.6, stones, (0.012, 0.014, 0.012)), stones)
    stones = mix(N, L, ramp(N, L, sep.outputs['Z'], -0.4, -4.0), stones, (0.012, 0.018, 0.014))
    L.new(stones, bsdf.inputs['Base Color'])
    bake.add_mask(mat, wind=0.0, snow=lies(N, L, 0.85), water_level=WATER)
    ob.data.materials.append(mat)
    return ob


ROCKS = [('rock_moss_set_01', 'rock_moss_set_01_rock%02d' % k) for k in range(1, 7)]      # mossy boulders, 2 to 3 m
STONES = [('rock_07', 'rock_07_LOD1'), ('rock_09', 'rock_09_LOD1')] + [('rock_moss_set_02', 'rock_moss_set_02_rock%02d' % k) for k in (8, 9, 12)]


def scatter(tag, kinds, places):
    """Instances of scans: `places` [(which kind, x, y, ground z, turn, size, sink)]; a scan stands with `sink` of
    its own height under the ground."""
    per = {}
    for k, x, y, z, turn, size, sink in places:
        per.setdefault(k, []).append((x, y, z, turn, size, sink))
    for k, pl in per.items():
        ob = scan(*kinds[k])
        lo, hi = box(ob)
        rows = [(x, y, z - (lo + sink * (hi - lo)) * size, turn, size, 0.0, 0.0) for x, y, z, turn, size, sink in pl]
        instancer('%s_%d' % (tag, k), ob, rows)


def bush(name, cards, mat, seed, size=3.0):
    """A shore bush (willow, young alder): leafy sprays on a few stems, lighter than the conifers behind it."""
    rng = np.random.default_rng(seed)
    parts = Parts()
    d, roll, sc, T = [], [], [], []
    for _ in range(int(rng.integers(5, 9))):
        a = rng.uniform(0, 6.283)
        tilt = rng.uniform(0.1, 0.9)
        ln = size * rng.uniform(0.6, 1.1)
        u = np.linspace(0.0, 1.0, 6)
        out = np.array([math.cos(a), math.sin(a), 0.0])
        pts = (out[None, :] * (tilt * u ** 1.4)[:, None] + np.array([0.0, 0.0, 1.0])[None, :] * (u * (1.0 - 0.25 * tilt * u))[:, None]) * ln
        tube(parts, pts, np.linspace(0.05, 0.01, 6), 4, 0)
        for k in range(1, 6):
            for _ in range(int(rng.integers(3, 6))):
                v = rng.normal(size=3)
                v[2] = abs(v[2]) * 0.6 - 0.1
                d.append(v / np.linalg.norm(v))
                roll.append(rng.uniform(0, 6.28))
                sc.append(size * rng.uniform(0.16, 0.3))
                T.append(pts[k])
    d, roll, sc, T = np.array(d), np.array(roll), np.array(sc), np.array(T)
    pick = rng.integers(0, len(cards), len(d))
    for c, card in enumerate(cards):
        sel = pick == c
        if sel.any():
            card.place(parts, frames(d[sel], roll[sel], sc[sel]), T[sel], 1)
    ob = bpy.data.objects.new(name, parts.mesh(name, mat))
    bpy.context.scene.collection.objects.link(ob)
    return ob


def leaf_material(twig):
    """The twigs' material once more, in the yellower, lighter green of shore bushes."""
    leaf = twig.copy()
    leaf.name = 'Leafy'
    N = leaf.node_tree.nodes
    hsv = N['Hue Saturation Value']
    for sock in ('Value', 'Hue'):
        for ln in list(hsv.inputs[sock].links):
            leaf.node_tree.links.remove(ln)
    hsv.inputs['Hue'].default_value = 0.47
    hsv.inputs['Value'].default_value = 1.25
    hsv.inputs['Saturation'].default_value = 1.05
    return leaf


def shoreline(land, count, seed, lo=0.012, hi=0.05):
    """Places along a piece of land's shore, just above the water: x, y, z and the shore's direction there."""
    rng = np.random.default_rng(seed)
    gy, gx = np.gradient(land.Q, land.cell)
    ok = np.argwhere((land.Q > lo) & (land.Q < hi) & (land.Z > 0.25) & (land.Z < 1.6))
    if len(ok) == 0:
        return np.zeros((0, 4))
    pick = ok[rng.integers(0, len(ok), count)]
    x, y = land.xs[pick[:, 1]], land.ys[pick[:, 0]]
    along = np.arctan2(gy[pick[:, 0], pick[:, 1]], gx[pick[:, 0], pick[:, 1]]) + math.pi / 2
    return np.column_stack([x, y, land.Z[pick[:, 0], pick[:, 1]], along])


def shore(kinds_ready):
    """Our beach and what lies on it, all of it scanned from life: boulders, loose stones, great logs and a root
    wad the winter storms left, grass and ferns among them."""
    rng = np.random.default_rng(71)
    beach()
    # Loose stones on the beach and in the shallows, many small and a few large.
    places = []
    for _ in range(420):
        y = rng.uniform(1.2, 26.0)
        x = rng.uniform(-1.0, 1.0) * (2.5 + 0.48 * y)
        k = int(rng.integers(0, len(STONES)))
        size = min(0.5 * math.exp(rng.normal(0.0, 0.5)), 1.6) * (2.0 if k == 1 else 1.0 if k == 0 else 0.22)
        places.append((k, x, y, float(beach_height(x, y)), rng.uniform(0, 6.283), size, 0.3))
    scatter('stones', STONES, places)
    # Boulders standing out of the water before us, and a few up the beach.
    places = []
    for x, y, size in ((-3.6, 12.0, 0.75), (-2.1, 13.4, 0.4), (3.3, 16.0, 0.75), (5.4, 22.0, 1.0), (6.9, 23.6, 0.6), (-7.5, 27.0, 1.2),
                       (-1.2, 8.6, 0.3), (9.0, 30.0, 1.5), (-2.5, 4.7, 0.4), (3.1, 2.9, 0.5)):
        places.append((int(rng.integers(0, len(ROCKS))), x, y, float(beach_height(x, y)), rng.uniform(0, 6.283), size, 0.12))
    scatter('rocks', ROCKS, places)
    # Driftwood: a long trunk across the beach before us, a stout one with its butt in the water, a root wad.
    for aid, name, x, y, turn, size, sink in (('dead_tree_trunk', None, 0.3, 5.0, 0.5, 2.0, 0.12), ('dead_tree_trunk_02', 'dead_tree_trunk_02_LOD1', 2.7, 8.8, 1.15, 1.0, 0.3),
                                              ('root_cluster_01', None, -2.0, 6.7, 0.9, 0.7, 0.08), ('dead_tree_trunk', None, -1.6, 2.9, -0.35, 1.6, 0.15)):
        ob = scan(aid, name)
        lo, hi = box(ob)
        ob.scale = (size, size, size)
        ob.rotation_euler = (0.0, 0.0, turn)
        ob.location = (x, y, float(beach_height(x, y)) - (lo + sink * (hi - lo)) * size)
    # Grass in clumps among the stones (taller than it grew where it was scanned: this is shore sedge), and ferns
    # by the old wood.
    grass = [('grass_medium_01', 'grass_medium_01_%s_LOD1' % k) for k in ('tall_a', 'tall_b', 'tall_c', 'large_a', 'large_b')]
    places = []
    for _ in range(90):
        y = rng.uniform(1.4, 5.7)
        x = rng.choice([-1.0, 1.0]) * (0.8 + rng.uniform(0.0, 1.0) * (0.5 + 0.45 * y))
        k = int(rng.integers(0, len(grass)))
        places.append((k, x, y, float(beach_height(x, y)) + 0.02, rng.uniform(0, 6.283), rng.uniform(1.6, 3.0), 0.03))
    for ob_kind in grass:
        scan(ob_kind[0], ob_kind[1], wind=1.0, snow=0.3).hide_render = False
        _scans[ob_kind[1]][1] = False
    scatter('grass', grass, places)
    ferns = [('fern_02', 'fern_02_%s' % k) for k in 'bc']
    for ob_kind in ferns:
        scan(ob_kind[0], ob_kind[1], wind=1.0, snow=0.3)
        _scans[ob_kind[1]][1] = False
    scatter('ferns', ferns, [(int(rng.integers(0, 2)), x, y, float(beach_height(x, y)) + 0.02, rng.uniform(0, 6.283), rng.uniform(1.2, 1.8), 0.03)
                             for x, y in ((-2.6, 5.7), (-1.1, 6.0), (-2.9, 6.3), (1.9, 5.9), (1.0, 3.6), (-1.3, 3.5), (2.2, 6.6))])


def deep():
    """The bed of the open water, out to the horizon: dark and deep. (The phone draws the water over whatever is
    marked as lying under it, so there must be something under all of it.)"""
    bpy.ops.mesh.primitive_plane_add(size=1.0, location=(0.0, 60000.0, -14.5))
    ob = bpy.context.object
    ob.name = 'Deep'
    ob.scale = (160000.0, 140000.0, 1.0)
    mat = bpy.data.materials.new('Deep')
    nt, N, L, bsdf = nodes(mat)
    bsdf.inputs['Base Color'].default_value = (0.012, 0.018, 0.014, 1.0)
    bsdf.inputs['Roughness'].default_value = 0.9
    bake.add_mask(mat, wind=0.0, snow=0.0, water_level=WATER)
    ob.data.materials.append(mat)


def haze():
    """Air between us and the far things, for the preview pictures only (the phone lays its own, of the moment)."""
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0.0, 25000.0, 1400.0))
    box = bpy.context.object
    box.name = 'PreviewAir'
    box.scale = (90000.0, 90000.0, 3000.0)
    mat = bpy.data.materials.new('PreviewAir')
    mat.use_nodes = True
    nt = mat.node_tree
    for nd in list(nt.nodes):
        nt.nodes.remove(nd)
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    vol = nt.nodes.new('ShaderNodeVolumeScatter')
    vol.inputs['Color'].default_value = (0.70, 0.82, 1.0, 1)
    vol.inputs['Density'].default_value = float(os.environ.get('NOOR_PNW_AIR', 1.0e-5))
    vol.inputs['Anisotropy'].default_value = 0.35
    nt.links.new(vol.outputs[0], out.inputs['Volume'])
    box.data.materials.append(mat)
    box.visible_shadow = False


def build():
    mode = bake.args()[0]
    want = lambda part: not ONLY or part in ONLY.split(',')
    # (Forty thousand trees clothed in sprays cut out of photographs: light has many leaves to pass, and the
    # picture is slow to settle. A little more noise is let stand; it is cleaned light by light afterwards.)
    bpy.context.scene.cycles.transparent_max_bounces = 28
    bpy.context.scene.cycles.adaptive_threshold = 0.035
    if want('mountain'):
        mountain()
    if want('hills'):
        hills()
    lands = []
    if want('island') or want('points'):
        rock = shore_material()
        if want('island'):
            lands.append(('far', FarShore('FarShore'), 8.0, 500.0))
            lands.append(('islet', Land('Islet', ISLET, 0.75, 3.0, 47, pad=20.0, ragged=0.3), 7.0, 500.0))
        if want('points'):
            lands.append(('left', Land('LeftPoint', LEFT, 1.0, 7.0, 43), 6.0, 230.0))
            lands.append(('right', Land('RightPoint', RIGHT, 1.5, 11.0, 45), 6.5, 230.0))
        for name, land, _, _ in lands:
            land.ob.data.materials.append(rock)
            bake.adaptive(land.ob, 1.5 if name != 'far' else 2.5)
    if want('trees') and lands:
        near, far = load_cards()
        mats = tree_materials()
        kinds = tree_kinds(near, far, mats)
        mixture = {'fir_a': 3, 'fir_b': 2.5, 'fir_c': 2.5, 'fir_d': 1.0, 'fir_old': 0.6, 'hemlock': 2.2, 'spruce': 1.6, 'cedar': 1.2, 'young_a': 1.0, 'young_b': 1.4}
        for i, (name, land, spacing, lod_far) in enumerate(lands):
            plant(land, kinds, name, 50 + i, spacing, lod_far, mixture, deep=3.6 if name == 'far' else 9.0)
        for kind, (fine, light, _, _) in kinds.items():      # the patterns themselves are not part of the picture
            for ob in {fine, light}:
                if ob.parent is None:
                    ob.hide_render = True
        # Bushes at the water's edge, and the logs the storms have left along every shore.
        leafy = [mats[0], leaf_material(mats[1])]
        bushes = [bush('bush%d' % k, far, leafy, 60 + k, 2.4 + 0.9 * k) for k in range(3)]
        bp = [[] for _ in bushes]
        rocks, drift = [], []
        rng = np.random.default_rng(77)
        for name, land, _, _ in lands:
            n = {'far': 520, 'islet': 14, 'left': 40, 'right': 70}[name]
            big = 2.0 if name == 'far' else 1.0                 # far off, only great rocks and whole trunks are seen
            for x, y, z, along in shoreline(land, n, 5, 0.03, 0.09):
                bp[rng.integers(0, 3)].append((x, y, z - 0.1, rng.uniform(0, 6.283), rng.uniform(0.7, 1.3), 0.0, 0.0))
            for x, y, z, along in shoreline(land, n, 6, 0.004, 0.05):
                rocks.append((int(rng.integers(0, len(ROCKS))), x, y, z, rng.uniform(0, 6.283), big * rng.uniform(0.5, 1.6), 0.35))
            for x, y, z, along in shoreline(land, n // 2, 7):
                drift.append((0, x, y, z + 0.1, along + rng.normal(0, 0.3), big * rng.uniform(2.0, 4.5), 0.1))
        for k, ob in enumerate(bushes):
            if bp[k]:
                instancer('bushes%d' % k, ob, bp[k])
        scatter('shore_rocks', ROCKS, rocks)
        scatter('drift', [('dead_tree_trunk', None)], drift)
    if want('shore'):
        shore(None)
    deep()
    if mode in ('preview', 'look') and not os.environ.get('NOOR_PNW_NOAIR'):
        haze()


# (A bake of this forest at bake.py's six hundred samples takes hours on the owner's PC; 240 are enough.)
_render = bake.bake_render
bake.bake_render = lambda out, yaw=0.0, samples=512, aovs=(): _render(out, yaw, min(samples, 240), aovs)

# (The preview's own sky is made clearer than bake.py's default: this coast's air is clean.)
_sky = bake.preview_sky
bake.preview_sky = lambda az, el, yaw=0.0, strength=1.0, turbidity=2.2: _sky(az, el, yaw, strength, 0.7)


if __name__ == '__main__':
    bake.run(build, EYE, aovs=('mask',), water_level=WATER, exposure=-2.3)
