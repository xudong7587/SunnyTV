#!/usr/bin/env python3
"""Offline validation of the exact one-file Actions artifact; not a CI/Android run.

Needs PyYAML. Executes only the checksum-verified embedded local Python restorer
inside temporary directories. Never authenticates to GitHub or installs tools.
"""
from __future__ import annotations

import ast
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
import zipfile

import yaml

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / '.github/workflows/sunnytv.yml'
TEXT = WORKFLOW.read_text(encoding='utf-8')
DATA = yaml.load(TEXT, Loader=yaml.BaseLoader)
STEP = next(s for s in DATA['jobs']['prepare']['steps'] if s.get('id') == 'seed')
SHELL = STEP['run']
PYTHON = SHELL.split("python3 - <<'SUNNYTV_PY'\n", 1)[1].rsplit('\nSUNNYTV_PY', 1)[0]
ENCODED = ''.join(STEP['env'][f'SUNNYTV_SEED_{i:03d}'] for i in range(int(STEP['env']['SUNNYTV_SEED_COUNT'])))
DIGEST = STEP['env']['SUNNYTV_SEED_SHA256']
PAYLOAD = base64.b64decode(ENCODED)


def execute_restore(target: Path, output: Path) -> str:
    env = {**os.environ, **STEP['env'], 'GITHUB_OUTPUT': str(output)}
    result = subprocess.run([sys.executable, '-'], input=PYTHON, cwd=target, env=env,
                            capture_output=True, text=True, check=True)
    return result.stdout


class WorkflowTests(unittest.TestCase):
    def test_yaml_events_permissions_and_repository_scope(self):
        self.assertEqual(DATA['on']['push']['branches'], ['main'])
        self.assertIn('workflow_dispatch', DATA['on'])
        self.assertEqual(DATA['permissions'], {'contents': 'read'})
        self.assertEqual(DATA['jobs']['prepare']['permissions'], {'contents': 'write'})
        self.assertEqual(DATA['jobs']['android']['permissions'], {'contents': 'read'})
        self.assertIn("github.event_name != 'pull_request'", DATA['jobs']['prepare']['if'])
        self.assertIn("'xudong7587/sunnytv'", TEXT)
        self.assertNotIn('pull_request_target', DATA['on'])
        self.assertTrue(all(len(v) < 48000 for v in STEP['env'].values()))

    def test_actual_embedded_python_and_digest(self):
        ast.parse(PYTHON)
        self.assertEqual(hashlib.sha256(PAYLOAD).hexdigest(), DIGEST)
        self.assertIn('Embedded seed SHA-256: ' + DIGEST, TEXT)

    def test_all_run_steps_are_valid_bash(self):
        checked = 0
        for job in DATA['jobs'].values():
            for step in job['steps']:
                if 'run' in step:
                    self.assertLessEqual(len(step['run']), 21000)
                    subprocess.run(['bash', '-n'], input=step['run'], text=True,
                                   capture_output=True, check=True)
                    checked += 1
        self.assertGreaterEqual(checked, 9)

    def test_real_seed_restores_and_has_matching_manifest(self):
        with tempfile.TemporaryDirectory(prefix='sunnytv-workflow-') as temp:
            parent = Path(temp)
            target = parent/'repo'; target.mkdir()
            (target/'README.md').write_text('Original README', encoding='utf-8')
            wf = target/'.github/workflows/sunnytv.yml'; wf.parent.mkdir(parents=True)
            wf.write_text(TEXT, encoding='utf-8')
            output = parent/'outputs'
            self.assertIn('initialized', execute_restore(target, output))
            self.assertIn('initialized=true', output.read_text())
            self.assertEqual((target/'docs/INITIAL_REPOSITORY_README.md').read_text(), 'Original README')
            self.assertEqual(wf.read_text(), TEXT)
            manifest = (target/'SOURCE_SHA256SUMS.txt').read_text().splitlines()
            for line in manifest:
                digest, name = line.split('  ', 1)
                self.assertEqual(hashlib.sha256((target/name).read_bytes()).hexdigest(), digest, name)
            self.assertIn('0.1.0-dev2', (target/'app/build.gradle.kts').read_text())
            self.assertTrue((target/'app/src/main/java/io/github/xudong7587/sunnytv/core/playback/StartupTiming.kt').exists())

    def test_rerun_never_rolls_back_changed_source(self):
        with tempfile.TemporaryDirectory(prefix='sunnytv-rerun-') as temp:
            parent=Path(temp);target=parent/'repo';target.mkdir();output=parent/'outputs'
            execute_restore(target,output)
            changed=target/'app/build.gradle.kts';changed.write_text('// future user change\n')
            extra=target/'CUSTOM_USER_FILE.txt';extra.write_text('keep this')
            self.assertIn('already-initialized',execute_restore(target,output))
            self.assertEqual(changed.read_text(),'// future user change\n')
            self.assertEqual(extra.read_text(),'keep this')
            self.assertTrue(output.read_text().endswith('initialized=false\n'))

    def test_snapshot_does_not_publish_workflows_images_keys_or_preview(self):
        with zipfile.ZipFile(io.BytesIO(PAYLOAD)) as archive:
            for name in archive.namelist():
                p=Path(name)
                self.assertNotIn(p.parts[0],{'.git','.github','preview'})
                self.assertNotIn(p.suffix.lower(),{'.jpg','.png','.jpeg','.webp','.ttf','.woff','.woff2','.jks','.keystore','.pem','.key','.apk'})
                self.assertNotIn(p.name, {'.env','local.properties'})

    def test_build_uses_published_revision_and_only_success_publishes_apk(self):
        job=DATA['jobs']['android']
        self.assertEqual(job['needs'],'prepare')
        checkout=next(s for s in job['steps'] if s.get('uses','').startswith('actions/checkout'))
        self.assertIn('needs.prepare.outputs.source_sha',checkout['with']['ref'])
        self.assertEqual(checkout['with']['persist-credentials'],'false')
        compile_step=next(s for s in job['steps'] if s.get('id')=='compile')
        self.assertIn('testDebugUnitTest lintDebug assembleDebug',compile_step['run'])
        self.assertNotIn('continue-on-error',compile_step)
        apk=next(s for s in job['steps'] if s.get('with',{}).get('name')=='SunnyTV-test-apk')
        logs=next(s for s in job['steps'] if s.get('with',{}).get('name')=='SunnyTV-build-reports')
        self.assertEqual(apk['if'],'success()')
        self.assertEqual(logs['if'],'always()')


if __name__=='__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(WorkflowTests)
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    report={'scope':'Offline parsing/execution of generated Actions restorer; not GitHub or Android',
            'tests':result.testsRun,'failures':len(result.failures),'errors':len(result.errors),
            'workflow_sha256':hashlib.sha256(TEXT.encode('utf-8')).hexdigest(),
            'seed_sha256':DIGEST}
    (ROOT/'docs/bootstrap-workflow-test-results.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    raise SystemExit(0 if result.wasSuccessful() else 1)
