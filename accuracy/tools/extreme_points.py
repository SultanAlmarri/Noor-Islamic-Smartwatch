"""Hypothesis: city-wide precaution - each start time computed at the city's latitude extreme that
gives the LATEST time (Shuruq: EARLIEST), i.e. t = max/min over {lat_S, lat_N} (longitude only shifts
all times uniformly and is absorbed by the constant shift)."""
import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_all.pkl','rb'))
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv'); P={**W,**api}
C=A.load('data/salatcalendar_official_dubai_2019_2026.csv')
sets={'IACAD E3':{d:v for d,v in P.items() if d>=dt.date(2020,8,20)},'IACAD E2':{d:v for d,v in P.items() if dt.date(2017,10,15)<=d<dt.date(2020,8,20)},'SC official':C}
LAT_S=np.round(np.arange(24.80,25.151,0.025),3); LAT_N=np.round(np.arange(25.15,25.451,0.025),3)
def tev(S,p,la,par):
    return A.times(S,p,la,55.3,par)
for name,Q in sets.items():
    ds=sorted(Q); S=A.subset_basis(B,ds); pub={p:np.array([Q[d][p] for d in ds],float) for p in A.PR}
    print('==',name,len(ds))
    for p,params in (('fajr',np.round(np.arange(17.4,18.81,0.1),2)),('isha',np.round(np.arange(17.4,18.81,0.1),2)),('asr',[None]),('maghrib',[0.0,0.1,0.19,0.3]),('shuruq',[0.0,0.1,0.19,0.3])):
        cache={}
        best=(9,)
        for par in params:
            for la in set(LAT_S)|set(LAT_N): cache[la]=tev(S,p,la,par)
            for s in LAT_S:
                for n_ in LAT_N:
                    t=np.minimum(cache[s],cache[n_]) if p=='shuruq' else np.maximum(cache[s],cache[n_])
                    u=pub[p]-t/60; sp=u.max()-u.min()
                    if sp<best[0]: best=(sp,s,n_,par)
        print('  %-8s min spread %.3f  latS %.3f latN %.3f param %s'%(p,best[0],best[1],best[2],best[3]))
