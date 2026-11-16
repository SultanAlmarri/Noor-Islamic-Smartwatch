"""The Red Rock scenery: Cathedral Rock over Oak Creek, from the slabs below Red Rock Crossing (Sedona, Arizona).

A real place. We stand on a slab of red sandstone at the edge of a long pool of the creek and look east-south-east up
its valley: the pool before us, a bar of cobbles and slickrock across it, the creek's cottonwoods, sycamores and
willows, and over them the red skirts and the domes and spires of Cathedral Rock, 1.4 km off and 300 m above the
water. The land is the USGS lidar elevation model at one metre, as it is (canyon_terrain.py): no rock is shaped here
and none is cut. Its surfaces are photogrammetry of real rock and ground (Poly Haven, CC0), tinted to the red of
the Schnebly Hill sandstone. The trees are grown branch by branch (broadleaf.py), because no scan of a cottonwood
exists; they stand where such trees stand: along the water and on the creek's flood plain. Every source and
licence: tools/scenery/sources/canyon.md.

    blender -b --factory-startup -t 3 -P tools/scenery/canyon.py -- preview <out dir> [azimuth elevation]
    blender -b --factory-startup -t 3 -P tools/scenery/canyon.py -- bakepart <out dir> 1 3      (then 2 3, 3 3)
    blender -b --factory-startup -t 3 -P tools/scenery/canyon.py -- packparts <out dir>
    NOOR_SEASON=autumn blender -b --factory-startup -t 3 -P tools/scenery/canyon.py -- colour <out dir>
    NOOR_SEASON=autumn blender -b --factory-startup -t 3 -P tools/scenery/canyon.py -- colourpack <out dir>

To make it again on a fresh machine (NoorTools = ~/ProjectMinhad/NoorTools, or NOOR_TOOLS):
  1. Blender 4.5 LTS; Python 3 with numpy and tifffile (pip install tifffile imagecodecs).
  2. The two USGS tiles into NoorTools/assets/usgs (the curl lines are in canyon_dem.py), then
     `python3 tools/scenery/canyon_dem.py` (writes NoorTools/work/canyon/cathedral_*.npy).
  3. The scans: `python3 tools/scenery/polyhaven_get.py 2k @canyon` (into NoorTools/assets/polyhaven).
  4. `preview` and `look` to see it. The summer set: `bakepart <out> 1 3`, `2 3`, `3 3`, then `packparts <out>`
     (on a machine with memory to spare, `bake <out>` does it in one run). Then, for each of NOOR_SEASON=autumn,
     winter, spring: `colour <out>` and `colourpack <out>`: they add a_<season>.webp and k_<season>.webp to
     <out>/land. NOOR_DICE=3 for these keeps the process near 4 GB.
  5. Copy <out>/land/* to app/src/main/assets/sky2/land/canyon/.

Environment: NOOR_SEASON (summer, autumn, winter, spring: what grows and lies there; the base set is summer),
NOOR_STAGE ('land' the ground alone, 'trees' ground and trees, 'all'), NOOR_SAMPLES (samples of a preview),
NOOR_CROP (left,right,top,bottom as fractions: only that part of the picture), NOOR_PCT (a smaller picture,
per cent: a `colour` trial beside a `bakefast` needs 50), NOOR_DICE (pixels a polygon of
the near ground may span: 1.5), NOOR_BAKE_SAMPLES, NOOR_BAKE_QUALITY.

Memory: Blender's own "Peak" in its log is not what the process holds (Cycles' own copy of the scene comes on top:
about 2.5 GB here). Watch the process itself when the machine is shared.
"""
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake                       # noqa: E402
import broadleaf                  # noqa: E402
import canyon_terrain as ct       # noqa: E402

EYE = 2.0                         # metres above the pool's water (we stand on a slab a hand above it)
WATER = 0.0                       # the pool's surface (scene z)
SEASON = os.environ.get('NOOR_SEASON', 'summer')
STAGE = os.environ.get('NOOR_STAGE', 'all')
MODE, OUT = bake.args()[0], bake.args()[1]
SCANS = os.environ.get('NOOR_POLYHAVEN', os.path.join(ct.TOOLS, 'assets', 'polyhaven'))
NEAR = ct.near_field()
NEAR_END = 140.0                  # metres: where the finely worked ground hands over to the far ground
VIEW = 24.8                       # degrees to either side that the ground of the picture is laid out to


