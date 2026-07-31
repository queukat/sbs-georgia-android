# AGENTS.md - контекст для работы с SBS Georgia Android

Этот файл - быстрый старт для будущих агентов и инженеров. Он описывает продукт,
словарь домена, архитектуру, команды и локальные ловушки проекта. Если факты в
этом файле расходятся с кодом, доверяй коду и обнови этот файл вместе с изменением.

## Public repo hygiene

Этот репозиторий открытый. По умолчанию считай, что любой tracked файл, commit, tag,
branch или pushed artifact увидят другие люди.

- Не добавляй в git приватные PDF, реальные taxpayer IDs, backup/export files,
  keystores, service-account JSON, локальные логи, screenshots с личными данными,
  QA dumps или временные артефакты.
- Не коммить внутреннюю кухню: черновики, одноразовые расследования, локальные
  заметки, machine-specific paths, сырой вывод инструментов и неочищенные архивы.
- Документы в публичной истории должны быть полезны внешнему читателю: продуктовый
  контекст, архитектура, проверяемые команды, release notes, безопасные runbooks.
- Если материал нужен только локально, держи его в ignored paths или вынеси из
  workspace; если нужно опубликовать пример, сначала обезличь и сократи его.

## Коротко о продукте

SBS Georgia Android - offline-first Android-приложение для индивидуальных
предпринимателей в Грузии со статусом малого бизнеса. Основной сценарий:
пользователь импортирует TBC PDF statement или вручную добавляет доходы, приложение
готовит значения для грузинской декларации малого бизнеса, считает налог, помогает
скопировать данные для декларации/платежа и отслеживает workflow месяца.

Что приложение делает:

- превращает TBC PDF statement в editable import preview;
- считает месячный доход, накопительный доход с начала года, `estimated tax`, due date и blockers;
- использует official NBG FX rates и local cache, но не делает silent FX guesses;
- хранит данные локально в Room без developer backend, аккаунтов и cloud sync;
- поддерживает reminders, Android widgets, CSV export и full JSON backup/restore.

Что сознательно не входит в scope:

- general accounting, VAT tooling и multi-entity accounting;
- OCR для scanned PDFs;
- bank API integration;
- rs.ge automation или automatic declaration submission;
- developer-operated backend или хранение налоговых данных на сервере.

## Stack snapshot

