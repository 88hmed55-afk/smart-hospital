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
  WHEN to_regclass('hospital.encounters') IS NULL OR to_regclass('hospital.patient_branch_access') IS NULL THEN 'NOT_V05'
  WHEN (SELECT count(*) FROM pg_tables WHERE schemaname='hospital') <> 62 THEN 'NOT_V05'
  WHEN to_regclass('hospital.encounter_care_team') IS NOT NULL THEN 'ALREADY_V06'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the database. Check database credentials and connectivity." }
if ($state.Trim() -ne "READY") {
    throw "Expected a v0.5 schema with 62 hospital tables (V001-V004). Detected '$($state.Trim())'. No upgrade was applied."
}

$preflight = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c @"
SELECT CASE
  WHEN EXISTS (SELECT 1 FROM hospital.lab_order_items GROUP BY lab_order_id, lab_test_id HAVING count(*) > 1) THEN 'DUPLICATE_LAB_ITEMS'
  WHEN EXISTS (SELECT 1 FROM hospital.lab_results WHERE released_at IS NOT NULL AND (reviewed_by IS NULL OR reviewed_at IS NULL)) THEN 'INVALID_RELEASED_RESULTS'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not run V005 data preflight checks. No upgrade was applied." }
if ($preflight.Trim() -ne "READY") { throw "V005 preflight detected '$($preflight.Trim())'. Reconcile existing data before upgrading. No migration was applied." }

$base = $PSScriptRoot
$migration = Join-Path $base "migrations\V005__care_team_and_laboratory_integrity.sql"
$seed = Join-Path $base "seed\V005__development_laboratory_accounts_and_catalog.sql"
$schemaValidation = Join-Path $base "validation\validate_schema_v005.sql"
$seedValidation = Join-Path $base "validation\validate_laboratory_demo_seed.sql"
foreach ($file in @($migration, $seed, $schemaValidation, $seedValidation)) {
    if (-not (Test-Path $file)) { throw "Required file missing: $file" }
}

Write-Host "Applying V005 care-team and laboratory safeguards..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $migration
if ($LASTEXITCODE -ne 0) { throw "V005 migration failed. Inspect the PostgreSQL error before retrying." }

Write-Host "Loading synthetic laboratory accounts and test catalogue..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seed
if ($LASTEXITCODE -ne 0) { throw "Laboratory development seed failed." }

Write-Host "Validating v0.6 schema and laboratory fixtures..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $schemaValidation
if ($LASTEXITCODE -ne 0) { throw "V005 schema validation failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedValidation
if ($LASTEXITCODE -ne 0) { throw "Laboratory demo seed validation failed." }

Write-Host "Smart Hospital database upgraded from v0.5 to v0.6."
