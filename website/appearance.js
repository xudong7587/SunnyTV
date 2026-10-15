/* Independent theme / palette / material controls following Sunny UI's token contract. */
(() => {
  const root = document.documentElement, dialog = document.querySelector('#appearance-dialog');
  const key = 'sunnytv-website-appearance';
  const palettes = {
    forest: {name:'森野', light:['#315b46','#b56c42','#d2a13d'], dark:['#a7d6b5','#e6ae86','#eed08b']},
    ocean: {name:'海岸', light:['#285fa0','#167b79','#ba7a33'], dark:['#9cc6ff','#88d5cc','#edc58c']},
    rose: {name:'玫瑰', light:['#9c3f62','#745099','#a96938'], dark:['#edacc3','#ccb4f1','#ecc69d']},
    sunset: {name:'日落', light:['#a4562c','#9d465a','#6a657d'], dark:['#f1b58f','#edaaba','#c2b7dd']}
  };
  const materials={paper:['纸页档案','温暖纸面与细描边'],default:['清晰默认','中性色与轻量层次'],aurora:['极光玻璃','多色柔光与通透层次'],glass:['iOS 玻璃','清透亮边与轻磨砂'],soft:['柔软浮雕','柔和方向性明暗'],outline:['精致描边','纤细轮廓与轻底色'],duotone:['双色渐层','有方向的双色过渡'],satin:['缎光瓷面','细腻高光与柔阴影']};
  const defaults={theme:'light',palette:'forest',material:'paper'};
  function normalize(value){return {theme:['light','dark'].includes(value?.theme)?value.theme:defaults.theme,palette:palettes[value?.palette]?value.palette:defaults.palette,material:materials[value?.material]?value.material:defaults.material};}
  let state=defaults;
  try{state=normalize(JSON.parse(localStorage.getItem(key)));}catch{}
  function apply(save=true){root.dataset.theme=state.theme;root.dataset.material=state.material;root.dataset.palette='multi';const p=palettes[state.palette];['accent','secondary','tertiary'].forEach((name,i)=>{root.style.setProperty(`--${name}-light`,p.light[i]);root.style.setProperty(`--${name}-dark`,p.dark[i]);});document.querySelectorAll('[data-group]').forEach(b=>b.setAttribute('aria-pressed',String(state[b.dataset.group]===b.dataset.value)));document.querySelector('meta[name="theme-color"]').content=state.theme==='dark'?'#191815':'#eee9de';if(save)try{localStorage.setItem(key,JSON.stringify(state));}catch{}}
  document.querySelectorAll('#themes button').forEach(b=>b.dataset.group='theme');
  for(const [id,p] of Object.entries(palettes)){const b=document.createElement('button');b.type='button';b.dataset.value=id;b.dataset.group='palette';const swatch=document.createElement('i');swatch.style.background=`conic-gradient(${p.light.join(',')},${p.light[0]})`;swatch.setAttribute('aria-hidden','true');b.append(swatch,document.createTextNode(p.name));document.querySelector('#palettes').append(b);}
  for(const [id,m] of Object.entries(materials)){const b=document.createElement('button');b.type='button';b.dataset.value=id;b.dataset.group='material';b.className='appearance-control';b.append(document.createTextNode(m[0]));const small=document.createElement('small');small.textContent=m[1];b.append(small);document.querySelector('#materials').append(b);}
  document.querySelectorAll('[data-group]').forEach(b=>b.addEventListener('click',()=>{state={...state,[b.dataset.group]:b.dataset.value};apply();}));
  ['appearance-open','try-appearance'].forEach(id=>document.getElementById(id).addEventListener('click',()=>dialog.showModal()));
  dialog.querySelector('.close').addEventListener('click',()=>dialog.close());
  dialog.addEventListener('click',e=>{if(e.target===dialog){const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)dialog.close();}});
  document.querySelector('#appearance-reset').addEventListener('click',()=>{state={...defaults};apply();});
  window.addEventListener('storage',e=>{if(e.key===key){try{state=normalize(JSON.parse(e.newValue));apply(false);}catch{}}});apply(false);
})();
