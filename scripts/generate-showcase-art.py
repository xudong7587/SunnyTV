"""Original geometric media artwork for native UI screenshots; no film assets or fonts shipped."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/androidTest/assets/showcase'
OUT.mkdir(parents=True, exist_ok=True)
TITLES = ['逐光计划', '海岸来信', '山野之间', '夜航星图', '风的形状', '远方有回声', '慢慢生活', '潮汐档案']
COLORS = [('#173749','#e9ae68'),('#224d66','#e8c7a5'),('#354e49','#d1cba5'),('#202744','#bea5b0'),('#53615a','#d9a878'),('#593a3c','#d4ab91'),('#496366','#d8cdad'),('#1a4857','#a8c7c0')]
def rgb(s): return tuple(bytes.fromhex(s[1:]))
for i,(name,(a,b)) in enumerate(zip(TITLES,COLORS)):
    for kind,w,h in [('poster',600,900),('wide',1600,900)]:
        im=Image.new('RGB',(w,h)); p=im.load(); top,bottom=rgb(a),rgb(b)
        for y in range(h):
            t=y/h
            c=tuple(int(top[k]*(1-t)+bottom[k]*t*.8) for k in range(3))
            for x in range(w): p[x,y]=c
        d=ImageDraw.Draw(im,'RGBA')
        sx=w*.69; sy=h*.30; r=h*(.115+i%3*.016)
        d.ellipse((sx-r,sy-r,sx+r,sy+r),fill=(*bottom,230))
        for layer in range(5):
            y0=h*(.51+layer*.096)
            pts=[(x,y0+math.sin(x/w*5+i*.8+layer*.7)*h*.09+math.sin(x/w*11+layer)*h*.025) for x in range(-10,w+20,12)]
            d.polygon(pts+[(w,h),(0,h)],fill=(*tuple(int(z*(.7-layer*.10)) for z in top),245))
        d.line([(w*.14,h*.86),(w*.48,h*.64),(w*.63,h*.82)],fill=(*bottom,90),width=2)
        font='C:/Windows/Fonts/msyh.ttc'
        if kind=='poster':
            d.text((w*.09,h*.073),f'SUNNY ORIGINAL  /  {i+1:02}',font=ImageFont.truetype(font,17),fill=(247,238,216,210))
            d.text((w*.09,h*.68),name,font=ImageFont.truetype(font,54),fill=(247,238,216,255))
            d.text((w*.09,h*.79),'AN ORIGINAL DEMO STORY',font=ImageFont.truetype(font,16),fill=(247,238,216,180))
            d.text((w*.09,h*.91),'原创演示素材 · 非真实影视作品',font=ImageFont.truetype(font,14),fill=(247,238,216,150))
        im.save(OUT/f'{i}-{kind}.png',optimize=True)
print(f'Generated {len(TITLES)*2} original artwork files')
