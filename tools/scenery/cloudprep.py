"""Prepares the CGHEVEN cloud volumes (see sources/clouds_low.md) for the sky scripts. The files themselves may not
be passed on: they, and everything this writes, stay outside the repository, with the work files.

    <Blender's python> tools/scenery/cloudprep.py

For every cloud in %USERPROFILE%/ProjectMinhad/NoorTools/assets/cgheven/<name>/*.vdb it writes, into
.../NoorTools/work/clouds_low/cg/:

- three copies at falling fineness (<name>_0.vdb as fine as the original, _1 half, _2 a quarter: far clouds are read
  from the coarse ones, which renders many times faster and cannot be told apart at their distance), each **cut off
  flat at the cloud's base**: the originals trail wisps under their bases, which in a sky full of them read as
  drips. The density is eased in over a few voxels above the base level, and the level wanders a little from place
  to place, so a base is flat and slightly ragged, as a real cumulus base is;
- info.json: each cloud's extent (in the file's units; y is up), its base level, its copies.
"""
import glob
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cloudgen      # noqa: E402

root = os.path.abspath(os.path.join(os.path.dirname(sys.executable), '..', '..', '..'))
for d in (root, os.path.join(root, 'blender.shared')):
    if os.path.isdir(d):
        os.add_dll_directory(d)
import openvdb       # noqa: E402

TOOLS = os.path.join(os.path.expanduser('~'), 'ProjectMinhad', 'NoorTools')
SRC = os.path.join(TOOLS, 'assets', 'cgheven')
OUT = os.path.join(TOOLS, 'work', 'clouds_low', 'cg')


def main():
    os.makedirs(OUT, exist_ok=True)
    info = {}
    for f in sorted(glob.glob(os.path.join(SRC, '*', '*.vdb'))):
        name = os.path.basename(os.path.dirname(f))
        g = openvdb.read(f, 'density')
        lo, hi = g.evalActiveVoxelBoundingBox()
        vs = g.transform.voxelSize()[0]
        dim = [hi[i] - lo[i] + 1 for i in range(3)]
        arr = np.zeros(dim, np.float32)                     # (x, y, z), y up
        g.copyToArray(arr, ijk=lo)
        # The base: the level at which the cloud first spreads to a good part of its full width. Under it, wisps.
        spread = (arr > 0.1).mean(axis=(0, 2))
        base = int(np.argmax(spread >= 0.45 * spread.max()))
        rng = np.random.default_rng(abs(hash(name)) % (2 ** 31))
        wander = cloudgen.smooth_field((dim[0], dim[2]), 1.0, 14.0, rng) * 1.6 + cloudgen.smooth_field((dim[0], dim[2]), 1.0, 4.0, rng) * 0.8
        y = np.arange(dim[1], dtype=np.float32)[None, :, None]
        ramp = np.clip((y - (base - 1.0 + wander[:, None, :])) / 7.0, 0.0, 1.0)
        arr *= ramp * ramp * (3.0 - 2.0 * ramp)
        solid = arr > 0.03
        xs = np.nonzero(solid.any(axis=(1, 2)))[0]
        ys = np.nonzero(solid.any(axis=(0, 2)))[0]
        zs = np.nonzero(solid.any(axis=(0, 1)))[0]
        info[name] = {'voxel': vs, 'base': float((lo[1] + base) * vs),
                      'x': [float((lo[0] + xs.min()) * vs), float((lo[0] + xs.max()) * vs)],
                      'y': [float((lo[1] + ys.min()) * vs), float((lo[1] + ys.max()) * vs)],
                      'z': [float((lo[2] + zs.min()) * vs), float((lo[2] + zs.max()) * vs)],
                      'mean': float(arr[solid].mean()), 'lods': []}
        d = arr.transpose(2, 1, 0)                          # (z, y, x) for cloudgen
        v = vs
        for k in range(3):
            p = os.path.join(OUT, '%s_%d.vdb' % (name, k)).replace(os.sep, '/')
            cloudgen.write_vdb(p, d, (lo[0] * vs, lo[1] * vs, lo[2] * vs), v, tolerance=0.002)
            info[name]['lods'].append(p)
            d = cloudgen.halve(d)
            v *= 2.0
        print(name, dim, 'base at %.2f (voxel %d of %d)' % (info[name]['base'], base, dim[1]),
              'height %.2f' % (info[name]['y'][1] - info[name]['base']), flush=True)
    json.dump(info, open(os.path.join(OUT, 'info.json'), 'w'), indent=1)


if __name__ == '__main__':
    main()
