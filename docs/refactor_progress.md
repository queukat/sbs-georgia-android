# Refactor Progress

## Current Status

Status: Platform refresh plus Feature Slices A, B, and C completed. Remaining open items are environment/test follow-ups and future scope questions.

## Planned Blocks

1. Audit and plan
2. Platform refresh
3. Feature Slice A
4. Feature Slice B
5. Feature Slice C
6. Final doc cleanup

## Platform Refresh

Status: Done

### What was done
- Centralized the active build/tool versions in `gradle/libs.versions.toml`.
- Removed `legacy-kapt` completely and migrated Room/Hilt annotation processing to KSP.
- Added the Room Gradle plugin and kept schema export in `app/schemas/...`.
- Switched Hilt Compose integration away from the old navigation-specific artifact to the current lifecycle-viewmodel Compose artifact.
- Migrated app navigation from `NavController`/`NavHost` to Navigation 3 with:
  - typed serializable route keys
  - explicit top-level back stacks
  - `NavDisplay`
  - `entryProvider`
  - `rememberViewModelStoreNavEntryDecorator`
- Reworked top-level navigation structure:
  - `Home`
  - `Months`
  - `Settings`
  - manual income entry is now a nested flow instead of a dedicated top-level tab
- Split the old UI monolith into screen-oriented packages:
  - `ui/navigation`
  - `ui/home`
  - `ui/months`
  - `ui/monthdetails`
  - `ui/manualentry`
  - `ui/settings`
  - `ui/common`
- Replaced stateful one-shot booleans in manual entry and settings with explicit effect flows.
- Added test foundation:
  - `AppNavigationState` unit tests
  - `MainDispatcherRule`
  - Compose UI tests for settings/manual entry/month navigation/unresolved FX visibility

### What was not done
- Feature Slice A was not started in this block on purpose.
- Feature Slice B was not started in this block on purpose.
- Feature Slice C was not started in this block on purpose.
- Room migration test scaffolding was not added yet because the schema version is still `1`; it should land together with the first real migration in the reminder-time schema bump.
- No module split was done because the current app size still does not justify module churn.

### Decisions taken
- Kept the Stage 1 business core unchanged.
- Accepted a pragmatic top-level navigation simplification: manual entry is task flow, not a persistent tab.
- Chose Navigation 3 multiple-back-stack support now, before payment/import/status flows multiply navigation edges.
- Chose KSP now, even though the working AGP 9 path still requires built-in Kotlin/new DSL opt-outs.

### Alternatives consciously not chosen
- Did not keep `NavController`/`navigation-compose`, because that would force another navigation rewrite once the app gains more nested flows.
- Did not keep `legacy-kapt`, because that was explicitly temporary debt.
- Did not split into multiple Gradle modules yet, because that would create churn without enough payoff at the current size.
- Did not force the experimental built-in-Kotlin + KSP path after it failed to produce a stable working build in this environment.

### Remaining risks
- AGP 9 now builds cleanly with KSP, but it still emits deprecation warnings because the currently working path needs `android.builtInKotlin=false` and `android.newDsl=false`.
- `connectedDebugAndroidTest` fails on the attached Android TV device because the Compose test harness does not obtain a compose hierarchy there; this is a test-environment issue that needs a focused follow-up before those UI tests can become a reliable device gate.
- AGP metrics logging still tries to touch `C:\Users\CodexSandboxOffline\.android` inside this sandboxed environment; it is noisy but not currently build-blocking.

### Files changed
- Build/config:
  - `build.gradle.kts`
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `gradle.properties`
- New UI/navigation structure:
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/SbsGeorgiaApp.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/app/AppThemeViewModel.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/navigation/...`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/common/...`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/home/...`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/months/...`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/...`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/manualentry/...`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/...`
- Removed Stage 1 monolith files:
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/AppViewModels.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/Screens.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/UiUtils.kt`
- Tests:
  - `app/src/test/java/com/queukat/sbsgeorgia/testing/MainDispatcherRule.kt`
  - `app/src/test/java/com/queukat/sbsgeorgia/ui/navigation/AppNavigationStateTest.kt`
  - `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/ManualEntryScreenTest.kt`
  - `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/SettingsScreenTest.kt`
  - `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/MonthsFlowTest.kt`

