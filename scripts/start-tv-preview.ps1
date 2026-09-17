param([string]$Serial = '')
$ErrorActionPreference = 'Stop'
$previewRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $previewRoot '.sunny-tools/scrcpy-win64-v4.1'
$previewExe = Join-Path $toolRoot 'scrcpy.exe'
$deviceSerial = $Serial
$logRoot = Join-Path $previewRoot '.ci-logs'
try {
    if (-not (Test-Path -LiteralPath $previewExe)) { throw "找不到固定预览工具：$previewExe" }
    New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
    if (-not $deviceSerial) {
        $connected = @(& (Join-Path $toolRoot 'adb.exe') devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
        if ($connected.Count -ne 1) { throw '请只连接一台已授权 USB 调试的手机，或通过 -Serial 指定设备。' }
        $deviceSerial = $connected[0]
    }
    $deviceState = & (Join-Path $toolRoot 'adb.exe') -s $deviceSerial get-state 2>$null
    if ($deviceState -ne 'device') { throw '请连接手机 USB，并在手机上允许 USB 调试后重试。' }
    $existing = Get-Process scrcpy -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $previewExe }
    if ($existing) { throw '电视预览已经运行。请先关闭旧窗口再启动，避免同时创建多个显示器。' }
    & (Join-Path $toolRoot 'adb.exe') -s $deviceSerial shell input keyevent KEYCODE_WAKEUP
    $previewArgs = @('-s', $deviceSerial, '--new-display=1920x1080/320',
        '--no-vd-system-decorations', '--no-audio', '--stay-awake', '--keep-active',
        '--start-app=io.github.xudong7587.sunnytv.debug', '--window-title=SunnyTV-TV-Preview-1080p',
        '--window-width=1280', '--window-height=720')
    # The SDL preview is intentionally visible. No global wm size/density override is used.
    $previewProcess = Start-Process -FilePath $previewExe -ArgumentList $previewArgs -WorkingDirectory $toolRoot `
        -RedirectStandardOutput (Join-Path $logRoot 'tv-preview.log') `
        -RedirectStandardError (Join-Path $logRoot 'tv-preview-error.log') -PassThru -Wait
    if ($previewProcess.ExitCode -ne 0) { throw '预览连接已退出。请重新连接手机后双击快捷方式；诊断日志保存在项目 .ci-logs 目录。' }
} catch {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'SunnyTV 电视预览') | Out-Null
}
