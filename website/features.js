(() => {
  const demo = document.querySelector('#palette-demo');
  const label = document.querySelector('#palette-name');
  const palettes = {
    blue: {name:'晴蓝',accent:'#376bd8',tint:'#edf2fc'},
    celadon: {name:'青瓷',accent:'#2f7f76',tint:'#eaf4f1'},
    violet: {name:'鸢紫',accent:'#8855cc',tint:'#f1ecf8'},
    amber: {name:'琥珀',accent:'#b27a16',tint:'#faf2e3'}
  };
  if (demo && label) {
    for (const button of demo.querySelectorAll('[data-palette]')) {
      button.addEventListener('click', () => {
        const palette = palettes[button.dataset.palette];
        if (!palette) return;
        demo.style.setProperty('--demo-accent', palette.accent);
        demo.style.setProperty('--demo-tint', palette.tint);
        label.textContent = palette.name;
        for (const option of demo.querySelectorAll('[data-palette]')) {
          option.setAttribute('aria-pressed', String(option === button));
        }
      });
    }
  }

  const remote = document.querySelector('[data-remote-demo]');
  if (!remote) return;
  const items = [...remote.querySelectorAll('[data-remote-item]')];
  const previous = remote.querySelector('[data-remote-prev]');
  const next = remote.querySelector('[data-remote-next]');
  const selection = remote.querySelector('#remote-selection');
  if (!items.length || !previous || !next || !selection) return;
  let selected = 0;
  function select(index, focus = false) {
    selected = Math.max(0, Math.min(items.length - 1, index));
    items.forEach((item, i) => {
      item.classList.toggle('is-selected', i === selected);
      item.setAttribute('aria-pressed', String(i === selected));
      item.tabIndex = i === selected ? 0 : -1;
    });
    previous.disabled = selected === 0;
    next.disabled = selected === items.length - 1;
    selection.textContent = items[selected].getAttribute('aria-label');
    if (focus) items[selected].focus({preventScroll:true});
  }
  items.forEach((item, index) => {
    item.addEventListener('click', () => select(index, true));
    item.addEventListener('focus', () => select(index));
    item.addEventListener('keydown', event => {
      if (!['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
      event.preventDefault();
      const index = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 :
        selected + (event.key === 'ArrowRight' ? 1 : -1);
      select(index, true);
    });
  });
  previous.addEventListener('click', () => select(selected - 1, true));
  next.addEventListener('click', () => select(selected + 1, true));
  select(0);
})();
