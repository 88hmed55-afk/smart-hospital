#requires -Version 5.1
<#
.SYNOPSIS
    Smart Hospital - quick smoke test (non-destructive).

.DESCRIPTION
    Verifies the UI, SPA deep links, bearer authentication, core REST modules and role
    gating. Uses the application token endpoint (POST /api/v1/auth/login); the legacy
    container BASIC realm is no longer used in production mode. Non-GET calls are
    limited to: the idempotent authentication-state maintenance job, a wrong-current
    password attempt (must be rejected without changing anything), and a disposable
    login/logout pair proving the session is revoked.

.EXAMPLE
    .\scripts\smoke-test.ps1

.EXAMPLE
    .\scripts\smoke-test.ps1 -BaseUrl 'http://localhost:8080' -Password 'Demo@2026'
#>
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminUser = 'hospitaladmin',
    [string]$Password = 'Demo@2026',
    [string]$BranchId = '10000000-0000-4000-8000-000000000001',
    [string]$PatientId = '08adbad3-84d9-4333-867e-8d095cef263f'
)

$ErrorActionPreference = 'Stop'
$script:passed = 0
$script:failed = 0
$script:tokens = @{}

function Get-Token {
    param([string]$User)

    if ($script:tokens.ContainsKey($User)) { return $script:tokens[$User] }

    $body = @{ username = $User; password = $Password } | ConvertTo-Json -Compress
    try {
        $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
            -ContentType 'application/json' -Body $body
    }
    catch {
        throw ("Login failed for {0}: {1}" -f $User, $_.Exception.Message)
    }
    if (-not $response.token) { throw "Login for $User returned no bearer token." }
    $script:tokens[$User] = $response.token
    return $response.token
}

function Invoke-Check {
    param(
        [string]$Label,
        [string]$Url,
        [int]$Expected = 200,
        [string]$User,
        [string]$Method = 'GET',
        [string]$Token,
        [switch]$NoAuth
    )

    $effectiveUser = $AdminUser
    if ($User) { $effectiveUser = $User }

    if (-not $Token -and -not $NoAuth) { $Token = Get-Token $effectiveUser }

    $curlArgs = @('-s', '-o', 'NUL', '-w', '%{http_code}', '-X', $Method)
    if ($Token) {
        $curlArgs += @('-H', ('Authorization: Bearer {0}' -f $Token))
    }
    $curlArgs += $Url

    $code = (& curl.exe @curlArgs 2>$null | Select-Object -Last 1)
    $code = ($code -as [string]).Trim()

    if ($code -eq [string]$Expected) {
        $script:passed++
        Write-Host ("  [PASS] {0,-4} {1}" -f $code, $Label) -ForegroundColor Green
    }
    else {
        $script:failed++
        Write-Host ("  [FAIL] {0,-4} expected {1}  {2}" -f $code, $Expected, $Url) -ForegroundColor Red
    }
}

function Invoke-BodyCheck {
    param(
        [string]$Label,
        [string]$Path,
        [hashtable]$Body = @{},
        [int]$Expected = 200,
        [string]$User,
        [string]$Token
    )

    if (-not $Token) {
        $effectiveUser = $AdminUser
        if ($User) { $effectiveUser = $User }
        $Token = Get-Token $effectiveUser
    }

    $payload = $Body | ConvertTo-Json -Compress
    try {
        $response = Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v1$Path" `
            -Headers @{ Authorization = "Bearer $Token" } `
            -ContentType 'application/json' -Body $payload -UseBasicParsing
        $code = [int]$response.StatusCode
    }
    catch {
        $code = [int]$_.Exception.Response.StatusCode
    }

    if ($code -eq $Expected) {
        $script:passed++
        Write-Host ("  [PASS] {0,-4} {1}" -f $code, $Label) -ForegroundColor Green
    }
    else {
        $script:failed++
        Write-Host ("  [FAIL] {0,-4} expected {1}  {2}" -f $code, $Expected, $Label) -ForegroundColor Red
    }
}

function Invoke-RawBodyCheck {
    param(
        [string]$Label,
        [string]$Path,
        [string]$RawBody,
        [int]$Expected = 200,
        [string]$Token
    )

    if (-not $Token) { $Token = Get-Token $AdminUser }

    try {
        $response = Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v1$Path" `
            -Headers @{ Authorization = "Bearer $Token" } `
            -ContentType 'application/json' -Body $RawBody -UseBasicParsing
        $code = [int]$response.StatusCode
    }
    catch {
        $code = [int]$_.Exception.Response.StatusCode
    }

    if ($code -eq $Expected) {
        $script:passed++
        Write-Host ("  [PASS] {0,-4} {1}" -f $code, $Label) -ForegroundColor Green
    }
    else {
        $script:failed++
        Write-Host ("  [FAIL] {0,-4} expected {1}  {2}" -f $code, $Expected, $Label) -ForegroundColor Red
    }
}

function Invoke-FieldCheck {
    param(
        [string]$Label,
        [string]$Path,
        [string]$Field,
        [string]$User
    )

    $effectiveUser = $AdminUser
    if ($User) { $effectiveUser = $User }

    try {
        $token = Get-Token $effectiveUser
        $response = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1$Path" `
            -Headers @{ Authorization = "Bearer $token" }
        $present = $null -ne $response.PSObject.Properties[$Field]
        $code = 200
    }
    catch {
        $present = $false
        $code = [int]$_.Exception.Response.StatusCode
    }

    if ($code -eq 200 -and $present) {
        $script:passed++
        Write-Host ("  [PASS] {0,-4} {1}" -f $code, $Label) -ForegroundColor Green
    }
    else {
        $script:failed++
        Write-Host ("  [FAIL] {0,-4} {1} (field: {2})" -f $code, $Label, $Field) -ForegroundColor Red
    }
}

