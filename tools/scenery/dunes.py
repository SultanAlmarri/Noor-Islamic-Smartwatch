"""The Dunes scenery: a sand sea seen from the crest of a dune.

The field comes from dunesim.py (sand moved by wind until it forms dunes). Here its lee slopes are given back
the sharp brink and the plane at the angle of repose that the simulation's coarse cells rounded off, it is laid
out under Noor's camera, and rippled.

    blender -b --factory-startup -P tools/scenery/dunes.py -- preview <out dir> [azimuth elevation]
"""
import json
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake                      # noqa: E402
from dunesim import CELL, blur   # noqa: E402

# The simulated field: `python dunesim.py field768.npy 768 500 12 11` (a few minutes), kept with the work files.
FIELD = os.environ.get('NOOR_DUNE_FIELD', os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'work',
                                                         'dunes', 'field768.npy'))
UP = 4                            # the field is refined this many times
WIND_AZ = -118.0                  # the wind blows towards this bearing of the view (degrees right of its middle):
                                  # towards us and to the left, so the dunes ahead show us their slip faces
EYE = 1.9                         # metres above the sand
STAND = 6                         # which of the places found by stands() the camera takes
RISE = 30.0                       # metres: how much higher the great dune stands than the field round it
REPOSE = 0.62                     # tangent of the slope sand rests at on a slip face
FLATTEN = 0.52                    # the simulation's dunes are too steep to windward: their heights are scaled by this
                                  # (the slip faces are then rebuilt at the true angle)


