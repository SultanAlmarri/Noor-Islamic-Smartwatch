#!/usr/bin/env python3
"""Builds the phone's offline time-zone map (0.9.7) from timezone-boundary-builder
(https://github.com/evansiroky/timezone-boundary-builder, ODbL; boundaries from OpenStreetMap).

Input: combined-with-oceans.json (the "timezones-with-oceans" release: every IANA zone, land zones extended to
territorial waters). The open-sea zones (Etc/GMT±n) are not stored: they are 15-degree bands of longitude, which
the phone computes. Land zones are simplified to 0.001 degree (about 100 m; borders keep their shape to within
that) and stored at 1e-4 degree (about 11 m).

Output (assets/tzmap.bin, gzip):
  "NTZ1", varint zoneCount, then each zone id as varint length + UTF-8,
  varint polygonCount, then per polygon: varint zone, zigzag minX minY maxX maxY, varint ringCount,
  and per ring: varint vertexCount, then zigzag delta x, y per vertex (the ring is closed implicitly).
Coordinates are x = longitude, y = latitude, in 1e-4 degree.

usage: build_timezones.py <combined-with-oceans.json> <out.bin>
"""
import gzip, json, sys
from shapely.geometry import shape

TOLERANCE = 0.001
Q = 1e4


def varint(out, v):
    while v >= 0x80:
        out.append((v & 0x7f) | 0x80); v >>= 7
    out.append(v)


def zig(v):
    return (v << 1) ^ (v >> 63)


def main(src, out):
    d = json.load(open(src))
    feats = [f for f in d['features'] if not f['properties']['tzid'].startswith('Etc/')]
    zones = sorted({f['properties']['tzid'] for f in feats})
    zi = {z: i for i, z in enumerate(zones)}
    o = bytearray(b'NTZ1')
    varint(o, len(zones))
    for z in zones:
        b = z.encode('utf-8'); varint(o, len(b)); o += b
    polys = []
    for f in feats:
        g = shape(f['geometry']).simplify(TOLERANCE, preserve_topology=True)
        for p in ([g] if g.geom_type == 'Polygon' else list(g.geoms)):
            if p.is_empty: continue
            rings = []
            for r in [p.exterior] + list(p.interiors):
                pts = [(round(x * Q), round(y * Q)) for x, y in list(r.coords)[:-1]]
                dedup = [pt for i, pt in enumerate(pts) if i == 0 or pt != pts[i - 1]]
                if len(dedup) >= 3: rings.append(dedup)
            if rings: polys.append((zi[f['properties']['tzid']], rings))
    varint(o, len(polys))
    nv = 0
    for z, rings in polys:
        xs = [x for x, _ in rings[0]]; ys = [y for _, y in rings[0]]
        varint(o, z)
        for v in (min(xs), min(ys), max(xs), max(ys)): varint(o, zig(v))
        varint(o, len(rings))
        for r in rings:
            varint(o, len(r)); px = py = 0
            for x, y in r:
                varint(o, zig(x - px)); varint(o, zig(y - py)); px, py = x, y
            nv += len(r)
    with gzip.open(out, 'wb', compresslevel=9) as g:
        g.write(bytes(o))
    print(f'{len(zones)} zones, {len(polys)} polygons, {nv} vertices -> {out}')


if __name__ == '__main__':
    main(*sys.argv[1:3])
