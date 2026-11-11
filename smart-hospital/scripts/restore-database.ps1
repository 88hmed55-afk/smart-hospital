#requires -Version 5.1
<#
.SYNOPSIS
    Restores a Smart Hospital MySQL logical backup.

.DESCRIPTION
    Safety-first restore:
      1. The backup file's SHA-256 is verified against its .sha256 sidecar (if present).
      2. The SQL is validated for expected markers before any write.
      3. By default the restore targets a NEW database (hospital_restore_<timestamp>) so a
         production schema is never overwritten by accident. Passing -TargetDatabase hospital
         requires both -AllowOverwrite and the literal confirmation token.

    After a restore, run .\scripts\verify-audit-chain.ps1 against the restored database and
    the application smoke test before promoting it.

.EXAMPLE
    .\restore-database.ps1 -BackupFile D:\backups\smart-hospital\hospital-20261110-010000.sql

.EXAMPLE
    .\restore-database.ps1 -BackupFile .\hospital-20261110-010000.sql -TargetDatabase hospital `
        -AllowOverwrite -ConfirmToken 'RESTORE hospital'
#>
param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$MysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin',
    [string]$LoginPath = 'smart-hospital-local',
    [string]$TargetDatabase,
    [switch]$AllowOverwrite,
    [switch]$DropAndRecreate,
    [string]$ConfirmToken
)

$ErrorActionPreference = 'Stop'

$mysqlExe = Join-Path $MysqlBin 'mysql.exe'
if (!(Test-Path $mysqlExe)) { throw "mysql.exe not found: $mysqlExe" }
if (!(Test-Path $BackupFile)) { throw "Backup file not found: $BackupFile" }

$BackupFile = [IO.Path]::GetFullPath($BackupFile)

# 1. Integrity check.
$sidecar = "$BackupFile.sha256"
if (Test-Path $sidecar) {
    $expected = (Get-Content $sidecar -Raw).Trim().Split(' ')[0].ToLowerInvariant()
    $actual = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expected -ne $actual) {
        throw "Checksum mismatch. Expected $expected but file is $actual. Refusing to restore."
    }
    Write-Host 'Checksum verified.' -ForegroundColor Green
}
else {
    Write-Warning 'No .sha256 sidecar found; integrity cannot be verified against a stored hash.'
}

# 2. Content validation before any write.
$head = Get-Content $BackupFile -TotalCount 4000
if (!($head -match 'CREATE TABLE')) { throw 'Backup does not contain CREATE TABLE statements.' }
if (!($head -match 'schema_version')) { throw 'Backup does not contain the schema_version table.' }

# 3. Target selection.
if ([string]::IsNullOrWhiteSpace($TargetDatabase)) {
    $TargetDatabase = 'hospital_restore_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
}

$targetIsProduction = ($TargetDatabase -eq 'hospital')
if ($targetIsProduction) {
    if (!$AllowOverwrite) {
        throw 'Refusing to restore into the live hospital database without -AllowOverwrite.'
    }
    if ($ConfirmToken -ne 'RESTORE hospital') {
        throw "Refusing live restore: pass -ConfirmToken 'RESTORE hospital' to confirm."
    }
}
if ($TargetDatabase -notmatch '^[A-Za-z0-9_]+$') {
    throw "Invalid target database name: $TargetDatabase"
}

function Invoke-MySqlNative {
    param([string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $output = & $mysqlExe @Arguments 2>&1 } finally { $ErrorActionPreference = $previous }
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = @($output) }
}

function Invoke-MySql([string]$Sql, [string]$Db) {
    $args = @("--login-path=$LoginPath", '--default-character-set=utf8mb4')
    if ($Db) { $args += "--database=$Db" }
    $result = Invoke-MySqlNative ($args + @('--execute', $Sql))
    if ($result.ExitCode -ne 0) { throw "SQL failed: $($result.Output -join [Environment]::NewLine)" }
    return $result.Output
}

$exists = Invoke-MySqlNative @("--login-path=$LoginPath", '--batch', '--skip-column-names', '--default-character-set=utf8mb4', '--execute', "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$TargetDatabase';")
if ($exists.ExitCode -ne 0) { throw "Cannot reach MySQL: $($exists.Output -join ' ')" }
$exists = [int](@($exists.Output) | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)

if ($exists -gt 0) {
    if (!$DropAndRecreate) {
        throw "Target database '$TargetDatabase' already exists. Pass -DropAndRecreate to replace it (never use this on production without a verified backup)."
    }
    Write-Host "Dropping existing database $TargetDatabase" -ForegroundColor Yellow
    Invoke-MySql "DROP DATABASE ``$TargetDatabase``;" $null | Out-Null
}

Write-Host "Creating database $TargetDatabase" -ForegroundColor Cyan
Invoke-MySql "CREATE DATABASE ``$TargetDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" $null | Out-Null

# 4. Restore. mysqldump output contains USE/CREATE DATABASE statements only when
#    --databases was used; this edition dumps a single schema, so import into the target.
Write-Host "Restoring $BackupFile" -ForegroundColor Cyan
$staged = Join-Path $env:TEMP 'smart_hospital_restore.sql'
Copy-Item -Path $BackupFile -Destination $staged -Force
$restore = Invoke-MySqlNative @("--login-path=$LoginPath", '--default-character-set=utf8mb4', "--database=$TargetDatabase", '--execute', "source $staged")
if ($restore.ExitCode -ne 0) { throw "Restore failed: $($restore.Output -join [Environment]::NewLine)" }
Remove-Item $staged -Force -ErrorAction SilentlyContinue

# 5. Post-restore sanity.
$tablesResult = Invoke-MySqlNative @("--login-path=$LoginPath", '--batch', '--skip-column-names', '--default-character-set=utf8mb4', "--database=$TargetDatabase", '--execute', "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TargetDatabase';")
$tables = [int](@($tablesResult.Output) | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
if ($tables -lt 10) { throw "Restore produced only $tables tables; expected the full hospital schema." }

Write-Host ("Restore complete: {0} tables in {1}" -f $tables, $TargetDatabase) -ForegroundColor Green
Write-Host 'Next: point a test deployment at this schema, run verify-audit-chain.ps1 and the smoke test.' -ForegroundColor DarkGray
Write-Host 'RESTORE_OK'
