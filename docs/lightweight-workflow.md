# Lightweight SBS workflow

The primary job is statement → review exceptions → copy declaration values. This is not general accounting or automatic tax filing.

## Supported calculation

Ordinary Georgia small-business status: with the standard configured rate of 1%, annual tracked SBS income above GEL 500,000 switches the entire crossing month and following months to 3%. Equality alone does not cross the threshold. Annual totals reset in January. Explicit pre-existing non-1% manual settings remain manual settings; they are not interpreted as automatic ordinary-SBS rules.

VAT, special tourism/agrotourism thresholds, loss of SBS eligibility, non-SBS income, and classification of cross-border income are outside this implementation. The user must verify eligibility and complete the annual income history. Importing one bank account for one month cannot establish the complete tax base.

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

`bash scripts/run-core-regressions.sh` compiles production pure-Kotlin rules, importer orchestration, parser and UI contracts, then runs 44 deterministic checks. Requires Kotlin 1.9+ with a JVM coroutines jar, Python 3 and JDK 17+. The script generates only DI annotation and resource-ID stubs; it does not stub business logic. It is not an Android build and does not exercise Room, PDFBox, Compose or WorkManager runtime.

The same checks are wrapped by `LightweightWorkflowRegressionTest` in the normal JUnit source set. Also run the existing unit suite and Android instrumentation suite with the project’s configured toolchain. Do not ship solely on the standalone checks.

No Room schema migration, new runtime dependency, backend, bank integration or automatic tax filing is introduced by this patch.
