"""The Arctic scenery: a snowy, rocky shore of a fjord in the far north in deep winter; across the still water,
peaks that rise straight out of the sea.

The mountains are real, and so is the view: the wall of peaks behind Reinefjorden in Lofoten as it is seen from the
shore at Reine (arctic_prep.py found the place: `wall`), nothing moved, cut or made taller. Their shapes come from
Kartverket's national height model (five-metre cells, fetched on the first run) and, farther off, from the
Copernicus DEM (thirty metres); see sources/arctic.md. Crags and ledges finer than that, and where the snow lies,
are grown on them here, the rock's colour and the pattern snow catches in from a scanned rock face.
The shore is photogrammetry of real shores (Poly Haven, CC0): scanned snow and tide stones for the ground, scanned
rocks, reefs and skerries at their true size, with winter (snow caps, ice, wet) made on them here.

    python -I tools/scenery/arctic_prep.py tiles <folder of Copernicus tiles> <work dir>      once, before anything
    blender -b --factory-startup -P tools/scenery/arctic.py -- preview <out dir> [azimuth elevation]

While working: NOOR_BAND=1 renders only the band the phone shows whole, NOOR_PCT its size (per cent),
NOOR_NO_SHORE=1 / NOOR_NO_MOUNTAINS=1 leave a part out, NOOR_SECTORS=<json> tries other views, NOOR_SPP the samples.
"""
import math
import os
import sys

import bmesh
import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake                      # noqa: E402
import arctic_prep as prep       # noqa: E402

WORK = os.environ.get('NOOR_ARCTIC_WORK', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'work', 'arctic'))
WATER = 0.0                       # the sea's level (scene z)
EYE = 2.25                        # the camera's height over the sea: a low bank, and a person standing on it
GRID_AZ = float(os.environ.get('NOOR_GRID_AZ', 0.05))      # the mountains' mesh: degrees between its spokes
GRID_R = float(os.environ.get('NOOR_GRID_R', 0.0015))      # ... how much farther each ring lies than the last
GRID_FAR = 0.004                  # ... and beyond the fine model

# The view: one real place, as the land has it. We stand on the shore at Reine (Moskenesoya, Lofoten: 67.949 N,
# 13.012 E) and look north by east (bearing 16) over Reinefjorden at the wall of peaks behind it, rank behind rank. Nothing is
# moved, cut or made taller. (Metres east and north in the region's grid, the bearing of the picture's middle; the
# fan of ground that is built, in degrees right of the middle; how far the fine elevation model is used.)
REGION = 'lofoten'
SECTORS = [
    dict(name='Wall', x=4635.0, y=22155.0, bearing=float(os.environ.get('NOOR_BEARING', 16.0)), mirror=1, az=(-27.0, 27.0), near=300.0, far=75000.0, fine=15000.0),
]
SCANS = os.environ.get('NOOR_POLYHAVEN', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'polyhaven'))
FINE = os.environ.get('NOOR_NHM', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'kartverket_nhm'))


# ---- Small tools ---------------------------------------------------------------------------------------------------

def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def hash3(ix, iy, iz, seed):
    n = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + iz.astype(np.int64) * 2147483647 + seed * 1274126177) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    n = n ^ (n >> 16)
    return (n & 0xffff).astype(np.float32) / 65535.0


