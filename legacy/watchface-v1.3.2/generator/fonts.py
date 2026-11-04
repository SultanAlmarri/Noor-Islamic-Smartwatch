"""Build every watch-face font from SIL-OFL fonts in the Google Fonts repository.

For each catalogue entry two files are written to res/font:
  <id>.ttf    Western digits
  <id>i.ttf   ASCII digit code points mapped to the Arabic-Indic glyphs (٠-٩)
              and '%' to the Arabic percent sign, so every number the platform
              prints switches numeral system just by switching font.
Digits are made tabular (equal widths) so the clock does not jitter.
Clock fonts are subset to digits and punctuation; text fonts keep full Latin +
Arabic with shaping features.  Metrics used for layout are written to
build_assets/font/metrics.json.

Usage: python3 fonts.py <google-fonts-ofl-dir> <out-dir>
"""
import os, sys, json
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

OFL = sys.argv[1]
OUT = sys.argv[2]

# id, label, source file (relative to OFL dir), axis settings, role
CLOCK_FONTS = [
    ("c01", "Readex Thin", "readexpro/ReadexPro[HEXP,wght].ttf", {"wght": 200, "HEXP": 0}),
    ("c02", "Readex Bold", "readexpro/ReadexPro[HEXP,wght].ttf", {"wght": 600, "HEXP": 0}),
    ("c03", "Alexandria Light", "alexandria/Alexandria[wght].ttf", {"wght": 300}),
    ("c04", "Alexandria Black", "alexandria/Alexandria[wght].ttf", {"wght": 800}),
    ("c05", "Cairo ExtraLight", "cairo/Cairo[slnt,wght].ttf", {"wght": 200, "slnt": 0}),
    ("c06", "Cairo Bold", "cairo/Cairo[slnt,wght].ttf", {"wght": 700, "slnt": 0}),
    ("c07", "Tajawal Light", "tajawal/Tajawal-Light.ttf", None),
    ("c08", "IBM Plex Thin", "ibmplexsansarabic/IBMPlexSansArabic-Thin.ttf", None),
    ("c09", "Noto Kufi Light", "notokufiarabic/NotoKufiArabic[wght].ttf", {"wght": 300}),
    ("c10", "Changa Light", "changa/Changa[wght].ttf", {"wght": 300}),
    ("c11", "El Messiri", "elmessiri/ElMessiri[wght].ttf", {"wght": 500}),
    ("c12", "Reem Kufi", "reemkufi/ReemKufi[wght].ttf", {"wght": 400}),
    ("c13", "Amiri", "amiri/Amiri-Regular.ttf", None),
    ("c14", "Lalezar", "lalezar/Lalezar-Regular.ttf", None),
    ("c15", "Vazirmatn Thin", "vazirmatn/Vazirmatn[wght].ttf", {"wght": 100}),
    ("c16", "Lemonada", "lemonada/Lemonada[wght].ttf", {"wght": 300}),
    ("c17", "Baloo Bhaijaan", "baloobhaijaan2/BalooBhaijaan2[wght].ttf", {"wght": 500}),
    ("c18", "Marhey", "marhey/Marhey[wght].ttf", {"wght": 400}),
    ("c19", "Kufam", "kufam/Kufam[wght].ttf", {"wght": 400}),
    ("c20", "Almarai Light", "almarai/Almarai-Light.ttf", None),
]
TEXT_FONTS = [
    ("t01", "Readex Pro", "readexpro/ReadexPro[HEXP,wght].ttf", {"wght": 400, "HEXP": 0}),
    ("t02", "Alexandria", "alexandria/Alexandria[wght].ttf", {"wght": 400}),
    ("t03", "Cairo", "cairo/Cairo[slnt,wght].ttf", {"wght": 500, "slnt": 0}),
    ("t04", "Tajawal", "tajawal/Tajawal-Medium.ttf", None),
    ("t05", "IBM Plex Arabic", "ibmplexsansarabic/IBMPlexSansArabic-Regular.ttf", None),
    ("t06", "Noto Kufi", "notokufiarabic/NotoKufiArabic[wght].ttf", {"wght": 400}),
    ("t07", "El Messiri", "elmessiri/ElMessiri[wght].ttf", {"wght": 500}),
    ("t08", "Amiri", "amiri/Amiri-Regular.ttf", None),
    ("t09", "Almarai", "almarai/Almarai-Regular.ttf", None),
    ("t10", "Vazirmatn", "vazirmatn/Vazirmatn[wght].ttf", {"wght": 400}),
]

DIGIT_CHARS = "0123456789:+%−-  ./,٪،AMPصم٫٬۰۱۲۳۴۵۶۷۸۹" + "".join(chr(0x660 + i) for i in range(10))
LATIN = "".join(chr(c) for c in range(0x20, 0x7F)) + " − ·•–—°"
ARABIC = "".join(chr(c) for c in range(0x0600, 0x0700)) + "‌‍‎‏؜"


def glyph_for(font, cp):
    return font.getBestCmap().get(cp)


def shift_glyph(font, name, dx):
    if "glyf" in font:
        g = font["glyf"][name]
        if g.isComposite():
            for comp in g.components:
                comp.x += dx
        elif g.numberOfContours > 0:
            g.coordinates.translate((dx, 0))
        g.recalcBounds(font["glyf"])
    else:  # CFF outlines: shift via charstring is complex; adjust lsb only
        pass
    adv, lsb = font["hmtx"][name]
    font["hmtx"][name] = (adv, lsb + dx)


def make_tabular(font, names, width):
    done = set()
    for n in names:
        if n in done:
            continue
        done.add(n)
        adv, lsb = font["hmtx"][n]
        shift_glyph(font, n, int(round((width - adv) / 2.0)))
        font["hmtx"][n] = (width, font["hmtx"][n][1])


