"""Clouds for the home screen, rendered the way the land is: a whole sky of clouds (volumes) is path traced once
through Noor's camera under a set of unit Suns, and the phone lights it with the Sun of the moment.

A "set" is one layer of one kind of cloud (fair-weather cumulus, a stratocumulus sheet, cirrus...). The phone shows a
low, a middle and a high set over one another, slides them slowly with the wind and fades from one to the next as
the day's weather changes.

    blender -b --factory-startup -P tools/scenery/<script>.py -- preview|look|bakefast|bake <out dir> [bearing height]

The picture is the sky part of Noor's frame only (bake.W wide and down to a little under the horizon), drawn PAN
times wider than the phone's view so the clouds have room to drift.

What is shipped (app/src/main/assets/sky2/clouds/<id>/), all greyscale:
    a.webp          how opaque the clouds are (0..1)
    f.webp          how far they are: the opacity-weighted mean of 1 - exp(-distance / FAR_KM)
    k.webp, g.webp  their light under a white sky above, and from a white ground below
    s_<sun>.webp    their light under each Sun: bake.sun_set(), the two glows, and four Suns under the horizon
                    (lu0, lu1, ru0, ru1: the light that still reaches clouds after sunset, from below)
    sky.json        sizes, the Suns, the layer and its height
Light is stored as sqrt(radiance / R_MAX), where 1 is a white surface facing the Sun; it is already multiplied by
the opacity (nothing is divided by a.webp on the phone).
"""
import json
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bake      # noqa: E402

PAN = 1.25                     # the picture is this many times wider than the phone's view
ROWS = bake.HORIZON + 24       # rows of Noor's frame that are kept (the horizon and a little under it)
R_MAX = 4.0
FAR_KM = 30.0
UNDER = [('LU0', -bake.RISE_AZIMUTH, -1.5), ('LU1', -bake.RISE_AZIMUTH, -4.0),
         ('RU0', bake.RISE_AZIMUTH, -1.5), ('RU1', bake.RISE_AZIMUTH, -4.0)]
EARTH = 6371000.0


def lights():
    """Every Sun a cloud set is rendered under: (name, bearing, height, angular size in degrees)."""
    out = [(n, az, el, 0.6) for n, az, el in bake.sun_set()]
    out += [(n, az, el, bake.GLOW_ANGLE) for n, az, el in bake.glow_set()]
    out += [(n, az, el, 0.6) for n, az, el in UNDER]
    return out


def drop(distance):
    """How far below the eye's level the round Earth has fallen at a distance (metres): subtract it from the height
    of anything placed far away, so that distant cloud sinks towards the horizon as it does in life."""
    return distance * distance / (2.0 * EARTH)


def camera(eye=(0.0, 0.0, 2.0)):
    s = bpy.context.scene
    width = int(round(bake.W * PAN * s.render.resolution_percentage / 100.0))
    cam = bpy.data.cameras.new('NoorSkyCamera')
    cam.sensor_fit = 'HORIZONTAL'
    cam.sensor_width = 36.0 * PAN
    cam.lens = bake.FOCAL * 36.0
    cam.shift_y = -(ROWS / 2 - bake.HORIZON) / (bake.W * PAN)
    cam.clip_start = 1.0
    cam.clip_end = 600000.0
    ob = bpy.data.objects.new('NoorSkyCamera', cam)
    ob.location = eye
    ob.rotation_euler = (math.pi / 2, 0.0, 0.0)
    s.collection.objects.link(ob)
    s.camera = ob
    s.render.resolution_x = int(round(bake.W * PAN))
    s.render.resolution_y = ROWS
    return ob


def cloud_material(name='Cloud', sigma=0.03, anisotropy=0.7, colour=(1.0, 1.0, 1.0)):
    """A cloud: `sigma` is its extinction per metre where the density grid is 1. (Real cumulus is about 0.06; less
    renders much faster and reads the same from kilometres away.)"""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    for nd in list(nt.nodes):
        nt.nodes.remove(nd)
    o = nt.nodes.new('ShaderNodeOutputMaterial')
    v = nt.nodes.new('ShaderNodeVolumePrincipled')
    v.inputs['Color'].default_value = (*colour, 1)
    v.inputs['Density'].default_value = sigma
    v.inputs['Anisotropy'].default_value = anisotropy
    nt.links.new(v.outputs[0], o.inputs['Volume'])
    return mat


def add_volume(path, location, material, rotation=0.0, scale=(1.0, 1.0, 1.0), data=None):
    """A VDB file as a volume object. Pass `data` (an object made before from the same file) to share its grid."""
    if data is None:
        bpy.ops.object.volume_import(filepath=path)
        ob = bpy.context.object
        ob.data.materials.append(material)
    else:
        ob = bpy.data.objects.new(data.name, data.data)
        bpy.context.scene.collection.objects.link(ob)
    ob.location = location
    ob.rotation_euler = (0.0, 0.0, rotation)
    ob.scale = scale
    return ob


