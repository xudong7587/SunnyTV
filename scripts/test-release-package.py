import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('gate', Path(__file__).with_name('verify-release-package.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class ReleasePackageTest(unittest.TestCase):
    good = "package: name='io.github.xudong7587.sunnytv.debug' versionCode='29' versionName='0.1.0-dev29'"

    def test_accepts_expected_release(self):
        gate.validate(self.good, 'android:debuggable=(type 0x12)0x0', ['classes.dex'], 29, '0.1.0-dev29')

    def test_accepts_only_verified_dependency_font_content(self):
        gate.validate(self.good, '', ['res/optimized.ttf'], 29, '0.1.0-dev29',
                      {'res/optimized.ttf': gate.MEDIA3_NUMBERS_SHA256})
        with self.assertRaises(ValueError):
            gate.validate(self.good, '', ['res/optimized.ttf'], 29, '0.1.0-dev29',
                          {'res/optimized.ttf': 'unapproved'})

    def test_rejects_debug_test_car_private_and_wrong_version(self):
        cases = [(self.good+'\napplication-debuggable', '', []),
                 (self.good, 'android:debuggable(0x0101000f)=(type 0x12)0xffffffff', []),
                 (self.good, 'android:testOnly(0x01010272)=(type 0x12)0xffffffff', []),
                 (self.good, 'com.ucar.intent.action.UCAR', []),
                 (self.good, '', ['assets/fonts/private.ttf']),
                 (self.good, '', ['assets/key.keystore']),
                 (self.good.replace("versionCode='29'", "versionCode='28'"), '', [])]
        for badging, manifest, names in cases:
            with self.subTest(badging=badging, manifest=manifest, names=names):
                with self.assertRaises(ValueError):
                    gate.validate(badging, manifest, names, 29, '0.1.0-dev29')


if __name__ == '__main__':
    unittest.main()
