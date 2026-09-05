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
        $optionalIntegrationEnvironment = @(
            'OPTIONAL_INTEGRATION_ENABLED',
            'OPTIONAL_INTEGRATION_EXECUTION_MODE',
            'OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED',
            'OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS',
            'OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION',
            'OPTIONAL_INTEGRATION_SENDER_QUALIFICATION',
            'OPTIONAL_INTEGRATION_RECEIVER_ORIGIN',
            'OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION',
            'OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN',
            'OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED',
            'OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256',
            'OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID',
            'OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256'
        )
        $previousOptionalIntegrationEnvironment = @{}
        foreach ($name in $optionalIntegrationEnvironment) {
            $previousOptionalIntegrationEnvironment[$name] =
                [Environment]::GetEnvironmentVariable($name, 'Process')
        }
        $applicationExitCode = 1
        try {
            $env:OPTIONAL_INTEGRATION_ENABLED = 'false'
            $env:OPTIONAL_INTEGRATION_EXECUTION_MODE = 'DISABLED'
            $env:OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED = 'false'
            $env:OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS = 'NOT_EVIDENCED'
            $env:OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION = 'NOT_QUALIFIED'
            $env:OPTIONAL_INTEGRATION_SENDER_QUALIFICATION = 'NOT_QUALIFIED'
            $env:OPTIONAL_INTEGRATION_RECEIVER_ORIGIN = ''
            $env:OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION = 'false'
            $env:OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN = ''
            $env:OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED = 'false'
            $env:OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256 = ''
            $env:OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID = ''
            $env:OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256 = ''

            & .\mvnw.cmd '-Dspring-boot.run.profiles=local' 'spring-boot:run'
            $applicationExitCode = $LASTEXITCODE
        }
        finally {
            foreach ($name in $optionalIntegrationEnvironment) {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    $previousOptionalIntegrationEnvironment[$name],
                    'Process')
            }
        }
        exit $applicationExitCode
    }

    Write-Host 'APPLICATION_STATUS=NOT_STARTED'
    Write-Host 'NEXT_ACTION=Run from Eclipse or execute .\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run'
}
finally {
    Pop-Location
}