function Invoke-LoginCheck {
    param(
        [string]$Label,
        [string]$User,
        [string]$TestPassword,
        [int]$Expected = 200
    )

    $body = @{ username = $User; password = $TestPassword } | ConvertTo-Json -Compress
    try {
        $response = Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
            -ContentType 'application/json' -Body $body -UseBasicParsing
        $code = [int]$response.StatusCode
    }
    catch {
        $code = [int]$_.Exception.Response.StatusCode
    }

    if ($code -eq $Expected) {
        $script:passed++
        Write-Host ("  [PASS] {0,-4} {1}" -f $code, $Label) -ForegroundColor Green
    }
    else {
        $script:failed++
        Write-Host ("  [FAIL] {0,-4} expected {1}  {2}" -f $code, $Expected, $Label) -ForegroundColor Red
    }
}

Write-Host ''
Write-Host 'Smart Hospital - Smoke Test' -ForegroundColor Cyan
Write-Host ("Target: {0}" -f $BaseUrl) -ForegroundColor DarkGray
Write-Host ''

Write-Host 'SPA (no auth)' -ForegroundColor Yellow
Invoke-Check -Label 'GET /' -Url "$BaseUrl/" -NoAuth
Invoke-Check -Label 'GET /login' -Url "$BaseUrl/login" -NoAuth
Invoke-Check -Label 'GET /dashboard' -Url "$BaseUrl/dashboard" -NoAuth
Invoke-Check -Label 'GET /settings' -Url "$BaseUrl/settings" -NoAuth
Invoke-Check -Label 'GET /audit' -Url "$BaseUrl/audit" -NoAuth
Invoke-Check -Label 'GET /change-password' -Url "$BaseUrl/change-password" -NoAuth
Invoke-Check -Label 'GET /mfa-setup' -Url "$BaseUrl/mfa-setup" -NoAuth
Invoke-Check -Label 'GET /security' -Url "$BaseUrl/security" -NoAuth

Write-Host ''
Write-Host 'Authentication' -ForegroundColor Yellow
Invoke-LoginCheck -Label 'login hospitaladmin' -User $AdminUser -TestPassword $Password
Invoke-LoginCheck -Label 'login doctor1' -User 'doctor1' -TestPassword $Password
Invoke-LoginCheck -Label 'login nurse1' -User 'nurse1' -TestPassword $Password
Invoke-LoginCheck -Label 'login accountant1' -User 'accountant1' -TestPassword $Password
Invoke-LoginCheck -Label 'wrong credentials rejected' -User 'smoke-test-no-such-user' -TestPassword 'not-the-password' -Expected 401
Invoke-Check -Label 'GET security/me without token' -Url "$BaseUrl/api/v1/security/me" -Expected 401 -NoAuth
Invoke-Check -Label 'GET patients with invalid token' -Url "$BaseUrl/api/v1/patients" -Expected 401 -NoAuth

Write-Host ''
Write-Host 'Identity and health (HOSPITAL_ADMIN)' -ForegroundColor Yellow
Invoke-Check -Label 'GET security/me' -Url "$BaseUrl/api/v1/security/me"
Invoke-Check -Label 'GET security/metrics' -Url "$BaseUrl/api/v1/security/metrics"
Invoke-Check -Label 'GET health' -Url "$BaseUrl/api/v1/health"
Invoke-Check -Label 'GET health/ready' -Url "$BaseUrl/api/v1/health/ready"
Invoke-Check -Label 'POST system/maintenance/cleanup' -Url "$BaseUrl/api/v1/system/maintenance/cleanup" -Method 'POST'

