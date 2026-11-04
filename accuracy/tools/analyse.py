"""Residual-spread analysis of a published timetable against astronomical models.

For each prayer and each candidate physical convention (location, angle, horizon), the
residual u = published_minute - model_time/60 is computed for every day.  A timetable can be
reproduced *exactly* by "model + one constant shift + rounding up to the minute" if and only if
max(u) - min(u) < 1 minute; the shift interval is then (max(u)-1, min(u)].
Integer shifts mean "round up (ceil) then add k minutes"; half-integer shifts mean
"round to nearest then add k minutes".  No per-date adjustment is ever used.
"""
import numpy as np, csv, sys, os, pickle, datetime as dt, json
sys.path.insert(0, os.path.dirname(__file__))
import basis

PR = ['fajr', 'shuruq', 'dhuhr', 'asr', 'maghrib', 'isha']
LATS = np.round(np.arange(24.95, 25.3501, 0.01), 2)
LONS = np.round(np.arange(55.10, 55.5001, 0.01), 2)
DIPS = np.round(np.arange(0.0, 0.3001, 0.01), 3)   # extra horizon depression (deg) on top of SD+refraction
REFR = 0.5667


def load(path):
    rows = {}
    for r in csv.DictReader(l for l in open(path) if not l.startswith('#')):
        d = dt.date.fromisoformat(r['date'])
        rows[d] = {p: int(r[p][:2]) * 60 + int(r[p][3:5]) for p in PR}
    return rows


def subset_basis(B, dates):
    idx = {d: i for i, d in enumerate(B['dates'])}
    ii = np.array([idx[d] for d in dates])
    S = {'dates': dates, 'lat0': B['lat0'], 'lon0': B['lon0'], 'ANG': B['ANG'], 'HZ': B['HZ'], 'sd': B['sd'][ii]}
    for k, v in B.items():
        if isinstance(v, tuple):
            S[k] = tuple(a[..., ii] for a in v)
    return S


def best_window(u):
    """max number of residuals inside any half-open interval of length 1 (u sorted along last axis)."""
    us = np.sort(u, axis=-1)
    n = us.shape[-1]
    j = np.zeros(us.shape, int)
    # for each i, count of k>=i with us[k] < us[i]+1
    cnt = np.zeros(us.shape[:-1], int)
    for i in range(n):
        c = np.sum(us[..., i:] < us[..., i:i + 1] + 1.0 - 1e-9, axis=-1)
        cnt = np.maximum(cnt, c)
    return cnt


def times(S, prayer, lat, lon, param):
    if prayer == 'dhuhr':
        return basis.at(S, 'dhuhr', lat, lon)
    if prayer == 'asr':
        return basis.at(S, 'asr1', lat, lon)
    if prayer in ('fajr', 'isha'):
        arr = basis.at(S, prayer, lat, lon)            # (nang, ndays)
        return basis.interp_axis(arr, S['ANG'], param)
    key = 'rise' if prayer == 'shuruq' else 'set'
    arr = basis.at(S, key, lat, lon)
    hz = -(S['sd'] + REFR + param)
    return basis.interp_axis(arr, S['HZ'], hz)


def params_for(prayer):
    if prayer in ('fajr', 'isha'):
        return list(np.round(np.arange(17.0, 19.61, 0.05), 3))
    if prayer in ('shuruq', 'maghrib'):
        return list(DIPS)
    return [None]


def _taylor(S, key, lat, lons):
    f0, fy, fx, fyy, fxx, fxy = S[key]
    y = lat - S['lat0']
    x = (np.asarray(lons) - S['lon0']).reshape((-1,) + (1,) * f0.ndim)
    return f0 + fy * y + fx * x + 0.5 * fyy * y * y + 0.5 * fxx * x * x + fxy * x * y   # (nlon, ...)


