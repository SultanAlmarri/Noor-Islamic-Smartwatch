"""Scores the frozen predictions against every official month collected so far. Writes REPORT.md."""
import csv, glob, os
here = os.path.dirname(__file__)
P = ['fajr', 'shuruq', 'dhuhr', 'asr', 'maghrib', 'isha']
pred = {r['date']: r for r in csv.DictReader(open(os.path.join(here, 'predictions_dubai_c1.csv')))}
lines = ['# Blind test: Dubai profile C1 vs official IACAD months', '',
         'Predictions frozen on 4 November 2026 (`predictions_dubai_c1.csv`, SHA-256 be4e2ab2…0b0e202abc) before any of these months was published.', '',
         '| Month | Times | Exact | Rate | Largest difference |', '|---|---|---|---|---|']
misses = []
for path in sorted(glob.glob(os.path.join(here, 'official', 'iacad_*.csv'))):
    rows = [r for r in csv.DictReader(l for l in open(path) if not l.startswith('#'))]
    n = exact = worst = 0
    for r in rows:
        if r['date'] not in pred: continue
        for p in P:
            a = int(r[p][:2]) * 60 + int(r[p][3:5]); b = int(pred[r['date']][p][:2]) * 60 + int(pred[r['date']][p][3:5])
            n += 1; exact += a == b; worst = max(worst, abs(a - b))
            if a != b: misses.append('| %s | %s | %s | %s | %+d |' % (r['date'], p, r[p], pred[r['date']][p], a - b))
    if n: lines.append('| %s | %d | %d | %.1f%% | %d min |' % (os.path.basename(path)[6:13], n, exact, 100.0 * exact / n, worst))
lines += ['', '## Every difference', '', '| Date | Prayer | Official | Predicted | Official − predicted |', '|---|---|---|---|---|'] + (misses or ['| — | — | — | — | — |'])
open(os.path.join(here, 'REPORT.md'), 'w').write('\n'.join(lines) + '\n')
print('\n'.join(lines[:12]))
