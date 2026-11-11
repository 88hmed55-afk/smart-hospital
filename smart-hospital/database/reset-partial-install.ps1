param(
  [string]$MysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin",
  [string]$LoginPath = "smart-hospital-local",
  [switch]$SkipSeeds
)
$ErrorActionPreference = "Stop"
$mysqlExe = Join-Path $MysqlBin "mysql.exe"
if (!(Test-Path $mysqlExe)) { throw "mysql.exe not found: $mysqlExe. Update -MysqlBin to your MySQL 8.0 bin folder." }

Write-Warning "DANGER: This reset deletes the ENTIRE MySQL database named 'hospital', including every table and row. Use it only to recover from the failed first-time installation when you have no important data in that database."
$confirmation = Read-Host "To delete and rebuild database 'hospital', type RESET exactly"
if ($confirmation -cne "RESET") {
    Write-Host "Cancelled. No database changes were made."
    exit 0
}

# Confirm that the previously saved local administrator login works before deleting anything.
$null = & $mysqlExe "--login-path=$LoginPath" "--batch" "--skip-column-names" "--execute" "SELECT 1;"
if ($LASTEXITCODE -ne 0) { throw "Cannot connect with login path '$LoginPath'. Do not continue. Run database/apply-mysql-local.ps1 once to save the MySQL administrator password, then retry." }

Write-Host "Removing the incomplete 'hospital' schema..."
& $mysqlExe "--login-path=$LoginPath" "--default-character-set=utf8mb4" "--execute" "DROP DATABASE IF EXISTS hospital; CREATE DATABASE hospital CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
if ($LASTEXITCODE -ne 0) { throw "Could not recreate database 'hospital'. No further installation was attempted." }

Write-Host "Reinstalling Smart Hospital database using the corrected schema..."
$installer = Join-Path $PSScriptRoot "apply-mysql-local.ps1"
$installerArgs = @{
    MysqlBin = $MysqlBin
    LoginPath = $LoginPath
    UseExistingLoginPath = $true
}
if ($SkipSeeds) { $installerArgs.SkipSeeds = $true }
& $installer @installerArgs
if (!$?) { throw "Database installation did not complete. Read the first SQL error above and stop; do not rerun the reset script repeatedly." }
