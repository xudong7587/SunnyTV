#!/usr/bin/env python3
"""Fail closed unless the candidate has exactly the dev14 signing certificate.
Only public certificate fingerprints are inspected. Never reads/exposes keystore secrets.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
BASELINE_SHA256='6f798dae3c4b3d3dd9dbd969859a6e190d49fc642d4fa41fe346eb1325538b04'
def certs(tool,apk):
    result=subprocess.run([tool,'verify','--print-certs',str(apk)],check=True,capture_output=True,text=True)
    values=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$',result.stdout,re.M)
    if len(values)!=1: raise RuntimeError('Expected exactly one verified APK signer')
    return values[0].lower()
def main():
    p=argparse.ArgumentParser()
    p.add_argument('--apksigner',required=True);p.add_argument('--baseline',type=Path,required=True)
    p.add_argument('--candidate',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args()
    if hashlib.sha256(a.baseline.read_bytes()).hexdigest()!=BASELINE_SHA256:
        raise RuntimeError('dev14 reference APK hash mismatch; refusing a different signing baseline')
    baseline=certs(a.apksigner,a.baseline); current=certs(a.apksigner,a.candidate)
    record={'baseline':'v0.1.0-dev14','baselineApkSha256':BASELINE_SHA256,
            'sameSignerAsDev14':current==baseline,'certificateSha256':current,
            'baselineCertificateSha256':baseline}
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(record,indent=2)+'\n')
    if current!=baseline: raise RuntimeError('APK signer differs from dev14: publishing is blocked')
    print('Verified: candidate and pinned dev14 APK use the same signing certificate.')
if __name__=='__main__': main()
