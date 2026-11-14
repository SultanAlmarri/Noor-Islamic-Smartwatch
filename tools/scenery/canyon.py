"""The Red Rock scenery: Monument Valley, as it stands.

The land's shape is the real one: the USGS elevation model of the valley (canyon_dem.py cuts it out of its tiles,
canyon_terrain.py reads it and says where we stand), with the Mittens, Merrick Butte and the mesas behind them where
and as high as they are. Nothing is moved, added or cut away. On that shape lie scanned surfaces (Poly Haven:
sandstone walls, bedded ledges, stony talus, red earth and sand), and on the ground stand scanned brush, shrubs,
grass, stones and fallen blocks. Where the model's ten metres round a wall off, the wall's joints and beds are cut
back into it as relief; the outlines and heights stay the model's.

    blender -b --factory-startup -P tools/scenery/canyon.py -- preview <out dir> [azimuth elevation]

Sources and licences: tools/scenery/sources/canyon.md.
"""
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake                      # noqa: E402
import canyon_terrain as T       # noqa: E402

EYE_H = 1.7                       # metres above the ground we stand on
# The valley's beds lie level: by its height above the sea a point is floor, talus, wall or cap. (Read off the model:
# round all three buttes the slopes of rubble end and the sheer wall begins near 1 715 m, and the wall ends near 1 850.)
WALL_FOOT, WALL_TOP = 1718.0, 1852.0
LEVEL_Z = [1400.0, 1605.0, 1625.0, WALL_FOOT - 6.0, WALL_FOOT + 2.0, WALL_FOOT + 14.0, WALL_TOP - 6.0, WALL_TOP + 5.0, 3000.0]
LEVEL_V = [0.0, 0.05, 0.40, 0.56, 0.61, 0.75, 0.75, 1.0, 1.0]
CLIFF_BASE, CLIFF_TOP = 190.0, 325.0     # the same two heights above the ground we stand on (set by build())
# The three buttes of the view (their summits in the model), for the blocks fallen on their slopes.
BUTTES = [('West Mitten', 36.99037, -110.09546), ('East Mitten', 36.98870, -110.06889), ('Merrick Butte', 36.98019, -110.08593)]

# The valley round us on a plain grid (for what the ground is made of and where plants stand), FAR_CELL metres a cell.
FAR_CELL = 2.5
FAR_X0, FAR_X1, FAR_Y0, FAR_Y1 = -3200.0, 3200.0, -250.0, 8750.0
# The ground round the camera, as a mesh of its own, NEAR_CELL metres between points.
NEAR_CELL = 0.5
NEAR_X0, NEAR_X1, NEAR_Y0, NEAR_Y1 = -120.0, 120.0, -30.0, 260.0
DICE = float(os.environ.get('NOOR_DICE', 1.5))     # pixels a diced polygon may span

# ---- Noise ------------------------------------------------------------------------------------------------------

def fractal(shape, cell, lo, hi, beta, seed):
    """A smooth random field on a grid (periodic): wavelengths between `lo` and `hi` metres, power falling as
    1/f^beta, mean 0 and unit spread."""
    rng = np.random.default_rng(seed)
    f = np.fft.rfft2(rng.standard_normal(shape).astype(np.float32))
    ky = np.fft.fftfreq(shape[0], cell)[:, None]
    kx = np.fft.rfftfreq(shape[1], cell)[None, :]
    k = np.sqrt(kx * kx + ky * ky)
    k[0, 0] = 1.0
    amp = k ** (-beta / 2.0)
    amp *= 1.0 / (1.0 + (k * lo) ** 8)                       # nothing finer than `lo`
    amp *= 1.0 - 1.0 / (1.0 + (k * hi) ** 6)                 # nothing broader than `hi`
    amp[0, 0] = 0.0
    out = np.fft.irfft2(f * amp, s=shape).astype(np.float32)
    return out / out.std()


def series(theta, seed, lo=2, hi=48, beta=1.25):
    """A random function of an angle (periodic), built of waves `lo`..`hi` to the turn, mean 0, about unit spread."""
    rng = np.random.default_rng(seed)
    out = np.zeros_like(theta)
    norm = 0.0
    for i in range(lo, hi + 1):
        a = i ** -beta
        out += a * np.cos(i * theta + rng.uniform(0, 2 * math.pi))
        norm += 0.5 * a * a
    return out / math.sqrt(norm)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def sample(field, x0, y0, cell, X, Y):
    """A field (rows along y) read at world places, smoothly (cubic B-spline)."""
    fx = (X - x0) / cell - 0.5
    fy = (Y - y0) / cell - 0.5
    ix = np.floor(fx).astype(np.int32)
    iy = np.floor(fy).astype(np.int32)
    tx = (fx - ix).astype(np.float32)
    ty = (fy - iy).astype(np.float32)

    def w(t):
        return [(1 - t) ** 3 / 6, (3 * t ** 3 - 6 * t ** 2 + 4) / 6, (-3 * t ** 3 + 3 * t ** 2 + 3 * t + 1) / 6, t ** 3 / 6]

    wx, wy = w(tx), w(ty)
    h, wd = field.shape
    out = np.zeros(X.shape, np.float32)
    for j in range(4):
        row = np.clip(iy - 1 + j, 0, h - 1)
        acc = np.zeros(X.shape, np.float32)
        for i in range(4):
            acc += field[row, np.clip(ix - 1 + i, 0, wd - 1)] * wx[i]
        out += acc * wy[j]
    return out


def grids(x0, x1, y0, y1, cell):
    xs = x0 + (np.arange(int(round((x1 - x0) / cell))) + 0.5) * cell
    ys = y0 + (np.arange(int(round((y1 - y0) / cell))) + 0.5) * cell
    X, Y = np.meshgrid(xs.astype(np.float32), ys.astype(np.float32))
    return xs, ys, X, Y


# ---- Node helpers -----------------------------------------------------------------------------------------------

