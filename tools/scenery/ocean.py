"""The Ocean scenery: the open sea at low tide, from the wet sand flat: pools and bars of rippled sand that mirror
the sky, a few dark stones, sea stacks at the picture's edges, rocky islets far out.

The sea itself is drawn by the phone (waves, the mirrored sky, the Sun's road on the water); here are the things it
stands in and on: the sand we stand on, the bed under the shallows, the rocks.

    blender -b --factory-startup -P tools/scenery/ocean.py -- preview <out dir> [azimuth elevation]
"""
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

EYE = 1.75                  # metres above the sea: standing on a sand bar of the tidal flat
SEA = 0.0


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


def beach_and_bed():
    """The sand from behind us down into the water, and the bed under the sea out to the horizon."""
    rng = np.random.default_rng(3)
    ys = np.concatenate([np.linspace(-30, 0, 16)[:-1], np.geomspace(1.0, 60000.0, 170) - 1.0])
    half = np.geomspace(1.0, 40000.0, 100) - 1.0
    xs = np.concatenate([-half[::-1], half[1:]])
    X, Y = np.meshgrid(xs, ys)
    # A tidal flat: nearly level, falling seaward a hand's breadth in ten paces, with low bars and shallow pools that
    # wander across it; a few steps out everything is under a film of water, further out the sea deepens.
    bars = fbm(X * 1.0, Y * 2.2, rng, 4, 1 / 7.0) * 0.050 + fbm(X, Y, rng, 3, 1 / 31.0) * 0.045
    z = 0.085 - 0.0042 * Y + bars * np.clip(1.0 - Y / 260.0, 0.0, 1.0)
    z = np.where(Y > 60, z - (Y - 60) * 0.006, z)
    z = np.where(Y > 600, z - (Y - 600) * 0.03, z)
    z = np.maximum(z, -45.0)
    # (Dry where we stand.)
    z = z + 0.06 * np.exp(-(X * X + Y * Y) / (2 * 2.2 ** 2))
    ob = bake.grid_mesh('Seabed', xs, ys, z + (SEA - 0.0))
    bake.adaptive(ob, 1.0)

    mat = bpy.data.materials.new('Sand')
    mat.use_nodes = True
    mat.displacement_method = 'DISPLACEMENT'
    nt = mat.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N['Principled BSDF']
    out = N['Material Output']
    geo = N.new('ShaderNodeNewGeometry')
    sep = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Position'], sep.inputs[0])
    # Dry sand is pale; near and under the water it is wet and darker; ripple marks under the shallows.
    wet = N.new('ShaderNodeMapRange'); wet.interpolation_type = 'SMOOTHSTEP'
    wet.inputs['From Min'].default_value = SEA + 0.16; wet.inputs['From Max'].default_value = SEA + 0.03
    L.new(sep.outputs['Z'], wet.inputs['Value'])
    patch = N.new('ShaderNodeTexNoise'); patch.inputs['Scale'].default_value = 0.11; patch.inputs['Detail'].default_value = 5.0
    L.new(geo.outputs['Position'], patch.inputs['Vector'])
    ramp = N.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.3; ramp.color_ramp.elements[0].color = (0.52, 0.42, 0.28, 1)
    ramp.color_ramp.elements[1].position = 0.75; ramp.color_ramp.elements[1].color = (0.70, 0.60, 0.44, 1)
    L.new(patch.outputs['Fac'], ramp.inputs['Fac'])
    dark = N.new('ShaderNodeMixRGB'); dark.blend_type = 'MULTIPLY'
    dark.inputs['Color2'].default_value = (0.50, 0.47, 0.42, 1)
    L.new(wet.outputs[0], dark.inputs['Fac']); L.new(ramp.outputs['Color'], dark.inputs['Color1'])
    L.new(dark.outputs['Color'], bsdf.inputs['Base Color'])
    rough = N.new('ShaderNodeMapRange')
    rough.inputs['To Min'].default_value = 0.85; rough.inputs['To Max'].default_value = 0.30
    L.new(wet.outputs[0], rough.inputs['Value']); L.new(rough.outputs[0], bsdf.inputs['Roughness'])
    # Grain, footless: small pocks and the ripple marks the swash leaves.
    rip = N.new('ShaderNodeTexWave'); rip.wave_type = 'BANDS'; rip.bands_direction = 'Y'
    rip.inputs['Scale'].default_value = 3.6; rip.inputs['Distortion'].default_value = 5.5; rip.inputs['Detail'].default_value = 2.0
    L.new(geo.outputs['Position'], rip.inputs['Vector'])
    grain = N.new('ShaderNodeTexNoise'); grain.inputs['Scale'].default_value = 28.0; grain.inputs['Detail'].default_value = 4.0
    L.new(geo.outputs['Position'], grain.inputs['Vector'])
    dist = N.new('ShaderNodeVectorMath'); dist.operation = 'LENGTH'
    L.new(geo.outputs['Position'], dist.inputs[0])
    near = N.new('ShaderNodeMapRange'); near.interpolation_type = 'SMOOTHSTEP'
    near.inputs['From Min'].default_value = 40.0; near.inputs['From Max'].default_value = 220.0
    near.inputs['To Min'].default_value = 1.0; near.inputs['To Max'].default_value = 0.0
    L.new(dist.outputs['Value'], near.inputs['Value'])
    h1 = N.new('ShaderNodeMath'); h1.operation = 'MULTIPLY'; h1.inputs[1].default_value = 0.030
    L.new(rip.outputs['Fac'], h1.inputs[0])
    h1w = N.new('ShaderNodeMath'); h1w.operation = 'MULTIPLY'; h1w.inputs[1].default_value = 1.0
    L.new(h1.outputs[0], h1w.inputs[0])
    h2 = N.new('ShaderNodeMath'); h2.operation = 'MULTIPLY'; h2.inputs[1].default_value = 0.006
    L.new(grain.outputs['Fac'], h2.inputs[0])
    hs = N.new('ShaderNodeMath'); hs.operation = 'ADD'
    L.new(h1w.outputs[0], hs.inputs[0]); L.new(h2.outputs[0], hs.inputs[1])
    hn = N.new('ShaderNodeMath'); hn.operation = 'MULTIPLY'
    L.new(hs.outputs[0], hn.inputs[0]); L.new(near.outputs[0], hn.inputs[1])
    disp = N.new('ShaderNodeDisplacement'); disp.inputs['Midlevel'].default_value = 0.0; disp.inputs['Scale'].default_value = 1.0
    L.new(hn.outputs[0], disp.inputs['Height'])
    L.new(disp.outputs[0], out.inputs['Displacement'])
    bake.add_mask(mat, wind=0.0, snow=0.0, water_level=SEA)
    ob.data.materials.append(mat)