def scan(S, P, prayer, lats=LATS, lons=LONS):
    """vectorised scan; returns arrays over (lat, lon, param): spread, lo (=max u - 1), hi (=min u)."""
    pub = np.array([P[d][prayer] for d in S['dates']], float)
    if prayer in ('fajr', 'isha'):
        pv = list(S['ANG'])
    elif prayer in ('shuruq', 'maghrib'):
        pv = list(DIPS)
        g = np.asarray(S['HZ'], float)[::-1]                       # ascending
        hz = -(S['sd'][None, :] + REFR + np.asarray(DIPS)[:, None])  # (ndip, ndays)
        ii = np.clip(np.searchsorted(g, hz) - 1, 0, len(g) - 2)
        ww = (hz - g[ii]) / (g[ii + 1] - g[ii])
    else:
        pv = [None]
    shape = (len(lats), len(lons), len(pv))
    spread = np.empty(shape); lo = np.empty(shape); hi = np.empty(shape)
    cols = np.arange(len(S['dates']))
    for i, la in enumerate(lats):
        if prayer == 'dhuhr':
            t = _taylor(S, 'dhuhr', la, lons)[:, None, :]
        elif prayer == 'asr':
            t = _taylor(S, 'asr1', la, lons)[:, None, :]
        elif prayer in ('fajr', 'isha'):
            t = _taylor(S, prayer, la, lons)                         # (nlon, nang, ndays)
        else:
            arr = _taylor(S, 'rise' if prayer == 'shuruq' else 'set', la, lons)[:, ::-1, :]   # (nlon, nhz asc, ndays)
            t = arr[:, ii, cols] * (1 - ww) + arr[:, ii + 1, cols] * ww                     # (nlon, ndip, ndays)
        u = pub - t / 60.0
        mx = u.max(-1); mn = u.min(-1)
        spread[i] = mx - mn; lo[i] = mx - 1.0; hi[i] = mn
    return {'params': pv, 'spread': spread, 'lo': lo, 'hi': hi}


def residuals(S, P, prayer, lat, lon, param):
    pub = np.array([P[d][prayer] for d in S['dates']], float)
    return pub - times(S, prayer, lat, lon, param) / 60.0


def shift_label(lo, hi):
    """describe the feasible constant shift interval (lo, hi] in minutes."""
    labels = []
    for k in range(-6, 8):
        if lo < k <= hi:
            labels.append('ceil%+d' % k)
        if lo < k - 0.5 <= hi:
            labels.append('round%+d' % k)
    return labels


def scan_matches(S, P, prayer, lats=LATS, lons=LONS):
    """best achievable exact-minute matches over a constant shift, for each (lat, lon, param)."""
    pub = np.array([P[d][prayer] for d in S['dates']], float)
    if prayer in ('fajr', 'isha'):
        pv = list(S['ANG'])
    elif prayer in ('shuruq', 'maghrib'):
        pv = list(DIPS)
        g = np.asarray(S['HZ'], float)[::-1]
        hz = -(S['sd'][None, :] + REFR + np.asarray(DIPS)[:, None])
        ii = np.clip(np.searchsorted(g, hz) - 1, 0, len(g) - 2)
        ww = (hz - g[ii]) / (g[ii + 1] - g[ii])
    else:
        pv = [None]
    best = np.zeros((len(lats), len(lons), len(pv)), int)
    sig = np.zeros(best.shape)
    cols = np.arange(len(S['dates']))
    for i, la in enumerate(lats):
        if prayer == 'dhuhr':
            t = _taylor(S, 'dhuhr', la, lons)[:, None, :]
        elif prayer == 'asr':
            t = _taylor(S, 'asr1', la, lons)[:, None, :]
        elif prayer in ('fajr', 'isha'):
            t = _taylor(S, prayer, la, lons)
        else:
            arr = _taylor(S, 'rise' if prayer == 'shuruq' else 'set', la, lons)[:, ::-1, :]
            t = arr[:, ii, cols] * (1 - ww) + arr[:, ii + 1, cols] * ww
        u = np.sort(pub - t / 60.0, axis=-1)
        n = u.shape[-1]
        flat = u.reshape(-1, n)
        for r in range(flat.shape[0]):
            row = flat[r]
            c = np.searchsorted(row, row + 1.0 - 1e-9, side='left') - np.arange(n)
            k = int(np.argmax(c))
            jj, kk = divmod(r, len(pv))
            best[i, jj, kk] = c[k]
            sig[i, jj, kk] = row[k]          # window [row[k], row[k]+1): shift sigma in (row[k+c-1]-1, row[k]]
    return {'params': pv, 'best': best, 'sigma_hi': sig}
