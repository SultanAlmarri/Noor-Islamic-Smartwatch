"""Golden vectors for the Kotlin engine, from the research reference (NREL SPA via pvlib)."""
import sys, datetime as dt, numpy as np, csv
sys.path.insert(0, 'tools'); import model as M
import pvlib.spa as spa
OUT = '/home/claude/noor-islamic-smartwatch/core/prayer/src/test/resources/'
def meridian(ds, lon, lat):
    ut0 = np.array([(dt.datetime(d.year, d.month, d.day) - dt.datetime(1970, 1, 1)).total_seconds() for d in ds])
    tr, _, _ = spa.transit_sunrise_sunset(ut0, lat, lon, M.DELTA_T, 1); return tr - (ut0 - M.TZ)
def run(ds, lat, lon, fa, ia, extra, tzh, meridian_based=False):
    M.TZ = tzh * 3600
    if not meridian_based:      # the research reference used for Dubai (unchanged)
        ev = M.events(ds, lat, lon, fa, ia, elev_m=1.0, dip_coef=extra)
        ev['dhuhr'] = meridian(ds, lon, lat)
        return ev
    # generic reference: everything anchored on the meridian transit (robust when the Sun passes near the zenith)
    mid = M.day_starts(ds); T = meridian(ds, lon, lat)
    alt_noon = M._state(mid + T, lat, lon, 0.0); z = np.radians(90 - alt_noon)
    asr_alt = np.degrees(np.arctan(1.0 / (1.0 + np.tan(z))))
    hz = -(M.semidiameter(ds) + 0.5667 + extra)
    ev = {'dhuhr': T,
          'fajr': M.solve_alt(mid, T / 3600 - 7, -fa, lat, lon), 'shuruq': M.solve_alt(mid, T / 3600 - 5.8, hz, lat, lon),
          'asr': M.solve_alt(mid, T / 3600 + 3.3, asr_alt, lat, lon), 'maghrib': M.solve_alt(mid, T / 3600 + 5.8, hz, lat, lon),
          'isha': M.solve_alt(mid, T / 3600 + 7, -ia, lat, lon)}
    return ev
P = ['fajr', 'shuruq', 'dhuhr', 'asr', 'maghrib', 'isha']
# 1) Dubai IACAD C1 at the reference point, every day 2014-2030
d0 = dt.date(2014, 1, 1); ds = [d0 + dt.timedelta(i) for i in range((dt.date(2030, 12, 31) - d0).days + 1)]
ev = run(ds, 25.12, 55.189, 18.0, 18.0, 0.10, 4)
with open(OUT + 'golden_dubai_c1_raw_seconds.csv', 'w', newline='') as f:
    w = csv.writer(f); w.writerow(['date'] + P)
    for i, d in enumerate(ds): w.writerow([d.isoformat()] + ['%.2f' % ev[p][i] for p in P])
# 2) generic checks: other places/angles, raw seconds (no rounding, no offsets), 2026 every 7 days
cases = [('makkah_uqu', 21.4225, 39.8262, 18.5, 18.0, 0.0, 3), ('cairo_egy', 30.0444, 31.2357, 19.5, 17.5, 0.0, 2),
         ('newyork_isna', 40.7128, -74.0060, 15.0, 15.0, 0.0, -5), ('jakarta_sgp', -6.2088, 106.8456, 20.0, 18.0, 0.0, 7),
         ('karachi_kar', 24.8607, 67.0011, 18.0, 18.0, 0.0, 5)]
ds2 = [dt.date(2026, 1, 3) + dt.timedelta(7 * i) for i in range(52)]
with open(OUT + 'golden_generic_raw_seconds.csv', 'w', newline='') as f:
    w = csv.writer(f); w.writerow(['case', 'lat', 'lon', 'fajr_angle', 'isha_angle', 'utc_offset_h', 'date'] + P)
    for name, lat, lon, fa, ia, extra, tz in cases:
        ev = run(ds2, lat, lon, fa, ia, extra, tz, meridian_based=True)
        # discard reference roots that landed on the wrong side of noon (solver jumped to the other crossing)
        for p, before in (('fajr', True), ('shuruq', True), ('asr', False), ('maghrib', False), ('isha', False)):
            bad = ((ev[p] >= ev['dhuhr']) if before else (ev[p] <= ev['dhuhr'])) | (np.abs(ev[p] - ev['dhuhr']) > 10 * 3600)
            ev[p] = np.where(bad, np.nan, ev[p])
        for i, d in enumerate(ds2): w.writerow([name, lat, lon, fa, ia, tz, d.isoformat()] + ['%.2f' % ev[p][i] for p in P])
M.TZ = 4 * 3600
print('ok', len(ds), 'days +', len(cases) * len(ds2), 'generic rows')
