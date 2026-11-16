"""Does a simpler ephemeris reproduce IACAD's official times exactly? (16 Nov 2026)

A published minute M can be reproduced by "continuous model time t + one constant shift c, then rounded up"
for every day if and only if the residuals r = 60*M - t (seconds) all fit inside a window shorter than 60 s
(max(r) - min(r) < 60). Every rounding rule (up, down, nearest) combined with any whole or fractional
precaution is such a constant shift, so the spread test covers all of them at once. No per-date term exists.

Dhuhr is the cleanest probe: meridian transit depends only on longitude and the equation of time (no latitude,
horizon, refraction or angle). This script measures the residual spread of IACAD's current-era Dhuhr (and the
other prayers at the C1 conventions) under several ephemerides that timetable software commonly uses:

  spa        NREL SPA (pvlib), the app's engine (exact to < 0.01 s)
  usno       the USNO "approximate solar coordinates" (praytimes.org, many apps), evaluated at the event
  usno_noon  the same, evaluated once at local noon (the praytimes.org default: one iteration)
  usno_0ut   the same, evaluated once at 0h UT of the date
  meeus      Meeus ch. 25 low-precision apparent Sun (adhan-js style), evaluated at the event
  meeus_0ut  the same, at 0h UT, transit by the 0h-UT equation of time
  eot_fourier  Spencer (1971) Fourier equation of time, evaluated per day (one value per date)
  base YYYY    a perpetual table: each date's times computed for the same calendar date of one base year

usage: python3 accuracy/tools/ephemeris_variants.py   (from the repository root; needs numpy, pvlib)
"""
import os, sys, csv, math, datetime as dt
import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
PR = ['fajr', 'shuruq', 'dhuhr', 'asr', 'maghrib', 'isha']
TZ = 4.0


def load(path):
    rows = {}
    for r in csv.DictReader(l for l in open(path) if not l.startswith('#')):
        d = dt.date.fromisoformat(r['date'])
        rows[d] = {p: int(r[p][:2]) * 60 + int(r[p][3:5]) for p in PR}
    return rows


def official_e3():
    W = load(os.path.join(ROOT, 'data/iacad_official_archive_2014_2023.csv'))
    A = load(os.path.join(ROOT, 'data/iacad_api_2026-11.iso.csv'))
    P = {**W, **A}
    return {d: v for d, v in P.items() if d >= dt.date(2020, 8, 20)}


def jd_of(date, hours_ut):
    return (date - dt.date(2000, 1, 1)).days + 2451544.5 + hours_ut / 24.0


# ------------------------------------------------------------------------- simple ephemerides
def usno(jd):
    """USNO approximate solar coordinates: returns (declination deg, equation of time hours)."""
    D = jd - 2451545.0
    g = math.radians((357.529 + 0.98560028 * D) % 360)
    q = (280.459 + 0.98564736 * D) % 360
    L = math.radians((q + 1.915 * math.sin(g) + 0.020 * math.sin(2 * g)) % 360)
    e = math.radians(23.439 - 0.00000036 * D)
    RA = math.degrees(math.atan2(math.cos(e) * math.sin(L), math.cos(L))) / 15.0
    dec = math.degrees(math.asin(math.sin(e) * math.sin(L)))
    eqt = q / 15.0 - (RA % 24)
    eqt = (eqt + 12) % 24 - 12
    return dec, eqt


def meeus(jd):
    """Meeus ch. 25 low accuracy apparent Sun (with nutation in longitude and obliquity): (dec, eot hours)."""
    T = (jd - 2451545.0) / 36525.0
    L0 = (280.46646 + 36000.76983 * T + 0.0003032 * T * T) % 360
    M = math.radians((357.52911 + 35999.05029 * T - 0.0001537 * T * T) % 360)
    e = 0.016708634 - 0.000042037 * T
    C = (1.914602 - 0.004817 * T) * math.sin(M) + (0.019993 - 0.000101 * T) * math.sin(2 * M) + 0.000289 * math.sin(3 * M)
    lam_true = L0 + C
    Om = math.radians(125.04 - 1934.136 * T)
    lam = math.radians(lam_true - 0.00569 - 0.00478 * math.sin(Om))
    eps0 = 23 + 26 / 60 + 21.448 / 3600 - (46.815 * T) / 3600
    eps = math.radians(eps0 + 0.00256 * math.cos(Om))
    RA = math.degrees(math.atan2(math.cos(eps) * math.sin(lam), math.cos(lam))) % 360
    dec = math.degrees(math.asin(math.sin(eps) * math.sin(lam)))
    # equation of time as mean longitude minus apparent RA, corrected for nutation in RA (Meeus 28.1, simplified)
    eot_deg = L0 - 0.0057183 - RA + (-0.00478 * math.sin(Om)) * math.cos(eps)
    eot_deg = (eot_deg + 180) % 360 - 180
    return dec, eot_deg / 15.0


def spencer_eot(date):
    g = 2 * math.pi / 365.0 * ((date - dt.date(date.year, 1, 1)).days)
    eot_min = 229.18 * (0.000075 + 0.001868 * math.cos(g) - 0.032077 * math.sin(g)
                        - 0.014615 * math.cos(2 * g) - 0.040849 * math.sin(2 * g))
    return eot_min / 60.0


