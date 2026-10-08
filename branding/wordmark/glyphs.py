# Custom monoline lettering for the Quota Vault wordmark. Every letter is built from strokes on a grid,
# so the shapes are ours (not taken from any font).
# Brand colors: charcoal gray, safety orange and white.
INK="#3A3F45"; CORAL="#F26B1D"; WHITE="#FFFFFF"

def f(v): return f"{v:.2f}".rstrip('0').rstrip('.')

import itertools
_ids=itertools.count(1)
class Pen:
    """Collects stroked letter parts. Clip ids are unique across every drawing, so many marks can share a page."""
    def __init__(s, S, prefix="qv"): s.S=S; s.parts=[]; s.clips=[]; s.prefix=prefix
    def stroke(s, d, color, x, clip=None, cap="butt", join="miter", w=None):
        sw = w or s.S
        g = f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{f(sw)}" stroke-linecap="{cap}" stroke-linejoin="{join}" stroke-miterlimit="10"/>'
        if clip:
            cid=f"{s.prefix}{next(_ids)}"
            if isinstance(clip,str):
                s.clips.append(f'<clipPath id="{cid}"><polygon points="{clip}"/></clipPath>')
            else:
                x0,y0,x1,y1=clip
                s.clips.append(f'<clipPath id="{cid}"><rect x="{f(x0)}" y="{f(y0)}" width="{f(x1-x0)}" height="{f(y1-y0)}"/></clipPath>')
            s.parts.append(f'<g transform="translate({f(x)},0)" clip-path="url(#{cid})">{g}</g>')
        else:
            s.parts.append(f'<g transform="translate({f(x)},0)">{g}</g>')
    def fill(s, inner, x):
        s.parts.append(f'<g transform="translate({f(x)},0)">{inner}</g>')

# ---------- capitals, cap height 100 ----------
def cap_O(p,x,c,w=104):
    S=p.S; rx=(w-S)/2; ry=(100-S)/2+1
    p.stroke(f"M{f(w/2-rx)},50a{f(rx)},{f(ry)} 0 1,0 {f(2*rx)},0a{f(rx)},{f(ry)} 0 1,0 {f(-2*rx)},0z",c,x); return w
def cap_Q(p,x,c,w=104,tail=True):
    cap_O(p,x,c,w); S=p.S
    if tail: p.stroke(f"M{f(w*0.60)},{f(66)}L{f(w*1.02)},{f(112)}",c,x)
    return w
def cap_U(p,x,c,w=86):
    S=p.S; r=(w-S)/2; y=100-S/2-r
    p.stroke(f"M{f(S/2)},0V{f(y)}a{f(r)},{f(r)} 0 0,0 {f(2*r)},0V0",c,x); return w
def cap_T(p,x,c,w=84):
    S=p.S; p.stroke(f"M0,{f(S/2)}H{f(w)}M{f(w/2)},{f(S/2)}V100",c,x); return w
def cap_A(p,x,c,w=94):
    S=p.S
    p.stroke(f"M{f(-6)},{f(112)}L{f(w/2)},{f(-14)}L{f(w+6)},{f(112)}",c,x,clip=(-30,0,w+30,100))
    # crossbar between the inside edges
    # The bar reaches the middle of each leg and is trimmed to the A's shape, so no corner pokes out.
    yb=70; p.stroke(f"M{f(-10)},{f(yb)}H{f(w+10)}",c,x,clip=f"-6,112 {f(w/2)},-14 {f(w+6)},112")
    return w
def cap_V(p,x,c,w=94):
    p.stroke(f"M{f(-6)},{f(-12)}L{f(w/2)},{f(114)}L{f(w+6)},{f(-12)}",c,x,clip=(-30,0,w+30,100)); return w
def cap_L(p,x,c,w=70):
    S=p.S; p.stroke(f"M{f(S/2)},0V{f(100-S/2)}H{f(w)}",c,x); return w

