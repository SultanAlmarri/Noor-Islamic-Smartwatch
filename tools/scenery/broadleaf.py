"""Broad-crowned trees (oaks), grown rather than drawn.

A crown is a volume of places where leaves could find light. Branches grow from the trunk towards the places nearest
to them and use them up (space colonisation, Runions et al. 2007); what comes out forks, wanders and fills its crown
the way a real open-grown oak does, with heavy low limbs and the leaves in a shell round the outside. Branch
thickness follows the pipe model (a limb is as thick as all it carries). Pure numpy; `to_objects` needs Blender.

    tree = grow(seed, height=11, radius=7.5)        # dict of arrays
"""
import math

import numpy as np


def _unit(v):
    return v / np.maximum(np.linalg.norm(v, axis=-1, keepdims=True), 1e-9)


def grow(seed, height=11.0, radius=7.5, trunk=2.3, girth=0.55, points=5200, step=0.34, influence=3.0, kill=0.62,
         leaves_per_twig=38, leaf=0.18, lobed=False, droop=0.25, masses=1.0):
    """Grows one tree standing at the origin. Returns
      wood_co, wood_faces      the trunk and branches (quads)
      leaf_co, leaf_faces      the leaves (triangles)
      leaf_col                 a colour for every leaf vertex (rgb, linear)
      leaf_tip                 for every leaf vertex, 0 at the leaf's stalk .. 1 at its tip
    `leaf` is a leaf's length in metres; `lobed` gives each the outline of an oak leaf (for trees seen close)."""
    rng = np.random.default_rng(seed)
    crown_h = height - trunk * 0.75
    zc = trunk * 0.75 + crown_h * 0.52
    rz = crown_h * 0.52
    # A crown is lumpy: a few great boughs each carry their own dome.
    bumps = _unit(rng.normal(size=(6, 3)) * np.array([1.0, 1.0, 0.6]))
    bump_amp = rng.uniform(0.12, 0.34, 6)

    def reach(dirs):
        """How far the crown reaches in each direction, as a fraction of the plain ellipsoid."""
        c = dirs @ bumps.T
        return np.minimum(0.74 + np.sum(bump_amp * np.exp((c - 1.0) * 5.0), axis=1), 1.15)

    # Places for leaves. Foliage stands in masses, each the head of one bough, with gaps of sky between them: the
    # places are gathered into such masses over the outside of the crown (and a few strays anywhere).
    scale = np.array([radius, radius, rz])
    area = 2.0 * math.pi * radius * (radius + rz)
    k = int(np.clip(area / 6.5 * masses, 18, 140))
    cdir = _unit(rng.normal(size=(k * 3, 3)) + np.array([0.0, 0.0, 0.35]))
    cdir = cdir[cdir[:, 2] > -0.45][:k]
    k = len(cdir)
    crad = rng.uniform(0.95, 1.75, k) * (radius / 8.0) ** 0.5
    cfrac = 1.0 - rng.uniform(0.02, 0.26, k) * 1.0
    centre = cdir * (cfrac * reach(cdir))[:, None] * scale + np.array([0.0, 0.0, zc])
    share = crad ** 3 / np.sum(crad ** 3)
    which = rng.choice(k, size=int(points * 0.9), p=share)
    p = centre[which] + rng.normal(size=(len(which), 3)) * (crad[which] / 1.9)[:, None] * np.array([1.0, 1.0, 0.8])
    stray = _unit(rng.normal(size=(points - len(which), 3)))
    stray = stray * (rng.uniform(0.55, 1.0, len(stray)) * reach(stray))[:, None] * scale + np.array([0.0, 0.0, zc])
    p = np.concatenate([p, stray])
    # The lower boughs of an open-grown oak hang towards the ground at their ends.
    out = np.hypot(p[:, 0], p[:, 1]) / radius
    p[:, 2] -= droop * crown_h * 0.25 * out ** 2 * (p[:, 2] < zc)
    p = p[p[:, 2] > max(1.3, trunk * 0.55)]

    # The trunk: a short, leaning column up to where it forks.
    lean = rng.normal(size=2) * 0.06
    n0 = max(3, int(trunk / step))
    zs = np.linspace(0.0, trunk, n0 + 1)
    nodes = [np.array([lean[0] * z * z / trunk, lean[1] * z * z / trunk, z]) for z in zs]
    parent = [-1] + list(range(n0))
    nodes = np.array(nodes)
    parent = np.array(parent)
    grown_from = n0                                     # nodes before this index have already put out their shoots
    alive = np.ones(len(p), bool)
    for it in range(260):
        pts = p[alive]
        if len(pts) == 0:
            break
        # Each place for a leaf calls to the nearest node.
        d2 = ((pts[:, None, :] - nodes[None, :, :]) ** 2).sum(-1) if len(pts) * len(nodes) < 4_000_000 else None
        if d2 is None:
            near = np.empty(len(pts), int)
            dist = np.empty(len(pts))
            for a in range(0, len(pts), 512):
                dd = ((pts[a:a + 512, None, :] - nodes[None, :, :]) ** 2).sum(-1)
                near[a:a + 512] = dd.argmin(1)
                dist[a:a + 512] = np.sqrt(dd.min(1))
        else:
            near = d2.argmin(1)
            dist = np.sqrt(d2.min(1))
        hear = dist < influence
        if not hear.any():
            hear = dist < dist.min() * 1.3 + 0.5        # nothing in reach yet: the leader makes for the nearest
        pull = np.zeros_like(nodes)
        np.add.at(pull, near[hear], _unit(pts[hear] - nodes[near[hear]]))
        grow_at = np.flatnonzero(np.linalg.norm(pull, axis=1) > 1e-6)
        if len(grow_at) == 0:
            break
        dirn = _unit(pull[grow_at] + rng.normal(size=(len(grow_at), 3)) * 0.22)
        new = nodes[grow_at] + dirn * step
        # (A shoot that would land on a node already there adds nothing.)
        dn = ((new[:, None, :] - nodes[None, :, :]) ** 2).sum(-1).min(1)
        ok = dn > (0.35 * step) ** 2
        new, grow_at = new[ok], grow_at[ok]
        if len(new) == 0:
            # Stuck between equal calls: use up the nearest places and go on.
            alive[np.flatnonzero(alive)[dist < kill * 1.6]] = False
            continue
        nodes = np.concatenate([nodes, new])
        parent = np.concatenate([parent, grow_at])
        dk = ((pts[:, None, :] - new[None, :, :]) ** 2).sum(-1).min(1)
        alive[np.flatnonzero(alive)[dk < kill * kill]] = False

    n = len(nodes)
    # Thickness: a twig is a finger thick at most; every limb carries the sum of what grows from it.
    kids = np.zeros(n, int)
    np.add.at(kids, parent[1:], 1)
    area = np.where(kids == 0, 1.0, 0.0)
    for i in range(n - 1, 0, -1):
        area[parent[i]] += area[i]
    r = area ** (1.0 / 2.35)
    r = r * (girth / r[0])
    r = np.maximum(r, 0.006)
    # The trunk flares where it meets the ground.
    flare = 1.0 + 0.55 * np.exp(-nodes[:, 2] / 0.45)
    r = r * flare

    # ---- wood: a tube round every stretch thick enough to be seen
    sides = 7
    thick = np.flatnonzero((r >= (0.012 if lobed else 0.03)) & (parent >= 0))
    ang = np.arange(sides) * (2.0 * math.pi / sides)
    axis = _unit(nodes[thick] - nodes[parent[thick]])
    ref = np.where(np.abs(axis[:, 2:3]) < 0.9, np.array([[0.0, 0.0, 1.0]]), np.array([[1.0, 0.0, 0.0]]))
    u = _unit(np.cross(axis, ref))
    w = np.cross(axis, u)
    ring = np.cos(ang)[None, :, None] * u[:, None, :] + np.sin(ang)[None, :, None] * w[:, None, :]
    lo = nodes[parent[thick]][:, None, :] + ring * r[parent[thick]][:, None, None]
    hi = nodes[thick][:, None, :] + ring * r[thick][:, None, None]
    wood_co = np.concatenate([lo, hi], axis=1).reshape(-1, 3)
    base = (np.arange(len(thick)) * 2 * sides)[:, None]
    k = np.arange(sides)[None, :]
    kn = (k + 1) % sides
    wood_faces = np.stack([base + k, base + kn, base + sides + kn, base + sides + k], -1).reshape(-1, 4)

    # ---- leaves: tufts on the twigs
    twig = np.flatnonzero(area <= 4.0)
    twig = twig[nodes[twig, 2] > 1.2]
    m = leaves_per_twig
    at = np.repeat(nodes[twig], m, axis=0) + rng.normal(size=(len(twig) * m, 3)) * np.array([0.34, 0.34, 0.26])
    nl = len(at)
    # A leaf turns its face to the open sky: up, and outward from the tree's heart.
    outward = _unit(at - np.array([0.0, 0.0, zc - 0.3 * rz]))
    normal = _unit(outward * 0.55 + np.array([0.0, 0.0, 0.75]) + rng.normal(size=(nl, 3)) * 0.55)
    along = _unit(np.cross(normal, rng.normal(size=(nl, 3))))
    across = np.cross(normal, along)
    size = leaf * rng.uniform(0.7, 1.25, nl)
    if lobed:
        # The outline of an oak leaf: lobes and bays up both sides of the midrib.
        t = np.array([0.0, 0.10, 0.22, 0.34, 0.46, 0.58, 0.70, 0.82, 0.92, 1.0])
        half = np.array([0.03, 0.16, 0.10, 0.24, 0.13, 0.27, 0.14, 0.22, 0.12, 0.0])
        ox = np.concatenate([[0.0], half[1:-1], [0.0], -half[-2:0:-1]])
        oy = np.concatenate([[0.0], t[1:-1], [1.0], t[-2:0:-1]])
        fold = 0.10 * np.abs(ox) / 0.27
    else:
        ox = np.array([0.0, 0.24, 0.20, 0.0, -0.20, -0.24])
        oy = np.array([0.0, 0.38, 0.78, 1.0, 0.78, 0.38])
        fold = np.array([0.0, 0.07, 0.05, 0.0, 0.05, 0.07])
    nv = len(ox)
    leaf_co = (at[:, None, :] + (along[:, None, :] * (oy[None, :, None] - 0.3) + across[:, None, :] * ox[None, :, None]
                                 + normal[:, None, :] * fold[None, :, None]) * size[:, None, None]).reshape(-1, 3)
    fan = np.stack([np.zeros(nv - 2, int), np.arange(1, nv - 1), np.arange(2, nv)], -1)
    leaf_faces = (np.arange(nl)[:, None, None] * nv + fan[None, :, :]).reshape(-1, 3)
    # Colour: dark, a little blue in the old leaves; yellower in the young ones at the tips of the boughs.
    young = np.clip((np.linalg.norm((at - np.array([0.0, 0.0, zc])) / np.array([radius, radius, rz]), axis=1) - 0.72) * 2.4, 0, 1)
    young = np.clip(young * rng.uniform(0.2, 1.3, nl), 0.0, 1.0)
    old = np.array([0.042, 0.082, 0.022])
    fresh = np.array([0.100, 0.170, 0.034])
    col = old[None, :] * (1.0 - young[:, None]) + fresh[None, :] * young[:, None]
    col = col * rng.uniform(0.75, 1.25, (nl, 1))
    leaf_col = np.repeat(col, nv, axis=0)
    leaf_tip = np.tile(oy, nl)
    return dict(wood_co=wood_co.astype(np.float32), wood_faces=wood_faces.astype(np.int32),
                leaf_co=leaf_co.astype(np.float32), leaf_faces=leaf_faces.astype(np.int32),
                leaf_col=leaf_col.astype(np.float32), leaf_tip=leaf_tip.astype(np.float32),
                height=np.float32(nodes[:, 2].max() + 0.2), radius=np.float32(np.hypot(nodes[:, 0], nodes[:, 1]).max() + 0.3))


