#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLINC="${KOTLINC:-kotlinc}"
mkdir -p .local-tests
ROOT=app/src/main/java/io/github/xudong7587/sunnytv
"$KOTLINC" "$ROOT/core/model/DisplayModePolicy.kt" "$ROOT/core/model/Models.kt" "$ROOT/core/model/MediaLogic.kt" \
    "$ROOT/core/model/FontCatalog.kt" "$ROOT/core/model/SubtitleAppearance.kt" \
    "$ROOT/core/network/HttpPolicy.kt" "$ROOT/source/strm/StrmParser.kt" \
    "$ROOT/source/clouddrive/DavXmlParser.kt" "$ROOT/core/playback/SessionEvents.kt" "$ROOT/core/playback/StartupTiming.kt" \
    tests/CoreContractTest.kt -include-runtime -d .local-tests/core-tests.jar
java -jar .local-tests/core-tests.jar
