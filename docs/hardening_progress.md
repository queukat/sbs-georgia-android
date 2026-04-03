# Hardening Progress

## Block 1. Connected UI Test Stabilization

### What was done
- Separated device-class issues from app issues by skipping Compose UI tests on Android TV / leanback devices with `assumePhoneLikeComposeTestDevice()`.
- Standardized Compose screen tests on `createComposeRule()` instead of a custom host activity, because the screens under test are pure composables.
- Added/kept Compose UI coverage for:
  - onboarding/settings happy path
  - manual income create/edit
  - months list -> month details
  - unresolved FX visibility
  - import preview happy path
- Added stable test tags for:
  - unresolved FX visibility in month details
  - selected import file in import preview
- Hardened assertions to use field-level text checks and scroll where appropriate instead of brittle duplicate text matches.
- Confirmed the actual environment issue behind `No compose hierarchies found in the app`: the phone emulator was launching tests while sleeping/locked.
- Added [`scripts/run_phone_connected_tests.ps1`](../scripts/run_phone_connected_tests.ps1) to wake/unlock the emulator and run connected tests against a phone device only.

### What was not done
- No screenshot test infrastructure was added.
- No TV device support for Compose connected tests was added; TV remains intentionally excluded from this signal.
- No new architecture/module refactor was introduced.

### Bugs found
- Compose connected tests were previously being run on an Android TV device, producing invalid failures and install targeting issues.
- On the phone emulator, the test activity launched while the device was sleeping/locked, causing `No compose hierarchies found in the app`.
- Several Compose assertions were stale or too brittle for the current UI:
  - duplicate text match in manual entry
  - off-screen save interaction in settings
  - off-screen unresolved FX assertion in month details
  - import preview file-name assertion relied on exact raw text instead of stable tagged content

### Decisions taken
- Keep phone-emulator-connected Compose tests as the supported path now.
- Treat TV devices as unsupported for this test layer and skip them explicitly.
- Prefer `createComposeRule()` for pure composable screen tests rather than maintaining a dedicated test host activity.
- Make wake/unlock a required preflight for connected Compose tests.

### Alternatives not chosen
- Keeping the custom debug-only host activity:
  it added churn and still was not the root fix once the device sleep state was identified.
- Trying to make the TV device a first-class connected Compose target:
  not useful for current product scope and adds unreliable signal.
- Adding screenshot infra now:
  too much setup for this hardening block.

### Remaining risks
- Connected Compose tests still rely on a real phone emulator being present and awake.
- AGP deprecation warnings (`android.builtInKotlin=false`, `android.newDsl=false`) remain and are deferred unless they can be removed safely in a later cleanup pass.
- Deprecated clipboard API in payment helper is still pending cleanup.

### Files changed
- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/UiTestDeviceAssumptions.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/ManualEntryScreenTest.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/SettingsScreenTest.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/MonthsFlowTest.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/ImportStatementScreenTest.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/MonthDetailScreen.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/importstatement/ImportStatementScreen.kt`
- `scripts/run_phone_connected_tests.ps1`
- `docs/hardening_progress.md`

### Commands run
- `adb devices -l`
- `adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP`
- `adb -s emulator-5554 shell input keyevent 82`
- `./gradlew.bat assembleDebugAndroidTest --console=plain`
- `./gradlew.bat connectedDebugAndroidTest --console=plain`
- `./gradlew.bat :app:installDebug --console=plain`
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat assembleDebug --console=plain`
- `./gradlew.bat lintDebug --console=plain`

