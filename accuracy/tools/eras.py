import sys, pickle, numpy as np, datetime as dt, csv
sys.path.insert(0,'tools'); import analyse as A
W=A.load('data/iacad_official_archive_2014_2023.csv')
api=A.load('data/iacad_api_2026-11.iso.csv')
allp={**W, **api}
# gaps
print('date        fajr->shuruq  maghrib->isha')
for d in sorted(allp):
    if d.day in (1,15) or d.year>=2019 or d in (dt.date(2015,1,7),dt.date(2016,3,2),dt.date(2016,12,30),dt.date(2017,9,19),dt.date(2017,10,1)):
        r=allp[d]; print(d, r['shuruq']-r['fajr'], r['isha']-r['maghrib'])
