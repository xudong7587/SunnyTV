$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Get-Command kotlinc -ErrorAction SilentlyContinue)) {
    throw 'Standalone tests require kotlinc (1.9+). Alternatively run the full Android build with scripts/build.ps1.'
}
New-Item -ItemType Directory -Force '.local-tests' | Out-Null
$Root = 'app/src/main/java/io/github/xudong7587/sunnytv'
$Sources = @(
    "$Root/core/model/Models.kt", "$Root/core/model/MediaLogic.kt",
    "$Root/core/network/HttpPolicy.kt", "$Root/source/strm/StrmParser.kt",
    "$Root/source/clouddrive/DavXmlParser.kt", "$Root/core/playback/SessionEvents.kt", "$Root/core/playback/StartupTiming.kt",
    'tests/CoreContractTest.kt'
)
& kotlinc @Sources -include-runtime -d '.local-tests/core-tests.jar'
if ($LASTEXITCODE -ne 0) { throw 'Kotlin core compilation failed.' }
& java -jar '.local-tests/core-tests.jar'
if ($LASTEXITCODE -ne 0) { throw 'Core contract tests failed.' }
