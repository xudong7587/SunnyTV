#!/usr/bin/env bash
set -euo pipefail
adb install -r "build-logs/device-tested-candidate/SunnyTV-v${VERSION}.apk"
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class io.github.xudong7587.sunnytv.PlayerResizeTest,io.github.xudong7587.sunnytv.SeriesDetailRegressionTest,io.github.xudong7587.sunnytv.UgreenLanConnectionTest io.github.xudong7587.sunnytv.debug.test/androidx.test.runner.AndroidJUnitRunner | tee build-logs/candidate-android.txt
python3 - <<'PY'
import pathlib,re
text=pathlib.Path('build-logs/candidate-android.txt').read_text()
assert re.search(r'^OK \([1-9][0-9]* tests?\)',text,re.M), 'Candidate Android tests did not complete successfully'
assert not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=',text), 'Candidate instrumentation failed'
PY
