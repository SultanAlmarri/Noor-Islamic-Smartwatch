"""The two low layers of the home screen's sky (see skybake.py and sky_low.py): a stratocumulus sheet and an overcast.

    NOOR_SET=<id> blender -b --factory-startup -P tools/scenery/sky_low2.py -- preview|look|bakefast|bake|shade <out dir> [bearing height]

    sc_sheet     stratocumulus: a sheet of flat cells grown together, cracks of blue between them
    st_overcast  an overcast: one grey layer, lighter and darker in patches, torn scud under it

Both stand on what sky_low.py made for the heaped clouds: the CGHEVEN cloud volumes (see sources/clouds_low.md: free
to use in finished work, never to be passed on; they stay outside the repository) set out with `field` and `set_out`,
and its layers from maps (`layer_pieces`). Work files go to NoorTools/work/clouds_low2.

NOOR_CPU=1 renders on the processor; NOOR_SCALE, NOOR_BAKE_SPP, NOOR_BOUNCES, NOOR_STEP as in sky_low.py.
"""
import json
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake               # noqa: E402
import skybake            # noqa: E402
import sky_low as L       # noqa: E402

SET = os.environ.get('NOOR_SET', 'sc_sheet')
WORK = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools', 'work', 'clouds_low2')
L.LIB = os.path.join(WORK, 'lib')            # our layers' volumes, apart from sky_low's


def groups(clouds, rng, n):
    """The clouds of a field dealt into n hands at random (each hand gets its own base and flatness: a sheet's cells
    do not all hang from one ruler-straight level)."""
    hands = [[] for _ in range(n)]
    for c in clouds:
        hands[int(rng.integers(0, n))].append(c)
    return hands


def sheet_maps():
    """The sheet the cells of a stratocumulus are grown together in: pads a kilometre and a half across, thick in
    their hearts and thinning to nothing at their rims, where the blue shows in cracks; more open in some parts of
    the sky than in others, and breaking up into single pads far off."""
    pat = L.Pattern(71, [30000.0, 9000.0, 3000.0, 1000.0, 350.0])

    def maps(x, y, voxel):
        big = pat.at(x, y, (30000.0, 9000.0), voxel) / 1.41
        mid = pat.at(x, y, (3000.0,), voxel)
        fine = pat.at(x, y, (1000.0,), voxel) + 0.5 * pat.at(x, y, (350.0,), voxel)
        # (Pads of two sizes, pushed about by the larger patterns: no two alike, none a neat oval.)
        d = np.minimum(L.cells_at(x + 700.0 * mid + 220.0 * fine, y + 380.0 * fine - 300.0 * mid, 1800.0, 72, 1.5, 0.5),
                       L.cells_at(x - 500.0 * mid, y + 260.0 * fine, 1050.0, 73, 1.2, 2.1) + 0.10)
        far = np.clip((np.hypot(x, y) - 30000.0) / 50000.0, 0.0, 1.0)
        pad = np.clip((0.50 + 0.13 * big + 0.05 * fine - 0.55 * far - d) / 0.28, 0.0, 1.0)
        base = 1185.0 + 25.0 * mid + 14.0 * fine + 70.0 * (1.0 - pad)            # (a pad's rim curls up a little)
        thick = 430.0 * pad ** 0.7 * (0.8 + 0.2 * np.tanh(big))
        return base, base + thick - 2.0
    return maps


