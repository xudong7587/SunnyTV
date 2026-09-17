#!/usr/bin/env bash
# Generate a real Gradle Wrapper in an isolated build, never fabricate its jar.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f gradlew && -f gradle/wrapper/gradle-wrapper.jar && -f gradle/wrapper/gradle-wrapper.properties ]]; then
  exit 0
fi
command -v gradle >/dev/null || { echo 'Pinned Gradle 8.11.1 is required to generate the wrapper.'; exit 1; }
gradle --version | grep -q '^Gradle 8\.11\.1$' || { echo 'Unexpected Gradle version; refusing a broad upgrade.'; exit 1; }
ROOT="$PWD"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
printf 'rootProject.name = "sunnytv-wrapper-bootstrap"\n' > "$TEMP/settings.gradle.kts"
CHECKSUM="$(curl --fail --silent --show-error --location --retry 2 --max-time 60 https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256 | tr -d '\r\n ')"
[[ "$CHECKSUM" =~ ^[a-fA-F0-9]{64}$ ]] || { echo 'Invalid Gradle distribution checksum'; exit 1; }
gradle --no-daemon --project-dir "$TEMP" wrapper --gradle-version 8.11.1 --distribution-type bin --gradle-distribution-sha256-sum "$CHECKSUM"
# Existing partial wrappers are not silently replaced.
for f in gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties; do
  [[ ! -e "$ROOT/$f" ]] || { echo "Partial wrapper exists at $f. Resolve it explicitly."; exit 1; }
done
mkdir -p "$ROOT/gradle/wrapper"
cp "$TEMP/gradlew" "$TEMP/gradlew.bat" "$ROOT/"
cp "$TEMP/gradle/wrapper/gradle-wrapper.jar" "$TEMP/gradle/wrapper/gradle-wrapper.properties" "$ROOT/gradle/wrapper/"
chmod +x "$ROOT/gradlew"
echo 'Generated a standard wrapper with distribution SHA-256 verification.'
