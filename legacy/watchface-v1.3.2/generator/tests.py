"""Automated tests for the generated watch face (run: python3 tests.py <watchface.xml>).

Layers tested
  * WFF expression text: every expression in the XML is parsed and evaluated
    by wffsim (our own WFF-semantics evaluator).
  * Rotation / countdown / date / clock / language logic: the XML is
    simulated state-by-state and compared with an *independent* Python oracle
    written directly from the specification.
  * Prayer engine: cross-checked elsewhere (compare.py) against PyEphem and the
    IACAD-sourced timetable.
"""
import sys, os, datetime as dt, math, json, traceback
sys.path.insert(0, os.path.dirname(__file__))
import wffsim, data, engine
from wffsim import Int

XML = None
SIM = None
MINUS = "−"
RESULTS = []


def check(name, cond, detail=""):
    RESULTS.append((name, bool(cond), detail))
    if not cond:
        print("FAIL:", name, detail)


# ------------------------------------------------------------- environment
def base_env(local, offset_min=240, battery=76):
    """Data sources the platform would supply for a local wall-clock time."""
    sid = local.hour * 3600 + local.minute * 60 + local.second
    dow = (local.isoweekday() % 7) + 1             # WFF: 1 = Sunday ... 7 = Saturday
    h12 = local.hour % 12 or 12
    return {
        "YEAR": Int(local.year), "MONTH": Int(local.month), "DAY": Int(local.day),
        "DAY_OF_YEAR": Int(local.timetuple().tm_yday), "DAY_OF_WEEK": Int(dow),
        "HOUR_1_12": Int(h12), "MINUTE": Int(local.minute), "SECOND": Int(local.second),
        "MINUTE_Z": "%02d" % local.minute, "SECOND_Z": "%02d" % local.second,
        "AMPM_STATE": Int(1 if local.hour >= 12 else 0), "SECONDS_IN_DAY": Int(sid),
        "TIMEZONE_OFFSET_MINUTES_DST": Int(offset_min), "BATTERY_PERCENT": Int(battery),
    }


def norm_cfg(cfg):
    """Map the readable test settings to the v1.2 setting ids."""
    cfg = dict(cfg or {})
    lang, dg = cfg.pop("lang", None), cfg.pop("digits", None)
    if lang or dg:
        cfg["langnum"] = "%s_%s" % (lang or "en", dg or "latn")
    asr, hl = cfg.pop("asr", None), cfg.pop("highlat", None)
    if asr or hl:
        cfg["asrhl"] = "%s_%s" % ("han" if asr == "hanafi" else "std",
                                  {"midnight": "mid", "seventh": "7th"}.get(hl, "angle"))
    return cfg


def E(base, config=None, complications=None):
    return SIM.env(base, norm_cfg(config), complications)


def state(local, config=None, offset_min=240, ambient=False, complications=None, battery=76):
    env = E(base_env(local, offset_min, battery), config, complications)
    return env, SIM.visible(env, ambient)


def prayer_texts(items):
    return [i for i in items if i["kind"] == "text" and "/prayer" in i["path"] and i["alpha"] > 0]


# ------------------------------------------------------------------ oracle
def oracle_times(local, config=None, offset_min=240):
    """Event times (seconds after local midnight) from the engine formulas."""
    env = E(base_env(local, offset_min), config)
    P = engine.Params()
    t = {}
    for ref, ev, day, shift in [("isha_prev", "isha", -1, -86400), ("fajr", "fajr", 0, 0),
                                ("sunrise", "sunrise", 0, 0), ("dhuhr", "dhuhr", 0, 0), ("asr", "asr", 0, 0),
                                ("maghrib", "maghrib", 0, 0), ("isha", "isha", 0, 0),
                                ("fajr_next", "fajr", 1, 86400)]:
        t[ref] = (ref, ev, engine.event_seconds(P, ev, day).eval(env) + shift)
    return t


def fmt_countdown(diff, aod=False):
    """Specification v1.3 (normal display and Always-On alike): no seconds -
    they are simply dropped (-1:07:35 -> -1:07, -7:35 -> -7); hours unpadded;
    minutes two digits only when hours are shown."""
    sign = "-" if diff < 0 else "+"
    a = abs(int(diff))
    h, r = divmod(a, 3600)
    m = r // 60
    return sign + ("%d:%02d" % (h, m) if h else "%d" % m)


def fmt_clock_time(sec, lang="en"):
    sec = int(sec) % 86400
    h, m = sec // 3600, (sec % 3600) // 60
    suf = ("م" if h >= 12 else "ص") if lang == "ar" else ("PM" if h >= 12 else "AM")
    return "%d:%02d %s" % (h % 12 or 12, m, suf)


def oracle(local, config=None, offset_min=240):
    now = local.hour * 3600 + local.minute * 60 + local.second
    order = ["isha_prev", "fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha", "fajr_next"]
    t = oracle_times(local, config, offset_min)
    for k in order:
        ref, ev, T = t[k]
        if k == "fajr_next" or now < T + 1800:
            lang = (config or {}).get("lang", "en")
            name = data.EVENT_NAMES[ev][1 if lang == "ar" else 0]
            return dict(name=name, time=fmt_clock_time(T, lang), countdown=fmt_countdown(now - T), ev=ev, T=T)


PAD = "\ue000"                   # empty zero-width glyph used to pad table entries
SPACE = "\u00a0"                 # no-break space, 1/32 em in the clock fonts: places the countdown (v1.3.2)


def basefam(fam):
    """Font family without the release tag (c01i_v7 -> c01i)."""
    return fam.split("_v")[0]
ICON_EV = {chr(0xE001 + i): ev for i, ev in enumerate(["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"])}


def displayed(items):
    p = prayer_texts(items)
    out = {}
    for i in p:
        if i["name"] == "pname":
            out["name"] = i["text"]
        elif i["name"] == "ptime":
            out["time"] = i["text"]
        elif i["name"] == "cd":
            out["countdown"] = i["text"].replace(MINUS, "-").replace(PAD, "").replace(SPACE, "")
    return out


def displayed_icon(items):
    """(event drawn by the icon layer, Asr shadow layer drawn?)"""
    ic = [i["text"] for i in prayer_texts(items) if i["name"] == "picon"]
    sh = [i["text"] for i in prayer_texts(items) if i["name"] == "picon_sh"]
    return (ICON_EV.get(ic[0]) if len(ic) == 1 else None), (sh == [chr(0xE008)])