def sc_sheet():
    """Stratocumulus: flat cumulus cells a kilometre or two across, grown together into a sheet that covers most of
    the sky; thick in the cells' hearts (grey beneath), thin at their rims (bright), cracks of blue between them
    that close towards the horizon as the sheet is seen more and more from the side. The sheet itself is ours (maps
    of pads and cracks); the cells on it are the cumulus volumes, flattened, set out in two rings so that the sky
    overhead is as full as the sky far off. It thins out and breaks up beyond 34 km."""
    mat = skybake.cloud_material('Sheet', sigma=0.042, anisotropy=0.6)
    L.layer_pieces('sc', sheet_maps(), mat, [(900.0, 12000.0, 25.0, 14.0), (12000.0, 40000.0, 60.0, 22.0),
                                             (40000.0, 95000.0, 150.0, 36.0)], margin=0.05)
    rng = np.random.default_rng(41)
    near = L.field(rng, [(0.45, L.CUMULUS, 520.0, 1000.0), (0.55, L.CUMULUS, 1000.0, 2300.0)], 0.42,
                   near=1100.0, far=9000.0, thin=9000.0, fade=1.0, clump=0.45, sun_road=1.0, small=0.05, big=0.80,
                   spacing=0.28)
    far = L.field(rng, [(0.30, L.CUMULUS, 800.0, 1500.0), (0.70, L.CUMULUS, 1500.0, 3400.0)], 0.44,
                  near=8600.0, far=85000.0, thin=34000.0, fade=15000.0, clump=0.55, sun_road=1.0, small=0.022, big=0.40,
                  spacing=0.28)
    # (Three hands: bases a little apart, some cells flatter than others.)
    for hand, base, flat in zip(groups(near + far, rng, 3), (1150.0, 1200.0, 1250.0), (0.45, 0.55, 0.68)):
        L.set_out(hand, sigma=0.085, base=base, flatten=flat, rng=rng, coarser=1)


def overcast_maps():
    """The overcast's underside and its depth, for any place at any fineness."""
    pat = L.Pattern(61, [42000.0, 16000.0, 6000.0, 2200.0, 800.0, 300.0])

    def maps(x, y, voxel):
        big = pat.at(x, y, (42000.0, 16000.0), voxel) / 1.41
        mid = pat.at(x, y, (6000.0,), voxel) + 0.55 * pat.at(x, y, (2200.0,), voxel)
        fine = pat.at(x, y, (800.0,), voxel) + 0.5 * pat.at(x, y, (300.0,), voxel)
        rolls = L.dome(L.cells_at(x + 600.0 * mid, y, 2300.0, 62, 2.8, 0.30))        # long soft rolls across the wind
        pads = L.dome(L.cells_at(x - 350.0 * mid, y + 200.0 * fine, 900.0, 63, 1.4, 1.1))
        # Its underside: slow swells, rolls and pads hanging from it, a fine raggedness; never the ground.
        base = np.maximum(640.0 + 110.0 * big - 190.0 * rolls - 85.0 * pads + 70.0 * mid + 35.0 * fine, 330.0)
        # Its depth: shallow here (the light comes through: the pale patches of a dull sky), deep there (the dark).
        deep = pat.at(x + 7000.0, y - 3000.0, (16000.0, 6000.0), voxel) / 1.41
        thick = 600.0 + 900.0 * (0.5 + 0.5 * np.tanh(1.6 * deep)) ** 1.3 + 300.0 * rolls + 130.0 * pads
        return base, base + thick
    return maps


def st_overcast():
    """An overcast: one deep layer everywhere, no blue; its underside in long soft rolls and pads, paler where it is
    shallow and darker where it is deep, with torn scud (flattened, thinned cumulus) drifting under it."""
    # (Thin enough for the light to come through: a dull day's sky is a pale, luminous grey, the brightest thing in
    # view, dark only where the layer is deepest. From 3 to 10 free paths deep: nowhere does the blue show.)
    mat = skybake.cloud_material(sigma=0.0055, anisotropy=0.6)
    L.layer_pieces('st', overcast_maps(), mat, [(300.0, 15000.0, 30.0, 16.0), (15000.0, 46000.0, 70.0, 24.0),
                                                (46000.0, 115000.0, 170.0, 40.0)], margin=0.30)
    rng = np.random.default_rng(53)
    scud = L.field(rng, [(0.6, L.CUMULUS, 380.0, 900.0), (0.4, L.CUMULUS, 900.0, 1700.0)], 0.10, near=2200.0, clump=1.6,
                   far=42000.0, thin=24000.0, fade=8000.0, sun_road=1.0, small=0.03, big=0.5)
    for hand, base, flat in zip(groups(scud, rng, 2), (390.0, 470.0), (0.45, 0.6)):
        L.set_out(hand, sigma=0.035, base=base, flatten=flat, rng=rng)


