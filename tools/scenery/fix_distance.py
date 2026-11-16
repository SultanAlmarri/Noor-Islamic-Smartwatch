"""(0.18) Mends the distance picture (g.png) of a packed scenery where texels above the horizon carry, instead of the
distance of what they show, one small constant (the eye's or the water's height: a texel of a tree's thin edge whose
ray the packer did not find a hit for). In a morning fog those texels came out unfogged, as dark specks along every
treeline (the forest lake, the red rock). Each is given the median distance of its sound neighbours.

    python3 tools/scenery/fix_distance.py app/src/main/assets/sky2/land/pnw app/src/main/assets/sky2/land/canyon
"""
import json
import os
import sys

import numpy as np
from PIL import Image

for d in sys.argv[1:]:
    meta = json.load(open(os.path.join(d, 'land.json')))
    path = os.path.join(d, 'g.png')
    g = np.asarray(Image.open(path).convert('RGB')).astype(np.int64)
    code = g[..., 0] * 256 + g[..., 1]
    v = code / 65535.0 * 18.0                                   # log2 of the distance in metres
    h, w = v.shape
    hz = int((meta['horizon'] - meta['top']) / meta['height'] * h)
    up = v[:hz]
    vals, counts = np.unique(code[:hz][up < 4.0], return_counts=True)
    if not len(vals):
        print('%s: nothing to mend' % d); continue
    bad_code = vals[np.argmax(counts)]
    if counts.max() < 50:
        print('%s: no constant to mend (%d texels at most)' % (d, counts.max())); continue
    bad = np.zeros_like(v, dtype=bool)
    bad[:hz] = code[:hz] == bad_code
    fixed = 0
    out = code.copy()
    for y, x in zip(*np.nonzero(bad)):
        for r in (1, 2, 3):
            ys, xs = slice(max(0, y - r), y + r + 1), slice(max(0, x - r), x + r + 1)
            good = code[ys, xs][~bad[ys, xs]]
            if len(good):
                out[y, x] = int(np.median(good)); fixed += 1; break
    rgb = np.stack([out // 256, out % 256, np.zeros_like(out)], -1).astype(np.uint8)
    Image.fromarray(rgb, 'RGB').save(path, optimize=True)
    print('%s: %d texels of distance %.1f m mended' % (d, fixed, 2 ** (bad_code / 65535.0 * 18.0)))
