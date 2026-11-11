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
  WHEN to_regclass('hospital.patients') IS NULL OR to_regclass('hospital.journal_entries') IS NULL THEN 'NOT_V03'
  WHEN (SELECT count(*) FROM pg_tables WHERE schemaname = 'hospital') <> 61 THEN 'NOT_V03'
  WHEN to_regclass('hospital.patient_branch_access') IS NOT NULL THEN 'ALREADY_V04'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the database. Check database credentials and connectivity." }
if ($state.Trim() -ne "READY") {
    throw "Expected a V0.3 schema (V001 + V002 = 61 hospital tables, without V003). Detected '$($state.Trim())'. No upgrade was applied."
}

$base = $PSScriptRoot
$v002Validation = Join-Path $base "validation\validate_schema_v002.sql"
$v003Migration = Join-Path $base "migrations\V003__patient_branch_access.sql"
$referenceSeed = Join-Path $base "seed\V001__development_reference_data.sql"
$accountSeed = Join-Path $base "seed\V002__development_accounts.sql"
$v003Validation = Join-Path $base "validation\validate_schema_v003.sql"
$seedValidation = Join-Path $base "validation\validate_development_seed.sql"
$files = @($v002Validation, $v003Migration, $referenceSeed, $accountSeed, $v003Validation, $seedValidation)
foreach ($file in $files) { if (-not (Test-Path $file)) { throw "Required file missing: $file" } }

Write-Host "Validating the existing V0.3 schema..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v002Validation
if ($LASTEXITCODE -ne 0) { throw "The existing V0.3 schema did not pass validation. No V003 upgrade was applied." }

Write-Host "Applying V003 patient branch access..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v003Migration
if ($LASTEXITCODE -ne 0) { throw "V003 failed. Review the PostgreSQL error; do not rerun V003 without inspecting the state." }

Write-Host "Ensuring synthetic reference data and local account mappings..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $referenceSeed
if ($LASTEXITCODE -ne 0) { throw "Reference-data seed failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $accountSeed
if ($LASTEXITCODE -ne 0) { throw "Development account seed failed." }

Write-Host "Validating upgraded schema and demo accounts..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v003Validation
if ($LASTEXITCODE -ne 0) { throw "V003 schema validation failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedValidation
if ($LASTEXITCODE -ne 0) { throw "Development seed validation failed." }

Write-Host "Smart Hospital database upgraded from V0.3 to V0.4."
