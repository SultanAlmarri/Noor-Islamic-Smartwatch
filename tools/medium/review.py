"""(0.22) Writes scenes for the bench to review a painted scenery under the app's own medium sky at seven moments:
    python3 tools/medium/review.py <id> <terrain> > /tmp/x.json
then  cd tools/skybench; PAINTED_DIR=<dir> NODE_PATH=$(npm root -g) node run.js /tmp/x.json <out>"""
import json, sys
sid, terrain = sys.argv[1], sys.argv[2]
dxb = {"lat": 25.2048, "lon": 55.2708, "tz": 4}
moments = [("1dawn", 6, 22), ("2morning", 8, 30), ("3midday", 12, 10), ("4afternoon", 15, 0), ("5golden", 17, 0), ("6dusk", 17, 55), ("7night", 21, 0)]
legs = [dict(set="hi_wisps", offset=8, weight=1, turned=False), dict(set="lo_few", offset=-6, weight=1, turned=False)]
out = [dict(name=f"{sid}_{n}", place=dxb, date="2026-11-04", fraction=(h * 60 + m) / 1440, terrain=terrain, dunes=False, seed=5, landSeed=78,
            weatherType=1, density=1.5, skyScale=0.6, wDp=412, hDp=400, horizonDp=318, arcTopDp=237, topDp=40, time=40, hijriDay=14,
            painted=sid, legs=legs) for n, h, m in moments]
print(json.dumps(out))
