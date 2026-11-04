"""Validate the existing watch-face engine (v1.3.2, read-only copy) against the official datasets."""
import sys, os, datetime as dt, csv
sys.dont_write_bytecode = True
sys.path.insert(0, 'engine_v132'); sys.path.insert(0, 'tools')
import data, engine
from compare import env_for
import analyse as A
P = engine.Params(); EXPR = {ev: engine.event_seconds(P, ev) for ev in data.EVENTS}
MAP = dict(zip(data.EVENTS, A.PR))
W = A.load('data/iacad_official_archive_2014_2023.csv'); api = A.load('data/iacad_api_2026-11.iso.csv'); OFF = {**W, **api}
def era(d): return 'E1' if d < dt.date(2017, 9, 21) else ('E2' if d < dt.date(2020, 8, 20) else 'E3')
rows = []
for d in sorted(OFF):
    env = env_for(d)
    for ev in data.EVENTS:
        p = MAP[ev]; mine = int(EXPR[ev].eval(env)) // 60
        rows.append((d, era(d), p, OFF[d][p], mine, OFF[d][p] - mine))
with open('results/current_engine_vs_official.csv', 'w', newline='') as f:
    w = csv.writer(f); w.writerow(['date', 'era', 'prayer', 'official', 'engine_v1.3.2', 'official_minus_engine'])
    for d, e, p, o, m, x in rows: w.writerow([d, e, p, '%02d:%02d' % divmod(o, 60), '%02d:%02d' % divmod(m, 60), x])
from collections import Counter, defaultdict
S = defaultdict(Counter)
for d, e, p, o, m, x in rows: S[(e, p)][x] += 1; S[(e, 'ALL')][x] += 1
for e in ('E3', 'E2', 'E1'):
    print(e, ' '.join('%s %d/%d' % (p[:3], S[(e, p)][0], sum(S[(e, p)].values())) for p in A.PR + ['ALL']),
          ' dist', dict(sorted(S[(e, 'ALL')].items())))
