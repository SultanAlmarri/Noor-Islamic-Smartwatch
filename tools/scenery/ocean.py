"""The Ocean scenery: a beach, the open sea in front of it, waves coming in.

The sand is photogrammetry of real beaches (ambientCG and Poly Haven, CC0: see tools/scenery/sources/ocean.md): dry,
trodden sand with real relief where we stand, wind ripples beside it, wet rippled sand down at the sea's edge. The
horizon is open: the Sun rises out of the sea and sets into it. The sea itself is drawn by the phone (the swell, the
breakers and their foam, the sheet of water that runs up the sand and back, the mirrored sky), from the beach's
shape, which is written into land.json (`shore`).

    blender -b --factory-startup -P tools/scenery/ocean.py -- preview <out dir> [azimuth elevation]

The scans are expected in %USERPROFILE%/ProjectMinhad/NoorTools/assets/polyhaven/<id>/ (Poly Haven's 2k .blend with
its textures folder) and .../assets/ambientcg/<id>_2K/ (ambientCG's 2K-JPG archive, unpacked).
"""
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

SEA = 0.0
STAND = 0.95                # metres above the sea: the dry sand we stand on
EYE = STAND + 1.75
SHORE_AHEAD = 11.5          # metres ahead of us (in the middle of the view) the sea's mean edge lies
SHORE_SLANT = 0.10          # it runs across the view on a slant: this much further away per metre to the right
BEACH_SLOPE = STAND / SHORE_AHEAD
BED_SLOPE = 0.040           # the bed under the breakers
SCANS = os.environ.get('NOOR_POLYHAVEN', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'polyhaven'))
ACG = os.environ.get('NOOR_AMBIENTCG', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'ambientcg'))
_loaded = {}


def scan(aid, lod=1):
    """A scanned model (one of its levels of detail) as a new object; later calls share its mesh."""
    key = '%s_LOD%d' % (aid, lod)
    if key in _loaded:
        ob = _loaded[key].copy()
        bpy.context.scene.collection.objects.link(ob)
        return ob
    d = os.path.join(SCANS, aid)
    blend = [f for f in os.listdir(d) if f.endswith('.blend')][0]
    with bpy.data.libraries.load(os.path.join(d, blend)) as (src, dst):
        dst.objects = [n for n in src.objects if n == key]
    ob = dst.objects[0]
    bpy.context.scene.collection.objects.link(ob)
    for m in ob.data.materials:
        bake.add_mask(m, wind=0.0, snow=0.0, water_level=SEA)
    _loaded[key] = ob
    return ob


def place(aid, x, y, scale=1.0, turn=0.0, sink=0.25, lod=1, base=SEA):
    """Stands a scan with the middle of its outline at (x, y), its lowest point `sink` (of its own height) under
    `base`, turned about the upright."""
    ob = scan(aid, lod)
    bb = [tuple(v) for v in ob.bound_box]
    zs = [v[2] for v in bb]
    cx = 0.5 * (min(v[0] for v in bb) + max(v[0] for v in bb))
    cy = 0.5 * (min(v[1] for v in bb) + max(v[1] for v in bb))
    a = math.radians(turn)
    ob.scale = (scale, scale, scale)
    ob.rotation_euler = (0.0, 0.0, a)
    ob.location = (x - (cx * math.cos(a) - cy * math.sin(a)) * scale, y - (cx * math.sin(a) + cy * math.cos(a)) * scale,
                   base - (min(zs) + sink * (max(zs) - min(zs))) * scale)
    return ob


def fbm(x, y, rng, octaves=6, f0=1.0, gain=0.5):
    """Smooth random relief from sums of sines at random bearings (cheap, endless, no seams)."""
    out = np.zeros_like(x, dtype=np.float32)
    amp, f, total = 1.0, f0, 0.0
    for _ in range(octaves):
        for _ in range(3):
            a = rng.uniform(0, 2 * math.pi)
            out += amp / 3 * np.sin((x * math.cos(a) + y * math.sin(a)) * f + rng.uniform(0, 6.28)).astype(np.float32)
        total += amp
        amp *= gain
        f *= 2.07
    return out / total


def texture(nt, aid, kind, vector, colour=False):
    d = os.path.join(SCANS, aid, 'textures')
    name = [f for f in os.listdir(d) if '_%s_' % kind in f][0]
    node = nt.nodes.new('ShaderNodeTexImage')
    node.image = bpy.data.images.load(os.path.join(d, name), check_existing=True)
    if not colour:
        node.image.colorspace_settings.name = 'Non-Color'
    nt.links.new(vector, node.inputs['Vector'])
    return node


