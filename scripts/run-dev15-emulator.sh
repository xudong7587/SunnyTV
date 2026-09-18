#!/usr/bin/env bash
set -euo pipefail
mkdir -p build-logs/visual-dev15
finish() {
  status=$?
  adb pull /sdcard/Android/data/io.github.xudong7587.sunnytv.debug/files/visual-dev15/. build-logs/visual-dev15/ || true
  exit "$status"
}
trap finish EXIT
# Changes apply only to this disposable emulator, never a user's television.
adb shell wm size 1920x1080
adb shell wm density 240
adb shell settings put system accelerometer_rotation 0
adb install -r previous/SunnyTV-v0.1.0-dev14.apk
adb shell run-as io.github.xudong7587.sunnytv.debug mkdir -p files
printf 'sunnytv-dev14-preserved\n' | adb shell run-as io.github.xudong7587.sunnytv.debug tee files/dev15-upgrade-check
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell run-as io.github.xudong7587.sunnytv.debug cat files/dev15-upgrade-check | tr -d '\r' | grep -qx 'sunnytv-dev14-preserved'
echo 'Verified adb install -r dev14 -> dev15; local data marker preserved.' | tee build-logs/upgrade-test.txt
./gradlew --no-daemon --console=plain connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.xudong7587.sunnytv.HomeHotfixTest,io.github.xudong7587.sunnytv.ShadowAlignmentTest \
  2>&1 | tee build-logs/emulator-tests.log
