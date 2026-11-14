"""What every scenery script shares: Noor's camera, the lights the phone relights with, rendering and export.

The home screen's camera never moves, so the land of each scenery is rendered once, here, by a path tracer (Blender
Cycles), under a set of Suns of unit strength and under a plain white sky. The phone adds those pictures together in
the proportions and colours of the moment (see docs/DESIGN.md, "The land"): real light, real shadows, at the cost of
a few pictures.

Run a scenery script with Blender:   blender -b --factory-startup -P tools/scenery/<name>.py -- <mode> <out dir>
"""
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# ---- The frame --------------------------------------------------------------------------------------------------
# The picture is as wide as the phone's screen and taller than any, with the horizon on a fixed row: the phone slides
# it up or down to where its own layout puts the horizon. The lens looks level and is shifted, as a view camera's
# is, so that sliding the picture is exact and upright things stay upright.
W = 1080
H = 2640
HORIZON = 1056            # row of the horizon, from the top
FOCAL = 1.15              # focal length, in picture widths (a phone's main camera)

# ---- The Suns ---------------------------------------------------------------------------------------------------
# Elevations (degrees) at which the land is rendered, for a Sun on the left (morning) and on the right (afternoon),
# and once from high in front. Low Suns stand where the Sun is seen to rise and set; higher ones swing round to the
# side, so that the land is modelled by light and shade (RealSkyFrame does the same).
SUN_ELEVATIONS = [0.7, 2.5, 5.0, 9.0, 14.0, 22.0, 34.0, 50.0]
RISE_AZIMUTH = 14.7       # degrees to the side of the view's middle


def sun_azimuth(el):
    t = min(max((el - 8.0) / 32.0, 0.0), 1.0)
    return RISE_AZIMUTH + 62.0 * t * t * (3 - 2 * t)


def sun_set():
    """[(name, azimuth, elevation)], azimuth from the view's middle, negative to the left."""
    out = []
    for i, e in enumerate(SUN_ELEVATIONS):
        out.append(('L%d' % i, -sun_azimuth(e), e))
    for i, e in enumerate(SUN_ELEVATIONS):
        out.append(('R%d' % i, sun_azimuth(e), e))
    out.append(('TOP', 0.0, 68.0))
    return out


ARC_TOP = 14.2            # degrees over the horizon: where the Sun is drawn at noon (the top of its path on the phone)


def skyline_limit(az):
    """How high the land (and anything on it) may stand at a bearing (degrees right of the view's middle), in
    degrees over the horizon. The Sun and the Moon rise at -RISE_AZIMUTH and set at +RISE_AZIMUTH on the phone, and
    cross the sky between on an arch: there the skyline must be the true horizon, and under the arch the land must
    stay under their path. Outside the two points it may be as high as it likes."""
    a = abs(az)
    if a > RISE_AZIMUTH + 1.8:
        return 90.0
    if a > RISE_AZIMUTH - 1.8:
        return 0.3
    x = math.tan(math.radians(a)) / math.tan(math.radians(RISE_AZIMUTH))
    return max(0.3, ARC_TOP * math.cos(0.5 * math.pi * x) - 2.5)


def check_skyline(alpha, top=0):
    """Says where a packed land picture (its alpha, `top` rows cut off above) stands higher than skyline_limit."""
    h, w = alpha.shape
    k = w / W
    solid = alpha > 0.5
    first = np.where(solid.any(axis=0), np.argmax(solid, axis=0), h) + top
    el = np.degrees(np.arctan((HORIZON * k - first) / (FOCAL * w)))
    az = np.degrees(np.arctan((np.arange(w) + 0.5 - w / 2) / (FOCAL * w)))
    over = el - np.array([skyline_limit(a) for a in az])
    bad = np.where(over > 0.0)[0]
    if len(bad):
        worst = bad[np.argmax(over[bad])]
        print('SKYLINE TOO HIGH at %d of %d columns: worst %.1f degrees over the limit at bearing %.1f (it stands %.1f '
              'degrees over the horizon there)' % (len(bad), w, over[worst], az[worst], el[worst]), flush=True)
    else:
        print('skyline: within its limits (highest %.1f degrees, at bearing %.1f)' % (el.max(), az[int(np.argmax(el))]), flush=True)
    return len(bad) == 0


GLOW_ANGLE = 75.0         # how broad the glow of the sky round a low Sun is taken to be (degrees across)


