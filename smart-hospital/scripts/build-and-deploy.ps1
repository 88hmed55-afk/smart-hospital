#requires -Version 5.1
<#
.SYNOPSIS
    Reproducible Windows build and GlassFish deployment for Smart Hospital.

.DESCRIPTION
    Wraps the exact toolchain used in this environment (JDK 17, NetBeans Maven,
    GlassFish 7.0.23). By default it runs the unit tests, builds the EAR and deploys
    it. Use -SkipTests for a fast local iteration and -BuildOnly to stop after
    packaging.

.EXAMPLE
    .\build-and-deploy.ps1

.EXAMPLE
    .\build-and-deploy.ps1 -SkipTests -BuildOnly
#>
param(
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string]$MavenCmd = 'C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd',
    [string]$AsadminCmd = 'C:\server\glassfish-7.0.23\glassfish7\bin\asadmin.bat',
    [string]$ProjectRoot = (Join-Path $PSScriptRoot '..'),
    [switch]$SkipTests,
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$pom = Join-Path $ProjectRoot 'pom.xml'
$ear = Join-Path $ProjectRoot 'smart-hospital-ear\target\smart-hospital.ear'

if (!(Test-Path $JavaHome)) { throw "JAVA_HOME not found: $JavaHome" }
if (!(Test-Path $MavenCmd)) { throw "Maven not found: $MavenCmd" }
if (!(Test-Path $pom)) { throw "pom.xml not found: $pom" }

$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"

$mvnArgs = @('-B', '-ntp', '-f', $pom, 'clean', 'package')
if ($SkipTests) { $mvnArgs += '-DskipTests' }

Write-Host "Building Smart Hospital ($($mvnArgs -join ' '))" -ForegroundColor Cyan
& $MavenCmd @mvnArgs
if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE." }
if (!(Test-Path $ear)) { throw "EAR not produced: $ear" }
Write-Host "Built $ear" -ForegroundColor Green

if ($BuildOnly) {
    Write-Host 'BUILD_OK'
    exit 0
}

if (!(Test-Path $AsadminCmd)) { throw "asadmin not found: $AsadminCmd" }

Write-Host 'Deploying to GlassFish' -ForegroundColor Cyan
& $AsadminCmd deploy --force=true --name=smart-hospital $ear
if ($LASTEXITCODE -ne 0) { throw "Deployment failed with exit code $LASTEXITCODE." }

Write-Host 'DEPLOY_OK' -ForegroundColor Green
