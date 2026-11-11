param(
    [string]$Database = "smart_hospital",
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$Username = "postgres"
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql was not found. Install PostgreSQL command-line tools and ensure psql.exe is on PATH."
}

$state = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c @"
SELECT CASE
  WHEN to_regnamespace('hospital') IS NULL THEN 'NO_HOSPITAL_SCHEMA'
  WHEN (SELECT count(*) FROM pg_tables WHERE schemaname='hospital') <> 64 THEN 'UNEXPECTED_TABLE_COUNT'
  WHEN to_regprocedure('hospital.reject_audit_log_mutation()') IS NOT NULL THEN 'ALREADY_V011'
  WHEN NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='hospital.audit_logs'::regclass AND tgname='trg_validate_journal_line_account' AND NOT tgisinternal) THEN 'ACCOUNTING_BASELINE_MISSING'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the database. Check credentials and connectivity." }
if ($state.Trim() -ne "READY") {
    throw "Expected Smart Hospital v0.13 database (V001–V010, 64 tables, accounting guards present). Detected '$($state.Trim())'. No upgrade was applied."
}

$migration = Join-Path $PSScriptRoot "migrations\V011__audit_log_append_only.sql"
$validation = Join-Path $PSScriptRoot "validation\validate_governance_v011.sql"
foreach ($file in @($migration, $validation)) {
    if (-not (Test-Path $file)) { throw "Required V011 file is missing: $file" }
}

Write-Host "Applying V011 append-only audit protection..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $migration
if ($LASTEXITCODE -ne 0) { throw "V011 migration failed. Inspect PostgreSQL output before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validation
if ($LASTEXITCODE -ne 0) { throw "V011 governance validation failed." }
Write-Host "Smart Hospital v0.14 governance protections installed. Audit history is now append-only."