def glow_set():
    """The bright sky round a rising or setting Sun, as a broad soft light low on its side: by it the land is
    modelled in twilight, when the Sun itself is down."""
    return [('GL', -RISE_AZIMUTH, 7.0), ('GR', RISE_AZIMUTH, 7.0)]


def args():
    a = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    mode = a[0] if a else 'preview'
    out = a[1] if len(a) > 1 else os.path.join(os.getcwd(), 'out')
    os.makedirs(out, exist_ok=True)
    return mode, out, a[2:]


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def cycles(samples=128, scale=1.0, noise=0.02):
    prefs = bpy.context.preferences.addons['cycles'].preferences
    try:
        prefs.compute_device_type = 'HIP'
        prefs.get_devices()
        for d in prefs.devices:
            d.use = (d.type == 'HIP')
        gpu = any(d.type == 'HIP' for d in prefs.devices)
    except Exception:
        gpu = False
    s = bpy.context.scene
    s.render.engine = 'CYCLES'
    s.cycles.device = 'GPU' if gpu else 'CPU'
    s.cycles.feature_set = 'EXPERIMENTAL'          # adaptive subdivision: polygons no larger than a pixel
    s.cycles.samples = samples
    s.cycles.use_adaptive_sampling = True
    s.cycles.adaptive_threshold = noise
    s.cycles.use_denoising = True
    s.cycles.denoiser = 'OPENIMAGEDENOISE'
    s.cycles.max_bounces = 6
    s.cycles.diffuse_bounces = 3
    s.cycles.glossy_bounces = 3
    s.cycles.transmission_bounces = 6
    s.cycles.transparent_max_bounces = 16
    s.cycles.dicing_rate = 1.0
    s.cycles.offscreen_dicing_scale = 6.0
    s.render.resolution_x = W
    s.render.resolution_y = H
    s.render.resolution_percentage = int(round(100 * scale))
    s.render.film_transparent = True
    s.view_settings.view_transform = 'Standard'
    s.view_settings.look = 'None'
    s.display_settings.display_device = 'sRGB'
    return s


def camera(eye, yaw=0.0):
    """The home screen's camera at `eye` (x, y, z metres), looking level along +y turned by `yaw` degrees."""
    cam = bpy.data.cameras.new('NoorCamera')
    cam.sensor_fit = 'HORIZONTAL'
    cam.sensor_width = 36.0
    cam.lens = FOCAL * 36.0
    cam.shift_x = 0.0
    cam.shift_y = -(H / 2 - HORIZON) / W          # the horizon above the middle: the frame is shifted down
    cam.clip_start = 0.05
    cam.clip_end = 400000.0
    ob = bpy.data.objects.new('NoorCamera', cam)
    ob.location = eye
    ob.rotation_euler = (math.pi / 2, 0.0, math.radians(-yaw))
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.scene.camera = ob
    return ob


def direction(az, el, yaw=0.0):
    """Unit vector towards a point of the sky; azimuth from the view's middle (+y), positive to the right (+x)."""
    a = math.radians(az + yaw)
    e = math.radians(el)
    return (math.cos(e) * math.sin(a), math.cos(e) * math.cos(a), math.sin(e))


def sun_lamp(name, az, el, strength=math.pi, angle=0.6, yaw=0.0, group=None):
    lamp = bpy.data.lights.new(name, 'SUN')
    lamp.energy = strength                        # pi: a white surface facing it has radiance 1
    lamp.angle = math.radians(angle)
    ob = bpy.data.objects.new(name, lamp)
    d = direction(az, el, yaw)
    # A lamp shines along its local -z: turn that to point from the Sun towards the scene.
    import mathutils
    ob.rotation_euler = mathutils.Vector(d).to_track_quat('Z', 'Y').to_euler()
    bpy.context.scene.collection.objects.link(ob)
    if group:
        vl = bpy.context.view_layer
        if group not in [g.name for g in vl.lightgroups]:
            vl.lightgroups.add(name=group)
        ob.lightgroup = group
    return ob


def white_sky(strength=1.0, group=None):
    world = bpy.data.worlds.new('NoorSky')
    world.use_nodes = True
    bg = world.node_tree.nodes['Background']
    bg.inputs['Color'].default_value = (1, 1, 1, 1)
    bg.inputs['Strength'].default_value = strength
    bpy.context.scene.world = world
    if group:
        vl = bpy.context.view_layer
        if group not in [g.name for g in vl.lightgroups]:
            vl.lightgroups.add(name=group)
        world.lightgroup = group
    return world


