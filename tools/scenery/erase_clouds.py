"""Takes clouds that stand alone in clear sky out of a packed cloud set, by a box on its pictures.

For a set whose bake is right but for one group that reads wrong. The first was lo_cb_line (0.14.1): five small
cumulus of its nearer fair-weather field lay one behind another on one bearing, and the view showed them as a column
of flat cloudlets piled up over the sea, which no photograph of such a sky shows. Baking the set again costs an hour
of the CPU; the group stands alone, so its pixels are made empty in every picture instead.

    python3 tools/scenery/erase_clouds.py <set folder> x0 x1 y0 y1

The box is in the set's own pixels (columns x0..x1, rows y0..y1). The script stops if the box's edge touches cloud
(something larger than the group would be cut). `cover` and `coverSeen` in sky.json shrink by the share of opacity
taken out. Any Python 3 with numpy and Pillow.
"""
import glob
import json
import os
import sys

import numpy as np
from PIL import Image

d = sys.argv[1]
x0, x1, y0, y1 = (int(v) for v in sys.argv[2:6])
a = np.asarray(Image.open(os.path.join(d, 'a.webp')).convert('L')).astype(np.float64)
edge = max(a[y0, x0:x1 + 1].max(), a[y1, x0:x1 + 1].max(), a[y0:y1 + 1, x0].max(), a[y0:y1 + 1, x1].max())
if edge > 2:
    sys.exit('the box cuts through cloud on its edge (opacity %d of 255): make it larger' % edge)
share = a[y0:y1 + 1, x0:x1 + 1].sum() / max(a.sum(), 1.0)
for f in sorted(glob.glob(os.path.join(d, '*.webp'))):
    im = Image.open(f)
    px = np.asarray(im).copy()
    px[y0:y1 + 1, x0:x1 + 1] = 0
    Image.fromarray(px, im.mode).save(f, quality=92, method=6, exact=True)
meta = json.load(open(os.path.join(d, 'sky.json')))
for k in ('cover', 'coverSeen'):
    if k in meta:
        meta[k] = meta[k] * (1.0 - share)
meta.setdefault('erased', []).append([x0, x1, y0, y1])
json.dump(meta, open(os.path.join(d, 'sky.json'), 'w'), indent=1)
print('erased %d..%d x %d..%d in %s: %.1f %% of its cloud' % (x0, x1, y0, y1, d, share * 100))
