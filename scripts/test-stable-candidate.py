#!/usr/bin/env python3
import copy
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location('candidate', Path(__file__).with_name('verify-stable-candidate.py'))
candidate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(candidate)


class StableCandidateTest(unittest.TestCase):
    def setUp(self):
        self.release = {'tag_name': 'v0.3.8', 'draft': True, 'prerelease': False,
                        'target_commitish': 'a' * 40, 'assets': [{'name': 'SunnyTV-v0.3.8.apk'}]}

    def test_only_exact_unpublished_draft_is_accepted(self):
        candidate.validate_draft(self.release, '0.3.8', 'a' * 40, 'b' * 64)
        for key, value in [('tag_name', 'v0.3.7'), ('draft', False), ('prerelease', True),
                           ('target_commitish', 'main'), ('assets', []),
                           ('assets', self.release['assets'] * 2)]:
            with self.subTest(key=key):
                other = copy.deepcopy(self.release)
                other[key] = value
                with self.assertRaises(ValueError):
                    candidate.validate_draft(other, '0.3.8', 'a' * 40, 'b' * 64)

    def test_digest_and_commit_must_be_exact(self):
        for commit, digest in [('main', 'b' * 64), ('a' * 40, ''), ('a' * 40, '$(command)')]:
            with self.assertRaises(ValueError):
                candidate.validate_draft(self.release, '0.3.8', commit, digest)

    def test_application_changes_and_missing_entries_block_candidate(self):
        with tempfile.TemporaryDirectory() as directory:
            first, second = Path(directory) / 'first.apk', Path(directory) / 'second.apk'
            entries = {name: b'content' for name in ['classes.dex', 'classes2.dex',
                'AndroidManifest.xml', 'resources.arsc', 'res/drawable/logo.xml',
                'assets/licenses/material-color-utilities.txt', 'assets/licenses/miuix-glass.txt',
                'assets/licenses/miuix-glass-notice.txt']}
            def write(path, values):
                with zipfile.ZipFile(path, 'w') as apk:
                    for name, data in values.items():
                        apk.writestr(name, data)
            write(first, entries)
            write(second, entries | {'META-INF/CERT.RSA': b'signature'})
            self.assertEqual(candidate.compare_application(first, second), len(entries))
            license_name = 'assets/licenses/material-color-utilities.txt'
            write(first, entries | {license_name: b'license\r\ntext\r\n'})
            write(second, entries | {license_name: b'license\ntext\n'})
            self.assertEqual(candidate.compare_application(first, second), len(entries))
            write(first, entries)
            for name in entries:
                with self.subTest(name=name):
                    write(second, entries | {name: b'changed'})
                    with self.assertRaises(ValueError):
                        candidate.compare_application(first, second)
            write(second, {k: v for k, v in entries.items() if k != 'classes.dex'})
            with self.assertRaises(ValueError):
                candidate.compare_application(first, second)


if __name__ == '__main__':
    unittest.main()
