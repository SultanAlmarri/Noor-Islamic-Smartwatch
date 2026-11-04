import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A, model as M
import pvlib.spa as spa
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv'); P={**W,**api}
sets={'E3':{d:v for d,v in P.items() if d>=dt.date(2020,8,20)},'E2':{d:v for d,v in P.items() if dt.date(2017,10,15)<=d<dt.date(2020,8,20)}}
def eot_at(ds, hour_ut):
    ut=np.array([(dt.datetime(d.year,d.month,d.day)-dt.datetime(1970,1,1)).total_seconds()+hour_ut*3600 for d in ds])
    r=spa.solar_position_numpy(ut,25.2,55.27,0,1010,10,M.DELTA_T,0.5667,1); return r[5]
def best(u):
    u=np.sort(u); c=np.searchsorted(u,u+1-1e-9)-np.arange(len(u)); k=int(np.argmax(c)); return c[k],u[k],u[k+c[k]-1],u[-1]-u[0]
for name,Q in sets.items():
    ds=sorted(Q); pub=np.array([Q[d]['dhuhr'] for d in ds],float)
    for h in [-4,0,4,8,12,16,20]:
        t=720+4*(60-55.27)-eot_at(ds,h); c,lo,hi,sp=best(pub-t)
        print(name,'EoT at %+3d h UT: best %d/%d spread %.3f window lo %.2f hi %.2f'%(h,c,len(ds),sp,lo,hi))
