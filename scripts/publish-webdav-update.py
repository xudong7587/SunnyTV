#!/usr/bin/env python3
"""Publish a verified release APK first, then switch the fixed read-only DAV manifest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

CERTIFICATE = '5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00'

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--destination',type=Path,default=Path('Z:/V1 Tools/SunnyTV-updata'))
    parser.add_argument('--aapt',required=True)
    parser.add_argument('--apksigner',required=True)
    args=parser.parse_args()
    args.apk=args.apk.resolve()
    args.aapt=str(Path(args.aapt).resolve())
    args.apksigner=str(Path(args.apksigner).resolve())
    root=Path(__file__).resolve().parent
    badging=subprocess.run([args.aapt,'dump','badging',str(args.apk)],check=True,capture_output=True,text=True).stdout
    identity=re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging,re.M)
    if not identity: raise RuntimeError('APK package metadata missing')
    app,code,version=identity.groups()
    if not re.fullmatch(r'[A-Za-z0-9._-]+',version): raise RuntimeError('Unsafe version name')
    subprocess.run([sys.executable,str(root/'verify-release-package.py'),'--aapt',args.aapt,'--apk',str(args.apk),
                    '--version-code',code,'--version-name',version],check=True)
    report=args.apk.parent/'WEBDAV_SIGNING_VALIDATION.json'
    subprocess.run([sys.executable,str(root/'verify-release-signer.py'),'--apksigner',args.apksigner,
                    '--expected-certificate-sha256',CERTIFICATE,'--candidate',str(args.apk),'--out',str(report)],check=True)
    destination=args.destination
    if not destination.is_dir(): raise RuntimeError('Update directory must already exist and be mounted')
    digest=hashlib.sha256(args.apk.read_bytes()).hexdigest()
    name=f'SunnyTV-v{version}-{digest[:12]}.apk'
    manifest={'applicationId':app,'versionCode':int(code),'versionName':version,'apk':name,'size':args.apk.stat().st_size,'sha256':digest}
    latest=destination/'latest.json'
    if latest.exists():
        old=json.loads(latest.read_text(encoding='utf-8-sig'))
        if old['versionCode']>int(code): raise RuntimeError('Refusing to replace latest with an older version')
        if old['versionCode']==int(code) and old.get('sha256')!=digest: raise RuntimeError('Same version has different bytes; increment version')
    target=destination/name
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest()!=digest: raise RuntimeError('Existing immutable APK has different bytes')
    else:
        partial=destination/(name+'.part')
        try:
            shutil.copyfile(args.apk,partial)
            if hashlib.sha256(partial.read_bytes()).hexdigest()!=digest: raise RuntimeError('NAS copy checksum mismatch')
            os.replace(partial,target)
        finally:
            if partial.exists(): partial.unlink()
    temporary=destination/'latest.json.part'
    temporary.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    os.replace(temporary,latest)
    if json.loads(latest.read_text(encoding='utf-8'))!=manifest: raise RuntimeError('NAS manifest verification failed')
    print(f'Published {name}; latest.json now selects version {version}.')

if __name__=='__main__': main()
