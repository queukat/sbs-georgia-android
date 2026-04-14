# Testers Feedback Report

## Summary

This report separates tester feedback into:

- app-code changes that belong inside the Android application;
- manual/store follow-up tasks that should not be faked through app code.

The goal is to land real product improvements in the app while keeping store/listing work explicitly outside the codebase.

## Implemented In App

- Added a lightweight quick-start guide that explains:
  - income tracking
  - GEL conversion with official/manual review paths
  - monthly declarations, statuses, reminders, and local backup/export
- Made the quick-start guide:
  - skippable
  - persistent after dismissal
  - reopenable later from Settings
- Added a Help/FAQ surface anchored from Settings with localized practical answers for the requested topics.
- Added a Settings `Help & feedback` section with:
  - Help/FAQ entry point
  - quick-start reopen action
  - rate-app action
  - feedback action
- Added a safe rate-app flow via Play Store intent with web fallback.
- Added a safe feedback/report flow via the existing public project issue tracker, including prefilled app/device context and explicit privacy guidance that the destination is public and should not receive tax IDs, PDFs, or backup/export files.
- Improved backup/export discoverability and explanation without rewriting the mechanics.
- Added all new copy in the currently supported locales only: `en` and `ru`.

## Hardening Pass

- Rechecked the quick-start state logic for:
  - fresh install after setup
  - existing configured users after update
  - manual reopen from Settings
- Confirmed the implemented approach still avoids unexpected auto-show for already configured users after update.
- Hardened external open behavior so a resolved activity is not the only safety check; failed launches still degrade back to the existing snackbar failure path.
- Tightened copy so the feedback entry point reads less like a developer-only workflow and more like a user-facing public feedback/report path.
- Tightened backup/export explanation to better distinguish spreadsheet exports from full restoreable backup.

## Intentionally Not Included

- No Play Review SDK or in-app review prompt loop was added.
- No aggressive rating prompt, incentive logic, or reminder loop was added.
- No tax-calculation, declaration-planning, parser, or FX business logic was changed.
- No deep backup/export refactor was performed because the tester request was primarily about clarity and discoverability.
- No Georgian locale was invented without a verified translation pass.

## App-Code Areas Addressed

- Add a lightweight first-run quick-start guide.
- Add Help/FAQ inside the app.
- Add a deliberate rate-app entry point.
- Add a feedback/reporting path that matches the project reality.
- Improve backup/export explanation and discoverability.
- Keep new copy localized through existing resources.

## Manual / Store Follow-up Only

### Play Store ASO improvements

This is not an app-code task. Recommended follow-up for the store listing:

- Lead with the app’s target audience:
  "Offline-first bookkeeping and declaration tracker for Georgian individual entrepreneurs with small business status."
- Highlight the most differentiating product promises in the short and long descriptions:
  - track income locally;
  - convert foreign-currency income to GEL with official NBG rates;
  - prepare monthly declaration totals;
  - track filing/payment status and reminders;
  - keep control with local backup/export and no required account.
- Add description bullets that reduce ambiguity about privacy and positioning:
  - local-first, no required sign-in;
  - designed for Georgian small business tax workflow;
  - manual entry and supported PDF import;
  - reminder and workflow status tracking;
  - JSON backup and CSV export.
- Make sure the listing avoids overclaiming:
  do not imply accountant substitution, official filing integration, or cloud sync if the app does not provide those things.

### Better Play Store screenshots

This is not an app-code task. Recommended screenshot messaging:

1. Track monthly income without spreadsheets
2. Convert foreign-currency income to GEL with official NBG rates
3. Prepare declaration-ready monthly totals and copy values faster
4. Follow filing, payment, and reminder status month by month
5. Keep your records local with backup and export tools

Recommended screenshot emphasis:

- show clean dashboard / months / month-detail states instead of settings-only shots;
- include at least one screenshot that communicates unresolved FX vs resolved FX;
- include one screenshot that shows monthly workflow readiness or filing/payment status;
- include one screenshot that shows local backup/export and privacy/local-only positioning;
- keep captions practical and outcome-driven, not generic.

No store assets are being generated in this task because the repository does not contain a dedicated approved asset-production workflow for that scope.

## Additional Notes

- "Regular updates" is not something to simulate in code. The app can expose help/feedback surfaces, but actual release cadence remains a product/release-process responsibility.
- The repository currently supports `en` and `ru` locales. No Georgian locale resources are present today, so any broader localization expansion should be treated as a separate scoped task with verified translations.
