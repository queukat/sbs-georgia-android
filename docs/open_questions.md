# Open Questions

1. Resolved:
   filing/payment due handling now shifts from the 15th to the next Georgian working day when the raw deadline lands on a weekend or statutory holiday.
   Remaining operational question: when and how should we maintain the local override table for rare government-declared extra days off?
2. In a month where the SBS effective date is mid-month, should pre-effective-date income merely be excluded, or should the UI require an explicit acknowledgment before the month can become `Ready to file`?
3. Should manual income entry later support attaching proof metadata directly, or remain lightweight and push attachment handling to period notes?
4. For the future PDF parser stage, do we need one fixed TBC export layout first, or multiple variants of the same bank format?
5. For Stage 4 reminders, what default reminder time should be used in the user’s local time zone if they only configure reminder days?
6. Resolved in Block 2:
   JSON backup now includes app settings, monthly records, FX cache, and raw imported statement metadata; CSV remains export-only.
7. For NBG FX Stage 2, what exact official JSON endpoint and fallback transport behavior should be treated as canonical?
8. Wrapped multi-line rows are now supported for the current TBC v1 extracted-text shape; do we expect materially different TBC export variants soon enough to justify another fixture/hardening pass?
9. Should imported excluded rows remain queryable in future UI, or is statement-level metadata plus imported income enough for the expected audit workflow?
10. For connected Compose UI tests, should the repo later add an emulator bootstrap task that explicitly wakes/unlocks the device, or is the lightweight `scripts/run_phone_connected_tests.ps1` workflow sufficient for now?
11. Should future local backup handling add optional encryption/passphrase support, or is device-level file protection sufficient for the current local-only product scope?
12. Should a later pass add an in-app language picker, or is system-locale-only behavior sufficient for the expected user base?
13. Do we already have more real registry extract PDFs beyond the current English-first fixture shape and pragmatic Georgian label-mapping case, or should onboarding document coverage stay conservative until more samples exist?
14. Do we expect materially different real TBC PDF exports beyond the current English/Georgian/bilingual extracted-text variants, and if yes, can we capture them as fixtures before expanding heuristics again?
15. Connected phone QA is now working again. Do we still want a dedicated reproducible phone-emulator bootstrap script later, or is manual device attachment acceptable for this app?
