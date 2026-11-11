param(
  [string]$MysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin",
  [string]$RootUser = "root",
  [string]$Database = "hospital",
  [string]$LoginPath = "smart-hospital-local",
  [switch]$SkipSeeds,
  [switch]$UseExistingLoginPath
)
$ErrorActionPreference = "Stop"
if ($Database -ne "hospital") { throw "This edition is mapped to the MySQL database name hospital. Do not change -Database without updating schema-qualified SQL and JPA mappings." }
$mysqlExe = Join-Path $MysqlBin "mysql.exe"
$loginEditor = Join-Path $MysqlBin "mysql_config_editor.exe"
if (!(Test-Path $mysqlExe)) { throw "mysql.exe not found: $mysqlExe. Update -MysqlBin to your MySQL 8.0 bin folder." }
if (!$UseExistingLoginPath -and !(Test-Path $loginEditor)) { throw "mysql_config_editor.exe not found: $loginEditor. Install MySQL Client Tools or update -MysqlBin." }
$schema = Join-Path $PSScriptRoot "mysql\01_schema.sql"
$constraints = Join-Path $PSScriptRoot "mysql\03_constraints.sql"
$triggers = Join-Path $PSScriptRoot "mysql\04_triggers.sql"
$seeds = Join-Path $PSScriptRoot "mysql\02_development_seed.sql"
$validation = Join-Path $PSScriptRoot "mysql\05_validation.sql"
foreach ($f in @($schema,$constraints,$triggers,$validation)) { if (!(Test-Path $f)) { throw "Required SQL file not found: $f" } }

Write-Warning "This installer targets a NEW/EMPTY MySQL schema named '$Database'. It does not migrate PostgreSQL data and must not be run against a database containing important data."
if ($UseExistingLoginPath) {
    Write-Host "Using the saved MySQL login path '$LoginPath'."
    $null = & $mysqlExe "--login-path=$LoginPath" "--batch" "--skip-column-names" "--execute" "SELECT 1;"
    if ($LASTEXITCODE -ne 0) { throw "Could not connect using saved login path '$LoginPath'. Run the installer without -UseExistingLoginPath once to save the MySQL password." }
} else {
    Write-Host "Create or update the local MySQL login path '$LoginPath'. Enter the MySQL password when prompted."
    & $loginEditor set "--login-path=$LoginPath" "--host=localhost" "--user=$RootUser" --password
    if ($LASTEXITCODE -ne 0) { throw "Could not create/update MySQL login path '$LoginPath'." }
}

# Refuse to overlay an existing schema. This is deliberately a fresh-install-only script.
$existingObjects = & $mysqlExe "--login-path=$LoginPath" "--batch" "--skip-column-names" "--execute" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$Database';"
if ($LASTEXITCODE -ne 0) { throw "Could not inspect the target schema before installation." }
$existingCount = 0
if (-not [int]::TryParse(($existingObjects | Select-Object -Last 1).ToString().Trim(), [ref]$existingCount)) {
    throw "Could not parse the target schema object count; stopping before changes."
}
if ($existingCount -gt 0) {
    throw "The schema '$Database' already contains $existingCount table/view objects. This script only installs into an empty schema. Back up your data and use a reviewed migration plan; do not rerun the fresh installer."
}

function Invoke-MySqlFile([string]$SqlFile, [string]$TargetDatabase = "") {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $mysqlExe
    $args = "--login-path=$LoginPath --default-character-set=utf8mb4 --show-warnings --verbose"
    if ($TargetDatabase) { $args += " --database=$TargetDatabase" }
    $psi.Arguments = $args
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.CreateNoWindow = $false
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    if (!$process.Start()) { throw "Could not start mysql.exe for $SqlFile" }
    try {
        $sql = [System.IO.File]::ReadAllText($SqlFile, [System.Text.Encoding]::UTF8)
        $process.StandardInput.Write($sql)
        $process.StandardInput.Close()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "MySQL returned exit code $($process.ExitCode) for $SqlFile. The schema may be partially applied; inspect the first SQL error before retrying." }
    } finally {
        $process.Dispose()
    }
}

Write-Host "[1/5] Creating schema and tables..."
Invoke-MySqlFile $schema
Write-Host "[2/5] Applying MySQL-specific unique constraints..."
Invoke-MySqlFile $constraints $Database
Write-Host "[3/5] Creating integrity triggers..."
Invoke-MySqlFile $triggers $Database
if (!$SkipSeeds) {
    Write-Host "[4/5] Applying synthetic development fixtures..."
    Invoke-MySqlFile $seeds $Database
} else {
    Write-Host "[4/5] Development fixtures skipped."
}
Write-Host "[5/5] Validating installed schema..."
Invoke-MySqlFile $validation $Database
Write-Host "Smart Hospital MySQL scripts completed. Review the validation counts before configuring GlassFish."
Write-Host "Login path '$LoginPath' is stored by mysql_config_editor; remove it later with mysql_config_editor remove --login-path=$LoginPath."
