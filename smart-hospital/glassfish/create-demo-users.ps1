param([string]$GlassFishHome = "C:\glassfish7")
$ErrorActionPreference = "Stop"
$asadmin = Join-Path $GlassFishHome "bin\asadmin.bat"
if (!(Test-Path $asadmin)) { throw "GlassFish asadmin.bat not found: $asadmin" }
$users = @(
    @{ Name = "hospitaladmin"; Group = "HOSPITAL_ADMIN" },
    @{ Name = "manager1"; Group = "HOSPITAL_MANAGER" },
    @{ Name = "receptionist1"; Group = "RECEPTIONIST" },
    @{ Name = "doctor1"; Group = "DOCTOR" },
    @{ Name = "nurse1"; Group = "NURSE" },
    @{ Name = "labtech1"; Group = "LAB_TECHNICIAN" },
    @{ Name = "labsupervisor1"; Group = "LAB_SUPERVISOR" },
    @{ Name = "pharmacist1"; Group = "PHARMACIST" },
    @{ Name = "accountant1"; Group = "ACCOUNTANT" },
    @{ Name = "inventoryofficer1"; Group = "INVENTORY_OFFICER" }
)
Write-Warning "Development only: this creates GlassFish file-realm users that match synthetic accounts in database/mysql seeds. Choose unique strong passwords; do not use these accounts for real patient data."
foreach ($entry in $users) {
    Write-Host "Creating $($entry.Name) in GlassFish file realm with group $($entry.Group)."
    & $asadmin create-file-user --groups $entry.Group $entry.Name
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create file-realm user '$($entry.Name)'. If it already exists, remove it with 'asadmin delete-file-user $($entry.Name)' or manage it manually, then rerun."
    }
}
Write-Host "All ten synthetic GlassFish file-realm users were created. The passwords you entered are not stored in the project files."
