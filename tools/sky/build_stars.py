#!/usr/bin/env python3
"""Builds the home sky's bright stars (0.9.8) from the Yale Bright Star Catalogue, 5th edition (Hoffleit and Warren,
NASA Astronomical Data Center; public domain), as converted to JSON by Bretton Wade
(github.com/brettonw/YaleBrightStarCatalog, bsc5-short.json).

Kept: every star of magnitude 5.0 or brighter (what the eye sees under a dark sky, about 1,600 stars).
Output (app/src/main/res/raw/stars.bin, big-endian): int32 count, then per star float32 RA (degrees, J2000),
float32 Dec (degrees), float32 V magnitude, int16 colour temperature (K).

usage: build_stars.py <bsc5-short.json> <out.bin>
"""
import json, re, struct, sys


def angle(s, hours):
    nums = [float(x) for x in re.findall(r'[0-9.]+', s)]
    v = nums[0] + nums[1] / 60 + nums[2] / 3600
    if hours:
        return v * 15.0
    return -v if s.strip().startswith('-') else v


def main(src, out):
    stars = []
    for e in json.load(open(src, encoding='utf-8')):
        try:
            v = float(e['V'])
        except (KeyError, ValueError):
            continue
        if v > 5.0:
            continue
        k = int(float(e.get('K') or 6000))
        stars.append((angle(e['RA'], True), angle(e['Dec'], False), v, max(2000, min(30000, k))))
    stars.sort(key=lambda s: s[2])
    with open(out, 'wb') as f:
        f.write(struct.pack('>i', len(stars)))
        for ra, dec, v, k in stars:
            f.write(struct.pack('>fffh', ra, dec, v, k))
    print(f'{len(stars)} stars (brightest {stars[0][2]:.2f}) -> {out}')


if __name__ == '__main__':
    main(*sys.argv[1:3])
