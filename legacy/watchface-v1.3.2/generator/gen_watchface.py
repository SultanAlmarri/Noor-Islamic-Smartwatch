"""Generate the complete Watch Face Format (v4) resource set.

Usage: python3 gen_watchface.py <android-module-src-main-dir>

Writes res/raw/watchface.xml, res/xml/watch_face_info.xml, English + Arabic
strings, option pictures, and copies fonts / icons.  The prayer-time formulas
come from engine.py, the same code the tests evaluate.

v1.1: text is serialised with NO whitespace inside <Template>/<Font>
(the renderer draws it); one shared countdown / time block driven by the
current event; 20 clock fonts, 10 text fonts, 24 accent and 12 clock colours;
a labelled picture for every option.
"""
import os, sys, shutil, json, math
import xml.etree.ElementTree as ET
import uharfbuzz as hb

sys.path.insert(0, os.path.dirname(__file__))
import data, engine, option_icons

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "build_assets")
FONT_META = json.load(open(os.path.join(ASSETS, "font", "metrics.json")))
CLOCK_FONTS = FONT_META["clock"]
TEXT_FONTS = FONT_META["text"]
REF_DIGIT_H = 0.72                      # Readex Pro digit height (em): size reference
W = H = 450
CX = W // 2
WFF_VERSION = 4
BIAS = 2000                             # carrier values are minutes + BIAS (always positive)
# v1.3.2: font files carry the release's version code in their names
# (c01_v7.ttf ...), so a watch can never keep drawing with an older copy of a
# font that has the same name.
FONT_TAG = "_v" + os.environ.get("VERSION_CODE", "0")

# ------------------------------------------------------------------ palette
GREY = "#FF9AA3A0"
VALUE = "#FFD5DBD8"
ACCENT = "[CONFIGURATION.accent]"
CLOCKCOL = "[CONFIGURATION.clockcolor]"
ACCENTS = [  # id, English, Arabic, colour
    ("emerald", "Emerald", "زمردي", "#FF4FD69C"), ("sage", "Sage", "مريمي", "#FF8FC7A6"),
    ("mint", "Mint", "نعناعي", "#FF7FE8C4"), ("jade", "Deep jade", "يشم", "#FF2FB37E"),
    ("olive", "Olive", "زيتوني", "#FFA8B865"), ("lime", "Lime", "ليموني", "#FFB8E05A"),
    ("teal", "Teal", "أزرق مخضر", "#FF2EC4B6"), ("turquoise", "Turquoise", "فيروزي", "#FF40E0D0"),
    ("sky", "Sky blue", "أزرق سماوي", "#FF6EC6FF"), ("ocean", "Ocean", "محيطي", "#FF3D8BFF"),
    ("indigo", "Indigo", "نيلي", "#FF7C83FD"), ("lavender", "Lavender", "خزامى", "#FFB7A4F5"),
    ("violet", "Violet", "بنفسجي", "#FFA66CFF"), ("orchid", "Orchid", "أوركيد", "#FFE07BE0"),
    ("rose", "Rose", "وردي", "#FFFF7AA2"), ("coral", "Coral", "مرجاني", "#FFFF7F6B"),
    ("red", "Ruby", "ياقوتي", "#FFFF5A5F"), ("amber", "Amber", "كهرماني", "#FFFFB547"),
    ("orange", "Orange", "برتقالي", "#FFFF9440"), ("gold", "Gold", "ذهبي", "#FFD8B65C"),
    ("sand", "Sand", "رملي", "#FFE3C79A"), ("copper", "Copper", "نحاسي", "#FFD08B5B"),
    ("silver", "Silver", "فضي", "#FFC9D1CE"), ("white", "White", "أبيض", "#FFFFFFFF"),
]
CLOCK_COLOURS = [
    ("white", "White", "أبيض", "#FFF4F6F5"), ("warm", "Warm white", "أبيض دافئ", "#FFFFF3E0"),
    ("silver", "Silver", "فضي", "#FFC9D1CE"), ("grey", "Soft grey", "رمادي", "#FFA7AFAC"),
    ("mint", "Mint", "نعناعي", "#FFB9F3DA"), ("emerald", "Emerald", "زمردي", "#FF4FD69C"),
    ("sky", "Sky", "سماوي", "#FFBFE3FF"), ("lavender", "Lavender", "خزامى", "#FFD9CFFF"),
    ("rose", "Rose", "وردي", "#FFFFD0DC"), ("gold", "Gold", "ذهبي", "#FFE8C872"),
    ("sand", "Sand", "رملي", "#FFF0DDB8"), ("amber", "Amber", "كهرماني", "#FFFFC46B"),
]

STR_EN, STR_AR = {}, {}
ICON_JOBS = []                          # (drawable name, lines, font path)


def S(key, en, ar):
    STR_EN[key] = en
    STR_AR[key] = ar
    return key


# ------------------------------------------------------------- measurements
_hb = {}


def base_family(family):
    """Font family as built (c01, c01i, t03, icons) - without the release tag."""
    return family.split("_v")[0]


def text_width(text, family, size, letter_spacing=0.0):
    if not text:
        return 0.0
    family = base_family(family)
    if family not in _hb:
        blob = hb.Blob.from_file_path(os.path.join(ASSETS, "font", family + ".ttf"))
        face = hb.Face(blob)
        _hb[family] = (hb.Font(face), face.upem)
    font, upem = _hb[family]
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(font, buf, {})
    return sum(p.x_advance for p in buf.glyph_positions) * size / upem + letter_spacing * size * len(text)


def norm(size, meta):
    """Font size that gives every font the same digit height."""
    return round(size * REF_DIGIT_H / meta["digit_h"], 1)


def fam(meta, digits):
    return meta["id"] + ("i" if digits == "arab" else "")


# ------------------------------------------------------------- XML helpers
def el(parent, tag, **attrs):
    e = ET.SubElement(parent, tag) if parent is not None else ET.Element(tag)
    for k, v in attrs.items():
        if v is not None:
            e.set(k.rstrip("_"), str(v))
    return e


def cond(parent, branches, default=None):
    """branches: [(name, expression, builder)]; first true Compare wins."""
    c = el(parent, "Condition")
    ex = el(c, "Expressions")
    for name, expr, _ in branches:
        el(ex, "Expression", name=name).text = expr
    for name, _, build in branches:
        build(el(c, "Compare", expression=name))
    if default is not None:
        default(el(c, "Default"))
    return c


