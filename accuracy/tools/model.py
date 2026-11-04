"""High-precision prayer-time event solver used only for the accuracy research.

Solar positions: NREL Solar Position Algorithm (pvlib.spa, numpy mode), topocentric,
without refraction (refraction and horizon terms are handled explicitly here so every
convention is visible).  Accuracy of the SPA is about 0.0003 deg, i.e. well under one
second of time for the events used here.

All event times are returned as continuous seconds after local midnight (Asia/Dubai, UTC+4,
no daylight saving), *before* any rounding or precautionary minutes.
"""
import numpy as np
import datetime as _dt
import pvlib.spa as spa

TZ = 4 * 3600          # Asia/Dubai, fixed UTC+4
DELTA_T = 69.2         # TT-UT1 in seconds for 2019-2026 (IERS values 69.2-69.4)


def day_starts(dates):
    """unix time of local midnight for each datetime.date"""
    return np.array([(_dt.datetime(d.year, d.month, d.day) - _dt.datetime(1970, 1, 1)).total_seconds() - TZ
                     for d in dates])


def _state(ut, lat, lon, h):
    th, th0, e, e0, phi, eot = spa.solar_position_numpy(ut, lat, lon, h, 1010.0, 10.0, DELTA_T, 0.5667, 1)
    return e0


def solve_alt(mid, guess_h, target, lat, lon, h=0.0, rising=True, iters=8):
    """time (s after local midnight) when topocentric geometric altitude of the Sun's centre == target.
    target may be an array (one per date). guess_h: initial guess in hours."""
    t = mid + np.asarray(guess_h, float) * 3600.0
    target = np.broadcast_to(np.asarray(target, float), t.shape)
    for _ in range(iters):
        f = _state(t, lat, lon, h) - target
        d = (_state(t + 30, lat, lon, h) - _state(t - 30, lat, lon, h)) / 60.0
        step = np.clip(f / d, -3600, 3600)
        t = t - step
        if np.max(np.abs(step)) < 0.01:
            break
    return t - mid


def transit(mid, lat, lon, h=0.0):
    """time of upper transit (max altitude), s after local midnight, by derivative root."""
    t = mid + 12.3 * 3600.0
    for _ in range(10):
        a, b, c = (_state(t - 60, lat, lon, h), _state(t, lat, lon, h), _state(t + 60, lat, lon, h))
        d1 = (c - a) / 120.0
        d2 = (c - 2 * b + a) / 3600.0
        step = np.clip(d1 / d2, -3600, 3600)
        t = t - step
        if np.max(np.abs(step)) < 0.01:
            break
    return t - mid


def semidiameter(dates):
    """apparent solar semidiameter in degrees (959.63"/r)."""
    n = np.array([(d - _dt.date(2000, 1, 1)).days for d in dates]) - 0.5 + 0.33  # days from J2000.0 to ~08 UT
    g = np.radians(357.529 + 0.98560028 * n)  # mean anomaly
    r = 1.00014 - 0.01671 * np.cos(g) - 0.00014 * np.cos(2 * g)
    return 959.63 / 3600.0 / r


def events(dates, lat, lon, fajr_angle=18.0, isha_angle=18.0, elev_m=0.0, dip_coef=0.0353333,
           refraction=0.5667, use_sd=True, parallax_term=0.0, asr_factor=1.0):
    """continuous event times (s after local midnight) for the given convention.
    Horizon for sunrise/maghrib: zenith = 90 + SD + refraction + dip_coef*sqrt(elev_m) (+parallax_term).
    The SPA altitude is already topocentric (parallax included), so parallax_term defaults to 0."""
    dates = list(dates)
    mid = day_starts(dates)
    sd = semidiameter(dates) if use_sd else 0.0
    hz = -(sd + refraction + dip_coef * np.sqrt(max(elev_m, 0.0)) + parallax_term)
    T = transit(mid, lat, lon)
    alt_noon = _state(mid + T, lat, lon, 0.0)
    z_noon = np.radians(90.0 - alt_noon)
    asr_alt = np.degrees(np.arctan(1.0 / (asr_factor + np.tan(z_noon))))
    out = {
        'fajr': solve_alt(mid, T / 3600 - 7, -fajr_angle, lat, lon),
        'shuruq': solve_alt(mid, T / 3600 - 5.8, hz, lat, lon),
        'dhuhr': T,
        'asr': solve_alt(mid, T / 3600 + 3.3, asr_alt, lat, lon),
        'maghrib': solve_alt(mid, T / 3600 + 5.8, hz, lat, lon),
        'isha': solve_alt(mid, T / 3600 + 7, -isha_angle, lat, lon),
    }
    return out


def to_minutes(sec, mode, offset_min=0):
    """apply rounding then precautionary minutes. mode: 'ceil' | 'round' | 'floor'"""
    m = sec / 60.0
    if mode == 'ceil':
        r = np.ceil(m - 1e-9)
    elif mode == 'round':
        r = np.floor(m + 0.5)
    elif mode == 'floor':
        r = np.floor(m)
    else:
        raise ValueError(mode)
    return (r + offset_min).astype(int)
