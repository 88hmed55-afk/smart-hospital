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
  WHEN to_regclass('hospital.encounter_care_team') IS NULL OR to_regclass('hospital.dispenses') IS NULL THEN 'NOT_V06'
  WHEN (SELECT count(*) FROM pg_tables WHERE schemaname='hospital') <> 63 THEN 'NOT_V06'
  WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='hospital' AND table_name='dispenses' AND column_name='idempotency_key') THEN 'ALREADY_V07'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the database. Check database credentials and connectivity." }
if ($state.Trim() -ne "READY") {
    throw "Expected v0.6 (V001-V005, 63 tables, no dispense idempotency columns). Detected '$($state.Trim())'. No upgrade was applied."
}

$preflight = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c @"
SELECT CASE
  WHEN EXISTS (SELECT 1 FROM hospital.prescription_items GROUP BY prescription_id, medication_id HAVING count(*) > 1) THEN 'DUPLICATE_PRESCRIPTION_MEDICATIONS'
  WHEN EXISTS (SELECT 1 FROM hospital.stock_movements WHERE reference_type='DISPENSE' AND movement_type='DISPENSE' GROUP BY reference_id, batch_id HAVING count(*) > 1) THEN 'DUPLICATE_DISPENSE_STOCK_MOVEMENTS'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not run V006 data preflight checks. No upgrade was applied." }
if ($preflight.Trim() -ne "READY") { throw "V006 preflight detected '$($preflight.Trim())'. Reconcile existing data before upgrading. No migration was applied." }

$base = $PSScriptRoot
$migration = Join-Path $base "migrations\V006__pharmacy_dispensing_integrity.sql"
$schemaValidation = Join-Path $base "validation\validate_schema_v006.sql"
$seed = Join-Path $base "seed\V006__development_pharmacy_accounts_and_stock.sql"
$seedValidation = Join-Path $base "validation\validate_pharmacy_demo_seed.sql"
foreach ($file in @($migration, $schemaValidation, $seed, $seedValidation)) {
    if (-not (Test-Path $file)) { throw "Required file missing: $file" }
}

Write-Host "Applying V006 pharmacy dispensing safeguards..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $migration
if ($LASTEXITCODE -ne 0) { throw "V006 migration failed. Inspect the PostgreSQL error before retrying." }

Write-Host "Validating V006 schema..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $schemaValidation
if ($LASTEXITCODE -ne 0) { throw "V006 schema validation failed." }

Write-Host "Loading synthetic pharmacist and pharmacy stock fixtures..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seed
if ($LASTEXITCODE -ne 0) { throw "Synthetic pharmacy seed failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedValidation
if ($LASTEXITCODE -ne 0) { throw "Pharmacy seed validation failed." }

Write-Host "Smart Hospital database upgraded from v0.6 to v0.7."
