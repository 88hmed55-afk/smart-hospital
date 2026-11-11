# Smart Hospital — Data Model Notes v0.1

## Domain ownership

| Domain | Owns | Main workflow |
|---|---|---|
| Organization | branches, departments, staff, doctors | define hospital structure and staff |
| Identity | accounts, roles, permissions, branch access | authenticate and authorize users |
| Patient | patients, contacts, allergies | register and identify patients |
| Scheduling | appointments | reserve clinician time and check-in |
| Clinical | encounters, vital signs, notes, diagnoses | record clinician-led care |
| Laboratory | test catalogue, orders, items, results | order, collect, process, review and release |
| Pharmacy | medication catalogue, batches, prescriptions, dispenses | prescribe and dispense by lot |
| Inventory | locations, stock balances, stock movements | track balances and immutable stock ledger |
| Billing | service catalogue, invoices, invoice items, payments | charge services and record settlements |
| Messaging | outbox, notifications | reliably publish and show notifications |
| Governance | audit logs, operational alerts, settings | trace sensitive actions and monitor operations |

## Key invariants

1. An appointment's end must be later than its start. The database excludes overlapping active appointments for the same doctor.
2. A patient has one permanent MRN. Validate that a birth date is not in the future in the application service (a PostgreSQL CHECK constraint must not depend on the changing CURRENT_DATE value). Duplicate detection beyond MRN (name/date/phone similarities) is an application workflow requiring human review; do not auto-merge patients.
3. Every encounter belongs to one patient and has an attending clinician. An appointment may produce at most one encounter.
4. A signed clinical note is amended, not overwritten in place. The amendment points to the prior note.
5. A lab result becomes visible as final only after authorized review/release according to the lab's workflow.
6. Every dispense item identifies the prescription line and medication batch used. Quantity is positive and stock reduction is recorded in the same transaction.
7. `stock_balances` is a transactionally maintained current balance; `stock_movements` is the movement ledger. A reconciliation job must detect divergence.
8. Invoice amounts use `NUMERIC`, never floating point. Invoice totals are calculated and validated on the backend.
9. Payments may be partial. The payment-posting service locks the invoice row, validates the remaining balance, inserts the payment and updates invoice status atomically.
10. The outbox event is written in the same transaction as the business change. JMS publication and notification delivery are retryable and idempotent.
11. Audit payloads contain metadata needed to explain an action, not a copy of the patient's full medical record.
12. Branch scope and patient-specific access are checked on the server for every sensitive endpoint.

## Important modelling choices

- `patients` is the patient master record. The longitudinal medical record is composed from encounters, notes, diagnoses, allergies, laboratory results, prescriptions and approved documents; there is no redundant standalone medical-record table in V001.
- `lab_results` is one final-result record per lab order item for the first increment. If a panel contains multiple analytes, add `lab_test_components` and `lab_result_components` before implementing complex panels.
- Stock quantity is represented as decimal because medication supplies can be measured in fractional dispensing units in some workflows. The UI must respect the medication's configured dispensing unit.
- `invoice_items` snapshots the displayed description and unit price so future service-catalog price changes do not rewrite historical invoices.
- `reference_type`/`reference_id` on stock movements are a traceability reference, not a relational FK. Application services must validate the referenced source and preserve the original source record.

## V002 extension

The domains previously listed as planned are now defined in `database/migrations/V002__admissions_procurement_analytics_accounting.sql` and documented in `docs/02_v002_domain_design.md`. V002 adds admissions, bed assignments, medication procurement, operational analytics/decision cards, and a single-base-currency general-ledger foundation.
