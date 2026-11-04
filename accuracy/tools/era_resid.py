import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_all.pkl','rb'))
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv')
P={**W,**api}
eras={'E1 <=2017-09-19':lambda d:d<=dt.date(2017,9,19),'E2 2017-10..2020-11':lambda d:dt.date(2017,10,1)<=d<=dt.date(2020,12,31),'E3 2021-09..2026-11':lambda d:d>=dt.date(2021,1,1)}
lat,lon=25.20,55.27
par={'fajr':18.0,'isha':18.0,'shuruq':0.19,'maghrib':0.19,'dhuhr':None,'asr':None}
for en,f in eras.items():
    Q={d:v for d,v in P.items() if f(d)}; S=A.subset_basis(B,sorted(Q))
    print('==',en,'days',len(Q))
    U={pr:A.residuals(S,Q,pr,lat,lon,par[pr]) for pr in A.PR}
    ds=sorted(Q)
    # by month
    for m in range(1,13):
        idx=[i for i,d in enumerate(ds) if d.month==m]
        if not idx: continue
        print('  %02d n=%3d '%(m,len(idx))+' '.join('%s[%5.2f,%5.2f]'%(pr[:3],U[pr][idx].min(),U[pr][idx].max()) for pr in A.PR))
    print('  ALL       '+' '.join('%s[%5.2f,%5.2f]'%(pr[:3],U[pr].min(),U[pr].max()) for pr in A.PR))
