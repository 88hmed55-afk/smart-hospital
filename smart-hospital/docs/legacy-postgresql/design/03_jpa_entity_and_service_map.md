# Smart Hospital — JPA Entity and EJB Service Map

**Purpose:** Define the next implementation boundary after V001/V002 schema review. This is a planning document, not generated Java source.

## 1. Package rule

Use `com.smarthospital.<module>` with `entity`, `dto`, `repository`, `service`, `rest` and `mapper` packages where they are justified. Avoid one global `entities` or `services` package. Entities must not be returned directly from REST resources; expose DTOs.

## 2. Entity implementation order

| Order | Module | Initial entities |
|---|---|---|
| 1 | organization | HospitalBranch, Department, StaffMember, DoctorProfile |
| 2 | identity | UserAccount, Role, Permission, UserRole, RolePermission, UserBranchAccess |
| 3 | patient | Patient, PatientContact, PatientAllergy |
| 4 | scheduling | Appointment |
| 5 | clinical | Encounter, VitalSign, ClinicalNote, DiagnosisCatalog, EncounterDiagnosis |
| 6 | laboratory | LabTestCatalog, LabOrder, LabOrderItem, LabResult |
| 7 | pharmacy/inventory | Medication, MedicationBatch, InventoryLocation, StockBalance, StockMovement, Prescription, PrescriptionItem, Dispense, DispenseItem |
| 8 | billing | ServiceCatalog, Invoice, InvoiceItem, Payment |
| 9 | admissions | Ward, Room, Bed, Admission, BedAssignment |
| 10 | procurement | Supplier, PurchaseOrder, PurchaseOrderItem, PurchaseOrderApproval, GoodsReceipt, GoodsReceiptItem |
| 11 | messaging/governance | MessageOutbox, Notification, AuditLog, OperationalAlert, SystemSetting |
| 12 | analytics/accounting | MetricDefinition, MetricSnapshot, DecisionCard, DecisionCardEvidence, DecisionCardAction, ChartOfAccount, FiscalPeriod, JournalEntry, JournalLine |

## 3. Persistence rules

- Define UUID identifiers consistently (`UUID` in Java and PostgreSQL `uuid` in SQL).
- Use `Instant` for `TIMESTAMPTZ` and `LocalDate` for business dates.
- Use `BigDecimal` for money and fractional quantities; never use `double` for financial values.
- Use `@Version` on high-contention mutable roots such as stock balances and invoice headers, in addition to service-level locking where required.
- Use explicit `@Table(schema = "hospital", name = "...")` mappings.
- Use lazy associations by default where appropriate; DTO mapping must occur while required data is available, without exposing lazy proxies over REST.
- Avoid automatic cascade remove on clinical, financial, stock-ledger and audit relationships.
- Use immutable or append-only handling for signed notes, posted movements, posted payments, and posted journal entries.
- Keep JSONB mapping choices explicit and provider-compatible. Prefer typed fields where data structure is stable; JSONB is for bounded metadata/configuration, not a substitute for relational modeling.

## 4. EJB service boundaries

| EJB service | Main use case | Transaction note |
|---|---|---|
| PatientService | register/update patient, contact and allergy maintenance | MRN uniqueness and duplicate-review workflow |
| AppointmentService | book, reschedule, check-in, cancel | rely on overlap constraint; translate conflict to 409 |
| EncounterService | open/complete encounter | verify patient, branch, doctor and appointment linkage |
| ClinicalNoteService | create draft, sign and amend note | signed note is never overwritten |
| LabOrderService | create/cancel lab order | write outbox event with order change |
| LabResultService | record, review and release result | release only after authorized review |
| PharmacyDispenseService | dispense prescription from batches | lock stock balance/batch; movement and balance update atomically |
| InvoiceService | calculate, issue and void invoice | server-calculated totals; posted history retained |
| PaymentService | post/refund payment | lock invoice; prevent overpayment; append correction record |
| AdmissionService | admit, assign bed, transfer and discharge | lock bed/admission; interval constraints enforce no overlap |
| GoodsReceiptService | post medication receipt | receipt, batch, stock ledger and balance in one transaction |
| JournalService | create, post and reverse journal | database trigger checks balanced entry and open period |
| OutboxPublisher | publish committed outbox records to JMS | retry with backoff and claim rows safely |
| NotificationConsumer | consume notification events into user inbox | idempotent by event/idempotency key |
| MetricAggregationService | calculate periodic operational snapshots | read-only aggregation; store formula/version metadata |
| DecisionCardService | evaluate thresholds and create explainable cards | deduplicate per branch/key/window |
| AuditService | record security and sensitive business events | avoid storing clinical note contents or credentials |

## 5. REST resource rules

- Prefix endpoints with `/api/v1`.
- Use plural nouns for resources and explicit action endpoints only for business transitions (`POST /admissions/{id}/transfer`, `POST /invoices/{id}/payments`).
- Use request/response DTOs; validate request DTOs with Bean Validation.
- Return `ProblemDetail`-style consistent error bodies (status, code, title, detail, traceId, fieldErrors).
- Never return password hashes, internal authorization data, or raw audit payloads to broad roles.
- Use pagination/filtering for patient search, appointments, orders, audit events and reports.
- Apply branch scope and resource scope checks in the service/security layer for every sensitive request.

## 6. Required integration tests

1. Appointment overlap for one doctor fails; adjacent non-overlapping appointments pass.
2. Encounter cannot be attached to an appointment for another patient.
3. Released lab result without review fails.
4. Two concurrent dispense requests cannot overdraw a medication batch.
5. If dispense stock movement insertion fails, the dispense and balance changes roll back.
6. Duplicate outbox delivery creates one notification only.
7. Two overlapping bed assignments for the same bed fail.
8. One admission cannot have simultaneous bed assignments.
9. Posted receipt quantities cannot exceed ordered quantities across multiple receipts.
10. Journal with unbalanced lines cannot be posted; balanced journal in closed period cannot be posted.
11. Posted journal lines cannot be updated or deleted.
12. Unauthorized role or out-of-scope patient request returns 403 and is audited without leaking clinical details.