Write-Host ''
Write-Host 'Core modules (HOSPITAL_ADMIN)' -ForegroundColor Yellow
$modules = @(
    '/organization/branches',
    '/admin/roles',
    "/admin/staff?branchId=$BranchId&limit=10",
    "/admin/accounts?branchId=$BranchId&limit=10",
    '/patients?offset=0&limit=5',
    "/appointments?branchId=$BranchId&from=2026-01-01T00:00:00Z&to=2027-12-31T00:00:00Z&offset=0&limit=5",
    "/admissions/active?branchId=$BranchId&limit=5",
    "/admissions/beds?branchId=$BranchId&availableOnly=true",
    "/billing/invoices?branchId=$BranchId&limit=5",
    "/accounting/accounts?branchId=$BranchId",
    "/accounting/journals?branchId=$BranchId&limit=5",
    "/accounting/trial-balance?branchId=$BranchId&asOf=2026-11-09",
    "/procurement/suppliers?limit=5",
    "/procurement/purchase-orders?branchId=$BranchId&limit=5",
    "/analytics/dashboard?branchId=$BranchId",
    "/analytics/decision-cards?branchId=$BranchId&openOnly=false&limit=5",
    '/notifications/unread-count',
    "/audit-logs?branchId=$BranchId&offset=0&limit=5",
    "/system/settings?branchId=$BranchId",
    "/patients/$PatientId/allergies",
    "/patients/$PatientId/consents"
)
foreach ($m in $modules) {
    Invoke-Check -Label ("GET " + $m.Split('?')[0]) -Url ("$BaseUrl/api/v1" + $m)
}

Write-Host ''
Write-Host 'Authentication lifecycle' -ForegroundColor Yellow
Invoke-BodyCheck -Label 'POST auth/password wrong current -> 401' -Path '/auth/password' `
    -Body @{ currentPassword = 'Definitely-Wrong@2026'; newPassword = 'SmokeTest@2026x' } -Expected 401
$disposableToken = (Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
        -ContentType 'application/json' `
        -Body (@{ username = 'manager1'; password = $Password } | ConvertTo-Json -Compress)).token
Invoke-BodyCheck -Label 'POST auth/logout -> 204' -Path '/auth/logout' -Token $disposableToken -Expected 204
Invoke-Check -Label 'GET security/me after logout -> 401' -Url "$BaseUrl/api/v1/security/me" `
    -Expected 401 -Token $disposableToken

Write-Host ''
Write-Host 'MFA (TOTP) surface' -ForegroundColor Yellow
Invoke-Check -Label 'GET auth/mfa/status without token -> 401' -Url "$BaseUrl/api/v1/auth/mfa/status" -Expected 401 -NoAuth
Invoke-Check -Label 'POST auth/mfa/enroll/start without token -> 401' -Url "$BaseUrl/api/v1/auth/mfa/enroll/start" -Method 'POST' -Expected 401 -NoAuth
Invoke-Check -Label 'GET auth/mfa/status as admin -> 200' -Url "$BaseUrl/api/v1/auth/mfa/status"
Invoke-BodyCheck -Label 'POST auth/mfa/verify empty challenge -> 401' -Path '/auth/mfa/verify' `
    -Body @{ challengeToken = ''; code = '123456' } -Expected 401 -Token 'anonymous'
Invoke-RawBodyCheck -Label 'POST auth/mfa/verify malformed JSON -> 400' -Path '/auth/mfa/verify' `
    -RawBody '{bad' -Expected 400 -Token 'anonymous'
Invoke-FieldCheck -Label 'auth/mfa/status exposes enabled' -Path '/auth/mfa/status' -Field 'enabled'
Invoke-FieldCheck -Label 'security/metrics exposes mfaEnrollmentEnforced' -Path '/security/metrics' -Field 'mfaEnrollmentEnforced'

Write-Host ''
Write-Host 'Role gating (must be denied)' -ForegroundColor Yellow
Invoke-Check -Label 'doctor -> admin roles' -Url "$BaseUrl/api/v1/admin/roles" -Expected 403 -User 'doctor1'
Invoke-Check -Label 'doctor -> security metrics' -Url "$BaseUrl/api/v1/security/metrics" -Expected 403 -User 'doctor1'
Invoke-Check -Label 'doctor -> maintenance cleanup' -Url "$BaseUrl/api/v1/system/maintenance/cleanup" -Method 'POST' -Expected 403 -User 'doctor1'
Invoke-Check -Label 'nurse -> billing' -Url "$BaseUrl/api/v1/billing/invoices?branchId=$BranchId&limit=5" -Expected 403 -User 'nurse1'
Invoke-Check -Label 'accountant -> admin roles' -Url "$BaseUrl/api/v1/admin/roles" -Expected 403 -User 'accountant1'

Write-Host ''
if ($script:failed -eq 0) {
    Write-Host ("RESULT: {0} passed, 0 failed" -f $script:passed) -ForegroundColor Green
    exit 0
}
else {
    Write-Host ("RESULT: {0} passed, {1} failed" -f $script:passed, $script:failed) -ForegroundColor Red
    exit 1
}
