# Lightweight SBS workflow

The primary job is statement → review exceptions → copy declaration values. This is not general accounting or automatic tax filing.

## Supported calculation

Ordinary Georgia small-business status: with the standard configured rate of 1%, annual tracked SBS income above GEL 500,000 switches the entire crossing month and following months to 3%. Equality alone does not cross the threshold. Annual totals reset in January. Explicit pre-existing non-1% manual settings remain manual settings; they are not interpreted as automatic ordinary-SBS rules.

VAT calculation/registration, special tourism/agrotourism thresholds, loss of SBS eligibility, non-SBS income, and classification of cross-border income are outside this implementation. The user must verify eligibility and complete the annual income history. Importing one bank account for one month cannot establish the complete tax base.

Unknown FX or unresolved review in an earlier month makes the later cumulative calculation incomplete. Never turn unknown tax into zero for copying or quick completion. Excluded pre-status transactions do not block an otherwise valid month.

Official references checked 2026-10-03:
- Tax Code, Articles 73, 89–93: https://www.matsne.gov.ge/en/document/view/1043717
- Revenue Service preferential regimes: https://rs.ge/PersonsPreferentialTax?cat=2&tab=1
- Revenue Service current SBS brochure, available from https://www.rs.ge/brochures
- Tax calendar: https://rs.ge/TaxCalendar

The 2026-09-30 amending law at https://www.matsne.gov.ge/ka/document/view/7013098 changes property-tax Articles 202 and 206, not the SBS rate/deadline provisions above. This implementation review is not a certification of a taxpayer’s filing.

## User flow

1. Home prioritises statement import; the native PDF picker opens once. Cancel does not reopen it repeatedly.
2. Parsing runs off the UI dispatcher. The preview highlights exceptions, locks concurrent imports and requires an explicit decision for ambiguous rows. Partial parsing requires acknowledgement, not silent omission. Users must add any missing income before filing.
3. A clean single-month import opens that month. Multi-month or tax-payment-review results keep the summary rather than picking an arbitrary first month. Copy tools precede diagnostic FX/status sections. A lightweight coverage acknowledgement gates the Home/month-detail copy actions and sharing. It is a user attestation, not automated completeness verification.

Stats start collapsed. Manual entry remains available for corrections and missing income. Opening RS.ge launches the browser; the app never stores RS.ge credentials, submits a declaration, or transfers money.

## Import and FX contracts

- Statement and income duplicate checks use a batched repository method (chunks of 400 fingerprints for each DAO query). The default repository implementation remains compatible with test doubles.
- Included transactions are validated before persistence; unreviewed ambiguous rows are rejected at the use-case boundary too.
- Once income is saved, failure to fetch NBG rates does not report the whole import as failed. Auto-resolution has a cooperative eight-second budget; remaining unknown rates stay visible for manual retry. Parent cancellation is not swallowed.
- Excluded transactions cause no FX fetch. No nearest-date or commercial-bank-rate fallback was added.
- Pure document interfaces now live in `DocumentImportPorts.kt`; Android/PDFBox implementations remain in `DocumentImportServices.kt`.

## Reminders

No reminders before the filing window. At most one actionable notification per worker run. Current due month has priority; explicitly tracked older incomplete months remain eligible. Unrecorded historical zero months are not invented arrears. A derived OVERDUE badge must not erase a stored filing date. A stored payment-sent date must not generate another payment instruction. Lock-screen visibility is private.

Notifications use WorkManager and are best-effort, not exact alarms: https://developer.android.com/reference/androidx/work/PeriodicWorkRequest . User permission, channel settings and OS scheduling still apply. Filed/paid/credited remain local user assertions, never government confirmations.

## Tests

