import pickle, numpy as np
a=pickle.load(open('data/basis_archive_pre2019.pkl','rb')); b=pickle.load(open('data/basis_2019_2026.pkl','rb'))
M={'dates':a['dates']+b['dates'],'lat0':a['lat0'],'lon0':a['lon0'],'ANG':a['ANG'],'HZ':a['HZ'],'sd':np.concatenate([a['sd'],b['sd']])}
for k,v in a.items():
    if isinstance(v,tuple): M[k]=tuple(np.concatenate([x,y],axis=-1) for x,y in zip(v,b[k]))
pickle.dump(M,open('data/basis_all.pkl','wb')); print(len(M['dates']))
