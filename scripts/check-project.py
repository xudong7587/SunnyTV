#!/usr/bin/env python3
"""Source-package sanity checks. This is NOT Android compilation or a dependency audit."""
from pathlib import Path
import ast
import json
import subprocess
import shutil
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

def no_packaged_demo():
    forbidden = ('.jpg','.jpeg','.png','.woff','.woff2','.ttf','.otf')
    # User screenshot and prototype illustrations belong outside the native APK tree.
    assert not [p for p in (root/'app/src/main').rglob('*') if p.suffix.lower() in forbidden]
check('No preview imagery or redistributed fonts in native app', no_packaged_demo)

result = {'scope':'source/XML/script sanity only; not Android compile', 'checks':checks}
(root/'docs/project-check-results.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2,ensure_ascii=False))
raise SystemExit(0 if all(c['passed'] for c in checks) else 1)
