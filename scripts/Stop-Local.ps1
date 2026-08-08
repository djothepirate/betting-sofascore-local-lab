[CmdletBinding()]
param(
    [switch]$RemoveData
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot
try {
    if ($RemoveData) {
        & docker compose --env-file .env down --volumes
    }
    else {
        & docker compose --env-file .env down
    }
    if ($LASTEXITCODE -ne 0) {
        throw 'docker compose down failed'
    }
    Write-Host "LOCAL_STACK_STOPPED=YES"
    Write-Host "POSTGRES_DATA_REMOVED=$RemoveData"
}
finally {
    Pop-Location
}
