import sys, numpy as np, datetime as dt, pickle
sys.path.insert(0,'tools'); import analyse as A, hourangle as HA
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv'); P={**W,**api}
C=A.load('data/salatcalendar_official_dubai_2019_2026.csv')
sets={'SC official':C,'IACAD E3':{d:v for d,v in P.items() if d>=dt.date(2020,8,20)},'IACAD E2':{d:v for d,v in P.items() if dt.date(2017,10,15)<=d<dt.date(2020,8,20)}}
cache={}
for name,Q in sets.items():
    ds=sorted(Q); pub={p:np.array([Q[d][p] for d in ds],float) for p in A.PR}
    print('==',name,len(ds))
    for href in (0,4,8,12):
        dec,eot=HA.sun_at(ds,href)
        best={}
        for la in np.arange(24.95,25.351,0.01):
            for ang in np.arange(17.0,19.01,0.1):
                for hz in (-0.8333,-0.8333-0.097,-0.8333-0.19):
                    t=HA.times(ds,la,55.3,dec,eot,ang,ang,hz)
                    for p in A.PR:
                        u=pub[p]-t[p]; sp=u.max()-u.min()
                        if p not in best or sp<best[p][0]: best[p]=(sp,round(la,2),round(ang,1),hz)
        print('  delta/EoT at %2d h UT: '%href+'  '.join('%s %.2f(lat %.2f%s)'%(p[:3],best[p][0],best[p][1],(' a%.1f'%best[p][2]) if p in('fajr','isha') else (' hz%.2f'%best[p][3] if p in ('shuruq','maghrib') else '')) for p in A.PR))