# ---- In Blender -------------------------------------------------------------------------------------------------

def materials():
    """Bark and leaves. The leaves take their colour from the mesh (attribute 'col') and let light through."""
    import bpy
    bark = bpy.data.materials.new('Bark')
    bark.use_nodes = True
    nt = bark.node_tree
    b = nt.nodes['Principled BSDF']
    noise = nt.nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 9.0
    noise.inputs['Detail'].default_value = 6.0
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.3
    ramp.color_ramp.elements[0].color = (0.035, 0.028, 0.022, 1)
    ramp.color_ramp.elements[1].position = 0.75
    ramp.color_ramp.elements[1].color = (0.13, 0.115, 0.095, 1)
    nt.links.new(noise.outputs['Fac'], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], b.inputs['Base Color'])
    b.inputs['Roughness'].default_value = 0.9
    bump = nt.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.8
    bump.inputs['Distance'].default_value = 0.03
    nt.links.new(noise.outputs['Fac'], bump.inputs['Height'])
    nt.links.new(bump.outputs['Normal'], b.inputs['Normal'])

    leaf = bpy.data.materials.new('Leaves')
    leaf.use_nodes = True
    nt = leaf.node_tree
    b = nt.nodes['Principled BSDF']
    out = nt.nodes['Material Output']
    col = nt.nodes.new('ShaderNodeAttribute')
    col.attribute_name = 'col'
    # No two trees are the same green: each copy is a little darker or lighter, bluer or yellower.
    info = nt.nodes.new('ShaderNodeObjectInfo')
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].color = (0.72, 0.86, 0.95, 1)
    ramp.color_ramp.elements[1].color = (1.30, 1.18, 0.85, 1)
    nt.links.new(info.outputs['Random'], ramp.inputs['Fac'])
    own = nt.nodes.new('ShaderNodeMix')
    own.data_type = 'RGBA'
    own.blend_type = 'MULTIPLY'
    own.inputs['Factor'].default_value = 1.0
    nt.links.new(col.outputs['Color'], [q for q in own.inputs if q.name == 'A' and q.type == 'RGBA'][0])
    nt.links.new(ramp.outputs['Color'], [q for q in own.inputs if q.name == 'B' and q.type == 'RGBA'][0])
    col = own
    col_out = [q for q in own.outputs if q.type == 'RGBA'][0]
    nt.links.new(col_out, b.inputs['Base Color'])
    b.inputs['Roughness'].default_value = 0.55
    b.inputs['Specular IOR Level'].default_value = 0.3
    # Light through a leaf is yellower and brighter than light off it.
    thru = nt.nodes.new('ShaderNodeBsdfTranslucent')
    warm = nt.nodes.new('ShaderNodeMix')
    warm.data_type = 'RGBA'
    warm.blend_type = 'MULTIPLY'
    warm.inputs['Factor'].default_value = 1.0
    nt.links.new(col_out, [s for s in warm.inputs if s.name == 'A' and s.type == 'RGBA'][0])
    [s for s in warm.inputs if s.name == 'B' and s.type == 'RGBA'][0].default_value = (2.6, 2.4, 1.0, 1.0)
    nt.links.new([s for s in warm.outputs if s.type == 'RGBA'][0], thru.inputs['Color'])
    mix = nt.nodes.new('ShaderNodeMixShader')
    mix.inputs['Fac'].default_value = 0.32
    nt.links.new(b.outputs['BSDF'], mix.inputs[1])
    nt.links.new(thru.outputs['BSDF'], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs['Surface'])
    return bark, leaf


