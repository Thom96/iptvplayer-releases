# Installeert IPTV Player op een Chromecast met Google TV via het netwerk.
# Gebruik:  powershell -ExecutionPolicy Bypass -File installeren.ps1 192.168.1.50
param([Parameter(Mandatory = $true)][string]$Ip)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "IPTVPlayer.apk"
if (-not (Test-Path $adb)) { Write-Error "adb niet gevonden op $adb"; exit 1 }
if (-not (Test-Path $apk)) { Write-Error "IPTVPlayer.apk niet gevonden naast dit script"; exit 1 }

$target = if ($Ip -match ':') { $Ip } else { "${Ip}:5555" }
Write-Host "Verbinden met $target ... (bevestig op de tv 'USB-foutopsporing toestaan')"
& $adb connect $target
& $adb -s $target wait-for-device
Write-Host "Installeren ..."
& $adb -s $target install -r $apk
