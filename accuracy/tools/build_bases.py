import sys, os, datetime as dt, time
sys.path.insert(0,'tools'); import basis
t=time.time()
d0=dt.date(2019,1,1); dates=[d0+dt.timedelta(i) for i in range((dt.date(2026,12,31)-d0).days+1)]
B=basis.build(dates,'data/basis_2019_2026.pkl'); print('built', len(dates),'days in %.0fs'%(time.time()-t))
sub=basis.__dict__  # check accuracy on a subset of dates at far points
import numpy as np
Bs={k:v for k,v in B.items()}
idx=np.arange(0,len(dates),37)
small={'dates':[dates[i] for i in idx],'lat0':B['lat0'],'lon0':B['lon0']}
for k,v in B.items():
    if isinstance(v,tuple): small[k]=tuple(a[...,idx] for a in v)
for lat,lon in [(25.35,55.50),(24.95,55.10),(25.25,55.20)]:
    w=basis.check_basis(small,lat,lon); print(lat,lon,{k:round(x,2) for k,x in w.items()})
