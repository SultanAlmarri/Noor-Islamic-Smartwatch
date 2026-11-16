"""Builds accuracy/results/EVIDENCE.md: for every authority whose own published timetable we hold, how often the
app's calculation gives the official minute, and whether the app is exact by carrying the official table instead.

The calculation reproduces the app's engine (core/prayer PrayerEngine: NREL SPA, topocentric, horizon = Sun's
radius + 34' refraction + the profile's extra dip, Asr from the noon altitude, rounding, then whole precaution
minutes). The Kotlin engine agrees with this SPA reference to 0.007 s (PrayerEngineTest); the same counts are locked
by core/prayer's OfficialAgreementTest, which runs the app's own engine and Profiles on the same files.

"Reachable by calculation?" is decided by the residual-spread test: an official column can be reproduced exactly by
ANY rule of the form "this smooth model time + one constant shift, then rounded" (every rounding rule and any
precaution minutes) only if all residuals 60*official - model_seconds fit inside one 60-second window. The best count
any such constant could reach is reported as the ceiling. A spread over 60 s on Dhuhr (meridian transit, which
depends on nothing but longitude and the equation of time) means no constant rule can be exact on every day.

usage: python3 accuracy/tools/evidence.py      (needs numpy, pvlib). Writes accuracy/results/EVIDENCE.md (the tables,
with the hand-written findings of accuracy/tools/evidence_notes.md), evidence_misses.csv, and the cleaned Emirates 24|7
copy that core/prayer's tests read.
"""
import os, sys, csv, datetime as dt
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import model  # noqa: E402
import pvlib.spa as spa  # noqa: E402

ROOT = os.path.join(HERE, '..')
PR = ['fajr', 'shuruq', 'dhuhr', 'asr', 'maghrib', 'isha']
NAMES = {'fajr': 'Fajr', 'shuruq': 'Shuruq', 'dhuhr': 'Dhuhr', 'asr': 'Asr', 'maghrib': 'Maghrib', 'isha': 'Isha'}


def load(path, since=None, until=None):
    rows = {}
    reader = csv.DictReader(l for l in open(path) if not l.startswith('#'))
    captures = 'wayback' in (reader.fieldnames or [])
    for r in reader:
        d = dt.date.fromisoformat(r['date'])
        if (since and d < since) or (until and d > until):
            continue
        if captures and not 4 <= (int(r['wayback'][8:10]) + 4) % 24 < 20:
            # Emirates 24|7 captures made between 20:00 and 04:00 Dubai time can show a neighbouring day's times under
            # the date (two such pairs disagree, 2024-05-18 and 2024-08-07): only daytime captures are used
            continue
        rows[d] = {p: int(r[p][:2]) * 60 + int(r[p][3:5]) for p in PR}
    if captures:
        # the Emirates 24|7 page froze for months (late Aug 2025 to Feb 2026 it showed one day's times every day):
        # a set of six times that recurs on dates two or more days apart is a stale page, and all its dates are dropped
        seen = {}
        for d, v in rows.items():
            seen.setdefault(tuple(v.values()), []).append(d)
        stale = {d for ds in seen.values() if max(ds) - min(ds) >= dt.timedelta(days=2) for d in ds}
        rows = {d: v for d, v in rows.items() if d not in stale}
    return rows


def raw(dates, lat, lon, tz, prof, ramadan=()):
    """continuous event seconds after local midnight, the app engine's way."""
    mid = np.array([(dt.datetime(d.year, d.month, d.day) - dt.datetime(1970, 1, 1)).total_seconds() - tz * 3600 for d in dates])
    sd = model.semidiameter(dates)
    hz = -(sd + 0.5667 + prof.get('dip', 0.0))
    # SPA's own transit (hour angle zero), as the app's engine; the altitude-maximum solver fails near the zenith
    ut0 = np.array([(dt.datetime(d.year, d.month, d.day) - dt.datetime(1970, 1, 1)).total_seconds() for d in dates])
    T = np.asarray(spa.transit_sunrise_sunset(ut0, lat, lon, model.DELTA_T, 1)[0]) - mid
    alt_noon = model._state(mid + T, lat, lon, 0.0)
    asr_alt = np.degrees(np.arctan(1.0 / (prof.get('asr', 1.0) + np.tan(np.radians(90.0 - alt_noon)))))
    out = {'fajr': model.solve_alt(mid, T / 3600 - 7, -prof['fajr'], lat, lon),
           'shuruq': model.solve_alt(mid, T / 3600 - 5.8, hz, lat, lon), 'dhuhr': T,
           'asr': model.solve_alt(mid, T / 3600 + 3.3, asr_alt, lat, lon)}
    sunset = model.solve_alt(mid, T / 3600 + 5.8, hz, lat, lon)
    out['maghrib'] = model.solve_alt(mid, T / 3600 + 5.9, -prof['maghrib_angle'], lat, lon) if prof.get('maghrib_angle') else sunset
    if prof.get('isha_min'):
        mins = np.array([prof.get('isha_min_ramadan', prof['isha_min']) if d in ramadan else prof['isha_min'] for d in dates])
        out['isha'] = sunset + mins * 60.0
    else:
        out['isha'] = model.solve_alt(mid, T / 3600 + 7, -prof['isha'], lat, lon)
    return out


