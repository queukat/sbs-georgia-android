# Testers Feedback Fix Plan

## Executive Summary

The tester report does not require a broad redesign or business-logic rewrite. The repository already contains a strong settings surface, an initial taxpayer/status onboarding flow, local backup/export, reminder settings, and localized string resources. The main gaps are product guidance and support surfaces inside the app:

- the current onboarding is a required setup form, not a lightweight product-value walkthrough;
- there is no Help/FAQ screen;
- there is no in-app rate-app entry point;
- there is no feedback/contact flow;
- backup/export exists but is explained only at a high level.

The implementation approach is to extend the existing architecture with a small first-run quick-start guide, a localized Help/FAQ surface anchored from Settings, clearer backup/export copy, and safe external actions for rating and feedback. Store-only recommendations will be documented separately instead of being forced into app code.

## What From the Report Belongs to App Code

- User onboarding walkthrough:
  the repo has mandatory setup onboarding, but it needs a lighter product-level first-run guide with skip/dismiss persistence and a way to reopen it later.
- Integrated "Rate your app" feature:
  add a deliberate, non-intrusive entry point in Settings/Help with a safe Play Store fallback flow.
- Localized content:
  add new strings through existing resource files and keep the new help/onboarding/support copy consistent across supported locales.
- FAQ and help section:
  add a small localized Help/FAQ surface in or from Settings.
- Backup/export should be straightforward and well-explained:
  improve copy, guidance, and discoverability around the existing export/backup actions without rewriting the mechanics.
- Feedback mechanism:
  add a safe support/reporting entry point that matches project reality.
- Regular updates:
  not a direct code task, but app help/support copy can make update/support expectations clearer.

## What Does Not Belong to App Code and Is Moved to Manual/Store Follow-up

- App Store Optimization (ASO) improvements
- Enhanced Play Store screenshots
- Release cadence / regular updates as a store/product process

These will be documented in `docs/testers_feedback_report.md` as concrete manual follow-up tasks, not simulated with code changes.

## Current Repository State

### Onboarding / first launch

- `app/src/main/java/com/queukat/sbsgeorgia/ui/onboarding/*` already implements a required first-run setup flow for taxpayer profile and small business status.
- `app/src/main/java/com/queukat/sbsgeorgia/ui/app/AppSetupViewModel.kt` shows that onboarding whenever taxpayer profile or status config is missing/incomplete.
- Current onboarding does not offer `Skip` and is not a lightweight product walkthrough. It is a setup form with optional document import.

### Settings / Help / About

- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsScreen.kt` provides a large existing Settings screen with sections for document import, profile, status, reminders, appearance, data management, and save.
- There is no dedicated Help or About screen today.
- Settings is the most natural place to attach Help/FAQ, rate-app, feedback, and "view guide again" actions.

### Rate app / Play review

- No Play review dependency or in-app review implementation is present in `app/build.gradle.kts` or `gradle/libs.versions.toml`.
- No Play Store / browser intent helper exists in the current codebase.
- A safe store-open flow is feasible because `applicationId` is known: `com.queukat.sbsgeorgia`.

### Feedback / contact

- There is no in-app feedback/contact flow today.
- The repository contains a real project URL in `play-privacy-site/index.html`: `https://github.com/queukat/sbs-georgia-android`.
- No support email is currently present in app resources or repository docs for end-user support.

### Backup / export explanation

- Backup/export functionality already exists in Settings via SAF:
  - income CSV export
  - monthly summaries CSV export
  - JSON backup export
  - JSON backup import
- Existing copy in `app/src/main/res/values/strings_settings.xml` explains local-only mode at a high level, but per-action guidance is still sparse.
- Existing generic failure messaging is serviceable but not especially helpful.

### Localization / resources

- `app/src/main/res/xml/locales_config.xml` declares `en` and `ru`.
- Strings are already split by feature across resource files, including `strings_settings.xml`, `strings_feedback.xml`, and `strings_onboarding_snapshot.xml`.
- There is no Georgian locale resource set in the repository right now.

### FAQ / help placement

- Settings is the best existing anchor because it already owns profile, reminders, data management, and re-import actions.
- The help content can stay static and resource-backed; there is no need for remote config or CMS.

## Planned Changes by File / Module

### App setup / first-run guide

- Extend app setup state so the app can distinguish:
  - mandatory setup onboarding
  - optional first-run quick-start guidance
- Add a small persistent app-preferences repository for the quick-start guide dismissal state.
- Show the quick-start guide only where it makes sense, with skip/done persistence and a manual reopen path from Settings/Help.

Likely files:

- `app/src/main/java/com/queukat/sbsgeorgia/ui/SbsGeorgiaApp.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/app/AppSetupViewModel.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/domain/repository/Repositories.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/data/repository/*` or a new preferences repository file
- `app/src/main/java/com/queukat/sbsgeorgia/di/AppModule.kt`
- new localized string resources for quick-start/help content

### Settings / help / support

- Add a `Help & feedback` section to Settings.
- Add a Help/FAQ surface with short practical answers for:
  - adding income
  - GEL conversion
  - missing FX rate on a date
  - monthly declarations
  - reminders and statuses
  - backup/export
  - feedback channel
- Add direct actions for:
  - open help
  - view quick-start again
  - rate the app
  - send feedback / report a problem

Likely files:

- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsViewModel.kt`
- new small help/support composable files if needed
- `app/src/main/res/values/strings_settings.xml`
- new `strings_help.xml` and Russian counterpart

### Backup/export UX copy

- Keep existing backup/export implementation.
- Add per-action explanatory text near the existing buttons.
- Tighten warning and failure copy where that can be done safely.

Likely files:

- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings_settings.xml`
- `app/src/main/res/values/strings_feedback.xml`
- Russian counterparts

### External support actions

- Add a safe Play Store open helper with market-intent first and web fallback.
- Add a safe feedback/report helper based on the existing repository issue tracker, with prefilled non-sensitive environment metadata and privacy guidance.

Likely files:

- new small support/link helper file under `ui` or `common`
- `app/src/main/java/com/queukat/sbsgeorgia/ui/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings_help.xml`

### Tests

- Add or update focused tests around Settings/help/support UI and app setup behavior where practical.
- Reuse existing settings/UI tests instead of introducing a heavy new test harness.

Likely files:

- `app/src/androidTest/java/com/queukat/sbsgeorgia/ui/SettingsScreenTest.kt`
- unit tests for app setup / preferences behavior if needed

## Risks / Limitations

- The current repo supports only `en` and `ru`; adding Georgian now without a verified translation pass would be speculative and low-confidence.
- Any product guide added now should avoid blocking experienced users. Existing users should not be spammed by a new walkthrough on every launch.
- Because there is no existing support email in the repo, feedback will need to use a public issue/repository path unless another verified channel appears during implementation.
- A Play Store entry point must degrade safely on devices without Google Play.
- The scope should stay clear of tax-calculation changes, parser changes, or major navigation refactors.

## What We Will Validate After Changes

- The app still builds successfully.
- Existing onboarding-to-main-app flow still works.
- The quick-start guide:
  - shows only when intended;
  - supports skip/done;
  - does not reappear after dismissal;
  - can be opened again from Settings/Help.
- Settings exposes Help/FAQ, rate-app, and feedback actions without breaking existing save/export flows.
- Backup/export explanatory copy is visible and understandable near the relevant actions.
- All new user-facing strings exist in both supported locales.
- Existing relevant tests still pass, and any new targeted tests pass.
