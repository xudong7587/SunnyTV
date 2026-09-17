#!/usr/bin/env python3
"""Package the offline design prototype as one HTML file. Standard library only."""
from pathlib import Path
import argparse
import base64
import re

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=ROOT / 'preview' / 'SunnyTV-preview.html')
args = parser.parse_args()
html = (ROOT / 'preview/index.html').read_text(encoding='utf-8')
css = (ROOT / 'preview/ui.css').read_text(encoding='utf-8')
js = (ROOT / 'preview/ui.js').read_text(encoding='utf-8')
html = re.sub(r'<link[^>]+href="ui.css"[^>]*>', lambda _: '<style>' + css + '</style>', html)
html = html.replace('<script src="ui.js"></script>', '<script>' + js + '</script>')
for image in (ROOT / 'preview/assets').glob('*.jpg'):
    html = html.replace('assets/' + image.name, 'data:image/jpeg;base64,' + base64.b64encode(image.read_bytes()).decode('ascii'))
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(html, encoding='utf-8')
print(args.output)