`bash scripts/run-core-regressions.sh` compiles production pure-Kotlin rules, importer orchestration, parser and UI contracts, then runs deterministic checks (the total includes every TBC statement fixture). Requires Kotlin 1.9+ with a JVM coroutines jar, Python 3 and JDK 17+. The script generates DI/Room annotation and resource-ID placeholders; production use-case bodies and the manual-rate DAO method are compiled unchanged. Test repositories are explicit fakes, not a database engine. It is not an Android build and does not exercise Room, PDFBox, Compose or WorkManager runtime.

The checks are wrapped by `LightweightWorkflowRegressionTest` and `HardeningRegressionTest` in the normal JUnit source set. `RepositoryConsistencyTest` exercises generated Room persistence on Android, including conditional FX writes, coherent settings reads and stale completion rejection. Also run the existing unit suite and Android instrumentation suite with the project’s configured toolchain. Do not ship solely on the standalone checks.

No Room schema migration, new runtime dependency, backend, bank integration or automatic tax filing is introduced by this patch.

## Rolling income awareness

Home separately shows recorded included income over the current calendar month to date and the previous 11 months, without resetting at January or the SBS effective date. Details include explicit date boundaries, monthly totals and a next-month scenario. There is no separate 11-month subtotal. Empty months are shown as no entries, not verified zero income.

This is an advisory income monitor, not a VAT turnover calculation. Under Article 165, the general mandatory-registration trigger is qualifying turnover **exceeding** GEL 100,000 in any consecutive 12 calendar months, with application within two business days. GEL 90,000 is an early-review warning. Services to foreign customers and exemptions require separate assessment; SBS inclusion does not determine VAT treatment. Official source: https://matsne.gov.ge/en/document/view/1043717 (Articles 165 and 1621; checked 2026-10-15).

The next-month scenario subtracts the oldest month and adds an expected monthly amount, assuming no further current-month receipts. The default is the median of the last six completed monthly totals, offered only when each sample month has income entries and the window has no unresolved FX/review rows. Users can enter their own estimate; it is not saved as income. Missing FX/review rows label the total partial and disable the scenario and distance display. Warnings appear on Home; this does not add background push notifications or assess past threshold crossings. The date refreshes when Home resumes.

## Read/write hardening

Calculation inputs now come from `DeclarationDataRepository` as one committed snapshot. The common
`ObserveDeclarationStateUseCase` partitions income by year and computes off the UI dispatcher. It is a
cold flow, not an application-wide retained cache: independent subscribers may still recompute.
Declaration projections respond to calendar-date changes during active collection. One-shot CSV,
worker and widget reads use a fresh transaction rather than a cached UI value.

FX persistence uses compare-before-write inside a transaction and updates only the FX fields.
A date/currency group is one write batch. Concurrent edits and deletions are skipped, not overwritten
or resurrected. Manual-rate replacement preserves its existing primary key. Cached rates are loaded
once per date/currency/manual group for month details, and are only displayed when they reproduce the
stored rounded GEL value. This is not a historical per-entry rate ledger.

Quick completion recomputes the month within the write transaction and refuses stale financial or
workflow snapshots. This strengthens the local assertion; it still does not verify government filing
or bank payment. Ordinary manual workflow editing is not a full multi-editor conflict-resolution system.

Backup export reads all tables within one transaction. Settings updates and import duplicate rechecks
are transactional too. Existing backup wire format and Room schema version are unchanged. Invalid
linked inclusion states and negative resolved GEL amounts are rejected before destructive restore.

Automatic widget refresh uses a conflated queue, serial rendering and one summary per provider batch.
Reminder bootstrap preserves scheduled work; explicit scheduling changes cancel and re-enqueue it.
WorkManager remains best-effort and requires on-device permission, timezone and Doze checks.

Input streams reject content beyond 32 MiB for PDF, 64 MiB for backup and 2 MiB for NBG JSON. These are
input byte limits, not decompressed-memory guarantees. Large-file streaming, PDF extraction profiling,
per-entry FX provenance and a fully shared read-model cache are not part of this change.
