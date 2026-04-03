# Platform Refresh And Feature Slice Plan

## Context Anchor

Goal: refresh the SBS Georgia Android project from the Stage 1 foundation onto a modern Compose-first stack, then continue with larger user-visible slices instead of more small technical stages.

Explicit constraints:
- keep the Stage 1 business core stable unless there is a concrete defect
- remove the temporary `legacy-kapt` path now
- migrate navigation to Navigation 3 now
- prefer practical slices that produce user-visible value
- avoid broad architecture churn that does not reduce real future debt

## What Stays Unchanged From Stage 1

- Core business rules in [plan.md](C:/Users/User/Desktop/sbsgeorgiaandroid/docs/plan.md):
  - monthly declaration period logic
  - cumulative reset semantics
  - zero declaration requirement
  - unresolved FX must stay explicit
  - pre-effective-date filtering and review semantics
- Local-first single-user architecture.
- Room as the source of truth.
- Hilt, repository/use-case style, Flow/coroutines.
- Compose + Material 3 UI direction.
- Existing Stage 1 unit coverage for month logic, with expansion rather than rewrite.

## What Must Change Now

1. Build stack cleanup
- remove `legacy-kapt`
- move to KSP
- centralize versions and align artifacts with supported modern AndroidX setup

2. Room setup modernization
- use Room plugin for schema export
- keep schemas in repo
- add migration readiness scaffolding

3. Navigation model
- replace `NavController` / `NavHost` flow with Navigation 3
- define typed route keys
- own the back stack state explicitly
- centralize navigation mutations in an app navigator/state holder
- enable future multi-back-stack behavior without another rewrite

4. UI architecture cleanup
- move away from large catch-all files
- make screen state/actions explicit
- separate state, effect, and navigation mutation responsibilities

5. Feature slices
- Slice A: FX + payment helper + month readiness
- Slice B: monthly status editing + reminders
- Slice C: real TBC PDF import v1

## What We Will Not Refactor Now

Not included because they would create churn without removing the main current debt:
- Room 3.0 migration
- broad module explosion
- generic platform/plugin architecture
- full clean architecture rewrite
- screenshot testing as a mandatory gate
- rs.ge automation
- bank API integration
- OCR or generic multi-bank import platform
- broad theme/design refactor unrelated to feature delivery

## Applicability

| Option | Description | Fits constraints | Residual risk |
| --- | --- | --- | --- |
| 1 | Keep current Stage 1 stack and just add features | No | Future debt compounds around `legacy-kapt` and old navigation |
| 2 | Platform refresh first, then large slices on cleaned foundation | Yes | Medium |
| 3 | Heavy modularization before new features | No | Build churn and delay without enough payoff yet |

Chosen path: Option 2.

What would flip the choice:
- if the app had already split into many teams/modules, a deeper structural refactor might pay off now
- if Navigation 3 or KSP migration exposed blockers in current stable artifacts, we would freeze the feature slices after platform refresh and reassess

## Migration Risks

1. Built-in Kotlin + KSP + Hilt interaction under AGP 9 could expose build-script incompatibilities.
2. Navigation 3 migration can easily spread navigation mutations through the UI if not centralized up front.
3. Compose UI tests can become brittle if the navigation and form state contracts are not explicit.
4. FX introduction can accidentally change Stage 1 totals if unresolved/manual override states are not modeled carefully.
5. PDF import can sprawl into a generic parser framework unless kept tightly bound to one TBC statement shape.

## Order Of Work

1. Audit current state and official docs.
2. Write refactor docs and stack decisions.
3. Platform refresh in one pass:
   - versions
   - KSP
   - Room plugin/schemas
   - Navigation 3
   - UI package cleanup
   - testing foundation
4. Verify build, lint, unit tests, and UI tests where available.
5. Implement Feature Slice A end-to-end.
6. Verify.
7. Implement Feature Slice B end-to-end.
8. Verify.
9. Implement Feature Slice C end-to-end.
10. Verify and close docs.

## Practicality Check

Repeated checkpoint:

“Am I removing real future debt and simplifying development, or just performing an attractive but unnecessary rebuild?”

If a change does not clearly improve:
- build/tooling stability
- navigation maintainability
- testability
- delivery speed of the next user slice

it should not be done in this pass.