class Nodes:
    """A material's node tree, written tersely: every method returns an output socket."""

    def __init__(self, name):
        self.mat = bpy.data.materials.new(name)
        self.mat.use_nodes = True
        self.mat.displacement_method = 'DISPLACEMENT'
        self.nt = self.mat.node_tree
        self.N, self.L = self.nt.nodes, self.nt.links
        for nd in list(self.N):
            self.N.remove(nd)
        self.out = self.N.new('ShaderNodeOutputMaterial')
        self.bsdf = self.N.new('ShaderNodeBsdfPrincipled')
        self.L.new(self.bsdf.outputs['BSDF'], self.out.inputs['Surface'])
        self.geo = self.N.new('ShaderNodeNewGeometry')
        self.pos = self.geo.outputs['Position']
        sep = self.N.new('ShaderNodeSeparateXYZ')
        self.L.new(self.pos, sep.inputs[0])
        self.x, self.y, self.z = sep.outputs['X'], sep.outputs['Y'], sep.outputs['Z']

    def put(self, sock, val):
        if isinstance(val, (int, float)):
            sock.default_value = float(val)
        elif isinstance(val, (tuple, list)):
            sock.default_value = tuple(val) if len(val) == len(sock.default_value) else tuple(val) + (1.0,)
        else:
            self.L.new(val, sock)

    def math(self, op, a, b=None, c=None, clamp=False):
        m = self.N.new('ShaderNodeMath')
        m.operation = op
        m.use_clamp = clamp
        self.put(m.inputs[0], a)
        if b is not None:
            self.put(m.inputs[1], b)
        if c is not None:
            self.put(m.inputs[2], c)
        return m.outputs[0]

    def add(self, a, b): return self.math('ADD', a, b)
    def sub(self, a, b): return self.math('SUBTRACT', a, b)
    def mul(self, a, b): return self.math('MULTIPLY', a, b)

    def step(self, a, b, x, lo=0.0, hi=1.0):
        """Smoothstep of x between a and b, mapped to lo..hi."""
        r = self.N.new('ShaderNodeMapRange')
        r.interpolation_type = 'SMOOTHSTEP'
        self.put(r.inputs['From Min'], a); self.put(r.inputs['From Max'], b)
        self.put(r.inputs['To Min'], lo); self.put(r.inputs['To Max'], hi)
        self.put(r.inputs['Value'], x)
        return r.outputs[0]

    def lin(self, a, b, x, lo=0.0, hi=1.0):
        r = self.N.new('ShaderNodeMapRange')
        r.interpolation_type = 'LINEAR'
        self.put(r.inputs['From Min'], a); self.put(r.inputs['From Max'], b)
        self.put(r.inputs['To Min'], lo); self.put(r.inputs['To Max'], hi)
        self.put(r.inputs['Value'], x)
        return r.outputs[0]

    def scaled(self, vec, scale, offset=(0.0, 0.0, 0.0)):
        m = self.N.new('ShaderNodeMapping')
        m.vector_type = 'POINT'
        m.inputs['Scale'].default_value = scale if isinstance(scale, tuple) else (scale, scale, scale)
        m.inputs['Location'].default_value = offset
        self.L.new(vec, m.inputs['Vector'])
        return m.outputs[0]

    def noise(self, vec, scale, detail=3.0, rough=0.5, distortion=0.0, lac=2.0, color=False):
        n = self.N.new('ShaderNodeTexNoise')
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        n.inputs['Lacunarity'].default_value = lac
        n.inputs['Distortion'].default_value = distortion
        self.L.new(vec, n.inputs['Vector'])
        return n.outputs['Color' if color else 'Fac']

    def voronoi(self, vec, scale, feature='F1', out='Distance', randomness=1.0):
        v = self.N.new('ShaderNodeTexVoronoi')
        v.feature = feature
        v.inputs['Scale'].default_value = scale
        v.inputs['Randomness'].default_value = randomness
        self.L.new(vec, v.inputs['Vector'])
        return v.outputs[out]

    def ramp(self, fac, stops, interp='LINEAR'):
        r = self.N.new('ShaderNodeValToRGB')
        r.color_ramp.interpolation = interp
        el = r.color_ramp.elements
        while len(el) < len(stops):
            el.new(0.5)
        for e, (p, c) in zip(el, stops):
            e.position = p
            e.color = tuple(c) + (1.0,) if len(c) == 3 else c
        self.put(r.inputs['Fac'], fac)
        return r.outputs['Color']

    def mixc(self, fac, a, b, blend='MIX'):
        m = self.N.new('ShaderNodeMix')
        m.data_type = 'RGBA'
        m.blend_type = blend
        m.clamp_factor = True
        self.put(m.inputs[0], fac)
        self.put(m.inputs[6], a)
        self.put(m.inputs[7], b)
        return m.outputs[2]

    def mixf(self, fac, a, b):
        m = self.N.new('ShaderNodeMix')
        m.data_type = 'FLOAT'
        m.clamp_factor = True
        self.put(m.inputs[0], fac)
        self.put(m.inputs[2], a)
        self.put(m.inputs[3], b)
        return m.outputs[0]

    def image(self, img, vec, interp='Cubic', ext='EXTEND', out='Color'):
        t = self.N.new('ShaderNodeTexImage')
        t.image = img
        t.interpolation = interp
        t.extension = ext
        self.L.new(vec, t.inputs['Vector'])
        return t.outputs[out]

    def rgb(self, col):
        s = self.N.new('ShaderNodeSeparateColor')
        self.put(s.inputs[0], col)
        return s.outputs['Red'], s.outputs['Green'], s.outputs['Blue']

    def vec(self, x, y, z):
        c = self.N.new('ShaderNodeCombineXYZ')
        self.put(c.inputs[0], x); self.put(c.inputs[1], y); self.put(c.inputs[2], z)
        return c.outputs[0]

    def displace(self, height):
        d = self.N.new('ShaderNodeDisplacement')
        d.space = 'OBJECT'
        d.inputs['Midlevel'].default_value = 0.0
        d.inputs['Scale'].default_value = 1.0
        self.put(d.inputs['Height'], height)
        self.L.new(d.outputs[0], self.out.inputs['Displacement'])

    def distance(self):
        d = self.N.new('ShaderNodeVectorMath')
        d.operation = 'DISTANCE'
        self.L.new(self.pos, d.inputs[0])
        d.inputs[1].default_value = EYE
        return d.outputs['Value']

    def facing_up(self):
        s = self.N.new('ShaderNodeSeparateXYZ')
        self.L.new(self.geo.outputs['True Normal'], s.inputs[0])
        return s.outputs['Z']


EYE = (0.0, 0.0, 0.0)             # set by build()


# ---- Scans ------------------------------------------------------------------------------------------------------
# Photogrammetry of real rock, ground and plants (Poly Haven, CC0: tools/scenery/sources/canyon.md), expected in
# %USERPROFILE%/ProjectMinhad/NoorTools/assets/polyhaven/<id>/ as its API serves them (the .blend and its textures).

SCANS = os.environ.get('NOOR_POLYHAVEN', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'polyhaven'))
_scans = {}


def scans(aid, names):
    """Scanned models out of one .blend, by their names in it (loaded once; they are not put into the scene
    themselves: copies of them are, see `stand`)."""
    need = [k for k in names if k not in _scans]
    if need:
        d = os.path.join(SCANS, aid)
        blend = [f for f in os.listdir(d) if f.endswith('.blend')][0]
        with bpy.data.libraries.load(os.path.join(d, blend)) as (src, dst):
            dst.objects = [k for k in src.objects if k in need]
        for ob in dst.objects:
            # (A modifier on a copy would make the renderer keep a mesh of its own for each: the copies must share one.)
            print('scan', ob.name, 'modifiers', [m.type for m in ob.modifiers], flush=True)
            ob.modifiers.clear()
            _scans[ob.name] = ob
    return [_scans[k] for k in names]


def tinted(ob, stops, gain=1.0, wind=0.0, snow=0.0):
    """Gives a scan's materials our light: their own colour times one that differs from copy to copy (`stops`: a
    ramp of colours, read at a number that every copy has its own), times `gain`; and their place in the mask."""
    for mat in ob.data.materials:
        if mat is None or mat.get('noor'):
            continue
        mat['noor'] = 1
        nt = mat.node_tree
        # (The scan's colour picture feeds its shader directly or through a group: wherever it goes, it goes tinted.)
        for tex in [nd for nd in nt.nodes if nd.type == 'TEX_IMAGE' and nd.image and '_diff' in nd.image.name]:
            targets = [l.to_socket for l in tex.outputs['Color'].links]
            if not targets:
                continue
            info = nt.nodes.new('ShaderNodeObjectInfo')
            ramp = nt.nodes.new('ShaderNodeValToRGB')
            el = ramp.color_ramp.elements
            while len(el) < len(stops):
                el.new(0.5)
            top = max(max(c) for _, c in stops) * gain            # (a ramp holds colours up to 1: the rest is a factor)
            for e, (p, c) in zip(el, stops):
                e.position = p
                e.color = (c[0] * gain / top, c[1] * gain / top, c[2] * gain / top, 1.0)
            nt.links.new(info.outputs['Random'], ramp.inputs['Fac'])
            mix = nt.nodes.new('ShaderNodeMix')
            mix.data_type = 'RGBA'
            mix.blend_type = 'MULTIPLY'
            mix.inputs[0].default_value = 1.0
            nt.links.new(tex.outputs['Color'], mix.inputs[6])
            nt.links.new(ramp.outputs['Color'], mix.inputs[7])
            mix2 = nt.nodes.new('ShaderNodeMix')
            mix2.data_type = 'RGBA'
            mix2.blend_type = 'MULTIPLY'
            mix2.inputs[0].default_value = 1.0
            nt.links.new(mix.outputs[2], mix2.inputs[6])
            mix2.inputs[7].default_value = (top, top, top, 1.0)
            for t in targets:
                nt.links.new(mix2.outputs[2], t)
        bake.add_mask(mat, wind=wind, snow=snow)


def stand(name, sources, pick, pos, scale, turn, lean=0.0, seed=1):
    """Copies of scans (they share the scan's mesh) at `pos`, each of the source `pick`, scaled, turned about the
    upright and leant a little."""
    rng = np.random.default_rng(seed)
    col = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(col)
    for i in range(len(pos)):
        ob = sources[int(pick[i])].copy()
        ob.location = (float(pos[i][0]), float(pos[i][1]), float(pos[i][2]))
        s = float(scale[i])
        ob.scale = (s, s, s)
        ob.rotation_euler = (float(rng.normal(0, lean)), float(rng.normal(0, lean)), float(turn[i]))
        col.objects.link(ob)
    return col


def scan_tex(n, aid, kind, vec, colour=False, box=True):
    """One map of a scanned surface ('diff' its colour, 'disp' its relief), laid on by the place of each point."""
    d = os.path.join(SCANS, aid, 'textures')
    name = [f for f in os.listdir(d) if '_%s_' % kind in f][0]
    t = n.N.new('ShaderNodeTexImage')
    t.image = bpy.data.images.load(os.path.join(d, name), check_existing=True)
    if not colour:
        t.image.colorspace_settings.name = 'Non-Color'
    if box:
        t.projection = 'BOX'
        t.projection_blend = 0.3
    n.L.new(vec, t.inputs['Vector'])
    return t.outputs['Color']


def scanned(n, mine, scan, mean, keep=0.5, gain=1.5):
    """A scan's colour married to ours: our colour carrying the scan's light and dark (`mean`: the scan's mean
    brightness), mixed with the scan's own colour times `gain` (`keep`: how much of that)."""
    bw = n.N.new('ShaderNodeRGBToBW')
    n.put(bw.inputs[0], scan)
    ratio = n.math('MINIMUM', n.math('MAXIMUM', n.math('DIVIDE', bw.outputs[0], mean), 0.3), 2.4)
    a = n.mixc(1.0, mine, n.vec(ratio, ratio, ratio), blend='MULTIPLY')
    b = n.mixc(1.0, scan, (gain, gain, gain), blend='MULTIPLY')
    return n.mixc(keep, a, b)


