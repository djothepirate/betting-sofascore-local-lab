[CmdletBinding()]
param(
    [switch]$RunApplication
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$distributionRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'Preflight-Local.ps1')

$applicationJars = @(
    Get-ChildItem -LiteralPath $distributionRoot -File -Filter 'betting-sofascore-local-lab-*.jar' |
        Where-Object {
            $_.Name -notmatch '-(sources|javadoc|tests|provider-playwright-worker)[.]jar$'
        }
)
if ($applicationJars.Count -ne 1) {
    throw "Exactly one bundled application JAR is required; found $($applicationJars.Count)."
}
$applicationJar = $applicationJars[0]

function Test-IsUnsafeEnvironmentName {
    param([Parameter(Mandatory = $true)][string]$Name)
    return (
        $Name -eq 'SPRING_APPLICATION_JSON' -or
        $Name -like 'SPRING_CONFIG_*' -or
        $Name -like 'SPRING_PROFILES_*' -or
        $Name -like 'SPRING_DATASOURCE_*' -or
        $Name -like 'SPRING_FLYWAY_*' -or
        $Name -like 'SERVER_*' -or
        $Name -like 'MANAGEMENT_SERVER_*' -or
        $Name -like 'SOFASCORE_*' -or
        $Name -like 'OPTIONAL_INTEGRATION_*' -or
        $Name -eq '_JAVA_OPTIONS' -or
        $Name -eq 'JAVA_TOOL_OPTIONS' -or
        $Name -eq 'JDK_JAVA_OPTIONS'
    )
}

function Get-LocalDockerArguments {
    $dockerContextOverride = [Environment]::GetEnvironmentVariable('DOCKER_CONTEXT')
    $dockerHostOverride = [Environment]::GetEnvironmentVariable('DOCKER_HOST')
    if (-not [string]::IsNullOrWhiteSpace($dockerContextOverride)) {
        $endpointOutput = & docker context inspect --format '{{.Endpoints.docker.Host}}' $dockerContextOverride
        $endpointExitCode = $LASTEXITCODE
        if ($endpointExitCode -ne 0) {
            throw 'Unable to inspect the Docker context selected by DOCKER_CONTEXT.'
        }
        $endpoint = ($endpointOutput | Out-String).Trim()
    }
    elseif (-not [string]::IsNullOrWhiteSpace($dockerHostOverride)) {
        if ($dockerHostOverride -notmatch '^npipe:////[.]/pipe/[^/]+$') {
            throw 'DOCKER_HOST must target a local Windows named pipe.'
        }
        $endpoint = $dockerHostOverride
    }
    else {
        $endpointOutput = & docker context inspect --format '{{.Endpoints.docker.Host}}'
        $endpointExitCode = $LASTEXITCODE
        if ($endpointExitCode -ne 0) {
            throw 'Unable to inspect the selected Docker context.'
        }
        $endpoint = ($endpointOutput | Out-String).Trim()
    }
    if ($endpoint -notmatch '^npipe:////[.]/pipe/[^/]+$') {
        throw 'The selected Docker context must target a local Windows named pipe.'
    }
    return @('--host', $endpoint)
}

Push-Location $distributionRoot
try {
    $dockerGlobalArguments = @(Get-LocalDockerArguments)
    & docker @dockerGlobalArguments compose --project-name betting-sofascore-local-lab --file compose.yaml --env-file .env up -d postgres
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to start PostgreSQL'
    }

    $containerOutput = & docker @dockerGlobalArguments compose --project-name betting-sofascore-local-lab --file compose.yaml --env-file .env ps -q postgres
    $containerQueryExitCode = $LASTEXITCODE
    if ($containerQueryExitCode -ne 0) {
        throw 'Failed to query the PostgreSQL container id'
    }
    $containerId = ($containerOutput | Out-String).Trim()
    if (-not $containerId) {
        throw 'PostgreSQL container id was not returned'
    }

    $healthy = $false
    for ($attempt = 1; $attempt -le 40; $attempt++) {
        $inspectOutput = & docker @dockerGlobalArguments inspect --format '{{.State.Health.Status}}' $containerId 2>$null
        $inspectExitCode = $LASTEXITCODE
        if ($inspectExitCode -ne 0) {
            throw 'Failed to inspect the PostgreSQL health status'
        }
        $status = ($inspectOutput | Out-String).Trim()
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
        $savedEnvironment = @{}
        try {
            foreach ($environmentEntry in @(Get-ChildItem Env:)) {
                if (Test-IsUnsafeEnvironmentName -Name $environmentEntry.Name) {
                    $savedEnvironment[$environmentEntry.Name] = $environmentEntry.Value
                    Remove-Item -LiteralPath "Env:$($environmentEntry.Name)"
                }
            }
            $javaArguments = @(
                '-Djdk.httpclient.disableRetryConnect=true'
                '-Djdk.httpclient.redirects.retrylimit=1'
                '-Djdk.httpclient.enableAllMethodRetry=false'
                '-jar'
                $applicationJar.FullName
                '--spring.profiles.active=local'
                '--server.address=127.0.0.1'
                '--server.port=8087'
                '--sofascore.enabled=false'
                '--sofascore.playwright.enabled=false'
                '--sofascore.playwright.loopback-qualification=false'
                '--sofascore.playwright.loopback-origin='
                '--sofascore.j3-qualification-enabled=false'
                '--sofascore.j4-event-details-qualification-enabled=false'
                '--sofascore.j4-event-details-phase2-enabled=false'
                '--sofascore.j5-event-data-qualification-enabled=false'
                '--sofascore.tournament-event-discovery-enabled=false'
                '--sofascore.automatic-refresh-enabled=false'
                '--sofascore.live-polling-enabled=false'
                '--optional-integration.enabled=false'
                '--optional-integration.remote-delivery-authorized=false'
                '--optional-integration.official-permission-status=NOT_EVIDENCED'
                '--optional-integration.loopback-qualification=false'
                '--optional-integration.loopback-origin='
                '--optional-integration.automatic-retry-enabled=false'
            )
            Write-Host 'ENVIRONMENT_GUARDS=APPLIED'
            Write-Host 'APPLICATION_STATUS=STARTING_LOCAL_ONLY'
            & java @javaArguments
            $applicationExitCode = $LASTEXITCODE
            if ($applicationExitCode -ne 0) {
                throw "The local application exited with code $applicationExitCode."
            }
        }
        finally {
            foreach ($savedEntry in $savedEnvironment.GetEnumerator()) {
                Set-Item -LiteralPath "Env:$($savedEntry.Key)" -Value $savedEntry.Value
            }
        }
        Write-Host 'APPLICATION_STATUS=STOPPED_EXIT_CODE_0'
    }
    else {
        Write-Host 'APPLICATION_STATUS=NOT_STARTED'
        Write-Host 'NEXT_ACTION=pwsh -NoProfile -File .\scripts\Start-Local.ps1 -RunApplication'
    }
}
finally {
    Pop-Location
}
