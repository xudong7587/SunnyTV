import { enhanceLiquidGlass } from './assets/liquid-glass-2.1.0.js';
const toolbar=document.querySelector('[data-web-glass]');
const image=document.querySelector('#glass-scene');
if(toolbar && image){
  enhanceLiquidGlass(toolbar,{radius:40});
  for(const button of document.querySelectorAll('[data-glass-scene]'))button.addEventListener('click',()=>{
    image.src=`assets/cinema/${button.dataset.glassScene}-wide.webp`;
    image.alt=`${button.textContent}背景中的网页玻璃材质演示`;
    toolbar.style.setProperty('--web-glass-ink',button.dataset.glassScene==='scifi'?'#ffffff':'#141416');
    for(const option of document.querySelectorAll('[data-glass-scene]'))option.setAttribute('aria-pressed',String(option===button));
  });
}
