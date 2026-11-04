"""Classic hour-angle prayer-time model with the Sun's declination and equation of time taken at
ONE reference instant per day (non-iterative), versus per-event evaluation (iterative).
Used to test which formulation the official timetables follow.  Solar quantities: PyEphem
(apparent geocentric declination) and SPA equation of time."""
import numpy as np, datetime as dt, ephem, sys, os
sys.path.insert(0, os.path.dirname(__file__))
import model as M
import pvlib.spa as spa


def sun_at(dates, hour_ut):
    dec = []; ut = []
    for d in dates:
        t = dt.datetime(d.year, d.month, d.day) + dt.timedelta(hours=hour_ut)
        s = ephem.Sun(ephem.Date(t)); dec.append(float(s.g_dec)); ut.append((t - dt.datetime(1970, 1, 1)).total_seconds())
    ut = np.array(ut)
    eot = spa.solar_position_numpy(ut, 25.2, 55.3, 0, 1010, 10, M.DELTA_T, 0.5667, 1)[5]
    return np.degrees(np.array(dec)), eot


def H(h, lat, dec):
    c = (np.sin(np.radians(h)) - np.sin(np.radians(lat)) * np.sin(np.radians(dec))) / (np.cos(np.radians(lat)) * np.cos(np.radians(dec)))
    return np.degrees(np.arccos(np.clip(c, -1, 1))) * 4.0      # minutes


def times(dates, lat, lon, dec, eot, fajr=18.0, isha=18.0, hz=-0.8333, asr_f=1.0):
    T = 720 + 4 * (60 - lon) - eot
    a_asr = np.degrees(np.arctan(1.0 / (asr_f + np.tan(np.radians(np.abs(lat - dec))))))
    return {'fajr': T - H(-fajr, lat, dec), 'shuruq': T - H(hz, lat, dec), 'dhuhr': T, 'asr': T + H(a_asr, lat, dec),
            'maghrib': T + H(hz, lat, dec), 'isha': T + H(-isha, lat, dec)}
