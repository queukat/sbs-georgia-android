param(
    [string]$Serial = "emulator-5554"
)

$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $PSScriptRoot

Write-Host "Using device: $Serial"
adb -s $Serial devices | Out-Null
adb -s $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
adb -s $Serial shell input keyevent 82 | Out-Null

Push-Location $workspace
try {
    $env:ANDROID_SERIAL = $Serial
    & .\gradlew.bat connectedDebugAndroidTest --console=plain
} finally {
    Pop-Location
}
