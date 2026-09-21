$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Force '.local-tests' | Out-Null
$Root = 'app/src/main/java/io/github/xudong7587/sunnytv'
$Sources = @(
    "$Root/core/model/DisplayModePolicy.kt", "$Root/core/model/Models.kt", "$Root/core/model/MediaLogic.kt",
    "$Root/core/model/FontCatalog.kt", "$Root/core/model/SubtitleAppearance.kt",
    "$Root/core/network/HttpPolicy.kt", "$Root/source/strm/StrmParser.kt",
    "$Root/source/clouddrive/DavXmlParser.kt", "$Root/core/playback/SessionEvents.kt", "$Root/core/playback/StartupTiming.kt",
    'tests/CoreContractTest.kt'
)

$Kotlinc = (Get-Command kotlinc -ErrorAction SilentlyContinue).Source
if ($Kotlinc) {
    & $Kotlinc @Sources -include-runtime -d '.local-tests/core-tests.jar'
    if ($LASTEXITCODE -ne 0) { throw 'Kotlin core compilation failed.' }
    & java -jar '.local-tests/core-tests.jar'
    if ($LASTEXITCODE -ne 0) { throw 'Core contract tests failed.' }
    return
}

# No standalone kotlinc: reuse the compiler Gradle already downloaded. Same source list as test-core.sh.
$Java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
$Cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
function Find-Jar([string]$Relative, [string]$Pattern) {
    $Path = Join-Path $Cache $Relative
    if (-not (Test-Path $Path)) { return $null }
    return (Get-ChildItem -Recurse -Filter $Pattern $Path | Select-Object -First 1 -ExpandProperty FullName)
}
$Compiler = Find-Jar 'org.jetbrains.kotlin/kotlin-compiler-embeddable' 'kotlin-compiler-embeddable-*.jar'
$Stdlib = Find-Jar 'org.jetbrains.kotlin/kotlin-stdlib' 'kotlin-stdlib-*.jar'
if (-not $Compiler -or -not $Stdlib) {
    throw '未找到 kotlinc，也未在 Gradle 缓存中找到 Kotlin 编译器。请先执行一次 :app:assembleRelease，或安装 kotlinc 1.9+。'
}
$Classpath = @($Compiler, $Stdlib,
    (Find-Jar 'org.jetbrains.kotlin/kotlin-script-runtime' 'kotlin-script-runtime-*.jar'),
    (Find-Jar 'org.jetbrains.kotlin/kotlin-daemon-embeddable' 'kotlin-daemon-embeddable-*.jar'),
    (Find-Jar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm' '*.jar'),
    (Find-Jar 'org.jetbrains/annotations' '*.jar'),
    (Find-Jar 'org.jetbrains.intellij.deps/trove4j' '*.jar')) | Where-Object { $_ }
$Classes = '.local-tests/classes'
if (Test-Path $Classes) { Remove-Item -Recurse -Force $Classes }
New-Item -ItemType Directory -Force $Classes | Out-Null
& $Java -cp ($Classpath -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -cp $Stdlib -d $Classes @Sources
if ($LASTEXITCODE -ne 0) { throw 'Kotlin core compilation failed.' }
& $Java -cp "$Classes;$Stdlib" io.github.xudong7587.sunnytv.contract.CoreContractTestKt
if ($LASTEXITCODE -ne 0) { throw 'Core contract tests failed.' }