SHADE_FLOOR = {'sc_sheet': 0.30, 'st_overcast': 0.50}
PACK_QUALITY = int(os.environ.get('NOOR_PACK_QUALITY', 97 if SET == 'st_overcast' else 90))


def shade_ground(out):
    """A layer that covers the sky stands between the Sun and the ground under it. The phone lights every set's
    underside with "the light thrown up by the ground" (g.webp) as if that ground lay in the open: right for
    scattered clouds, far too bright under a sheet or an overcast, where it drowns the layer's own light and dark
    in an even grey. So for these two sets g.webp is packed darker by the share of the sky's light that reaches the
    ground through the set: where there is cloud, what the cloud lets through (read from the bake itself: the
    brightness of its underside under a white sky, looking up), and all of it through the gaps. The share is
    written into sky.json (`groundShade`). It is not let fall under SHADE_FLOOR: the phone goes on drawing the land
    under the set in sunshine, and over a sunlit land a layer as dark as the sum asks reads as a coming storm, not
    as a dull day.

    The pictures of these sets are smooth, and smooth dark pictures show the blocks of lossy compression once they
    are squared and lit: the whole set is packed again at a finer quality (PACK_QUALITY)."""
    raw, sky = os.path.join(out, 'raw'), os.path.join(out, 'sky')

    def rd(name):
        return bake.read_exr(os.path.join(raw, name + '0001.exr'))

    skybake.bake_pack(raw, sky, skybake.lights(), INFO[SET], quality=PACK_QUALITY)
    alpha = np.clip(rd('alpha')[..., 0], 0, 1)
    up = slice(0, alpha.shape[0] // 3)                                    # the part of the picture that looks up
    solid = alpha[up] > 0.9
    through = float(np.clip(bake.luma(rd('sky')[..., :3])[up][solid].mean(), 0.05, 1.0)) if solid.any() else 1.0
    cover = float(alpha[up].mean())
    share = max(1.0 - cover * (1.0 - through), SHADE_FLOOR[SET])
    g = np.sqrt(np.clip(bake.luma(rd('gnd')[..., :3]) * share, 0, skybake.R_MAX) / skybake.R_MAX)
    bake.save8(os.path.join(sky, 'g.webp'), g, quality=PACK_QUALITY)
    meta = json.load(open(os.path.join(sky, 'sky.json')))
    meta['groundShade'] = share
    json.dump(meta, open(os.path.join(sky, 'sky.json'), 'w'), indent=1)
    print('ground light under the set: %.2f of that in the open (the cloud lets %.2f through, cover overhead %.2f)' % (share, through, cover), flush=True)


SETS = {'sc_sheet': sc_sheet, 'st_overcast': st_overcast}
INFO = {
    'sc_sheet': {'layer': 'low', 'altitude': 1.3, 'kind': 'sheet'},
    'st_overcast': {'layer': 'low', 'altitude': 1.1, 'kind': 'overcast'},
}

if __name__ == '__main__':
    def build():
        s = bpy.context.scene
        if os.environ.get('NOOR_BORDER'):
            b = [float(v) for v in os.environ['NOOR_BORDER'].split(',')]
            s.render.use_border = True
            s.render.use_crop_to_border = True
            s.render.border_min_x, s.render.border_max_x, s.render.border_min_y, s.render.border_max_y = b
        if os.environ.get('NOOR_CPU'):
            s.cycles.device = 'CPU'
        s.cycles.volume_step_rate = float(os.environ.get('NOOR_STEP', 1.0))
        SETS[SET]()

    if bake.args()[0] == 'shade':                      # only the ground light of a set that is already baked
        shade_ground(bake.args()[1])
    else:
        skybake.run(build, INFO[SET], samples=int(os.environ.get('NOOR_BAKE_SPP', 512)), bounces=int(os.environ.get('NOOR_BOUNCES', 40)))
        if bake.args()[0] in ('bake', 'bakefast', 'pack'):
            shade_ground(bake.args()[1])
