"""Train/test validation of the joint convention search (no per-date terms).

Fit on a training subset of official days, then apply the *frozen* convention to held-out days
and count exact-minute matches.  Two model families:
  uniform : one rounding rule for all prayers
  mixed   : rounding rule (ceil / round-nearest) chosen per prayer
Location (lat, lon), Fajr/Isha angles, shared horizon dip and integer minutes are fitted.
usage: python3 holdout.py TRAIN_FROM TRAIN_TO TEST_FROM TEST_TO [family]
"""
import sys, os, pickle, numpy as np, datetime as dt, json
sys.path.insert(0, os.path.dirname(__file__))
import analyse as A
from joint_search import LATS, LONS, KS

B = pickle.load(open('data/basis_all.pkl', 'rb'))
W = A.load('data/iacad_official_archive_2014_2023.csv'); api = A.load('data/iacad_api_2026-11.iso.csv')
P = {**W, **api}


def tables(Q):
    ds = sorted(Q); S = A.subset_basis(B, ds); n = len(ds)
    pub = {p: np.array([Q[d][p] for d in ds], float) for p in A.PR}
    g = np.asarray(S['HZ'], float)[::-1]
    hz = -(S['sd'][None, :] + A.REFR + A.DIPS[:, None])
    ii = np.clip(np.searchsorted(g, hz) - 1, 0, len(g) - 2); ww = (hz - g[ii]) / (g[ii + 1] - g[ii])
    return ds, S, pub, ii, ww, np.arange(n)


def event_arrays(S, la, ii, ww, cols, lons=LONS):
    T = {'dhuhr': A._taylor(S, 'dhuhr', la, lons)[:, None, :], 'asr': A._taylor(S, 'asr1', la, lons)[:, None, :],
         'fajr': A._taylor(S, 'fajr', la, lons), 'isha': A._taylor(S, 'isha', la, lons)}
    for key, p in (('rise', 'shuruq'), ('set', 'maghrib')):
        arr = A._taylor(S, key, la, lons)[:, ::-1, :]
        T[p] = arr[:, ii, cols] * (1 - ww) + arr[:, ii + 1, cols] * ww
    return T


def quant(t, mode):
    return np.ceil(t / 60.0 - 1e-9) if mode == 'ceil' else np.floor(t / 60.0 + 0.5)


def fit(Q, family):
    ds, S, pub, ii, ww, cols = tables(Q)
    best = None
    for la in LATS:
        T = event_arrays(S, la, ii, ww, cols)
        cnt = {}; off = {}
        for p in A.PR:
            for mode in ('ceil', 'round'):
                diff = pub[p] - quant(T[p], mode)
                c = np.stack([(diff == k).sum(-1) for k in KS], -1)      # (nlon, nparam, nk)
                cnt[p, mode] = c.max(-1); off[p, mode] = KS[c.argmax(-1)]
        for modes in (['ceil'] * 6, ['round'] * 6) if family == 'uniform' else [None]:
            sel = {}
            for idx, p in enumerate(A.PR):
                if modes is None:
                    m = np.where(cnt[p, 'ceil'] >= cnt[p, 'round'], 'ceil', 'round')
                    c = np.maximum(cnt[p, 'ceil'], cnt[p, 'round'])
                    sel[p] = (c, m)
                else:
                    sel[p] = (cnt[p, modes[idx]], np.full(cnt[p, modes[idx]].shape, modes[idx]))
            rs = sel['shuruq'][0] + sel['maghrib'][0]
            di = rs.argmax(-1); fi = sel['fajr'][0].argmax(-1); si = sel['isha'][0].argmax(-1)
            L = np.arange(len(LONS))
            tot = sel['dhuhr'][0][:, 0] + sel['asr'][0][:, 0] + rs[L, di] + sel['fajr'][0][L, fi] + sel['isha'][0][L, si]
            j = int(tot.argmax())
            pick = {'dhuhr': 0, 'asr': 0, 'fajr': fi[j], 'isha': si[j], 'shuruq': di[j], 'maghrib': di[j]}
            conv = {'lat': float(la), 'lon': float(LONS[j]), 'fajr_angle': float(S['ANG'][fi[j]]), 'isha_angle': float(S['ANG'][si[j]]),
                    'dip': float(A.DIPS[di[j]]),
                    'mode': {p: str(sel[p][1][j, pick[p]]) for p in A.PR},
                    'offset': {p: int(off[p, str(sel[p][1][j, pick[p]])][j, pick[p]]) for p in A.PR},
                    'train_matches': int(tot[j]), 'train_of': 6 * len(ds)}
            if best is None or tot[j] > best['train_matches']:
                best = conv
    return best


def apply(conv, Q):
    ds, S, pub, ii, ww, cols = tables(Q)
    la = conv['lat']; lon = np.array([conv['lon']])
    T = event_arrays(S, la, ii, ww, cols, lons=lon)
    pidx = {'fajr': int(np.argmin(np.abs(S['ANG'] - conv['fajr_angle']))), 'isha': int(np.argmin(np.abs(S['ANG'] - conv['isha_angle']))),
            'shuruq': int(np.argmin(np.abs(A.DIPS - conv['dip']))), 'maghrib': int(np.argmin(np.abs(A.DIPS - conv['dip']))), 'dhuhr': 0, 'asr': 0}
    res = {}; misses = []
    for p in A.PR:
        pred = quant(T[p][0, pidx[p]], conv['mode'][p]) + conv['offset'][p]
        diff = (pub[p] - pred).astype(int)
        res[p] = int((diff == 0).sum())
        misses += [(str(d), p, int(x)) for d, x in zip(ds, diff) if x != 0]
    return res, misses, len(ds)


if __name__ == '__main__':
    a, b, c, d = (dt.date.fromisoformat(x) for x in sys.argv[1:5])
    family = sys.argv[5] if len(sys.argv) > 5 else 'uniform'
    train = {k: v for k, v in P.items() if a <= k <= b}
    test = {k: v for k, v in P.items() if c <= k <= d}
    conv = fit(train, family)
    res, misses, n = apply(conv, test)
    tot = sum(res.values())
    print(json.dumps(conv))
    print('TEST %s..%s: %d/%d = %.1f%%' % (c, d, tot, 6 * n, 100 * tot / (6 * n)), res)
    print('test misses (published - predicted):', misses[:40], '...' if len(misses) > 40 else '')
