# Smart Hospital — Schema Review and Validation Checklist

Use this checklist before generating JPA entities or seeding data.

## A. Environment

- [ ] PostgreSQL 16 or later is installed and running.
- [ ] A new empty database named `smart_hospital_dev` (or an explicitly chosen name) exists.
- [ ] The database role used for migration can create the required extensions and schema.
- [ ] `psql` is available in the Windows PATH.
- [ ] No real patient information is used in development data.

## B. Migration sequence

- [ ] Apply `V001__hospital_core.sql` to an empty database.
- [ ] Confirm V001 commits successfully.
- [ ] Apply `V002__admissions_procurement_analytics_accounting.sql`.
- [ ] Run `database/validation/validate_schema_v002.sql`.
- [ ] Confirm the expected 61 tables exist after both migrations.
- [ ] Confirm `doctor_profiles.branch_id`, `lab_orders.branch_id`, `prescriptions.branch_id` and `dispenses.branch_id` are populated and NOT NULL.

The included PowerShell helper applies these steps to a fresh database. It intentionally refuses to rerun over an existing `hospital` schema.

## C. Integrity constraints to exercise in a disposable test transaction

- [ ] Create two overlapping active appointments for the same doctor: the second must fail.
- [ ] Create adjacent appointments for the same doctor where first `ends_at = second starts_at`: both should pass.
- [ ] Attempt to link an appointment to a doctor from another branch: it must fail.
- [ ] Attempt to create two active admissions for one patient in one branch: the second must fail.
- [ ] Attempt overlapping assignments for the same bed: the second must fail.
- [ ] Attempt overlapping bed assignments for two beds in the same admission: the second must fail.
- [ ] Attempt to post a journal with one line or unbalanced debits/credits: posting must fail.
- [ ] Attempt to post a balanced journal into a closed or non-covering fiscal period: posting must fail.
- [ ] Attempt to edit or delete a posted journal line: the operation must fail.
- [ ] Attempt to use a medication batch belonging to a different medication on a goods receipt line: the FK must fail.

## D. Application-level invariants not fully expressible as simple table constraints

These must be enforced in EJB services and covered by integration tests:

- [ ] An inpatient encounter must be of encounter type `INPATIENT` before admission.
- [ ] Goods-receipt accepted quantities across all posted receipts cannot exceed the ordered quantity.
- [ ] Every line on a POSTED goods receipt has a batch reference.
- [ ] Stock balance and movement ledger are updated in the same transaction.
- [ ] Dispensed quantities cannot exceed the outstanding prescription quantity.
- [ ] An invoice total is calculated from invoice lines and tax/discount policy on the backend.
- [ ] A payment cannot exceed the remaining invoice balance; concurrent payment attempts lock the invoice row.
- [ ] Only reviewed lab results can be released.
- [ ] A decision-card deduplication key includes rule/version and time-window identity to prevent both duplicate spam and permanent suppression.
- [ ] Evidence and metrics linked to a decision card belong to the same branch and permitted time window.
- [ ] A journal reversal is a separate, balanced journal linked to the original; the original posted entry is never edited.

## E. Review outcomes

Record the date, PostgreSQL server version, migration execution output, validation output and any manual schema amendments. If a migration fails, preserve the exact database error and fix the migration before generating entity classes.

**Important limitation:** The current delivered SQL files have had static source-level checks (table/reference/quote checks), but have not yet been executed against a live PostgreSQL server in this workspace. The database execution and constraint tests in this checklist remain outstanding.