- Language/build: Kotlin, Gradle 9.3.1 wrapper, AGP 9.1.x, JVM 17.
- Android: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`.
- App id/namespace: `com.queukat.sbsgeorgia`.
- UI: Jetpack Compose, Material 3, typed Navigation 3 destinations.
- Data: Room database `SbsGeorgiaDatabase`, schema version 6, checked schemas in
  `app/schemas/com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase/`.
- DI/background: Hilt, Hilt WorkManager integration, WorkManager.
- Parsing/network/export: PdfBox Android, official NBG JSON endpoint,
  kotlinx.serialization.
- Tooling: KSP, ktlint, detekt, Jacoco/Sonar, Gradle Play Publisher and a
  checked-in release startup profile.
- Localization: English and Russian string resources, locale config in
  `res/xml/locales_config.xml`.

## Архитектурная карта

- `app/src/main/java/com/queukat/sbsgeorgia/MainActivity.kt` - Android entrypoint.
  Запускает Compose, bootstraps reminders, starts widget refresh observer.
- `SbsGeorgiaApplication.kt` - Hilt app class and custom WorkManager configuration.
- `ui/SbsGeorgiaApp.kt` - верхний Compose shell: brand splash, onboarding gate,
  bottom navigation, Navigation 3 entry provider.
- `ui/navigation` - typed `NavKey` destinations and separate top-level back stacks
  for `Home`, `Months`, `Settings`.
- `ui/*` feature folders - обычно триада `*Contract.kt`, `*ViewModel.kt`,
  `*Screen.kt`. `Route` wires ViewModel/effects, `Screen` renders state.
- `ui/common` - shared scaffolds, formatters, form parsers, copy/share helpers,
  document import and backup controllers.
- `ui/theme` - Material 3 theme, app colors, typography.
- `domain/model` - domain enums/data classes and small validation helpers.
- `domain/repository` - repository interfaces used by use cases.
- `domain/usecase` - orchestration for declarations, settings, import, FX, export,
  onboarding and payment helper.
- `domain/service` - pure planners/parsers/calendars: declaration planner,
  reminder planner, TBC parser, onboarding document parsers, tax-payment detection.
- `data/local` - Room entities, DAOs, type converters, database and migrations.
- `data/repository` - repository implementations over Room/remote sources.
- `data/importer` - Android document reader, PDF text extraction and PdfBox init.
- `data/remote` - official NBG FX remote datasource.
- `data/export` - CSV/JSON backup/export, backup payloads and validation.
- `worker` - reminder scheduling, notifications and `MonthlyReminderWorker`.
- `widget` - Android AppWidget providers and dashboard widget rendering.
- `baselineprofile` - retained macrobenchmark/profile-generation sources. The
  module is currently detached from `settings.gradle.kts`; release consumes the
  checked-in `app/src/release/generated/baselineProfiles/startup-prof.txt`.

## Доменный глоссарий

- `SBS` / small business status - грузинский статус малого бизнеса для локального
  taxpayer profile.
- `TBC statement` - text-based PDF bank statement from TBC Bank. Текущий importer
  оптимизирован под текущий TBC v1 layout и его English/Georgian/bilingual variants.
- `graph20TotalGel` - legacy/internal name месячного in-scope income total в GEL;
  это не универсальная привязка к номеру строки формы.
- `graph15CumulativeGel` - legacy/internal name накопительного дохода с начала года.
- `RS field 15` - накопительный доход с начала календарного года по отчётный месяц.
- `RS fields 18/19/20/21` - взаимоисключающий light-mode destination месячного
  итога: cash register, POS terminal, non-cash excluding POS, other income.
- `DeclarationFormConfig` - включает/скрывает cumulative/monthly copy values и
  выбирает одну строку `18-21`; default - поля `15 + 20`.
- `GEL equivalent` - сумма записи в GEL. Для `GEL` равна original amount; для
  foreign currency нужна official NBG rate или manual override.
- `NBG FX` - официальный курс National Bank of Georgia из JSON endpoint.
- `manual FX override` - ручной курс/units для конкретной currency/date пары.
- `due period` - обычно предыдущий income month относительно текущей даты.
- `filing window` - с 1-го по 15-е число месяца после income month.
- `due date` - 15-е число следующего месяца, с переносом на следующий business day
  при weekend/Georgian holiday.
- `zero declaration` - месяц без included in-scope income, но декларация может
  все равно требоваться.
- `review needed` - состояние, когда месяц нельзя считать безопасно готовым:
  missing setup, out-of-scope, pre-effective-date entries или review-required rows.
- `unresolved FX` - non-GEL income entry без `gelEquivalent`.
- `out of scope` - месяц раньше effective month статуса малого бизнеса.
- `workflow status` - `DRAFT`, `READY_TO_FILE`, `FILED`,
  `TAX_PAYMENT_PENDING`, `PAYMENT_SENT`, `PAYMENT_CREDITED`, `SETTLED`;
  `OVERDUE` вычисляется, но не является обычной persisted truth.
- `quick settle` - быстрый перевод месяца в settled, когда filing window открыт и
  нет blockers.
- `treasury code` - текущий код оплаты `101001000`.
- `payment comment` - generated text вида
  `<registrationId> small business tax for <Month yyyy>`.
- `source fingerprint` - SHA-256 PDF bytes, защита от повторного импорта файла.
- `transaction fingerprint` - fingerprint отдельной imported transaction,
  защита от повторного импорта строки.

## Data glossary

- `TaxpayerProfile` - registration ID, display name, legal form/date/address,
  activity type and base currency view.
- `SmallBusinessStatusConfig` - effective date, default tax rate percent,
  certificate number and issued date.
- `ReminderConfig` - reminder days/time, enable flags and `ThemeMode`.
- `DeclarationFormConfig` - persisted copy-field visibility and the selected monthly
  RS income field; входит в Room migration и full JSON backup/restore.
- `IncomeEntry` - ручной или imported доход: date, amount, currency, category,
  note, declaration inclusion, GEL equivalent and source linkage.
- `MonthlyDeclarationRecord` - user workflow state for month: status,
  zero-declaration flag, filing/payment dates, payment amount and notes.
- `FxRate` - rate date, currency, units, rate to GEL, source and manual flag.
- `ImportedStatement` - metadata for imported PDF source.
- `ImportedTransaction` - parsed transaction row and duplicate-protection metadata.
- `MonthlyDeclarationSnapshot` - calculated read model for UI/widgets/charts/export:
  period, monthly/cumulative income, tax, FX count, review/setup flags and stored record.
- `AppBackupDocument` and `*Payload` classes - JSON backup wire shape. Restore
  validates uniqueness, references and positive amounts before replacing tables.

## Workflow notes

- Onboarding gates the main app until required profile/status data exists. It can
  use registry extract import, SBS certificate import, full backup restore or manual
  input.
- Manual income defaults to declaration inclusion and keeps non-GEL entries
  unresolved until official/manual FX is available.
- Statement import is two-step: load preview, let the user edit rows, then confirm.
  Confirm stores statement metadata, transactions and included income entries.
- Duplicate protection exists both at statement level (`sourceFingerprint`) and
  transaction level (`transactionFingerprint`), with duplicate rows flagged in preview.
- TBC parsing is pure domain code. Android file access and PDF text extraction stay
  in `data/importer`; do not put `Uri` into use-case APIs.
- FX resolution checks local cache first, then official NBG JSON. Do not silently
  substitute nearest dates or guessed rates unless product requirements change.
- Declaration planning lives in `MonthlyDeclarationPlanner`; Georgian non-business
  day adjustment lives in `GeorgiaTaxBusinessCalendar`.
- Home and Month Detail build copy rows from the persisted `DeclarationFormConfig`.
  Field 15 is cumulative; the whole monthly total goes to exactly one selected
  field 18/19/20/21. Mixed per-account/per-payment-method allocation is not modeled.
- Aggregate declaration actions use shared `DeclarationCopyActions`: one full-width
  primary copy-all action, then a stable bank-text/share row. Do not reintroduce
  width-by-label `FlowRow` layouts independently on Home and Month Detail.
- A zero-tax month is complete only after the declaration is persisted as `FILED`
  with a filing date. It must not require or invent payment dates/amounts. An
  unfiled zero month can still be overdue; Home/Months expose a direct action that
  files the zero declaration and then presents the month as closed.
- Charts belongs to the `Months` top-level back stack and is opened from the compact
  action at the top of Months. Back from Charts must return to Months.
- Snapshot original-currency totals are source amounts, not FX rates. Exact applied
  rates are shown in Month Detail as distinct date/currency/source rows; never show
  one invented or averaged monthly rate.
- Backup restore replaces local tables inside a Room transaction. If imported setup
  is incomplete, onboarding remains active.
- Reminders are daily WorkManager jobs scheduled by stored reminder config and
  bootstrapped on app launch.
- Widgets render through `RemoteViews` and refresh from Room invalidation observer.
- Localization uses `values/` and `values-ru/`; user-visible strings should not be
  hardcoded in Kotlin when they belong in resources.

## Local commands

Common checks:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat lintDebug --console=plain
```

Fast connected-phone install:

```powershell
.\gradlew.bat :app:installPhone --console=plain
```

- `installPhone` installs the ordinary `debug` variant with application ID
  `com.queukat.sbsgeorgia.debug` and launcher label `Georgia Small Biz Dev`.
  It coexists with the Play-signed app and uses a separate local database.
- Gradle build cache and configuration cache are enabled. Keep the Gradle/Kotlin
  daemons warm during an active edit-install loop; do not add `--no-daemon`.
  Stop stale daemons after parallel investigations, not between normal rebuilds.
- `installQa` remains the slower, release-like path for startup/performance checks;
  it is not the default UI iteration command.

Static analysis and coverage:

```powershell
.\gradlew.bat detekt ktlintCheck --console=plain
.\gradlew.bat :app:jacocoDebugUnitTestReport --console=plain
```

Connected phone/emulator tests:

```powershell
.\scripts\run_phone_connected_tests.ps1
```

The connected-test script auto-selects the only ready device. When a phone and an
emulator are both present, pass `-Serial <adb-serial>` explicitly.

Do not run the connected suite on a physical phone that holds the user's restored
dev backup. UTP may uninstall the active dev package before a replacement install
fails on a signature mismatch, erasing its local data. Use an AVD for instrumentation
and reserve the phone for `installPhone`, launch, UI-tree and screenshot checks.

Play screenshots:

```powershell
.\scripts\run-play-screenshots.ps1
```

Release/publishing notes live in `docs/play_console_setup.md`, but `docs/` is
ignored in this repo, so confirm local availability before relying on it.

GitHub auth and CI:

- On this Windows workspace, auth-dependent `gh` and HTTPS `git push` commands must
  run outside the filesystem sandbox so they can use the system keyring. If a
  sandboxed command reports an invalid token or `SEC_E_NO_CREDENTIALS`, do not start
  a browser login first: retry `gh auth status` and the required command with
  elevated/unsandboxed execution.
- This repository allows direct delivery to `main`; a pull request is optional.
  For substantial changes, a temporary branch may still be used as a remote CI
  sandbox, but after the full GitHub matrix is green, fast-forward `main` and push
  it directly unless the user asks to retain a PR review step.
- Run the full shared verification matrix on GitHub CI, not concurrently on the
  workstation: unit tests, debug assembly, Android-test compilation, Android lint,
  detekt and ktlint. Local Gradle runs should be narrow checks needed to investigate
  or reproduce a specific failure; stop leftover daemons after parallel work.

## Tests and fixtures

- JVM unit tests live under `app/src/test/java`.
- Connected Compose/Room tests live under `app/src/androidTest/java`.
- PDF parser fixtures live under `app/src/test/resources/fixtures`.
- Use `MainDispatcherRule` for coroutine ViewModel tests.
- Connected UI tests intentionally skip Android TV/leanback devices via
  `assumePhoneLikeComposeTestDevice()`.
- Import/parser changes should usually update or add fixture coverage, especially
  for TBC statement variants and onboarding document parsers.
- Room schema changes require a migration, checked schema JSON and migration test
  updates.
- For broad audits or cross-surface regressions, use a swarm of bounded agents split
  by independent contracts (for example navigation, import, declaration workflow,
  FX, backup and reminders). Agents inspect and add scoped tests; the root agent
  reviews and integrates every production change. Do not let swarm agents launch
  overlapping full Gradle suites locally. Push the consolidated branch and let
  GitHub CI run the full matrix once.

## Project traps

- Public repository rule: before staging/pushing, assume every tracked file is
  visible to external readers.
- The worktree may already be dirty with user changes. Do not revert or rewrite
  existing modified files unless explicitly asked.
- `docs/`, `scripts/`, `artifacts/`, `qa_*`, signing files and Play listing metadata
  are ignored by `.gitignore`. New files under ignored paths will not show up unless
  force-added or ignore rules change.
- `AGENTS.md` is intentionally in repo root and should be tracked.
- Keep Room entities, migrations, schema JSON and backup validation in sync.
- Keep EN/RU resource strings aligned when changing user-facing UI.
- Do not expose internal `graph15*`/`graph20*` names as portal field labels. The
  current RS form mapping is modeled by `DeclarationFormField` and user config.
- Do not add silent FX fallback behavior. Visible unresolved states are a product
  principle.
- `PaymentHelperDestination` and screen exist. The primary payment preparation flow
  is currently embedded in `Home` and `MonthDetail`.
- Release builds require local `keystore.properties`; Play publishing uses
  `PLAY_KEY_FILE`.
- Do not document `:baselineprofile:*` as directly runnable while the module is
  detached. Re-enable it in `settings.gradle.kts` as an explicit profile-regeneration
  task, or keep using the checked-in release profile.
- A Play-installed `com.queukat.sbsgeorgia` is signed by Google Play and cannot be
  replaced by a locally signed APK. Do not uninstall it for local QA without an
  explicit user-approved backup/data-loss plan; use `installPhone` alongside it.
- Installing over the same debug signature preserves the dev database, but connected
  test infrastructure may remove the package during setup. Treat physical-phone
  instrumentation as destructive unless the device contains disposable data.
- `allowBackup=false` is intentional for local-first/privacy positioning.
- `MonthlyWorkflowStatus.OVERDUE` is derived from dates and base status, not a normal
  user-selected persisted state.
- When changing statement import, remember both duplicate layers: PDF fingerprint and
  transaction fingerprint.

## Дополнительные рабочие материалы

- `docs/domain-map.md` - Mermaid data-flow diagrams from PDF/manual entry to Room,
  declaration snapshots, widgets and exports.
- `docs/import-fixtures-index.md` - индекс parser fixtures/import variants, текущая
  coverage и known gaps.
- `scripts/check-local.ps1` - короткий local quality gate: unit tests,
  assembleDebug, lintDebug, detekt and ktlint; `-WithCoverage` добавляет Jacoco.
- `docs/play-release-checklist.md` - release checklist для Play: version bump,
  release notes, screenshots, privacy page, bundle signing, internal track publish.
- `docs/connected-android-tests-troubleshooting.md` - troubleshooting guide для
  connected Android tests: ADB serial, wake/unlock, locale, animation scales and
  phone-like device assumptions.