def great_dune(z):
    """A broad rise under the dune we shall stand on (a great dune carrying smaller ones, as in the Empty Quarter):
    its height over the field (metres, same grid) and where its top is (cell x, y)."""
    n = z.shape[0]
    sm = blur(z, 4.0)
    y, x = np.unravel_index(np.argmax(sm), sm.shape)
    yy, xx = np.meshgrid(np.arange(n), np.arange(n), indexing='ij')
    dx = (xx - x + n // 2) % n - n // 2
    dy = (yy - y + n // 2) % n - n // 2
    r2 = (dx * CELL) ** 2 + (dy * CELL) ** 2
    return (RISE * np.exp(-r2 / (2 * 260.0 ** 2))).astype(np.float32), int(x), int(y)


def sharpen(z, rise):
    """The field at UP times its resolution, on top of `rise`, with sharp brinks and slip faces at the angle of
    repose. Wind towards +x (axis 1). Returns the surface before the sand has slid (`pre`) and after."""
    z = z * FLATTEN
    drop = np.roll(z, 1, axis=1) - z                       # fall from the upwind neighbour
    lee = drop > 0.36 * CELL * FLATTEN
    # A lee face is a band of cells, not a lone one: stray cells are noise of the simulation.
    near = sum(np.roll(lee, (dy, dx), axis=(0, 1)).astype(np.int8) for dy in (-1, 0, 1) for dx in (-1, 0, 1))
    lee = lee & (near >= 4)
    lee = lee | np.roll(lee, 1, axis=1)                    # and the cell after: the foot of the face
    m = (~lee).astype(np.float32)
    zs = blur(z * m, 1.1) / np.maximum(blur(m, 1.1), 1e-3)
    low = z.copy()
    for k in range(1, 12):                                 # the lowest ground within a dozen cells downwind
        low = np.minimum(low, np.roll(z, -k, axis=1))
    # Refine (linear between cells, then eased, so nothing overshoots). The line where the lee begins is eased
    # most of all: a brink is a smooth curve, not a staircase of cells, and its height follows the crest evenly.
    n = z.shape[0]
    idx = (np.arange(n * UP) + 0.5) / UP - 0.5
    i0 = np.floor(idx).astype(int)
    f = (idx - i0).astype(np.float32)

    def refine(a, r):
        a = a[i0 % n] * (1 - f)[:, None] + a[(i0 + 1) % n] * f[:, None]
        a = a[:, i0 % n] * (1 - f)[None, :] + a[:, (i0 + 1) % n] * f[None, :]
        return blur(a, r)

    w = np.clip((refine(blur(m, 1.2), 4.0) - 0.42) / 0.16, 0.0, 1.0)
    w = w * w * (3 - 2 * w)
    pre = (refine(zs, 1.6) * w + refine(low, 2.5) * (1 - w) + refine(rise, 2.0)).astype(np.float32)
    # (Eased once more: a brink follows the lie of the sand it tops, and every kink in that would run down the
    # slip face under it as a crease.)
    pre = blur(pre, 2.6).astype(np.float32)
    return pre, slide(pre.copy(), CELL / UP)


def slide(s, cell, most=None):
    """Sand slides wherever it lies steeper than it can rest: the surface becomes the lowest one that is nowhere
    steeper than the angle of repose and nowhere below what was given. From each brink a face falls away at that
    angle, square to the crest, and the horns of a dune are rounded off the same way."""
    step = REPOSE * cell
    for _ in range(most or int(110.0 / cell)):
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (1, -1), (-1, 1), (-1, -1),
                       (1, 2), (2, 1), (-1, 2), (-2, 1), (1, -2), (2, -1), (-1, -2), (-2, -1)):
            d = step * math.hypot(dy, dx)
            np.maximum(s, np.roll(s, (dy, dx), axis=(0, 1)) - d, out=s)
    return s


VIEW = {'a': math.radians(WIND_AZ)}      # which way the view looks in the field (set by the stand that is chosen)


def tile_xy(px, py, cx, cy):
    """Scene metres (camera at the origin, looking along +y) to field metres."""
    a = VIEW['a']
    return px * math.sin(a) + py * math.cos(a) + cx, -px * math.cos(a) + py * math.sin(a) + cy


def brinks(s, cell):
    """Where a slip face begins: gentle ground to windward, sand at rest falling away to leeward."""
    k = max(1, int(round(2.5 / cell)))
    down = (s - np.roll(s, -k, axis=1)) / (k * cell)
    up = (np.roll(s, k, axis=1) - s) / (k * cell)
    return (down > 0.5) & (up < 0.2)


def stands(s, cell, count=12):
    """Places to stand, best first. We stand at the brink of a crest and look up the wind and across it, so the
    dunes ahead turn their slip faces to us, rank behind rank, their crests crossing the picture on a slant. What
    counts is the part of the picture that is seen whole on the phone (the first fifteen degrees under the horizon):
    it should hold several ranks of dunes and their slip faces, not our own slope, and the far skyline must be the
    true horizon where the Sun rises and sets. Each: field metres x, y, the ground's height, the view's angle in
    the field, and what was measured."""
    n = s.shape[0]
    mask = brinks(s, cell)
    k = max(1, int(round(2.5 / cell)))
    lee = (s - np.roll(s, -k, axis=1)) / (k * cell) > 0.5
    rng = np.random.default_rng(5)
    ys, xs = np.nonzero(mask)
    pick = rng.choice(len(xs), size=min(len(xs), 2500), replace=False)
    az = np.radians(np.linspace(-22.0, 22.0, 23))
    el = -np.radians(np.linspace(0.4, 15.0, 24))
    t = np.geomspace(8.0, 2600.0, 96)
    gap = [int(np.argmin(np.abs(np.degrees(az) - v))) for v in (-bake.RISE_AZIMUTH, bake.RISE_AZIMUTH)]
    mid = np.abs(np.degrees(az)) < 9.0
    found = []
    for q in pick:
        x0, y0 = int(xs[q]), int(ys[q])
        ex, ey = x0 * cell - 2.5, y0 * cell                  # two steps to windward of the edge
        ground = float(s[y0, int(ex / cell) % n])
        eye = ground + EYE
        for a in (math.radians(v + rng.uniform(-5, 5)) for v in (-150.0, -135.0, -120.0, 120.0, 135.0, 150.0)):
            b = a - az                                         # each column's bearing in the field
            cx = np.cos(b)[None, :, None] * np.cos(el)[:, None, None] * t[None, None, :]
            cy = np.sin(b)[None, :, None] * np.cos(el)[:, None, None] * t[None, None, :]
            hz = eye + np.sin(el)[:, None, None] * t[None, None, :]
            px = ((ex + cx) / cell).astype(np.int32) % n
            py = ((ey + cy) / cell).astype(np.int32) % n
            under = s[py, px] >= hz
            hit = np.argmax(under, axis=2)                     # the first sample under the ground
            any_hit = under.any(axis=2)
            dist = np.where(any_hit, t[hit], 1e5)
            ii, jj = np.indices(hit.shape)
            on_lee = lee[py[ii, jj, hit], px[ii, jj, hit]] & any_hit & (dist > 40.0)
            # the skyline of every column, from the level rays
            lx = ((ex + np.cos(b)[:, None] * t[None, :]) / cell).astype(np.int32) % n
            ly = ((ey + np.sin(b)[:, None] * t[None, :]) / cell).astype(np.int32) % n
            sky = np.degrees(np.max(np.arctan2(s[ly, lx] - eye, t[None, :])[:, t > 20.0], axis=1))
            if max(sky[gap[0]], sky[gap[1]]) > -0.7 or sky.max() > -0.25:
                continue
            near = float(np.mean(dist < 40.0))
            faces = float(np.mean(on_lee))
            ranks = float(np.mean(np.sum(dist[:-1] / np.maximum(dist[1:], 1.0) > 1.5, axis=0)))
            high = float(np.max(sky[mid]))
            score = (1.5 * min(faces, 0.30) / 0.30 + 1.5 * min(ranks, 3.0) / 3.0 - 2.5 * max(near - 0.15, 0.0)
                     + 0.8 * math.exp(-((high + 1.1) / 0.9) ** 2))
            found.append((float(score), float(ex), float(ey), ground, float(a), dict(faces=round(faces, 2), ranks=round(ranks, 2), near=round(near, 2),
                          high=round(high, 1), gaps=[round(float(sky[g]), 1) for g in gap], view=round(math.degrees(a)))))
    found.sort(key=lambda c: -c[0])
    out = []
    for c in found:                                          # the best, no two of them the same view
        if all(math.hypot(c[1] - o[1], c[2] - o[2]) > 60.0 or abs(c[4] - o[4]) > 0.5 for o in out):
            out.append(c)
        if len(out) == count:
            break
    return out


def surface(out):
    """The whole field, sharpened (kept from one run to the next: it takes a minute)."""
    z = np.load(FIELD).astype(np.float32)
    rise, sx, sy = great_dune(z)
    keep = os.path.join(out, 'dune_surface2_%s_%g_%g_%g.npz' % (os.path.basename(FIELD)[:-4], FLATTEN, REPOSE, RISE))
    if os.path.exists(keep):
        d = np.load(keep)
        pre, s = d['pre'], d['s']
    else:
        pre, s = sharpen(z, rise)
        np.savez(keep, pre=pre, s=s)
    return pre, s, (sx * UP, sy * UP)


HERO = 240.0          # metres: the square round the camera that is modelled finely
HERO_CELL = 0.125


def hero(pre, cell, cx, cy):
    """The ground round the camera at a hand's breadth: the brink we stand on has to be a clean edge. Returns the
    patch (square, HERO metres, centred on the camera) of the surface after the sand has slid."""
    n = pre.shape[0]
    m = int(HERO / HERO_CELL)
    pad = 6
    c0x, c0y = int((cx - HERO / 2) / cell) - pad, int((cy - HERO / 2) / cell) - pad
    k = int(HERO / cell) + 2 * pad + 1
    win = pre[np.ix_(np.arange(c0y, c0y + k) % n, np.arange(c0x, c0x + k) % n)]
    # Smooth (cubic B-spline) refinement of the window.
    fx = ((cx - HERO / 2) + (np.arange(m) + 0.5) * HERO_CELL) / cell - c0x - 0.5
    fy = ((cy - HERO / 2) + (np.arange(m) + 0.5) * HERO_CELL) / cell - c0y - 0.5

    def weights(f):
        i = np.floor(f).astype(int)
        t = (f - i).astype(np.float32)
        return i, [(1 - t) ** 3 / 6, (3 * t ** 3 - 6 * t ** 2 + 4) / 6, (-3 * t ** 3 + 3 * t ** 2 + 3 * t + 1) / 6, t ** 3 / 6]

    iy, wy = weights(fy)
    ix, wx = weights(fx)
    rows = sum(win[iy - 1 + j] * wy[j][:, None] for j in range(4))
    fine = sum(rows[:, ix - 1 + j] * wx[j][None, :] for j in range(4)).astype(np.float32)
    # (np.roll wraps round: a border is slid as well and thrown away.)
    return slide(fine, HERO_CELL, most=int(60.0 / HERO_CELL))


def build(mode, out):
    pre, s, top = surface(out)
    cell = CELL / UP
    size = s.shape[0] * cell
    # (The search takes minutes: its findings are kept beside the surface they were made on.)
    keep = os.path.join(out, 'dune_stands_%g.json' % RISE)
    if os.path.exists(keep):
        found = json.load(open(keep))
    else:
        found = stands(s, cell)
        json.dump(found, open(keep, 'w'))
    for i, c in enumerate(found):
        print('stand %d: score %.2f' % (i, c[0]), c[5], flush=True)
    c = found[int(os.environ.get('NOOR_STAND', STAND))]
    cx, cy, cz, VIEW['a'] = c[1], c[2], c[3], c[4]
    fine = None
    if os.environ.get('NOOR_HERO'):
        keep = os.path.join(out, 'dune_hero_%d_%d.npy' % (round(cx), round(cy)))
        if os.path.exists(keep):
            fine = np.load(keep)
        else:
            fine = hero(pre, cell, cx, cy)
            np.save(keep, fine)
        mid = fine.shape[0] // 2
        cz = float(fine[mid, mid])
    print('stand', cx, cy, cz, 'view', math.degrees(VIEW['a']), 'field', s.min(), s.max(), flush=True)
    # (Row 0 of a Blender picture is its bottom row, which is where v = 0 lies: the field's row 0, as it should be.)
    height = bake.float_image('dune_height', s, os.path.join(out, 'dune_height.exr'))

    # The ground: a sheet in front of the camera, finer near it; the renderer dices it to pixel-sized polygons and
    # the material lifts it into dunes.
    ys = np.concatenate([np.linspace(-40, 0, 9)[:-1], np.geomspace(1.0, 26000.0, 150) - 1.0])
    xs_half = np.geomspace(1.0, 16000.0, 90) - 1.0
    xs = np.concatenate([-xs_half[::-1], xs_half[1:]])
    ground = bake.grid_mesh('Dunes', xs, ys)
    bake.adaptive(ground, 1.0)

    mat = bpy.data.materials.new('Sand')
    mat.use_nodes = True
    mat.displacement_method = 'DISPLACEMENT'        # real geometry, not only shading
    nt = mat.node_tree
    N = nt.nodes
    L = nt.links
    for nd in list(N):
        N.remove(nd)
    outn = N.new('ShaderNodeOutputMaterial')
    bsdf = N.new('ShaderNodeBsdfPrincipled')
    L.new(bsdf.outputs['BSDF'], outn.inputs['Surface'])
    geo = N.new('ShaderNodeNewGeometry')

    # Field coordinates (metres) of a point of the scene: turned so that the wind blows towards WIND_AZ, and moved so
    # that the camera (at the scene's origin) stands at (cx, cy) of the field.
    a = VIEW['a']
    fmap = N.new('ShaderNodeMapping')
    fmap.vector_type = 'POINT'
    fmap.inputs['Rotation'].default_value = (0.0, 0.0, a - math.pi / 2)
    fmap.inputs['Location'].default_value = (cx, cy, 0.0)
    L.new(geo.outputs['Position'], fmap.inputs['Vector'])
    field = fmap
    uv = N.new('ShaderNodeMapping')
    uv.vector_type = 'POINT'
    uv.inputs['Scale'].default_value = (1.0 / size, 1.0 / size, 0.0)
    L.new(field.outputs[0], uv.inputs['Vector'])
    tex = N.new('ShaderNodeTexImage')
    tex.image = height
    tex.interpolation = 'Cubic'
    tex.extension = 'REPEAT'
    L.new(uv.outputs[0], tex.inputs['Vector'])

    ground_h = tex.outputs['Color']
    if fine is not None:
        himg = bake.float_image('dune_hero', fine, os.path.join(out, 'dune_hero.exr'))
        huv = N.new('ShaderNodeMapping')
        huv.vector_type = 'POINT'
        huv.inputs['Location'].default_value = (0.5 - cx / HERO, 0.5 - cy / HERO, 0.0)
        huv.inputs['Scale'].default_value = (1.0 / HERO, 1.0 / HERO, 0.0)
        L.new(field.outputs[0], huv.inputs['Vector'])
        htex = N.new('ShaderNodeTexImage')
        htex.image = himg
        htex.interpolation = 'Cubic'
        htex.extension = 'EXTEND'
        L.new(huv.outputs[0], htex.inputs['Vector'])
        # Inside the square the fine ground, fading to the field's over the last thirty metres of it.
        ctr = N.new('ShaderNodeVectorMath'); ctr.operation = 'SUBTRACT'; ctr.inputs[1].default_value = (0.5, 0.5, 0.0)
        L.new(huv.outputs[0], ctr.inputs[0])
        cab = N.new('ShaderNodeVectorMath'); cab.operation = 'ABSOLUTE'
        L.new(ctr.outputs[0], cab.inputs[0])
        cs = N.new('ShaderNodeSeparateXYZ')
        L.new(cab.outputs[0], cs.inputs[0])
        cm = N.new('ShaderNodeMath'); cm.operation = 'MAXIMUM'
        L.new(cs.outputs['X'], cm.inputs[0]); L.new(cs.outputs['Y'], cm.inputs[1])
        inside = N.new('ShaderNodeMapRange'); inside.interpolation_type = 'SMOOTHSTEP'
        inside.inputs['From Min'].default_value = 0.5 - 34.0 / HERO; inside.inputs['From Max'].default_value = 0.5 - 4.0 / HERO
        inside.inputs['To Min'].default_value = 1.0; inside.inputs['To Max'].default_value = 0.0
        L.new(cm.outputs[0], inside.inputs['Value'])
        hm = N.new('ShaderNodeMix'); hm.data_type = 'FLOAT'
        L.new(inside.outputs[0], hm.inputs['Factor'])
        L.new(tex.outputs['Color'], hm.inputs[2]); L.new(htex.outputs['Color'], hm.inputs[3])
        ground_h = hm.outputs[0]

    # How far from the camera (it stands at the origin): ripples and grain fade into the distance.
    dist = N.new('ShaderNodeVectorMath'); dist.operation = 'LENGTH'
    L.new(geo.outputs['Position'], dist.inputs[0])

    def fade(d0, d1):
        r = N.new('ShaderNodeMapRange'); r.interpolation_type = 'SMOOTHSTEP'
        r.inputs['From Min'].default_value = d0; r.inputs['From Max'].default_value = d1
        r.inputs['To Min'].default_value = 1.0; r.inputs['To Max'].default_value = 0.0
        L.new(dist.outputs['Value'], r.inputs['Value'])
        return r.outputs[0]

    # Wind ripples: a hand's breadth apart, across the wind, forking and joining.
    rip = N.new('ShaderNodeTexWave')
    rip.wave_type = 'BANDS'; rip.bands_direction = 'X'; rip.wave_profile = 'SIN'
    rip.inputs['Scale'].default_value = 0.314 / 0.115
    rip.inputs['Distortion'].default_value = 3.2
    rip.inputs['Detail'].default_value = 2.0
    rip.inputs['Detail Scale'].default_value = 0.35
    rip.inputs['Detail Roughness'].default_value = 0.5
    L.new(field.outputs[0], rip.inputs['Vector'])
    # Sharper crests than a sine: the windward side long, the lee short.
    ripc = N.new('ShaderNodeMath'); ripc.operation = 'POWER'; ripc.inputs[1].default_value = 0.72
    L.new(rip.outputs['Fac'], ripc.inputs[0])
    rf = fade(18.0, 110.0)
    ripa = N.new('ShaderNodeMath'); ripa.operation = 'MULTIPLY'
    L.new(ripc.outputs[0], ripa.inputs[0]); L.new(rf, ripa.inputs[1])
    # Ripples lie on the windward slopes and the flats, not on the sliding lee faces: where the ground is steep, none.
    steep = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['True Normal'], steep.inputs[0])
    flat = N.new('ShaderNodeMapRange'); flat.interpolation_type = 'SMOOTHSTEP'
    flat.inputs['From Min'].default_value = 0.84; flat.inputs['From Max'].default_value = 0.93
    L.new(steep.outputs['Z'], flat.inputs['Value'])

    # Broader swells in the sand, a stride or two across.
    swell = N.new('ShaderNodeTexNoise')
    swell.inputs['Scale'].default_value = 0.45; swell.inputs['Detail'].default_value = 3.0
    L.new(field.outputs[0], swell.inputs['Vector'])
    sf = fade(60.0, 400.0)

    def mul(x, k):
        m = N.new('ShaderNodeMath'); m.operation = 'MULTIPLY'
        if isinstance(k, float):
            m.inputs[1].default_value = k
        else:
            L.new(k, m.inputs[1])
        L.new(x, m.inputs[0])
        return m.outputs[0]

    def add(x, y):
        m = N.new('ShaderNodeMath'); m.operation = 'ADD'
        L.new(x, m.inputs[0]); L.new(y, m.inputs[1])
        return m.outputs[0]

    # Far off, great dunes: the same field read seven times larger and four times higher, beyond a kilometre.
    uv2 = N.new('ShaderNodeMapping')
    uv2.vector_type = 'POINT'
    uv2.inputs['Scale'].default_value = (1.0 / (size * 7.0), 1.0 / (size * 7.0), 0.0)
    uv2.inputs['Location'].default_value = (0.37, 0.18, 0.0)
    L.new(field.outputs[0], uv2.inputs['Vector'])
    tex2 = N.new('ShaderNodeTexImage')
    tex2.image = height
    tex2.interpolation = 'Cubic'
    tex2.extension = 'REPEAT'
    L.new(uv2.outputs[0], tex2.inputs['Vector'])
    farf = N.new('ShaderNodeMapRange'); farf.interpolation_type = 'SMOOTHSTEP'
    farf.inputs['From Min'].default_value = 900.0; farf.inputs['From Max'].default_value = 2600.0
    L.new(dist.outputs['Value'], farf.inputs['Value'])
    posx = N.new('ShaderNodeSeparateXYZ')
    L.new(geo.outputs['Position'], posx.inputs[0])
    azn = N.new('ShaderNodeMath'); azn.operation = 'ARCTAN2'
    L.new(posx.outputs['X'], azn.inputs[0]); L.new(posx.outputs['Y'], azn.inputs[1])
    aza = N.new('ShaderNodeMath'); aza.operation = 'ABSOLUTE'
    L.new(azn.outputs[0], aza.inputs[0])
    azd = N.new('ShaderNodeMath'); azd.operation = 'SUBTRACT'; azd.inputs[1].default_value = math.radians(bake.RISE_AZIMUTH)
    L.new(aza.outputs[0], azd.inputs[0])
    azb = N.new('ShaderNodeMath'); azb.operation = 'ABSOLUTE'
    L.new(azd.outputs[0], azb.inputs[0])
    gap = N.new('ShaderNodeMapRange'); gap.interpolation_type = 'SMOOTHSTEP'
    gap.inputs['From Min'].default_value = 0.05; gap.inputs['From Max'].default_value = 0.17
    gap.inputs['To Min'].default_value = 1.0; gap.inputs['To Max'].default_value = 1.0     # (whole: no gap is cut for the Sun)
    L.new(azb.outputs[0], gap.inputs['Value'])
    great = mul(mul(tex2.outputs['Color'], 3.6), mul(farf.outputs[0], gap.outputs[0]))

    h = add(ground_h, mul(mul(ripa.outputs[0], 0.0085), flat.outputs[0]))
    h = add(h, great)
    h = add(h, mul(mul(swell.outputs['Fac'], 0.07), sf))
    off = N.new('ShaderNodeMath'); off.operation = 'SUBTRACT'; off.inputs[1].default_value = cz
    L.new(h, off.inputs[0])
    disp = N.new('ShaderNodeDisplacement')
    disp.space = 'OBJECT'
    disp.inputs['Midlevel'].default_value = 0.0
    disp.inputs['Scale'].default_value = 1.0
    L.new(off.outputs[0], disp.inputs['Height'])
    L.new(disp.outputs[0], outn.inputs['Displacement'])

    # The sand itself: red-gold, a little paler and finer on the crests, darker and coarser in the hollows.
    big = N.new('ShaderNodeTexNoise')
    big.inputs['Scale'].default_value = 1.0 / 260.0; big.inputs['Detail'].default_value = 4.0
    L.new(field.outputs[0], big.inputs['Vector'])
    hn = N.new('ShaderNodeMapRange')
    hn.inputs['From Min'].default_value = float(np.percentile(s, 8)); hn.inputs['From Max'].default_value = float(np.percentile(s, 97))
    L.new(tex.outputs['Color'], hn.inputs['Value'])
    tone = N.new('ShaderNodeMath'); tone.operation = 'ADD'
    L.new(mul(hn.outputs[0], 0.65), tone.inputs[0]); L.new(mul(big.outputs['Fac'], 0.5), tone.inputs[1])
    ramp = N.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.25
    ramp.color_ramp.elements[0].color = (0.58, 0.235, 0.075, 1)
    ramp.color_ramp.elements[1].position = 0.95
    ramp.color_ramp.elements[1].color = (0.80, 0.42, 0.155, 1)
    L.new(tone.outputs[0], ramp.inputs['Fac'])
    grain = N.new('ShaderNodeTexNoise')
    grain.inputs['Scale'].default_value = 900.0; grain.inputs['Detail'].default_value = 2.0
    L.new(field.outputs[0], grain.inputs['Vector'])
    gm = N.new('ShaderNodeMixRGB'); gm.blend_type = 'MULTIPLY'; gm.inputs['Fac'].default_value = 0.22
    L.new(ramp.outputs['Color'], gm.inputs['Color1']); L.new(grain.outputs['Color'], gm.inputs['Color2'])
    L.new(gm.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.88
    bsdf.inputs['Specular IOR Level'].default_value = 0.25
    bsdf.inputs['Sheen Weight'].default_value = 0.12
    bsdf.inputs['Sheen Roughness'].default_value = 0.6
    if os.environ.get('NOOR_DEBUG'):
        # See the height field itself, painted on the ground.
        em = N.new('ShaderNodeEmission')
        if os.environ.get('NOOR_DEBUG') == 'uv':
            chk = N.new('ShaderNodeTexChecker'); chk.inputs['Scale'].default_value = 80.0
            L.new(uv.outputs[0], chk.inputs['Vector'])
            L.new(chk.outputs['Color'], em.inputs['Color'])
        else:
            L.new(hn.outputs[0], em.inputs['Color'])
        L.new(em.outputs[0], outn.inputs['Surface'])
        print('height image', height.size[:], height.channels, list(height.pixels[:8]), flush=True)
    ground.data.materials.append(mat)
    mountains(cz)
    return cz


def mountains(cz):
    """A range of bare rock far beyond the sand (the Hajar, as seen from the desert's edge): ridged, jagged, low
    on the horizon, unbroken across the view: the Sun rises over it and sets behind it."""
    rng = np.random.default_rng(11)
    nx, ny = 700, 160
    az = np.radians(np.linspace(-40.0, 40.0, nx))
    r = np.linspace(46000.0, 70000.0, ny)
    A, R = np.meshgrid(az, r)
    X, Y = R * np.sin(A), R * np.cos(A)

    def ridged(x, y, octaves=7):
        out = np.zeros_like(x)
        amp, f = 1.0, 1.0 / 9000.0
        for o in range(octaves):
            ph = rng.uniform(0, 100, 4)
            n = np.sin(x * f * 1.7 + ph[0] + 1.3 * np.sin(y * f * 1.1 + ph[1])) * np.cos(y * f * 1.9 + ph[2] + 1.1 * np.sin(x * f * 0.8 + ph[3]))
            out += amp * (1.0 - np.abs(n))
            amp *= 0.52
            f *= 2.03
        return out / 2.0

    h = ridged(X, Y) ** 2.2 * 2500.0
    # A range, not a wall: high in its middle depth, falling to the plain in front and behind.
    depth = (R - r[0]) / (r[-1] - r[0])
    h *= np.clip(np.sin(np.pi * depth), 0, 1) ** 0.8
    # A range has high and low stretches of its own: a slow rise and fall along it, nothing cut out of it.
    h *= 0.62 + 0.38 * np.sin(A * 7.3 + 1.1) * np.sin(A * 3.1 + 0.4)
    co = np.stack([X, Y, h - cz - 40.0], -1).reshape(-1, 3).astype(np.float32)
    i = np.arange((ny - 1) * (nx - 1))
    rr, cc = i // (nx - 1), i % (nx - 1)
    a = rr * nx + cc
    faces = np.stack([a, a + 1, a + nx + 1, a + nx], -1).astype(np.int32)
    me = bpy.data.meshes.new('Mountains')
    me.vertices.add(len(co))
    me.vertices.foreach_set('co', co.ravel())
    me.loops.add(faces.size)
    me.loops.foreach_set('vertex_index', faces.ravel())
    me.polygons.add(len(faces))
    me.polygons.foreach_set('loop_start', np.arange(0, faces.size, 4, dtype=np.int32))
    me.polygons.foreach_set('loop_total', np.full(len(faces), 4, np.int32))
    me.polygons.foreach_set('use_smooth', np.ones(len(faces), bool))
    me.update(calc_edges=True)
    ob = bpy.data.objects.new('Mountains', me)
    bpy.context.scene.collection.objects.link(ob)
    mat = bpy.data.materials.new('Rock')
    mat.use_nodes = True
    b = mat.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (0.30, 0.215, 0.165, 1)
    b.inputs['Roughness'].default_value = 0.9
    ob.data.materials.append(mat)


OUT = {}


def make():
    OUT['cz'] = build('', bake.args()[1])


bake.run(make, (0.0, 0.0, EYE), exposure=-2.3)
