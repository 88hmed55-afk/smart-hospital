# REST API smoke examples — backend 0.1

Base URL:

```powershell
$baseUrl = "http://localhost:8080/smart-hospital-api/api/v1"
$credential = Get-Credential  # enter a local GlassFish file-realm username/password
```

For local development only, use an account created in the GlassFish `file` realm. On PowerShell, initialize these variables once. Use HTTPS outside localhost.

## 1. Health

```powershell
Invoke-RestMethod -Method Get -Uri "$baseUrl/health" -Credential $credential
```

Expected shape: `status = UP`, application name and timestamp. This checks the API process only, not its database or JMS readiness.

## 2. Branches and departments

```powershell
$branches = Invoke-RestMethod -Method Get -Uri "$baseUrl/organization/branches" -Credential $credential
$branchId = $branches[0].id
Invoke-RestMethod -Method Get -Uri "$baseUrl/organization/branches/$branchId/departments" -Credential $credential
```

The seed script creates a synthetic main branch, general clinic, laboratory, pharmacy, emergency, inpatient and finance departments, plus one synthetic demo doctor profile.

## 3. Register a synthetic patient

```powershell
$patientBody = @{
  mrn = "DEMO-0001"
  firstName = "مريض"
  middleName = "تجريبي"
  lastName = "واحد"
  dateOfBirth = "1998-05-12"
  sex = "NOT_RECORDED"
  bloodGroup = "O+"
  primaryPhone = "0000000000"
  preferredLanguage = "ar"
  contacts = @(
    @{
      contactType = "EMERGENCY"
      contactName = "جهة اتصال تجريبية"
      relationship = "قريب"
      phoneNumber = "0000000001"
      primary = $true
    }
  )
  allergies = @(
    @{
      allergenName = "DEMO: no confirmed allergies"
      severity = "UNKNOWN"
      verificationStatus = "UNVERIFIED"
    }
  )
} | ConvertTo-Json -Depth 8

$patient = Invoke-RestMethod -Method Post -Uri "$baseUrl/patients" -Credential $credential -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($patientBody))
$patient.id
```

Use only synthetic test records. The example allergy row is explicitly unverified and is not a clinical assertion.

## 4. Search patients

```powershell
Invoke-RestMethod -Method Get -Uri "$baseUrl/patients?q=DEMO-0001&offset=0&limit=20" -Credential $credential
```

Search returns summary DTOs, not the full patient contact/allergy collections.

## 5. Book an appointment

Use the synthetic demo doctor's staff ID seeded in `V001__development_reference_data.sql`:

```powershell
$appointmentBody = @{
  branchId = "10000000-0000-4000-8000-000000000001"
  patientId = $patient.id
  doctorStaffId = "30000000-0000-4000-8000-000000000001"
  departmentId = "20000000-0000-4000-8000-000000000001"
  startsAt = "2026-12-01T08:00:00Z"
  endsAt = "2026-12-01T08:20:00Z"
  reason = "Synthetic appointment test"
} | ConvertTo-Json

$appointment = Invoke-RestMethod -Method Post -Uri "$baseUrl/appointments" -Credential $credential -ContentType "application/json" -Body $appointmentBody
$appointment.id
```

A second overlapping appointment for the same doctor should return HTTP 409 due to the PostgreSQL exclusion constraint. A time interval that touches the previous interval boundary is allowed because intervals use `[start, end)`.