def by_config(parent, cfg, options, build):
    """One branch per option of a ListConfiguration; options[0] is the Default."""
    if len(options) == 1:
        build(parent, options[0][1])
        return
    branches = [("o_%s" % oid, '[CONFIGURATION.%s] == "%s"' % (cfg, oid),
                 (lambda o: lambda p: build(p, o))(o)) for oid, o in options[1:]]
    cond(parent, branches, lambda p: build(p, options[0][1]))


def by_lang(parent, build):
    cond(parent, [("lang_ar", '([CONFIGURATION.langnum] == "ar_arab") || ([CONFIGURATION.langnum] == "ar_latn")',
                   lambda p: build(p, "ar"))], lambda p: build(p, "en"))


def by_digits(parent, build):
    cond(parent, [("dig_arab", '([CONFIGURATION.langnum] == "ar_arab") || ([CONFIGURATION.langnum] == "en_arab")',
                   lambda p: build(p, "arab"))], lambda p: build(p, "latn"))


def by_clockfont(parent, build):
    by_config(parent, "clockfont", [(m["id"], m) for m in CLOCK_FONTS], build)


def by_textfont(parent, build):
    by_config(parent, "textfont", [(m["id"], m) for m in TEXT_FONTS], build)


def part_text(parent, x, y, w, h, family, size, color, text=None, template=None, params=(),
              align="CENTER", letter_spacing=None, ambient=None, alpha=None, name=None):
    pt = el(parent, "PartText", x=int(round(x)), y=int(round(y)), width=max(1, int(round(w))),
            height=int(round(h)), alpha=alpha, name=name)
    for tgt, val in (ambient or {}).items():
        el(pt, "Variant", mode="AMBIENT", target=tgt, value=val)
    t = el(pt, "Text", align=align, ellipsis="FALSE")
    f = el(t, "Font", family=family, size=size, color=color, letterSpacing=letter_spacing)
    if template is not None:
        tp = el(f, "Template")
        tp.text = template              # format string: no surrounding whitespace!
        for p in params:
            el(tp, "Parameter", expression=p)
    else:
        f.text = text
    return pt


def part_image(parent, x, y, w, h, resource, tint=None, ambient=None):
    pi = el(parent, "PartImage", x=int(round(x)), y=int(round(y)), width=int(round(w)),
            height=int(round(h)), tintColor=tint)
    for tgt, val in (ambient or {}).items():
        el(pi, "Variant", mode="AMBIENT", target=tgt, value=val)
    el(pi, "Image", resource=resource)
    return pi


HIDE_AMBIENT = {"alpha": "0"}


# =========================================================== user settings
def user_configurations(root):
    uc = el(root, "UserConfigurations")

    def lst(cid, label_en, label_ar, default, options):
        """options: (id, en, ar, icon_lines, icon_font_path)"""
        lc = el(uc, "ListConfiguration", id=cid, displayName=S("cfg_" + cid, label_en, label_ar),
                screenReaderText="cfg_" + cid, defaultValue=default)
        for oid, en, ar, lines, fpath in options:
            key = S("opt_%s_%s" % (cid, oid), en, ar)
            icon = "opt_%s_%s" % (cid, oid)
            ICON_JOBS.append((icon, lines, fpath))
            el(lc, "ListOption", id=oid, displayName=key, screenReaderText=key, icon=icon)

    def colours(cid, label_en, label_ar, items):
        cc = el(uc, "ColorConfiguration", id=cid, displayName=S("cfg_" + cid, label_en, label_ar),
                screenReaderText="cfg_" + cid, defaultValue=items[0][0])
        for oid, en, ar, col in items:
            key = S("opt_%s_%s" % (cid, oid), en, ar)
            el(cc, "ColorOption", id=oid, displayName=key, screenReaderText=key, colors=col)

    fdir = os.path.join(ASSETS, "font")
    lst("clockfont", "Clock font", "خط الساعة", CLOCK_FONTS[0]["id"],
        [(m["id"], m["label"], m["label"], ["12:34", m["label"]], os.path.join(fdir, m["id"] + ".ttf"))
         for m in CLOCK_FONTS])
    lst("textfont", "Text font", "خط النصوص", TEXT_FONTS[0]["id"],
        [(m["id"], m["label"], m["label"], ["Aa ع", m["label"]], os.path.join(fdir, m["id"] + ".ttf"))
         for m in TEXT_FONTS])
    colours("accent", "Accent colour", "لون التمييز", ACCENTS)
    colours("clockcolor", "Clock colour", "لون الساعة", CLOCK_COLOURS)
    lst("langnum", "Language & numerals", "اللغة والأرقام", "en_latn",
        [("en_latn", "English \u00b7 0123", "English \u00b7 0123", ["EN", "123"], None),
         ("ar_arab", "العربية \u00b7 ٠١٢٣", "العربية \u00b7 ٠١٢٣", ["ع", "١٢٣"], None),
         ("ar_latn", "العربية \u00b7 0123", "العربية \u00b7 0123", ["ع", "123"], None),
         ("en_arab", "English \u00b7 ٠١٢٣", "English \u00b7 ٠١٢٣", ["EN", "١٢٣"], None)])
    lst("city", "Location", "الموقع", data.CITIES[0][0],
        [(c[0], c[1], c[2], [c[1], c[2]], None) for c in data.CITIES])
    short = {"dubai": ["IACAD", "Dubai 18.2°"], "mwl": ["MWL", "World League"], "uqu": ["Umm", "al-Qura"],
             "egy": ["Egypt", "19.5° / 17.5°"], "kar": ["Karachi", "18° / 18°"], "isna": ["ISNA", "15° / 15°"],
             "kwt": ["Kuwait", "18° / 17.5°"], "qat": ["Qatar", "18° + 90 min"], "sgp": ["MUIS", "Singapore"],
             "tur": ["Diyanet", "Türkiye"], "teh": ["Tehran", "17.7° / 14°"]}
    lst("method", "Calculation method", "طريقة الحساب", data.METHODS[0]["id"],
        [(m["id"], m["en"], m["ar"], short[m["id"]], None) for m in data.METHODS])
    hl = {"angle": ("Angle-based", "حسب الزاوية", "angle"), "mid": ("Middle of night", "منتصف الليل", "1/2 night"),
          "7th": ("One-seventh of night", "سُبع الليل", "1/7 night")}
    asr_opts = []
    for a_id, a_en, a_ar, a_ic in (("std", "Asr standard", "العصر: الجمهور", "Standard"), ("han", "Asr Hanafi", "العصر: حنفي", "Hanafi")):
        for h_id, (h_en, h_ar, h_ic) in hl.items():
            asr_opts.append(("%s_%s" % (a_id, h_id), "%s \u00b7 %s" % (a_en, h_en), "%s \u00b7 %s" % (a_ar, h_ar), [a_ic, h_ic], None))
    lst("asrhl", "Asr & high latitude", "العصر وخطوط العرض العليا", "std_angle", asr_opts)
    names = {"fajr": ("Fajr", "الفجر"), "sunrise": ("Shuruq", "الشروق"), "dhuhr": ("Dhuhr", "الظهر"),
             "asr": ("Asr", "العصر"), "maghrib": ("Maghrib", "المغرب"), "isha": ("Isha", "العشاء")}
    for ev in data.EVENTS:
        opts = []
        for v in data.ADJ_RANGE:
            lab = "0 min" if v == 0 else ("%+d min" % v).replace("-", "−")
            labar = "٠ دقيقة" if v == 0 else ("%+d دقيقة" % v).replace("-", "−")
            big = "0" if v == 0 else ("%+d" % v).replace("-", "−")
            opts.append((data.adj_id(v), lab, labar, [big, "min"], None))
        lst("adj_" + ev, "%s adjustment" % names[ev][0], "تعديل %s" % names[ev][1], "p0", opts)
    for cid, en, ar in SIZE_SETTINGS:
        opts = []
        for n in SIZE_STEPS:
            for a in AOD_STEPS:
                oid = "n%da%d" % (n, a)
                opts.append((oid, "%d%% \u00b7 Always-On %d%%" % (n, a), "%d٪ \u00b7 الشاشة الدائمة %d٪" % (n, a),
                             ["%d%%" % n, "AOD %d%%" % a], None))
        lst(cid, en, ar, "n100a100", opts)