def vnoise(p, seed=0):
    """Smooth noise 0..1 at points p (..., 3)."""
    i = np.floor(p).astype(np.int64)
    f = (p - i).astype(np.float32)
    u = f * f * (3 - 2 * f)
    out = 0.0
    for dz in (0, 1):
        for dy in (0, 1):
            for dx in (0, 1):
                w = (u[..., 0] if dx else 1 - u[..., 0]) * (u[..., 1] if dy else 1 - u[..., 1]) * (u[..., 2] if dz else 1 - u[..., 2])
                out = out + w * hash3(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
    return out


def fbm(p, octaves=4, seed=0, gain=0.5):
    out, amp, tot = 0.0, 1.0, 0.0
    for o in range(octaves):
        out = out + amp * vnoise(p * (2.0 ** o), seed + o * 7)
        tot += amp
        amp *= gain
    return out / tot


def mesh_from(name, co, faces, smooth=True):
    me = bpy.data.meshes.new(name)
    me.vertices.add(len(co))
    me.vertices.foreach_set('co', np.asarray(co, np.float32).ravel())
    n = faces.shape[1]
    me.loops.add(faces.size)
    me.loops.foreach_set('vertex_index', faces.astype(np.int32).ravel())
    me.polygons.add(len(faces))
    me.polygons.foreach_set('loop_start', np.arange(0, faces.size, n, dtype=np.int32))
    me.polygons.foreach_set('loop_total', np.full(len(faces), n, np.int32))
    me.polygons.foreach_set('use_smooth', np.full(len(faces), smooth, bool))
    me.update(calc_edges=True)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def grid_faces(ny, nx):
    i = np.arange((ny - 1) * (nx - 1))
    r, c = i // (nx - 1), i % (nx - 1)
    a = r * nx + c
    return np.stack([a, a + 1, a + nx + 1, a + nx], -1).astype(np.int32)


class Nodes:
    """Shorthand for building a material's node tree."""

    def __init__(self, name, displace=True):
        self.mat = bpy.data.materials.new(name)
        self.mat.use_nodes = True
        if displace:
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
        self.z = sep.outputs['Z']
        nrm = self.N.new('ShaderNodeSeparateXYZ')
        self.L.new(self.geo.outputs['Normal'], nrm.inputs[0])
        self.nz = nrm.outputs['Z']

    def put(self, sock, val):
        if isinstance(val, (int, float)):
            sock.default_value = float(val)
        elif isinstance(val, tuple):
            sock.default_value = val
        else:
            self.L.new(val, sock)

    def math(self, op, a, b=None, c=None):
        m = self.N.new('ShaderNodeMath')
        m.operation = op
        self.put(m.inputs[0], a)
        if b is not None:
            self.put(m.inputs[1], b)
        if c is not None:
            self.put(m.inputs[2], c)
        return m.outputs[0]

    def add(self, a, b): return self.math('ADD', a, b)
    def sub(self, a, b): return self.math('SUBTRACT', a, b)
    def mul(self, a, b): return self.math('MULTIPLY', a, b)

    def ramp(self, x, a, b, lo=0.0, hi=1.0, smooth=True):
        r = self.N.new('ShaderNodeMapRange')
        r.interpolation_type = 'SMOOTHSTEP' if smooth else 'LINEAR'
        self.put(r.inputs['Value'], x)
        r.inputs['From Min'].default_value = a
        r.inputs['From Max'].default_value = b
        r.inputs['To Min'].default_value = lo
        r.inputs['To Max'].default_value = hi
        return r.outputs[0]

    def noise(self, scale, detail=4.0, rough=0.5, kind='FBM', vector=None, lac=2.0, dist=0.0, out='Fac'):
        n = self.N.new('ShaderNodeTexNoise')
        n.noise_dimensions = '3D'
        n.noise_type = kind
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        n.inputs['Lacunarity'].default_value = lac
        n.inputs['Distortion'].default_value = dist
        self.L.new(vector if vector is not None else self.pos, n.inputs['Vector'])
        return n.outputs[out]

    def voronoi(self, scale, feature='F1', vector=None, out='Distance', rand=1.0):
        n = self.N.new('ShaderNodeTexVoronoi')
        n.feature = feature
        n.inputs['Scale'].default_value = scale
        n.inputs['Randomness'].default_value = rand
        self.L.new(vector if vector is not None else self.pos, n.inputs['Vector'])
        return n.outputs[out]

    def mix(self, f, a, b):
        m = self.N.new('ShaderNodeMix')
        m.data_type = 'RGBA'
        self.put(m.inputs[0], f)
        self.put(m.inputs[6], a)
        self.put(m.inputs[7], b)
        return m.outputs[2]

    def mixf(self, f, a, b):
        m = self.N.new('ShaderNodeMix')
        m.data_type = 'FLOAT'
        self.put(m.inputs[0], f)
        self.put(m.inputs[2], a)
        self.put(m.inputs[3], b)
        return m.outputs[0]

    def displace(self, height, scale=1.0):
        d = self.N.new('ShaderNodeDisplacement')
        d.space = 'OBJECT'
        d.inputs['Midlevel'].default_value = 0.0
        d.inputs['Scale'].default_value = scale
        self.L.new(height, d.inputs['Height'])
        self.L.new(d.outputs[0], self.out.inputs['Displacement'])

    def attr(self, name):
        n = self.N.new('ShaderNodeAttribute')
        n.attribute_name = name
        return n.outputs['Fac']

    def colour(self, c): self.put(self.bsdf.inputs['Base Color'], c)
    def rough(self, r): self.put(self.bsdf.inputs['Roughness'], r)


SNOW = (0.80, 0.82, 0.85, 1.0)


# ---- The mountains -------------------------------------------------------------------------------------------------

def mountain_material():
    """Winter mountains by the sea. Snow lies whole on what is gentle; on the steep faces it clings to every ledge
    and gully and leaves the risers bare, a lace of white on grey rock, thinner the steeper the face; the wind
    strips ridges and fills hollows. A dark line of washed rock runs along the sea, and birch scrub shows through
    the snow of the low slopes. Crags and drifts finer than the elevation model are grown on it."""
    m = Nodes('Mountain')
    slope = m.attr('nz')                                             # the model's own slope (1 level .. 0 a wall)
    hollow = m.attr('cv')                                            # + in a hollow, - on a ridge (about -1..1)
    hollow = m.math('MINIMUM', m.math('MAXIMUM', hollow, -1.0), 1.0)
    steep = m.ramp(slope, 0.50, 0.74, 1.0, 0.0)                      # where rock breaks through: crags and ledges
    above = m.ramp(m.z, 1.0, 14.0)                                   # nothing is grown at the water's edge
    # Rock is bedded: its ledges run level. (The same noise, read through a squeezed height.)
    bed = m.N.new('ShaderNodeMapping')
    bed.inputs['Scale'].default_value = (1.0, 1.0, 3.6)
    m.L.new(m.pos, bed.inputs['Vector'])
    ledge = m.noise(1.0 / 34.0, 10.0, 0.64, vector=bed.outputs[0])
    crag = m.noise(1.0 / 90.0, 8.0, 0.55, 'RIDGED_MULTIFRACTAL')
    crag2 = m.noise(1.0 / 9.0, 6.0, 0.58)
    drift = m.noise(1.0 / 46.0, 3.0, 0.45)
    h = m.mul(m.add(m.mul(m.sub(crag, 0.6), 7.0), m.add(m.mul(m.sub(ledge, 0.5), 5.0), m.mul(m.sub(crag2, 0.5), 1.6))), steep)
    h = m.add(h, m.mul(m.mul(m.sub(drift, 0.5), 2.0), m.sub(1.0, steep)))
    m.displace(m.mul(h, above))

    # How much of the ground snow covers here: all of it where it is gentle, down to a sixth on a wall; more in a
    # hollow, less on a ridge. Then which parts: the ledges (where the bedded noise is low), not the risers.
    patch = m.noise(1.0 / 26.0, 5.0, 0.6)
    cover = m.add(m.ramp(slope, 0.50, 0.82, 0.08, 1.16, smooth=False), m.add(m.mul(hollow, 0.16), m.mul(m.sub(patch, 0.5), 0.16)))
    # (A part of the lace's pattern is a real rock face's: a scan's own relief, laid on at many times its size.)
    face = {}
    for kind, size in (('disp', 55.0), ('diff', 90.0)):
        mp = m.N.new('ShaderNodeMapping')
        mp.inputs['Scale'].default_value = (1.0 / size, 1.0 / size, 1.0 / size)
        m.L.new(m.pos, mp.inputs['Vector'])
        d = os.path.join(SCANS, 'dark_rock_02', 'textures')
        node = m.N.new('ShaderNodeTexImage')
        node.image = bpy.data.images.load(os.path.join(d, [f for f in os.listdir(d) if '_%s_' % kind in f][0]), check_existing=True)
        if kind != 'diff':
            node.image.colorspace_settings.name = 'Non-Color'
        node.projection = 'BOX'
        node.projection_blend = 0.3
        m.L.new(mp.outputs[0], node.inputs['Vector'])
        face[kind] = node.outputs['Color']
    relief = m.N.new('ShaderNodeSeparateColor')
    m.L.new(face['disp'], relief.inputs[0])
    lace = m.add(m.add(m.mul(ledge, 0.58), m.mul(relief.outputs[0], 0.20)), m.mul(m.noise(1.0 / 3.1, 5.0, 0.6, vector=bed.outputs[0]), 0.22))
    snow = m.ramp(m.sub(m.add(0.2, m.mul(cover, 0.75)), lace), -0.035, 0.035)
    # The sea keeps the lowest rock bare and dark.
    wash = m.ramp(m.add(m.z, m.mul(m.sub(patch, 0.5), 3.0)), 1.5, 4.0)
    snow = m.mul(snow, wash)
    rock_tone = m.noise(1.0 / 55.0, 6.0, 0.62)
    tone = m.mix(m.ramp(rock_tone, 0.28, 0.75), (0.5, 0.5, 0.5, 1.0), (1.3, 1.3, 1.3, 1.0))
    grey = m.N.new('ShaderNodeHueSaturation')
    grey.inputs['Saturation'].default_value = 0.6
    grey.inputs['Value'].default_value = 0.9
    m.L.new(face['diff'], grey.inputs['Color'])
    rk = m.N.new('ShaderNodeMix')
    rk.data_type = 'RGBA'
    rk.blend_type = 'MULTIPLY'
    rk.inputs[0].default_value = 1.0
    m.L.new(grey.outputs['Color'], rk.inputs[6])
    m.L.new(tone, rk.inputs[7])
    rock = rk.outputs[2]
    # Rime: even bare rock is greyed by frost, except where the sea washes it.
    rime = m.mul(m.mul(m.ramp(m.noise(1.0 / 6.0, 5.0, 0.6), 0.35, 0.7), 0.30), wash)
    rock = m.mix(rime, rock, SNOW)
    rock = m.mix(m.sub(1.0, wash), rock, (0.022, 0.022, 0.024, 1.0))
    # Scrub (dwarf birch, heather) through thin snow on the low, gentler slopes: dark speckle.
    speck = m.voronoi(1.0 / 2.2)
    low = m.mul(m.ramp(m.z, 170.0, 20.0), m.ramp(slope, 0.72, 0.90))
    scrub = m.mul(m.mul(m.ramp(speck, 0.42, 0.20), m.ramp(m.noise(1.0 / 70.0, 3.0), 0.40, 0.62)), low)
    col = m.mix(snow, rock, SNOW)
    col = m.mix(m.mul(scrub, 0.8), col, (0.045, 0.036, 0.030, 1.0))
    m.colour(col)
    m.rough(m.mixf(snow, 0.9, 0.55))
    m.bsdf.inputs['Specular IOR Level'].default_value = 0.3
    bake.add_mask(m.mat, wind=0.0, snow=0.0, water_level=WATER)
    return m.mat


def sector(land, fine, s, mat):
    """One real view, laid in its part of the picture: a fan of ground from the camera outwards. Near the camera
    its heights come from the fine model (five metres), beyond `fine` metres from the coarse one (thirty)."""
    a0, a1 = s['az']
    az = np.arange(a0, a1 + 1e-6, GRID_AZ)
    # Where does this view's land begin? (No mesh is spent on the open water in front of it.)
    pa = math.radians(s['bearing']) + s['mirror'] * np.radians(np.linspace(a0, a1, 60))[None, :]
    pr = np.geomspace(s['near'], s['fine'], 400)[:, None]
    isl = land.at(s['x'] + pr * np.sin(pa), s['y'] + pr * np.cos(pa)) > 0.5
    near = max(s['near'], 0.9 * float(pr[np.argmax(isl.any(axis=1)), 0]) - 60.0) if isl.any() else s['near']
    # Rings as close as the fine model's cells where it is used, wider apart beyond.
    r1 = np.geomspace(near, s['fine'], int(math.log(s['fine'] / near) / GRID_R))
    r2 = np.geomspace(s['fine'], s['far'], int(math.log(s['far'] / s['fine']) / GRID_FAR))[1:]
    r = np.concatenate([r1, r2])
    A, R = np.meshgrid(np.radians(az), r)
    b = math.radians(s['bearing']) + s['mirror'] * A
    x, y = s['x'] + R * np.sin(b), s['y'] + R * np.cos(b)

    def height(x, y):
        z = land.smooth(x, y).astype(np.float32)
        e, n = prep.region_utm(REGION, x, y)
        zf, have = fine.at(e, n)
        w = have * (1.0 - smoothstep(s['fine'] - 1500.0, s['fine'], R))
        return (z * (1.0 - w) + zf * w).astype(np.float32)

    z = height(x, y)
    # Hollows and ridges: how the ground stands against the ground forty metres round it.
    d = 40.0
    hollow = (0.25 * (height(x + d, y) + height(x - d, y) + height(x, y + d) + height(x, y - d)) - z) / 10.0
    z = z - (R ** 2 / (2 * prep.R_EARTH) * prep.REFRACT).astype(np.float32)     # the Earth is round
    # The sea's bed falls away from the shore (what lies under water is drawn by the phone).
    z = np.where(z >= 1.0, z, z - (1.0 - z) * 3.0)
    z = np.maximum(z, -9.0)
    P = np.stack([R * np.sin(A), R * np.cos(A), z], -1).astype(np.float32)
    # The ground's own slope, smooth (the renderer's polygons are flat, and snow laid by their slope shows them).
    du = np.gradient(P, axis=0)
    dv = np.gradient(P, axis=1)
    nrm = np.cross(dv, du)
    nz = np.abs(nrm[..., 2]) / np.maximum(np.linalg.norm(nrm, axis=-1), 1e-9)
    ob = mesh_from(s['name'], P.reshape(-1, 3), grid_faces(len(r), len(az)))
    for name, val in (('nz', nz), ('cv', hollow)):
        at = ob.data.attributes.new(name=name, type='FLOAT', domain='POINT')
        at.data.foreach_set('value', np.ascontiguousarray(val, np.float32).ravel())
    ob.data.materials.append(mat)
    # (Not diced further: its points are already about a pixel apart, and the fine model's every cell is kept.)
    print(s['name'], 'vertices', P.shape[0] * P.shape[1], 'highest', float(z.max()), 'skyline', float(np.degrees(np.arctan2(z - EYE, R)).max()), flush=True)
    return ob


# ---- The sea's bed ---------------------------------------------------------------------------------------------------

def bed():
    """Under all the water out to the horizon: the phone draws the sea wherever this shows."""
    ys = np.geomspace(1.0, 160000.0, 60) - 1.0
    xh = np.geomspace(1.0, 90000.0, 40) - 1.0
    xs = np.concatenate([-xh[::-1], xh[1:]])
    ob = bake.grid_mesh('SeaBed', xs, np.concatenate([[-30.0], ys]), None)
    ob.location = (0.0, 0.0, -8.0)
    m = Nodes('SeaBed', displace=False)
    m.colour((0.03, 0.04, 0.045, 1.0))
    m.rough(0.9)
    bake.add_mask(m.mat, water_level=WATER)
    ob.data.materials.append(m.mat)
    return ob


# ---- The shore we stand on -----------------------------------------------------------------------------------------
#
# Built from photogrammetry of real places (Poly Haven, CC0; see sources/arctic.md): the snow underfoot and the wet
# stones the sea keeps bare are scanned ground, laid over a bank shaped here; every rock, reef and skerry is a scan
# of a real shore at its true size. On the scans, winter is made here: snow lying on what faces up (real geometry,
# a soft cap grown on the scan), rock greyed and darkened to northern gneiss, wet and dark by the water, glazed
# with ice in the band the sea splashes.

_scans = {}


def shoreline(x):
    """How far in front of the camera the water's edge lies (metres), across the picture: a shallow cove."""
    return 4.4 + 0.7 * np.sin(0.21 * x + 0.7) + 0.45 * np.sin(0.47 * x + 2.0) + 0.2 * np.sin(1.3 * x + 0.3) + 0.012 * x * x


def ground_height(x, y, water):
    """The ground and the sea's bed near the camera (scene z), and how far inland of the water's edge a place is."""
    d = shoreline(x) - y
    p = np.stack([x, y, np.zeros_like(x)], -1)
    d = d + (fbm(p * 0.45, 3, 11) - 0.5) * 1.1
    up = 0.05 + 0.60 * (1.0 - np.exp(-np.maximum(d, 0.0) / 1.3)) + 0.03 * np.maximum(d, 0.0)
    down = -0.6 * (1.0 - np.exp(np.minimum(d, 0.0) / 9.0)) + np.minimum(d, 0.0) * 0.008      # shallows, far out
    z = np.where(d > 0.0, up, down)
    z = z + smoothstep(0.5, 2.2, d) * (fbm(p * 0.25, 3, 3) - 0.5) * 0.30                    # drifts in the bank's snow
    return np.maximum(water + z, water - 7.5), d


def scan_texture(m, aid, kind, vector, colour=False):
    d = os.path.join(SCANS, aid, 'textures')
    name = [f for f in os.listdir(d) if '_%s_' % kind in f][0]
    node = m.N.new('ShaderNodeTexImage')
    node.image = bpy.data.images.load(os.path.join(d, name), check_existing=True)
    if not colour:
        node.image.colorspace_settings.name = 'Non-Color'
    m.L.new(vector, node.inputs['Vector'])
    return node.outputs['Color']


def ground(water):
    """The bank and the bed: scanned snow above the sea's reach, scanned low-tide stones below it."""
    xh = np.geomspace(1.0, 141.0, 150) - 1.0
    xs = np.concatenate([-xh[::-1], xh[1:]])
    ys = np.concatenate([np.linspace(-10.0, -0.5, 20), np.geomspace(1.0, 221.0, 230) - 1.0])
    X, Y = np.meshgrid(xs.astype(np.float32), ys.astype(np.float32))
    Z, _ = ground_height(X, Y, water)
    ob = bake.grid_mesh('Shore', xs, ys, Z)
    bake.adaptive(ob, 1.0)

    m = Nodes('Shore')
    h = m.sub(m.z, water)

    def laid(aid, size, turn, kinds):
        mp = m.N.new('ShaderNodeMapping')
        mp.inputs['Scale'].default_value = (1.0 / size, 1.0 / size, 1.0)
        mp.inputs['Rotation'].default_value = (0.0, 0.0, turn)
        m.L.new(m.pos, mp.inputs['Vector'])
        return {k: scan_texture(m, aid, k, mp.outputs[0], k == 'diff') for k in kinds}

    kinds = ('diff', 'disp', 'nor_gl', 'rough')
    # Each scan twice, at its true size and a little larger and turned, the two mixed by patches: no tile shows twice.
    patch = m.ramp(m.noise(0.31, 3.0, 0.5), 0.42, 0.58)
    sa, sb = laid('snow_02', 2.0, 0.0, kinds), laid('snow_02', 3.1, 0.9, kinds)
    ta, tb = laid('low_tide_rocks', 2.17, 0.0, kinds), laid('low_tide_rocks', 3.4, 2.2, kinds)
    snow_t = {k: m.mix(patch, sa[k], sb[k]) for k in kinds}
    tide_t = {k: m.mix(patch, ta[k], tb[k]) for k in kinds}
    # Where the sea reaches (waves, tide) no snow lies; above, the snow, ending in a soft lip.
    edge = m.noise(0.9, 3.0, 0.5)
    reach = m.add(h, m.mul(m.sub(edge, 0.5), 0.16))
    snow = m.ramp(reach, 0.20, 0.34)
    sep_s = m.N.new('ShaderNodeSeparateColor'); m.L.new(snow_t['disp'], sep_s.inputs[0])
    sep_t = m.N.new('ShaderNodeSeparateColor'); m.L.new(tide_t['disp'], sep_t.inputs[0])
    lift = m.add(m.mul(snow, 0.10), m.mul(m.mul(m.sub(sep_s.outputs[0], 0.5), 0.05), snow))
    stones = m.mul(m.mul(m.sub(sep_t.outputs[0], 0.5), 0.09), m.sub(1.0, snow))
    m.displace(m.add(lift, stones))
    # The stones of a northern shore are grey and, wet, nearly black; a rim of ice on those just above the water.
    hsv = m.N.new('ShaderNodeHueSaturation')
    hsv.inputs['Saturation'].default_value = 0.45
    hsv.inputs['Value'].default_value = 0.55
    m.L.new(tide_t['diff'], hsv.inputs['Color'])
    rim = m.mul(m.mul(m.ramp(reach, 0.015, 0.06), m.ramp(reach, 0.30, 0.12)), m.ramp(m.noise(3.1, 3.0, 0.6), 0.35, 0.6))
    tide = m.mix(m.mul(rim, 0.7), hsv.outputs['Color'], (0.62, 0.68, 0.72, 1.0))
    # Scanned snow, kept as bright as fresh snow is.
    sn = m.N.new('ShaderNodeHueSaturation')
    sn.inputs['Saturation'].default_value = 0.6
    sn.inputs['Value'].default_value = 1.38
    m.L.new(snow_t['diff'], sn.inputs['Color'])
    m.colour(m.mix(snow, tide, sn.outputs['Color']))
    sr = m.N.new('ShaderNodeSeparateColor'); m.L.new(snow_t['rough'], sr.inputs[0])
    tr = m.N.new('ShaderNodeSeparateColor'); m.L.new(tide_t['rough'], tr.inputs[0])
    m.rough(m.mixf(snow, m.mul(tr.outputs[0], 0.55), sr.outputs[0]))
    nm = m.N.new('ShaderNodeNormalMap')
    m.L.new(m.mix(snow, tide_t['nor_gl'], snow_t['nor_gl']), nm.inputs['Color'])
    m.L.new(nm.outputs['Normal'], m.bsdf.inputs['Normal'])
    m.bsdf.inputs['Specular IOR Level'].default_value = 0.4
    bake.add_mask(m.mat, wind=0.0, snow=0.0, water_level=water)
    ob.data.materials.append(m.mat)
    return ob


def winter_rock(mat, water):
    """A scanned rock's own material, taken north and into winter: greyer and darker (gneiss, not sandstone), wet
    and nearly black where the sea reaches it, glazed with ice in the band above the water, rimed on top."""
    nt = mat.node_tree
    N, L = nt.nodes, nt.links
    bsdf = [n for n in N if n.bl_idname == 'ShaderNodeBsdfPrincipled'][0]
    base = bsdf.inputs['Base Color'].links[0].from_socket
    rough = bsdf.inputs['Roughness'].links[0].from_socket if bsdf.inputs['Roughness'].links else None
    geo = N.new('ShaderNodeNewGeometry')
    sep = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Position'], sep.inputs[0])

    def ramp(x, a, b):
        r = N.new('ShaderNodeMapRange')
        r.interpolation_type = 'SMOOTHSTEP'
        r.inputs['From Min'].default_value = a
        r.inputs['From Max'].default_value = b
        L.new(x, r.inputs['Value'])
        return r.outputs[0]

    def mul(a, b):
        n = N.new('ShaderNodeMath')
        n.operation = 'MULTIPLY'
        L.new(a, n.inputs[0])
        if isinstance(b, float):
            n.inputs[1].default_value = b
        else:
            L.new(b, n.inputs[1])
        return n.outputs[0]

    def mix(f, a, b):
        n = N.new('ShaderNodeMix')
        n.data_type = 'RGBA'
        L.new(f, n.inputs[0])
        for sock, v in ((n.inputs[6], a), (n.inputs[7], b)):
            if isinstance(v, tuple):
                sock.default_value = v
            else:
                L.new(v, sock)
        return n.outputs[2]

    hsv = N.new('ShaderNodeHueSaturation')
    hsv.inputs['Saturation'].default_value = 0.35
    hsv.inputs['Value'].default_value = 0.50
    L.new(base, hsv.inputs['Color'])
    noise = N.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 2.3
    noise.inputs['Detail'].default_value = 4.0
    L.new(geo.outputs['Position'], noise.inputs['Vector'])
    wet = ramp(sep.outputs['Z'], water + 0.30, water + 0.04)
    col = mix(mul(wet, 0.6), hsv.outputs['Color'], (0.010, 0.010, 0.011, 1.0))
    glaze = mul(mul(ramp(sep.outputs['Z'], water + 0.01, water + 0.05), ramp(sep.outputs['Z'], water + 0.36, water + 0.10)),
                ramp(noise.outputs['Fac'], 0.30, 0.62))
    col = mix(mul(glaze, 0.8), col, (0.60, 0.67, 0.72, 1.0))
    L.new(col, bsdf.inputs['Base Color'])
    if rough is not None:
        rsep = N.new('ShaderNodeSeparateColor')
        L.new(rough, rsep.inputs[0])
        rm = N.new('ShaderNodeMix')
        rm.data_type = 'FLOAT'
        add = N.new('ShaderNodeMath')
        add.operation = 'MAXIMUM'
        L.new(wet, add.inputs[0])
        L.new(glaze, add.inputs[1])
        L.new(add.outputs[0], rm.inputs[0])
        L.new(rsep.outputs[0], rm.inputs[2])
        rm.inputs[3].default_value = 0.2
        L.new(rm.outputs[0], bsdf.inputs['Roughness'])
    bake.add_mask(mat, wind=0.0, snow=0.0, water_level=water)


def scan(aid, lod, water):
    """A scanned model (one of its levels of detail) as a new object; later calls share its mesh."""
    key = '%s_LOD%d' % (aid, lod)
    if key in _scans:
        ob = _scans[key].copy()
        bpy.context.scene.collection.objects.link(ob)
        return ob
    d = os.path.join(SCANS, aid)
    blend = [f for f in os.listdir(d) if f.endswith('.blend')][0]
    with bpy.data.libraries.load(os.path.join(d, blend)) as (src, dst):
        dst.objects = [n for n in src.objects if n == key]
    ob = dst.objects[0]
    bpy.context.scene.collection.objects.link(ob)
    for mat in ob.data.materials:
        if 'winter' not in mat:
            winter_rock(mat, water)
            mat['winter'] = True
    _scans[key] = ob
    return ob


def place(aid, x, y, top, water, scale=1.0, turn=0.0, lod=2):
    """Stands a scan at (x, y), turned about the upright, so that its highest point is `top` metres over the water."""
    ob = scan(aid, lod, water)
    zs = [v[2] for v in ob.bound_box]
    ob.scale = (scale, scale, scale)
    ob.rotation_euler = (0.0, 0.0, math.radians(turn))
    ob.location = (x, y, water + top - max(zs) * scale)
    return ob


def snow_caps(obs, water, rng):
    """Snow on the scanned rocks, as snow lies: on what faces up, clear of the sea's reach, in soft caps thickest in
    their middles. Real geometry, grown from the scans' own surfaces."""
    cos, fas, n0 = [], [], 0
    for ob in obs:
        me = ob.data
        n = len(me.vertices)
        co = np.zeros(n * 3, np.float32)
        me.vertices.foreach_get('co', co)
        nr = np.zeros(n * 3, np.float32)
        me.vertices.foreach_get('normal', nr)
        M = np.array(ob.matrix_world, np.float32)
        ob_scale = float(np.linalg.norm(M[:3, 0]))
        co = co.reshape(-1, 3) @ M[:3, :3].T + M[:3, 3]
        nr = nr.reshape(-1, 3) @ (M[:3, :3] / ob_scale).T
        me.calc_loop_triangles()
        tri = np.zeros(len(me.loop_triangles) * 3, np.int32)
        me.loop_triangles.foreach_get('vertices', tri)
        tri = tri.reshape(-1, 3)
        wob = fbm(co * 1.3 + 17.0, 2, 5)
        on = (nr[:, 2] > 0.74 + (wob - 0.5) * 0.22) & (co[:, 2] > water + 0.46 + (wob - 0.5) * 0.14)
        keep = on[tri].all(axis=1)
        if keep.sum() < 8:
            continue
        ft = tri[keep]
        # How far inside its cap each point lies (metres), by a few rounds over the edges.
        e = np.concatenate([ft[:, [0, 1]], ft[:, [1, 2]], ft[:, [2, 0]]])
        elen = np.linalg.norm(co[e[:, 0]] - co[e[:, 1]], axis=1)
        rim = np.zeros(n, bool)
        out = tri[~keep]
        rim[out.ravel()] = True
        dist = np.where(rim, 0.0, 9.0).astype(np.float32)
        for _ in range(14):
            np.minimum.at(dist, e[:, 0], dist[e[:, 1]] + elen)
            np.minimum.at(dist, e[:, 1], dist[e[:, 0]] + elen)
        inside = smoothstep(0.0, 0.22 * max(ob_scale, 0.6), np.minimum(dist, 9.0))
        # Snow smooths what it covers: the cap's points move towards the mean of their neighbours, then up.
        p = co.copy()
        for _ in range(5):
            acc = np.zeros_like(p)
            cnt = np.zeros(n, np.float32)
            np.add.at(acc, e[:, 0], p[e[:, 1]])
            np.add.at(acc, e[:, 1], p[e[:, 0]])
            np.add.at(cnt, e[:, 0], 1.0)
            np.add.at(cnt, e[:, 1], 1.0)
            mean = acc / np.maximum(cnt, 1.0)[:, None]
            w = (0.6 * inside * (cnt > 0))[:, None]
            p = p * (1 - w) + mean * w
        thick = (0.07 + 0.07 * wob) * min(max(ob_scale, 0.7), 1.6)
        p[:, 2] = np.maximum(p[:, 2], co[:, 2]) + 0.006 + thick * inside
        used = np.unique(ft)
        remap = np.full(n, -1, np.int32)
        remap[used] = np.arange(len(used)) + n0
        cos.append(p[used])
        fas.append(remap[ft])
        n0 += len(used)
    if not cos:
        return None
    ob = mesh_from('SnowCaps', np.concatenate(cos), np.concatenate(fas))
    m = Nodes('SnowCap', displace=False)
    mp = m.N.new('ShaderNodeMapping')
    mp.inputs['Scale'].default_value = (0.5, 0.5, 1.0)
    m.L.new(m.pos, mp.inputs['Vector'])
    sn = m.N.new('ShaderNodeHueSaturation')
    sn.inputs['Saturation'].default_value = 0.6
    sn.inputs['Value'].default_value = 1.38
    m.L.new(scan_texture(m, 'snow_02', 'diff', mp.outputs[0], True), sn.inputs['Color'])
    m.colour(sn.outputs['Color'])
    nm = m.N.new('ShaderNodeNormalMap')
    nm.inputs['Strength'].default_value = 0.5
    m.L.new(scan_texture(m, 'snow_02', 'nor_gl', mp.outputs[0]), nm.inputs['Color'])
    m.L.new(nm.outputs['Normal'], m.bsdf.inputs['Normal'])
    m.rough(0.6)
    m.bsdf.inputs['Specular IOR Level'].default_value = 0.3
    bake.add_mask(m.mat, wind=0.0, snow=0.0, water_level=water)
    ob.data.materials.append(m.mat)
    print('snow caps: vertices', n0, flush=True)
    return ob


def shore(water, eye):
    rng = np.random.default_rng(int(os.environ.get('NOOR_SHORE_SEED', 7)))
    ground(water)
    obs = []

    def put(aid, x, y, top, scale=1.0, turn=None, lod=2):
        obs.append(place(aid, x, y, top, water, scale, rng.uniform(0, 360) if turn is None else turn, lod))

    # To either side of the open water in front of us: shelves and heaps of shore rock, as they were scanned.
    put('coast_land_rocks_02', -5.4, 10.5, 1.0, 1.0, 20.0, 1)
    put('coast_land_rocks_03', 5.2, 8.6, 1.1, 1.0, 200.0, 1)
    put('coast_rocks_05', -2.6, 6.2, 0.55, 0.8, None, 1)
    put('coast_rocks_05', 3.1, 11.5, 0.6, 1.0, None, 1)
    put('boulder_01', -1.05, 3.3, float(ground_height(np.float32(-1.05), np.float32(3.3), water)[0]) - water + 0.42, 0.7, None, 1)
    put('boulder_01', 1.35, 3.8, float(ground_height(np.float32(1.35), np.float32(3.8), water)[0]) - water + 0.5, 0.85, None, 1)
    put('sand_rocks_small_01', 0.6, 5.3, 0.16, 1.0, None, 1)
    put('sand_rocks_small_01', -4.0, 4.9, 0.2, 1.0, None, 2)
    put('sand_rocks_small_01', 5.0, 4.6, 0.2, 1.0, None, 2)
    # A reef running out on the left, another farther off on the right.
    put('coast_rocks_03', -13.5, 26.0, 1.25, 1.0, 35.0, 1)
    put('coast_rocks_03', 19.0, 44.0, 1.0, 0.9, 170.0, 2)
    # Single boulders in the shallows between, thinning out with distance.
    k = 0
    while k < 16:
        y = float(6.0 + rng.gamma(2.0, 9.0))
        x = float(rng.uniform(-1.0, 1.0) * (y * 0.44 + 3.0))
        if y > 75.0 or abs(x) < 0.14 * y + 0.8:
            continue
        aid = ('boulder_01', 'coast_rocks_05')[int(rng.integers(0, 2))]
        put(aid, x, y, float(rng.uniform(0.2, 0.6)), float(rng.uniform(0.5, 1.1)), None, 2)
        k += 1
    # Skerries: low reefs far out, snow on their backs. (Never as high as the eye: they stay under the horizon.)
    put('coast_rocks_01', -52.0, 150.0, 1.05, 0.55, 80.0, 3)
    put('coast_rocks_02', 46.0, 118.0, 1.2, 0.5, 300.0, 3)
    put('coast_rocks_01', 30.0, 260.0, 1.2, 0.6, 200.0, 3)
    put('coast_rocks_02', -18.0, 330.0, 1.3, 0.7, 120.0, 3)
    bpy.context.view_layer.update()
    snow_caps(obs, water, rng)


def build():
    # For looking closely while working: NOOR_BAND=1 renders only the part of the picture the phone shows whole
    # (16 degrees over the horizon to 11 under it), NOOR_PCT its size in per cent of the full picture.
    r = bpy.context.scene.render
    if os.environ.get('NOOR_PCT'):
        r.resolution_percentage = int(os.environ['NOOR_PCT'])
    if os.environ.get('NOOR_SPP'):
        bpy.context.scene.cycles.samples = int(os.environ['NOOR_SPP'])
    if os.environ.get('NOOR_BAND'):
        r.use_border, r.use_crop_to_border = True, True
        r.border_min_x, r.border_max_x, r.border_min_y, r.border_max_y = 0.0, 1.0, 0.50, 0.75
    land = prep.Land(WORK, *prep.REGIONS[REGION])
    sectors = SECTORS
    if os.environ.get('NOOR_SECTORS'):                 # (trying other views while composing)
        import json
        sectors = json.load(open(os.environ['NOOR_SECTORS']))
    if os.environ.get('NOOR_NO_MOUNTAINS'):
        sectors = []
    for s in sectors:
        prep.fetch_sector(FINE, REGION, s['x'], s['y'], s['bearing'], s['mirror'], s['az'], s['near'], s['fine'])
    fine = prep.Fine(FINE)
    mat = mountain_material()
    for s in sectors:
        sector(land, fine, s, mat)
    bed()
    if not os.environ.get('NOOR_NO_SHORE'):
        shore(WATER, EYE)


# How hard each packed picture is squeezed (WebP quality): the colour picture least, the pictures of light most (they
# are soft, and nineteen of them are what a scenery weighs).
QUALITY = {'a.webp': 88, 'k.webp': 76, 'm.webp': 84, 's': int(os.environ.get('NOOR_QUALITY', 68))}


def squeeze():
    save = bake.save8

    def save8(path, arr, quality=82):
        name = os.path.basename(path)
        return save(path, arr, quality=QUALITY.get(name, QUALITY['s'] if name.startswith('s_') else quality))

    bake.save8 = save8


if __name__ == '__main__':
    squeeze()
    bake.run(build, (0.0, 0.0, EYE), aovs=('mask',), water_level=WATER, exposure=float(os.environ.get('NOOR_EXPOSURE', -3.0)))