def turned(n, vec, scale, angle=0.0, offset=(0.0, 0.0, 0.0)):
    m = n.N.new('ShaderNodeMapping')
    m.vector_type = 'POINT'
    m.inputs['Scale'].default_value = (scale, scale, scale)
    m.inputs['Rotation'].default_value = (0.0, 0.0, angle)
    m.inputs['Location'].default_value = offset
    n.L.new(vec, m.inputs['Vector'])
    return m.outputs[0]


# ---- The rock ---------------------------------------------------------------------------------------------------

RED_DEEP = (0.33, 0.078, 0.036)
RED = (0.47, 0.135, 0.062)
ORANGE = (0.58, 0.20, 0.092)
PALE = (0.64, 0.31, 0.175)
VARNISH = (0.075, 0.034, 0.026)
SHALE_DARK = (0.22, 0.055, 0.03)
SHALE = (0.34, 0.095, 0.048)
PALE_BED = (0.52, 0.36, 0.27)
SOIL = (0.40, 0.105, 0.044)
SOIL_PALE = (0.53, 0.20, 0.09)
TALUS_A = (0.42, 0.14, 0.07)
TALUS_B = (0.54, 0.235, 0.125)


def rock_material():
    """The buttes' rock. What a point is (talus, a wall, the cap) comes with the solid (its 'zone' map: u = 1 on
    walls, v = the level: 0 skirt, 0.25 bench, 0.5 talus, 0.75 the great wall, 1 cap and top); joints, beds and
    blocks from where it stands. Relief is real (displacement along the surface's normal)."""
    n = Nodes('Rock')
    P = n.pos
    uvn = n.N.new('ShaderNodeUVMap')
    uvn.uv_map = 'zone'
    su = n.N.new('ShaderNodeSeparateXYZ')
    n.L.new(uvn.outputs['UV'], su.inputs[0])
    wall, level = su.outputs['X'], su.outputs['Y']
    great = n.mul(n.step(0.66, 0.72, level), n.step(0.93, 0.86, level))        # the great wall of massive sandstone
    cap = n.step(0.86, 0.93, level)
    low = n.step(0.60, 0.585, level)                                           # bench, talus, skirt
    upper = n.step(0.455, 0.54, level)                                         # the talus near the wall: ledgy
    talus = n.mul(low, n.sub(1.0, wall))
    ledgy = n.mul(wall, n.sub(1.0, n.mul(great, n.step(0.5, 0.9, wall))))      # walls of thin beds: bench, foot, cap
    skirt = n.math('MAXIMUM', n.step(0.20, 0.08, level), n.mul(n.step(0.955, 0.985, n.facing_up()), n.step(0.62, 0.56, level)))

    # Beds are not quite level: their heights wander a few metres over a butte's width.
    warp = n.noise(n.scaled(P, (1 / 420.0, 1 / 420.0, 0.0)), 1.0, 2.0)
    z = n.add(n.z, n.mul(n.sub(warp, 0.5), 10.0))
    bz = n.vec(0.0, 0.0, z)

    # --- Scanned rock (Poly Haven), laid on many times its true size: rock looks alike at every size. A massive
    # rock face for the great wall, bedded sandstone for the walls of thin beds, stony red ground for the talus.
    pw, pw2 = n.scaled(P, 1 / 66.0, (0.13, 0.41, 0.27)), n.scaled(P, 1 / 21.0, (0.37, 0.11, 0.53))
    wf_c = n.mixc(0.5, scan_tex(n, 'rock_face_03', 'diff', pw, True), scan_tex(n, 'rock_face_03', 'diff', pw2, True))
    d_scan_great = n.add(n.mul(n.sub(scan_tex(n, 'rock_face_03', 'disp', pw), 0.575), 9.0),
                         n.mul(n.sub(scan_tex(n, 'rock_face_03', 'disp', pw2), 0.575), 2.6))
    ps = n.scaled(P, 1 / 46.0, (0.2, 0.6, 0.1))
    cs_c = scan_tex(n, 'cliff_side', 'diff', ps, True)
    d_scan_ledgy = n.mul(n.sub(scan_tex(n, 'cliff_side', 'disp', ps), 0.584), 5.0)
    pt, pt2 = n.scaled(P, 1 / 44.0, (0.5, 0.2, 0.8)), n.scaled(P, 1 / 11.0, (0.1, 0.7, 0.3))
    lt_c = n.mixc(0.5, scan_tex(n, 'red_laterite_soil_stones', 'diff', pt, True), scan_tex(n, 'red_laterite_soil_stones', 'diff', pt2, True))
    d_scan_talus = n.add(n.mul(n.sub(scan_tex(n, 'red_laterite_soil_stones', 'disp', pt), 0.29), 4.5),
                         n.mul(n.sub(scan_tex(n, 'red_laterite_soil_stones', 'disp', pt2), 0.29), 1.2))

    # --- The great wall: split by upright joints into buttresses, slabs and columns; deep clefts between.
    def joints(sx, sz, off, cleft, proud, width):
        pj = n.scaled(P, (1 / sx, 1 / sx, 1 / sz), off)
        e = n.voronoi(pj, 1.0, 'DISTANCE_TO_EDGE')
        c = n.rgb(n.voronoi(pj, 1.0, 'F1', 'Color'))[0]
        return n.add(n.mul(n.step(0.0, width, e, -1.0, 0.0), cleft), n.mul(n.sub(c, 0.5), proud)), e

    j1, e1 = joints(44.0, 520.0, (0.0, 0.0, 0.0), 11.0, 9.0, 0.09)
    j2, e2 = joints(14.0, 150.0, (5.1, 2.3, 0.0), 3.6, 4.5, 0.10)
    j3, e3 = joints(4.4, 38.0, (7.3, 1.1, 0.0), 1.0, 1.5, 0.12)
    part = n.noise(n.scaled(bz, 1 / 30.0), 1.0, 1.0)
    setback = n.step(0.50, 0.55, part, 0.0, -2.4)                              # a parting: the wall steps back above it
    lines = n.mul(n.sub(n.noise(n.scaled(bz, 1 / 2.4, (3.0, 0.0, 0.0)), 1.0, 2.0, 0.7), 0.5), 0.8)
    d_great = n.add(n.add(j1, n.mul(j2, 0.6)), n.add(setback, d_scan_great))

    # --- Walls of thin beds (the bench round the foot, the ledges under the wall, the cap): hard beds stand out.
    beds = n.noise(n.scaled(bz, 1 / 3.2, (9.0, 0.0, 0.0)), 1.0, 2.0, 0.6)
    hard = n.step(0.45, 0.55, beds)
    pv = n.scaled(P, (1 / 3.4, 1 / 3.4, 1 / 9.0), (2.0, 5.0, 0.0))
    ev = n.voronoi(pv, 1.0, 'DISTANCE_TO_EDGE')
    d_ledgy = n.add(n.mul(n.sub(hard, 0.5), 1.1), d_scan_ledgy)

    # --- The talus: rubble with blocks in it, a few of them as big as houses; hard beds crop out here and there.
    pb = n.scaled(P, 1 / 7.5, (1.0, 2.0, 3.0))
    fb = n.voronoi(pb, 1.0, 'F1')
    cb = n.rgb(n.voronoi(pb, 1.0, 'F1', 'Color'))
    big = n.mul(n.step(0.42, 0.12, fb), n.step(0.55, 0.95, cb[1]))            # a block in some of the cells
    pb2 = n.scaled(P, 1 / 2.3, (4.0, 1.0, 7.0))
    fb2 = n.voronoi(pb2, 1.0, 'F1')
    cb2 = n.rgb(n.voronoi(pb2, 1.0, 'F1', 'Color'))
    small = n.mul(n.step(0.45, 0.15, fb2), n.step(0.35, 0.9, cb2[1]))
    rubble = n.noise(n.scaled(P, 1 / 55.0, (3.0, 9.0, 0.0)), 1.0, 2.0)        # where the slope is blocky, where fine
    blocky = n.step(0.38, 0.62, rubble)
    drape = n.noise(n.scaled(P, (1 / 90.0, 1 / 90.0, 1 / 260.0), (8.0, 1.0, 0.0)), 1.0, 3.0, 0.6)      # rubble let down over the ledges
    crop = n.mul(n.step(0.47, 0.55, n.noise(n.scaled(bz, 1 / 13.0, (6.0, 0.0, 0.0)), 1.0, 0.0)),
                 n.math('MAXIMUM', n.step(0.42, 0.56, drape), n.mul(upper, n.step(0.30, 0.50, drape))))
    d_talus = n.add(d_scan_talus,
                    n.add(n.mul(crop, 3.0), n.mul(n.sub(n.noise(n.scaled(P, 1 / 24.0), 1.0, 3.0), 0.5), 3.0)))

    # --- Everywhere: rock is rough at every size.
    rough = n.mul(n.sub(n.noise(n.scaled(P, 1 / 9.0), 1.0, 4.0, 0.55), 0.5), 0.6)
    disp = n.add(n.mul(d_great, n.mul(great, wall)), n.add(n.mul(d_ledgy, ledgy), n.mul(d_talus, n.mul(talus, n.sub(1.0, skirt)))))
    disp = n.add(disp, n.mul(n.mul(n.sub(hard, 0.5), 2.4), n.mul(n.mul(talus, upper), n.step(0.35, 0.6, drape))))
    disp = n.add(disp, rough)
    # (Nothing is pushed out where the solid meets the floor: its rim lies under the sand.)
    disp = n.mul(disp, n.step(1.0, 9.0, n.z))
    n.displace(disp)

    # --- Colour.
    tone = n.noise(n.scaled(P, (1 / 70.0, 1 / 70.0, 1 / 22.0)), 1.0, 4.0, 0.6)
    band = n.noise(n.scaled(bz, 1 / 11.0, (1.0, 0.0, 0.0)), 1.0, 3.0, 0.65)
    c_great = n.ramp(n.add(n.mul(tone, 0.62), n.mul(band, 0.45)), [(0.30, RED_DEEP), (0.47, RED), (0.66, ORANGE), (0.90, PALE)])
    # Desert varnish: dark streaks let down the wall from its ledges and its top.
    streak = n.noise(n.scaled(P, (1 / 3.0, 1 / 3.0, 1 / 110.0)), 1.0, 3.0, 0.6, distortion=0.5)
    wide = n.noise(n.scaled(P, (1 / 45.0, 1 / 45.0, 1 / 200.0), (4.0, 2.0, 0.0)), 1.0, 2.0)
    under = n.step(CLIFF_TOP - 70.0, CLIFF_TOP, z, 0.0, 0.12)                # most under the cap
    varn = n.step(0.49, 0.70, n.add(n.add(n.mul(streak, 0.55), n.mul(wide, 0.55)), under))
    c_great = n.mixc(n.mul(varn, 0.78), c_great, VARNISH)
    # Scars where slabs have fallen lately: pale, unstained.
    scar = n.step(0.66, 0.74, n.noise(n.scaled(P, (1 / 28.0, 1 / 28.0, 1 / 70.0), (2.0, 7.0, 0.0)), 1.0, 2.0, 0.5))
    c_great = n.mixc(n.mul(scar, 0.7), c_great, PALE)
    # The clefts between the slabs are dark with it too.
    c_great = n.mixc(n.step(0.10, 0.0, e2, 0.0, 0.5), c_great, VARNISH)
    c_great = scanned(n, c_great, wf_c, 0.155, 0.3, 1.25)
    c_ledgy = n.ramp(n.add(n.mul(beds, 0.8), n.mul(tone, 0.2)), [(0.28, SHALE_DARK), (0.45, SHALE), (0.57, RED), (0.66, PALE_BED), (0.72, SHALE), (0.9, RED_DEEP)])
    c_ledgy = scanned(n, c_ledgy, cs_c, 0.107, 0.5, 1.5)
    c_talus = n.ramp(n.add(n.mul(rubble, 0.6), n.mul(tone, 0.4)), [(0.3, TALUS_A), (0.7, TALUS_B)])
    c_talus = scanned(n, c_talus, lt_c, 0.065, 0.45, 2.0)
    # Blocks: fresh ones the colour of the wall they fell from, old ones dark.
    blockc = n.ramp(cb[0], [(0.2, VARNISH), (0.45, RED), (0.8, ORANGE)])
    c_talus = n.mixc(n.mul(crop, 0.85), c_talus, n.ramp(beds, [(0.3, SHALE_DARK), (0.6, SHALE), (0.75, RED_DEEP)]))
    # Towards the wall the slope is of beds more than of rubble: darker, banded.
    c_talus = n.mixc(n.mul(n.mul(upper, n.step(0.35, 0.6, drape)), 0.75), c_talus, c_ledgy)
    # Brush on the lower slopes and the skirt, too far to be told apart: olive specks.
    sp = n.step(0.20, 0.06, n.voronoi(n.scaled(P, 1 / 6.5, (0.5, 0.25, 0.1)), 1.0, 'F1'))
    c_talus = n.mixc(n.mul(n.mul(sp, 0.6), n.step(CLIFF_BASE - 60.0, CLIFF_BASE - 115.0, n.z)), c_talus, (0.12, 0.115, 0.07))
    c_skirt = n.ramp(tone, [(0.25, SOIL), (0.75, TALUS_A)])
    c_skirt = n.mixc(n.mul(sp, 0.6), c_skirt, (0.12, 0.115, 0.07))
    c_skirt = scanned(n, c_skirt, lt_c, 0.065, 0.45, 2.0)
    c = n.mixc(skirt, c_talus, c_skirt)
    c = n.mixc(ledgy, c, c_ledgy)
    c = n.mixc(n.mul(great, wall), c, c_great)
    c_top = n.ramp(tone, [(0.3, (0.25, 0.075, 0.04)), (0.7, SOIL)])
    c = n.mixc(n.mul(cap, n.sub(1.0, wall)), c, c_top)
    # Ledges hold sand and dust: paler, redder, flat.
    dust = n.mul(n.step(0.75, 0.95, n.facing_up()), wall)
    c = n.mixc(n.mul(dust, 0.7), c, SOIL_PALE)
    # A grain of light and dark at the size of a block.
    n.put(n.bsdf.inputs['Base Color'], c)
    n.bsdf.inputs['Roughness'].default_value = 0.92
    n.bsdf.inputs['Specular IOR Level'].default_value = 0.22
    bake.add_mask(n.mat, wind=0.0, snow=n.step(0.62, 0.90, n.facing_up()))
    return n.mat