SIZE_STEPS = [80, 90, 100, 110, 120]          # normal display
AOD_STEPS = [80, 100, 120]                    # Always-On Display
SIZE_SETTINGS = [("size_clock", "Clock size", "حجم الساعة"),
                 ("size_prayer", "Prayer name & time size", "حجم اسم الصلاة ووقتها"),
                 ("size_countdown", "Countdown size", "حجم العد التنازلي"),
                 ("size_steps", "Steps size", "حجم الخطوات"),
                 ("size_hr", "Heart rate size", "حجم نبض القلب"),
                 ("size_battery", "Battery size", "حجم البطارية")]


def size_chain(cid, ambient):
    """Scale factor for a size setting (normal or Always-On)."""
    pairs = [("n%da%d" % (n, a), (a if ambient else n) / 100.0) for n in SIZE_STEPS for a in AOD_STEPS]
    expr = "1.0"
    for oid, f in reversed(pairs):
        if oid == "n100a100":
            continue
        expr = '(([CONFIGURATION.%s] == "%s") ? %s : %s)' % (cid, oid, ("%.2f" % f), expr)
    return expr


def scaled(parent, name, cid, x, y, w, h, px=0.5, py=0.5):
    """Group whose content scales about (px, py) by a size setting; the
    Always-On variant uses the setting's AOD factor."""
    g = el(parent, "Group", name=name, x=int(x), y=int(y), width=int(w), height=int(h), pivotX=px, pivotY=py)
    for axis in ("scaleX", "scaleY"):
        el(g, "Transform", target=axis, value=size_chain(cid, False))
    for axis in ("scaleX", "scaleY"):
        el(g, "Variant", mode="AMBIENT", target=axis, value=size_chain(cid, True))
    return g


# ============================================================ prayer engine
# key, event, day offset, minutes shift into today's frame, Reference name
CARRIERS = [
    ("isha_prev", "isha", -1, -1440, "e0"), ("fajr", "fajr", 0, 0, "e1"), ("sunrise", "sunrise", 0, 0, "e2"),
    ("dhuhr", "dhuhr", 0, 0, "e3"), ("asr", "asr", 0, 0, "e4"), ("maghrib", "maghrib", 0, 0, "e5"),
    ("isha", "isha", 0, 0, "e6"), ("fajr_next", "fajr", 1, 1440, "e7"),
]
REF = {c[0]: c[4] for c in CARRIERS}
UNAVAILABLE = -99999
NOW = "[SECONDS_IN_DAY]"


def engine_group(scene):
    """Invisible carriers: each evaluates one prayer time as (minutes after
    today's local midnight + BIAS) into the width of a 1-px transparent
    PartDraw anchored on-screen, and publishes it as a Reference."""
    P = engine.Params()
    g = el(scene, "Group", name="prayer_engine", x=0, y=0, width=W, height=H)
    for key, ev, day, shift, ref in CARRIERS:
        minutes = engine.event_seconds(P, ev, day) / 60
        pd = el(g, "PartDraw", name="c_" + key, x=0, y=0, width=1, height=1)
        el(pd, "Transform", target="width", value="(%s + %d)" % (minutes.wff(), BIAS + shift))
        el(pd, "Reference", name=ref, source="width", defaultValue=UNAVAILABLE)
        r = el(pd, "Rectangle", x=0, y=0, width=1, height=1)
        el(r, "Fill", color="#00000000")


# v1.3 - ONE selection drives the whole prayer section.
# On the watch, a <Condition> that reads [REFERENCE.*] is not refreshed (v1.2.1
# froze on its Fajr branch), while Template parameters that read the same
# References update every second.  So the event name, its icon, its time and
# its countdown are now ALL Template parameters built from pick() below: the
# same comparisons, in the same order, for every piece.  No Condition and no
# Transform reads a Reference any more (a test enforces this).
def still_on(ref):
    """The event stays on screen until 30 minutes after its time."""
    return "(%s<([REFERENCE.%s]-%d)*60)" % (NOW, ref, BIAS - 30)


