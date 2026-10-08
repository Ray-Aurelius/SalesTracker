"""Converts the wordmark SVGs (our own simple subset) into Android VectorDrawable XML."""
import re, sys, xml.etree.ElementTree as ET
NS='{http://www.w3.org/2000/svg}'
def num(v): return float(v)
def f(v): return f"{v:.2f}".rstrip('0').rstrip('.')
def color(c, opacity=None):
    c=c.strip()
    if opacity is not None and float(opacity)<1:
        a=round(float(opacity)*255); return "#%02X%s"%(a,c[1:].upper())
    return "#FF"+c[1:].upper() if len(c)==7 else c
def circle_d(cx,cy,r): return f"M{f(cx-r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2*r)},0a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0z"
def rect_d(x,y,w,h,rx=0):
    if not rx: return f"M{f(x)},{f(y)}h{f(w)}v{f(h)}h{f(-w)}z"
    return (f"M{f(x+rx)},{f(y)}h{f(w-2*rx)}a{f(rx)},{f(rx)} 0 0,1 {f(rx)},{f(rx)}v{f(h-2*rx)}a{f(rx)},{f(rx)} 0 0,1 {f(-rx)},{f(rx)}"
            f"h{f(-(w-2*rx))}a{f(rx)},{f(rx)} 0 0,1 {f(-rx)},{f(-rx)}v{f(-(h-2*rx))}a{f(rx)},{f(rx)} 0 0,1 {f(rx)},{f(-rx)}z")
def convert(svg_text, width_dp):
    root=ET.fromstring(svg_text)
    vx,vy,vw,vh=[num(v) for v in root.get('viewBox').split()]
    clips={}
    for cp in root.iter(NS+'clipPath'):
        el=list(cp)[0]
        if el.tag==NS+'rect': d=rect_d(num(el.get('x')),num(el.get('y')),num(el.get('width')),num(el.get('height')))
        else:
            pts=el.get('points').split(); d="M"+"L".join(pts)+"z"
        clips[cp.get('id')]=d
    out=[]
    def path_attrs(el, d):
        a=[f'android:pathData="{d}"']
        op=el.get('opacity')
        fill=el.get('fill'); stroke=el.get('stroke')
        if fill and fill!='none': a.append(f'android:fillColor="{color(fill,op)}"')
        if stroke and stroke!='none':
            a.append(f'android:strokeColor="{color(stroke,op)}"')
            a.append(f'android:strokeWidth="{el.get("stroke-width","1")}"')
            cap=el.get('stroke-linecap'); join=el.get('stroke-linejoin'); ml=el.get('stroke-miterlimit')
            if cap: a.append(f'android:strokeLineCap="{cap}"')
            if join: a.append(f'android:strokeLineJoin="{join}"')
            if ml: a.append(f'android:strokeMiterLimit="{ml}"')
        return "<path "+" ".join(a)+" />"
    def walk(el, ind):
        for c in el:
            t=c.tag.replace(NS,'')
            if t in ('defs','clipPath'): continue
            if t=='g':
                attrs=[]
                tr=c.get('transform','')
                m=re.search(r'translate\(([-\d.]+)[ ,]+([-\d.]+)\)',tr)
                if m: attrs+= [f'android:translateX="{m.group(1)}"', f'android:translateY="{m.group(2)}"']
                m=re.search(r'scale\(([-\d.]+)\)',tr)
                if m: attrs+= [f'android:scaleX="{m.group(1)}"', f'android:scaleY="{m.group(1)}"']
                out.append(ind+"<group "+" ".join(attrs)+">")
                cl=c.get('clip-path')
                if cl:
                    cid=re.search(r'#([^)]+)',cl).group(1)
                    out.append(ind+f'    <clip-path android:pathData="{clips[cid]}" />')
                walk(c, ind+"    ")
                out.append(ind+"</group>")
            elif t=='path': out.append(ind+path_attrs(c,c.get('d')))
            elif t=='circle': out.append(ind+path_attrs(c,circle_d(num(c.get('cx')),num(c.get('cy')),num(c.get('r')))))
            elif t=='rect': out.append(ind+path_attrs(c,rect_d(num(c.get('x',0)),num(c.get('y',0)),num(c.get('width')),num(c.get('height')),num(c.get('rx',0)))))
            elif t=='line': out.append(ind+path_attrs(c,f"M{c.get('x1')},{c.get('y1')}L{c.get('x2')},{c.get('y2')}"))
            else: raise ValueError(t)
    walk(root,"        ")
    h_dp=width_dp*vh/vw
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- Quota Vault lockup: the vault-door mark with QUOTA over VAULT. Custom lettering; generated from branding/wordmark. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="{f(width_dp)}dp"\n    android:height="{f(h_dp)}dp"\n'
            f'    android:viewportWidth="{f(vw)}"\n    android:viewportHeight="{f(vh)}">\n'
            f'    <group android:translateX="{f(-vx)}" android:translateY="{f(-vy)}">\n'+"\n".join(out)+"\n    </group>\n</vector>\n")
if __name__=='__main__':
    src,dst,w=sys.argv[1],sys.argv[2],float(sys.argv[3])
    open(dst,'w').write(convert(open(src).read(),w))
