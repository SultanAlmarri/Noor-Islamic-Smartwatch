"""The Ocean scenery: a rocky shore at low tide, built from photogrammetry of real coasts.

Everything solid here is a scan of a real place (Poly Haven, CC0: see tools/scenery/sources/ocean.md): the damp sand
underfoot, the rocks on the flat and in the shallows, the cliffs that frame the view, the islets far out. The sea
itself is drawn by the phone (waves, the mirrored sky, the Sun's road on the water).

    blender -b --factory-startup -P tools/scenery/ocean.py -- preview <out dir> [azimuth elevation]

The scans are expected in %USERPROFILE%/ProjectMinhad/NoorTools/assets/polyhaven/<id>/ (their .blend at 2k with its
textures folder), as the Poly Haven API serves them.
"""
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

EYE = 1.75                  # metres above the sea: standing on the damp sand of the flat
SEA = 0.0
SCANS = os.environ.get('NOOR_POLYHAVEN', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'assets', 'polyhaven'))
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


def flat():
    """The tidal flat: scanned damp beach sand, nearly level, its hollows under a film of water, falling away into
    the sea."""
    rng = np.random.default_rng(3)
    ys = np.concatenate([np.linspace(-30, 0, 16)[:-1], np.geomspace(1.0, 60000.0, 170) - 1.0])
    half = np.geomspace(1.0, 40000.0, 100) - 1.0
    xs = np.concatenate([-half[::-1], half[1:]])
    X, Y = np.meshgrid(xs, ys)
    bars = fbm(X * 1.0, Y * 2.2, rng, 4, 1 / 7.0) * 0.045 + fbm(X, Y, rng, 3, 1 / 31.0) * 0.05
    z = 0.045 - 0.0040 * Y + bars * np.clip(1.0 - Y / 260.0, 0.0, 1.0)
    z = np.where(Y > 60, z - (Y - 60) * 0.006, z)
    z = np.where(Y > 600, z - (Y - 600) * 0.03, z)
    z = np.maximum(z, -45.0)
    z = z + 0.10 * np.exp(-(X * X + Y * Y) / (2 * 2.4 ** 2))       # dry where we stand
    ob = bake.grid_mesh('Flat', xs, ys, z + SEA)
    bake.adaptive(ob, 1.0)

    mat = bpy.data.materials.new('DampSand')
    mat.use_nodes = True
    mat.displacement_method = 'DISPLACEMENT'
    nt = mat.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N['Principled BSDF']
    out = N['Material Output']
    geo = N.new('ShaderNodeNewGeometry')
    # The scan is 1.87 m across: laid out by the ground's own metres, and once more larger and turned, the two mixed
    # by patches, so no tile is seen twice the same.
    m1 = N.new('ShaderNodeMapping'); m1.inputs['Scale'].default_value = (1 / 1.87, 1 / 1.87, 1.0)
    m2 = N.new('ShaderNodeMapping'); m2.inputs['Scale'].default_value = (1 / 4.3, 1 / 4.3, 1.0); m2.inputs['Rotation'].default_value = (0, 0, 0.66)
    L.new(geo.outputs['Position'], m1.inputs['Vector']); L.new(geo.outputs['Position'], m2.inputs['Vector'])
    patch = N.new('ShaderNodeTexNoise'); patch.inputs['Scale'].default_value = 0.23; patch.inputs['Detail'].default_value = 3.0
    L.new(geo.outputs['Position'], patch.inputs['Vector'])
    pm = N.new('ShaderNodeMapRange'); pm.interpolation_type = 'SMOOTHSTEP'
    pm.inputs['From Min'].default_value = 0.42; pm.inputs['From Max'].default_value = 0.58
    L.new(patch.outputs['Fac'], pm.inputs['Value'])

    def both(kind, colour=False):
        a = texture(nt, 'damp_beach_sand_02', kind, m1.outputs[0], colour)
        b = texture(nt, 'damp_beach_sand_02', kind, m2.outputs[0], colour)
        mx = N.new('ShaderNodeMixRGB')
        L.new(pm.outputs[0], mx.inputs['Fac']); L.new(a.outputs['Color'], mx.inputs['Color1']); L.new(b.outputs['Color'], mx.inputs['Color2'])
        return mx.outputs['Color']

    diff = both('diff', True)
    sep = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Position'], sep.inputs[0])
    # Wetter (darker, glossier) the nearer the water's level it lies.
    wet = N.new('ShaderNodeMapRange'); wet.interpolation_type = 'SMOOTHSTEP'
    wet.inputs['From Min'].default_value = SEA + 0.11; wet.inputs['From Max'].default_value = SEA + 0.02
    L.new(sep.outputs['Z'], wet.inputs['Value'])
    dark = N.new('ShaderNodeMixRGB'); dark.blend_type = 'MULTIPLY'
    dark.inputs['Color2'].default_value = (0.62, 0.60, 0.57, 1)
    L.new(wet.outputs[0], dark.inputs['Fac']); L.new(diff, dark.inputs['Color1'])
    L.new(dark.outputs['Color'], bsdf.inputs['Base Color'])
    rough = both('rough')
    rw = N.new('ShaderNodeMixRGB'); rw.blend_type = 'MULTIPLY'; rw.inputs['Color2'].default_value = (0.45, 0.45, 0.45, 1)
    L.new(wet.outputs[0], rw.inputs['Fac']); L.new(rough, rw.inputs['Color1'])
    L.new(rw.outputs['Color'], bsdf.inputs['Roughness'])
    nor = both('nor_gl')
    nm = N.new('ShaderNodeNormalMap')
    L.new(nor, nm.inputs['Color']); L.new(nm.outputs['Normal'], bsdf.inputs['Normal'])
    # Its real relief near the eye (the scan's own height map), fading out with distance.
    disp_tex = both('disp')
    dist = N.new('ShaderNodeVectorMath'); dist.operation = 'LENGTH'
    L.new(geo.outputs['Position'], dist.inputs[0])
    near = N.new('ShaderNodeMapRange'); near.interpolation_type = 'SMOOTHSTEP'
    near.inputs['From Min'].default_value = 30.0; near.inputs['From Max'].default_value = 160.0
    near.inputs['To Min'].default_value = 0.045; near.inputs['To Max'].default_value = 0.0
    L.new(dist.outputs['Value'], near.inputs['Value'])
    disp = N.new('ShaderNodeDisplacement'); disp.inputs['Midlevel'].default_value = 0.5
    L.new(disp_tex, disp.inputs['Height']); L.new(near.outputs[0], disp.inputs['Scale'])
    L.new(disp.outputs[0], out.inputs['Displacement'])
    bake.add_mask(mat, wind=0.0, snow=0.0, water_level=SEA)
    ob.data.materials.append(mat)


