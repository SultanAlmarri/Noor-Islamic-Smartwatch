"""The Meadow scenery: green hills and oaks in spring, a lake winding between them far below.

A real place: the Diablo Range of California above Sunol, looking east-south-east over Calaveras Reservoir to the
ridges that climb towards Mount Hamilton. The land is the USGS elevation model as it is (meadow_terrain.py): no hill
is shaped here and none is cut. What grows near enough to be told is photogrammetry of real plants and stones (Poly
Haven, CC0: see tools/scenery/sources/meadow.md): the grass, the flowers, the small gnarled trees, the rocks, the
ground under them, the bark of the oaks. The oaks themselves are grown branch by branch (broadleaf.py), because no
scan of a broad oak exists; they stand where oaks stand on such hills: on the slopes that face north and along the
hollows where water runs.

    blender -b --factory-startup -P tools/scenery/meadow.py -- preview <out dir> [azimuth elevation]

The scans are expected in %USERPROFILE%/ProjectMinhad/NoorTools/assets/polyhaven/<id>/ (the .blend with its textures
folder), as the Poly Haven API serves them; the elevation tile in .../assets/usgs (see meadow_dem.py).

NOOR_STAGE (environment) builds less for quick looks: 'land' the ground alone, 'trees' the ground and the oaks.
NOOR_SAMPLES sets the samples of a preview.
"""
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake                       # noqa: E402
import broadleaf                  # noqa: E402
import hills                      # noqa: E402
import meadow_terrain as mt       # noqa: E402

EYE = 1.7                         # metres above the grass we stand on
STAGE = os.environ.get('NOOR_STAGE', 'all')
MODE = bake.args()[0]
OUT = bake.args()[1]
LAND = mt.land(OUT)
GROUND = float(LAND['ground'])                 # the camera's ground, metres above the sea
SEED = 7
FIELD_FAR = mt.N * mt.CELL - mt.BACK          # how far ahead the worked ground reaches (metres)
FIELD_HALF = mt.N * mt.CELL / 2.0
SCANS = os.environ.get('NOOR_POLYHAVEN', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'polyhaven'))


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

    def mix(self, f, a, b):
        """Colours a and b mixed by f (sockets, numbers or tuples)."""
        n = self.N.new('ShaderNodeMix')
        n.data_type = 'RGBA'
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

    def noise(self, vec, scale, detail=3.0, rough=0.5, dim='3D'):
        n = self.N.new('ShaderNodeTexNoise')
        n.noise_dimensions = dim
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        self.L.new(vec, n.inputs['Vector'])
        return n.outputs['Fac']


def mesh_object(name, co, faces, smooth=True, col=None):
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
    me.polygons.foreach_set('use_smooth', np.full(len(faces), smooth, bool))
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
    """A sheet of ground laid out as the camera sees it: along `bearings` (degrees) and `dists` (metres) from the
    camera, lifted by height(x, y)."""
    b = np.radians(np.asarray(bearings, np.float64))
    d = np.asarray(dists, np.float64)
    x = np.sin(b)[None, :] * d[:, None]
    y = np.cos(b)[None, :] * d[:, None]
    z = height(x.ravel(), y.ravel()).reshape(x.shape)
    co = np.stack([x, y, z], -1).reshape(-1, 3)
    nd, nb = len(d), len(b)
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


def scan_texture(nt, aid, kind, vector, colour=False):
    d = os.path.join(SCANS, aid, 'textures')
    name = [f for f in os.listdir(d) if '_%s_' % kind in f][0]
    node = nt.nodes.new('ShaderNodeTexImage')
    node.image = bpy.data.images.load(os.path.join(d, name), check_existing=True)
    if not colour:
        node.image.colorspace_settings.name = 'Non-Color'
    nt.links.new(vector, node.inputs['Vector'])
    return node


_SEEN = set()


def dress(ob, wind, snow, hue=None):
    """Gives a scan's materials their place in the mask and, for plants, the season: `hue` = (turn of hue, saturation,
    value) makes dry scanned grass the green of spring; each copy differs a little from the next."""
    for mat in ob.data.materials:
        if mat is None or mat.name in _SEEN:
            continue
        _SEEN.add(mat.name)
        nt = mat.node_tree
        if hue is not None:
            for nd in list(nt.nodes):
                if nd.type != 'BSDF_PRINCIPLED' or not nd.inputs['Base Color'].links:
                    continue
                src = nd.inputs['Base Color'].links[0].from_socket
                hs = nt.nodes.new('ShaderNodeHueSaturation')
                hs.inputs['Saturation'].default_value = hue[1]
                info = nt.nodes.new('ShaderNodeObjectInfo')
                var = nt.nodes.new('ShaderNodeMapRange')
                var.inputs['To Min'].default_value = hue[0] - 0.035
                var.inputs['To Max'].default_value = hue[0] + 0.035
                nt.links.new(info.outputs['Random'], var.inputs['Value'])
                nt.links.new(var.outputs[0], hs.inputs['Hue'])
                val = nt.nodes.new('ShaderNodeMapRange')
                val.inputs['To Min'].default_value = hue[2] * 0.8
                val.inputs['To Max'].default_value = hue[2] * 1.2
                nt.links.new(info.outputs['Random'], val.inputs['Value'])
                nt.links.new(val.outputs[0], hs.inputs['Value'])
                nt.links.new(src, hs.inputs['Color'])
                nt.links.new(hs.outputs['Color'], nd.inputs['Base Color'])
        bake.add_mask(mat, wind=wind, snow=snow)