def mesh_object(name, co, faces, smooth_shading=True):
    me = bpy.data.meshes.new(name)
    me.vertices.add(len(co))
    me.vertices.foreach_set('co', np.asarray(co, np.float32).ravel())
    k = faces.shape[1]
    me.loops.add(faces.size)
    me.loops.foreach_set('vertex_index', np.asarray(faces, np.int32).ravel())
    me.polygons.add(len(faces))
    me.polygons.foreach_set('loop_start', np.arange(0, faces.size, k, dtype=np.int32))
    me.polygons.foreach_set('loop_total', np.full(len(faces), k, np.int32))
    me.polygons.foreach_set('use_smooth', np.full(len(faces), smooth_shading, bool))
    me.update(calc_edges=True)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


# ---- The floor's material ---------------------------------------------------------------------------------------

def floor_material(n, height, mask, far):
    """Soil, wash beds and bare rock, by the picture `mask` (R rock, G wash bed, B brush cover). Its surface is
    scanned ground (Poly Haven): red earth at its true size near us and many times enlarged far off (earth looks
    alike at every size), stony laterite in patches, loose red sand in the washes and the drifts, bedded sandstone
    where rock lies bare."""
    P = n.pos
    dist = n.distance()
    rockm, bedm, brush = n.rgb(mask)
    soily = n.step(0.55, 0.25, rockm)
    near = n.step(16.0, 70.0, dist, 1.0, 0.0)
    # The scans: a tile laid by the ground's own metres, and again larger and turned, mixed by patches so that no
    # tile is seen twice the same; and once more very large, for the distance.
    patch = n.step(0.42, 0.58, n.noise(n.scaled(P, 1 / 4.3), 1.0, 3.0))
    p1, p2, p3 = turned(n, P, 1 / 2.4), turned(n, P, 1 / 6.1, 0.66, (0.3, 0.7, 0.0)), turned(n, P, 1 / 41.0, 1.9, (0.11, 0.53, 0.0))

    def earth(kind, colour):
        a = scan_tex(n, 'red_dirt_mud_01', kind, p1, colour, False)
        b = scan_tex(n, 'red_dirt_mud_01', kind, p2, colour, False)
        c = scan_tex(n, 'red_dirt_mud_01', kind, p3, colour, False)
        if colour:
            return n.mixc(near, c, n.mixc(patch, a, b))
        return n.mixf(patch, a, b), c

    stony = n.mul(n.step(0.50, 0.62, n.noise(n.scaled(P, 1 / 13.0, (7.0, 2.0, 0.0)), 1.0, 3.0, 0.6)), n.step(60.0, 160.0, dist, 1.0, 0.0))
    p4 = turned(n, P, 1 / 3.1, 1.2)
    lat_c = scan_tex(n, 'red_laterite_soil_stones', 'diff', p4, True, False)
    lat_d = scan_tex(n, 'red_laterite_soil_stones', 'disp', p4, False, False)
    p5 = turned(n, P, 1 / 2.2, 0.4)
    sand_c = scan_tex(n, 'red_sand', 'diff', p5, True, False)
    sand_d = scan_tex(n, 'red_sand', 'disp', p5, False, False)
    p6 = n.scaled(P, 1 / 12.0, (0.2, 0.4, 0.6))
    slab_c = scan_tex(n, 'cliff_side', 'diff', p6, True)
    slab_d = scan_tex(n, 'cliff_side', 'disp', p6)

    # Wind ripples in loose sand near us: in the wash beds and the drifts.
    rip = n.N.new('ShaderNodeTexWave')
    rip.wave_type = 'BANDS'; rip.bands_direction = 'X'; rip.wave_profile = 'SIN'
    rip.inputs['Scale'].default_value = 0.314 / 0.10
    rip.inputs['Distortion'].default_value = 5.5
    rip.inputs['Detail'].default_value = 2.5
    rip.inputs['Detail Scale'].default_value = 0.42
    rip.inputs['Detail Roughness'].default_value = 0.55
    n.L.new(turned(n, P, 1.0, math.radians(38.0)), rip.inputs['Vector'])
    drift = n.step(0.42, 0.62, n.noise(n.scaled(P, 1 / 9.0, (5.0, 5.0, 0.0)), 1.0, 2.0))
    loose = n.mul(soily, n.math('MAXIMUM', bedm, drift))
    ripple = n.mul(n.math('POWER', rip.outputs['Fac'], 0.7), n.step(12.0, 60.0, dist, 1.0, 0.0))
    d_sand = n.add(n.mul(ripple, 0.008), n.mul(n.sub(sand_d, 0.483), 0.04))
    e_near, e_far = earth('disp', False)
    d_soil = n.add(n.mul(n.mul(n.sub(e_near, 0.265), 0.09), near),
                   n.mul(n.mul(n.sub(e_far, 0.265), 0.75), n.mul(n.sub(1.0, near), n.step(2500.0, 900.0, dist))))
    d_soil = n.mixf(stony, d_soil, n.mul(n.sub(lat_d, 0.29), 0.13))
    d_soil = n.mixf(loose, d_soil, d_sand)
    d_rock = n.add(n.mul(n.sub(slab_d, 0.584), 0.7), n.mul(n.sub(n.noise(n.scaled(P, 1 / 3.5), 1.0, 4.0, 0.6), 0.5), 0.3))
    n.displace(n.mixf(rockm, d_soil, d_rock))

    # Colour: ours for the lie of the land (darker hollows, paler washes), the scans' for everything smaller.
    tone = n.noise(n.scaled(P, 1 / 180.0), 1.0, 5.0, 0.6)
    tone2 = n.noise(n.scaled(P, 1 / 17.0), 1.0, 4.0, 0.6)
    c_soil = n.ramp(n.add(n.mul(tone, 0.65), n.mul(tone2, 0.4)), [(0.28, (0.27, 0.066, 0.03)), (0.5, SOIL), (0.72, (0.50, 0.165, 0.07)), (0.9, (0.56, 0.24, 0.115))])
    # Dry grass between the bushes where brush stands thick: a straw-coloured cast.
    straw = n.mul(n.step(0.45, 0.8, brush), n.step(0.40, 0.65, n.noise(n.scaled(P, 1 / 23.0, (2.0, 8.0, 0.0)), 1.0, 3.0, 0.6)))
    c_soil = n.mixc(n.mul(straw, 0.45), c_soil, (0.42, 0.30, 0.13))
    c_earth = scanned(n, c_soil, earth('diff', True), 0.11, 0.3, 1.5)
    c_earth = n.mixc(stony, c_earth, scanned(n, c_soil, lat_c, 0.065, 0.5, 2.0))
    c_bed = scanned(n, n.ramp(tone2, [(0.3, (0.50, 0.19, 0.085)), (0.8, SOIL_PALE)]), sand_c, 0.123, 0.4, 1.6)
    c_earth = n.mixc(n.mul(drift, 0.6), c_earth, c_bed)
    c_rock = scanned(n, n.ramp(tone2, [(0.25, RED_DEEP), (0.5, RED), (0.75, ORANGE), (0.95, PALE)]), slab_c, 0.107, 0.5, 1.6)
    crust = n.step(0.52, 0.72, n.noise(n.scaled(P, 1 / 6.0, (11.0, 3.0, 0.0)), 1.0, 5.0, 0.65))
    c_rock = n.mixc(n.mul(crust, 0.5), c_rock, VARNISH)
    c = n.mixc(bedm, c_earth, c_bed)
    c = n.mixc(rockm, c, c_rock)
    # Brush too far to be told apart: specks of grey-green and of dry grass, as thick as the brush stands there.
    v1 = n.voronoi(n.scaled(P, (1 / 4.2, 1 / 4.2, 0.0)), 1.0, 'F1')
    vc = n.rgb(n.voronoi(n.scaled(P, (1 / 4.2, 1 / 4.2, 0.0)), 1.0, 'F1', 'Color'))
    speck = n.mul(n.step(0.30, 0.12, v1), n.step(0.9, 0.35, vc[1]))
    speck = n.mul(n.mul(speck, n.step(650.0, 950.0, dist)), brush)
    c = n.mixc(n.mul(speck, 0.8), c, n.ramp(vc[0], [(0.2, (0.075, 0.085, 0.045)), (0.6, (0.17, 0.18, 0.095)), (0.9, (0.33, 0.27, 0.12))]))
    c = n.mixc(n.mul(n.mul(n.step(500.0, 2200.0, dist), n.step(0.25, 0.8, brush)), n.mul(soily, 0.42)), c, (0.25, 0.215, 0.105))
    n.put(n.bsdf.inputs['Base Color'], c)
    n.bsdf.inputs['Roughness'].default_value = 0.9
    n.bsdf.inputs['Specular IOR Level'].default_value = 0.25
    bake.add_mask(n.mat, wind=0.0, snow=n.step(0.70, 0.92, n.facing_up()))
    return n.mat


