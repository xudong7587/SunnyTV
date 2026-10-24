#!/usr/bin/env python3
"""Render the actual SDL app with synthetic Emby data and exercise its finger event handlers."""
import base64
import io
import functools
import http.server
import json
import math
import os
from pathlib import Path
import subprocess
import tempfile
import threading
from urllib.parse import urlsplit

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TITLES = ['逐光计划', '海岸来信', '山野之间', '夜航星图', '风的形状', '远方有回声', '薄雾清晨', '潮汐对白']
COLORS = [(27, 62, 75), (42, 83, 98), (55, 81, 69), (41, 42, 72), (79, 88, 74), (91, 58, 61), (64, 91, 92), (28, 84, 99)]
FONT = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'


def art(directory):
    for index, title in enumerate(TITLES):
        for kind, size in [('Primary', (480, 720)), ('Backdrop', (1280, 720))]:
            width, height = size
            color = COLORS[index]
            image = Image.new('RGB', size)
            draw = ImageDraw.Draw(image)
            for y in range(height):
                t = y / height
                draw.line((0, y, width, y), fill=tuple(int(c * (1 - t) + v * t) for c, v in zip(color, (114, 101, 73))))
            cx, cy, radius = int(width * .7), int(height * .3), int(min(width, height) * .18)
            draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=(224 - index * 4, 173 + index * 3, 104 + index * 5))
            for band in range(4):
                points = [(x, int(height * (.66 + band * .08) + math.sin(x / width * 5 + index + band) * height * .12)) for x in range(0, width + 1, 8)]
                points += [(width, height), (0, height)]
                draw.polygon(points, fill=(max(4, color[0] - band * 5), max(12, color[1] - band * 10), max(19, color[2] - band * 9)))
            if kind == 'Primary':
                draw.text((28, 30), f'SUNNY ORIGINAL / {index + 1:02d}', font=ImageFont.truetype(FONT, 12), fill=(235, 235, 220))
                draw.text((28, 550), title, font=ImageFont.truetype(FONT, 38), fill=(245, 245, 232))
                draw.text((28, 615), 'AN ORIGINAL DEMO STORY', font=ImageFont.truetype(FONT, 13), fill=(219, 221, 210))
            image.save(directory / f'{index + 1}-{kind}.jpg', quality=90)
        logo = Image.new('RGBA', (850, 115))
        ImageDraw.Draw(logo).text((0, 0), title, font=ImageFont.truetype(FONT, 80), fill=(245, 245, 242, 255))
        logo.save(directory / f'{index + 1}-Logo.png')
    for index, name in enumerate(['原创电影', '原创剧集', '纪录片']):
        image = Image.new('RGB', (640, 360), COLORS[index])
        ImageDraw.Draw(image).text((56, 130), name, font=ImageFont.truetype(FONT, 60), fill=(240, 244, 232))
        image.save(directory / f'lib{index + 1}-Primary.jpg')


def movie(index):
    return {'Id': f'm{index + 1}', 'Name': TITLES[index], 'Type': 'Movie', 'ProductionYear': 2026,
            'Overview': '一段关于相遇与发现的原创演示故事。让自己的媒体库，回到大屏。',
            'Genres': ['原创演示'], 'CommunityRating': 8.2, 'RunTimeTicks': 108 * 60 * 10_000_000,
            'ImageTags': {'Primary': 'p1', 'Logo': 'l1'}, 'BackdropImageTags': ['b1'],
            'UserData': {'PlaybackPositionTicks': 34 * 60 * 10_000_000 if index < 2 else 0}}


class Fixture(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        path = urlsplit(self.path).path
        if '/Images/' in path:
            item, kind = path.split('/')[2], path.split('/')[4]
            name = (item[1:] if item.startswith('m') else item) + '-' + kind
            extension = '.png' if kind == 'Logo' else '.jpg'
            file = assets / (name + extension)
            if not file.exists():
                self.send_error(404)
                return
            body, content_type = file.read_bytes(), 'image/png' if kind == 'Logo' else 'image/jpeg'
        else:
            items = [movie(i) for i in range(len(TITLES))]
            if path.endswith('/Views'):
                data = {'Items': [{'Id': f'lib{i+1}', 'Name': name, 'Type': 'CollectionFolder', 'IsFolder': True, 'ImageTags': {'Primary': 'library'}} for i, name in enumerate(['原创电影', '原创剧集', '纪录片'])]}
            elif path.endswith('/Items/Latest'):
                data = items
            elif path.endswith('/Items/Resume'):
                data = {'Items': items[:2], 'TotalRecordCount': 2}
            elif '/Items/m' in path:
                data = items[int(path.rsplit('/m', 1)[1]) - 1]
            elif path.endswith('/Items'):
                data = {'Items': items, 'TotalRecordCount': len(items)}
            else:
                self.send_error(404)
                return
            body, content_type = json.dumps(data, ensure_ascii=False).encode(), 'application/json'
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass


if __name__ == '__main__':
    output = ROOT / 'build/ui-screens'
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='sunny-ui-') as tmp:
        assets = Path(tmp) / 'art'
        assets.mkdir()
        art(assets)
        server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        config_dir = Path(tmp) / 'sdmc:/switch/SunnyTV'
        config_dir.mkdir(parents=True)
        config = {'base': f'http://127.0.0.1:{server.server_port}/', 'userId': 'u1', 'token': 'fixture', 'device': 'ui-fixture', 'remember': True, 'dark': True, 'accent': 2}
        (config_dir / 'config.json').write_text(json.dumps(config))
        env = os.environ.copy()
        env['NO_PROXY'] = env['no_proxy'] = '127.0.0.1,localhost'
        env['SUNNYTV_UI_PREVIEW'] = str(output)
        try:
            subprocess.run([str(ROOT / 'build/host/ui-tests')], cwd=tmp, env=env, check=True, timeout=45)
            for name in ['home', 'home-rotated', 'libraries', 'library', 'grid', 'detail', 'settings', 'appearance', 'light']:
                with Image.open(output / f'{name}.png') as image:
                    assert image.size == (1280, 720)
                    assert len(image.resize((128, 72)).getcolors(10000)) > 100, f'Blank screen: {name}'
            # A real finger swipe must replace the artwork inside the same featured square.
            before = Image.open(output / 'home.png').convert('RGB')
            after = Image.open(output / 'home-rotated.png').convert('RGB')
            assert before.crop((560, 355, 860, 650)).tobytes() != after.crop((560, 355, 860, 650)).tobytes(), 'Hero swipe did not change fixed-slot artwork'
            print('9 rendered screens verified; real finger swipe rotated the fixed hero, navigation and theme changes passed')
            review = Image.new('RGB', (960, 810))
            for index, name in enumerate(['home', 'home-rotated', 'library', 'grid', 'detail', 'light']):
                with Image.open(output / f'{name}.png') as screen:
                    review.paste(screen.convert('RGB').resize((480, 270)), ((index % 2) * 480, (index // 2) * 270))
            stream = io.BytesIO()
            review.save(stream, format='JPEG', quality=85)
            print('UI_REVIEW_JPEG_BASE64=' + base64.b64encode(stream.getvalue()).decode())
        finally:
            server.shutdown()
            server.server_close()
