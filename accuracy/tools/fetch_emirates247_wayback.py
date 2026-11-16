"""Recovers Dubai's daily prayer times as Emirates 24|7 showed them, from the Internet Archive's captures of
https://www.emirates247.com/prayer-time (2023-2026). Emirates 24|7 republishes IACAD's times ("sourced from the
Islamic Affairs and Charitable Activities Department"); its copy was identical to IACAD's own feed on all 180 times
of November 2026 (validate.py, integrity test 3), and this script re-checks that on every capture that falls in
November 2026. Tier: secondary copy of IACAD, used for validation only (never shown in the app).

Only captures made on the day the page shows are kept (Dubai clock); a date whose captures disagree is dropped.
One capture per day (the CDX "collapse" option), one request at a time with a pause between them.
usage: python3 accuracy/tools/fetch_emirates247_wayback.py   (writes accuracy/data/iacad_via_emirates247_wayback.csv)
"""
import os, re, gzip, json, time, datetime as dt, urllib.request

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(ROOT, 'data', 'iacad_via_emirates247_wayback.csv')
UA = {'User-Agent': 'accuracy-research/1.0 (one capture at a time)'}
MONTHS = {m: i + 1 for i, m in enumerate(['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August',
                                          'September', 'October', 'November', 'December'])}
KEYS = ['fajr', 'shorooq', 'duhr', 'asr', 'magrib', 'isha']


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90) as r:
        b = r.read()
    if b[:2] == b'\x1f\x8b':          # some captures are stored gzip-compressed
        b = gzip.decompress(b)
    return b.decode('utf-8', 'replace')


def parse(html):
    """(date shown above the main table, six Dubai times) or None."""
    main = html.find('widget prayerholder')
    if main < 0:
        return None
    m = re.search(r'<li class="el1 prayer[^"]*">.*?<span class="location">Dubai</span>(.*?)</li>', html[main:], re.S)
    if not m:
        return None
    t = dict(re.findall(r'<span class="time (\w+)">(\d\d:\d\d)</span>', m.group(1)))
    if any(k not in t for k in KEYS):
        return None
    head = re.findall(r'>\s*(\d{1,2}) (January|February|March|April|May|June|July|August|September|October|November|December) (20\d\d)\s*<', html)
    if not head:
        return None
    d, mon, y = head[0]
    return dt.date(int(y), MONTHS[mon], int(d)), [t[k] for k in KEYS]


def main():
    cdx = json.loads(get('https://web.archive.org/cdx/search/cdx?url=emirates247.com/prayer-time&output=json'
                         '&from=2023&filter=statuscode:200&collapse=timestamp:8'))[1:]
    rows, conflicts = {}, set()
    for c in cdx:
        ts = c[1]
        try:
            html = get('https://web.archive.org/web/%sid_/%s' % (ts, c[2]))
        except Exception as e:
            print('skip', ts, e)
            continue
        p = parse(html)
        time.sleep(1.5)
        if not p:
            print('no table', ts)
            continue
        d, t = p
        cap = dt.datetime.strptime(ts, '%Y%m%d%H%M%S') + dt.timedelta(hours=4)   # capture time, Dubai clock
        if cap.date() != d:
            # the page may be served from a cache made the day before: keep only captures of the day they show
            print('date shown %s differs from capture day %s: skipped' % (d, cap))
            continue
        if d in rows and rows[d][0] != t:
            print('conflict', d, rows[d][0], t, ': date dropped')
            conflicts.add(d)
        rows.setdefault(d, (t, ts))
        print(d, t, ts)
    with open(OUT, 'w') as f:
        f.write('# Emirates 24|7 prayer-time page (republishes IACAD, Dubai), recovered from Internet Archive captures.\n')
        f.write('# Tier: secondary copy of IACAD. Date = the date the page shows above its table. Column wayback = capture id.\n')
        f.write('date,fajr,shuruq,dhuhr,asr,maghrib,isha,wayback\n')
        for d in sorted(set(rows) - conflicts):
            f.write('%s,%s,%s\n' % (d, ','.join(rows[d][0]), rows[d][1]))
    print('saved', OUT, len(set(rows) - conflicts), 'days')


if __name__ == '__main__':
    main()
