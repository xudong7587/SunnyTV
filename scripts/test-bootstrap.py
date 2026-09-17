#!/usr/bin/env python3
"""Offline restore/security tests; NOT a hosted GitHub Actions execution."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('restore', ROOT/'scripts/bootstrap_restore.py')
restore=importlib.util.module_from_spec(spec);spec.loader.exec_module(restore)


def seed(extra=None):
    files={'settings.gradle.kts':b'rootProject.name = "SunnyTV"', 'app/build.gradle.kts':b'android {}', 'AGENTS.md':b'rules', 'README.md':b'new readme'}
    if extra:files.update(extra)
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        for name,content in files.items():z.writestr(name,content)
    return out.getvalue()


class RestoreTests(unittest.TestCase):
    def setUp(self):self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
    def tearDown(self):self.tmp.cleanup()
    def apply(self,data=None,sha=None):
        data=seed() if data is None else data
        return restore.restore_zip(data,self.root,sha or hashlib.sha256(data).hexdigest())
    def test_fresh_project(self):
        self.assertEqual(self.apply(),'initialized');self.assertTrue((self.root/'app/build.gradle.kts').exists())
    def test_readme_backup(self):
        (self.root/'README.md').write_text('original');self.apply()
        self.assertEqual((self.root/'docs/INITIAL_REPOSITORY_README.md').read_text(),'original')
    def test_workflow_kept(self):
        f=self.root/'.github/workflows/sunnytv.yml';f.parent.mkdir(parents=True);f.write_text('original workflow');self.apply()
        self.assertEqual(f.read_text(),'original workflow')
    def test_existing_app_is_never_overwritten(self):
        self.apply();f=self.root/'app/build.gradle.kts';f.write_text('user changes')
        self.assertEqual(self.apply(),'already-initialized');self.assertEqual(f.read_text(),'user changes')
    def test_bad_hash(self):
        with self.assertRaises(ValueError):self.apply(sha='0'*64)
        self.assertFalse((self.root/'app').exists())
    def test_path_traversal(self):
        with self.assertRaises(ValueError):self.apply(seed({'../oops':b'x'}))
        self.assertFalse((self.root/'app').exists())
    def test_absolute_path(self):
        with self.assertRaises(ValueError):self.apply(seed({'/oops':b'x'}))
    def test_backslash_path(self):
        with self.assertRaises(ValueError):self.apply(seed({'app\\oops':b'x'}))
    def test_no_git_writes(self):
        with self.assertRaises(ValueError):self.apply(seed({'.git/config':b'x'}))
    def test_no_workflow_writes(self):
        with self.assertRaises(ValueError):self.apply(seed({'.github/workflows/evil.yml':b'x'}))
    def test_no_signing_key(self):
        with self.assertRaises(ValueError):self.apply(seed({'debug.keystore':b'x'}))
    def test_no_machine_config(self):
        with self.assertRaises(ValueError):self.apply(seed({'local.properties':b'x'}))
    def test_existing_unrelated_files(self):
        (self.root/'important.txt').write_text('keep me')
        with self.assertRaises(ValueError):self.apply()
        self.assertEqual((self.root/'important.txt').read_text(),'keep me');self.assertFalse((self.root/'app').exists())
    def test_existing_collision(self):
        (self.root/'AGENTS.md').write_text('custom rules')
        with self.assertRaises(ValueError):self.apply()
        self.assertEqual((self.root/'AGENTS.md').read_text(),'custom rules')
    def test_missing_required_files(self):
        out=io.BytesIO()
        with zipfile.ZipFile(out,'w') as z:z.writestr('README.md','not a project')
        with self.assertRaises(ValueError):self.apply(out.getvalue())
    def test_archive_symlink(self):
        out=io.BytesIO()
        with zipfile.ZipFile(out,'w') as z:
            for f in restore.REQUIRED:z.writestr(f,'stub')
            i=zipfile.ZipInfo('link');i.create_system=3;i.external_attr=(stat.S_IFLNK | 0o777)<<16;z.writestr(i,'/etc/passwd')
        with self.assertRaises(ValueError):self.apply(out.getvalue())
    def test_destination_symlink(self):
        with tempfile.TemporaryDirectory() as outside:
            (self.root/'docs').symlink_to(outside,target_is_directory=True)
            with self.assertRaises(ValueError):self.apply(seed({'docs/file.md':b'x'}))
    def test_large_archive(self):
        data=b'0'*(restore.MAX_ARCHIVE_BYTES+1)
        with self.assertRaises(ValueError):self.apply(data)

if __name__=='__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(RestoreTests)
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    report={'scope':'offline Python archive restore/security; not GitHub/Gradle', 'tests':result.testsRun,'failures':len(result.failures),'errors':len(result.errors)}
    (ROOT/'docs/bootstrap-test-results.json').write_text(json.dumps(report,indent=2)+'\n')
    raise SystemExit(0 if result.wasSuccessful() else 1)
