# Manual QA Checklist

Device target:
- Preferred: phone emulator or real Android phone
- Current status: blocked at the end of the latest pass because the only visible adb target was an offline Android TV device (`192.168.100.5:5555`)

Build target:
- `debug`

## Scenarios

1. Theme mode switching
- Goal: verify `System / Light / Dark` applies immediately, survives restart, and matches imported backup settings after restore.
- Status: Pending device QA
- Notes:

2. First launch onboarding
- Goal: clean install lands in onboarding instead of the main app when taxpayer/status setup is incomplete.
- Status: Pending device QA
- Notes:

3. Registration PDF import happy path
- Goal: import a supported registry extract PDF, inspect preview, apply parsed fields, edit if needed, and complete onboarding.
- Status: Pending device QA
- Notes:

4. Manual fallback onboarding
- Goal: skip PDF import, fill onboarding manually, save, then reopen settings and confirm values persisted.
- Status: Pending device QA
- Notes:

5. Manual income create / edit / delete
- Goal: create a manual GEL income, edit it, then delete it from month details.
- Status: Pending device QA
- Notes:

6. Unresolved FX + manual override
- Goal: create non-GEL income, see unresolved state, apply manual override, verify month updates.
- Status: Pending device QA
- Notes:

7. Zero declaration month
- Goal: confirm zero-income month guidance and toggle zero declaration prepared state.
- Status: Pending device QA
- Notes:

8. January / December cumulative reset
- Goal: verify January starts a new cumulative cycle and December carries the year-end cumulative.
- Status: Pending device QA
- Notes:

9. Reminder permission denied / granted
- Goal: verify settings messaging for notification permission states and reminder-related UX remains coherent.
- Status: Pending device QA
- Notes:

10. Payment helper
- Goal: verify treasury code, payment comment, tax amount, and copy actions.
- Status: Pending device QA
- Notes:

11. Import same PDF twice
- Goal: import one TBC statement and confirm repeat import is blocked by statement fingerprint.
- Status: Pending device QA
- Notes:

12. Import overlapping statements
- Goal: import a second overlapping statement and confirm already-known rows are flagged as duplicates while new rows remain importable.
- Status: Pending device QA
- Notes:

13. TBC import from English statement
- Goal: import a supported English TBC statement, review suggestions, and confirm rows can be corrected before import.
- Status: Pending device QA
- Notes:

14. TBC import from Georgian statement
- Goal: import a supported Georgian TBC statement, verify header detection and safe taxable/excluded suggestions.
- Status: Pending device QA
- Notes:

15. Russian locale UI pass
- Goal: switch device/app locale to Russian and confirm the main UI does not show mixed half-English / half-Russian screen text.
- Status: Pending device QA
- Notes:

16. Backup/import with theme and settings preserved
- Goal: export JSON backup, reinstall or reset local data, import backup, and confirm theme/settings reflect restored values immediately.
- Status: Pending device QA
- Notes:

17. Month before SBS effective date
- Goal: confirm month is marked out-of-scope and excluded from small-business totals.
- Status: Pending device QA
- Notes:

18. Mid-month SBS effective date
- Goal: confirm only post-effective-date income contributes to totals and the month is marked review-needed.
- Status: Pending device QA
- Notes:
