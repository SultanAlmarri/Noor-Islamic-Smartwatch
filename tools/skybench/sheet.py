import sys, glob
from PIL import Image
files = sys.argv[3:]; cols = int(sys.argv[2])
ims = [Image.open(f) for f in files]
w, h = ims[0].size; sc = 0.5
tw, th = int(w*sc), int(h*sc)
rows = (len(ims)+cols-1)//cols
S = Image.new("RGB", (cols*tw, rows*th), (0,0,0))
for i, im in enumerate(ims): S.paste(im.convert("RGB").resize((tw, th), Image.LANCZOS), ((i%cols)*tw, (i//cols)*th))
S.save(sys.argv[1])