def pick(values):
    """values[i] belongs to CARRIERS[i]; the first event whose time + 30 min
    is still ahead is the current one (the last carrier, tomorrow's Fajr,
    closes the chain)."""
    e = str(values[-1])
    for c, v in reversed(list(zip(CARRIERS[:-1], values[:-1]))):
        e = "(%s?%s:%s)" % (still_on(c[4]), v, e)
    return e


CUR_B = pick(["[REFERENCE.%s]" % c[4] for c in CARRIERS])   # current event: minutes + BIAS
X = "(%s-(%s-%d)*60)" % (NOW, CUR_B, BIAS)                   # seconds since it (negative before)
TOD = "((%s-%d)%%1440)" % (CUR_B, BIAS - 1440)                # its minute of the day, 0..1439


def engine_guard():
    """Plausibility guard WITHOUT References (Conditions must not read them):
    today's Dhuhr, computed inline, must fall between 08:00 and 18:00 local -
    otherwise the chosen location does not match the watch's time zone."""
    d = engine.event_seconds(engine.Params(), "dhuhr", 0).wff()
    return "abs(%s - 46800) > 18000" % d


# ================================================================== layout
# v1.2: bigger elements that fill the circle; the divider line is gone.
Y_DATE, H_DATE, DATE_SIZE = 32, 40, 25
Y_CLOCK, H_CLOCK, CLOCK_SIZE = 66, 136, 128
Y_STATS, SLOT_W, SLOT_H = 342, 104, 62
COLS = [CX - 94, CX, CX + 94]
# v1.3 prayer section: [icon | name over time] beside the countdown.
# English left-to-right; Arabic is the mirror image (icon at the right).
Y_PC = 258                          # vertical centre of the whole section
ICON = 48
P_GAP = 10                          # icon -> text
P_TEXT_W_EN, P_TEXT_W_AR = 148, 120  # name / time column
CD_FIT = 166                        # widest countdown ("−10:59") at 100 %
# v1.3.1: the countdown sits CD_GAP px after the name/time block instead of in
# a centred column of its own.  The block's width depends on the event (long
# names such as MAGHRIB) and on the hour of its time (12:05 is wider than
# 5:32); the countdown text starts with a run of 1/32-em spacing glyphs whose
# length the same pick() chooses, so the gap stays the same for every event.
CD_GAP = 16
CD_TYPICAL = "−1:07"                # the composite is centred for this width
HOUR2_EVENTS = {"dhuhr"}            # scheduled hour usually has two digits (11, 12)
SPACE = "\u00a0"                    # no-break space, 1/32 em wide in the clock fonts (fonts.py)
NAME_SIZE, H_NAME, NAME_DY = 30, 40, -19
TIME_SIZE, H_TIME, TIME_DY = 25, 32, 19
CD_SIZE, H_CD = 60, 72
STAT_ICON, STAT_SIZE = 28, 25
WEEK_EN = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
WEEK_AR = ["الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"]
AMB_DIM = {"alpha": "170"}


def lookup(words, index_expr):
    """subText() over the concatenated words, picking words[index-1]."""
    starts, pos = [], 0
    for w_ in words:
        starts.append(pos)
        pos += len(w_)
    ends = [s + len(w_) for s, w_ in zip(starts, words)]

    def chain(vals):
        e = str(vals[-1])
        for i in range(len(vals) - 2, -1, -1):
            e = "((%s == %d) ? %d : %s)" % (index_expr, i + 1, vals[i], e)
        return e
    return 'subText("%s", %s, %s)' % ("".join(words), chain(starts), chain(ends))


def fit(size, text, family, max_w, ls=0.0):
    w_ = text_width(text, family, size, ls)
    return round(size * max_w / w_, 1) if w_ > max_w else size


def date_line(scene):
    g = el(scene, "Group", name="date", x=0, y=Y_DATE, width=W, height=H_DATE)

    def build(p, meta, lang, dg):
        if lang == "ar":
            tmpl, wd, longest = "%s، %s/%s/%s", lookup(WEEK_AR, "[DAY_OF_WEEK]"), "الأربعاء، 28/12/2026"
        else:
            tmpl, wd, longest = "%s, %s/%s/%s", lookup(WEEK_EN, "[DAY_OF_WEEK]"), "Wednesday, 28/12/2026"
        size = fit(norm(DATE_SIZE, meta), longest, meta["id"], 292)
        part_text(p, 20, 0, W - 40, H_DATE, fam(meta, dg), size, GREY, template=tmpl,
                  params=[wd, "[DAY]", "[MONTH]", "[YEAR]"], ambient=AMB_DIM, name="date")
    by_textfont(g, lambda p, m: by_lang(p, lambda q, lang: by_digits(q, lambda r, dg: build(r, m, lang, dg))))


# Numbers come only from integer data sources and are printed digit by digit,
# so the system language can never format them; the font chosen by the
# Language & numerals setting then draws every digit in one numeral system.
MIN_T, MIN_U = "round(floor([MINUTE] / 10.0))", "([MINUTE] % 10)"
SEC_T, SEC_U = "round(floor([SECOND] / 10.0))", "([SECOND] % 10)"


def clock(scene):
    sg = scaled(scene, "s_clock", "size_clock", 0, Y_CLOCK, W, H_CLOCK)

    def build(p, meta):
        size = fit(norm(CLOCK_SIZE, meta), "12:59", meta["id"], 290)
        w_big2 = text_width("12:59", meta["id"], size)
        wd = text_width("0", meta["id"], size)
        ssize = round(size * 0.36, 1)
        asize = round(size * 0.2, 1)
        w_col = max(text_width("00", meta["id"], ssize), text_width("PM", meta["id"], asize))
        gap = 10
        x_right = CX - (w_big2 + gap + w_col) / 2 + w_big2
        dh = REF_DIGIT_H * size * meta["digit_h"] / REF_DIGIT_H
        top = (H_CLOCK - dh) / 2
        g = el(p, "Group", name="clock_" + meta["id"], x=0, y=0, width=W, height=H_CLOCK)
        el(g, "Transform", target="x", value="([HOUR_1_12] < 10) ? %.1f : 0" % (-wd / 2))
        box_l = x_right - w_big2 - 40
        sec_h, am_h = ssize * 1.25, asize * 1.4
        sec_y = top + 0.70 * dh - sec_h / 2
        am_y = top + 0.22 * dh - am_h / 2

        def digits(q, dg):
            part_text(q, box_l, 0, w_big2 + 40, H_CLOCK, fam(meta, dg), size, CLOCKCOL,
                      template="%s:%s%s", params=["[HOUR_1_12]", MIN_T, MIN_U], align="END",
                      ambient={"alpha": "235"}, name="clk")
            part_text(q, x_right + gap, sec_y, w_col + 30, sec_h, fam(meta, dg), ssize, CLOCKCOL,
                      template="%s%s", params=[SEC_T, SEC_U], align="START", ambient=HIDE_AMBIENT,
                      alpha=215, name="sec")
        by_digits(g, digits)

        def ampm(q, lang):
            if lang == "ar":
                expr = 'subText("صم", [AMPM_STATE], [AMPM_STATE] + 1)'
            else:
                expr = 'subText("AMPM", [AMPM_STATE] * 2, [AMPM_STATE] * 2 + 2)'
            part_text(q, x_right + gap, am_y, w_col + 30, am_h, meta["id"], asize, ACCENT,
                      template="%s", params=[expr], align="START",
                      letter_spacing=0.06 if lang == "en" else None, ambient=AMB_DIM, name="ampm")
        by_lang(g, ampm)
    by_clockfont(sg, build)


