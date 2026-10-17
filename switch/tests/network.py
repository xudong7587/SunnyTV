#!/usr/bin/env python3
"""Local fixtures only. No user service, login or media is contacted."""
import http.server
import json
import os
from pathlib import Path
import ssl
import subprocess
import tempfile
import threading
import time
from urllib.parse import urlsplit


class Fixture(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def log_message(self, *_):
        pass

    def send(self, body, code=200, headers=None):
        if isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header('Content-Length', str(len(body)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if self.command != 'HEAD':
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = urlsplit(self.path).path
        if path.endswith('redirect-origin'):
            self.send('redirect-body', 302, {'Location': outsider + '/echo?sig=a%2Fb'})
        elif path.endswith('redirect-path'):
            self.send('', 302, {'Location': '/outside/echo?sig=a%2Fb'})
        elif path.endswith('/loop'):
            self.send('', 302, {'Location': '/scope/loop'})
        elif path.endswith('/large'):
            self.send('x' * 4096)
        elif path.endswith('/slow'):
            time.sleep(2)
            self.send('slow')
        elif path.endswith('/index.m3u8'):
            self.send('#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key?st=local"\n#EXTINF:6,\n' + outsider + '/segment?sig=a%2Fb\n', headers={'Content-Type': 'application/vnd.apple.mpegurl'})
        elif path.endswith('/movie'):
            if self.headers.get('Range') == 'bytes=2-5':
                self.send('2345', 206, {'Content-Range': 'bytes 2-5/10', 'Content-Type': 'video/mp4'})
            else:
                self.send('0123456789', headers={'Content-Type': 'video/mp4'})
        else:
            self.send(json.dumps({'path': self.path, 'token': self.headers.get('X-Emby-Token', ''), 'authorization': self.headers.get('Authorization', ''), 'cookie': self.headers.get('Cookie', '')}), headers={'Content-Type': 'application/json'})


def start(server):
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return f'http://127.0.0.1:{server.server_port}'


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix='sunny-network-') as tmp:
        cert, key = str(Path(tmp) / 'cert.pem'), str(Path(tmp) / 'key.pem')
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', key, '-out', cert, '-days', '1', '-subj', '/CN=localhost'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        origin = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
        outside = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
        secure = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cert, key)
        secure.socket = context.wrap_socket(secure.socket, server_side=True)
        outsider = start(outside)
        base = start(origin)
        tls = start(secure).replace('http:', 'https:')
        env = os.environ.copy()
        env['NO_PROXY'] = env['no_proxy'] = '127.0.0.1,localhost'
        try:
            subprocess.run([str(root / 'build/host/network-tests'), base, tls], check=True, env=env, timeout=30)
        finally:
            for server in (origin, outside, secure):
                server.shutdown()
                server.server_close()