# ---- Plants and stones ------------------------------------------------------------------------------------------

def icosphere(level):
    """A sphere of triangles: points (unit), faces."""
    t = (1.0 + math.sqrt(5.0)) / 2.0
    v = np.array([[-1, t, 0], [1, t, 0], [-1, -t, 0], [1, -t, 0], [0, -1, t], [0, 1, t], [0, -1, -t], [0, 1, -t],
                  [t, 0, -1], [t, 0, 1], [-t, 0, -1], [-t, 0, 1]], np.float64)
    v /= np.linalg.norm(v, axis=1)[:, None]
    f = np.array([[0, 11, 5], [0, 5, 1], [0, 1, 7], [0, 7, 10], [0, 10, 11], [1, 5, 9], [5, 11, 4], [11, 10, 2], [10, 7, 6],
                  [7, 1, 8], [3, 9, 4], [3, 4, 2], [3, 2, 6], [3, 6, 8], [3, 8, 9], [4, 9, 5], [2, 4, 11], [6, 2, 10],
                  [8, 6, 7], [9, 8, 1]], np.int64)
    for _ in range(level):
        cache = {}
        verts = list(v)

        def mid(a, b):
            key = (min(a, b), max(a, b))
            if key not in cache:
                m = verts[a] + verts[b]
                verts.append(m / np.linalg.norm(m))
                cache[key] = len(verts) - 1
            return cache[key]

        nf = []
        for a, b, c in f:
            ab, bc, ca = mid(a, b), mid(b, c), mid(c, a)
            nf += [[a, ab, ca], [b, bc, ab], [c, ca, bc], [ab, bc, ca]]
        v, f = np.array(verts), np.array(nf, np.int64)
    return v.astype(np.float32), f.astype(np.int32)


def lumpy(v, seed, amount):
    """Pushes a sphere's points in and out, smoothly, so it is a lump and not a ball."""
    rng = np.random.default_rng(seed)
    out = np.ones(len(v), np.float32)
    for k in range(7):
        d = rng.standard_normal(3)
        d /= np.linalg.norm(d)
        out += amount * rng.uniform(0.4, 1.0) * np.sin(v @ d * rng.uniform(1.5, 5.0) + rng.uniform(0, 6.28))
    return v * out[:, None]


def scatter(name, templates, pos, size, squash, turn, pick, colour, material):
    """Many copies of a few shapes as one mesh: each at `pos`, scaled by `size` (and by `squash` upward), turned by
    `turn` about the upright, of the shape `pick`; `colour` (rgb each) goes into the points' colour."""
    cos, faces, cols = [], [], []
    base = 0
    for t, (tv, tf) in enumerate(templates):
        sel = np.where(pick == t)[0]
        if not len(sel):
            continue
        c, s = np.cos(turn[sel])[:, None], np.sin(turn[sel])[:, None]
        x = tv[None, :, 0] * c - tv[None, :, 1] * s
        y = tv[None, :, 0] * s + tv[None, :, 1] * c
        z = np.broadcast_to(tv[None, :, 2], x.shape) * squash[sel][:, None]
        p = np.stack([x, y, z], -1) * size[sel][:, None, None] + pos[sel][:, None, :]
        cos.append(p.reshape(-1, 3))
        nv = len(tv)
        faces.append((tf[None, :, :] + (base + np.arange(len(sel)) * nv)[:, None, None]).reshape(-1, 3))
        cols.append(np.repeat(colour[sel], nv, axis=0))
        base += len(sel) * nv
    co = np.concatenate(cos).astype(np.float32)
    fa = np.concatenate(faces).astype(np.int32)
    ob = mesh_object(name, co, fa)
    col = np.concatenate(cols).astype(np.float32)
    attr = ob.data.color_attributes.new('Col', 'FLOAT_COLOR', 'POINT')
    attr.data.foreach_set('color', np.concatenate([col, np.ones((len(col), 1), np.float32)], 1).ravel())
    ob.data.materials.append(material)
    return ob, co


