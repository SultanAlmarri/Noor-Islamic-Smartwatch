"""What grows in the Red Rock scenery (canyon.py): the creek's trees and willows, the junipers of the slopes, the
grass of the banks; and the stones and the driftwood the floods leave.

No scan of a cottonwood, a sycamore or a juniper exists that may be shipped, so the trees are grown branch by branch
(broadleaf.py) to the measure of the real ones: Fremont cottonwood (to 24 m, a broad open crown), Arizona sycamore
(white limbs, large leaves), Goodding's willow, the coyote willow's thickets at the water's edge, velvet ash, and
on the dry slopes one-seed juniper and pinyon. Where they stand follows the land (canyon_terrain.py): the gallery
wood on the creek's flood plain, willows on its bars, junipers on what is neither steep nor wet.

The season (NOOR_SEASON) is in the leaves: summer green; autumn gold cottonwoods, tan sycamores, yellow ash and
willows still partly green (no two trees at the same stage); winter bare, with the willows' red-gold stems; spring
small fresh leaves.
"""
import math
import os

import bpy
import numpy as np

import bake
import broadleaf

C = None            # canyon.py's module (the land's height, helpers, the season)


# ---- Kinds of tree: the shapes -----------------------------------------------------------------------------------
# (`size`: the tree is grown this many times larger and stood that much smaller: a bush has its leaves to the ground)
KINDS = {
    'cottonwood': dict(leaf=0.16, per_twig=34, trees=[
        dict(seed=101, height=21.0, radius=7.4, trunk=5.0, girth=0.75, points=4400, droop=0.2, masses=1.3),
        dict(seed=102, height=25.0, radius=7.8, trunk=6.5, girth=0.95, points=4800, droop=0.15, masses=1.3),
        dict(seed=103, height=16.5, radius=5.6, trunk=4.0, girth=0.50, points=4200, masses=1.3),
        dict(seed=104, height=12.0, radius=3.9, trunk=3.0, girth=0.30, points=3000, masses=1.2)]),
    'sycamore': dict(leaf=0.21, per_twig=24, trees=[
        dict(seed=111, height=17.0, radius=8.4, trunk=3.0, girth=0.85, points=4800, masses=0.8, droop=0.3),
        dict(seed=112, height=13.0, radius=6.2, trunk=2.4, girth=0.55, points=3600, masses=0.8)]),
    'ash': dict(leaf=0.15, per_twig=32, trees=[
        dict(seed=116, height=10.5, radius=4.6, trunk=2.4, girth=0.34, points=3200),
        dict(seed=117, height=8.0, radius=3.6, trunk=1.8, girth=0.24, points=2400)]),
    'willow': dict(leaf=0.15, per_twig=36, trees=[
        dict(seed=121, height=9.0, radius=5.2, trunk=1.3, girth=0.42, points=3400, droop=0.7),
        dict(seed=122, height=7.0, radius=4.0, trunk=0.9, girth=0.30, points=2600, droop=0.6)]),
    'thicket': dict(leaf=0.26, per_twig=30, size=2.0, trees=[
        dict(seed=131, height=7.4, radius=4.8, trunk=0.6, girth=0.22, points=1300, step=0.36, kill=0.7),
        dict(seed=132, height=5.4, radius=4.0, trunk=0.5, girth=0.18, points=1000, step=0.36, kill=0.7),
        dict(seed=133, height=8.4, radius=3.6, trunk=0.6, girth=0.20, points=1000, step=0.36, kill=0.7)]),
    'juniper': dict(leaf=0.40, per_twig=30, size=2.0, evergreen=True, trees=[
        dict(seed=141, height=8.4, radius=4.8, trunk=1.0, girth=0.60, points=1500, masses=1.6, droop=0.1),
        dict(seed=142, height=6.4, radius=4.2, trunk=0.8, girth=0.50, points=1200, masses=1.6, droop=0.1),
        dict(seed=143, height=10.5, radius=4.4, trunk=1.4, girth=0.55, points=1500, masses=1.5, droop=0.0)]),
    # The brush of the dry ground (what scrub oak, manzanita, cliffrose and rabbitbrush are to such slopes).
    'brush': dict(leaf=0.34, per_twig=22, size=3.0, evergreen=True, trees=[
        dict(seed=151, height=3.6, radius=3.0, trunk=0.5, girth=0.16, points=520, step=0.3, kill=0.6, masses=1.4),
        dict(seed=152, height=3.0, radius=3.4, trunk=0.5, girth=0.16, points=520, step=0.3, kill=0.6, masses=1.4)]),
}

