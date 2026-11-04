"""Build res/font/icons.ttf: the six prayer/event icons as font glyphs.

Why a font (v1.3): on the watch, a Condition that reads the computed prayer
times (References) is not refreshed, so the old icon/name branches stayed on
Fajr.  Text Templates that read the same values DO update every second, so the
icon is now chosen exactly like the prayer time and countdown: a Template
parameter picks one character, and this font draws that character as the icon.

The outlines are traced from the very same SVG drawings that produce the PNG
icons (icons.py), rendered at 2048 px - so each glyph is the existing icon,
not a redesign.  The Asr icon's 55 %-opacity shadow line is a separate glyph,
drawn by a second text layer at 55 % alpha, exactly as in the PNG.

  U+E000  empty (padding)     U+E003  Dhuhr      U+E006  Isha
  U+E001  Fajr                U+E004  Asr        U+E008  Asr shadow line (55 %)
  U+E002  Shuruq              U+E005  Maghrib

Usage: python3 icon_font.py <out.ttf> [--check <icons-png-dir>]
"""
import io, os, re, sys
import numpy as np
import cv2
import cairosvg
from PIL import Image
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import icons

UPM = 2048
ORDER = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"]
CP = {ev: 0xE001 + i for i, ev in enumerate(ORDER)}
PAD = 0xE000
SHADOW = 0xE008
SHADOW_ALPHA = 0.55
SHADOW_RE = re.compile(r'<line [^>]*opacity="0\.55"/>')


def split_svg(name):
    body = icons.ICONS[name]
    shadow = SHADOW_RE.findall(body)
    main = SHADOW_RE.sub("", body)
    sh = None
    if shadow:
        sh = body[:body.index(">") + 1] + shadow[0].replace(' opacity="0.55"', "") + "</svg>"
    return main, sh


def alpha(svg, size):
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=size, output_height=size)
    return np.array(Image.open(io.BytesIO(png)).convert("RGBA"))[:, :, 3]


def trace(svg):
    """SVG -> TrueType contours (font units, y up, outer clockwise)."""
    a = alpha(svg, UPM)
    _, bw = cv2.threshold(a, 127, 255, cv2.THRESH_BINARY)
    contours, hier = cv2.findContours(bw, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_NONE)
    out = []
    for i, c in enumerate(contours):
        poly = cv2.approxPolyDP(c, 0.75, True).reshape(-1, 2).astype(float)
        if len(poly) < 3:
            continue
        pts = [(float(x) + 0.5, UPM - (float(y) + 0.5)) for x, y in poly]
        area = 0.5 * sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]))
        hole = hier[0][i][3] != -1
        if (not hole and area > 0) or (hole and area < 0):
            pts.reverse()                    # outer: clockwise, hole: counter-clockwise
        out.append([(int(round(x)), int(round(y))) for x, y in pts])
    return out


def glyph(contours):
    pen = TTGlyphPen(None)
    for c in contours:
        pen.moveTo(c[0])
        for p in c[1:]:
            pen.lineTo(p)
        pen.closePath()
    return pen.glyph()


def notdef():
    pen = TTGlyphPen(None)
    for (x0, y0, x1, y1), cw in (((0, 0, UPM, UPM), True), ((64, 64, UPM - 64, UPM - 64), False)):
        pts = [(x0, y0), (x0, y1), (x1, y1), (x1, y0)]
        if not cw:
            pts.reverse()
        pen.moveTo(pts[0])
        for p in pts[1:]:
            pen.lineTo(p)
        pen.closePath()
    return pen.glyph()


