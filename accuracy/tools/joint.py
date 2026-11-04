import sys, pickle, numpy as np, datetime as dt
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_2019_2026.pkl','rb'))
PA=A.load('data/iacad_api_2026-11.iso.csv')
PB=A.load('data/iacad_via_nestle_ramadan_2020.csv')
bad={dt.date(2020,4,30),dt.date(2020,5,1)}
PB={d:v for d,v in PB.items() if d not in bad}
PAB={**PA,**PB}
S=A.subset_basis(B,sorted(PAB))
feas={}
for pr in A.PR:
    r=A.scan(S,PAB,pr); sp=r['spread']; ok=sp<1
    feas[pr]=ok.any(axis=2)   # (lat,lon) where some param works
    k=np.unravel_index(np.argmin(sp),sp.shape)
    print('A+B',pr,'min spread %.3f'%sp[k],'feasible cells',int(ok.sum()),'best at',A.LATS[k[0]],A.LONS[k[1]],r['params'][k[2]])
    if ok.any():
        ps=sorted(set(r['params'][i] for i in np.nonzero(ok)[2]))
        print('   feasible params', ps[:3],'...',ps[-3:] if len(ps)>3 else '', 'n=',len(ps))
        labs=set()
        for i,j,k2 in zip(*np.nonzero(ok)): labs.update(A.shift_label(r['lo'][i,j,k2], r['hi'][i,j,k2]))
        print('   shift labels', sorted(labs))
allok=np.logical_and.reduce([feas[p] for p in A.PR])
print('locations where ALL six prayers feasible:', int(allok.sum()))
if allok.any():
    ii,jj=np.nonzero(allok); print('lat range',A.LATS[ii].min(),A.LATS[ii].max(),'lon range',A.LONS[jj].min(),A.LONS[jj].max())
pickle.dump({'feas':feas,'allok':allok},open('data/joint_AB.pkl','wb'))
