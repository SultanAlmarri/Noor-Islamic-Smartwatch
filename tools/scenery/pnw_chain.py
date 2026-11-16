"""Lays the Pacific Northwest's new chain of mountains behind its pictures of 0.13.

0.14 replaced the scenery's mountain (Mount Rainier alone) by a chain of mountains (the Stuart Range, pnw.py). Only
what lies beyond the lake's wooded ridges changed, and the whole scene is too heavy to bake again here (32 000
trees; more memory than the cloud machines have). So the chain is baked alone, under the same Suns, through the same
camera:

    NOOR_PNW_ONLY=mountain blender -b --factory-startup -t 3 -P tools/scenery/pnw.py -- bake <out dir>

and laid behind the shipped pictures with this script (any Python 3 with numpy and Pillow):

    python3 tools/scenery/pnw_chain.py <out dir>/land app/src/main/assets/sky2/land/pnw [<old set>]

<old set> is the folder of the pictures to lay it behind (by default the destination itself: run it once on the
pictures of 0.13, or give their copy). Everything nearer than BEHIND metres in them is kept as it was and laid over
the chain by its own coverage, every picture in the light values it stands for (colours in linear light, the light
pictures relative to each pixel's own colour, as bake.bake_pack makes them): the trees' soft edges stand against
the new mountains, not against the old one or the sky.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

BEHIND = 11500.0          # metres: the farthest of the lake's ridges stands nearer than this, the chain beyond it
FAR = 2.0e5               # what the packer writes where there is no land
S_MAX = 2.0


def lin(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def srgb(x):
    x = np.clip(x, 0.0, 1.0)
    return np.where(x <= 0.0031308, 12.92 * x, 1.055 * np.power(np.maximum(x, 1e-6), 1.0 / 2.4) - 0.055)


def luma(c):
    return c[..., 0] * 0.2126 + c[..., 1] * 0.7152 + c[..., 2] * 0.0722


def load(d, name, mode):
    return np.asarray(Image.open(os.path.join(d, name)).convert(mode)).astype(np.float64) / 255.0


def save(path, arr, quality):
    a = np.clip(np.round(arr * 255.0), 0, 255).astype(np.uint8)
    im = Image.fromarray(a, 'L' if a.ndim == 2 else {3: 'RGB', 4: 'RGBA'}[a.shape[2]])
    if path.endswith('.png'):
        im.save(path)
    else:
        im.save(path, quality=quality, method=6, exact=True)


def frame(arr, top, rows, top_out, fill):
    """A picture cropped at row [top] of the frame, put on the rows of the output (from [top_out], [rows] of them)."""
    out = np.full((rows,) + arr.shape[1:], fill, np.float64)
    out[top - top_out:top - top_out + arr.shape[0]] = arr
    return out


def dist_of(g):
    v = np.round(g[..., 0] * 255.0) * 256.0 + np.round(g[..., 1] * 255.0)
    return np.exp2(v / 65535.0 * 18.0)


def skyline_profile(alpha, top, horizon, focal_px, rows, bins=360):
    """bake.skyline_profile: the tangent of the highest solid point of each of `bins` strips across the picture."""
    h, w = alpha.shape
    solid = alpha >= 0.5
    first = np.where(solid.any(axis=0), solid.argmax(axis=0), h)
    out = []
    for i in range(bins):
        a, b = int(i * w / bins), max(int((i + 1) * w / bins), int(i * w / bins) + 1)
        y = int(first[a:b].min())
        t = -0.05 if y >= h else (horizon - (top + y * rows / h)) / focal_px
        out.append(round(float(min(max(t, -0.05), 0.8)), 4))
    return out


def sun_lift(alpha, top, horizon, focal_px, rows):
    """bake.sun_lift: the tangents of how high the skyline stands where the Sun rises and sets."""
    h, w = alpha.shape
    out = []
    for u in (0.5 - 0.3024, 0.5 + 0.3024):
        low = 1.0e9
        for i in range(11):
            x = min(max(int((u - 0.0125 + 0.0025 * i) * w), 0), w - 1)
            solid = np.nonzero(alpha[:, x] >= 0.5)[0]
            y = int(solid[0]) if len(solid) else h
            low = min(low, (horizon - (top + y * rows / h)) / focal_px)
        out.append(float(min(max(low, 0.0), 0.5)))
    return out


def main():
    new_dir, dst = sys.argv[1], sys.argv[2]
    old_dir = sys.argv[3] if len(sys.argv) > 3 else dst
    mo = json.load(open(os.path.join(old_dir, 'land.json')))
    mn = json.load(open(os.path.join(new_dir, 'land.json')))
    assert mo['width'] == mn['width'] and [s['name'] for s in mo['suns']] == [s['name'] for s in mn['suns']]
    top = min(mo['top'], mn['top']) // 4 * 4
    rows = max(mo['top'] + mo['height'], mn['top'] + mn['height']) - top
    W = mo['width']

    # The old pictures' front: what stands nearer than BEHIND (the lake, its shores and trees, the wooded ridges).
    ao = load(old_dir, 'a.webp', 'RGBA')
    an = load(new_dir, 'a.webp', 'RGBA')
    go = load(old_dir, 'g.png', 'RGB')
    gn = load(new_dir, 'g.png', 'RGB')
    do = dist_of(go)
    near_o = np.repeat(np.repeat(do < BEHIND, 2, 0), 2, 1)[:ao.shape[0], :W]
    if near_o.shape[0] < ao.shape[0]:
        near_o = np.vstack([near_o, np.repeat(near_o[-1:], ao.shape[0] - near_o.shape[0], 0)])
    fF = frame(ao[..., 3] * near_o, mo['top'], rows, top, 0.0)
    fN = frame(an[..., 3], mn['top'], rows, top, 0.0)
    a = fF + (1.0 - fF) * fN
    wF = fF
    wN = (1.0 - fF) * fN
    cov = np.maximum(a, 1e-6)

    # The land's colour (linear), and its brightness (luma) that the light pictures are measured against.
    cF = frame(lin(ao[..., :3]), mo['top'], rows, top, 0.0)
    cN = frame(lin(an[..., :3]), mn['top'], rows, top, 0.0)
    col = (cF * wF[..., None] + cN * wN[..., None]) / cov[..., None]
    rF = np.maximum(luma(cF), 0.03)
    rN = np.maximum(luma(cN), 0.03)
    ref = np.maximum(luma(col), 0.03)
    os.makedirs(dst, exist_ok=True)
    save(os.path.join(dst, 'a.webp'), np.concatenate([srgb(col), a[..., None]], -1), 86)

    kF = frame(lin(load(old_dir, 'k.webp', 'RGB')), mo['top'], rows, top, 0.0)
    kN = frame(lin(load(new_dir, 'k.webp', 'RGB')), mn['top'], rows, top, 0.0)
    save(os.path.join(dst, 'k.webp'), srgb((kF * wF[..., None] + kN * wN[..., None]) / cov[..., None]), 80)

    names = [s['name'] for s in mo['suns']] + ['gl', 'gr']
    for n in names:
        f = 's_%s.webp' % n
        sF = frame(load(old_dir, f, 'L') ** 2 * S_MAX, mo['top'], rows, top, 0.0)
        sN = frame(load(new_dir, f, 'L') ** 2 * S_MAX, mn['top'], rows, top, 0.0)
        s = (sF * rF * wF + sN * rN * wN) / (ref * cov)
        save(os.path.join(dst, f), np.sqrt(np.clip(s, 0, S_MAX) / S_MAX), 80)

    if mo.get('mask'):
        mF = frame(load(old_dir, 'm.webp', 'RGB'), mo['top'], rows, top, 0.0)
        mN = frame(load(new_dir, 'm.webp', 'RGB'), mn['top'], rows, top, 0.0) if mn.get('mask') else np.zeros_like(mF)
        save(os.path.join(dst, 'm.webp'), (mF * wF[..., None] + mN * wN[..., None]) / cov[..., None], 88)

    # Distance, at half size: the nearest thing in each block of four pixels, as the packer has it.
    hh = (rows + 1) // 2
    dF = np.full((hh, go.shape[1]), FAR)
    dN = np.full((hh, gn.shape[1]), FAR)
    oF, oN = (mo['top'] - top) // 2, (mn['top'] - top) // 2
    dF[oF:oF + do.shape[0]] = np.where(do < BEHIND, do, FAR)
    dn = dist_of(gn)
    dN[oN:oN + dn.shape[0]] = dn
    d = np.minimum(dF, dN)
    v = np.round(np.clip(np.log2(np.maximum(d, 1.0)) / 18.0, 0, 1) * 65535.0)
    hi = np.floor(v / 256.0)
    save(os.path.join(dst, 'g.png'), np.stack([hi / 255.0, (v - hi * 256.0) / 255.0, np.zeros_like(v)], -1), 100)

    meta = dict(mo)
    meta['top'] = int(top)
    meta['height'] = int(rows)
    meta['lift'] = sun_lift(a, top, mo['horizon'], mo.get('focal', 1.15) * W, rows)
    meta['skyline'] = skyline_profile(a, top, mo['horizon'], mo.get('focal', 1.15) * W, rows)
    meta['chain'] = 'Stuart Range (pnw.py, pnw_chain.py)'
    json.dump(meta, open(os.path.join(dst, 'land.json'), 'w'), indent=1)
    print('chain laid behind %s: %d x %d from row %d, lift %s' % (old_dir, W, rows, top, meta['lift']))


if __name__ == '__main__':
    main()
