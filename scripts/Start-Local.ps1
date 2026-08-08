[CmdletBinding()]
param(
    [switch]$RunApplication
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'Preflight-Local.ps1')

Push-Location $repositoryRoot
try {
    & docker compose --env-file .env up -d postgres
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to start PostgreSQL'
    }

    $containerId = (& docker compose --env-file .env ps -q postgres | Out-String).Trim()
    if (-not $containerId) {
        throw 'PostgreSQL container id was not returned'
    }

    $healthy = $false
    for ($attempt = 1; $attempt -le 40; $attempt++) {
        $status = (& docker inspect --format '{{.State.Health.Status}}' $containerId 2>$null | Out-String).Trim()
        if ($status -eq 'healthy') {
            $healthy = $true
            break
        }
        if ($status -eq 'unhealthy') {
            throw 'PostgreSQL healthcheck reported unhealthy'
        }
        Start-Sleep -Seconds 2
    }

    if (-not $healthy) {
        throw 'PostgreSQL did not become healthy in time'
    }

    Write-Host 'POSTGRES_STATUS=HEALTHY'
    if ($RunApplication) {
        & .\mvnw.cmd '-Dspring-boot.run.profiles=local' 'spring-boot:run'
        exit $LASTEXITCODE
    }

    Write-Host 'APPLICATION_STATUS=NOT_STARTED'
    Write-Host 'NEXT_ACTION=Run from Eclipse or execute .\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run'
}
finally {
    Pop-Location
}
