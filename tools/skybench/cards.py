"""(0.20) The medium graphics' pictures on the scenery cards (Settings -> Home scenery), made with the bench from the
app's own shaders (0.20 candidate: the land from the real sky's pictures, PictureLand) and clouds: each scenery on a fine afternoon in Dubai, without the path, the marks or the text, framed as the
high graphics' cards are (560 x 420, the horizon 62 % down, the whole width of a phone).

    cd tools/skybench
    python3 cards.py scenes          # writes cards.json
    NODE_PATH=$(npm root -g) node run.js cards.json cards_out
    python3 cards.py crop            # writes ../../app/src/main/res/drawable-nodpi/scenery_medium_<id>.webp

The low graphics' pictures come from the app's own code in a software render (SceneryCardPicturesTest)."""
import json, os, sys
from PIL import Image

# The scenery cards (Biome ids) and the 0.9 sky's terrain for each (Terrain.of(Biome)).
CARDS = [("dunes", "dunes"), ("pnw", "forest"), ("arctic", "arctic"), ("ocean", "sea"), ("meadow", "hills"), ("canyon", "canyon")]
DXB = {"lat": 25.2048, "lon": 55.2708, "tz": 4}
DEN = 1.5
W_DP, H_DP, HORIZON_DP = 412, 500, 380

if sys.argv[1:2] == ["scenes"]:
    out = [dict(name=f"card_{cid}", place=DXB, date="2026-11-04", fraction=(15 * 60 + 40) / 1440, terrain=t, dunes=t == "dunes",
                seed=5, landSeed=78, weatherType=1, density=DEN, skyScale=0.6, wDp=W_DP, hDp=H_DP, horizonDp=HORIZON_DP,
                arcTopDp=150, topDp=40, time=40, hijriDay=14, pathAlpha=0, marksAlpha=0, text=False,
                # (0.20) A fair afternoon's clouds (the real sky's baked sets, as the app shows them: SetClouds).
                legs=[dict(set="hi_wisps", offset=8, weight=1, turned=False), dict(set="lo_few", offset=-6, weight=1, turned=False)]) for cid, t in CARDS]
    json.dump(out, open("cards.json", "w"))
elif sys.argv[1:2] == ["crop"]:
    res = os.path.join(os.path.dirname(__file__), "../../app/src/main/res/drawable-nodpi")
    for cid, _ in CARDS:
        im = Image.open(f"cards_out/card_{cid}.png").convert("RGB")
        w = im.width; hy = HORIZON_DP * DEN
        sh = w * 420 / 560; top = max(0, hy - sh * 0.62)
        im.crop((0, int(top), w, int(top + sh))).resize((560, 420), Image.LANCZOS).save(os.path.join(res, f"scenery_medium_{cid}.webp"), "WEBP", quality=86)
        print(cid)
else:
    print(__doc__)
