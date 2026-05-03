# SBS Georgia Android

Offline-first Android app for Georgian individual entrepreneurs with small business status.

<div align="center">
  <p><strong>Install SBS Georgia from Google Play</strong></p>
  <a href="https://play.google.com/store/apps/details?id=com.queukat.sbsgeorgia">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="220">
  </a>
</div>

SBS Georgia turns a monthly TBC bank statement into the values needed for a Georgian small business declaration. The routine work is automated: import the statement PDF, review anything the app flags, copy the prepared declaration/payment values, and paste them into the official filing or bank flow.

## Main flow

1. Set up the taxpayer profile once by importing a registry extract, importing a small business status certificate, restoring a backup, or entering the fields manually.
2. Import a TBC bank statement PDF for the month.
3. Review the preview: income rows are suggested automatically, duplicates are skipped, and tax payments/transfers/review-needed rows are called out.
4. Let the app calculate GEL totals, cumulative yearly totals, estimated tax, filing deadline, payment details, and reminder state.
5. Copy the ready values for Graph 20, Graph 15, tax amount, treasury code, payment comment, or the full declaration/payment text.
6. Paste the copied values into the declaration and payment flows, then mark the month as filed or paid in the app.

## What it handles

- TBC PDF statement import with editable preview, taxable-income suggestions, duplicate detection, and detected tax-payment rows.
- Monthly declaration snapshots with Graph 20, cumulative Graph 15, zero-declaration handling, filing windows, due dates, overdue state, and estimated tax.
- Official NBG FX conversion to GEL with local caching, visible unresolved states, and manual FX overrides when needed.
- Payment helper with treasury code, generated payment comment, and the exact amount to pay.
- Workflow tracking for declaration filing, payment sent/credited dates, notes, and month status.
- Reminders for declarations and tax payments, plus a home-screen widget for the current due period.
- Charts, income CSV export, monthly summary CSV export, and full JSON backup/restore.
- Local-first storage: the app has no backend, and user data stays on the device unless exported by the user.

## Scope

The app is intentionally focused on Georgian small business status declarations for one local taxpayer profile. It is optimized for TBC statement PDFs and does not try to be a general accounting system, VAT tool, bank API client, or rs.ge automation bot.

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
