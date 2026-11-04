"""Compare the watch-face prayer engine with
  (1) a high-precision ephemeris (PyEphem / VSOP87) for the raw astronomy, and
  (2) the IACAD-sourced Dubai timetable for the final official-style times.
"""
import csv, datetime as dt, math, sys, os
import ephem
sys.path.insert(0, os.path.dirname(__file__))
import engine, data

P = engine.Params()


def env_for(date, city="dxb", method="dubai", offset_min=240, asr="standard", hl="angle", adj=None):
    e = {
        "YEAR": date.year, "DAY_OF_YEAR": date.timetuple().tm_yday,
        "TIMEZONE_OFFSET_MINUTES_DST": offset_min,
        "CONFIGURATION.city": city, "CONFIGURATION.method": method,
        "CONFIGURATION.asrhl": ("han_" if asr == "hanafi" else "std_") + {"angle": "angle", "midnight": "mid", "seventh": "7th"}[hl],
    }
    for ev in data.EVENTS:
        e["CONFIGURATION.adj_" + ev] = data.adj_id((adj or {}).get(ev, 0))
    return e


EXPR = {ev: engine.event_seconds(P, ev) for ev in data.EVENTS}
RAW = {ev: engine.event_hours(P, ev) for ev in data.EVENTS}


def hm(sec):
    sec = int(sec) % 86400
    return "%02d:%02d" % (sec // 3600, (sec % 3600) // 60)


def ephem_crossing(date, lat, lon, elev, alt_deg, rising, offset_h):
    o = ephem.Observer()
    o.lat, o.lon, o.elevation = str(lat), str(lon), 0
    o.pressure = 0
    o.horizon = str(alt_deg)
    # local midnight -> UTC
    start = dt.datetime(date.year, date.month, date.day) - dt.timedelta(hours=offset_h)
    o.date = ephem.Date(start)
    s = ephem.Sun()
    t = o.next_rising(s, use_center=True) if rising else o.next_setting(s, use_center=True)
    local = ephem.Date(t).datetime() + dt.timedelta(hours=offset_h)
    return (local - dt.datetime(date.year, date.month, date.day)).total_seconds() / 3600.0


def ephem_transit(date, lat, lon, offset_h):
    o = ephem.Observer(); o.lat, o.lon = str(lat), str(lon)
    start = dt.datetime(date.year, date.month, date.day) - dt.timedelta(hours=offset_h)
    o.date = ephem.Date(start)
    t = o.next_transit(ephem.Sun())
    local = ephem.Date(t).datetime() + dt.timedelta(hours=offset_h)
    return (local - dt.datetime(date.year, date.month, date.day)).total_seconds() / 3600.0


def astro_check():
    """Raw astronomy vs PyEphem, several cities / seasons. Returns max error (s)."""
    worst = 0.0
    rows = []
    for cid, en, ar, lat, lon, elev in data.CITIES:
        for month in (1, 3, 6, 9, 12):
            date = dt.date(2026, month, 15)
            # use the city's own standard-ish offset (only matters for day boundaries)
            off = round(lon / 15.0)
            env = env_for(date, city=cid, method="mwl", offset_min=off * 60)
            h0 = 0.833 + 0.0347 * math.sqrt(elev)
            checks = {
                "dhuhr": (RAW["dhuhr"].eval(env), ephem_transit(date, lat, lon, off)),
                "sunrise": (RAW["sunrise"].eval(env), ephem_crossing(date, lat, lon, elev, -h0, True, off)),
            }
            if abs(lat) <= 45:
                checks["fajr18"] = (RAW["fajr"].eval(env), ephem_crossing(date, lat, lon, elev, -18.0, True, off))
            for k, (mine, ref) in checks.items():
                if k == "fajr18" and abs(lat) > 45:
                    continue      # high-latitude rule may legitimately differ
                err = (mine - ref) * 3600
                worst = max(worst, abs(err))
                rows.append((cid, month, k, err))
    return worst, rows


def iacad_compare(lat=None, lon=None, rounding=None):
    path = os.path.join(os.path.dirname(__file__), "..", "reference_data", "iacad_dubai_2026_11.csv")
    rows = [r for r in csv.DictReader(l for l in open(path) if not l.startswith("#"))]
    out = []
    for r in rows:
        date = dt.date.fromisoformat(r["date"])
        env = env_for(date)
        mine = {ev: EXPR[ev].eval(env) for ev in data.EVENTS}
        ref = {}
        for ev, col in zip(data.EVENTS, ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"]):
            h, m = map(int, r[col].split(":"))
            ref[ev] = h * 3600 + m * 60
        out.append((r["date"], mine, ref))
    return out


if __name__ == "__main__":
    worst, rows = astro_check()
    print("Raw astronomy vs PyEphem (VSOP87): worst |error| = %.1f s over %d checks" % (worst, len(rows)))
    res = iacad_compare()
    diffs = {ev: [] for ev in data.EVENTS}
    print("\nDubai, Nov 2026: engine (Dubai method) vs IACAD-sourced table (minutes, engine - table)")
    print("date        " + " ".join("%8s" % e for e in data.EVENTS))
    for date, mine, ref in res:
        line = []
        for ev in data.EVENTS:
            d = (mine[ev] - ref[ev]) // 60
            diffs[ev].append(d)
            line.append("%8s" % ("%s(%+d)" % (hm(mine[ev]), d)))
        print(date, " ".join(line))
    print("\nexact-minute matches per event:")
    for ev in data.EVENTS:
        v = diffs[ev]
        print("  %-8s %2d/%d exact, max |diff| %d min" % (ev, sum(1 for x in v if x == 0), len(v), max(abs(x) for x in v)))
