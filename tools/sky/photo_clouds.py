"""Cuts the home sky's clouds (0.9.9) out of real photographs and packs them for the app.

The photographs are public domain or CC0, from Wikimedia Commons (tools/sky/photos/manifest.json lists each one, its
author, licence and page; docs/CREDITS.md credits them). Each cloud is separated from the sky behind it the way a
photographer would key it: the sky's colour is estimated across the picture (a smooth fit to the clear-sky pixels),
and a cloud's opacity is how far a pixel has moved from that blue towards the grey-white of vapour. What remains is
the cloud's own brightness (its light and shade, as photographed) and its opacity; the app recolours the brightness
with the light of the moment (white and blue-grey by day, gold and violet at sunset, moonlit at night).

Writes app/src/main/res/raw/clouds.webp (straight RGBA: grey = the cloud's brightness, alpha = opacity; every cloud at
up to four sizes) and cloud_atlas.txt (where each is, its kind, how it is lit, whether its base is hidden).

The photographs themselves are not kept in the repository: `--fetch` downloads them (Wikimedia's 1280-pixel copies,
the URLs in the manifest) into tools/sky/photos/ first.

Usage: python3 tools/sky/photo_clouds.py [--fetch] [photos_dir] [out_dir] [--sheet sheet.png]   (needs numpy, scipy, Pillow)
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
ORDER = ["humilis", "mediocris", "congestus", "cumulonimbus", "stratocumulus", "altocumulus", "fractus", "row"]

# Per photograph: the kind of its clouds, where the ground starts (fraction of the height; everything below is
# ignored), rectangles to ignore (buildings, lamp posts), and the clouds to take: rectangles (fractions x0, y0, x1, y1)
# chosen by eye around whole clouds, each with optional flags: "cut" (its base is cut off by the ground or the picture;
# it hides behind the app's land) and a kind of its own. Where a cloud is cut by its rectangle's sides it thins out.
SPECS = {
    "a06": dict(kind="humilis", ground=0.78, boxes=[(0.28, 0.47, 0.60, 0.60, "x2"), (0.03, 0.57, 0.20, 0.68), (0.70, 0.59, 0.86, 0.66)]),
    "a10": dict(kind="congestus", ground=0.999, boxes=[(0.15, 0.55, 1.0, 1.0, "cut", "x3")]),
    "a12": dict(kind="congestus", ground=0.999, boxes=[(0.33, 0.20, 1.0, 1.0, "cut", "x2")]),
    "a13": dict(kind="congestus", ground=0.999, boxes=[(0.0, 0.10, 1.0, 1.0, "cut", "x3")]),
    "a16": dict(kind="congestus", ground=0.999, boxes=[(0.0, 0.03, 0.73, 1.0, "cut", "x2")]),
    "a18": dict(kind="congestus", ground=0.80, boxes=[(0.47, 0.48, 0.68, 0.80, "cut")]),
    "a20": dict(kind="cumulonimbus", ground=0.60, boxes=[(0.08, 0.04, 0.97, 0.60, "cut")]),
    "a25": dict(kind="cumulonimbus", ground=0.73, ignore=[(0.86, 0.45, 1.0, 1.0), (0.0, 0.3, 0.15, 1.0)], boxes=[(0.17, 0.20, 0.93, 0.73, "cut")]),
    "a26": dict(kind="cumulonimbus", ground=0.70, boxes=[(0.05, 0.18, 0.97, 0.70, "cut")]),
    "c00": dict(kind="mediocris", ground=0.999, boxes=[(0.06, 0.08, 0.92, 0.88)]),
    "c02": dict(kind="mediocris", ground=0.999, boxes=[(0.08, 0.13, 0.94, 0.84)]),
    "c03": dict(kind="mediocris", ground=0.999, boxes=[(0.10, 0.06, 0.67, 0.58), (0.0, 0.60, 0.14, 0.71, "humilis"),
                                                        (0.18, 0.74, 0.34, 0.88, "humilis")]),
    "b10": dict(kind="fractus", ground=0.999, boxes=[(0.33, 0.32, 0.66, 0.64)]),
    "b13": dict(kind="mediocris", ground=0.80, ignore=[(0.8, 0.4, 1.0, 1.0)], boxes=[(0.08, 0.06, 0.78, 0.62)]),
}

# The largest copy of each kind, px wide: no larger than the app ever draws it on a large phone.
MAX_W = {"humilis": 176, "mediocris": 256, "congestus": 320, "cumulonimbus": 400, "fractus": 144, "row": 512, "stratocumulus": 448}
ATLAS_W = 2048


def srgb_to_lin(x):
    return np.where(x <= 0.04045, x / 12.92, ((x + 0.055) / 1.055) ** 2.4)


def lin_to_srgb(x):
    x = np.clip(x, 0, 1)
    return np.where(x <= 0.0031308, 12.92 * x, 1.055 * x ** (1 / 2.4) - 0.055)


def poly_fit(xs, ys, vals, X, Y):
    """A smooth (quadratic) field through the sky samples, evaluated over the image."""
    A = np.stack([np.ones_like(xs), xs, ys, xs * xs, xs * ys, ys * ys], 1)
    coef = np.zeros(6)
    keep = np.ones(len(xs), bool)
    for _ in range(3):   # drop the samples furthest from the fit (thin haze, the Sun's glare)
        coef, *_ = np.linalg.lstsq(A[keep], vals[keep], rcond=None)
        err = np.abs(A @ coef - vals)
        keep = err <= np.percentile(err[keep], 85) * 1.5 + 1e-4
    return coef[0] + coef[1] * X + coef[2] * Y + coef[3] * X * X + coef[4] * X * Y + coef[5] * Y * Y


def key(img, spec):
    """Opacity and the cloud's own linear colour."""
    h, w, _ = img.shape
    lin = srgb_to_lin(img)
    Y, X = np.mgrid[0:h, 0:w].astype(np.float64)
    Xn, Yn = X / w, Y / h
    valid = Yn < spec["ground"]
    for x0, y0, x1, y1 in spec.get("ignore", []):
        valid &= ~((Xn >= x0) & (Xn < x1) & (Yn >= y0) & (Yn < y1))
    L = 0.2126 * lin[..., 0] + 0.7152 * lin[..., 1] + 0.0722 * lin[..., 2]
    if spec.get("mono"):
        # A black-and-white photograph: the sky is the darker, the cloud the brighter.
        sky_px = valid & (L < np.percentile(L[valid], 30))
        Ls = poly_fit(Xn[sky_px], Yn[sky_px], L[sky_px], Xn, Yn)
        Lc = np.percentile(L[valid], 99)
        a = np.clip((L - Ls) / np.maximum(Lc - Ls, 1e-3) * 1.6, 0, 1)
        sky = np.repeat(Ls[..., None], 3, -1)
    else:
        d = lin[..., 2] - lin[..., 0]                       # how blue: the sky's mark
        rel = d / np.maximum(lin[..., 2], 1e-3)
        sky_px = valid & (rel >= np.percentile(rel[valid], 70)) & (rel > 0.15)
        if sky_px.sum() < 500: sky_px = valid & (rel > np.percentile(rel[valid], 80))
        sky = np.stack([poly_fit(Xn[sky_px], Yn[sky_px], lin[..., c][sky_px], Xn, Yn) for c in range(3)], -1)
        sky = np.maximum(sky, 1e-4)
        # Each pixel is the sky seen through the cloud plus the cloud's own light: P = (1 − a)·S + a·C, where C is
        # grey (a little blue where shaded, lit by the blue sky) of any brightness l. Unknowns u = a·l and a, three
        # equations (red, green, blue): solved per pixel by least squares. Shaded, bluish bases come out opaque.
        t = np.array([1.0, 1.02, 1.15])
        R = lin - sky
        m11 = float(t @ t); m12 = -(sky @ t); m22 = (sky * sky).sum(-1)
        b1 = R @ t; b2 = -(R * sky).sum(-1)
        det = m11 * m22 - m12 * m12
        a = (m11 * b2 - m12 * b1) / np.maximum(det, 1e-9)
        a = np.clip(a * 1.25, 0, 1)
    a = np.where(valid, a, 0.0)
    a = ndimage.gaussian_filter(a, 0.6)
    # What is left of the sky's own noise and of an imperfect fit (a faint veil) is clear sky.
    a = np.clip((a - 0.16) / 0.84, 0, 1)
    col = np.where(a[..., None] > 0.03, (lin - sky * (1 - a[..., None])) / np.maximum(a[..., None], 0.03), 0)
    return a, np.clip(col, 0, 4), valid