def plant_material(name, wind, snow, rough=0.75, bump=0.5, scale=14.0, dark=0.55):
    n = Nodes(name)
    n.mat.displacement_method = 'BUMP'
    a = n.N.new('ShaderNodeVertexColor')
    a.layer_name = 'Col'
    shade = n.noise(n.scaled(n.pos, scale), 1.0, 3.0, 0.6)
    c = n.mixc(1.0, a.outputs['Color'], n.ramp(shade, [(0.25, (dark, dark, dark)), (0.8, (1.1, 1.1, 1.1))]), blend='MULTIPLY')
    n.put(n.bsdf.inputs['Base Color'], c)
    n.bsdf.inputs['Roughness'].default_value = rough
    n.bsdf.inputs['Specular IOR Level'].default_value = 0.2
    if bump > 0.0:
        b = n.N.new('ShaderNodeBump')
        b.inputs['Strength'].default_value = bump
        b.inputs['Distance'].default_value = 0.08
        n.L.new(shade, b.inputs['Height'])
        n.L.new(b.outputs['Normal'], n.bsdf.inputs['Normal'])
    bake.add_mask(n.mat, wind=wind, snow=snow)
    return n.mat


def brush_shapes():
    """Shapes of brush: low rounded bushes made of a few lumps; the nearer ones of more."""
    rng = np.random.default_rng(77)
    sv, sf = icosphere(1)
    out = []
    for k, lumps in enumerate((1, 1, 3, 3, 7, 7, 12)):
        vs, fs = [], []
        for j in range(lumps):
            v = lumpy(sv, 100 + k * 20 + j, 0.16)
            if lumps > 1:
                a = rng.uniform(0, 6.28)
                r = rng.uniform(0.2, 0.62)
                v = v * rng.uniform(0.42, 0.62) + np.array([r * math.cos(a), r * math.sin(a), rng.uniform(0.15, 0.55)], np.float32)
            else:
                v = v * np.array([1.0, 1.0, 0.8], np.float32) + np.array([0, 0, 0.45], np.float32)
            fs.append(sf + len(vs) * len(sv))
            vs.append(v)
        out.append((np.concatenate(vs).astype(np.float32), np.concatenate(fs).astype(np.int32)))
    return out


ROCK_TINT = [(0.0, (1.9, 1.05, 0.78)), (0.5, (2.4, 1.3, 0.95)), (1.0, (2.9, 1.75, 1.3))]     # the scans' brown to our red rock


def plants(ground, eye, taluses):
    """What stands on the land, all of it scanned from life (Poly Haven) but the farthest brush: bushes over the floor
    (thick along the washes, none on bare rock), dark evergreen shrubs for junipers, dry grass, stones and fallen
    blocks round us, outcrops of rock along the floor's ledges, blocks on the buttes' talus. `ground(x, y)` gives
    the height, the rock, the wash bed and the brush cover at places; `taluses` the buttes' slopes (points).
    Returns (name, the highest points) of what could stand in the sky."""
    rng = np.random.default_rng(12)
    tall = []

    # ---- Brush: within the picture's wedge (and a little beside it), out to where a bush is a speck.
    n = 150000
    r = np.sqrt(rng.uniform(6.0 ** 2, 1000.0 ** 2, n))
    a = np.radians(rng.uniform(-29.0, 29.0, n))
    x, y = r * np.sin(a), r * np.cos(a)
    h, rock, bed, cover = ground(x, y)
    keep = rng.uniform(0, 1, n) < cover * (1 - smooth(0.25, 0.6, rock)) * (1 - smooth(0.3, 0.7, bed)) * 0.62
    x, y, h, r = x[keep], y[keep], h[keep], r[keep]
    n = len(x)
    size = np.exp(rng.normal(math.log(0.55), 0.38, n)).astype(np.float32)
    turn = rng.uniform(0, 6.28, n).astype(np.float32)
    nearb = r < 400.0
    # Near and in the middle distance: scanned bushes (a wild rooibos: a grey-green twiggy desert shrub).
    names = ['wild_rooibos_bush_a_LOD3', 'wild_rooibos_bush_b_LOD3', 'wild_rooibos_bush_c_LOD3']
    src = scans('wild_rooibos_bush', names)
    for ob in src:
        tinted(ob, [(0.0, (0.86, 0.86, 0.78)), (0.45, (1.08, 1.05, 1.0)), (0.8, (1.3, 1.08, 0.8)), (1.0, (1.05, 0.8, 0.62))], 1.45, wind=0.8, snow=0.4)
    half = np.array([0.36, 0.26, 0.23])
    k = int(nearb.sum())
    pick = rng.integers(0, 3, k)
    pos = np.stack([x[nearb], y[nearb], h[nearb] - 0.03], -1)
    tall_of = np.array([0.54, 0.50, 0.45])                    # the scans' own heights: no bush taller than a man's chest
    sc = np.minimum(size[nearb] * 1.35 / half[pick], 1.25 / tall_of[pick])
    stand('Brush', src, pick, pos, sc, turn[nearb], 0.06, 1)
    # Farther off a bush is a few points of the picture: a lump of its colour stands for it.
    far = ~nearb
    kind = rng.uniform(0, 1, n)
    grey, olive = np.array([0.150, 0.150, 0.088]), np.array([0.075, 0.085, 0.034])
    gold, dead = np.array([0.30, 0.25, 0.09]), np.array([0.20, 0.13, 0.09])
    col = np.where(kind[:, None] < 0.5, grey, np.where(kind[:, None] < 0.84, olive, np.where(kind[:, None] < 0.93, gold, dead)))
    col = col * rng.uniform(0.7, 1.25, (n, 1))
    posf = np.stack([x, y, h - 0.06 * size], -1).astype(np.float32)
    scatter('BrushFar', brush_shapes(), posf[far], size[far], rng.uniform(0.62, 1.0, n).astype(np.float32)[far], turn[far],
            np.where(r[far] > 600.0, rng.integers(0, 2, int(far.sum())), rng.integers(2, 4, int(far.sum()))),
            col[far].astype(np.float32), plant_material('BrushFar', 0.75, 0.45))
    print('brush', n, 'scanned', k, flush=True)

    # ---- Junipers: few, where there is rock or a wash for their roots (scans of a dark evergreen shrub).
    n = 9000
    r = np.sqrt(rng.uniform(35.0 ** 2, 1900.0 ** 2, n))
    a = np.radians(rng.uniform(-29.0, 29.0, n))
    x, y = r * np.sin(a), r * np.cos(a)
    h, rock, bed, cover = ground(x, y)
    like = 0.012 + 0.10 * smooth(0.15, 0.6, rock) * (1 - smooth(0.6, 0.95, rock)) + 0.05 * smooth(0.02, 0.2, bed) * (1 - smooth(0.2, 0.5, bed))
    keep = rng.uniform(0, 1, n) < like
    x, y, h = x[keep], y[keep], h[keep]
    n = len(x)
    height = rng.uniform(1.8, 4.6, n)
    src = scans('searsia_burchellii', ['searsia_burchellii_small_LOD2', 'searsia_burchellii_large_LOD2'])
    for ob in src:
        tinted(ob, [(0.0, (0.55, 0.72, 0.5)), (1.0, (0.85, 0.95, 0.7))], 1.0, wind=0.45, snow=0.6)
    pick = (height > 3.0).astype(int)
    pos = np.stack([x, y, h - 0.08], -1)
    stand('Junipers', src, pick, pos, height / np.array([1.92, 3.22])[pick], rng.uniform(0, 6.28, n), 0.04, 2)
    tall.append(('Junipers', np.stack([x, y, h + height], -1).astype(np.float32)))
    print('junipers', n, flush=True)

    # ---- Dry grass: tussocks on the soil near us (scanned grass, sun-bleached).
    n = 9000
    r = np.sqrt(rng.uniform(1.5 ** 2, 110.0 ** 2, n))
    a = np.radians(rng.uniform(-36.0, 36.0, n))
    x, y = r * np.sin(a), r * np.cos(a)
    h, rock, bed, cover = ground(x, y)
    keep = rng.uniform(0, 1, n) < (0.25 + 0.75 * cover) * (1 - smooth(0.2, 0.5, rock)) * (1 - 0.7 * bed)
    x, y, h = x[keep], y[keep], h[keep]
    n = len(x)
    src = scans('grass_medium_01', ['grass_medium_01_tall_a_LOD1', 'grass_medium_01_tall_b_LOD1', 'grass_medium_01_tall_c_LOD1', 'grass_medium_01_mid_a_LOD1'])
    for ob in src:
        tinted(ob, [(0.0, (2.6, 1.7, 0.75)), (0.7, (3.4, 2.0, 0.75)), (1.0, (2.0, 1.6, 0.7))], 1.5, wind=1.0, snow=0.3)
    stand('Grass', src, rng.integers(0, 4, n), np.stack([x, y, h - 0.01], -1), rng.uniform(1.4, 3.0, n), rng.uniform(0, 6.28, n), 0.08, 3)
    print('grass', n, flush=True)

    # ---- Stones round us, a few fallen blocks, and a dead trunk (scanned boulders and wood).
    small = scans('namaqualand_boulders_01', ['namaqualand_boulders_01_a_LOD2', 'namaqualand_boulders_01_b_LOD2'])
    for ob in small:
        tinted(ob, [(0.0, (1.1, 0.5, 0.36)), (0.6, (1.5, 0.72, 0.5)), (1.0, (1.9, 1.05, 0.8))], 1.0, snow=0.7)
    n = 1500
    r = np.sqrt(rng.uniform(1.3 ** 2, 70.0 ** 2, n))
    a = np.radians(rng.uniform(-40.0, 40.0, n))
    x, y = r * np.sin(a), r * np.cos(a)
    h, rock, bed, cover = ground(x, y)
    sc = np.minimum(np.exp(rng.normal(math.log(0.22), 0.7, n)), 1.3)
    sc = np.where((r < 4.0) & (sc > 0.6), 0.3, sc)
    stand('Stones', small, rng.integers(0, 2, n), np.stack([x, y, h - 0.03 * sc], -1), sc, rng.uniform(0, 6.28, n), 0.25, 4)
    big = scans('namaqualand_boulder_02', ['namaqualand_boulder_02_LOD2', 'namaqualand_boulder_02_LOD3'])
    for ob in big:
        tinted(ob, ROCK_TINT, 1.0, snow=0.7)
    bx = np.array([-9.0, 7.5, -17.0, 16.0, 3.0, -30.0, 26.0, -6.0])
    by = np.array([13.0, 19.0, 30.0, 38.0, 52.0, 60.0, 70.0, 88.0])
    bs = rng.uniform(0.45, 1.0, len(bx))
    bh = ground(bx, by)[0]
    stand('Blocks', big, np.zeros(len(bx), int), np.stack([bx, by, bh - 0.25 * 0.88 * bs], -1), bs, rng.uniform(0, 6.28, len(bx)), 0.12, 5)
    trunk = scans('dead_tree_trunk_02', ['dead_tree_trunk_02_LOD1'])
    tinted(trunk[0], [(0.0, (1.0, 1.0, 1.0)), (1.0, (1.0, 1.0, 1.0))], 1.0, snow=0.6)
    th = float(ground(np.array([4.2]), np.array([7.6]))[0][0])
    stand('Trunk', trunk, [0], [(4.2, 7.6, th + 0.28)], [0.85], [2.3], 0.0, 6)

    # ---- Outcrops: where the floor steps up in a ledge, rock breaks out of it (scanned blocks, enlarged).
    n = 60000
    r = np.sqrt(rng.uniform(110.0 ** 2, 1300.0 ** 2, n))
    a = np.radians(rng.uniform(-29.0, 29.0, n))
    x, y = r * np.sin(a), r * np.cos(a)
    h, rock, bed, cover = ground(x, y)
    keep = rng.uniform(0, 1, n) < smooth(0.45, 0.9, rock) * 0.5
    x, y, h, r = x[keep][:420], y[keep][:420], h[keep][:420], r[keep][:420]
    n = len(x)
    sc = rng.uniform(1.3, 4.2, n) * (0.8 + r / 1300.0)
    stand('Outcrops', big, np.ones(n, int), np.stack([x, y, h - 0.4 * 0.88 * sc], -1), sc, rng.uniform(0, 6.28, n), 0.1, 7)
    print('outcrops', n, flush=True)

    # ---- The buttes' talus: blocks fallen from the wall, most of them near its foot, some as big as houses.
    for name, pts, dist in taluses:
        m = int(np.clip(260.0 * (2600.0 / dist) ** 0.7, 40, 420))
        idx = rng.integers(0, len(pts), m)
        p = pts[idx]
        sc = np.exp(rng.normal(math.log(4.0), 0.55, m))
        sc = np.clip(sc, 1.5, 13.0)
        stand('Talus' + name, big, np.ones(m, int), np.stack([p[:, 0], p[:, 1], p[:, 2] - 0.35 * 0.88 * sc], -1), sc, rng.uniform(0, 6.28, m), 0.2, 8)
    return tall


