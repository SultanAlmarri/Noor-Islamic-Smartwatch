"""Builds the widgets' countdown fonts (0.9.11) from the app's own IBM Plex Sans Arabic Light.

On a home screen only Android's own Chronometer can tick every second without Noor running; it writes its digits in the
phone's language (Arabic-Indic digits on an Arabic phone), whatever Noor's own choice. So the widgets' countdown is set
in a font that draws every digit Android may write as the digits chosen in Noor:

- noor_count_latin.ttf / noor_count_arabic.ttf: Western (0–9) or Arabic-Indic (٠–٩) glyphs for every common set of
  digits (Western, Arabic-Indic, Persian, Devanagari, Bengali), with the colon, plus and minus;
- noor_count_latin_lead.ttf / noor_count_arabic_lead.ttf: the same, except that the last colon and the two digits after
  it (the seconds) are drawn blank (a contextual substitution, on by default). Laid over the first in a lighter colour,
  the countdown reads as on the home screen: "−1:23" bright and ":45" quieter.

Usage: python3 tools/widget/count_fonts.py   (needs fonttools)
"""
import io
import os

from fontTools.feaLib.builder import addOpenTypeFeaturesFromString
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "../../app/src/main/res/font")
SOURCE = os.path.join(FONTS, "plex_arabic_light.ttf")
LATIN = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]
ARABIC = [f"uni066{i}" for i in range(10)]
DIGIT_BLOCKS = [0x30, 0x660, 0x6F0, 0x966, 0x9E6]   # Western, Arabic-Indic, Persian, Devanagari, Bengali
OTHER = {0x3A: "colon", 0x2B: "plus", 0x2212: "minus", 0x2D: "hyphen", 0x20: "space"}


def build(digits, lead, out):
    font = TTFont(SOURCE)
    # Only what a countdown needs; no layout features from the source.
    opts = Options(); opts.layout_features = []; opts.name_IDs = ["*"]; opts.notdef_outline = True; opts.glyph_names = True
    sub = Subsetter(opts)
    sub.populate(glyphs=digits + list(OTHER.values()))
    sub.subset(font)
    cmap = {cp: name for cp, name in OTHER.items()}
    for start in DIGIT_BLOCKS:
        for i in range(10): cmap[start + i] = digits[i]
    for t in font["cmap"].tables:
        if t.isUnicode(): t.cmap = dict(cmap)
    if lead:
        # Blank twins of the digits and the colon, as wide as they are.
        glyf = font["glyf"]; hmtx = font["hmtx"]
        blanks = [name + ".blank" for name in digits + ["colon"]]
        order = list(font.getGlyphOrder()) + blanks
        font.setGlyphOrder(order)
        glyf.glyphOrder = order
        for name, blank in zip(digits + ["colon"], blanks):
            glyf.glyphs[blank] = TTGlyphPen(None).glyph()
            hmtx.metrics[blank] = (hmtx[name][0], 0)
        d = " ".join(digits); db = " ".join(n + ".blank" for n in digits)
        fea = f"""
languagesystem DFLT dflt;
languagesystem latn dflt;
languagesystem arab dflt;
@D = [{d}];
@DB = [{db}];
lookup HIDE_D {{ sub @D by @DB; }} HIDE_D;
lookup SHOW_D {{ sub @DB by @D; }} SHOW_D;
lookup HIDE_C {{ sub colon by colon.blank; }} HIDE_C;
lookup SHOW_C {{ sub colon.blank by colon; }} SHOW_C;
# Every colon with two digits after it is hidden with them; then those followed by another colon (the hours' and the
# minutes' groups) are shown again, so only the last group, the seconds, stays blank.
lookup HIDE_ALL {{ sub colon' lookup HIDE_C @D' lookup HIDE_D @D' lookup HIDE_D; }} HIDE_ALL;
lookup SHOW_INNER {{ sub colon.blank' lookup SHOW_C @DB' lookup SHOW_D @DB' lookup SHOW_D [colon colon.blank]; }} SHOW_INNER;
feature calt {{ lookup HIDE_ALL; lookup SHOW_INNER; }} calt;
feature ccmp {{ lookup HIDE_ALL; lookup SHOW_INNER; }} ccmp;
"""
        addOpenTypeFeaturesFromString(font, fea)
    name = font["name"]
    family = "Noor Countdown" + (" Lead" if lead else "")
    for rec in name.names:
        if rec.nameID in (1, 4, 16): rec.string = family
        if rec.nameID == 6: rec.string = family.replace(" ", "")
        # A modified font: its own name (Plex is a Reserved Font Name under the OFL); copyright and licence kept.
        if rec.nameID == 3: rec.string = f"{family}; Noor; modified from IBM Plex Sans Arabic Light 1.101"
    font.save(out)
    print(out, os.path.getsize(out), "bytes")


if __name__ == "__main__":
    for script, digits in (("latin", LATIN), ("arabic", ARABIC)):
        build(digits, False, os.path.join(FONTS, f"noor_count_{script}.ttf"))
        build(digits, True, os.path.join(FONTS, f"noor_count_{script}_lead.ttf"))
