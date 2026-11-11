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

$schema = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c "SELECT CASE WHEN to_regnamespace('hospital') IS NULL THEN 'NO' ELSE 'YES' END;"
if ($LASTEXITCODE -ne 0 -or $schema.Trim() -ne "YES") {
    throw "The target database does not contain the hospital schema."
}
$tableCount = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c "SELECT count(*) FROM pg_tables WHERE schemaname='hospital';"
if ($LASTEXITCODE -ne 0 -or $tableCount.Trim() -ne "63") {
    throw "Expected the v0.8 baseline with 63 hospital tables. Found '$($tableCount.Trim())'. Stop and identify the actual migration version."
}
$v008Exists = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c "SELECT CASE WHEN to_regclass('hospital.inventory_reorder_policies') IS NULL THEN 'NO' ELSE 'YES' END;"
if ($LASTEXITCODE -ne 0 -or $v008Exists.Trim() -eq "YES") {
    throw "V008 appears to be already applied or the schema is inconsistent. Do not rerun this upgrade."
}

$migration = Join-Path $PSScriptRoot "migrations\V008__operational_analytics_and_reorder_policies.sql"
$validation = Join-Path $PSScriptRoot "validation\validate_schema_v008.sql"
$seed = Join-Path $PSScriptRoot "seed\V008__development_analytics_fixtures.sql"
$seedValidation = Join-Path $PSScriptRoot "validation\validate_analytics_seed.sql"
foreach ($file in @($migration, $validation, $seed, $seedValidation)) {
    if (-not (Test-Path $file)) { throw "Required V008 file is missing: $file" }
}

Write-Host "Applying Smart Hospital V008 operational analytics schema..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $migration
if ($LASTEXITCODE -ne 0) { throw "V008 migration failed. Inspect PostgreSQL output before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validation
if ($LASTEXITCODE -ne 0) { throw "V008 schema validation failed." }
Write-Host "Loading synthetic analytics and reorder-point fixtures..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seed
if ($LASTEXITCODE -ne 0) { throw "V008 development fixtures failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedValidation
if ($LASTEXITCODE -ne 0) { throw "V008 analytics fixture validation failed." }
Write-Host "Smart Hospital v0.9 database upgrade completed: 64 tables, analytics metrics and synthetic reorder policies."
