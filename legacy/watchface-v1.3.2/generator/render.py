"""Render simulator states of the generated watch face to PNG (via Chromium).

Used for the watch-face picker preview (res/drawable-nodpi/preview.png) and
for the review images.  Every pixel comes from the generated watchface.xml
as interpreted by wffsim - but this is a simulation, not the Wear OS renderer.
"""
import os, sys, html, asyncio, datetime as dt, base64
sys.path.insert(0, os.path.dirname(__file__))
import wffsim
from tests import base_env
from playwright.sync_api import sync_playwright

RES = None
BODY_BG = '#111'


def css_color(c, alpha=255):
    if c is None:
        return "transparent"
    c = c.lstrip("#")
    if len(c) == 8:
        a, r, g, b = int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), int(c[6:8], 16)
    else:
        a, r, g, b = 255, int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)
    return "rgba(%d,%d,%d,%.3f)" % (r, g, b, a / 255.0)


def page(items, res_dir, ambient=False):
    fonts = sorted(set(i["family"] for i in items if i["kind"] == "text"))
    ff = "".join("@font-face{font-family:'%s';src:url('file://%s/font/%s.ttf');}" % (f, res_dir, f) for f in fonts)
    out = []
    for i in items:
        if i["alpha"] <= 0:
            continue
        op = i["alpha"] / 255.0
        if i["kind"] == "text":
            jc = {"START": "flex-start", "CENTER": "center", "END": "flex-end"}[i["align"]]
            out.append('<div style="position:absolute;left:%.1fpx;top:%.1fpx;width:%.1fpx;height:%.1fpx;'
                       'display:flex;align-items:center;justify-content:%s;opacity:%.3f;">'
                       '<span style="font-family:\'%s\';font-size:%.1fpx;color:%s;letter-spacing:%.3fem;'
                       'white-space:pre;unicode-bidi:plaintext;line-height:1">%s</span></div>'
                       % (i["x"], i["y"], i["w"], i["h"], jc, op, i["family"], i["size"],
                          css_color(i["color"]), i["letterSpacing"], html.escape(i["text"])))
        elif i["kind"] == "image":
            png = "data:image/png;base64," + base64.b64encode(
                open("%s/drawable-nodpi/%s.png" % (res_dir, i["resource"]), "rb").read()).decode()
            out.append('<div style="position:absolute;left:%.1fpx;top:%.1fpx;width:%.1fpx;height:%.1fpx;'
                       'opacity:%.3f;background:%s;-webkit-mask:url(%s) center/contain no-repeat;"></div>'
                       % (i["x"], i["y"], i["w"], i["h"], op, css_color(i["tint"]), png))
        elif i["kind"] == "rect":
            border = "%.1fpx solid %s" % (i["thickness"], css_color(i["stroke"])) if i["stroke"] else "none"
            out.append('<div style="position:absolute;box-sizing:border-box;left:%.1fpx;top:%.1fpx;width:%.1fpx;'
                       'height:%.1fpx;opacity:%.3f;background:%s;border:%s;border-radius:%.1fpx"></div>'
                       % (i["x"], i["y"], i["w"], i["h"], op, css_color(i["fill"]), border, i["radius"]))
    return ('<html><head><style>%s body{margin:0;background:%s}</style></head><body>'
            '<div style="position:relative;width:450px;height:450px;border-radius:50%%;overflow:hidden;background:#000">'
            '%s</div></body></html>' % (ff, BODY_BG, "".join(out)))


def render(states, res_dir, out_dir, scale=2):
    sim = wffsim.Sim(os.path.join(res_dir, "raw", "watchface.xml"))
    os.makedirs(out_dir, exist_ok=True)
    paths = []
    with sync_playwright() as p:
        b = p.chromium.launch()
        ctx = b.new_context(viewport={"width": 450, "height": 450}, device_scale_factor=scale)
        pg = ctx.new_page()
        for name, local, cfg, ambient, comps, batt in states:
            env = sim.env(base_env(local, battery=batt), cfg, comps)
            items = sim.visible(env, ambient)
            f = os.path.join(out_dir, name + ".html")
            open(f, "w").write(page(items, os.path.abspath(res_dir), ambient))
            pg.goto("file://" + os.path.abspath(f))
            pg.wait_for_timeout(250)
            png = os.path.join(out_dir, name + ".png")
            pg.screenshot(path=png, omit_background=False)
            paths.append(png)
            os.remove(f)
        b.close()
    return paths