def wordmark_caps(S=17, tracking=12, word_gap=44, colors=(INK,INK), target=True):
    """QUOTA VAULT. With target=True the Q holds a bullseye and its tail is the icon's arrow."""
    p=Pen(S); x=0
    c1,c2=colors
    # Q
    w=104
    cap_Q(p,x,c1,w,tail=not target)
    if target:
        # bullseye inside the Q's bowl and the arrow as its tail
        cx=x+w/2; cy=50
        p.fill(f'<circle cx="{f(w/2)}" cy="50" r="17" fill="{CORAL}"/><circle cx="{f(w/2)}" cy="50" r="9.5" fill="{WHITE}"/><circle cx="{f(w/2)}" cy="50" r="4" fill="{CORAL}"/>',x)
        p.stroke(f"M{f(w/2+3)},{f(53)}L{f(w*1.06)},{f(116)}",c1,x,cap="butt")
    x+=w+tracking-4
    for fn,ww,k in [(cap_U,86,0),(cap_O,104,-2),(cap_T,84,-8),(cap_A,94,0)]:
        x+=k; fn(p,x,c1,ww); x+=ww+tracking
    x+=word_gap-tracking
    for fn,ww,k in [(cap_V,94,0),(cap_A,94,-12),(cap_U,86,0),(cap_L,70,0),(cap_T,84,-14)]:
        x+=k; fn(p,x,c2,ww); x+=ww+tracking
    width=x-tracking
    return p, width

# ---------- lowercase: x-height 64 (y 36..100), ascender 0, descender 132 ----------
XT=36
def lc_o(p,x,c,w=72):
    S=p.S; r=(w-S)/2; ry=(100-XT-S)/2+1; cy=(XT+100)/2
    p.stroke(f"M{f(w/2-r)},{f(cy)}a{f(r)},{f(ry)} 0 1,0 {f(2*r)},0a{f(r)},{f(ry)} 0 1,0 {f(-2*r)},0z",c,x); return w
def lc_a(p,x,c,w=78):
    S=p.S; lc_o(p,x,c,w-6); p.stroke(f"M{f(w-S/2)},{f(XT)}V100",c,x); return w
def lc_q(p,x,c,w=78):
    S=p.S; lc_o(p,x,c,w-6); p.stroke(f"M{f(w-S/2)},{f(XT)}V134",c,x); return w
def lc_u(p,x,c,w=70):
    S=p.S; r=(w-S)/2; y=100-S/2-r
    p.stroke(f"M{f(S/2)},{f(XT)}V{f(y)}a{f(r)},{f(r)} 0 0,0 {f(2*r)},0",c,x); p.stroke(f"M{f(w-S/2)},{f(XT)}V100",c,x); return w
def lc_t(p,x,c,w=46):
    S=p.S; p.stroke(f"M{f(w*0.42)},8V100M0,{f(XT+S/2)}H{f(w)}",c,x); return w
def lc_v(p,x,c,w=72):
    p.stroke(f"M{f(-5)},{f(XT-10)}L{f(w/2)},{f(114)}L{f(w+5)},{f(XT-10)}",c,x,clip=(-30,XT,w+30,100)); return w
def lc_l(p,x,c,w=17):
    S=p.S; p.stroke(f"M{f(S/2)},0V100",c,x); return S

def dial_o(p,x,c_ring,c_mark,w=72):
    """The vault-dial 'o': a ring with tick marks and a pointer, like a safe's combination dial."""
    lc_o(p,x,c_ring,w)
    import math
    cx=w/2; cy=(XT+100)/2; r1=13; r2=18
    ticks="".join(f'<line x1="{f(cx+r1*math.sin(a))}" y1="{f(cy-r1*math.cos(a))}" x2="{f(cx+r2*math.sin(a))}" y2="{f(cy-r2*math.cos(a))}" stroke="{c_mark}" stroke-width="2.6" stroke-linecap="round"/>' for a in [i*math.pi/6 for i in range(12)])
    p.fill(ticks+f'<circle cx="{f(cx)}" cy="{f(cy)}" r="6.5" fill="{c_mark}"/>',x)
    return w

def wordmark_lower(S=17, tracking=7, colors=(INK,CORAL), dial=True):
    p=Pen(S); x=0; c1,c2=colors
    seq=[(lc_q,78,0,c1),('o',72,0,c1),(lc_u,70,0,c1),(lc_t,46,-2,c1),(lc_a,78,-4,c1),
         (lc_v,72,0,c2),(lc_a,78,-10,c2),(lc_u,70,0,c2),(lc_l,17,0,c2),(lc_t,46,0,c2)]
    # order: q u o t a  -> fix to "quota"
    seq=[(lc_q,78,0,c1),(lc_u,70,0,c1),('o',72,0,c1),(lc_t,46,-2,c1),(lc_a,78,-4,c1),
         (lc_v,72,2,c2),(lc_a,78,-4,c2),(lc_u,70,0,c2),(lc_l,17,0,c2),(lc_t,46,0,c2)]
    for fn,w,k,c in seq:
        x+=k
        if fn=='o':
            ww = dial_o(p,x,c,CORAL,w) if dial else lc_o(p,x,c,w)
        else:
            ww=fn(p,x,c,w)
        x+=ww+tracking
    return p, x-tracking

