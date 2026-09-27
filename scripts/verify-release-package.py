#!/usr/bin/env python3
"""Fail closed on APK identity, debug flags, car-only entry points and private fonts."""
import argparse
import hashlib
import re
import subprocess
import zipfile


# Media3 UI 1.9.4 res/font/roboto_medium_numbers.ttf (also present in dev27).
# Release resource optimization renames its path, so check bytes, not a filename exception.
MEDIA3_NUMBERS_SHA256 = 'acbf6c59d8c5765ffa9af2a839249e2800e0b107aad4045cf5c070765fa29322'


def validate(badging, manifest, names, version_code, version_name, font_hashes=None):
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ('io.github.xudong7587.sunnytv.debug', str(version_code), version_name):
        raise ValueError('Release package/version identity mismatch')
    if 'application-debuggable' in badging or re.search(r'android:(debuggable|testOnly).*=(?:\(type [^)]+\))?(?:0xffffffff|0x1|true)\b', manifest):
        raise ValueError('Debuggable/test-only APK must not be released')
    if 'com.ucar' in manifest.lower() or 'carlinktest' in manifest.lower():
        raise ValueError('Carlink content is outside this release')
    for name in names:
        if name.lower().endswith(('.jks', '.keystore')):
            raise ValueError('Signing material in APK')
        if name.lower().endswith(('.ttf', '.otf', '.ttc')):
            if not name.startswith('res/') or (font_hashes or {}).get(name) != MEDIA3_NUMBERS_SHA256:
                raise ValueError('Unapproved font in APK')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apk', required=True)
    parser.add_argument('--version-code', required=True, type=int)
    parser.add_argument('--version-name', required=True)
    args = parser.parse_args()
    def dump(*parts):
        return subprocess.run([args.aapt, 'dump', *parts], check=True, capture_output=True, text=True).stdout
    with zipfile.ZipFile(args.apk) as archive:
        validate(dump('badging', args.apk), dump('xmltree', args.apk, 'AndroidManifest.xml'),
                 archive.namelist(), args.version_code, args.version_name,
                 {name: hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist()
                  if name.lower().endswith(('.ttf', '.otf', '.ttc'))})
    print('Verified: expected package/version, non-debuggable, no car entry or private fonts/keys.')


if __name__ == '__main__':
    main()
