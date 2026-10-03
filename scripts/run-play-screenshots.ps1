param(
    [ValidateSet("en-US", "ru-RU")]
    [string[]]$Locales = @("en-US", "ru-RU"),
    [string]$Serial = $env:ANDROID_SERIAL
)

$ErrorActionPreference = "Stop"
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

function Clear-DeviceScreenshotOutput {
    param([string]$Locale)

    $mediaUri = "content://media/external/images/media"
    $relativePath = "Pictures/SbsGeorgiaScreenshots/localized/$Locale/"
    $queryOutput = adb shell content query --uri $mediaUri --projection _id:relative_path | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to inspect existing screenshots for locale $Locale."
    }

    $rowChunks = [regex]::Split($queryOutput, "(?=Row:)")
    foreach ($row in $rowChunks) {
        if ($row.Contains("relative_path=$relativePath") -and $row -match "_id=(?<id>\d+)") {
            adb shell content delete --uri "$mediaUri/$($matches['id'])" | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to remove a previous screenshot for locale $Locale."
            }
        }
    }
}

$devices = @(Get-OnlineDevices)
if ($devices.Count -eq 0) {
    throw "No connected Android device or emulator is online."
}

if (-not $Serial) {
    $emulators = @($devices | Where-Object { $_ -like "emulator-*" })
    if ($emulators.Count -ne 1) {
        throw "Select one running emulator with -Serial. Physical-phone instrumentation is not allowed."
    }
    $Serial = $emulators[0]
}
if ($Serial -notlike "emulator-*" -or $Serial -notin $devices) {
    throw "Screenshot capture requires an online emulator; selected target: $Serial"
}
$previousSerial = $env:ANDROID_SERIAL
$env:ANDROID_SERIAL = $Serial
$animationSettings = @{}
foreach ($setting in @("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
    $animationSettings[$setting] = (adb shell settings get global $setting | Out-String).Trim()
}

New-Item -ItemType Directory -Force -Path $localOutputRoot | Out-Null

try {
    adb shell settings put global window_animation_scale 0 | Out-Null
    adb shell settings put global transition_animation_scale 0 | Out-Null
    adb shell settings put global animator_duration_scale 0 | Out-Null

    foreach ($locale in $Locales) {
        Write-Host "Generating Play screenshots for locale $locale"
        Clear-DeviceScreenshotOutput -Locale $locale

        ./gradlew.bat :app:connectedDebugAndroidTest --console=plain `
            "-Pandroid.testInstrumentationRunnerArguments.class=$testClass" `
            "-Pandroid.testInstrumentationRunnerArguments.testLocale=$locale" `
            "-Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true"
        if ($LASTEXITCODE -ne 0) {
            throw "Screenshot instrumentation failed for locale $locale."
        }

        $targetOutput = Join-Path $localOutputRoot $locale
        $resolvedRoot = [IO.Path]::GetFullPath($localOutputRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
        $resolvedTarget = [IO.Path]::GetFullPath($targetOutput)
        if (-not $resolvedTarget.StartsWith($resolvedRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase)) {
            throw "Screenshot output is outside the intended artifact directory."
        }
        if (Test-Path -LiteralPath $resolvedTarget) {
            Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
        }

        $devicePath = "$deviceOutputRoot/$locale"
        adb pull $devicePath $targetOutput | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to pull screenshots for locale $locale."
        }
        $pngCount = @(Get-ChildItem -Path $targetOutput -Filter "*.png" -File).Count
        if ($pngCount -ne 6) {
            throw "Expected 6 screenshots for locale $locale, found $pngCount."
        }
        Write-Host "Saved screenshots to $targetOutput"
    }
}
finally {
    foreach ($setting in $animationSettings.Keys) {
        $previousValue = $animationSettings[$setting]
        if ($previousValue -eq "null") {
            adb shell settings delete global $setting | Out-Null
        } else {
            adb shell settings put global $setting $previousValue | Out-Null
        }
    }
    $env:ANDROID_SERIAL = $previousSerial
}
