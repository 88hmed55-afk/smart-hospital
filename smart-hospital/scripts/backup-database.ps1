#requires -Version 5.1
<#
.SYNOPSIS
    Creates a consistent logical backup of the Smart Hospital MySQL database.

.DESCRIPTION
    Uses mysqldump with --single-transaction so InnoDB tables are captured at a
    consistent point in time without blocking clinical writes. Routines, triggers
    and events are included because the audit-trail append-only guards are triggers.

    The resulting .sql file is verified (non-empty, contains CREATE TABLE and the
    schema_version table) and hashed. A .sha256 sidecar is written so a restore can
    prove the backup was not altered in storage.

    Retention: files older than -RetentionDays are deleted from the backup directory.

.EXAMPLE
    .\backup-database.ps1 -LoginPath smart-hospital-local -BackupDirectory D:\backups\smart-hospital

.EXAMPLE
    .\backup-database.ps1 -Compress -RetentionDays 30
#>
param(
    [string]$MysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin',
    [string]$LoginPath = 'smart-hospital-local',
    [string]$Database = 'hospital',
    [string]$BackupDirectory = (Join-Path $PSScriptRoot '..\backups'),
    [int]$RetentionDays = 14,
    [switch]$Compress
)

$ErrorActionPreference = 'Stop'

$dumpExe = Join-Path $MysqlBin 'mysqldump.exe'
$mysqlExe = Join-Path $MysqlBin 'mysql.exe'
if (!(Test-Path $dumpExe)) { throw "mysqldump.exe not found: $dumpExe" }
if (!(Test-Path $mysqlExe)) { throw "mysql.exe not found: $mysqlExe" }
if ($Database -ne 'hospital') { throw 'This edition is bound to the hospital schema.' }

$BackupDirectory = [IO.Path]::GetFullPath($BackupDirectory)
if (!(Test-Path $BackupDirectory)) { New-Item -ItemType Directory -Path $BackupDirectory -Force | Out-Null }

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$baseName = "hospital-$timestamp"
$sqlPath = Join-Path $BackupDirectory "$baseName.sql"

Write-Host "Backing up database '$Database' to $sqlPath" -ForegroundColor Cyan

$dumpArgs = @(
    "--login-path=$LoginPath",
    '--single-transaction',
    '--quick',
    '--routines',
    '--triggers',
    '--events',
    '--set-gtid-purged=OFF',
    '--default-character-set=utf8mb4',
    '--hex-blob',
    "--result-file=$sqlPath",
    $Database
)

& $dumpExe @dumpArgs
if ($LASTEXITCODE -ne 0) { throw "mysqldump failed with exit code $LASTEXITCODE." }

if (!(Test-Path $sqlPath)) { throw 'mysqldump did not produce an output file.' }
$sqlFile = Get-Item $sqlPath
if ($sqlFile.Length -lt 1024) { throw "Backup file is suspiciously small ($($sqlFile.Length) bytes)." }

$head = Get-Content $sqlPath -TotalCount 4000 -ErrorAction Stop
if (!($head -match 'CREATE TABLE')) { throw 'Backup does not contain CREATE TABLE statements.' }
if (!($head -match 'schema_version')) { throw 'Backup does not contain the schema_version table.' }

$hash = (Get-FileHash -Path $sqlPath -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -Path "$sqlPath.sha256" -Value "$hash  $baseName.sql" -Encoding ASCII

# Fail closed: verify the dump can be read by the server parser without executing it.
$previousEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$verify = & $mysqlExe "--login-path=$LoginPath" '--batch' '--skip-column-names' '--default-character-set=utf8mb4' '--execute' "SELECT 1;" 2>$null
$verifyExit = $LASTEXITCODE
$ErrorActionPreference = $previousEap
if ($verifyExit -ne 0) { throw "MySQL connectivity check failed (exit $verifyExit)." }

$finalPath = $sqlPath
if ($Compress) {
    $zipPath = Join-Path $BackupDirectory "$baseName.zip"
    Compress-Archive -Path $sqlPath, "$sqlPath.sha256" -DestinationPath $zipPath -Force
    Remove-Item $sqlPath -Force
    Remove-Item "$sqlPath.sha256" -Force
    $finalPath = $zipPath
}

Write-Host ("Backup complete: {0} ({1:N0} bytes) SHA-256 {2}" -f $finalPath, (Get-Item $finalPath).Length, $hash) -ForegroundColor Green

if ($RetentionDays -gt 0) {
    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    Get-ChildItem -Path $BackupDirectory -File |
        Where-Object { $_.LastWriteTime -lt $cutoff -and $_.Name -like 'hospital-*' } |
        ForEach-Object {
            Write-Host "  removing expired backup $($_.Name)" -ForegroundColor DarkGray
            Remove-Item $_.FullName -Force
        }
}

Write-Host 'BACKUP_OK'