def extract(name, img, spec):
    h, w, _ = img.shape
    a, col, valid = key(img, spec)
    sprites = []
    for k, box in enumerate(spec["boxes"]):
        x0, y0, x1, y1 = box[:4]
        flags = box[4:]
        rooted = "cut" in flags
        kind = next((f for f in flags if f in ORDER), spec["kind"])
        # How many clouds' worth of width it holds (a group of clouds): it is drawn that much wider.
        group = next((int(f[1:]) for f in flags if f[0] == "x"), 1)
        # A little more room than chosen by eye, so that a whole cloud never touches its rectangle.
        x0, y0, x1 = max(x0 - 0.03, 0.0), max(y0 - 0.03, 0.0), min(x1 + 0.03, 1.0)
        y1 = y1 if rooted else min(y1 + 0.03, spec["ground"])
        X0, Y0, X1, Y1 = int(x0 * w), int(y0 * h), int(round(x1 * w)), int(round(y1 * h))
        aa = a[Y0:Y1, X0:X1].copy()
        cc = col[Y0:Y1, X0:X1]
        hh, ww = aa.shape
        # Thin out towards the rectangle's sides (nothing to do where the cloud does not reach them); a cut base is
        # kept as it is, flat, for the land to hide.
        ramp = lambda n, size: np.clip(np.arange(n) / max(size, 1), 0, 1) ** 1.5
        aa *= ramp(ww, 3)[None, :] * ramp(ww, 3)[::-1][None, :] * ramp(hh, 3)[:, None]
        if not rooted: aa *= ramp(hh, 3)[::-1][:, None]
        # Only the cloud itself and a soft margin round it: whatever veil is left elsewhere in the rectangle goes, and
        # so do other clouds the rectangle cuts into. Where the cloud itself is cut by the rectangle, it thins out.
        core = aa > 0.45
        lab, nl = ndimage.label(core)
        keep = np.zeros_like(core)
        mx, my = 3, 3
        if nl:
            sizes = ndimage.sum(core, lab, range(1, nl + 1))
            main = 1 + int(np.argmax(sizes))
            for j, sl in enumerate(ndimage.find_objects(lab), 1):
                if sizes[j - 1] < max(0.004 * hh * ww, 30): continue
                ys, xs = sl
                mx, my = 3, 3          # the first three pixels of each side are thinned out anyway
                edge = xs.start <= mx or xs.stop >= ww - mx or ys.start <= my or (ys.stop >= hh - my and not rooted)
                if edge and j != main: continue
                keep |= lab == j
            ys, xs = ndimage.find_objects(lab)[main - 1]
            # A cloud cut by its rectangle (on a side the land will not hide) would show a straight edge: left out.
            if xs.start <= mx or xs.stop >= ww - mx or ys.start <= my or (ys.stop >= hh - my and not rooted):
                if not (rooted or spec.get("thin_sides")) or ys.start <= my or (ys.stop >= hh - my and not rooted):
                    print("  (cut by its rectangle:", name, k, ")"); continue
                # Rising from behind the land, or a long row along the horizon: its ends thin out into the haze.
                if xs.start <= mx: aa *= ramp(ww, 0.3 * ww)[None, :]
                if xs.stop >= ww - mx: aa *= ramp(ww, 0.3 * ww)[::-1][None, :]
        if rooted:
            # Far off, a cloud's lower part melts into the haze over the horizon (and its cut-off foot with it).
            aa *= np.clip(np.arange(hh)[::-1] / max(0.45 * hh, 1), 0.12, 1)[:, None] ** 0.9
        r = max(int(0.03 * max(hh, ww)), 3)
        near = ndimage.gaussian_filter(ndimage.binary_dilation(keep, iterations=r).astype(np.float32), r / 2)
        aa *= np.clip(near * 1.5, 0, 1)
        L = 0.2126 * cc[..., 0] + 0.7152 * cc[..., 1] + 0.0722 * cc[..., 2]
        wts = aa > 0.5
        if wts.sum() < 200: print("  (nothing solid in", name, k, ")"); continue
        hi = np.percentile(L[wts], 99.5)
        # The cloud's light and shade as photographed, its brightest at 1, on a display scale.
        g = lin_to_srgb(np.clip(L / max(hi, 1e-3), 0, 1))
        # Under the transparent parts, the nearest cloud's brightness (so filtering and compression keep edges clean).
        wa = ndimage.gaussian_filter(aa, 6) + 1e-4
        g = np.where(aa > 0.08, g, ndimage.gaussian_filter(g * aa, 6) / wa)
        # Which side is lit (+1 right, −1 left): the brighter half.
        cx = np.arange(ww)[None, :].repeat(hh, 0)
        mid = (cx[wts].min() + cx[wts].max()) / 2
        right = g[wts & (cx > mid)].mean(); left = g[wts & (cx <= mid)].mean()
        side = 1 if right > left + 0.03 else (-1 if left > right + 0.03 else 0)
        # The base: the lowest row where the cloud is solid across a good width.
        rows = np.where((aa > 0.5).sum(1) > 0.12 * ww)[0]
        base = hh - 1 - rows.max() if len(rows) and not rooted else 0
        sprites.append(dict(src=f"{name}:{k}", kind=kind, group=group, rooted=rooted, side=side, backlit=bool(spec.get("backlit")),
                            a=aa.astype(np.float32), g=g.astype(np.float32), base=int(base)))
    return sprites


