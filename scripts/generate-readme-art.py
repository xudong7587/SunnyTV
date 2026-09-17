import html
from pathlib import Path

OUT=Path(__file__).resolve().parents[1]/'docs'/'images'
OUT.mkdir(parents=True,exist_ok=True)
PALETTES=[('#153840','#65d9c3'),('#24284c','#f5b676'),('#352d42','#d998bd'),('#234340','#c0d392'),('#263e58','#80b5ed'),('#443738','#efa783'),('#313555','#aca2e1')]
TITLES=['星港来信','微光列车','雾岛手记','风的形状','蓝色回声','山海之间','第七颗种子']
def text(x,y,t,size=22,fill='#eff4ee',weight=400):
 return f'<text x="{x}" y="{y}" fill="{fill}" font-size="{size}" font-weight="{weight}">{html.escape(str(t))}</text>'
def rect(x,y,w,h,fill,rx=18,extra=''):
 return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" {extra}/>'
def sun(x,y,size=42):
 return f'<g transform="translate({x} {y}) scale({size/100})"><g stroke="#ffd879" stroke-width="7" stroke-linecap="round"><path d="M50 7V17 M50 83V93 M7 50H17 M83 50H93 M20 20L27 27 M73 73L80 80 M20 80L27 73 M73 27L80 20"/></g><path fill="url(#gold)" fill-rule="evenodd" d="M50 25 A25 25 0 1 1 50 75 A25 25 0 1 1 50 25Z M43 39V62L63 50Z"/></g>'
def base(body,title):
 return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="900" viewBox="0 0 1600 900" role="img" aria-label="{title}"><defs><linearGradient id="gold" x2="1" y2="1"><stop stop-color="#ffe99e"/><stop offset="1" stop-color="#f4a52d"/></linearGradient><linearGradient id="fade" x2="0" y2="1"><stop stop-color="#0b1418" stop-opacity="0"/><stop offset="1" stop-color="#0b1418"/></linearGradient></defs><g font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif">{rect(0,0,1600,900,'#0b1418',0)}{body}</g></svg>'''
def poster(x,y,w,h,i,label=True):
 a,b=PALETTES[i%7];uid=f'p{x}_{y}'
 body=f'<defs><clipPath id="{uid}">{rect(x,y,w,h,"white",18)}</clipPath></defs><g clip-path="url(#{uid})">'
 body+=rect(x,y,w,h,a,0)
 body+=f'<circle cx="{x+w*.7}" cy="{y+h*.27}" r="{w*.30}" fill="{b}" opacity=".85"/>'
 for j in range(5):
  body+=f'<path d="M{x-w*.2} {y+h*(.55+j*.10)} Q{x+w*.5} {y+h*(.12+j*.14)} {x+w*1.2} {y+h*(.62+j*.11)} L{x+w*1.2} {y+h} H{x-w*.2}Z" fill="{a if j%2 else b}" opacity=".4"/>'
 body+=rect(x,y+h*.55,w,h*.45,'url(#fade)',0)
 if label: body+=text(x+16,y+h-24,TITLES[i%7],min(27,w/6),weight=600)
 return body+'</g>'
def brand(): return sun(46,30,48)+text(108,66,'SunnyTV',29,weight=700)
def foot(): return text(48,866,'界面示意 · 片名、图形与数据均为原创虚构素材',16,'#869f9c')
def pill(x,y,label,active=False): return rect(x,y,60 if len(label)==1 else 144,52,'#dce9bf' if active else '#233335',26)+text(x+20,y+34,label,20,'#203331' if active else '#e4eeea')
def header(x,y,name): return text(x,y,name,25,weight=600)+pill(x+250,y-36,'→')
body=brand()+rect(676,26,274,62,'#1a2a2d',31)+text(704,66,'⌂    ▥    ⌕    ⚙',25)
body+=f'<circle cx="1240" cy="150" r="300" fill="#244540" opacity=".35"/>'
body+=text(48,192,'首映推荐',18,'#dce9bf')+text(48,268,'星港来信',62,weight=700)+text(48,306,'2030 · 科幻 · 104 分钟',19,'#a9bdb8')
body+=text(48,367,'一封来自无人星港的信，让远行者重新找到归途。',21,'#bfceca')+text(48,403,'在缓慢转动的星环下，陌生人开始交换各自的故事。',21,'#bfceca')
body+=pill(48,446,'▷',True)+pill(124,446,'ⓘ')+pill(200,446,'Ⅱ')
body+=text(48,543,'继续播放',17,'#b1c6be')
for i in range(2):
 x=48+i*260;body+=rect(x,558,244,62,'#213032',31)+f'<circle cx="{x+31}" cy="589" r="21" fill="{PALETTES[i][1]}"/>'+text(x+65,584,TITLES[i+1],16)+text(x+65,606,'剩余 28 分钟',12,'#b1c6be')
