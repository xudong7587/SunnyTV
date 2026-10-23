#!/usr/bin/env bash
set -euo pipefail
./gradlew --no-daemon --console=plain -PsunnytvTestBuildType=release assembleReleaseAndroidTest
adb install -r "build-logs/device-tested-candidate/SunnyTV-v${VERSION}.apk"
adb install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell am instrument -w -r -e class io.github.xudong7587.sunnytv.LibraryChoiceTest,io.github.xudong7587.sunnytv.LibraryAudioPlaybackTest,io.github.xudong7587.sunnytv.PlayerAudioPreferenceTest,io.github.xudong7587.sunnytv.AmbilightLifecycleTest,io.github.xudong7587.sunnytv.AmbilightSettingsTest,io.github.xudong7587.sunnytv.AmbilightSurfaceTest,androidx.media3.decoder.ffmpeg.LocalAudioDecoderTest,io.github.xudong7587.sunnytv.PlayerResizeTest,io.github.xudong7587.sunnytv.PlayerFramingTest,io.github.xudong7587.sunnytv.LegacyGlassTest,io.github.xudong7587.sunnytv.PlayerPanelFocusTest,io.github.xudong7587.sunnytv.CloudQualityPlaybackTest,io.github.xudong7587.sunnytv.CloudSubtitleContinuityTest,io.github.xudong7587.sunnytv.SeriesDetailRegressionTest,io.github.xudong7587.sunnytv.UgreenLanConnectionTest,io.github.xudong7587.sunnytv.OutroAdvanceTest,io.github.xudong7587.sunnytv.StartupCacheTest,io.github.xudong7587.sunnytv.ExperimentalUpdateTest,io.github.xudong7587.sunnytv.AutomaticUpdateStartupTest io.github.xudong7587.sunnytv.debug.test/androidx.test.runner.AndroidJUnitRunner | tee build-logs/candidate-android.txt
python3 - <<'PY'
import pathlib,re
text=pathlib.Path('build-logs/candidate-android.txt').read_text()
assert re.search(r'^OK \([1-9][0-9]* tests?\)',text,re.M), 'Candidate Android tests did not complete successfully'
assert not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=',text), 'Candidate instrumentation failed'
PY
