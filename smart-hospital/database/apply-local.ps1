# Compatibility entry point for the current MySQL edition only.
# Historical PostgreSQL scripts are isolated under database\legacy-postgresql and must not be used.
& (Join-Path $PSScriptRoot "apply-mysql-local.ps1") @args