EV_ORDER = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"]
PAD = ""                                    # empty, zero-width glyph (fonts.py)
ICON_STR = "".join(chr(0xE001 + i) for i in range(6))            # icons.ttf (icon_font.py)
SHADOW_STR = "".join(chr(0xE008) if ev == "asr" else PAD for ev in EV_ORDER)
# Countdown pieces, each picked with subText() from a fixed-width table:
#   HOURS[3h:3h+3]   ""  for h = 0, else "h:"          (left-padded with PAD)
#   TENS[k:k+1]      ""  for k = 0, else minute tens   (k = whole 10-minute steps)
HOURS = "".join(PAD * (3 - len(s)) + s for s in [""] + ["%d:" % h for h in range(1, 30)])
TENS = "".join(PAD if k == 0 else str(k % 6) for k in range(30 * 6))


def ev_index(offset=0):
    return pick([EV_ORDER.index(c[1]) + offset for c in CARRIERS])


def name_param(lang):
    words = [data.EVENT_NAMES[ev][1 if lang == "ar" else 0] for ev in EV_ORDER]
    span, pos = {}, 0
    for ev, w_ in zip(EV_ORDER, words):
        span[ev] = (pos, pos + len(w_))
        pos += len(w_)
    return 'subText("%s", %s, %s)' % ("".join(words), pick([span[c[1]][0] for c in CARRIERS]),
                                      pick([span[c[1]][1] for c in CARRIERS]))


def icon_param(table):
    return 'subText("%s", %s, %s)' % (table, ev_index(0), ev_index(1))


def time_params(lang):
    """Scheduled time of the current event, h:mm + AM/PM, integer digits only."""
    h12 = "round((floor(%s/60)+11)%%12+1)" % TOD
    mt = "round(floor((%s%%60)/10))" % TOD
    mu = "round(%s%%10)" % TOD
    pm = "round(floor(%s/720))" % TOD
    ap = ('subText("صم", %s, %s+1)' % (pm, pm)) if lang == "ar" else \
         ('subText("AMPM", %s*2, %s*2+2)' % (pm, pm))
    return [h12, mt, mu, ap]


def countdown_params():
    """v1.3, normal display and Always-On alike - no seconds:
         below 1 h  [-|+]M        e.g. −7      +0     +29
         from 1 h   [-|+]H:MM     e.g. −1:07   −10:00
    Hours never padded; minutes two digits only when hours are shown; the
    seconds are dropped (−1:07:35 shows −1:07)."""
    neg = "(%s<0)" % X
    a = "abs(%s)" % X
    h = "round(floor(%s/3600))" % a
    k = "round(floor(%s/600))" % a
    units = "round(floor(%s/60)%%10)" % a
    return ['subText("−+", %s ? 0 : 1, %s ? 1 : 2)' % (neg, neg),
            'subText("%s", %s*3, %s*3+3)' % (HOURS, h, h),
            'subText("%s", %s, %s+1)' % (TENS, k, k),
            units]


def name_size(m, lang):
    ar = lang == "ar"
    return fit(norm(NAME_SIZE + (2 if ar else 0), m), "المغرب" if ar else "MAGHRIB", m["id"],
               (P_TEXT_W_AR if ar else P_TEXT_W_EN) - 4, 0.0 if ar else 0.08)


def time_size(m, lang):
    """English: every text font draws "12:59 PM" exactly as wide as the
    default font does, so the countdown can be placed next to it whatever
    font is chosen.  Arabic: as before (fits its column)."""
    if lang == "ar":
        return fit(norm(TIME_SIZE, m), "12:59 م", m["id"], P_TEXT_W_AR - 4)
    ref = TEXT_FONTS[0]
    target = text_width("12:59 PM", ref["id"], norm(TIME_SIZE, ref))
    s0 = norm(TIME_SIZE, m)
    return round(s0 * target / text_width("12:59 PM", m["id"], s0), 2)


AM_EVENTS = {"fajr", "sunrise"}     # their time is always morning; the others afternoon/evening


def _time_w(m, lang, two_digit, ev=None):
    am, pm = (" ص", " م") if lang == "ar" else (" AM", " PM")
    sfx = [am] if ev in AM_EVENTS else [pm] if ev else [am, pm]
    return max(text_width(("12:59" if two_digit else "9:59") + x, m["id"], time_size(m, lang)) for x in sfx)


def block_widths(lang):
    """Per event: the widest name/time block over every text font (px, 100 %),
    with the hour digits that event normally has; plus the extra width of a
    two-digit hour where an event has one only rarely (10 PM - 12 AM)."""
    ar = lang == "ar"
    out, extra = {}, 0.0
    for ev in EV_ORDER:
        word = data.EVENT_NAMES[ev][1 if ar else 0]
        w = 0.0
        for m in TEXT_FONTS:
            w = max(w, text_width(word, m["id"], name_size(m, lang), 0.0 if ar else 0.08),
                    _time_w(m, lang, ev in HOUR2_EVENTS, ev))
            extra = max(extra, _time_w(m, lang, True) - _time_w(m, lang, False))
        out[ev] = w
    return out, extra


BLOCK_W = {lang: block_widths(lang) for lang in ("en", "ar")}


