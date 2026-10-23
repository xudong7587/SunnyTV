"""Generate a redistributable geometry fixture: encoded bars, circle, and side markers."""
import argparse
import subprocess
import tempfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--ffmpeg', default='ffmpeg')
parser.add_argument('--out', default='app/src/androidTest/assets/quality/framing.mp4')
args = parser.parse_args()
with tempfile.TemporaryDirectory(prefix='sunny-framing-') as folder:
    ppm = Path(folder) / 'frame.ppm'
    data = bytearray()
    for y in range(180):
        for x in range(320):
            rgb = (0, 0, 0) if y < 24 or y >= 156 else (24, 80, 200)
            if 24 <= y < 156 and x < 24:
                rgb = (230, 30, 30)
            if 24 <= y < 156 and x >= 296:
                rgb = (30, 220, 30)
            if (x - 160)**2 + (y - 90)**2 <= 26**2:
                rgb = (245, 245, 245)
            data.extend(rgb)
    ppm.write_bytes(b'P6\n320 180\n255\n' + data)
    subprocess.run([args.ffmpeg, '-v', 'error', '-y', '-loop', '1', '-framerate', '10',
                    '-i', str(ppm), '-t', '30', '-c:v', 'libx264', '-threads', '1',
                    '-preset', 'ultrafast', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', args.out], check=True)