def _far_materials():
    """Swaps every volume material for one that only glows by how far it is (and still hides what is behind)."""
    for mat in bpy.data.materials:
        if not mat.use_nodes:
            continue
        nt = mat.node_tree
        vols = [n for n in nt.nodes if n.type == 'PRINCIPLED_VOLUME']
        if not vols:
            continue
        v = vols[0]
        v.inputs['Color'].default_value = (0, 0, 0, 1)                    # absorbs, scatters nothing
        geo = nt.nodes.new('ShaderNodeNewGeometry')
        ln = nt.nodes.new('ShaderNodeVectorMath'); ln.operation = 'LENGTH'
        nt.links.new(geo.outputs['Position'], ln.inputs[0])
        m = nt.nodes.new('ShaderNodeMath'); m.operation = 'MULTIPLY'; m.inputs[1].default_value = -1.0 / (FAR_KM * 1000.0)
        nt.links.new(ln.outputs['Value'], m.inputs[0])
        e = nt.nodes.new('ShaderNodeMath'); e.operation = 'EXPONENT'
        nt.links.new(m.outputs[0], e.inputs[0])
        f = nt.nodes.new('ShaderNodeMath'); f.operation = 'SUBTRACT'; f.inputs[0].default_value = 1.0
        nt.links.new(e.outputs[0], f.inputs[1])
        # (A medium that glows by f times what it absorbs sends f times its opacity to the eye. The node's emission is
        # per metre whatever the density, so the density is put in by hand.)
        att = nt.nodes.new('ShaderNodeAttribute'); att.attribute_name = 'density'
        em = nt.nodes.new('ShaderNodeMath'); em.operation = 'MULTIPLY'
        nt.links.new(f.outputs[0], em.inputs[0]); nt.links.new(att.outputs['Fac'], em.inputs[1])
        em2 = nt.nodes.new('ShaderNodeMath'); em2.operation = 'MULTIPLY'; em2.inputs[1].default_value = v.inputs['Density'].default_value
        nt.links.new(em.outputs[0], em2.inputs[0])
        v.inputs['Emission Color'].default_value = (1, 1, 1, 1)
        nt.links.new(em2.outputs[0], v.inputs['Emission Strength'])


def bake_render(out, samples=1024, bounces=96):
    s = bpy.context.scene
    vl = bpy.context.view_layer
    ls = lights()
    for name, az, el, ang in ls:
        bake.sun_lamp(name, az, el, angle=ang, group=name)
    # A white sky above only, and white ground below, each its own light.
    world = bpy.data.worlds.new('NoorSkyAbove')
    world.use_nodes = True
    nt = world.node_tree
    bg = nt.nodes['Background']
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(tc.outputs['Generated'], sep.inputs[0])
    up = nt.nodes.new('ShaderNodeMath'); up.operation = 'GREATER_THAN'; up.inputs[1].default_value = 0.0
    nt.links.new(sep.outputs['Z'], up.inputs[0])
    nt.links.new(up.outputs[0], bg.inputs['Strength'])
    bg.inputs['Color'].default_value = (1, 1, 1, 1)
    s.world = world
    vl.lightgroups.add(name='SKY')
    world.lightgroup = 'SKY'
    bpy.ops.mesh.primitive_plane_add(size=1200000.0, location=(0, 0, -300.0))
    gnd = bpy.context.object
    gm = bpy.data.materials.new('GroundLight')
    gm.use_nodes = True
    gt = gm.node_tree
    for nd in list(gt.nodes):
        gt.nodes.remove(nd)
    go = gt.nodes.new('ShaderNodeOutputMaterial')
    ge = gt.nodes.new('ShaderNodeEmission')
    ge.inputs['Strength'].default_value = 1.0
    gt.links.new(ge.outputs[0], go.inputs['Surface'])
    gnd.data.materials.append(gm)
    gnd.visible_camera = False
    gnd.visible_shadow = False                    # the Suns under the horizon shine up through it
    vl.lightgroups.add(name='GND')
    gnd.lightgroup = 'GND'

    s.cycles.samples = samples
    s.cycles.max_bounces = bounces
    s.cycles.volume_bounces = bounces
    s.cycles.use_adaptive_sampling = False
    s.render.film_transparent = True
    vl.cycles.denoising_store_passes = True
    s.use_nodes = True
    ct = s.node_tree
    for nd in list(ct.nodes):
        ct.nodes.remove(nd)
    rl = ct.nodes.new('CompositorNodeRLayers')
    fo = ct.nodes.new('CompositorNodeOutputFile')
    fo.base_path = out
    fo.format.file_format = 'OPEN_EXR'
    fo.format.color_depth = '32'
    fo.format.exr_codec = 'ZIP'
    fo.file_slots.clear()

    def slot(name, socket, denoise=False):
        fo.file_slots.new(name)
        src = rl.outputs[socket]
        if denoise:
            dn = ct.nodes.new('CompositorNodeDenoise')
            dn.use_hdr = True
            ct.links.new(src, dn.inputs['Image'])
            src = dn.outputs['Image']
        ct.links.new(src, fo.inputs[name])

    slot('alpha', 'Alpha')
    for name, _, _, _ in ls:
        slot('sun_' + name, 'Combined_' + name, denoise=True)
    slot('sky', 'Combined_SKY', denoise=True)
    slot('gnd', 'Combined_GND', denoise=True)
    bpy.ops.render.render(write_still=False)

    # Once more, quickly: how far the clouds are.
    for ob in list(bpy.data.objects):
        if ob.type == 'LIGHT':
            bpy.data.objects.remove(ob)
    gnd.hide_render = True
    bg.inputs['Strength'].default_value = 0.0
    for l in list(nt.links):
        if l.to_socket == bg.inputs['Strength']:
            nt.links.remove(l)
    _far_materials()
    s.cycles.samples = 48
    s.cycles.max_bounces = 0
    s.cycles.volume_bounces = 0
    fo.file_slots.clear()
    for nd in list(ct.nodes):
        if nd.type == 'DENOISE':
            ct.nodes.remove(nd)
    fo.file_slots.new('far')
    ct.links.new(rl.outputs['Image'], fo.inputs['far'])
    bpy.ops.render.render(write_still=False)
    return ls


