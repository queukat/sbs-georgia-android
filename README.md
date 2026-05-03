# SBS Georgia Android

Offline-first Android app for Georgian individual entrepreneurs with small business status.

<div align="center">
  <p><strong>Install SBS Georgia from Google Play</strong></p>
  <a href="https://play.google.com/store/apps/details?id=com.queukat.sbsgeorgia">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="220">
  </a>
</div>

The app is focused on replacing manual spreadsheets for:

- monthly income tracking
- FX conversion to GEL using official NBG rates
- monthly declaration preparation
- tax payment workflow tracking
- reminders and local backup/export

## Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room
- WorkManager
- Hilt
- Coroutines / Flow
- KSP

## Project docs

- [Plan](docs/plan.md)
- [Refactor plan](docs/refactor_plan.md)
- [Progress](docs/progress.md)
- [Refactor progress](docs/refactor_progress.md)
- [Hardening progress](docs/hardening_progress.md)
- [Assumptions](docs/assumptions.md)
- [Open questions](docs/open_questions.md)
- [Startup performance](docs/startup_performance.md)
- [Play Console setup](docs/play_console_setup.md)

## Local checks

```powershell
./gradlew.bat testDebugUnitTest --console=plain
./gradlew.bat assembleDebug --console=plain
./gradlew.bat lintDebug --console=plain
```

## Play publishing

The repository publishes to Google Play via Gradle Play Publisher.

Authentication:

- preferred: set `PLAY_KEY_FILE` to the service account JSON path
- supported by GPP directly: set `ANDROID_PUBLISHER_CREDENTIALS` to the JSON file contents

Common commands:

```powershell
./gradlew.bat publishReleaseBundle --track internal --console=plain
./gradlew.bat publishReleaseBundle --track closed --console=plain
./gradlew.bat publishReleaseListing --console=plain
./gradlew.bat bootstrapListing --console=plain
```

`internal` is the default track in the build, so `--track internal` is optional. `--track closed` overrides it for closed testing.