def digit_height(font, name):
    if "glyf" in font:
        g = font["glyf"][name]
        g.recalcBounds(font["glyf"])
        return g.yMax - g.yMin
    return font["head"].unitsPerEm * 0.7


def normalise_v(font, cmap, digits, percent):
    """One numeral system per file, whatever digits the platform or a
    complication sends (Western 0-9, Arabic-Indic U+0660-0669, Extended
    Arabic-Indic U+06F0-06F9) and whatever separators come with them."""
    comma, period = cmap.get(0x2C), cmap.get(0x2E)
    for t in font["cmap"].tables:
        if not t.isUnicode():
            continue
        for i in range(10):
            for base in (0x30, 0x660, 0x6F0):
                t.cmap[base + i] = digits[i]
        if comma:
            t.cmap[0x66C] = comma               # Arabic thousands separator -> ","
        if period:
            t.cmap[0x66B] = period              # Arabic decimal separator -> "."
        if percent:
            t.cmap[0x25] = percent
            t.cmap[0x66A] = percent


PAD_CP = 0xE000          # v1.3: empty, zero-advance glyph (invisible table padding)
NBSP_CP = 0x00A0         # v1.3.2: no-break space re-drawn 1/32 em wide (positions the countdown)


def add_pad_glyph(font):
    """Clock fonts only (nothing else on the face draws these characters):
      U+E000  zero advance - pads the countdown's fixed-width subText() tables
      U+00A0  no-break space, 1/32 em - a run of them moves the countdown next
              to the prayer name / time by an exact, known distance.
    v1.3.1 used the private-use U+E001 for the spacing; on the watch it was
    not drawn with this font and showed foreign symbols instead of a blank.
    A no-break space is a standard character: any font draws it blank."""
    from fontTools.ttLib.tables._g_l_y_f import Glyph
    upm = font["head"].unitsPerEm
    for name, cp, adv in (("noorpad", PAD_CP, 0), ("noorspace", NBSP_CP, upm // 32)):
        order = font.getGlyphOrder()
        if name not in order:
            font.setGlyphOrder(order + [name])
            font["glyf"].glyphs[name] = Glyph()
            font["glyf"].glyphOrder = font.getGlyphOrder()
            font["hmtx"].metrics[name] = (adv, 0)
        for t in font["cmap"].tables:
            if t.isUnicode():
                t.cmap[cp] = name


def build_one(fid, label, src, axes, text_set):
    f = TTFont(os.path.join(OFL, src))
    if axes and "fvar" in f:
        f = instancer.instantiateVariableFont(f, axes, updateFontNames=False)
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    opts.glyph_names = True
    opts.hinting = False
    sub = subset.Subsetter(opts)
    sub.populate(text=text_set)
    sub.subset(f)
    cmap = f.getBestCmap()
    latin = [cmap[0x30 + i] for i in range(10)]
    indic = [cmap[0x660 + i] for i in range(10)]
    tab_w = max(f["hmtx"][n][0] for n in latin + indic)
    make_tabular(f, latin + indic, tab_w)
    if text_set is DIGIT_CHARS:
        add_pad_glyph(f)
    upem = f["head"].unitsPerEm
    h0 = digit_height(f, latin[0])
    for rec in f["name"].names:
        if rec.nameID in (1, 4, 16):
            rec.string = "NoorWF " + label
    tmp = os.path.join(OUT, fid + ".base.tmp")
    if "post" in f:          # keep real glyph names (post format 3 would rename
        f["post"].formatType = 2.0      # glyphs after digits are re-mapped)
        f["post"].extraNames = []
        f["post"].mapping = {}
    f.save(tmp)
    path = os.path.join(OUT, fid + ".ttf")
    for out, use_indic, suffix in ((path, False, ""), (os.path.join(OUT, fid + "i.ttf"), True, " Indic")):
        v = TTFont(tmp)
        vc = v.getBestCmap()
        lat = [vc[0x30 + i] for i in range(10)]
        ind = [vc[0x660 + i] for i in range(10)]
        normalise_v(v, vc, ind if use_indic else lat, (vc.get(0x66A) or vc.get(0x25)) if use_indic else vc.get(0x25))
        for rec in v["name"].names:
            if rec.nameID in (1, 4, 16):
                rec.string = "NoorWF " + label + suffix
        v.save(out)
    os.remove(tmp)
    return dict(id=fid, label=label, source=src, axes=axes, upem=upem, tab=tab_w / upem,
                digit_h=h0 / upem, asc=f["hhea"].ascent / upem, desc=-f["hhea"].descent / upem,
                bytes=os.path.getsize(path))


def main():
    os.makedirs(OUT, exist_ok=True)
    for fn in os.listdir(OUT):
        if fn.endswith(".ttf") and fn != "icons.ttf":      # icons.ttf: icon_font.py
            os.remove(os.path.join(OUT, fn))
    meta = {"clock": [], "text": []}
    for fid, label, src, axes in CLOCK_FONTS:
        meta["clock"].append(build_one(fid, label, src, axes, DIGIT_CHARS))
    for fid, label, src, axes in TEXT_FONTS:
        meta["text"].append(build_one(fid, label, src, axes, LATIN + ARABIC))
    json.dump(meta, open(os.path.join(OUT, "metrics.json"), "w"), indent=1)
    tot = sum(m["bytes"] * 2 for k in meta for m in meta[k])
    print("built %d clock + %d text fonts, %.0f KB total" % (len(meta["clock"]), len(meta["text"]), tot / 1024))


if __name__ == "__main__":
    main()
