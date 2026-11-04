import json, datetime, csv, sys
d = json.load(open('data/salatcalendar_official_dubai_enc.json'))
out = []
ok = True
for y in sorted(k for k in d if not k.startswith('_')):
    e = d[y]['enc']; vals = [int(e[i*4:i*4+4]) for i in range(6)]; rows=[vals[:]]
    body = e[24:]
    if len(body) % 6: print(y, 'bad length', len(body)); ok=False; continue
    for i in range(0, len(body), 6):
        vals = [v + ord(c) - 109 for v, c in zip(vals, body[i:i+6])]; rows.append(vals[:])
    s = sum(map(sum, rows))
    good = len(rows) == d[y]['n'] and s == d[y]['sum']
    ok &= good
    print(y, 'days', len(rows), 'n', d[y]['n'], 'sum', s, d[y]['sum'], 'OK' if good else 'MISMATCH')
    day = datetime.date(int(y), 1, 1)
    for r in rows:
        out.append([day.isoformat()] + ['%02d:%02d' % divmod(v, 60) for v in r]); day += datetime.timedelta(1)
w = csv.writer(open('data/salatcalendar_official_dubai_2019_2026.csv', 'w', newline=''))
w.writerow(['date','fajr','shuruq','dhuhr','asr','maghrib','isha'])
w.writerows(out)
sys.exit(0 if ok else 1)
