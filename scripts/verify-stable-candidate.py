#!/usr/bin/env python3
"""Verify an already device-tested APK without replacing its distributed bytes."""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path


def validate_draft(release, version, commit, digest):
    if not re.fullmatch(r'[0-9a-f]{64}', digest):
        raise ValueError('Expected a lowercase APK SHA256')
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('Expected an exact source commit')
    if (release.get('tag_name') != 'v' + version or release.get('draft') is not True
            or release.get('prerelease') is not False or release.get('target_commitish') != commit):
        raise ValueError('Candidate must be a new stable draft targeting this exact main commit')
    assets = release.get('assets', [])
    if len(assets) != 1 or assets[0].get('name') != f'SunnyTV-v{version}.apk':
        raise ValueError('Draft must contain only the device-tested candidate APK')


def compare_application(candidate, rebuilt):
    # Signing/ZIP metadata and unchanged native decoders are checked separately.
    # DEX, manifest, all compiled resources and third-party shader/color licenses
    # must match the fresh release build byte for byte.
    def contents(path):
        with zipfile.ZipFile(path) as apk:
            return {name: hashlib.sha256(apk.read(name)).hexdigest()
                    for name in apk.namelist()
                    if re.fullmatch(r'classes\d*\.dex', name)
                    or name in ('AndroidManifest.xml', 'resources.arsc')
                    or name.startswith('res/')
                    or name in ('assets/licenses/material-color-utilities.txt',
                                'assets/licenses/miuix-glass.txt',
                                'assets/licenses/miuix-glass-notice.txt')}
    original, fresh = contents(candidate), contents(rebuilt)
    required = {'AndroidManifest.xml', 'resources.arsc', 'classes.dex',
                'assets/licenses/material-color-utilities.txt',
                'assets/licenses/miuix-glass.txt', 'assets/licenses/miuix-glass-notice.txt'}
    if not required <= original.keys() or not required <= fresh.keys():
        raise ValueError('Candidate or rebuilt APK is missing required application entries')
    if original != fresh:
        different = sorted(k for k in original.keys() | fresh.keys() if original.get(k) != fresh.get(k))
        raise ValueError('Candidate differs from committed application build: ' + ', '.join(different))
    return len(original)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--rebuilt', type=Path, required=True)
    parser.add_argument('--native-baseline', required=True)
    args = parser.parse_args()
    release = json.loads(subprocess.check_output(
        ['gh', 'api', f'repos/{args.repository}/releases/tags/v{args.version}'], text=True))
    validate_draft(release, args.version, args.commit, args.sha256)
    subprocess.run(['git', 'fetch', '--no-tags', 'origin', args.native_baseline], check=True)
    subprocess.run(['git', 'diff', '--exit-code', args.native_baseline, args.commit, '--',
                    'decoder-ffmpeg', 'scripts/build-audio-decoders.sh'], check=True)
    folder = Path('build-logs/device-tested-candidate')
    folder.mkdir(parents=True, exist_ok=False)
    subprocess.run(['gh', 'release', 'download', f'v{args.version}', '--repo', args.repository,
                    '--pattern', f'SunnyTV-v{args.version}.apk', '--dir', str(folder)], check=True)
    candidate = folder / f'SunnyTV-v{args.version}.apk'
    digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
    if digest != args.sha256:
        raise ValueError('Candidate APK SHA256 differs from the explicitly selected tested bytes')
    count = compare_application(candidate, args.rebuilt)
    Path('build-logs/candidate-validation.json').write_text(json.dumps({
        'sha256': digest, 'sourceCommit': args.commit, 'matchedApplicationEntries': count,
        'nativeSourceUnchangedSince': args.native_baseline,
        'distribution': 'originalDeviceTestedBytes'}, indent=2) + '\n')
    print(f'Verified candidate SHA256 and {count} committed application entries.')


if __name__ == '__main__':
    main()