def rock_material():
    mat = bpy.data.materials.new('SeaRock')
    mat.use_nodes = True
    mat.displacement_method = 'DISPLACEMENT'
    nt = mat.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N['Principled BSDF']
    out = N['Material Output']
    geo = N.new('ShaderNodeNewGeometry')
    sep = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Position'], sep.inputs[0])
    # Dark basalt in beds; a black wet band where the sea reaches; paler, lichened and bird-whitened tops.
    beds = N.new('ShaderNodeTexNoise'); beds.inputs['Scale'].default_value = 0.35; beds.inputs['Detail'].default_value = 6.0
    bmap = N.new('ShaderNodeMapping'); bmap.inputs['Scale'].default_value = (0.12, 0.12, 1.6)
    L.new(geo.outputs['Position'], bmap.inputs['Vector']); L.new(bmap.outputs[0], beds.inputs['Vector'])
    ramp = N.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.30; ramp.color_ramp.elements[0].color = (0.035, 0.032, 0.030, 1)
    ramp.color_ramp.elements[1].position = 0.80; ramp.color_ramp.elements[1].color = (0.16, 0.13, 0.105, 1)
    L.new(beds.outputs['Fac'], ramp.inputs['Fac'])
    tide = N.new('ShaderNodeMapRange'); tide.interpolation_type = 'SMOOTHSTEP'
    tide.inputs['From Min'].default_value = SEA + 2.2; tide.inputs['From Max'].default_value = SEA + 0.6
    L.new(sep.outputs['Z'], tide.inputs['Value'])
    wetm = N.new('ShaderNodeMixRGB'); wetm.blend_type = 'MULTIPLY'; wetm.inputs['Color2'].default_value = (0.35, 0.36, 0.38, 1)
    L.new(tide.outputs[0], wetm.inputs['Fac']); L.new(ramp.outputs['Color'], wetm.inputs['Color1'])
    nrm = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Normal'], nrm.inputs[0])
    topm = N.new('ShaderNodeMapRange'); topm.interpolation_type = 'SMOOTHSTEP'
    topm.inputs['From Min'].default_value = 0.72; topm.inputs['From Max'].default_value = 0.95
    L.new(nrm.outputs['Z'], topm.inputs['Value'])
    high = N.new('ShaderNodeMapRange'); high.interpolation_type = 'SMOOTHSTEP'
    high.inputs['From Min'].default_value = SEA + 3.0; high.inputs['From Max'].default_value = SEA + 9.0
    L.new(sep.outputs['Z'], high.inputs['Value'])
    cap = N.new('ShaderNodeMath'); cap.operation = 'MULTIPLY'
    L.new(topm.outputs[0], cap.inputs[0]); L.new(high.outputs[0], cap.inputs[1])
    capc = N.new('ShaderNodeMixRGB'); capc.inputs['Color2'].default_value = (0.30, 0.30, 0.22, 1)
    L.new(cap.outputs[0], capc.inputs['Fac']); L.new(wetm.outputs['Color'], capc.inputs['Color1'])
    L.new(capc.outputs['Color'], bsdf.inputs['Base Color'])
    rough = N.new('ShaderNodeMapRange'); rough.inputs['To Min'].default_value = 0.9; rough.inputs['To Max'].default_value = 0.35
    L.new(tide.outputs[0], rough.inputs['Value']); L.new(rough.outputs[0], bsdf.inputs['Roughness'])
    # Cracked and blocky: cells at two sizes push the surface in and out along its normal.
    v1 = N.new('ShaderNodeTexVoronoi'); v1.feature = 'DISTANCE_TO_EDGE'; v1.inputs['Scale'].default_value = 0.22
    v2 = N.new('ShaderNodeTexVoronoi'); v2.feature = 'F1'; v2.inputs['Scale'].default_value = 0.9
    n3 = N.new('ShaderNodeTexNoise'); n3.inputs['Scale'].default_value = 3.0; n3.inputs['Detail'].default_value = 6.0
    for nd in (v1, v2, n3):
        L.new(bmap.outputs[0] if nd is v1 else geo.outputs['Position'], nd.inputs['Vector'])
    a1 = N.new('ShaderNodeMath'); a1.operation = 'MULTIPLY'; a1.inputs[1].default_value = 3.2
    L.new(v1.outputs['Distance'], a1.inputs[0])
    a2 = N.new('ShaderNodeMath'); a2.operation = 'MULTIPLY'; a2.inputs[1].default_value = 0.9
    L.new(v2.outputs['Distance'], a2.inputs[0])
    a3 = N.new('ShaderNodeMath'); a3.operation = 'MULTIPLY'; a3.inputs[1].default_value = 0.35
    L.new(n3.outputs['Fac'], a3.inputs[0])
    s1 = N.new('ShaderNodeMath'); s1.operation = 'ADD'; L.new(a1.outputs[0], s1.inputs[0]); L.new(a2.outputs[0], s1.inputs[1])
    s2 = N.new('ShaderNodeMath'); s2.operation = 'ADD'; L.new(s1.outputs[0], s2.inputs[0]); L.new(a3.outputs[0], s2.inputs[1])
    disp = N.new('ShaderNodeDisplacement'); disp.inputs['Midlevel'].default_value = 1.0; disp.inputs['Scale'].default_value = 1.0
    L.new(s2.outputs[0], disp.inputs['Height'])
    L.new(disp.outputs[0], out.inputs['Displacement'])
    bake.add_mask(mat, wind=0.0, snow=0.0, water_level=SEA)
    return mat


