# Smart Hospital — Domain Design v0.2

**Status:** Proposed schema increment; requires migration review and execution against PostgreSQL before use.
**Baseline:** V001 core schema.
**Added in V002:** admissions/bed management, medication procurement, operational metrics/decision support, and general-ledger foundations.

## 1. Scope decisions

1. One hospital is configured for the first runnable release; branch-aware keys remain in the schema.
2. V002 procurement covers medications because V001 inventory is currently medication-batch based. General medical consumables must not be forced into the medication model. A later unified inventory-item/stock-batch model is required before adding them.
3. Admissions are linked to an inpatient `encounters` record. The application service must create/validate an `INPATIENT` encounter before admitting the patient.
4. A patient may have only one currently `ADMITTED` admission per branch. Bed transfers are sequential bed-assignment intervals inside one admission.
5. Bed occupancy is derived from bed-assignment intervals. Do not add an independently edited `occupied` flag that can drift from assignment history.
6. Smart analytics are operational decision support, not clinical diagnosis or treatment recommendations.
7. The accounting increment is a double-entry general-ledger foundation in each branch's base currency. Multi-currency accounting, tax jurisdiction configuration, insurance settlement and cost accounting require separately specified work.

## 2. V002 table catalogue

### 2.1 Admissions and bed management

| Table | Purpose | Main relationships |
|---|---|---|
| `wards` | Defines inpatient units within a branch and department | branch, department |
| `rooms` | Defines rooms within a ward | ward, branch |
| `beds` | Defines beds and non-occupancy operational state | room, branch |
| `admissions` | Records a patient's inpatient admission and discharge lifecycle | patient, inpatient encounter, attending doctor, branch |
| `bed_assignments` | Immutable-in-practice timeline of bed placement/transfers | admission, bed, branch, assigning/releasing users |

**Important constraints:**
- Branch identity is preserved across ward → room → bed and admission → bed assignment relationships by composite foreign keys.
- `ex_bed_assignment_no_overlap` prevents two non-cancelled assignments from overlapping on the same bed.
- `ex_admission_one_bed_at_a_time` prevents one admission from occupying two beds at once.
- `uq_one_active_admission_per_patient_branch` limits each patient to one active admission in a branch.
- An ACTIVE bed assignment must have no `ended_at`; a completed/cancelled assignment must have an end time.
- A patient transfer closes the previous assignment and creates the next assignment in one transaction. Adjacent intervals are allowed because the range is half-open `[start, end)`.

### 2.2 Medication procurement

| Table | Purpose | Main relationships |
|---|---|---|
| `suppliers` | Supplier master data | referenced by purchase orders |
| `purchase_orders` | Medication purchase request/order header | branch, supplier, requester, approver |
| `purchase_order_items` | Medication lines and price snapshots | purchase order, medication |
| `purchase_order_approvals` | Ordered approval decisions | purchase order, approver |
| `goods_receipts` | Delivery receipt header and destination stock location | purchase order, inventory location, users |
| `goods_receipt_items` | Accepted/rejected quantities by medication batch | receipt, exact order line, medication batch |

**Workflow:** Draft → Submitted → Approved → Ordered → Partially Received/Received. A receipt is drafted, checked, then posted. Posting must create or locate the batch, add positive stock ledger movements and update `stock_balances` inside one EJB transaction.

**Important constraints and service rules:**
- Composite keys prevent a receipt item from referencing a purchase-order line from another order, or a batch for a different medication.
- Cumulative accepted quantities across POSTED receipts must not exceed the purchase-order line quantity. This is an aggregate business rule and is validated while locking the order line in the service.
- A POSTED receipt must have a batch on every receipt line; validate before status transition in `GoodsReceiptService`.
- Rejected quantity requires a rejection reason.
- Purchase-order totals are calculated on the server from lines. Never accept final totals from React.
- This increment intentionally handles medications only. Consumable procurement is deferred until inventory is generalized.

### 2.3 Smart operational analytics

| Table | Purpose | Main relationships |
|---|---|---|
| `metric_definitions` | Versionable catalogue of operational measures | source module and metric metadata |
| `metric_snapshots` | Measured value for branch/department/time window | metric, branch, optional department |
| `decision_cards` | Explainable operational finding and recommendation | branch, deduplication key, status |
| `decision_card_evidence` | Evidence references and summarized observed values | decision card, optional metric snapshot |
| `decision_card_actions` | Assigned action and outcome tracking | decision card, users, baseline/outcome snapshots |

**Smart Decision Card minimum content:** problem statement, evidence window, evidence references, observed/threshold values where relevant, analysis summary, recommendation, expected impact, assumptions and confidence. Confidence is a documented estimate/quality indicator, not a guarantee.

