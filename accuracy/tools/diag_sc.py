import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_2019_2026.pkl','rb'))
C=A.load('data/salatcalendar_official_dubai_2019_2026.csv')
lat,lon=25.20,55.27
par={'fajr':18.0,'isha':18.0,'shuruq':0.19,'maghrib':0.19,'dhuhr':None,'asr':None}
for y in range(2019,2027):
    P={d:v for d,v in C.items() if d.year==y}; S=A.subset_basis(B,sorted(P))
    row=[]
    for pr in A.PR:
        u=A.residuals(S,P,pr,lat,lon,par[pr]); row.append('%s %.2f'%(pr[:3],u.max()-u.min()))
    print(y,' '.join(row))
# Dhuhr residual by month (min/max) across all years
S=A.subset_basis(B,sorted(C)); u=A.residuals(S,C,'dhuhr',lat,lon,None); ds=sorted(C)
print('Dhuhr residual envelope by month (min,max) over 2019-2026:')
for m in range(1,13):
    x=np.array([uu for d,uu in zip(ds,u) if d.month==m]); print(' %02d  %.2f %.2f'%(m,x.min(),x.max()))
