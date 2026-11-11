#requires -Version 5.1
<#
.SYNOPSIS
    Verifies the Smart Hospital tamper-evident audit hash chain.

.DESCRIPTION
    Calls GET /api/v1/audit-logs/integrity as a HOSPITAL_ADMIN and reports whether
    the chain is continuous, every stored digest matches its persisted preimage,
    and the head matches audit_chain_state.

    Exit code 0 = intact, 1 = verification failed or endpoint unreachable.
    Run this after every restore, before promoting a backup, and on a schedule as
    part of the security monitoring runbook.

.EXAMPLE
    .\verify-audit-chain.ps1
#>
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminUser = 'hospitaladmin',
    [string]$Password = 'Demo@2026'
)

$ErrorActionPreference = 'Stop'

try {
    $loginBody = @{ username = $AdminUser; password = $Password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
        -ContentType 'application/json' -Body $loginBody
    if (-not $login.token) { throw 'Login returned no bearer token.' }

    $report = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/audit-logs/integrity" `
        -Headers @{ Authorization = "Bearer $($login.token)" }
}
catch {
    Write-Host "AUDIT_VERIFY_ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host 'Audit chain integrity' -ForegroundColor Cyan
Write-Host ("  checked at        : {0}" -f $report.checkedAt)
Write-Host ("  total rows        : {0}" -f $report.totalRows)
Write-Host ("  chain rows        : {0}" -f $report.chainRows)
Write-Host ("  pre-chain rows    : {0}" -f $report.unchainedRows)
Write-Host ("  verified rows     : {0}" -f $report.verifiedRows)
Write-Host ("  legacy rows       : {0}" -f $report.legacyRows)
Write-Host ("  hash mismatches   : {0}" -f $report.hashMismatches)
Write-Host ("  broken links      : {0}" -f $report.brokenLinks)
Write-Host ("  stored sequence   : {0}" -f $report.storedSequence)
Write-Host ("  stored head       : {0}" -f $report.storedHeadHash)
Write-Host ("  recomputed head   : {0}" -f $report.recomputedHeadHash)
Write-Host ("  message           : {0}" -f $report.message)
Write-Host ''

if ($report.chainIntact) {
    Write-Host 'AUDIT_CHAIN_OK' -ForegroundColor Green
    exit 0
}
Write-Host 'AUDIT_CHAIN_BROKEN' -ForegroundColor Red
exit 1