### Test results
- `./gradlew.bat testDebugUnitTest --console=plain`: passed
- `./gradlew.bat assembleDebug --console=plain`: passed
- `./gradlew.bat lintDebug --console=plain`: passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain`: passed
- `./gradlew.bat connectedDebugAndroidTest --console=plain` on `emulator-5554`: passed

### Next
- Block 2: complete Stage 5 with charts, CSV export, and JSON backup export/import.

## Block 2. Stage 5 Completion: Charts + CSV Export + JSON Backup

### What was done
- Added end-to-end local data management in Settings:
  - export income entries CSV
  - export monthly summaries CSV
  - export JSON backup
  - import JSON backup through SAF
- Added a dedicated Charts screen reachable from Home, with:
  - monthly Graph 20 GEL chart
  - yearly cumulative Graph 15 GEL chart
  - YTD summary, peak month, unresolved FX month count
- Implemented JSON backup/export plumbing that preserves:
  - taxpayer profile
  - small business status config
  - reminder config/theme
  - income entries
  - monthly declaration records
  - FX cache
  - raw imported statement metadata
  - raw imported transaction metadata
- Hardened restore behavior to clear tables explicitly inside a controlled restore path instead of relying on a broad `clearAllTables()` call inside the backup transaction.
- Made settings reload after backup import so imported settings are visible immediately without app restart.
- Added tests for:
  - CSV export integrity and escaping
  - chart input mapping order
  - JSON backup round-trip with imported statement metadata preservation

### What was not done
- No cloud sync or remote backup target was added.
- No encrypted backup format was added in this pass.
- No selective/merge import flow was added; import remains an explicit replace-local-data action.
- No new module split or platform refactor was introduced.

### Bugs found
- `SettingsViewModel` previously loaded settings once on init, which meant imported backup values would not be reflected on the settings screen until restart.
- Backup restore was relying on a broad table wipe path that was less explicit than needed for a product hardening pass.
- Home quick actions were starting to outgrow a single fixed row once charts were added.

### Decisions taken
- Keep charts as one dedicated user-facing screen instead of embedding partial graphs across multiple existing screens.
- Keep backup/export/import in Settings as the explicit local data-management hub.
- Make JSON backup the canonical full-fidelity local restore format for v1, including raw imported statement metadata.
- Keep CSV export as a reporting/export format only, not an import source.
- Keep chart scope pragmatic: current tax year only, because that matches the filing workflow and avoids unnecessary filtering UI.

### Alternatives not chosen
- Pulling in a third-party charting library:
  unnecessary for the current product slice, and it would add dependency/setup churn for simple charts.
- Adding a separate "Data" top-level navigation destination:
  not needed when settings already acts as the management surface for local-only app data.
- Implementing selective backup restore:
  higher product complexity with more data-conflict edge cases than needed for this pass.

### Remaining risks
- Backup import is intentionally destructive to the current local dataset; user intent is explicit in the UI copy, but there is still no pre-import diff/preview.
- Backup files are plain JSON and rely on device/file-system handling for protection.
- Charts currently focus on the current year only; if users later need multi-year chart browsing, that will need an intentional UX extension.
- Deprecated clipboard usage in payment helper is still pending cleanup.

### Files changed
- `app/src/main/java/com/queukat/sbsgeorgia/data/local/Daos.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/data/export/DocumentFileStore.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/data/export/AppBackupManager.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/ExportDataUseCases.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/di/AppModule.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/navigation/AppDestination.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/navigation/AppNavigationState.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/SbsGeorgiaApp.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/home/HomeScreen.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsContract.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/charts/ChartsContract.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/charts/ChartsViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/charts/ChartsScreen.kt`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/usecase/ExportDataUseCasesTest.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/data/export/AppBackupManagerTest.kt`
- `docs/hardening_progress.md`

### Commands run
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat assembleDebug --console=plain`
- `./gradlew.bat lintDebug --console=plain`
- `./gradlew.bat assembleDebugAndroidTest --console=plain`
- `adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP`
- `adb -s emulator-5554 shell input keyevent 82`
- `./gradlew.bat connectedDebugAndroidTest --console=plain`

### Test results
- `./gradlew.bat testDebugUnitTest --console=plain`: passed
- `./gradlew.bat assembleDebug --console=plain`: passed
- `./gradlew.bat lintDebug --console=plain`: passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain`: passed
- `./gradlew.bat connectedDebugAndroidTest --console=plain` on `emulator-5554`: passed