def stand(name, child, x, y, z, size, yaw):
    """Stands a copy of `child` at every (x, y, z), `size` times its own size and turned by `yaw`: one small square
    per copy, and the renderer puts the copy on it."""
    corners = np.array([[-0.5, -0.5], [0.5, -0.5], [0.5, 0.5], [-0.5, 0.5]])
    ca, sa = np.cos(yaw)[:, None], np.sin(yaw)[:, None]
    px = x[:, None] + (corners[None, :, 0] * ca - corners[None, :, 1] * sa) * size[:, None]
    py = y[:, None] + (corners[None, :, 0] * sa + corners[None, :, 1] * ca) * size[:, None]
    co = np.stack([px, py, np.repeat(z[:, None], 4, axis=1)], -1).reshape(-1, 3)
    holder = mesh_object(name, co, np.arange(len(x) * 4).reshape(-1, 4), smooth=False)
    child.location = (0.0, 0.0, 0.0)
    child.parent = holder
    holder.instance_type = 'FACES'
    holder.use_instance_faces_scale = True
    holder.instance_faces_scale = 1.0
    holder.show_instancer_for_render = False
    holder.show_instancer_for_viewport = False
    return holder


# ---- The land ---------------------------------------------------------------------------------------------------

def land_height(x, y):
    """The ground's height anywhere (camera's ground = 0): the near ground's fine grid, running into the elevation
    model itself beyond it (lowered by the Earth's curve)."""
    x = np.asarray(x, np.float64)
    y = np.asarray(y, np.float64)
    near = mt.height_at(LAND, x, y)
    edge = np.maximum(np.maximum(np.abs(x) - (FIELD_HALF - 500.0), np.maximum(y - (FIELD_FAR - 500.0), -y - (mt.BACK - 120.0))), 0.0) / 450.0
    w = mt.smooth(0.0, 1.0, edge)
    if not np.any(w > 0.0):
        return near
    return (near * (1.0 - w) + mt.far_height(x, y, GROUND) * w).astype(np.float32)


