param(
    [string]$Database = "smart_hospital_dev",
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$Username = "postgres"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql was not found. Install PostgreSQL command-line tools and ensure psql.exe is on PATH."
}

$schemaExists = & psql -X -A -t -h $HostName -p $Port -U $Username -d $Database -c "SELECT CASE WHEN to_regnamespace('hospital') IS NULL THEN 'NO' ELSE 'YES' END;"
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the target database. Check the connection and database name." }
if ($schemaExists.Trim() -eq "YES") {
    throw "The hospital schema already exists. This script is for a fresh database only; do not rerun V001/V002 over an existing installation."
}

$v001 = Join-Path $PSScriptRoot "migrations\V001__hospital_core.sql"
$v002 = Join-Path $PSScriptRoot "migrations\V002__admissions_procurement_analytics_accounting.sql"
$v003 = Join-Path $PSScriptRoot "migrations\V003__patient_branch_access.sql"
$validate = Join-Path $PSScriptRoot "validation\validate_schema_v002.sql"
$validateV003 = Join-Path $PSScriptRoot "validation\validate_schema_v003.sql"
$v004 = Join-Path $PSScriptRoot "migrations\V004__clinical_workflow_integrity.sql"
$validateV004 = Join-Path $PSScriptRoot "validation\validate_schema_v004.sql"
$seedDiagnosis = Join-Path $PSScriptRoot "seed\V003__synthetic_diagnosis_catalog.sql"
$seedNurse = Join-Path $PSScriptRoot "seed\V004__development_nurse_account.sql"
$seed = Join-Path $PSScriptRoot "seed\V001__development_reference_data.sql"
$seedAccounts = Join-Path $PSScriptRoot "seed\V002__development_accounts.sql"
$validateSeed = Join-Path $PSScriptRoot "validation\validate_development_seed.sql"
$validateClinicalSeed = Join-Path $PSScriptRoot "validation\validate_clinical_demo_seed.sql"
$v005 = Join-Path $PSScriptRoot "migrations\V005__care_team_and_laboratory_integrity.sql"
$validateV005 = Join-Path $PSScriptRoot "validation\validate_schema_v005.sql"
$seedLaboratory = Join-Path $PSScriptRoot "seed\V005__development_laboratory_accounts_and_catalog.sql"
$validateLaboratorySeed = Join-Path $PSScriptRoot "validation\validate_laboratory_demo_seed.sql"
$v006 = Join-Path $PSScriptRoot "migrations\V006__pharmacy_dispensing_integrity.sql"
$validateV006 = Join-Path $PSScriptRoot "validation\validate_schema_v006.sql"
$seedPharmacy = Join-Path $PSScriptRoot "seed\V006__development_pharmacy_accounts_and_stock.sql"
$validatePharmacySeed = Join-Path $PSScriptRoot "validation\validate_pharmacy_demo_seed.sql"
$v007 = Join-Path $PSScriptRoot "migrations\V007__billing_and_admission_integrity.sql"
$validateV007 = Join-Path $PSScriptRoot "validation\validate_schema_v007.sql"
$seedV007 = Join-Path $PSScriptRoot "seed\V007__development_billing_admission_fixtures.sql"
$validateV007Seed = Join-Path $PSScriptRoot "validation\validate_billing_admission_seed.sql"
$v008 = Join-Path $PSScriptRoot "migrations\V008__operational_analytics_and_reorder_policies.sql"
$validateV008 = Join-Path $PSScriptRoot "validation\validate_schema_v008.sql"
$seedV008 = Join-Path $PSScriptRoot "seed\V008__development_analytics_fixtures.sql"
$validateV008Seed = Join-Path $PSScriptRoot "validation\validate_analytics_seed.sql"
$v009 = Join-Path $PSScriptRoot "migrations\V009__procurement_receiving_integrity.sql"
$validateV009 = Join-Path $PSScriptRoot "validation\validate_schema_v009.sql"
$seedV009 = Join-Path $PSScriptRoot "seed\V009__development_supplier_fixture.sql"
$validateV009Seed = Join-Path $PSScriptRoot "validation\validate_procurement_demo_seed.sql"
$v010 = Join-Path $PSScriptRoot "migrations\V010__accounting_posting_guards.sql"
$validateV010 = Join-Path $PSScriptRoot "validation\validate_accounting_v010.sql"
$seedV010 = Join-Path $PSScriptRoot "seed\V010__development_chart_of_accounts.sql"
$v011 = Join-Path $PSScriptRoot "migrations\V011__audit_log_append_only.sql"
$validateV011 = Join-Path $PSScriptRoot "validation\validate_governance_v011.sql"

foreach ($file in @($v001, $v002, $v003, $v004, $validate, $validateV003, $validateV004, $seed, $seedAccounts, $seedDiagnosis, $seedNurse, $validateSeed, $validateClinicalSeed, $v005, $validateV005, $seedLaboratory, $validateLaboratorySeed, $v006, $validateV006, $seedPharmacy, $validatePharmacySeed, $v007, $validateV007, $seedV007, $validateV007Seed, $v008, $validateV008, $seedV008, $validateV008Seed, $v009, $validateV009, $seedV009, $validateV009Seed, $v010, $validateV010, $seedV010, $v011, $validateV011)) {
    if (-not (Test-Path $file)) {
        throw "Required SQL file is missing: $file"
    }
}

Write-Host "Applying Smart Hospital V001 to database '$Database'..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v001
if ($LASTEXITCODE -ne 0) { throw "V001 failed. Stop and inspect the PostgreSQL error before continuing." }

