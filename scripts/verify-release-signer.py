#!/usr/bin/env python3
"""Verify the APK against the selected existing signing certificate.
Only public certificate fingerprints are inspected. Never reads/exposes keystore secrets.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
def certs(tool,apk):
    result=subprocess.run([tool,'verify','--print-certs',str(apk)],check=True,capture_output=True,text=True)
    values=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$',result.stdout,re.M)
    if len(values)!=1: raise RuntimeError('Expected exactly one verified APK signer')
    return values[0].lower()
def main():
    p=argparse.ArgumentParser()
    p.add_argument('--apksigner',required=True)
    selection=p.add_mutually_exclusive_group(required=True)
    selection.add_argument('--baseline',type=Path)
    selection.add_argument('--expected-certificate-sha256')
    p.add_argument('--candidate',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args()
    expected=certs(a.apksigner,a.baseline) if a.baseline else a.expected_certificate_sha256.lower()
    if not re.fullmatch(r'[0-9a-f]{64}',expected):
        raise RuntimeError('Expected a valid SHA256 certificate fingerprint')
    current=certs(a.apksigner,a.candidate)
    record={'matchesSelectedSigner':current==expected,'certificateSha256':current,
            'expectedCertificateSha256':expected}
    if a.baseline:
        record.update(baselineApkSha256=hashlib.sha256(a.baseline.read_bytes()).hexdigest())
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(record,indent=2)+'\n')
    if current!=expected: raise RuntimeError('APK signer differs from selected existing key: publishing is blocked')
    print('Verified: APK signature matches the selected existing signing certificate.')
if __name__=='__main__': main()