def smooth(a, b, x):
    t = np.clip((np.asarray(x, np.float64) - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def land_height(x, y):
    return ct.land_height(x, y, near=NEAR)


def slope_at(x, y, e=1.5):
    return np.hypot(land_height(x + e, y) - land_height(x - e, y), land_height(x, y + e) - land_height(x, y - e)) / (2 * e)


def value_noise(x, y, cell, seed):
    """Smooth random values 0..1 at positions (a lattice of random numbers `cell` metres apart)."""
    gx, gy = np.asarray(x, np.float64) / cell, np.asarray(y, np.float64) / cell
    ix, iy = np.floor(gx).astype(np.int64), np.floor(gy).astype(np.int64)
    fx, fy = gx - ix, gy - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

    def h(i, j):
        n = (i * 374761393 + j * 668265263 + seed * 1442695041) & 0xffffffff
        n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
        return ((n ^ (n >> 16)) & 0xffff) / 65535.0

    return (h(ix, iy) * (1 - fx) + h(ix + 1, iy) * fx) * (1 - fy) + (h(ix, iy + 1) * (1 - fx) + h(ix + 1, iy + 1) * fx) * fy


# ---- Small helpers ----------------------------------------------------------------------------------------------

def new_material(name):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    for nd in list(nt.nodes):
        nt.nodes.remove(nd)
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    bsdf = nt.nodes.new('ShaderNodeBsdfPrincipled')
    nt.links.new(bsdf.outputs['BSDF'], out.inputs['Surface'])
    return mat, nt, bsdf, out


class Nodes:
    """Short hands for building a node tree."""

    def __init__(self, nt):
        self.nt, self.N, self.L = nt, nt.nodes, nt.links

    def link(self, a, b):
        self.L.new(a, b)

    def math(self, op, a, b=None, c=None, clamp=False):
        n = self.N.new('ShaderNodeMath')
        n.operation = op
        n.use_clamp = clamp
        for i, v in enumerate((a, b, c)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = float(v)
            else:
                self.L.new(v, n.inputs[i])
        return n.outputs[0]

    def mul(self, a, b):
        return self.math('MULTIPLY', a, b)

    def ramp(self, x, a, b, lo=0.0, hi=1.0):
        """Smooth step of x from a to b, mapped to lo..hi."""
        n = self.N.new('ShaderNodeMapRange')
        n.interpolation_type = 'SMOOTHSTEP'
        n.inputs['From Min'].default_value = a
        n.inputs['From Max'].default_value = b
        n.inputs['To Min'].default_value = lo
        n.inputs['To Max'].default_value = hi
        self.L.new(x, n.inputs['Value'])
        return n.outputs[0]

    def mix(self, f, a, b, blend='MIX'):
        """Colours a and b mixed by f (sockets, numbers or tuples)."""
        n = self.N.new('ShaderNodeMix')
        n.data_type = 'RGBA'
        n.blend_type = blend
        socks = {'Factor': [s for s in n.inputs if s.name == 'Factor' and s.type == 'VALUE'][0],
                 'A': [s for s in n.inputs if s.name == 'A' and s.type == 'RGBA'][0],
                 'B': [s for s in n.inputs if s.name == 'B' and s.type == 'RGBA'][0]}
        for key, v in (('Factor', f), ('A', a), ('B', b)):
            if isinstance(v, (int, float)):
                socks[key].default_value = float(v)
            elif isinstance(v, tuple):
                socks[key].default_value = (v[0], v[1], v[2], 1.0)
            else:
                self.L.new(v, socks[key])
        return [s for s in n.outputs if s.type == 'RGBA'][0]

    def tint(self, a, b):
        """Colour a multiplied by colour b."""
        return self.mix(1.0, a, b, 'MULTIPLY')

    def grey(self, v):
        """A value as a colour."""
        n = self.N.new('ShaderNodeCombineColor')
        for key in ('Red', 'Green', 'Blue'):
            self.L.new(v, n.inputs[key])
        return n.outputs[0]

    def bw(self, c):
        n = self.N.new('ShaderNodeRGBToBW')
        self.L.new(c, n.inputs[0])
        return n.outputs[0]

    def noise(self, vec, scale, detail=3.0, rough=0.5, dim='3D'):
        n = self.N.new('ShaderNodeTexNoise')
        n.noise_dimensions = dim
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        self.L.new(vec, n.inputs['W' if dim == '1D' else 'Vector'])
        return n.outputs['Fac']

    def mapping(self, vec, scale, turn=0.0, shift=(0.0, 0.0, 0.0)):
        n = self.N.new('ShaderNodeMapping')
        n.inputs['Scale'].default_value = scale if isinstance(scale, tuple) else (scale, scale, scale)
        n.inputs['Rotation'].default_value = (0.0, 0.0, turn)
        n.inputs['Location'].default_value = shift
        self.L.new(vec, n.inputs['Vector'])
        return n.outputs[0]


def mesh_object(name, co, faces, smooth_shade=True, col=None):
    """An object from vertex positions [n, 3] and quads or triangles [m, 3 or 4]; `col` gives every vertex a colour
    (attribute 'col')."""
    co = np.asarray(co, np.float32)
    faces = np.asarray(faces, np.int32)
    k = faces.shape[1]
    me = bpy.data.meshes.new(name)
    me.vertices.add(len(co))
    me.vertices.foreach_set('co', co.ravel())
    me.loops.add(faces.size)
    me.loops.foreach_set('vertex_index', faces.ravel())
    me.polygons.add(len(faces))
    me.polygons.foreach_set('loop_start', np.arange(0, faces.size, k, dtype=np.int32))
    me.polygons.foreach_set('loop_total', np.full(len(faces), k, np.int32))
    me.polygons.foreach_set('use_smooth', np.full(len(faces), smooth_shade, bool))
    me.update(calc_edges=True)
    if col is not None:
        attr = me.color_attributes.new('col', 'FLOAT_COLOR', 'POINT')
        rgba = np.ones((len(co), 4), np.float32)
        rgba[:, :3] = col
        attr.data.foreach_set('color', rgba.ravel())
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def fan(name, bearings, dists, height):
    """A sheet of ground laid out as the camera sees it: along `bearings` (degrees) and `dists` (metres along the
    view's middle) from the camera, lifted by height(x, y)."""
    t = np.tan(np.radians(np.asarray(bearings, np.float64)))
    d = np.asarray(dists, np.float64)
    wide = np.abs(np.asarray(bearings)).max() > 80.0
    if wide:                                    # all round us: by true bearing and distance
        b = np.radians(np.asarray(bearings, np.float64))
        x = np.sin(b)[None, :] * d[:, None]
        y = np.cos(b)[None, :] * d[:, None]
    else:                                       # the picture's own columns: every one a straight line
        x = t[None, :] * d[:, None]
        y = np.repeat(d[:, None], len(t), axis=1)
    z = height(x.ravel(), y.ravel()).reshape(x.shape)
    co = np.stack([x, y, z], -1).reshape(-1, 3)
    nd, nb = len(d), len(t)
    i = np.arange((nd - 1) * (nb - 1))
    r, c = i // (nb - 1), i % (nb - 1)
    a = r * nb + c
    return mesh_object(name, co, np.stack([a, a + 1, a + nb + 1, a + nb], -1))


# ---- Scans -----------------------------------------------------------------------------------------------------

def scan_objects(aid, want):
    """Objects of a scanned asset whose names pass `want` (a function of the name), linked into the scene."""
    d = os.path.join(SCANS, aid)
    blend = [f for f in os.listdir(d) if f.endswith('.blend')][0]
    with bpy.data.libraries.load(os.path.join(d, blend)) as (src, dst):
        dst.objects = [n for n in src.objects if want(n)]
    out = []
    for ob in dst.objects:
        if ob is None or ob.type != 'MESH':
            continue
        bpy.context.scene.collection.objects.link(ob)
        ob.location = (0.0, 0.0, 0.0)
        out.append(ob)
    return out


_MEAN = {}


def scan_image(aid, kind, colour=False):
    d = os.path.join(SCANS, aid, 'textures')
    name = [f for f in os.listdir(d) if '_%s' % kind in f][0]
    img = bpy.data.images.load(os.path.join(d, name), check_existing=True)
    if not colour:
        img.colorspace_settings.name = 'Non-Color'
    return img


def scan_mean(aid):
    """The mean colour of a scan's colour picture (linear), so that a scan can lend its light and dark and its
    detail to a colour of ours."""
    if aid not in _MEAN:
        # (From a small copy: reading the picture itself would keep a full-size float copy of it in memory. The
        # picture's values are sRGB: made linear here, as the renderer reads them.)
        small = scan_image(aid, 'diff', True).copy()
        small.scale(64, 64)
        px = np.empty(64 * 64 * 4, np.float32)
        small.pixels.foreach_get(px)
        bpy.data.images.remove(small)
        c = px.reshape(-1, 4)[:, :3]
        lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
        _MEAN[aid] = tuple(float(v) for v in lin.mean(axis=0))
    return _MEAN[aid]


def scan_texture(g, aid, kind, vector, colour=False, box=False):
    node = g.N.new('ShaderNodeTexImage')
    node.image = scan_image(aid, kind, colour)
    if box:
        node.projection = 'BOX'
        node.projection_blend = 0.25
    g.link(vector, node.inputs['Vector'])
    return node.outputs['Color']


def detail_of(g, aid, vector, box=False, amount=1.0):
    """A scan's colour picture divided by its own mean: 1 on average, with all its light, dark and tints."""
    c = scan_texture(g, aid, 'diff', vector, True, box)
    m = scan_mean(aid)
    d = g.N.new('ShaderNodeVectorMath')
    d.operation = 'DIVIDE'
    g.link(c, d.inputs[0])
    d.inputs[1].default_value = m
    return g.mix(amount, (1.0, 1.0, 1.0), d.outputs[0]) if amount < 1.0 else d.outputs[0]


def stand(name, child, x, y, z, size, yaw):
    """Stands a copy of `child` at every (x, y, z), `size` times its own size and turned by `yaw`: one small square
    per copy, and the renderer puts the copy on it."""
    corners = np.array([[-0.5, -0.5], [0.5, -0.5], [0.5, 0.5], [-0.5, 0.5]])
    ca, sa = np.cos(yaw)[:, None], np.sin(yaw)[:, None]
    px = x[:, None] + (corners[None, :, 0] * ca - corners[None, :, 1] * sa) * size[:, None]
    py = y[:, None] + (corners[None, :, 0] * sa + corners[None, :, 1] * ca) * size[:, None]
    co = np.stack([px, py, np.repeat(z[:, None], 4, axis=1)], -1).reshape(-1, 3)
    holder = mesh_object(name, co, np.arange(len(x) * 4).reshape(-1, 4), smooth_shade=False)
    child.location = (0.0, 0.0, 0.0)
    child.parent = holder
    holder.instance_type = 'FACES'
    holder.use_instance_faces_scale = True
    holder.instance_faces_scale = 1.0
    holder.show_instancer_for_render = False
    holder.show_instancer_for_viewport = False
    return holder


# ---- The land ---------------------------------------------------------------------------------------------------

# The red of the place (linear): the Schnebly Hill sandstone of the domes and the Hermit beds of the skirts.
RED = (0.420, 0.076, 0.032)
ORANGE = (0.500, 0.112, 0.042)
DARK = (0.250, 0.052, 0.026)
BUFF = (0.560, 0.215, 0.112)
SOIL = (0.290, 0.070, 0.032)
SNOW = (0.80, 0.82, 0.85)


def snow_lies(g, normal_z, pos, amount):
    """Where a dusting of snow lies (winter): on what is level enough to hold it, thinner on the sunny side."""
    patch = g.ramp(g.noise(pos, 1.0 / 9.0, 4.0, 0.6), 0.38, 0.70)
    lie = g.mul(g.ramp(normal_z, 0.55, 0.86), g.math('ADD', 0.30, g.mul(patch, 0.70)))
    return g.mul(lie, amount)


def land_material():
    """Rock, soil and the creek's bed, from the shape of the land itself: bare bedded sandstone where it is steep,
    red soil and rubble where it is not, slickrock and cobbles along the water."""
    mat, nt, bsdf, outn = new_material('Land')
    g = Nodes(nt)
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    pos = geo.outputs['Position']
    psep = nt.nodes.new('ShaderNodeSeparateXYZ')
    g.link(pos, psep.inputs[0])
    z = psep.outputs['Z']
    nsep = nt.nodes.new('ShaderNodeSeparateXYZ')
    g.link(geo.outputs['True Normal'], nsep.inputs[0])
    nz = nsep.outputs['Z']
    dn = nt.nodes.new('ShaderNodeVectorMath')
    dn.operation = 'LENGTH'
    g.link(pos, dn.inputs[0])
    dist = dn.outputs['Value']
    flat = g.mapping(pos, (1.0, 1.0, 0.0))                    # the ground seen from above
    far = g.ramp(dist, 90.0, 260.0)                           # 0 by the creek .. 1 beyond its banks
    steep = g.ramp(nz, 0.80, 0.56)                            # 1 on walls and ledges

    # ---- The rock of the walls: beds upon beds, each its own red
    warp = g.noise(pos, 1.0 / 140.0, 3.0, 0.5)
    zw = g.math('ADD', z, g.mul(warp, 16.0))                  # the beds dip and wander a little
    b1 = g.noise(g.math('MULTIPLY', zw, 1.0 / 30.0), 1.0, 2.0, 0.5, '1D')
    b2 = g.noise(g.math('MULTIPLY', zw, 1.0 / 7.0), 1.0, 2.0, 0.5, '1D')
    b3 = g.noise(g.math('MULTIPLY', zw, 1.0 / 1.7), 1.0, 1.0, 0.5, '1D')
    # (The skirts are thin beds, ledge upon ledge, and show their bands; the domes are massive sandstone, where a
    # band is a faint parting and what the eye reads is the joints that cut them from top to bottom.)
    banded = g.ramp(z, 215.0, 180.0, 0.45, 1.0)
    rock = g.mix(g.ramp(b1, 0.36, 0.64), RED, ORANGE)
    rock = g.mix(g.mul(g.mul(g.ramp(b2, 0.52, 0.70), 0.55), banded), rock, DARK)
    rock = g.mix(g.mul(g.mul(g.ramp(b3, 0.58, 0.74), 0.34), banded), rock, BUFF)
    b4 = g.noise(g.math('MULTIPLY', zw, 1.0 / 3.6), 7.3, 1.0, 0.5, '1D')
    rock = g.mix(g.mul(g.mul(g.ramp(b4, 0.62, 0.74), 0.34), banded), rock, g.tint(rock, (0.55, 0.50, 0.50)))
    # The domes are paler at their very tops (the creamy upper beds).
    cap = g.mul(g.ramp(z, 262.0, 298.0), g.ramp(g.noise(pos, 1.0 / 38.0, 3.0, 0.6), 0.25, 0.75, 0.25, 0.60))
    rock = g.mix(cap, rock, BUFF)
    # Joints: upright clefts, some metres apart, dark with shade and varnish.
    j1 = g.noise(g.mapping(pos, (1.0 / 11.0, 1.0 / 11.0, 1.0 / 150.0)), 1.0, 2.0, 0.5)
    j2 = g.noise(g.mapping(pos, (1.0 / 19.0, 1.0 / 19.0, 1.0 / 260.0), shift=(3.1, 1.7, 0.0)), 1.0, 2.0, 0.5)
    cleft = g.math('MAXIMUM', g.ramp(g.math('ABSOLUTE', g.math('SUBTRACT', j1, 0.5)), 0.028, 0.004),
                   g.ramp(g.math('ABSOLUTE', g.math('SUBTRACT', j2, 0.5)), 0.022, 0.003))
    cleft = g.mul(cleft, g.ramp(nz, 0.70, 0.45))
    rock = g.mix(g.mul(cleft, 0.45), rock, g.tint(rock, (0.30, 0.26, 0.26)))
    # Streaks of desert varnish down the walls.
    streak = g.noise(g.mapping(pos, (1.0 / 9.0, 1.0 / 9.0, 1.0 / 140.0)), 1.0, 4.0, 0.62)
    rock = g.mix(g.mul(g.ramp(streak, 0.52, 0.74), 0.50), rock, g.tint(rock, (0.42, 0.36, 0.36)))
    # Scanned rock gives it its light and dark and its small things: thin beds, and weathered faces.
    beds = detail_of(g, 'cliff_side', g.mapping(pos, 1.0 / 23.0), box=True)
    face = detail_of(g, 'rock_face', g.mapping(pos, 1.0 / 47.0, shift=(0.31, 0.17, 0.4)), box=True)
    which = g.ramp(g.noise(pos, 1.0 / 75.0, 2.0, 0.5), 0.40, 0.60)
    rock = g.tint(rock, g.mix(0.85, (1.0, 1.0, 1.0), g.mix(which, beds, face)))

    # ---- What is not steep: red soil and rubble, greyed with brush and grass where the slope is gentle
    soil = g.tint(SOIL, detail_of(g, 'red_laterite_soil_stones', g.mapping(flat, 1.0 / 38.0)))
    soil = g.tint(soil, g.mix(0.6, (1.0, 1.0, 1.0), detail_of(g, 'red_mud_stones', g.mapping(flat, 1.0 / 9.0, 0.6))))
    brush = g.mul(g.ramp(nz, 0.80, 0.93), g.ramp(g.noise(flat, 1.0 / 45.0, 4.0, 0.62), 0.30, 0.58))
    green = {'summer': (0.062, 0.078, 0.032), 'autumn': (0.130, 0.105, 0.048), 'winter': (0.120, 0.100, 0.060),
             'spring': (0.058, 0.098, 0.030)}[SEASON]
    soil = g.mix(g.mul(brush, 0.80), soil, green)
    land = g.mix(steep, soil, rock)

    # ---- Along the creek: slickrock, cobbles, sand, leaf litter under the trees
    slab = g.tint(detail_of(g, 'sandstone_cracks', g.mapping(flat, 1.0 / 5.3, 0.3)),
                  g.mix(0.6, (1.0, 1.0, 1.0), detail_of(g, 'sandstone_cracks', g.mapping(flat, 1.0 / 17.0, 1.9, (0.4, 0.7, 0.0)))))
    slab = g.tint(slab, g.mix(g.ramp(g.noise(flat, 1.0 / 6.0, 3.0, 0.55), 0.3, 0.7), (0.360, 0.118, 0.056), (0.460, 0.180, 0.090)))
    slab = g.mix(g.ramp(g.noise(flat, 1.0 / 2.2, 4.0, 0.65), 0.58, 0.76, 0.0, 0.40), slab, g.tint(slab, (1.15, 1.30, 1.45)))      # bleached patches
    slab = g.mix(g.ramp(g.noise(flat, 1.0 / 4.5, 4.0, 0.6), 0.52, 0.70, 0.0, 0.45), slab, g.tint(slab, (0.62, 0.56, 0.54)))        # water stains
    ledge = g.tint((0.36, 0.13, 0.06), detail_of(g, 'dry_riverbed_rock', g.mapping(pos, 1.0 / 3.1), box=True))
    slab = g.mix(g.ramp(nz, 0.93, 0.78), slab, ledge)
    cobble = g.tint(scan_texture(g, 'river_small_rocks', 'diff', g.mapping(flat, 1.0 / 2.7), True), (1.10, 0.92, 0.80))
    stony = g.mul(g.ramp(g.noise(flat, 1.0 / 16.0, 3.0, 0.6), 0.47, 0.60), g.ramp(z, 1.3, 0.35))
    stony = g.mul(stony, g.ramp(nz, 0.90, 0.97))
    bank = g.mix(stony, slab, cobble)
    litter_c = {'summer': (1.0, 1.0, 1.0), 'autumn': (1.35, 1.05, 0.55), 'winter': (0.95, 0.85, 0.70), 'spring': (0.95, 1.05, 0.85)}[SEASON]
    litter = g.tint(scan_texture(g, 'forest_leaves_02', 'diff', g.mapping(flat, 1.0 / 2.9, 1.1), True), litter_c)
    dirt = g.tint((0.300, 0.140, 0.080), detail_of(g, 'red_dirt_mud_01', g.mapping(flat, 1.0 / 3.3)))      # the flood plain's sand
    floor = g.mix(g.ramp(g.noise(flat, 1.0 / 7.0, 3.0, 0.6), 0.40, 0.62), dirt, litter)
    turf = {'summer': (0.105, 0.135, 0.046), 'autumn': (0.250, 0.205, 0.090), 'winter': (0.270, 0.225, 0.120), 'spring': (0.090, 0.155, 0.040)}[SEASON]
    turf = g.tint(turf, g.grey(g.ramp(g.noise(flat, 1.0 / 0.7, 3.0, 0.7), 0.25, 0.75, 0.6, 1.4)))
    floor = g.mix(g.ramp(g.noise(flat, 1.0 / 12.0, 4.0, 0.65), 0.30, 0.50, 0.0, 0.92), floor, turf)
    up = g.mul(g.ramp(z, 0.40, 1.1), g.ramp(g.noise(flat, 1.0 / 23.0, 3.0, 0.6), 0.16, 0.40))
    bank = g.mix(g.mul(up, g.ramp(nz, 0.86, 0.95)), bank, floor)
    # Wet at the water's edge, dark and a little green under it.
    bank = g.mix(g.ramp(z, 0.14, 0.02), bank, g.tint(bank, (0.50, 0.47, 0.44)))
    bank = g.mix(g.ramp(z, -0.02, -0.30), bank, g.tint(bank, (0.62, 0.70, 0.52)))
    land = g.mix(far, bank, land)

    snow = None
    if SEASON == 'winter':
        # A dusting on every ledge and level place, less where the low Sun finds it; none on the wet rock.
        snow = g.mul(snow_lies(g, nz, pos, 0.9), g.mul(g.ramp(z, 0.10, 0.45), g.ramp(z, 0.0, 130.0, 0.55, 1.0)))      # (more of it higher up)
        land = g.mix(snow, land, SNOW)
    g.link(land, bsdf.inputs['Base Color'])
    rough = g.ramp(z, 0.02, 0.16, 0.30, 0.86)
    g.link(rough, bsdf.inputs['Roughness'])
    bsdf.inputs['Specular IOR Level'].default_value = 0.25

    # ---- Relief: the beds as ledges on the walls; the scans' own relief near us (real displacement)
    far_h = g.math('ADD', g.mul(g.bw(scan_texture(g, 'cliff_side', 'disp', g.mapping(pos, 1.0 / 23.0), box=True)), 1.6),
                   g.math('ADD', g.math('ADD', g.mul(b3, 1.5), g.mul(b2, 2.0)), g.math('SUBTRACT', g.mul(streak, 2.6), g.mul(cleft, 6.0))))
    bump = nt.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 1.0
    bump.inputs['Distance'].default_value = 1.0
    g.link(g.mul(far_h, g.mul(far, g.ramp(nz, 0.9, 0.6, 0.25, 1.0))), bump.inputs['Height'])
    g.link(bump.outputs['Normal'], bsdf.inputs['Normal'])
    near_h = g.mul(g.bw(scan_texture(g, 'sandstone_cracks', 'disp', g.mapping(flat, 1.0 / 5.3, 0.3))), 0.035)
    near_h = g.math('ADD', near_h, g.mul(g.bw(scan_texture(g, 'sandstone_cracks', 'disp', g.mapping(flat, 1.0 / 17.0, 1.9, (0.4, 0.7, 0.0)))), 0.07))
    cob_h = g.mul(g.bw(scan_texture(g, 'river_small_rocks', 'disp', g.mapping(flat, 1.0 / 2.7))), 0.10)
    near_h = g.math('ADD', g.mul(near_h, g.math('SUBTRACT', 1.0, stony)), g.mul(cob_h, stony))
    near_h = g.mul(g.math('SUBTRACT', near_h, 0.05), g.ramp(dist, NEAR_END - 15.0, NEAR_END - 60.0))
    disp = nt.nodes.new('ShaderNodeDisplacement')
    disp.inputs['Midlevel'].default_value = 0.0
    disp.inputs['Scale'].default_value = 1.0
    g.link(near_h, disp.inputs['Height'])
    g.link(disp.outputs['Displacement'], outn.inputs['Displacement'])
    mat.displacement_method = 'DISPLACEMENT'
    # Its place in the mask: rock does not move; open ground takes snow; the pool is water.
    bake.add_mask(mat, wind=0.0, snow=g.ramp(nz, 0.60, 0.88), water_level=WATER)
    return mat


def ground():
    mat = land_material()
    # Near us the renderer dices the ground to the size of a pixel by itself (and lifts it by the scans' relief): a
    # coarse sheet is enough. From there to behind Cathedral Rock, a point of the lidar for every pixel across and
    # every metre and a quarter in depth. Beyond, the ten-metre model: coarser again. (Each sheet starts a little
    # before the last one ends, so no seam shows the sky; all of it is one surface.)
    near = fan('LandNear', np.linspace(-VIEW, VIEW, 621), np.geomspace(0.7, NEAR_END + 1.0, 430), land_height)
    bake.adaptive(near, float(os.environ.get('NOOR_DICE', 1.5)))
    far = fan('Land', np.linspace(-VIEW, VIEW, 1241),
              np.concatenate([np.linspace(NEAR_END, 620.0, 380), np.linspace(620.0, 1950.0, 1065)[1:]]), land_height)
    beyond = fan('LandBeyond', np.linspace(-VIEW, VIEW, 621), np.geomspace(1900.0, 45000.0, 420), land_height)
    # What the camera does not see, but which shades and lights what it does: the land to either side and behind.
    rest = np.geomspace(0.7, 16000.0, 260)
    left = fan('LandLeft', np.linspace(-180.0, -VIEW, 170), rest, land_height)
    right = fan('LandRight', np.linspace(VIEW, 180.0, 170), rest, land_height)
    for ob in (near, far, beyond, left, right):
        ob.data.materials.append(mat)
    return near, far


def build():
    if os.environ.get('NOOR_SAMPLES'):          # quicker, rougher looks while the scene is being made
        bpy.context.scene.cycles.samples = int(os.environ['NOOR_SAMPLES'])
    if os.environ.get('NOOR_PCT'):              # a smaller picture (per cent of the full size), for trials
        bpy.context.scene.render.resolution_percentage = int(os.environ['NOOR_PCT'])
    if os.environ.get('NOOR_CROP'):             # only a part of the picture: left,right,top,bottom as fractions
        x0, x1, y0, y1 = (float(v) for v in os.environ['NOOR_CROP'].split(','))
        r = bpy.context.scene.render
        r.use_border, r.use_crop_to_border = True, True
        r.border_min_x, r.border_max_x, r.border_min_y, r.border_max_y = x0, x1, 1.0 - y1, 1.0 - y0
    ground()
    if STAGE != 'land':
        import canyon_plants
        canyon_plants.grow_all(sys.modules[__name__])


# (bake.run fixes a bake's samples and the pictures' quality; both can be set from outside: NOOR_BAKE_SAMPLES is
# the samples of the whole bake (a part takes its share), NOOR_COLOUR_SAMPLES those of a season's colour pass,
# NOOR_BAKE_QUALITY the WebP quality.)
_render, _pack, _crender, _cpack = bake.bake_render, bake.bake_pack, bake.colour_render, bake.colour_pack
SAMPLES = int(os.environ.get('NOOR_BAKE_SAMPLES', 300))
QUALITY = int(os.environ.get('NOOR_BAKE_QUALITY', 62))


def _bake_render(out, yaw=0.0, samples=512, aovs=(), only=None, layers=None):
    share = 1.0 if only is None else len(only) / 20.0
    return _render(out, yaw=yaw, samples=max(int(round(SAMPLES * share)), 8), aovs=aovs, only=only, layers=layers)


def _bake_pack(src, dst, suns, eye, aovs=(), quality=80, frame='0001', water=None, extra=None):
    return _pack(src, dst, suns, eye, aovs=aovs, quality=QUALITY, frame=frame, water=water, extra=extra)


def _colour_render(out, yaw=0.0, samples=256):
    return _crender(out, yaw=yaw, samples=int(os.environ.get('NOOR_COLOUR_SAMPLES', 128)))


def _colour_pack(src, dst, season, quality=80, frame='0001', extra=None):
    return _cpack(src, dst, season, quality=QUALITY, frame=frame, extra=extra)


bake.bake_render, bake.bake_pack, bake.colour_render, bake.colour_pack = _bake_render, _bake_pack, _colour_render, _colour_pack

if __name__ == '__main__':
    # The base set is summer; winter's pictures carry their own snow (the renderer lays none of its own then).
    if MODE in ('bake', 'bakefast', 'bakepart', 'pack', 'packparts') and SEASON != 'summer':
        raise SystemExit('the base set is baked in summer: unset NOOR_SEASON (other seasons are made with `colour`)')
    bake.run(build, (0.0, 0.0, EYE), aovs=('mask',), water_level=WATER, exposure=-2.3, season=SEASON,
             extra={'season': 'summer'} if MODE in ('bake', 'bakefast', 'pack', 'packparts') else
                   ({'winterBaked': True} if SEASON == 'winter' else None))