body+=poster(620,293,312,312,0)
for i in range(6):body+=poster(946+i*99,293,87,312,i+1,False)
body+=header(48,696,'星际故事 · 最新入库')
for i in range(7): body+=poster(48+i*216,720,200,104,i)
body+=foot();(OUT/'home.svg').write_text(base(body,'SunnyTV 首页界面示意'),encoding='utf-8')
body=brand()+text(48,160,'单集排布，按你的习惯选择',38,weight=700)+text(48,201,'《雾岛手记》· 虚构剧集 · 三种选集方式',20,'#a9bdb8')
for i,title in enumerate(['横向海报','竖向列表','数字选集']):
 x=48+i*512;body+=rect(x,244,480,550,'#172629',24)+text(x+24,294,title,26,'#dce9bf',600)
 if i==0:
  body+=poster(x+24,324,300,187,2)+poster(x+338,324,118,187,3,False)
  body+=text(x+24,548,'第 1 集 · 潮汐时刻',21,weight=600)+text(x+24,589,'一座灯塔亮起了陌生的信号。岛上的',18,'#b7c9c4')+text(x+24,619,'记录员沿着海岸寻找它的来处……',18,'#b7c9c4')
 elif i==1:
  for j in range(3):
   yy=326+j*143;body+=poster(x+22,yy,145,99,j+2,False)+text(x+185,yy+23,f'第 {j+1} 集 · '+['潮汐时刻','风中的地图','无人来信'][j],19,weight=600)+text(x+185,yy+60,'雾散之前，新的线索',16,'#b7c9c4')+text(x+185,yy+87,'留在岸边的石阶上。',16,'#b7c9c4')
 else:
  for j in range(30):
   xx=x+24+(j%5)*88;yy=325+(j//5)*70
   body+=rect(xx,yy,74,54,'#dce9bf' if j==16 else '#283b3d',12)+text(xx+23,yy+35,j+1,21,'#19302b' if j==16 else '#dce8e2')
body+=foot();(OUT/'episodes.svg').write_text(base(body,'SunnyTV 三种单集排布示意'),encoding='utf-8')
body=brand()+text(48,156,'按媒体库保存自己的展示方式',37,weight=700)+text(48,198,'深浅主题 · 五档界面大小 · 独立媒体库视图',20,'#a9bdb8')
body+=text(48,278,'风景档案 · 最新入库',26,weight=600)+pill(340,241,'→')
for i in range(7):body+=poster(48+i*216,310,200,325,i)
body+=rect(48,675,1504,135,'#1a2c2e',22)+text(76,720,'为大屏设计，也适配手机竖屏',26,weight=600)+text(76,765,'原生文字与图标  /  遥控器焦点  /  手机分区手势  /  受限的图片缓存',20,'#b7c9c4')
body+=foot();(OUT/'library.svg').write_text(base(body,'SunnyTV 媒体库界面示意'),encoding='utf-8')
logo=f'<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128"><defs><linearGradient id="gold" x2="1" y2="1"><stop stop-color="#ffe99e"/><stop offset="1" stop-color="#f4a52d"/></linearGradient></defs>{rect(0,0,128,128,"#fff8ea",28)}{sun(16,16,96)}</svg>'
(OUT/'sunnytv-logo.svg').write_text(logo,encoding='utf-8')
print('Generated 4 original SVG assets; no external images or network requests.')
