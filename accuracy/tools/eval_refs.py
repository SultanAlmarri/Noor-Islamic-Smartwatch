import sys, datetime as dt, numpy as np, csv
sys.path.insert(0,'tools'); import model as M, analyse as A
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv'); OFF={**W,**api}
C=A.load('data/salatcalendar_official_dubai_2019_2026.csv')
E3=sorted(d for d in OFF if d>=dt.date(2020,8,20))
import pvlib.spa as spa
def meridian(ds, lon, lat):
    ut0=np.array([(dt.datetime(d.year,d.month,d.day)-dt.datetime(1970,1,1)).total_seconds() for d in ds])
    tr,_,_=spa.transit_sunrise_sunset(ut0, lat, lon, M.DELTA_T, 1); return tr-(ut0-M.TZ)
def run(lat,lon,elev,fa,ia,offs,mode,dip_coef=0.0353333):
    ev=M.events(E3,lat,lon,fa,ia,elev_m=elev,dip_coef=dip_coef); ev['dhuhr']=meridian(E3,lon,lat)
    out={}
    for p in A.PR:
        pred=M.to_minutes(ev[p],mode,offs[p]); pub=np.array([OFF[d][p] for d in E3]); out[p]=(pred==pub).sum()
    return out
cfgs={'IAC platform UAE method @ platform Dubai point 25.07725,55.30927, 30 m, ceil':(25.07725,55.30927,30,18,18,dict(fajr=0,shuruq=0,dhuhr=2,asr=0,maghrib=0,isha=0),'ceil'),
      'same, round-to-nearest':(25.07725,55.30927,30,18,18,dict(fajr=0,shuruq=0,dhuhr=2,asr=0,maghrib=0,isha=0),'round'),
      'AlAdhan-style Dubai 18.2/18.2, -3/+3/+3/+3 @ 25.2048,55.2708, 0 m, round':(25.2048,55.2708,0,18.2,18.2,dict(fajr=0,shuruq=-3,dhuhr=3,asr=3,maghrib=3,isha=0),'round')}
for name,c in cfgs.items():
    o=run(*c); tot=sum(o.values()); print('%-80s %d/%d (%.1f%%) %s'%(name,tot,6*len(E3),100*tot/6/len(E3),{k:int(v) for k,v in o.items()}))
# salatcalendar official vs IACAD on E3 overlap
ov=[d for d in E3 if d in C]; eq={p:sum(C[d][p]==OFF[d][p] for d in ov) for p in A.PR}; tot=sum(eq.values())
print('%-80s %d/%d (%.1f%%) %s'%('salatcalendar.com official mode (IAC replica) vs IACAD',tot,6*len(ov),100*tot/6/len(ov),eq))
