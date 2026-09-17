import asyncio,json,os,shutil,subprocess,sys,tempfile
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
SCREENS=ROOT/'docs/screens'
SCREENS.mkdir(parents=True,exist_ok=True)
async def main():
 async with async_playwright() as p:
  browser=await p.chromium.launch(executable_path=os.environ.get('SUNNY_CHROMIUM') or shutil.which('chromium'),headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
  page=await browser.new_page(viewport={'width':1920,'height':1080},device_scale_factor=1)
  errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  # Inline resources avoid file:// restrictions in managed Chromium environments.
  with tempfile.TemporaryDirectory(prefix='sunny-preview-') as temp:
   single=Path(temp)/'preview.html'
   subprocess.run([sys.executable,str(ROOT/'scripts/export-preview.py'),'--output',str(single)],check=True)
   await page.set_content(single.read_text(encoding='utf-8'),wait_until='load')
  await page.wait_for_timeout(400)
  await page.screenshot(path=str(SCREENS/'01-home.png'))
  results=[]
  results.append(('home library cards',await page.locator('.library-card').count()==6))
  await page.locator('.library-card').first.click()
  await page.wait_for_timeout(400)
  await page.screenshot(path=str(SCREENS/'02-library.png'))
  results.append(('library hero',await page.locator('.library-hero').count()==1))
  await page.locator('.library-feature [data-action=grid]').click()
  results.append(('poster grid',await page.locator('.poster-grid .poster').count()==8))
  await page.screenshot(path=str(SCREENS/'03-grid.png'))
  await page.locator('.poster-grid .poster').first.click()
  results.append(('detail',await page.locator('.detail .info h1').inner_text()=='后室'))
  await page.locator('.detail .info [data-action=player]').first.click()
  results.append(('player mock labeled',await page.locator('.player-pill').is_visible()))
  await page.locator('[data-action=subtitles]').click()
  results.append(('subtitles panel',await page.locator('.player-panel').is_visible()))
  await page.keyboard.press('Escape')
  await page.keyboard.press('Escape')
  await page.locator('nav [data-action=settings]').click()
  await page.screenshot(path=str(SCREENS/'04-settings.png'))
  await page.locator('[data-action=settings-tab][data-id=appearance]').click()
  await page.locator('[data-action=toggle][data-id=motion]').click()
  results.append(('reduce motion toggle',await page.locator('body').evaluate('(e)=>e.classList.contains("reduced")')))
  await page.locator('nav [data-action=search]').click()
  await page.locator('.search-input').fill('后室')
  await page.locator('.search-input').press('Enter')
  results.append(('search filters',await page.locator('.poster').count()==1))
  await page.locator('.search-input').fill('<img src=x onerror=alert(1)>')
  await page.locator('.search-input').press('Enter')
  results.append(('search escaped',await page.locator('.empty').count()==1))
  await page.locator('nav [data-action=cloud]').click()
  await page.locator('[data-action=folder]').click()
  results.append(('folder entries',await page.locator('.file-row').count()==5))
  await page.locator('nav [data-action=home]').click()
  await page.locator('nav [data-action=home]').focus()
  for k in ['ArrowRight','ArrowRight','ArrowDown','ArrowLeft','ArrowDown','ArrowUp']:
   await page.keyboard.press(k)
  results.append(('keyboard retains button focus',await page.evaluate('document.activeElement.tagName')=='BUTTON'))
  results.append(('no browser JS exceptions',not errors))
  await browser.close()
  report={'tests':[{'name':name,'passed':ok} for name,ok in results],'browser_errors':errors,'scope':'Offline HTML design preview only; not Android APK or real servers.'}
  (ROOT/'docs/preview-test-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
  print(json.dumps(report,ensure_ascii=False,indent=2))
  assert all(ok for _,ok in results), "Browser prototype check failed"
asyncio.run(main())