Write-Host "Applying Smart Hospital V002..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v002
if ($LASTEXITCODE -ne 0) { throw "V002 failed. Its transaction should have rolled back, but V001 is already committed. Do not rerun this script; fix the issue and apply V002 only, or recreate the empty database." }

Write-Host "Validating V001 + V002 baseline..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validate
if ($LASTEXITCODE -ne 0) { throw "V002 schema validation failed. V003 was not applied." }

Write-Host "Applying V003 patient branch access controls..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v003
if ($LASTEXITCODE -ne 0) { throw "V003 failed. Do not rerun earlier migrations; inspect the PostgreSQL error or recreate the empty database." }

Write-Host "Validating final V003 schema..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV003
if ($LASTEXITCODE -ne 0) { throw "V003 schema validation failed." }

Write-Host "Applying V004 clinical workflow integrity..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v004
if ($LASTEXITCODE -ne 0) { throw "V004 failed. Inspect the PostgreSQL error before continuing." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV004
if ($LASTEXITCODE -ne 0) { throw "V004 schema validation failed." }

Write-Host "Loading synthetic development reference data..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seed
if ($LASTEXITCODE -ne 0) { throw "Development reference data could not be loaded. Schema migrations are already applied; inspect the seed error before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedAccounts
if ($LASTEXITCODE -ne 0) { throw "Development account mapping could not be loaded. Schema migrations are already applied; inspect the seed error before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedDiagnosis
if ($LASTEXITCODE -ne 0) { throw "Synthetic diagnosis catalog could not be loaded. Schema migrations are already applied; inspect the seed error before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedNurse
if ($LASTEXITCODE -ne 0) { throw "Synthetic nurse account mapping could not be loaded. Schema migrations are already applied; inspect the seed error before retrying." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateSeed
if ($LASTEXITCODE -ne 0) { throw "Development seed validation failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateClinicalSeed
if ($LASTEXITCODE -ne 0) { throw "Clinical demo seed validation failed." }

Write-Host "Applying V005 care team and laboratory integrity..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v005
if ($LASTEXITCODE -ne 0) { throw "V005 failed. Inspect PostgreSQL errors before continuing." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV005
if ($LASTEXITCODE -ne 0) { throw "V005 schema validation failed." }
Write-Host "Loading synthetic laboratory staff and catalogue entries..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedLaboratory
if ($LASTEXITCODE -ne 0) { throw "Synthetic laboratory seed failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateLaboratorySeed
if ($LASTEXITCODE -ne 0) { throw "Laboratory demo seed validation failed." }

Write-Host "Applying V006 pharmacy dispensing integrity..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v006
if ($LASTEXITCODE -ne 0) { throw "V006 failed. Inspect the PostgreSQL error before continuing." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV006
if ($LASTEXITCODE -ne 0) { throw "V006 schema validation failed." }
Write-Host "Loading synthetic pharmacist account, medication catalogue and opening stock..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedPharmacy
if ($LASTEXITCODE -ne 0) { throw "Synthetic pharmacy seed failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validatePharmacySeed
if ($LASTEXITCODE -ne 0) { throw "Pharmacy demo seed validation failed." }

Write-Host "Applying V007 billing and admission integrity safeguards..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v007
if ($LASTEXITCODE -ne 0) { throw "V007 failed. Inspect the PostgreSQL error before continuing." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV007
if ($LASTEXITCODE -ne 0) { throw "V007 schema validation failed." }
Write-Host "Loading synthetic admission beds, billing services and accountant role mapping..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedV007
if ($LASTEXITCODE -ne 0) { throw "V007 development fixtures failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV007Seed
if ($LASTEXITCODE -ne 0) { throw "V007 development fixture validation failed." }

Write-Host "Applying V008 operational analytics and reorder policies..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v008
if ($LASTEXITCODE -ne 0) { throw "V008 failed. Inspect the PostgreSQL error before continuing." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV008
if ($LASTEXITCODE -ne 0) { throw "V008 schema validation failed." }
Write-Host "Loading synthetic analytics thresholds and pharmacy reorder policies..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedV008
if ($LASTEXITCODE -ne 0) { throw "V008 analytics fixtures failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV008Seed
if ($LASTEXITCODE -ne 0) { throw "V008 analytics fixtures validation failed." }

Write-Host "Applying V009 procurement receiving integrity..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v009
if ($LASTEXITCODE -ne 0) { throw "V009 procurement migration failed. Inspect PostgreSQL output before continuing." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV009
if ($LASTEXITCODE -ne 0) { throw "V009 schema validation failed." }
Write-Host "Loading synthetic procurement supplier..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedV009
if ($LASTEXITCODE -ne 0) { throw "V009 synthetic supplier fixture failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV009Seed
if ($LASTEXITCODE -ne 0) { throw "V009 procurement fixture validation failed." }

Write-Host "Applying V010 accounting posting guards..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v010
if ($LASTEXITCODE -ne 0) { throw "V010 accounting migration failed. Inspect PostgreSQL output before continuing." }
Write-Host "Loading synthetic chart of accounts and current fiscal period..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $seedV010
if ($LASTEXITCODE -ne 0) { throw "V010 chart-of-accounts seed failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV010
if ($LASTEXITCODE -ne 0) { throw "V010 accounting validation failed." }

Write-Host "Applying V011 append-only audit protection..."
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $v011
if ($LASTEXITCODE -ne 0) { throw "V011 audit protection migration failed." }
& psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $Username -d $Database -f $validateV011
if ($LASTEXITCODE -ne 0) { throw "V011 governance validation failed." }

Write-Host "Smart Hospital schema V001–V011 applied and validated; 64 synthetic-development tables and append-only audit protection are ready."