def stack(rng, mat, x, y, width, height, lean=0.0):
    """A sea stack or a skerry: a column of rock with ledges, wider at its foot, standing in the sea."""
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=6, radius=1.0, location=(x, y, SEA))
    ob = bpy.context.object
    me = ob.data
    co = np.empty(len(me.vertices) * 3, np.float32)
    me.vertices.foreach_get('co', co)
    co = co.reshape(-1, 3)
    u = co[:, 2]                                            # -1 foot .. 1 top
    t = (u + 1) / 2
    # Its outline: ledges where harder beds stand out, a flat top, a broad foot under the water.
    prof = 0.55 + 0.45 * (1 - t) ** 1.6 + 0.07 * np.sin(t * 19.0 + rng.uniform(0, 6)) + 0.05 * np.sin(t * 43.0 + rng.uniform(0, 6))
    ang = np.arctan2(co[:, 1], co[:, 0])
    lobes = 1.0 + 0.16 * np.sin(ang * 2 + rng.uniform(0, 6)) + 0.10 * np.sin(ang * 5 + rng.uniform(0, 6)) + 0.06 * np.sin(ang * 9 + t * 6)
    r = np.hypot(co[:, 0], co[:, 1])
    k = np.clip(r, 0, 1) ** 0.45 / np.maximum(r, 1e-4)      # squarer than a ball: walls
    co[:, 0] = co[:, 0] * k * prof * lobes * width * 0.5 + lean * t * height
    co[:, 1] = co[:, 1] * k * prof * lobes * width * 0.5
    co[:, 2] = np.where(u > 0, np.tanh(u * 2.2) / math.tanh(2.2), u * 0.35) * height + 0.0
    co[:, 2] = np.where(u > 0, co[:, 2] * (0.93 + 0.07 * np.sin(ang * 3 + 1.0)), co[:, 2])
    me.vertices.foreach_set('co', co.ravel())
    me.update()
    for p in me.polygons:
        p.use_smooth = True
    bake.adaptive(ob, 1.0)
    ob.data.materials.append(mat)
    return ob