def acg(nt, aid, kind, vector, colour=False):
    """One map of an ambientCG scan (Color, Displacement, Roughness, NormalGL)."""
    d = os.path.join(ACG, aid + '_2K')
    name = [f for f in os.listdir(d) if f.endswith('_%s.jpg' % kind)][0]
    node = nt.nodes.new('ShaderNodeTexImage')
    node.image = bpy.data.images.load(os.path.join(d, name), check_existing=True)
    if not colour:
        node.image.colorspace_settings.name = 'Non-Color'
    nt.links.new(vector, node.inputs['Vector'])
    return node


def beach():
    """The beach and the bed of the sea: its shape (a level top where we stand, a face falling to the sea's edge, a
    gently shelving bed) and its sand, three scans laid by how high above the sea the sand lies."""
    rng = np.random.default_rng(3)
    ys = np.concatenate([np.linspace(-30, 0, 16)[:-1], np.geomspace(1.0, 60000.0, 190) - 1.0])
    half = np.geomspace(1.0, 40000.0, 110) - 1.0
    xs = np.concatenate([-half[::-1], half[1:]])
    X, Y = np.meshgrid(xs, ys)
    c = 1.0 / math.sqrt(1.0 + SHORE_SLANT * SHORE_SLANT)
    s = (Y - SHORE_AHEAD - SHORE_SLANT * X) * c              # metres seaward of the sea's mean edge
    z = np.where(s < 0, -s * BEACH_SLOPE, -s * BED_SLOPE)
    # The top of the beach is nearly level and a little hummocky; cusps scallop the face along the shore.
    top = STAND + 0.05 * fbm(X, Y, rng, 3, 1 / 14.0)
    z = np.minimum(z, top)
    z = z + 0.035 * np.sin(X / 7.5 + 0.6 * np.sin(X / 23.0)) * np.exp(-np.abs(s + 2.5) / 3.5)
    z = np.where(s > 120, z - (s - 120) * 0.05, z)
    z = np.maximum(z, -60.0)
    ob = bake.grid_mesh('Beach', xs, ys, z + SEA)
    bake.adaptive(ob, 1.0)

    mat = bpy.data.materials.new('BeachSand')
    mat.use_nodes = True
    mat.displacement_method = 'DISPLACEMENT'
    nt = mat.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N['Principled BSDF']
    out = N['Material Output']
    geo = N.new('ShaderNodeNewGeometry')
    sep = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Position'], sep.inputs[0])

    def mapping(size, turn):
        m = N.new('ShaderNodeMapping')
        m.inputs['Scale'].default_value = (1 / size, 1 / size, 1.0)
        m.inputs['Rotation'].default_value = (0, 0, turn)
        L.new(geo.outputs['Position'], m.inputs['Vector'])
        return m.outputs[0]

    def mix(a, b, fac):
        m = N.new('ShaderNodeMixRGB')
        L.new(fac, m.inputs['Fac']); L.new(a, m.inputs['Color1']); L.new(b, m.inputs['Color2'])
        return m.outputs['Color']

    def ramp(lo, hi):
        """1 where the sand lies at `lo` over the sea or lower, 0 at `hi` or higher."""
        r = N.new('ShaderNodeMapRange'); r.interpolation_type = 'SMOOTHSTEP'
        r.inputs['From Min'].default_value = SEA + hi; r.inputs['From Max'].default_value = SEA + lo
        L.new(sep.outputs['Z'], r.inputs['Value'])
        return r.outputs[0]

    # Where the scans go: trodden dry sand (3.5 m across) and wind-rippled sand (3 m) in patches on the top and the
    # upper face; wet, tide-rippled sand below, where the sea reaches.
    trod, ripple, wet_s = mapping(3.5, 0.37), mapping(3.0, -0.52), mapping(2.4, 0.21)
    patch = N.new('ShaderNodeTexNoise'); patch.inputs['Scale'].default_value = 0.17; patch.inputs['Detail'].default_value = 3.0
    L.new(geo.outputs['Position'], patch.inputs['Vector'])
    pm = N.new('ShaderNodeMapRange'); pm.interpolation_type = 'SMOOTHSTEP'
    pm.inputs['From Min'].default_value = 0.40; pm.inputs['From Max'].default_value = 0.60
    L.new(patch.outputs['Fac'], pm.inputs['Value'])
    wet = ramp(0.16, 0.36)

    def maps(kind, colour=False):
        dry = mix(acg(nt, 'Ground054', kind, trod, colour).outputs['Color'], acg(nt, 'Ground055L', kind, ripple, colour).outputs['Color'], pm.outputs[0])
        return mix(dry, acg(nt, 'Ground061', kind, wet_s, colour).outputs['Color'], wet)

    col = maps('Color', True)
    # Wet sand is darker and shines; the wetter, the more.
    soak = ramp(0.02, 0.30)
    dark = N.new('ShaderNodeMixRGB'); dark.blend_type = 'MULTIPLY'
    dark.inputs['Color2'].default_value = (0.50, 0.47, 0.43, 1)
    L.new(soak, dark.inputs['Fac']); L.new(col, dark.inputs['Color1'])
    L.new(dark.outputs['Color'], bsdf.inputs['Base Color'])
    rough = N.new('ShaderNodeMixRGB'); rough.blend_type = 'MULTIPLY'; rough.inputs['Color2'].default_value = (0.38, 0.38, 0.38, 1)
    L.new(soak, rough.inputs['Fac']); L.new(maps('Roughness'), rough.inputs['Color1'])
    L.new(rough.outputs['Color'], bsdf.inputs['Roughness'])
    nm = N.new('ShaderNodeNormalMap')
    L.new(maps('NormalGL'), nm.inputs['Color']); L.new(nm.outputs['Normal'], bsdf.inputs['Normal'])
    # The scans' own relief, as real geometry: hollows and heaps a hand deep in the dry sand, ripples a finger high,
    # the wet sand nearly smooth. It fades with distance (under the sea it is gone).
    dist = N.new('ShaderNodeVectorMath'); dist.operation = 'LENGTH'
    L.new(geo.outputs['Position'], dist.inputs[0])
    near = N.new('ShaderNodeMapRange'); near.interpolation_type = 'SMOOTHSTEP'
    near.inputs['From Min'].default_value = 25.0; near.inputs['From Max'].default_value = 90.0
    near.inputs['To Min'].default_value = 1.0; near.inputs['To Max'].default_value = 0.0
    L.new(dist.outputs['Value'], near.inputs['Value'])

    def height(node, depth):
        a = N.new('ShaderNodeMath'); a.operation = 'SUBTRACT'; a.inputs[1].default_value = 0.5
        L.new(node.outputs['Color'], a.inputs[0])
        b = N.new('ShaderNodeMath'); b.operation = 'MULTIPLY'; b.inputs[1].default_value = depth
        L.new(a.outputs[0], b.inputs[0])
        return b.outputs[0]

    hd = mix(height(acg(nt, 'Ground054', 'Displacement', trod), 0.085), height(acg(nt, 'Ground055L', 'Displacement', ripple), 0.030), pm.outputs[0])
    hw = height(acg(nt, 'Ground061', 'Displacement', wet_s), 0.014)
    hh = mix(hd, hw, wet)
    hn = N.new('ShaderNodeMath'); hn.operation = 'MULTIPLY'
    L.new(hh, hn.inputs[0]); L.new(near.outputs[0], hn.inputs[1])
    disp = N.new('ShaderNodeDisplacement'); disp.inputs['Midlevel'].default_value = 0.0; disp.inputs['Scale'].default_value = 1.0
    L.new(hn.outputs[0], disp.inputs['Height'])
    L.new(disp.outputs[0], out.inputs['Displacement'])
    ob.data.materials.append(mat)


def build():
    beach()
    # On the sand beside us: a few sea-worn stones (a scan, true size). Nothing else stands between us and the
    # horizon: the Sun rises out of the open sea and sets into it.
    place('sand_rocks_small_01', -2.7, 4.3, 1.0, 200.0, sink=0.30, base=SEA + BEACH_SLOPE * (SHORE_AHEAD - 4.3) - 0.02)


def shore_meta(out):
    """Tells the phone how the beach lies, for the waves it draws on it."""
    import json
    path = os.path.join(out, 'land', 'land.json')
    if os.path.exists(path):
        meta = json.load(open(path))
        meta['shore'] = [SHORE_AHEAD, SHORE_SLANT, BEACH_SLOPE, BED_SLOPE]
        json.dump(meta, open(path, 'w'), indent=1)


bake.run(build, (0.0, 0.0, EYE), water_level=SEA, exposure=-2.4)
shore_meta(bake.args()[1])
