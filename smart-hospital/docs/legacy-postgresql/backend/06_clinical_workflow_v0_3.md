# Smart Hospital — Clinical Workflow Increment 0.3

## Scope
This increment implements the first physician-led encounter workflow on top of V001–V004. It is an academic development slice and is not approved for clinical deployment.

## Workflow

1. A receptionist registers the synthetic patient in the main branch.
2. A receptionist books an appointment for an active doctor and department.
3. A receptionist checks the patient in. The appointment row is locked with `PESSIMISTIC_WRITE` while changing from `BOOKED`/`CONFIRMED` to `CHECKED_IN`.
4. The attending doctor opens an encounter. A linked appointment must match the patient, doctor, department and branch, and must be checked in. Walk-in encounters may omit the appointment.
5. The nurse or attending doctor records vital signs. Blood pressure values must be supplied as a pair, and systolic must exceed diastolic. At least one measurement is required.
6. Doctors author clinical notes of supported types. Nurses may author `PROGRESS` notes only. Only the author can sign their draft.
7. The attending doctor records diagnosis catalogue entries. No more than one primary diagnosis is allowed. A diagnosis status may subsequently be updated by the attending doctor.
8. Signed notes are immutable. The attending doctor creates an amendment draft referencing the signed original and signs the amendment. The original is preserved.
9. The attending doctor completes the encounter after all drafts have been signed. A linked checked-in appointment moves to `COMPLETED` in the same JTA transaction.

## Authorization matrix

| Operation | Hospital admin | Receptionist | Attending doctor | Nurse |
|---|---:|---:|---:|---:|
| Appointment check-in | Yes | Yes | No | No |
| Open encounter | No | No | Yes | No |
| Read encounter / patient encounter history | No | No | Yes | Yes |
| Record vital signs | No | No | Yes | Yes |
| Create clinical note | No | No | Yes | `PROGRESS` only |
| Sign own note | No | No | Yes | Own `PROGRESS` note only |
| Amend signed note | No | No | Yes | No |
| Add/update diagnosis | No | No | Yes | No |
| Complete encounter | No | No | Yes | No |

The backend also verifies that the caller has access to the branch and patient, that the staff profile is active in the encounter branch, and that doctor-only operations are performed by the attending doctor. These controls are not a substitute for a production care-relationship authorization model.

## API root

`http://localhost:8080/smart-hospital-api/api/v1`

All examples below use synthetic development data. Use HTTPS outside isolated localhost development. The GlassFish `file` realm Basic authentication is only a local development mechanism.

## Synthetic workflow example

### 1. Register patient as receptionist

`POST /patients`

```json
{
  "mrn": "DEMO-MRN-0001",
  "firstName": "Demo",
  "lastName": "Patient",
  "sex": "UNKNOWN",
  "preferredLanguage": "ar"
}
```

Store the returned `id` as `PATIENT_ID`.

### 2. Book appointment as receptionist

`POST /appointments`

```json
{
  "branchId": "10000000-0000-4000-8000-000000000001",
  "patientId": "PATIENT_ID",
  "doctorStaffId": "30000000-0000-4000-8000-000000000001",
  "departmentId": "20000000-0000-4000-8000-000000000001",
  "startsAt": "2030-01-15T09:00:00Z",
  "endsAt": "2030-01-15T09:20:00Z",
  "reason": "Synthetic academic test appointment"
}
```

Replace the example future timestamps with an available interval when executing the test. Store the returned `id` as `APPOINTMENT_ID`.

### 3. Check in as receptionist

`POST /appointments/APPOINTMENT_ID/check-in`

No request body is required.

### 4. Open encounter as the attending doctor

`POST /encounters`

```json
{
  "branchId": "10000000-0000-4000-8000-000000000001",
  "patientId": "PATIENT_ID",
  "departmentId": "20000000-0000-4000-8000-000000000001",
  "appointmentId": "APPOINTMENT_ID",
  "encounterType": "OUTPATIENT",
  "chiefComplaint": "Synthetic complaint for software testing"
}
```

Store the returned `id` as `ENCOUNTER_ID`.

### 5. Record vital signs as nurse or attending doctor

`POST /encounters/ENCOUNTER_ID/vitals`

```json
{
  "temperatureC": 36.8,
  "systolicMmhg": 118,
  "diastolicMmhg": 76,
  "pulseBpm": 72,
  "respiratoryRate": 16,
  "oxygenSaturation": 98.0,
  "weightKg": 68.5,
  "heightCm": 172.0,
  "notes": "Synthetic vital-sign data"
}
```

### 6. Create and sign a clinical note as doctor

`POST /encounters/ENCOUNTER_ID/notes`

```json
{
  "noteType": "ASSESSMENT",
  "noteText": "Synthetic assessment text for application testing only."
}
```

Store returned `id` as `NOTE_ID`, then call:

`POST /encounters/ENCOUNTER_ID/notes/NOTE_ID/sign`

No request body is required.

### 7. Search the synthetic diagnosis catalogue

`GET /diagnoses?q=TEST-DIAG&limit=10`

The seeded `DEMO` catalogue is a software test fixture only. Its entries are not ICD codes and must not be used in clinical decisions.

### 8. Add diagnosis as attending doctor

`POST /encounters/ENCOUNTER_ID/diagnoses`

```json
{
  "diagnosisId": "DIAGNOSIS_ID_FROM_SEARCH",
  "primary": true,
  "clinicalStatus": "PROVISIONAL"
}
```

### 9. Complete encounter as attending doctor

`POST /encounters/ENCOUNTER_ID/complete`

All draft notes must be signed first. If there is a linked appointment it must still be `CHECKED_IN`; encounter and appointment completion are committed together.

## Important database guarantees

- An encounter's patient/branch pair must exist in `patient_branch_access` and be active at encounter creation.
- The encounter doctor and department must be active and belong to the same branch; the doctor must be assigned to that department.
- A linked appointment must match the patient, doctor, department and branch and be checked in.
- Vital observations are append-only; corrections are new measurements.
- Signed and amended clinical notes cannot be updated or deleted. Draft notes may be changed before signing; amendments are separate notes.
- Diagnosis identity cannot be changed or deleted; the clinical status may be updated.
- The database partial unique index allows at most one primary diagnosis per encounter.

## Not included yet

Laboratory orders/results, medication prescriptions/dispensing, clinical decision support, real terminology integration, patient portal access, external identity provider, and production-grade care-relationship access control are later increments.