def minutes(sec, prof, p):
    mode = prof.get('rounding_by', {}).get(p, prof.get('rounding'))
    m = np.ceil(sec / 60.0 - 1e-9) if mode == 'up' else np.floor(sec / 60.0 + 0.5)
    return m.astype(int) + prof.get('offsets', {}).get(p, 0)


def ceiling(r):
    """most residuals (s) inside one half-open 60 s window, the spread, and the shift range that reaches the ceiling:
    official = ceil((t + c) / 60) on those days for every c (seconds) in (lo, hi]."""
    s = np.sort(r)
    cnt = [int(np.sum((s >= x) & (s < x + 60 - 1e-6))) for x in s]
    i = int(np.argmax(cnt))
    sub = s[(s >= s[i]) & (s < s[i] + 60 - 1e-6)]
    return cnt[i], float(s[-1] - s[0]), (float(sub.max() - 60), float(sub.min()))


# The app's profiles (core/prayer Profiles.kt, as of this commit). OfficialAgreementTest uses Profiles.kt itself.
DUBAI = dict(fajr=18.0, isha=18.0, dip=0.10, rounding='up', offsets=dict(fajr=-2, shuruq=-4, dhuhr=2, asr=0, maghrib=2, isha=0))
SINGAPORE = dict(fajr=20.0, isha=18.0, rounding='up', offsets=dict(dhuhr=1))
UQU = dict(fajr=18.5, isha_min=90, isha_min_ramadan=120)
JAKIM = dict(fajr=18.0, isha=18.0, offsets=dict(dhuhr=1, maghrib=2))
# UAE_AWQAF since 16 Nov 2026 (awqaf_fit.py); the provisional profile before it is AWQAF_OLD
AWQAF = dict(fajr=18.1, isha=18.1, dip=0.31, rounding_by=dict(shuruq='up', isha='up'),
             offsets=dict(fajr=-1, shuruq=-3, dhuhr=2, asr=0, maghrib=1, isha=-1))
AWQAF_OLD = dict(fajr=18.0, isha=18.0, dip=0.19, offsets=dict(fajr=-1, shuruq=-3, dhuhr=3, asr=3, maghrib=2, isha=1))
ABU_DHABI = (24.44, 54.28)   # the profile's reference point (Abu Dhabi region)
KEMENAG = dict(fajr=20.0, isha=18.0, offsets=dict(dhuhr=1))
DIYANET = dict(fajr=18.0, isha=17.0, offsets=dict(shuruq=-7, dhuhr=5, asr=4, maghrib=7))