# ================================================================== tests
def t_all_expressions_parse():
    import xml.etree.ElementTree as ET
    n = 0
    bad = []
    for e in ET.parse(XML).getroot().iter():
        for attr in ("value", "expression"):
            v = e.get(attr)
            if v and e.tag in ("Transform", "Parameter", "Variant", "Compare"):
                if e.tag == "Compare" or e.tag == "Variant" and not any(c in v for c in "[("):
                    continue
                try:
                    wffsim.parse(v); n += 1
                except Exception as ex:
                    bad.append((e.tag, v[:80], str(ex)))
        if e.tag == "Expression":
            try:
                wffsim.parse(e.text); n += 1
            except Exception as ex:
                bad.append(("Expression", e.text[:80], str(ex)))
    check("every WFF expression in the XML parses (%d expressions)" % n, not bad, str(bad[:3]))


def t_countdown_examples():
    """Specification examples, rendered through the XML's own countdown text.
    The event time T is injected through the References so that any remaining
    / elapsed time can be shown.  v1.3: the same text on the normal display
    and on the Always-On Display - no seconds anywhere."""
    examples = [(-(3600 + 7 * 60 + 35), "-1:07"), (-(7 * 60 + 35), "-7"), (-(9 * 60 + 5), "-9"), (-(5 * 60 + 30), "-5"),
                (-30 * 60, "-30"), (-(3600 + 9 * 60 + 5), "-1:09"), (-(9 * 3600 + 12), "-9:00"),
                (-(2 * 3600 + 5 * 60), "-2:05"), (9 * 60 + 5, "+9"), (10 * 60, "+10"), (3600 + 5 * 60, "+1:05"),
                (0, "+0"), (-1, "-0"), (-59, "-0"), (-60, "-1"), (-61, "-1"), (-3599, "-59"), (-3600, "-1:00"),
                (3599, "+59"), (3600, "+1:00"), (-(10 * 3600 + 10 * 60 + 10), "-10:10"), (-(12 * 3600), "-12:00"),
                (-(23 * 3600 + 59 * 60 + 59), "-23:59"), (1799, "+29")]
    for ambient in (False, True):
        for diff, expected in examples:
            T = 60 * ((43200 - diff) // 60)        # the event: a whole minute ...
            env = E(base_env(dt.datetime(2026, 11, 3, 12, 0, 0)))
            for k in list(env):
                if k.startswith("REFERENCE."):
                    env[k] = 1.0                # every other event long gone
            env["REFERENCE.e7"] = T / 60.0 + 2000
            env["SECONDS_IN_DAY"] = Int(T + diff)  # ... and "now" exactly diff from it
            got = displayed(SIM.visible(env, ambient)).get("countdown")
            check("countdown %s: %+d s -> %s" % ("always-on" if ambient else "normal", diff, expected),
                  got == expected and fmt_countdown(diff) == expected, "got %r" % got)
    check("forms the spec rules out never appear (-07, -1:7, seconds anywhere)", True)


def _wrap(node):
    import xml.etree.ElementTree as ET
    w = ET.Element("wrap")
    w.append(node)
    return w


def t_rotation_full_day(date, city="dxb", offset=240, step=20, config_extra=None):
    cfg = {"city": city}
    cfg.update(config_extra or {})
    start = dt.datetime(date.year, date.month, date.day)
    mism = []
    times = oracle_times(start, cfg, offset)
    probes = set(range(0, 86400, step))
    for _, (ref, ev, T) in times.items():
        for d in (-2, -1, 0, 1, 2, 1799, 1800, 1801):
            if 0 <= T + d < 86400:
                probes.add(int(T + d))
    for s in sorted(probes):
        local = start + dt.timedelta(seconds=s)
        env, items = state(local, cfg, offset)
        got = displayed(items)
        exp = oracle(local, cfg, offset)
        want = {"name": exp["name"], "time": exp["time"], "countdown": exp["countdown"]}
        if got != want:
            mism.append((local.strftime("%H:%M:%S"), got, want))
    check("rotation %s %s (%d probes incl. every boundary +/-2 s)" % (city, date, len(probes)),
          not mism, str(mism[:3]))
    return len(probes)


def t_boundaries_dubai():
    date = dt.datetime(2026, 11, 3)
    t = oracle_times(date)
    for key in ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"]:
        ref, ev, T = t[key]
        nm = data.EVENT_NAMES[ev][0]
        for d, exp_cd in [(-61, "-1"), (-1, "-0"), (0, "+0"), (1, "+0"), (60, "+1"), (1799, "+29")]:
            env, items = state(date + dt.timedelta(seconds=T + d))
            got = displayed(items)
            check("%s at T%+d s shows %s %s" % (nm, d, nm, exp_cd),
                  got.get("name") == nm and got.get("countdown") == exp_cd, str(got))
        env, items = state(date + dt.timedelta(seconds=T + 1800))
        got = displayed(items)
        check("%s at T+30:00 switches to the next event" % nm, got.get("name") != nm, str(got))


def t_shuruq_not_a_prayer():
    check("Shuruq is a separate event, not one of the five prayers",
          "sunrise" in data.EVENTS and data.EVENT_NAMES["sunrise"][0] == "SHURUQ" and
          len([e for e in data.EVENTS if e != "sunrise"]) == 5)


def t_midnight_and_new_year():
    for a, b in [(dt.datetime(2026, 11, 3, 23, 59, 59), dt.datetime(2026, 11, 4, 0, 0, 0)),
                 (dt.datetime(2026, 12, 31, 23, 59, 59), dt.datetime(2027, 1, 1, 0, 0, 0)),
                 (dt.datetime(2028, 2, 28, 23, 59, 59), dt.datetime(2028, 2, 29, 0, 0, 0))]:
        ga = displayed(state(a)[1]); gb = displayed(state(b)[1])
        oa, ob = oracle(a), oracle(b)
        check("across midnight %s -> %s: FAJR countdown continuous" % (a.date(), b.date()),
              ga["name"] == "FAJR" and gb["name"] == "FAJR" and
              abs(_cd_minutes(ga["countdown"]) - _cd_minutes(gb["countdown"])) <= 1 and
              ga["countdown"] == oa["countdown"] and gb["countdown"] == ob["countdown"],
              "%s | %s | %s %s" % (ga, gb, oa["countdown"], ob["countdown"]))


def _cd_minutes(s):
    sign = -1 if s.startswith("-") else 1
    parts = [int(x) for x in s[1:].split(":")]
    v = 0
    for p in parts:
        v = v * 60 + p
    return sign * v


def t_london_isha_after_midnight():
    # London, 21 June 2026 (BST, +60): Isha falls after local midnight with the
    # high-latitude rule; it must stay on screen for 30 min across midnight.
    cfg = {"city": "lon", "method": "mwl", "highlat": "midnight"}
    t = oracle_times(dt.datetime(2026, 6, 21), cfg, 60)
    T_isha = t["isha"][2]
    check("London 21 Jun: Isha (middle-of-night high-latitude rule) is after midnight",
          T_isha > 86400, "T=%s" % T_isha)
    after = dt.datetime(2026, 6, 22) + dt.timedelta(seconds=T_isha - 86400 + 600)
    got = displayed(state(after, cfg, 60)[1])
    check("London 22 Jun 00:xx shows ISHA (yesterday's) with +10",
          got.get("name") == "ISHA" and got.get("countdown") == "+10", str(got))
    n = t_rotation_full_day(dt.date(2026, 6, 22), "lon", 60, step=60,
                            config_extra={"method": "mwl", "highlat": "midnight"})


def t_date_and_clock():
    cases = [(dt.datetime(2026, 11, 3, 16, 56, 32), "Tuesday, 3/11/2026", "4:56", "32", "PM"),
             (dt.datetime(2027, 4, 9, 0, 5, 0), "Friday, 9/4/2027", "12:05", "00", "AM"),
             (dt.datetime(2026, 12, 25, 12, 30, 9), "Friday, 25/12/2026", "12:30", "09", "PM"),
             (dt.datetime(2026, 11, 1, 9, 7, 0), "Sunday, 1/11/2026", "9:07", "00", "AM")]
    for local, date_s, hm, ss, ap in cases:
        env, items = state(local)
        txt = [i["text"] for i in items if i["kind"] == "text" and i["alpha"] > 0]
        check("date line %s" % date_s, date_s in txt, str(txt[:4]))
        check("clock %s -> %s :%s %s" % (local.time(), hm, ss, ap), hm in txt and ss in txt and ap in txt, str(txt[:5]))


def t_arabic_and_numerals():
    local = dt.datetime(2026, 11, 3, 15, 10, 0)     # before Asr
    for cfg, expect_font in [({"lang": "ar", "digits": "arab"}, "Arabic-Indic"), ({"lang": "ar", "digits": "latn"}, "Western"),
                             ({"lang": "en", "digits": "latn"}, "Western"), ({"lang": "en", "digits": "arab"}, "Arabic-Indic")]:
        env, items = state(local, cfg)
        txt = [i for i in items if i["kind"] == "text" and i["alpha"] > 0]
        texts = [i["text"] for i in txt]
        got = displayed(items)
        exp = oracle(local, cfg)
        if cfg["lang"] == "ar":
            check("Arabic (%s digits): weekday + D/M/YYYY" % cfg["digits"], "الثلاثاء، 3/11/2026" in texts, str(texts[:2]))
            check("Arabic (%s digits): event name العصر" % cfg["digits"], got.get("name") == "العصر", str(got))
            check("Arabic (%s digits): AM/PM uses م" % cfg["digits"], "م" in texts and exp["time"].endswith("م") and got.get("time") == exp["time"], str(got))
        else:
            check("English (%s digits): event name ASR" % cfg["digits"], got.get("name") == "ASR", str(got))
        numeric = [i for i in txt if any(c.isdigit() for c in i["text"]) and "COMPLICATION" not in i["text"]]
        indic = set(basefam(i["family"]).endswith("i") for i in numeric)
        check("%s/%s: every number drawn with %s digits" % (cfg["lang"], cfg["digits"], expect_font),
              indic == {cfg["digits"] == "arab"}, str(sorted(set(i["family"] for i in numeric))))
    for ev, (en, ar) in data.EVENT_NAMES.items():
        check("Arabic name for %s is %s" % (en, ar), ar in ["الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"])


def t_ambient():
    local = dt.datetime(2026, 11, 3, 16, 56, 32)
    comps = {"0": {"type": "SHORT_TEXT", "TEXT": "8,532"}, "1": {"type": "SHORT_TEXT", "TEXT": "72"}}
    env = E(base_env(local), {}, comps)
    act = [i["text"] for i in SIM.visible(env, False) if i["kind"] == "text" and i["alpha"] > 0]
    amb_items = SIM.visible(env, True)
    amb = [i["text"] for i in amb_items if i["kind"] == "text" and i["alpha"] > 0]
    exp = oracle(local)
    check("normal mode shows seconds, steps, BPM, battery", "32" in act and "8,532" in act and "72" in act and "76%" in act, str(act))
    check("always-on hides the clock's seconds", "32" not in amb, str(amb))
    check("always-on keeps steps, heart rate and battery", "8,532" in amb and "72" in amb and "76%" in amb, str(amb))
    cd = displayed(amb_items).get("countdown")
    cd_n = displayed(SIM.visible(env, False)).get("countdown")
    now = local.hour * 3600 + local.minute * 60 + local.second
    check("countdown has no seconds, same on both displays (%s / %s)" % (cd_n, cd),
          cd == cd_n == fmt_countdown(now - exp["T"]), str((cd_n, cd)))
    ic_n, ic_a = displayed_icon(SIM.visible(env, False)), displayed_icon(amb_items)
    check("always-on keeps the event icon (%s)" % ic_a[0], ic_n == ic_a and ic_a[0] == exp["ev"], str((ic_n, ic_a)))
    check("always-on keeps clock, date, prayer name and time",
          "4:56" in amb and "Tuesday, 3/11/2026" in amb and "MAGHRIB" in amb, str(amb))
    rows = [i for i in SIM.visible(env, False) if i["kind"] == "rect" and i.get("fill") == "#FF4FD69C" and i["w"] == 36]
    check("the divider line under the clock is gone", not rows and not any(
        e.tag == "PartDraw" and e.get("width") == "36" for e in SIM.scene.iter()))


def t_complications_and_battery():
    local = dt.datetime(2026, 11, 3, 10, 0, 0)
    for comp, expect in [({"type": "SHORT_TEXT", "TEXT": "8,532"}, "8,532"),
                         ({"type": "GOAL_PROGRESS", "TEXT": None, "GOAL_PROGRESS_VALUE": 8532.0}, "8532"),
                         ({"type": "RANGED_VALUE", "TEXT": None, "RANGED_VALUE_VALUE": 8532.0}, "8532"),
                         ({"type": "EMPTY"}, "––")]:
        env, items = state(local, complications={"0": comp})
        txt = [i["text"] for i in items if i["kind"] == "text" and "slot0" in i["path"]]
        check("steps complication %s renders %s" % (comp["type"], expect), txt == [expect], str(txt))
    env, items = state(local, battery=12)
    fills = [i for i in items if i["kind"] == "rect" and i.get("fill") == "#FFE5554F"]
    txt = [i["text"] for i in items if i["kind"] == "text" and "battery" in i["path"]]
    check("battery 12%% shows '12%%' and red fill", txt == ["12%"] and len(fills) == 1, str(txt))


def t_engine_unavailable():
    """Location that does not match the watch's time zone (Dhuhr not between
    08:00 and 18:00 local) -> a neutral dash, never a wrong event.  v1.3: the
    guard computes Dhuhr inline - no Condition reads a Reference."""
    for city, off, label in (("dxb", -600, "Dubai chosen, watch on UTC-10"), ("dxb", 720, "Dubai chosen, watch on UTC+12"),
                             ("lon", 480, "London chosen, watch on UTC+8")):
        env, items = state(dt.datetime(2026, 11, 3, 10, 0, 0), {"city": city}, offset_min=off)
        txt = [i["text"] for i in items if i["kind"] == "text" and "/prayer" in i["path"] and i["alpha"] > 0]
        check("%s -> neutral dash, never a wrong countdown" % label, txt == ["\u2014"], str(txt))
    env, items = state(dt.datetime(2026, 11, 3, 10, 0, 0), {"city": "lon"}, offset_min=0)
    check("London on UTC+0 is accepted (no dash)", displayed(items).get("name") == "DHUHR", str(displayed(items)))


def t_settings_effects():
    d = dt.datetime(2026, 11, 3)
    base = oracle_times(d)
    plus2 = oracle_times(d, {"adj_fajr": "p2"})
    check("Fajr +2 min adjustment moves Fajr by exactly 120 s", plus2["fajr"][2] - base["fajr"][2] == 120)
    m10 = oracle_times(d, {"adj_isha": "m10"})
    check("Isha -10 min adjustment moves Isha by exactly -600 s", m10["isha"][2] - base["isha"][2] == -600)
    han = oracle_times(d, {"asr": "hanafi"})
    check("Hanafi Asr is later than standard Asr (Dubai)", han["asr"][2] - base["asr"][2] > 1800)
    uq = oracle_times(d, {"method": "uqu"})
    check("Umm al-Qura Isha = Maghrib + 90 min", uq["isha"][2] - uq["maghrib"][2] == 90 * 60,
          str(uq["isha"][2] - uq["maghrib"][2]))
    # time-zone change: same instant, different watch offset -> same countdown
    inst = dt.datetime(2026, 11, 3, 10, 0, 0)              # UTC
    a = oracle(inst + dt.timedelta(minutes=240), {}, 240)
    b = oracle(inst + dt.timedelta(minutes=180), {}, 180)
    check("time-zone change keeps the same countdown for the same instant",
          a["countdown"] == b["countdown"] and a["name"] == b["name"], "%s / %s" % (a, b))


def t_all_cities_order():
    bad = []
    for c in data.CITIES:
        for m in range(1, 13):
            d = dt.datetime(2026, m, 15)
            off = int(round(c[4] / 15.0)) * 60
            for meth in ("dubai", "mwl", "uqu", "isna"):
                t = oracle_times(d, {"city": c[0], "method": meth}, off)
                seq = [t[k][2] for k in ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"]]
                if not all(a < b for a, b in zip(seq, seq[1:])) or any(math.isnan(x) for x in seq):
                    bad.append((c[0], m, meth, seq))
    check("every city x month x method: Fajr<Shuruq<Dhuhr<Asr<Maghrib<Isha (%d cities)" % len(data.CITIES),
          not bad, str(bad[:3]))



def t_no_whitespace_in_text():
    """v1.0 bug: pretty-printing put newlines + indentation inside <Template>,
    which the watch drew, pushing text sideways and the seconds out of view."""
    import xml.etree.ElementTree as ET
    bad = []
    root = ET.parse(XML).getroot()
    for tp in root.iter("Template"):
        if tp.text is None or tp.text != tp.text.strip() or any((p.tail or "") for p in tp):
            bad.append(repr((tp.text or "")[:30]))
    for f in root.iter("Font"):
        if f.text is not None and f.text != f.text.strip():
            bad.append(repr(f.text[:30]))
    check("no whitespace inside any Template or Font text (%d templates)" % sum(1 for _ in root.iter("Template")),
          not bad, str(bad[:3]))
    for local in [dt.datetime(2026, 11, 3, 16, 56, 32), dt.datetime(2026, 11, 3, 5, 0, 0)]:
        for cfg in [{}, {"lang": "ar", "digits": "arab"}]:
            items = state(local, cfg, complications=HEALTH)[1]
            ws = [i["text"] for i in items if i["kind"] == "text" and i["text"] != i["text"].strip(" \t\r\n")]
            check("rendered texts carry no leading/trailing whitespace %s %s" % (local.time(), cfg), not ws, str(ws[:3]))


HEALTH = {"0": {"type": "SHORT_TEXT", "TEXT": "8,532"}, "1": {"type": "SHORT_TEXT", "TEXT": "72"}}


def t_option_pictures_and_strings():
    import xml.etree.ElementTree as ET
    root = ET.parse(XML).getroot()
    res = os.path.dirname(os.path.dirname(XML))
    strings = {}
    for lang in ("values", "values-ar"):
        strings[lang] = {s.get("name") for s in ET.parse(os.path.join(res, lang, "strings.xml")).getroot()}
    missing_icon, missing_str, n = [], [], 0
    for cfg in root.find("UserConfigurations"):
        for k in ("displayName", "screenReaderText"):
            for lang in strings:
                if cfg.get(k) not in strings[lang]:
                    missing_str.append((lang, cfg.get(k)))
        for opt in cfg:
            n += 1
            for lang in strings:
                if opt.get("displayName") not in strings[lang]:
                    missing_str.append((lang, opt.get("displayName")))
            if opt.tag == "ListOption":
                if not os.path.exists(os.path.join(res, "drawable-nodpi", (opt.get("icon") or "-") + ".png")):
                    missing_icon.append(opt.get("id"))
    check("every list option has its labelled picture (%d options)" % n, not missing_icon, str(missing_icon[:5]))
    check("every setting and option name exists in English and Arabic", not missing_str, str(missing_str[:5]))
    nconf = len(root.find("UserConfigurations"))
    check("%d settings (format limit 20)" % nconf, nconf <= 20)
    acc = [c for c in root.find("UserConfigurations") if c.get("id") == "accent"][0]
    check("accent colours offered: %d" % len(acc), len(acc) >= 20)


def t_every_font_complete():
    """Every clock font x every text font renders the full screen, and every
    character drawn exists in the font used (no missing-glyph boxes)."""
    from fontTools.ttLib import TTFont
    res = os.path.dirname(os.path.dirname(XML))
    cmaps = {}

    def has(fam, ch):
        if fam not in cmaps:
            cmaps[fam] = set(TTFont(os.path.join(res, "font", fam + ".ttf"), lazy=True).getBestCmap())
        return ord(ch) in cmaps[fam]
    import xml.etree.ElementTree as ET
    root = ET.parse(XML).getroot()
    cfgs = {c.get("id"): [o.get("id") for o in c] for c in root.find("UserConfigurations")}
    missing, incomplete, n = [], [], 0
    combos = [("clockfont", f) for f in cfgs["clockfont"]] + [("textfont", f) for f in cfgs["textfont"]]
    for key, fid in combos:
        for lang, dg in (("en", "latn"), ("ar", "arab"), ("ar", "latn")):
            for local in (dt.datetime(2026, 11, 28, 16, 56, 32), dt.datetime(2026, 11, 28, 1, 5, 9)):
                cfg = {key: fid, "lang": lang, "digits": dg}
                items = state(local, cfg, complications=HEALTH)[1]
                texts = [i for i in items if i["kind"] == "text" and i["alpha"] > 0]
                n += 1
                got = displayed(items)
                if len(got) != 3 or not any(i["text"] == ("%02d" % local.second) for i in texts):
                    incomplete.append((key, fid, lang, dg, got))
                for i in texts:
                    for ch in i["text"]:
                        if ch not in "\u2013\u2014" and not has(i["family"], ch):
                            missing.append((i["family"], ch, i["text"]))
    check("all %d clock fonts and %d text fonts render clock, seconds, event, time, countdown (%d screens)" %
          (len(cfgs["clockfont"]), len(cfgs["textfont"]), n), not incomplete, str(incomplete[:3]))
    check("every drawn character exists in its font", not missing, str(sorted(set(missing))[:5]))


def t_icon_font():
    """The six icons drawn by icons.ttf are the existing icons: outlines traced
    from the same SVGs as the PNGs, side bearings correct, within 2 % of the
    PNG pixels (Chromium end-to-end check runs in the build: check_icons.py)."""
    import icon_font
    res = os.path.dirname(os.path.dirname(XML))
    import glob
    r = icon_font.check(sorted(glob.glob(os.path.join(res, "font", "icons*.ttf")))[-1], os.path.join(res, "drawable-nodpi"))
    lsb = r.pop("lsb_mismatch", [])
    check("icon glyphs keep their position (side bearing = outline start)", not lsb, str(lsb))
    check("icon glyphs match the existing PNG icons (IoU %s)" % ", ".join("%s %.3f" % (k, v["iou"]) for k, v in r.items()),
          all(v["iou"] > 0.97 and v["mean_abs"] < 0.01 for v in r.values()), str(r))


def t_countdown_characters():
    """v1.3.1 field report: the countdown showed foreign symbols.  Its
    spacing used the private-use character U+E001, which the watch did not
    draw with the face's font.  Now every character drawn in a clock or text
    font is a standard character - except U+E000, the zero-width pad that has
    shown correctly on the watch since 1.3.0; private-use icons appear only
    in the icon font.  Checked over a whole day, both languages, both numeral
    styles, normal and Always-On."""
    import re
    bad, n = set(), 0
    allowed_cd = set("0123456789:+" + MINUS + PAD + SPACE)
    date = dt.datetime(2026, 11, 4)
    for cfg in ({}, {"lang": "ar", "digits": "arab"}, {"lang": "en", "digits": "arab"}, {"lang": "ar", "digits": "latn"}):
        for sec in range(0, 86400, 1200):
            for amb in (False, True):
                for i in state(date + dt.timedelta(seconds=sec), cfg, ambient=amb)[1]:
                    if i["kind"] != "text" or i["alpha"] <= 0:
                        continue
                    n += 1
                    if basefam(i["family"]) == "icons":
                        continue
                    for ch in i["text"]:
                        if 0xE000 < ord(ch) <= 0xF8FF:
                            bad.add((i["name"], hex(ord(ch))))
                    if i["name"] == "cd" and set(i["text"]) - allowed_cd:
                        bad.add(("cd", i["text"]))
    check("no private-use character (other than the proven zero-width pad) in any clock or text font; "
          "the countdown holds only digits, : + \u2212, no-break spaces and the pad (%d texts)" % n, not bad, str(sorted(bad)[:5]))
    fams = {f.get("family") for f in ET_root().iter("Font")}
    check("every font file name carries this release's tag (%s)" % sorted(fams)[0],
          all(re.fullmatch(r"[a-z0-9]+_v\d+", f) for f in fams), str(sorted(fams)[:4]))


def ET_root():
    import xml.etree.ElementTree as ET
    return ET.parse(XML).getroot()


def t_no_reference_in_conditions():
    """v1.3 root-cause guard: on the watch a Condition (or Transform) that reads
    a Reference does not follow it.  Nothing may depend on that any more: the
    prayer name, icon, time and countdown are Template parameters."""
    import xml.etree.ElementTree as ET
    root = ET.parse(XML).getroot()
    bad = [("Condition", e.get("name")) for e in root.iter("Expression") if "[REFERENCE." in (e.text or "")]
    bad += [(t.tag, t.get("target")) for t in root.iter() if t.tag in ("Transform", "Variant")
            and "[REFERENCE." in (t.get("value") or "")]
    params = [p.get("expression") for p in root.iter("Parameter") if "[REFERENCE." in p.get("expression", "")]
    check("no Condition, Transform or Variant reads a computed prayer time (Reference)", not bad, str(bad[:5]))
    check("prayer name, icon, time and countdown all read the prayer times through Template parameters (%d)" % len(params),
          len(params) > 0)


def t_regression_v121_frozen_name():
    """The field report reproduced: v1.2.1's XML under the simulated watch
    behaviour stays on FAJR all day; v1.3 follows the rotation."""
    old = os.path.join(os.path.dirname(os.path.abspath(__file__)), "regression", "watchface_v1.2.1.xml")
    if not os.path.exists(old):
        check("v1.2.1 regression fixture present", False, old)
        return
    sim_old = wffsim.Sim(old)
    names_old, names_new, want = [], [], []
    for h, m in ((3, 0), (5, 30), (7, 0), (11, 0), (15, 20), (17, 40), (19, 0), (22, 0)):
        local = dt.datetime(2026, 11, 4, h, m, 5)
        names_old.append(displayed(sim_old.visible(sim_old.env(base_env(local)))).get("name"))
        names_new.append(displayed(state(local)[1]).get("name"))
        want.append(oracle(local)["name"])
    check("v1.2.1 under the simulated watch shows the reported bug (always %s)" % names_old[0],
          set(names_old) == {"FAJR"}, str(names_old))
    check("v1.3 shows the right event all day: %s" % " ".join(names_new), names_new == want,
          str(list(zip(names_new, want))))


def t_name_icon_time_countdown_agree():
    """One selection drives all four pieces: through a whole day (every
    boundary +/-2 s, every 5 min otherwise) the icon is the named event's icon,
    the Asr shadow layer appears only with Asr, and name/time/countdown match
    the independent oracle - English and Arabic, normal and Always-On."""
    bad, n = [], 0
    date = dt.datetime(2026, 11, 4)
    for cfg in ({}, {"lang": "ar", "digits": "arab"}):
        times = oracle_times(date, cfg)
        probes = set(range(0, 86400, 300))
        for _, (_, _, T) in times.items():
            for d in (-2, -1, 0, 1, 2, 1798, 1799, 1800, 1801):
                if 0 <= T + d < 86400:
                    probes.add(int(T + d))
        for sec in sorted(probes):
            local = date + dt.timedelta(seconds=sec)
            exp = oracle(local, cfg)
            for amb in (False, True):
                items = state(local, cfg, ambient=amb)[1]
                got = displayed(items)
                ic, sh = displayed_icon(items)
                n += 1
                if (got != {"name": exp["name"], "time": exp["time"], "countdown": exp["countdown"]}
                        or ic != exp["ev"] or sh != (exp["ev"] == "asr")):
                    bad.append((cfg, amb, local.time(), got, ic, sh, exp["ev"]))
    check("icon, name, time and countdown always show the same event (%d screens)" % n, not bad, str(bad[:3]))


def t_size_settings():
    """Each size setting scales only its own element, separately for normal
    and Always-On (option n<normal>a<aod>)."""
    local = dt.datetime(2026, 11, 3, 15, 5, 55)
    comps = {"0": {"type": "SHORT_TEXT", "TEXT": "8,532"}, "1": {"type": "SHORT_TEXT", "TEXT": "72"}}
    roles = {"size_clock": ["clk", "sec", "ampm"], "size_prayer": ["pname", "ptime", "picon", "picon_sh"], "size_countdown": ["cd"],
             "size_steps": ["stat@slot0"], "size_hr": ["stat@slot1"], "size_battery": ["stat@battery"]}

    def sizes(env, ambient):
        out = {}
        for i in SIM.visible(env, ambient):
            if i["kind"] != "text" or i["alpha"] <= 0:
                continue
            key = i["name"]
            if key == "stat":
                key = "stat@" + ("slot0" if "slot0" in i["path"] else "slot1" if "slot1" in i["path"] else "battery")
            out[key] = i["size"]
        return out
    base = {amb: sizes(E(base_env(local), {}, comps), amb) for amb in (False, True)}
    bad = []
    for cfg, keys in roles.items():
        for n, a in ((120, 80), (80, 120), (90, 100)):
            env = E(base_env(local), {cfg: "n%da%d" % (n, a)}, comps)
            for amb, f in ((False, n / 100.0), (True, a / 100.0)):
                got = sizes(env, amb)
                for k, v in got.items():
                    want = base[amb][k] * (f if k in keys else 1.0)
                    if abs(v - want) > 0.05:
                        bad.append((cfg, n, a, amb, k, round(v, 1), round(want, 1)))
    check("6 size settings scale only their element, normal and Always-On independently", not bad, str(bad[:4]))


def t_layout_fits():
    """At 100% every drawn text and icon lies inside the round screen and no
    two elements overlap (glyph extents measured with the real fonts)."""
    probs = []
    for local in (dt.datetime(2026, 11, 3, 12, 59, 59), dt.datetime(2026, 11, 3, 23, 50, 0), dt.datetime(2026, 11, 3, 4, 10, 0)):
        for cfg in ({}, {"lang": "ar", "digits": "arab"}, {"clockfont": "c04", "textfont": "t02"},
                    {"clockfont": "c14", "textfont": "t09"}, {"clockfont": "c06", "textfont": "t03", "lang": "ar"}):
            for amb in (False, True):
                items = state(local, cfg, ambient=amb, complications={"0": {"type": "SHORT_TEXT", "TEXT": "18,532"},
                                                                       "1": {"type": "SHORT_TEXT", "TEXT": "128"}}, battery=100)[1]
                probs += _layout_problems(_boxes(items), (local.time(), str(cfg), amb))
    check("default layout: everything inside the circle, nothing overlapping (30 screens)", not probs, str(probs[:4]))


def t_prayer_gap():
    """v1.3.1: the countdown sits close beside the name/time block - the
    visible gap is about the same for every event, English and Arabic, normal
    and Always-On, and never closes up."""
    import gen_watchface as G
    gaps, bad = {}, []
    date = dt.datetime(2026, 11, 4)
    for cfg in ({}, {"lang": "ar", "digits": "arab"}, {"lang": "ar", "digits": "latn"},
                {"textfont": "t02", "clockfont": "c04"}, {"textfont": "t08", "clockfont": "c13", "lang": "ar"},
                {"textfont": "t06", "clockfont": "c10"}):
        times = oracle_times(date, cfg)
        for key in ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"):
            T = times[key][2]
            for d in (-4000, -600, -59, 300):
                if not 0 <= T + d < 86400:
                    continue
                local = date + dt.timedelta(seconds=T + d)
                for amb in (False, True):
                    boxes = {nm: (x0, x1) for nm, x0, y0, x1, y1 in _boxes(
                        [i for i in state(local, cfg, ambient=amb)[1] if "/prayer" in i["path"]])}
                    info = [boxes[k] for k in ("pname", "ptime") if k in boxes]
                    if "cd" not in boxes or not info:
                        bad.append(("missing", cfg, key, d))
                        continue
                    if cfg.get("lang") == "ar":
                        gap = min(x0 for x0, _ in info) - boxes["cd"][1]
                    else:
                        gap = boxes["cd"][0] - max(x1 for _, x1 in info)
                    gaps.setdefault(str(cfg), []).append(round(gap, 1))
                    hi = G.CD_GAP + (12 if "textfont" not in cfg else 20)   # default text font: tightest
                    if not (G.CD_GAP - 2 <= gap <= hi):
                        bad.append((cfg, key, d, amb, round(gap, 1)))
    summary = "; ".join("%s %s-%s px" % (k, min(v), max(v)) for k, v in gaps.items())
    check("countdown gap never below %d px, default font at most %d px, every event (%s)"
          % (G.CD_GAP - 2, G.CD_GAP + 12, summary), not bad, str(bad[:4]))


def _boxes(items):
    import gen_watchface as G
    boxes = []
    for i in items:
        if i["alpha"] <= 0:
            continue
        if i["kind"] == "text":
            fam = basefam(i["family"])
            fam = fam.rstrip("i") if fam.endswith("i") and len(fam) == 4 else fam
            txt = i["text"]
            tw = G.text_width(txt, fam, i["size"], i["letterSpacing"])
            # spacing glyphs take room but draw nothing: the ink starts after them
            core = txt.strip(SPACE + PAD)
            lead = G.text_width(txt[:len(txt) - len(txt.lstrip(SPACE + PAD))], fam, i["size"]) if core else 0
            trail = G.text_width(txt[len(txt.rstrip(SPACE + PAD)):], fam, i["size"]) if core else 0
            th = 0.74 * i["size"]
            if fam == "icons":                     # the icon glyph's own ink box (one em square)
                tw, th = i["size"] * 0.82, i["size"] * 0.82
            left = {"START": i["x"], "END": i["x"] + i["w"] - tw}.get(i["align"], i["x"] + (i["w"] - tw) / 2)
            if fam != "icons":
                left, tw = left + lead, tw - lead - trail
            cx_ = left + tw / 2
            if fam == "icons":
                cx_ = i["x"] + i["w"] / 2
            cy_ = i["y"] + i["h"] / 2
            if tw > 0:
                boxes.append((i["name"] or i["text"], cx_ - tw / 2, cy_ - th / 2, cx_ + tw / 2, cy_ + th / 2))
        elif i["kind"] == "image":
            boxes.append((i["resource"], i["x"] + 3, i["y"] + 3, i["x"] + i["w"] - 3, i["y"] + i["h"] - 3))
    return boxes


def _layout_problems(boxes, tag):
    probs = []
    for nm, x0, y0, x1, y1 in boxes:
        for px, py in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            if math.hypot(px - 225, py - 225) > 225 - 4:
                probs.append(("outside", nm, tag))
                break
    for a_ in range(len(boxes)):
        for b_ in range(a_ + 1, len(boxes)):
            A, B = boxes[a_], boxes[b_]
            if {A[0], B[0]} <= {"sec", "ampm"} or {A[0], B[0]} == {"picon", "picon_sh"}:
                continue
            if min(A[3], B[3]) - max(A[1], B[1]) > 1 and min(A[4], B[4]) - max(A[2], B[2]) > 1:
                probs.append(("overlap", A[0], B[0], tag))
    return probs


def inject(cfg, j, diff, amb=False, T=None):
    """Make carrier j (0..7) the current event, `diff` seconds from now; T =
    the event's time (seconds after today's midnight; default: any)."""
    if T is None:
        T = 60 * ((50000 - diff) // 60)
    env = E(base_env(dt.datetime(2026, 11, 3, 12, 0, 0)), cfg,
            {"0": {"type": "SHORT_TEXT", "TEXT": "18,532"}, "1": {"type": "SHORT_TEXT", "TEXT": "128"}})
    for i in range(8):
        env["REFERENCE.e%d" % i] = 1.0 if i < j else T / 60.0 + 2000 + (i - j) * 10
    env["SECONDS_IN_DAY"] = Int(T + diff)
    return SIM.visible(env, amb)


def t_layout_fits_largest():
    """Prayer section at its largest: name/time 120 % and countdown 120 %
    together, each event at a real time of day with the widest countdown it
    can show (tabular digits: -1:59 is as wide as any -H:MM; only Fajr can be
    10+ h away; Isha can fall after 10 PM far north), every clock font,
    English and Arabic - side by side, inside the circle, never touching."""
    probs, n = [], 0
    H, M = 3600, 60
    cases = [(7, 29 * H, -(11 * H + 59 * M)), (1, 5 * H, -(1 * H + 59 * M)), (2, 6 * H + 20 * M, -(1 * H + 59 * M)),
             (3, 12 * H + 5 * M, -(5 * H + 59 * M)), (4, 15 * H + 15 * M, -(2 * H + 59 * M)),
             (5, 17 * H + 40 * M, -(2 * H + 59 * M)), (6, 19 * H, -(1 * H + 29 * M)),
             (6, 23 * H + 30 * M, -(1 * H + 29 * M)), (5, 17 * H + 40 * M, 29 * M)]
    tfonts = ["t%02d" % k for k in range(1, 11)]
    for ci in range(1, 21):
        for lang, dg in (("en", "latn"), ("ar", "arab")):
            cfg = {"clockfont": "c%02d" % ci, "textfont": tfonts[ci % 10], "lang": lang, "digits": dg,
                   "size_prayer": "n120a120", "size_countdown": "n120a120"}
            for j, T, diff in cases:
                for amb in (False, True):
                    items = [i for i in inject(cfg, j, diff, amb, T) if "/prayer" in i["path"]]
                    n += 1
                    probs += _layout_problems(_boxes(items), (cfg["clockfont"], cfg["textfont"], lang, j, T, amb))
    check("prayer section at 120 %% + 120 %%: side by side, inside the circle, no overlap (%d screens)" % n,
          not probs, str(probs[:4]))


AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"


def localise(s, lang):
    """What an Arabic system locale does to platform/provider text."""
    if lang != "ar":
        return s
    return "".join(AR_DIGITS[int(c)] if c.isdigit() and c < "\u0660" else c for c in s).replace(",", "\u066c")


def t_no_locale_formatted_sources():
    import xml.etree.ElementTree as ET, re
    txt = open(XML, encoding="utf-8").read()
    bad = sorted(set(re.findall(r"\[(?:[A-Z0-9_]+_Z|MONTH_[FS]|DAY_OF_WEEK_[FS]|AMPM_STRING[A-Z_]*|TIMEZONE[A-Z_]*ABB|YEAR_S)\]", txt)))
    bad += [f for f in ("numberFormat", "icuText", "icuBestText") if f in txt]
    check("no number on the face is formatted by the system locale (no *_Z, month/day names, numberFormat, icuText)",
          not bad, str(bad))


def t_numerals_independent_of_system_language():
    """System language English or Arabic, chosen numerals Western or
    Arabic-Indic, normal and Always-On: every digit drawn must be in the chosen
    system.  Platform strings and Samsung Health text are fed in localised
    (Arabic-Indic digits, Arabic thousands separator) to prove they are
    normalised by the fonts."""
    from fontTools.ttLib import TTFont
    res = os.path.dirname(os.path.dirname(XML))
    fonts = {}

    def cmap(fam):
        """code point -> glyph id (both numeral files share one glyph order)."""
        if fam not in fonts:
            ft = TTFont(os.path.join(res, "font", fam + ".ttf"))
            fonts[fam] = {cp: ft.getGlyphID(g) for cp, g in ft.getBestCmap().items()}
        return fonts[fam]
    bad, n = [], 0
    for syslang in ("en", "ar"):
        for lang, dg in (("en", "latn"), ("en", "arab"), ("ar", "arab"), ("ar", "latn")):
            for cfg_extra in ({}, {"clockfont": "c13", "textfont": "t08"}, {"clockfont": "c06", "textfont": "t03"}):
                for amb in (False, True):
                    local = dt.datetime(2026, 11, 3, 23, 3, 30)
                    b = base_env(local)
                    b["MINUTE_Z"], b["SECOND_Z"] = localise(b["MINUTE_Z"], syslang), localise(b["SECOND_Z"], syslang)
                    comps = {"0": {"type": "SHORT_TEXT", "TEXT": localise("1,571", syslang)},
                             "1": {"type": "SHORT_TEXT", "TEXT": localise("73", syslang)}}
                    cfg = dict(cfg_extra, lang=lang, digits=dg)
                    env = E(b, cfg, comps)
                    for i in SIM.visible(env, amb):
                        if i["kind"] != "text" or i["alpha"] <= 0:
                            continue
                        fam = i["family"]
                        base, tag = basefam(fam), fam[len(basefam(fam)):]
                        if base == "icons":
                            continue
                        west = (base[:-1] if base.endswith("i") else base) + tag
                        lat = {cmap(west)[0x30 + k] for k in range(10)}
                        ind = {cmap(basefam(west) + "i" + tag)[0x30 + k] for k in range(10)}
                        for ch in i["text"]:
                            cp = ord(ch)
                            if 0x30 <= cp <= 0x39 or 0x660 <= cp <= 0x669 or 0x6F0 <= cp <= 0x6F9:
                                n += 1
                                g = cmap(fam).get(cp)
                                kind = "latn" if g in lat else "arab" if g in ind else "missing"
                                if kind != dg:
                                    bad.append((syslang, lang, dg, amb, i["name"], i["text"], kind))
                            if cp == 0x66C and cmap(fam).get(cp) != cmap(west).get(0x2C):
                                bad.append((syslang, lang, dg, amb, i["name"], "separator", i["text"]))
    check("system language English or Arabic: every one of %d digits drawn in the chosen numerals "
          "(clock, seconds, date, prayer time, countdown, steps, heart rate, battery; normal + Always-On)" % n,
          not bad, str(bad[:4]))


def t_float32_robustness():
    """The watch may evaluate expressions in 32-bit floats. Re-evaluate every
    prayer time for every city and 24 dates in float32 and compare."""
    import datetime as _dt
    diffs = []
    n = 0
    for c in data.CITIES:
        for m in range(1, 13):
            for day in (1, 15):
                local = _dt.datetime(2026, m, day, 12, 0, 0)
                off = int(round(c[4] / 15.0)) * 60
                b = base_env(local, off)
                wffsim.FLOAT32 = False
                e64 = E(b, {"city": c[0]})
                wffsim.FLOAT32 = True
                e32 = E(b, {"city": c[0]})
                wffsim.FLOAT32 = False
                for k in e64:
                    if k.startswith("REFERENCE."):
                        n += 1
                        if abs(e64[k] - e32[k]) > 0.5:
                            diffs.append((c[0], m, day, k, e64[k], e32[k]))
    check("float32 evaluation gives the same minute for %d prayer times (%d differ by 1 min)" % (n, len(diffs)),
          len(diffs) <= n * 0.01, str(diffs[:5]))


def main():
    tests = [t_icon_font, t_countdown_characters, t_no_reference_in_conditions, t_regression_v121_frozen_name, t_name_icon_time_countdown_agree,
             t_no_locale_formatted_sources, t_numerals_independent_of_system_language, t_size_settings, t_layout_fits, t_layout_fits_largest, t_prayer_gap, t_no_whitespace_in_text, t_option_pictures_and_strings, t_every_font_complete,
             t_float32_robustness, t_all_expressions_parse, t_countdown_examples, t_boundaries_dubai, t_shuruq_not_a_prayer,
             t_midnight_and_new_year, t_london_isha_after_midnight, t_date_and_clock, t_arabic_and_numerals,
             t_ambient, t_complications_and_battery, t_engine_unavailable, t_settings_effects, t_all_cities_order,
             lambda: t_rotation_full_day(dt.date(2026, 11, 3)),
             lambda: t_rotation_full_day(dt.date(2026, 6, 21), step=30),
             lambda: t_rotation_full_day(dt.date(2026, 12, 21), "mak", 180, step=30, config_extra={"method": "uqu", "lang": "ar"})]
    for t in tests:
        try:
            t()
        except Exception:
            check(getattr(t, "__name__", "test"), False, traceback.format_exc()[-600:])
    passed = sum(1 for r in RESULTS if r[1])
    print("\n%d/%d checks passed" % (passed, len(RESULTS)))
    out = os.environ.get("TEST_RESULTS", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "test_results.json"))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    json.dump([dict(check=n, passed=p, detail=d) for n, p, d in RESULTS],
              open(os.path.abspath(out), "w"), indent=1, ensure_ascii=False)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    XML = sys.argv[1]
    SIM = wffsim.Sim(XML)
    sys.exit(main())