def preview_sky(az, el, yaw=0.0, strength=1.0, turbidity=2.2):
    """A physical sky and its Sun, for looking at a scene while it is being made (never baked)."""
    world = bpy.data.worlds.new('PreviewSky')
    world.use_nodes = True
    nt = world.node_tree
    sky = nt.nodes.new('ShaderNodeTexSky')
    sky.sky_type = 'NISHITA'
    sky.sun_elevation = math.radians(el)
    sky.sun_rotation = math.radians(az + yaw)
    sky.sun_intensity = 1.0
    sky.air_density = 1.0
    sky.dust_density = turbidity
    sky.ozone_density = 1.0
    bg = nt.nodes['Background']
    bg.inputs['Strength'].default_value = strength
    nt.links.new(sky.outputs['Color'], bg.inputs['Color'])
    bpy.context.scene.world = world
    return world


def filmic(exposure=0.0, look='AgX'):
    s = bpy.context.scene
    try:
        s.view_settings.view_transform = look
    except Exception:
        s.view_settings.view_transform = 'Filmic'
    s.view_settings.exposure = exposure
    try:
        s.view_settings.look = 'AgX - Punchy'
    except Exception:
        pass


def render(path):
    s = bpy.context.scene
    s.render.filepath = path
    s.render.image_settings.file_format = 'PNG' if path.lower().endswith('.png') else 'JPEG'
    s.render.image_settings.color_mode = 'RGB' if not s.render.film_transparent or path.lower().endswith('.jpg') else 'RGBA'
    s.render.image_settings.quality = 92
    bpy.ops.render.render(write_still=True)
    return path


# ---- Mesh helpers -----------------------------------------------------------------------------------------------

def grid_mesh(name, xs, ys, z=None):
    """A grid over the given x and y coordinates (1D arrays); z as a 2D array [len(ys), len(xs)] or None for flat."""
    nx, ny = len(xs), len(ys)
    X, Y = np.meshgrid(np.asarray(xs, np.float32), np.asarray(ys, np.float32))
    Z = np.zeros_like(X) if z is None else np.asarray(z, np.float32)
    co = np.stack([X, Y, Z], -1).reshape(-1, 3)
    i = np.arange((ny - 1) * (nx - 1))
    r, c = i // (nx - 1), i % (nx - 1)
    a = r * nx + c
    faces = np.stack([a, a + 1, a + nx + 1, a + nx], -1).astype(np.int32)
    me = bpy.data.meshes.new(name)
    me.vertices.add(len(co))
    me.vertices.foreach_set('co', co.ravel())
    me.loops.add(faces.size)
    me.loops.foreach_set('vertex_index', faces.ravel())
    me.polygons.add(len(faces))
    me.polygons.foreach_set('loop_start', np.arange(0, faces.size, 4, dtype=np.int32))
    me.polygons.foreach_set('loop_total', np.full(len(faces), 4, np.int32))
    me.polygons.foreach_set('use_smooth', np.ones(len(faces), bool))
    me.update(calc_edges=True)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def adaptive(ob, dicing=1.0):
    """Polygons no larger than `dicing` pixels where the camera looks (Cycles dices them at render time)."""
    m = ob.modifiers.new('Dice', 'SUBSURF')
    m.subdivision_type = 'SIMPLE'
    m.levels = 0
    m.render_levels = 1
    ob.cycles.use_adaptive_subdivision = True
    ob.cycles.dicing_rate = dicing
    return m


def float_image(name, arr, path):
    """A 2D float array as a Blender image for a height or mask texture (non-colour data), kept in an EXR file at
    `path` (the renderer reads textures from files; a picture that exists only in memory comes out blank)."""
    h, w = arr.shape[:2]
    img = bpy.data.images.new(name + '_src', w, h, alpha=False, float_buffer=True, is_data=True)
    px = np.zeros((h, w, 4), np.float32)
    if arr.ndim == 2:
        px[..., 0] = px[..., 1] = px[..., 2] = arr
    else:
        px[..., :arr.shape[2]] = arr
    px[..., 3] = 1.0
    img.pixels.foreach_set(px.ravel())
    img.filepath_raw = path
    img.file_format = 'OPEN_EXR'
    img.save()
    bpy.data.images.remove(img)
    out = bpy.data.images.load(path)
    out.name = name
    out.colorspace_settings.name = 'Non-Color'
    return out