def build():
    flat()
    # Underfoot and in the first shallows: sand with small stones, shelves of rock (true size).
    place('sand_rocks_small_01', 1.8, 5.2, 1.0, 20.0, sink=0.35, base=SEA + 0.06)
    place('coast_land_rocks_02', 9.5, 15.0, 1.0, -25.0, sink=0.30)
    place('coast_land_rocks_03', -7.5, 11.0, 1.0, 140.0, sink=0.30)
    place('coast_rocks_03', -17.0, 34.0, 1.0, 35.0, sink=0.22)
    place('coast_rocks_05', 5.5, 31.0, 1.6, 70.0, sink=0.30)
    # A reef the tide has left, further out.
    place('coast_rocks_01', 44.0, 105.0, 1.0, -15.0, sink=0.35, lod=2)
    place('coast_rocks_02', -70.0, 150.0, 1.0, 60.0, sink=0.40, lod=2)
    # The picture's edges: sea stacks, near and tall. They are scans of whole boulders, many times larger than
    # life (rock looks alike at every size), so they are solid all round, as a stack is. (Nothing may stand in the
    # Sun's way between bearings -16.5 and 16.5 degrees but what is under its path; see bake.skyline_limit.)
    place('namaqualand_boulder_04', -80.0, 200.0, 13.0, 40.0, sink=0.10, lod=0)
    place('namaqualand_boulder_03', -152.0, 420.0, 7.0, 110.0, sink=0.12, lod=0)
    place('namaqualand_boulder_03', 97.0, 232.0, 10.0, -30.0, sink=0.10, lod=0)
    place('coast_rocks_05', 262.0, 665.0, 28.0, 150.0, sink=0.10)
    # Out in the bay, under the Sun's arch: a rocky island, and islets beyond it.
    place('coast_rocks_05', -80.0, 2300.0, 100.0, 30.0, sink=0.10)
    place('namaqualand_boulder_04', 425.0, 3300.0, 58.0, 200.0, sink=0.18, lod=2)
    place('boulder_01', -620.0, 5200.0, 120.0, 80.0, sink=0.30, lod=2)


bake.run(build, (0.0, 0.0, EYE), aovs=('mask',), water_level=SEA, exposure=-2.4)
