param(
    [string]$GlassFishHome = "C:\glassfish7",
    [string]$MysqlConnectorJar = "C:\mysql-connector\mysql-connector-j-9.7.0.jar",
    [string]$DbUser = "smart_hospital_app",
    [string]$PoolName = "SmartHospitalPool",
    [string]$JndiName = "jdbc/SmartHospitalDS",
    [string]$PasswordAlias = "SmartHospitalDbPassword"
)
$ErrorActionPreference = "Stop"
$asadmin = Join-Path $GlassFishHome "bin\asadmin.bat"
if (!(Test-Path $asadmin)) { throw "GlassFish asadmin.bat not found: $asadmin. Set -GlassFishHome to your GlassFish 7.0.23 installation folder." }
if (!(Test-Path $MysqlConnectorJar)) { throw "MySQL Connector/J JAR not found: $MysqlConnectorJar. Download Connector/J and set -MysqlConnectorJar." }

# Add Connector/J to the GlassFish domain classloader.
$domainLib = Join-Path $GlassFishHome "glassfish\domains\domain1\lib"
New-Item -ItemType Directory -Force -Path $domainLib | Out-Null
Copy-Item -Force $MysqlConnectorJar $domainLib

Write-Host "Restarting GlassFish domain so the MySQL JDBC driver is loaded..."
& $asadmin stop-domain domain1
if ($LASTEXITCODE -ne 0) { Write-Warning "stop-domain returned non-zero; the domain may already be stopped." }
& $asadmin start-domain domain1
if ($LASTEXITCODE -ne 0) { throw "GlassFish domain1 could not start." }

Write-Host "Create/update GlassFish password alias '$PasswordAlias'. Enter the dedicated MySQL application-user password when prompted."
& $asadmin create-password-alias $PasswordAlias
if ($LASTEXITCODE -ne 0) { throw "Could not create password alias. If it already exists, delete it with 'asadmin delete-password-alias $PasswordAlias' and rerun." }

$poolProperties = "user=$DbUser`:password=`${ALIAS=$PasswordAlias}`:serverName=127.0.0.1`:port=3306`:databaseName=hospital`:connectionTimeZone=UTC`:forceConnectionTimeZoneToSession=true`:sslMode=DISABLED"
& $asadmin create-jdbc-connection-pool `
  --datasourceclassname com.mysql.cj.jdbc.MysqlXADataSource `
  --restype javax.sql.XADataSource `
  --property $poolProperties `
  $PoolName
if ($LASTEXITCODE -ne 0) { throw "Could not create JDBC connection pool '$PoolName'. Check whether a pool with that name already exists." }

& $asadmin create-jdbc-resource --connectionpoolid $PoolName $JndiName
if ($LASTEXITCODE -ne 0) { throw "Could not create JDBC resource '$JndiName'. Check whether the resource already exists." }
& $asadmin ping-connection-pool $PoolName
if ($LASTEXITCODE -ne 0) { throw "GlassFish could not connect to MySQL. Check MySQL service, database, user grant, Connector/J and pool settings." }
Write-Host "Datasource $JndiName created and pinged successfully."
