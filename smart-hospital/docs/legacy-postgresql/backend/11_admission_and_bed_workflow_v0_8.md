# Admission and Bed Management Workflow — v0.8

## Scope
This increment implements inpatient admission from an active `INPATIENT` or `EMERGENCY` encounter, bed availability, initial bed allocation, bed transfer and discharge. It uses the existing `wards`, `rooms`, `beds`, `admissions` and `bed_assignments` tables created in V002; no new tables are added in V007.

## API
- `POST /api/v1/admissions`
- `GET /api/v1/admissions/active?branchId={uuid}&limit=50`
- `GET /api/v1/admissions/beds?branchId={uuid}&wardId={uuid}&availableOnly=true`
- `POST /api/v1/admissions/{admissionId}/bed-assignments`
- `POST /api/v1/admissions/{admissionId}/bed-transfers`
- `POST /api/v1/admissions/{admissionId}/discharge`

## Admission creation
The caller must hold one of `HOSPITAL_ADMIN`, `HOSPITAL_MANAGER`, `DOCTOR` or `RECEPTIONIST`. A doctor may initiate admission only for an encounter assigned to that doctor. The encounter must be active, be of type `INPATIENT` or `EMERGENCY`, and belong to the requested branch. Patient-to-branch access is checked before creating the admission.

The patient and attending physician are derived from the encounter; the client cannot substitute a different patient or attending doctor. The admission number is generated server-side. The database's existing partial unique index prevents more than one active admission for the same patient and branch. An optional initial bed can be assigned in the same JTA transaction.

## Bed assignment and transfer
Bed selection verifies the bed is active, operationally `AVAILABLE`, and in the same branch. The bed row is pessimistically locked before checking active occupancy. PostgreSQL exclusion constraints in V002 prevent overlapping assignment intervals for the same bed and overlapping bed intervals for one admission. The current assignment is ended at the same instant the next assignment starts; intervals use `[start, end)` semantics.

Nurses and doctors must be in the active care team for the encounter before they can assign or transfer a bed. Managers and hospital administrators can perform these operations within their branch scope. A transfer releases the current assignment and creates the next assignment in one transaction.

## Discharge
Only `HOSPITAL_ADMIN`, `HOSPITAL_MANAGER` and `DOCTOR` can discharge. A doctor must be the attending doctor. The service records a supported discharge disposition and optional summary, closes the admission, and releases any active bed assignment in the same transaction. It does not automatically close or rewrite the clinical encounter; clinical documentation remains a separate workflow.

## Security and privacy
- Every request checks the caller's branch access.
- Patient record access is checked against an active `patient_branch_access` grant.
- Doctor listing is limited to the doctor's own admissions.
- Nurse listing is limited to encounters where the nurse has an active care-team assignment.
- The response contains operational patient identifiers required by authorized care users; it does not expose contact details or full medical history.

## Deliberate limitations
- Ward/room/bed master-data CRUD screens are not included; this increment consumes preconfigured data.
- Discharge planning, transfer between hospitals, bed cleaning workflow and isolation-room compatibility rules are future work.
- No nurse-led discharge and no automatic clinical encounter completion are implemented.
- The database schema is branch-aware, but the development fixture contains one branch only.