# ---- Baking: the land under each Sun and under a white sky ------------------------------------------------------

def bake_render(out, yaw=0.0, samples=512, aovs=()):
    """Renders the scene once with every light of the set in its own light group, and writes one EXR per pass."""
    s = bpy.context.scene
    vl = bpy.context.view_layer
    suns = sun_set()
    for name, az, el in suns:
        sun_lamp(name, az, el, group=name, yaw=yaw)
    for name, az, el in glow_set():
        sun_lamp(name, az, el, angle=GLOW_ANGLE, group=name, yaw=yaw)
    white_sky(1.0, group='SKY')
    s.cycles.samples = samples
    s.render.film_transparent = True
    vl.use_pass_diffuse_color = True
    vl.use_pass_normal = True
    vl.use_pass_position = True
    vl.cycles.denoising_store_passes = True
    for a in aovs:
        aov = vl.aovs.add()
        aov.name = a
        aov.type = 'COLOR'
    s.use_nodes = True
    nt = s.node_tree
    for nd in list(nt.nodes):
        nt.nodes.remove(nd)
    rl = nt.nodes.new('CompositorNodeRLayers')
    fo = nt.nodes.new('CompositorNodeOutputFile')
    fo.base_path = out
    fo.format.file_format = 'OPEN_EXR'
    fo.format.color_depth = '32'
    fo.format.exr_codec = 'ZIP'
    fo.file_slots.clear()

    def slot(name, socket, denoise=False):
        fo.file_slots.new(name)
        src = rl.outputs[socket]
        if denoise:
            dn = nt.nodes.new('CompositorNodeDenoise')
            dn.use_hdr = True
            nt.links.new(src, dn.inputs['Image'])
            nt.links.new(rl.outputs['Denoising Normal'], dn.inputs['Normal'])
            nt.links.new(rl.outputs['Denoising Albedo'], dn.inputs['Albedo'])
            src = dn.outputs['Image']
        nt.links.new(src, fo.inputs[name])

    slot('alpha', 'Alpha')
    slot('albedo', 'DiffCol')
    slot('normal', 'Normal')
    slot('position', 'Position')
    for name, _, _ in suns + glow_set():
        slot('sun_' + name, 'Combined_' + name, denoise=True)
    slot('sky', 'Combined_SKY', denoise=True)
    for a in aovs:
        slot('aov_' + a, a)
    bpy.ops.render.render(write_still=False)
    return suns


def read_exr(path):
    """An EXR written above as a float array [rows from the top, columns, 4]."""
    img = bpy.data.images.load(path)
    w, h = img.size
    px = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(px)
    bpy.data.images.remove(img)
    return px.reshape(h, w, 4)[::-1].copy()


def luma(c):
    return c[..., 0] * 0.2126 + c[..., 1] * 0.7152 + c[..., 2] * 0.0722


def srgb(x):
    x = np.clip(x, 0.0, 1.0)
    return np.where(x <= 0.0031308, x * 12.92, 1.055 * np.power(x, 1 / 2.4) - 0.055)


def save8(path, arr, quality=82):
    """An 8-bit picture ([rows from the top, columns, 1|3|4], values 0..1 as they are to be stored) as PNG or WebP."""
    h, w = arr.shape[:2]
    ch = arr.shape[2] if arr.ndim == 3 else 1
    px = np.ones((h, w, 4), np.float32)
    if ch == 1:
        px[..., 0] = px[..., 1] = px[..., 2] = arr.reshape(h, w)
    else:
        px[..., :ch] = arr
    img = bpy.data.images.new('save8', w, h, alpha=(ch == 4), float_buffer=False, is_data=True)
    img.pixels.foreach_set(px[::-1].ravel())
    img.file_format = 'WEBP' if path.lower().endswith('.webp') else 'PNG'
    img.filepath_raw = path
    img.save(quality=quality)
    bpy.data.images.remove(img)


S_MAX = 2.0     # the shading pictures hold 0..S_MAX, as its square root (more steps in the shade)


