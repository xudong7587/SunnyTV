param([string[]]$Tasks = @('testDebugUnitTest','lintDebug','assembleDebug'))
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$Version = '8.11.1'
$Tools = Join-Path $PWD '.sunny-tools'
$Gradle = Join-Path $Tools "gradle-$Version\bin\gradle.bat"
if (-not $env:JAVA_HOME) {
    $Jbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path $Jbr) { $env:JAVA_HOME = $Jbr }
}
if (-not $env:ANDROID_HOME) {
    $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $Sdk) { $env:ANDROID_HOME = $Sdk }
}
if (-not $env:ANDROID_HOME -and -not (Test-Path 'local.properties')) {
    throw 'Install Android Studio + SDK Platform 35 first, then configure ANDROID_HOME.'
}
if (-not (Test-Path $Gradle)) {
    New-Item -ItemType Directory -Force -Path $Tools | Out-Null
    $Url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"
    $Zip = Join-Path $Tools "gradle-$Version-bin.zip"
    Invoke-WebRequest -Uri $Url -OutFile $Zip
    $Expected = (Invoke-WebRequest -Uri "$Url.sha256").Content.Trim()
    if ($Expected -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid Gradle checksum response.' }
    $Actual = (Get-FileHash -Algorithm SHA256 $Zip).Hash
    if ($Actual -ine $Expected) { throw 'Gradle download checksum mismatch.' }
    Expand-Archive -Force -Path $Zip -DestinationPath $Tools
}
& $Gradle --no-daemon @Tasks
if ($LASTEXITCODE -ne 0) { throw "Android build failed ($LASTEXITCODE). Read the Gradle errors above." }
Write-Host 'APK: app\build\outputs\apk\debug\app-debug.apk'
