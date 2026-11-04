#!/usr/bin/env python3
"""Builds Noor's offline gazetteer from GeoNames (https://www.geonames.org, CC BY 4.0).

Input: cities15000.txt (every place with 15,000+ people, incl. city districts) and admin1CodesASCII.txt.
Output: a gzipped UTF-8 TSV (shipped as assets/gazetteer.bin: Android strips ".gz" asset names), one place per line:
  geonameId, name, arabicName, latE5, lonE5, countryCode, admin1Code, admin1Name, population, timezone, elevationM

The Arabic name is taken from GeoNames' untagged alternate names: the first one written in Arabic script that uses no
letters specific to Persian or Urdu. It is empty when there is none (the app then shows the Latin name).

usage: build_gazetteer.py <cities15000.txt> <admin1CodesASCII.txt> <out.tsv.gz>
"""
import gzip, re, sys

ARABIC = re.compile(r'^[؀-ۿ\s\-‌‍\'’ـ]+$')
NOT_ARABIC_LANGS = set('پچژگیکٹڈڑںھہےۀې')


def arabic_name(alternates):
    for n in alternates.split(','):
        n = n.strip()
        if n and ARABIC.match(n) and not (set(n) & NOT_ARABIC_LANGS) and any('ء' <= ch <= 'ي' for ch in n):
            return n
    return ''


def main(cities, admin1, out):
    admin = {}
    for line in open(admin1, encoding='utf-8'):
        code, name, ascii_name, _ = line.rstrip('\n').split('\t')
        admin[code] = name
    rows = []
    for line in open(cities, encoding='utf-8'):
        f = line.rstrip('\n').split('\t')
        gid, name, ascii_name, alts, lat, lon = f[0], f[1], f[2], f[3], float(f[4]), float(f[5])
        cc, a1, pop, dem, tz = f[8], f[10], f[14] or '0', f[16] or '0', f[17]
        if not tz:
            continue
        elev = int(dem) if dem.lstrip('-').isdigit() and int(dem) > -500 else 0
        rows.append('\t'.join([gid, name.replace('\t', ' '), arabic_name(alts), str(round(lat * 1e5)), str(round(lon * 1e5)),
                               cc, a1, admin.get(f'{cc}.{a1}', ''), pop, tz, str(elev)]))
    with gzip.open(out, 'wt', encoding='utf-8', compresslevel=9) as g:
        g.write('\n'.join(rows) + '\n')
    print(f'{len(rows)} places -> {out}')


if __name__ == '__main__':
    main(*sys.argv[1:4])