G = (0.040, 0.088, 0.016)           # a summer leaf
# Leaf colours through a kind's range (position 0..1 along it: the tree's own stage, a little each leaf's own).
PALETTE = {
    'summer': {
        'cottonwood': [(0.0, (0.036, 0.086, 0.016)), (0.5, (0.054, 0.116, 0.020)), (1.0, (0.080, 0.150, 0.027))],
        'sycamore': [(0.0, (0.040, 0.084, 0.020)), (1.0, (0.072, 0.128, 0.030))],
        'ash': [(0.0, (0.034, 0.080, 0.018)), (1.0, (0.062, 0.120, 0.024))],
        'willow': [(0.0, (0.054, 0.100, 0.030)), (1.0, (0.090, 0.142, 0.046))],
        'thicket': [(0.0, (0.064, 0.110, 0.036)), (1.0, (0.104, 0.154, 0.054))],
        'juniper': [(0.0, (0.026, 0.044, 0.018)), (1.0, (0.046, 0.066, 0.026))],
        'brush': [(0.0, (0.060, 0.074, 0.034)), (1.0, (0.105, 0.118, 0.056))],
    },
    'autumn': {
        'cottonwood': [(0.0, G), (0.16, (0.070, 0.118, 0.022)), (0.30, (0.200, 0.215, 0.026)), (0.48, (0.500, 0.340, 0.028)),
                       (0.82, (0.520, 0.290, 0.022)), (1.0, (0.330, 0.170, 0.036))],
        'sycamore': [(0.0, (0.085, 0.115, 0.028)), (0.25, (0.230, 0.200, 0.040)), (0.6, (0.330, 0.190, 0.050)), (1.0, (0.250, 0.120, 0.040))],
        'ash': [(0.0, (0.110, 0.150, 0.026)), (0.3, (0.420, 0.360, 0.030)), (1.0, (0.560, 0.400, 0.030))],
        'willow': [(0.0, (0.065, 0.105, 0.032)), (0.25, (0.130, 0.160, 0.036)), (1.0, (0.400, 0.320, 0.036))],
        'thicket': [(0.0, (0.080, 0.118, 0.036)), (0.2, (0.200, 0.200, 0.036)), (1.0, (0.460, 0.340, 0.034))],
        'juniper': [(0.0, (0.026, 0.044, 0.018)), (1.0, (0.046, 0.066, 0.026))],
        'brush': [(0.0, (0.085, 0.085, 0.036)), (0.6, (0.170, 0.140, 0.050)), (1.0, (0.360, 0.270, 0.040))],
    },
    'spring': {
        'cottonwood': [(0.0, (0.105, 0.185, 0.030)), (1.0, (0.165, 0.250, 0.046))],
        'sycamore': [(0.0, (0.120, 0.180, 0.044)), (1.0, (0.180, 0.230, 0.070))],
        'ash': [(0.0, (0.090, 0.170, 0.030)), (1.0, (0.140, 0.220, 0.044))],
        'willow': [(0.0, (0.120, 0.200, 0.040)), (1.0, (0.190, 0.260, 0.060))],
        'thicket': [(0.0, (0.130, 0.200, 0.046)), (1.0, (0.200, 0.260, 0.066))],
        'juniper': [(0.0, (0.026, 0.046, 0.018)), (1.0, (0.048, 0.070, 0.026))],
        'brush': [(0.0, (0.060, 0.096, 0.032)), (1.0, (0.110, 0.150, 0.050))],
    },
}
PALETTE['winter'] = dict(PALETTE['summer'])     # (only the junipers and the brush keep their leaves)
PALETTE['winter']['brush'] = [(0.0, (0.085, 0.080, 0.050)), (1.0, (0.170, 0.145, 0.095))]
# How much of a kind's range a tree's own stage spans, and each leaf's own share of it.
SPREAD = {'summer': (0.55, 0.45), 'autumn': (0.80, 0.22), 'spring': (0.55, 0.45), 'winter': (0.55, 0.45)}
# Bark and twigs: (trunk tint over the scan, colour of the bare twigs in winter).
WOOD = {
    'cottonwood': ('bark_willow_02', (1.00, 0.95, 0.88), (0.34, 0.30, 0.25)),
    'sycamore': ('bark_platanus', (1.9, 1.8, 1.6), (0.46, 0.42, 0.36)),
    'ash': ('bark_willow_02', (0.80, 0.76, 0.70), (0.26, 0.23, 0.19)),
    'willow': ('bark_willow_02', (0.75, 0.68, 0.58), (0.30, 0.22, 0.13)),
    'thicket': ('bark_willow_02', (0.9, 0.62, 0.42), (0.30, 0.19, 0.11)),
    'juniper': ('bark_willow_02', (0.80, 0.70, 0.62), (0.30, 0.25, 0.20)),
    'brush': ('bark_willow_02', (0.70, 0.62, 0.55), (0.26, 0.22, 0.18)),
}


