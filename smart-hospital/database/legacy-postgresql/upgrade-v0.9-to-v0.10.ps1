param(
    [string]$Database = "smart_hospital",
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$Username = "postgres"
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) { throw "psql was not found. Install PostgreSQL command-line tools and ensure psql.exe is on PATH." }
$schema = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c "SELECT CASE WHEN to_regnamespace('hospital') IS NULL THEN 'NO' ELSE 'YES' END;"
if ($LASTEXITCODE -ne 0 -or $schema.Trim() -ne "YES") { throw "The target database does not contain the hospital schema." }
$tableCount = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c "SELECT count(*) FROM pg_tables WHERE schemaname='hospital';"
if ($LASTEXITCODE -ne 0 -or $tableCount.Trim() -ne "64") { throw "Expected v0.9 baseline with 64 tables. Found '$($tableCount.Trim())'. Stop and identify the actual migration version." }
$hasColumn = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='hospital' AND table_name='purchase_order_items' AND column_name='received_quantity') THEN 'YES' ELSE 'NO' END;"
if ($LASTEXITCODE -ne 0 -or $hasColumn.Trim() -eq "YES") { throw "V009 appears to be already applied or the schema is inconsistent. Do not rerun this upgrade." }
$migration = Join-Path $PSScriptRoot "migrations\V009__procurement_receiving_integrity.sql"
$validation = Join-Path $PSScriptRoot "validation\validate_schema_v009.sql"
$seed = Join-Path $PSScriptRoot "seed\V009__development_supplier_fixture.sql"
$seedValidation = Join-Path $PSScriptRoot "validation\validate_procurement_demo_seed.sql"
foreach ($file in @($migration, $validation, $seed, $seedValidation)) { if (-not (Test-Path $file)) { throw "Required V009 file is missing: $file" } }
Write-Host "Applying Smart Hospital V009 procurement receiving safeguards..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $migration
if ($LASTEXITCODE -ne 0) { throw "V009 migration failed. Inspect PostgreSQL output before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validation
if ($LASTEXITCODE -ne 0) { throw "V009 schema validation failed." }
Write-Host "Loading synthetic supplier, main-store location and inventory-officer mapping..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seed
if ($LASTEXITCODE -ne 0) { throw "V009 development fixtures failed. Inspect the SQL error before retrying the seed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedValidation
if ($LASTEXITCODE -ne 0) { throw "V009 procurement fixture validation failed." }
Write-Host "Smart Hospital v0.10 database upgrade completed. V009 safeguards and synthetic procurement fixtures are installed."