def cd_size(m):
    return fit(norm(CD_SIZE, m), "−10:59", m["id"], CD_FIT)


def space_param(m, lang):
    """The run of spacing glyphs before (English) or after (Arabic) the
    countdown: the event's block width beyond the narrowest block, plus one
    digit when the event's time has an unusual two-digit hour (22:00-00:59).
    Arabic needs half, because its name/time block is centred."""
    from fontTools.ttLib import TTFont
    if "_space_em" not in m:
        f = TTFont(os.path.join(ASSETS, "font", m["id"] + ".ttf"))
        m["_space_em"] = f["hmtx"][f.getBestCmap()[0x00A0]][0] / f["head"].unitsPerEm
    unit = cd_size(m) * m["_space_em"]
    widths, extra = BLOCK_W[lang]
    k = 0.5 if lang == "ar" else 1.0
    wmin = min(widths.values())
    counts = [int(math.ceil(k * (widths[c[1]] - wmin) / unit - 1e-6)) for c in CARRIERS]
    two = int(math.ceil(k * extra / unit - 1e-6))
    late = "((%s+120)%%1440<180)" % TOD
    run = SPACE * (max(counts) + two + 2)
    pads = {c[1]: n * unit for c, n in zip(CARRIERS, counts)}
    pads["_two"] = two * unit
    return 'subText("%s", 0, %s+(%s?%d:0))' % (run, pick(counts), late, two), pads


EDGE_L, EDGE_R = 13, 437   # outermost x the icon / countdown may reach (round screen)
LATE_EVENTS = {"isha"}           # can fall 22:00-00:59 (two-digit hour) in summer far north
LONGEST_CD = {"fajr": "−10:59"}  # Fajr can be 10+ h away; every other wait is under 10 h


def prayer_geometry(lang):
    """Horizontal geometry of the section, in English orientation (Arabic
    mirrors it): x0 = outer edge of the icon box.  The pair is centred for the
    average event with a typical countdown.  Larger size settings move both
    halves together (never apart or into each other) just as far as needed to
    keep the widest real countdown inside the circle - see size_shift()."""
    widths, extra = BLOCK_W[lang]
    wmin = min(widths.values())
    ar = lang == "ar"
    k = 0.5 if ar else 1.0
    ref = CLOCK_FONTS[0]
    typical = text_width(CD_TYPICAL, ref["id"], cd_size(ref))
    col = P_TEXT_W_AR if ar else P_TEXT_W_EN
    to_block = ICON + P_GAP + (col - (col - wmin) / 2 if ar else wmin)   # icon edge -> narrowest block edge
    mean_extra = sum(k * (widths[e] - wmin) for e in EV_ORDER) / len(EV_ORDER)
    x0 = (W - (to_block + mean_extra + CD_GAP + typical)) / 2
    reach = 0.0                    # countdown: its pivot edge -> farthest ink, at 100 %
    for m in CLOCK_FONTS:
        _, pads = space_param(m, lang)
        for ev in EV_ORDER:
            pad = pads[ev] + (pads["_two"] if ev in LATE_EVENTS else 0)
            reach = max(reach, pad + text_width(LONGEST_CD.get(ev, "−9:59"), m["id"], cd_size(m)))
    return dict(x0=x0, wmin=wmin, to_block=to_block, reach=reach, icon_reach=to_block - 4)


def size_shift(lang, geo, ambient):
    """x offset (English orientation) for every size-setting option: zero
    unless the scaled countdown or icon would leave the circle."""
    steps = [("n%da%d" % (n, a), (a if ambient else n) / 100.0) for n in SIZE_STEPS for a in AOD_STEPS]
    cd_edge = geo["x0"] + geo["to_block"] + CD_GAP       # countdown pivot
    blk_edge = geo["x0"] + geo["to_block"]               # name/time pivot

    def chain(cid, fn):
        e = "0"
        for oid, f in steps:
            d = round(fn(f), 1)
            if d != 0:
                e = '(([CONFIGURATION.%s] == "%s") ? %s : %s)' % (cid, oid, d, e)
        return e
    # room the other half leaves even at its own largest size (both halves
    # move together, so neither may be pushed off the other side)
    room_icon = max(0.0, (blk_edge - max(SIZE_STEPS) / 100.0 * geo["icon_reach"]) - EDGE_L)
    room_cd = max(0.0, EDGE_R - (cd_edge + max(SIZE_STEPS) / 100.0 * geo["reach"]))
    right = chain("size_countdown", lambda f: max(-room_icon, min(0.0, EDGE_R - (cd_edge + f * geo["reach"]))))
    left = chain("size_prayer", lambda f: min(room_cd, max(0.0, EDGE_L - (blk_edge - f * geo["icon_reach"]))))
    return right, left


def shift(group, lang, geo, x):
    """Both halves get the same size-dependent x offset (mirrored in Arabic)."""
    sign = -1 if lang == "ar" else 1
    for ambient in (False, True):
        r, l = size_shift(lang, geo, ambient)
        if r == "0" and l == "0":
            continue
        v = "(%d + %d * (%s + %s))" % (int(x), sign, r, l)
        if ambient:
            el(group, "Variant", mode="AMBIENT", target="x", value=v)
        else:
            el(group, "Transform", target="x", value=v)