def leaf_material(kind):
    S = C.SEASON
    mat, nt, bsdf, outn = C.new_material('Leaves_' + kind)
    g = C.Nodes(nt)
    attr = nt.nodes.new('ShaderNodeAttribute')
    attr.attribute_name = 'col'
    sep = nt.nodes.new('ShaderNodeSeparateColor')
    g.link(attr.outputs['Color'], sep.inputs[0])
    info = nt.nodes.new('ShaderNodeObjectInfo')
    own, each = SPREAD[S]
    t = g.math('ADD', g.mul(info.outputs['Random'], own), g.mul(sep.outputs[0], each), clamp=True)
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    stops = PALETTE[S][kind]
    el = ramp.color_ramp.elements
    el[0].position, el[0].color = stops[0][0], (*stops[0][1], 1.0)
    el[1].position, el[1].color = stops[-1][0], (*stops[-1][1], 1.0)
    for p, c in stops[1:-1]:
        e = el.new(p)
        e.color = (*c, 1.0)
    g.link(t, ramp.inputs['Fac'])
    col = g.tint(ramp.outputs['Color'], g.grey(g.ramp(sep.outputs[1], 0.0, 1.0, 0.72, 1.28)))
    snow = 0.0
    if S == 'winter' and kind in ('juniper', 'brush'):
        # A dusting on the junipers' sprays, heavier on some trees than on others.
        geo = nt.nodes.new('ShaderNodeNewGeometry')
        nsep = nt.nodes.new('ShaderNodeSeparateXYZ')
        g.link(geo.outputs['True Normal'], nsep.inputs[0])
        up = g.math('ABSOLUTE', nsep.outputs['Z'])
        dust = g.mul(g.ramp(up, 0.45, 0.85), g.ramp(g.math('ADD', sep.outputs[2], g.mul(info.outputs['Random'], 0.5)), 0.45, 0.95))
        col = g.mix(dust, col, C.SNOW)
    g.link(col, bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.5
    bsdf.inputs['Specular IOR Level'].default_value = 0.35
    if kind not in ('juniper', 'brush'):
        # Light through a leaf is yellower and brighter than light off it.
        thru = nt.nodes.new('ShaderNodeBsdfTranslucent')
        g.link(g.tint(col, (2.2, 2.1, 1.0)), thru.inputs['Color'])
        mix = nt.nodes.new('ShaderNodeMixShader')
        mix.inputs['Fac'].default_value = 0.32
        g.link(bsdf.outputs['BSDF'], mix.inputs[1])
        g.link(thru.outputs['BSDF'], mix.inputs[2])
        g.link(mix.outputs[0], outn.inputs['Surface'])
    bake.add_mask(mat, wind=1.0 if kind != 'juniper' else 0.5, snow=0.35 if kind == 'juniper' else 0.2)
    return mat


def wood_material(kind):
    scan, tint, twig = WOOD[kind]
    mat, nt, bsdf, outn = C.new_material('Wood_' + kind)
    g = C.Nodes(nt)
    tc = nt.nodes.new('ShaderNodeTexCoord')
    vec = g.mapping(tc.outputs['Object'], (0.8, 0.8, 0.4))
    diff = nt.nodes.new('ShaderNodeTexImage')
    diff.image = C.scan_image(scan, 'diff', True)
    diff.projection = 'BOX'
    diff.projection_blend = 0.3
    g.link(vec, diff.inputs['Vector'])
    col = g.tint(diff.outputs['Color'], tint)
    if kind == 'sycamore':
        # The sycamore sheds its bark in plates: white and pale green limbs, mottled.
        patch = g.ramp(g.noise(tc.outputs['Object'], 3.0, 3.0, 0.6), 0.40, 0.55)
        col = g.mix(patch, col, (0.50, 0.48, 0.41))
    # Thin wood is smooth and of the twigs' own colour (seen bare in winter).
    geo = nt.nodes.new('ShaderNodeAttribute')
    geo.attribute_name = 'col'
    sep = nt.nodes.new('ShaderNodeSeparateColor')
    g.link(geo.outputs['Color'], sep.inputs[0])
    col = g.mix(sep.outputs[0], col, twig)
    g.link(col, bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.85
    disp = nt.nodes.new('ShaderNodeTexImage')
    disp.image = C.scan_image(scan, 'disp')
    disp.projection = 'BOX'
    disp.projection_blend = 0.3
    g.link(vec, disp.inputs['Vector'])
    bump = nt.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.7
    bump.inputs['Distance'].default_value = 0.04
    g.link(disp.outputs['Color'], bump.inputs['Height'])
    g.link(bump.outputs['Normal'], bsdf.inputs['Normal'])
    bake.add_mask(mat, wind=0.15, snow=0.1)
    return mat


def twig_sprays(rng, tree, count, length):
    """The finest twigs of a bare tree (winter): thin slivers at the ends of its branches, where its leaves were."""
    lc = tree['leaf_co'].reshape(-1, 6, 3)
    if len(lc) == 0:
        return np.zeros((0, 3), np.float32), np.zeros((0, 4), np.int32)
    pick = rng.choice(len(lc), size=min(count, len(lc)), replace=False)
    base = lc[pick, 0]
    d = broadleaf._unit(lc[pick, 3] - lc[pick, 0] + rng.normal(size=(len(pick), 3)) * 0.02 + np.array([0.0, 0.0, 0.004]))
    tip = base + d * (length * rng.uniform(0.5, 1.3, len(pick)))[:, None]
    side = broadleaf._unit(np.cross(d, rng.normal(size=(len(pick), 3)))) * 0.006
    co = np.stack([base - side, base + side, tip + side * 0.4, tip - side * 0.4], 1).reshape(-1, 3)
    faces = np.arange(len(pick) * 4).reshape(-1, 4)
    return co.astype(np.float32), faces.astype(np.int32)


def make_tree(kind, spec, k, wood, leaf):
    """One tree of a kind as an object (wood and leaves), at the season."""
    K = KINDS[kind]
    S = C.SEASON
    bare = S == 'winter' and not K.get('evergreen')
    rng = np.random.default_rng(spec['seed'] * 7 + 1)
    per_twig, size = K['per_twig'], K['leaf']
    if S == 'spring' and not K.get('evergreen'):
        per_twig, size = int(per_twig * 0.8), size * 0.72           # the leaves are out, not yet grown
    tree = broadleaf.grow(leaves_per_twig=per_twig, leaf=size, **spec)
    wc, wf = tree['wood_co'], tree['wood_faces']
    # A value for every vertex of the wood: 0 on trunk and limbs, 1 on the thinnest (from how far it is from the
    # tube's own axis: thin tubes have their vertices close together).
    ring = wc.reshape(-1, 2, 7, 3)
    rad = np.linalg.norm(ring - ring.mean(axis=2, keepdims=True), axis=-1)            # [tubes, 2, 7]
    thin = np.clip((0.085 - rad) / 0.055, 0.0, 1.0).reshape(-1)
    wcol = np.stack([thin, thin, thin], -1)
    lc, lf = tree['leaf_co'], tree['leaf_faces']
    nl = len(lc) // 6
    r1, r2, r3 = rng.random(nl), rng.random(nl), rng.random(nl)
    lcol = np.repeat(np.stack([r1, r2, r3], -1), 6, axis=0)
    if bare:
        tw_co, tw_f = twig_sprays(rng, tree, 9000 if kind != 'thicket' else 5000, 0.9 if kind != 'thicket' else 1.3)
        wf = np.concatenate([wf, tw_f + len(wc)])
        wc = np.concatenate([wc, tw_co])
        wcol = np.concatenate([wcol, np.ones((len(tw_co), 3), np.float32)])
        lc, lf, lcol = lc[:0], lf[:0], lcol[:0]
    me = bpy.data.meshes.new('%s%d' % (kind, k))
    nv = len(wc) + len(lc)
    me.vertices.add(nv)
    me.vertices.foreach_set('co', np.concatenate([wc, lc]).astype(np.float32).ravel())
    loops = np.concatenate([wf.ravel(), lf.ravel() + len(wc)])
    me.loops.add(len(loops))
    me.loops.foreach_set('vertex_index', loops.astype(np.int32))
    me.polygons.add(len(wf) + len(lf))
    start = np.concatenate([np.arange(len(wf)) * 4, len(wf) * 4 + np.arange(len(lf)) * 3]).astype(np.int32)
    total = np.concatenate([np.full(len(wf), 4), np.full(len(lf), 3)]).astype(np.int32)
    me.polygons.foreach_set('loop_start', start)
    me.polygons.foreach_set('loop_total', total)
    me.polygons.foreach_set('use_smooth', np.concatenate([np.ones(len(wf), bool), np.zeros(len(lf), bool)]))
    me.polygons.foreach_set('material_index', np.concatenate([np.zeros(len(wf), np.int32), np.ones(len(lf), np.int32)]))
    me.update(calc_edges=True)
    a = me.color_attributes.new('col', 'FLOAT_COLOR', 'POINT')
    rgba = np.ones((nv, 4), np.float32)
    rgba[:len(wc), :3] = wcol
    rgba[len(wc):, :3] = lcol
    a.data.foreach_set('color', rgba.ravel())
    me.materials.append(wood)
    me.materials.append(leaf)
    ob = bpy.data.objects.new(me.name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob, float(tree['height']), float(tree['radius'])


# ---- Where they stand ------------------------------------------------------------------------------------------

CREEK_RISE = 0.0065       # the creek climbs this much per metre ahead of us (from the lidar's water levels)


def skyline_cap(az):
    """How high (degrees) anything planted may stand at a bearing: free, except under Cathedral Rock's domes (the
    wood of the flood plain hides its skirts, as it does there, not the rock itself) and where the Sun rises and
    sets (the Sun's path stands on the lowest skyline there: a gap in the trees keeps its day long)."""
    cap = np.full(np.shape(az), 40.0)
    cap = np.where((az > -4.5) & (az < 13.0), 5.0 + 0.5 * np.sin(az * 1.7) + 1.6 * np.clip((-1.0 - az) / 3.5, 0.0, 1.0), cap)
    cap = np.where((az >= 13.0) & (az < 17.0), 5.6, cap)
    cap = np.where((az > -17.2) & (az < -12.4), 5.2, cap)
    return cap


def candidates(rng, n, d0, d1, half=26.5):
    b = np.radians(rng.uniform(-half, half, n))
    d = np.sqrt(rng.uniform(d0 * d0, d1 * d1, n))
    return d * np.sin(b), d * np.cos(b)


def thin_out(x, y, cell):
    """Keeps one of whatever falls in the same square of `cell` metres."""
    cid = np.floor(x / cell).astype(np.int64) * 1000003 + np.floor(y / cell).astype(np.int64)
    _, first = np.unique(cid, return_index=True)
    keep = np.zeros(len(x), bool)
    keep[first] = True
    return keep


def plant(objs, name, x, y, kindsel, scale, rng, sink=0.15):
    """Stands the trees: objs = [(object, height, radius)], kindsel picks one for each place. Trees that would
    stand higher than the skyline's cap at their bearing are made smaller (to a half), or left out."""
    z = C.land_height(x, y)
    az = np.degrees(np.arctan2(x, y))
    d = np.hypot(x, y)
    h = np.array([objs[k][1] for k in kindsel]) * scale
    r = np.array([objs[k][2] for k in kindsel]) * scale
    cap = skyline_cap(az)
    # Its top as the eye sees it; its crown also reaches sideways into the neighbouring bearings.
    side = np.degrees(np.arctan2(r * 0.8, d))
    cap = np.minimum(cap, np.minimum(skyline_cap(az - side), skyline_cap(az + side)) + 1.5)
    top = np.degrees(np.arctan2(z + h - C.EYE, d))
    fit = np.where(top > cap, (np.tan(np.radians(cap)) * d + C.EYE - z) / np.maximum(h, 1e-3), 1.0)
    keep = fit > 0.5
    scale = scale * np.clip(fit, 0.5, 1.0)
    under = keep & (az > -4.0) & (az < 13.0) & (h > 5.0)
    if under.any():
        t = np.degrees(np.arctan2(z + h * np.clip(fit, 0.5, 1.0) - C.EYE, d))[under]
        print('%s: %d under the domes (of %d places there), their tops %.1f degrees up in the middle, %.1f at most; at %d to %d m'
              % (name, int(under.sum()), int(((az > -4.0) & (az < 13.0) & (h > 5.0)).sum()), np.median(t), t.max(), d[under].min(), d[under].max()), flush=True)
    n = 0
    for k, (ob, _, _) in enumerate(objs):
        sel = keep & (kindsel == k)
        if sel.any():
            C.stand('%s%d' % (name, k), ob, x[sel], y[sel], z[sel] - sink, scale[sel], rng.uniform(0.0, 2 * math.pi, int(sel.sum())))
            n += int(sel.sum())
    return n


def wood_of_the_creek(trees, rng):
    """The gallery wood of the flood plain, the willows of its bars and banks."""
    x, y = candidates(rng, 260000, 12.0, 1150.0)
    z = C.land_height(x, y)
    d = np.hypot(x, y)
    rel = z - CREEK_RISE * y                                   # metres above the creek abreast of the place
    slope = C.slope_at(x, y)
    grove = C.value_noise(x, y, 38.0, 5) * 0.65 + C.value_noise(x, y, 11.0, 6) * 0.35
    # Tall trees: on the flood plain's terrace (a metre to five above the creek), in groves.
    per_ha = 600.0 * C.smooth(0.15, 0.5, rel) * C.smooth(9.0, 5.5, rel) * (0.45 + 0.55 * C.smooth(0.26, 0.46, grove)) * C.smooth(0.5, 0.25, slope)
    per_ha += 26.0 * C.smooth(0.4, 0.9, rel) * C.smooth(1.6, 0.9, rel) * C.smooth(0.55, 0.7, grove)      # a few on the bars
    per_ha += 500.0 * C.smooth(0.5, 0.9, rel) * C.smooth(6.0, 4.0, rel) * C.smooth(95.0, 120.0, d) * C.smooth(260.0, 215.0, d)   # the wood's front
    per_ha *= 1.0 + 1.3 * C.smooth(-4.0, -11.0, np.degrees(np.arctan2(x, y)))      # (the creek itself runs on our left)
    area = math.radians(53.0) / 2 * (1150.0 ** 2 - 12.0 ** 2) / 1e4
    keep = (rng.random(len(x)) < per_ha / (len(x) / area)) & (z > 0.35)
    keep &= thin_out(x, y, 3.8)
    tx, ty, trel = x[keep], y[keep], rel[keep]
    print('places for trees of the creek:', len(tx), flush=True)
    names = ['cottonwood'] * 4 + ['sycamore'] * 2 + ['ash'] * 2 + ['willow'] * 2
    objs = trees['cottonwood'] + trees['sycamore'] + trees['ash'] + trees['willow']
    p = np.array([0.20, 0.13, 0.17, 0.10, 0.09, 0.08, 0.06, 0.05, 0.07, 0.05])
    kind = rng.choice(len(objs), size=len(tx), p=p / p.sum())
    # On the low bars only what a flood lets stand: young cottonwoods, ash and willows.
    low = trel < 0.3
    kind = np.where(low, rng.choice([3, 6, 7, 8, 9], size=len(tx)), kind)
    n = plant(objs, 'Wood', tx, ty, kind, rng.uniform(0.8, 1.15, len(tx)), rng)
    print('trees of the creek:', n, flush=True)
    # Thickets of coyote willow: along the water's edge and on the bars, in patches.
    x, y = candidates(rng, 400000, 8.0, 700.0)
    z = C.land_height(x, y)
    rel = z - CREEK_RISE * y
    patch = C.value_noise(x, y, 17.0, 9) * 0.6 + C.value_noise(x, y, 5.0, 10) * 0.4
    per_ha = 3200.0 * C.smooth(0.10, 0.30, rel) * C.smooth(1.7, 0.8, rel) * C.smooth(0.34, 0.52, patch)
    per_ha += 250.0 * C.smooth(0.8, 1.5, rel) * C.smooth(5.0, 3.0, rel) * C.smooth(0.55, 0.7, patch)       # under the wood's edge
    area = math.radians(53.0) / 2 * (700.0 ** 2 - 8.0 ** 2) / 1e4
    keep = (rng.random(len(x)) < per_ha / (len(x) / area)) & (z > 0.10) & (np.hypot(x, y) > 9.0)
    keep &= thin_out(x, y, 1.6)
    tx, ty = x[keep], y[keep]
    kind = rng.integers(0, len(trees['thicket']), len(tx))
    n = plant(trees['thicket'], 'Thicket', tx, ty, kind, rng.uniform(0.38, 0.62, len(tx)), rng, sink=0.05)
    print('willow thickets:', n, flush=True)


def junipers(trees, rng):
    """Juniper and pinyon on the dry ground: the hills by the creek, the benches and skirts of the rock."""
    x, y = candidates(rng, 2200000, 60.0, 3300.0, half=27.0)
    z = C.land_height(x, y)
    rel = z - CREEK_RISE * np.minimum(y, 1100.0)
    slope = C.slope_at(x, y, 2.5)
    d = np.hypot(x, y)
    patch = C.value_noise(x, y, 120.0, 21) * 0.5 + C.value_noise(x, y, 31.0, 22) * 0.5
    per_ha = 430.0 * C.smooth(4.0, 7.0, rel) * C.smooth(0.70, 0.36, slope) * C.smooth(0.16, 0.50, patch)
    per_ha *= 1.0 - 0.6 * C.smooth(170.0, 240.0, z)           # few on the high ledges of the domes
    area = math.radians(54.0) / 2 * (3300.0 ** 2 - 60.0 ** 2) / 1e4
    keep = rng.random(len(x)) < per_ha / (len(x) / area)
    keep &= thin_out(x, y, 3.0)
    tx, ty = x[keep], y[keep]
    kind = rng.choice(3, size=len(tx), p=[0.45, 0.35, 0.20])
    n = plant(trees['juniper'], 'Juniper', tx, ty, kind, rng.uniform(0.36, 0.60, len(tx)), rng, sink=0.1)
    print('junipers:', n, flush=True)
    # Brush between them, thick on the gentler ground, and on the open parts of the flood plain.
    x, y = candidates(rng, 1600000, 25.0, 1900.0, half=27.0)
    z = C.land_height(x, y)
    rel = z - CREEK_RISE * np.minimum(y, 1100.0)
    slope = C.slope_at(x, y, 2.5)
    patch = C.value_noise(x, y, 60.0, 23) * 0.5 + C.value_noise(x, y, 13.0, 24) * 0.5
    per_ha = 900.0 * C.smooth(2.5, 6.0, rel) * C.smooth(0.75, 0.35, slope) * C.smooth(0.25, 0.60, patch)
    per_ha += 150.0 * C.smooth(0.6, 1.4, rel) * C.smooth(6.0, 3.0, rel) * C.smooth(0.50, 0.70, patch)
    area = math.radians(54.0) / 2 * (1900.0 ** 2 - 25.0 ** 2) / 1e4
    keep = rng.random(len(x)) < per_ha / (len(x) / area)
    keep &= thin_out(x, y, 1.8)
    tx, ty = x[keep], y[keep]
    kind = rng.integers(0, len(trees['brush']), len(tx))
    n = plant(trees['brush'], 'Brush', tx, ty, kind, rng.uniform(0.22, 0.50, len(tx)), rng, sink=0.03)
    print('brush:', n, flush=True)


def framing(trees, wood, leaf, rng):
    """The trees nearest us, set by hand: a cottonwood on the left bank whose boughs reach into the picture, a
    sycamore and willows across the pool."""
    spots = [  # kind, index, x, y, scale
        ('cottonwood', 1, -27.0, 52.0, 1.0), ('sycamore', 0, -41.0, 84.0, 1.0), ('cottonwood', 0, -52.0, 118.0, 0.95),
        ('willow', 0, -24.5, 70.0, 0.9),
        ('cottonwood', 3, 25.0, 62.0, 1.0), ('ash', 0, 31.0, 70.0, 1.0), ('cottonwood', 2, 43.0, 92.0, 1.0),
        ('sycamore', 1, 36.0, 84.0, 0.9),
    ]
    for kind, k, x, y, s in spots:
        ob, h, r = trees[kind][k]
        xa, ya = np.array([x]), np.array([y])
        plant([(ob, h, r)], 'Near_%s_%d_%d' % (kind, k, int(x)), xa, ya, np.array([0]), np.array([s]), rng)


# ---- Small things: grass, stones, driftwood -----------------------------------------------------------------------

_SEEN = set()


def dress(ob, wind, snow, hue=None, tint=None):
    """Gives a scan's materials their place in the mask; `hue` = (turn of hue, saturation, value) and `tint` change
    its colours (each copy differs a little from the next)."""
    for mat in ob.data.materials:
        if mat is None or mat.name in _SEEN:
            continue
        _SEEN.add(mat.name)
        nt = mat.node_tree
        for nd in list(nt.nodes):
            if nd.type != 'BSDF_PRINCIPLED' or not nd.inputs['Base Color'].links:
                continue
            src = nd.inputs['Base Color'].links[0].from_socket
            if hue is not None:
                hs = nt.nodes.new('ShaderNodeHueSaturation')
                hs.inputs['Saturation'].default_value = hue[1]
                info = nt.nodes.new('ShaderNodeObjectInfo')
                var = nt.nodes.new('ShaderNodeMapRange')
                var.inputs['To Min'].default_value = hue[0] - 0.02
                var.inputs['To Max'].default_value = hue[0] + 0.02
                nt.links.new(info.outputs['Random'], var.inputs['Value'])
                nt.links.new(var.outputs[0], hs.inputs['Hue'])
                val = nt.nodes.new('ShaderNodeMapRange')
                val.inputs['To Min'].default_value = hue[2] * 0.8
                val.inputs['To Max'].default_value = hue[2] * 1.2
                nt.links.new(info.outputs['Random'], val.inputs['Value'])
                nt.links.new(val.outputs[0], hs.inputs['Value'])
                nt.links.new(src, hs.inputs['Color'])
                src = hs.outputs['Color']
            if tint is not None:
                mx = nt.nodes.new('ShaderNodeMix')
                mx.data_type = 'RGBA'
                mx.blend_type = 'MULTIPLY'
                [q for q in mx.inputs if q.name == 'Factor' and q.type == 'VALUE'][0].default_value = 1.0
                nt.links.new(src, [q for q in mx.inputs if q.name == 'A' and q.type == 'RGBA'][0])
                [q for q in mx.inputs if q.name == 'B' and q.type == 'RGBA'][0].default_value = (*tint, 1.0)
                src = [q for q in mx.outputs if q.type == 'RGBA'][0]
            nt.links.new(src, nd.inputs['Base Color'])
        bake.add_mask(mat, wind=wind, snow=snow, water_level=C.WATER if wind == 0.0 else None)


def grass(rng):
    """Tufts of grass and sedge on the banks and bars (scans): green in spring and summer, straw from autumn on."""
    kinds = C.scan_objects('grass_medium_01', lambda n: n.endswith('_LOD0') and 'geonodes' not in n
                           and any(k in n for k in ('tall_', 'mid_', 'large_a', 'large_b')))
    hue = {'summer': (0.52, 1.25, 0.85), 'spring': (0.56, 1.5, 0.90), 'autumn': (0.47, 0.9, 1.25), 'winter': (0.46, 0.65, 1.30)}[C.SEASON]
    for ob in kinds:
        dress(ob, 1.0, 0.5, hue=hue)
    xs, ys, ss = [], [], []
    for d0, d1, dens, (s0, s1) in ((1.5, 14.0, 5.0, (1.1, 2.2)), (14.0, 45.0, 9.0, (2.2, 4.0)), (45.0, 130.0, 7.0, (4.0, 7.0))):
        count = int(dens * math.radians(26.0) * (d1 * d1 - d0 * d0))
        x, y = candidates(rng, count, d0, d1, 26.0)
        z = C.land_height(x, y)
        patch = C.value_noise(x, y, 9.0, 31) * 0.5 + C.value_noise(x, y, 2.5, 32) * 0.5
        want = C.smooth(0.10, 0.30, z) * (0.12 + 0.88 * C.smooth(0.44, 0.60, patch)) * (0.35 + 0.65 * C.smooth(0.5, 1.2, z))
        keep = rng.random(count) < want
        xs.append(x[keep])
        ys.append(y[keep])
        ss.append(rng.uniform(s0, s1, count)[keep])
    x, y, size = np.concatenate(xs), np.concatenate(ys), np.concatenate(ss)
    z = C.land_height(x, y) - 0.02
    which = rng.integers(0, len(kinds), len(x))
    for k, ob in enumerate(kinds):
        sel = which == k
        C.stand('Grass%d' % k, ob, x[sel], y[sel], z[sel], size[sel], rng.uniform(0.0, 6.28, int(sel.sum())))
    print('grass tufts:', len(x), flush=True)


def place(src, x, y, want, rng, sink=0.25, tilt=0.0):
    ob = src.copy()
    bpy.context.scene.collection.objects.link(ob)
    dims = src.dimensions
    sc = want / max(dims[0], dims[1], 1e-3)
    zs = [v[2] for v in src.bound_box]
    gz = float(C.land_height(np.array([x]), np.array([y]))[0])
    ob.scale = (sc, sc, sc)
    ob.rotation_euler = (tilt, 0.0, float(rng.uniform(0.0, 6.28)))
    ob.location = (x, y, gz - (min(zs) + sink * (max(zs) - min(zs))) * sc)
    return ob


def stones_and_wood(rng):
    """Blocks of sandstone fallen and rolled into the creek, cobble on the bars, and what the floods leave: a trunk,
    roots, branches (scans; the boulders tinted to the red of the rock here)."""
    red = (1.45, 0.82, 0.58)
    big = C.scan_objects('namaqualand_boulder_02', lambda n: n.endswith('LOD1'))
    big += C.scan_objects('namaqualand_boulder_05', lambda n: n.endswith('LOD1'))
    small = C.scan_objects('namaqualand_boulders_01', lambda n: n.endswith('LOD1'))
    for ob in big + small:
        dress(ob, 0.0, 0.7, tint=red)
        ob.location = (0.0, -900.0, -900.0)
    spots = [(2.6, 5.2, 0.5, small), (-2.9, 6.6, 0.35, small), (5.2, 9.5, 1.1, big), (-6.0, 15.0, 1.6, big),
             (15.0, 33.0, 2.0, big), (21.0, 46.0, 2.6, big), (6.0, 49.0, 1.2, big),
             (-14.0, 44.0, 1.8, big), (12.0, 70.0, 2.2, big), (-9.0, 88.0, 2.4, big), (3.4, 3.4, 0.3, small)]
    for x, y, want, pool in spots:
        place(pool[int(rng.integers(0, len(pool)))], x, y, want, rng)
    # Cobbles strewn on the bars.
    x, y = candidates(rng, 9000, 4.0, 110.0, 26.0)
    z = C.land_height(x, y)
    patch = C.value_noise(x, y, 16.0, 41)
    keep = (z > -0.15) & (z < 1.1) & (rng.random(len(x)) < 0.14 * C.smooth(0.45, 0.62, patch))
    x, y = x[keep], y[keep]
    which = rng.integers(0, len(small), len(x))
    for k, ob in enumerate(small):
        sel = which == k
        C.stand('Cobble%d' % k, ob, x[sel], y[sel], C.land_height(x[sel], y[sel]) - 0.04, rng.uniform(0.25, 0.9, int(sel.sum())), rng.uniform(0.0, 6.28, int(sel.sum())))
    trunk = C.scan_objects('dead_tree_trunk_02', lambda n: n.endswith('LOD1'))[0]
    dress(trunk, 0.0, 0.5, tint=(1.5, 1.4, 1.3))
    trunk.location = (0.0, -900.0, -900.0)
    for x, y, want in ((7.5, 13.0, 5.5), (24.0, 52.0, 7.0), (-16.0, 61.0, 6.0)):
        place(trunk, x, y, want, rng, sink=0.35)
    print('stones and driftwood placed', flush=True)


def grow_all(canyon):
    global C
    C = canyon
    rng = np.random.default_rng(2026)
    trees = {}
    mats = {}
    for kind, K in KINDS.items():
        wood, leaf = wood_material(kind), leaf_material(kind)
        mats[kind] = (wood, leaf)
        trees[kind] = []
        for k, spec in enumerate(K['trees']):
            ob, h, r = make_tree(kind, spec, k, wood, leaf)
            s = K.get('size', 1.0)
            trees[kind].append((ob, h, r))
        print(kind, 'grown:', len(K['trees']), flush=True)
    wood_of_the_creek(trees, np.random.default_rng(11))
    junipers(trees, np.random.default_rng(12))
    framing(trees, None, None, np.random.default_rng(13))
    if C.STAGE == 'all':
        grass(np.random.default_rng(14))
        stones_and_wood(np.random.default_rng(15))
