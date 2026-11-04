"""Precompute event-time bases so that convention searches are fast.

For a set of dates we compute, at a base location, continuous event times for
  - Fajr / Isha at a grid of depression angles,
  - sunrise / sunset at a grid of horizon altitudes (target topocentric geometric altitude),
  - transit (Dhuhr) and Asr (shadow factor 1 and 2),
plus first and second partial derivatives with respect to latitude and longitude.
Times anywhere within about +/-0.25 deg of the base location are then obtained by a
second-order Taylor expansion (checked against direct computation in check_basis()).
"""
import numpy as np, sys, os, pickle, datetime as dt
sys.path.insert(0, os.path.dirname(__file__))
import model as M

LAT0, LON0 = 25.15, 55.30
ANG = np.round(np.arange(17.0, 19.81, 0.1), 2)
HZ = np.round(np.arange(-0.40, -1.81, -0.05), 3)
D = 0.05  # finite-difference step (deg)


def _ev(dates, lat, lon):
    mid = M.day_starts(dates)
    T = M.transit(mid, lat, lon)
    alt_noon = M._state(mid + T, lat, lon, 0.0)
    z = np.radians(90 - alt_noon)
    r = {'dhuhr': T}
    for f in (1.0, 2.0):
        a = np.degrees(np.arctan(1.0 / (f + np.tan(z))))
        r['asr%d' % f] = M.solve_alt(mid, T / 3600 + 3.3 * f ** 0.4, a, lat, lon)
    r['fajr'] = np.stack([M.solve_alt(mid, T / 3600 - 7, -a, lat, lon) for a in ANG])
    r['isha'] = np.stack([M.solve_alt(mid, T / 3600 + 7, -a, lat, lon) for a in ANG])
    r['rise'] = np.stack([M.solve_alt(mid, T / 3600 - 5.8, h, lat, lon) for h in HZ])
    r['set'] = np.stack([M.solve_alt(mid, T / 3600 + 5.8, h, lat, lon) for h in HZ])
    return r


def build(dates, path):
    dates = list(dates)
    pts = {'0': (LAT0, LON0), 'n': (LAT0 + D, LON0), 's': (LAT0 - D, LON0), 'e': (LAT0, LON0 + D), 'w': (LAT0, LON0 - D),
           'ne': (LAT0 + D, LON0 + D), 'sw': (LAT0 - D, LON0 - D)}
    R = {k: _ev(dates, *v) for k, v in pts.items()}
    B = {'dates': dates, 'lat0': LAT0, 'lon0': LON0, 'ANG': ANG, 'HZ': HZ, 'sd': M.semidiameter(dates)}
    for key in R['0']:
        f0 = R['0'][key]
        fy = (R['n'][key] - R['s'][key]) / (2 * D); fx = (R['e'][key] - R['w'][key]) / (2 * D)
        fyy = (R['n'][key] - 2 * f0 + R['s'][key]) / D ** 2; fxx = (R['e'][key] - 2 * f0 + R['w'][key]) / D ** 2
        fxy = ((R['ne'][key] - R['sw'][key]) - (R['n'][key] - R['s'][key]) - (R['e'][key] - R['w'][key])) / (2 * D * D) * 0 \
            + ((R['ne'][key] + R['sw'][key] - R['n'][key] - R['s'][key] - R['e'][key] - R['w'][key] + 2 * f0) / (2 * D * D))
        B[key] = (f0, fy, fx, fyy, fxx, fxy)
    pickle.dump(B, open(path, 'wb'))
    return B


def at(B, key, lat, lon):
    f0, fy, fx, fyy, fxx, fxy = B[key]
    y = lat - B['lat0']; x = lon - B['lon0']
    return f0 + fy * y + fx * x + 0.5 * fyy * y * y + 0.5 * fxx * x * x + fxy * x * y


def interp_axis(arr, grid, value):
    """linear interpolation along axis 0 (grid may be descending); value scalar or per-date array"""
    g = np.asarray(grid, float)
    if g[0] > g[-1]:
        g = g[::-1]; arr = arr[::-1]
    v = np.broadcast_to(np.asarray(value, float), arr.shape[1:])
    i = np.clip(np.searchsorted(g, v) - 1, 0, len(g) - 2)
    w = (v - g[i]) / (g[i + 1] - g[i])
    cols = np.arange(arr.shape[1])
    return arr[i, cols] * (1 - w) + arr[i + 1, cols] * w


def check_basis(B, lat, lon):
    dates = B['dates']
    direct = _ev(dates, lat, lon)
    worst = {}
    for key in direct:
        worst[key] = float(np.max(np.abs(direct[key] - at(B, key, lat, lon))))
    return worst
