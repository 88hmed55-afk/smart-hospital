param(
    [string]$Database = "smart_hospital",
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$Username = "postgres"
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) { throw "psql was not found. Install PostgreSQL command-line tools and ensure psql.exe is on PATH." }

$state = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c @"
SELECT CASE
  WHEN to_regnamespace('hospital') IS NULL THEN 'NO_HOSPITAL_SCHEMA'
  WHEN (SELECT count(*) FROM pg_tables WHERE schemaname='hospital') <> 64 THEN 'UNEXPECTED_TABLE_COUNT'
  WHEN EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_validate_journal_line_account' AND NOT tgisinternal) THEN 'ALREADY_V010'
  WHEN to_regclass('hospital.chart_of_accounts') IS NULL OR to_regclass('hospital.journal_entries') IS NULL THEN 'ACCOUNTING_BASELINE_MISSING'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the database. Check credentials and connectivity." }
if ($state.Trim() -ne "READY") { throw "Expected Smart Hospital v0.11 database (V001-V009, 64 tables, accounting foundation present). Detected '$($state.Trim())'. No upgrade was applied." }

$migration = Join-Path $PSScriptRoot "migrations\V010__accounting_posting_guards.sql"
$seed = Join-Path $PSScriptRoot "seed\V010__development_chart_of_accounts.sql"
$validation = Join-Path $PSScriptRoot "validation\validate_accounting_v010.sql"
foreach ($file in @($migration, $seed, $validation)) { if (-not (Test-Path $file)) { throw "Required V010 file is missing: $file" } }

Write-Host "Applying V010 accounting integrity guard..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $migration
if ($LASTEXITCODE -ne 0) { throw "V010 migration failed. Inspect PostgreSQL output before retrying." }
Write-Host "Loading default chart of accounts and open fiscal period for active branches..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seed
if ($LASTEXITCODE -ne 0) { throw "V010 development accounting seed failed. Inspect the SQL output before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validation
if ($LASTEXITCODE -ne 0) { throw "V010 accounting validation failed." }
Write-Host "Smart Hospital v0.12 accounting foundation installed. Existing invoices and payments remain unchanged; new invoice issuance and payments will generate journals."
