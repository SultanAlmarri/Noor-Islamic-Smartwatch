import sys, pickle, numpy as np, datetime as dt, time
sys.path.insert(0,'tools'); import analyse as A
B=pickle.load(open('data/basis_2019_2026.pkl','rb'))
sets={'A_iacad_api_2026_11':A.load('data/iacad_api_2026-11.iso.csv'),
      'B_iacad_nestle_2020':A.load('data/iacad_via_nestle_ramadan_2020.csv'),
      'C_salatcalendar_official':A.load('data/salatcalendar_official_dubai_2019_2026.csv')}
res={}
for name,P in sets.items():
    S=A.subset_basis(B, sorted(P)); res[name]={}
    for pr in A.PR:
        t=time.time(); r=A.scan(S,P,pr); res[name][pr]=r
        sp=r['spread']; k=np.unravel_index(np.argmin(sp),sp.shape)
        print(name, pr, 'n=%d'%len(P), 'min spread %.3f at lat %.2f lon %.2f param %s'%(sp[k],A.LATS[k[0]],A.LONS[k[1]],r['params'][k[2]]), 'feasible cells', int((sp<1).sum()), '%.1fs'%(time.time()-t))
pickle.dump(res,open('data/scan_results.pkl','wb'))
