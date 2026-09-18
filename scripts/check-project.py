#!/usr/bin/env python3
"""Source-package sanity checks. This is NOT Android compilation or a dependency audit."""
from pathlib import Path
import ast
import json
import subprocess
import shutil
import hashlib
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
checks = []

def check(name, action):
    try:
        action()
        checks.append({'name': name, 'passed': True})
    except Exception as exc:
        checks.append({'name': name, 'passed': False, 'error': str(exc)})

for path in (root/'app/src/main').rglob('*.xml'):
    check('XML ' + str(path.relative_to(root)), lambda p=path: ET.parse(p))
for path in (root/'scripts').glob('*.py'):
    check('Python syntax ' + path.name, lambda p=path: ast.parse(p.read_text(encoding='utf-8')))
if shutil.which('bash'):
    for path in (root/'scripts').glob('*.sh'):
        check('Bash syntax ' + path.name, lambda p=path: subprocess.run(['bash','-n',str(p)],check=True,capture_output=True))
if shutil.which('node') and (root/'preview/ui.js').exists():
    check('JavaScript syntax', lambda: subprocess.run(['node','--check',str(root/'preview/ui.js')],check=True,capture_output=True))

FONT_HASHES = {
    'app/src/main/assets/fonts/fz_zhenghei.ttf': 'a92f243f92a8af7b1b9b0f31782b965bd9882a7331e88b10d3b2100d0941fd94',
    'app/src/main/assets/fonts/fz_youhei.ttf': '2050762a1c6478d1ef85208013cd2d72cf3248c8f50b02ef91ec3d4570a0f03a',
    'app/src/main/assets/fonts/coca_cola_care.ttf': '2c1075fddb3445501e9f7b3fd4ed01c796f2ac90cffe55059b45003ad3701192',
}

def safe_native_assets():
    forbidden_images = ('.jpg','.jpeg','.png','.woff','.woff2','.otf')
    assert not [p for p in (root/'app/src/main').rglob('*') if p.suffix.lower() in forbidden_images]
    for path in (root/'app/src/main').rglob('*.ttf'):
        rel=str(path.relative_to(root)).replace('\\','/')
        expected=FONT_HASHES.get(rel)
        assert expected, f'Unexpected bundled font: {rel}'
        actual=hashlib.sha256(path.read_bytes()).hexdigest()
        assert actual==expected, f'Bundled font checksum mismatch: {rel}'
check('No preview imagery; bundled font assets are explicitly allow-listed', safe_native_assets)

result = {'scope':'source/XML/script sanity only; not Android compile', 'checks':checks}
(root/'docs/project-check-results.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2,ensure_ascii=False))
raise SystemExit(0 if all(c['passed'] for c in checks) else 1)
