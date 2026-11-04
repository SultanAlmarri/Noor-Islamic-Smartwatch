"""End-to-end check of the icon font (v1.3): every icon glyph is drawn by
Chromium (Skia + HarfBuzz + FreeType - the same text stack family as Wear OS)
exactly the way the watch face lays it out (one character centred in its box,
line height = font size), at 96 px, and compared with the PNG icon drawn from
the same SVG.  Fails if any icon differs visibly or is off-centre.

Usage: python3 check_icons.py <icons.ttf> <icons-png-dir> <report.json>
"""
import base64, numpy as np, io, sys, json
from PIL import Image
from playwright.sync_api import sync_playwright
ORDER=['fajr','sunrise','dhuhr','asr','maghrib','isha']
font = base64.b64encode(open(sys.argv[1],'rb').read()).decode()
def cell(ch, op=1.0):
    return ('<div style="position:absolute;left:0;top:0;width:96px;height:96px;display:flex;align-items:center;'
            'justify-content:center;opacity:%.3f"><span style="font-family:ic;font-size:96px;line-height:1;color:#fff">%s</span></div>' % (op, ch))
res = {}
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={'width':96,'height':96})
    for i, ev in enumerate(ORDER):
        body = cell(chr(0xE001+i)) + (cell(chr(0xE008), 0.55) if ev=='asr' else '')
        pg.set_content('<html><head><style>@font-face{font-family:ic;src:url(data:font/ttf;base64,%s)} body{margin:0;background:#000}</style></head><body>%s</body></html>' % (font, body))
        pg.wait_for_timeout(150)
        g = np.array(Image.open(io.BytesIO(pg.screenshot())).convert('L'))/255.
        ref = np.array(Image.open('%s/ic_%s.png'%(sys.argv[2],ev)).convert('RGBA'))[:,:,3]/255.
        ys,xs=np.indices(g.shape)
        com = lambda a:( (a*xs).sum()/a.sum(), (a*ys).sum()/a.sum())
        (gx,gy),(rx,ry)=com(g),com(ref)
        res[ev]=(np.abs(g-ref).mean(), np.minimum(g,ref).sum()/np.maximum(g,ref).sum(), gx-rx, gy-ry)
    b.close()
out = {k: dict(mean_abs_diff=round(m, 4), iou=round(iou, 4), dx_px=round(dx, 2), dy_px=round(dy, 2)) for k, (m, iou, dx, dy) in res.items()}
json.dump(out, open(sys.argv[3], "w"), indent=1)
bad = [k for k, v in out.items() if v["iou"] < 0.95 or abs(v["dx_px"]) > 0.5 or abs(v["dy_px"]) > 0.5]
for k, v in out.items():
    print("icon %-8s IoU %.3f  offset %+.2f/%+.2f px" % (k, v["iou"], v["dx_px"], v["dy_px"]))
sys.exit(1 if bad else 0)
