#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1920x1080
adb shell wm density 240
adb shell settings put system accelerometer_rotation 0
./gradlew --no-daemon --console=plain connectedDebugAndroidTest 2>&1 | tee build-logs/emulator-tests.log