### Next
- Block 3: harden the TBC v1 PDF import flow with realistic fixture variants and duplicate/overlap coverage.

## Block 3. TBC PDF Import Hardening

### What was done
- Hardened the TBC v1 parser to build logical transaction rows from extracted PDF text instead of assuming every transaction stays on one physical line.
- Added support for current-fixture-confirmed cases:
  - multi-page statements with repeated page headers
  - extra whitespace / tab-separated extracted text
  - wrapped transaction rows where description/additional information continues on the next line
  - overlapping statement windows where some transactions are already known and should be flagged as duplicates
- Added 4 additional TBC v1 extracted-text fixtures:
  - `tbc_statement_v1_multipage_extracted.txt`
  - `tbc_statement_v1_wrapped_extracted.txt`
  - `tbc_statement_v1_whitespace_extracted.txt`
  - `tbc_statement_v1_overlap_extracted.txt`
- Extended parser and import-preview tests to cover:
  - repeated headers across pages
  - wrapped-row merge behavior
  - whitespace tolerance
  - duplicate import detection from overlapping windows
  - suggestion quality for software services / fees / transfers / generic client payments
- Tightened non-taxable source-category heuristics so commission/fee rows are categorized as bank-fee-like exclusions instead of own-account transfers.

### What was not done
- No OCR support was added.
- No universal bank-parser framework was introduced.
- No multi-bank abstraction or plugin system was added.
- No binary PDF-fixture suite was added; hardening remains focused on extracted-text fixtures that match the current TBC v1 flow.

### Bugs found
- The parser previously assumed every transaction lived on one physical extracted-text line, which made wrapped rows vulnerable to false “unsupported PDF” failures.
- Repeated page headers and page breaks were not being normalized into logical transaction rows.
- Fee/commission rows could be excluded correctly but still receive a misleading own-transfer source-category suggestion.

### Decisions taken
- Keep the parser TBC-specific and extracted-text-driven.
- Introduce logical row assembly as the minimal robust fix for TBC v1 instead of creating a generic parser abstraction.
- Improve heuristics only where fixtures proved a real problem.
- Keep overlap handling at the fingerprint/repository layer rather than trying to infer statement-window semantics from dates alone.

### Alternatives not chosen
- OCR for wrapped/poorly extracted statements:
  explicitly out of scope and unnecessary for the current statement shape.
- A generic intermediate parsing DSL:
  too much architecture for one bank format and current product needs.
- Silent fuzzy duplicate matching by date/amount only:
  weaker than the current deterministic fingerprint approach.

### Remaining risks
- Real TBC exports that differ materially from the current extracted-text shape can still fail and will need additional fixture-driven refinement.
- Wrapped rows are now supported for the confirmed layout, but highly irregular wrapping or footer noise could still require more skip rules.
- The fixture suite is stronger now, but it still does not include raw PDF binaries from multiple real export batches.

### Files changed
- `app/src/main/java/com/queukat/sbsgeorgia/domain/service/TbcStatementParser.kt`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/service/TbcStatementParserTest.kt`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/usecase/StatementImportUseCasesTest.kt`
- `app/src/test/resources/fixtures/tbc_statement_v1_multipage_extracted.txt`
- `app/src/test/resources/fixtures/tbc_statement_v1_wrapped_extracted.txt`
- `app/src/test/resources/fixtures/tbc_statement_v1_whitespace_extracted.txt`
- `app/src/test/resources/fixtures/tbc_statement_v1_overlap_extracted.txt`
- `docs/hardening_progress.md`

### Commands run
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat assembleDebug --console=plain`
- `./gradlew.bat lintDebug --console=plain`
- `./gradlew.bat assembleDebugAndroidTest --console=plain`
- `adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP`
- `adb -s emulator-5554 shell input keyevent 82`
- `./gradlew.bat connectedDebugAndroidTest --console=plain`

### Test results
- `./gradlew.bat testDebugUnitTest --console=plain`: passed
- `./gradlew.bat assembleDebug --console=plain`: passed
- `./gradlew.bat lintDebug --console=plain`: passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain`: passed
- `./gradlew.bat connectedDebugAndroidTest --console=plain` on `emulator-5554`: passed

