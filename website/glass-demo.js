import { enhanceLiquidGlass } from './assets/liquid-glass-2.1.0.js';

const toolbar=document.querySelector('[data-web-glass]');
const dialog=document.querySelector('#glass-info');
if(toolbar && dialog){
  enhanceLiquidGlass(toolbar,{radius:40});
  enhanceLiquidGlass(dialog);
  const image=document.querySelector('#glass-scene');
  for(const button of document.querySelectorAll('[data-glass-scene]'))button.addEventListener('click',()=>{
    image.src=`assets/cinema/${button.dataset.glassScene}-wide.webp`;
    image.alt=`${button.textContent}背景中的 SunnyUI 网页玻璃演示`;
    toolbar.style.setProperty('--web-glass-ink',button.dataset.glassScene==='scifi'?'#ffffff':'#141416');
    for(const option of document.querySelectorAll('[data-glass-scene]'))option.setAttribute('aria-pressed',String(option===button));
  });
  document.querySelector('#glass-info-open').addEventListener('click',()=>dialog.showModal());
  document.querySelector('#glass-info-close').addEventListener('click',()=>dialog.close());
}