def blur(a, radius):
    """A field smoothed over `radius` cells (Gaussian; the field is taken to wrap round)."""
    ky = np.fft.fftfreq(a.shape[0])[:, None]
    kx = np.fft.rfftfreq(a.shape[1])[None, :]
    return np.fft.irfft2(np.fft.rfft2(a) * np.exp(-2 * (math.pi * radius) ** 2 * (kx * kx + ky * ky)), s=a.shape).astype(np.float32)


def land_maps(X, Y, h, cell, veg, patch):
    """What the real ground is made of, read from its own shape: bare rock where it steps or steepens (ledges, the
    rims of washes), a sandy bed along the lines water runs in (the hollows of the model), brush thick near those
    lines and thin on the swells. Returns rock, bed, cover (0..1 each)."""
    gy, gx = np.gradient(h, cell)
    slope = np.hypot(gx, gy)
    rock = smooth(0.16, 0.42, blur(slope, 3.0 / cell)) * np.clip(0.75 + 0.5 * patch, 0.0, 1.0)
    hollow = blur(h, 45.0 / cell) - blur(h, 9.0 / cell)              # positive where the ground lies in a hollow
    bed = smooth(0.35, 1.1, hollow) * smooth(0.22, 0.08, slope)
    near_bed = smooth(0.0, 0.5, blur(bed, 14.0 / cell))
    cover = np.clip(0.40 + 0.22 * veg + 0.40 * near_bed, 0.05, 1.0) * smooth(0.6, 0.25, slope)
    return rock.astype(np.float32), bed.astype(np.float32), cover.astype(np.float32)


def zone_of(z_abs, slope):
    """What a point of the real land is, for the rock's material (its 'zone' map): u = how much of a wall it is,
    by its steepness; v = its bed, by its height above the sea (the beds lie level across the valley)."""
    return smooth(0.8, 1.4, slope).astype(np.float32), np.interp(z_abs, LEVEL_Z, LEVEL_V).astype(np.float32)


def fan(name, r0, r1, rings, cols, base, sink=None):
    """A part of the real land as a fan of rings round the camera, closer together near it, the model's heights in
    its points (so every cliff is a true wall of polygons, diced as it is seen)."""
    rr = np.geomspace(r0, r1, rings)
    aa = np.radians(np.linspace(-29.0, 29.0, cols))
    RR, AA = np.meshgrid(rr, aa, indexing='ij')
    FX, FY = RR * np.sin(AA), RR * np.cos(AA)
    z_abs = T.heights(FX, FY, T.SITE)
    fz = (z_abs - base - (FX * FX + FY * FY) / (2 * T.EARTH) * 0.87).astype(np.float32)
    # Its steepness, from the model's heights a few metres to each side.
    e = 6.0
    sl = np.hypot(T.heights(FX + e, FY, T.SITE, False) - T.heights(FX - e, FY, T.SITE, False),
                  T.heights(FX, FY + e, T.SITE, False) - T.heights(FX, FY - e, T.SITE, False)) / (2 * e)
    wall, level = zone_of(z_abs, sl)
    if sink is not None:
        fz -= sink(FX, FY, RR)
    nr_, na_ = RR.shape
    co = np.stack([FX, FY, fz], -1).reshape(-1, 3).astype(np.float32)
    i = np.arange((nr_ - 1) * (na_ - 1))
    r, cc = i // (na_ - 1), i % (na_ - 1)
    a = r * na_ + cc
    faces = np.stack([a, a + 1, a + na_ + 1, a + na_], -1).astype(np.int32)
    ob = mesh_object(name, co, faces)
    uvl = ob.data.uv_layers.new(name='zone')
    loops = faces.ravel()
    uvl.data.foreach_set('uv', np.stack([wall.ravel()[loops], level.ravel()[loops]], -1).ravel())
    # Rock where the land is a butte's (its beds, by height) or steep; the floor's earth elsewhere.
    rocky = ((level.ravel() > 0.2) | (sl.ravel() > 0.5)).astype(np.float32)
    ob.data.polygons.foreach_set('material_index', (rocky[faces].mean(axis=1) > 0.5).astype(np.int32))
    return ob, co, sl.ravel(), level.ravel()


