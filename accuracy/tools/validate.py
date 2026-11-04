"""Automated validation harness (research only - nothing here touches the watch-face project).

  python3 tools/validate.py            -> runs dataset integrity tests and scores every candidate
                                          engine against every official dataset; writes
                                          results/validation_report.md and results/discrepancies_*.csv

Candidate engines are plain functions date-list -> {prayer: minute-of-day array}.
Every discrepancy is listed; nothing is averaged away.
"""
import sys, os, csv, json, datetime as dt, numpy as np, pickle
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(__file__))
import analyse as A
from holdout import tables, event_arrays, quant

ROOT = os.path.join(os.path.dirname(__file__), '..')
os.chdir(ROOT)
PR = A.PR
W = A.load('data/iacad_official_archive_2014_2023.csv')
API = A.load('data/iacad_api_2026-11.iso.csv')
SC = A.load('data/salatcalendar_official_dubai_2019_2026.csv')
NESTLE = A.load('data/iacad_via_nestle_ramadan_2020.csv')
OFFICIAL = {**W, **API}


def era(d):
    if d < dt.date(2017, 9, 21): return 'E1 (to 1438 AH)'
    if d < dt.date(2020, 8, 20): return 'E2 (1439-1441 AH)'
    return 'E3 (1442 AH onward)'


# ---------------------------------------------------------------- integrity tests
def integrity():
    checks = []
    def ok(name, cond, detail=''):
        checks.append((name, bool(cond), detail))
    # 1 every official row has six times in increasing order
    bad = [d for d, v in OFFICIAL.items() if not (v['fajr'] < v['shuruq'] < v['dhuhr'] < v['asr'] < v['maghrib'] < v['isha'])]
    ok('official rows ordered Fajr<Shuruq<Dhuhr<Asr<Maghrib<Isha', not bad, str(bad[:5]))
    # 2 day-to-day steps small inside consecutive runs (catches transcription slips)
    ds = sorted(OFFICIAL); jumps = []
    for a, b in zip(ds, ds[1:]):
        if (b - a).days == 1:
            for p in PR:
                if abs(OFFICIAL[b][p] - OFFICIAL[a][p]) > 3: jumps.append((str(b), p, OFFICIAL[b][p] - OFFICIAL[a][p]))
    ok('no day-to-day jump > 3 min in consecutive official days', not jumps, str(jumps[:6]))
    # 3 emirates247 live copy equals the IACAD API (Nov 2026)
    import csv as _csv
    e247 = {}
    src = '/home/claude/project/reference_data/iacad_dubai_2026_11.csv'
    if os.path.exists(src):
        for r in _csv.DictReader(l for l in open(src) if not l.startswith('#')):
            e247[dt.date.fromisoformat(r['date'])] = [int(r[c][:2]) * 60 + int(r[c][3:5]) for c in ('fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha')]
        diff = [(d, p) for d in API for i, p in enumerate(PR) if e247[d][i] != API[d][p]]
        ok('emirates247 copy identical to IACAD API (Nov 2026, 180 values)', not diff, str(diff[:5]))
    # 4 Nestle 2020 secondary copy vs official archive capture of 24 Apr 2020
    d = dt.date(2020, 4, 24)
    ok('Nestle 2020 PDF equals official archive on 2020-04-24', all(NESTLE[d][p] == W[d][p] for p in PR))
    # 5 Era-1 fixed intervals (documented convention); the one known exception is locked in
    e1 = [d for d in W if d < dt.date(2017, 9, 21)]
    exc = sorted(str(d) for d in e1 if not (W[d]['shuruq'] - W[d]['fajr'] == 80 and W[d]['isha'] - W[d]['maghrib'] == 90))
    ok('Era 1: Shuruq-Fajr = 80 and Isha-Maghrib = 90 on %d of %d days (exception: %s)' % (len(e1) - len(exc), len(e1), ', '.join(exc)), exc == ['2016-04-12'])
    # 6 Era-1 near-perpetual table: same calendar date in consecutive years
    pairs = [(d, d.replace(year=d.year + 1)) for d in e1 if not (d.month == 2 and d.day == 29) and d.replace(year=d.year + 1) in W and d.replace(year=d.year + 1) < dt.date(2017, 9, 21)]
    nd = [str(a) for a, b in pairs if W[a] != W[b]]
    ok('Era 1 near-perpetual: %d of %d year-apart pairs identical (differ: %s, Asr by 1 min)' % (len(pairs) - len(nd), len(pairs), ', '.join(nd)), nd == ['2015-05-05', '2016-05-05'])
    return checks


