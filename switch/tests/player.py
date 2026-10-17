#!/usr/bin/env python3
import functools
import http.server
import os
from pathlib import Path
import subprocess
import tempfile
import threading


class Handler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='sunny-player-') as tmp:
    media = str(Path(tmp) / 'synthetic.mp4')
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', 'testsrc=size=320x180:rate=24,drawbox=x=0:y=0:w=320:h=40:color=red:t=fill,drawbox=x=0:y=140:w=320:h=40:color=blue:t=fill', '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000', '-t', '5', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-movflags', '+faststart', media], check=True)
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-i', media, '-c', 'copy', '-hls_time', '1', '-hls_list_size', '0', str(Path(tmp) / 'index.m3u8')], check=True)
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Handler, directory=tmp))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    env = os.environ.copy()
    env['NO_PROXY'] = env['no_proxy'] = '127.0.0.1,localhost'
    env['SUNNYTV_TEST_GL_TRACE'] = '1'
    try:
        failures = []
        for driver in ['opengles2', 'opengl']:
            for no_scripts in ['', '1']:
                env['SUNNYTV_TEST_RENDER_DRIVER'] = driver
                env['SUNNYTV_TEST_NO_SCRIPTS'] = no_scripts
                result = subprocess.run([str(root / 'build/host/player-tests'), f'http://127.0.0.1:{server.server_port}/synthetic.mp4', f'http://127.0.0.1:{server.server_port}/index.m3u8'], env=env, timeout=40)
                if result.returncode:
                    failures.append((driver, no_scripts))
        assert not failures, f'Playback regression failed: {failures}'
    finally:
        server.shutdown()
        server.server_close()