CASES = [
    # key, authority, source, file, since, point (lat, lon), tz, profile, how the app gets exact times, ramadan range
    ('iacad', 'IACAD (Dulook DXB), Dubai', 'IACAD website feed (Nov 2026) + IACAD SmartPortal pages in the Internet Archive (2020-2023)',
     ['iacad_official_archive_2014_2023.csv', 'iacad_api_2026-11.iso.csv'], dt.date(2020, 8, 20), (25.12, 55.189), 4, DUBAI,
     'Carried: the app shows IACAD\'s own times on the 97 official days it bundles (2021-2023 and Nov 2026). No public feed the app may use: IACAD\'s feed refuses non-browser clients (Cloudflare, HTTP 403, checked 16 Nov 2026).', None),
    ('dulook', 'IACAD (Dulook DXB app), Dubai South', 'The official Dulook DXB app, read from the owner\'s screen recording (location "Dubai South"); dates outside the Nov 2026 feed',
     ['dulook_dxb_recording_2026.csv'], None, (25.12, 55.189), 4, DUBAI, 'Read by eye from the official app; validation only.', None),
    ('iacad247', 'IACAD (Dulook DXB), Dubai: 2023-2026 days', 'Emirates247 prayer page (republishes IACAD; identical to the IACAD feed on all 180 times of Nov 2026), Internet Archive captures',
     ['iacad_via_emirates247_wayback.csv'], dt.date(2020, 8, 20), (25.12, 55.189), 4, DUBAI, 'Secondary copy: used for validation only.', None),
    ('awqaf', 'Awqaf (UAE), Abu Dhabi', 'awqaf.gov.ae/prayer-times as shown on a visit (region "Abu Dhabi", official times), 16-17 Nov 2026',
     ['awqaf_website_abudhabi_2026-11.csv'], None, ABU_DHABI, 4, AWQAF,
     'Calculated (profile fitted 16 Nov 2026 to the UAE calendar below). Awqaf\'s official API exists on the UAE Government API portal (api.government.ae, "Prayer Timings", needs an API key); not used yet.', None),
    ('szgmc', 'UAE Hijri Calendar (Sheikh Zayed Grand Mosque Centre), Abu Dhabi City', 'HijriBook1445_en.pdf: times "calculated by the International Astronomy Center - Abu Dhabi" under the Centre\'s Sharia and Astronomical Committee (not Awqaf\'s own table)',
     ['szgmc_abudhabi_1445.csv'], None, ABU_DHABI, 4, AWQAF, 'Reference only (compared with the Awqaf profile).', None),
    ('muis', 'MUIS, Singapore', 'data.gov.sg dataset "Muslim Prayer Timetable" (Singapore Open Data Licence)',
     ['muis_singapore_2024_2026.csv'], None, (1.25, 103.80), 8, SINGAPORE,
     'Carried and fetched: bundled 2024-2026 and refreshed from data.gov.sg on the phone (weekly) - exact by construction on those dates.', None),
    ('uqu', 'Umm al-Qura, Makkah', 'KACST Umm al-Qura service, the yearly table of www.ummulqura.org.sa (Hijri year 1448)',
     ['ummalqura_makkah_1448.csv'], None, (21.426666, 39.831666), 3, UQU, 'Calculated only (the official service is not used by the app).',
     (dt.date(2027, 2, 8), dt.date(2027, 3, 8))),
    ('jakim', 'JAKIM, Malaysia (zone WLY01: Kuala Lumpur, Putrajaya)', 'JAKIM e-Solat yearly zone table 2026',
     ['jakim_wly01_2026.csv'], None, (3.139, 101.6869), 8, JAKIM, 'Calculated only, at the user\'s position (zone tables not carried).', None),
    ('diyanet', 'Diyanet, Türkiye (Istanbul)', 'namazvakitleri.diyanet.gov.tr, Istanbul yearly table 2026',
     ['diyanet_istanbul_2026.csv'], None, (41.0082, 28.9784), 3, DIYANET, 'Calculated only (Diyanet\'s API needs registration).', None),
    ('kemenag', 'Kemenag, Indonesia (Kota Jakarta)', 'bimasislam.kemenag.go.id/jadwalshalat (SIHAT), November 2026, at its stated point 6°10\'12.32" S 106°49\'51.78" E',
     ['kemenag_jakarta_2026-11.csv'], None, (-6.170089, 106.831050), 7, KEMENAG, 'Calculated only.', None),
]


def measure(case):
    key, name, source, files, since, (lat, lon), tz, prof, route, ram = case
    P = {}
    for f in files:
        path = os.path.join(ROOT, 'data', f)
        if os.path.exists(path):
            P.update(load(path, since))
    if key == 'dulook':     # only the dates the IACAD feed does not hold
        feed = load(os.path.join(ROOT, 'data', 'iacad_api_2026-11.iso.csv'))
        P = {d: v for d, v in P.items() if d not in feed}
    if not P:
        return None
    ds = sorted(P)
    ramadan = {d for d in ds if ram and ram[0] <= d <= ram[1]}
    ev = raw(ds, lat, lon, tz, prof, ramadan)
    res = {'key': key, 'name': name, 'source': source, 'route': route, 'days': len(ds), 'from': ds[0], 'to': ds[-1],
           'per': {}, 'exact': 0, 'one': 0, 'worse': 0, 'n': 0, 'ceil': {}, 'spread': {}, 'misses': []}
    for p in PR:
        off = np.array([P[d][p] for d in ds])
        got = minutes(ev[p], prof, p)
        diff = off - got
        res['per'][p] = int((diff == 0).sum())
        res['exact'] += int((diff == 0).sum()); res['one'] += int((abs(diff) == 1).sum()); res['worse'] += int((abs(diff) > 1).sum())
        res['n'] += len(ds)
        # residuals against the raw model time (before the profile's rounding and minutes)
        res['ceil'][p], res['spread'][p], res.setdefault('shift', {})[p] = ceiling(off * 60.0 - ev[p])
        res['misses'] += [(d, p, int(x)) for d, x in zip(ds, diff) if x != 0]
    res['maxdiff'] = max([abs(m[2]) for m in res['misses']] or [0])
    return res


