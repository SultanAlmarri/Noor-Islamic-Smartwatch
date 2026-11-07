#!/usr/bin/env python3
"""Builds the phone's offline world places (0.9.7) from GeoNames (https://www.geonames.org, CC BY 4.0).

Input: allCountries.txt (the full GeoNames dump), cities500.txt, admin1CodesASCII.txt, alternateNamesV2.txt.
Kept: every town and city with 500+ inhabitants or that is the seat of an administrative division (cities500), every
section of a populated place (feature code PPLX: districts, neighbourhoods, quarters) in the world, and, in countries
with at most SMALL_COUNTRY populated places in GeoNames (most of the Arab world among them, where GeoNames often has
no population figures), every populated place, villages included.

Output (assets/world.bin, gzip): columnar, so it compresses well and loads into a few flat arrays on the phone.
  "NWP1"
  strings  zones          (IANA time-zone ids)
  strings  admins         ("CC.code" keys)
  strings  adminNames     (English, GeoNames)
  strings  adminNamesAr   (Arabic, from the ADM1 feature's alternate names; "" when none)
  varint   n
  strings  names[n]; strings namesAr[n] (the Arabic name shown, then other Arabic spellings to search by, '|'-separated)
  ints     lat[n], lon[n]  (1e-4 degree, zigzag deltas)
  ints     admin[n] (index), pop[n], zone[n] (index), elev[n] (metres, zigzag), kind[n] (0 town or city, 1 neighbourhood)
Rows are sorted by country, region, kind, then population (largest first).
A "strings" block is a varint byte length then UTF-8 lines joined by '\n'.

Arabic names: GeoNames' names tagged "ar" (the preferred one shown, without diacritics where there is a choice; the
others kept for search); where none is tagged, the first untagged alternate name written in Arabic script with no
letters specific to Persian or Urdu.

usage: build_world.py <allCountries.txt> <cities500.txt> <admin1CodesASCII.txt> <alternateNamesV2.txt> <out.bin>
"""
import gzip, io, re, sys

ARABIC = re.compile(r'^[؀-ۿ\s\-‌‍\'’ـ]+$')
NOT_ARABIC_LANGS = set('پچژگیکٹڈڑںھہےۀې')


def arabic_name(alternates):
    for n in alternates.split(','):
        n = n.strip()
        if n and ARABIC.match(n) and not (set(n) & NOT_ARABIC_LANGS) and any('ء' <= ch <= 'ي' for ch in n):
            return n
    return ''


DIACRITICS = re.compile('[\u064B-\u065F\u0670\u0640]')


def load_arabic(path):
    """geonameid -> Arabic names, best first: preferred, then without diacritics; colloquial and historic names last."""
    names = {}
    for line in open(path, encoding='utf-8'):
        f = line.rstrip('\n').split('\t')
        if len(f) < 8 or f[2] != 'ar' or not f[3].strip():
            continue
        pref, colloq, hist = f[4] == '1', f[6] == '1', f[7] == '1'
        names.setdefault(f[1], []).append((colloq or hist, not pref, bool(DIACRITICS.search(f[3])), len(names.get(f[1], [])), f[3].strip()))
    return {k: [n for *_, n in sorted(v)] for k, v in names.items()}


def arabic_names(gid, alternates, tagged):
    """The Arabic name to show, then the other spellings (for search), '|'-separated; '' when none."""
    out = list(tagged.get(gid, []))
    untagged = [n.strip() for n in alternates.split(',') if n.strip() and ARABIC.match(n.strip()) and not (set(n) & NOT_ARABIC_LANGS)
                and any('ء' <= ch <= 'ي' for ch in n)]
    for n in untagged:
        if n not in out:
            out.append(n)
    return '|'.join(n.replace('|', ' ') for n in out[:6])


def varint(out, v):
    while v >= 0x80:
        out.append((v & 0x7f) | 0x80); v >>= 7
    out.append(v)