def downsample(t):
    """Half size, averaging premultiplied values (straight in, straight out)."""
    pm = t.copy(); pm[..., :3] *= pm[..., 3:4]
    h, w = t.shape[0] // 2, t.shape[1] // 2
    hh = pm[:h * 2, :w * 2].reshape(h, 2, w, 2, 4).mean(axis=(1, 3))
    hh[..., :3] = np.where(hh[..., 3:4] > 1e-4, np.clip(hh[..., :3] / np.maximum(hh[..., 3:4], 1e-4), 0, 1), 0)
    return hh


def shelf_pack(sizes, width, pad=2):
    order = sorted(range(len(sizes)), key=lambda i: (-sizes[i][1], -sizes[i][0]))
    pos = [None] * len(sizes); x = y = row_h = 0
    for i in order:
        w, h = sizes[i]
        if x + w > width: x = 0; y += row_h + pad; row_h = 0
        pos[i] = (x, y); x += w + pad; row_h = max(row_h, h)
    return pos, y + row_h


def fetch(photos):
    """Downloads the photographs of the manifest (a few at a time: Wikimedia asks automated clients to go slowly)."""
    import subprocess, time
    ua = "NoorSkyAssets/0.9 (https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch)"
    for m in json.load(open(os.path.join(HERE, "photos", "manifest.json"))):
        out = os.path.join(photos, m["name"] + ".jpg")
        if os.path.exists(out) and os.path.getsize(out) > 20000: continue
        for attempt in range(6):
            r = subprocess.run(["curl", "-sS", "-m", "90", "-A", ua, "-o", out, "-w", "%{http_code}", m["url"]], capture_output=True, text=True)
            if r.stdout.strip() == "200": break
            time.sleep(20 * (attempt + 1))
        else:
            raise SystemExit("could not download " + m["url"])
        print("fetched", m["name"], flush=True)
        time.sleep(6)


