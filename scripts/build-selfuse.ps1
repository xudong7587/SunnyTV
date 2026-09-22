# Builds the maintainer's private "self-use" APK: the normal app plus the fonts that live only on
# this machine. Font files are copied into a git-ignored asset folder for the build and are never
# committed, never part of a published release, and never read from the repository by the app.
param(
    [string[]]$Fonts = @(
        'D:\个人资料\版权字体\2023方正字体260款\Coca-ColaCareFont-TextLight.TTF',
        'D:\个人资料\版权字体\2023方正字体260款\FZLTHJW.TTF'
    ),
    [string[]]$Tasks = @('assembleDebug'),
    [switch]$KeepFonts
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$AssetDir = 'app/src/selfUse/assets/fonts'
New-Item -ItemType Directory -Force -Path $AssetDir | Out-Null
Get-ChildItem $AssetDir -File -ErrorAction SilentlyContinue | Remove-Item -Force
foreach ($Font in $Fonts) {
    if (-not (Test-Path -LiteralPath $Font)) { throw "Font file not found: $Font" }
    Copy-Item -LiteralPath $Font -Destination $AssetDir -Force
    Write-Host "bundled $(Split-Path $Font -Leaf)"
}
if (-not $env:ANDROID_HOME) {
    $Sdk = Join-Path $PWD '.sunny-tools/android-sdk'
    if (Test-Path $Sdk) { $env:ANDROID_HOME = (Resolve-Path $Sdk).Path; $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
}
try {
    & (Join-Path $PWD 'gradlew.bat') --no-daemon --console=plain -PsunnytvSelfUse=true @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Android build failed ($LASTEXITCODE)." }
} finally {
    if (-not $KeepFonts) { Remove-Item -Recurse -Force $AssetDir -ErrorAction SilentlyContinue }
}
Write-Host 'Self-use APK: app\build\outputs\apk\debug\app-debug.apk'