def build():
    rng = np.random.default_rng(12)
    beach_and_bed()
    rock = rock_material()
    pebble = bpy.data.materials.new('Stone')
    pebble.use_nodes = True
    pb = pebble.node_tree.nodes['Principled BSDF']
    pb.inputs['Base Color'].default_value = (0.045, 0.043, 0.042, 1)
    pb.inputs['Roughness'].default_value = 0.45
    bake.add_mask(pebble, water_level=SEA)
    # The picture's edges: tall stacks, near. (Nothing may stand in the Sun's way between bearings -16.5 and 16.5
    # degrees but what is under its path; see bake.skyline_limit.)
    stack(rng, rock, -175.0, 430.0, 46.0, 62.0, lean=0.05)
    stack(rng, rock, -128.0, 300.0, 16.0, 24.0)
    stack(rng, rock, 152.0, 330.0, 30.0, 38.0, lean=-0.06)
    stack(rng, rock, 262.0, 590.0, 60.0, 34.0)
    # Far out, rocky islets: in the middle of the picture, well under the Sun's arch.
    for x, y, w, h in ((-190.0, 2200.0, 320.0, 70.0), (420.0, 3400.0, 260.0, 40.0), (-60.0, 5200.0, 420.0, 22.0)):
        stack(rng, rock, x, y, w, h)
    # On the flat: a few dark stones, rounded by the sea, each in its own small pool.
    for i in range(6):
        x = rng.uniform(-7.0, 7.0); y = rng.uniform(4.5, 26.0)
        r = rng.uniform(0.22, 0.6)
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=5, radius=1.0, location=(x, y, SEA + 0.02))
        ob = bpy.context.object
        ob.scale = (r * rng.uniform(1.0, 1.7), r * rng.uniform(0.8, 1.3), r * rng.uniform(0.45, 0.7))
        ob.rotation_euler = (0.0, 0.0, rng.uniform(0, 3.1))
        for p in ob.data.polygons:
            p.use_smooth = True
        ob.data.materials.append(pebble)


bake.run(build, (0.0, 0.0, EYE), aovs=('mask',), water_level=SEA, exposure=-2.4)
