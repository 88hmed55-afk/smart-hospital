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
  WHEN to_regclass('hospital.dispenses') IS NULL OR to_regclass('hospital.bed_assignments') IS NULL THEN 'NOT_V07'
  WHEN (SELECT count(*) FROM pg_tables WHERE schemaname='hospital') <> 63 THEN 'NOT_V07'
  WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_invoice_item_line_math') THEN 'ALREADY_V08'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the database. Check database credentials and connectivity." }
if ($state.Trim() -ne "READY") {
    throw "Expected a v0.7 database (V001-V006, 63 tables, no V007 billing constraints). Detected '$($state.Trim())'. No upgrade was applied."
}

$preflight = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c @"
SELECT CASE
  WHEN EXISTS (SELECT 1 FROM hospital.invoice_items WHERE line_total <> round(quantity * unit_price - line_discount, 2) OR line_discount > quantity * unit_price) THEN 'INVOICE_LINE_ARITHMETIC_MISMATCH'
  WHEN EXISTS (SELECT 1 FROM hospital.payments p JOIN hospital.invoices i ON i.id=p.invoice_id WHERE p.currency_code <> i.currency_code) THEN 'PAYMENT_CURRENCY_MISMATCH'
  ELSE 'READY'
END;
"@
if ($LASTEXITCODE -ne 0) { throw "Could not run V007 financial preflight checks. No upgrade was applied." }
if ($preflight.Trim() -ne "READY") { throw "V007 preflight detected '$($preflight.Trim())'. Reconcile existing financial data before upgrading. No migration was applied." }

$base = $PSScriptRoot
$migration = Join-Path $base "migrations\V007__billing_and_admission_integrity.sql"
$schemaValidation = Join-Path $base "validation\validate_schema_v007.sql"
$seed = Join-Path $base "seed\V007__development_billing_admission_fixtures.sql"
$seedValidation = Join-Path $base "validation\validate_billing_admission_seed.sql"
foreach ($file in @($migration, $schemaValidation, $seed, $seedValidation)) {
    if (-not (Test-Path $file)) { throw "Required file missing: $file" }
}

Write-Host "Applying V007 billing and admission integrity..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $migration
if ($LASTEXITCODE -ne 0) { throw "V007 migration failed. Inspect the PostgreSQL error before retrying." }
Write-Host "Validating V007..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $schemaValidation
if ($LASTEXITCODE -ne 0) { throw "V007 schema validation failed." }
Write-Host "Loading synthetic billing and admission fixtures..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seed
if ($LASTEXITCODE -ne 0) { throw "V007 fixtures failed. Inspect the seed error before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedValidation
if ($LASTEXITCODE -ne 0) { throw "V007 fixture validation failed." }
Write-Host "Smart Hospital database upgraded from v0.7 to v0.8."
