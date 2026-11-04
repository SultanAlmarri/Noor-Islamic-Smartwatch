"""Hypothesis test: published = q1(transit) +/- q2(semi-arc) + k  (two-stage minute rounding)."""
import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_all.pkl','rb'))
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv'); P={**W,**api}
C=A.load('data/salatcalendar_official_dubai_2019_2026.csv')
sets={'IACAD E3':{d:v for d,v in P.items() if d>=dt.date(2020,8,20)},'IACAD E2':{d:v for d,v in P.items() if dt.date(2017,10,15)<=d<dt.date(2020,8,20)}}
q={'ceil':lambda x:np.ceil(x-1e-9),'round':lambda x:np.floor(x+0.5),'floor':lambda x:np.floor(x+1e-9)}
LONS=np.round(np.arange(55.15,55.4001,0.0025),4)
for name,Q in sets.items():
    ds=sorted(Q); S=A.subset_basis(B,ds); n=len(ds); pub={p:np.array([Q[d][p] for d in ds],float) for p in A.PR}
    print('==',name,n)
    for pr in ('fajr','isha','asr','maghrib','shuruq'):
        best=(0,)
        for la in np.round(np.arange(24.95,25.351,0.02),2):
            T=A._taylor(S,'dhuhr',la,LONS)/60.0                         # (nlon, nd) minutes
            if pr in ('fajr','isha'): E=A._taylor(S,pr,la,LONS)/60.0; plist=list(S['ANG'])     # (nlon,nang,nd)
            elif pr=='asr': E=A._taylor(S,'asr1',la,LONS)[:,None,:]/60.0; plist=[None]
            else:
                key='rise' if pr=='shuruq' else 'set'; arr=A._taylor(S,key,la,LONS)[:, ::-1,:]/60.0
                g=np.asarray(S['HZ'])[::-1]; hz=-(S['sd']+A.REFR+0.19); ii=np.clip(np.searchsorted(g,hz)-1,0,len(g)-2); ww=(hz-g[ii])/(g[ii+1]-g[ii])
                E=(arr[:,ii,np.arange(n)]*(1-ww)+arr[:,ii+1,np.arange(n)]*ww)[:,None,:]; plist=[0.19]
            Hs=np.abs(E-T[:,None,:]); sign=-1 if pr in ('fajr','shuruq') else 1
            for m1 in ('ceil','round'):
                for m2 in ('ceil','round','floor'):
                    pred=q[m1](T)[:,None,:]+sign*q[m2](Hs)
                    diff=pub[pr]-pred
                    cnt=np.stack([(diff==k).sum(-1) for k in range(-6,7)],-1).max(-1)
                    j=np.unravel_index(cnt.argmax(),cnt.shape)
                    if cnt[j]>best[0]: best=(int(cnt[j]),la,LONS[j[0]],plist[j[1]],m1,m2)
        print('  %-8s best %d/%d  lat %.2f lon %.4f param %s  q1=%s q2=%s'%(pr,best[0],n,best[1],best[2],best[3],best[4],best[5]))