def prayer_block(parent, lang):
    ar = lang == "ar"
    text_w = P_TEXT_W_AR if ar else P_TEXT_W_EN
    geo = prayer_geometry(lang)
    gw = ICON + P_GAP + text_w
    gx = W - geo["x0"] - gw if ar else geo["x0"]
    gy, gh = Y_PC - 40, 80
    # name over time, with the icon beside them.  Each half scales about the
    # edge facing the other (the name/time block about the edge of its
    # narrowest event, where the countdown's spacing starts), so a larger
    # size moves them apart together and never closes the gap.
    piv = geo["to_block"] / gw
    sp = scaled(parent, "s_prayer", "size_prayer", gx, gy, gw, gh, px=round(1.0 - piv if ar else piv, 4))
    shift(sp, lang, geo, gx)
    icon_x = gw - ICON if ar else 0
    text_x = 0 if ar else ICON + P_GAP
    amb = {"alpha": "190"}
    for table, kw in ((ICON_STR, dict(ambient=amb, name="picon")),
                      (SHADOW_STR, dict(alpha=140, ambient={"alpha": "105"}, name="picon_sh"))):
        part_text(sp, icon_x, Y_PC - ICON / 2 - gy, ICON, ICON, "icons", ICON, ACCENT,
                  template="%s", params=[icon_param(table)], align="CENTER", **kw)
    # English: left-aligned under each other.  Arabic: centred in its column
    # (START/END would depend on how the renderer resolves right-to-left
    # text; CENTER cannot go wrong).
    align = "CENTER" if ar else "START"

    def name(r, m):
        ls = None if ar else 0.08
        part_text(r, text_x, Y_PC + NAME_DY - H_NAME / 2 - gy, text_w, H_NAME, m["id"], name_size(m, lang), ACCENT,
                  template="%s", params=[name_param(lang)], align=align, letter_spacing=ls,
                  ambient=amb, name="pname")
    by_textfont(sp, name)

    def sched(r, m, dg):
        part_text(r, text_x, Y_PC + TIME_DY - H_TIME / 2 - gy, text_w, H_TIME, fam(m, dg), time_size(m, lang), GREY,
                  template="%s:%s%s %s", params=time_params(lang), align=align, ambient=amb, name="ptime")
    by_textfont(sp, lambda r, m: by_digits(r, lambda s, dg: sched(s, m, dg)))

    # the countdown CD_GAP after the narrowest name/time block; the spacing
    # glyphs in its text add whatever the current event's block needs
    cd_w = CD_FIT + 60
    if ar:
        col_l = W - geo["x0"] - gw
        edge = col_l + (P_TEXT_W_AR - geo["wmin"]) / 2 - CD_GAP
        cx, pivot, calign = edge - cd_w, 1.0, "END"
    else:
        cx, pivot, calign = geo["x0"] + ICON + P_GAP + geo["wmin"] + CD_GAP, 0.0, "START"
    sc = scaled(parent, "s_countdown", "size_countdown", cx, Y_PC - H_CD / 2, cd_w, H_CD, px=pivot)
    shift(sc, lang, geo, cx)

    def cd(r, m, dg):
        sp_param, _ = space_param(m, lang)
        params = countdown_params()
        params = params + [sp_param] if ar else [sp_param] + params
        part_text(r, 0, 0, cd_w, H_CD, fam(m, dg), cd_size(m), CLOCKCOL, template="%s%s%s%s%s",
                  params=params, align=calign, ambient={"alpha": "215"}, name="cd")
    by_clockfont(sc, lambda r, m: by_digits(r, lambda s, dg: cd(s, m, dg)))


def prayer_section(scene):
    g = el(scene, "Group", name="prayer", x=0, y=0, width=W, height=H)

    def unavailable(p):
        part_text(p, 0, Y_PC - H_CD / 2, W, H_CD, "t01", CD_SIZE, GREY, text="—", name="cd")
    cond(g, [("engine_off", engine_guard(), unavailable)], lambda p: by_lang(p, prayer_block))


# ------------------------------------------------------------------ stats
def stat_value(parent, expr_or_text, is_expr=True):
    def build(p, meta, dg):
        kw = dict(template="%s", params=[expr_or_text]) if is_expr else dict(text=expr_or_text)
        size = fit(norm(STAT_SIZE, meta), "18,888", meta["id"], 88)
        part_text(p, 0, STAT_ICON + 2, SLOT_W, SLOT_H - STAT_ICON - 2, fam(meta, dg), size, VALUE,
                  ambient=AMB_DIM, name="stat", **kw)
    by_textfont(parent, lambda p, m: by_digits(p, lambda q, dg: build(q, m, dg)))


def complication_slot(scene, slot_id, cx, label_key, icon, default_provider, types, size_cfg):
    cs = el(scene, "ComplicationSlot", slotId=slot_id, x=int(cx - SLOT_W / 2), y=Y_STATS,
            width=SLOT_W, height=SLOT_H, displayName=label_key, supportedTypes=" ".join(types),
            isCustomizable="TRUE")
    el(cs, "BoundingBox", x=0, y=0, width=SLOT_W, height=SLOT_H)
    el(cs, "DefaultProviderPolicy", defaultSystemProvider=default_provider, defaultSystemProviderType="SHORT_TEXT")
    for typ in types:
        cpx = el(cs, "Complication", type=typ)
        g = scaled(cpx, "s_" + size_cfg + "_" + typ.lower(), size_cfg, 0, 0, SLOT_W, SLOT_H)
        part_image(g, (SLOT_W - STAT_ICON) / 2, 0, STAT_ICON, STAT_ICON, icon, tint=ACCENT, ambient=AMB_DIM)
        if typ == "SHORT_TEXT":
            stat_value(g, "[COMPLICATION.TEXT]")
        elif typ in ("RANGED_VALUE", "GOAL_PROGRESS"):
            src = "RANGED_VALUE_VALUE" if typ == "RANGED_VALUE" else "GOAL_PROGRESS_VALUE"
            cond(g, [("has_text", "[COMPLICATION.TEXT] != null", lambda q: stat_value(q, "[COMPLICATION.TEXT]"))],
                 lambda q, s=src: stat_value(q, "round([COMPLICATION.%s])" % s))
        elif typ == "EMPTY":
            part_text(g, 0, STAT_ICON + 2, SLOT_W, SLOT_H - STAT_ICON - 2, "t01", STAT_SIZE, VALUE,
                      text="––", ambient=AMB_DIM, name="stat")