def build():
    global EYE, CLIFF_BASE, CLIFF_TOP
    out = bake.args()[1]
    if os.environ.get('NOOR_SAMPLES'):
        bpy.context.scene.cycles.samples = int(os.environ['NOOR_SAMPLES'])
    if os.environ.get('NOOR_BORDER'):                      # look at a part only: left, top, right, bottom (0..1)
        l, t, r, b = (float(v) for v in os.environ['NOOR_BORDER'].split(','))
        rs = bpy.context.scene.render
        rs.use_border, rs.use_crop_to_border = True, True
        rs.border_min_x, rs.border_max_x, rs.border_min_y, rs.border_max_y = l, r, 1.0 - b, 1.0 - t
    base = T.ground(T.SITE)
    CLIFF_BASE, CLIFF_TOP = WALL_FOOT - base, WALL_TOP - base
    print('we stand at %.4f N %.4f W, %.0f m above the sea, looking %.0f degrees from north' % (
        T.SITE['lat'], -T.SITE['lon'], base, T.SITE['bearing']), flush=True)

    # The valley round us on a plain grid: its heights, and what it is made of.
    xs, ys, X, Y = grids(FAR_X0, FAR_X1, FAR_Y0, FAR_Y1, FAR_CELL)
    hf = T.scene_z(X, Y, T.SITE, True, base)
    shape = hf.shape
    veg = fractal(shape, FAR_CELL, 30.0, 500.0, 2.2, 6)
    patch = fractal(shape, FAR_CELL, 8.0, 90.0, 2.4, 4)
    rockf, bedf, coverf = land_maps(X, Y, hf, FAR_CELL, veg, patch)
    # The ground round the camera, finer. The model has a height every ten metres; between them the ground is given
    # the small swells and hummocks every desert floor has (a hand high to knee high), no more.
    nxs, nys, NX, NY = grids(NEAR_X0, NEAR_X1, NEAR_Y0, NEAR_Y1, NEAR_CELL)
    hn = T.scene_z(NX, NY, T.SITE, True, base)
    fine = fractal(NX.shape, NEAR_CELL, 1.0, 9.0, 2.2, 9)
    swell = fractal(NX.shape, NEAR_CELL, 8.0, 45.0, 2.6, 10)
    rockn, bedn, covern = (sample(v, FAR_X0, FAR_Y0, FAR_CELL, NX, NY) for v in (rockf, bedf, coverf))
    rockn = np.clip(rockn + 0.25 * smooth(0.8, 1.6, fine + 0.6 * swell), 0.0, 1.0)
    hn = hn + (0.05 * fine + 0.22 * swell) * (1 - 0.5 * rockn)
    j0, i0 = int((0.0 - NEAR_Y0) / NEAR_CELL), int((0.0 - NEAR_X0) / NEAR_CELL)
    EYE = (0.0, 0.0, float(hn[j0, i0]) + EYE_H)
    print('eye', EYE, flush=True)

    mimg = bake.float_image('floor_mask', np.stack([rockf, bedf, coverf], -1), os.path.join(out, 'floor_mask.exr'))
    nimg = bake.float_image('near_mask', np.stack([rockn, bedn, covern], -1), os.path.join(out, 'near_mask.exr'))
    n = Nodes('Floor')
    uv = n.scaled(n.pos, (1.0 / (FAR_X1 - FAR_X0), 1.0 / (FAR_Y1 - FAR_Y0), 0.0), (-FAR_X0 / (FAR_X1 - FAR_X0), -FAR_Y0 / (FAR_Y1 - FAR_Y0), 0.0))
    fmat = floor_material(n, None, n.image(mimg, uv, 'Linear'), False)
    rock = rock_material()

    # The land: the model's heights from near us to the far skyline.
    def under_near(FX, FY, RR):
        inx = np.abs(FX - 0.5 * (NEAR_X0 + NEAR_X1))
        iny = np.abs(FY - 0.5 * (NEAR_Y0 + NEAR_Y1))
        return (2.5 * smooth(0.5 * (NEAR_X1 - NEAR_X0) - 4.0, 0.5 * (NEAR_X1 - NEAR_X0) - 14.0, inx) *
                smooth(0.5 * (NEAR_Y1 - NEAR_Y0) - 4.0, 0.5 * (NEAR_Y1 - NEAR_Y0) - 14.0, iny)).astype(np.float32)

    land, co, sl, level = fan('Land', 90.0, 9000.0, 1154, 751, base, under_near)
    bake.adaptive(land, DICE)
    land.data.materials.append(fmat)
    land.data.materials.append(rock)
    # Beyond nine kilometres a polygon is smaller than a point of the picture: no dicing, relief as shading only.
    far, _, _, _ = fan('LandFar', 8990.0, 61000.0, 250, 451, base, lambda FX, FY, RR: 3.0 * smooth(9200.0, 8990.0, RR))
    for m in (fmat, rock):
        m2 = m.copy()
        m2.displacement_method = 'BUMP'
        far.data.materials.append(m2)

    # The ground round the camera: its heights in its points; its rim dips under the land's.
    rim = np.minimum(np.minimum(NX - NEAR_X0, NEAR_X1 - NX), np.minimum(NY - NEAR_Y0, NEAR_Y1 - NY))
    zn = hn - 1.2 * smooth(9.0, 1.0, rim)
    cn = np.stack([NX, NY, zn], -1).reshape(-1, 3)
    ny_, nx_ = NX.shape
    i = np.arange((ny_ - 1) * (nx_ - 1))
    r, cc = i // (nx_ - 1), i % (nx_ - 1)
    a = r * nx_ + cc
    faces = np.stack([a, a + 1, a + nx_ + 1, a + nx_], -1).astype(np.int32)
    near = mesh_object('Near', cn, faces)
    bake.adaptive(near, DICE)
    n2 = Nodes('Near')
    uvn = n2.scaled(n2.pos, (1.0 / (NEAR_X1 - NEAR_X0), 1.0 / (NEAR_Y1 - NEAR_Y0), 0.0), (-NEAR_X0 / (NEAR_X1 - NEAR_X0), -NEAR_Y0 / (NEAR_Y1 - NEAR_Y0), 0.0))
    near.data.materials.append(floor_material(n2, None, n2.image(nimg, uvn, 'Linear'), False))

    def ground(x, y):
        """Height, rock, wash bed and brush cover at places: from the mesh round the camera where it reaches."""
        x = np.asarray(x, np.float32)
        y = np.asarray(y, np.float32)
        inn = (x > NEAR_X0 + 12) & (x < NEAR_X1 - 12) & (y > NEAR_Y0 + 12) & (y < NEAR_Y1 - 12)
        res = []
        for fa, na in ((hf, hn), (rockf, rockn), (bedf, bedn), (coverf, covern)):
            v = sample(fa, FAR_X0, FAR_Y0, FAR_CELL, x, y)
            if inn.any():
                v[inn] = sample(na, NEAR_X0, NEAR_Y0, NEAR_CELL, x[inn], y[inn])
            res.append(v)
        return res

    # The slopes under the buttes' walls, for the blocks that have fallen on them: each butte's own.
    taluses = []
    slopes = (level > 0.42) & (level < 0.58) & (sl > 0.3) & (sl < 0.95)
    for name, lat, lon in BUTTES:
        bx, by = T.scene_xy(lat, lon, T.SITE)
        pick = slopes & (np.hypot(co[:, 0] - bx, co[:, 1] - by) < 900.0)
        print('%-12s bearing %+.1f, %.0f m away, %d points of talus' % (name, math.degrees(math.atan2(bx, by)), math.hypot(bx, by), int(pick.sum())), flush=True)
        if pick.sum() > 50:
            taluses.append((name.replace(' ', ''), co[pick], math.hypot(bx, by)))
    if not os.environ.get('NOOR_BARE'):
        plants(ground, EYE, taluses)


def main():
    if os.environ.get('NOOR_BAKE_SAMPLES'):
        render = bake.bake_render
        bake.bake_render = lambda out, yaw=0.0, samples=512, aovs=(): render(out, yaw=yaw, samples=int(os.environ['NOOR_BAKE_SAMPLES']), aovs=aovs)
    if os.environ.get('NOOR_PACK_QUALITY'):
        pack = bake.bake_pack
        bake.bake_pack = lambda src, dst, suns, eye, aovs=(), quality=80, frame='0001', water=None: pack(
            src, dst, suns, eye, aovs=aovs, quality=int(os.environ['NOOR_PACK_QUALITY']), frame=frame, water=water)
    # (The camera's height is found while the ground is built: bake.run places the camera afterwards, so it is
    # handed a list that build() fills in.)
    eye = [0.0, 0.0, 0.0]

    def make():
        build()
        eye[:] = EYE

    bake.run(make, eye, aovs=('mask',), exposure=-2.3)


main()
