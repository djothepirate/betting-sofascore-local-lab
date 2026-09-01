[CmdletBinding()]
param(
    [switch]$RemoveData
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$distributionRoot = Split-Path -Parent $PSScriptRoot

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
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

$composePath = Join-Path $distributionRoot 'compose.yaml'
$envPath = Join-Path $distributionRoot '.env'
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
    throw 'compose.yaml is missing from the distribution.'
}
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
    throw 'Missing .env. Run scripts\Initialize-LocalConfig.ps1 first.'
}

Assert-Command -Name 'docker'
$dockerGlobalArguments = @(Get-LocalDockerArguments)
& docker @dockerGlobalArguments info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Desktop is not ready'
}
& docker @dockerGlobalArguments compose version *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose is not available'
}

Push-Location $distributionRoot
try {
    $composeArguments = @(
        'compose'
        '--project-name'
        'betting-sofascore-local-lab'
        '--file'
        'compose.yaml'
        '--env-file'
        '.env'
        'down'
    )
    if ($RemoveData) {
        $composeArguments += '--volumes'
    }
    $dockerArguments = @($dockerGlobalArguments) + $composeArguments
    & docker @dockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'docker compose down failed'
    }
    Write-Host 'LOCAL_STACK_STOPPED=YES'
    Write-Host "POSTGRES_DATA_REMOVED=$RemoveData"
}
finally {
    Pop-Location
}
