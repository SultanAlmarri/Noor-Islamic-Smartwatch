"""Score physically-plausible fixed conventions (lat 25.12, Fajr/Isha 18.0 deg) with only a common
longitude (fine grid) and integer minutes fitted; uniform rounding. Reports in-sample and
cross-era / cross-season held-out matches."""
import sys, pickle, numpy as np, datetime as dt, json
sys.path.insert(0,'tools'); import analyse as A
from holdout import tables, event_arrays, quant
from joint_search import KS
LONS=np.round(np.arange(55.10,55.50,0.001),4)
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv'); P={**W,**api}
E3=lambda d: d>=dt.date(2020,8,20)
def fit_eval(train, tests, lat=25.12, ang=18.0, dip=0.0, mode='ceil'):
    ds,S,pub,ii,ww,cols=tables(train); T=event_arrays(S,lat,ii,ww,cols,lons=LONS)
    ai=int(np.argmin(np.abs(S['ANG']-ang))); di=int(np.argmin(np.abs(A.DIPS-dip)))
    idx={'fajr':ai,'isha':ai,'shuruq':di,'maghrib':di,'dhuhr':0,'asr':0}
    tot=np.zeros(len(LONS)); offs={}
    for p in A.PR:
        diff=pub[p]-quant(T[p][:,idx[p]],mode); c=np.stack([(diff==k).sum(-1) for k in KS],-1)
        tot+=c.max(-1); offs[p]=KS[c.argmax(-1)]
    j=int(tot.argmax()); conv={'lat':lat,'lon':float(LONS[j]),'angle':ang,'dip':dip,'mode':mode,'offsets':{p:int(offs[p][j]) for p in A.PR}}
    res={'train':(int(tot[j]),6*len(ds))}
    for name,Q in tests.items():
        ds2,S2,pub2,ii2,ww2,cols2=tables(Q); T2=event_arrays(S2,lat,ii2,ww2,cols2,lons=np.array([conv['lon']]))
        m=sum(int((pub2[p]-(quant(T2[p][0,idx[p]],mode)+conv['offsets'][p])==0).sum()) for p in A.PR); res[name]=(m,6*len(ds2))
    return conv,res
splits={'all E3 (in-sample)':({d:v for d,v in P.items() if E3(d)},{}),
        'train E3 2020-23 -> test Nov 2026':({d:v for d,v in P.items() if E3(d) and d.year<2026},{'Nov 2026':{d:v for d,v in P.items() if d.year==2026}}),
        'train Nov 2026 -> test E3 2020-23':({d:v for d,v in P.items() if d.year==2026},{'E3 2020-23':{d:v for d,v in P.items() if E3(d) and d.year<2026}})}
out=[]
for dip in (0.0,0.1,0.19):
    for mode in ('ceil','round'):
        for name,(tr,te) in splits.items():
            conv,res=fit_eval(tr,te,dip=dip,mode=mode)
            line='dip %.2f %-5s %-36s train %d/%d'%(dip,mode,name,*res['train'])+''.join('  TEST %s %d/%d (%.1f%%)'%(k,v[0],v[1],100*v[0]/v[1]) for k,v in res.items() if k!='train')+'  lon %.3f offs %s'%(conv['lon'],conv['offsets'])
            print(line); out.append({'dip':dip,'mode':mode,'split':name,'conv':conv,'res':{k:list(v) for k,v in res.items()}})
json.dump(out,open('results/principled_conventions.json','w'),indent=1)