### Next
- Block 4: manual QA pass, clipboard cleanup, and pragmatic product polish based on real flows.

## Product Pass. Block 1: Theme Fix + Visual System Cleanup

### What was done
- Fixed the real theme root cause:
  `SettingsViewModel.updateThemeMode()` now persists theme mode immediately, so `System / Light / Dark` applies across the app without restart.
- Kept theme restore behavior working through the already-existing reminder/theme persistence path, including post-backup restore reload.
- Replaced the earlier generated-looking palette with one calmer Material 3 direction:
  - neutral surfaces
  - teal/green primary accent
  - unified scaffold, app bar, bottom bar, cards, and selected states
- Aligned screen shells to the same background/surface model across:
  - home
  - months
  - month details
  - manual entry
  - charts
  - payment helper
  - workflow status
  - FX override
  - import statement
- Added/kept theme-state coverage through `AppThemeViewModelTest`.

### What was not done
- No design-system rewrite or custom design language was introduced.
- No dynamic-color reintroduction was attempted in this pass.
- No screenshot artifact bundle was captured, because there was no stable online phone/emulator session at the end of the pass.

### Bugs found
- Theme buttons previously changed only local Settings screen state but did not persist immediately, so the app theme looked unchanged until later reload.
- Bottom navigation and several screens were using inconsistent surface/background combinations from the earlier palette.

### Decisions taken
- Keep one controlled Material 3 palette instead of dynamic or seed-based variation.
- Prefer coherent neutral surfaces over a more aggressive visual redesign.
- Keep theme persistence tied to the existing reminder/theme config store instead of adding a separate theme preferences layer.

### Alternatives not chosen
- Dynamic color:
  it was not worth leaving palette consistency to device-specific colors.
- A larger visual redesign:
  too much churn for a product-hardening pass.

### Remaining risks
- Visual QA still needs a real phone pass for dark-theme contrast checks after the latest RU/localization updates.
- AGP 9 deprecation warnings remain unchanged and are still deferred.

### Notes
- Visual cleanup focused on coherence, not novelty:
  the main before-state issue was palette disagreement between bottom bar, scaffold, app bars, and cards.
  The after-state keeps one calm surface stack and one accent direction.

## Product Pass. Block 2: Proper Onboarding + Registration PDF Import

### What was done
- Added a real first-run onboarding gate before the main app when taxpayer/status setup is incomplete.
- Added onboarding document import through SAF with preview-before-apply and editable fields before save.
- Implemented two separate onboarding parsers without creating a generic legal-document platform:
  - registry extract parser
  - Georgian small business status certificate parser
- Registry extract parser:
  - supports English labels first
  - also supports Georgian labels through the same pragmatic label-mapping path when field extraction shape matches
  - does not autofill `smallBusinessStatusEffectiveDate`
- Georgian SBS certificate parser:
  - detects certificate markers
  - extracts display name, registration/personal ID, activity type, certificate number, issued date
  - autofills `smallBusinessStatusEffectiveDate` from the line stating when status was granted
- Added onboarding parser fixtures/tests for:
  - English registry extract extracted-text shape
  - Georgian registry extract extracted-text shape
  - Georgian SBS certificate extracted-text shape
- Structured onboarding preview notes and parse errors so UI localization is no longer tied to raw parser English strings.
- Extended backup/restore and Room migration coverage for onboarding metadata fields.

### What was not done
- No OCR support was added.
- No generic legal-document engine was added.
- No separate future SBS-certificate wizard beyond the current onboarding preview/apply path was introduced.

### Bugs found
- Onboarding previously depended entirely on manual settings entry.
- Parser/user-facing onboarding notes and mismatch errors were previously plain English strings coming out of parser logic.

