# Stack Decisions

## Decision Log

### 1. Build baseline
- Keep AGP 9.1.x because it is current stable and already matches the project wrapper/toolchain.
- Move off `legacy-kapt` onto KSP because the Android migration guidance now recommends KSP and kapt is maintenance-only.
- Keep Gradle 9.3.1 wrapper as-is because it already matches the current AGP branch.

### 2. Room
- Stay on Room 2.8.x for now.
- Use the Room Gradle plugin and repo-checked schemas.
- Do not jump to Room 3.0 during this pass.

### 3. Navigation
- Migrate from `navigation-compose`/`NavController` to Navigation 3 now.
- Use typed serializable route keys and explicit back stack ownership.
- Keep navigation state mutations out of screen composables.
- Use separate top-level back stacks for `Home`, `Months`, and `Settings`.
- Treat manual income entry as a nested task flow rather than a permanent tab.

### 4. Hilt ViewModel integration
- Stop using the deprecated `androidx.hilt:hilt-navigation-compose` path.
- Move to the current `androidx.hilt:hilt-lifecycle-viewmodel-compose` package/artifact so ViewModel resolution is not coupled to old Navigation Compose.

### 5. Module split
- No module split by default in this pass.
- Reassess only if Feature Slice C makes the app module materially harder to reason about.

### 6. KSP path under AGP 9
- The desired fully modern target was: built-in Kotlin + new DSL + KSP.
- The tested reality in this workspace was different:
  - KSP `2.2.21-2.0.4` rejects AGP built-in Kotlin.
  - A follow-up attempt to move to a newer built-in-Kotlin/KSP pair did not produce a stable working Compose plugin resolution path here.
- Chosen practical decision for now:
  - keep AGP 9.1
  - remove `kapt`
  - use KSP
  - apply `org.jetbrains.kotlin.android`
  - opt out of built-in Kotlin/new DSL via `android.builtInKotlin=false` and `android.newDsl=false`
- Residual risk:
  - this path is build-clean today but AGP 10 will require revisiting it.

### 7. Feature delivery rule
- Prefer one usable vertical slice over more architectural preparation layers.

### 8. Official FX path
- Use the official NBG JSON feed path for exact-date currency lookup.
- Keep local cache + manual override in Room.
- Do not implement silent nearest-date substitution.

### 9. Reminder scheduling
- Use WorkManager periodic daily execution with reminder-time-driven initial delay.
- Reschedule on settings save.
- Also bootstrap stored reminder config on app launch so the scheduler is restored even if the work graph was cleared.
- Keep reminder logic in a pure planner (`ReminderPlanner`) and keep scheduling/notifications in worker infrastructure.

### 10. Workflow editing model
- Treat the monthly workflow screen as a direct editor, not a transition wizard.
- Allow manual selection of all stored non-derived statuses.
- Keep `OVERDUE` derived-only from filing due date and never store it as the user's chosen truth.
- Clear later-stage dates automatically when the user moves the status backward, so records do not retain contradictory timestamps.

### 11. Reminder permission UX
- Do not fail silently when notification permission is missing on Android 13+.
- Surface permission state in Settings where reminder preferences already live, instead of adding a separate onboarding branch just for notifications.

### 12. PDF import architecture
- Support one concrete TBC statement layout first.
- Keep the parser pure and TBC-specific.
- Keep Android-specific file access and PDF text extraction in separate services.
- Use a preview/correction screen before writing anything into Room.
- Use repository-enforced duplicate safety via:
  - statement source fingerprint
  - transaction fingerprint
  - persisted imported-statement metadata

### 13. Duplicate safety scope
- Do not add a new `income_entry` uniqueness migration yet.
- Current practical protection is sufficient for MVP because:
  - `imported_statement.sourceFingerprint` is unique
  - `imported_transaction.transactionFingerprint` is unique
  - preview marks duplicate transaction fingerprints before import
  - repository double-checks duplicates again during confirm

### 14. Testability boundary for import
- `Uri` is kept out of use-case signatures.
- Use cases work with a document-reader interface that accepts a simple string reference.
- This keeps parser/import orchestration testable in JVM unit tests without Robolectric.
