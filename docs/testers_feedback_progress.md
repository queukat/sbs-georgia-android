# Testers Feedback Progress

## Stage 0: Inventory and Scope Triage

Status: Done

### What was found

- The app already has a mandatory onboarding flow for taxpayer/status setup in `ui/onboarding/*`, but it is not a lightweight product walkthrough and it has no skip path.
- The app already has a strong Settings surface in `ui/settings/*`, but there is no Help/FAQ or About surface.
- Backup/export already exists and is wired through SAF:
  - income CSV export
  - monthly summaries CSV export
  - JSON backup export/import
- There is currently no rate-app entry point and no Play review dependency.
- There is currently no in-app feedback/contact flow.
- Supported locales in the app are `en` and `ru`.
- No verified end-user support email was found in the repo; the verified existing support endpoint is the project repository URL.

### Scope decisions made

- ASO and Play Store screenshot improvements are being treated as manual/store follow-up tasks only.
- The backup/export implementation itself will not be rewritten unless a concrete bug is discovered.
- No new backend, analytics, crash SDK, or review SDK will be added just to satisfy the tester checklist.
- The implementation path will favor Settings-anchored help/support surfaces and a small persistent quick-start guide.

### Next implementation steps

1. Add the plan/report artifacts in `docs/`.
2. Implement quick-start guide persistence and first-run display logic.
3. Add Help/FAQ and support actions in Settings.
4. Improve backup/export explanatory copy.
5. Run relevant checks and update this log after each major step.

## Stage 1: Product Guidance and Support Surfaces

Status: Done

### What was done

- Added a lightweight persistent quick-start guide separate from the required taxpayer/status setup onboarding.
- Wired first-run guide state through a small app-preferences repository so:
  - new users can see the guide after setup;
  - existing configured users are not forced into it after the feature lands;
  - skip/dismiss is remembered.
- Added reusable Help/FAQ and quick-start dialog components.
- Extended Settings with a new `Help & feedback` section containing:
  - open help / FAQ
  - reopen quick-start guide
  - rate-app entry point
  - feedback entry point
- Added safe support actions:
  - Play Store opening with browser fallback
  - public feedback issue opening with prefilled app/device context
- Improved backup/export UX copy with per-action explanations for:
  - income CSV export
  - monthly CSV export
  - JSON backup export
  - JSON backup import
- Improved generic data-operation failure copy.
- Added all new user-facing strings in both supported locales (`en`, `ru`).

### What was intentionally not done

- No Play Review SDK was added.
- No rating popup loop or milestone nag flow was introduced.
- No deep backup/export implementation rewrite was done.
- No tax/business-rule logic was changed.
- No new locale was added beyond the already supported `en` and `ru`.

## Stage 2: Verification

Status: Done

### Checks run

- `.\gradlew.bat testDebugUnitTest --console=plain`
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat lintDebug --console=plain`

### Test additions

- Added a unit test for quick-start guide setup behavior in `AppSetupViewModel`.
- Extended the existing Settings UI test to verify that Help/FAQ can be opened from Settings.

### Not run

- Instrumented / connected UI tests were not run in this pass because they require a configured device or emulator session.

## Stage 3: Hardening Pass Before Merge

Status: Done

### Residual risks reviewed

- quick-start behavior for:
  - fresh install after setup
  - existing configured user after update
  - reopening the guide later from Settings
- Settings `Help & feedback` action clarity
- Play Store opening fallback behavior on devices without Play Store
- feedback flow clarity when it opens a public GitHub issue page
- backup/export explanatory copy

### What was tightened

- Hardened external link opening so failed `startActivity(...)` calls fall back cleanly to snackbar handling instead of relying only on `resolveActivity(...)`.
- Strengthened user-facing feedback copy in `en` and `ru` to say more explicitly that:
  - the feedback page opens in a public browser page/form;
  - what the user writes may be visible to others;
  - tax IDs, PDFs, and backup/export files should not be shared there.
- Tightened backup/export copy so it explains more clearly:
  - income CSV is per-entry export;
  - monthly CSV is period-level summary export;
  - JSON backup is a full restoreable app-state backup;
  - JSON import restores the full app backup and replaces current local data.
- Added a Settings UI test for reopening quick-start from Settings.

### Manual smoke checklist

- fresh install -> complete setup onboarding -> quick-start appears once -> skip/done hides it
- existing user upgrade path -> app opens normally -> quick-start does not auto-open unexpectedly
- Settings -> Help/FAQ opens and content is readable
- Settings -> View quick start guide again opens the guide on demand
- Settings -> Rate app opens Play Store when available, otherwise falls back to browser or shows failure feedback
- Settings -> Feedback opens the public browser issue form and copy warns about public visibility / sensitive data
- Settings -> export/import entry points still launch the expected system document flows
