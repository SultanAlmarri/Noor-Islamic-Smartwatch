import sys, datetime as dt, numpy as np
sys.path.insert(0,'tools'); import model as M
d=[dt.date(2026,11,4)]
for lat,lon,h,lab in [(25.2048,55.2708,0,'city centre, no elev'),(25.2048,55.2708,30,'city centre, 30 m'),(25.07725,55.30927,30,'SC geonames point, 30 m')]:
    ev=M.events(d,lat,lon,18,18,elev_m=h)
    print(lab, {k:'%02d:%02d:%02d'%(v[0]//3600,v[0]%3600//60,v[0]%60) for k,v in ev.items()})
print('IACAD 2026-11-04: 05:09 06:25 12:05 15:14 17:40 18:55')