### Decisions taken
- Keep onboarding import document-specific and explicit.
- Keep preview/apply separate from save so the user can correct all parsed fields first.
- Keep registry extract and SBS certificate as different parser types because they represent different business meaning.

### Alternatives not chosen
- One merged “legal document parser” abstraction:
  unnecessary for the current scope and would make validation less explicit.
- Autofilling SBS effective date from registry extract:
  explicitly rejected because that document is not the SBS certificate.

### Remaining risks
- Real PDFs that differ materially from the current extracted-text fixture shape will still require fixture-driven refinement.
- Georgian registry extract support is pragmatic label-mapping support, not broad format coverage.

## Product Pass. Block 3: TBC Statement Import English + Georgian Variants

### What was done
- Extended the existing TBC v1 parser to support:
  - English headers
  - Georgian headers
  - mixed bilingual header lines
- Kept the flow TBC-specific and extracted-text-only.
- Preserved duplicate-safe import behavior and existing preview/correction flow.
- Kept safe suggestion behavior:
  - software/services/invoice-like incoming rows suggest taxable
  - own transfers / fees suggest excluded
  - ambiguous rows stay review-needed
- Added fixture coverage for:
  - English TBC extracted text
  - Georgian TBC extracted text
  - bilingual header extracted text
- Consolidated stable raw source-category presets so parser/storage uses canonical values while UI can localize labels safely.

### What was not done
- No OCR.
- No multi-bank support.
- No generic parser framework.

### Bugs found
- The earlier parser only recognized the English header/layout family.
- User-facing source-category labels were effectively tied to raw English stored values.

### Decisions taken
- Keep parser heuristics fixture-driven and conservative.
- Keep raw stored source categories stable in English canonical form, but localize known labels in the UI.

### Alternatives not chosen
- Translating stored source-category values directly in the database:
  that would have made localization depend on mutable UI language.

### Remaining risks
- Additional real-world TBC variants still need real fixture examples before further heuristic expansion.

## Product Pass. Block 4: Russian Localization + Product Polish

### What was done
- Moved remaining user-facing validation/info/error text out of the affected viewmodels and into resources.
- Added or completed Russian resources for:
  - onboarding
  - settings
  - home
  - months
  - month details
  - workflow/status
  - import preview
  - payment helper
  - charts
  - reminders/settings feedback
  - empty/error/loading states touched in this pass
- Made month/status/category labels locale-safe without changing domain storage semantics.
- Localized onboarding preview notes through structured note types instead of raw parser strings.
- Cleaned several obvious RU resource leftovers such as:
  - `workflow`
  - `registry extract`
  - `backup`
  - `override`
- Kept default language behavior on system locale only. No in-app language picker was added.

### What was not done
- No custom locale picker.
- No translation of business data values that are intentionally stored as stable raw values unless they are known preset labels.
- No broad rework of export CSV column headers; export remains a data/export surface, not a localized reporting template.

### Bugs found
- Several screens were localized, but multiple viewmodel validation/error strings still surfaced in English.
- Known source-category presets were being shown as raw English text even when the rest of the UI was Russian.

### Decisions taken
- Keep localization pragmatic:
  resources for user-facing UI, stable canonical raw values for storage/parser logic.
- Prefer generic localized import/export failure messages over surfacing raw internal English exception text.

### Alternatives not chosen
- Translating parser/business internals directly:
  rejected so business logic does not depend on UI locale.
- Adding an in-app language picker:
  out of scope for this pass.

### Remaining risks
- Connected Compose/device QA could not be rerun end-to-end because the only visible adb target at the end of the pass was an offline Android TV device.
- Russian localization is product-complete for the touched UI, but future new screens/features must keep using resources to avoid regressions.

