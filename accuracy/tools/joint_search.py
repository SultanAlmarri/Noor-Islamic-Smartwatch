"""Joint convention search: one location (lat, lon), one rounding rule for all prayers,
integer precautionary minutes per prayer, Fajr/Isha depression angles, one horizon dip shared by
sunrise and maghrib.  Objective: number of exact-minute matches.  No per-date terms.

usage: python3 joint_search.py ERA_START ERA_END OUTFILE
"""
import sys, os, pickle, numpy as np, datetime as dt, json
sys.path.insert(0, os.path.dirname(__file__))
import analyse as A

LATS = np.round(np.arange(24.95, 25.3501, 0.01), 2)
LONS = np.round(np.arange(55.10, 55.5001, 0.0025), 4)
KS = np.arange(-6, 7)


def counts(t_sec, pub, mode):
    m = np.ceil(t_sec / 60.0 - 1e-9) if mode == 'ceil' else np.floor(t_sec / 60.0 + 0.5)
    diff = pub - m                                            # (..., ndays)
    c = np.stack([(diff == k).sum(-1) for k in KS], -1)        # (..., nk)
    return c.max(-1), KS[c.argmax(-1)]


def main(a, b, out):
    B = pickle.load(open(os.path.join(os.path.dirname(__file__), '..', 'data', 'basis_all.pkl'), 'rb'))
    W = A.load('data/iacad_official_archive_2014_2023.csv'); api = A.load('data/iacad_api_2026-11.iso.csv')
    P = {**W, **api}
    Q = {d: v for d, v in P.items() if a <= d <= b}
    ds = sorted(Q); S = A.subset_basis(B, ds); n = len(ds)
    pub = {p: np.array([Q[d][p] for d in ds], float) for p in A.PR}
    g = np.asarray(S['HZ'], float)[::-1]
    hz = -(S['sd'][None, :] + A.REFR + A.DIPS[:, None])
    ii = np.clip(np.searchsorted(g, hz) - 1, 0, len(g) - 2); ww = (hz - g[ii]) / (g[ii + 1] - g[ii]); cols = np.arange(n)
    results = []
    for mode in ('ceil', 'round'):
        for la in LATS:
            T = {}
            T['dhuhr'] = A._taylor(S, 'dhuhr', la, LONS)                      # (nlon, nd)
            T['asr'] = A._taylor(S, 'asr1', la, LONS)
            T['fajr'] = A._taylor(S, 'fajr', la, LONS)                        # (nlon, nang, nd)
            T['isha'] = A._taylor(S, 'isha', la, LONS)
            for key, p in (('rise', 'shuruq'), ('set', 'maghrib')):
                arr = A._taylor(S, key, la, LONS)[:, ::-1, :]
                T[p] = arr[:, ii, cols] * (1 - ww) + arr[:, ii + 1, cols] * ww     # (nlon, ndip, nd)
            c = {}; k = {}
            for p in A.PR:
                c[p], k[p] = counts(T[p], pub[p], mode)
            # shared dip for shuruq+maghrib
            rs = c['shuruq'] + c['maghrib']                                 # (nlon, ndip)
            dip_i = rs.argmax(-1)
            fa_i = c['fajr'].argmax(-1); is_i = c['isha'].argmax(-1)
            L = np.arange(len(LONS))
            total = (c['dhuhr'] + c['asr'] + rs[L, dip_i] + c['fajr'][L, fa_i] + c['isha'][L, is_i])
            for j in np.argsort(-total)[:3]:
                results.append({'mode': mode, 'lat': float(la), 'lon': float(LONS[j]), 'total': int(total[j]), 'of': 6 * n,
                                'per': {'fajr': int(c['fajr'][j, fa_i[j]]), 'shuruq': int(c['shuruq'][j, dip_i[j]]),
                                        'dhuhr': int(c['dhuhr'][j]), 'asr': int(c['asr'][j]),
                                        'maghrib': int(c['maghrib'][j, dip_i[j]]), 'isha': int(c['isha'][j, is_i[j]])},
                                'fajr_angle': float(S['ANG'][fa_i[j]]), 'isha_angle': float(S['ANG'][is_i[j]]),
                                'dip': float(A.DIPS[dip_i[j]]),
                                'offsets': {'fajr': int(k['fajr'][j, fa_i[j]]), 'shuruq': int(k['shuruq'][j, dip_i[j]]),
                                            'dhuhr': int(k['dhuhr'][j]), 'asr': int(k['asr'][j]),
                                            'maghrib': int(k['maghrib'][j, dip_i[j]]), 'isha': int(k['isha'][j, is_i[j]])}})
    results.sort(key=lambda r: -r['total'])
    json.dump({'era': [str(a), str(b)], 'days': n, 'top': results[:60]}, open(out, 'w'), indent=1)
    for r in results[:12]:
        print(r['mode'], r['lat'], r['lon'], '%d/%d' % (r['total'], r['of']), r['per'], 'F%.1f I%.1f dip%.2f' % (r['fajr_angle'], r['isha_angle'], r['dip']), r['offsets'])


if __name__ == '__main__':
    main(dt.date.fromisoformat(sys.argv[1]), dt.date.fromisoformat(sys.argv[2]), sys.argv[3])
