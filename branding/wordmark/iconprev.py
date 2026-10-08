import re
fg=open('/home/claude/SalesTracker/SalesTracker/app/src/main/res/drawable/ic_launcher_foreground.xml').read()
mono=open('/home/claude/SalesTracker/SalesTracker/app/src/main/res/drawable/ic_launcher_monochrome.xml').read()
def to_svg_inner(vd, recolor=None):
    tx,ty,sc=re.search(r'translateX="([\d.]+)" android:translateY="([\d.]+)" android:scaleX="([\d.]+)"',vd).groups()
    out=[]
    for m in re.finditer(r'<path ([^>]*)/>',vd):
        a=dict(re.findall(r'android:(\w+)="([^"]*)"',m.group(1)))
        def col(c): return (recolor or '#'+c[3:]), int(c[1:3],16)/255
        attrs=[f'd="{a["pathData"]}"']
        if 'fillColor' in a:
            c,o=col(a['fillColor']); attrs.append(f'fill="{c}" fill-opacity="{o:.2f}"')
            if a.get('fillType')=='evenOdd': attrs.append('fill-rule="evenodd"')
        else: attrs.append('fill="none"')
        if 'strokeColor' in a:
            c,o=col(a['strokeColor']); attrs.append(f'stroke="{c}" stroke-opacity="{o:.2f}" stroke-width="{a["strokeWidth"]}"')
            if 'strokeLineCap' in a: attrs.append(f'stroke-linecap="{a["strokeLineCap"]}"')
        out.append('<path '+' '.join(attrs)+'/>')
    return f'<g transform="translate({tx},{ty}) scale({sc})">'+"".join(out)+'</g>'
FG=to_svg_inner(fg); MONO=to_svg_inner(mono, recolor="#3A4A2E")
n=[0]
def icon(mask, bg="#3A3F45", inner=FG, size=160):
    n[0]+=1
    clip={'circle':'<circle cx="54" cy="54" r="36"/>','squircle':'<path d="M18,54 C18,22 22,18 54,18 C86,18 90,22 90,54 C90,86 86,90 54,90 C22,90 18,86 18,54z"/>',
          'rounded':'<rect x="18" y="18" width="72" height="72" rx="16"/>'}.get(mask,'<rect x="18" y="18" width="72" height="72"/>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="{size}" height="{size}" style="display:block"><defs><clipPath id="m{n[0]}">{clip}</clipPath></defs><g clip-path="url(#m{n[0]})"><rect x="0" y="0" width="108" height="108" fill="{bg}"/>{inner}</g></svg>'
html='<html><body style="margin:0;background:#6E8BA6;padding:30px;display:flex;gap:30px;flex-wrap:wrap;width:900px;align-items:center">'
for m in ['circle','squircle','rounded']: html+=icon(m)
html+=icon('circle',bg="#DCE8CF",inner=MONO)+icon('rounded',size=48)+icon('circle',size=48)+'</body></html>'
open('icons.html','w').write(html)
open('play-icon.html','w').write('<html><body style="margin:0">'+icon('square',size=512)+'</body></html>')
