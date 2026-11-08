# Installs IPTV Player on a Chromecast with Google TV over the network.
# Usage:  powershell -ExecutionPolicy Bypass -File install.ps1 192.168.1.50
param([Parameter(Mandatory = $true)][string]$Ip)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "IPTVPlayer.apk"
if (-not (Test-Path $adb)) { Write-Error "adb not found at $adb"; exit 1 }
if (-not (Test-Path $apk)) { Write-Error "IPTVPlayer.apk not found next to this script"; exit 1 }

$target = if ($Ip -match ':') { $Ip } else { "${Ip}:5555" }
Write-Host "Connecting to $target ... (confirm 'Allow USB debugging' on the TV)"
& $adb connect $target
& $adb -s $target wait-for-device
Write-Host "Installing ..."
& $adb -s $target install -r $apk