### Commands run
- `./gradlew.bat :app:tasks --all --console=plain` — passed
- `./gradlew.bat :app:assembleDebug --console=plain` — passed
- `./gradlew.bat testDebugUnitTest --console=plain` — passed
- `./gradlew.bat assembleDebug --console=plain` — passed
- `./gradlew.bat lintDebug --console=plain` — passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain` — passed
- `./gradlew.bat connectedDebugAndroidTest --console=plain` — failed on attached Android TV test environment with `No compose hierarchies found in the app`

### Next iteration
- Feature Slice A: official NBG FX cache, manual override flow, month readiness UX, and payment helper.

## Feature Slice A

Status: Done

### What was done
- Added a real FX repository with local cache and explicit exact-date lookup semantics.
- Added official NBG JSON datasource wiring through `NbgFxRemoteDataSource`.
- Added FX use cases for:
  - resolving a month from cached/official rates
  - applying manual FX override without silent fallback
- Added month-level readiness UX in month details:
  - unresolved FX state
  - zero declaration guidance
  - ready-for-payment summary
- Added payment helper screen with:
  - treasury code `101001000`
  - payment comment generation
  - tax amount copy actions
- Added FX manual override screen and navigation path from unresolved entries.

### What was not done
- No nearest-date rate fallback was added on purpose.
- No broad FX provider abstraction was added beyond the current official source + local cache.
- No multi-currency analytics expansion beyond the current month-detail/readiness flow was added yet.

### Decisions taken
- Kept unresolved FX explicit all the way to month readiness and payment helper.
- Cached official/manual FX by exact `date + currency`.
- Treated manual override as a visible, persisted exception path instead of hidden correction logic.

### Alternatives consciously not chosen
- Did not auto-substitute the closest NBG date, because that would violate the no-silent-fallback rule.
- Did not delay payment helper until later export/banking work, because it already produces immediate user value now.

### Remaining risks
- Official NBG endpoint handling is implemented against the expected official JSON shape, but live endpoint verification from shell was blocked by environment/auth/TLS issues in this workspace.
- Payment helper still uses deprecated `LocalClipboardManager`; runtime behavior is fine, but this should be cleaned in a later polish pass.

### Files changed
- `app/src/main/java/com/queukat/sbsgeorgia/data/remote/NbgFxRemoteDataSource.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/data/repository/FxRateRepositoryImpl.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/FxUseCases.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/PaymentHelperUseCase.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/fxoverride/...`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/payment/...`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/...`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/navigation/...`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/usecase/FxUseCasesTest.kt`

### Commands run
- `./gradlew.bat assembleDebug --console=plain` — passed
- `./gradlew.bat testDebugUnitTest --console=plain` — passed
- `./gradlew.bat lintDebug --console=plain` — passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain` — passed
- `./gradlew.bat connectedDebugAndroidTest --console=plain` — failed because no reliable connected test device/environment was available

### Next iteration
- Feature Slice B: workflow status editing, reminders, notification channels, due-state UX hardening, and migration test scaffolding.

## Feature Slice B

Status: Done

### What was done
- Added reminder-time schema bump from DB version `1` to `2` with checked-in Room schema `2.json`.
- Added `MIGRATION_1_2` and instrumented migration test scaffolding.
- Completed workflow status editing with:
  - manual status selection
  - filed/payment sent/payment credited dates
  - zero declaration prepared toggle
  - clear actions for dates
  - overdue shown as derived state, not stored truth
- Wired reminder scheduling to settings save via `ReminderScheduler`.
- Added reminder bootstrap on app start so stored reminder settings are rescheduled when the app launches.
- Added notification channels and explicit notification-permission UX in settings.
- Added reminder planner unit tests for:
  - zero declaration reminders
  - taxable month payment reminder
  - suppression after payment sent
  - out-of-scope suppression
- Fixed WorkManager initialization manifest issue for `Configuration.Provider`.
- Fixed lint compatibility issue in reminder scheduling for `minSdk 24`.

### What was not done
- No screenshot test infrastructure was added.
- No boot-completed receiver was added; current reminder persistence relies on WorkManager plus app-start bootstrap.
- No extra workflow wizard was added; the screen remains a direct editor, not a step-by-step state machine UI.

### Decisions taken
- Treated workflow editing as a direct correction surface, not as a forced status-transition wizard.
- Kept `OVERDUE` derived-only and excluded it from manual status selection.
- Scheduled reminders from settings plus startup bootstrap, which is enough for the current local-only app without adding more lifecycle/platform plumbing.
- Restricted reminder day validation to `1..15`, matching the actual filing/payment window.

### Alternatives consciously not chosen
- Did not store a separate overdue flag, because that would create contradictory state against the derived due logic.
- Did not add a receiver-heavy reminder infrastructure yet, because the current WorkManager path already solves the first useful version.
- Did not over-modularize reminder/workflow code, because the current app size still does not justify it.

### Remaining risks
- `connectedDebugAndroidTest` still cannot be used as a trustworthy gate in this workspace because no connected Android test device is currently available.
- Notification permission is now explicit in UI, but full reminder behavior still depends on the user granting it on Android 13+.
- App-start bootstrap uses a lightweight app-level coroutine scope; this is acceptable for the current single-purpose initialization but should stay narrow.

