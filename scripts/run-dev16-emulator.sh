#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1920x1080
adb shell wm density 240
adb shell settings put system accelerometer_rotation 0
adb install -r previous/SunnyTV-v0.1.0-dev14.apk
adb shell run-as io.github.xudong7587.sunnytv.debug mkdir -p files
printf 'sunnytv-dev14-preserved\n' | adb shell run-as io.github.xudong7587.sunnytv.debug tee files/dev16-upgrade-check
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell run-as io.github.xudong7587.sunnytv.debug cat files/dev16-upgrade-check | tr -d '\r' | grep -qx 'sunnytv-dev14-preserved'
echo 'Verified dev14 -> dev16 adb install -r; data marker preserved.' | tee build-logs/upgrade-test.txt
./gradlew --no-daemon --console=plain connectedDebugAndroidTest 2>&1 | tee build-logs/emulator-tests.log