def svg(p, width, top=-4, bottom=104, pad=6, extra_h=0):
    vb=f"{f(-pad)} {f(top-pad)} {f(width+2*pad)} {f(bottom-top+2*pad+extra_h)}"
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}"><defs>{"".join(p.clips)}</defs>{"".join(p.parts)}</svg>'


def monogram(size=120, ink=INK, coral=CORAL, light=WHITE, edge=None):
    """Vault-door mark: a rounded-square door, a combination-dial ring with a bullseye centre, four bolts,
    and the Q's tail as the door handle."""
    import math
    s=size; r=s*0.2; cx=s/2; cy=s/2
    out=[f'<rect x="0" y="0" width="{f(s)}" height="{f(s)}" rx="{f(r)}" fill="{ink}"/>']
    if edge:  # a thin rim so the door still reads on a dark background
        out.append(f'<rect x="{f(s*0.008)}" y="{f(s*0.008)}" width="{f(s*0.984)}" height="{f(s*0.984)}" rx="{f(r*0.98)}" fill="none" stroke="{edge}" stroke-width="{f(s*0.016)}"/>')
    R=s*0.30; sw=s*0.085
    out.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(R)}" fill="none" stroke="{light}" stroke-width="{f(sw)}"/>')
    for i in range(12):
        a=i*math.pi/6; r1=R-sw/2-s*0.025; r2=r1-s*0.04
        out.append(f'<line x1="{f(cx+r1*math.sin(a))}" y1="{f(cy-r1*math.cos(a))}" x2="{f(cx+r2*math.sin(a))}" y2="{f(cy-r2*math.cos(a))}" stroke="{light}" stroke-width="{f(s*0.016)}" stroke-linecap="round" opacity="0.55"/>')
    out.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(s*0.115)}" fill="{coral}"/><circle cx="{f(cx)}" cy="{f(cy)}" r="{f(s*0.06)}" fill="{ink}"/><circle cx="{f(cx)}" cy="{f(cy)}" r="{f(s*0.026)}" fill="{coral}"/>')
    # handle = the Q's tail
    out.append(f'<line x1="{f(cx+R*0.62)}" y1="{f(cy+R*0.62)}" x2="{f(s*0.86)}" y2="{f(s*0.86)}" stroke="{light}" stroke-width="{f(sw)}" stroke-linecap="butt"/>')
    for bx,by in [(0.13,0.13),(0.87,0.13),(0.13,0.87)]:
        out.append(f'<circle cx="{f(s*bx)}" cy="{f(s*by)}" r="{f(s*0.028)}" fill="{light}" opacity="0.8"/>')
    return "".join(out)

def stacked(S=17, colors=(INK,CORAL)):
    """QUOTA over VAULT. The lines are matched by their VISIBLE edges: a slanted A or V reaches about
    10 units past its box, so VAULT is placed and spaced to line up with QUOTA by eye, not by box."""
    c1,c2=colors
    p1=Pen(S); x=0
    for fn,ww,k in [(cap_Q,104,0),(cap_U,86,0),(cap_O,104,-2),(cap_T,84,-8),(cap_A,94,0)]:
        x+=k; fn(p1,x,c1,ww); x+=ww+12
    w1=x-12
    visible_right=w1+10          # QUOTA ends on the A's slanted foot
    p2=Pen(S)
    letters=[(cap_V,94,0),(cap_A,94,-12),(cap_U,86,0),(cap_L,70,0),(cap_T,84,-14)]
    natural=sum(ww+k for _,ww,k in letters)
    start=8                      # the V's top-left arm reaches ~10 left of its box
    track=(visible_right-start-natural)/(len(letters)-1)
    x=start
    for fn,ww,k in letters:
        x+=k; fn(p2,x,c2,ww); x+=ww+track
    return p1,p2,visible_right
