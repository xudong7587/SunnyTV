#!/usr/bin/env bash
# Bootstrap pinned Gradle if absent. Android SDK + JDK 17+ must already be installed.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=8.11.1
TOOL="$PWD/.sunny-tools/gradle-$VERSION/bin/gradle"
if command -v gradle >/dev/null && gradle --version | grep -q "Gradle $VERSION"; then
  TOOL="$(command -v gradle)"
elif [[ ! -x "$TOOL" ]]; then
  mkdir -p .sunny-tools
  ZIP="gradle-$VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/$ZIP"
  curl --fail --location --retry 2 "$URL" --output ".sunny-tools/$ZIP"
  CHECKSUM="$(curl --fail --location "$URL.sha256" | tr -d '\r\n ' )"
  [[ "$CHECKSUM" =~ ^[a-fA-F0-9]{64}$ ]] || { echo 'Invalid Gradle checksum response'; exit 1; }
  printf '%s  %s\n' "$CHECKSUM" ".sunny-tools/$ZIP" | sha256sum --check
  unzip -q -o ".sunny-tools/$ZIP" -d .sunny-tools
fi
if [[ $# -eq 0 ]]; then set -- testDebugUnitTest lintDebug assembleDebug; fi
exec "$TOOL" --no-daemon "$@"
