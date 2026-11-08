# Builds a new version and puts it online; the app on the TV sees it at its next start.
# Usage:  powershell -ExecutionPolicy Bypass -File publish.ps1 "What's new"
param([string]$Notes = "")
$ErrorActionPreference = "Stop"
$repo = "Thom96/iptvplayer-releases"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21" }
Set-Location $PSScriptRoot

Write-Host "Building..."
& .\gradlew.bat assembleRelease --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
$apk = Join-Path $env:LOCALAPPDATA "IPTVPlayer-build\app\outputs\apk\release\app-release.apk"
Copy-Item $apk "IPTVPlayer.apk" -Force

# Read the version from the APK.
$aapt = Get-ChildItem (Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools") -Recurse -Filter aapt2.exe | Select-Object -First 1
$badging = (& $aapt.FullName dump badging "IPTVPlayer.apk" | Select-Object -First 1)
$code = [int]([regex]::Match($badging, "versionCode='(\d+)'").Groups[1].Value)
$name = [regex]::Match($badging, "versionName='([^']*)'").Groups[1].Value
$tag = "v$code"

$tmp = Join-Path $env:TEMP "iptv-release"
New-Item -ItemType Directory -Force $tmp | Out-Null
$json = @{
    versionCode = $code
    versionName = $name
    apkUrl      = "https://github.com/$repo/releases/download/$tag/IPTVPlayer.apk"
    notes       = $Notes
} | ConvertTo-Json
# UTF-8 without BOM (Set-Content in PowerShell 5 adds a BOM).
[IO.File]::WriteAllText((Join-Path $tmp "version.json"), $json, (New-Object Text.UTF8Encoding $false))

Write-Host "Publishing as $name ($tag)..."
$title = "Version $name"
& gh release create $tag "IPTVPlayer.apk" (Join-Path $tmp "version.json") --repo $repo --title $title --notes ($(if ($Notes) { $Notes } else { $title })) --latest
if ($LASTEXITCODE -ne 0) { throw "Publishing failed" }
Write-Host "Done: version $name is online."
