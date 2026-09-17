#!/usr/bin/env bash
set -euo pipefail
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
adb shell wm size
adb shell wm density
# Read-only commands; do not change wm size or wm density.
