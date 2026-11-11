# Backend Increment 0.1 — Engineering Notes

## Scope implemented
The first deployable slice is intentionally small: organization lookup, patient registry and appointment creation. This gives the application a running vertical slice from React-ready REST endpoint to EJB transaction to JPA to PostgreSQL.

## Entity mappings
- `HospitalBranch` → `hospital.hospital_branches`
- `Department` → `hospital.departments`
- `StaffMember` → `hospital.staff_members`
- `DoctorProfile` → `hospital.doctor_profiles` (`@MapsId` one-to-one primary-key association)
- `Patient` → `hospital.patients`
- `PatientContact` → `hospital.patient_contacts`
- `PatientAllergy` → `hospital.patient_allergies`
- `Appointment` → `hospital.appointments`

All IDs are Java UUIDs. JPA does not create or alter the schema. The SQL migrations remain authoritative.

## REST endpoints
| Method | Path | Roles | Behavior |
|---|---|---|---|
| GET | `/api/v1/health` | PermitAll | API process status only |
| GET | `/api/v1/organization/branches` | hospital roles | Active branches |
| GET | `/api/v1/organization/branches/{branchId}/departments` | hospital roles | Active departments in branch |
| POST | `/api/v1/patients` | HOSPITAL_ADMIN, RECEPTIONIST | Register patient, contacts, allergies |
| GET | `/api/v1/patients` | HOSPITAL_ADMIN, RECEPTIONIST, DOCTOR, NURSE | Paginated patient summary search |
| GET | `/api/v1/patients/{patientId}` | HOSPITAL_ADMIN, RECEPTIONIST, DOCTOR, NURSE | Patient detail including contacts and allergies |
| PUT | `/api/v1/patients/{patientId}` | HOSPITAL_ADMIN, RECEPTIONIST | Update demographic data only |
| POST | `/api/v1/appointments` | HOSPITAL_ADMIN, RECEPTIONIST | Book appointment |
| GET | `/api/v1/appointments/{appointmentId}` | HOSPITAL_ADMIN, RECEPTIONIST, DOCTOR, NURSE | Appointment lookup |
| GET | `/api/v1/appointments/{appointmentId}` | HOSPITAL_ADMIN, RECEPTIONIST, DOCTOR, NURSE | Appointment lookup |

## Core design details
- DTOs are the REST boundary; JPA entities are not serialized directly.
- Patient search uses a summary DTO, preventing collection-driven N+1 reads of contacts and allergies.
- Patient creation validates MRN before insert and still relies on the database unique constraint as the race-condition guard.
- Appointment creation validates branch/doctor/department consistency and flushes inside the service to catch the PostgreSQL exclusion violation before returning.
- PostgreSQL SQLSTATE `23P01` is translated to an HTTP 409 conflict.
- EJB container-managed transactions are used for write operations.
- Minimal audit events are written in the same transaction for patient registration, search, view, demographic update and appointment booking/view. The container principal is stored in audit JSONB metadata until database-backed user mapping is implemented.
- Patient actions create audit records in the same database transaction. The GlassFish file-realm principal is recorded in audit JSONB metadata until the DB identity integration is completed.

## Important security gap
Role-based authorization is in place as a first layer. Fine-grained authorization must be added before using real patient information: branch scope, assigned-care relationship, encounter scope, emergency access, access denial audit, database-backed identity mapping and production-grade session/token handling.

## Local test data
The seed script creates one synthetic branch, six departments, one synthetic doctor and role catalog entries. It contains no real person data and no reusable passwords.
