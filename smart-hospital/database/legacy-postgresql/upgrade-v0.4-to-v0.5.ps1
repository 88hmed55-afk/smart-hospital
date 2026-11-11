param(
    [string]$Database = "smart_hospital",
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$Username = "smart_hospital"
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql was not found. Install PostgreSQL command-line tools and ensure psql.exe is on PATH."
}
$state = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c @"
SELECT CASE
  WHEN to_regclass('hospital.encounters') IS NULL OR to_regclass('hospital.patient_branch_access') IS NULL THEN 'NOT_V04'
  WHEN (SELECT count(*) FROM pg_tables WHERE schemaname='hospital') <> 62 THEN 'NOT_V04'
  WHEN EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_encounters_validate_scope' AND NOT tgisinternal) THEN 'ALREADY_V05'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the database. Check database credentials and connectivity." }
if ($state.Trim() -ne "READY") { throw "Expected a V0.4 schema with 62 hospital tables and no V004 triggers. Detected '$($state.Trim())'. No upgrade was applied." }
$orphanEncounters = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c @"
SELECT count(*)
  FROM hospital.encounters e
  LEFT JOIN hospital.patient_branch_access pba
    ON pba.patient_id=e.patient_id AND pba.branch_id=e.branch_id
 WHERE pba.patient_id IS NULL;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not check encounter branch grants. No upgrade was applied." }
if ([int]$orphanEncounters.Trim() -gt 0) {
    throw "V004 preflight found encounters without patient_branch_access rows. Add the correct patient/branch grant for each existing encounter, then rerun this upgrade. No migration was applied."
}
$migration = Join-Path $PSScriptRoot "migrations\V004__clinical_workflow_integrity.sql"
$seed = Join-Path $PSScriptRoot "seed\V003__synthetic_diagnosis_catalog.sql"
$validation = Join-Path $PSScriptRoot "validation\validate_schema_v004.sql"
$clinicalSeedValidation = Join-Path $PSScriptRoot "validation\validate_clinical_demo_seed.sql"
$seedNurse = Join-Path $PSScriptRoot "seed\V004__development_nurse_account.sql"
foreach ($file in @($migration,$seed,$seedNurse,$validation,$clinicalSeedValidation)) { if (-not (Test-Path $file)) { throw "Required file missing: $file" } }
Write-Host "Applying V004 clinical workflow integrity..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $migration
if ($LASTEXITCODE -ne 0) { throw "V004 failed. Inspect PostgreSQL errors before retrying." }
Write-Host "Loading synthetic diagnosis catalog entries..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seed
if ($LASTEXITCODE -ne 0) { throw "Synthetic diagnosis seed failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedNurse
if ($LASTEXITCODE -ne 0) { throw "Synthetic nurse account seed failed." }
Write-Host "Validating V004..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validation
if ($LASTEXITCODE -ne 0) { throw "V004 validation failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $clinicalSeedValidation
if ($LASTEXITCODE -ne 0) { throw "Clinical demo seed validation failed." }
Write-Host "Smart Hospital upgraded from V0.4 to V0.5."