### Files changed
- `app/src/main/java/com/queukat/sbsgeorgia/domain/model/OnboardingImportModels.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/model/SourceCategoryPresets.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/service/OnboardingDocumentParsers.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/service/TbcStatementParser.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/common/SourceCategoryLabels.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/manualentry/ManualEntryContract.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/manualentry/ManualEntryViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/manualentry/ManualEntryScreen.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/fxoverride/FxOverrideViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/workflow/WorkflowStatusViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/MonthDetailViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/MonthDetailScreen.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/importstatement/ImportStatementViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/onboarding/OnboardingViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/onboarding/OnboardingScreen.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsViewModel.kt`
- `app/src/main/res/values/strings_feedback.xml`
- `app/src/main/res/values-ru/strings_feedback.xml`
- `app/src/main/res/values-ru/strings_common.xml`
- `app/src/main/res/values-ru/strings_entry_monthdetail.xml`
- `app/src/main/res/values-ru/strings_home_months_charts.xml`
- `app/src/main/res/values-ru/strings_onboarding_snapshot.xml`
- `app/src/main/res/values-ru/strings_settings.xml`
- `app/src/main/res/values-ru/strings_workflow_payment_import.xml`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/service/OnboardingDocumentParsersTest.kt`
- `docs/hardening_progress.md`

### Commands run
- `./gradlew.bat --stop`
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat assembleDebug --console=plain`
- `./gradlew.bat lintDebug --console=plain`
- `./gradlew.bat assembleDebugAndroidTest --console=plain`
- `adb devices -l`
- `./gradlew.bat connectedDebugAndroidTest --console=plain`

### Test results
- `./gradlew.bat testDebugUnitTest --console=plain`: passed
- `./gradlew.bat assembleDebug --console=plain`: passed
- `./gradlew.bat lintDebug --console=plain`: passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain`: passed
- `./gradlew.bat connectedDebugAndroidTest --console=plain`: failed because the only visible adb target was `192.168.100.5:5555`, an offline Android TV device; no online phone/emulator was available.

## Follow-up. Real TBC Statement Variant Check

### What was done
- Verified a real TBC statement PDF export with collapsed columns and bilingual header fragments:
  `statement-818670212_260402_120010.pdf`.
- Added a real extracted-text fixture:
  `app/src/test/resources/fixtures/tbc_statement_v1_collapsed_bilingual_extracted.txt`.
- Hardened `TbcStatementParser` for this concrete TBC variant by:
  - deriving transaction direction/amount from running balance when `Paid Out` or `Paid In` columns collapse away
  - stripping page counters like `1 - 3`
  - skipping split header fragments such as `Date`, `Description`, `Paid In`, `Balance`
  - preserving safe suggestions and existing preview/correction flow
- Installed the updated debug build on the connected phone `R58N81CZH3P`.

### What was not done
- No OCR was added.
- No generic parser layer or multi-bank abstraction was introduced.
- No guessing from “nearest format” was added; this remains a TBC-specific extracted-text fix.

### Bugs found
- Some real TBC exports collapse the first money column into the end of the additional-information text, for example account suffix + amount with no separating whitespace.
- Page counters like `1 - 3` and split bilingual header lines were being appended to the previous transaction row, which broke recognition on real multi-page statements.

### Decisions taken
- Use running-balance inference for this TBC shape instead of trying to heuristically split glued numeric tokens out of account numbers.
- Keep the fix fixture-driven and TBC-specific.

### Remaining risks
- If TBC changes the export layout again and removes reliable running balances, this parser path will need another real-fixture adjustment.
- Extremely irregular wrapped rows can still require additional skip/split rules.

### Files changed
- `app/src/main/java/com/queukat/sbsgeorgia/domain/service/TbcStatementParser.kt`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/service/TbcStatementParserTest.kt`
- `app/src/test/resources/fixtures/tbc_statement_v1_collapsed_bilingual_extracted.txt`
- `docs/hardening_progress.md`