HEALTH = {"0": {"type": "SHORT_TEXT", "TEXT": "8,532"}, "1": {"type": "SHORT_TEXT", "TEXT": "72"}}  # sample values

if __name__ == "__main__":
    res_dir = sys.argv[1]
    out_dir = sys.argv[2]
    d = dt.datetime(2026, 11, 3)
    states = [
        ("en_asr_countdown", d.replace(hour=15, minute=5, second=55), {}, False, HEALTH, 76),
        ("ar_arabic_digits", d.replace(hour=15, minute=5, second=55), {"langnum": "ar_arab"}, False, HEALTH, 76),
        ("en_hours_countdown", d.replace(hour=10, minute=57, second=25), {"clockfont": "c03", "textfont": "t02", "accent": "sky"}, False, HEALTH, 64),
        ("ar_western_digits", d.replace(hour=6, minute=33, second=12), {"langnum": "ar_latn", "clockfont": "c13", "textfont": "t08", "accent": "gold", "clockcolor": "warm"}, False, HEALTH, 64),
        ("en_bold_coral", d.replace(hour=17, minute=45, second=10), {"clockfont": "c06", "textfont": "t03", "accent": "coral"}, False, HEALTH, 41),
        ("en_fajr_long_wait", d.replace(hour=19, minute=21, second=40), {}, False, HEALTH, 58),
        ("en_fajr_before_dawn", d.replace(hour=4, minute=3, second=20), {}, False, HEALTH, 81),
        ("ar_fajr_before_dawn", d.replace(hour=4, minute=3, second=20), {"langnum": "ar_arab"}, False, HEALTH, 81),
        ("en_maghrib_sizes_120", d.replace(hour=16, minute=0, second=30), {"size_prayer": "n120a100", "size_countdown": "n120a100", "clockfont": "c10", "textfont": "t08"}, False, HEALTH, 76),
        ("ar_maghrib_sizes_120", d.replace(hour=16, minute=0, second=30), {"langnum": "ar_arab", "size_prayer": "n120a100", "size_countdown": "n120a100", "clockfont": "c04", "textfont": "t03"}, False, HEALTH, 76),
        ("en_sizes_custom", d.replace(hour=11, minute=2, second=48), {"clockfont": "c14", "textfont": "t09", "accent": "violet", "clockcolor": "lavender", "size_clock": "n90a100", "size_countdown": "n120a100", "size_steps": "n110a100", "size_hr": "n110a100", "size_battery": "n110a100"}, False, HEALTH, 88),
        ("en_always_on", d.replace(hour=10, minute=57, second=0), {}, True, HEALTH, 76),
        ("ar_always_on", d.replace(hour=15, minute=7, second=0), {"langnum": "ar_arab"}, True, HEALTH, 76),
        ("en_always_on_big", d.replace(hour=10, minute=57, second=0), {"size_clock": "n100a120", "size_countdown": "n100a120", "size_prayer": "n100a80"}, True, HEALTH, 76),
    ]
    for p in render(states, res_dir, out_dir, scale=2):
        print(p)
    # watch-face picker preview: 450x450, black outside the circle
    BODY_BG = "#000"
    globals()["BODY_BG"] = "#000"
    prev = render([states[0]], res_dir, "/tmp/preview_render", scale=1)[0]
    import shutil
    shutil.copy(prev, os.path.join(res_dir, "drawable-nodpi", "preview.png"))
    print("preview ->", os.path.join(res_dir, "drawable-nodpi", "preview.png"))
