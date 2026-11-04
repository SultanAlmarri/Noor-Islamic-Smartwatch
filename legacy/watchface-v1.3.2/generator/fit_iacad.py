"""Fit coordinates / per-event offsets / rounding to the IACAD-sourced table."""
import csv, datetime as dt, math, os, itertools
import data, engine
from compare import env_for

path = os.path.join(os.path.dirname(__file__), "..", "reference_data", "iacad_dubai_2026_11.csv")
rows = [r for r in csv.DictReader(l for l in open(path) if not l.startswith("#"))]
P = engine.Params()
RAW = {ev: engine.event_hours(P, ev) for ev in data.EVENTS}

def raw_minutes(lat, lon, elev, fajr=18.2, isha=18.2):
    # temporarily patch Dubai's constants in data and rebuild
    data.CITIES[0] = ("dxb", "Dubai", "دبي", lat, lon, elev)
    m = dict(data.METHODS[0]); m["fajr"] = fajr; m["isha"] = isha
    data.METHODS[0] = m
    P = engine.Params()
    raw = {ev: engine.event_hours(P, ev) for ev in data.EVENTS}
    out = []
    for r in rows:
        env = env_for(dt.date.fromisoformat(r["date"]))
        out.append({ev: raw[ev].eval(env) * 60 for ev in data.EVENTS})
    return out

ref = []
for r in rows:
    ref.append({ev: int(r[c][:2]) * 60 + int(r[c][3:]) for ev, c in zip(data.EVENTS, ["fajr","sunrise","dhuhr","asr","maghrib","isha"])})

RND = {"nearest": lambda x: math.floor(x + 0.5), "ceil": math.ceil, "floor": math.floor}

def score(raw, ev):
    best = None
    for name, f in RND.items():
        for off in range(-6, 7):
            s = sum(1 for a, b in zip(raw, ref) if f(a[ev] + off) == b[ev])
            if best is None or s > best[0]:
                best = (s, off, name)
    return best

results = []
for lat in [25.05, 25.10, 25.15, 25.2048, 25.25, 25.2582, 25.30]:
    for lon in [55.10, 55.15, 55.20, 55.2708, 55.30, 55.35, 55.40]:
        raw = raw_minutes(lat, lon, 5)
        sc = {ev: score(raw, ev) for ev in data.EVENTS}
        total = sum(v[0] for v in sc.values())
        results.append((total, lat, lon, sc))
results.sort(key=lambda r: -r[0])
for total, lat, lon, sc in results[:6]:
    print(total, lat, lon, {k: v for k, v in sc.items()})

print("\n--- fixed offsets (fajr 0, sunrise -3, dhuhr +3, asr +1, maghrib +3, isha 0), nearest rounding")
OFF = dict(fajr=0, sunrise=-3, dhuhr=3, asr=1, maghrib=3, isha=0)
for lat, lon in [(25.2048, 55.2708), (25.2582, 55.3047), (25.27, 55.30), (25.15, 55.27), (25.10, 55.27), (25.05, 55.30), (25.10, 55.20)]:
    raw = raw_minutes(lat, lon, 5)
    per = {ev: sum(1 for a, b in zip(raw, ref) if math.floor(a[ev] + OFF[ev] + 0.5) == b[ev]) for ev in data.EVENTS}
    mx = max(abs(math.floor(a[ev] + OFF[ev] + 0.5) - b[ev]) for a, b in zip(raw, ref) for ev in data.EVENTS)
    print(lat, lon, sum(per.values()), per, "max|diff|", mx)