### Commands run
- `./gradlew.bat :app:testDebugUnitTest --tests com.queukat.sbsgeorgia.domain.service.TbcStatementParserTest --console=plain`
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat assembleDebug --console=plain`
- `./gradlew.bat lintDebug --console=plain`
- `adb devices -l`
- `./gradlew.bat :app:installDebug --console=plain`

### Test results
- `./gradlew.bat :app:testDebugUnitTest --tests com.queukat.sbsgeorgia.domain.service.TbcStatementParserTest --console=plain`: passed
- `./gradlew.bat testDebugUnitTest --console=plain`: passed
- `./gradlew.bat assembleDebug --console=plain`: passed
- `./gradlew.bat lintDebug --console=plain`: passed

## Follow-up. Product Flow Fixes: App Language, All-Year Months, Auto FX, Quick Filed

### What was done
- Added Android per-app language exposure through `android:localeConfig` and `res/xml/locales_config.xml`, so supported locales show up in system app language settings.
- Reworked Months from “current year only” to “all relevant declaration years”, driven by imported entries, stored month records, SBS effective year, and current year.
- Added a quick `Mark declaration filed` action directly on month cards.
  After marking a month as `FILED`, declaration reminders for later configured days no longer apply because reminder logic already excludes filed months from declaration notifications.
- Added automatic FX resolution:
  - immediately after statement import for affected non-GEL months
  - once on month-detail open for unresolved non-GEL entries, without forcing the user to press the FX button first
- Kept manual FX override and explicit unresolved states for cases where NBG has no exact rate.

### What was not done
- No custom in-app language picker was added.
- No reminder engine rewrite was introduced.
- No broad refactor of charts/home to multi-year browsing was added in this pass.

### Decisions taken
- Use Android system app-language settings instead of adding a custom locale picker now.
- Show all declaration months in one chronological list grouped by year instead of adding a separate year filter first.
- Treat quick `Filed` as a pragmatic shortcut on month cards while keeping the full workflow editor for detailed status/date control.
- Auto-fetch official FX where deterministic data exists, but keep explicit unresolved/manual override paths when it does not.

### Remaining risks
- System app-language settings visibility depends on Android version/vendor behavior; this pass wires the standard locale-config path rather than a custom picker.
- Auto FX retries on month open are intentionally conservative; if NBG has no rate yet, the user still sees unresolved state and can retry later or override manually.

### Files changed
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/locales_config.xml`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/DeclarationUseCases.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/StatementImportUseCases.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/model/ImportModels.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/importstatement/ImportStatementViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/MonthDetailViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/months/MonthsContract.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/months/MonthsViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/months/MonthsScreen.kt`
- `app/src/main/res/values/strings_home_months_charts.xml`
- `app/src/main/res/values-ru/strings_home_months_charts.xml`
- `app/src/main/res/values/strings_feedback.xml`
- `app/src/main/res/values-ru/strings_feedback.xml`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/usecase/DeclarationUseCasesTest.kt`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/usecase/StatementImportUseCasesTest.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/MonthsFlowTest.kt`
- `docs/hardening_progress.md`

### Commands run
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat assembleDebug --console=plain`
- `./gradlew.bat lintDebug --console=plain`
- `./gradlew.bat assembleDebugAndroidTest --console=plain`
- `adb devices -l`
- `./gradlew.bat :app:installDebug --console=plain`

