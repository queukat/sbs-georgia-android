# Product Plan

## Context Anchor

Goal: build a single-user, offline-first Android app for Georgia small business taxpayers that replaces monthly Excel bookkeeping for income tracking, GEL conversion, declaration preparation, tax payment follow-up, and reminders.

Explicit constraints:
- Kotlin + Jetpack Compose + Material 3
- Room, Hilt, Coroutines/Flow, repository/use-case style
- local-only, no backend in v1
- deterministic business rules, no silent fallback logic
- explicit unresolved/error states
- stage-by-stage delivery, starting with Stage 1 only

## Approach Selection

| Option | Description | Fits constraints | Pros | Cons | Residual risk |
| --- | --- | --- | --- | --- | --- |
| 1 | Fast prototype with in-memory state and later rewrite | No | Faster first screen | Violates product-shaped requirement, throws away work | High |
| 2 | Room-first domain MVP with derived month snapshots and staged integrations | Yes | Stable domain core, testable rules, future-safe for FX/import/reminders | More upfront structure | Medium |
| 3 | Generic bookkeeping engine from day one | No | Highly flexible | Too much abstraction for narrow MVP, slows delivery | High |

Chosen path: Option 2.

What would flip the choice:
- If v1 required immediate multi-user sync or server workflows, local-only Room-first would stop being sufficient.
- If bank import scope changed from one concrete statement format to a broad parser platform, Stage 1 would need different boundaries.

Not included in Stage 1 (because outside the current narrow MVP slice):
- NBG network integration, because that is Stage 2.
- PDF parsing/import approval UI, because that is Stage 3.
- reminder scheduling, because that is Stage 4.
- charts, export/import backup, because that is Stage 5.
- rs.ge automation, banking APIs, OCR, VAT, expense accounting, broad bookkeeping flows.

## Delivery Stages

### Stage 1
- App skeleton
- Room schema and repositories
- Domain model and declaration state machine
- Month logic and yearly cumulative calculation
- Manual income entry flow
- Home, months list, month details, settings foundations
- Unit tests for month logic

### Stage 2
- Official NBG FX repository
- FX caching and unresolved/manual-override flows
- FX conversion tests

### Stage 3
- PDF import flow through SAF
- Statement parser for the initial concrete bank format
- Preview, user correction, duplicate detection
- Parser and duplicate tests

### Stage 4
- Monthly workflow statuses
- WorkManager reminders
- Due logic tests

### Stage 5
- Charts
- CSV export
- JSON backup export/import

### Stage 6
- cleanup
- broader test pass
- docs finalization
- release sanity checks

## Domain Model

### Core persisted aggregates

1. `TaxpayerProfile`
- `registrationId`
- `displayName`
- `baseCurrencyView` = `GEL`

2. `SmallBusinessStatusConfig`
- `effectiveDate`
- `defaultTaxRatePercent`

3. `ReminderConfig`
- declaration reminder days
- tax reminder days
- theme mode
- reminder enablement flags

4. `IncomeEntry`
- `id`
- `sourceType` = manual / imported
- `incomeDate`
- `originalAmount`
- `originalCurrency`
- `sourceCategory`
- `note`
- `declarationInclusion`
- `gelEquivalent`
- `rateSource`
- `manualFxOverride`
- source linkage fields for future import tracing

5. `ImportedStatement`
- `id`
- `sourceFileName`
- `sourceFingerprint`
- `importedAt`
- metadata for future parser/import stages

6. `ImportedTransaction`
- `id`
- `statementId`
- `transactionFingerprint`
- raw statement fields
- corrected fields
- taxability suggestion and user decision

7. `FxRate`
- `rateDate`
- `currencyCode`
- `units`
- `rateToGEL`
- `source`
- `manualOverride`

8. `MonthlyDeclarationRecord`
- `year`
- `month`
- `workflowStatus`
- `zeroDeclarationPrepared`
- `declarationFiledDate`
- `paymentSentDate`
- `paymentCreditedDate`
- `notes`

### Derived read models

1. `MonthlyDeclarationPeriod`
- calendar month of income
- filing window in the next month
- due date = 15th of next month
- in-scope / out-of-scope flags

2. `MonthlyDeclarationSnapshot`
- `graph20TotalGEL`
- `graph15CumulativeGEL`
- original-currency grouped totals
- estimated tax
- unresolved FX count
- zero-declaration suggested flag
- review-needed flag
- effective workflow status

## State Machine

Base monthly workflow state machine:

`Draft -> ReadyToFile -> Filed -> TaxPaymentPending -> PaymentSent -> PaymentCredited -> Settled`

Derived overlay:
- `Overdue` is derived from current date and the base state, not silently stored as a separate source of truth.

Why this model:
- it avoids contradictory persisted states like `Settled` + `Overdue`
- it keeps manual status tracking explicit while still letting the UI warn about missed deadlines

State rules for Stage 1 foundation:
- new months start as `Draft`
- zero-income months still exist as declaration periods
- `zeroDeclarationPrepared` is separate from income totals
- overdue evaluation depends on filing due date and whether the period already reached a paid/settled state

## Business Rule Anchors

1. Declaration month is always the previous calendar month.
2. Filing window is from day 1 to day 15 of the next month.
3. Zero declarations are still required.
4. `graph20` is the key declared non-cash income figure.
5. `graph15` is the calendar-year cumulative total.
6. Cumulative resets with January income declared in February.
7. Months before `smallBusinessStatusEffectiveDate` are excluded from small-business totals and flagged for review.
8. No implicit nearest FX rate fallback is allowed.

## Stage 1 Architecture Decisions

- Room is the source of truth for profile, configuration, manual incomes, and monthly declaration records.
- Month summaries are derived, not stored.
- Currency conversion for non-GEL entries is explicit unresolved state until Stage 2.
- Manual entries default to declaration inclusion, but the model already supports explicit exclusion/review states for later import workflows.
- The UI ships with product-shaped screens, but only Stage 1 capabilities are enabled.
