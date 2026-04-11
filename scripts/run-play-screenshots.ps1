param(
    [string[]]$Locales = @("en-US", "ru-RU")
)

$ErrorActionPreference = "Stop"
$packageName = "com.queukat.sbsgeorgia"
$testClass = "com.queukat.sbsgeorgia.screenshots.PlayStoreScreenshotsTest"
$deviceOutputRoot = "/sdcard/Pictures/SbsGeorgiaScreenshots/localized"
$localOutputRoot = Join-Path (Get-Location) "artifacts\play-screenshots"

function Get-OnlineDevices {
    $lines = adb devices | Select-Object -Skip 1
    $devices = @()
    foreach ($line in $lines) {
        if ($line -match "^(?<serial>\S+)\s+device$") {
            $devices += $matches["serial"]
        }
    }
    return $devices
}

$devices = Get-OnlineDevices
if ($devices.Count -eq 0) {
    throw "No connected Android device or emulator is online."
}

if ($devices.Count -gt 1 -and -not $env:ANDROID_SERIAL) {
    throw "Multiple Android devices are online. Set ANDROID_SERIAL to choose one target device."
}

New-Item -ItemType Directory -Force -Path $localOutputRoot | Out-Null

try {
    adb shell settings put global window_animation_scale 0 | Out-Null
    adb shell settings put global transition_animation_scale 0 | Out-Null
    adb shell settings put global animator_duration_scale 0 | Out-Null

    foreach ($locale in $Locales) {
        Write-Host "Generating Play screenshots for locale $locale"

        ./gradlew.bat :app:connectedDebugAndroidTest --console=plain `
            "-Pandroid.testInstrumentationRunnerArguments.class=$testClass" `
            "-Pandroid.testInstrumentationRunnerArguments.testLocale=$locale"

        $targetOutput = Join-Path $localOutputRoot $locale
        if (Test-Path $targetOutput) {
            Remove-Item -Recurse -Force $targetOutput
        }

        $devicePath = "$deviceOutputRoot/$locale"
        adb pull $devicePath $targetOutput | Out-Null
        Write-Host "Saved screenshots to $targetOutput"
    }
}
finally {
    adb shell settings put global window_animation_scale 1 | Out-Null
    adb shell settings put global transition_animation_scale 1 | Out-Null
    adb shell settings put global animator_duration_scale 1 | Out-Null
}