def to_object(tree, name, bark, leaf, link=True):
    """One Blender object (wood and leaves, two materials) from what grow() returned."""
    import bpy
    wc, wf, lc, lf = tree['wood_co'], tree['wood_faces'], tree['leaf_co'], tree['leaf_faces']
    me = bpy.data.meshes.new(name)
    nv = len(wc) + len(lc)
    me.vertices.add(nv)
    me.vertices.foreach_set('co', np.concatenate([wc, lc]).ravel())
    loops = np.concatenate([wf.ravel(), lf.ravel() + len(wc)])
    me.loops.add(len(loops))
    me.loops.foreach_set('vertex_index', loops.astype(np.int32))
    npoly = len(wf) + len(lf)
    me.polygons.add(npoly)
    start = np.concatenate([np.arange(len(wf)) * 4, len(wf) * 4 + np.arange(len(lf)) * 3]).astype(np.int32)
    total = np.concatenate([np.full(len(wf), 4), np.full(len(lf), 3)]).astype(np.int32)
    me.polygons.foreach_set('loop_start', start)
    me.polygons.foreach_set('loop_total', total)
    me.polygons.foreach_set('use_smooth', np.concatenate([np.ones(len(wf), bool), np.zeros(len(lf), bool)]))
    me.polygons.foreach_set('material_index', np.concatenate([np.zeros(len(wf), np.int32), np.ones(len(lf), np.int32)]))
    me.update(calc_edges=True)
    attr = me.color_attributes.new('col', 'FLOAT_COLOR', 'POINT')
    rgba = np.ones((nv, 4), np.float32)
    rgba[len(wc):, :3] = tree['leaf_col']
    attr.data.foreach_set('color', rgba.ravel())
    me.materials.append(bark)
    me.materials.append(leaf)
    ob = bpy.data.objects.new(name, me)
    if link:
        bpy.context.scene.collection.objects.link(ob)
    return ob
