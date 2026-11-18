"""(0.22) Packs the medium graphics' painted sceneries into the app.

    python3 tools/medium/pack.py <final dir> [id ...]

<final dir>/<id>/ holds the seven light states the painter delivered (night, twilight, golden_left, day_left, noon,
day_right, golden_right: 1080 x 1440 RGBA, the sky clear, the true horizon on row 900) and card.png (560 x 420). They
become app/src/main/assets/medium/<id>/<state>.webp (lossy with alpha) and meta.json (the skyline: the top of the
land in 270 strips across, as heights over the horizon in picture widths, for the Sun's ring), and
app/src/main/res/drawable-nodpi/scenery_medium_<id>.webp (the Settings card). See PaintedLand.kt and HANDOVER.md
section 33 (the owner's choice of paintings)."""
import json, os, sys
import numpy as np
from PIL import Image

STATES = ["night", "twilight", "golden_left", "day_left", "noon", "day_right", "golden_right"]
W, H, HORIZON, STRIPS = 1080, 1440, 900, 270
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def skyline(rgba):
    a = np.asarray(rgba)[:, :, 3]
    out = []
    for j in range(STRIPS):
        cols = a[:, j * W // STRIPS:(j + 1) * W // STRIPS]
        rows = np.where((cols >= 128).any(axis=1))[0]
        top = int(rows[0]) if len(rows) else HORIZON
        out.append(round(max(0, HORIZON - top) / W, 5))
    return out


def pack(final, sid):
    src = os.path.join(final, sid)
    dst = os.path.join(ROOT, "app/src/main/assets/medium", sid)
    os.makedirs(dst, exist_ok=True)
    tops = None
    for st in STATES:
        im = Image.open(os.path.join(src, st + ".png")).convert("RGBA")
        assert im.size == (W, H), f"{sid}/{st}: {im.size}, expected {(W, H)}"
        sk = skyline(im)
        # The land's outline is the same in every light; the highest of the seven is kept (a crest lit in one, dark in another).
        tops = sk if tops is None else [max(a, b) for a, b in zip(tops, sk)]
        im.save(os.path.join(dst, st + ".webp"), "WEBP", quality=88, alpha_quality=90, method=6)
    # The land must stay below the home screen's countdown (PaintedLandTest.MAX_RISE).
    assert max(tops) <= 0.225, f"{sid}: the land rises {max(tops):.3f} of the width over the horizon (at most 0.225)"
    json.dump({"width": W, "height": H, "horizon": HORIZON, "skyline": tops}, open(os.path.join(dst, "meta.json"), "w"))
    card = Image.open(os.path.join(src, "card.png")).convert("RGB")
    assert card.size == (560, 420), f"{sid}/card: {card.size}"
    card.save(os.path.join(ROOT, "app/src/main/res/drawable-nodpi", f"scenery_medium_{sid}.webp"), "WEBP", quality=86)
    size = sum(os.path.getsize(os.path.join(dst, f)) for f in os.listdir(dst))
    print(f"{sid}: {size / 1e6:.2f} MB, skyline max {max(tops):.3f} of the width")


if __name__ == "__main__":
    final = sys.argv[1]
    ids = sys.argv[2:] or [d for d in ["pnw", "meadow", "arctic", "ocean", "canyon"] if os.path.isdir(os.path.join(final, d))]
    for sid in ids:
        pack(final, sid)
