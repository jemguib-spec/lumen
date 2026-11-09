# Paysages Lumen : 4 moments x 3 scènes statiques + 1 scène animée (calques) par moment
import numpy as np, os, json
from PIL import Image
W,H=1920,1080
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'assets')
rng=np.random.default_rng(5)
y,x=np.mgrid[0:H,0:W].astype(np.float32); u=x/W; v=y/H

def fbm_periodic(n,seed,oct=7):
    r=np.random.default_rng(seed); out=np.zeros(n); amp=1.0; f=3
    for o in range(oct):
        pts=r.normal(0,1,f); xs=np.arange(n)/n*f
        i=np.floor(xs).astype(int); t=xs-i; t=t*t*(3-2*t)
        out+=amp*(pts[i%f]*(1-t)+pts[(i+1)%f]*t); amp*=.5; f*=2
    return out/1.8

P={
 'aube': dict(top=[38,32,64],bot=[246,176,150],sun=[255,190,150],sunpos=(.68,.60),sunr=.05,sunk=.8,
             ridge=[[150,110,130],[112,80,108],[78,56,84],[46,34,56],[20,15,28]],haze=.55,stars=0,label='Aube'),
 'jour': dict(top=[58,112,178],bot=[196,214,226],sun=[255,250,235],sunpos=(.25,.18),sunr=.035,sunk=.5,
             ridge=[[120,148,170],[88,116,140],[62,88,110],[40,60,76],[22,34,44]],haze=.6,stars=0,label='Jour'),
 'soir': dict(top=[30,24,58],bot=[236,128,82],sun=[255,170,100],sunpos=(.42,.62),sunr=.055,sunk=1.0,
             ridge=[[130,74,84],[92,52,70],[62,36,56],[36,22,38],[14,9,16]],haze=.5,stars=0,label='Soir'),
 'nuit': dict(top=[4,7,18],bot=[30,44,78],sun=[170,195,255],sunpos=(.74,.24),sunr=.018,sunk=.6,
             ridge=[[40,52,82],[28,38,64],[18,26,46],[10,15,30],[4,6,14]],haze=.5,stars=1,label='Nuit'),
}

def sky(p):
    t=v[...,None]
    img=np.array(p['top'],np.float32)*(1-t)+np.array(p['bot'],np.float32)*t
    d=np.sqrt(((u-p['sunpos'][0])*1.78)**2+(v-p['sunpos'][1])**2)[...,None]
    img+=np.array(p['sun'],np.float32)*(np.exp(-(d/.35)**2)*.45*p['sunk']+np.exp(-(d/p['sunr'])**2)*.95)
    if p['stars']:
        n=900; sx=rng.integers(0,W,n); sy=(rng.random(n)**1.6*H*.6).astype(int); b=rng.random(n)**3*200+40
        for X,Y,B in zip(sx,sy,b):
            img[Y,X]+=B; 
            if B>170 and X+1<W and Y+1<H: img[Y,X+1]+=B*.4; img[Y+1,X]+=B*.4
    return img

def ridge_profiles(n,base0,step,amp0,seed):
    return [ (base0+k*step, amp0*(1-k*.08), fbm_periodic(W,seed+k*13)) for k in range(n)]

def ridge_layer_rgba(p,k,n,prof,base,amp):
    yy=base+amp*prof
    edge=np.clip((v-yy[None,:])*H/1.4,0,1)
    depth=1-k/(n-1)
    c=np.array(p['ridge'][k],np.float32)
    c=c*(1-p['haze']*depth*.6)+np.array(p['bot'],np.float32)*p['haze']*depth*.6
    fog=np.exp(-((v-yy[None,:])*H/160).clip(0))[...,None]*.28*depth
    col=c*(1-fog)+np.array(p['bot'],np.float32)*fog
    # légère lumière sur l'arête
    rim=np.exp(-((v-yy[None,:])*H/2.5).clip(0)**2)[...,None]*np.array(p['sun'],np.float32)*.12*depth
    col=col+rim*edge[...,None]
    return col, edge

def comp(img,col,a):
    a=a[...,None]; return img*(1-a)+col*a

def grain(img,g=1.8): return img+rng.normal(0,g,(H,W,1))
def save_jpg(img,path,q=90):
    Image.fromarray(np.clip(img,0,255).astype(np.uint8)).save(path,quality=q,optimize=True,progressive=True)

