# Starts the Google TV emulator, installs the debug build and opens the app.
# Usage:  powershell -ExecutionPolicy Bypass -File emulator.ps1
# Controls: arrow keys = navigate, Enter = OK, Esc = back (or click with the mouse).
# The "Set up Google TV" screen belongs to the emulator and can be ignored: the app opens on top of it.
$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21" }

$running = & $adb devices | Select-String "emulator-"
if (-not $running) {
    Write-Host "Starting emulator..."
    Start-Process $emulator -ArgumentList "-avd", "ChromecastTV", "-no-boot-anim", "-no-snapshot-save"
}
& $adb -s emulator-5554 wait-for-device
do { Start-Sleep 2 } until ((& $adb -s emulator-5554 shell getprop sys.boot_completed).Trim() -eq "1")
Start-Sleep 5
& $adb -s emulator-5554 shell cmd alarm set-timezone Europe/Amsterdam | Out-Null

Write-Host "Building and installing the app..."
Push-Location $PSScriptRoot
$env:ANDROID_SERIAL = "emulator-5554"   # only to the emulator, never to the TV
& .\gradlew.bat installDebug --console=plain -q
Pop-Location
& $adb -s emulator-5554 shell am start -n nl.iptvplayer.app/.MainActivity | Out-Null
Write-Host "Done: the app is open in the emulator."