def main(photos=None, out_dir=None, sheet=None):
    photos = photos or os.path.join(HERE, "photos")
    out_dir = out_dir or os.path.join(HERE, "../../app/src/main/res/raw")
    sprites = []
    for name in sorted(SPECS):
        path = os.path.join(photos, name + ".jpg")
        if not os.path.exists(path): print("missing", path); continue
        img = np.asarray(Image.open(path).convert("RGB")).astype(np.float64) / 255
        got = extract(name, img, SPECS[name])
        print(name, SPECS[name]["kind"], len(got), "clouds", flush=True)
        sprites += got
    # Each at up to four sizes, the largest at most MAX_W wide.
    levels = []
    for s in sprites:
        t = np.dstack([s["g"], s["g"], s["g"], s["a"]])
        f = min(1.0, MAX_W[s["kind"]] * (1.0 if s["group"] == 1 else 1.2) / t.shape[1])
        if f < 1:
            im = Image.fromarray((np.clip(t, 0, 1) * 255).astype(np.uint8), "RGBA")
            # Resize premultiplied, so the colour of transparent pixels cannot leak into edges.
            pm = t.copy(); pm[..., :3] *= pm[..., 3:4]
            nw, nh = max(int(t.shape[1] * f), 1), max(int(t.shape[0] * f), 1)
            r = np.dstack([np.asarray(Image.fromarray(pm[..., c].astype(np.float32), "F").resize((nw, nh), Image.LANCZOS)) for c in range(4)])
            r[..., 3] = np.clip(r[..., 3], 0, 1)
            r[..., :3] = np.where(r[..., 3:4] > 1e-4, np.clip(r[..., :3] / np.maximum(r[..., 3:4], 1e-4), 0, 1), 0)
            t = r
            s["base"] = int(round(s["base"] * f))
        lv = [t]
        while lv[-1].shape[1] > 64 and len(lv) < 3: lv.append(downsample(lv[-1]))
        levels.append(lv)
    images = [(i, l) for i, lv in enumerate(levels) for l in range(len(lv))]
    sizes = [(levels[i][l].shape[1], levels[i][l].shape[0]) for i, l in images]
    pos, used = shelf_pack(sizes, ATLAS_W)
    height = int(np.ceil(used / 64) * 64)
    atlas = np.zeros((height, ATLAS_W, 4), np.float32)
    rects = [[] for _ in sprites]
    for (i, l), (x, y), (w, h) in zip(images, pos, sizes):
        atlas[y:y + h, x:x + w] = levels[i][l]
        rects[i].append([x, y, w, h])
    # Lossy WebP for the brightness (photographs: a sixth of the size of a PNG), lossless for the opacity.
    Image.fromarray((np.clip(atlas, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(
        os.path.join(out_dir, "clouds.webp"), "WEBP", quality=90, method=6, alpha_quality=100)
    lines = ["# Generated by tools/sky/photo_clouds.py: the clouds in clouds.png, cut out of photographs. Do not edit.",
             "# kinds: " + " ".join(ORDER),
             "# cloud: kind, width and height of the largest copy (px), its base (px above the bottom; 0 when cut),",
             "#   cut (1: its base is cut off and hides behind the land), lit side (1 right, -1 left, 0 front), backlit,",
             "#   how many clouds' width it holds,",
             "#   then x y w h of each copy in the image, largest first; # and the photograph it comes from"]
    for s, lv, r in zip(sprites, levels, rects):
        lines.append(f"cloud {ORDER.index(s['kind'])} {lv[0].shape[1]} {lv[0].shape[0]} {s['base']} {int(s['rooted'])} {s['side']} {int(s['backlit'])} {s['group']} "
                     + " ".join(" ".join(str(v) for v in q) for q in r) + f"  # {s['src']}")
    open(os.path.join(out_dir, "cloud_atlas.txt"), "w").write("\n".join(lines) + "\n")
    print(len(sprites), "clouds; atlas", ATLAS_W, "x", height)
    if sheet:
        # Every cloud over a plain sky, for review.
        tiles = []
        for s, lv in zip(sprites, levels):
            t = lv[min(1, len(lv) - 1)]
            bg = np.ones_like(t[..., :3]) * np.array([0.30, 0.52, 0.85])
            c = t[..., :3] * t[..., 3:4] + bg * (1 - t[..., 3:4])
            tile = Image.fromarray((np.clip(c, 0, 1) * 255).astype(np.uint8))
            from PIL import ImageDraw
            ImageDraw.Draw(tile).text((3, 2), s["src"] + (" cut" if s["rooted"] else ""), fill=(255, 255, 0))
            tiles.append(np.pad(np.asarray(tile) / 255.0, ((2, 2), (2, 2), (0, 0)), constant_values=0.15))
        W = 1600; rows = []; row = []; rw = 0
        for t in tiles:
            if rw + t.shape[1] > W and row: rows.append(row); row = []; rw = 0
            row.append(t); rw += t.shape[1]
        if row: rows.append(row)
        out = []
        for row in rows:
            hh = max(t.shape[0] for t in row)
            r = np.concatenate([np.pad(t, ((0, hh - t.shape[0]), (0, 0), (0, 0)), constant_values=0.15) for t in row], 1)
            out.append(np.pad(r, ((0, 0), (0, W - r.shape[1]), (0, 0)), constant_values=0.15))
        Image.fromarray((np.concatenate(out, 0) * 255).astype(np.uint8)).save(sheet)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    sheet = sys.argv[sys.argv.index("--sheet") + 1] if "--sheet" in sys.argv else None
    if sheet in args: args.remove(sheet)
    if "--fetch" in sys.argv: fetch((args + [None])[0] or os.path.join(HERE, "photos"))
    main(*(args + [None, None])[:2], sheet=sheet)
