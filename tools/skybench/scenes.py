"""Writes scenes.json. Usage: python3 scenes.py [density] [terrains]

Without "terrains": Dubai (dunes) and Istanbul (hills) through 7 November 2026.
With "terrains" (0.19): each of the six sceneries the 0.9 sky draws (dunes, hills, mountains, sea, forest, arctic,
canyon) at seven moments of the same day in Istanbul, so they differ only in their land: names <terrain>_<moment>."""
import json, sys
def hm(h, m): return (h * 60 + m) / 1440
dxb = {"lat": 25.2048, "lon": 55.2708, "tz": 4}
ist = {"lat": 41.0082, "lon": 28.9784, "tz": 3}
times = [("0430", 4, 30), ("0525", 5, 25), ("0600", 6, 0), ("0620", 6, 20), ("0645", 6, 45), ("0900", 9, 0), ("1200", 12, 0),
         ("1530", 15, 30), ("1700", 17, 0), ("1725", 17, 25), ("1745", 17, 45), ("1810", 18, 10), ("1840", 18, 40), ("2200", 22, 0)]
den = float(sys.argv[1]) if len(sys.argv) > 1 else 1.5
mode = sys.argv[2] if len(sys.argv) > 2 else ""
out = []
if mode == "terrains":
    # Istanbul, 7 November 2026: Shuruq 7:42, Maghrib 17:53.
    moments = [("1dawn", 7, 25), ("2morning", 9, 0), ("3midday", 12, 30), ("4golden", 17, 0), ("5sunset", 17, 50), ("6dusk", 18, 15), ("7night", 21, 30)]
    terrains = sys.argv[3].split(",") if len(sys.argv) > 3 else ["dunes", "hills", "mountains", "sea", "forest", "arctic", "canyon"]
    # (0.19.1) The clouds of each moment: the real sky's baked sets, as its director might choose them (the app asks
    # SkyDirector; here a fixed, varied day): (set, offset in degrees, weight, turned).
    legs = {"1dawn": [("hi_wisps", -10, 1, False), ("mi_patches", 14, 1, False)],
            "2morning": [("hi_sprays", 6, 1, False), ("lo_few", -8, 1, False)],
            "3midday": [("hi_wisps", 20, 1, True), ("lo_scattered", 4, 1, False)],
            "4golden": [("hi_mixed", -4, 1, False), ("lo_broken", -12, 1, False), ("lo_scattered", 52, 1, True)],
            "5sunset": [("hi_ripples", 0, 1, False), ("mi_bands", 10, 1, False), ("lo_few", 20, 0.8, False)],
            "6dusk": [("hi_veil", -6, 1, False), ("mi_patches", 24, 1, True)],
            "7night": [("hi_wisps", 12, 0.7, False), ("lo_few", -20, 0.5, False)]}
    for t in terrains:
        for name, h, m in moments:
            out.append(dict(name=f"{t}_{name}", place=ist, date="2026-11-07", fraction=hm(h, m), terrain=t, dunes=t == "dunes", seed=11, landSeed=78,
                            weatherType=1, density=den, skyScale=0.6, wDp=412, hDp=400, horizonDp=318, arcTopDp=237, topDp=40, time=40, hijriDay=12,
                            legs=[dict(set=a, offset=o, weight=w, turned=tu) for a, o, w, tu in legs[name]]))
else:
    for tag, place, dunes, seed in [("dxb", dxb, True, 3), ("ist", ist, False, 11)]:
        for name, h, m in times:
            out.append(dict(name=f"{tag}_{name}", place=place, date="2026-11-07", fraction=hm(h, m), dunes=dunes, seed=seed, landSeed=seed * 7 + 1,
                            density=den, skyScale=0.6, wDp=412, hDp=400, horizonDp=318, arcTopDp=237, topDp=40, time=40, hijriDay=26))
json.dump(out, open("scenes.json", "w"))
