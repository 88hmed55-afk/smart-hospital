#requires -Version 5.1
<#
.SYNOPSIS
    Applies Smart Hospital database migrations in order and records them in hospital.schema_version.

.DESCRIPTION
    Uses the reviewed SQL files in database/mysql/migrations. Each file is applied exactly
    once, in filename order, and its SHA-256 checksum is stored. The first run on a database
    created by the legacy installer is baselined automatically (V001..V011 marked as applied).

    Production practice: run this with a dedicated migration account (DDL rights) from your
    deployment pipeline, never with the runtime application account.

.EXAMPLE
    .\apply-migrations.ps1 -LoginPath smart-hospital-local
#>
param(
    [string]$MysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin",
    [string]$LoginPath = "smart-hospital-local",
    [string]$Database = "hospital",
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$mysqlExe = Join-Path $MysqlBin 'mysql.exe'
if (!(Test-Path $mysqlExe)) { throw "mysql.exe not found: $mysqlExe" }
if ($Database -ne 'hospital') { throw 'This edition is bound to the hospital schema.' }

$migrationDir = Join-Path $PSScriptRoot 'mysql\migrations'
$files = Get-ChildItem -Path $migrationDir -Filter 'V*__*.sql' | Sort-Object Name
if (!$files) { throw "No migration files found in $migrationDir" }

$dbArg = "--database=$Database"

function Invoke-Sql([string]$Sql) {
    $out = & $mysqlExe "--login-path=$LoginPath" '--batch' '--skip-column-names' '--default-character-set=utf8mb4' $dbArg '--execute' $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $($out -join [Environment]::NewLine)" }
    return $out
}

function Invoke-SqlFile([string]$Path) {
    # mysql's `source` command does not quote paths with spaces or non-ASCII characters.
    # Stage every migration under an ASCII temporary path before executing it.
    $staged = Join-Path $env:TEMP 'smart_hospital_migration.sql'
    Copy-Item -Path $Path -Destination $staged -Force
    $out = & $mysqlExe "--login-path=$LoginPath" '--default-character-set=utf8mb4' $dbArg '--show-warnings' '--verbose' '--execute' "source $staged" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Migration failed: $Path`n$($out -join [Environment]::NewLine)" }
}

function Read-Scalar([string]$Sql) {
    $out = Invoke-Sql $Sql
    $line = @($out | Where-Object { $_ -match '^-?\d+$' }) | Select-Object -Last 1
    return [int]$line
}

Write-Host "Migration target: $Database (login path: $LoginPath)"

Invoke-Sql "CREATE TABLE IF NOT EXISTS schema_version (
    version     VARCHAR(32) NOT NULL PRIMARY KEY,
    description VARCHAR(200) NOT NULL,
    checksum    CHAR(64) NOT NULL,
    applied_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;" | Out-Null

# Baseline a legacy install that predates schema_version.
$baseline = Read-Scalar "SELECT COUNT(*) FROM schema_version;"
if ($baseline -eq 0) {
    $existing = Read-Scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='user_accounts';"
    if ($existing -gt 0) {
        Write-Host 'Baselining legacy schema (V001..V011).'
        foreach ($v in 1..11) {
            $version = 'V{0:D3}' -f $v
            Invoke-Sql "INSERT IGNORE INTO schema_version (version, description, checksum) VALUES ('$version', 'baseline', REPEAT('0',64));" | Out-Null
        }
    }
}

$applied = @{}
Invoke-Sql "SELECT version FROM schema_version;" | ForEach-Object { $applied[$_.Trim()] = $true }

$count = 0
foreach ($file in $files) {
    if ($file.Name -notmatch '^(V\d{3})__(.+)\.sql$') { throw "Unrecognised migration filename: $($file.Name)" }
    $version = $Matches[1]
    $description = $Matches[2].Replace('_', ' ')
    if ($applied.ContainsKey($version)) {
        Write-Host "  [skip] $version already applied"
        continue
    }
    $checksum = (Get-FileHash -Path $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($WhatIf.IsPresent) {
        Write-Host "  [whatif] would apply $($file.Name)"
        continue
    }
    Write-Host "  [apply] $($file.Name)"
    Invoke-SqlFile $file.FullName
    Invoke-Sql "INSERT INTO schema_version (version, description, checksum) VALUES ('$version', '$description', '$checksum');" | Out-Null
    $count++
}

Write-Host "Migrations complete. Applied: $count."
