#requires -Version 5.1
<#
.SYNOPSIS
    Starts the local Smart Hospital stack (MySQL + GlassFish) after a reboot.

.DESCRIPTION
    Idempotent, non-destructive helper for the development machine:
      1. starts the MySQL80 service when it is stopped,
      2. starts the GlassFish domain when nothing listens on port 8080,
      3. waits until POST /api/v1/auth/login answers 400/401 (application ready).

    Starting the MySQL service needs an elevated shell the first time. The service
    is configured for automatic start, so normally only GlassFish needs starting.

.EXAMPLE
    .\start-local-stack.ps1

.EXAMPLE
    .\start-local-stack.ps1 -BaseUrl 'http://localhost:8080' -TimeoutSeconds 180
#>
param(
    [string]$ServiceName = 'MYSQL80',
    [string]$AsadminCmd = 'C:\server\glassfish-7.0.23\glassfish7\bin\asadmin.bat',
    [string]$Domain = 'domain1',
    [string]$BaseUrl = 'http://localhost:8080',
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Continue'

function Test-LoginEndpoint {
    try {
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
            -ContentType 'application/json' `
            -Body '{"username":"__startup_probe__","password":"__startup_probe__"}' `
            -TimeoutSec 5 -UseBasicParsing | Out-Null
        return $false
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        return ($code -eq 400 -or $code -eq 401)
    }
}

# 1. MySQL ---------------------------------------------------------------
$service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($null -eq $service) {
    Write-Host "MySQL service '$ServiceName' was not found." -ForegroundColor Yellow
} elseif ($service.Status -ne 'Running') {
    Write-Host "Starting MySQL service '$ServiceName'..." -ForegroundColor Cyan
    try {
        Start-Service -Name $ServiceName -ErrorAction Stop
    } catch {
        Write-Host "Could not start '$ServiceName': $($_.Exception.Message)" -ForegroundColor Yellow
        Write-Host 'Open an elevated PowerShell and run: Start-Service MYSQL80' -ForegroundColor Yellow
    }
} else {
    Write-Host "MySQL service '$ServiceName' is running." -ForegroundColor Green
}

# 2. GlassFish -----------------------------------------------------------
$listening = $false
try {
    $client = New-Object Net.Sockets.TcpClient
    $client.Connect('localhost', 8080)
    $listening = $client.Connected
    $client.Close()
} catch {
    $listening = $false
}

if (-not $listening) {
    if (!(Test-Path $AsadminCmd)) { throw "asadmin not found: $AsadminCmd" }
    Write-Host "Starting GlassFish domain '$Domain'..." -ForegroundColor Cyan
    # asadmin may write diagnostics to stderr; readiness is checked below.
    & $AsadminCmd start-domain $Domain | Out-Host
} else {
    Write-Host 'Port 8080 is already listening.' -ForegroundColor Green
}

# 3. Readiness -----------------------------------------------------------
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    if (Test-LoginEndpoint) { $ready = $true; break }
    Start-Sleep -Seconds 2
}

if ($ready) {
    Write-Host ''
    Write-Host "READY  $BaseUrl/" -ForegroundColor Green
    Write-Host 'Login  hospitaladmin / Demo@2026' -ForegroundColor Green
    exit 0
}

Write-Host ''
Write-Host "NOT READY after $TimeoutSeconds seconds. Check the GlassFish server log." -ForegroundColor Red
exit 1