os.makedirs(f'{OUT}/scenes',exist_ok=True); os.makedirs(f'{OUT}/anim',exist_ok=True)
for f in os.listdir(f'{OUT}/scenes'): os.remove(f'{OUT}/scenes/{f}')
meta=[]
for pi,(name,p) in enumerate(P.items()):
    S=sky(p)
    # 1. Crêtes
    img=S.copy(); n=5
    for k,(b,a,pr) in enumerate(ridge_profiles(n,.50,.085,.11,100+pi)):
        c,e=ridge_layer_rgba(p,k,n,pr,b,a); img=comp(img,c,e)
    save_jpg(grain(img),f'{OUT}/scenes/{name}_cretes.jpg'); meta.append((name,'cretes','Crêtes'))
    # 2. Horizon (mer)
    img=S.copy(); hz=.64
    sea=(v>hz)[...,None]; tt=((v-hz)/(1-hz)).clip(0,1)[...,None]
    seac=np.array(p['bot'],np.float32)*.55*(1-tt)+np.array(p['ridge'][4],np.float32)*tt
    w=.03+.22*tt[...,0]
    refl=np.exp(-((u-p['sunpos'][0])/w)**2)[...,None]*np.array(p['sun'],np.float32)*.5*(1-tt)*p['sunk']
    rip=(np.sin((v*H-hz*H).clip(0)**1.2*.7)*.5+.5)[...,None]
    seac=seac+refl*(.55+.45*rip)
    img=np.where(sea,seac,img)
    save_jpg(grain(img),f'{OUT}/scenes/{name}_horizon.jpg'); meta.append((name,'horizon','Horizon'))
    # 3. Lac (crêtes + reflet)
    top=S.copy(); n=4; lake=.70
    for k,(b,a,pr) in enumerate(ridge_profiles(n,.46,.07,.12,200+pi)):
        c,e=ridge_layer_rgba(p,k,n,pr,b*(lake/.70),a*.9); top=comp(top,c,e)
    img=top.copy()
    yi=(lake-(v-lake)).clip(0,1); yi=(yi*(H-1)).astype(int)
    mir=top[yi,x.astype(int)]
    mir=mir*.72+np.array(p['ridge'][4],np.float32)*.28
    rip=(np.sin(v*H*.9+np.sin(u*40)*1.5)*.5+.5)[...,None]*.025
    mir=mir*(1-rip)
    img=np.where((v>lake)[...,None],mir,img)
    img=img*(1-.0)+0
    save_jpg(grain(img),f'{OUT}/scenes/{name}_lac.jpg'); meta.append((name,'lac','Lac'))

    # Scène animée : ciel + 4 calques de crêtes tuilables + brume
    d=f'{OUT}/anim/{name}'; os.makedirs(d,exist_ok=True)
    save_jpg(grain(S,1.2),f'{d}/sky.jpg',q=88)
    n=4
    for k,(b,a,pr) in enumerate(ridge_profiles(n,.55,.1,.12,400+pi)):
        c,e=ridge_layer_rgba(p,k,n,pr,b,a)
        top_px=int(max(0,(b-a*1.2-0.05))*H)
        rgba=np.dstack([np.clip(c,0,255),e[...,None]*255]).astype(np.uint8)[top_px:]
        Image.fromarray(rgba,'RGBA').save(f'{d}/l{k}.png',optimize=True)
        with open(f'{d}/l{k}.txt','w') as fh: fh.write(str(top_px))
    # brume : bande douce tuilable
    fx=fbm_periodic(W,900+pi,5)
    band=np.exp(-((v-.70-.03*fx[None,:])/.06)**2)*(0.55+0.45*(fbm_periodic(W,950+pi,6)[None,:]*.5+.5))
    fogc=np.array(p['bot'],np.float32)*.85+255*.15
    rgba=np.dstack([np.broadcast_to(fogc,(H,W,3)),band[...,None]*150]).astype(np.uint8)[int(H*.55):int(H*.88)]
    Image.fromarray(rgba,'RGBA').save(f'{d}/fog.png',optimize=True)
    with open(f'{d}/fog.txt','w') as fh: fh.write(str(int(H*.55)))
print(meta)