def build(out):
    names = [".notdef", "pad"] + ["ic_" + ev for ev in ORDER] + ["ic_asr_shadow"]
    glyphs = {".notdef": notdef(), "pad": TTGlyphPen(None).glyph()}
    for ev in ORDER:
        main, sh = split_svg("ic_" + ev)
        glyphs["ic_" + ev] = glyph(trace(main))
        if sh:
            glyphs["ic_asr_shadow"] = glyph(trace(sh))
    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(names)
    cmap = {PAD: "pad", SHADOW: "ic_asr_shadow"}
    cmap.update({CP[ev]: "ic_" + ev for ev in ORDER})
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    # left side bearing MUST equal each outline's xMin: renderers place the
    # outline from it (a 0 here would shift every icon to the left)
    glyf = fb.font["glyf"]
    lsb = {}
    for n in names:
        glyf[n].recalcBounds(glyf)
        lsb[n] = getattr(glyf[n], "xMin", 0) if glyf[n].numberOfContours else 0
    fb.setupHorizontalMetrics({n: (UPM, lsb[n]) for n in names})
    # The icon fills exactly one em: ascent = em, descent = 0, no line gap, so
    # however the renderer centres a line in its box, the icon fills the box.
    fb.setupHorizontalHeader(ascent=UPM, descent=0, lineGap=0)
    fb.setupOS2(sTypoAscender=UPM, sTypoDescender=0, sTypoLineGap=0, usWinAscent=UPM, usWinDescent=0,
                fsSelection=0x40 | 0x80, version=4, achVendID="NOOR", fsType=0,
                sxHeight=UPM // 2, sCapHeight=UPM)
    fb.setupNameTable({"familyName": "NoorWF Icons", "styleName": "Regular"})
    fb.setupPost()
    fb.save(out)


def rasterise(font, cp, size=96):
    """Glyph outline -> coverage at `size` px (filled at 2048 px, area-averaged)."""
    from fontTools.ttLib import TTFont
    f = TTFont(font) if isinstance(font, str) else font
    g = f["glyf"][f.getBestCmap()[cp]]
    canvas = np.zeros((UPM, UPM), np.uint8)
    if g.numberOfContours > 0:
        coords, ends = g.getCoordinates(f["glyf"])[:2]
        start = 0
        polys = []
        for e in ends:
            polys.append(np.array([(x, UPM - y) for x, y in coords[start:e + 1]], np.int32))
            start = e + 1
        cv2.fillPoly(canvas, polys, 255)          # contours never overlap: even-odd == non-zero
    return cv2.resize(canvas, (size, size), interpolation=cv2.INTER_AREA).astype(float) / 255


def check(font_path, png_dir, size=96):
    """Compare each glyph's outline with the PNG icon drawn from the same SVG,
    and check every glyph's side bearing (renderers position outlines by it)."""
    from fontTools.ttLib import TTFont
    font = TTFont(font_path)
    res = {}
    glyf = font["glyf"]
    for n in font.getGlyphOrder():
        g = glyf[n]
        if g.numberOfContours:
            g.recalcBounds(glyf)
            res.setdefault("lsb_mismatch", [])
            if font["hmtx"][n][1] != g.xMin:
                res["lsb_mismatch"].append(n)
    for ev in ORDER:
        def draw(cp):
            return rasterise(font, cp, size)
        g = draw(CP[ev])
        if ev == "asr":
            s = draw(SHADOW)
            g = g + (1 - g) * s * SHADOW_ALPHA         # 55 % layer drawn over the icon
        ref = np.array(Image.open(os.path.join(png_dir, "ic_%s.png" % ev)).convert("RGBA"))[:, :, 3] / 255.0
        d = np.abs(g - ref)
        inter = np.minimum(g, ref).sum()
        union = np.maximum(g, ref).sum()
        res[ev] = dict(mean_abs=float(d.mean()), max_abs=float(d.max()), iou=float(inter / union),
                       px_over_25=int((d > 0.25).sum()))
    return res


if __name__ == "__main__":
    build(sys.argv[1])
    if "--check" in sys.argv:
        import json
        print(json.dumps(check(sys.argv[1], sys.argv[sys.argv.index("--check") + 1]), indent=1))
