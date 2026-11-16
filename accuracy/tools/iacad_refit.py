"""Fresh fit of IACAD's conventions with held-out validation (16 Nov 2026).

Model families (all single rule sets, no per-date terms):
  C1-family : one latitude, one longitude, Fajr/Isha angles, ONE horizon dip for Shuruq and Maghrib, ONE rounding
              rule for every prayer, whole precaution minutes per prayer      (the family C1 came from)
  free      : the same, but separate dips for Shuruq and Maghrib and a rounding rule (up / nearest) per prayer
The engine is the app's (evidence.raw: NREL SPA, Dhuhr = SPA transit). Longitude moves every event by -240 s per
degree, which is applied as a shift (exact to well under 0.01 s over the range searched).

Fits are made on one set of days and scored, frozen, on days never seen by the fit:
  split A: fit on IACAD's own current-era days (2020-2023 archive + Nov 2026 feed), test on the 2023-2026 days of the
           Emirates 24|7 copy (secondary, independent dates)
  split B: fit on 2020-2023, test on Nov 2026 + the Emirates 24|7 days
usage: python3 accuracy/tools/iacad_refit.py
"""
import os, sys, datetime as dt
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import evidence as E  # noqa: E402

ROOT = os.path.join(HERE, '..')
PR = E.PR
LATS = np.round(np.arange(25.00, 25.3001, 0.02), 2)
LONS = np.round(np.arange(55.10, 55.4001, 0.005), 3)
ANG = np.round(np.arange(17.0, 19.0001, 0.1), 2)
DIPS = np.round(np.arange(0.0, 0.4001, 0.01), 2)
KS = np.arange(-8, 9)
LON0 = 55.25


def data():
    off = {}
    off.update(E.load(os.path.join(ROOT, 'data/iacad_official_archive_2014_2023.csv'), dt.date(2020, 8, 20)))
    off.update(E.load(os.path.join(ROOT, 'data/iacad_api_2026-11.iso.csv')))
    sec = E.load(os.path.join(ROOT, 'data/iacad_via_emirates247_wayback.csv'))
    sec = {d: v for d, v in sec.items() if d not in off}
    return off, sec


def tables(dates):
    """event seconds at LON0 for every lat and parameter: T[p][lat_i] -> array (nparam, ndays). Same engine as
    evidence.raw, vectorised over the parameter grid."""
    m, spa = E.model, E.spa
    nd = len(dates)
    mid = np.array([(dt.datetime(d.year, d.month, d.day) - dt.datetime(1970, 1, 1)).total_seconds() - 4 * 3600 for d in dates])
    ut0 = mid + 4 * 3600
    T0 = np.asarray(spa.transit_sunrise_sunset(ut0, 25.0, LON0, m.DELTA_T, 1)[0]) - mid
    sd = m.semidiameter(dates)
    out = {p: [] for p in PR}

    def grid(la, guess, targets):
        k = targets.shape[0]
        return m.solve_alt(np.tile(mid, k), np.tile(T0 / 3600 + guess, k), targets.reshape(-1), la, LON0).reshape(k, nd)

    for la in LATS:
        alt_noon = m._state(mid + T0, la, LON0, 0.0)
        asr_alt = np.degrees(np.arctan(1.0 / (1.0 + np.tan(np.radians(90.0 - alt_noon)))))
        hz = -(sd[None, :] + 0.5667 + DIPS[:, None])
        out['fajr'].append(grid(la, -7, np.repeat(-ANG[:, None], nd, 1)))
        out['isha'].append(grid(la, 7, np.repeat(-ANG[:, None], nd, 1)))
        out['shuruq'].append(grid(la, -5.8, hz))
        out['maghrib'].append(grid(la, 5.8, hz))
        out['dhuhr'].append(T0[None, :])
        out['asr'].append(grid(la, 3.3, asr_alt[None, :]))
    return out


def quant(t, mode):
    return np.ceil(t / 60.0 - 1e-9) if mode == 'ceil' else np.floor(t / 60.0 + 0.5)


def counts(T, li, p, dates, P):
    """exact counts (nlon, nparam, mode, k) of prayer p at latitude index li."""
    off = np.array([P[d][p] for d in dates], float)
    t = T[p][li][None, :, :] - (LONS[:, None, None] - LON0) * 240.0       # (nlon, nparam, ndays)
    out = np.zeros((len(LONS), t.shape[1], 2, len(KS)), int)
    for m, mode in enumerate(('ceil', 'round')):
        diff = off[None, None, :] - quant(t, mode)
        for j, k in enumerate(KS):
            out[:, :, m, j] = (diff == k).sum(-1)
    return out