def battery(scene):
    g = scaled(scene, "s_battery", "size_battery", int(COLS[2] - SLOT_W / 2), Y_STATS, SLOT_W, SLOT_H)
    bw, bh = 36, 18
    bx, by = (SLOT_W - bw) / 2 - 2, (STAT_ICON - bh) / 2
    pd = el(g, "PartDraw", x=int(bx), y=int(by), width=bw + 6, height=bh, alpha=255)
    el(pd, "Variant", mode="AMBIENT", target="alpha", value="170")
    r = el(pd, "RoundRectangle", x=1, y=1, width=bw - 2, height=bh - 2, cornerRadiusX=4, cornerRadiusY=4)
    el(r, "Stroke", color=ACCENT, thickness=2.5)
    nub = el(pd, "RoundRectangle", x=bw, y=int(bh / 2 - 4), width=4, height=8, cornerRadiusX=1.5, cornerRadiusY=1.5)
    el(nub, "Fill", color=ACCENT)
    fill_w = bw - 10

    def fill(p, color):
        q = el(p, "PartDraw", x=int(bx + 5), y=int(by + 5), width=fill_w, height=bh - 10)
        el(q, "Variant", mode="AMBIENT", target="alpha", value="170")
        rr = el(q, "Rectangle", x=0, y=0, width=fill_w, height=bh - 10)
        el(rr, "Transform", target="width", value="clamp(%d * [BATTERY_PERCENT] / 100.0, 1, %d)" % (fill_w, fill_w))
        el(rr, "Fill", color=color)
    cond(g, [("low", "[BATTERY_PERCENT] <= 15", lambda p: fill(p, "#FFE5554F"))], lambda p: fill(p, ACCENT))

    def value(p, m, dg):
        size = fit(norm(STAT_SIZE, m), "100%", m["id"], 76)
        part_text(p, 0, STAT_ICON + 2, SLOT_W, SLOT_H - STAT_ICON - 2, fam(m, dg), size, VALUE,
                  template="%s%%", params=["[BATTERY_PERCENT]"], ambient=AMB_DIM, name="stat")
    by_textfont(g, lambda p, m: by_digits(p, lambda q, dg: value(q, m, dg)))


# ================================================================ document
def build_document():
    root = el(None, "WatchFace", width=W, height=H, clipShape="CIRCLE")
    el(root, "Metadata", key="CLOCK_TYPE", value="DIGITAL")
    el(root, "Metadata", key="PREVIEW_TIME", value="16:56:32")
    user_configurations(root)
    scene = el(root, "Scene", backgroundColor="#FF000000")
    engine_group(scene)
    date_line(scene)
    clock(scene)
    prayer_section(scene)
    complication_slot(scene, "0", COLS[0], S("slot_steps", "Steps (choose Samsung Health)", "الخطوات (اختر Samsung Health)"),
                      "ic_steps", "STEP_COUNT", ["SHORT_TEXT", "RANGED_VALUE", "GOAL_PROGRESS", "EMPTY"], "size_steps")
    complication_slot(scene, "1", COLS[1], S("slot_hr", "Heart rate (choose Samsung Health)", "نبض القلب (اختر Samsung Health)"),
                      "ic_heart", "HEART_RATE", ["SHORT_TEXT", "RANGED_VALUE", "EMPTY"], "size_hr")
    battery(scene)
    return root


# ------------------------------------------------------- safe serialiser
def serialise(root):
    """Indented XML, but elements that carry text (Template, Font, Expression)
    are written on one line with their text untouched - any whitespace added
    inside them would be drawn on the watch."""
    out = ['<?xml version="1.0" encoding="utf-8"?>\n']

    def attrs(e):
        return "".join(' %s="%s"' % (k, esc_attr(v)) for k, v in e.attrib.items())

    def write(e, depth):
        pad = ""  # no indentation: keeps the file small (newlines only)
        if e.text and e.text.strip() or e.tag in ("Template", "Font", "Expression"):
            out.append(pad + ET.tostring(e, encoding="unicode", short_empty_elements=True).strip() + "\n")
            return
        if len(e) == 0:
            out.append("%s<%s%s/>\n" % (pad, e.tag, attrs(e)))
            return
        out.append("%s<%s%s>\n" % (pad, e.tag, attrs(e)))
        for c in e:
            write(c, depth + 1)
        out.append("%s</%s>\n" % (pad, e.tag))
    write(root, 0)
    return "".join(out)


def esc_attr(v):
    return (v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;"))


def strings_xml(table, app_name):
    res = ET.Element("resources")
    ET.SubElement(res, "string", name="watch_face_name").text = app_name
    for k in sorted(table):
        v = table[k].replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
        if v[:1] in "@?":
            v = "\\" + v
        ET.SubElement(res, "string", name=k).text = v
    ET.indent(res)
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(res, encoding="unicode") + "\n"


def main(out_main):
    res = os.path.join(out_main, "res")
    for d in ("raw", "xml", "values", "values-ar", "font", "drawable-nodpi"):
        os.makedirs(os.path.join(res, d), exist_ok=True)
    for d in ("font",):
        for fn in os.listdir(os.path.join(res, d)):
            os.remove(os.path.join(res, d, fn))
    for fn in os.listdir(os.path.join(res, "drawable-nodpi")):
        if fn.startswith("opt_"):
            os.remove(os.path.join(res, "drawable-nodpi", fn))
    root = build_document()
    for f in root.iter("Font"):                    # every face font, release-tagged (see FONT_TAG)
        f.set("family", f.get("family") + FONT_TAG)
    xml = serialise(root)
    open(os.path.join(res, "raw", "watchface.xml"), "w", encoding="utf-8").write(xml)
    open(os.path.join(res, "values", "strings.xml"), "w", encoding="utf-8").write(strings_xml(STR_EN, "Noor Prayer Watch Face"))
    open(os.path.join(res, "values-ar", "strings.xml"), "w", encoding="utf-8").write(strings_xml(STR_AR, "واجهة نور للصلاة"))
    open(os.path.join(res, "xml", "watch_face_info.xml"), "w", encoding="utf-8").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<WatchFaceInfo>\n'
        '  <Preview value="@drawable/preview" />\n  <Editable value="true" />\n'
        '  <MultipleInstancesAllowed value="false" />\n</WatchFaceInfo>\n')
    for fn in os.listdir(os.path.join(ASSETS, "font")):
        if fn.endswith(".ttf"):
            shutil.copy(os.path.join(ASSETS, "font", fn), os.path.join(res, "font", fn[:-4] + FONT_TAG + ".ttf"))
    for fn in os.listdir(os.path.join(ASSETS, "icons")):
        if fn.endswith(".png"):
            shutil.copy(os.path.join(ASSETS, "icons", fn), os.path.join(res, "drawable-nodpi", fn))
    for name, lines, fpath in ICON_JOBS:
        option_icons.make_icon(os.path.join(res, "drawable-nodpi", name + ".png"), lines, fpath)
    print("watchface.xml: %d bytes, %d elements, %d option pictures" %
          (len(xml.encode()), sum(1 for _ in root.iter()), len(ICON_JOBS)))
    return root


if __name__ == "__main__":
    main(sys.argv[1])
