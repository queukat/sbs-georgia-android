# Progress Log

## Stage 1

Status: Done

### What was done
- Reworked the template project into a Kotlin + Compose + Material 3 + Room + Hilt Android app.
- Added product docs: `plan.md`, `assumptions.md`, `open_questions.md`, and this progress log.
- Fixed the initial domain model and monthly declaration workflow state machine in `docs/plan.md`.
- Implemented Room entities and schema generation for:
  - taxpayer profile
  - small business status config
  - reminder config
  - income entries
  - monthly declaration records
  - future-facing placeholder entities for imported statements, imported transactions, and FX rates
- Implemented repository layer and use cases for settings, income entries, monthly records, dashboard summary, and monthly snapshots.
- Implemented deterministic monthly declaration planning logic:
  - next-month filing window
  - zero-month handling
  - cumulative yearly totals
  - explicit unresolved FX state for non-GEL entries
  - exclusion of months before SBS effective date
  - review flag for same-month pre-effective-date income
  - derived overdue workflow state
- Implemented Stage 1 UI:
  - Home dashboard
  - Months list
  - Month details
  - Manual income entry and edit flow
  - Settings / onboarding form
- Added Room schema export at `app/schemas/.../1.json`.
- Added unit tests for month logic and overdue behavior.

### What was not done
- Official NBG FX integration was not done because it belongs to Stage 2.
- PDF import, parser preview, duplicate-safe import flow, and parser fixtures were not done because they belong to Stage 3.
- Manual monthly status editing beyond the zero-declaration flag was not done because the full status-tracking feature belongs to Stage 4.
- WorkManager reminders were not done because they belong to Stage 4.
- Charts and export/import backup were not done because they belong to Stage 5.
- Payment helper screen was not done in Stage 1 to avoid scope creep before FX, statuses, and month workflows are stable.

### Assumptions used in Stage 1
- Manual entries are graph-20 eligible by default unless explicitly excluded.
- Non-GEL entries remain unresolved in Stage 1 and do not silently contribute converted GEL totals.
- If the SBS effective date is mid-month, entries before that date are excluded and the month is flagged for review.
- `Overdue` is derived from date plus workflow state instead of being persisted as a contradictory standalone truth.
- Reminder days are stored now, but reminder execution is deferred.

### Risks
- AGP 9 built-in Kotlin required migration to the supported `legacy-kapt` path for current Hilt/Room annotation processing. This works, but should be revisited later to migrate to the long-term built-in Kotlin/KSP path.
- Stage 1 allows non-GEL manual income capture without FX conversion. This is intentional, but until Stage 2 some months can remain only partially calculable.
- UI behavior has build/lint/test coverage, but no emulator/manual QA pass has been run yet.

### Files changed
- Build/config:
  - `build.gradle.kts`
  - `gradle.properties`
  - `gradle/libs.versions.toml`
  - `app/build.gradle.kts`
- Documentation:
  - `docs/plan.md`
  - `docs/assumptions.md`
  - `docs/open_questions.md`
  - `docs/progress.md`
- Android manifest/resources:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values-night/themes.xml`
- App/bootstrap:
  - `app/src/main/java/com/queukat/sbsgeorgia/SbsGeorgiaApplication.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/MainActivity.kt`
- Domain/data/DI:
  - `app/src/main/java/com/queukat/sbsgeorgia/domain/model/DomainModels.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/domain/repository/Repositories.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/domain/service/MonthlyDeclarationPlanner.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/DeclarationUseCases.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/data/local/Converters.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/data/local/Daos.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/data/local/Entities.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/data/local/SbsGeorgiaDatabase.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/data/repository/RepositoryImpls.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/di/AppModule.kt`
  - `app/schemas/com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase/1.json`
- UI:
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/SbsGeorgiaApp.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/AppViewModels.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/Screens.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/UiUtils.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/theme/Color.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/theme/Theme.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/theme/Type.kt`
- Tests:
  - `app/src/test/java/com/queukat/sbsgeorgia/domain/service/MonthlyDeclarationPlannerTest.kt`
  - removed template example unit/instrumented tests

### Tests and verification
- `./gradlew.bat testDebugUnitTest --console=plain` — passed
- `./gradlew.bat assembleDebug --console=plain` — passed
- `./gradlew.bat lintDebug --console=plain` — passed

Unit tests currently cover:
- next-month filing window calculation
- zero declaration month handling
- cumulative carry across zero months
- filtering of months before SBS effective date
- same-month pre-effective-date review flag
- unresolved non-GEL handling
- derived overdue status behavior

### Next iteration
- Stage 2: implement official NBG FX repository and local rate cache.
- Add explicit FX resolution UI states into month details and manual-entry editing flow.
- Verify the official NBG JSON endpoint and wire source/rate metadata into `FxRate` and `IncomeEntry`.