def bake_pack(src, dst, suns, eye, aovs=(), quality=80, frame='0001', water=None):
    """From the EXRs of bake_render to what the app ships: see docs/DESIGN.md, "The land".

    a.webp   the land's colour under white light (sRGB), alpha = where there is land
    k.webp   the land lit by a plain white sky (sRGB)
    s_*.webp how brightly each pixel is lit by each Sun of sun_set() and by the two glows (square root of 0..S_MAX)
    g.png    R,G = distance (log2 metres / 18, 16 bits), at half size: the nearest thing in each block of four
             pixels (so that haze never spills from a far thing onto the rim of a near one)
    m.webp   the 'mask' AOV, if the scenery writes one: R = water, G = moves in the wind (grass, leaves),
             B = takes snow in winter
    land.json  sizes, the camera, the Suns; `water` = the water's level in metres below the eye, if there is any"""
    import json
    os.makedirs(dst, exist_ok=True)

    def rd(name):
        return read_exr(os.path.join(src, name + frame + '.exr'))

    alpha = np.clip(rd('alpha')[..., 0], 0, 1)
    rows = np.where(alpha.max(axis=1) > 0.004)[0]
    top = max(int(rows.min()) - 2, 0) if len(rows) else 0
    top -= top % 4
    crop = slice(top, None)
    alpha = alpha[crop]
    cover = np.maximum(alpha, 1e-4)[..., None]

    def unmult(c):
        return c[crop][..., :3] / cover

    albedo = unmult(rd('albedo'))
    lit = [unmult(rd('sun_' + n)) for n, _, _ in suns]
    sky = unmult(rd('sky'))
    # The colour the land shows under white sunlight: the hue of all its lit pictures together, as bright as its
    # diffuse colour (which is what the shading pictures are measured against).
    total = sum(lit) + 1e-6
    hue = total / luma(total)[..., None]
    ref = np.maximum(luma(albedo), 0.03)
    base = np.clip(hue * ref[..., None], 0, 1)
    save8(os.path.join(dst, 'a.webp'), np.concatenate([srgb(base), alpha[..., None]], -1), quality=quality + 6)
    save8(os.path.join(dst, 'k.webp'), srgb(sky), quality=quality)
    for (n, _, _), c in zip(suns, lit):
        sh = np.clip(luma(c) / ref, 0, S_MAX)
        save8(os.path.join(dst, 's_%s.webp' % n.lower()), np.sqrt(sh / S_MAX), quality=quality)
    for n, _, _ in glow_set():
        sh = np.clip(luma(unmult(rd('sun_' + n))) / ref, 0, S_MAX)
        save8(os.path.join(dst, 's_%s.webp' % n.lower()), np.sqrt(sh / S_MAX), quality=quality)
    # How far every pixel is (metres, on a log scale in 16 bits), for the air between and the clouds' shadows.
    pos = rd('position')[crop][..., :3]
    dist = np.linalg.norm(pos - np.asarray(eye, np.float32), axis=-1)
    dist = np.where(alpha > 0.5, dist, 2.0e5)
    hh, ww = dist.shape[0] // 2 * 2, dist.shape[1] // 2 * 2
    near = dist[:hh, :ww].reshape(hh // 2, 2, ww // 2, 2).min(axis=(1, 3))
    v = np.round(np.clip(np.log2(np.maximum(near, 1.0)) / 18.0, 0, 1) * 65535.0)
    hi = np.floor(v / 256.0)
    g = np.stack([hi / 255.0, (v - hi * 256.0) / 255.0, np.zeros_like(v)], -1)
    save8(os.path.join(dst, 'g.png'), g)
    check_skyline(alpha, top)
    if 'mask' in aovs:
        save8(os.path.join(dst, 'm.webp'), np.clip(rd('aov_mask')[crop][..., :3], 0, 1), quality=quality + 8)
    k = alpha.shape[1] / W                                      # a trial bake may be smaller than the frame
    meta = {
        'width': int(alpha.shape[1]), 'height': int(alpha.shape[0]), 'top': int(top), 'horizon': HORIZON * k, 'frameHeight': H * k,
        'focal': FOCAL, 'eye': list(map(float, eye)), 'sMax': S_MAX, 'mask': 'mask' in aovs,
        'water': None if water is None else float(water),
        'suns': [{'name': n.lower(), 'azimuth': az, 'elevation': el} for n, az, el in suns],
    }
    json.dump(meta, open(os.path.join(dst, 'land.json'), 'w'), indent=1)
    return meta


def add_mask(material, wind=0.0, snow=0.0, water_level=None):
    """Gives a material its place in the 'mask' picture the phone animates by (see bake_pack; pass aovs=('mask',) to
    run()). `wind`: how much the surface moves in the wind (0 rock .. 1 grass and leaves; these are also what turns
    with the seasons). `snow`: how much snow it takes in winter (0..1). `water_level`: the height (scene z, metres)
    of standing water: whatever of this material lies under it is marked as under water, and the phone draws the
    water over it (model the bed and the shore, never the water itself).

    `wind` and `snow` may be node sockets instead of numbers. Returns the AOV node."""
    nt = material.node_tree
    N, L = nt.nodes, nt.links
    aov = N.new('ShaderNodeOutputAOV')
    aov.aov_name = 'mask'
    comb = N.new('ShaderNodeCombineColor')
    for key, val in (('Green', wind), ('Blue', snow)):
        if isinstance(val, (int, float)):
            comb.inputs[key].default_value = float(val)
        else:
            L.new(val, comb.inputs[key])
    if water_level is not None:
        geo = N.new('ShaderNodeNewGeometry')
        sep = N.new('ShaderNodeSeparateXYZ')
        L.new(geo.outputs['Position'], sep.inputs[0])
        lt = N.new('ShaderNodeMath')
        lt.operation = 'LESS_THAN'
        lt.inputs[1].default_value = float(water_level)
        L.new(sep.outputs['Z'], lt.inputs[0])
        L.new(lt.outputs[0], comb.inputs['Red'])
    L.new(comb.outputs[0], aov.inputs['Color'])
    return aov


def preview_water(level, size=60000.0):
    """Still water at a height, for the preview pictures only (on the phone the water is drawn live, so a bake never
    holds any): a dark mirror with a breath of ripple on it."""
    bpy.ops.mesh.primitive_plane_add(size=size, location=(0.0, size * 0.4, level))
    ob = bpy.context.object
    ob.name = 'PreviewWater'
    mat = bpy.data.materials.new('PreviewWater')
    mat.use_nodes = True
    nt = mat.node_tree
    b = nt.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (0.012, 0.03, 0.035, 1)
    b.inputs['Roughness'].default_value = 0.03
    b.inputs['IOR'].default_value = 1.33
    b.inputs['Specular IOR Level'].default_value = 0.5
    noise = nt.nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 0.9
    noise.inputs['Detail'].default_value = 3.0
    bump = nt.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.03
    nt.links.new(noise.outputs['Fac'], bump.inputs['Height'])
    nt.links.new(bump.outputs['Normal'], b.inputs['Normal'])
    ob.data.materials.append(mat)
    return ob


def run(build, eye, yaw=0.0, aovs=(), water_level=None, preview_sun=(60.0, 28.0), exposure=-2.3):
    """The modes every scenery script offers. `build()` makes the scene (everything but camera and lights); `eye`
    is where the camera stands (scene metres; it looks along +y, level); `water_level` the height (scene z) of
    standing water, if the scenery has any (see add_mask).

      preview [az el]   a quick small picture under a physical sky with the Sun at that bearing and height
      look [az el]      the same at full size
      bakefast          a half-size trial bake      bake   the real one      pack   only repack the last bake

    NOOR_TAG (environment) is added to a preview's file name; NOOR_EXPOSURE overrides its exposure.
    """
    mode, out, rest = args()
    reset()
    scale = {'preview': 0.4, 'look': 1.0, 'bake': 1.0, 'bakefast': 0.5}.get(mode, 1.0)
    s = cycles(samples=96 if mode == 'preview' else 400, scale=scale)
    build()
    camera(eye, yaw)
    water = None if water_level is None else float(eye[2]) - float(water_level)
    if mode in ('preview', 'look'):
        az = float(rest[0]) if rest else preview_sun[0]
        el = float(rest[1]) if len(rest) > 1 else preview_sun[1]
        s.render.film_transparent = False
        preview_sky(az, el, yaw=yaw, strength=0.22)
        if water_level is not None:
            preview_water(water_level)
        s.view_settings.view_transform = 'Standard'
        s.view_settings.exposure = float(os.environ.get('NOOR_EXPOSURE', exposure))
        render(os.path.join(out, '%s_%d_%d%s.jpg' % (mode, az, el, os.environ.get('NOOR_TAG', ''))))
    elif mode in ('bake', 'bakefast'):
        raw = os.path.join(out, 'raw')
        suns = bake_render(raw, yaw=yaw, samples=160 if mode == 'bakefast' else 600, aovs=aovs)
        bake_pack(raw, os.path.join(out, 'land'), suns, eye, aovs=aovs, water=water)
    elif mode == 'pack':
        bake_pack(os.path.join(out, 'raw'), os.path.join(out, 'land'), sun_set(), eye, aovs=aovs, water=water)
