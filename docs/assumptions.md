# Assumptions

1. `graph20` is modeled as the sum of in-scope income entries explicitly marked for declaration inclusion.
2. Manual income entries are treated as graph-20 eligible by default unless the user later changes their inclusion state.
3. If the SBS effective date lands mid-month, entries before that exact date are excluded from totals and the month is flagged as `review needed`.
4. Months strictly earlier than the effective-date month are shown as out-of-scope for small business declarations.
5. Until Stage 2, non-GEL income entries remain unresolved unless they already have a manual GEL equivalent captured locally.
6. Reminder execution time is not implemented in Stage 1; only reminder preferences are stored.
7. `Overdue` is derived from the clock and stored workflow state, not separately persisted as a hard status.
8. The app currently targets one local taxpayer profile only.
9. No weekend/holiday due-date shifting is applied yet because that rule is not explicitly confirmed in the requirements.
10. Stage 1 focuses on current-year declaration periods in the main UI; historical support can be widened without schema changes.
11. TBC PDF import v1 assumes one concrete extracted-text layout where transaction columns stay separable by multi-space gaps.
12. TBC PDF import v1 assumes transaction rows stay on one extracted text line; wrapped/multi-line statement rows are not handled yet.
13. If statement currency is not recoverable from the extracted text, the preview requires the user to correct currency explicitly before including a row as taxable.
14. Duplicate-safe repeated import is enforced by PDF fingerprint plus transaction fingerprint, not by an additional unique index on `income_entry` yet.
