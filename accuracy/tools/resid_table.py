import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_2019_2026.pkl','rb'))
sets={'A 2026-11 official':A.load('data/iacad_api_2026-11.iso.csv'),
      'B 2020 Ramadan (2 rows excl.)':{d:v for d,v in A.load('data/iacad_via_nestle_ramadan_2020.csv').items() if d not in {dt.date(2020,4,30),dt.date(2020,5,1)}},
      'C SC 2026-11':{d:v for d,v in A.load('data/salatcalendar_official_dubai_2019_2026.csv').items() if d.year==2026 and d.month==11},
      'C SC 2020 Ramadan':{d:v for d,v in A.load('data/salatcalendar_official_dubai_2019_2026.csv').items() if dt.date(2020,4,24)<=d<=dt.date(2020,5,23) and d not in {dt.date(2020,4,30),dt.date(2020,5,1)}},
      'C SC all 2019-2026':A.load('data/salatcalendar_official_dubai_2019_2026.csv')}
lat,lon=25.20,55.27
par={'fajr':18.0,'isha':18.0,'shuruq':0.19,'maghrib':0.19,'dhuhr':None,'asr':None}
print('reference model: lat %.2f lon %.2f, Fajr/Isha 18.0, horizon SD+0.5667+0.19 (≈30 m, Odeh dip), Asr shadow 1; u = published - model (minutes)'%(lat,lon))
for name,P in sets.items():
    S=A.subset_basis(B,sorted(P))
    cells=[]
    for pr in A.PR:
        u=A.residuals(S,P,pr,lat,lon,par[pr]); cells.append('%s [%.2f,%.2f]'%(pr[:3],u.min(),u.max()))
    print('%-30s'%name,' '.join(cells))