def fit(dates, P, family):
    T = tables(dates)
    best = None
    for li, la in enumerate(LATS):
        C = {p: counts(T, li, p, dates, P) for p in PR}
        if family == 'free':
            tot = np.zeros(len(LONS), int); pick = {}
            for p in PR:
                c = C[p].reshape(len(LONS), -1)
                tot += c.max(-1); pick[p] = c.argmax(-1)
            j = int(tot.argmax())
            conv = {'lat': float(la), 'lon': float(LONS[j]), 'fit': int(tot[j])}
            for p in PR:
                pi, m, k = np.unravel_index(pick[p][j], C[p].shape[1:])
                conv[p] = (int(pi), ('ceil', 'round')[m], int(KS[k]))
        else:  # C1 family: shared rounding, shared dip
            for m in range(2):
                rs = C['shuruq'][:, :, m, :].max(-1) + C['maghrib'][:, :, m, :].max(-1)     # (nlon, ndip)
                di = rs.argmax(-1)
                tot = rs[np.arange(len(LONS)), di] + C['fajr'][:, :, m, :].max(-1).max(-1) + C['isha'][:, :, m, :].max(-1).max(-1) \
                    + C['dhuhr'][:, 0, m, :].max(-1) + C['asr'][:, 0, m, :].max(-1)
                j = int(tot.argmax())
                conv = {'lat': float(la), 'lon': float(LONS[j]), 'fit': int(tot[j])}
                for p in PR:
                    if p in ('shuruq', 'maghrib'):
                        pi = int(di[j])
                    elif p in ('fajr', 'isha'):
                        pi = int(C[p][j, :, m, :].max(-1).argmax())
                    else:
                        pi = 0
                    conv[p] = (pi, ('ceil', 'round')[m], int(KS[C[p][j, pi, m, :].argmax()]))
                if best is None or conv['fit'] > best['fit']:
                    best = conv
            continue
        if best is None or conv['fit'] > best['fit']:
            best = conv
    return best


def describe(c):
    def par(p):
        i = c[p][0]
        if p in ('fajr', 'isha'):
            return '%s %.1f°' % (E.NAMES[p], ANG[i])
        if p in ('shuruq', 'maghrib'):
            return '%s dip %.2f°' % (E.NAMES[p], DIPS[i])
        return E.NAMES[p]
    NAMES = E.NAMES
    return 'lat %.2f lon %.3f; ' % (c['lat'], c['lon']) + '; '.join('%s %s %+d' % (par(p), c[p][1], c[p][2]) for p in PR)


def score(c, dates, P):
    pts = {}
    tot = 0
    for p in PR:
        i, mode, k = c[p]
        prof = dict(fajr=18.0, isha=18.0, dip=0.0)
        if p == 'fajr': prof['fajr'] = ANG[i]
        if p == 'isha': prof['isha'] = ANG[i]
        if p in ('shuruq', 'maghrib'): prof['dip'] = DIPS[i]
        t = E.raw(dates, c['lat'], c['lon'], 4, prof)[p]
        got = quant(t, mode) + k
        n = int(sum(1 for d, g in zip(dates, got) if P[d][p] == g))
        pts[p] = n; tot += n
    return tot, pts


C1 = {'lat': 25.12, 'lon': 55.189, 'fajr': (int(np.argmin(abs(ANG - 18.0))), 'ceil', -2), 'shuruq': (10, 'ceil', -4),
      'dhuhr': (0, 'ceil', 2), 'asr': (0, 'ceil', 0), 'maghrib': (10, 'ceil', 2), 'isha': (int(np.argmin(abs(ANG - 18.0))), 'ceil', 0)}


def main():
    off, sec = data()
    old = {d: v for d, v in off.items() if d.year < 2024}
    new = {d: v for d, v in off.items() if d.year >= 2024}
    splits = [('A', off, sec), ('B', old, {**new, **sec})]
    print('official current-era days: %d (2020-2023: %d, 2026: %d); Emirates 24|7 days not in them: %d (%s .. %s)' % (
        len(off), len(old), len(new), len(sec), min(sec), max(sec)))
    for name, tr, te in splits:
        trd, ted = sorted(tr), sorted(te)
        print('\nsplit %s: fit on %d days, test on %d days' % (name, len(trd), len(ted)))
        a, ap = score(C1, trd, tr); b, bp = score(C1, ted, te)
        print('  C1 (current profile)  fit-set %d/%d  test %d/%d (%.1f%%)  test per prayer %s' % (a, 6 * len(trd), b, 6 * len(ted), 100.0 * b / (6 * len(ted)), bp))
        for fam in ('c1family', 'free'):
            c = fit(trd, tr, fam)
            b, bp = score(c, ted, te)
            print('  %-9s fitted: %s\n            fit-set %d/%d  test %d/%d (%.1f%%)  test per prayer %s' % (
                fam, describe(c), c['fit'], 6 * len(trd), b, 6 * len(ted), 100.0 * b / (6 * len(ted)), bp))


if __name__ == '__main__':
    main()