def pct(a, b):
    return '%.1f%%' % (100.0 * a / b) if b else '-'


def main():
    results = [r for r in (measure(c) for c in CASES) if r]
    L = []
    L.append('# Evidence: how exact are Noor\'s prayer times against each authority\'s own timetable?')
    L.append('')
    L.append('Generated by `accuracy/tools/evidence.py` (%s). Every number below is recomputed from the files in `accuracy/data`' % dt.date.today().isoformat())
    L.append('and locked by `core/prayer/src/test/kotlin/com/noor/prayer/OfficialAgreementTest.kt` (the app\'s own engine and profiles).')
    L.append('"Exact" means the same minute as the authority published. Nothing here is a device test: these are calculations')
    L.append('compared with published tables.')
    L.append('')
    L.append('## Summary')
    L.append('')
    L.append('| Authority | Official source | Dates compared | Times | Exact | ±1 min | Worse | How the app can be exact |')
    L.append('|---|---|---|---|---|---|---|---|')
    for r in results:
        L.append('| %s | %s | %s – %s (%d days) | %d | %d (%s) | %d | %d | %s |' % (
            r['name'], r['source'], r['from'], r['to'], r['days'], r['n'], r['exact'], pct(r['exact'], r['n']), r['one'], r['worse'], r['route']))
    L.append('')
    L.append('## Per prayer, and the ceiling of any calculation')
    L.append('')
    L.append('"Ceiling" is the most days that ANY rule "the app\'s model time + one constant shift per prayer, then rounded" could')
    L.append('get right (it covers every rounding rule and every precaution minute, and is an upper bound: such a shift is')
    L.append('fitted to the data). "Spread" is the width of the official minus model residuals; over 60 s, no constant rule can')
    L.append('be exact on every day, whatever its constants.')
    L.append('')
    L.append('"Rule at the ceiling": official = the model time plus c seconds, rounded up, for any c in the range shown')
    L.append('(a range that contains a whole minute k means "round up, then add k minutes" reaches the ceiling).')
    L.append('')
    L.append('| Authority | Prayer | App exact | Ceiling | Residual spread | Rule at the ceiling: c in (s) |')
    L.append('|---|---|---|---|---|---|')
    for r in results:
        for p in PR:
            lo, hi = r['shift'][p]
            L.append('| %s | %s | %d/%d | %d/%d | %.0f s | (%.0f, %.0f] |' % (r['name'], NAMES[p], r['per'][p], r['days'], r['ceil'][p], r['days'], r['spread'][p], lo, hi))
    L.append('')
    notes = open(os.path.join(HERE, 'evidence_notes.md')).read()
    with open(os.path.join(ROOT, 'results', 'EVIDENCE.md'), 'w') as f:
        f.write('\n'.join(L[:6]) + '\n' + notes.split('<!-- tables -->')[0] + '\n'.join(L[6:]) + '\n' + notes.split('<!-- tables -->')[1])
    # the cleaned Emirates 24|7 days, as the Kotlin test reads them
    e247 = load(os.path.join(ROOT, 'data', 'iacad_via_emirates247_wayback.csv'))
    with open(os.path.join(ROOT, '..', 'core', 'prayer', 'src', 'test', 'resources', 'iacad_via_emirates247_wayback.csv'), 'w') as f:
        f.write('# Emirates 24|7 copy of IACAD (Dubai), cleaned copy of accuracy/data/iacad_via_emirates247_wayback.csv\n')
        f.write('# (daytime captures only; stale repeated pages removed): written by accuracy/tools/evidence.py\n')
        f.write('date,fajr,shuruq,dhuhr,asr,maghrib,isha\n')
        for d in sorted(e247):
            f.write(d.isoformat() + ',' + ','.join('%02d:%02d' % divmod(e247[d][p], 60) for p in PR) + '\n')
    with open(os.path.join(ROOT, 'results', 'evidence_misses.csv'), 'w') as f:
        f.write('authority,date,prayer,official_minus_app\n')
        for r in results:
            for d, p, x in r['misses']:
                f.write('%s,%s,%s,%d\n' % (r['key'], d, p, x))
    for r in results:
        print('%-10s %4d days %5d times exact %5d (%s) +-1 %4d worse %4d max %d  per %s  ceiling %s' % (
            r['key'], r['days'], r['n'], r['exact'], pct(r['exact'], r['n']), r['one'], r['worse'], r['maxdiff'],
            r['per'], {p: '%d/%.0fs' % (r['ceil'][p], r['spread'][p]) for p in PR}))


if __name__ == '__main__':
    main()