def zig(v):
    return (v << 1) ^ (v >> 63)


def strings(out, items):
    b = '\n'.join(items).encode('utf-8')
    varint(out, len(b)); out += b


def ints(out, values, delta=False, signed=False):
    prev = 0
    for v in values:
        x = v - prev if delta else v
        if delta: prev = v
        varint(out, zig(x) if (signed or delta) else x)


def clean(s):
    return s.replace('\t', ' ').replace('\n', ' ').strip()


SMALL_COUNTRY = 12000
PPL = {'PPL', 'PPLA', 'PPLA2', 'PPLA3', 'PPLA4', 'PPLC'}


def main(all_countries, cities500, admin1, alternate_names, out):
    admin_en = {}
    for line in open(admin1, encoding='utf-8'):
        code, name, _ascii, _gid = line.rstrip('\n').split('\t')
        admin_en[code] = name
    rows = {}
    admin_ar = {}
    tagged = load_arabic(alternate_names)

    def add(f):
        gid = f[0]
        lat, lon, cc, a1, tz = float(f[4]), float(f[5]), f[8], f[10], f[17]
        if not tz or not cc:
            return
        pop = int(f[14] or 0)
        dem = f[16] or '0'
        elev = int(dem) if dem.lstrip('-').isdigit() and int(dem) > -500 else 0
        kind = 1 if f[7] == 'PPLX' else 0
        rows[gid] = (cc, a1, kind, -pop, clean(f[1]), arabic_names(gid, f[3], tagged), round(lat * 1e4), round(lon * 1e4), pop, tz, elev)

    for line in open(cities500, encoding='utf-8'):
        add(line.rstrip('\n').split('\t'))
    per_country = {}
    for line in open(all_countries, encoding='utf-8'):
        f = line.split('\t', 9)
        if f[6] == 'P' and f[7] in PPL:
            per_country[f[8]] = per_country.get(f[8], 0) + 1
    small = {cc for cc, n in per_country.items() if n <= SMALL_COUNTRY}
    for line in open(all_countries, encoding='utf-8'):
        f = line.rstrip('\n').split('\t')
        if f[6] == 'A' and f[7] == 'ADM1':
            ar = (tagged.get(f[0]) or [arabic_name(f[3])])[0]
            if ar: admin_ar[f'{f[8]}.{f[10]}'] = ar
        elif f[6] == 'P' and f[0] not in rows and (f[7] == 'PPLX' or (f[7] in PPL and f[8] in small)):
            add(f)

    data = sorted(rows.values())
    zones = sorted({r[9] for r in data})
    admins = sorted({f'{r[0]}.{r[1]}' for r in data})
    zi = {z: i for i, z in enumerate(zones)}
    ai = {a: i for i, a in enumerate(admins)}
    o = bytearray(b'NWP1')
    strings(o, zones)
    strings(o, admins)
    strings(o, [admin_en.get(a, '') for a in admins])
    strings(o, [admin_ar.get(a, '') for a in admins])
    varint(o, len(data))
    strings(o, [r[4] for r in data])
    strings(o, [r[5] for r in data])
    ints(o, [r[6] for r in data], delta=True)
    ints(o, [r[7] for r in data], delta=True)
    ints(o, [ai[f'{r[0]}.{r[1]}'] for r in data], delta=True)
    ints(o, [r[8] for r in data])
    ints(o, [zi[r[9]] for r in data])
    ints(o, [r[10] for r in data], signed=True)
    ints(o, [r[2] for r in data])
    with gzip.open(out, 'wb', compresslevel=9) as g:
        g.write(bytes(o))
    towns = sum(1 for r in data if r[2] == 0)
    print(f'{len(data)} places ({towns} towns and cities, {len(data) - towns} neighbourhoods), {len(zones)} zones, {len(admins)} regions -> {out}')


if __name__ == '__main__':
    main(*sys.argv[1:6])