### Files changed
- `app/src/main/java/com/queukat/sbsgeorgia/domain/model/DomainModels.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/data/local/Converters.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/data/local/Entities.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/data/local/SbsGeorgiaDatabase.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/SbsGeorgiaApplication.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/...`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/workflow/...`
- `app/src/main/java/com/queukat/sbsgeorgia/worker/ReminderScheduler.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/worker/ReminderBootstrapper.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/worker/MonthlyReminderWorker.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/worker/ReminderNotifications.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/service/ReminderPlannerTest.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/data/local/SbsGeorgiaDatabaseMigrationTest.kt`
- `app/schemas/com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase/2.json`

### Commands run
- `./gradlew.bat assembleDebug --console=plain` — passed
- `./gradlew.bat testDebugUnitTest --console=plain` — passed
- `./gradlew.bat lintDebug --console=plain` — passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain` — passed
- `./gradlew.bat connectedDebugAndroidTest --console=plain` — failed with `No connected devices!`

### Next iteration
- Feature Slice C: real TBC PDF import v1 with parser, preview/correction flow, and duplicate-safe import into `IncomeEntry`.

## Feature Slice C

Status: Done

### What was done
- Added a real TBC PDF import v1 flow with:
  - SAF document picker
  - PDF text extraction via `pdfbox-android`
  - TBC-specific parser for the current statement layout
  - preview/correction UI before import
  - duplicate detection via source PDF fingerprint and transaction fingerprint
  - import into `IncomeEntry`
- Added dedicated import models and repository boundary for duplicate-safe statement persistence.
- Added imported-statement/imported-transaction DAOs and repository implementation backed by Room.
- Added parser heuristics:
  - positive incoming rows with service/invoice-style descriptions are suggested taxable
  - internal transfers / own-account transfers / fees are suggested excluded
  - other incoming rows are explicitly marked review-required instead of silently assumed taxable
- Kept non-GEL imported income unresolved so the existing FX slice handles it later in month details.
- Added parser fixture test and import orchestration tests:
  - parser reads realistic extracted-text fixture
  - duplicate statement rejection
  - duplicate transaction marking
  - manual correction values are preserved during confirmed import

### What was not done
- No OCR was added.
- No generic multi-bank parser framework was added.
- No attempt was made to support multi-line wrapped transaction rows beyond the current TBC v1 extracted-text shape.
- No new schema migration was added for unique `income_entry.sourceTransactionFingerprint`; current duplicate safety is enforced through repository logic plus `imported_transaction` uniqueness.

### Decisions taken
- Kept Android-specific document access and PDF extraction outside the parser itself.
- Kept the parser tightly bound to one TBC layout instead of abstracting for unknown future banks.
- Used a preview/correction screen rather than auto-importing parsed rows directly.
- Moved `Uri` out of the use-case boundary to keep JVM tests Android-stub-free.

### Alternatives consciously not chosen
- Did not build a broad parser/plugin architecture, because it would add churn without improving the first real TBC flow.
- Did not assume statement currency when it is absent from extracted text; the preview leaves currency editable and validation blocks included rows with missing currency.
- Did not treat every `Paid In` row as taxable, because that would recreate the exact dangerous shortcut the app is supposed to replace.

### Remaining risks
- The current parser is designed for the TBC v1 extracted-text shape and may skip rows if the bank changes column spacing or starts wrapping rows differently.
- The connected Android UI tests still fail on the attached TV device with `No compose hierarchies found in the app`, so device-side Compose UI verification remains an environment problem.
- `pdfbox-android` initialization is now in app startup; this is acceptable for MVP, but PDF import memory/performance should still be observed on large statements.

### Files changed
- Build/runtime:
  - `gradle/libs.versions.toml`
  - `app/build.gradle.kts`
  - `app/src/main/java/com/queukat/sbsgeorgia/SbsGeorgiaApplication.kt`
- Import data/domain:
  - `app/src/main/java/com/queukat/sbsgeorgia/data/importer/DocumentImportServices.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/data/local/Daos.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/data/local/SbsGeorgiaDatabase.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/data/repository/StatementImportRepositoryImpl.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/domain/model/ImportModels.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/domain/repository/Repositories.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/domain/service/TbcStatementParser.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/StatementImportUseCases.kt`
- Import UI/navigation:
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/importstatement/...`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/home/HomeScreen.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/months/MonthsScreen.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/navigation/AppDestination.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/navigation/AppNavigationState.kt`
  - `app/src/main/java/com/queukat/sbsgeorgia/ui/SbsGeorgiaApp.kt`
- Tests/fixtures:
  - `app/src/test/resources/fixtures/tbc_statement_v1_extracted.txt`
  - `app/src/test/java/com/queukat/sbsgeorgia/domain/service/TbcStatementParserTest.kt`
  - `app/src/test/java/com/queukat/sbsgeorgia/domain/usecase/StatementImportUseCasesTest.kt`
  - `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/MonthsFlowTest.kt`

### Commands run
- `./gradlew.bat assembleDebug --console=plain` — passed
- `./gradlew.bat testDebugUnitTest --console=plain` — passed
- `./gradlew.bat lintDebug --console=plain` — passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain` — passed
- `./gradlew.bat connectedDebugAndroidTest --console=plain` — failed on the attached `4K SMART TV - 11` device with `No compose hierarchies found in the app`

### Next iteration
- Final doc cleanup and explicit open-item capture.
