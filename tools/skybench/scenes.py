"""Writes scenes.json: Dubai (dunes) and Istanbul (hills) through 7 November 2026. Usage: python3 scenes.py [density]"""
import json, sys
def hm(h, m): return (h * 60 + m) / 1440
dxb = {"lat": 25.2048, "lon": 55.2708, "tz": 4}
ist = {"lat": 41.0082, "lon": 28.9784, "tz": 3}
times = [("0430", 4, 30), ("0525", 5, 25), ("0600", 6, 0), ("0620", 6, 20), ("0645", 6, 45), ("0900", 9, 0), ("1200", 12, 0),
         ("1530", 15, 30), ("1700", 17, 0), ("1725", 17, 25), ("1745", 17, 45), ("1810", 18, 10), ("1840", 18, 40), ("2200", 22, 0)]
den = float(sys.argv[1]) if len(sys.argv) > 1 else 1.5
out = []
for tag, place, dunes, seed in [("dxb", dxb, True, 3), ("ist", ist, False, 11)]:
    for name, h, m in times:
        out.append(dict(name=f"{tag}_{name}", place=place, date="2026-11-07", fraction=hm(h, m), dunes=dunes, seed=seed, landSeed=seed * 7 + 1,
                        density=den, skyScale=0.6, wDp=412, hDp=400, horizonDp=318, arcTopDp=237, topDp=40, time=40, hijriDay=26))
json.dump(out, open("scenes.json", "w"))
