"""Package only native source and the four JNI link objects, never the build cache or SDK."""
from pathlib import Path
import argparse
import tarfile

parser = argparse.ArgumentParser()
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--variant', choices=('Debug', 'RelWithDebInfo', 'Release'), default='RelWithDebInfo')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
objects = list((root/'decoder-ffmpeg/.cxx'/args.variant).rglob('ffmpeg_jni.cc.o'))
abis = ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')
selected = {}
for abi in abis:
    candidates = [p for p in objects if abi in p.parts]
    if len(candidates) != 1:
        raise RuntimeError(f'Expected exactly one JNI link object for {abi}; found {len(candidates)}')
    selected[abi] = candidates[0]
files = [root/p for p in (
    'decoder-ffmpeg/build.gradle.kts', 'decoder-ffmpeg/LICENSE-APACHE-2.0',
    'decoder-ffmpeg/src/main/jni/CMakeLists.txt', 'decoder-ffmpeg/src/main/jni/ffmpeg_jni.cc',
    'scripts/build-audio-decoders.sh')]
files += sorted((root/'decoder-ffmpeg/src/main/java').rglob('*.java'))
args.out.parent.mkdir(parents=True, exist_ok=True)
with tarfile.open(args.out, 'w:gz') as archive:
    for path in files:
        archive.add(path, arcname=path.relative_to(root).as_posix())
    for abi, path in selected.items():
        archive.add(path, arcname=f'objects/{abi}/ffmpeg_jni.cc.o')
print('Packaged native source and four ABI JNI relink objects.')
