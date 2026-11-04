import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_all.pkl','rb'))
W=A.load('data/iacad_official_archive_2014_2023.csv'); api=A.load('data/iacad_api_2026-11.iso.csv')
P={**W,**api}
sets={'E3 (>=2020-08-20)':{d:v for d,v in P.items() if d>=dt.date(2020,8,20)},
      'E2 (2017-10..2020-08)':{d:v for d,v in P.items() if dt.date(2017,10,15)<=d<dt.date(2020,8,20)}}
def best(u):
    u=np.sort(u); c=np.searchsorted(u,u+1-1e-9)-np.arange(len(u)); k=int(np.argmax(c)); return c[k], u[k], u[k+c[k]-1]
def eot_spencer(d):
    N=d.timetuple().tm_yday; Bv=2*np.pi*(N-1)/365
    return 229.18*(0.000075+0.001868*np.cos(Bv)-0.032077*np.sin(Bv)-0.014615*np.cos(2*Bv)-0.040849*np.sin(2*Bv))
def eot_simple(d):
    N=d.timetuple().tm_yday; Bv=np.radians(360/365*(N-81)); return 9.87*np.sin(2*Bv)-7.53*np.cos(Bv)-1.5*np.sin(Bv)
def eot_usno(d, hour_ut=8.0):
    jd=(dt.datetime(d.year,d.month,d.day)-dt.datetime(2000,1,1,12)).total_seconds()/86400+hour_ut/24
    g=np.radians((357.529+0.98560028*jd)%360); q=(280.459+0.98564736*jd)%360
    L=np.radians((q+1.915*np.sin(g)+0.020*np.sin(2*g))%360); e=np.radians(23.439-0.00000036*jd)
    RA=np.degrees(np.arctan2(np.cos(e)*np.sin(L),np.cos(L)))/15; RA=RA%24
    eqt=q/15-RA; eqt=(eqt+12)%24-12; return eqt*60
def eot_noaa(d):  # NOAA spreadsheet (Meeus-based) evaluated at local noon
    jd=(dt.datetime(d.year,d.month,d.day)-dt.datetime(2000,1,1,12)).total_seconds()/86400+ (12-4)/24 + 2451545.0
    T=(jd-2451545)/36525
    L0=(280.46646+T*(36000.76983+T*0.0003032))%360; M=357.52911+T*(35999.05029-0.0001537*T); e=0.016708634-T*(0.000042037+0.0000001267*T)
    eps0=23+(26+((21.448-T*(46.815+T*(0.00059-T*0.001813))))/60)/60; om=125.04-1934.136*T; eps=eps0+0.00256*np.cos(np.radians(om))
    y=np.tan(np.radians(eps/2))**2
    E=y*np.sin(2*np.radians(L0))-2*e*np.sin(np.radians(M))+4*e*y*np.sin(np.radians(M))*np.cos(2*np.radians(L0))-0.5*y*y*np.sin(4*np.radians(L0))-1.25*e*e*np.sin(2*np.radians(M))
    return 4*np.degrees(E)
for name,Q in sets.items():
    ds=sorted(Q); pub=np.array([Q[d]['dhuhr'] for d in ds],float); S=A.subset_basis(B,ds)
    spa=A.times(S,'dhuhr',25.2,55.27,None)/60
    print(name,'n=',len(ds))
    for lab,t in [('SPA precise transit',spa),
                  ('NOAA/Meeus EoT',np.array([12*60+4*(60-55.27)-eot_noaa(d) for d in ds])),
                  ('USNO approx EoT (praytimes)',np.array([12*60+4*(60-55.27)-eot_usno(d) for d in ds])),
                  ('Spencer 1971 EoT',np.array([12*60+4*(60-55.27)-eot_spencer(d) for d in ds])),
                  ('simple 9.87/7.53/1.5 EoT',np.array([12*60+4*(60-55.27)-eot_simple(d) for d in ds]))]:
        c,lo,hi=best(pub-t); print('   %-30s best %d/%d  spread(all) %.2f'%(lab,c,len(ds),(pub-t).max()-(pub-t).min()))