# ---------------------------------------------------------------- candidate engines
def engine_v132(ds):
    sys.path.insert(0, 'engine_v132')
    import data, engine
    from compare import env_for
    Pm = engine.Params(); EX = {ev: engine.event_seconds(Pm, ev) for ev in data.EVENTS}
    MAP = dict(zip(data.EVENTS, PR))
    out = {p: [] for p in PR}
    for d in ds:
        env = env_for(d)
        for ev in data.EVENTS: out[MAP[ev]].append(int(EX[ev].eval(env)) // 60)
    return {p: np.array(v) for p, v in out.items()}


def principled(ds, lat=25.12, lon=55.189, ang=18.0, dip=0.10, mode='ceil', offs=None):
    offs = offs or {'fajr': -2, 'shuruq': -4, 'dhuhr': 2, 'asr': 0, 'maghrib': 2, 'isha': 0}
    Q = {d: OFFICIAL.get(d, SC.get(d)) for d in ds}
    d_, S, pub, ii, ww, cols = tables(Q)
    T = event_arrays(S, lat, ii, ww, cols, lons=np.array([lon]))
    ai = int(np.argmin(np.abs(S['ANG'] - ang))); di = int(np.argmin(np.abs(A.DIPS - dip)))
    idx = {'fajr': ai, 'isha': ai, 'shuruq': di, 'maghrib': di, 'dhuhr': 0, 'asr': 0}
    return {p: (quant(T[p][0, idx[p]], mode) + offs[p]).astype(int) for p in PR}


def salatcalendar(ds):
    return {p: np.array([SC[d][p] if d in SC else -9999 for d in ds]) for p in PR}


ENGINES = {
    'current watch-face engine v1.3.2': engine_v132,
    'candidate C1 (lat 25.12, 18.0/18.0, horizon SD+34\'+0.10 deg, ceil, -2/-4/+2/0/+2/0) - NOT ADOPTED': principled,
    'salatcalendar.com official mode (IAC replica, reference only)': salatcalendar,
}


def score(fn, Q):
    ds = sorted(Q); pred = fn(ds)
    rows = []; per = {p: [0, 0] for p in PR}
    for i, d in enumerate(ds):
        for p in PR:
            if pred[p][i] == -9999: continue
            diff = int(Q[d][p] - pred[p][i]); per[p][0] += diff == 0; per[p][1] += 1
            if diff: rows.append((d, p, Q[d][p], int(pred[p][i]), diff))
    return per, rows


def hm(m): return '%02d:%02d' % divmod(int(m), 60)


def main():
    os.makedirs('results', exist_ok=True)
    md = ['# Validation report (research harness)', '', 'Generated %s by `tools/validate.py`.' % dt.date.today(), '']
    md += ['## Dataset integrity tests', '']
    for name, passed, detail in integrity():
        md.append('- [%s] %s%s' % ('PASS' if passed else 'FAIL', name, ('  (%s)' % detail) if detail and not passed else ('  (%s)' % detail if detail else '')))
    sets = {e: {d: v for d, v in OFFICIAL.items() if era(d) == e} for e in sorted(set(era(d) for d in OFFICIAL))}
    md += ['', '## Exact-minute agreement with official IACAD data', '',
           '| Engine | Dataset | Fajr | Shuruq | Dhuhr | Asr | Maghrib | Isha | All |', '|---|---|---|---|---|---|---|---|---|']
    summary = {}
    for en, fn in ENGINES.items():
        for sn, Q in sets.items():
            per, rows = score(fn, Q)
            n = sum(v[1] for v in per.values())
            if n == 0: continue
            tot = sum(v[0] for v in per.values())
            md.append('| %s | %s, %d days | %s | %d/%d (%.1f%%) |' % (en, sn, len(Q), ' | '.join('%d/%d' % tuple(per[p]) for p in PR), tot, n, 100 * tot / n))
            summary[(en, sn)] = (tot, n)
            slug = ''.join(c if c.isalnum() else '_' for c in (en[:24] + '_' + sn[:2]))
            with open('results/discrepancies_%s.csv' % slug, 'w', newline='') as f:
                w = csv.writer(f); w.writerow(['date', 'prayer', 'official', 'engine', 'official_minus_engine'])
                for d, p, o, m, x in rows: w.writerow([d, p, hm(o), hm(m), x])
    open('results/validation_report.md', 'w').write('\n'.join(md) + '\n')
    print('\n'.join(md))


if __name__ == '__main__':
    main()