def grass_material(out):
    """The grassland as it looks beyond the reach of real blades: every green of a spring hillside."""
    f = LAND
    n = f['z'].shape[0]
    size = n * mt.CELL / mt.UP
    img = bake.float_image('meadow_marks', np.stack([f['crest'], f['wet'], f['lake']], -1), os.path.join(out, 'meadow_marks.exr'))
    mat, nt, bsdf, outn = new_material('Grassland')
    g = Nodes(nt)
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    pos = geo.outputs['Position']
    uv = nt.nodes.new('ShaderNodeMapping')
    uv.vector_type = 'POINT'
    uv.inputs['Location'].default_value = (0.5, mt.BACK / size, 0.0)
    uv.inputs['Scale'].default_value = (1.0 / size, 1.0 / size, 0.0)
    g.link(pos, uv.inputs['Vector'])
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Cubic'
    tex.extension = 'EXTEND'
    g.link(uv.outputs[0], tex.inputs['Vector'])
    sep = nt.nodes.new('ShaderNodeSeparateColor')
    g.link(tex.outputs['Color'], sep.inputs[0])
    crest, wet, lake = sep.outputs[0], sep.outputs[1], sep.outputs[2]
    flat = nt.nodes.new('ShaderNodeMapping')          # the ground seen from above: patterns do not stretch on slopes
    flat.vector_type = 'POINT'
    flat.inputs['Scale'].default_value = (1.0, 1.0, 0.0)
    g.link(pos, flat.inputs['Vector'])
    xy = flat.outputs[0]
    zsep = nt.nodes.new('ShaderNodeSeparateXYZ')
    g.link(pos, zsep.inputs[0])

    # Greens: lush in the hollows and along the lines water follows, paler and yellower on the crests, and patchy
    # at every size (different grasses, different soil).
    big = g.noise(xy, 1.0 / 190.0, 4.0, 0.55)
    med = g.noise(xy, 1.0 / 23.0, 4.0, 0.6)
    small = g.noise(xy, 1.0 / 2.6, 3.0, 0.6)
    # (A flat surface has none of the shade that lies between real blades: these are grass as a whole sward shows.)
    lush = (0.026, 0.086, 0.011)
    fresh = (0.056, 0.150, 0.018)
    pale = (0.110, 0.180, 0.034)
    c = g.mix(g.ramp(big, 0.38, 0.62), lush, fresh)
    dry = g.math('ADD', g.ramp(crest, 1.0, 9.0), g.math('MULTIPLY', g.ramp(med, 0.45, 0.75), 0.35), clamp=True)
    c = g.mix(g.math('MULTIPLY', dry, 0.34), c, pale)
    c = g.mix(g.math('MULTIPLY', g.ramp(wet, 0.40, 0.72), 0.8), c, lush)
    c = g.mix(g.math('MULTIPLY', g.ramp(crest, -1.0, -9.0), 0.6), c, lush)
    tint = g.mix(g.ramp(small, 0.3, 0.7), (0.74, 0.84, 0.70), (1.10, 1.06, 1.02))
    mul = nt.nodes.new('ShaderNodeMix')
    mul.data_type = 'RGBA'
    mul.blend_type = 'MULTIPLY'
    mul.inputs['Factor'].default_value = 1.0
    g.link(c, [s for s in mul.inputs if s.name == 'A' and s.type == 'RGBA'][0])
    g.link(tint, [s for s in mul.inputs if s.name == 'B' and s.type == 'RGBA'][0])
    c = [s for s in mul.outputs if s.type == 'RGBA'][0]
    # Drifts of flowers, seen from afar as washes of colour (near the camera they are real flowers: flowers()).
    m = maps()
    fimg = bake.float_image('meadow_flowers', np.stack([m['yellow'], m['violet'], m['grove']], -1), os.path.join(out, 'meadow_flowers.exr'))
    ftex = nt.nodes.new('ShaderNodeTexImage')
    ftex.image = fimg
    ftex.interpolation = 'Cubic'
    ftex.extension = 'EXTEND'
    g.link(uv.outputs[0], ftex.inputs['Vector'])
    fsep = nt.nodes.new('ShaderNodeSeparateColor')
    g.link(ftex.outputs['Color'], fsep.inputs[0])
    dsep = nt.nodes.new('ShaderNodeVectorMath')
    dsep.operation = 'LENGTH'
    g.link(pos, dsep.inputs[0])
    away = g.ramp(dsep.outputs['Value'], 25.0, 140.0)
    speck = g.ramp(g.noise(xy, 1.0 / 1.3, 2.0, 0.7), 0.42, 0.62)
    c = g.mix(g.math('MULTIPLY', g.math('MULTIPLY', fsep.outputs[0], speck), g.math('MULTIPLY', away, 0.80)), c, (0.46, 0.34, 0.02))
    speck2 = g.ramp(g.noise(xy, 1.0 / 1.7, 2.0, 0.7), 0.45, 0.65)
    c = g.mix(g.math('MULTIPLY', g.math('MULTIPLY', fsep.outputs[1], speck2), g.math('MULTIPLY', away, 0.55)), c, (0.10, 0.06, 0.26))
    # The far grassland takes its mottling from an aerial scan of real grass and stones (laid out twice at
    # different sizes, so no patch repeats); its hue stays the spring green above.
    def aerial(size, turn):
        mp = nt.nodes.new('ShaderNodeMapping')
        mp.inputs['Scale'].default_value = (1.0 / size, 1.0 / size, 1.0)
        mp.inputs['Rotation'].default_value = (0.0, 0.0, turn)
        g.link(xy, mp.inputs['Vector'])
        return scan_texture(nt, 'aerial_grass_rock', 'diff', mp.outputs[0], True).outputs['Color']
    pattern = g.mix(g.ramp(g.noise(xy, 1.0 / 420.0, 2.0, 0.5), 0.4, 0.6), aerial(64.0, 0.0), aerial(215.0, 0.7))
    lum = nt.nodes.new('ShaderNodeRGBToBW')
    g.link(pattern, lum.inputs[0])
    shade = g.ramp(lum.outputs[0], 0.03, 0.30, 0.68, 1.36)
    mul2 = nt.nodes.new('ShaderNodeMix')
    mul2.data_type = 'RGBA'
    mul2.blend_type = 'MULTIPLY'
    g.link(away, [q for q in mul2.inputs if q.name == 'Factor' and q.type == 'VALUE'][0])
    g.link(c, [q for q in mul2.inputs if q.name == 'A' and q.type == 'RGBA'][0])
    comb = nt.nodes.new('ShaderNodeCombineColor')
    for key in ('Red', 'Green', 'Blue'):
        g.link(shade, comb.inputs[key])
    g.link(comb.outputs[0], [q for q in mul2.inputs if q.name == 'B' and q.type == 'RGBA'][0])
    c = [q for q in mul2.outputs if q.type == 'RGBA'][0]
    # Where the hills are too steep to hold a sward the soil and rock show: the aerial scan's own colours.
    nsep = nt.nodes.new('ShaderNodeSeparateXYZ')
    g.link(geo.outputs['True Normal'], nsep.inputs[0])
    steep = g.math('MULTIPLY', g.ramp(nsep.outputs['Z'], 0.80, 0.66), g.ramp(g.noise(xy, 1.0 / 37.0, 4.0, 0.6), 0.42, 0.68))
    bare = nt.nodes.new('ShaderNodeMix')
    bare.data_type = 'RGBA'
    bare.blend_type = 'MULTIPLY'
    [q for q in bare.inputs if q.name == 'Factor' and q.type == 'VALUE'][0].default_value = 1.0
    g.link(pattern, [q for q in bare.inputs if q.name == 'A' and q.type == 'RGBA'][0])
    [q for q in bare.inputs if q.name == 'B' and q.type == 'RGBA'][0].default_value = (1.25, 1.0, 0.7, 1.0)
    c = g.mix(g.math('MULTIPLY', steep, away), c, [q for q in bare.outputs if q.type == 'RGBA'][0])
    # Far off, beyond where single oaks are planted, the north slopes are dark with woods and brush.
    b_view = math.radians(mt.SITE['bearing'])
    northness = nt.nodes.new('ShaderNodeVectorMath')
    northness.operation = 'DOT_PRODUCT'
    northness.inputs[1].default_value = (-math.sin(b_view), math.cos(b_view), 0.0)
    g.link(geo.outputs['True Normal'], northness.inputs[0])
    woods = g.math('MULTIPLY', g.ramp(northness.outputs['Value'], 0.04, 0.30),
                   g.ramp(g.noise(xy, 1.0 / 260.0, 5.0, 0.62), 0.30, 0.62))
    woods = g.math('MULTIPLY', woods, g.ramp(dsep.outputs['Value'], 4800.0, 6600.0))
    c = g.mix(g.math('MULTIPLY', woods, 0.85), c, (0.020, 0.040, 0.014))
    # Under the clumps near our feet: scanned ground, soil and thatch between the grass.
    near_map = nt.nodes.new('ShaderNodeMapping')
    near_map.inputs['Scale'].default_value = (1.0 / 2.6, 1.0 / 2.6, 1.0)
    g.link(xy, near_map.inputs['Vector'])
    soil = scan_texture(nt, 'grass_ground', 'diff', near_map.outputs[0], True)
    dk = nt.nodes.new('ShaderNodeMix')
    dk.data_type = 'RGBA'
    dk.blend_type = 'MULTIPLY'
    [q for q in dk.inputs if q.name == 'Factor' and q.type == 'VALUE'][0].default_value = 1.0
    g.link(soil.outputs['Color'], [q for q in dk.inputs if q.name == 'A' and q.type == 'RGBA'][0])
    [q for q in dk.inputs if q.name == 'B' and q.type == 'RGBA'][0].default_value = (0.34, 0.62, 0.28, 1.0)
    c = g.mix(g.ramp(dsep.outputs['Value'], 130.0, 25.0, 0.0, 0.9), c, [q for q in dk.outputs if q.type == 'RGBA'][0])
    # The lake's bed and its wet shore.
    under = g.math('MULTIPLY', g.ramp(zsep.outputs['Z'], float(f['water']) + 0.30, float(f['water']) - 0.05), lake)
    c = g.mix(under, c, (0.085, 0.075, 0.050))
    g.link(c, bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.8
    bsdf.inputs['Specular IOR Level'].default_value = 0.2
    bsdf.inputs['Sheen Weight'].default_value = 0.10
    bsdf.inputs['Sheen Roughness'].default_value = 0.45
    bsdf.inputs['Sheen Tint'].default_value = (0.75, 1.0, 0.55, 1.0)
    # Tussocks and hummocks, as relief in the shading.
    bump = nt.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.5
    bump.inputs['Distance'].default_value = 0.12
    g.link(g.math('ADD', g.noise(xy, 1.0 / 0.55, 3.0, 0.65), g.math('MULTIPLY', small, 1.5)), bump.inputs['Height'])
    relief_map = nt.nodes.new('ShaderNodeMapping')
    relief_map.inputs['Scale'].default_value = (1.0 / 64.0, 1.0 / 64.0, 1.0)
    g.link(xy, relief_map.inputs['Vector'])
    relief = scan_texture(nt, 'aerial_grass_rock', 'nor_gl', relief_map.outputs[0])
    nm = nt.nodes.new('ShaderNodeNormalMap')
    g.link(g.math('MULTIPLY', away, 1.3), nm.inputs['Strength'])
    g.link(relief.outputs['Color'], nm.inputs['Color'])
    g.link(nm.outputs['Normal'], bump.inputs['Normal'])
    g.link(bump.outputs['Normal'], bsdf.inputs['Normal'])
    # Its place in the mask: grass sways and turns with the seasons; open ground takes snow; the lake is water.
    aov = bake.add_mask(mat, wind=g.math('SUBTRACT', 1.0, under, clamp=True), snow=1.0, water_level=float(f['water']))
    comb = aov.inputs['Color'].links[0].from_node
    red = comb.inputs['Red'].links[0].from_socket
    g.link(g.math('MULTIPLY', red, g.ramp(lake, 0.4, 0.6)), comb.inputs['Red'])
    return mat


def ground(out):
    mat = grass_material(out)
    # What the camera sees, a vertex for about every pixel across and enough in depth for every fold.
    view = fan('Land', np.linspace(-24.6, 24.6, 1231), np.geomspace(0.9, 46000.0, 1800), land_height)
    # What it does not see, but which shades and lights what it does: the land to either side and behind us.
    rest = np.geomspace(0.9, 14000.0, 170)
    left = fan('LandLeft', np.linspace(-180.0, -24.6, 150), rest, land_height)
    right = fan('LandRight', np.linspace(24.6, 180.0, 150), rest, land_height)
    for ob in (view, left, right):
        ob.data.materials.append(mat)
    return view


def coarse_at(a, x, y):
    """A map on the terrain's coarse grid, read at scene positions."""
    return hills.sample(a, np.asarray(x) / mt.CELL + mt.N / 2 - 0.5, (np.asarray(y) + mt.BACK) / mt.CELL - 0.5)


_MAPS = {}


def maps():
    """Where things grow (coarse grid): `grove` oaks standing together, `yellow` and `violet` drifts of flowers."""
    if not _MAPS:
        _MAPS['grove'] = hills.noise(mt.N, SEED + 20, 26, 7, 2.2)
        _MAPS['yellow'] = mt.smooth(0.25, 1.3, hills.noise(mt.N, SEED + 21, 22, 4, 2.0))
        _MAPS['violet'] = mt.smooth(0.95, 1.7, hills.noise(mt.N, SEED + 22, 14, 3, 2.0))
    return _MAPS


def slope_at(x, y):
    e = 2.0
    return np.hypot(land_height(x + e, y) - land_height(x - e, y), land_height(x, y + e) - land_height(x, y - e)) / (2 * e)


# ---- Oaks -------------------------------------------------------------------------------------------------------

OAKS = [dict(seed=11, height=12.0, radius=8.5),
        dict(seed=12, height=9.5, radius=6.5, trunk=2.0, girth=0.45),
        dict(seed=13, height=13.5, radius=10.0, trunk=2.6, girth=0.72, points=6200),
        dict(seed=14, height=7.5, radius=5.0, trunk=1.8, girth=0.30, points=3600),
        dict(seed=15, height=10.5, radius=9.0, trunk=2.1, girth=0.60, droop=0.4),
        dict(seed=16, height=11.0, radius=7.0, trunk=2.8, girth=0.50)]


def oak_density(x, y, d):
    """Oaks to the hectare at a place: few on the open sunny slopes, many on the slopes that face north, and lines
    of them down every hollow where water runs. In groves, not evenly."""
    grove = coarse_at(maps()['grove'], x, y)
    north = coarse_at(LAND['north'], x, y)
    wet = mt.field_at(LAND['wet'], x, y)
    rho = (0.9 + 17.0 * mt.smooth(0.0, 1.3, grove) ** 2) * (0.28 + 2.6 * np.clip(north, 0.0, 1.0) ** 1.3)
    rho += 48.0 * mt.smooth(0.38, 0.66, wet) * mt.smooth(200.0, 400.0, d)
    rho *= 1.0 + 0.6 * mt.smooth(1200.0, 2600.0, d)          # (far off a single tree is a speck: a few more of them)
    return rho


def scatter_oaks(bark, leaf):
    """The oaks of the hills, out to where the near ground ends (six kilometres)."""
    rng = np.random.default_rng(21)
    n = 420000
    b = rng.uniform(-42.0, 42.0, n)
    d = np.sqrt(rng.uniform(60.0 ** 2, 7000.0 ** 2, n))
    x, y = d * np.sin(np.radians(b)), d * np.cos(np.radians(b))
    inside = (np.abs(x) < FIELD_HALF - 520.0) & (y < FIELD_FAR - 520.0)
    x, y, d = x[inside], y[inside], d[inside]
    per_ha = n / (0.5 * math.radians(84.0) * 7000.0 ** 2 / 1e4)
    keep = rng.random(len(x)) < oak_density(x, y, d) / per_ha
    keep &= mt.field_at(LAND['lake'], x, y) < 0.01
    x, y = x[keep], y[keep]
    keep = slope_at(x, y) < 0.75
    # No two in the same eight metres.
    cellid = np.floor(x / 8.0).astype(np.int64) * 100003 + np.floor(y / 8.0).astype(np.int64)
    _, first = np.unique(cellid, return_index=True)
    one = np.zeros(len(x), bool)
    one[first] = True
    keep &= one
    x, y = x[keep], y[keep]
    z = land_height(x, y)
    kind = rng.integers(0, len(OAKS), len(x))
    size = rng.uniform(0.78, 1.18, len(x))
    yaw = rng.uniform(0.0, 2.0 * math.pi, len(x))
    for k, spec in enumerate(OAKS):
        tree = broadleaf.grow(**spec)
        sel = kind == k
        child = broadleaf.to_object(tree, 'Oak%d' % k, bark, leaf)
        stand('Oaks%d' % k, child, x[sel], y[sel], z[sel] - 0.12, size[sel], yaw[sel])
    print('oaks standing:', len(x), flush=True)


def single_oak(name, spec, x, y, bark, leaf, yaw=0.0):
    """One oak of its own at a chosen place."""
    tree = broadleaf.grow(**spec)
    z = float(land_height(np.array([x]), np.array([y]))[0]) - 0.12
    c, sn = math.cos(yaw), math.sin(yaw)
    for key in ('wood_co', 'leaf_co'):
        v = tree[key]
        tree[key] = np.stack([x + v[:, 0] * c - v[:, 1] * sn, y + v[:, 0] * sn + v[:, 1] * c, z + v[:, 2]], -1).astype(np.float32)
    return broadleaf.to_object(tree, name, bark, leaf)


def oak_bark(bark):
    """The oaks' trunks and limbs wear scanned oak bark."""
    nt = bark.node_tree
    b = nt.nodes['Principled BSDF']
    tc = nt.nodes.new('ShaderNodeTexCoord')
    mp = nt.nodes.new('ShaderNodeMapping')
    mp.inputs['Scale'].default_value = (0.9, 0.9, 0.45)
    nt.links.new(tc.outputs['Object'], mp.inputs['Vector'])
    diff = scan_texture(nt, 'jolcham_oak_bark_01', 'diff', mp.outputs[0], True)
    nor = scan_texture(nt, 'jolcham_oak_bark_01', 'nor_gl', mp.outputs[0])
    for t in (diff, nor):
        t.projection = 'BOX'
        t.projection_blend = 0.3
    nt.links.new(diff.outputs['Color'], b.inputs['Base Color'])
    nm = nt.nodes.new('ShaderNodeNormalMap')
    nt.links.new(nor.outputs['Color'], nm.inputs['Color'])
    nt.links.new(nm.outputs['Normal'], b.inputs['Normal'])


def thorns():
    """Small gnarled trees (a scan), here and there on the open slopes: what hawthorn and buckeye are to such
    hills."""
    rng = np.random.default_rng(23)
    tree = scan_objects('island_tree_02', lambda n: n == 'island_tree_02_LOD1')[0]
    dress(tree, 1.0, 0.3, hue=(0.5, 1.0, 0.9))
    n = 60000
    b = rng.uniform(-30.0, 30.0, n)
    d = np.sqrt(rng.uniform(120.0 ** 2, 3200.0 ** 2, n))
    x, y = d * np.sin(np.radians(b)), d * np.cos(np.radians(b))
    keep = rng.random(n) < 0.030 * (0.4 + mt.smooth(-0.2, 1.2, coarse_at(maps()['grove'], x, y)))
    keep &= mt.field_at(LAND['lake'], x, y) < 0.01
    x, y = x[keep], y[keep]
    z = land_height(x, y)
    size = rng.uniform(1.3, 2.4, len(x))
    stand('Thorns', tree, x, y, z - 0.05, size, rng.uniform(0.0, 6.28, len(x)))
    print('small trees:', len(x), flush=True)


def oaks():
    bark, leaf = broadleaf.materials()
    oak_bark(bark)
    bake.add_mask(bark, wind=0.0, snow=0.15)
    bake.add_mask(leaf, wind=1.0, snow=0.3)
    scatter_oaks(bark, leaf)
    # An oak on the brow of our rise, right of the lake: the picture's nearer ground.
    single_oak('BrowOak', dict(seed=41, height=12.5, radius=8.5, trunk=2.5, girth=0.65, points=6200, leaves_per_twig=44, leaf=0.15),
               30.0, 150.0, bark, leaf, yaw=1.1)
    # And one beside us, its trunk out of the picture to the right, its boughs reaching into the edge.
    single_oak('NearOak', dict(seed=42, height=14.0, radius=9.0, trunk=3.0, girth=0.8, points=9000, step=0.30, kill=0.5,
                               leaves_per_twig=70, leaf=0.10, droop=0.5),
               21.0, 31.0, bark, leaf, yaw=2.4)
    thorns()


# ---- Grass and flowers ------------------------------------------------------------------------------------------

def plant_material():
    """Blades, stalks and petals: their colour comes from the mesh; light passes through them."""
    mat, nt, bsdf, outn = new_material('Plants')
    col = nt.nodes.new('ShaderNodeAttribute')
    col.attribute_name = 'col'
    nt.links.new(col.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.5
    bsdf.inputs['Specular IOR Level'].default_value = 0.3
    thru = nt.nodes.new('ShaderNodeBsdfTranslucent')
    nt.links.new(col.outputs['Color'], thru.inputs['Color'])
    mix = nt.nodes.new('ShaderNodeMixShader')
    mix.inputs['Fac'].default_value = 0.38
    nt.links.new(bsdf.outputs['BSDF'], mix.inputs[1])
    nt.links.new(thru.outputs['BSDF'], mix.inputs[2])
    nt.links.new(mix.outputs[0], outn.inputs['Surface'])
    bake.add_mask(mat, wind=1.0, snow=0.6)
    return mat


def wedge(rng, n, d0, d1, half=25.6):
    """n places spread evenly over the part of the ground the camera sees between two distances."""
    b = np.radians(rng.uniform(-half, half, n))
    d = np.sqrt(rng.uniform(d0 * d0, d1 * d1, n))
    return d * np.sin(b), d * np.cos(b), d


GREENS = np.array([[0.040, 0.120, 0.018], [0.085, 0.200, 0.032], [0.150, 0.240, 0.048], [0.340, 0.290, 0.130]])
ROOT = np.array([0.13, 0.15, 0.055])
STRAW = np.array([0.40, 0.33, 0.17])


def blades(rng, x, y, z, h, w, lean, col, tip):
    """Grass blades as curved, tapering ribbons. Arrays per blade: foot, height, width, how far it bends over,
    colour (rgb), colour of its tip. Returns co, triangles, colours."""
    n = len(x)
    t = np.array([0.0, 0.3, 0.6, 0.85])
    yaw = rng.uniform(0.0, 2.0 * math.pi, n)
    dx, dy = np.cos(yaw), np.sin(yaw)
    turn = yaw + rng.uniform(0.6, 2.5, n)
    cx, cy = np.cos(turn), np.sin(turn)
    foot = np.stack([x, y, z], -1)
    out = np.stack([dx, dy, np.zeros(n)], -1)
    side = np.stack([cx, cy, np.zeros(n)], -1)
    up = np.array([0.0, 0.0, 1.0])
    rings = []
    cols = []
    for tk in t:
        mid = foot + out * (lean * h * tk * tk)[:, None] + up[None, :] * (h * tk * (1.0 - 0.25 * lean * tk))[:, None]
        hw = (0.5 * w * (1.0 - 0.8 * tk ** 1.4))[:, None]
        rings += [mid - side * hw, mid + side * hw]
        c = ROOT[None, :] * (1.0 - min(tk / 0.3, 1.0)) + col * min(tk / 0.3, 1.0)
        c = c * (1.0 - max(tk - 0.6, 0.0) / 0.4) + tip * (max(tk - 0.6, 0.0) / 0.4)
        cols += [c, c]
    rings.append(foot + out * (lean * h)[:, None] + up[None, :] * (h * (1.0 - 0.25 * lean))[:, None])
    cols.append(tip)
    co = np.stack(rings, 1).reshape(-1, 3)
    cc = np.stack(cols, 1).reshape(-1, 3)
    tri = np.array([[0, 1, 3], [0, 3, 2], [2, 3, 5], [2, 5, 4], [4, 5, 7], [4, 7, 6], [6, 7, 8]])
    faces = (np.arange(n)[:, None, None] * 9 + tri[None, :, :]).reshape(-1, 3)
    return co.astype(np.float32), faces.astype(np.int32), cc.astype(np.float32)


def stalks(mat):
    """The taller blades and the stalks gone to seed that stand up out of the sward (made here, one by one: the
    scanned clumps are a hand high), and beyond the reach of the clumps the tufts on the far slope of our rise."""
    rng = np.random.default_rng(31)
    tuss = hills.noise(512, SEED + 30, 40, 4, 2.0)             # patchiness, tiled every 128 m
    parts = []
    #        from   to     blades  width   height        (wider and fewer with distance: what a pixel can hold)
    zones = [(0.5, 9.0, 40000, 0.006, (0.24, 0.50)),
             (9.0, 30.0, 100000, 0.014, (0.26, 0.55)),
             (30.0, 110.0, 300000, 0.045, (0.26, 0.62)),
             (110.0, 330.0, 420000, 0.130, (0.22, 0.50))]
    for d0, d1, count, width, (h0, h1) in zones:
        x, y, d = wedge(rng, count, d0, d1)
        patch = hills.sample(tuss, (x * 4.0) % 510.0, (y * 4.0) % 510.0)
        keep = rng.random(count) < 0.30 + 0.70 * mt.smooth(-1.0, 0.8, patch)
        keep &= mt.field_at(LAND['lake'], x, y) < 0.5
        x, y, patch = x[keep], y[keep], patch[keep]
        n = len(x)
        z = land_height(x, y) - 0.01
        h = rng.uniform(h0, h1, n) * (0.75 + 0.35 * mt.smooth(-1.0, 1.2, patch))
        w = width * rng.uniform(0.7, 1.3, n)
        lean = rng.uniform(0.15, 1.0, n) ** 1.5
        pick = rng.random(n)
        which = np.where(pick < 0.34, 0, np.where(pick < 0.74, 1, np.where(pick < 0.93, 2, 3)))
        col = GREENS[which] * rng.uniform(0.8, 1.2, (n, 1))
        tip = col * np.array([1.25, 1.15, 0.9])
        # Stalks gone to seed: taller, thinner, straw-coloured at the top.
        seed = rng.random(n) < 0.07
        h = np.where(seed, h * 1.9, h)
        w = np.where(seed, w * 0.8, w)
        lean = np.where(seed, lean * 0.4, lean)
        tip = np.where(seed[:, None], STRAW[None, :] * rng.uniform(0.8, 1.15, (n, 1)), tip)
        parts.append(blades(rng, x, y, z, h, w, lean, col, tip))
    off = 0
    cos, faces, cols = [], [], []
    for co, f, c in parts:
        cos.append(co)
        faces.append(f + off)
        cols.append(c)
        off += len(co)
    ob = mesh_object('Grass', np.concatenate(cos), np.concatenate(faces), smooth=True, col=np.concatenate(cols))
    ob.data.materials.append(mat)
    print('grass blades:', off // 9, flush=True)


def sward():
    """The grass itself: scanned clumps of real meadow grass, thousands of them, thick near our feet and thinning
    with distance (where each stands for more), turned and sized every one its own way. Uneven as a meadow is."""
    rng = np.random.default_rng(33)
    tuss = hills.noise(512, SEED + 30, 40, 4, 2.0)
    kinds = scan_objects('grass_medium_01', lambda n: n.endswith('_LOD0') and 'geonodes' not in n
                         and any(k in n for k in ('tall_', 'mid_', 'large_a', 'large_b', 'small_a')))
    kinds += scan_objects('grass_medium_02', lambda n: n in ('grass_medium_02_c', 'grass_medium_02_d', 'grass_medium_02_e'))
    for ob in kinds:
        dress(ob, 1.0, 0.6, hue=(0.582, 1.6, 0.80))
    weight = np.array([3.0 if 'tall' in ob.name else 1.6 if 'mid' in ob.name or '02_' in ob.name else 1.0 for ob in kinds])
    xs, ys, ss = [], [], []
    #        from  to     per m2  size
    for d0, d1, dens, (s0, s1) in ((0.5, 12.0, 34.0, (1.5, 2.6)), (12.0, 40.0, 15.0, (2.2, 3.8)), (40.0, 75.0, 4.2, (3.6, 5.2))):
        count = int(dens * math.radians(25.6) * (d1 * d1 - d0 * d0))
        x, y, d = wedge(rng, count, d0, d1)
        patch = hills.sample(tuss, (x * 4.0) % 510.0, (y * 4.0) % 510.0)
        keep = rng.random(count) < 0.45 + 0.55 * mt.smooth(-1.0, 0.8, patch)
        keep &= mt.field_at(LAND['lake'], x, y) < 0.5
        xs.append(x[keep])
        ys.append(y[keep])
        ss.append(rng.uniform(s0, s1, count)[keep] * (0.8 + 0.4 * mt.smooth(-1.0, 1.2, patch[keep])))
    x, y, size = np.concatenate(xs), np.concatenate(ys), np.concatenate(ss)
    z = land_height(x, y) - 0.02
    which = rng.choice(len(kinds), size=len(x), p=weight / weight.sum())
    for k, ob in enumerate(kinds):
        sel = which == k
        stand('Sward%d' % k, ob, x[sel], y[sel], z[sel], size[sel], rng.uniform(0.0, 6.28, int(sel.sum())))
    print('grass clumps:', len(x), 'of', len(kinds), 'kinds', flush=True)


def flowers():
    """Wildflowers in drifts (scans): yellow celandine and daisies, violet periwinkle and heliophila. Farther off a
    plant stands for a patch of them, and is as much larger."""
    rng = np.random.default_rng(32)
    m = maps()
    sets = {'yellow': scan_objects('celandine_01', lambda n: n.endswith('_LOD0') and 'geonodes' not in n)
            + scan_objects('flower_ursinia', lambda n: n in ('flower_ursinia_c_LOD0', 'flower_ursinia_d_LOD0', 'flower_ursinia_e_LOD0')),
            'violet': scan_objects('periwinkle_plant', lambda n: n in ('periwinkle_plant_04_LOD0', 'periwinkle_plant_05_LOD0', 'periwinkle_plant_06_LOD0'))
            + scan_objects('flower_heliophila', lambda n: n == 'flower_heliophila_small_LOD0')}
    for kind, count, d1, (s0, s1) in (('yellow', 60000, 130.0, (1.3, 1.9)), ('violet', 26000, 110.0, (1.3, 1.9))):
        kinds = sets[kind]
        for ob in kinds:
            dress(ob, 1.0, 0.5)
        x, y, d = wedge(rng, count, 1.2, d1)
        drift = coarse_at(m[kind], x, y)
        fine = hills.sample(hills.noise(256, SEED + (40 if kind == 'yellow' else 41), 30, 4, 2.0), (x * 2.0) % 254.0, (y * 2.0) % 254.0)
        keep = rng.random(count) < np.clip(drift * 1.5 + 0.10, 0.0, 1.0) * mt.smooth(-0.8, 0.7, fine)
        keep &= mt.field_at(LAND['lake'], x, y) < 0.5
        x, y, d = x[keep], y[keep], d[keep]
        z = land_height(x, y) + 0.05
        size = rng.uniform(s0, s1, len(x)) * (1.0 + d / 70.0)
        which = rng.integers(0, len(kinds), len(x))
        for k, ob in enumerate(kinds):
            sel = which == k
            stand('%s%d' % (kind, k), ob, x[sel], y[sel], z[sel], size[sel], rng.uniform(0.0, 6.28, int(sel.sum())))
        print(kind, 'flowers:', len(x), flush=True)


def rocks():
    """Stones (scans): mossy ones in the grass near us, boulders by the lake and where the hills' bones show."""
    small = scan_objects('rock_moss_set_01', lambda n: 'LOD' not in n or n.endswith('LOD0'))
    small += scan_objects('rock_moss_set_02', lambda n: 'LOD' not in n or n.endswith('LOD0'))
    big = scan_objects('boulder_01', lambda n: 'LOD' not in n or n.endswith('LOD0'))
    for ob in small + big:
        dress(ob, 0.0, 0.7)
        ob.location = (0.0, -900.0, -900.0)          # the originals wait out of sight; copies are placed
    rng = np.random.default_rng(35)
    spots = [(-2.3, 5.6, 0.95, small), (-3.5, 6.5, 0.5, small), (-1.4, 6.6, 0.4, small), (4.6, 11.5, 0.8, small),
             (-7.5, 19.0, 1.3, small), (-8.8, 20.0, 0.7, small), (9.0, 44.0, 1.6, big), (-26.0, 70.0, 2.4, big),
             (-29.0, 73.0, 1.2, small), (41.0, 96.0, 2.8, big)]
    for k, (x, y, want, pool) in enumerate(spots):
        src = pool[int(rng.integers(0, len(pool)))]
        ob = src.copy()
        bpy.context.scene.collection.objects.link(ob)
        dims = src.dimensions
        sc = want / max(dims[0], dims[1], 1e-3)
        zs = [v[2] for v in src.bound_box]
        ground_z = float(land_height(np.array([x]), np.array([y]))[0])
        ob.scale = (sc, sc, sc)
        ob.rotation_euler = (0.0, 0.0, float(rng.uniform(0.0, 6.28)))
        ob.location = (x, y, ground_z - (min(zs) + 0.28 * (max(zs) - min(zs))) * sc)
    print('rocks:', len(spots), 'from', len(small), 'small and', len(big), 'big scans', flush=True)


def lake_water():
    """For the preview pictures only: still water on the reservoir (the phone draws the real one)."""
    lk = LAND['lake'] > 0.5
    cell = mt.CELL / mt.UP
    n = lk.shape[0]
    rows, cols = np.nonzero(lk)
    x0, x1 = (cols.min() - n / 2) * cell - 30.0, (cols.max() - n / 2) * cell + 30.0
    y0, y1 = rows.min() * cell - mt.BACK - 30.0, rows.max() * cell - mt.BACK + 30.0
    bpy.ops.mesh.primitive_plane_add(size=1.0, location=((x0 + x1) / 2, (y0 + y1) / 2, float(LAND['water'])))
    ob = bpy.context.object
    ob.scale = (x1 - x0, y1 - y0, 1.0)
    mat, nt, bsdf, outn = new_material('PreviewWater')
    bsdf.inputs['Base Color'].default_value = (0.012, 0.03, 0.035, 1)
    bsdf.inputs['Roughness'].default_value = 0.03
    ob.data.materials.append(mat)


def build():
    if os.environ.get('NOOR_SAMPLES'):          # quicker, rougher looks while the scene is being made
        bpy.context.scene.cycles.samples = int(os.environ['NOOR_SAMPLES'])
    ground(OUT)
    if STAGE != 'land':
        oaks()
    if STAGE == 'all':
        sward()
        stalks(plant_material())
        flowers()
        rocks()
    if MODE in ('preview', 'look'):
        lake_water()


# (bake.run fixes a bake's samples and the pictures' quality. This scenery is all fine grass: its pictures are
# packed harder, or they would weigh twice what the others do. Both can be set from outside: NOOR_BAKE_SAMPLES,
# NOOR_BAKE_QUALITY.)
_render, _pack = bake.bake_render, bake.bake_pack


def _bake_render(out, yaw=0.0, samples=512, aovs=()):
    return _render(out, yaw=yaw, samples=int(os.environ.get('NOOR_BAKE_SAMPLES', 200)), aovs=aovs)


def _bake_pack(src, dst, suns, eye, aovs=(), quality=80, frame='0001', water=None):
    return _pack(src, dst, suns, eye, aovs=aovs, quality=int(os.environ.get('NOOR_BAKE_QUALITY', 38)), frame=frame, water=water)


bake.bake_render, bake.bake_pack = _bake_render, _bake_pack

bake.run(build, (0.0, 0.0, EYE), aovs=('mask',), exposure=-2.3,
         water_level=None if MODE in ('preview', 'look') else float(LAND['water']))
