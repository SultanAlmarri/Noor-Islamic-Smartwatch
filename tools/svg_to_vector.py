"""Converts the watch face's prayer icons (simple SVG shapes) into Android VectorDrawables."""
import re, sys, os, xml.etree.ElementTree as ET
SRC = sys.argv[1]; DST = sys.argv[2]
def f(x): return ('%.2f' % float(x)).rstrip('0').rstrip('.')
for name in ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha']:
    root = ET.parse(os.path.join(SRC, 'ic_%s.svg' % name)).getroot()
    vb = root.get('viewBox').split()
    paths = []
    for el in root.iter():
        tag = el.tag.split('}')[-1]
        op = el.get('opacity')
        alpha = '' if op is None else ' android:%sAlpha="%s"' % ('stroke' if el.get('stroke') else 'fill', op)
        if tag == 'path':
            paths.append('<path android:fillColor="#FFFFFFFF" android:pathData="%s"%s/>' % (el.get('d'), alpha))
        elif tag == 'polygon':
            pts = el.get('points').split()
            d = 'M' + ' L'.join(p.replace(',', ' ') for p in pts) + ' Z'
            paths.append('<path android:fillColor="#FFFFFFFF" android:pathData="%s"%s/>' % (d, alpha))
        elif tag == 'circle':
            cx, cy, r = (float(el.get(k)) for k in ('cx', 'cy', 'r'))
            d = 'M%s %s a%s %s 0 1 0 %s 0 a%s %s 0 1 0 %s 0 Z' % (f(cx - r), f(cy), f(r), f(r), f(2 * r), f(r), f(r), f(-2 * r))
            if el.get('fill', 'none') != 'none':
                paths.append('<path android:fillColor="#FFFFFFFF" android:pathData="%s"%s/>' % (d, alpha))
            if el.get('stroke'):
                paths.append('<path android:strokeColor="#FFFFFFFF" android:strokeWidth="%s" android:pathData="%s"%s/>' % (el.get('stroke-width', '1'), d, alpha))
        elif tag == 'line':
            d = 'M%s %s L%s %s' % tuple(f(el.get(k)) for k in ('x1', 'y1', 'x2', 'y2'))
            cap = el.get('stroke-linecap', 'butt')
            paths.append('<path android:strokeColor="#FFFFFFFF" android:strokeWidth="%s" android:strokeLineCap="%s" android:strokeLineJoin="round" android:pathData="%s"%s/>' % (el.get('stroke-width', '1'), cap, d, alpha))
        elif tag == 'polyline':
            pts = el.get('points').split()
            d = 'M' + ' L'.join(p.replace(',', ' ') for p in pts)
            paths.append('<path android:strokeColor="#FFFFFFFF" android:strokeWidth="%s" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="%s"%s/>' % (el.get('stroke-width', '1'), d, alpha))
        elif tag not in ('svg',):
            raise SystemExit('unsupported element %s in %s' % (tag, name))
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from the Noor watch face icon ic_%s.svg by tools/svg_to_vector.py -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" '
           'android:viewportWidth="%s" android:viewportHeight="%s">\n    %s\n</vector>\n') % (name, vb[2], vb[3], '\n    '.join(paths))
    open(os.path.join(DST, 'ic_prayer_%s.xml' % name), 'w').write(xml)
    print(name, len(paths), 'paths')