**Metric governance:** each metric requires a documented formula, source query, aggregation window, owner, refresh interval and null/low-volume treatment. Snapshots must be reproducible from authorized operational data. Never store patient names, phone numbers, MRNs or clinical note text in analytics dimensions or card payloads.

### 2.4 General ledger

| Table | Purpose | Main relationships |
|---|---|---|
| `chart_of_accounts` | Branch-specific account hierarchy | branch, optional parent account |
| `fiscal_periods` | Open/closed accounting periods | branch, closing user |
| `journal_entries` | Journal header and source traceability | branch, fiscal period, creator/poster, optional reversal source |
| `journal_lines` | Debit/credit lines against accounts | journal entry, chart account, branch |

**Posting rules:**
- A posted entry has at least two lines, total debits exactly equal total credits, a posting user/time, and an open fiscal period containing the entry date.
- Every line must have exactly one positive side: debit or credit.
- A posted journal entry and its lines cannot be edited or deleted. Correct a posted entry with a separate reversing journal and a replacement entry.
- One non-reversal journal can be generated for a given branch/source module/source reference.
- The database trigger checks balance and open-period validity at the transition from DRAFT to POSTED. The EJB service still validates account activity, posting permission, source eligibility and that the reversal mirrors the original amounts.
- This increment uses branch base currency amounts. Foreign-currency accounting requires transaction currency, exchange-rate snapshots, and base-currency debit/credit fields before implementation.

## 3. V001 integrity hardening included in V002

- Composite unique keys on departments, staff, inventory locations, encounters, medications and medication batches support stronger composite foreign keys.
- Staff, appointments and encounters cannot reference a department belonging to a different branch.
- Doctor profiles receive a backfilled `branch_id`, allowing appointments, encounters and admissions to enforce doctor/branch consistency with composite foreign keys.
- An invoice linked to an encounter must match the encounter's patient and branch.
- A lab result cannot be marked released until it has been reviewed by an identified reviewer at a recorded time.

## 4. Business transactions to implement in EJB

### 4.1 Admit patient and assign bed

`AdmissionService.admitAndAssignBed(...)` should:
1. Confirm the patient has no active admission in the branch.
2. Confirm the encounter belongs to the same patient and branch and has type `INPATIENT`.
3. Lock/validate the target bed and verify it is operationally available.
4. Insert the admission and current bed assignment.
5. Write audit metadata and an outbox event in the same transaction.

### 4.2 Transfer patient

`AdmissionService.transferBed(...)` should lock the active assignment, close it at the transfer timestamp, insert the next bed assignment, validate both bed and admission timeline constraints, and write an audit/outbox event atomically.

### 4.3 Post goods receipt

`GoodsReceiptService.postReceipt(...)` should lock the purchase order and order lines, check cumulative receipt quantities, ensure batch/expiry details are valid, insert stock movements, update balances and post the receipt in one transaction. If any step fails, all changes roll back.

### 4.4 Publish a smart decision card

A metric calculation job stores a metric snapshot. A rule evaluator reads governed metrics and thresholds, then creates or updates a deduplicated decision card and evidence rows. It does not execute staffing, clinical or financial actions automatically. An authorized manager reviews and assigns an action.

### 4.5 Post a journal entry

`JournalService.postEntry(...)` should validate account activity and branch, ensure the fiscal period is open, persist all lines, then transition the header to POSTED. The database trigger verifies balance and dates. A reversal is a new journal entry linked through `reversal_of_entry_id`.

## 5. Implementation order after schema review

1. Apply V001 and V002 to a disposable local PostgreSQL database.
2. Run schema checks: tables, FKs, constraints, partial indexes, exclusion constraints and journal triggers.
3. Add synthetic reference data for branches, departments, roles, permissions, staff, accounts and catalogue items.
4. Create JPA entities for Organization and Identity first, then Patient/Scheduling/Clinical, then Laboratory/Pharmacy/Inventory/Billing, then Admissions/Procurement, then Analytics/Accounting.
5. Create DTOs and REST resources after service boundaries and authorization rules are defined.
6. Add integration tests with GlassFish for transaction rollback, authorization, duplicate messages, concurrency conflicts and journal posting.

## 6. Known limitations / not yet modeled

- Radiology/imaging orders and image storage.
- Insurance providers, policy coverage, pre-authorization and claims.
- General medical consumables in inventory procurement.
- Full multi-currency accounting and exchange-rate history.
- Payroll, time attendance and detailed HR workflows.
- Clinical document binary storage, retention schedule and consent model.
- Clinical terminology catalogs and detailed multi-analyte laboratory panels.
- Advanced patient identity matching/merge review workflow.

These are deliberately excluded from V002 so the model remains reviewable and the first version can be implemented and tested coherently.
