# Start de Google TV-emulator, installeert de testversie en opent de app.
# Gebruik:  powershell -ExecutionPolicy Bypass -File emulator.ps1
# Bediening: pijltjestoetsen = navigeren, Enter = OK, Esc = terug (of klikken met de muis).
# Het scherm "Set up Google TV" hoort bij de emulator en kun je negeren: de app opent eroverheen.
$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21" }

$running = & $adb devices | Select-String "emulator-"
if (-not $running) {
    Write-Host "Emulator starten..."
    Start-Process $emulator -ArgumentList "-avd", "ChromecastTV", "-no-boot-anim", "-no-snapshot-save"
}
& $adb -s emulator-5554 wait-for-device
do { Start-Sleep 2 } until ((& $adb -s emulator-5554 shell getprop sys.boot_completed).Trim() -eq "1")
Start-Sleep 5
& $adb -s emulator-5554 shell cmd alarm set-timezone Europe/Amsterdam | Out-Null

Write-Host "App bouwen en installeren..."
Push-Location $PSScriptRoot
$env:ANDROID_SERIAL = "emulator-5554"   # alleen naar de emulator, nooit naar de tv
& .\gradlew.bat installDebug --console=plain -q
Pop-Location
& $adb -s emulator-5554 shell am start -n nl.iptvplayer.app/.MainActivity | Out-Null
Write-Host "Klaar: de app staat open in de emulator."