def bake_pack(src, dst, ls, info, quality=78, frame='0001'):
    os.makedirs(dst, exist_ok=True)

    def rd(name):
        return bake.read_exr(os.path.join(src, name + frame + '.exr'))

    alpha = np.clip(rd('alpha')[..., 0], 0, 1)
    bake.save8(os.path.join(dst, 'a.webp'), alpha, quality=quality + 8)
    far = bake.luma(rd('far')[..., :3]) / np.maximum(alpha, 1e-3)
    bake.save8(os.path.join(dst, 'f.webp'), np.clip(far, 0, 1) * (alpha > 0.004), quality=quality)

    def light(name):
        return np.sqrt(np.clip(bake.luma(rd(name)[..., :3]), 0, R_MAX) / R_MAX)

    bake.save8(os.path.join(dst, 'k.webp'), light('sky'), quality=quality)
    bake.save8(os.path.join(dst, 'g.webp'), light('gnd'), quality=quality)
    for name, _, _, _ in ls:
        bake.save8(os.path.join(dst, 's_%s.webp' % name.lower()), light('sun_' + name), quality=quality)
    k = alpha.shape[1] / (bake.W * PAN)
    meta = dict(info)
    meta.update({
        'width': int(alpha.shape[1]), 'height': int(alpha.shape[0]), 'horizon': bake.HORIZON * k,
        'viewWidth': bake.W * k, 'rMax': R_MAX, 'farKm': FAR_KM, 'cover': float(alpha.mean()),
        'suns': [{'name': n.lower(), 'azimuth': az, 'elevation': el} for n, az, el, _ in ls],
    })
    json.dump(meta, open(os.path.join(dst, 'sky.json'), 'w'), indent=1)
    print('packed', dst, 'cover %.2f' % meta['cover'], flush=True)
    return meta


def run(build, info, eye=(0.0, 0.0, 2.0), preview_sun=(60.0, 28.0), exposure=-2.6, samples=1024, bounces=96):
    """The modes of a cloud script. `build()` makes the clouds (volume objects, see add_volume and cloud_material).
    `info`: what the phone needs to know of the set: {'layer': 'low'|'mid'|'high', 'altitude': km (its middle, for
    the colour of the sunlight up there), 'kind': a word}."""
    mode, out, rest = bake.args()
    bake.reset()
    scale = {'preview': 0.4, 'look': 1.0, 'bake': 1.0, 'bakefast': 0.5}.get(mode, 1.0) * float(os.environ.get('NOOR_SCALE', 1.0))
    s = bake.cycles(samples=int(os.environ.get('NOOR_SPP', 192)), scale=scale)
    s.cycles.max_bounces = bounces
    s.cycles.volume_bounces = bounces
    s.cycles.volume_step_rate = 0.5
    s.cycles.volume_max_steps = 2048
    build()
    camera(eye)
    if mode in ('preview', 'look'):
        az = float(rest[0]) if rest else preview_sun[0]
        el = float(rest[1]) if len(rest) > 1 else preview_sun[1]
        s.render.film_transparent = False
        bake.preview_sky(az, el, strength=0.22)
        bpy.ops.mesh.primitive_plane_add(size=1200000.0, location=(0, 0, -2.0))       # ground: its light on the bases
        g = bpy.context.object
        gm = bpy.data.materials.new('PreviewGround')
        gm.use_nodes = True
        gm.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (0.30, 0.26, 0.20, 1)
        g.data.materials.append(gm)
        s.view_settings.view_transform = 'Standard'
        s.view_settings.exposure = float(os.environ.get('NOOR_EXPOSURE', exposure))
        bake.render(os.path.join(out, '%s_%d_%d%s.jpg' % (mode, az, el, os.environ.get('NOOR_TAG', ''))))
    elif mode in ('bake', 'bakefast'):
        raw = os.path.join(out, 'raw')
        ls = bake_render(raw, samples=samples if mode == 'bake' else samples // 3, bounces=bounces)
        bake_pack(raw, os.path.join(out, 'sky'), ls, info)
    elif mode == 'pack':
        bake_pack(os.path.join(out, 'raw'), os.path.join(out, 'sky'), lights(), info)
