# Optional CLI route. Browser-only setup is documented in docs/WEB_SETUP.md.
# Uses your existing GitHub CLI login; never paste credentials into this file/chat.
param([string]$Repository = 'xudong7587/SunnyTV')
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
if ($Repository -ine 'xudong7587/SunnyTV') { throw 'This initializer is scoped to xudong7587/SunnyTV.' }
foreach ($Command in @('git','gh')) {
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) { throw "Install $Command first, or use docs/WEB_SETUP.md for the browser-only route." }
}
& gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Sign in locally using gh auth login, then rerun. Do not paste a token into chat.' }
$Login = & gh api user --jq .login
if ($LASTEXITCODE -ne 0 -or $Login -ine 'xudong7587') { throw 'The authenticated GitHub account is not xudong7587.' }
if (Test-Path '.git') { throw 'This script only publishes a freshly extracted source package. Existing Git history must be handled explicitly.' }
& gh repo view $Repository *> $null
if ($LASTEXITCODE -eq 0) { throw 'Repository already exists; refusing to overwrite it. Use the browser workflow or an explicit reviewed Git push.' }
& git init -b main
if ($LASTEXITCODE -ne 0) { throw 'git init failed.' }
# These exclusions keep private review imagery out of the initial GitHub upload.
& git add -- . ':!preview' ':!docs/screens' ':!docs/reference/user-lumiplayer-reference.jpg'
if ($LASTEXITCODE -ne 0) { throw 'git add failed.' }
& git commit -m 'feat: initialize SunnyTV 0.1.0-dev2'
if ($LASTEXITCODE -ne 0) { throw 'Commit failed. Configure your local Git author identity, then publish manually; no repository was created.' }
& gh repo create $Repository --private --source . --remote origin --push --description 'Native Android TV client for Emby, CloudDrive2 WebDAV and MediaIndex STRM'
if ($LASTEXITCODE -ne 0) { throw 'Publish failed or repository was created but push failed. Inspect GitHub and git remote -v before retrying; do not force-push.' }
Write-Host "Published to https://github.com/$Repository. APK availability depends on the Actions build result."
