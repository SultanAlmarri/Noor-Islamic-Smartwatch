"""Reads the elevation models the sceneries are built from (GeoTIFF), with nothing beyond numpy."""
import struct
import zlib

import numpy as np


def read_geotiff(path):
    """A single-band float32 or int16 GeoTIFF (tiles or strips; no compression, deflate; predictor 1, 2 or 3).
    Returns the grid (row 0 is the north edge), the longitude and latitude of its top-left corner, and the size of
    a pixel in degrees (x, y)."""
    with open(path, 'rb') as f:
        data = f.read()
    bo = '<' if data[:2] == b'II' else '>'
    magic = struct.unpack(bo + 'H', data[2:4])[0]
    if magic != 42:
        raise ValueError('not a classic TIFF: ' + path)
    off = struct.unpack(bo + 'I', data[4:8])[0]
    n = struct.unpack(bo + 'H', data[off:off + 2])[0]
    sizes = {1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 6: 1, 7: 1, 8: 2, 9: 4, 10: 8, 11: 4, 12: 8, 16: 8}
    fmts = {1: 'B', 2: 'c', 3: 'H', 4: 'I', 6: 'b', 8: 'h', 9: 'i', 11: 'f', 12: 'd', 16: 'Q'}
    tags = {}
    for i in range(n):
        e = off + 2 + 12 * i
        tag, typ, cnt = struct.unpack(bo + 'HHI', data[e:e + 8])
        size = sizes.get(typ, 1) * cnt
        at = e + 8 if size <= 4 else struct.unpack(bo + 'I', data[e + 8:e + 12])[0]
        if typ in fmts and typ != 2:
            tags[tag] = struct.unpack(bo + str(cnt) + fmts[typ], data[at:at + size])
    w, h = tags[256][0], tags[257][0]
    bits = tags[258][0]
    comp = tags.get(259, (1,))[0]
    pred = tags.get(317, (1,))[0]
    fmt = tags.get(339, (1,))[0]
    dt = {(32, 3): 'f4', (16, 2): 'i2', (16, 1): 'u2', (32, 2): 'i4'}[(bits, fmt)]
    nb = bits // 8
    out = np.zeros((h, w), np.float32)

    def block(raw, bw, bh):
        if comp in (8, 32946):
            raw = zlib.decompress(raw)
        elif comp != 1:
            raise ValueError('compression %d not handled' % comp)
        a = np.frombuffer(raw, np.uint8)[:bw * bh * nb].reshape(bh, bw * nb)
        if pred == 3:
            a = np.cumsum(a, axis=1, dtype=np.uint8)               # the bytes of a row, differenced
            a = a.reshape(bh, nb, bw)[:, ::-1, :] if bo == '<' else a.reshape(bh, nb, bw)
            a = np.ascontiguousarray(a.transpose(0, 2, 1)).view(bo + dt).reshape(bh, bw)
            if bo == '<':
                pass
        else:
            a = np.ascontiguousarray(a).view(bo + dt).reshape(bh, bw)
            if pred == 2:
                a = np.cumsum(a, axis=1, dtype=a.dtype)
        return a.astype(np.float32)

    if 322 in tags:
        tw, th = tags[322][0], tags[323][0]
        offs, cnts = tags[324], tags[325]
        k = 0
        for ty in range(0, h, th):
            for tx in range(0, w, tw):
                if cnts[k]:                                 # (an empty tile holds nothing: sea)
                    a = block(data[offs[k]:offs[k] + cnts[k]], tw, th)
                    out[ty:ty + th, tx:tx + tw] = a[:min(th, h - ty), :min(tw, w - tx)]
                k += 1
    else:
        rps = tags.get(278, (h,))[0]
        offs, cnts = tags[273], tags[279]
        for k, y in enumerate(range(0, h, rps)):
            bh = min(rps, h - y)
            out[y:y + bh] = block(data[offs[k]:offs[k] + cnts[k]], w, bh)
    out[~np.isfinite(out) | (out < -500.0) | (out > 9000.0)] = 0.0       # 'no data' is the sea
    scale = tags[33550]
    tie = tags[33922]
    return out, tie[3] - tie[0] * scale[0], tie[4] + tie[1] * scale[1], scale[0], scale[1]