def transit_simple(date, lon, eph, at):
    """local-clock hours of transit by eq. of time evaluated at 'event' (iterated), 'noon' or '0ut'."""
    if at == '0ut':
        _, E = eph(jd_of(date, 0.0))
        return 12 + TZ - lon / 15 - E
    t = 12.0  # local hours
    for _ in range(3 if at == 'event' else 1):
        _, E = eph(jd_of(date, t - TZ))
        t = 12 + TZ - lon / 15 - E
    return t


def hour_angle_time(date, lat, lon, alt, eph, before, at):
    """time (local hours) at which the Sun's centre reaches altitude alt (deg), hour-angle formula."""
    t = transit_simple(date, lon, eph, at) + (-6 if before else 6)
    for _ in range(4 if at == 'event' else 1):
        jd = jd_of(date, (t if at == 'event' else 12.0) - TZ) if at != '0ut' else jd_of(date, 0.0)
        dec, _ = eph(jd)
        T = transit_simple(date, lon, eph, at)
        phi, de = math.radians(lat), math.radians(dec)
        c = (math.sin(math.radians(alt)) - math.sin(phi) * math.sin(de)) / (math.cos(phi) * math.cos(de))
        H = math.degrees(math.acos(max(-1, min(1, c)))) / 15.0
        t = T - H if before else T + H
    return t


def asr_alt(date, lat, lon, eph, at, factor=1.0):
    jd = jd_of(date, transit_simple(date, lon, eph, at) - TZ) if at != '0ut' else jd_of(date, 0.0)
    dec, _ = eph(jd)
    z = abs(lat - dec)
    return math.degrees(math.atan(1.0 / (factor + math.tan(math.radians(z)))))


def semidiameter(date):
    n = (date - dt.date(2000, 1, 1)).days - 0.5 + 0.33
    g = math.radians(357.529 + 0.98560028 * n)
    r = 1.00014 - 0.01671 * math.cos(g) - 0.00014 * math.cos(2 * g)
    return 959.63 / 3600.0 / r


def simple_events(dates, eph, at, lat=25.12, lon=55.189, fa=18.0, ia=18.0, dip=0.10):
    out = {p: [] for p in PR}
    for d in dates:
        hz = -(semidiameter(d) + 0.5667 + dip)
        out['fajr'].append(hour_angle_time(d, lat, lon, -fa, eph, True, at))
        out['shuruq'].append(hour_angle_time(d, lat, lon, hz, eph, True, at))
        out['dhuhr'].append(transit_simple(d, lon, eph, at))
        out['asr'].append(hour_angle_time(d, lat, lon, asr_alt(d, lat, lon, eph, at), eph, False, at))
        out['maghrib'].append(hour_angle_time(d, lat, lon, hz, eph, False, at))
        out['isha'].append(hour_angle_time(d, lat, lon, -ia, eph, False, at))
    return {p: np.array(v) * 3600.0 for p, v in out.items()}


def spa_events(dates, lat=25.12, lon=55.189, fa=18.0, ia=18.0, dip=0.10):
    """the app engine's continuous times (NREL SPA, Dhuhr = SPA transit), from evidence.raw."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import evidence
    return evidence.raw(dates, lat, lon, TZ, dict(fajr=fa, isha=ia, dip=dip))


def best_window(r):
    """largest number of residuals inside a half-open window of 60 s, and the window's start."""
    s = np.sort(r)
    best, start = 0, s[0]
    for i in range(len(s)):
        c = int(np.sum((s >= s[i]) & (s < s[i] + 60 - 1e-6)))
        if c > best:
            best, start = c, s[i]
    return best, start


def report(name, ev, P, dates):
    line = [name]
    tot = 0
    for p in PR:
        r = np.array([P[d][p] * 60.0 for d in dates]) - ev[p]
        n, _ = best_window(r)
        tot += n
        line.append('%s spread %5.1f s, best %d/%d' % (p, r.max() - r.min(), n, len(r)))
    return name, tot, line


def main():
    P = official_e3()
    dates = sorted(P)
    variants = [('spa', spa_events(dates))]
    for at in ('event', 'noon', '0ut'):
        variants.append(('usno_' + at, simple_events(dates, usno, at)))
        variants.append(('meeus_' + at, simple_events(dates, meeus, at)))
    # Spencer: Dhuhr only
    sp = {'dhuhr': np.array([(12 + TZ - 55.189 / 15 - spencer_eot(d)) * 3600 for d in dates])}
    print('IACAD current era: %d official days, %d times' % (len(dates), 6 * len(dates)))
    print('Best achievable exact count with ONE constant shift per prayer (any rounding rule), at the C1 conventions:')
    for name, ev in variants:
        n, tot, line = report(name, ev, P, dates)
        print('  %-12s total %d/%d' % (name, tot, 6 * len(dates)))
        for l in line[1:]:
            print('      ' + l)
    r = np.array([P[d]['dhuhr'] * 60.0 for d in dates]) - sp['dhuhr']
    print('  spencer      dhuhr spread %.1f s, best %d/%d' % (r.max() - r.min(), best_window(r)[0], len(r)))
    # A perpetual table: every year's times copied from one base year's calculation (IACAD's 2014-2017 tables were
    # nearly perpetual: validate.py, integrity test 6)
    print('Perpetual-table hypothesis (times of the same calendar date in one base year):')
    for Y in range(2016, 2028):
        base = [dt.date(Y, d.month, d.day) for d in dates]
        ev = spa_events(base)
        n, tot, line = report('base %d' % Y, ev, P, dates)
        print('  base %d     total %d/%d   dhuhr: %s' % (Y, tot, 6 * len(dates), line[3]))


if __name__ == '__main__':
    main()
