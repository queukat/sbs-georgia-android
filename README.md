# SBS Georgia Android

Offline-first Android app for Georgian individual entrepreneurs with small business status.

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

## Local checks

```powershell
./gradlew.bat testDebugUnitTest --console=plain
./gradlew.bat assembleDebug --console=plain
./gradlew.bat lintDebug --console=plain
```

## Commit policy

This repository expects commits to use the project identity:

- Name: `queukat`
- Email: `75810528+queukat@users.noreply.github.com`

And each commit must include a matching `Signed-off-by` trailer.