### Test results
- `./gradlew.bat testDebugUnitTest --console=plain`: passed
- `./gradlew.bat assembleDebug --console=plain`: passed
- `./gradlew.bat lintDebug --console=plain`: passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain`: passed
- Updated `debug` build installed on phone `R58N81CZH3P`

## Follow-up. Filing Window Clarity, Due-Date Rollovers, and Copy Tools

### What was done
- Added a local Georgia tax-business calendar and started using it for effective due dates:
  - base declaration/payment deadline stays the 15th of the following month
  - if the 15th lands on Saturday, Sunday, or a Georgian statutory holiday, the app now moves the effective due date to the next working day
- Kept the declaration filing window explicit:
  - window start remains the 1st of the following month
  - month cards now show `Filing opens on ...` instead of offering a misleading quick-file action before the next-month window starts
- Added direct copy tools inside month details, so declaration and bank data no longer require a separate helper step:
  - Graph 20 copy
  - Graph 15 cumulative copy
  - bank text copy
  - full declaration + bank text copy
- Copy values use dot-decimal plain strings for declaration/manual-bank entry.
- Added an explicit effective due date line to snapshot summaries.
- Fixed the flaky connected Compose test for import preview by switching that test to `createAndroidComposeRule<ComponentActivity>()`.

### What was not done
- No live holiday API was added.
- No government-ordinance sync flow was added for extra one-off days off.
- The existing Payment Helper screen was not deleted; it was simply de-emphasized by moving the useful copy workflow into month details.

### Decisions taken
- Use a local deterministic tax calendar instead of a runtime holiday API.
- Keep the rare government extra days off as a manual override table for now.
- Treat month details as the primary surface for Graph 15/20 and bank-copy actions, because that is where users already inspect readiness and totals.
- Avoid silent copyability for months that are not yet fileable or still unresolved/review-blocked.

### Bugs found
- Quick-file visibility was confusing because the UI did not explain that filing opens only from the 1st of the following month.
- Connected device Compose tests still had one flaky import-preview test even on a phone; the root cause was the lighter Compose rule for a screen that behaves better with a real host activity.

### Remaining risks
- Government-declared extra days off are still a maintained local table and may need occasional updates from official ordinances.
- `connectedDebugAndroidTest` still prints unrelated noise from the attached Android TV and `androidx.test.services` appops setup, but the phone test run now completes successfully.

### Files changed
- `app/src/main/java/com/queukat/sbsgeorgia/domain/service/GeorgiaTaxBusinessCalendar.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/service/MonthlyDeclarationPlanner.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/DeclarationCopyPayload.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/usecase/PaymentHelperUseCase.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/common/MonthlySnapshotComponents.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/MonthDetailContract.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/MonthDetailViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/monthdetails/MonthDetailScreen.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/months/MonthsContract.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/months/MonthsViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/months/MonthsScreen.kt`
- `app/src/main/res/values/strings_onboarding_snapshot.xml`
- `app/src/main/res/values-ru/strings_onboarding_snapshot.xml`
- `app/src/main/res/values/strings_entry_monthdetail.xml`
- `app/src/main/res/values-ru/strings_entry_monthdetail.xml`
- `app/src/main/res/values/strings_home_months_charts.xml`
- `app/src/main/res/values-ru/strings_home_months_charts.xml`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/service/GeorgiaTaxBusinessCalendarTest.kt`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/service/MonthlyDeclarationPlannerTest.kt`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/usecase/DeclarationCopyPayloadTest.kt`
- `app/src/test/java/com/queukat/sbsgeorgia/domain/usecase/ExportDataUseCasesTest.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/ImportStatementScreenTest.kt`
- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/MonthsFlowTest.kt`
- `docs/hardening_progress.md`

### Commands run
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat assembleDebug --console=plain`
- `./gradlew.bat lintDebug --console=plain`
- `./gradlew.bat assembleDebugAndroidTest --console=plain`
- `adb devices -l`
- `$env:ANDROID_SERIAL='R58N81CZH3P'; ./gradlew.bat connectedDebugAndroidTest --console=plain`
- `$env:ANDROID_SERIAL='R58N81CZH3P'; ./gradlew.bat :app:installDebug --console=plain`

### Test results
- `./gradlew.bat testDebugUnitTest --console=plain`: passed
- `./gradlew.bat assembleDebug --console=plain`: passed
- `./gradlew.bat lintDebug --console=plain`: passed
- `./gradlew.bat assembleDebugAndroidTest --console=plain`: passed
- `$env:ANDROID_SERIAL='R58N81CZH3P'; ./gradlew.bat connectedDebugAndroidTest --console=plain`: passed on `SM-N985F - 13`
- Updated `debug` build installed on phone `R58N81CZH3P`
