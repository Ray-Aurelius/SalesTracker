import sys; sys.path.insert(0,'.')
import glyphs as G
from glyphs import *
CREAM="#F6ECE8"; NIGHT="#16191D"
def standalone(inner_parts, clips, vb):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}"><defs>{"".join(clips)}</defs>{inner_parts}</svg>'
def caps(ink, prefix):
    G.WHITE_SAVE=G.WHITE
    p,w=wordmark_caps(colors=(ink,ink)); p.prefix=prefix
    return standalone("".join(p.parts), p.clips, f"-6 -10 {f(w+12)} 132"), w
def lower(ink, prefix):
    p,w=wordmark_lower(colors=(ink,CORAL))
    return standalone("".join(p.parts), p.clips, f"-6 -6 {f(w+12)} 146"), w
def lockup(ink, door, doorlight, prefix, edge=None):
    p1,p2,w1=stacked(colors=(ink,CORAL))
    mono=monogram(240, ink=door, light=doorlight, edge=edge)
    inner=f'<g>{mono}</g><g transform="translate(280,0)">{"".join(p1.parts)}<g transform="translate(0,140)">{"".join(p2.parts)}</g></g>'
    return standalone(inner, p1.clips+p2.clips, f"-4 -4 {f(280+w1+10)} 258")
def name_only(ink, prefix):
    """QUOTA over VAULT without the mark, for layouts that show the mark separately."""
    p1,p2,w1=stacked(colors=(ink,CORAL))
    inner=f'{"".join(p1.parts)}<g transform="translate(0,140)">{"".join(p2.parts)}</g>'
    return standalone(inner, p1.clips+p2.clips, f"-14 -4 {f(w1+20)} 258")
def mark(door, light, edge=None):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240">{monogram(240, ink=door, light=light, edge=edge)}</svg>'
files={}
s,_=caps(INK,"a"); files["qv-a-light.svg"]=s
# dark: cream letters; the bullseye keeps its white ring
s,_=caps(CREAM,"b"); files["qv-a-dark.svg"]=s
s,_=lower(INK,"c"); files["qv-b-light.svg"]=s
s,_=lower(CREAM,"d"); files["qv-b-dark.svg"]=s.replace('stroke-width="2.6"','stroke-width="2.6"')
files["qv-c-light.svg"]=lockup(INK, INK, "#FFFFFF","e")
files["qv-c-dark.svg"]=lockup(CREAM, "#2C3138", CREAM,"f", edge="#454C56")
files["qv-c-name-light.svg"]=name_only(INK,"g")
files["qv-c-name-dark.svg"]=name_only(CREAM,"h")
files["qv-c-mark-light.svg"]=mark(INK,"#FFFFFF")
files["qv-c-mark-dark.svg"]=mark("#2C3138",CREAM, edge="#454C56")
for k,v in files.items():
    open("out/"+k,"w").write(v); print(k,len(v))
